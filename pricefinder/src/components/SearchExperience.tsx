'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useLocalValue, writeLocalValue } from '@/lib/clientStorage';
import { LocationSelector } from '@/components/LocationSelector';
import { SearchBar } from '@/components/SearchBar';
import { SearchResults } from '@/components/SearchResults';
import { SortSelect } from '@/components/SortSelect';
import { DEFAULT_PINCODE, DEFAULT_SORT, SORT_OPTIONS, type SortOption } from '@/lib/validation';
import type { SearchResponse } from '@/types/search';

const QUICK_SEARCHES = ['Tomato', 'Milk', 'Eggs', 'Rice', 'Bread', 'Washing powder'];
const PINCODE_STORAGE_KEY = 'pricefinder.pincode';
const PINCODE_PATTERN = /^[1-9][0-9]{5}$/;

function isSortOption(value: string | null): value is SortOption {
  return value !== null && (SORT_OPTIONS as readonly string[]).includes(value);
}

/**
 * The whole search flow.
 *
 * State lives in the URL so a comparison can be shared or reloaded, and the
 * PIN code is remembered locally so a returning shopper does not retype it.
 */
export function SearchExperience() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const urlQuery = searchParams.get('q') ?? '';
  const urlPincode = searchParams.get('pincode') ?? '';
  const urlSort = searchParams.get('sort');

  // PIN code precedence: what the user last chose here, then the URL, then a
  // remembered value from a previous visit, then the default.
  const rememberedPincode = useLocalValue(PINCODE_STORAGE_KEY);
  const [chosenPincode, setChosenPincode] = useState<string | null>(
    PINCODE_PATTERN.test(urlPincode) ? urlPincode : null,
  );
  const pincode =
    chosenPincode ??
    (rememberedPincode && PINCODE_PATTERN.test(rememberedPincode) ? rememberedPincode : DEFAULT_PINCODE);

  const [sort, setSort] = useState<SortOption>(isSortOption(urlSort) ? urlSort : DEFAULT_SORT);
  const [data, setData] = useState<SearchResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);
  const [locationNote, setLocationNote] = useState<string | undefined>();

  const inFlight = useRef<AbortController | null>(null);

  const runSearch = useCallback(
    async (query: string, nextPincode: string, nextSort: SortOption) => {
      const trimmed = query.trim();
      if (trimmed.length < 2) return;

      inFlight.current?.abort();
      const controller = new AbortController();
      inFlight.current = controller;

      setIsLoading(true);
      setError(null);

      const params = new URLSearchParams({ q: trimmed, pincode: nextPincode, sort: nextSort });
      // Keep the address bar in step without adding a history entry per keystroke.
      router.replace(`/?${params.toString()}`, { scroll: false });

      try {
        const response = await fetch(`/api/search?${params.toString()}`, { signal: controller.signal });
        const payload = (await response.json()) as SearchResponse | { error?: string };

        if (!response.ok) {
          setData(null);
          setError('error' in payload && payload.error ? payload.error : 'Search failed. Please try again.');
          return;
        }

        setData(payload as SearchResponse);
      } catch (caught) {
        if (caught instanceof DOMException && caught.name === 'AbortError') return;
        setData(null);
        setError('Could not reach the search service. Check your connection and try again.');
      } finally {
        if (inFlight.current === controller) {
          inFlight.current = null;
          setIsLoading(false);
        }
      }
    },
    [router],
  );

  // Run the search carried in the URL on first load, so a shared link works.
  const bootstrapped = useRef(false);
  useEffect(() => {
    if (bootstrapped.current || !urlQuery) return;
    bootstrapped.current = true;
    void runSearch(urlQuery, urlPincode || pincode, sort);
  }, [urlQuery, urlPincode, pincode, sort, runSearch]);

  useEffect(() => () => inFlight.current?.abort(), []);

  const handlePincodeChange = (next: string) => {
    setChosenPincode(next);
    writeLocalValue(PINCODE_STORAGE_KEY, next);
    if (data) void runSearch(data.query, next, sort);
  };

  const handleSortChange = (next: SortOption) => {
    setSort(next);
    if (data) void runSearch(data.query, pincode, next);
  };

  /**
   * GPS is strictly optional. Browsers give coordinates, not a PIN code, so we
   * pass the coordinates through to the API and leave the typed PIN code alone.
   */
  const handleUseLocation = () => {
    if (!('geolocation' in navigator)) {
      setLocationNote('This browser cannot share a location. Enter a PIN code instead.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setLocationNote(
          `Location shared (${position.coords.latitude.toFixed(2)}, ${position.coords.longitude.toFixed(2)}). PIN code still drives store availability.`,
        );
      },
      () => {
        setLocating(false);
        setLocationNote('Location permission denied. Enter a PIN code instead.');
      },
      { timeout: 8000, maximumAge: 300_000 },
    );
  };

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-5 px-4 pb-16 pt-8 sm:pt-12">
      <header className="text-center">
        <h1 className="text-3xl font-black tracking-tight text-(--color-ink) sm:text-4xl">PriceFinder</h1>
        <p className="mt-1 text-sm text-(--color-ink-soft) sm:text-base">Compare prices across stores</p>
      </header>

      <div className="flex flex-col gap-3 rounded-2xl border border-(--color-line) bg-(--color-surface) p-4 shadow-sm">
        <SearchBar
          defaultValue={data?.query ?? urlQuery}
          isSearching={isLoading}
          onSearch={(query) => void runSearch(query, pincode, sort)}
        />
        <LocationSelector
          pincode={pincode}
          onChange={handlePincodeChange}
          onUseLocation={handleUseLocation}
          locating={locating}
          locationNote={locationNote}
        />
        <SortSelect value={sort} onChange={handleSortChange} />
      </div>

      <nav aria-label="Quick searches" className="flex flex-wrap gap-2">
        {QUICK_SEARCHES.map((term) => (
          <button
            key={term}
            type="button"
            onClick={() => void runSearch(term, pincode, sort)}
            className="rounded-full border border-(--color-line) bg-(--color-surface) px-3 py-1.5 text-sm text-(--color-ink-soft) transition-colors hover:border-(--color-brand) hover:text-(--color-brand)"
          >
            {term}
          </button>
        ))}
      </nav>

      <SearchResults data={data} isLoading={isLoading} error={error} />

      {!data && !isLoading && !error ? (
        <section className="rounded-2xl border border-dashed border-(--color-line) p-6 text-center">
          <p className="text-sm text-(--color-ink-soft)">
            Search a product to compare it across Blinkit, Zepto, Swiggy Instamart, BigBasket, Amazon and Flipkart.
          </p>
        </section>
      ) : null}

      <footer className="mt-2 border-t border-(--color-line) pt-4 text-xs leading-relaxed text-(--color-ink-faint)">
        <p>
          Prices marked <span className="font-semibold text-(--color-warn)">Demo data</span> are sample values, not real
          store prices. Stores without an authorised public product API stay in demo mode until partner access is
          configured. See <code className="rounded bg-(--color-surface-muted) px-1">/api/providers</code> for the current
          live/demo status of each store.
        </p>
      </footer>
    </div>
  );
}
