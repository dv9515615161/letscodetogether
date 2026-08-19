import { describe, expect, it } from 'vitest';
import { sortGroups, sortMetric, sortOffers } from '@/services/sorting';
import type { SearchOffer, SearchProductGroup } from '@/types/search';

function offer(partial: Partial<SearchOffer> & { id: string }): SearchOffer {
  return {
    provider: 'zepto',
    providerName: 'Zepto',
    title: 'Tomato 1 kg',
    price: 50,
    currency: 'INR',
    unitBasis: 'kg',
    availability: 'available',
    dataSource: 'demo',
    fetchedAt: '2026-01-01T00:00:00.000Z',
    ...partial,
  };
}

const zepto = offer({ id: 'z', provider: 'zepto', providerName: 'Zepto', price: 39, pricePerUnit: 39, deliveryTimeMinutes: 15, rating: 4.1 });
const blinkit = offer({ id: 'b', provider: 'blinkit', providerName: 'Blinkit', price: 42, pricePerUnit: 42, deliveryTimeMinutes: 12, rating: 4.5 });
const bigbasket = offer({ id: 'g', provider: 'bigbasket', providerName: 'BigBasket', price: 45, pricePerUnit: 45, deliveryTimeMinutes: 120, rating: 4.3 });
const amazon = offer({ id: 'a', provider: 'amazon', providerName: 'Amazon', price: 49, pricePerUnit: 49, deliveryTimeMinutes: 1440, rating: 3.9 });

const all = [bigbasket, amazon, zepto, blinkit];

describe('sortOffers', () => {
  it('orders by lowest price', () => {
    expect(sortOffers(all, 'price').map((entry) => entry.providerName)).toEqual([
      'Zepto',
      'Blinkit',
      'BigBasket',
      'Amazon',
    ]);
  });

  it('orders by lowest price per unit', () => {
    // A cheap small pack must not beat a better-value large one.
    const halfKilo = offer({ id: 'h', providerName: 'Instamart', provider: 'instamart', price: 25, pricePerUnit: 50 });
    const sorted = sortOffers([halfKilo, bigbasket], 'price_per_unit');
    expect(sorted[0]!.providerName).toBe('BigBasket');
  });

  it('orders by fastest delivery', () => {
    expect(sortOffers(all, 'delivery').map((entry) => entry.providerName)).toEqual([
      'Blinkit',
      'Zepto',
      'BigBasket',
      'Amazon',
    ]);
  });

  it('orders by highest rating', () => {
    expect(sortOffers(all, 'rating').map((entry) => entry.providerName)).toEqual([
      'Blinkit',
      'BigBasket',
      'Zepto',
      'Amazon',
    ]);
  });

  it('orders by store name', () => {
    expect(sortOffers(all, 'provider').map((entry) => entry.providerName)).toEqual([
      'Amazon',
      'BigBasket',
      'Blinkit',
      'Zepto',
    ]);
  });

  it('sinks out-of-stock offers whatever the sort', () => {
    const cheapButGone = offer({ id: 'x', providerName: 'Instamart', provider: 'instamart', price: 10, pricePerUnit: 10, availability: 'out_of_stock' });
    const sorted = sortOffers([cheapButGone, zepto], 'price');
    expect(sorted[0]!.providerName).toBe('Zepto');
    expect(sorted[1]!.availability).toBe('out_of_stock');
  });

  it('places offers with a missing metric last', () => {
    const noRating = offer({ id: 'n', providerName: 'Instamart', provider: 'instamart', rating: undefined });
    const sorted = sortOffers([noRating, blinkit], 'rating');
    expect(sorted[0]!.providerName).toBe('Blinkit');
  });

  it('does not mutate the input array', () => {
    const input = [...all];
    sortOffers(input, 'price');
    expect(input).toEqual(all);
  });
});

describe('sortMetric', () => {
  it('falls back to absolute price when there is no unit price', () => {
    expect(sortMetric(offer({ id: 'p', price: 120, pricePerUnit: undefined }), 'price_per_unit')).toBe(120);
  });
});

describe('sortGroups', () => {
  const groups: SearchProductGroup[] = [
    { matchKey: 'onion|kg', title: 'Onion', unitBasis: 'kg', offers: [offer({ id: 'o', price: 80, pricePerUnit: 80 })] },
    { matchKey: 'tomato|kg', title: 'Tomato', unitBasis: 'kg', offers: [bigbasket, zepto, blinkit] },
  ];

  it('leads with the group holding the best offer and marks it', () => {
    const sorted = sortGroups(groups, 'price');
    expect(sorted[0]!.title).toBe('Tomato');
    expect(sorted[0]!.bestOffer?.providerName).toBe('Zepto');
    expect(sorted[0]!.offers[0]!.providerName).toBe('Zepto');
  });

  it('picks a purchasable best offer over a cheaper sold-out one', () => {
    const soldOut = offer({ id: 's', providerName: 'Instamart', provider: 'instamart', price: 5, pricePerUnit: 5, availability: 'out_of_stock' });
    const sorted = sortGroups([{ matchKey: 'k', title: 'Tomato', unitBasis: 'kg', offers: [soldOut, zepto] }], 'price');
    expect(sorted[0]!.bestOffer?.providerName).toBe('Zepto');
  });

  it('handles an empty list', () => {
    expect(sortGroups([], 'price')).toEqual([]);
  });
});
