import { BaseProvider } from '@/providers/base';
import type { Provider } from '@/providers/types';

/**
 * Blinkit (Zomato-owned quick commerce).
 *
 * STATUS: demo data.
 *
 * Blinkit publishes no public product-search API for third parties, and its
 * storefront is protected by access controls that we do not attempt to work
 * around — no scraping, no private endpoints, no bot-protection evasion.
 *
 * TODO(live): requires a commercial catalogue/partner agreement with Blinkit
 * (Blink Commerce Pvt. Ltd.) granting documented API access. Once granted, set
 * BLINKIT_API_BASE_URL and BLINKIT_API_KEY, then implement `searchLive` below
 * against the endpoint they document. Nothing else needs to change.
 */
export class BlinkitProvider extends BaseProvider {
  readonly id: Provider = 'blinkit';
  readonly name = 'Blinkit';
  readonly websiteUrl = 'https://blinkit.com';
  readonly brandColor = '#F8CB46';
  protected readonly requiredEnv = ['BLINKIT_API_BASE_URL', 'BLINKIT_API_KEY'] as const;

  liveRequirement(): string {
    return 'Needs a Blinkit partner/catalogue API agreement (BLINKIT_API_BASE_URL, BLINKIT_API_KEY). No public API exists today.';
  }
}
