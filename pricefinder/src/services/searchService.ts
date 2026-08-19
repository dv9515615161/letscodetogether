/**
 * Search orchestration: fan out to every enabled provider, normalise what comes
 * back, group it, sort it, and summarise it.
 *
 * Two properties matter most here.
 *  1. Providers run concurrently via `Promise.allSettled`, so total latency is
 *     the slowest provider rather than the sum of all of them.
 *  2. One provider failing never fails the search. Failures become entries in
 *     `providers[]` that the UI renders inline ("Zepto unavailable right now")
 *     while still showing everyone else's results.
 */

import { config } from '@/lib/env';
import { logger, toSafeMessage } from '@/lib/logger';
import type { SortOption } from '@/lib/validation';
import { ProviderTimeoutError } from '@/providers/base';
import { getEnabledProviders } from '@/providers/registry';
import type {
  Location,
  NormalizedProduct,
  ProductResult,
  Provider,
  ProviderOutcome,
  ShoppingProvider,
} from '@/providers/types';
import { buildSearchCacheKey, getCacheStore } from '@/services/cacheService';
import { recordSearch } from '@/services/persistence';
import { buildMatchKey, groupProducts } from '@/services/productMatcher';
import { normalizeProduct, parseQuantity, unitBasisFor } from '@/services/priceNormalizer';
import { sortGroups } from '@/services/sorting';
import type { SearchOffer, SearchProductGroup, SearchResponse, SearchSummary } from '@/types/search';

export interface SearchInput {
  query: string;
  location: Location;
  sort: SortOption;
  /** Restrict to a subset of the enabled providers. */
  providers?: Provider[];
  /** Skip the cache read (the cache is still written). */
  refresh?: boolean;
}

/** Shape stored in the cache: the response minus fields that must stay fresh. */
type CachedPayload = Omit<SearchResponse, 'sort' | 'summary'> & {
  summary: Omit<SearchSummary, 'cached' | 'durationMs'>;
};

function toOffer(product: NormalizedProduct, providerName: string): SearchOffer {
  return {
    id: product.id,
    provider: product.provider,
    providerName,
    title: product.title,
    brand: product.brand,
    description: product.description,
    imageUrl: product.imageUrl,
    productUrl: product.productUrl,
    price: product.price,
    originalPrice: product.originalPrice,
    currency: product.currency,
    quantity: product.quantity,
    unit: product.unit,
    pricePerUnit: product.normalizedPricePerUnit ?? product.pricePerUnit,
    unitBasis: product.unitBasis,
    unitPriceLabel: product.unitPriceLabel,
    availability: product.availability,
    deliveryTimeMinutes: product.deliveryTimeMinutes,
    deliveryFee: product.deliveryFee,
    rating: product.rating,
    reviewCount: product.reviewCount,
    dataSource: product.dataSource,
    fetchedAt: product.fetchedAt.toISOString(),
  };
}

interface ProviderRun {
  outcome: ProviderOutcome;
  products: NormalizedProduct[];
}

/** Runs one provider, converting any failure into a reportable outcome. */
async function runProvider(provider: ShoppingProvider, input: SearchInput): Promise<ProviderRun> {
  const startedAt = Date.now();
  const dataSource = provider.dataSource();

  try {
    const raw = await provider.search(input.query, input.location, {
      timeoutMs: config.providerTimeoutMs,
    });
    const normalized = raw.map((product) => normalizeOne(product, dataSource));
    return {
      outcome: {
        provider: provider.id,
        name: provider.name,
        status: 'ok',
        dataSource,
        productsFound: normalized.length,
        durationMs: Date.now() - startedAt,
      },
      products: normalized,
    };
  } catch (error) {
    const timedOut = error instanceof ProviderTimeoutError;
    // Logged with the provider id only — never the query's origin or any header.
    logger.warn('provider search failed', {
      provider: provider.id,
      timedOut,
      error: toSafeMessage(error),
    });
    return {
      outcome: {
        provider: provider.id,
        name: provider.name,
        status: timedOut ? 'timeout' : 'error',
        dataSource,
        productsFound: 0,
        durationMs: Date.now() - startedAt,
        message: timedOut
          ? `${provider.name} took too long to respond`
          : `${provider.name} unavailable right now`,
      },
      products: [],
    };
  }
}

function normalizeOne(product: ProductResult, dataSource: 'live' | 'demo'): NormalizedProduct {
  const parsed =
    product.quantity && product.unit ? { quantity: product.quantity, unit: product.unit } : parseQuantity(product.title);
  const basis = parsed ? unitBasisFor(parsed.unit) : 'unknown';
  return normalizeProduct(product, {
    matchKey: buildMatchKey(product.title, basis),
    dataSource,
  });
}

