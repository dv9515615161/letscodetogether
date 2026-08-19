/**
 * Shared test environment.
 *
 * Demo latency is zeroed so the suite is fast, and no DATABASE_URL is set so
 * every test exercises the "no database" path — the same path a fresh clone
 * and a zero-config Vercel deploy take.
 */
process.env.DEMO_MIN_LATENCY_MS = '0';
process.env.DEMO_MAX_LATENCY_MS = '0';
delete process.env.DATABASE_URL;
