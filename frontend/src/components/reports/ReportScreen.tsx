'use client';
import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Download,
  FileSpreadsheet,
  FileText,
  Loader2,
  RefreshCw,
  Search,
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import { Card, CardContent } from '@/components/ui/Card';
import DateRangeFilter, {
  DateRange,
  lastNDays,
} from '@/components/ui/DateRangeFilter';
import { reportsApi } from '@/lib/api/aiApi';
import { useAuth } from '@/lib/auth/AuthProvider';
import { formatCurrency, formatDate } from '@/lib/utils';

/**
 * One screen, every report.
 *
 * <p>The reports differ in columns and filters, not in structure: they all come
 * from {@code /reports/*}, they all take the same date range, and they all
 * return {@code {columns, rows, totals}}. That uniformity is the point — a
 * figure can be reproduced from saved transaction data, and the export is the
 * same data the screen showed, because it is the same endpoint.
 *
 * <h2>Why the rows come from the server and not from a filtered list</h2>
 *
 * <p>Every row here was fetched over the date filter that is currently set. The
 * totals are computed by the same code that produced the rows, so the footer
 * always adds up, and a report filtered to February cannot quietly include
 * January. Searching is client-side over the fetched page and says so, because
 * it does not re-query.
 *
 * <h2>Export</h2>
 *
 * <p>CSV and PDF are produced by the backend from the same report, not
 * assembled in the browser, so an exported file can never contain a number the
 * screen did not show. Export needs {@code REPORT_EXPORT}; the button is hidden
 * without it rather than shown and then refused.
 */

/** How many rows a page shows. */
const PAGE_SIZE = 50;

export interface ReportColumn {
  key: string;
  /** Column heading. */
  label: string;
  /** Right-align and format as money. */
  money?: boolean;
  /** Right-align as a plain number. */
  numeric?: boolean;
}

export interface ReportScreenProps {
  /** Title shown in the page header. */
  title: string;
  subtitle?: string;
  /** The report name understood by /reports/{name}. */
  report: string;
  columns: ReportColumn[];
  /** Whether the report accepts a date filter. */
  dateFiltered?: boolean;
  /**
   * Suppresses the page header.
   *
   * <p>For a report embedded in a page that already has one — the inventory
   * screen shows several reports under one heading, and repeating the title per
   * tab would read as several unrelated pages.
   */
  hideHeader?: boolean;
  /** The key in the payload holding the row array. */
  rowsKey?: string;
  /** Extra query parameters built from the date range and local filter state. */
  extraParams?: Record<string, unknown>;
  /** Filter controls rendered next to the date picker. */
  filters?: React.ReactNode;
  /** Stat cards derived from the payload. */
  summary?: (payload: Record<string, any>) => React.ReactNode;
  /** The first range the report opens on. */
  initialRange?: () => DateRange;
}

