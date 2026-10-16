'use client';
import React from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft,
  Calendar,
  CreditCard,
  FileText,
  Loader2,
  Package,
  Receipt,
  Store,
  User,
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import ErrorBoundary from '@/components/ui/ErrorBoundary';
import { salesApi, type SaleOrder } from '@/lib/api/salesApi';
import { formatCurrency, formatDateTime } from '@/lib/utils';

const statusVariant: Record<string, 'success' | 'info' | 'default' | 'danger'> = {
  delivered: 'success',
  confirmed: 'info',
  draft: 'default',
  cancelled: 'danger',
  PAID: 'success',
  COMPLETED: 'success',
  PENDING: 'info',
  CANCELLED: 'danger',
  VOIDED: 'danger',
};

const payVariant: Record<string, 'success' | 'warning' | 'danger'> = {
  paid: 'success',
  partial: 'warning',
  unpaid: 'danger',
  PAID: 'success',
  PARTIAL: 'warning',
  UNPAID: 'danger',
};

export default function SaleOrderDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params?.id as string;

  const {
    data: order,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['sales', 'detail', id],
    queryFn: () => salesApi.get(id),
    enabled: !!id,
    staleTime: 30_000,
  });

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center gap-2 text-gray-500">
        <Loader2 className="animate-spin" size={20} />
        <span>Loading order details...</span>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="space-y-4 p-4">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft size={16} className="mr-1" /> Back
        </Button>
        <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-800 dark:bg-red-900/20">
          <p className="font-semibold text-red-800 dark:text-red-200">
            Order could not be found or loaded
          </p>
          <p className="mt-1 text-sm text-red-600 dark:text-red-400">
            {error instanceof Error ? error.message : 'Please check the order ID or permissions.'}
          </p>
          <div className="mt-4 flex justify-center gap-3">
            <Button variant="outline" onClick={() => refetch()}>
              Try again
            </Button>
            <Button onClick={() => router.push('/sales')}>Go to Sales</Button>
          </div>
        </div>
      </div>
    );
  }

  const orderNumber = order.orderNumber || order.reference || `#${order.id}`;
  const totalAmount = order.totalAmount ?? 0;
  const discountAmount = order.discount ?? 0;
  const taxAmount = order.tax ?? 0;
  const finalTotal = order.finalAmount ?? order.total ?? totalAmount - discountAmount + taxAmount;
  const paidAmount = order.paid ?? (order.paymentStatus === 'PAID' ? finalTotal : 0);

  return (
    <ErrorBoundary label="Order Details">
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" onClick={() => router.back()}>
              <ArrowLeft size={16} /> Back
            </Button>
            <div>
              <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                <Receipt className="text-blue-600" size={22} />
                Order {orderNumber}
              </h1>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Created at {order.createdAt ? formatDateTime(order.createdAt) : '—'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant={statusVariant[order.status ?? ''] ?? 'default'}>
              Status: {order.status ?? '—'}
            </Badge>
            <Badge variant={payVariant[order.paymentStatus ?? ''] ?? 'default'}>
              Payment: {order.paymentStatus ?? '—'}
            </Badge>
          </div>
        </div>

        {/* Overview cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
          <Card>
            <CardContent className="p-4 flex items-center gap-3">
              <div className="rounded-lg bg-blue-50 p-2.5 text-blue-600 dark:bg-blue-900/30">
                <User size={20} />
              </div>
              <div>
                <p className="text-xs text-gray-500">Customer</p>
                <p className="font-semibold text-gray-900 dark:text-gray-100">
                  {order.customerName || 'Walk-in Customer'}
                </p>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-4 flex items-center gap-3">
              <div className="rounded-lg bg-emerald-50 p-2.5 text-emerald-600 dark:bg-emerald-900/30">
                <Store size={20} />
              </div>
              <div>
                <p className="text-xs text-gray-500">Cashier & Branch</p>
                <p className="font-semibold text-gray-900 dark:text-gray-100">
                  {order.cashier?.name ?? '—'}
                </p>
                <p className="text-xs text-gray-400">
                  {order.branch?.name ?? order.warehouseName ?? 'Main Branch'}
                </p>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-4 flex items-center gap-3">
              <div className="rounded-lg bg-purple-50 p-2.5 text-purple-600 dark:bg-purple-900/30">
                <CreditCard size={20} />
              </div>
              <div>
                <p className="text-xs text-gray-500">Payment Method</p>
                <p className="font-semibold text-gray-900 dark:text-gray-100">
                  {order.paymentMethod || 'CASH'}
                </p>
                <p className="text-xs text-gray-400">
                  Paid: {formatCurrency(paidAmount)}
                </p>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-4 flex items-center gap-3">
              <div className="rounded-lg bg-amber-50 p-2.5 text-amber-600 dark:bg-amber-900/30">
                <FileText size={20} />
              </div>
              <div>
                <p className="text-xs text-gray-500">Net Final Total</p>
                <p className="text-lg font-bold text-gray-900 dark:text-gray-100 font-mono">
                  {formatCurrency(finalTotal)}
                </p>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Items Table */}
        <Card>
          <CardHeader>
            <h2 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
              <Package size={18} className="text-blue-500" />
              Order Items ({order.items?.length ?? 0})
            </h2>
          </CardHeader>
          <CardContent>
            {(!order.items || order.items.length === 0) ? (
              <p className="py-6 text-center text-sm text-gray-500">No items recorded on this order.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 text-left text-xs uppercase tracking-wide text-gray-500 dark:border-gray-700 dark:text-gray-400">
                      <th scope="col" className="py-2.5 pr-4">Product</th>
                      <th scope="col" className="py-2.5 pr-4">SKU</th>
                      <th scope="col" className="py-2.5 pr-4 text-right">Quantity</th>
                      <th scope="col" className="py-2.5 pr-4 text-right">Unit Price</th>
                      <th scope="col" className="py-2.5 pr-4 text-right">Discount</th>
                      <th scope="col" className="py-2.5 text-right">Line Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {order.items.map((item, idx) => (
                      <tr
                        key={item.id ?? idx}
                        className="border-b border-gray-100 dark:border-gray-800 last:border-0 hover:bg-gray-50/50 dark:hover:bg-gray-800/50"
                      >
                        <td className="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100">
                          {item.productName ?? '—'}
                        </td>
                        <td className="py-3 pr-4 font-mono text-xs text-gray-500">
                          {item.productSku ?? '—'}
                        </td>
                        <td className="py-3 pr-4 text-right tabular-nums text-gray-700 dark:text-gray-300">
                          {item.quantity ?? 1}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono text-gray-700 dark:text-gray-300">
                          {formatCurrency(item.unitPrice ?? 0)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono text-gray-500">
                          {formatCurrency(item.discount ?? 0)}
                        </td>
                        <td className="py-3 text-right font-mono font-semibold text-gray-900 dark:text-gray-100">
                          {formatCurrency(item.total ?? ((item.unitPrice ?? 0) * (item.quantity ?? 1) - (item.discount ?? 0)))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Financial Summary Calculation Breakdown */}
            <div className="mt-6 flex flex-col items-end border-t border-gray-200 pt-4 dark:border-gray-700">
              <div className="w-full max-w-xs space-y-2 text-sm">
                <div className="flex justify-between text-gray-600 dark:text-gray-400">
                  <span>Gross Subtotal:</span>
                  <span className="font-mono">{formatCurrency(totalAmount)}</span>
                </div>
                {discountAmount > 0 && (
                  <div className="flex justify-between text-amber-600">
                    <span>Discount:</span>
                    <span className="font-mono">-{formatCurrency(discountAmount)}</span>
                  </div>
                )}
                {taxAmount > 0 && (
                  <div className="flex justify-between text-gray-600 dark:text-gray-400">
                    <span>Tax:</span>
                    <span className="font-mono">+{formatCurrency(taxAmount)}</span>
                  </div>
                )}
                <div className="flex justify-between border-t border-gray-200 pt-2 text-base font-bold text-gray-900 dark:border-gray-700 dark:text-gray-100">
                  <span>Final Total:</span>
                  <span className="font-mono text-blue-600 dark:text-blue-400">{formatCurrency(finalTotal)}</span>
                </div>
                <div className="flex justify-between text-xs text-gray-500">
                  <span>Amount Collected:</span>
                  <span className="font-mono">{formatCurrency(paidAmount)}</span>
                </div>
                {finalTotal - paidAmount > 0 && (
                  <div className="flex justify-between text-xs font-semibold text-red-600">
                    <span>Outstanding Balance:</span>
                    <span className="font-mono">{formatCurrency(finalTotal - paidAmount)}</span>
                  </div>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </ErrorBoundary>
  );
}
