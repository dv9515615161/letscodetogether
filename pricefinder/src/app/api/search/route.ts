/**
 * GET /api/search?q=tomato&pincode=500001
 *
 * Also accepts POST with a JSON body of the same shape, which is handy for
 * longer queries and for the future basket UI.
 */

import { NextResponse } from 'next/server';
import { config } from '@/lib/env';
import { logger, toSafeMessage } from '@/lib/logger';
import { checkRateLimit, clientKeyFromHeaders } from '@/lib/rateLimit';
import { formatIssues, parseSearchParams, searchParamsSchema } from '@/lib/validation';
import { search } from '@/services/searchService';
import type { ApiError, SearchResponse } from '@/types/search';

export const runtime = 'nodejs';
/** Results depend on query parameters, so the route is always dynamic. */
export const dynamic = 'force-dynamic';

function rateLimitResponse(retryAfterSeconds: number): NextResponse<ApiError> {
  return NextResponse.json(
    { error: 'Too many searches. Please wait a moment and try again.' },
    { status: 429, headers: { 'Retry-After': String(retryAfterSeconds) } },
  );
}

async function handle(request: Request, raw: unknown): Promise<NextResponse<SearchResponse | ApiError>> {
  const limit = checkRateLimit(
    `search:${clientKeyFromHeaders(request.headers)}`,
    config.rateLimitMax,
    config.rateLimitWindowSeconds,
  );
  if (!limit.allowed) return rateLimitResponse(limit.retryAfterSeconds);

  const parsed = searchParamsSchema.safeParse(raw);
  if (!parsed.success) {
    return NextResponse.json(
      { error: 'Invalid search request', details: formatIssues(parsed.error) },
      { status: 400 },
    );
  }

  const input = parsed.data;

  try {
    const response = await search({
      query: input.q,
      location: {
        pincode: input.pincode,
        latitude: input.latitude,
        longitude: input.longitude,
      },
      sort: input.sort,
      providers: input.providers,
      refresh: input.refresh,
    });

    return NextResponse.json(response, {
      headers: {
        'X-RateLimit-Limit': String(limit.limit),
        'X-RateLimit-Remaining': String(limit.remaining),
        // Cache freshness is owned by the cache service; browsers should not
        // hold a copy that outlives it.
        'Cache-Control': `public, max-age=0, s-maxage=${config.cacheTtlSeconds}, stale-while-revalidate=60`,
      },
    });
  } catch (error) {
    logger.error('search failed', { error: toSafeMessage(error) });
    return NextResponse.json({ error: 'Search failed. Please try again.' }, { status: 500 });
  }
}

export async function GET(request: Request) {
  const parsedUrl = new URL(request.url);
  const validated = parseSearchParams(parsedUrl.searchParams);
  if (!validated.success) {
    const limit = checkRateLimit(
      `search:${clientKeyFromHeaders(request.headers)}`,
      config.rateLimitMax,
      config.rateLimitWindowSeconds,
    );
    if (!limit.allowed) return rateLimitResponse(limit.retryAfterSeconds);
    return NextResponse.json(
      { error: 'Invalid search request', details: formatIssues(validated.error) },
      { status: 400 },
    );
  }

  const raw: Record<string, string> = {};
  for (const [key, value] of parsedUrl.searchParams.entries()) {
    if (value !== '') raw[key] = value;
  }
  return handle(request, raw);
}

export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: 'Request body must be valid JSON' }, { status: 400 });
  }
  return handle(request, body);
}
