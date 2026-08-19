/**
 * Input validation and sanitisation for every public entry point.
 *
 * Search text reaches a database (as history), a cache key, and outbound API
 * calls, so it is both validated and normalised here rather than at each stop.
 */

import { z } from 'zod';
import { PROVIDER_IDS } from '@/providers/types';

export const SORT_OPTIONS = [
  'price',
  'price_per_unit',
  'delivery',
  'rating',
  'provider',
] as const;

export type SortOption = (typeof SORT_OPTIONS)[number];

export const DEFAULT_SORT: SortOption = 'price_per_unit';
export const DEFAULT_PINCODE = '500001';

/**
 * Replaces C0/C7F control characters with spaces. Done by code point rather
 * than a regex so no literal control character ever appears in this source
 * file. Keeps newlines out of log lines and cache keys.
 */
function stripControlCharacters(value: string): string {
  let result = '';
  for (const char of value) {
    const code = char.codePointAt(0) ?? 0;
    result += code < 0x20 || code === 0x7f ? ' ' : char;
  }
  return result;
}

/**
 * Strips characters a product search never legitimately needs: control
 * characters, angle brackets, and anything that would let a query smuggle
 * markup into a rendered page or a log line.
 */
export function sanitizeQuery(raw: string): string {
  return stripControlCharacters(raw)
    .replace(/[<>{}[\]\\^~`|]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 100);
}

const querySchema = z
  .string()
  .min(1, 'Enter something to search for')
  .max(200, 'Search is too long')
  .transform(sanitizeQuery)
  .refine((value) => value.length >= 2, 'Search must be at least 2 characters')
  .refine((value) => /[a-z0-9]/i.test(value), 'Search must contain letters or numbers');

/** Indian PIN codes: six digits, never starting with 0. */
const pincodeSchema = z
  .string()
  .trim()
  .regex(/^[1-9][0-9]{5}$/, 'Enter a valid 6-digit PIN code');

const providersSchema = z
  .string()
  .transform((value) =>
    value
      .split(',')
      .map((entry) => entry.trim().toLowerCase())
      .filter(Boolean),
  )
  .pipe(z.array(z.enum(PROVIDER_IDS)));

export const searchParamsSchema = z.object({
  q: querySchema,
  pincode: pincodeSchema.optional().default(DEFAULT_PINCODE),
  sort: z.enum(SORT_OPTIONS).optional().default(DEFAULT_SORT),
  providers: providersSchema.optional(),
  latitude: z.coerce.number().min(-90).max(90).optional(),
  longitude: z.coerce.number().min(-180).max(180).optional(),
  /** Set by the UI's refresh affordance to skip the cache for one request. */
  refresh: z
    .union([z.literal('1'), z.literal('true'), z.literal('0'), z.literal('false')])
    .optional()
    .transform((value) => value === '1' || value === 'true'),
});

export type SearchParamsInput = z.input<typeof searchParamsSchema>;
export type SearchParams = z.output<typeof searchParamsSchema>;

export const basketRequestSchema = z.object({
  items: z
    .array(
      z.union([
        querySchema,
        z.object({
          query: querySchema,
          quantity: z.coerce.number().int().min(1).max(50).optional().default(1),
        }),
      ]),
    )
    .min(1, 'Add at least one item')
    .max(25, 'A basket can hold at most 25 items'),
  pincode: pincodeSchema.optional().default(DEFAULT_PINCODE),
  providers: z.array(z.enum(PROVIDER_IDS)).optional(),
});

export type BasketRequest = z.output<typeof basketRequestSchema>;

/** Flattens a ZodError into `{ field: message }` for a compact API response. */
export function formatIssues(error: z.ZodError): Record<string, string> {
  const fields: Record<string, string> = {};
  for (const issue of error.issues) {
    const key = issue.path.join('.') || 'request';
    if (!fields[key]) fields[key] = issue.message;
  }
  return fields;
}

/** Parses URLSearchParams into validated search input. */
export function parseSearchParams(searchParams: URLSearchParams) {
  const raw: Record<string, string> = {};
  for (const key of ['q', 'pincode', 'sort', 'providers', 'latitude', 'longitude', 'refresh']) {
    const value = searchParams.get(key);
    if (value !== null && value !== '') raw[key] = value;
  }
  return searchParamsSchema.safeParse(raw);
}
