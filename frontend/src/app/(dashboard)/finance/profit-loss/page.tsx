'use client';
import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { PieChart, TrendingDown, TrendingUp, DollarSign } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent } from '@/components/ui/Card';
import DateRangeFilter, {
  DateRange,
  lastCalendarMonth,
} from '@/components/ui/DateRangeFilter';
import { reportsApi } from '@/lib/api/aiApi';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useState } from 'react';

/**
 * Profit and loss.
 *
 * <p>This screen used to build the statement in the browser: it summed the
 * sale orders the list endpoint returned, took each product's <em>current</em>
 * purchase price as the cost of what had been sold, and subtracted every expense
 * including claims nobody had approved. Three things followed from that. The
 * cost of goods sold was today's price applied to last month's sales, so
 * changing a product's price silently rewrote history. Cancelled orders that had
 * fallen off the first page of the list were excluded or included depending on
 * pagination. And this page's gross profit had no relationship to the profit
 * and loss report, because there was no profit and loss report — this was it.
 *
 * <p>Now the statement comes from the backend's calculation layer over validated
 * records, using the cost frozen on each sale line at the moment it was sold,
 * approved expenses only, and returns netted off. Every screen that shows these
 * numbers reads the same code, so they cannot disagree.
 *
 * <p>The memo lines at the bottom are the ones most often confused with the
 * figures above: tax collected is not income, cash received is not sales, and
 * receivables are not revenue until paid. They are shown separately for that
 * reason.
 */
