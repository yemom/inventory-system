'use client';
import React, { useMemo } from 'react';
import { Calendar, X } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * The date filter every financial screen shares.
 *
 * <p>One control, one meaning: the range shown here is the range the backend
 * was asked about. Because the backend does the arithmetic, the figures on two
 * screens filtered to the same range are guaranteed to be the same figures —
 * which is not true when each screen filters its own list in the browser.
 *
 * <p>Presets exist because "last month" has an unambiguous meaning to an
 * accountant and three to a browser, and only one of them is right.
 */

export interface DateRange {
  /** Inclusive first day, ISO format. */
  from: string;
  /** Inclusive last day, ISO format. */
  to: string;
}

export const isoDay = (date: Date): string =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;

export const startOfToday = (): Date => new Date();

/** The last {@code days} days, ending today. */
export const lastNDays = (days: number): DateRange => {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - (days - 1));
  return { from: isoDay(from), to: isoDay(to) };
};

/**
 * Last calendar month, complete.
 *
 * <p>Not "the last 30 days". A month is the unit financial figures are
 * reported and compared in, so the filter should offer it.
 */
export const lastCalendarMonth = (): DateRange => {
  const today = new Date();
  const end = new Date(today.getFullYear(), today.getMonth(), 0);
  const start = new Date(end.getFullYear(), end.getMonth(), 1);
  return { from: isoDay(start), to: isoDay(end) };
};

export const thisCalendarMonth = (): DateRange => {
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth(), 1);
  return { from: isoDay(start), to: isoDay(today) };
};

export const lastCalendarYear = (): DateRange => {
  const today = new Date();
  const end = new Date(today.getFullYear(), 0, 1);
  end.setDate(end.getDate() - 1);
  const start = new Date(end.getFullYear(), 0, 1);
  return { from: isoDay(start), to: isoDay(end) };
};

export interface Preset {
  label: string;
  range: () => DateRange;
}

export const RANGE_PRESETS: Preset[] = [
  { label: 'Last 30 days', range: () => lastNDays(30) },
  { label: 'Last 90 days', range: () => lastNDays(90) },
  { label: 'This month', range: thisCalendarMonth },
  { label: 'Last month', range: lastCalendarMonth },
  { label: 'Last year', range: lastCalendarYear },
];

interface DateRangeFilterProps {
  value: DateRange;
  onChange: (range: DateRange) => void;
  /** True when the current range matches a preset exactly. */
  className?: string;
  label?: string;
}

export function DateRangeFilter({
  value,
  onChange,
  className,
  label = 'Date range',
}: DateRangeFilterProps) {
  const activePreset = useMemo(
    () =>
      RANGE_PRESETS.find((preset) => {
        const range = preset.range();
        return range.from === value.from && range.to === value.to;
      })?.label ?? null,
    [value.from, value.to],
  );

  // Guarded rather than applied silently: a range that starts after it ends
  // returns nothing from the backend, and a quietly empty report is much harder
  // to diagnose than a message saying the dates are the wrong way round.
  const inverted = value.from > value.to;

  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-2 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2',
        className,
      )}
    >
      <Calendar size={15} className="text-gray-400 shrink-0" />
      <span className="text-xs text-gray-500 dark:text-gray-400 shrink-0">
        {label}
      </span>

      <div className="flex items-center gap-1">
        <label htmlFor="range-from" className="sr-only">
          From date
        </label>
        <input
          id="range-from"
          type="date"
          value={value.from}
          max={value.to}
          onChange={(event) =>
            onChange({ ...value, from: event.target.value })
          }
          className="rounded-md border border-gray-200 dark:border-gray-600 bg-transparent px-2 py-1 text-xs text-gray-700 dark:text-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <span className="text-xs text-gray-400">to</span>
        <label htmlFor="range-to" className="sr-only">
          To date
        </label>
        <input
          id="range-to"
          type="date"
          value={value.to}
          min={value.from}
          onChange={(event) => onChange({ ...value, to: event.target.value })}
          className="rounded-md border border-gray-200 dark:border-gray-600 bg-transparent px-2 py-1 text-xs text-gray-700 dark:text-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {RANGE_PRESETS.map((preset) => (
        <button
          key={preset.label}
          type="button"
          onClick={() => onChange(preset.range())}
          aria-pressed={activePreset === preset.label}
          className={cn(
            'rounded-full px-2.5 py-1 text-xs font-medium transition-colors',
            activePreset === preset.label
              ? 'bg-blue-600 text-white'
              : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700',
          )}
        >
          {preset.label}
        </button>
      ))}

      {inverted && (
        <span
          role="alert"
          className="flex items-center gap-1 text-xs text-red-600 dark:text-red-400"
        >
          <X size={12} /> Start date is after the end date
        </span>
      )}
    </div>
  );
}

export default DateRangeFilter;