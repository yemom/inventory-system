import React from 'react';
import { AlertCircle, ShieldOff } from 'lucide-react';
import Button from './Button';

interface ErrorStateProps {
  title?: string;
  message?: string;
  /**
   * The error itself, when the caller has one. Preferred over `message` because
   * it lets this component tell a *refusal* apart from a *failure*.
   *
   * That distinction matters: before this existed, pages rendered
   * `<ErrorState retry={...} />` with no message, so a correctly-denied 403 and a
   * genuine 500 both read "Something went wrong / Failed to load data". A user
   * whose role lacks a permission was told the application was broken, and the
   * "Try Again" button invited them to keep retrying something that was never
   * going to succeed.
   */
  error?: unknown;
  retry?: () => void;
}

function messageOf(error: unknown): string {
  if (error == null) return '';
  if (typeof error === 'string') return error;
  if (error instanceof Error) return error.message;
  return String(error);
}

/**
 * True when the failure was the server refusing on authority grounds rather
 * than failing.
 *
 * Phrased as a loose list on purpose, because the wording arrives from four
 * different layers and the job is only to recognise "you are not allowed":
 *
 * - our Axios interceptor: "You don't have permission to do that…"
 * - our AccessDeniedException handler: "Access denied. You do not have permission."
 * - Spring Security's own: "Missing authority INVENTORY_READ"
 * - a bare "Forbidden" / "Access is denied"
 *
 * Deliberately does not match a stack trace or a generic 500, so a real fault is
 * still reported as a fault.
 */
function isDenial(message: string): boolean {
  return /permission|authoris|authoriz|access (is )?denied|forbidden|missing authority|not permitted|unauthori/i.test(
    message,
  );
}

export default function ErrorState({
  title,
  message,
  error,
  retry,
}: ErrorStateProps) {
  const derived = message ?? messageOf(error);
  const denied = isDenial(derived);

  const resolvedTitle = title ?? (denied ? 'You do not have access to this' : 'Something went wrong');
  const resolvedMessage = derived || (denied
    ? 'Your role does not include permission to view this page.'
    : 'Failed to load data. Please try again.');

  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      {denied
        ? <ShieldOff size={48} className="text-amber-400 mb-4" />
        : <AlertCircle size={48} className="text-red-400 mb-4" />}
      <h3 className="text-base font-semibold text-gray-700 dark:text-gray-300 mb-1">{resolvedTitle}</h3>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-6 max-w-md">{resolvedMessage}</p>
      {/* Retrying is only useful when the request might succeed next time. */}
      {retry && !denied && <Button variant="outline" onClick={retry}>Try Again</Button>}
    </div>
  );
}
