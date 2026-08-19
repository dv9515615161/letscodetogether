'use client';

import { useRef, useState } from 'react';

export function SearchBar({
  defaultValue,
  onSearch,
  isSearching,
}: {
  defaultValue: string;
  onSearch: (query: string) => void;
  isSearching: boolean;
}) {
  const [value, setValue] = useState(defaultValue);
  const [lastDefault, setLastDefault] = useState(defaultValue);
  const inputRef = useRef<HTMLInputElement>(null);

  // Keep the field in step when the query changes from elsewhere (a quick-search
  // chip, or the browser's back button). Adjusting state during render is
  // React's recommended alternative to a syncing effect: it re-renders before
  // the browser paints, with no flash of the stale value.
  if (defaultValue !== lastDefault) {
    setLastDefault(defaultValue);
    setValue(defaultValue);
  }

  return (
    <form
      className="flex w-full gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        const trimmed = value.trim();
        if (trimmed.length >= 2) {
          inputRef.current?.blur();
          onSearch(trimmed);
        }
      }}
      role="search"
    >
      <div className="relative flex-1">
        <label htmlFor="product-search" className="sr-only">
          Search products
        </label>
        <input
          ref={inputRef}
          id="product-search"
          name="q"
          type="search"
          inputMode="search"
          autoComplete="off"
          enterKeyHint="search"
          placeholder="Search products..."
          value={value}
          onChange={(event) => setValue(event.target.value)}
          // 16px minimum stops iOS Safari zooming on focus.
          className="w-full rounded-xl border border-(--color-line) bg-(--color-surface) px-4 py-3 text-base text-(--color-ink) placeholder:text-(--color-ink-faint) shadow-sm"
        />
      </div>
      <button
        type="submit"
        disabled={isSearching || value.trim().length < 2}
        className="shrink-0 rounded-xl bg-(--color-brand) px-5 py-3 text-base font-semibold text-white shadow-sm transition-colors hover:bg-(--color-brand-strong) disabled:cursor-not-allowed disabled:opacity-50"
      >
        {isSearching ? 'Searching' : 'Search'}
      </button>
    </form>
  );
}
