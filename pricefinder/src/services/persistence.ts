/**
 * Optional persistence of search history and price observations.
 *
 * Every function here is a no-op when DATABASE_URL is unset, and every failure
 * is swallowed after a warning: a database outage must never turn into a failed
 * search. Price rows accumulate here so a future "tomato is up 12% this week"
 * feature has data to read.
 */

import { tryDb } from '@/lib/db';
import { logger } from '@/lib/logger';
import { describeProviders } from '@/providers/registry';
import type { SearchResponse } from '@/types/search';

let providersEnsured = false;

/** Upserts the provider rows the foreign keys depend on. Runs at most once. */
async function ensureProviders(): Promise<void> {
  if (providersEnsured) return;
  const result = await tryDb('providers.ensure', async (prisma) => {
    for (const provider of describeProviders()) {
      await prisma.provider.upsert({
        where: { id: provider.id },
        create: {
          id: provider.id,
          name: provider.name,
          websiteUrl: provider.websiteUrl,
          enabled: provider.enabled,
          dataSource: provider.dataSource,
        },
        update: {
          name: provider.name,
          websiteUrl: provider.websiteUrl,
          enabled: provider.enabled,
          dataSource: provider.dataSource,
        },
      });
    }
    return true;
  });
  if (result) providersEnsured = true;
}

/**
 * Writes one search, its result rows, and a price-history point per offer.
 * Called fire-and-forget from the search service.
 */
export async function recordSearch(response: SearchResponse): Promise<void> {
  await ensureProviders();

  await tryDb('search.record', async (prisma) => {
    const offers = response.results.flatMap((group) =>
      group.offers.map((offer) => ({ offer, matchKey: group.matchKey })),
    );

    const searchQuery = await prisma.searchQuery.create({
      data: {
        query: response.query,
        normalizedQuery: response.query.toLowerCase(),
        pincode: response.location.pincode,
        latitude: response.location.latitude,
        longitude: response.location.longitude,
        providersSearched: response.summary.providersSearched,
        productsFound: response.summary.productsFound,
        lowestPrice: response.summary.lowestPrice,
        durationMs: response.summary.durationMs,
        cacheHit: response.summary.cached,
      },
    });

    for (const { offer, matchKey } of offers) {
      const product = await prisma.product.upsert({
        where: { providerId_externalId: { providerId: offer.provider, externalId: offer.id } },
        create: {
          providerId: offer.provider,
          externalId: offer.id,
          title: offer.title,
          normalizedTitle: offer.title.toLowerCase(),
          matchKey,
          brand: offer.brand,
          description: offer.description,
          imageUrl: offer.imageUrl,
          productUrl: offer.productUrl,
          quantity: offer.quantity,
          unit: offer.unit,
        },
        update: {
          title: offer.title,
          normalizedTitle: offer.title.toLowerCase(),
          matchKey,
          productUrl: offer.productUrl,
          quantity: offer.quantity,
          unit: offer.unit,
        },
      });

      await prisma.searchResult.create({
        data: {
          searchQueryId: searchQuery.id,
          providerId: offer.provider,
          productId: product.id,
          title: offer.title,
          price: offer.price,
          originalPrice: offer.originalPrice,
          currency: offer.currency,
          quantity: offer.quantity,
          unit: offer.unit,
          pricePerUnit: offer.pricePerUnit,
          unitBasis: offer.unitBasis,
          availability: offer.availability,
          deliveryTimeMinutes: offer.deliveryTimeMinutes,
          deliveryFee: offer.deliveryFee,
          rating: offer.rating,
          reviewCount: offer.reviewCount,
          productUrl: offer.productUrl,
          imageUrl: offer.imageUrl,
          matchKey,
          dataSource: offer.dataSource,
          fetchedAt: new Date(offer.fetchedAt),
        },
      });

      await prisma.priceHistory.create({
        data: {
          productId: product.id,
          providerId: offer.provider,
          price: offer.price,
          pricePerUnit: offer.pricePerUnit,
          unitBasis: offer.unitBasis,
          currency: offer.currency,
          pincode: response.location.pincode,
        },
      });
    }

    return true;
  });
}

/**
 * Reads recent price points for a product. Not used by the MVP UI — it is the
 * seam the price-trend feature will build on.
 */
export async function getPriceHistory(matchKey: string, days = 30) {
  const since = new Date(Date.now() - days * 24 * 60 * 60 * 1000);
  const rows = await tryDb('priceHistory.read', (prisma) =>
    prisma.priceHistory.findMany({
      where: { recordedAt: { gte: since }, product: { matchKey } },
      orderBy: { recordedAt: 'asc' },
      select: { price: true, pricePerUnit: true, providerId: true, recordedAt: true },
    }),
  );
  if (!rows) {
    logger.info('price history unavailable (no database configured)');
    return [];
  }
  return rows;
}
