/**
 * Basket comparison — "which store is cheapest for my whole list?".
 *
 * This is the v2 feature, but it is implemented here rather than deferred
 * because doing so proves the search pipeline composes: a basket is just N
 * searches reduced per provider. The API route `/api/basket` exposes it; the
 * MVP UI does not link to it yet.
 */

import { DEFAULT_SORT } from '@/lib/validation';
import type { Location, Provider } from '@/providers/types';
import { search } from '@/services/searchService';
import type { SearchOffer } from '@/types/search';

export interface BasketItemInput {
  query: string;
  /** How many of this item the shopper wants. */
  quantity: number;
}

export interface BasketLine {
  query: string;
  quantity: number;
  /** Cheapest in-stock offer this provider had for the line, if any. */
  offer?: Pick<SearchOffer, 'title' | 'price' | 'unitPriceLabel' | 'productUrl' | 'availability' | 'dataSource'>;
  lineTotal?: number;
  /** True when the provider had nothing for this line. */
  missing: boolean;
}

export interface BasketProviderTotal {
  provider: Provider;
  providerName: string;
  /** Sum of the lines this provider could fill. */
  total: number;
  itemsFound: number;
  itemsMissing: number;
  lines: BasketLine[];
  /** Whether every filled line came from demo data. */
  demoOnly: boolean;
}

export interface BasketComparison {
  items: BasketItemInput[];
  location: Location;
  providers: BasketProviderTotal[];
  /** The provider that can fill the most lines, cheapest — or undefined if none can. */
  cheapest?: {
    provider: Provider;
    providerName: string;
    total: number;
    itemsMissing: number;
  };
  durationMs: number;
}

/** Accepts either "tomato 1kg" or { query, quantity }. */
export function normalizeBasketItems(items: Array<string | { query: string; quantity?: number }>): BasketItemInput[] {
  return items.map((item) =>
    typeof item === 'string' ? { query: item, quantity: 1 } : { query: item.query, quantity: item.quantity ?? 1 },
  );
}

/**
 * Runs one search per basket line (all concurrently) and reduces the results
 * into a per-provider total.
 */
export async function compareBasket(options: {
  items: Array<string | { query: string; quantity?: number }>;
  location: Location;
  providers?: Provider[];
}): Promise<BasketComparison> {
  const startedAt = Date.now();
  const items = normalizeBasketItems(options.items);

  const searches = await Promise.all(
    items.map((item) =>
      search({
        query: item.query,
        location: options.location,
        sort: DEFAULT_SORT,
        providers: options.providers,
      }),
    ),
  );

  const totals = new Map<Provider, BasketProviderTotal>();

  searches.forEach((response, index) => {
    const item = items[index]!;

    // Every provider that responded gets a line, even an empty one, so the
    // comparison shows honestly who could not fill the basket.
    for (const outcome of response.providers) {
      if (!totals.has(outcome.provider)) {
        totals.set(outcome.provider, {
          provider: outcome.provider,
          providerName: outcome.name,
          total: 0,
          itemsFound: 0,
          itemsMissing: 0,
          lines: [],
          demoOnly: true,
        });
      }
    }

    // The leading group is the best interpretation of the line's query.
    const leadGroup = response.results[0];

    for (const total of totals.values()) {
      const offer = leadGroup?.offers.find(
        (candidate) => candidate.provider === total.provider && candidate.availability !== 'out_of_stock',
      );

      if (!offer) {
        total.itemsMissing += 1;
        total.lines.push({ query: item.query, quantity: item.quantity, missing: true });
        continue;
      }

      const lineTotal = Math.round(offer.price * item.quantity * 100) / 100;
      total.total = Math.round((total.total + lineTotal) * 100) / 100;
      total.itemsFound += 1;
      total.demoOnly = total.demoOnly && offer.dataSource === 'demo';
      total.lines.push({
        query: item.query,
        quantity: item.quantity,
        missing: false,
        lineTotal,
        offer: {
          title: offer.title,
          price: offer.price,
          unitPriceLabel: offer.unitPriceLabel,
          productUrl: offer.productUrl,
          availability: offer.availability,
          dataSource: offer.dataSource,
        },
      });
    }
  });

  const providers = [...totals.values()].sort((a, b) => {
    // A cheap total that skips half the list is not actually cheaper, so
    // completeness ranks ahead of price.
    if (a.itemsMissing !== b.itemsMissing) return a.itemsMissing - b.itemsMissing;
    return a.total - b.total;
  });

  const best = providers.find((provider) => provider.itemsFound > 0);

  return {
    items,
    location: options.location,
    providers,
    cheapest: best
      ? {
          provider: best.provider,
          providerName: best.providerName,
          total: best.total,
          itemsMissing: best.itemsMissing,
        }
      : undefined,
    durationMs: Date.now() - startedAt,
  };
}
