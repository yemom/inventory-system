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

export default function Input({ label, error, hint, leftIcon, rightIcon, className, id, ...props }: InputProps) {
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
}