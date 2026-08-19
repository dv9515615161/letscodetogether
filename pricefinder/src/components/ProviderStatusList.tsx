import type { ProviderOutcome } from '@/providers/types';
import { DataSourceBadge } from '@/components/ProviderBadge';

/**
 * Per-provider status strip.
 *
 * Failures are shown here rather than swallowed: if Zepto times out the user
 * sees "Zepto took too long to respond" next to five sets of working results,
 * which is far more trustworthy than a silently shorter list.
 */
export function ProviderStatusList({ providers }: { providers: ProviderOutcome[] }) {
  if (providers.length === 0) return null;

  const failed = providers.filter((provider) => provider.status !== 'ok');

  return (
    <section aria-label="Store status" className="rounded-2xl border border-(--color-line) bg-(--color-surface) p-3 sm:p-4">
      <h2 className="text-xs font-bold uppercase tracking-widest text-(--color-ink-soft)">Stores searched</h2>
      <ul className="mt-2 flex flex-wrap gap-2">
        {providers.map((provider) => {
          const ok = provider.status === 'ok';
          return (
            <li
              key={provider.provider}
              className={`flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs ${
                ok
                  ? 'border-(--color-line) bg-(--color-surface-muted) text-(--color-ink-soft)'
                  : 'border-(--color-danger) bg-(--color-danger-soft) text-(--color-danger)'
              }`}
            >
              <span className="font-semibold text-(--color-ink)">{provider.name}</span>
              {ok ? (
                <>
                  <span>{provider.productsFound} found</span>
                  <DataSourceBadge dataSource={provider.dataSource} />
                </>
              ) : (
                <span>{provider.message}</span>
              )}
            </li>
          );
        })}
      </ul>
      {failed.length > 0 ? (
        <p className="mt-2 text-xs text-(--color-ink-faint)">
          {failed.length} store{failed.length === 1 ? '' : 's'} could not be reached. Results from the others are shown
          above.
        </p>
      ) : null}
    </section>
  );
}
