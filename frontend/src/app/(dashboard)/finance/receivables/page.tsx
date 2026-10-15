'use client';

import React, { useMemo, useState } from 'react';

import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, Clock, DollarSign } from 'lucide-react';

import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ErrorState from '@/components/ui/ErrorState';
import { Card, CardContent } from '@/components/ui/Card';

import { formatCurrency } from '@/lib/utils';
import { reportsApi } from '@/lib/api/aiApi';
import { useSales } from '@/hooks/useSales';
import { useCreatePayment } from '@/hooks/useFinance';
import { useToast } from '@/components/ui/ToastProvider';

/**
 * Who owes what.
 *
 * <h2>What changed and why it mattered</h2>
 *
 * <p>This screen used to derive every figure in the browser: it pulled the sales
 * list, subtracted what each order said had been paid, and grouped the result by
 * customer. Two things were wrong with that, and both were invisible on the
 * page.
 *
 * <p>The sales list is paginated. A customer with an unpaid invoice on page
 * three simply did not appear in this table, and the total was quietly wrong by
 * however much was on the pages that had not loaded. And it read
 * {@code paidAmount} / {@code paid} — fields that the sale list endpoint had
 * stopped returning — so any order that was not fully paid looked fully paid and
 * contributed nothing.
 *
 * <p>The balances, ageing and totals now come from {@code /reports/receivables},
 * which walks every outstanding invoice regardless of pagination and computes
 * them from the sale's own amount paid. The collection form is unchanged in
 * spirit but is now honest about what it writes: a payment is attached to a
 * specific sale order, and the server derives the rest.
 */

/** One row of the receivables report. */
interface ReceivableRow {
  customer: string;
  invoiceCount: number;
  invoiceTotal: number;
  paid: number;
  outstanding: number;
  oldestDays: number;
  ageing: string;
  overdue: boolean;
}

/**
 * A starting reference for a collection.
 *
 * <p>Only a suggestion — the field is editable, and the server stores whatever
 * the operator types as the receipt number. Kept outside the component so the
 * clock is read when a form is opened rather than during a render.
 */
function suggestedReference(prefix: string): string {
  return `${prefix}-${Date.now().toString().slice(-8)}`;
}

