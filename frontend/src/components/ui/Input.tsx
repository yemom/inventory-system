import React, { useId } from 'react';
import { cn } from '@/lib/utils';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  /** Explanatory text below the field. Distinct from `error`: a hint is normal. */
  hint?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

/**
 * `forwardRef` is what makes this usable from `react-hook-form`.
 *
 * <p>React strips `ref` from the props of a plain function component before the
 * component body runs, so `{...register('amount')}` on this input passed RHF no
 * DOM node at all — focus-on-error and the field-level rules that depend on the
 * element silently did nothing, and React logged "Function components cannot be
 * given refs" for every one. Forwarding the ref hands RHF the real element.
 */
const Input = React.forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, hint, leftIcon, rightIcon, className, id, ...props },
  ref,
) {
  // useId gives a stable, unique id so the <label> can actually point at the
  // input. It previously had no `htmlFor` at all, which left every labelled
  // field in the app unassociated: clicking the label did not focus the control
  // and screen readers announced the text without the field it described.
  const generatedId = useId();
  const inputId = id ?? generatedId;

  return (
    <div className="w-full">
      {label && (
        <label htmlFor={inputId} className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          {label}
        </label>
      )}
      <div className="relative">
        {leftIcon && <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">{leftIcon}</span>}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? `${inputId}-help` : undefined}
          className={cn(
            'w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2 text-sm placeholder-gray-400 transition focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:bg-gray-50 disabled:cursor-not-allowed',
            !!leftIcon && 'pl-10',
            !!rightIcon && 'pr-10',
            !!error && 'border-red-400 focus:ring-red-400',
            className
          )}
          {...props}
        />
        {rightIcon && <span className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400">{rightIcon}</span>}
      </div>
      {hint && !error && (
        <p id={`${inputId}-help`} className="mt-1 text-xs text-gray-500 dark:text-gray-400">{hint}</p>
      )}
      {error && <p id={`${inputId}-help`} className="mt-1 text-xs text-red-500">{error}</p>}
    </div>
  );
});

export default Input;