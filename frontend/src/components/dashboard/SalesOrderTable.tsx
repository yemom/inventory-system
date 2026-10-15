'use client';
import React, { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import {
  ChevronLeft,
  ChevronRight,
  Loader2,
  RefreshCw,
  Receipt,
  Search,
} from 'lucide-react';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { salesApi } from '@/lib/api/salesApi';
import { useAuth } from '@/lib/auth/AuthProvider';
import { formatCurrency, formatDateTime } from '@/lib/utils';

/**
 * The actual orders, as they were recorded.
 *
 * <p>The summary cards above answer "how did today go". This answers "which
 * orders, exactly" — because a total nobody can trace back to individual rows is
 * a number to be believed rather than checked, and a cashier disputing a shift
 * needs the rows, not the aggregate.
 *
 * <p>Search, status and payment filters are sent to the server, so paging stays
 * correct: filtering ten thousand rows down to four in the browser would mean
 * downloading all ten thousand to show one page. The rows returned are already
 * the caller's to see — a cashier's list is scoped to their own orders by the
 * backend, and the header says which scope was applied rather than implying one.
 *
 * <p>Every field degrades honestly. An order with no branch recorded shows a
 * dash, because `branch` is free text on the user and genuinely can be absent;
 * inventing one would put a name against a transaction that never had it.
 */
export default function SalesOrderTable({ limit = 8 }: { limit?: number }) {
  const { hasPermission, user } = useAuth();
  const maySeeEveryone = hasPermission('SALES_REPORT_VIEW');

  const [term, setTerm] = useState('');
  const [debounced, setDebounced] = useState('');
  const [status, setStatus] = useState('');
  const [paymentStatus, setPaymentStatus] = useState('');
  const [page, setPage] = useState(0);

  // Debounced so that typing a customer name is one request, not one per key.
  useEffect(() => {
    const handle = setTimeout(() => setDebounced(term.trim()), 350);
    return () => clearTimeout(handle);
  }, [term]);

  // A new filter starts at the first page: staying on page 7 of a result set
  // that now has one page shows an empty table with no obvious reason.
  useEffect(() => {
    setPage(0);
  }, [debounced, status, paymentStatus]);

  const query = useQuery({
    queryKey: ['sales', 'dashboard-page', debounced, status, paymentStatus, page, limit],
    queryFn: () =>
      salesApi.page({
        page,
        size: limit,
        sort: 'createdAt,desc',
        search: debounced || undefined,
        status: status || undefined,
        paymentStatus: paymentStatus || undefined,
      }),
    // Keeps the previous page on screen while the next loads, so paging does
    // not flash an empty table between requests.
    placeholderData: keepPreviousData,
    staleTime: 30_000,
    retry: false,
  });

  const data = query.data;
  const totalPages = data?.totalPages ?? 0;
  const showingFrom = data && data.totalElements > 0 ? data.number * limit + 1 : 0;
  const showingTo = data ? Math.min((data.number + 1) * limit, data.totalElements) : 0;

  const scopeNote = useMemo(() => {
    if (maySeeEveryone) return 'Every order in the business.';
    return `Only orders recorded by you${user?.fullName ? ` (${user.fullName})` : ''}.`;
  }, [maySeeEveryone, user?.fullName]);

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <Receipt size={16} className="text-blue-500" />
            Recent orders
          </h2>
          <div className="flex items-center gap-2">
            <label className="sr-only" htmlFor="dash-order-search">
              Search orders
            </label>
            <div className="relative">
              <Search
                size={14}
                className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400"
                aria-hidden="true"
              />
              <input
                id="dash-order-search"
                type="search"
                value={term}
                onChange={(e) => setTerm(e.target.value)}
                placeholder="Order number or customer"
                className="w-56 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 py-1.5 pl-8 pr-3 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <label className="sr-only" htmlFor="dash-order-status">
              Filter by order status
            </label>
            <select
              id="dash-order-status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-2 py-1.5 text-sm text-gray-900 dark:text-gray-100"
            >
              <option value="">All statuses</option>
              <option value="PAID">Paid</option>
              <option value="PENDING">Pending</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
            <label className="sr-only" htmlFor="dash-order-payment">
              Filter by payment status
            </label>
            <select
              id="dash-order-payment"
              value={paymentStatus}
              onChange={(e) => setPaymentStatus(e.target.value)}
              className="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-2 py-1.5 text-sm text-gray-900 dark:text-gray-100"
            >
              <option value="">All payments</option>
              <option value="PAID">Paid</option>
              <option value="UNPAID">Unpaid</option>
            </select>
            <button
              type="button"
              onClick={() => query.refetch()}
              disabled={query.isFetching}
              aria-label="Refresh orders"
              className="rounded-lg border border-gray-300 dark:border-gray-600 p-1.5 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50"
            >
              <RefreshCw
                size={14}
                className={query.isFetching ? 'animate-spin' : undefined}
                aria-hidden="true"
              />
            </button>
          </div>
        </div>
        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{scopeNote}</p>
      </CardHeader>

      <CardContent>
        {query.isLoading && (
          <div
            className="flex items-center justify-center gap-2 py-8 text-sm text-gray-500"
            role="status"
          >
            <Loader2 size={16} className="animate-spin" aria-hidden="true" /> Loading orders
          </div>
        )}

        {query.error && (
          <div
            role="alert"
            className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
          >
            <p className="font-medium">Orders could not be loaded</p>
            <button
              type="button"
              onClick={() => query.refetch()}
              className="mt-2 text-xs font-medium underline"
            >
              Try again
            </button>
          </div>
        )}

        {!query.isLoading && !query.error && data && data.content.length === 0 && (
          <EmptyState
            icon={<Receipt size={20} />}
            title="No orders match"
            message={
              debounced || status || paymentStatus
                ? 'No order matches these filters. Clear them to see the full list.'
                : maySeeEveryone
                  ? 'No orders have been recorded yet.'
                  : 'You have not recorded any orders yet.'
            }
          />
        )}

        {!query.isLoading && !query.error && data && data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <caption className="sr-only">
                  Sales orders you are authorised to see, newest first
                </caption>
                <thead>
                  <tr className="border-b border-gray-200 dark:border-gray-700 text-left text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
                    <th scope="col" className="py-2 pr-3">Order</th>
                    <th scope="col" className="py-2 pr-3">Date</th>
                    <th scope="col" className="py-2 pr-3">Customer</th>
                    {maySeeEveryone && <th scope="col" className="py-2 pr-3">Cashier</th>}
                    <th scope="col" className="py-2 pr-3 text-right">Items</th>
                    <th scope="col" className="py-2 pr-3 text-right">Total</th>
                    <th scope="col" className="py-2 pr-3">Payment</th>
                    <th scope="col" className="py-2">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((order) => (
                    <tr
                      key={order.id}
                      className="border-b border-gray-100 dark:border-gray-700 last:border-0 hover:bg-gray-50 dark:hover:bg-gray-700/40"
                    >
                      <th scope="row" className="py-2 pr-3 text-left font-medium">
                        <Link
                          href={`/sales/${order.id}`}
                          className="text-blue-600 dark:text-blue-400 hover:underline"
                        >
                          {order.orderNumber ?? order.reference ?? `#${order.id}`}
                        </Link>
                        {order.branch?.name && (
                          <span className="block text-xs font-normal text-gray-400">
                            {order.branch.name}
                          </span>
                        )}
                      </th>
                      <td className="py-2 pr-3 text-gray-600 dark:text-gray-300 whitespace-nowrap">
                        {order.createdAt ? formatDateTime(order.createdAt) : '—'}
                      </td>
                      <td className="py-2 pr-3 text-gray-600 dark:text-gray-300">
                        {order.customerName || '—'}
                      </td>
                      {maySeeEveryone && (
                        <td className="py-2 pr-3 text-gray-600 dark:text-gray-300">
                          {order.cashier?.name || '—'}
                        </td>
                      )}
                      <td className="py-2 pr-3 text-right tabular-nums text-gray-600 dark:text-gray-300">
                        {order.items?.length ?? 0}
                      </td>
                      <td className="py-2 pr-3 text-right font-mono text-gray-900 dark:text-gray-100">
                        {formatCurrency(order.total ?? order.finalAmount ?? order.totalAmount ?? 0)}
                      </td>
                      <td className="py-2 pr-3">
                        <span className="text-xs text-gray-600 dark:text-gray-300">
                          {order.paymentMethod ?? '—'}
                        </span>
                      </td>
                      <td className="py-2">
                        <StatusBadge
                          orderStatus={order.status}
                          paymentStatus={order.paymentStatus}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-3 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
              <span>
                {showingFrom}–{showingTo} of {data.totalElements}
                {data.totalElements > 0 && ` order${data.totalElements === 1 ? '' : 's'}`}
              </span>
              {totalPages > 1 && (
                <span className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                    aria-label="Previous page of orders"
                    className="rounded-md border border-gray-300 dark:border-gray-600 p-1 disabled:opacity-40 hover:bg-gray-50 dark:hover:bg-gray-700"
                  >
                    <ChevronLeft size={14} aria-hidden="true" />
                  </button>
                  <span className="tabular-nums">
                    Page {page + 1} of {totalPages}
                  </span>
                  <button
                    type="button"
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                    aria-label="Next page of orders"
                    className="rounded-md border border-gray-300 dark:border-gray-600 p-1 disabled:opacity-40 hover:bg-gray-50 dark:hover:bg-gray-700"
                  >
                    <ChevronRight size={14} aria-hidden="true" />
                  </button>
                </span>
              )}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

/**
 * Order status and payment status shown together.
 *
 * <p>Two different facts that are easy to conflate: an order can be PAID and
 * still be awaiting approval, and an order can be PENDING because its payment
 * cleared but nobody approved it yet. Collapsing them into one colour would hide
 * exactly the case worth noticing.
 */
function StatusBadge({
  orderStatus,
  paymentStatus,
}: {
  orderStatus?: string | null;
  paymentStatus?: string | null;
}) {
  const order = (orderStatus ?? 'UNKNOWN').toUpperCase();
  const payment = (paymentStatus ?? 'UNKNOWN').toUpperCase();

  if (order === 'CANCELLED') return <Badge variant="danger">Cancelled</Badge>;
  if (payment !== 'PAID') {
    return <Badge variant="warning">{order === 'PENDING' ? 'Pending' : payment}</Badge>;
  }
  return <Badge variant="success">Paid</Badge>;
}