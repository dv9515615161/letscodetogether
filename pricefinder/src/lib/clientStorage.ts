'use client';

import { useSyncExternalStore } from 'react';

/**
 * Reads a localStorage value without a syncing effect.
 *
 * `useSyncExternalStore` is the React-sanctioned way to read a browser store:
 * the server snapshot keeps server and hydration renders identical, and React
 * re-renders with the real value immediately afterwards, so there is no
 * hydration mismatch and no cascading render.
 */

const listeners = new Set<() => void>();

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  // `storage` fires for other tabs; the local set below covers this one.
  window.addEventListener('storage', listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('storage', listener);
  };
}

export function writeLocalValue(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Private-browsing modes can refuse writes; remembering a PIN code is a
    // convenience, so failing quietly is the right behaviour.
  }
  for (const listener of listeners) listener();
}

export function useLocalValue(key: string): string | null {
  return useSyncExternalStore(
    subscribe,
    () => {
      try {
        return window.localStorage.getItem(key);
      } catch {
        return null;
      }
    },
    () => null,
  );
}
