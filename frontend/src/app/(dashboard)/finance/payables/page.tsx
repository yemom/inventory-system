'use client';

import React, { useMemo, useState } from 'react';

import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, CalendarClock, Wallet } from 'lucide-react';

import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ErrorState from '@/components/ui/ErrorState';
import { Card, CardContent } from '@/components/ui/Card';

import { formatCurrency, formatDate } from '@/lib/utils';
import { reportsApi } from '@/lib/api/aiApi';
import { usePurchases } from '@/hooks/usePurchases';
import { useCreateSupplierPayment } from '@/hooks/useFinance';
import { useToast } from '@/components/ui/ToastProvider';

/**
 * What the business owes its suppliers.
 *
 * <h2>What changed and why it mattered</h2>
 *
 * <p>The balances used to be derived in the browser from the purchases list,
 * subtracting a field named {@code paid} that the list endpoint no longer
 * returned — so every purchase looked unpaid, and the page reported the full
 * order value as a liability. It also counted orders that had been placed but
 * not received, which is not a payable: the supplier has not delivered and the
 * business has nothing to owe for yet.
 *
 * <p>Both are now decided on the server. {@code /reports/payables} walks every
 * order with a balance regardless of pagination, uses the amount actually paid,
 * and includes only goods that have been received. The distinction is stated on
 * the page because it is the one most often got wrong: an order in transit is
 * neither stock nor a payable.
 */

/** One row of the payables report. */
interface PayableRow {
  supplier: string;
  orderCount: number;
  orderTotal: number;
  paid: number;
  outstanding: number;
  oldestDueDate: string;
  overdue: boolean;
  orders: string[];
}

/**
 * A starting reference for a supplier payment.
 *
 * <p>Only a suggestion — the field is editable, and the operator's own bank
 * reference is what should end up in the ledger.
 */
function suggestedReference(prefix: string): string {
  return `${prefix}-${Date.now().toString().slice(-8)}`;
}

