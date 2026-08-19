import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryCacheStore, buildSearchCacheKey, getCacheStore, setCacheStore } from '@/services/cacheService';

describe('MemoryCacheStore', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns what was stored', async () => {
    const cache = new MemoryCacheStore();
    await cache.set('k', { value: 1 }, 60);
    await expect(cache.get('k')).resolves.toEqual({ value: 1 });
  });

  it('misses on an unknown key', async () => {
    const cache = new MemoryCacheStore();
    await expect(cache.get('nope')).resolves.toBeNull();
  });

  it('expires an entry once its TTL has passed', async () => {
    const cache = new MemoryCacheStore();
    await cache.set('k', 'v', 300);

    vi.advanceTimersByTime(299_000);
    await expect(cache.get('k')).resolves.toBe('v');

    // The brief's rule: the same query within five minutes may reuse the result.
    vi.advanceTimersByTime(2_000);
    await expect(cache.get('k')).resolves.toBeNull();
  });

  it('ignores a non-positive TTL', async () => {
    const cache = new MemoryCacheStore();
    await cache.set('k', 'v', 0);
    await expect(cache.get('k')).resolves.toBeNull();
  });

  it('deletes and clears', async () => {
    const cache = new MemoryCacheStore();
    await cache.set('a', 1, 60);
    await cache.set('b', 2, 60);

    await cache.delete('a');
    await expect(cache.get('a')).resolves.toBeNull();
    await expect(cache.get('b')).resolves.toBe(2);

    await cache.clear();
    await expect(cache.get('b')).resolves.toBeNull();
  });

  it('evicts the least recently used entry when full', async () => {
    const cache = new MemoryCacheStore(2);
    await cache.set('a', 1, 60);
    await cache.set('b', 2, 60);
    // Touching "a" makes "b" the least recently used.
    await cache.get('a');
    await cache.set('c', 3, 60);

    await expect(cache.get('b')).resolves.toBeNull();
    await expect(cache.get('a')).resolves.toBe(1);
    await expect(cache.get('c')).resolves.toBe(3);
  });
});

describe('buildSearchCacheKey', () => {
  it('ignores case and surrounding whitespace', () => {
    const a = buildSearchCacheKey({ query: '  Tomato ', pincode: '500001', providers: ['zepto'] });
    const b = buildSearchCacheKey({ query: 'tomato', pincode: '500001', providers: ['zepto'] });
    expect(a).toBe(b);
  });

  it('separates different PIN codes', () => {
    const a = buildSearchCacheKey({ query: 'tomato', pincode: '500001', providers: ['zepto'] });
    const b = buildSearchCacheKey({ query: 'tomato', pincode: '110001', providers: ['zepto'] });
    expect(a).not.toBe(b);
  });

  it('separates different provider sets but not their order', () => {
    const ordered = buildSearchCacheKey({ query: 'tomato', pincode: '500001', providers: ['zepto', 'blinkit'] });
    const reversed = buildSearchCacheKey({ query: 'tomato', pincode: '500001', providers: ['blinkit', 'zepto'] });
    const fewer = buildSearchCacheKey({ query: 'tomato', pincode: '500001', providers: ['zepto'] });

    expect(ordered).toBe(reversed);
    expect(ordered).not.toBe(fewer);
  });
});

describe('getCacheStore', () => {
  afterEach(() => setCacheStore(null));

  it('falls back to memory when no database is configured', () => {
    setCacheStore(null);
    expect(getCacheStore().kind).toBe('memory');
  });

  it('can be swapped for a test double', () => {
    const store = new MemoryCacheStore();
    setCacheStore(store);
    expect(getCacheStore()).toBe(store);
  });
});
