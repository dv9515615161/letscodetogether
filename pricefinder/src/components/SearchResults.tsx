'use client';

import { PriceComparison } from '@/components/PriceComparison';
import { ProductCard } from '@/components/ProductCard';
import { ProviderStatusList } from '@/components/ProviderStatusList';
import { SearchSkeleton } from '@/components/Skeletons';
import type { SearchResponse } from '@/types/search';

export function SearchResults({
  data,
  isLoading,
  error,
}: {
  data: SearchResponse | null;
  isLoading: boolean;
  error: string | null;
}) {
  if (isLoading) return <SearchSkeleton />;

  if (error) {
    return (
      <div
        role="alert"
        className="rounded-2xl border border-(--color-danger) bg-(--color-danger-soft) p-4 text-sm text-(--color-danger)"
      >
        {error}
      </div>
    );
  }

  if (!data) return null;

  const hasResults = data.results.length > 0;

  return (
    <div className="flex flex-col gap-4">
      {hasResults ? <PriceComparison summary={data.summary} leadGroup={data.results[0]} /> : null}

      <ProviderStatusList providers={data.providers} />

      {hasResults ? (
        <>
          <p className="text-sm text-(--color-ink-soft)">
            {data.summary.productsFound} offer{data.summary.productsFound === 1 ? '' : 's'} for{' '}
            <span className="font-semibold text-(--color-ink)">{data.query}</span> near {data.location.pincode}
            {data.summary.cached ? ' · from cache' : ''} · {data.summary.durationMs} ms
          </p>
          <div className="flex flex-col gap-4">
            {data.results.map((group) => (
              <ProductCard key={group.matchKey} group={group} />
            ))}
          </div>
        </>
      ) : (
        <div className="rounded-2xl border border-(--color-line) bg-(--color-surface) p-6 text-center">
          <p className="font-semibold text-(--color-ink)">No results for “{data.query}”</p>
          <p className="mt-1 text-sm text-(--color-ink-soft)">
            Try a simpler term, for example “tomato” instead of a brand and size together.
          </p>
        </div>
      )}
    </div>
  );
}
