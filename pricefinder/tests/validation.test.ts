import { describe, expect, it } from 'vitest';
import {
  DEFAULT_PINCODE,
  DEFAULT_SORT,
  basketRequestSchema,
  formatIssues,
  parseSearchParams,
  sanitizeQuery,
  searchParamsSchema,
} from '@/lib/validation';

describe('sanitizeQuery', () => {
  it('collapses whitespace and trims', () => {
    expect(sanitizeQuery('  amul   milk  1L ')).toBe('amul milk 1L');
  });

  it('strips markup characters', () => {
    expect(sanitizeQuery('<script>alert(1)</script>tomato')).toBe('script alert(1) /script tomato');
  });

  it('strips control characters, including newlines that could forge log lines', () => {
    const withNewlines = ['tomato', 'milk'].join('\n\r\t');
    expect(sanitizeQuery(withNewlines)).toBe('tomato milk');

    const withNull = `tomato${String.fromCharCode(0)}milk`;
    expect(sanitizeQuery(withNull)).toBe('tomato milk');
  });

  it('caps the length', () => {
    expect(sanitizeQuery('a'.repeat(500))).toHaveLength(100);
  });
});

describe('searchParamsSchema', () => {
  it('applies sensible defaults', () => {
    const parsed = searchParamsSchema.parse({ q: 'tomato' });
    expect(parsed.pincode).toBe(DEFAULT_PINCODE);
    expect(parsed.sort).toBe(DEFAULT_SORT);
    expect(parsed.refresh).toBe(false);
  });

  it('rejects a query that is too short or has no content', () => {
    expect(searchParamsSchema.safeParse({ q: 'a' }).success).toBe(false);
    expect(searchParamsSchema.safeParse({ q: '' }).success).toBe(false);
    expect(searchParamsSchema.safeParse({ q: '***' }).success).toBe(false);
  });

  it('rejects malformed PIN codes', () => {
    expect(searchParamsSchema.safeParse({ q: 'tomato', pincode: '12345' }).success).toBe(false);
    expect(searchParamsSchema.safeParse({ q: 'tomato', pincode: '012345' }).success).toBe(false);
    expect(searchParamsSchema.safeParse({ q: 'tomato', pincode: 'abcdef' }).success).toBe(false);
    expect(searchParamsSchema.safeParse({ q: 'tomato', pincode: '500001' }).success).toBe(true);
  });

  it('rejects an unknown sort or provider', () => {
    expect(searchParamsSchema.safeParse({ q: 'tomato', sort: 'cheapest' }).success).toBe(false);
    expect(searchParamsSchema.safeParse({ q: 'tomato', providers: 'zepto,bigbazaar' }).success).toBe(false);
  });

  it('parses a comma-separated provider list', () => {
    const parsed = searchParamsSchema.parse({ q: 'tomato', providers: 'zepto, blinkit' });
    expect(parsed.providers).toEqual(['zepto', 'blinkit']);
  });

  it('bounds coordinates', () => {
    expect(searchParamsSchema.safeParse({ q: 'tomato', latitude: '17.4' }).success).toBe(true);
    expect(searchParamsSchema.safeParse({ q: 'tomato', latitude: '120' }).success).toBe(false);
  });
});

describe('parseSearchParams', () => {
  it('reads a query string', () => {
    const parsed = parseSearchParams(new URLSearchParams('q=tomato&pincode=110001&sort=price'));
    expect(parsed.success).toBe(true);
    expect(parsed.success && parsed.data.pincode).toBe('110001');
  });

  it('ignores empty parameters rather than failing on them', () => {
    const parsed = parseSearchParams(new URLSearchParams('q=tomato&pincode='));
    expect(parsed.success && parsed.data.pincode).toBe(DEFAULT_PINCODE);
  });
});

describe('basketRequestSchema', () => {
  it('accepts a plain list of strings', () => {
    const parsed = basketRequestSchema.parse({ items: ['tomato 1kg', 'milk 1L'] });
    expect(parsed.items).toHaveLength(2);
  });

  it('accepts items with quantities', () => {
    const parsed = basketRequestSchema.parse({ items: [{ query: 'eggs 12', quantity: 2 }] });
    expect(parsed.items[0]).toEqual({ query: 'eggs 12', quantity: 2 });
  });

  it('rejects an empty or oversized basket', () => {
    expect(basketRequestSchema.safeParse({ items: [] }).success).toBe(false);
    expect(basketRequestSchema.safeParse({ items: Array(26).fill('milk') }).success).toBe(false);
  });
});

describe('formatIssues', () => {
  it('flattens errors into one message per field', () => {
    const result = searchParamsSchema.safeParse({ q: 'a', pincode: 'nope' });
    expect(result.success).toBe(false);
    if (!result.success) {
      const issues = formatIssues(result.error);
      expect(Object.keys(issues).sort()).toEqual(['pincode', 'q']);
    }
  });
});
