'use client';

import { formatDeliveryTime, formatDiscount, formatQuantity, formatRupees } from '@/lib/format';
import type { SearchOffer } from '@/types/search';
import { DataSourceBadge, ProviderBadge } from '@/components/ProviderBadge';

/**
 * One store's offer for a product — the row a shopper actually compares.
 * Laid out as a stacked card on phones and a single row from `sm` up.
 */
export function ProviderCard({ offer, isBest }: { offer: SearchOffer; isBest: boolean }) {
  const outOfStock = offer.availability === 'out_of_stock';
  const discount = formatDiscount(offer.price, offer.originalPrice);
  const packSize = formatQuantity(offer.quantity, offer.unit);
  const delivery = formatDeliveryTime(offer.deliveryTimeMinutes);

  return (
    <li
      className={`rounded-xl border p-3 transition-colors sm:p-4 ${
        isBest
          ? 'border-(--color-best) bg-(--color-best-soft)'
          : 'border-(--color-line) bg-(--color-surface)'
      } ${outOfStock ? 'opacity-60' : ''}`}
    >
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <ProviderBadge provider={offer.provider} name={offer.providerName} />
        <DataSourceBadge dataSource={offer.dataSource} />
        {isBest && !outOfStock ? (
          <span className="rounded-full bg-(--color-best) px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-white">
            Best price
          </span>
        ) : null}
      </div>

      <p className="mt-1 line-clamp-2 text-sm text-(--color-ink-soft)">{offer.title}</p>

      <div className="mt-2 flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight text-(--color-ink)">{formatRupees(offer.price)}</span>
            {offer.originalPrice ? (
              <span className="text-sm text-(--color-ink-faint) line-through">{formatRupees(offer.originalPrice)}</span>
            ) : null}
            {discount ? (
              <span className="rounded bg-(--color-best-soft) px-1.5 py-0.5 text-xs font-semibold text-(--color-best)">
                {discount}% off
              </span>
            ) : null}
          </div>

          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-(--color-ink-soft)">
            {offer.unitPriceLabel ? <span className="font-semibold">{offer.unitPriceLabel}</span> : null}
            {packSize ? <span>{packSize}</span> : null}
            <span className={outOfStock ? 'font-medium text-(--color-danger)' : ''}>
              {outOfStock ? 'Out of stock' : offer.availability === 'available' ? 'Available' : 'Availability unknown'}
            </span>
            {delivery ? <span>Delivery {delivery}</span> : null}
            {offer.rating ? (
              <span>
                {offer.rating.toFixed(1)}★
                {offer.reviewCount ? ` (${offer.reviewCount.toLocaleString('en-IN')})` : ''}
              </span>
            ) : null}
          </div>
        </div>

        {offer.productUrl ? (
          <a
            href={offer.productUrl}
            target="_blank"
            // noopener/noreferrer: these are third-party destinations.
            rel="noopener noreferrer nofollow"
            className="shrink-0 rounded-lg border border-(--color-brand) px-4 py-2 text-sm font-semibold text-(--color-brand) transition-colors hover:bg-(--color-brand) hover:text-white"
          >
            Open {offer.providerName}
          </a>
        ) : null}
      </div>

    </li>
  );
}
