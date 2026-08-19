'use client';

import { useState } from 'react';

const PINCODE_PATTERN = /^[1-9][0-9]{5}$/;

/**
 * PIN code entry. GPS is offered but never required — the MVP must work
 * without a location permission prompt, and browsers only expose coordinates,
 * not a PIN code, so the geolocation button fills in coordinates alongside
 * whatever PIN code the user typed.
 */
export function LocationSelector({
  pincode,
  onChange,
  onUseLocation,
  locating,
  locationNote,
}: {
  pincode: string;
  onChange: (pincode: string) => void;
  onUseLocation?: () => void;
  locating?: boolean;
  locationNote?: string;
}) {
  const [value, setValue] = useState(pincode);
  const [lastPincode, setLastPincode] = useState(pincode);
  const [touched, setTouched] = useState(false);

  // Adjust during render rather than in an effect (see SearchBar for why).
  if (pincode !== lastPincode) {
    setLastPincode(pincode);
    setValue(pincode);
  }

  const invalid = touched && !PINCODE_PATTERN.test(value);

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-end gap-2">
        <div className="flex-1">
          <label htmlFor="pincode" className="mb-1 block text-xs font-semibold uppercase tracking-wide text-(--color-ink-soft)">
            PIN code
          </label>
          <input
            id="pincode"
            name="pincode"
            type="text"
            inputMode="numeric"
            autoComplete="postal-code"
            maxLength={6}
            placeholder="500001"
            value={value}
            onBlur={() => setTouched(true)}
            onChange={(event) => {
              const next = event.target.value.replace(/\D/g, '').slice(0, 6);
              setValue(next);
              if (PINCODE_PATTERN.test(next)) onChange(next);
            }}
            aria-invalid={invalid}
            aria-describedby={invalid ? 'pincode-error' : undefined}
            className={`w-full rounded-xl border bg-(--color-surface) px-4 py-3 text-base tracking-[0.2em] text-(--color-ink) shadow-sm ${
              invalid ? 'border-(--color-danger)' : 'border-(--color-line)'
            }`}
          />
        </div>
        {onUseLocation ? (
          <button
            type="button"
            onClick={onUseLocation}
            disabled={locating}
            className="shrink-0 rounded-xl border border-(--color-line) bg-(--color-surface) px-4 py-3 text-sm font-medium text-(--color-ink-soft) transition-colors hover:text-(--color-ink) disabled:opacity-50"
          >
            {locating ? 'Locating…' : 'Use GPS'}
          </button>
        ) : null}
      </div>
      {invalid ? (
        <p id="pincode-error" className="text-xs text-(--color-danger)">
          Enter a valid 6-digit PIN code.
        </p>
      ) : locationNote ? (
        <p className="text-xs text-(--color-ink-faint)">{locationNote}</p>
      ) : null}
    </div>
  );
}
