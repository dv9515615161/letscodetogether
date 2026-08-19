import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BaseProvider, ProviderTimeoutError, ProviderUnavailableError, withTimeout } from '@/providers/base';
import { describeProviders, getEnabledProviders, getProvider } from '@/providers/registry';
import { PROVIDER_IDS, isProviderId, type Location, type Provider } from '@/providers/types';

const location: Location = { pincode: '500001' };

const ENV_KEYS = [
  ...PROVIDER_IDS.map((id) => `ENABLE_${id.toUpperCase()}`),
  'DEMO_FAILING_PROVIDERS',
  'AMAZON_PAAPI_ACCESS_KEY',
  'AMAZON_PAAPI_SECRET_KEY',
  'AMAZON_PARTNER_TAG',
  'FLIPKART_AFFILIATE_ID',
  'FLIPKART_AFFILIATE_TOKEN',
];

let saved: Record<string, string | undefined> = {};

beforeEach(() => {
  saved = Object.fromEntries(ENV_KEYS.map((key) => [key, process.env[key]]));
});

afterEach(() => {
  for (const [key, value] of Object.entries(saved)) {
    if (value === undefined) delete process.env[key];
    else process.env[key] = value;
  }
});

describe('provider registry', () => {
  it('registers all six target stores', () => {
    expect(describeProviders().map((provider) => provider.id).sort()).toEqual([...PROVIDER_IDS].sort());
  });

  it('enables every provider by default', () => {
    for (const id of PROVIDER_IDS) delete process.env[`ENABLE_${id.toUpperCase()}`];
    expect(getEnabledProviders()).toHaveLength(PROVIDER_IDS.length);
  });

  it('lets a single store be switched off by environment variable', () => {
    process.env.ENABLE_AMAZON = 'false';
    const enabled = getEnabledProviders().map((provider) => provider.id);
    expect(enabled).not.toContain('amazon');
    expect(enabled).toContain('zepto');
  });

  it('can be narrowed to a caller-supplied subset', () => {
    const enabled = getEnabledProviders(['zepto', 'blinkit']).map((provider) => provider.id);
    expect(enabled.sort()).toEqual(['blinkit', 'zepto']);
  });

  it('validates provider ids', () => {
    expect(isProviderId('zepto')).toBe(true);
    expect(isProviderId('bigbazaar')).toBe(false);
  });
});

describe('live vs demo labelling', () => {
  it('reports demo while credentials are missing', () => {
    delete process.env.AMAZON_PAAPI_ACCESS_KEY;
    delete process.env.AMAZON_PAAPI_SECRET_KEY;
    delete process.env.AMAZON_PARTNER_TAG;
    expect(getProvider('amazon')!.dataSource()).toBe('demo');
  });

  it('flips to live once every required credential is present', () => {
    process.env.AMAZON_PAAPI_ACCESS_KEY = 'test-access';
    process.env.AMAZON_PAAPI_SECRET_KEY = 'test-secret';
    process.env.AMAZON_PARTNER_TAG = 'test-tag';
    expect(getProvider('amazon')!.dataSource()).toBe('live');
  });

  it('stays demo when only some credentials are set', () => {
    process.env.FLIPKART_AFFILIATE_ID = 'id-only';
    delete process.env.FLIPKART_AFFILIATE_TOKEN;
    expect(getProvider('flipkart')!.dataSource()).toBe('demo');
  });

  it('explains what live access needs, without leaking values', () => {
    for (const provider of describeProviders()) {
      expect(provider.liveRequirement.length).toBeGreaterThan(20);
      expect(provider.liveRequirement).not.toContain('test-secret');
    }
  });
});

describe('demo providers', () => {
  it('returns comparable results for a catalogue query', async () => {
    const zepto = getProvider('zepto')!;
    const results = await zepto.search('tomato', location);
    expect(results.length).toBeGreaterThan(0);
    for (const result of results) {
      expect(result.provider).toBe('zepto');
      expect(result.price).toBeGreaterThan(0);
      expect(result.currency).toBe('INR');
    }
  });

  it('is deterministic for the same query and PIN code', async () => {
    const blinkit = getProvider('blinkit')!;
    const first = await blinkit.search('milk', location);
    const second = await blinkit.search('milk', location);
    expect(first.map((entry) => entry.price)).toEqual(second.map((entry) => entry.price));
  });

  it('prices the same product differently across stores', async () => {
    const [zepto, blinkit] = [getProvider('zepto')!, getProvider('blinkit')!];
    const [zeptoResults, blinkitResults] = await Promise.all([
      zepto.search('tomato', location),
      blinkit.search('tomato', location),
    ]);
    expect(zeptoResults[0]!.price).not.toBe(blinkitResults[0]!.price);
  });

  it('returns nothing when a store does not carry the category', async () => {
    // Grocery-only stores have no iPhones; that is a real empty result, not an error.
    await expect(getProvider('blinkit')!.search('iPhone 16', location)).resolves.toEqual([]);
    await expect(getProvider('amazon')!.search('iPhone 16', location)).resolves.not.toEqual([]);
  });
});

describe('provider failures', () => {
  it('surfaces a configured demo failure as an error', async () => {
    process.env.DEMO_FAILING_PROVIDERS = 'zepto';
    await expect(getProvider('zepto')!.search('tomato', location)).rejects.toBeInstanceOf(ProviderUnavailableError);
    // Everyone else keeps working.
    await expect(getProvider('blinkit')!.search('tomato', location)).resolves.not.toHaveLength(0);
  });
});

describe('withTimeout', () => {
  it('passes a fast result through', async () => {
    await expect(withTimeout(Promise.resolve('ok'), 50, 'Test')).resolves.toBe('ok');
  });

  it('rejects when the provider is too slow', async () => {
    vi.useFakeTimers();
    try {
      const slow = new Promise((resolve) => setTimeout(resolve, 10_000));
      const raced = withTimeout(slow, 1_000, 'Slow Store');
      const assertion = expect(raced).rejects.toBeInstanceOf(ProviderTimeoutError);
      await vi.advanceTimersByTimeAsync(1_100);
      await assertion;
    } finally {
      vi.useRealTimers();
    }
  });

  it('propagates the original error unchanged', async () => {
    const boom = new Error('upstream exploded');
    await expect(withTimeout(Promise.reject(boom), 1_000, 'Test')).rejects.toBe(boom);
  });
});

describe('BaseProvider', () => {
  class UnwiredProvider extends BaseProvider {
    readonly id: Provider = 'zepto';
    readonly name = 'Unwired';
    readonly websiteUrl = 'https://example.invalid';
    readonly brandColor = '#000000';
    protected readonly requiredEnv = ['UNWIRED_TEST_KEY'] as const;
    liveRequirement() {
      return 'Set UNWIRED_TEST_KEY to enable the live client for this test provider.';
    }
  }

  it('refuses to fake live data when credentials exist but no client does', async () => {
    process.env.UNWIRED_TEST_KEY = 'present';
    const provider = new UnwiredProvider();
    expect(provider.dataSource()).toBe('live');
    await expect(provider.search('tomato', location)).rejects.toBeInstanceOf(ProviderUnavailableError);
    delete process.env.UNWIRED_TEST_KEY;
  });
});
