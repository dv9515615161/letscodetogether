import { BaseProvider, ProviderUnavailableError } from '@/providers/base';
import type { Location, ProductResult, Provider } from '@/providers/types';
import { parseQuantity } from '@/services/priceNormalizer';

/**
 * Flipkart via the official Affiliate/Commerce API.
 *
 * STATUS: live-capable. Runs live as soon as affiliate credentials are
 * configured; otherwise it serves demo data.
 *
 * Flipkart's affiliate programme exposes a documented product-search endpoint
 * authenticated with an affiliate id and token. Availability of the programme
 * has varied over time and it is not open to everyone — check the current
 * terms before relying on it. Docs: https://affiliate.flipkart.com/api-docs/
 *
 * TODO(live): set FLIPKART_AFFILIATE_ID and FLIPKART_AFFILIATE_TOKEN. The
 * mapping below follows the documented response shape; it has not been
 * exercised against live credentials in this repository.
 */

interface FlipkartProduct {
  productBaseInfoV1?: {
    productId?: string;
    title?: string;
    productDescription?: string;
    imageUrls?: Record<string, string>;
    productUrl?: string;
    productBrand?: string;
    inStock?: boolean;
    maximumRetailPrice?: { amount?: number; currency?: string };
    flipkartSellingPrice?: { amount?: number; currency?: string };
  };
}

export class FlipkartProvider extends BaseProvider {
  readonly id: Provider = 'flipkart';
  readonly name = 'Flipkart';
  readonly websiteUrl = 'https://www.flipkart.com';
  readonly brandColor = '#2874F0';
  protected readonly requiredEnv = ['FLIPKART_AFFILIATE_ID', 'FLIPKART_AFFILIATE_TOKEN'] as const;

  liveRequirement(): string {
    return 'Needs an approved Flipkart affiliate account (FLIPKART_AFFILIATE_ID, FLIPKART_AFFILIATE_TOKEN).';
  }

  protected async searchLive(query: string, _location: Location): Promise<ProductResult[]> {
    const url = new URL('https://affiliate-api.flipkart.net/affiliate/1.0/search.json');
    url.searchParams.set('query', query);
    url.searchParams.set('resultCount', '10');

    const response = await fetch(url, {
      headers: {
        'Fk-Affiliate-Id': process.env.FLIPKART_AFFILIATE_ID!,
        'Fk-Affiliate-Token': process.env.FLIPKART_AFFILIATE_TOKEN!,
      },
    });
    if (!response.ok) {
      throw new ProviderUnavailableError(`Flipkart affiliate API returned HTTP ${response.status}`);
    }

    const data = (await response.json()) as { products?: FlipkartProduct[] };
    const fetchedAt = new Date();

    return (data.products ?? []).flatMap((entry): ProductResult[] => {
      const info = entry.productBaseInfoV1;
      const price = info?.flipkartSellingPrice?.amount;
      const title = info?.title;
      if (!info || price === undefined || !title) return [];

      const parsed = parseQuantity(title);
      return [
        {
          id: `flipkart-${info.productId ?? title}`,
          provider: 'flipkart',
          title,
          brand: info.productBrand,
          description: info.productDescription,
          imageUrl: info.imageUrls?.['200x200'] ?? Object.values(info.imageUrls ?? {})[0],
          productUrl: info.productUrl,
          price,
          originalPrice: info.maximumRetailPrice?.amount,
          currency: info.flipkartSellingPrice?.currency ?? 'INR',
          quantity: parsed?.quantity,
          unit: parsed?.unit,
          availability: info.inStock === undefined ? 'unknown' : info.inStock ? 'available' : 'out_of_stock',
          fetchedAt,
        },
      ];
    });
  }
}
