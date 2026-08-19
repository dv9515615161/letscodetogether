'use client';

import Image from 'next/image';
import { ProviderCard } from '@/components/ProviderCard';
import type { SearchProductGroup } from '@/types/search';

/**
 * A matched product across stores: one heading, one image, and a stacked list
 * of per-store offers ordered by the active sort.
 */
export function ProductCard({ group }: { group: SearchProductGroup }) {
  const bestId = group.bestOffer?.id;
  const stores = new Set(group.offers.map((offer) => offer.provider)).size;
  const hasDemo = group.offers.some((offer) => offer.dataSource === 'demo');

  return (
    <article className="rounded-2xl border border-(--color-line) bg-(--color-surface) p-3 shadow-sm sm:p-4">
      <header className="flex items-center gap-3">
        {group.imageUrl ? (
          <Image
            src={group.imageUrl}
            alt=""
            width={56}
            height={56}
            // Demo images are inline SVG data URIs; skip the optimizer for them.
            unoptimized
            className="size-14 shrink-0 rounded-xl border border-(--color-line) object-cover"
          />
        ) : (
          <div aria-hidden className="size-14 shrink-0 rounded-xl bg-(--color-surface-muted)" />
        )}
        <div className="min-w-0">
          <h2 className="truncate text-base font-bold text-(--color-ink) sm:text-lg">{group.title}</h2>
          <p className="text-xs text-(--color-ink-soft)">
            {group.offers.length} offer{group.offers.length === 1 ? '' : 's'} across {stores} store
            {stores === 1 ? '' : 's'}
          </p>
        </div>
      </header>

      <ul className="mt-3 flex flex-col gap-2">
        {group.offers.map((offer) => (
          <ProviderCard key={`${offer.provider}-${offer.id}`} offer={offer} isBest={offer.id === bestId} />
        ))}
      </ul>

      {hasDemo ? (
        // Once per product rather than once per offer: the per-offer "Demo data"
        // badge already carries the warning, and repeating this paragraph on
        // every row buried the prices it was meant to qualify.
        <p className="mt-3 text-[11px] leading-snug text-(--color-ink-faint)">
          Rows marked <span className="font-semibold">Demo data</span> are sample prices. Their links open the store&apos;s
          own search page so you can check the real price.
        </p>
      ) : null}
    </article>
  );
}
