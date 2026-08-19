/**
 * Wire types shared by the API route and the client components.
 *
 * Kept free of server-only imports so client components can import them
 * without pulling `@prisma/client` or `node:crypto` into the browser bundle.
 */

import type { Availability, DataSource, Provider, ProviderOutcome, UnitBasis } from '@/providers/types';
import type { SortOption } from '@/lib/validation';

/** A single store's offer, as serialised over HTTP. */
export interface SearchOffer {
  id: string;
  provider: Provider;
  providerName: string;
  title: string;
  brand?: string;
  description?: string;
  imageUrl?: string;
  productUrl?: string;
  price: number;
  originalPrice?: number;
  currency: string;
  quantity?: number;
  unit?: string;
  pricePerUnit?: number;
  unitBasis: UnitBasis;
  /** e.g. "₹50/kg" — already formatted for display. */
  unitPriceLabel?: string;
  availability: Availability;
  deliveryTimeMinutes?: number;
  deliveryFee?: number;
  rating?: number;
  reviewCount?: number;
  dataSource: DataSource;
  /** ISO 8601 timestamp. */
  fetchedAt: string;
}

/** Offers from different stores that were matched to the same product. */
export interface SearchProductGroup {
  matchKey: string;
  title: string;
  imageUrl?: string;
  unitBasis: UnitBasis;
  offers: SearchOffer[];
  /** Cheapest in-stock offer in this group, by the active sort's price metric. */
  bestOffer?: SearchOffer;
}

export interface SearchSummary {
  providersSearched: number;
  providersSucceeded: number;
  providersFailed: number;
  productsFound: number;
  /** Cheapest absolute price across all results, in INR. */
  lowestPrice?: number;
  /** Cheapest normalised unit price, e.g. 39 for "₹39/kg". */
  lowestPricePerUnit?: number;
  lowestPricePerUnitLabel?: string;
  bestProvider?: Provider;
  bestProviderName?: string;
  cached: boolean;
  durationMs: number;
  /** True when every result shown came from a demo provider. */
  demoOnly: boolean;
}

export interface SearchResponse {
  query: string;
  location: {
    pincode: string;
    latitude?: number;
    longitude?: number;
  };
  sort: SortOption;
  results: SearchProductGroup[];
  /** Per-provider status, including failures — the UI shows these inline. */
  providers: ProviderOutcome[];
  summary: SearchSummary;
}

export interface ApiError {
  error: string;
  details?: Record<string, string>;
}
