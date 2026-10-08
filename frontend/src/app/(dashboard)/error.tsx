'use client';

import ErrorBoundary from '@/components/ui/ErrorBoundary';

/**
 * Dashboard route-segment error boundary.
 *
 * <p>Next.js applies this to every route under `app/(dashboard)/`. Without it,
 * an uncaught render error anywhere in the dashboard replaces the whole
 * application — sidebar included — with a blank page.
 *
 * <p>Next hands us the error it already caught, so it is passed straight to the
 * boundary rather than re-thrown; `reset` is forwarded too, so "Try again" asks
 * Next to re-render the segment rather than only clearing local state.
 */
export default function DashboardError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <ErrorBoundary externalError={error} onReset={reset}>
      <div />
    </ErrorBoundary>
  );
}
