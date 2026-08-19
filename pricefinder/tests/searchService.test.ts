import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryCacheStore, setCacheStore } from '@/services/cacheService';
import { search } from '@/services/searchService';
import { PROVIDER_IDS } from '@/providers/types';

const location = { pincode: '500001' };

function resetProviderEnv() {
  for (const id of PROVIDER_IDS) delete process.env[`ENABLE_${id.toUpperCase()}`];
  delete process.env.DEMO_FAILING_PROVIDERS;
}

beforeEach(() => {
  resetProviderEnv();
  setCacheStore(new MemoryCacheStore());
});

afterEach(() => {
  resetProviderEnv();
  setCacheStore(null);
});

describe('search', () => {
  it('queries every enabled provider and groups the results', async () => {
    const response = await search({ query: 'tomato', location, sort: 'price_per_unit' });

    expect(response.query).toBe('tomato');
    expect(response.location.pincode).toBe('500001');
    expect(response.summary.providersSearched).toBe(PROVIDER_IDS.length);
    expect(response.results.length).toBeGreaterThan(0);
    expect(response.summary.productsFound).toBeGreaterThan(0);

    // The lead group should carry offers from several different stores.
    const stores = new Set(response.results[0]!.offers.map((offer) => offer.provider));
    expect(stores.size).toBeGreaterThan(1);
  });

  it('reports a lowest price and the store offering it', async () => {
    const response = await search({ query: 'tomato', location, sort: 'price' });
    expect(response.summary.lowestPrice).toBeGreaterThan(0);
    expect(response.summary.bestProviderName).toBeTruthy();
    expect(response.summary.lowestPricePerUnitLabel).toMatch(/^₹/);
  });

  it('labels demo results so they are never mistaken for real prices', async () => {
    const response = await search({ query: 'tomato', location, sort: 'price' });
    const offers = response.results.flatMap((group) => group.offers);
    expect(offers.every((offer) => offer.dataSource === 'demo')).toBe(true);
    expect(response.summary.demoOnly).toBe(true);
  });

  it('keeps going when one provider fails', async () => {
    process.env.DEMO_FAILING_PROVIDERS = 'zepto,flipkart';
    const response = await search({ query: 'tomato', location, sort: 'price', refresh: true });

    const failed = response.providers.filter((provider) => provider.status !== 'ok');
    expect(failed.map((provider) => provider.provider).sort()).toEqual(['flipkart', 'zepto']);
    for (const provider of failed) {
      expect('message' in provider && provider.message).toMatch(/unavailable|too long/i);
    }

    // The other four still returned results.
    expect(response.summary.providersSucceeded).toBe(PROVIDER_IDS.length - 2);
    expect(response.results.length).toBeGreaterThan(0);
    const stores = new Set(response.results.flatMap((group) => group.offers).map((offer) => offer.provider));
    expect(stores.has('zepto')).toBe(false);
    expect(stores.has('blinkit')).toBe(true);
  });

  it('still answers when every provider fails', async () => {
    process.env.DEMO_FAILING_PROVIDERS = PROVIDER_IDS.join(',');
    const response = await search({ query: 'tomato', location, sort: 'price', refresh: true });

    expect(response.results).toEqual([]);
    expect(response.summary.productsFound).toBe(0);
    expect(response.summary.providersFailed).toBe(PROVIDER_IDS.length);
  });

  it('returns an empty result set when every provider is disabled', async () => {
    for (const id of PROVIDER_IDS) process.env[`ENABLE_${id.toUpperCase()}`] = 'false';
    const response = await search({ query: 'tomato', location, sort: 'price' });
    expect(response.providers).toEqual([]);
    expect(response.summary.providersSearched).toBe(0);
  });

  it('honours a provider subset', async () => {
    const response = await search({ query: 'tomato', location, sort: 'price', providers: ['zepto', 'blinkit'] });
    expect(response.summary.providersSearched).toBe(2);
    const stores = new Set(response.results.flatMap((group) => group.offers).map((offer) => offer.provider));
    expect([...stores].sort()).toEqual(['blinkit', 'zepto']);
  });

  it('runs providers concurrently rather than one after another', async () => {
    process.env.DEMO_MIN_LATENCY_MS = '60';
    process.env.DEMO_MAX_LATENCY_MS = '60';
    try {
      const startedAt = Date.now();
      await search({ query: 'tomato', location, sort: 'price', refresh: true });
      const elapsed = Date.now() - startedAt;
      // Six providers at 60 ms each would take 360 ms in sequence.
      expect(elapsed).toBeLessThan(250);
    } finally {
      process.env.DEMO_MIN_LATENCY_MS = '0';
      process.env.DEMO_MAX_LATENCY_MS = '0';
    }
  });

  it('finds a product that only the marketplaces carry', async () => {
    const response = await search({ query: 'iPhone 16', location, sort: 'price' });
    const stores = new Set(response.results.flatMap((group) => group.offers).map((offer) => offer.provider));
    expect([...stores].sort()).toEqual(['amazon', 'flipkart']);
    // No pack size to speak of, so it is compared on absolute price only.
    expect(response.results[0]!.offers[0]!.price).toBeGreaterThan(1000);
  });
});

describe('search caching', () => {
  it('serves a repeat query from cache', async () => {
    const first = await search({ query: 'tomato', location, sort: 'price' });
    expect(first.summary.cached).toBe(false);

    const second = await search({ query: 'tomato', location, sort: 'price' });
    expect(second.summary.cached).toBe(true);
    expect(second.summary.productsFound).toBe(first.summary.productsFound);
  });

  it('treats a different PIN code as a different search', async () => {
    await search({ query: 'tomato', location, sort: 'price' });
    const other = await search({ query: 'tomato', location: { pincode: '110001' }, sort: 'price' });
    expect(other.summary.cached).toBe(false);
  });

  it('re-sorts cached results without re-querying providers', async () => {
    await search({ query: 'tomato', location, sort: 'price' });
    const byDelivery = await search({ query: 'tomato', location, sort: 'delivery' });

    expect(byDelivery.summary.cached).toBe(true);
    expect(byDelivery.sort).toBe('delivery');
    // Out-of-stock offers sink to the bottom whatever the sort, so only the
    // purchasable ones are expected to be in ascending delivery order.
    const times = byDelivery.results[0]!.offers
      .filter((offer) => offer.availability !== 'out_of_stock')
      .map((offer) => offer.deliveryTimeMinutes ?? Infinity);
    expect(times.length).toBeGreaterThan(1);
    expect([...times].sort((a, b) => a - b)).toEqual(times);
  });

  it('skips the cache when a refresh is requested', async () => {
    await search({ query: 'tomato', location, sort: 'price' });
    const refreshed = await search({ query: 'tomato', location, sort: 'price', refresh: true });
    expect(refreshed.summary.cached).toBe(false);
  });

  it('expires cached results after the configured TTL', async () => {
    process.env.SEARCH_CACHE_TTL_SECONDS = '300';
    vi.useFakeTimers();
    try {
      await search({ query: 'tomato', location, sort: 'price' });
      vi.advanceTimersByTime(299_000);
      expect((await search({ query: 'tomato', location, sort: 'price' })).summary.cached).toBe(true);

      vi.advanceTimersByTime(2_000);
      expect((await search({ query: 'tomato', location, sort: 'price' })).summary.cached).toBe(false);
    } finally {
      vi.useRealTimers();
      delete process.env.SEARCH_CACHE_TTL_SECONDS;
    }
  });
});
