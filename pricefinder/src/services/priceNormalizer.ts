/**
 * Pack-size parsing and price-per-unit maths.
 *
 * Comparing "₹25 for 500 g" against "₹45 for 1 kg" is the whole point of the
 * app, so this module converts every pack size into one of three comparable
 * bases — kilograms, litres, or pieces — and expresses the price against it.
 */

import type { NormalizedProduct, ProductResult, UnitBasis, DataSource } from '@/providers/types';
import { formatRupees } from '@/lib/format';

export interface ParsedQuantity {
  /** Amount in the canonical unit below. */
  quantity: number;
  /** Canonical unit token: g, kg, ml, l or piece. */
  unit: CanonicalUnit;
}

export type CanonicalUnit = 'mg' | 'g' | 'kg' | 'ml' | 'l' | 'piece';

/**
 * Every spelling we accept, mapped to a canonical unit. Keys are matched
 * case-insensitively against whole words in a product title.
 */
const UNIT_ALIASES: Record<string, CanonicalUnit> = {
  mg: 'mg',
  milligram: 'mg',
  milligrams: 'mg',
  g: 'g',
  gm: 'g',
  gms: 'g',
  gr: 'g',
  gram: 'g',
  grams: 'g',
  gramme: 'g',
  grammes: 'g',
  kg: 'kg',
  kgs: 'kg',
  kilo: 'kg',
  kilos: 'kg',
  kilogram: 'kg',
  kilograms: 'kg',
  ml: 'ml',
  millilitre: 'ml',
  millilitres: 'ml',
  milliliter: 'ml',
  milliliters: 'ml',
  cc: 'ml',
  l: 'l',
  lt: 'l',
  ltr: 'l',
  ltrs: 'l',
  litre: 'l',
  litres: 'l',
  liter: 'l',
  liters: 'l',
  pc: 'piece',
  pcs: 'piece',
  piece: 'piece',
  pieces: 'piece',
  pack: 'piece',
  packs: 'piece',
  packet: 'piece',
  packets: 'piece',
  unit: 'piece',
  units: 'piece',
  count: 'piece',
  nos: 'piece',
  no: 'piece',
  egg: 'piece',
  eggs: 'piece',
  tablet: 'piece',
  tablets: 'piece',
};

/** Words that denote a fixed count rather than a measured amount. */
const COUNT_WORDS: Record<string, number> = {
  dozen: 12,
  'half dozen': 6,
  pair: 2,
};

const UNIT_TOKEN_GROUP = Object.keys(UNIT_ALIASES)
  .sort((a, b) => b.length - a.length)
  .join('|');

/** e.g. "6 x 100 g", "2 × 1 L" — a multi-pack whose total is the product. */
const MULTIPACK_PATTERN = new RegExp(
  String.raw`(\d+(?:\.\d+)?)\s*(?:x|×|\*)\s*(\d+(?:\.\d+)?)\s*(${UNIT_TOKEN_GROUP})\b`,
  'i',
);

/** e.g. "500 g", "1kg", "1.5 ltr", "12 pcs". */
const SINGLE_PATTERN = new RegExp(
  String.raw`(\d+(?:\.\d+)?)\s*(${UNIT_TOKEN_GROUP})\b`,
  'i',
);

/** e.g. "pack of 6", "combo of 12". */
const PACK_OF_PATTERN = /\b(?:pack|packet|set|combo|box|tray)\s+of\s+(\d+)\b/i;

/** e.g. "1 dozen eggs", or a bare "dozen". */
const COUNT_WORD_PATTERN = new RegExp(
  String.raw`(?:(\d+(?:\.\d+)?)\s*)?\b(${Object.keys(COUNT_WORDS).join('|')})\b`,
  'i',
);

export function canonicalUnit(token: string): CanonicalUnit | undefined {
  return UNIT_ALIASES[token.trim().toLowerCase()];
}

/**
 * Pulls a pack size out of free text. Returns `undefined` when the text carries
 * no size at all (e.g. "iPhone 16"), which is a legitimate outcome — such
 * products are compared on absolute price only.
 */
