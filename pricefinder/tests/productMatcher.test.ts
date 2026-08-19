import { describe, expect, it } from 'vitest';
import { analyzeTitle, buildMatchKey, groupProducts, isSameProduct, singularize } from '@/services/productMatcher';
import { normalizeProduct } from '@/services/priceNormalizer';
import type { NormalizedProduct, Provider, ProductResult } from '@/providers/types';

function offer(provider: Provider, title: string, price: number): NormalizedProduct {
  const raw: ProductResult = {
    id: `${provider}-${title}`,
    provider,
    title,
    price,
    currency: 'INR',
    availability: 'available',
    fetchedAt: new Date('2026-01-01T00:00:00Z'),
  };
  return normalizeProduct(raw, { matchKey: buildMatchKey(title, 'kg'), dataSource: 'demo' });
}

describe('singularize', () => {
  it('folds common plurals', () => {
    expect(singularize('tomatoes')).toBe('tomato');
    expect(singularize('eggs')).toBe('egg');
    expect(singularize('berries')).toBe('berry');
    expect(singularize('boxes')).toBe('box');
  });

  it('leaves short words and false plurals alone', () => {
    expect(singularize('gas')).toBe('gas');
    expect(singularize('oil')).toBe('oil');
    expect(singularize('glass')).toBe('glass');
  });
});

describe('analyzeTitle', () => {
  it('strips filler, punctuation and pack sizes', () => {
    expect(analyzeTitle('Fresh Tomato - 1 KG').coreTokens).toEqual(['tomato']);
    expect(analyzeTitle('Tomatoes, Premium Quality (500 g)').coreTokens).toEqual(['tomato']);
  });

  it('keeps variant markers out of the core tokens', () => {
    const organic = analyzeTitle('Organic Tomato 500 g');
    expect(organic.coreTokens).toEqual(['tomato']);
    expect(organic.variantTags).toContain('organic');
  });

  it('treats multi-word variants as one tag', () => {
    const analysis = analyzeTitle('Amul Gold Full Cream Milk 1 L');
    expect(analysis.variantTags).toContain('full-cream');
    expect(analysis.coreTokens).toEqual(['amul', 'gold', 'milk']);
  });
});

describe('isSameProduct', () => {
  it('matches the same item written three different ways', () => {
    expect(isSameProduct('Tomato 1kg', 'Fresh Tomato 1 kg')).toBe(true);
    expect(isSameProduct('Tomato 1kg', 'Tomatoes - 1 KG')).toBe(true);
    expect(isSameProduct('Fresh Tomato 1 kg', 'Tomatoes - 1 KG')).toBe(true);
  });

  it('keeps genuinely different variants apart', () => {
    // The rule that must not regress: organic is not regular.
    expect(isSameProduct('Organic Tomato 1 kg', 'Tomato 1 kg')).toBe(false);
    expect(isSameProduct('Cherry Tomato 200 g', 'Tomato 200 g')).toBe(false);
    expect(isSameProduct('Tomato 1 kg', 'Tomato Ketchup 1 kg')).toBe(false);
    expect(isSameProduct('Amul Toned Milk 1 L', 'Amul Full Cream Milk 1 L')).toBe(false);
  });

  it('does not merge across unit families', () => {
    expect(isSameProduct('Milk 1 L', 'Milk 1 kg')).toBe(false);
  });
});

describe('model and capacity variants', () => {
  // Regression: numbers used to be stripped as size residue, which merged
  // every iPhone into one row and invented a "best price" across models.
  it('keeps different storage capacities apart', () => {
    expect(isSameProduct('Apple iPhone 16 (128 GB, Black)', 'Apple iPhone 16 (256 GB, Black)')).toBe(false);
  });

  it('keeps different models apart', () => {
    expect(isSameProduct('Apple iPhone 16 (128 GB)', 'Apple iPhone 16 Pro (128 GB)')).toBe(false);
    expect(isSameProduct('Apple iPhone 16 (128 GB)', 'Apple iPhone 15 (128 GB)')).toBe(false);
  });

  it('still matches the same model written differently', () => {
    expect(isSameProduct('Apple iPhone 16 - 128 GB', 'Apple iPhone 16 (128 GB)')).toBe(true);
  });
});

describe('groupProducts', () => {
  it('gathers one product across stores into a single row', () => {
    const groups = groupProducts([
      offer('zepto', 'Tomato - 1 kg', 39),
      offer('blinkit', 'Fresh Tomato 1 KG', 42),
      offer('bigbasket', 'Tomatoes - 1 kg', 45),
    ]);

    expect(groups).toHaveLength(1);
    expect(groups[0]!.offers).toHaveLength(3);
    expect(groups[0]!.title).toBe('Tomato');
  });

  it('splits variants into their own rows', () => {
    const groups = groupProducts([
      offer('zepto', 'Tomato 1 kg', 39),
      offer('blinkit', 'Organic Tomato 1 kg', 89),
    ]);

    expect(groups).toHaveLength(2);
    const titles = groups.map((group) => group.title).sort();
    expect(titles).toEqual(['Organic Tomato', 'Tomato']);
  });

  it('compares different pack sizes of one product in the same row', () => {
    const groups = groupProducts([offer('zepto', 'Tomato 1 kg', 45), offer('blinkit', 'Tomato 500 g', 25)]);
    expect(groups).toHaveLength(1);
    // 500 g at Rs 25 is Rs 50/kg, so the 1 kg pack is the better buy.
    const perUnit = groups[0]!.offers.map((entry) => entry.normalizedPricePerUnit);
    expect(perUnit).toContain(45);
    expect(perUnit).toContain(50);
  });

  it('returns nothing for an empty result set', () => {
    expect(groupProducts([])).toEqual([]);
  });
});
