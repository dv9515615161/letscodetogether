import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { checkRateLimit, clientKeyFromHeaders, resetRateLimits } from '@/lib/rateLimit';

beforeEach(() => resetRateLimits());
afterEach(() => {
  resetRateLimits();
  vi.useRealTimers();
});

describe('checkRateLimit', () => {
  it('allows requests up to the limit, then blocks', () => {
    expect(checkRateLimit('ip', 3, 60).allowed).toBe(true);
    expect(checkRateLimit('ip', 3, 60).allowed).toBe(true);
    expect(checkRateLimit('ip', 3, 60)).toMatchObject({ allowed: true, remaining: 0 });
    expect(checkRateLimit('ip', 3, 60)).toMatchObject({ allowed: false, retryAfterSeconds: expect.any(Number) });
  });

  it('counts each client separately', () => {
    checkRateLimit('a', 1, 60);
    expect(checkRateLimit('a', 1, 60).allowed).toBe(false);
    expect(checkRateLimit('b', 1, 60).allowed).toBe(true);
  });

  it('opens a fresh window once the old one expires', () => {
    vi.useFakeTimers();
    checkRateLimit('ip', 1, 60);
    expect(checkRateLimit('ip', 1, 60).allowed).toBe(false);

    vi.advanceTimersByTime(61_000);
    expect(checkRateLimit('ip', 1, 60).allowed).toBe(true);
  });
});

describe('clientKeyFromHeaders', () => {
  it('takes the first hop of x-forwarded-for', () => {
    const headers = new Headers({ 'x-forwarded-for': '203.0.113.5, 70.41.3.18' });
    expect(clientKeyFromHeaders(headers)).toBe('203.0.113.5');
  });

  it('falls back to x-real-ip, then to a shared bucket', () => {
    expect(clientKeyFromHeaders(new Headers({ 'x-real-ip': '198.51.100.9' }))).toBe('198.51.100.9');
    expect(clientKeyFromHeaders(new Headers())).toBe('anonymous');
  });
});
