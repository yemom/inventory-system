'use client';

import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import Button from '@/components/ui/Button';

/**
 * A render-error boundary that degrades to a recoverable message.
 *
 * <p>Without one, an uncaught render error unmounts the whole React tree and
 * leaves a blank page with nothing in it. A user cannot report "the page went
 * blank" usefully and cannot screenshot their way out of it.
 *
 * <p>This does not replace handling errors where you expect them. It exists so a
 * single malformed row cannot take the application down, and so the failure is
 * visible and recoverable.
 *
 * <p>Used two ways:
 * <ul>
 *   <li>wrapped around content, catching anything thrown during render;</li>
 *   <li>given an `externalError`, which is how Next.js's own `error.tsx` hook
 *       passes in the error it already caught.</li>
 * </ul>
 */
interface ErrorBoundaryProps {
  children: React.ReactNode;
  /** Shown above the generic message, e.g. "Sales". */
  label?: string;
  /** An error already caught elsewhere, e.g. by the Next.js segment boundary. */
  externalError?: Error | null;
  /** Overrides the internal reset, so a host can supply its own recovery. */
  onReset?: () => void;
}

interface ErrorBoundaryState {
  error: Error | null;
}

export default class ErrorBoundary extends React.Component<
  ErrorBoundaryProps,
  ErrorBoundaryState
> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    // The console is the only place this can be seen, so log it even though the
    // UI deliberately avoids showing raw internals.
    console.error(
      '[ErrorBoundary]',
      this.props.label ?? 'app',
      error,
      info.componentStack,
    );
  }

  private reset = () => {
    if (this.props.onReset) {
      this.props.onReset();
      return;
    }
    this.setState({ error: null });
  };

  render(): React.ReactNode {
    const { children, label, externalError } = this.props;
    const error = this.state.error ?? externalError ?? null;

    if (!error) return children;

    return (
      <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
        <AlertTriangle size={48} className="text-amber-400 mb-4" />

        <h2 className="text-base font-semibold text-gray-700 dark:text-gray-300 mb-1">
          {label
            ? `${label} could not be displayed`
            : 'This page could not be displayed'}
        </h2>

        <p className="text-sm text-gray-500 dark:text-gray-400 mb-2 max-w-md">
          Something in the data this page received was not in the expected shape.
          The rest of the application is unaffected.
        </p>

        {/* The message only. A stack trace is noise to a reader and can leak
            internals; it is in the console for whoever is debugging. */}
        <p className="text-xs text-gray-400 dark:text-gray-500 mb-6 font-mono max-w-lg break-words">
          {error.message}
        </p>

        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={this.reset}>
            <RefreshCw size={15} /> Try again
          </Button>
          <Button variant="ghost" onClick={() => window.location.reload()}>
            Reload page
          </Button>
        </div>
      </div>
    );
  }
}
