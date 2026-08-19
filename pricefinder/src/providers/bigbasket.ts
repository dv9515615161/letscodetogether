import { BaseProvider } from '@/providers/base';
import type { Provider } from '@/providers/types';

/**
 * BigBasket (Tata Digital).
 *
 * STATUS: demo data.
 *
 * BigBasket has run affiliate programmes through networks in the past, but
 * those provide deep links and commissions rather than a product-search API.
 * We do not scrape the site.
 *
 * TODO(live): either (a) a BigBasket/Tata Digital partner catalogue API, or
 * (b) an affiliate network product feed you are licensed to query. Set
 * BIGBASKET_API_BASE_URL and BIGBASKET_API_KEY, then implement `searchLive`.
 */
export class BigBasketProvider extends BaseProvider {
  readonly id: Provider = 'bigbasket';
  readonly name = 'BigBasket';
  readonly websiteUrl = 'https://www.bigbasket.com';
  readonly brandColor = '#84C225';
  protected readonly requiredEnv = ['BIGBASKET_API_BASE_URL', 'BIGBASKET_API_KEY'] as const;

  liveRequirement(): string {
    return 'Needs a BigBasket partner API or a licensed affiliate product feed (BIGBASKET_API_BASE_URL, BIGBASKET_API_KEY).';
  }
}
