import type { DataSource, Provider } from '@/providers/types';

/**
 * Store identity as a text label rather than a logo.
 *
 * Retailer logos are trademarks; reproducing them needs permission we do not
 * have, so the MVP uses a coloured wordmark. `README > Provider logos` covers
 * how to swap in real assets once you are licensed to.
 */
const BRAND_COLORS: Record<Provider, string> = {
  blinkit: '#E5A500',
  zepto: '#7B2CBF',
  amazon: '#D97706',
  flipkart: '#2874F0',
  bigbasket: '#4B8B0F',
  instamart: '#FC8019',
};

export function ProviderBadge({ provider, name }: { provider: Provider; name: string }) {
  return (
    <span
      className="inline-flex items-center gap-1.5 text-sm font-semibold"
      style={{ color: BRAND_COLORS[provider] }}
    >
      <span aria-hidden className="size-2 rounded-full" style={{ backgroundColor: BRAND_COLORS[provider] }} />
      {name}
    </span>
  );
}

/**
 * The LIVE / DEMO marker. This is a product requirement, not decoration: sample
 * prices must never look like real ones.
 */
export function DataSourceBadge({ dataSource, className = '' }: { dataSource: DataSource; className?: string }) {
  const isLive = dataSource === 'live';
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide ${
        isLive ? 'bg-(--color-best-soft) text-(--color-best)' : 'bg-(--color-warn-soft) text-(--color-warn)'
      } ${className}`}
      title={isLive ? 'Live data from an authorised API' : 'Sample data for demonstration — not a real price'}
    >
      {isLive ? 'Live' : 'Demo data'}
    </span>
  );
}
