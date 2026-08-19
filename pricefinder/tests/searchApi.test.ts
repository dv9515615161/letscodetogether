/**
 * Exercises the route handlers directly. Next's route handlers are plain
 * functions over the Fetch API, so they can be called without a server.
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { GET, POST } from '@/app/api/search/route';
import { GET as PROVIDERS_GET } from '@/app/api/providers/route';
import { GET as HEALTH_GET } from '@/app/api/health/route';
import { POST as BASKET_POST } from '@/app/api/basket/route';
import { resetRateLimits } from '@/lib/rateLimit';
import { MemoryCacheStore, setCacheStore } from '@/services/cacheService';
import { PROVIDER_IDS } from '@/providers/types';
import type { SearchResponse } from '@/types/search';

/** Each test gets its own client IP so rate-limit windows never overlap. */
let clientCounter = 0;

function get(url: string): Request {
  clientCounter += 1;
  return new Request(url, { headers: { 'x-forwarded-for': `10.0.0.${clientCounter}` } });
}

function post(url: string, body: unknown, ip?: string): Request {
  clientCounter += 1;
  return new Request(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-forwarded-for': ip ?? `10.0.0.${clientCounter}` },
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });
}

beforeEach(() => {
  resetRateLimits();
  setCacheStore(new MemoryCacheStore());
  for (const id of PROVIDER_IDS) delete process.env[`ENABLE_${id.toUpperCase()}`];
  delete process.env.DEMO_FAILING_PROVIDERS;
});

afterEach(() => {
  resetRateLimits();
  setCacheStore(null);
  delete process.env.RATE_LIMIT_MAX;
  delete process.env.DEMO_FAILING_PROVIDERS;
});

describe('GET /api/search', () => {
  it('answers with the documented response shape', async () => {
    const response = await GET(get('http://localhost/api/search?q=tomato&pincode=500001'));
    expect(response.status).toBe(200);

    const body = (await response.json()) as SearchResponse;
    expect(body.query).toBe('tomato');
    expect(body.location).toMatchObject({ pincode: '500001' });
    expect(Array.isArray(body.results)).toBe(true);
    expect(body.summary.providersSearched).toBe(PROVIDER_IDS.length);
    expect(body.summary.productsFound).toBeGreaterThan(0);
    expect(body.summary.lowestPrice).toBeGreaterThan(0);
  });

  it('sets rate-limit and cache headers', async () => {
    const response = await GET(get('http://localhost/api/search?q=tomato'));
    expect(response.headers.get('X-RateLimit-Limit')).toBeTruthy();
    expect(response.headers.get('Cache-Control')).toContain('s-maxage');
  });

  it('defaults the PIN code when none is given', async () => {
    const response = await GET(get('http://localhost/api/search?q=milk'));
    const body = (await response.json()) as SearchResponse;
    expect(body.location.pincode).toBe('500001');
  });

  it('rejects a missing or invalid query with field-level detail', async () => {
    const missing = await GET(get('http://localhost/api/search'));
    expect(missing.status).toBe(400);

    const bad = await GET(get('http://localhost/api/search?q=tomato&pincode=1'));
    expect(bad.status).toBe(400);
    const body = (await bad.json()) as { error: string; details?: Record<string, string> };
    expect(body.error).toMatch(/invalid/i);
    expect(body.details?.pincode).toBeTruthy();
  });

  it('reports per-provider failures without failing the request', async () => {
    process.env.DEMO_FAILING_PROVIDERS = 'zepto';
    const response = await GET(get('http://localhost/api/search?q=tomato'));
    expect(response.status).toBe(200);

    const body = (await response.json()) as SearchResponse;
    const zepto = body.providers.find((provider) => provider.provider === 'zepto');
    expect(zepto?.status).not.toBe('ok');
    expect(body.summary.providersSucceeded).toBe(PROVIDER_IDS.length - 1);
    expect(body.results.length).toBeGreaterThan(0);
  });

  it('marks demo results as demo', async () => {
    const response = await GET(get('http://localhost/api/search?q=tomato'));
    const body = (await response.json()) as SearchResponse;
    expect(body.results.flatMap((group) => group.offers).every((offer) => offer.dataSource === 'demo')).toBe(true);
  });

  it('rate limits a client that searches too fast', async () => {
    process.env.RATE_LIMIT_MAX = '2';
    const ip = '10.9.9.9';
    const request = () =>
      GET(new Request('http://localhost/api/search?q=tomato', { headers: { 'x-forwarded-for': ip } }));

    expect((await request()).status).toBe(200);
    expect((await request()).status).toBe(200);

    const limited = await request();
    expect(limited.status).toBe(429);
    expect(limited.headers.get('Retry-After')).toBeTruthy();
  });
});

describe('POST /api/search', () => {
  it('accepts a JSON body', async () => {
    const response = await POST(post('http://localhost/api/search', { q: 'milk', pincode: '110001' }));
    expect(response.status).toBe(200);
    const body = (await response.json()) as SearchResponse;
    expect(body.query).toBe('milk');
    expect(body.location.pincode).toBe('110001');
  });

  it('rejects a malformed body', async () => {
    const response = await POST(post('http://localhost/api/search', 'not json'));
    expect(response.status).toBe(400);
  });
});

describe('GET /api/providers', () => {
  it('lists every store with its live/demo status', async () => {
    const response = await PROVIDERS_GET();
    const body = (await response.json()) as {
      providers: Array<{ id: string; dataSource: string; liveRequirement: string }>;
      summary: { total: number; demo: number };
    };

    expect(body.providers).toHaveLength(PROVIDER_IDS.length);
    expect(body.summary.total).toBe(PROVIDER_IDS.length);
    for (const provider of body.providers) {
      expect(['live', 'demo']).toContain(provider.dataSource);
      expect(provider.liveRequirement).toBeTruthy();
    }
  });

  it('never exposes credential values', async () => {
    process.env.FLIPKART_AFFILIATE_TOKEN = 'super-secret-token';
    try {
      const body = await (await PROVIDERS_GET()).text();
      expect(body).not.toContain('super-secret-token');
    } finally {
      delete process.env.FLIPKART_AFFILIATE_TOKEN;
    }
  });
});

describe('GET /api/health', () => {
  it('reports service status', async () => {
    const body = (await (await HEALTH_GET()).json()) as { status: string; cache: string };
    expect(body.status).toBe('ok');
    expect(['memory', 'database']).toContain(body.cache);
  });
});

describe('POST /api/basket', () => {
  it('totals a shopping list per store and names the cheapest', async () => {
    const response = await BASKET_POST(
      post('http://localhost/api/basket', { items: ['tomato 1kg', 'milk 1L'], pincode: '500001' }),
    );
    expect(response.status).toBe(200);

    const body = (await response.json()) as {
      providers: Array<{ providerName: string; total: number; itemsFound: number; itemsMissing: number }>;
      cheapest?: { providerName: string; total: number };
    };

    expect(body.providers.length).toBeGreaterThan(0);
    expect(body.cheapest?.total).toBeGreaterThan(0);

    // The named cheapest must really be the cheapest among the complete baskets.
    const complete = body.providers.filter((provider) => provider.itemsMissing === 0);
    if (complete.length > 0) {
      const min = Math.min(...complete.map((provider) => provider.total));
      expect(body.cheapest?.total).toBe(min);
    }
  });

  it('rejects an empty basket', async () => {
    const response = await BASKET_POST(post('http://localhost/api/basket', { items: [] }));
    expect(response.status).toBe(400);
  });
});
