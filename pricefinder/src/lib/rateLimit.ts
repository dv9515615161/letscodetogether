/**
 * Fixed-window rate limiting for the public API.
 *
 * In-process by design: it needs no external service, which keeps the MVP
 * deployable with zero configuration. The trade-off is that on a horizontally
 * scaled deployment each instance counts separately — see README > Limitations
 * for the Redis/Upstash upgrade path.
 */

interface Window {
  count: number;
  resetAt: number;
}

const windows = new Map<string, Window>();

export interface RateLimitResult {
  allowed: boolean;
  limit: number;
  remaining: number;
  /** Unix epoch milliseconds at which the current window resets. */
  resetAt: number;
  retryAfterSeconds: number;
}

export function checkRateLimit(key: string, limit: number, windowSeconds: number): RateLimitResult {
  const now = Date.now();
  const existing = windows.get(key);

  if (!existing || existing.resetAt <= now) {
    const resetAt = now + windowSeconds * 1000;
    windows.set(key, { count: 1, resetAt });
    // Opportunistic cleanup so the map cannot grow without bound on a long-lived server.
    if (windows.size > 5000) {
      for (const [candidate, window] of windows) {
        if (window.resetAt <= now) windows.delete(candidate);
      }
    }
    return { allowed: true, limit, remaining: limit - 1, resetAt, retryAfterSeconds: 0 };
  }

  existing.count += 1;
  return {
    allowed: existing.count <= limit,
    limit,
    remaining: Math.max(0, limit - existing.count),
    resetAt: existing.resetAt,
    retryAfterSeconds: Math.max(1, Math.ceil((existing.resetAt - now) / 1000)),
  };
}

export function resetRateLimits(): void {
  windows.clear();
}

/**
 * Best-effort client identity. Behind Vercel's proxy `x-forwarded-for` is
 * trustworthy; elsewhere it is a hint, which is acceptable for a courtesy limit
 * on an unauthenticated, read-only endpoint.
 */
export function clientKeyFromHeaders(headers: Headers): string {
  const forwarded = headers.get('x-forwarded-for');
  if (forwarded) return forwarded.split(',')[0]!.trim();
  return headers.get('x-real-ip')?.trim() || 'anonymous';
}
