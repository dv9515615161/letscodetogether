'use client';

import { SORT_OPTIONS, type SortOption } from '@/lib/validation';

const LABELS: Record<SortOption, string> = {
  price_per_unit: 'Lowest price per unit',
  price: 'Lowest price',
  delivery: 'Fastest delivery',
  rating: 'Highest rating',
  provider: 'Store name',
};

/** Sorting is applied server-side from cache, so switching costs no API calls. */
export function SortSelect({ value, onChange }: { value: SortOption; onChange: (sort: SortOption) => void }) {
  return (
    <div className="flex items-center gap-2">
      <label htmlFor="sort" className="text-xs font-semibold uppercase tracking-wide text-(--color-ink-soft)">
        Sort
      </label>
      <select
        id="sort"
        value={value}
        onChange={(event) => onChange(event.target.value as SortOption)}
        className="flex-1 rounded-lg border border-(--color-line) bg-(--color-surface) px-3 py-2 text-sm text-(--color-ink) sm:flex-none"
      >
        {SORT_OPTIONS.map((option) => (
          <option key={option} value={option}>
            {LABELS[option]}
          </option>
        ))}
      </select>
    </div>
  );
}
