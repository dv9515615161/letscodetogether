/**
 * Grouping "the same product, different store" together.
 *
 * The rule that matters most here is the negative one: it is far worse to merge
 * two genuinely different items (organic vs. regular tomatoes, toned vs. full
 * cream milk) than to leave two spellings of the same item in separate groups.
 * So generic marketing words are stripped, but anything that changes what you
 * actually receive is kept as a *variant tag* and blocks a merge.
 */

import type { NormalizedProduct, UnitBasis } from '@/providers/types';
import { parseQuantity, unitBasisFor } from '@/services/priceNormalizer';

/**
 * Marketing filler. Removing these is what makes "Fresh Tomato" and
 * "Tomatoes - 1 KG" land in the same group.
 */
const NOISE_WORDS = new Set([
  'fresh',
  'freshly',
  'farm',
  'farmfresh',
  'premium',
  'quality',
  'best',
  'super',
  'superior',
  'select',
  'special',
  'value',
  'saver',
  'daily',
  'new',
  'offer',
  'combo',
  'loose',
  'assorted',
  'approx',
  'approximately',
  'each',
  'the',
  'a',
  'an',
  'of',
  'and',
  'with',
  'for',
  'by',
  'in',
  'pack',
  'packet',
  'packed',
  'box',
  'bag',
  'pouch',
  'bottle',
  'tetra',
  'buy',
  'free',
  'online',
  'grocery',
  'item',
  'items',
  'product',
]);

/**
 * Multi-word variants, checked before tokenisation so "full cream" is one tag
 * rather than the two meaningless tokens "full" and "cream".
 */
const VARIANT_PHRASES: string[] = [
  'full cream',
  'double toned',
  'low fat',
  'fat free',
  'sugar free',
  'gluten free',
  'whole wheat',
  'whole grain',
  'extra virgin',
  'cold pressed',
  'sunflower oil',
  'country egg',
  'free range',
];

/**
 * Single words that change what the buyer gets. Two products never merge
 * unless their variant sets are identical.
 */
const VARIANT_WORDS = new Set([
  'organic',
  'inorganic',
  'hybrid',
  'desi',
  'country',
  'native',
  'imported',
  'exotic',
  'local',
  'toned',
  'skimmed',
  'unsweetened',
  'sweetened',
  'brown',
  'white',
  'red',
  'green',
  'yellow',
  'black',
  'multigrain',
  'refined',
  'unrefined',
  'raw',
  'roasted',
  'salted',
  'unsalted',
  'frozen',
  'dried',
  'powder',
  'liquid',
  'seedless',
  'boneless',
  'diet',
  'lite',
  'a2',
  'cow',
  'buffalo',
  'basmati',
  'sona',
  'masoori',
  'idli',
  'dosa',
  'cherry',
  'baby',
  'mini',
  'large',
  'small',
  'medium',
]);

/** Words whose trailing "s" is part of the word, not a plural marker. */
const NON_PLURAL = new Set(['gas', 'grass', 'glass', 'plus', 'bites', 'oats', 'chips', 'crisps', 'greens']);

/** Very small stemmer — enough to fold "tomatoes"/"tomato" and "eggs"/"egg". */
export function singularize(word: string): string {
  if (word.length <= 3 || NON_PLURAL.has(word)) return word;
  if (word.endsWith('ies')) return `${word.slice(0, -3)}y`;
  if (/(ch|sh|x|z|s|o)es$/.test(word)) return word.slice(0, -2);
  if (word.endsWith('ss')) return word;
  if (word.endsWith('s')) return word.slice(0, -1);
  return word;
}

export interface TitleAnalysis {
  /** Meaningful, order-independent product words, e.g. ["tomato"]. */
  coreTokens: string[];
  /** Variant markers that must match for two products to be merged. */
  variantTags: string[];
  /** Whitespace-joined core tokens; handy for display and debugging. */
  normalizedTitle: string;
}

/**
 * Strips punctuation, pack sizes, plurals and filler from a product title,
 * splitting what is left into core words and variant markers.
 */