export default function ReceivablesPage() {
  const { toast } = useToast();
  const [asOf, setAsOf] = useState(() => new Date().toISOString().slice(0, 10));

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['report', 'receivables', asOf],
    queryFn: () => reportsApi.get('receivables', { asOf }),
    retry: false,
  });

  // Needed only to let the operator choose which invoice a collection settles.
  // The balance itself is never derived here.
  const { data: sales = [] } = useSales();

  const createPayment = useCreatePayment();

  const [open, setOpen] = useState(false);
  const [selectedRow, setSelectedRow] = useState<ReceivableRow | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<string>('');
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState('BANK');
  const [reference, setReference] = useState('');

  const rows: ReceivableRow[] = data?.rows ?? [];
  const totals = data?.totals ?? {};

  /**
   * Unpaid orders for one customer.
   *
   * <p>Filtered from the sales already loaded for the table; the authoritative
   * list of what may be collected against is the report's, and the server will
   * refuse an amount greater than the balance even if this list is stale.
   */
  const openOrders = useMemo(() => {
    if (!selectedRow) return [];
    const needle = selectedRow.customer.trim().toLowerCase();
    return (sales as any[])
      .filter((sale) => {
        const status = String(sale.status ?? '').toUpperCase();
        if (status === 'CANCELLED') return false;
        const name = String(
          sale.customerName ?? sale.customer?.name ?? '',
        ).toLowerCase();
        if (name !== needle) return false;
        const total = Number(sale.finalAmount ?? sale.total ?? 0);
        const paid = Number(sale.amountPaid ?? sale.paidAmount ?? 0);
        return total - paid > 0.01;
      })
      .map((sale) => ({
        id: String(sale.id),
        label: `${sale.orderNumber ?? sale.reference ?? sale.id} — ${formatCurrency(
          Number(sale.finalAmount ?? sale.total ?? 0) - Number(sale.amountPaid ?? sale.paidAmount ?? 0),
        )} outstanding`,
      }));
  }, [selectedRow, sales]);

  const openOrder = openOrders.find((o) => o.id === selectedOrderId);
  const outstandingOnOrder = useMemo(() => {
    const sale = (sales as any[]).find((s) => String(s.id) === selectedOrderId);
    if (!sale) return 0;
    return (
      Number(sale.finalAmount ?? sale.total ?? 0) -
      Number(sale.amountPaid ?? sale.paidAmount ?? 0)
    );
  }, [sales, selectedOrderId]);

  const startCollection = (row: ReceivableRow) => {
    setSelectedRow(row);
    setSelectedOrderId('');
    setAmount('');
    setReference(suggestedReference('REC'));
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
        'Choose an invoice',
        'A payment must be recorded against a specific sale order.',
      );
      return;
    }
    if (value > outstandingOnOrder + 0.01) {
      toast(
        'warning',
        'Amount too large',
        `That invoice has ${formatCurrency(outstandingOnOrder)} outstanding.`,
      );
      return;
    }
    if (!reference.trim()) {
      toast('warning', 'Reference required', 'Enter a receipt or transaction reference.');
      return;
    }

    try {
      await createPayment.mutateAsync({
        saleOrderId: selectedOrderId,
        amount: value,
        method,
        reference: reference.trim(),
        notes: `Collection from ${selectedRow.customer}`,
      });
      toast(
        'success',
        'Payment recorded',
        `${formatCurrency(value)} received from ${selectedRow.customer}.`,
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

  const columns: Column<ReceivableRow>[] = [
    {
      key: 'customer',
      label: 'Customer',
      render: (_, row) => (
        <span className="font-semibold text-gray-900 dark:text-gray-100">
          {row.customer}
        </span>
      ),
    },
    { key: 'invoiceCount', label: 'Invoices', render: (v) => Number(v ?? 0).toString() },
    {
      key: 'invoiceTotal',
      label: 'Invoiced',
      render: (v) => formatCurrency(Number(v ?? 0)),
    },
    { key: 'paid', label: 'Paid', render: (v) => formatCurrency(Number(v ?? 0)) },
    {
      key: 'outstanding',
      label: 'Outstanding',
      render: (_, row) => (
        <span className="font-bold text-amber-600 dark:text-amber-400">
          {formatCurrency(row.outstanding)}
        </span>
      ),
    },
    {
      key: 'oldestDays',
      label: 'Oldest',
      render: (v) => `${Number(v ?? 0)} days`,
    },
    {
      key: 'ageing',
      label: 'Ageing',
      render: (_, row) => (
        <Badge variant={row.overdue ? 'danger' : 'warning'}>{row.ageing}</Badge>
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
        title="Receivables"
        subtitle="Every outstanding invoice, computed server-side from the sales ledger"
      />

      <div className="flex flex-wrap items-center gap-2 text-sm">
        <label htmlFor="receivables-asof" className="text-gray-500 dark:text-gray-400">
          Balances as at
        </label>
        <input
          id="receivables-asof"
          type="date"
          value={asOf}
          onChange={(event) => setAsOf(event.target.value)}
          className="rounded-md border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 px-2 py-1 text-sm text-gray-700 dark:text-gray-300"
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Total Outstanding"
          value={formatCurrency(Number(totals.totalOutstanding ?? 0))}
          changeLabel="across all customers"
          color="amber"
          icon={<DollarSign size={20} />}
        />
        <StatCard
          title="Overdue"
          value={formatCurrency(Number(totals.overdueAmount ?? 0))}
          changeLabel="older than 30 days"
          color="red"
          icon={<AlertTriangle size={20} />}
        />
        <StatCard
          title="Customers Owing"
          value={String(rows.length)}
          changeLabel="with a balance over zero"
          color="blue"
          icon={<Clock size={20} />}
        />
        <StatCard
          title="Collection Rate"
          value={
            Number(totals.totalOutstanding ?? 0) + Number(rows.reduce(
              (s, r) => s + r.paid,
              0,
            )) === 0
              ? '—'
              : `${(
                  (rows.reduce((s, r) => s + r.paid, 0) /
                    (Number(totals.totalOutstanding ?? 0) +
                      rows.reduce((s, r) => s + r.paid, 0))) *
                  100
                ).toFixed(1)}%`
          }
          changeLabel="of invoiced value collected"
          color="emerald"
          icon={<DollarSign size={20} />}
        />
      </div>

      <DataTable<ReceivableRow>
        columns={columns}
        data={rows}
        loading={isLoading}
        searchable
        searchPlaceholder="Search customers..."
        actions={(row) => (
          <Button size="sm" variant="outline" onClick={() => startCollection(row)}>
            Record payment
          </Button>
        )}
      />

      {rows.length === 0 && !isLoading && (
        <Card>
          <CardContent className="py-6 text-center text-xs text-gray-400">
            Nobody owes anything as at {asOf}. That is an empty answer, not a
            missing one — every outstanding invoice was walked, not just those on
            the first page of a list.
          </CardContent>
        </Card>
      )}

      <Modal
        open={open}
        onClose={close}
        title={`Record a payment — ${selectedRow?.customer ?? ''}`}
      >
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-1 rounded-lg bg-gray-50 p-3 text-xs dark:bg-gray-800">
            <div className="flex justify-between">
              <span className="text-gray-400">Total outstanding:</span>
              <strong className="text-amber-600 dark:text-amber-400">
                {formatCurrency(selectedRow?.outstanding ?? 0)}
              </strong>
            </div>
          </div>

          <Select
            label="Against which invoice"
            placeholder="Choose an invoice to settle"
            options={
              openOrders.length > 0
                ? openOrders.map((o) => ({ value: o.id, label: o.label }))
                : [{ value: '', label: 'No unpaid invoice loaded' }]
            }
            value={selectedOrderId}
            onChange={(event) => {
              setSelectedOrderId(event.target.value);
              const sale = (sales as any[]).find(
                (s) => String(s.id) === event.target.value,
              );
              if (sale) {
                setAmount(
                  String(
                    Number(sale.finalAmount ?? sale.total ?? 0) -
                      Number(sale.amountPaid ?? sale.paidAmount ?? 0),
                  ),
                );
              }
            }}
          />

          {openOrders.length === 0 && (
            <p className="text-xs text-amber-600 dark:text-amber-400">
              The unpaid invoices for this customer are not on the pages of the
              sales list that have loaded. Open the sale order from the sales
              screen to record a payment against it.
            </p>
          )}

          <Input
            label="Payment reference / receipt number"
            value={reference}
            onChange={(event) => setReference(event.target.value)}
            required
          />

          <Input
            label={`Amount collected (ETB)${
              selectedOrderId
                ? ` — this invoice has ${formatCurrency(outstandingOnOrder)} outstanding`
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
              { value: 'CASH', label: 'Cash in register' },
              { value: 'BANK', label: 'Bank transfer' },
              { value: 'MOBILE', label: 'Mobile money' },
            ]}
            value={method}
            onChange={(event) => setMethod(event.target.value)}
          />

          {openOrder && (
            <p className="text-xs text-gray-400">{openOrder.label}</p>
          )}

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