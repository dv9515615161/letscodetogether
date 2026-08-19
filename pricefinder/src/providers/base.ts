/**
 * Shared plumbing for provider adapters.
 *
 * A provider subclass declares which environment variables it needs to run
 * live. While those are missing it serves demo data and reports
 * `dataSource() === 'demo'`; once they are present, `searchLive` is called
 * instead. That single switch is what lets a store go live without any change
 * to the search pipeline or the UI.
 */

import { config, hasCredentials } from '@/lib/env';
import type { DataSource, Location, ProductResult, Provider, ShoppingProvider } from '@/providers/types';
import { demoSearch } from '@/providers/demo/engine';

export class ProviderTimeoutError extends Error {
  constructor(providerName: string, timeoutMs: number) {
    super(`${providerName} did not respond within ${timeoutMs}ms`);
    this.name = 'ProviderTimeoutError';
  }
}

export class ProviderUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ProviderUnavailableError';
  }
}

/** Rejects with `ProviderTimeoutError` if the promise outlives the deadline. */
export async function withTimeout<T>(promise: Promise<T>, timeoutMs: number, providerName: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<never>((_resolve, reject) => {
        timer = setTimeout(() => reject(new ProviderTimeoutError(providerName, timeoutMs)), timeoutMs);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export abstract class BaseProvider implements ShoppingProvider {
  abstract readonly id: Provider;
  abstract readonly name: string;
  abstract readonly websiteUrl: string;
  abstract readonly brandColor: string;

  /**
   * Environment variables that must all be set for this provider to run live.
   * An empty list means no live integration is wired up yet.
   */
  protected abstract readonly requiredEnv: readonly string[];

  abstract liveRequirement(): string;

  dataSource(): DataSource {
    return this.requiredEnv.length > 0 && hasCredentials(...this.requiredEnv) ? 'live' : 'demo';
  }

  /**
   * Overridden by providers with an authorised API. The base implementation is
   * never reached unless `requiredEnv` is satisfied.
   */
  protected async searchLive(_query: string, _location: Location): Promise<ProductResult[]> {
    throw new ProviderUnavailableError(
      `${this.name} has credentials configured but no live client is implemented yet`,
    );
  }

  /** Sample data, always tagged demo downstream. */
  protected async searchDemo(query: string, location: Location): Promise<ProductResult[]> {
    if (config.demoFailingProviders.includes(this.id)) {
      throw new ProviderUnavailableError(`${this.name} is unavailable right now`);
    }
    const min = config.demoMinLatencyMs;
    const max = Math.max(min, config.demoMaxLatencyMs);
    if (max > 0) {
      // A little latency so loading skeletons and timeouts behave realistically.
      await sleep(min + Math.random() * (max - min));
    }
    return demoSearch(this.id, query, location);
  }

  async search(query: string, location: Location, options?: { timeoutMs?: number }): Promise<ProductResult[]> {
    const timeoutMs = options?.timeoutMs ?? config.providerTimeoutMs;
    const work = this.dataSource() === 'live' ? this.searchLive(query, location) : this.searchDemo(query, location);
    return withTimeout(work, timeoutMs, this.name);
  }
}
