'use client';
/**
 * ============================================================
 * SALE VOID / REFUND APPROVALS
 * ============================================================
 *
 * The queue behind GET /api/v1/sales/approvals.
 *
 * A Cashier may ask for a sale to be voided or refunded, but cannot decide: a
 * request goes in with SALE_VOID_REQUEST / SALE_REFUND_REQUEST and the stock
 * stays out until someone holding the matching *_APPROVE permission signs it off.
 * That is the whole point of the split — the person at the till is not the person
 * who gives the money back.
 *
 * Approving a void or refund returns the sold items to stock, so this screen is
 * where stock correctness is defended. It therefore requires an approve
 * authority; the request authority alone does not reach it.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Check, X, Ban, Undo2, Clock } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import ErrorState from '@/components/ui/ErrorState';
import { useToast } from '@/components/ui/ToastProvider';
import apiClient, { fetchAllPages } from '@/lib/api/apiClient';
import { extractErrorMessage } from '@/lib/api/response';
import { formatCurrency } from '@/lib/utils';

const PENDING = 'PENDING';

interface AwaitingApproval {
  id: number;
  orderNumber?: string | null;
  reference?: string | null;
  customerName?: string | null;
  total?: number | null;
  finalAmount?: number | null;
  status?: string | null;
  paymentStatus?: string | null;
  date?: string | null;
  createdBy?: string | null;
  voidStatus?: string | null;
  voidReason?: string | null;
  refundStatus?: string | null;
  refundReason?: string | null;
}

type Decision = 'void' | 'refund';

function requestOf(row: AwaitingApproval): Decision | null {
  if (row.voidStatus === PENDING) return 'void';
  if (row.refundStatus === PENDING) return 'refund';
  return null;
}

function reasonOf(row: AwaitingApproval): string {
  return (requestOf(row) === 'refund' ? row.refundReason : row.voidReason) || '';
}

export default function SaleApprovalsPage() {
  const { toast } = useToast();
  const [orders, setOrders] = useState<AwaitingApproval[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [workingKey, setWorkingKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Every page: an approval left unread on page 3 is still an approval.
      setOrders(await fetchAllPages<AwaitingApproval>('/sales/approvals'));
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load the approval queue.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const review = async (row: AwaitingApproval, decision: Decision, verdict: 'approve' | 'reject') => {
    const kind = requestOf(row) ?? decision;
    const key = `${row.id}:${kind}`;
    setWorkingKey(key);
    try {
      await apiClient.post(`/sales/${row.id}/${kind}-${verdict}`, { note: '' });
      toast('success',
        verdict === 'approve'
          ? `${kind === 'void' ? 'Void' : 'Refund'} approved. Stock returned to inventory.`
          : `${kind === 'void' ? 'Void' : 'Refund'} rejected. Nothing changed.`);
      await load();
    } catch (err) {
      toast('error', 'Review failed', extractErrorMessage(err, 'Please try again.'));
    } finally {
      setWorkingKey(null);
    }
  };

  const columns: Column<AwaitingApproval>[] = [
    {
      key: 'orderNumber',
      label: 'Sale',
      render: (_: unknown, row) => (
        <div>
          <p className="font-mono text-sm text-gray-900 dark:text-gray-100">
            {row.orderNumber ?? row.reference ?? `#${row.id}`}
          </p>
          <p className="text-xs text-gray-500">
            {row.date ?? ''}{row.customerName ? ` · ${row.customerName}` : ''}
          </p>
        </div>
      ),
    },
    {
      key: 'request',
      label: 'Request',
      render: (_: unknown, row) => {
        const kind = requestOf(row);
        if (kind === 'void') {
          return <Badge variant="warning">Void</Badge>;
        }
        if (kind === 'refund') {
          return <Badge variant="info">Refund</Badge>;
        }
        return <Badge variant="default">Settled</Badge>;
      },
    },
    {
      key: 'reason',
      label: 'Reason given',
      render: (_: unknown, row) => {
        const reason = reasonOf(row);
        return reason ? (
          <span className="text-sm">{reason}</span>
        ) : (
          <span className="text-gray-400">No reason supplied</span>
        );
      },
    },
    {
      key: 'amount',
      label: 'Amount',
      render: (_: unknown, row) => (
        <span className="font-semibold">
          {formatCurrency(Number(row.total ?? row.finalAmount ?? 0))}
        </span>
      ),
    },
    {
      key: 'createdBy',
      label: 'Raised by',
      render: (v: unknown) => (v ? String(v) : <span className="text-gray-400">—</span>),
    },
    {
      key: 'state',
      label: 'State',
      render: (_: unknown, row) => (
        <div className="space-y-1">
          <Badge variant={row.status === 'CANCELLED' ? 'warning' : 'default'}>
            {row.status ?? 'COMPLETED'}
          </Badge>
          <p className="text-xs text-gray-500">{row.paymentStatus ?? ''}</p>
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Sale Approvals"
        subtitle="Void and refund requests waiting on a decision. Nothing changes until approved."
      />

      {error ? (
        <ErrorState message={error} retry={load} />
      ) : (
        <div className="space-y-4">
          <div className="flex items-center gap-3 rounded-xl border border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-900/20 px-4 py-3">
            <Clock size={18} className="text-blue-600 dark:text-blue-300 shrink-0" />
            <p className="text-sm text-blue-800 dark:text-blue-200">
              {orders.length === 0
                ? 'Nothing is waiting for approval.'
                : `${orders.length} request${orders.length === 1 ? '' : 's'} waiting for approval. ` +
                  'Approving returns the sold items to stock.'}
            </p>
          </div>

          <DataTable
            columns={columns}
            data={orders}
            loading={loading}
            searchable
            searchPlaceholder="Search by order number or customer..."
            actions={(row) => {
              const kind = requestOf(row);
              if (!kind) {
                return <span className="text-xs text-gray-400">Already settled</span>;
              }
              const Icon = kind === 'void' ? Ban : Undo2;
              return (
                <div className="flex items-center justify-end gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    title={`Reject this ${kind} request`}
                    disabled={workingKey === `${row.id}:${kind}`}
                    onClick={() => review(row, kind, 'reject')}
                  >
                    <X size={15} className="text-red-500" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    title={`Approve this ${kind} and return stock`}
                    disabled={workingKey === `${row.id}:${kind}`}
                    onClick={() => review(row, kind, 'approve')}
                  >
                    <Icon size={15} className="text-emerald-600" />
                    <Check size={13} className="text-emerald-600 ml-0.5" />
                  </Button>
                </div>
              );
            }}
          />
        </div>
      )}
    </div>
  );
}
