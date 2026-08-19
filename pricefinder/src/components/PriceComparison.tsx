import { formatRupees } from '@/lib/format';
import type { SearchProductGroup, SearchSummary } from '@/types/search';

/**
 * The headline answer: which store is cheapest for the product the user most
 * likely meant. Uses the normalised unit price where one exists, because
 * "₹39 for 500 g" is not cheaper than "₹45 for 1 kg".
 */
export function PriceComparison({
  summary,
  leadGroup,
}: {
  summary: SearchSummary;
  leadGroup?: SearchProductGroup;
}) {
  const best = leadGroup?.bestOffer;
  if (!best) return null;

  return (
    <section
      aria-label="Best price"
      className="rounded-2xl border border-(--color-best) bg-(--color-best-soft) p-4 sm:p-5"
    >
      <p className="text-xs font-bold uppercase tracking-widest text-(--color-best)">Best price</p>
      <p className="mt-1 text-lg font-bold text-(--color-ink) sm:text-xl">
        {best.providerName} — {formatRupees(best.price)}
        {best.unitPriceLabel ? (
          <span className="font-semibold text-(--color-ink-soft)"> ({best.unitPriceLabel})</span>
        ) : null}
      </p>
      <p className="mt-1 text-sm text-(--color-ink-soft)">
        {leadGroup.title}
        {leadGroup.offers.length > 1 ? ` · compared across ${leadGroup.offers.length} stores` : ''}
      </p>
      {summary.demoOnly ? (
        <p className="mt-2 text-xs font-medium text-(--color-warn)">
          Every result shown is demo data — no live store API is connected yet.
        </p>
      ) : null}
    </section>
  );
}
