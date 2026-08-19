/**
 * GET /api/providers — which stores exist, which are switched on, and whether
 * each is serving live or demo data.
 *
 * Deliberately exposes no credential values, only whether a provider is
 * configured, plus a plain-English note on what live access would require.
 */

import { NextResponse } from 'next/server';
import { describeProviders } from '@/providers/registry';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET() {
  const providers = describeProviders();
  return NextResponse.json({
    providers,
    summary: {
      total: providers.length,
      enabled: providers.filter((provider) => provider.enabled).length,
      live: providers.filter((provider) => provider.enabled && provider.dataSource === 'live').length,
      demo: providers.filter((provider) => provider.enabled && provider.dataSource === 'demo').length,
    },
  });
}