export default function ReportScreen({
  title,
  subtitle,
  report,
  columns,
  dateFiltered = true,
  hideHeader = false,
  rowsKey = 'rows',
  extraParams,
  filters,
  summary,
  initialRange,
}: ReportScreenProps) {
  const { hasPermission } = useAuth();
  const [range, setRange] = useState<DateRange>(
    initialRange ?? (() => lastNDays(30)),
  );
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [exporting, setExporting] = useState<'csv' | 'pdf' | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  // The extra parameters are usually an inline object literal, so it would be a
  // new reference on every render and would defeat the memo entirely. Keyed by
  // content instead: two renders that mean the same thing produce the same key
  // and the same query, and a changed filter genuinely re-fetches.
  const extraKey = JSON.stringify(extraParams ?? {});

  const params = useMemo(
    () => ({
      ...(dateFiltered ? { from: range.from, to: range.to } : {}),
      ...(JSON.parse(extraKey) as Record<string, unknown>),
    }),
    [range.from, range.to, dateFiltered, extraKey],
  );

  const query = useQuery({
    queryKey: ['report', report, params],
    queryFn: () => reportsApi.get(report as any, params),
    retry: false,
  });

  const payload = query.data;

  // Memoised on the payload rather than defaulted inline. `payload?.[rowsKey] ?? []`
  // would produce a brand-new empty array on every render while loading, and the
  // search memo below would then recompute each time for no reason.
  const allRows: Array<Record<string, unknown>> = useMemo(
    () => payload?.[rowsKey] ?? [],
    [payload, rowsKey],
  );

  // Searching is over the fetched rows and says so in the UI. It does not
  // re-query, because the server's filters are the date range and the report's
  // own parameters — pretending a text box filters the whole table would be a
  // quiet lie on a screen full of numbers.
  const filteredRows = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (!needle) return allRows;
    return allRows.filter((row) =>
      Object.values(row).some(
        (value) =>
          value !== null &&
          value !== undefined &&
          String(value).toLowerCase().includes(needle),
      ),
    );
  }, [allRows, search]);

  const pageCount = Math.max(1, Math.ceil(filteredRows.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount - 1);
  const visible = filteredRows.slice(
    safePage * PAGE_SIZE,
    safePage * PAGE_SIZE + PAGE_SIZE,
  );

  const doExport = async (format: 'csv' | 'pdf') => {
    setExporting(format);
    setExportError(null);
    try {
      await reportsApi.download(report, format, params);
    } catch (error: unknown) {
      // The server's own message is the useful one. For the row cap it says how
      // many rows matched and how to narrow the range, which is exactly what the
      // reader needs to do next. Replacing it with a generic "export failed"
      // would throw away the only actionable thing in the response.
      const serverMessage = (error as any)?.response?.data?.message;
      setExportError(
        serverMessage ??
          (error as any)?.message ??
          'The export could not be produced. The figures on screen are unchanged.',
      );
    } finally {
      setExporting(null);
    }
  };

  const mayExport = hasPermission('REPORT_EXPORT');

  return (
    <div className="space-y-6">
      {!hideHeader && (
        <PageHeader
          title={title}
          subtitle={subtitle}
          actions={
            mayExport ? (
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={exporting !== null}
                  onClick={() => doExport('csv')}
                >
                  {exporting === 'csv' ? (
                    <Loader2 size={14} className="animate-spin" />
                  ) : (
                    <FileSpreadsheet size={14} />
                  )}
                  CSV
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={exporting !== null}
                  onClick={() => doExport('pdf')}
                >
                  {exporting === 'pdf' ? (
                    <Loader2 size={14} className="animate-spin" />
                  ) : (
                    <FileText size={14} />
                  )}
                  PDF
                </Button>
              </div>
            ) : undefined
          }
        />
      )}

      {hideHeader && mayExport && (
        <div className="flex justify-end -mt-2">
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={exporting !== null}
              onClick={() => doExport('csv')}
            >
              {exporting === 'csv' ? (
                <Loader2 size={14} className="animate-spin" />
              ) : (
                <FileSpreadsheet size={14} />
              )}
              CSV
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={exporting !== null}
              onClick={() => doExport('pdf')}
            >
              {exporting === 'pdf' ? (
                <Loader2 size={14} className="animate-spin" />
              ) : (
                <FileText size={14} />
              )}
              PDF
            </Button>
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        {dateFiltered && <DateRangeFilter value={range} onChange={(next) => {
          setRange(next);
          setPage(0);
        }} />}
        {filters}
        <div className="relative">
          <Search
            size={14}
            className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400"
          />
          <input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Search the rows below"
            aria-label={`Search the ${title} rows on this page`}
            className="rounded-lg border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 pl-8 pr-3 py-1.5 text-xs text-gray-700 dark:text-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      {exportError && (
        <div
          role="alert"
          className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
        >
          <p className="flex items-center gap-2 font-medium">
            <AlertTriangle size={15} /> Export refused
          </p>
          <p className="mt-1 text-xs">{exportError}</p>
          <p className="mt-1 text-xs">
            The figures above are unaffected — an export is a download, not the
            report.
          </p>
        </div>
      )}

      {query.error ? (
        <ReportError
          status={(query.error as any)?.response?.status}
          onRetry={() => query.refetch()}
        />
      ) : query.isLoading ? (
        <div className="h-64 rounded-xl bg-gray-100 dark:bg-gray-700/50 animate-pulse" />
      ) : (
        <>
          {summary && payload ? <>{summary(payload)}</> : null}

          <Card>
            <CardContent className="p-0">
              <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  {filteredRows.length.toLocaleString()} row
                  {filteredRows.length === 1 ? '' : 's'}
                  {search.trim() && (
                    <span className="ml-1">
                      matching “{search.trim()}” within the rows fetched for{' '}
                      {dateFiltered
                        ? `${formatDate(range.from)} – ${formatDate(range.to)}`
                        : 'all time'}
                    </span>
                  )}
                  {!search.trim() && dateFiltered && (
                    <span className="ml-1">
                      · {formatDate(range.from)} – {formatDate(range.to)}
                    </span>
                  )}
                </p>
                {payload?.basis && (
                  <p className="text-xs text-gray-400">{String(payload.basis)}</p>
                )}
              </div>

              {visible.length === 0 ? (
                <EmptyReport
                  filtered={search.trim().length > 0}
                  onClear={() => setSearch('')}
                  dateFiltered={dateFiltered}
                  range={range}
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 dark:bg-gray-800/60">
                      <tr>
                        {columns.map((column) => (
                          <th
                            key={column.key}
                            className={`px-4 py-3 text-xs font-semibold uppercase text-gray-500 ${
                              column.money || column.numeric ? 'text-right' : 'text-left'
                            }`}
                          >
                            {column.label}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                      {visible.map((row, index) => (
                        <tr
                          key={String(
                            (row as any).id ?? (row as any).order ?? index,
                          )}
                          className="hover:bg-gray-50 dark:hover:bg-gray-700/30"
                        >
                          {columns.map((column) => (
                            <Cell
                              key={column.key}
                              column={column}
                              value={row[column.key]}
                            />
                          ))}
                        </tr>
                      ))}
                    </tbody>
                    {payload?.totals && (
                      <tfoot className="bg-gray-50 dark:bg-gray-800/60 font-semibold">
                        <tr>
                          {columns.map((column, index) => {
                            const value = (payload.totals as any)[column.key];
                            return (
                              <td
                                key={column.key}
                                className={`px-4 py-3 text-sm text-gray-800 dark:text-gray-200 ${
                                  column.money || column.numeric ? 'text-right' : 'text-left'
                                } ${index === 0 ? '' : 'border-l border-gray-200 dark:border-gray-700'}`}
                              >
                                {index === 0
                                  ? 'Total'
                                  : value === undefined || value === null
                                    ? ''
                                    : column.money
                                      ? formatCurrency(Number(value))
                                      : column.numeric
                                        ? Number(value).toLocaleString()
                                        : String(value)}
                              </td>
                            );
                          })}
                        </tr>
                      </tfoot>
                    )}
                  </table>
                </div>
              )}

              {pageCount > 1 && (
                <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200 dark:border-gray-700">
                  <p className="text-xs text-gray-400">
                    Page {safePage + 1} of {pageCount}
                  </p>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={safePage === 0}
                      onClick={() => setPage(safePage - 1)}
                    >
                      <ChevronLeft size={14} /> Previous
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={safePage >= pageCount - 1}
                      onClick={() => setPage(safePage + 1)}
                    >
                      Next <ChevronRight size={14} />
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

function Cell({
  column,
  value,
}: {
  column: ReportColumn;
  value: unknown;
}) {
  const className = `px-4 py-2.5 ${
    column.money || column.numeric ? 'text-right tabular-nums' : 'text-left'
  }`;

  if (value === null || value === undefined || value === '') {
    return <td className={`${className} text-gray-300`}>—</td>;
  }

  if (column.money) {
    const amount = Number(value);
    return (
      <td
        className={`${className} font-medium ${
          amount < 0 ? 'text-red-600 dark:text-red-400' : 'text-gray-800 dark:text-gray-200'
        }`}
      >
        {formatCurrency(amount)}
      </td>
    );
  }

  if (column.numeric) {
    return <td className={`${className} text-gray-700 dark:text-gray-300`}>
      {Number(value).toLocaleString()}
    </td>;
  }

  // Anything that looks like an ISO date is formatted as one. Left as a
  // timestamp string it renders with a time component nobody on a financial
  // report needs.
  const asText = String(value);
  if (/^\d{4}-\d{2}-\d{2}(T.*)?$/.test(asText)) {
    return (
      <td className={`${className} text-gray-600 dark:text-gray-400`}>
        {formatDate(asText.slice(0, 10))}
      </td>
    );
  }

  return (
    <td className={`${className} text-gray-700 dark:text-gray-300`}>{asText}</td>
  );
}

function EmptyReport({
  filtered,
  onClear,
  dateFiltered,
  range,
}: {
  filtered: boolean;
  onClear: () => void;
  dateFiltered: boolean;
  range: DateRange;
}) {
  return (
    <div className="py-14 text-center px-6">
      <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
        {filtered ? 'Nothing matches that search' : 'Nothing recorded in this period'}
      </p>
      <p className="mt-1.5 text-xs text-gray-400 max-w-md mx-auto">
        {filtered ? (
          <>The search covers the rows fetched for this date range only.</>
        ) : dateFiltered ? (
          <>
            No transactions were recorded between {formatDate(range.from)} and{' '}
            {formatDate(range.to)}. Widen the date range, or record a
            transaction — this is an empty answer, not a missing one.
          </>
        ) : (
          <>Nothing has been recorded yet.</>
        )}
      </p>
      {filtered && (
        <button
          type="button"
          onClick={onClear}
          className="mt-3 text-xs text-blue-600 hover:underline"
        >
          Clear the search
        </button>
      )}
    </div>
  );
}

/**
 * A failed report, told apart from an empty one.
 *
 * <p>A 403 says the reader is not permitted; anything else says the request
 * failed. Neither is the same as "there is nothing", and showing the same empty
 * table for all three would leave a reader believing the business had no
 * activity when in fact the page could not load.
 */
function ReportError({ status, onRetry }: { status?: number; onRetry: () => void }) {
  const forbidden = status === 403;
  return (
    <div
      role="alert"
      className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
    >
      <p className="flex items-center gap-2 font-medium">
        <AlertTriangle size={15} />
        {forbidden
          ? 'You do not have permission to view this report'
          : 'This report could not be loaded'}
      </p>
      <p className="mt-1 text-xs">
        {forbidden
          ? 'Ask an administrator for the reporting permission this screen needs. Nothing is hidden behind an error — the report was refused.'
          : 'The figures could not be retrieved from the server. This is a failure to load, not an absence of data.'}
      </p>
      {!forbidden && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium hover:underline"
        >
          <RefreshCw size={12} /> Try again
        </button>
      )}
    </div>
  );
}

export { Download };