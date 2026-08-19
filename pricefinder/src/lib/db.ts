/**
 * Prisma access.
 *
 * The database is optional. `getPrisma()` returns `null` when DATABASE_URL is
 * unset, and every caller is expected to handle that — the app must work with
 * no database at all, which is what makes a one-click Vercel deploy possible.
 */

import { PrismaClient } from '@prisma/client';
import { config } from '@/lib/env';
import { logger, toSafeMessage } from '@/lib/logger';

const globalForPrisma = globalThis as unknown as { prisma?: PrismaClient | null };

export function getPrisma(): PrismaClient | null {
  if (!config.hasDatabase) return null;
  if (globalForPrisma.prisma === undefined) {
    globalForPrisma.prisma = new PrismaClient({
      log: config.isProduction ? ['error'] : ['error', 'warn'],
    });
  }
  return globalForPrisma.prisma ?? null;
}

/**
 * Runs a database side effect that must never break the request. Persistence
 * and cross-instance caching are enhancements; a search still succeeds if the
 * database is down.
 */
export async function tryDb<T>(label: string, fn: (prisma: PrismaClient) => Promise<T>): Promise<T | null> {
  const prisma = getPrisma();
  if (!prisma) return null;
  try {
    return await fn(prisma);
  } catch (error) {
    logger.warn(`database operation failed: ${label}`, { error: toSafeMessage(error) });
    return null;
  }
}
