/**
 * POST /api/basket — compare a whole shopping list across stores.
 *
 * Body: { "items": ["tomato 1kg", "milk 1L"], "pincode": "500001" }
 *
 * Not yet surfaced in the UI; exposed so the feature can be built on the
 * client alone. Rate limited more tightly than /api/search because one basket
 * request fans out to one search per line.
 */

import { NextResponse } from 'next/server';
import { config } from '@/lib/env';
import { logger, toSafeMessage } from '@/lib/logger';
import { checkRateLimit, clientKeyFromHeaders } from '@/lib/rateLimit';
import { basketRequestSchema, formatIssues } from '@/lib/validation';
import { compareBasket } from '@/services/basketService';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function POST(request: Request) {
  const limit = checkRateLimit(
    `basket:${clientKeyFromHeaders(request.headers)}`,
    Math.max(1, Math.floor(config.rateLimitMax / 3)),
    config.rateLimitWindowSeconds,
  );
  if (!limit.allowed) {
    return NextResponse.json(
      { error: 'Too many basket comparisons. Please wait a moment.' },
      { status: 429, headers: { 'Retry-After': String(limit.retryAfterSeconds) } },
    );
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: 'Request body must be valid JSON' }, { status: 400 });
  }

  const parsed = basketRequestSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json(
      { error: 'Invalid basket request', details: formatIssues(parsed.error) },
      { status: 400 },
    );
  }

  try {
    const comparison = await compareBasket({
      items: parsed.data.items,
      location: { pincode: parsed.data.pincode },
      providers: parsed.data.providers,
    });
    return NextResponse.json(comparison);
  } catch (error) {
    logger.error('basket comparison failed', { error: toSafeMessage(error) });
    return NextResponse.json({ error: 'Basket comparison failed. Please try again.' }, { status: 500 });
  }
}
