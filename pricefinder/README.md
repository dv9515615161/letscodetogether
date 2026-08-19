# PriceFinder

Mobile-first price comparison across six Indian shopping platforms. Search once,
see what every store charges for the same product, normalised to a comparable
unit price (₹/kg, ₹/L, ₹/piece) so different pack sizes can be compared honestly.

```
Search "tomato"  →  Zepto ₹36 (₹36/kg) · Blinkit ₹37 (₹37/kg) · BigBasket ₹41 …
                    Best price: Zepto — ₹36/kg
```

> **Data honesty.** Five of the six stores have no authorised public product
> API. Those run on clearly-labelled **demo data** — sample prices, never
> presented as real ones. See [Live vs demo](#live-vs-demo).

---

## Contents

- [What it does](#what-it-does)
- [Architecture](#architecture)
- [Quick start](#quick-start)
- [Environment variables](#environment-variables)
- [Database setup](#database-setup)
- [Live vs demo](#live-vs-demo)
- [Connecting live providers](#connecting-live-providers)
- [Adding a new provider](#adding-a-new-provider)
- [API documentation](#api-documentation)
- [Testing](#testing)
- [Deployment](#deployment)
- [Limitations](#limitations)
- [Next steps](#next-steps)

---

## What it does

1. You type a product and a PIN code.
2. The backend queries every enabled store **concurrently**.
3. Each store's response is normalised into one common `ProductResult` shape.
4. Pack sizes are parsed (`500 g`, `6 x 200 ml`, `pack of 4`, `1 dozen`) and
   converted into a comparable unit price.
5. Offers for the same product are grouped across stores.
6. Results are sorted, summarised ("Best price: …"), and cached.
7. Every row links out to the store, and carries a **Live** or **Demo data** badge.

A store that fails, times out, or is switched off never breaks the search — it
appears in the response as a per-provider status the UI renders inline
("Zepto unavailable right now") while the other five stores' results still show.

## Architecture

```
Browser (Next.js App Router, client components)
   │  GET /api/search?q=…&pincode=…&sort=…
   ▼
Route handler ── validate (zod) ── rate limit ── searchService
                                                     │
              ┌──────────────────────────────────────┼─── cache (memory | Postgres)
              │                                      │
              ▼  Promise.allSettled, per-provider timeout
   ┌──────────┴──────────┬──────────┬──────────┬──────────┬──────────┐
 Blinkit   Zepto   Instamart   BigBasket    Amazon     Flipkart
 (demo)    (demo)    (demo)      (demo)   (PA-API v5) (Affiliate API)
   └──────────┬──────────┴──────────┴──────────┴──────────┴──────────┘
              ▼
   priceNormalizer → productMatcher → sorting → summary
              │
              └─ fire-and-forget: SearchQuery / SearchResult / PriceHistory
```

```
src/
  app/
    page.tsx                 Home (server) → SearchExperience (client)
    layout.tsx               SEO metadata, viewport, theme colour
    globals.css              Tailwind v4 + design tokens (light/dark)
    api/
      search/route.ts        GET + POST search
      providers/route.ts     Store list with live/demo status
      basket/route.ts        POST multi-item basket comparison
      health/route.ts        Liveness probe
  components/                SearchBar, LocationSelector, SortSelect,
                             SearchResults, ProductCard, ProviderCard,
                             PriceComparison, ProviderStatusList, Skeletons
  providers/
    types.ts                 ProductResult, ShoppingProvider, Provider…
    base.ts                  BaseProvider: demo/live switch, timeouts
    registry.ts              The one place that knows which stores exist
    blinkit|zepto|instamart|bigbasket|amazon|flipkart.ts
    demo/                    Sample catalogue + deterministic generator
  services/
    searchService.ts         Fan-out, normalise, group, sort, summarise
    priceNormalizer.ts       Pack-size parsing, ₹/kg · ₹/L · ₹/piece
    productMatcher.ts        Cross-store product grouping
    sorting.ts               The five sort orders
    cacheService.ts          CacheStore interface: memory | database
    basketService.ts         Whole-basket comparison (v2 feature, API-ready)
    persistence.ts           Optional search + price history
  lib/                       env, db, validation, rateLimit, logger, format
  types/search.ts            Wire types shared by API and UI
prisma/schema.prisma         Provider, Product, SearchQuery, SearchResult,
                             PriceHistory, CacheEntry, User, Basket
tests/                       10 suites, 128 tests
```

**Why it is shaped this way**

- **`ShoppingProvider` is the only integration seam.** Everything downstream
  works on `ProductResult`, so a store can be added, swapped from demo to live,
  or switched off without touching the search pipeline or the UI.
- **The database is optional.** `getPrisma()` returns `null` without
  `DATABASE_URL` and every write is best-effort, so the app deploys with zero
  configuration and gains persistence when a database appears.
- **Failure is data, not an exception.** Provider errors become
  `ProviderOutcome` entries, which is what makes partial results possible.

## Quick start

Requires Node.js 20+.

```bash
cd pricefinder
npm install
cp .env.example .env      # optional — every value has a working default
npm run dev               # http://localhost:3000
```

No database, no API keys, no accounts. All six stores run in demo mode.

```bash
npm test          # 128 tests
npm run typecheck # tsc --noEmit
npm run lint      # eslint
npm run build     # production build
```

## Environment variables

Every variable is optional. Copy `.env.example` to `.env` and set what you need.
**Never commit `.env`** — it is git-ignored, and `.env.example` contains no real
keys.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | *(unset)* | PostgreSQL connection string. Unset ⇒ no persistence, in-memory cache. |
| `ENABLE_BLINKIT` … `ENABLE_INSTAMART` | `true` | Switch any store off without code changes. |
| `SEARCH_CACHE_TTL_SECONDS` | `300` | How long a (query + PIN code) result set is reused. |
| `PROVIDER_TIMEOUT_MS` | `6000` | Per-provider deadline. |
| `RATE_LIMIT_MAX` | `30` | Requests to `/api/search` per window, per IP. |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | Rate-limit window length. |
| `AMAZON_PAAPI_ACCESS_KEY` / `_SECRET_KEY` / `AMAZON_PARTNER_TAG` | *(unset)* | Amazon PA-API v5 → flips Amazon to **Live**. |
| `AMAZON_PAAPI_HOST` / `AMAZON_PAAPI_REGION` | `webservices.amazon.in` / `eu-west-1` | Marketplace selection. |
| `FLIPKART_AFFILIATE_ID` / `_TOKEN` | *(unset)* | Flipkart affiliate API → flips Flipkart to **Live**. |
| `BLINKIT_*`, `ZEPTO_*`, `BIGBASKET_*`, `INSTAMART_*` | *(unset)* | Reserved for partner API access (see below). |
| `DEMO_FAILING_PROVIDERS` | *(empty)* | Dev aid: force stores to fail, e.g. `zepto,flipkart`. |
| `DEMO_MIN_LATENCY_MS` / `DEMO_MAX_LATENCY_MS` | `120` / `600` | Simulated demo latency. |

Secrets are read server-side only (`src/lib/env.ts`), are never sent to the
browser, and the logger redacts any context key matching
`key|secret|token|password|authorization|cookie|credential`.

## Database setup

The app works without one. To enable search history, price history and a
cross-instance cache:

```bash
# 1. Point at a PostgreSQL database
echo 'DATABASE_URL="postgresql://user:pass@host:5432/pricefinder?schema=public"' >> .env

# 2. Apply the schema
npx prisma migrate deploy     # production
npm run prisma:migrate        # development (creates a new migration)

# 3. Optional: pre-populate the provider table
npm run db:seed
```

Models: `Provider`, `Product`, `SearchQuery`, `SearchResult`, `PriceHistory`,
`CacheEntry`, plus `User`, `Basket` and `BasketItem`, which nothing reads yet —
they exist so that adding authentication and saved baskets later is a migration
rather than a redesign.

`PriceHistory` is written on every search (one row per offer), which is the data
a future "tomato is up 12% this week" feature needs. `getPriceHistory()` in
`services/persistence.ts` is the read seam.

## Live vs demo

| Store | Status | Why |
| --- | --- | --- |
| **Amazon** | **Live-capable** | Product Advertising API v5 — Amazon's documented, authorised interface. Needs an approved Associates account with qualifying sales. |
| **Flipkart** | **Live-capable** | Affiliate/Commerce API — documented, token-authenticated. Needs an approved affiliate account. |
| Blinkit | Demo | No public product-search API for third parties. |
| Zepto | Demo | No public product-search API for third parties. |
| BigBasket | Demo | Affiliate programmes provide deep links and commissions, not a searchable catalogue API. |
| Swiggy Instamart | Demo | Swiggy's public developer surface covers partner/restaurant integrations, not Instamart retail search. |

**What this project does not do:** no scraping of storefronts, no undocumented
or private endpoints, no CAPTCHA solving, no bot-protection evasion, no
robots.txt circumvention. A store without authorised access stays in demo mode.
That is a deliberate product decision, not a missing feature.

**How demo data behaves.** It is generated from a hand-written catalogue
(`src/providers/demo/catalog.ts`) with plausible 2024–25 Indian retail prices,
varied per store by a seeded PRNG so results are deterministic for a given
(store, query, PIN code). Every demo row is tagged `dataSource: "demo"`, renders
a **Demo data** badge, and its "Open" link goes to the store's own public search
page — never a fabricated product URL.

## Connecting live providers

A provider goes live the moment its required environment variables are all
present. No code change, no redeploy flag, no UI change — the badge flips from
**Demo data** to **Live** on its own.

**Amazon** (`src/providers/amazon.ts`)
1. Join the [Amazon Associates](https://affiliate-program.amazon.in/) programme
   and get approved. PA-API access requires qualifying sales.
2. Create PA-API credentials, then set `AMAZON_PAAPI_ACCESS_KEY`,
   `AMAZON_PAAPI_SECRET_KEY`, `AMAZON_PARTNER_TAG`.
3. `searchLive` is implemented, including the AWS SigV4 signing PA-API mandates.
   It has **not** been exercised against a live key here — verify the response
   mapping before trusting it in production.

**Flipkart** (`src/providers/flipkart.ts`)
1. Apply to the [Flipkart affiliate programme](https://affiliate.flipkart.com/);
   availability has varied over time — check current terms.
2. Set `FLIPKART_AFFILIATE_ID` and `FLIPKART_AFFILIATE_TOKEN`.
3. `searchLive` follows the documented `search.json` response shape; likewise
   unverified against live credentials.

**Blinkit / Zepto / BigBasket / Instamart**
These need a commercial catalogue or partner agreement with the retailer. Each
adapter carries a `TODO(live)` naming exactly what is required. Once you have
documented access: set the store's `*_API_BASE_URL` and `*_API_KEY`, then
implement `searchLive` in that one file. Nothing else changes.

**Provider logos.** Retailer logos are trademarks. The UI uses coloured text
wordmarks (`src/components/ProviderBadge.tsx`) rather than reproducing marks
without permission. Swap in real assets only once licensed to do so, and add the
image host to `next.config.ts` → `images.remotePatterns`.

## Adding a new provider

Five steps, one of them optional:

1. **Write the adapter** — `src/providers/mystore.ts`:
   ```ts
   export class MyStoreProvider extends BaseProvider {
     readonly id: Provider = 'mystore';
     readonly name = 'My Store';
     readonly websiteUrl = 'https://mystore.example';
     readonly brandColor = '#123456';
     protected readonly requiredEnv = ['MYSTORE_API_KEY'] as const;

     liveRequirement() { return 'Needs a My Store partner API key (MYSTORE_API_KEY).'; }

     protected async searchLive(query: string, location: Location) {
       // Call the authorised API, map into ProductResult[].
     }
   }
   ```
   Omit `searchLive` and it serves demo data until you write it.
2. **Register it** — add to `ALL_PROVIDERS` in `src/providers/registry.ts`.
3. **Add the id** — to `PROVIDER_IDS` in `src/providers/types.ts` and to the
   `ProviderId` enum in `prisma/schema.prisma` (then migrate).
4. **Document the toggle** — `ENABLE_MYSTORE` in `.env.example`.
5. *(Demo only)* give it a profile in `src/providers/demo/engine.ts`.

Normalisation, matching, sorting, caching, error handling and the UI all pick it
up automatically.

## API documentation

### `GET /api/search`

| Parameter | Required | Default | Notes |
| --- | --- | --- | --- |
| `q` | yes | — | 2–100 chars after sanitisation |
| `pincode` | no | `500001` | 6 digits, not starting with 0 |
| `sort` | no | `price_per_unit` | `price` · `price_per_unit` · `delivery` · `rating` · `provider` |
| `providers` | no | all enabled | Comma-separated subset, e.g. `zepto,blinkit` |
| `latitude` / `longitude` | no | — | Optional GPS hint; PIN code still drives availability |
| `refresh` | no | `false` | `1`/`true` skips the cache read |

```bash
curl "http://localhost:3000/api/search?q=tomato&pincode=500001"
```

```jsonc
{
  "query": "tomato",
  "location": { "pincode": "500001" },
  "sort": "price_per_unit",
  "results": [
    {
      "matchKey": "tomato|kg",
      "title": "Tomato",
      "unitBasis": "kg",
      "offers": [
        {
          "provider": "zepto",
          "providerName": "Zepto",
          "title": "Tomato - 1 kg",
          "price": 36,
          "currency": "INR",
          "quantity": 1,
          "unit": "kg",
          "pricePerUnit": 36,
          "unitPriceLabel": "₹36/kg",
          "availability": "available",
          "deliveryTimeMinutes": 12,
          "dataSource": "demo",          // "live" once an authorised API is wired up
          "productUrl": "https://www.zeptonow.com/search?query=Tomato",
          "fetchedAt": "2026-08-19T21:04:08.136Z"
        }
      ],
      "bestOffer": { "provider": "zepto", "price": 36 }
    }
  ],
  "providers": [
    { "provider": "blinkit", "name": "Blinkit", "status": "ok", "dataSource": "demo", "productsFound": 5, "durationMs": 142 },
    { "provider": "zepto", "name": "Zepto", "status": "error", "dataSource": "demo", "productsFound": 0, "durationMs": 3, "message": "Zepto unavailable right now" }
  ],
  "summary": {
    "providersSearched": 6,
    "providersSucceeded": 5,
    "providersFailed": 1,
    "productsFound": 25,
    "lowestPrice": 22,               // cheapest absolute price anywhere in results
    "lowestPricePerUnit": 36,        // cheapest normalised unit price anywhere
    "lowestPricePerUnitLabel": "₹36/kg",
    "bestProvider": "zepto",         // best store for the leading (most relevant) product
    "bestProviderName": "Zepto",
    "cached": false,
    "durationMs": 429,
    "demoOnly": true
  }
}
```

`200` even when providers fail — check `providers[]` for per-store status.
`400` invalid input (with `details` per field) · `429` rate limited (with
`Retry-After`) · `500` unexpected failure.

### `POST /api/search`
Same fields as a JSON body: `{ "q": "milk", "pincode": "110001" }`.

### `POST /api/basket`
Compares a whole shopping list. Not yet surfaced in the UI.
```bash
curl -X POST http://localhost:3000/api/basket \
  -H 'content-type: application/json' \
  -d '{"items":["tomato 1kg","milk 1L","eggs 12"],"pincode":"500001"}'
```
Returns a per-store total, how many lines each store could fill, and
`cheapest` — the store with the fewest missing items, then the lowest total.

### `GET /api/providers`
Every store with `enabled`, `dataSource` (`live`/`demo`) and `liveRequirement`.
Never exposes credential values.

### `GET /api/health`
`{ status, database, cache, providersEnabled }`.

## Testing

```bash
npm test
```

128 tests across 10 suites, covering exactly the areas the brief called out:

| Suite | Covers |
| --- | --- |
| `priceNormalizer` | Pack-size parsing, unit conversion, ₹/kg · ₹/L · ₹/piece — including `500 g @ ₹25 → ₹50/kg` and `1 L @ ₹60 → ₹60/L` |
| `productMatcher` | Cross-store grouping; organic vs regular and iPhone 128 GB vs 256 GB staying **separate** |
| `searchService` | Fan-out, partial failure, total failure, concurrency, provider subsets |
| `searchApi` | Route handlers: response shape, validation, rate limiting, per-provider errors |
| `providers` | Registry, `ENABLE_*` toggles, live/demo switching, timeouts, failures |
| `cacheService` | Hit, miss, TTL expiry, LRU eviction, key construction |
| `sorting` | All five orders, out-of-stock sinking, missing-metric handling |
| `validation` | Sanitisation (markup, control characters), PIN codes, baskets |
| `rateLimit`, `format` | Windows, per-client isolation; ₹ formatting, Indian digit grouping |

Tests run with no database, which is the same path a fresh clone takes.

## Deployment

Deploys to Vercel with no configuration:

1. Import the repository, set the root directory to `pricefinder/`.
2. Deploy. That is the whole list — all six stores run in demo mode.

To add persistence, set `DATABASE_URL` (Vercel Postgres, Neon, Supabase…) and
run `npx prisma migrate deploy` against it. `postinstall` runs `prisma generate`
so the client is built during Vercel's install step.

To go live on Amazon or Flipkart, add their credentials as environment variables
and redeploy — nothing else changes.

## Limitations

Worth knowing before this goes anywhere near real users:

- **Four of six stores are demo data.** Blinkit, Zepto, BigBasket and Instamart
  prices are invented. They are badged as such everywhere, but the app is not
  useful for real shopping decisions on those stores until partner access
  exists.
- **The two live integrations are unverified.** Amazon PA-API and Flipkart
  affiliate `searchLive` implementations follow the documented contracts but
  have never run against real credentials here.
- **PIN code is not a serviceability check.** It varies the demo data and is
  passed to live APIs, but the app does not verify a store actually delivers to
  that PIN code.
- **Rate limiting is per-instance.** In-memory by design (zero config); on
  Vercel's serverless runtime each instance counts separately. Move to Redis or
  Upstash for a real limit — `checkRateLimit` is the seam.
- **Caching is per-instance without a database.** With `DATABASE_URL` set the
  cache is shared; without it, each instance warms its own.
- **Product matching is conservative.** It groups by an exact normalised
  signature. A fuzzy pass was removed because at any useful threshold it merged
  "iPhone 16" with "iPhone 16 Pro". Expect occasional duplicate rows for the
  same item rather than wrong merges.
- **No authentication.** By design for the MVP. `User`, `Basket` and the
  optional `userId` on `SearchQuery` are in place for when it is added.
- **English/Latin-script titles only.** Matching lower-cases and de-pluralises
  with rules that assume Latin script.

## Next steps

1. **Pursue authorised access** for the four demo stores, or replace them with
   retailers that publish an API. This is the highest-value work by far.
2. **Verify the Amazon and Flipkart adapters** against real credentials and add
   contract tests with recorded fixtures.
3. **Ship basket comparison in the UI** — `/api/basket` and `basketService`
   already work; it needs a list builder and a results screen.
4. **Price history charts** — `PriceHistory` is already being written; add a
   read path and a sparkline per product.
5. **Serviceability by PIN code** — check whether a store delivers before
   showing its price.
6. **Shared rate limiting and cache** via Redis/Upstash for multi-instance
   deployments.
7. **Better matching** — a brand/variant dictionary, or embeddings, to raise
   recall without giving up the precision guarantee.
8. **Accessibility and i18n passes** — the UI is keyboard-navigable and
   theme-aware, but has not been audited with a screen reader, and Indian
   language product names are not handled.

---

Built with Next.js 16 · TypeScript · Tailwind CSS v4 · Prisma · PostgreSQL · Vitest.