function summarize(groups: SearchProductGroup[], outcomes: ProviderOutcome[]): Omit<SearchSummary, 'cached' | 'durationMs'> {
  const offers = groups.flatMap((group) => group.offers);
  const purchasable = offers.filter((offer) => offer.availability !== 'out_of_stock');
  const considered = purchasable.length > 0 ? purchasable : offers;

  let lowestPrice: number | undefined;
  let cheapest: SearchOffer | undefined;
  let lowestPerUnit: number | undefined;
  let lowestPerUnitLabel: string | undefined;

  for (const offer of considered) {
    if (lowestPrice === undefined || offer.price < lowestPrice) {
      lowestPrice = offer.price;
      cheapest = offer;
    }
    if (offer.pricePerUnit !== undefined && (lowestPerUnit === undefined || offer.pricePerUnit < lowestPerUnit)) {
      lowestPerUnit = offer.pricePerUnit;
      lowestPerUnitLabel = offer.unitPriceLabel;
    }
  }

  // "Best" is judged on the leading group — the product the user most likely
  // meant — rather than the cheapest item anywhere in the response.
  const leadBest = groups[0]?.bestOffer ?? cheapest;

  return {
    providersSearched: outcomes.length,
    providersSucceeded: outcomes.filter((outcome) => outcome.status === 'ok').length,
    providersFailed: outcomes.filter((outcome) => outcome.status !== 'ok').length,
    productsFound: offers.length,
    lowestPrice,
    lowestPricePerUnit: lowestPerUnit,
    lowestPricePerUnitLabel: lowestPerUnitLabel,
    bestProvider: leadBest?.provider,
    bestProviderName: leadBest?.providerName,
    demoOnly: offers.length > 0 && offers.every((offer) => offer.dataSource === 'demo'),
  };
}

/**
 * The public entry point used by `/api/search` and by server components.
 */
export async function search(input: SearchInput): Promise<SearchResponse> {
  const startedAt = Date.now();
  const providers = getEnabledProviders(input.providers);

  const cache = getCacheStore();
  const cacheKey = buildSearchCacheKey({
    query: input.query,
    pincode: input.location.pincode,
    providers: providers.map((provider) => provider.id),
  });

  if (!input.refresh) {
    const cached = await cache.get<CachedPayload>(cacheKey);
    if (cached) {
      // Sort is applied on read, so changing the sort order never costs a
      // provider round trip.
      const results = sortGroups(cached.results, input.sort);
      return {
        ...cached,
        sort: input.sort,
        results,
        summary: {
          ...summarize(results, cached.providers),
          cached: true,
          durationMs: Date.now() - startedAt,
        },
      };
    }
  }

  if (providers.length === 0) {
    return {
      query: input.query,
      location: input.location,
      sort: input.sort,
      results: [],
      providers: [],
      summary: {
        providersSearched: 0,
        providersSucceeded: 0,
        providersFailed: 0,
        productsFound: 0,
        cached: false,
        durationMs: Date.now() - startedAt,
        demoOnly: false,
      },
    };
  }

  // Concurrent fan-out. `runProvider` never rejects, so `allSettled` is belt
  // and braces against a provider throwing synchronously.
  const settled = await Promise.allSettled(providers.map((provider) => runProvider(provider, input)));

  const outcomes: ProviderOutcome[] = [];
  const products: NormalizedProduct[] = [];

  settled.forEach((result, index) => {
    const provider = providers[index]!;
    if (result.status === 'fulfilled') {
      outcomes.push(result.value.outcome);
      products.push(...result.value.products);
    } else {
      outcomes.push({
        provider: provider.id,
        name: provider.name,
        status: 'error',
        dataSource: provider.dataSource(),
        productsFound: 0,
        durationMs: Date.now() - startedAt,
        message: `${provider.name} unavailable right now`,
      });
    }
  });

  const nameById = new Map(providers.map((provider) => [provider.id, provider.name]));
  const grouped = groupProducts(products).map<SearchProductGroup>((group) => ({
    matchKey: group.matchKey,
    title: group.title,
    imageUrl: group.imageUrl,
    unitBasis: group.unitBasis,
    offers: group.offers.map((offer) => toOffer(offer, nameById.get(offer.provider) ?? offer.provider)),
  }));

  const results = sortGroups(grouped, input.sort);
  const summaryBase = summarize(results, outcomes);

  const payload: CachedPayload = {
    query: input.query,
    location: input.location,
    results,
    providers: outcomes,
    summary: summaryBase,
  };

  // Cache misses must not be fatal, and neither must cache writes.
  await cache.set(cacheKey, payload, config.cacheTtlSeconds).catch((error: unknown) => {
    logger.warn('cache write failed', { error: toSafeMessage(error) });
  });

  const response: SearchResponse = {
    ...payload,
    sort: input.sort,
    summary: { ...summaryBase, cached: false, durationMs: Date.now() - startedAt },
  };

  // Fire-and-forget persistence: search history and price history are useful
  // but must never delay or break a response.
  void recordSearch(response);

  return response;
}
