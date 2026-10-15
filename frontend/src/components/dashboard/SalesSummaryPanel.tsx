'use client';
import React from 'react';
import {
  AlertTriangle,
  Banknote,
  CalendarDays,
  Clock,
  Loader2,
  Receipt,
  ShoppingCart,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { useSalesSummary } from '@/hooks/useAiDashboard';
import { DateRangeParams, SalesSummary } from '@/lib/api/aiApi';
import { labelApiError } from '@/lib/api/apiErrors';
import { formatCurrency } from '@/lib/utils';

/**
 * What the sales record actually says, for whoever is looking.
 *
 * <p>This panel exists because the question a person opens a dashboard with is
 * usually "how did today go", and answering it honestly needs more than one
 * number. An order that was written down is not a sale; a sale that was
 * cancelled is not money; an order on credit has been invoiced but not
 * collected. Each of those is its own line here rather than one net figure that
 * could mean any of them.
 *
 * <p>The scope is the server's decision, not this component's. A caller holding
 * {@code SALES_REPORT_VIEW} is told they are seeing the whole business; anyone
 * else is told they are seeing their own orders — and because there is no
 * parameter to widen it, a cashier cannot read another cashier's takings by
 * editing a request. This component only reports the scope it was given, which
 * is why the badge is shown rather than the label merely implying it.
 *
 * <p>Every figure below is a recorded value. Nothing here is estimated,
 * extrapolated, or filled in when a field is missing: a sale with no payment
 * method appears as "unspecified" rather than being quietly reclassified.
 */
export default function SalesSummaryPanel({ range }: { range: DateRangeParams }) {
  const { data, isLoading, error, refetch } = useSalesSummary(range);

  const period = `${range.from} to ${range.to}`;
  const ownSalesOnly = data?.scope === 'OWN_SALES';

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <Receipt size={16} className="text-blue-500" />
            Sales {ownSalesOnly ? 'taken by you' : 'across the business'}
          </h2>
          {data && (
            <Badge variant={ownSalesOnly ? 'info' : 'default'}>
              {ownSalesOnly ? 'Your orders only' : 'All sellers'}
            </Badge>
          )}
        </div>
        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
          <CalendarDays size={12} aria-hidden="true" />
          <span>{describePeriod(data, range)}</span>
        </p>
      </CardHeader>

      <CardContent className="space-y-4">
        {error && (
          <div
            role="alert"
            className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
          >
            <p className="font-medium">Sales figures could not be loaded</p>
            <p className="mt-1 text-xs">
              {labelApiError('Sales summary', error).message}
            </p>
            <button
              type="button"
              onClick={() => refetch()}
              className="mt-2 inline-flex items-center gap-1 text-xs font-medium underline"
            >
              <Loader2 size={12} aria-hidden="true" /> Try again
            </button>
          </div>
        )}

        {isLoading && (
          <div
            className="grid grid-cols-2 lg:grid-cols-3 gap-3"
            aria-label="Loading sales figures"
          >
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="h-20 rounded-xl bg-gray-100 dark:bg-gray-700/50 animate-pulse"
              />
            ))}
          </div>
        )}

        {!isLoading && !error && data && data.ordersCreated === 0 && (
          <EmptyState
            icon={<Receipt size={20} />}
            title="No orders in this period"
            message={
              ownSalesOnly
                ? 'You have not recorded any orders between these dates. That is the whole record — there is nothing hidden behind it.'
                : 'No orders were recorded in the business between these dates.'
            }
          />
        )}

        {!isLoading && !error && data && data.ordersCreated > 0 && (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
              <StatCard
                title="Orders recorded"
                value={data.ordersCreated}
                changeLabel={`${data.completedSales} completed`}
                icon={<ShoppingCart size={18} />}
                color="blue"
              />
              <StatCard
                title="Net sales"
                value={formatCurrency(data.netSales)}
                changeLabel={`${data.unitsSold} units`}
                icon={<TrendingUp size={18} />}
                color="emerald"
              />
              <StatCard
                title="Collected"
                value={formatCurrency(data.amountCollected)}
                changeLabel={
                  data.outstanding > 0
                    ? `${formatCurrency(data.outstanding)} still owing`
                    : 'Nothing outstanding'
                }
                icon={<Banknote size={18} />}
                color={data.outstanding > 0 ? 'amber' : 'emerald'}
              />
              <StatCard
                title="Average order"
                value={formatCurrency(data.averageOrderValue)}
                changeLabel="Across completed sales only"
                icon={<Wallet size={18} />}
                color="purple"
              />
              {data.cancelledOrders > 0 && (
                <StatCard
                  title="Cancelled orders"
                  value={data.cancelledOrders}
                  changeLabel="Not counted as sales"
                  icon={<AlertTriangle size={18} />}
                  color="red"
                />
              )}
              {data.voidedOrders > 0 && (
                <StatCard
                  title="Voided orders"
                  value={data.voidedOrders}
                  changeLabel="Reversed — returned to the takings"
                  icon={<AlertTriangle size={18} />}
                  color="red"
                />
              )}
            </div>

            <HowTheFiguresAddUp summary={data} />
            <HourlyBars hourly={data.hourlySales} />
            <PaymentMix methods={data.paymentMethods} />
          </>
        )}
      </CardContent>
    </Card>
  );
}

/** The period in words, falling back to the requested one before data lands. */
function describePeriod(data: SalesSummary | undefined, range: DateRangeParams) {
  if (!data) return `${range.from} to ${range.to}`;
  return data.from === data.to
    ? `on ${data.from}`
    : `${data.from} to ${data.to}`;
}