export function parseQuantity(text: string | undefined | null): ParsedQuantity | undefined {
  if (!text) return undefined;
  const haystack = text.toLowerCase();

  const multipack = MULTIPACK_PATTERN.exec(haystack);
  if (multipack) {
    const count = Number(multipack[1]);
    const size = Number(multipack[2]);
    const unit = canonicalUnit(multipack[3] ?? '');
    if (unit && count > 0 && size > 0) {
      return { quantity: count * size, unit };
    }
  }

  const single = SINGLE_PATTERN.exec(haystack);
  if (single) {
    const value = Number(single[1]);
    const unit = canonicalUnit(single[2] ?? '');
    if (unit && value > 0) {
      return { quantity: value, unit };
    }
  }

  const packOf = PACK_OF_PATTERN.exec(haystack);
  if (packOf) {
    const value = Number(packOf[1]);
    if (value > 0) return { quantity: value, unit: 'piece' };
  }

  const countWord = COUNT_WORD_PATTERN.exec(haystack);
  if (countWord) {
    const multiplier = countWord[1] ? Number(countWord[1]) : 1;
    const base = COUNT_WORDS[(countWord[2] ?? '').toLowerCase()];
    if (base && multiplier > 0) {
      return { quantity: multiplier * base, unit: 'piece' };
    }
  }

  return undefined;
}

/** Which comparable family a canonical unit belongs to. */
export function unitBasisFor(unit: CanonicalUnit | string | undefined): UnitBasis {
  const canonical = typeof unit === 'string' ? (canonicalUnit(unit) ?? unit) : unit;
  switch (canonical) {
    case 'mg':
    case 'g':
    case 'kg':
      return 'kg';
    case 'ml':
    case 'l':
      return 'l';
    case 'piece':
      return 'piece';
    default:
      return 'unknown';
  }
}

/** Converts an amount into its basis unit: grams → kg, millilitres → litres. */
export function toBasisAmount(quantity: number, unit: CanonicalUnit): number {
  switch (unit) {
    case 'mg':
      return quantity / 1_000_000;
    case 'g':
      return quantity / 1000;
    case 'kg':
      return quantity;
    case 'ml':
      return quantity / 1000;
    case 'l':
      return quantity;
    case 'piece':
      return quantity;
  }
}

export interface UnitPrice {
  basis: UnitBasis;
  /** Price for one kilogram / litre / piece. */
  value: number;
  /** e.g. "₹50/kg". */
  label: string;
}

export function basisLabel(basis: UnitBasis): string {
  switch (basis) {
    case 'kg':
      return 'kg';
    case 'l':
      return 'L';
    case 'piece':
      return 'piece';
    default:
      return '';
  }
}

/**
 * The headline calculation: 500 g for ₹25 becomes ₹50/kg.
 * Returns `undefined` when there is no usable pack size.
 */
export function computeUnitPrice(
  price: number,
  quantity: number | undefined,
  unit: CanonicalUnit | string | undefined,
): UnitPrice | undefined {
  if (!Number.isFinite(price) || price < 0) return undefined;
  if (!quantity || quantity <= 0 || !unit) return undefined;

  const canonical = canonicalUnit(String(unit)) ?? (unit as CanonicalUnit);
  const basis = unitBasisFor(canonical);
  if (basis === 'unknown') return undefined;

  const amount = toBasisAmount(quantity, canonical);
  if (!amount || amount <= 0) return undefined;

  // Two decimals is enough precision for a price and avoids 49.000000000000004.
  const value = Math.round((price / amount) * 100) / 100;
  return { basis, value, label: `${formatRupees(value)}/${basisLabel(basis)}` };
}

/**
 * Fills in everything a raw `ProductResult` may have left out: pack size taken
 * from the title when the provider did not send it, and the comparable unit
 * price. `matchKey` is attached separately by `productMatcher`.
 */
export function normalizeProduct(
  product: ProductResult,
  meta: { matchKey: string; dataSource: DataSource },
): NormalizedProduct {
  const parsed =
    product.quantity && product.unit
      ? { quantity: product.quantity, unit: canonicalUnit(product.unit) ?? ('piece' as CanonicalUnit) }
      : parseQuantity(product.title);

  const unitPrice = computeUnitPrice(product.price, parsed?.quantity, parsed?.unit);

  return {
    ...product,
    quantity: parsed?.quantity ?? product.quantity,
    unit: parsed?.unit ?? product.unit,
    pricePerUnit: unitPrice?.value ?? product.pricePerUnit,
    normalizedPricePerUnit: unitPrice?.value,
    unitBasis: unitPrice?.basis ?? 'unknown',
    unitPriceLabel: unitPrice?.label,
    matchKey: meta.matchKey,
    dataSource: meta.dataSource,
  };
}
