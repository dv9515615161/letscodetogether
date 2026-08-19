/**
 * Result ordering.
 *
 * Out-of-stock offers always sink to the bottom of their group regardless of
 * the chosen sort — a cheap price you cannot buy is not a useful first row.
 */

import type { SortOption } from '@/lib/validation';
import type { SearchOffer, SearchProductGroup } from '@/types/search';

const LAST = Number.POSITIVE_INFINITY;

function availabilityRank(offer: SearchOffer): number {
  switch (offer.availability) {
    case 'available':
      return 0;
    case 'unknown':
      return 1;
    case 'out_of_stock':
      return 2;
  }
}

/** The value being minimised (or, for rating, maximised) for a given sort. */
export function sortMetric(offer: SearchOffer, sort: SortOption): number {
  switch (sort) {
    case 'price':
      return offer.price;
    case 'price_per_unit':
      // Products with no parseable pack size fall back to absolute price so
      // they still order sensibly among themselves.
      return offer.pricePerUnit ?? offer.price;
    case 'delivery':
      return offer.deliveryTimeMinutes ?? LAST;
    case 'rating':
      // Negated so that "smaller is better" holds for every sort.
      return offer.rating === undefined ? LAST : -offer.rating;
    case 'provider':
      return 0;
  }
}

export function compareOffers(a: SearchOffer, b: SearchOffer, sort: SortOption): number {
  const availability = availabilityRank(a) - availabilityRank(b);
  if (availability !== 0) return availability;

  if (sort === 'provider') {
    const byName = a.providerName.localeCompare(b.providerName);
    if (byName !== 0) return byName;
    return a.price - b.price;
  }

  const metric = sortMetric(a, sort) - sortMetric(b, sort);
  if (metric !== 0 && Number.isFinite(metric)) return metric;
  if (metric !== 0) return sortMetric(a, sort) === LAST ? 1 : -1;

  // Stable, meaningful tiebreaks: cheaper first, then faster, then by name.
  if (a.price !== b.price) return a.price - b.price;
  const deliveryA = a.deliveryTimeMinutes ?? LAST;
  const deliveryB = b.deliveryTimeMinutes ?? LAST;
  if (deliveryA !== deliveryB) return deliveryA < deliveryB ? -1 : 1;
  return a.providerName.localeCompare(b.providerName);
}

export function sortOffers(offers: SearchOffer[], sort: SortOption): SearchOffer[] {
  return [...offers].sort((a, b) => compareOffers(a, b, sort));
}

/**
 * Orders groups by their own best offer, so the cheapest product overall leads
 * the page.
 */
export function sortGroups(groups: SearchProductGroup[], sort: SortOption): SearchProductGroup[] {
  const sorted = groups.map((group) => ({
    ...group,
    offers: sortOffers(group.offers, sort),
  }));

  for (const group of sorted) {
    group.bestOffer = group.offers.find((offer) => offer.availability !== 'out_of_stock') ?? group.offers[0];
  }

  return sorted.sort((a, b) => {
    const bestA = a.bestOffer;
    const bestB = b.bestOffer;
    if (!bestA || !bestB) return bestA ? -1 : bestB ? 1 : 0;
    // More cross-store coverage is more useful, so break ties on offer count.
    const primary = compareOffers(bestA, bestB, sort);
    if (primary !== 0) return primary;
    return b.offers.length - a.offers.length;
  });
}
