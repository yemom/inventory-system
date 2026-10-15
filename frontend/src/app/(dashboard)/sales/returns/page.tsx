'use client';

import React, { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, Eye, RotateCcw, X } from 'lucide-react';

import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { Card, CardContent } from '@/components/ui/Card';
import { formatCurrency, formatDate } from '@/lib/utils';
import { returnsApi } from '@/lib/api/aiApi';
import { useSales } from '@/hooks/useSales';
import { useAuth } from '@/lib/auth/AuthProvider';
import { useToast } from '@/components/ui/ToastProvider';

/**
 * Customer returns.
 *
 * <h2>What changed and why it mattered</h2>
 *
 * <p>This screen used to keep its records in {@code localStorage}. That is not
 * a small limitation — it meant a return existed on the machine that typed it
 * and nowhere else, so it appeared in no report, was lost when the browser
 * cleared its storage, and could not be audited. Worse, the stock side was
 * faked: it posted a bare inventory adjustment with the return reference in the
 * note, so the ledger showed unexplained stock appearing and disappearing, and
 * the financial side was absent entirely. A return that reverses a sale has to
 * reverse the revenue too, or the margin for that month is simply wrong.
 *
 * <p>Returns are now documents on the server. Raising one records what is being
 * returned and against which sale; approving one is a separate human decision
 * that posts both the stock movement and the financial reversal. That split is
 * deliberate: accepting goods back is a judgement about whether they are
 * resellable, and an unreviewed return should not have moved any balances.
 *
 * <p>The refund is a further, explicit step. Nothing here pays anyone on its own.
 */

/** Reasons a customer might return goods. */
const REASONS = [
  { value: 'DAMAGED', label: 'Damaged goods' },
  { value: 'WRONG_ITEM', label: 'Wrong item supplied' },
  { value: 'NOT_AS_DESCRIBED', label: 'Not as described' },
  { value: 'CHANGE_OF_MIND', label: 'Changed mind' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'OTHER', label: 'Other' },
];

interface ReturnLine {
  id: number | string;
  productName: string;
  productSku: string;
  quantity: number;
  quantityReturned: number;
  unitPrice: number;
  unitCost: number;
}

