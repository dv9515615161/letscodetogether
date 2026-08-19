/** Loading placeholders shown while providers are being queried. */

function Shimmer({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded bg-(--color-surface-muted) ${className}`} />;
}

export function ProductCardSkeleton() {
  return (
    <div className="rounded-2xl border border-(--color-line) bg-(--color-surface) p-3 shadow-sm sm:p-4">
      <div className="flex items-center gap-3">
        <Shimmer className="size-14 rounded-xl" />
        <div className="flex-1 space-y-2">
          <Shimmer className="h-4 w-2/5" />
          <Shimmer className="h-3 w-1/4" />
        </div>
      </div>
      <div className="mt-3 space-y-2">
        {[0, 1, 2].map((index) => (
          <div key={index} className="rounded-xl border border-(--color-line) p-3">
            <Shimmer className="h-3 w-24" />
            <Shimmer className="mt-2 h-6 w-28" />
            <Shimmer className="mt-2 h-3 w-40" />
          </div>
        ))}
      </div>
    </div>
  );
}

export function SearchSkeleton({ count = 2 }: { count?: number }) {
  return (
    <div className="flex flex-col gap-4" aria-busy="true" aria-live="polite">
      <span className="sr-only">Searching stores…</span>
      <Shimmer className="h-20 rounded-2xl" />
      {Array.from({ length: count }, (_, index) => (
        <ProductCardSkeleton key={index} />
      ))}
    </div>
  );
}