export default function PayablesPage() {
  const { toast } = useToast();
  const [asOf, setAsOf] = useState(() => new Date().toISOString().slice(0, 10));

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['report', 'payables', asOf],
    queryFn: () => reportsApi.get('payables', { asOf }),
    retry: false,
  });

  const createPayment = useCreateSupplierPayment();

  const [open, setOpen] = useState(false);
  const [selectedRow, setSelectedRow] = useState<PayableRow | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState('');
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState('BANK');
  const [reference, setReference] = useState('');

  const rows: PayableRow[] = data?.rows ?? [];
  const totals = data?.totals ?? {};

  // Loaded only so a payment can be attached to a specific order. The balance
  // shown in the table never comes from here.
  const { data: purchases = [] } = usePurchases();

  const openOrders = useMemo(() => {
    if (!selectedRow) return [];
    const needle = selectedRow.supplier.trim().toLowerCase();
    return (purchases as any[])
      .filter((order) => {
        const status = String(order.status ?? '').toUpperCase();
        // Goods not received are not a payable, so they cannot be paid against.
        if (status === 'DRAFT' || status === 'ORDERED' || status === 'CANCELLED') {
          return false;
        }
        const supplier = String(
          order.supplierName ?? order.supplier?.name ?? '',
        ).toLowerCase();
        if (supplier !== needle) return false;
        const total = Number(order.totalAmount ?? order.total ?? 0);
        const paid = Number(order.amountPaid ?? 0);
        return total - paid > 0.01;
      })
      .map((order) => ({
        id: String(order.id),
        label: `${order.orderNumber ?? order.reference ?? order.id} — ${formatCurrency(
          Number(order.totalAmount ?? 0) - Number(order.amountPaid ?? 0),
        )} outstanding`,
      }));
  }, [selectedRow, purchases]);

  const outstandingOnOrder = useMemo(() => {
    const order = (purchases as any[]).find(
      (o) => String(o.id) === selectedOrderId,
    );
    if (!order) return 0;
    return (
      Number(order.totalAmount ?? 0) - Number(order.amountPaid ?? 0)
    );
  }, [purchases, selectedOrderId]);

  const startPayment = (row: PayableRow) => {
    setSelectedRow(row);
    setSelectedOrderId('');
    setAmount('');
    setReference(suggestedReference('PAY'));
    setMethod('BANK');
    setOpen(true);
  };

  const close = () => {
    if (createPayment.isPending) return;
    setOpen(false);
    setSelectedRow(null);
    setSelectedOrderId('');
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedRow) return;

    const value = Number.parseFloat(amount);
    if (!Number.isFinite(value) || value <= 0) {
      toast('warning', 'Invalid amount', 'Enter an amount greater than zero.');
      return;
    }
    if (!selectedOrderId) {
      toast(
        'warning',
        'Choose an order',
        'A payment must be recorded against a specific purchase order.',
      );
      return;
    }
    if (value > outstandingOnOrder + 0.01) {
      toast(
        'warning',
        'Amount too large',
        `That order has ${formatCurrency(outstandingOnOrder)} outstanding.`,
      );
      return;
    }
    if (!reference.trim()) {
      toast('warning', 'Reference required', 'Enter a transaction reference.');
      return;
    }

    try {
      await createPayment.mutateAsync({
        purchaseOrderId: selectedOrderId,
        amount: value,
        method,
        reference: reference.trim(),
        notes: `Payment to ${selectedRow.supplier}`,
      });
      toast(
        'success',
        'Payment recorded',
        `${formatCurrency(value)} paid to ${selectedRow.supplier}.`,
      );
      close();
      await refetch();
    } catch (err: unknown) {
      const message =
        (err as any)?.response?.data?.message ??
        (err as any)?.response?.data?.error ??
        (err as any)?.message ??
        'Failed to record the payment.';
      toast('error', 'Payment failed', message);
    }
  };

  const columns: Column<PayableRow>[] = [
    {
      key: 'supplier',
      label: 'Supplier',
      render: (_, row) => (
        <div>
          <span className="font-semibold text-gray-900 dark:text-gray-100">
            {row.supplier}
          </span>
          <span className="block text-[11px] text-gray-400">
            {row.orders?.slice(0, 2).join(' · ')}
            {(row.orders?.length ?? 0) > 2
              ? ` · +${(row.orders?.length ?? 0) - 2} more`
              : ''}
          </span>
        </div>
      ),
    },
    {
      key: 'orderCount',
      label: 'Orders',
      render: (v) => Number(v ?? 0).toString(),
    },
    {
      key: 'orderTotal',
      label: 'Ordered',
      render: (v) => formatCurrency(Number(v ?? 0)),
    },
    { key: 'paid', label: 'Paid', render: (v) => formatCurrency(Number(v ?? 0)) },
    {
      key: 'outstanding',
      label: 'Owed',
      render: (_, row) => (
        <span className="font-bold text-red-600 dark:text-red-400">
          {formatCurrency(row.outstanding)}
        </span>
      ),
    },
    {
      key: 'oldestDueDate',
      label: 'Due',
      render: (v, row) =>
        row.overdue ? (
          <span className="text-red-600 dark:text-red-400">
            {formatDate(String(v))}
          </span>
        ) : (
          formatDate(String(v))
        ),
    },
  ];

  if (error) {
    return (
      <ErrorState
        error={error}
        retry={() => {
          void refetch();
        }}
      />
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payables"
        subtitle="Balances on goods already received, computed server-side from the purchase ledger"
      />

      <div className="flex flex-wrap items-center gap-2 text-sm">
        <label htmlFor="payables-asof" className="text-gray-500 dark:text-gray-400">
          Balances as at
        </label>
        <input
          id="payables-asof"
          type="date"
          value={asOf}
          onChange={(event) => setAsOf(event.target.value)}
          className="rounded-md border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 px-2 py-1 text-sm text-gray-700 dark:text-gray-300"
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Total Owed"
          value={formatCurrency(Number(totals.totalOutstanding ?? 0))}
          changeLabel="for goods received"
          color="red"
          icon={<Wallet size={20} />}
        />
        <StatCard
          title="Overdue"
          value={formatCurrency(Number(totals.overdueAmount ?? 0))}
          changeLabel="past the agreed due date"
          color="amber"
          icon={<AlertTriangle size={20} />}
        />
        <StatCard
          title="Suppliers Owed"
          value={String(rows.length)}
          changeLabel="with a balance over zero"
          color="blue"
          icon={<Wallet size={20} />}
        />
        <StatCard
          title="Orders Past Due"
          value={String(rows.filter((r) => r.overdue).length)}
          changeLabel="suppliers concerned"
          color="purple"
          icon={<CalendarClock size={20} />}
        />
      </div>

      <DataTable<PayableRow>
        columns={columns}
        data={rows}
        loading={isLoading}
        searchable
        searchPlaceholder="Search suppliers..."
        actions={(row) => (
          <Button size="sm" variant="outline" onClick={() => startPayment(row)}>
            Record payment
          </Button>
        )}
      />

      {rows.length === 0 && !isLoading && (
        <Card>
          <CardContent className="py-6 text-center text-xs text-gray-400">
            Nothing is owed to suppliers as at {asOf}. Only goods that have been
            received appear here — an order still in transit is neither stock nor a
            payable.
          </CardContent>
        </Card>
      )}

      <Modal
        open={open}
        onClose={close}
        title={`Pay ${selectedRow?.supplier ?? ''}`}
      >
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-1 rounded-lg bg-gray-50 p-3 text-xs dark:bg-gray-800">
            <div className="flex justify-between">
              <span className="text-gray-400">Total owed to this supplier:</span>
              <strong className="text-red-600 dark:text-red-400">
                {formatCurrency(selectedRow?.outstanding ?? 0)}
              </strong>
            </div>
          </div>

          <Select
            label="Against which order"
            placeholder="Choose an order to pay"
            options={
              openOrders.length > 0
                ? openOrders.map((o) => ({ value: o.id, label: o.label }))
                : [{ value: '', label: 'No unpaid order loaded' }]
            }
            value={selectedOrderId}
            onChange={(event) => {
              setSelectedOrderId(event.target.value);
              const order = (purchases as any[]).find(
                (o) => String(o.id) === event.target.value,
              );
              if (order) {
                setAmount(
                  String(
                    Number(order.totalAmount ?? 0) - Number(order.amountPaid ?? 0),
                  ),
                );
              }
            }}
          />

          {openOrders.length === 0 && (
            <p className="text-xs text-amber-600 dark:text-amber-400">
              The unpaid orders for this supplier are not on the pages of the
              purchases list that have loaded. Open the order from the purchases
              screen to pay against it.
            </p>
          )}

          <Input
            label="Transaction reference"
            value={reference}
            onChange={(event) => setReference(event.target.value)}
            required
          />

          <Input
            label={`Amount paid (ETB)${
              selectedOrderId
                ? ` — this order has ${formatCurrency(outstandingOnOrder)} outstanding`
                : ''
            }`}
            type="number"
            min={0.01}
            step="0.01"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            required
          />

          <Select
            label="Payment channel"
            options={[
              { value: 'BANK', label: 'Bank transfer' },
              { value: 'CASH', label: 'Cash' },
              { value: 'MOBILE', label: 'Mobile money' },
            ]}
            value={method}
            onChange={(event) => setMethod(event.target.value)}
          />

          <div className="flex justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={close}
              disabled={createPayment.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              loading={createPayment.isPending}
              disabled={createPayment.isPending || !selectedOrderId}
            >
              Record payment
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}