export default function ProfitLossPage() {
  const [range, setRange] = useState<DateRange>(() => lastCalendarMonth());

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['report', 'profit-loss-detail', range],
    queryFn: () =>
      reportsApi.get('profit-loss-detail', {
        from: range.from,
        to: range.to,
      }),
    retry: false,
  });

  const summary = data?.summary;
  const statement: Array<Record<string, unknown>> = data?.statement ?? [];
  const memo = data?.memo;

  if (error) {
    const status = (error as any)?.response?.status;
    const forbidden = status === 403;
    return (
      <div className="space-y-6">
        <PageHeader title="Profit & Loss" />
        <div
          role="alert"
          className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
        >
          <p className="font-medium">
            {forbidden
              ? 'You do not have permission to view the profit and loss statement'
              : 'The statement could not be loaded'}
          </p>
          <p className="mt-1 text-xs">
            {forbidden
              ? 'Profit and loss is restricted to roles accountable for the figures.'
              : 'This is a failure to load, not an absence of transactions.'}
          </p>
          {!forbidden && (
            <button
              type="button"
              onClick={() => refetch()}
              className="mt-2 text-xs font-medium hover:underline"
            >
              Try again
            </button>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Profit & Loss"
        subtitle="Calculated from validated records: cost of goods frozen at the point of sale, approved expenses only"
      />

      <DateRangeFilter value={range} onChange={setRange} />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {isLoading || !summary ? (
          Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              className="h-24 rounded-xl bg-gray-100 dark:bg-gray-700/50 animate-pulse"
            />
          ))
        ) : (
          <>
            <StatCard
              title="Net Sales"
              value={formatCurrency(Number(summary.netSales))}
              changeLabel={`${formatCurrency(Number(summary.grossSales))} before returns`}
              color="blue"
              icon={<DollarSign size={20} />}
            />
            <StatCard
              title="Cost of Goods Sold"
              value={formatCurrency(Number(summary.costOfGoodsSold))}
              changeLabel="cost recorded at the time of sale"
              color="amber"
              icon={<TrendingDown size={20} />}
            />
            <StatCard
              title="Gross Profit"
              value={formatCurrency(Number(summary.grossProfit))}
              change={Number(summary.grossMarginPercent)}
              changeLabel="gross margin"
              color="emerald"
              icon={<TrendingUp size={20} />}
            />
            <StatCard
              title="Net Profit"
              value={formatCurrency(Number(summary.netProfit))}
              change={Number(summary.netMarginPercent)}
              changeLabel="net margin"
              color={Number(summary.netProfit) >= 0 ? 'emerald' : 'red'}
              icon={<PieChart size={20} />}
            />
          </>
        )}
      </div>

      {summary?.insufficientData && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-xs text-amber-800 dark:text-amber-200">
          <p className="font-medium">Very little trading in this period</p>
          <p className="mt-1">
            The arithmetic is correct but rests on too few transactions to mean
            much; percentages will swing on a single sale. {String(summary.basis)}
          </p>
        </div>
      )}

      <Card>
        <div className="px-5 py-4 border-b border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 flex items-center justify-between">
          <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">
            Statement of Operations
          </h3>
          <span className="text-xs text-gray-500">
            {data
              ? `${formatDate(String(data.from))} – ${formatDate(String(data.to))}`
              : '…'}
          </span>
        </div>

        {isLoading ? (
          <CardContent className="space-y-2 py-6">
            {Array.from({ length: 7 }).map((_, i) => (
              <div key={i} className="h-7 rounded bg-gray-100 dark:bg-gray-700/50" />
            ))}
          </CardContent>
        ) : (
          <div className="divide-y divide-gray-100 dark:divide-gray-700/60 text-sm">
            {statement.map((line, index) => {
              const emphasised = Boolean(line.emphasis);
              const amount = Number(line.amount ?? 0);
              return (
                <div
                  key={`${String(line.line)}-${index}`}
                  className={`px-5 py-3 flex justify-between items-center ${
                    emphasised
                      ? 'bg-emerald-50/40 dark:bg-emerald-900/15 font-semibold'
                      : ''
                  }`}
                >
                  <span
                    className={
                      emphasised
                        ? 'text-emerald-700 dark:text-emerald-400'
                        : 'text-gray-700 dark:text-gray-300'
                    }
                  >
                    {String(line.line)}
                  </span>
                  <div className="text-right">
                    <span
                      className={`tabular-nums ${
                        amount < 0
                          ? 'text-red-600 dark:text-red-400'
                          : emphasised
                            ? 'text-emerald-700 dark:text-emerald-400'
                            : 'text-gray-800 dark:text-gray-200'
                      }`}
                    >
                      {formatCurrency(amount)}
                    </span>
                    {line.previous !== undefined && line.previous !== null && (
                      <span className="block text-xs font-normal text-gray-400">
                        was {formatCurrency(Number(line.previous))}
                      </span>
                    )}
                  </div>
                </div>
              );
            })}

            {statement.length === 0 && (
              <p className="px-5 py-10 text-center text-sm text-gray-400">
                Nothing was traded in this period, so there is nothing to report.
                This is an empty answer, not a missing one.
              </p>
            )}
          </div>
        )}
      </Card>

      {memo && (
        <Card>
          <div className="px-5 py-3 border-b border-gray-200 dark:border-gray-700">
            <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">
              Not part of the result
            </h3>
            <p className="text-xs text-gray-400 mt-0.5">
              Figures that are easy to mistake for income, profit or liability.
            </p>
          </div>
          <CardContent className="pt-4">
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-2 text-sm">
              <MemoTerm
                label="Sales tax collected"
                value={Number(memo.taxCollected ?? 0)}
                note="Held on behalf of the tax authority, not income."
              />
              <MemoTerm
                label="Cash actually received"
                value={Number(memo.cashReceived ?? 0)}
                note="Payments recorded. Sales that are invoiced but unpaid are not cash."
              />
              <MemoTerm
                label="Invoiced but not yet paid"
                value={Number(memo.cashNotYetCollected ?? 0)}
                note="Revenue that has been recognised but not collected."
              />
              <MemoTerm
                label="Owed by customers"
                value={Number(memo.accountsReceivable ?? 0)}
                note="A balance, not a loss and not income."
              />
              <MemoTerm
                label="Owed to suppliers"
                value={Number(memo.accountsPayable ?? 0)}
                note="A liability for goods already received."
              />
            </dl>
          </CardContent>
        </Card>
      )}

      {data?.basis && (
        <p className="text-xs text-gray-400">{String(data.basis)}</p>
      )}
    </div>
  );
}

function MemoTerm({
  label,
  value,
  note,
}: {
  label: string;
  value: number;
  note: string;
}) {
  return (
    <div className="border-b border-gray-100 dark:divide-gray-700/40 py-2 last:border-0">
      <div className="flex justify-between gap-4">
        <dt className="text-gray-600 dark:text-gray-400">{label}</dt>
        <dd className="tabular-nums font-medium text-gray-800 dark:text-gray-200 whitespace-nowrap">
          {formatCurrency(value)}
        </dd>
      </div>
      <p className="text-xs text-gray-400 mt-0.5">{note}</p>
    </div>
  );
}