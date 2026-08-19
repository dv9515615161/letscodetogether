import { describe, expect, it } from 'vitest';
import { formatDeliveryTime, formatDiscount, formatQuantity, formatRupees } from '@/lib/format';

describe('formatRupees', () => {
  it('omits paise when the amount is whole', () => {
    expect(formatRupees(39)).toBe('₹39');
    expect(formatRupees(1234)).toBe('₹1,234');
  });

  it('keeps two decimals when there are paise', () => {
    expect(formatRupees(7.666)).toBe('₹7.67');
  });

  it('uses the Indian digit grouping', () => {
    expect(formatRupees(174900)).toBe('₹1,74,900');
  });
});

describe('formatDeliveryTime', () => {
  it('scales the unit to the wait', () => {
    expect(formatDeliveryTime(15)).toBe('~15 min');
    expect(formatDeliveryTime(120)).toBe('~2 hrs');
    expect(formatDeliveryTime(60)).toBe('~1 hr');
    expect(formatDeliveryTime(2880)).toBe('~2 days');
    expect(formatDeliveryTime(undefined)).toBeUndefined();
  });
});

describe('formatQuantity', () => {
  it('renders a pack size, or nothing when it is unknown', () => {
    expect(formatQuantity(500, 'g')).toBe('500 g');
    expect(formatQuantity(1.5, 'l')).toBe('1.5 l');
    expect(formatQuantity(undefined, 'kg')).toBeUndefined();
    expect(formatQuantity(1, undefined)).toBeUndefined();
  });
});

describe('formatDiscount', () => {
  it('computes a percentage only when there is a real saving', () => {
    expect(formatDiscount(80, 100)).toBe(20);
    expect(formatDiscount(100, 100)).toBeUndefined();
    expect(formatDiscount(100, 80)).toBeUndefined();
    expect(formatDiscount(100, undefined)).toBeUndefined();
  });
});
