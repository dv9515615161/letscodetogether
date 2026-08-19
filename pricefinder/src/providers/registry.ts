/**
 * The provider registry — the one place that knows which stores exist.
 *
 * Adding a store is: write the adapter, import it, append it to `ALL_PROVIDERS`,
 * add `ENABLE_<ID>` to `.env.example`, and add its id to the `ProviderId` enum
 * in the Prisma schema. Nothing else in the app changes.
 */

import { isProviderEnabled } from '@/lib/env';
import { AmazonProvider } from '@/providers/amazon';
import { BigBasketProvider } from '@/providers/bigbasket';
import { BlinkitProvider } from '@/providers/blinkit';
import { FlipkartProvider } from '@/providers/flipkart';
import { InstamartProvider } from '@/providers/instamart';
import { ZeptoProvider } from '@/providers/zepto';
import type { DataSource, Provider, ShoppingProvider } from '@/providers/types';

export const ALL_PROVIDERS: readonly ShoppingProvider[] = [
  new BlinkitProvider(),
  new ZeptoProvider(),
  new InstamartProvider(),
  new BigBasketProvider(),
  new AmazonProvider(),
  new FlipkartProvider(),
];

const BY_ID = new Map<Provider, ShoppingProvider>(ALL_PROVIDERS.map((provider) => [provider.id, provider]));

export function getProvider(id: Provider): ShoppingProvider | undefined {
  return BY_ID.get(id);
}

/**
 * Providers switched on via `ENABLE_*`, optionally narrowed to a caller-supplied
 * subset (used by the `providers` query parameter on /api/search).
 */
export function getEnabledProviders(only?: readonly Provider[]): ShoppingProvider[] {
  return ALL_PROVIDERS.filter((provider) => {
    if (!isProviderEnabled(provider.id)) return false;
    if (only && only.length > 0 && !only.includes(provider.id)) return false;
    return true;
  });
}

export interface ProviderInfo {
  id: Provider;
  name: string;
  websiteUrl: string;
  brandColor: string;
  enabled: boolean;
  dataSource: DataSource;
  /** What is needed to move this provider from demo to live. */
  liveRequirement: string;
}

/** Metadata for the UI badges and the /api/providers endpoint. */
export function describeProviders(): ProviderInfo[] {
  return ALL_PROVIDERS.map((provider) => ({
    id: provider.id,
    name: provider.name,
    websiteUrl: provider.websiteUrl,
    brandColor: provider.brandColor,
    enabled: isProviderEnabled(provider.id),
    dataSource: provider.dataSource(),
    liveRequirement: provider.liveRequirement(),
  }));
}
