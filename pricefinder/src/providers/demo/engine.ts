/**
 * Turns the sample catalogue into per-provider offers.
 *
 * Output is deterministic: the same (provider, query, pincode) always produces
 * the same prices. That keeps the demo credible, makes cache behaviour testable,
 * and stops the comparison table from reshuffling on every keystroke.
 *
 * IMPORTANT: everything here is fabricated sample data, surfaced with
 * `dataSource: "demo"` and a visible "Demo data" badge.
 */

import type { Location, ProductResult, Provider } from '@/providers/types';
import { CATALOG, filterSkus, findCatalogItems, type CatalogCategory, type CatalogItem, type CatalogSku } from './catalog';

/** FNV-1a: small, fast, and stable across runs and platforms. */
function hashString(value: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash >>> 0;
}

/** Seeded PRNG so "random" jitter is reproducible. */
function mulberry32(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export interface DemoProviderProfile {
  id: Provider;
  /** Multiplier applied to catalogue reference prices. */
  priceFactor: number;
  /** Inclusive delivery estimate range, in minutes. */
  deliveryMinutes: [number, number];
  deliveryFee: number;
  /** Categories this store is treated as carrying. */
  categories: CatalogCategory[];
  ratingRange: [number, number];
  reviewRange: [number, number];
  /** Public search URL template; `{q}` is replaced with the encoded query. */
  searchUrlTemplate: string;
  /** Share of SKUs shown as out of stock, 0–1. */
  outOfStockRate: number;
}

const ALL_GROCERY: CatalogCategory[] = ['produce', 'dairy', 'staples', 'packaged', 'household'];

/**
 * Per-store personality. The price factors encode the rough real-world pattern
 * — quick commerce charges a small premium for speed, big-basket-style stores
 * compete on staples — but the numbers themselves are invented.
 */
export const DEMO_PROFILES: Record<Provider, DemoProviderProfile> = {
  blinkit: {
    id: 'blinkit',
    priceFactor: 1.02,
    deliveryMinutes: [8, 16],
    deliveryFee: 25,
    categories: ALL_GROCERY,
    ratingRange: [3.8, 4.7],
    reviewRange: [40, 3000],
    searchUrlTemplate: 'https://blinkit.com/s/?q={q}',
    outOfStockRate: 0.08,
  },
  zepto: {
    id: 'zepto',
    priceFactor: 0.97,
    deliveryMinutes: [7, 15],
    deliveryFee: 25,
    categories: ALL_GROCERY,
    ratingRange: [3.7, 4.8],
    reviewRange: [30, 2500],
    searchUrlTemplate: 'https://www.zeptonow.com/search?query={q}',
    outOfStockRate: 0.1,
  },
  instamart: {
    id: 'instamart',
    priceFactor: 1.01,
    deliveryMinutes: [10, 22],
    deliveryFee: 29,
    categories: ALL_GROCERY,
    ratingRange: [3.6, 4.6],
    reviewRange: [25, 1800],
    searchUrlTemplate: 'https://www.swiggy.com/instamart/search?custom_back=true&query={q}',
    outOfStockRate: 0.12,
  },
  bigbasket: {
    id: 'bigbasket',
    priceFactor: 0.95,
    deliveryMinutes: [60, 240],
    deliveryFee: 0,
    categories: ALL_GROCERY,
    ratingRange: [3.9, 4.7],
    reviewRange: [100, 9000],
    searchUrlTemplate: 'https://www.bigbasket.com/ps/?q={q}',
    outOfStockRate: 0.07,
  },
  amazon: {
    id: 'amazon',
    priceFactor: 1.06,
    deliveryMinutes: [720, 2880],
    deliveryFee: 0,
    categories: [...ALL_GROCERY, 'electronics'],
    ratingRange: [3.5, 4.6],
    reviewRange: [200, 90000],
    searchUrlTemplate: 'https://www.amazon.in/s?k={q}',
    outOfStockRate: 0.05,
  },
  flipkart: {
    id: 'flipkart',
    priceFactor: 1.04,
    deliveryMinutes: [1440, 4320],
    deliveryFee: 0,
    categories: [...ALL_GROCERY, 'electronics'],
    ratingRange: [3.4, 4.6],
    reviewRange: [150, 120000],
    searchUrlTemplate: 'https://www.flipkart.com/search?q={q}',
    outOfStockRate: 0.06,
  },
};

/** Placeholder artwork as an inline SVG, so no external image host is needed. */
function placeholderImage(label: string, slug: string): string {
  const hue = hashString(slug) % 360;
  const initials = label
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160" role="img" aria-label="${initials}"><rect width="160" height="160" rx="20" fill="hsl(${hue} 70% 92%)"/><text x="80" y="98" font-family="system-ui,sans-serif" font-size="58" font-weight="600" fill="hsl(${hue} 55% 35%)" text-anchor="middle">${initials}</text></svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function round(value: number, step: number): number {
  return Math.max(step, Math.round(value / step) * step);
}

function skuToResult(
  profile: DemoProviderProfile,
  item: CatalogItem,
  sku: CatalogSku,
  index: number,
  location: Location,
): ProductResult {
  const seed = hashString(`${profile.id}|${item.slug}|${sku.title}|${sku.quantity}${sku.unit}|${location.pincode}`);
  const random = mulberry32(seed);

  // ±6% store-to-store jitter on top of the store's base price factor.
  const jitter = 0.94 + random() * 0.12;
  const raw = sku.basePrice * profile.priceFactor * jitter;
  // Round to the nearest rupee below ₹1000, nearest ₹10 above — like real listings.
  const price = raw < 1000 ? round(raw, 1) : round(raw, 10);

  // Roughly a third of listings show a struck-through MRP.
  const hasDiscount = random() < 0.35;
  const originalPrice = hasDiscount ? round(price * (1.08 + random() * 0.25), price < 1000 ? 1 : 10) : undefined;

  const outOfStock = random() < profile.outOfStockRate;
  const [minDelivery, maxDelivery] = profile.deliveryMinutes;
  const deliveryTimeMinutes = Math.round(minDelivery + random() * (maxDelivery - minDelivery));

  const [minRating, maxRating] = profile.ratingRange;
  const rating = Math.round((minRating + random() * (maxRating - minRating)) * 10) / 10;
  const [minReviews, maxReviews] = profile.reviewRange;
  const reviewCount = Math.round(minReviews + random() * (maxReviews - minReviews));

  const unitLabel = sku.unit === 'piece' ? (sku.quantity === 1 ? 'piece' : 'pieces') : sku.unit;
  const title = `${sku.title} - ${sku.quantity} ${unitLabel}`;

  return {
    id: `${profile.id}-${item.slug}-${index}`,
    provider: profile.id,
    title,
    brand: sku.brand,
    description: sku.description,
    imageUrl: placeholderImage(sku.title, item.slug),
    // Demo mode cannot know a real SKU URL, so "Open" goes to the store's own
    // public search page for the query — an honest destination, not a fake link.
    productUrl: profile.searchUrlTemplate.replace('{q}', encodeURIComponent(sku.title)),
    price,
    originalPrice,
    currency: 'INR',
    quantity: sku.quantity,
    unit: sku.unit,
    availability: outOfStock ? 'out_of_stock' : 'available',
    deliveryTimeMinutes,
    deliveryFee: profile.deliveryFee,
    rating,
    reviewCount,
    fetchedAt: new Date(),
  };
}

/**
 * For queries the catalogue does not cover, produce one clearly generic
 * placeholder listing rather than an empty screen. Still demo data, still
 * badged as such.
 */
function syntheticResults(profile: DemoProviderProfile, query: string, location: Location): ProductResult[] {
  const seed = hashString(`${profile.id}|synthetic|${query}|${location.pincode}`);
  const random = mulberry32(seed);
  const title = query
    .split(/\s+/)
    .map((word) => (word ? word[0]!.toUpperCase() + word.slice(1) : word))
    .join(' ');
  const price = round(60 + random() * 240, 1);
  const [minDelivery, maxDelivery] = profile.deliveryMinutes;

  return [
    {
      id: `${profile.id}-synthetic-${hashString(query)}`,
      provider: profile.id,
      title: `${title} - 1 piece`,
      description: 'Sample listing generated for a query outside the demo catalogue.',
      imageUrl: placeholderImage(title, query),
      productUrl: profile.searchUrlTemplate.replace('{q}', encodeURIComponent(query)),
      price,
      currency: 'INR',
      quantity: 1,
      unit: 'piece',
      availability: 'unknown',
      deliveryTimeMinutes: Math.round(minDelivery + random() * (maxDelivery - minDelivery)),
      deliveryFee: profile.deliveryFee,
      fetchedAt: new Date(),
    },
  ];
}

/** Builds one provider's demo result set for a query. */
export function demoSearch(providerId: Provider, query: string, location: Location): ProductResult[] {
  const profile = DEMO_PROFILES[providerId];
  const items = findCatalogItems(query).filter((item) => profile.categories.includes(item.category));

  if (items.length === 0) {
    // The query matched the catalogue, but not in a category this store carries
    // (e.g. an iPhone at a grocery-only store) — that is a genuine "no results".
    const matchedAnywhere = findCatalogItems(query).length > 0;
    return matchedAnywhere ? [] : syntheticResults(profile, query, location);
  }

  const results: ProductResult[] = [];
  let index = 0;
  for (const item of items) {
    for (const sku of filterSkus(item, query)) {
      results.push(skuToResult(profile, item, sku, index, location));
      index += 1;
    }
  }
  return results;
}

/** Every catalogue slug — used by the quick-search chips and tests. */
export function catalogSlugs(): string[] {
  return CATALOG.map((item) => item.slug);
}
