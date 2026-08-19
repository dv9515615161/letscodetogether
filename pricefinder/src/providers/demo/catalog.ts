/**
 * Sample catalogue backing every provider that has no authorised live API yet.
 *
 * This is fabricated data for demonstration. It is never presented as a real
 * price: every row it produces is tagged `dataSource: "demo"` and rendered with
 * a "Demo data" badge. Prices are plausible Indian retail figures from 2024-25
 * but they are NOT scraped, observed, or in any way authoritative.
 */

export type CatalogCategory = 'produce' | 'dairy' | 'staples' | 'packaged' | 'household' | 'electronics';

export interface CatalogSku {
  /** Product title as a store would list it, including the pack size. */
  title: string;
  brand?: string;
  quantity: number;
  unit: 'g' | 'kg' | 'ml' | 'l' | 'piece';
  /** Reference price in INR before per-provider adjustment. */
  basePrice: number;
  description?: string;
}

export interface CatalogItem {
  slug: string;
  /** Search terms that should surface this item, including common Hindi names. */
  keywords: string[];
  category: CatalogCategory;
  skus: CatalogSku[];
}

export const CATALOG: CatalogItem[] = [
  {
    slug: 'tomato',
    keywords: ['tomato', 'tomatoes', 'tamatar'],
    category: 'produce',
    skus: [
      { title: 'Tomato', quantity: 1, unit: 'kg', basePrice: 42, description: 'Everyday cooking tomatoes' },
      { title: 'Fresh Tomato', quantity: 500, unit: 'g', basePrice: 24 },
      { title: 'Organic Tomato', quantity: 500, unit: 'g', basePrice: 48, description: 'Certified organic' },
      { title: 'Cherry Tomato', quantity: 200, unit: 'g', basePrice: 59 },
      { title: 'Hybrid Tomato', quantity: 1, unit: 'kg', basePrice: 38 },
    ],
  },
  {
    slug: 'onion',
    keywords: ['onion', 'onions', 'pyaz', 'kanda'],
    category: 'produce',
    skus: [
      { title: 'Onion', quantity: 1, unit: 'kg', basePrice: 36 },
      { title: 'Fresh Onion', quantity: 2, unit: 'kg', basePrice: 68 },
      { title: 'Organic Onion', quantity: 1, unit: 'kg', basePrice: 62 },
    ],
  },
  {
    slug: 'potato',
    keywords: ['potato', 'potatoes', 'aloo'],
    category: 'produce',
    skus: [
      { title: 'Potato', quantity: 1, unit: 'kg', basePrice: 32 },
      { title: 'Potato', quantity: 500, unit: 'g', basePrice: 18 },
      { title: 'Baby Potato', quantity: 500, unit: 'g', basePrice: 45 },
    ],
  },
  {
    slug: 'banana',
    keywords: ['banana', 'bananas', 'kela'],
    category: 'produce',
    skus: [
      { title: 'Banana Robusta', quantity: 6, unit: 'piece', basePrice: 42 },
      { title: 'Banana Yelakki', quantity: 500, unit: 'g', basePrice: 55 },
    ],
  },
  {
    slug: 'apple',
    keywords: ['apple', 'apples', 'seb'],
    category: 'produce',
    skus: [
      { title: 'Apple Shimla', quantity: 1, unit: 'kg', basePrice: 180 },
      { title: 'Apple Royal Gala', quantity: 4, unit: 'piece', basePrice: 165 },
    ],
  },
  {
    slug: 'milk',
    keywords: ['milk', 'doodh', 'dudh'],
    category: 'dairy',
    skus: [
      { title: 'Amul Taaza Toned Milk', brand: 'Amul', quantity: 1, unit: 'l', basePrice: 68 },
      { title: 'Amul Gold Full Cream Milk', brand: 'Amul', quantity: 1, unit: 'l', basePrice: 78 },
      { title: 'Amul Taaza Toned Milk', brand: 'Amul', quantity: 500, unit: 'ml', basePrice: 35 },
      { title: 'Mother Dairy Toned Milk', brand: 'Mother Dairy', quantity: 1, unit: 'l', basePrice: 66 },
      { title: 'Nandini Toned Milk', brand: 'Nandini', quantity: 500, unit: 'ml', basePrice: 26 },
      { title: 'Heritage Double Toned Milk', brand: 'Heritage', quantity: 1, unit: 'l', basePrice: 62 },
      { title: 'Organic A2 Cow Milk', brand: 'Akshayakalpa', quantity: 1, unit: 'l', basePrice: 125 },
    ],
  },
  {
    slug: 'curd',
    keywords: ['curd', 'dahi', 'yogurt', 'yoghurt'],
    category: 'dairy',
    skus: [
      { title: 'Amul Masti Curd', brand: 'Amul', quantity: 400, unit: 'g', basePrice: 35 },
      { title: 'Nandini Curd', brand: 'Nandini', quantity: 500, unit: 'g', basePrice: 32 },
    ],
  },
  {
    slug: 'paneer',
    keywords: ['paneer', 'cottage cheese'],
    category: 'dairy',
    skus: [
      { title: 'Amul Malai Paneer', brand: 'Amul', quantity: 200, unit: 'g', basePrice: 95 },
      { title: 'Milky Mist Paneer', brand: 'Milky Mist', quantity: 200, unit: 'g', basePrice: 99 },
    ],
  },
  {
    slug: 'eggs',
    keywords: ['egg', 'eggs', 'anda', 'ande'],
    category: 'dairy',
    skus: [
      { title: 'Farm Eggs', quantity: 6, unit: 'piece', basePrice: 48 },
      { title: 'Farm Eggs', quantity: 12, unit: 'piece', basePrice: 92 },
      { title: 'Country Egg', quantity: 6, unit: 'piece', basePrice: 78 },
      { title: 'Organic Eggs', quantity: 6, unit: 'piece', basePrice: 96 },
    ],
  },
  {
    slug: 'bread',
    keywords: ['bread', 'pav', 'bun'],
    category: 'packaged',
    skus: [
      { title: 'Britannia Sandwich Bread', brand: 'Britannia', quantity: 400, unit: 'g', basePrice: 50 },
      { title: 'Harvest Brown Bread', brand: 'Harvest', quantity: 400, unit: 'g', basePrice: 55 },
      { title: 'Whole Wheat Bread', quantity: 350, unit: 'g', basePrice: 58 },
    ],
  },
  {
    slug: 'rice',
    keywords: ['rice', 'chawal', 'basmati', 'sona masoori'],
    category: 'staples',
    skus: [
      { title: 'India Gate Basmati Rice', brand: 'India Gate', quantity: 5, unit: 'kg', basePrice: 690 },
      { title: 'Daawat Basmati Rice', brand: 'Daawat', quantity: 1, unit: 'kg', basePrice: 148 },
      { title: 'Sona Masoori Rice', quantity: 5, unit: 'kg', basePrice: 420 },
      { title: 'Organic Sona Masoori Rice', quantity: 5, unit: 'kg', basePrice: 545 },
    ],
  },
  {
    slug: 'atta',
    keywords: ['atta', 'flour', 'wheat', 'maida'],
    category: 'staples',
    skus: [
      { title: 'Aashirvaad Whole Wheat Atta', brand: 'Aashirvaad', quantity: 5, unit: 'kg', basePrice: 285 },
      { title: 'Fortune Chakki Fresh Atta', brand: 'Fortune', quantity: 5, unit: 'kg', basePrice: 262 },
      { title: 'Aashirvaad Whole Wheat Atta', brand: 'Aashirvaad', quantity: 1, unit: 'kg', basePrice: 62 },
    ],
  },
  {
    slug: 'sugar',
    keywords: ['sugar', 'cheeni', 'chini'],
    category: 'staples',
    skus: [
      { title: 'Sugar', quantity: 1, unit: 'kg', basePrice: 48 },
      { title: 'Organic Sugar', quantity: 1, unit: 'kg', basePrice: 92 },
    ],
  },
  {
    slug: 'salt',
    keywords: ['salt', 'namak'],
    category: 'staples',
    skus: [{ title: 'Tata Salt', brand: 'Tata', quantity: 1, unit: 'kg', basePrice: 28 }],
  },
  {
    slug: 'oil',
    keywords: ['oil', 'tel', 'sunflower oil', 'refined oil', 'cooking oil'],
    category: 'staples',
    skus: [
      { title: 'Fortune Sunflower Oil', brand: 'Fortune', quantity: 1, unit: 'l', basePrice: 145 },
      { title: 'Saffola Gold Refined Oil', brand: 'Saffola', quantity: 1, unit: 'l', basePrice: 178 },
      { title: 'Cold Pressed Groundnut Oil', quantity: 1, unit: 'l', basePrice: 320 },
    ],
  },
  {
    slug: 'tea',
    keywords: ['tea', 'chai', 'chai patti'],
    category: 'packaged',
    skus: [
      { title: 'Tata Tea Premium', brand: 'Tata', quantity: 500, unit: 'g', basePrice: 265 },
      { title: 'Red Label Tea', brand: 'Brooke Bond', quantity: 250, unit: 'g', basePrice: 140 },
    ],
  },
  {
    slug: 'coffee',
    keywords: ['coffee', 'instant coffee'],
    category: 'packaged',
    skus: [
      { title: 'Nescafe Classic Coffee', brand: 'Nescafe', quantity: 50, unit: 'g', basePrice: 175 },
      { title: 'Bru Instant Coffee', brand: 'Bru', quantity: 100, unit: 'g', basePrice: 265 },
    ],
  },
  {
    slug: 'biscuits',
    keywords: ['biscuit', 'biscuits', 'cookies', 'parle', 'marie'],
    category: 'packaged',
    skus: [
      { title: 'Parle-G Biscuits', brand: 'Parle', quantity: 800, unit: 'g', basePrice: 85 },
      { title: 'Britannia Marie Gold', brand: 'Britannia', quantity: 250, unit: 'g', basePrice: 40 },
    ],
  },
  {
    slug: 'washing-powder',
    keywords: ['washing powder', 'detergent', 'surf', 'ariel', 'washing', 'laundry'],
    category: 'household',
    skus: [
      { title: 'Surf Excel Easy Wash Detergent Powder', brand: 'Surf Excel', quantity: 1, unit: 'kg', basePrice: 138 },
      { title: 'Ariel Complete Detergent Washing Powder', brand: 'Ariel', quantity: 1, unit: 'kg', basePrice: 165 },
      { title: 'Tide Plus Detergent Washing Powder', brand: 'Tide', quantity: 2, unit: 'kg', basePrice: 235 },
      { title: 'Nirma Washing Powder', brand: 'Nirma', quantity: 1, unit: 'kg', basePrice: 68 },
    ],
  },
  {
    slug: 'dishwash',
    keywords: ['dishwash', 'dish wash', 'vim', 'utensil'],
    category: 'household',
    skus: [
      { title: 'Vim Dishwash Liquid Gel', brand: 'Vim', quantity: 750, unit: 'ml', basePrice: 199 },
      { title: 'Vim Dishwash Bar', brand: 'Vim', quantity: 3, unit: 'piece', basePrice: 30 },
    ],
  },
  {
    slug: 'soap',
    keywords: ['soap', 'bathing bar', 'sabun', 'dove', 'lifebuoy'],
    category: 'household',
    skus: [
      { title: 'Dove Cream Beauty Bathing Bar', brand: 'Dove', quantity: 4, unit: 'piece', basePrice: 220 },
      { title: 'Lifebuoy Total Soap', brand: 'Lifebuoy', quantity: 4, unit: 'piece', basePrice: 132 },
    ],
  },
  {
    slug: 'shampoo',
    keywords: ['shampoo', 'hair wash'],
    category: 'household',
    skus: [
      { title: 'Dove Intense Repair Shampoo', brand: 'Dove', quantity: 340, unit: 'ml', basePrice: 399 },
      { title: 'Clinic Plus Strong Shampoo', brand: 'Clinic Plus', quantity: 355, unit: 'ml', basePrice: 285 },
    ],
  },
  {
    slug: 'toothpaste',
    keywords: ['toothpaste', 'colgate', 'dental'],
    category: 'household',
    skus: [
      { title: 'Colgate Strong Teeth Toothpaste', brand: 'Colgate', quantity: 200, unit: 'g', basePrice: 118 },
      { title: 'Sensodyne Fresh Mint Toothpaste', brand: 'Sensodyne', quantity: 150, unit: 'g', basePrice: 205 },
    ],
  },
  {
    slug: 'iphone-16',
    keywords: ['iphone', 'iphone 16', 'apple phone', 'apple iphone'],
    category: 'electronics',
    skus: [
      { title: 'Apple iPhone 16 (128 GB, Black)', brand: 'Apple', quantity: 1, unit: 'piece', basePrice: 74900 },
      { title: 'Apple iPhone 16 (256 GB, Black)', brand: 'Apple', quantity: 1, unit: 'piece', basePrice: 84900 },
      { title: 'Apple iPhone 16 Pro (128 GB, Natural Titanium)', brand: 'Apple', quantity: 1, unit: 'piece', basePrice: 119900 },
    ],
  },
  {
    slug: 'android-phone',
    keywords: ['samsung', 'galaxy', 'android phone', 'smartphone', 'mobile', 'phone'],
    category: 'electronics',
    skus: [
      { title: 'Samsung Galaxy S24 (128 GB)', brand: 'Samsung', quantity: 1, unit: 'piece', basePrice: 64999 },
      { title: 'Samsung Galaxy M35 5G (128 GB)', brand: 'Samsung', quantity: 1, unit: 'piece', basePrice: 17499 },
    ],
  },
  {
    slug: 'earbuds',
    keywords: ['earbuds', 'headphones', 'earphones', 'airpods', 'tws'],
    category: 'electronics',
    skus: [
      { title: 'boAt Airdopes 141 Earbuds', brand: 'boAt', quantity: 1, unit: 'piece', basePrice: 1299 },
      { title: 'Apple AirPods (3rd Generation)', brand: 'Apple', quantity: 1, unit: 'piece', basePrice: 16900 },
    ],
  },
];

