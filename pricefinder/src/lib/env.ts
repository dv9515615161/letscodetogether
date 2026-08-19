/**
 * Server-only environment access.
 *
 * Nothing in this file may be imported from a client component: it reads
 * secrets. Values are read lazily so that tests can mutate `process.env`
 * between cases, and so a missing variable never breaks the build.
 */

import { PROVIDER_IDS, type Provider } from '@/providers/types';

function readNumber(name: string, fallback: number): number {
  const raw = process.env[name];
  if (!raw) return fallback;
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function readBool(name: string, fallback: boolean): boolean {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(raw.trim().toLowerCase());
}

/** `ENABLE_BLINKIT` etc. Providers default to enabled when unset. */
export function isProviderEnabled(provider: Provider): boolean {
  return readBool(`ENABLE_${provider.toUpperCase()}`, true);
}

export function enabledProviderIds(): Provider[] {
  return PROVIDER_IDS.filter(isProviderEnabled);
}

export const config = {
  get databaseUrl(): string | undefined {
    return process.env.DATABASE_URL || undefined;
  },
  get hasDatabase(): boolean {
    return Boolean(process.env.DATABASE_URL);
  },
  get cacheTtlSeconds(): number {
    return readNumber('SEARCH_CACHE_TTL_SECONDS', 300);
  },
  get providerTimeoutMs(): number {
    return readNumber('PROVIDER_TIMEOUT_MS', 6000);
  },
  get rateLimitMax(): number {
    return readNumber('RATE_LIMIT_MAX', 30);
  },
  get rateLimitWindowSeconds(): number {
    return readNumber('RATE_LIMIT_WINDOW_SECONDS', 60);
  },
  /** Demo-only: force these providers to fail, to exercise error handling. */
  get demoFailingProviders(): Provider[] {
    const raw = process.env.DEMO_FAILING_PROVIDERS ?? '';
    return raw
      .split(',')
      .map((value) => value.trim().toLowerCase())
      .filter((value): value is Provider => (PROVIDER_IDS as readonly string[]).includes(value));
  },
  get demoMinLatencyMs(): number {
    return readNumber('DEMO_MIN_LATENCY_MS', 120);
  },
  get demoMaxLatencyMs(): number {
    return readNumber('DEMO_MAX_LATENCY_MS', 600);
  },
  get isProduction(): boolean {
    return process.env.NODE_ENV === 'production';
  },
};

/**
 * True when every named variable is present and non-empty. Used by providers to
 * decide whether they can run live. Deliberately does not log the values.
 */
export function hasCredentials(...names: string[]): boolean {
  return names.every((name) => Boolean(process.env[name]?.trim()));
}
