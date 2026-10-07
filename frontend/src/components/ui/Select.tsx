import React, { useId } from 'react';
import { cn } from '@/lib/utils';

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  /** Explanatory text below the field. Distinct from `error`: a hint is normal. */
  hint?: string;
  options: { value: string; label: string }[];
  placeholder?: string;
}

export default function Select({
  label,
  error,
  hint,
  options,
  placeholder,
  className,
  id,
  ...props
}: SelectProps) {
  // See Input: the label had no `htmlFor`, so no select in the app was
  // associated with its label.
  const generatedId = useId();
  const selectId = id ?? generatedId;

  return (
    <div className="w-full">
      {label && (
        <label htmlFor={selectId} className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          {label}
        </label>
      )}
      <select
        id={selectId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? `${selectId}-help` : undefined}
        className={cn(
          'w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:bg-gray-50 disabled:cursor-not-allowed',
          error && 'border-red-400',
          className
        )}
        {...props}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options.map(opt => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
      </select>
      {hint && !error && (
        <p id={`${selectId}-help`} className="mt-1 text-xs text-gray-500 dark:text-gray-400">{hint}</p>
      )}
      {error && <p id={`${selectId}-help`} className="mt-1 text-xs text-red-500">{error}</p>}
    </div>
  );
}