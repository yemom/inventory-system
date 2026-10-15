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
import { usePurchases } from '@/hooks/usePurchases';
import { useAuth } from '@/lib/auth/AuthProvider';
import { useToast } from '@/components/ui/ToastProvider';

/**
 * Returns to suppliers.
 *
 * <h2>What changed and why it mattered</h2>
 *
 * <p>Like the customer-return screen, this one kept its records in
 * {@code localStorage} and faked the stock side with a bare inventory
 * adjustment. A purchase return is the more consequential of the two to get
 * wrong: goods leaving the warehouse back to a supplier reduces stock at cost,
 * and without a real movement the inventory valuation stays overstated and the
 * stock ledger shows unexplained shrinkage instead of a return.
 *
 * <p>Returns are now documents on the server, with the same two-step split —
 * raised, then approved — and approving posts both the stock movement and the
 * financial reversal. Nothing is written off automatically.
 */

/** Why goods go back to a supplier. */
const REASONS = [
  { value: 'DAMAGED', label: 'Damaged in transit' },
  { value: 'WRONG_ITEM', label: 'Wrong item supplied' },
  { value: 'NOT_AS_DESCRIBED', label: 'Not as described' },
  { value: 'OVER_ORDERED', label: 'Over-ordered' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'QUALITY', label: 'Quality below standard' },
  { value: 'OTHER', label: 'Other' },
];

export default function PurchaseReturnsPage() {
  const { toast } = useToast();
  const { hasPermission } = useAuth();
  const qc = useQueryClient();

  const [status, setStatus] = useState('ALL');
  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState<any>(null);

  const [orderId, setOrderId] = useState('');
  const [lines, setLines] = useState<Record<string, number>>({});
  const [reason, setReason] = useState('DAMAGED');
  const [notes, setNotes] = useState('');

  const { data: returns = [], isLoading, error, refetch } = useQuery({
    queryKey: ['returns', 'purchases', status],
    queryFn: () =>
      returnsApi.list({
        type: 'PURCHASE',
        ...(status === 'ALL' ? {} : { status }),
      }) as Promise<any[]>,
    retry: false,
  });

  // Only orders with something still returnable — goods actually received, not
  // merely ordered. You cannot send back what has not arrived.
  const { data: purchases = [] } = usePurchases();
  const returnableOrders = useMemo(
    () =>
      (purchases as any[]).filter((order) => {
        const status = String(order.status ?? '').toUpperCase();
        if (status === 'DRAFT' || status === 'CANCELLED') return false;
        return (order.items ?? []).some(
          (item: any) =>
            Number(item.quantityReceived ?? item.quantity ?? 0) -
              Number(item.quantityReturned ?? 0) >
            0,
        );
      }),
    [purchases],
  );

  const selectedOrder = useMemo(
    () =>
      (returnableOrders as any[]).find((o) => String(o.id) === orderId) ?? null,
    [returnableOrders, orderId],
  );

  const selectedLines = useMemo<
    Array<{
      id: number | string;
      productName: string;
      received: number;
      returned: number;
      unitPrice: number;
    }>
  >(() => {
    if (!selectedOrder) return [];
    return (selectedOrder.items ?? []).map((item: any) => {
      const received = Number(item.quantityReceived ?? item.quantity ?? 0);
      return {
        id: item.id,
        productName: item.productName ?? item.product?.name ?? '—',
        received,
        returned: Number(item.quantityReturned ?? 0),
        unitPrice: Number(item.unitPrice ?? 0),
      };
    });
  }, [selectedOrder]);

  const reset = () => {
    setOpen(false);
    setOrderId('');
    setLines({});
    setReason('DAMAGED');
    setNotes('');
  };

  const raise = useMutation({
    mutationFn: () =>
      returnsApi.purchaseReturns(
        orderId,
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
        'It is now awaiting approval. No stock has left the warehouse yet.',
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
          ? 'Stock has been written down and the purchase reversed.'
          : 'Nothing was written off.',
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
      label: 'Against order',
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
        title="Purchase Returns"
        subtitle="Goods going back to a supplier. Raising one changes nothing until it is approved."
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
        <label htmlFor="purchase-return-status" className="text-sm text-gray-500">
          Show
        </label>
        <Select
          id="purchase-return-status"
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
            No purchase returns recorded. This is an empty answer, not a missing
            one.
          </CardContent>
        </Card>
      )}

      <Modal open={open} onClose={reset} title="Raise a purchase return">
        <div className="space-y-4">
          <Select
            label="Against which purchase order"
            options={
              returnableOrders.length > 0
                ? (returnableOrders as any[]).map((o) => ({
                    value: String(o.id),
                    label: `${o.orderNumber ?? o.reference ?? o.id} — ${
                      o.supplierName ?? o.supplier?.name ?? 'Supplier'
                    }`,
                  }))
                : [{ value: '', label: 'No order has anything returnable' }]
            }
            value={orderId}
            onChange={(event) => {
              setOrderId(event.target.value);
              setLines({});
            }}
          />

          {selectedLines.length > 0 && (
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium text-gray-700 dark:text-gray-300">
                What is going back
              </legend>
              {selectedLines.map((line) => {
                const remaining = line.received - line.returned;
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
                        {remaining} of {line.received} received units still
                        returnable
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
            Raising a return records the request only. Stock stays in the
            warehouse and the supplier is not credited until somebody with
            approval permission accepts it.
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
                !orderId ||
                Object.values(lines).every((q) => q <= 0)
              }
            >
              Raise return
            </Button>
          </div>
        </div>
      </Modal>

      <Modal
        open={detail !== null}
        onClose={() => setDetail(null)}
        title={detail ? `Return ${detail.reference}` : ''}
      >
        {detail && (
          <div className="space-y-4">
            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              <Term
                label="Against order"
                value={String(detail.originalReference)}
              />
              <Term label="Status" value={String(detail.status)} />
              <Term
                label="Reason"
                value={String(detail.reason ?? '—').replace(/_/g, ' ')}
              />
              <Term
                label="Date"
                value={formatDate(
                  String(detail.transactionDate ?? '').slice(0, 10),
                )}
              />
            </dl>

            {(detail.items ?? []).length > 0 && (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs uppercase text-gray-400">
                    <th className="py-1.5">Item</th>
                    <th className="py-1.5 text-right">Qty</th>
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
                    <Check size={14} /> Approve and write off
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
                This return has already been{' '}
                {String(detail.status).toLowerCase()}.
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