export default function SalesReturnsPage() {
  const { toast } = useToast();
  const { hasPermission } = useAuth();
  const qc = useQueryClient();

  const [status, setStatus] = useState('ALL');
  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState<any>(null);

  // Which sale the return is raised against.
  const [saleId, setSaleId] = useState('');
  const [lines, setLines] = useState<Record<string, number>>({});
  const [reason, setReason] = useState('DAMAGED');
  const [notes, setNotes] = useState('');

  const { data: returns = [], isLoading, error, refetch } = useQuery({
    queryKey: ['returns', status],
    queryFn: () =>
      returnsApi.list(
        status === 'ALL' ? undefined : { status },
      ) as Promise<any[]>,
    retry: false,
  });

  // Only sales with something still returnable. A fully returned order is not
  // offered, because the server will refuse it anyway and an error after the
  // form is filled in is a poor way to learn that.
  const { data: sales = [] } = useSales();
  const returnableSales = useMemo(
    () =>
      (sales as any[]).filter((sale) => {
        if (String(sale.status ?? '').toUpperCase() === 'CANCELLED') return false;
        return (sale.items ?? []).some(
          (item: any) =>
            Number(item.quantity ?? 0) - Number(item.quantityReturned ?? 0) > 0,
        );
      }),
    [sales],
  );

  const selectedSale = useMemo(
    () =>
      (returnableSales as any[]).find((s) => String(s.id) === saleId) ?? null,
    [returnableSales, saleId],
  );

  const selectedLines: ReturnLine[] = useMemo(() => {
    if (!selectedSale) return [];
    return (selectedSale.items ?? []).map((item: any) => ({
      id: item.id,
      productName: item.productName ?? item.product?.name ?? '—',
      productSku: item.productSku ?? item.product?.sku ?? '',
      quantity: Number(item.quantity ?? 0),
      quantityReturned: Number(item.quantityReturned ?? 0),
      unitPrice: Number(item.unitPrice ?? 0),
      unitCost: Number(item.unitCost ?? item.product?.purchasePrice ?? 0),
    }));
  }, [selectedSale]);

  const reset = () => {
    setOpen(false);
    setSaleId('');
    setLines({});
    setReason('DAMAGED');
    setNotes('');
  };

  const raise = useMutation({
    mutationFn: () =>
      returnsApi.salesReturns(
        saleId,
        Object.entries(lines)
          .filter(([, quantity]) => quantity > 0)
          .map(([itemId, quantity]) => ({
            itemId: Number(itemId),
            quantity,
          })),
        reason,
        notes || undefined,
      ),
    onSuccess: () => {
      toast(
        'success',
        'Return raised',
        'It is now awaiting approval. Nothing has been restocked or refunded yet.',
      );
      reset();
      void qc.invalidateQueries({ queryKey: ['returns'] });
    },
    onError: (err: any) => {
      toast(
        'error',
        'Return not recorded',
        err?.response?.data?.message ?? err?.message ?? 'Please try again.',
      );
    },
  });

  const decide = useMutation({
    mutationFn: (input: { id: number | string; approve: boolean }) =>
      input.approve
        ? returnsApi.approve(input.id)
        : returnsApi.reject(input.id),
    onSuccess: (_data, input) => {
      toast(
        'success',
        input.approve ? 'Return approved' : 'Return rejected',
        input.approve
          ? 'Stock has been returned to the warehouse and the sale reversed.'
          : 'Nothing was restocked. The return is on file as rejected.',
      );
      setDetail(null);
      void qc.invalidateQueries({ queryKey: ['returns'] });
    },
    onError: (err: any) => {
      toast(
        'error',
        'Decision not recorded',
        err?.response?.data?.message ?? err?.message ?? 'Please try again.',
      );
    },
  });

  const statusVariant = (value: string) =>
    value === 'APPROVED'
      ? 'success'
      : value === 'REJECTED'
        ? 'danger'
        : 'warning';

  const columns: Column<any>[] = [
    {
      key: 'reference',
      label: 'Return',
      render: (v) => (
        <span className="font-mono text-xs text-blue-600">{String(v)}</span>
      ),
    },
    {
      key: 'originalReference',
      label: 'Against sale',
      render: (v) => <span className="font-mono text-xs">{String(v)}</span>,
    },
    {
      key: 'transactionDate',
      label: 'Date',
      render: (v) => formatDate(String(v ?? '').slice(0, 10)),
    },
    {
      key: 'reason',
      label: 'Reason',
      render: (v) => String(v ?? '—').replace(/_/g, ' '),
    },
    {
      key: 'status',
      label: 'Status',
      render: (v) => (
        <Badge variant={statusVariant(String(v)) as any}>{String(v)}</Badge>
      ),
    },
    {
      key: 'actions',
      label: '',
      render: (_, row) => (
        <Button size="sm" variant="ghost" onClick={() => setDetail(row)}>
          <Eye size={14} /> Details
        </Button>
      ),
    },
  ];

  if (error) {
    return (
      <Card>
        <CardContent className="py-6 text-center text-sm text-gray-500">
          Returns could not be loaded. That is a failure to reach the server, not
          an absence of returns.
          <div className="mt-3">
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Try again
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Sales Returns"
        subtitle="Returns are documents on the server. Raising one changes nothing until it is approved."
        actions={
          <Button
            size="sm"
            onClick={() => setOpen(true)}
            disabled={!hasPermission('RETURN_CREATE')}
          >
            <RotateCcw size={14} /> Raise a return
          </Button>
        }
      />

      <div className="flex items-center gap-2">
        <label htmlFor="return-status" className="text-sm text-gray-500">
          Show
        </label>
        <Select
          id="return-status"
          options={[
            { value: 'ALL', label: 'All returns' },
            { value: 'PENDING', label: 'Awaiting approval' },
            { value: 'APPROVED', label: 'Approved' },
            { value: 'REJECTED', label: 'Rejected' },
          ]}
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          className="w-48 text-sm"
        />
      </div>

      <DataTable
        columns={columns}
        data={returns}
        loading={isLoading}
        searchable
        searchPlaceholder="Search returns..."
      />

      {returns.length === 0 && !isLoading && (
        <Card>
          <CardContent className="py-6 text-center text-xs text-gray-400">
            No sales returns recorded. This is an empty answer, not a missing one.
          </CardContent>
        </Card>
      )}

      {/* ── Raise ─────────────────────────────────────────────────────── */}
      <Modal open={open} onClose={reset} title="Raise a sales return">
        <div className="space-y-4">
          <Select
            label="Against which sale"
            options={
              returnableSales.length > 0
                ? (returnableSales as any[]).map((s) => ({
                    value: String(s.id),
                    label: `${s.orderNumber ?? s.reference ?? s.id} — ${
                      s.customerName ?? 'Walk-in customer'
                    }`,
                  }))
                : [{ value: '', label: 'No sale has anything returnable' }]
            }
            value={saleId}
            onChange={(event) => {
              setSaleId(event.target.value);
              setLines({});
            }}
          />

          {selectedLines.length > 0 && (
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium text-gray-700 dark:text-gray-300">
                What is being returned
              </legend>
              {selectedLines.map((line) => {
                const remaining =
                  line.quantity - line.quantityReturned;
                return (
                  <div
                    key={line.id}
                    className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 dark:border-gray-700 px-3 py-2"
                  >
                    <div className="min-w-0">
                      <p className="text-sm text-gray-800 dark:text-gray-200 truncate">
                        {line.productName}
                      </p>
                      <p className="text-[11px] text-gray-400">
                        {remaining} of {line.quantity} still returnable ·{' '}
                        {formatCurrency(line.unitPrice)} each
                      </p>
                    </div>
                    <Input
                      type="number"
                      min={0}
                      max={remaining}
                      value={lines[String(line.id)] ?? 0}
                      onChange={(event) => {
                        const next = Math.max(
                          0,
                          Math.min(remaining, Number(event.target.value) || 0),
                        );
                        setLines((current) => ({
                          ...current,
                          [String(line.id)]: next,
                        }));
                      }}
                      className="w-20"
                      aria-label={`Quantity returned for ${line.productName}`}
                    />
                  </div>
                );
              })}
            </fieldset>
          )}

          <Select
            label="Reason"
            options={REASONS}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />

          <Input
            label="Notes (optional)"
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
          />

          <div className="rounded-lg bg-blue-50 dark:bg-blue-900/20 px-3 py-2 text-xs text-blue-800 dark:text-blue-200">
            Raising a return records the request only. Stock is not restocked and
            no refund is paid until somebody with approval permission accepts it.
          </div>

          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={reset}>
              Cancel
            </Button>
            <Button
              onClick={() => raise.mutate()}
              loading={raise.isPending}
              disabled={
                raise.isPending ||
                !saleId ||
                Object.values(lines).every((q) => q <= 0)
              }
            >
              Raise return
            </Button>
          </div>
        </div>
      </Modal>

      {/* ── Detail and decision ────────────────────────────────────────── */}
      <Modal
        open={detail !== null}
        onClose={() => setDetail(null)}
        title={detail ? `Return ${detail.reference}` : ''}
      >
        {detail && (
          <div className="space-y-4">
            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              <Term label="Against sale" value={String(detail.originalReference)} />
              <Term label="Status" value={String(detail.status)} />
              <Term label="Reason" value={String(detail.reason ?? '—').replace(/_/g, ' ')} />
              <Term
                label="Date"
                value={formatDate(String(detail.transactionDate ?? '').slice(0, 10))}
              />
            </dl>

            {(detail.items ?? []).length > 0 && (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase text-gray-400">
                    <th className="py-1.5">Item</th>
                    <th className="py-1.5 text-right">Qty</th>
                    <th className="py-1.5 text-right">Unit</th>
                    <th className="py-1.5 text-right">Value</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                  {(detail.items ?? []).map((item: any) => (
                    <tr key={item.id}>
                      <td className="py-1.5 text-gray-700 dark:text-gray-300">
                        {item.productName ?? item.product?.name ?? '—'}
                      </td>
                      <td className="py-1.5 text-right tabular-nums">
                        {item.quantity}
                      </td>
                      <td className="py-1.5 text-right tabular-nums">
                        {formatCurrency(Number(item.unitPrice ?? 0))}
                      </td>
                      <td className="py-1.5 text-right tabular-nums">
                        {formatCurrency(
                          Number(item.unitPrice ?? 0) * Number(item.quantity ?? 0),
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}

            {detail.notes && (
              <p className="text-xs text-gray-500">{detail.notes}</p>
            )}

            {detail.status === 'PENDING' &&
              (hasPermission('RETURN_APPROVE') ? (
                <div className="flex justify-end gap-2 pt-2 border-t border-gray-200 dark:border-gray-700">
                  <Button
                    variant="outline"
                    onClick={() => decide.mutate({ id: detail.id, approve: false })}
                    disabled={decide.isPending}
                  >
                    <X size={14} /> Reject
                  </Button>
                  <Button
                    onClick={() => decide.mutate({ id: detail.id, approve: true })}
                    disabled={decide.isPending}
                  >
                    <Check size={14} /> Approve and restock
                  </Button>
                </div>
              ) : (
                <p className="text-xs text-gray-400 border-t border-gray-200 dark:border-gray-700 pt-3">
                  This return is awaiting approval from somebody who holds that
                  permission. Nothing about it is decided yet.
                </p>
              ))}

            {detail.status !== 'PENDING' && (
              <p className="text-xs text-gray-400 border-t border-gray-200 dark:border-gray-700 pt-3">
                This return has already been {String(detail.status).toLowerCase()}.
                {detail.decisionNote && ` Reason: ${detail.decisionNote}`}
              </p>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}

function Term({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-gray-400">{label}</dt>
      <dd className="text-gray-800 dark:text-gray-200">{value}</dd>
    </div>
  );
}