import { BaseProvider } from '@/providers/base';
import type { Provider } from '@/providers/types';

/**
 * Swiggy Instamart.
 *
 * STATUS: demo data.
 *
 * Swiggy's public developer surface covers partner/restaurant integrations, not
 * Instamart retail search. We do not call internal app endpoints.
 *
 * TODO(live): requires a Swiggy partner agreement covering Instamart catalogue
 * access. Set INSTAMART_API_BASE_URL and INSTAMART_API_KEY, then implement
 * `searchLive` against the documented endpoint.
 */
export class InstamartProvider extends BaseProvider {
  readonly id: Provider = 'instamart';
  readonly name = 'Swiggy Instamart';
  readonly websiteUrl = 'https://www.swiggy.com/instamart';
  readonly brandColor = '#FC8019';
  protected readonly requiredEnv = ['INSTAMART_API_BASE_URL', 'INSTAMART_API_KEY'] as const;

  liveRequirement(): string {
    return 'Needs a Swiggy Instamart partner catalogue agreement (INSTAMART_API_BASE_URL, INSTAMART_API_KEY).';
  }
}
