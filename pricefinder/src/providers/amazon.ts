import { createHash, createHmac } from 'node:crypto';
import { BaseProvider, ProviderUnavailableError } from '@/providers/base';
import type { Location, ProductResult, Provider } from '@/providers/types';
import { parseQuantity } from '@/services/priceNormalizer';

/**
 * Amazon India via the official Product Advertising API v5.
 *
 * STATUS: live-capable. Runs live as soon as PA-API credentials are configured;
 * otherwise it serves demo data.
 *
 * PA-API v5 is Amazon's documented, authorised interface for exactly this use
 * case. It requires an Amazon Associates account that has been approved and has
 * made qualifying sales — Amazon revokes API keys for accounts that do not.
 * Docs: https://webservices.amazon.com/paapi5/documentation/
 *
 * TODO(live): set AMAZON_PAAPI_ACCESS_KEY, AMAZON_PAAPI_SECRET_KEY and
 * AMAZON_PARTNER_TAG (plus AMAZON_PAAPI_HOST / AMAZON_PAAPI_REGION for a
 * marketplace other than amazon.in). The request signing below follows the
 * AWS Signature V4 scheme PA-API mandates; it has not been exercised against a
 * live key in this repository.
 */

interface PaapiItem {
  ASIN?: string;
  DetailPageURL?: string;
  ItemInfo?: {
    Title?: { DisplayValue?: string };
    ByLineInfo?: { Brand?: { DisplayValue?: string } };
    Features?: { DisplayValues?: string[] };
  };
  Images?: { Primary?: { Medium?: { URL?: string } } };
  Offers?: {
    Listings?: Array<{
      Price?: { Amount?: number; Currency?: string };
      SavingBasis?: { Amount?: number };
      Availability?: { Type?: string };
    }>;
  };
  CustomerReviews?: { StarRating?: { Value?: number }; Count?: number };
}

const SERVICE = 'ProductAdvertisingAPI';
const TARGET = 'com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems';

function sha256Hex(value: string): string {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

function hmac(key: Buffer | string, value: string): Buffer {
  return createHmac('sha256', key).update(value, 'utf8').digest();
}

/** AWS Signature V4, as required by PA-API v5. */
function signRequest(options: {
  host: string;
  region: string;
  path: string;
  payload: string;
  accessKey: string;
  secretKey: string;
  now: Date;
}): Record<string, string> {
  const amzDate = `${options.now.toISOString().replace(/[:-]|\.\d{3}/g, '').slice(0, 15)}Z`;
  const dateStamp = amzDate.slice(0, 8);

  const headers: Record<string, string> = {
    'content-encoding': 'amz-1.0',
    'content-type': 'application/json; charset=utf-8',
    host: options.host,
    'x-amz-date': amzDate,
    'x-amz-target': TARGET,
  };

  const signedHeaders = Object.keys(headers).sort().join(';');
  const canonicalHeaders = Object.keys(headers)
    .sort()
    .map((name) => `${name}:${headers[name]}\n`)
    .join('');

  const canonicalRequest = [
    'POST',
    options.path,
    '',
    canonicalHeaders,
    signedHeaders,
    sha256Hex(options.payload),
  ].join('\n');

  const credentialScope = `${dateStamp}/${options.region}/${SERVICE}/aws4_request`;
  const stringToSign = [
    'AWS4-HMAC-SHA256',
    amzDate,
    credentialScope,
    sha256Hex(canonicalRequest),
  ].join('\n');

  const signingKey = hmac(
    hmac(hmac(hmac(`AWS4${options.secretKey}`, dateStamp), options.region), SERVICE),
    'aws4_request',
  );
  const signature = createHmac('sha256', signingKey).update(stringToSign, 'utf8').digest('hex');

  return {
    ...headers,
    Authorization: `AWS4-HMAC-SHA256 Credential=${options.accessKey}/${credentialScope}, SignedHeaders=${signedHeaders}, Signature=${signature}`,
  };
}

export class AmazonProvider extends BaseProvider {
  readonly id: Provider = 'amazon';
  readonly name = 'Amazon';
  readonly websiteUrl = 'https://www.amazon.in';
  readonly brandColor = '#FF9900';
  protected readonly requiredEnv = [
    'AMAZON_PAAPI_ACCESS_KEY',
    'AMAZON_PAAPI_SECRET_KEY',
    'AMAZON_PARTNER_TAG',
  ] as const;

  liveRequirement(): string {
    return 'Needs an approved Amazon Associates account with Product Advertising API v5 access (AMAZON_PAAPI_ACCESS_KEY, AMAZON_PAAPI_SECRET_KEY, AMAZON_PARTNER_TAG).';
  }

  protected async searchLive(query: string, _location: Location): Promise<ProductResult[]> {
    const accessKey = process.env.AMAZON_PAAPI_ACCESS_KEY!;
    const secretKey = process.env.AMAZON_PAAPI_SECRET_KEY!;
    const partnerTag = process.env.AMAZON_PARTNER_TAG!;
    const host = process.env.AMAZON_PAAPI_HOST?.trim() || 'webservices.amazon.in';
    const region = process.env.AMAZON_PAAPI_REGION?.trim() || 'eu-west-1';
    const path = '/paapi5/searchitems';

    const payload = JSON.stringify({
      Keywords: query,
      SearchIndex: 'All',
      ItemCount: 10,
      PartnerTag: partnerTag,
      PartnerType: 'Associates',
      Marketplace: host.replace('webservices.', 'www.'),
      Resources: [
        'ItemInfo.Title',
        'ItemInfo.ByLineInfo',
        'ItemInfo.Features',
        'Images.Primary.Medium',
        'Offers.Listings.Price',
        'Offers.Listings.SavingBasis',
        'Offers.Listings.Availability.Type',
        'CustomerReviews.StarRating',
        'CustomerReviews.Count',
      ],
    });

    const headers = signRequest({ host, region, path, payload, accessKey, secretKey, now: new Date() });

    const response = await fetch(`https://${host}${path}`, { method: 'POST', headers, body: payload });
    if (!response.ok) {
      // The body can echo request parameters, so it is not surfaced to users.
      throw new ProviderUnavailableError(`Amazon PA-API returned HTTP ${response.status}`);
    }

    const data = (await response.json()) as { SearchResult?: { Items?: PaapiItem[] } };
    const items = data.SearchResult?.Items ?? [];
    const fetchedAt = new Date();

    return items.flatMap((item): ProductResult[] => {
      const listing = item.Offers?.Listings?.[0];
      const price = listing?.Price?.Amount;
      const title = item.ItemInfo?.Title?.DisplayValue;
      if (price === undefined || !title) return [];

      const parsed = parseQuantity(title);
      return [
        {
          id: `amazon-${item.ASIN ?? title}`,
          provider: 'amazon',
          title,
          brand: item.ItemInfo?.ByLineInfo?.Brand?.DisplayValue,
          description: item.ItemInfo?.Features?.DisplayValues?.[0],
          imageUrl: item.Images?.Primary?.Medium?.URL,
          productUrl: item.DetailPageURL,
          price,
          originalPrice: listing?.SavingBasis?.Amount,
          currency: listing?.Price?.Currency ?? 'INR',
          quantity: parsed?.quantity,
          unit: parsed?.unit,
          availability: listing?.Availability?.Type === 'Now' ? 'available' : 'unknown',
          rating: item.CustomerReviews?.StarRating?.Value,
          reviewCount: item.CustomerReviews?.Count,
          fetchedAt,
        },
      ];
    });
  }
}