export function analyzeTitle(rawTitle: string): TitleAnalysis {
  let text = ` ${rawTitle.toLowerCase()} `
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9.\s]+/g, ' ')
    // Pack sizes are handled by priceNormalizer; they must not affect grouping.
    .replace(/\b(?:pack|packet|set|combo|box|tray)\s+of\s+\d+\b/g, ' ')
    .replace(/\b\d+(?:\.\d+)?\s*(?:x|\*)\s*\d+(?:\.\d+)?\s*[a-z]+\b/g, ' ')
    .replace(
      /\b\d+(?:\.\d+)?\s*(?:mg|g|gm|gms|gr|grams?|kgs?|kilos?|kilograms?|ml|millilitres?|milliliters?|l|lt|ltrs?|litres?|liters?|pcs?|pieces?|units?|nos?|count|dozen)\b/g,
      ' ',
    )
    .replace(/\b(?:half\s+)?dozen\b/g, ' ');

  const variantTags = new Set<string>();
  for (const phrase of VARIANT_PHRASES) {
    if (text.includes(` ${phrase} `)) {
      variantTags.add(phrase.replace(/\s+/g, '-'));
      text = text.replace(new RegExp(`\\s${phrase}\\s`, 'g'), ' ');
    }
  }

  const coreTokens: string[] = [];
  for (const rawToken of text.split(/\s+/)) {
    const token = rawToken.replace(/^\.+|\.+$/g, '');
    if (!token) continue;
    // Numbers are kept. Pack sizes were already stripped above, so a surviving
    // number is part of the product's identity — the 128 in "iPhone 16 128 GB",
    // the 16 in "iPhone 16" — and dropping it merges different models.
    const stem = singularize(token);
    if (VARIANT_WORDS.has(stem)) {
      variantTags.add(stem);
      continue;
    }
    if (NOISE_WORDS.has(stem)) continue;
    coreTokens.push(stem);
  }

  const uniqueCore = [...new Set(coreTokens)];
  return {
    coreTokens: uniqueCore,
    variantTags: [...variantTags].sort(),
    normalizedTitle: uniqueCore.join(' '),
  };
}

/**
 * The grouping signature. Same key ⇒ same product row in the comparison table.
 * The unit basis is part of the key so that a 1 kg bag of rice and a 1 L bottle
 * of rice bran oil can never collide.
 */
export function buildMatchKey(title: string, unitBasis: UnitBasis = 'unknown'): string {
  const { coreTokens, variantTags } = analyzeTitle(title);
  const core = [...coreTokens].sort().join('-') || 'unknown';
  const variants = variantTags.join('-');
  return `${core}${variants ? `|${variants}` : ''}|${unitBasis}`;
}

/** Convenience wrapper: derives the unit basis from the title itself. */
export function matchKeyForTitle(title: string): string {
  const parsed = parseQuantity(title);
  return buildMatchKey(title, parsed ? unitBasisFor(parsed.unit) : 'unknown');
}

/** Titles are "the same product" when they group to the same key. */
export function isSameProduct(titleA: string, titleB: string): boolean {
  return matchKeyForTitle(titleA) === matchKeyForTitle(titleB);
}

export interface ProductGroup {
  matchKey: string;
  /** Cleaned-up name shown as the group heading, e.g. "Organic Tomato". */
  title: string;
  imageUrl?: string;
  /** One offer per provider row, cheapest-normalised first. */
  offers: NormalizedProduct[];
  unitBasis: UnitBasis;
}

/**
 * Builds the heading for a group.
 *
 * Uses the shortest real product title with its pack size trimmed, rather than
 * reassembling the normalised tokens: token order is not meaningful after
 * analysis, so rebuilding from it produces things like "Powder Nirma Washing".
 * Pack sizes are shown per offer, so they do not belong in the heading.
 */
function groupTitle(offers: NormalizedProduct[]): string {
  const shortest = [...offers].sort((a, b) => a.title.length - b.title.length)[0];
  const raw = shortest?.title?.trim();
  if (!raw) return 'Product';

  const trimmed = raw
    // Strip a trailing size suffix: " - 1 kg", ", 500 g", " (12 pieces)".
    .replace(
      /[\s,\-–(]+\d+(?:\.\d+)?\s*(?:mg|g|gm|gms|gr|grams?|kgs?|kilos?|kilograms?|ml|millilitres?|milliliters?|l|lt|ltrs?|litres?|liters?|pcs?|pieces?|units?|nos?|count|dozen)\s*\)?\s*$/i,
      '',
    )
    .replace(/[\s,\-–]+$/, '')
    .trim();

  return trimmed || raw;
}

/**
 * Groups normalised offers into comparable rows, bucketing by exact match key.
 *
 * An earlier version added a fuzzy second pass that folded buckets with high
 * token overlap. It was removed: at any threshold loose enough to be useful it
 * also merged "iPhone 16" with "iPhone 16 Pro", and a wrong merge invents a
 * "best price" for a product nobody is selling. Under-merging is visible and
 * harmless — the shopper sees two rows — so precision wins here. Recall is
 * instead improved in `analyzeTitle`, by normalising harder before matching.
 */
export function groupProducts(products: NormalizedProduct[]): ProductGroup[] {
  const buckets = new Map<string, NormalizedProduct[]>();
  for (const product of products) {
    const existing = buckets.get(product.matchKey);
    if (existing) existing.push(product);
    else buckets.set(product.matchKey, [product]);
  }

  return [...buckets.entries()].map(([matchKey, offers]) => ({
    matchKey,
    title: groupTitle(offers),
    imageUrl: offers.find((offer) => offer.imageUrl)?.imageUrl,
    unitBasis: offers[0]?.unitBasis ?? 'unknown',
    offers,
  }));
}