/**
 * The arithmetic, shown rather than asserted.
 *
 * <p>A net figure is easy to misread, so the components of it are given: what
 * was invoiced, what came off as a discount, what came back as a return. A
 * reader who disagrees with the total can see which part they disagree with.
 */
function HowTheFiguresAddUp({ summary }: { summary: SalesSummary }) {
  const rows: Array<{ label: string; value: string; note: string }> = [
    {
      label: 'Before discount and returns',
      value: formatCurrency(summary.grossSales),
      note: 'Every line on the orders in this period',
    },
    {
      label: 'Discounts',
      value: `-${formatCurrency(summary.discounts)}`,
      note: 'Taken off the order totals themselves',
    },
    {
      label: 'Returns',
      value: `-${formatCurrency(summary.returns)}`,
      note: 'Units handed back, at the price they were sold for',
    },
    {
      label: 'Net sales',
      value: formatCurrency(summary.netSales),
      note: 'Gross, less discounts, less returns',
    },
    {
      label: 'Amount collected',
      value: formatCurrency(summary.amountCollected),
      note: 'Completed payments only — an unpaid order is not cash',
    },
  ];

  return (
    <div>
      <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400 mb-2">
        How these figures add up
      </h3>
      <table className="w-full text-sm">
        <caption className="sr-only">
          How gross sales, discounts, returns, net sales and collections relate
        </caption>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label} className="border-b border-gray-100 dark:border-gray-700 last:border-0">
              <th scope="row" className="py-1.5 text-left font-medium text-gray-700 dark:text-gray-300">
                {row.label}
              </th>
              <td className="py-1.5 text-right font-mono text-gray-900 dark:text-gray-100">
                {row.value}
              </td>
              <td className="py-1.5 pl-3 text-xs text-gray-500 dark:text-gray-400 hidden sm:table-cell">
                {row.note}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">{summary.basis}</p>
    </div>
  );
}

/**
 * Trading by hour, which is the shape of a working day.
 *
 * <p>Rendered as bars rather than a chart because there is one series and a
 * dozen values: anything more elaborate would imply a comparison that is not
 * being made. The hours with no sales are absent rather than shown as zero
 * bars, so an empty morning reads as an empty morning.
 */
function HourlyBars({ hourly }: { hourly: Record<string, number> }) {
  const entries = Object.entries(hourly)
    .filter(([, value]) => Number(value) !== 0)
    .sort((a, b) => Number(a[0]) - Number(b[0]));
  if (entries.length === 0) return null;

  const busiest = Math.max(...entries.map(([, value]) => Number(value)));
  const openingHour = entries[0][0];
  const peakHour = entries.reduce((best, entry) =>
    Number(entry[1]) > Number(best[1]) ? entry : best,
  )[0];

  return (
    <div>
      <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400 mb-2 flex items-center gap-1">
        <Clock size={12} aria-hidden="true" /> Trading by hour
      </h3>
      <ul className="space-y-1">
        {entries.map(([hour, value]) => (
          <li key={hour} className="flex items-center gap-2 text-xs">
            <span className="w-14 shrink-0 tabular-nums text-gray-500 dark:text-gray-400">
              {hour.padStart(2, '0')}:00
            </span>
            <span className="h-2 flex-1 rounded-full bg-gray-100 dark:bg-gray-700 overflow-hidden">
              <span
                className="block h-full rounded-full bg-blue-500"
                style={{ width: `${Math.max(3, (Number(value) / busiest) * 100)}%` }}
              />
            </span>
            <span className="w-20 shrink-0 text-right font-mono text-gray-700 dark:text-gray-300">
              {formatCurrency(Number(value))}
            </span>
          </li>
        ))}
      </ul>
      <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
        First order at {openingHour.padStart(2, '0')}:00, busiest hour{' '}
        {peakHour.padStart(2, '0')}:00. Hours with no sales are left out rather
        than shown as zero.
      </p>
    </div>
  );
}

/**
 * How the money was taken, which is not the same as how much.
 *
 * <p>Split on net sales so the parts sum to the figure above them. A method the
 * orders never recorded is shown as "unspecified" instead of being dropped,
 * because silently reclassifying a payment would overstate how confident this
 * breakdown is.
 */
function PaymentMix({ methods }: { methods: Record<string, number> }) {
  const entries = Object.entries(methods).sort((a, b) => Number(b[1]) - Number(a[1]));
  if (entries.length === 0) return null;

  const total = entries.reduce((sum, [, value]) => sum + Number(value), 0);

  return (
    <div>
      <h3 className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400 mb-2 flex items-center gap-1">
        <Wallet size={12} aria-hidden="true" /> How it was paid for
      </h3>
      <ul className="space-y-1">
        {entries.map(([method, value]) => (
          <li key={method} className="flex items-center justify-between gap-2 text-sm">
            <span className="text-gray-700 dark:text-gray-300">{prettyMethod(method)}</span>
            <span className="flex items-center gap-2">
              <span className="text-xs text-gray-500 dark:text-gray-400 tabular-nums">
                {total > 0 ? `${((Number(value) / total) * 100).toFixed(0)}%` : '—'}
              </span>
              <span className="font-mono text-gray-900 dark:text-gray-100">
                {formatCurrency(Number(value))}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function prettyMethod(method: string) {
  if (!method || method === 'UNSPECIFIED') return 'Not recorded';
  return method.charAt(0) + method.slice(1).toLowerCase().replace(/_/g, ' ');
}