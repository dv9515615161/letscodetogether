import { BaseProvider } from '@/providers/base';
import type { Provider } from '@/providers/types';

/**
 * Zepto (quick commerce).
 *
 * STATUS: demo data.
 *
 * Zepto offers no public product-search API. We do not scrape the storefront or
 * call undocumented internal endpoints.
 *
 * TODO(live): requires a Zepto partner/marketplace API agreement (Kiranakart
 * Technologies Pvt. Ltd.). Set ZEPTO_API_BASE_URL and ZEPTO_API_KEY and
 * implement `searchLive` against the documented endpoint.
 */
export class ZeptoProvider extends BaseProvider {
  readonly id: Provider = 'zepto';
  readonly name = 'Zepto';
  readonly websiteUrl = 'https://www.zeptonow.com';
  readonly brandColor = '#7B2CBF';
  protected readonly requiredEnv = ['ZEPTO_API_BASE_URL', 'ZEPTO_API_KEY'] as const;

  liveRequirement(): string {
    return 'Needs a Zepto partner API agreement (ZEPTO_API_BASE_URL, ZEPTO_API_KEY). No public API exists today.';
  }
}
