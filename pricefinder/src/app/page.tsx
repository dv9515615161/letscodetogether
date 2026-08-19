import { Suspense } from 'react';
import { SearchExperience } from '@/components/SearchExperience';
import { SearchSkeleton } from '@/components/Skeletons';

/**
 * Home page. The interactive shell is a client component because search state
 * lives in the URL; `useSearchParams` requires a Suspense boundary.
 */
export default function HomePage() {
  return (
    <main>
      <Suspense
        fallback={
          <div className="mx-auto w-full max-w-2xl px-4 pt-12">
            <SearchSkeleton count={1} />
          </div>
        }
      >
        <SearchExperience />
      </Suspense>
    </main>
  );
}