/** Case- and plural-insensitive lookup used by the demo search. */
export function findCatalogItems(query: string): CatalogItem[] {
  const needle = query.trim().toLowerCase();
  if (!needle) return [];
  const queryTokens = needle.split(/\s+/).filter(Boolean);

  const scored = CATALOG.map((item) => {
    let score = 0;
    for (const keyword of item.keywords) {
      if (needle === keyword) score += 10;
      else if (needle.includes(keyword)) score += 6;
      else if (keyword.includes(needle) && needle.length >= 3) score += 4;
    }
    for (const token of queryTokens) {
      if (token.length < 3) continue;
      if (item.keywords.some((keyword) => keyword.includes(token) || token.includes(keyword))) score += 3;
      if (item.skus.some((sku) => sku.title.toLowerCase().includes(token))) score += 2;
      if (item.skus.some((sku) => sku.brand?.toLowerCase().includes(token))) score += 3;
    }
    return { item, score };
  }).filter((entry) => entry.score > 0);

  scored.sort((a, b) => b.score - a.score);

  // Relevance gate: without it a broad keyword ("phone" inside "iPhone") pulls
  // in a loosely related item, which then outranks the real match once results
  // are sorted by price. Only items scoring close to the best match survive.
  const topScore = scored[0]!.score;
  return scored.filter((entry) => entry.score >= topScore * 0.75).slice(0, 2).map((entry) => entry.item);
}

/**
 * Narrows an item's SKUs to those matching extra query terms, so
 * "amul milk 1L" prefers the Amul 1 L pack over every milk in the catalogue.
 */
export function filterSkus(item: CatalogItem, query: string): CatalogSku[] {
  const tokens = query
    .toLowerCase()
    .split(/\s+/)
    .filter((token) => token.length >= 3 && !item.keywords.includes(token));
  if (tokens.length === 0) return item.skus;

  const matches = item.skus.filter((sku) => {
    const haystack = `${sku.brand ?? ''} ${sku.title}`.toLowerCase();
    return tokens.some((token) => haystack.includes(token));
  });
  return matches.length > 0 ? matches : item.skus;
}
