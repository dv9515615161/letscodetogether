/**
 * The contract every shopping platform integration implements.
 *
 * Nothing outside `src/providers/` knows how a store is reached — whether it is
 * an official affiliate API, a partner feed, or (for the MVP) a demo catalogue.
 * Adding a store means adding one file here and one line in `registry.ts`.
 */

export const PROVIDER_IDS = [
  'blinkit',
  'zepto',
  'amazon',
  'flipkart',
  'bigbasket',
  'instamart',
] as const;

export type Provider = (typeof PROVIDER_IDS)[number];

export type Availability = 'available' | 'out_of_stock' | 'unknown';

/**
 * `live`  — data came from an authorised, documented API using real credentials.
 * `demo`  — data is generated sample data. It must always be labelled as such
 *           in the UI and in API responses; never present it as a real price.
 */
export type DataSource = 'live' | 'demo';

/** Unit families we can compare across. */
export type UnitBasis = 'kg' | 'l' | 'piece' | 'unknown';

export interface Location {
  /** 6-digit Indian PIN code. */
  pincode: string;
  latitude?: number;
  longitude?: number;
}

/**
 * The normalised shape every provider returns. Providers are responsible for
 * mapping their own payloads into this; downstream code never sees a
 * store-specific field.
 */
export interface ProductResult {
  id: string;
  provider: Provider;
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
  availability: Availability;
  deliveryTimeMinutes?: number;
  deliveryFee?: number;
  rating?: number;
  reviewCount?: number;
  fetchedAt: Date;
}

/**
 * A `ProductResult` after `services/priceNormalizer` has run: pack size parsed
 * out of the title where the provider did not supply it, and a comparable
 * price expressed per kilogram / litre / piece.
 */
export interface NormalizedProduct extends ProductResult {
  /** Price expressed per `unitBasis`, e.g. 50 for "₹50/kg". */
  normalizedPricePerUnit?: number;
  unitBasis: UnitBasis;
  /** Human-readable form of the above, e.g. "₹50/kg". */
  unitPriceLabel?: string;
  /** Grouping signature from `services/productMatcher`. */
  matchKey: string;
  /** Whether this row is real data or sample data. */
  dataSource: DataSource;
}

export interface ProviderSearchContext {
  query: string;
  location: Location;
  /** Hard deadline for the provider call, in milliseconds. */
  timeoutMs: number;
  signal?: AbortSignal;
}

export interface ShoppingProvider {
  /** Stable machine id, also the enum value stored in the database. */
  readonly id: Provider;
  /** Display name shown in the UI, e.g. "Swiggy Instamart". */
  readonly name: string;
  readonly websiteUrl: string;
  /** Tailwind-friendly brand colour used for the text badge fallback. */
  readonly brandColor: string;

  /**
   * `live` once the provider's credentials are configured and its authorised
   * API is wired up; `demo` otherwise. Drives the LIVE / DEMO badge.
   */
  dataSource(): DataSource;

  /**
   * Human-readable explanation of what is missing for this provider to go
   * live. Shown in the UI and in `/api/providers`.
   */
  liveRequirement(): string;

  search(query: string, location: Location, options?: Partial<ProviderSearchContext>): Promise<ProductResult[]>;
}

/** Outcome of a single provider within one search — success or failure. */
export type ProviderOutcome =
  | {
      provider: Provider;
      name: string;
      status: 'ok';
      dataSource: DataSource;
      productsFound: number;
      durationMs: number;
    }
  | {
      provider: Provider;
      name: string;
      status: 'error' | 'timeout' | 'disabled';
      dataSource: DataSource;
      productsFound: 0;
      durationMs: number;
      /** Safe, user-facing message. Never contains credentials or stack data. */
      message: string;
    };

export function isProviderId(value: unknown): value is Provider {
  return typeof value === 'string' && (PROVIDER_IDS as readonly string[]).includes(value);
}
