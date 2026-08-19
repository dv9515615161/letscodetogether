/** GET /api/health — liveness probe for deployment checks. */

import { NextResponse } from 'next/server';
import { config } from '@/lib/env';
import { getCacheStore } from '@/services/cacheService';
import { describeProviders } from '@/providers/registry';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET() {
  return NextResponse.json({
    status: 'ok',
    time: new Date().toISOString(),
    database: config.hasDatabase ? 'configured' : 'not configured',
    cache: getCacheStore().kind,
    providersEnabled: describeProviders().filter((provider) => provider.enabled).length,
  });
}
