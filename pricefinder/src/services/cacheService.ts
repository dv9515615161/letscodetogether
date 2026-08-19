/**
 * Cache abstraction with two interchangeable backends.
 *
 * - `MemoryCacheStore` — always available, per-process. Good enough for local
 *   dev and a single long-lived server.
 * - `DatabaseCacheStore` — used when DATABASE_URL is set, so serverless
 *   instances on Vercel share one cache instead of each warming its own.
 *
 * A Redis backend would slot in here by implementing `CacheStore`; see
 * README > Caching. Nothing outside this file knows which backend is live.
 */

import { tryDb } from '@/lib/db';
import { config } from '@/lib/env';

export interface CacheStore {
  readonly kind: 'memory' | 'database';
  get<T>(key: string): Promise<T | null>;
  set<T>(key: string, value: T, ttlSeconds: number): Promise<void>;
  delete(key: string): Promise<void>;
  clear(): Promise<void>;
}

interface MemoryEntry {
  value: unknown;
  expiresAt: number;
}

export class MemoryCacheStore implements CacheStore {
  readonly kind = 'memory' as const;
  /** Bounded so a long-running process cannot grow without limit. */
  private readonly maxEntries: number;
  private readonly entries = new Map<string, MemoryEntry>();

  constructor(maxEntries = 500) {
    this.maxEntries = maxEntries;
  }

  async get<T>(key: string): Promise<T | null> {
    const entry = this.entries.get(key);
    if (!entry) return null;
    if (entry.expiresAt <= Date.now()) {
      this.entries.delete(key);
      return null;
    }
    // Refresh recency for the LRU eviction below.
    this.entries.delete(key);
    this.entries.set(key, entry);
    return entry.value as T;
  }

  async set<T>(key: string, value: T, ttlSeconds: number): Promise<void> {
    if (ttlSeconds <= 0) return;
    if (this.entries.size >= this.maxEntries) {
      const oldest = this.entries.keys().next();
      if (!oldest.done) this.entries.delete(oldest.value);
    }
    this.entries.set(key, { value, expiresAt: Date.now() + ttlSeconds * 1000 });
  }

  async delete(key: string): Promise<void> {
    this.entries.delete(key);
  }

  async clear(): Promise<void> {
    this.entries.clear();
  }
}

export class DatabaseCacheStore implements CacheStore {
  readonly kind = 'database' as const;

  async get<T>(key: string): Promise<T | null> {
    const row = await tryDb('cache.get', (prisma) => prisma.cacheEntry.findUnique({ where: { key } }));
    if (!row) return null;
    if (row.expiresAt.getTime() <= Date.now()) {
      await this.delete(key);
      return null;
    }
    return row.value as T;
  }

  async set<T>(key: string, value: T, ttlSeconds: number): Promise<void> {
    if (ttlSeconds <= 0) return;
    const expiresAt = new Date(Date.now() + ttlSeconds * 1000);
    // Cast: Prisma's Json input type does not accept an arbitrary generic.
    const payload = value as never;
    await tryDb('cache.set', (prisma) =>
      prisma.cacheEntry.upsert({
        where: { key },
        create: { key, value: payload, expiresAt },
        update: { value: payload, expiresAt },
      }),
    );
  }

  async delete(key: string): Promise<void> {
    await tryDb('cache.delete', (prisma) => prisma.cacheEntry.deleteMany({ where: { key } }));
  }

  async clear(): Promise<void> {
    await tryDb('cache.clear', (prisma) => prisma.cacheEntry.deleteMany({}));
  }
}

let store: CacheStore | null = null;

export function getCacheStore(): CacheStore {
  if (!store) {
    store = config.hasDatabase ? new DatabaseCacheStore() : new MemoryCacheStore();
  }
  return store;
}

/** Test seam: swap the backend, or reset to the environment default. */
export function setCacheStore(next: CacheStore | null): void {
  store = next;
}

/**
 * Cache identity for a search. The enabled provider set is part of the key so
 * that toggling a provider does not serve a stale, differently-shaped result.
 */
export function buildSearchCacheKey(input: {
  query: string;
  pincode: string;
  providers: readonly string[];
}): string {
  const normalizedQuery = input.query.trim().toLowerCase().replace(/\s+/g, ' ');
  const providers = [...input.providers].sort().join(',');
  return `search:v1:${normalizedQuery}:${input.pincode}:${providers}`;
}
