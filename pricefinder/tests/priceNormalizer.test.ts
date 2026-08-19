import { describe, expect, it } from 'vitest';
import {
  basisLabel,
  canonicalUnit,
  computeUnitPrice,
  normalizeProduct,
  parseQuantity,
  toBasisAmount,
  unitBasisFor,
} from '@/services/priceNormalizer';
import type { ProductResult } from '@/providers/types';

describe('parseQuantity', () => {
  it('reads a simple weight', () => {
    expect(parseQuantity('Tomato 1 kg')).toEqual({ quantity: 1, unit: 'kg' });
    expect(parseQuantity('Tomato 500g')).toEqual({ quantity: 500, unit: 'g' });
  });

  it('accepts the many spellings stores use', () => {
    expect(parseQuantity('Amul Milk 1 ltr')).toEqual({ quantity: 1, unit: 'l' });
    expect(parseQuantity('Amul Milk 1 Litre')).toEqual({ quantity: 1, unit: 'l' });
    expect(parseQuantity('Oil 500 ML')).toEqual({ quantity: 500, unit: 'ml' });
    expect(parseQuantity('Atta 5 Kilograms')).toEqual({ quantity: 5, unit: 'kg' });
  });

  it('reads counts and packs as pieces', () => {
    expect(parseQuantity('Eggs 12 pcs')).toEqual({ quantity: 12, unit: 'piece' });
    expect(parseQuantity('Soap - Pack of 4')).toEqual({ quantity: 4, unit: 'piece' });
    expect(parseQuantity('Farm Eggs 1 dozen')).toEqual({ quantity: 12, unit: 'piece' });
  });

  it('multiplies out a multipack', () => {
    expect(parseQuantity('Milk 6 x 200 ml')).toEqual({ quantity: 1200, unit: 'ml' });
    expect(parseQuantity('Biscuits 4 X 100 g')).toEqual({ quantity: 400, unit: 'g' });
  });

  it('returns nothing when there is no pack size', () => {
    expect(parseQuantity('Apple iPhone 16 Pro')).toBeUndefined();
    expect(parseQuantity('')).toBeUndefined();
    expect(parseQuantity(undefined)).toBeUndefined();
  });
});

describe('unit conversion', () => {
  it('maps aliases onto canonical units', () => {
    expect(canonicalUnit('KG')).toBe('kg');
    expect(canonicalUnit('gms')).toBe('g');
    expect(canonicalUnit('Litres')).toBe('l');
    expect(canonicalUnit('pcs')).toBe('piece');
    expect(canonicalUnit('nonsense')).toBeUndefined();
  });

  it('groups units into comparable bases', () => {
    expect(unitBasisFor('g')).toBe('kg');
    expect(unitBasisFor('ml')).toBe('l');
    expect(unitBasisFor('piece')).toBe('piece');
    expect(unitBasisFor('parsec')).toBe('unknown');
  });

  it('converts amounts into the base unit', () => {
    expect(toBasisAmount(500, 'g')).toBe(0.5);
    expect(toBasisAmount(1, 'kg')).toBe(1);
    expect(toBasisAmount(250, 'ml')).toBe(0.25);
    expect(toBasisAmount(2, 'l')).toBe(2);
    expect(toBasisAmount(6, 'piece')).toBe(6);
  });

  it('labels each base the way a shopper writes it', () => {
    expect(basisLabel('kg')).toBe('kg');
    expect(basisLabel('l')).toBe('L');
    expect(basisLabel('piece')).toBe('piece');
  });
});

describe('computeUnitPrice', () => {
  // The two worked examples from the product brief.
  it('turns 500 g for Rs 25 into Rs 50/kg', () => {
    const result = computeUnitPrice(25, 500, 'g');
    expect(result).toMatchObject({ basis: 'kg', value: 50 });
    expect(result?.label).toBe('₹50/kg');
  });

  it('turns 1 L for Rs 60 into Rs 60/L', () => {
    const result = computeUnitPrice(60, 1, 'l');
    expect(result).toMatchObject({ basis: 'l', value: 60 });
    expect(result?.label).toBe('₹60/L');
  });

  it('prices a pack per piece', () => {
    expect(computeUnitPrice(92, 12, 'piece')).toMatchObject({ basis: 'piece', value: 7.67 });
  });

  it('makes different pack sizes directly comparable', () => {
    const half = computeUnitPrice(25, 500, 'g');
    const full = computeUnitPrice(45, 1, 'kg');
    // Rs 25 for 500 g (Rs 50/kg) is dearer than Rs 45 for 1 kg.
    expect(half!.value).toBeGreaterThan(full!.value);
  });

  it('refuses to guess without a usable pack size', () => {
    expect(computeUnitPrice(25, undefined, 'g')).toBeUndefined();
    expect(computeUnitPrice(25, 0, 'g')).toBeUndefined();
    expect(computeUnitPrice(25, 500, 'parsec')).toBeUndefined();
    expect(computeUnitPrice(Number.NaN, 500, 'g')).toBeUndefined();
  });
});

describe('normalizeProduct', () => {
  const base: ProductResult = {
    id: 'x',
    provider: 'zepto',
    title: 'Fresh Tomato - 500 g',
    price: 25,
    currency: 'INR',
    availability: 'available',
    fetchedAt: new Date('2026-01-01T00:00:00Z'),
  };

  it('parses the pack size out of the title when the provider omits it', () => {
    const normalized = normalizeProduct(base, { matchKey: 'tomato|kg', dataSource: 'demo' });
    expect(normalized.quantity).toBe(500);
    expect(normalized.unit).toBe('g');
    expect(normalized.normalizedPricePerUnit).toBe(50);
    expect(normalized.unitPriceLabel).toBe('₹50/kg');
    expect(normalized.unitBasis).toBe('kg');
    expect(normalized.dataSource).toBe('demo');
  });

  it('trusts an explicit quantity over the title', () => {
    const normalized = normalizeProduct(
      { ...base, title: 'Tomato Combo 500 g', quantity: 2, unit: 'kg', price: 90 },
      { matchKey: 'tomato|kg', dataSource: 'live' },
    );
    expect(normalized.normalizedPricePerUnit).toBe(45);
    expect(normalized.dataSource).toBe('live');
  });

  it('leaves unit price empty for products with no pack size', () => {
    const normalized = normalizeProduct(
      { ...base, title: 'Apple iPhone 16', price: 74900 },
      { matchKey: 'iphone|unknown', dataSource: 'demo' },
    );
    expect(normalized.normalizedPricePerUnit).toBeUndefined();
    expect(normalized.unitBasis).toBe('unknown');
  });
});
