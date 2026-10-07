'use client';
/**
 * ============================================================
 * STOCK ADJUSTMENT / COUNT APPROVALS
 * ============================================================
 *
 * The queue behind GET /api/v1/inventory/movements/pending.
 *
 * Inventory Staff record an adjustment or a stock count; those are written
 * PENDING and the product's quantity on hand does not move until somebody signs
 * them off. This page is that sign-off, and it is only reachable with
 * STOCK_ADJUSTMENT_APPROVE (Supervisor, Manager).
 *
 * Approving is the only button here, and it is deliberately the only one: a
 * rejected movement leaves stock untouched, so both outcomes are safe, but the
 * screen exists to make a human decision rather than to offer a bulk action.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { ClipboardCheck, Check, X, Clock } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import ErrorState from '@/components/ui/ErrorState';
import { useToast } from '@/components/ui/ToastProvider';
import apiClient, { fetchAllPages } from '@/lib/api/apiClient';
import { extractErrorMessage } from '@/lib/api/response';

interface PendingMovement {
  id: number;
  productId: number;
  productName?: string | null;
  productSku?: string | null;
  warehouseName?: string | null;
  type: string;
  quantity: number;
  notes?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  date?: string | null;
  status?: string | null;
}

export default function StockApprovalsPage() {
  const { toast } = useToast();
  const [movements, setMovements] = useState<PendingMovement[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [workingId, setWorkingId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetchAllPages<PendingMovement>('/inventory/movements/pending', {});
      setMovements(response as PendingMovement[]);
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load pending stock movements.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const review = async (movement: PendingMovement, decision: 'approve' | 'reject') => {
    setWorkingId(movement.id);
    try {
      await apiClient.post(`/inventory/movements/${movement.id}/${decision}`, { note: '' });
      toast('success',
        decision === 'approve'
          ? `Approved. Stock for ${movement.productSku ?? movement.productName} updated.`
          : 'Rejected. Stock was left unchanged.');
      await load();
    } catch (err) {
      toast('error', 'Review failed', extractErrorMessage(err, 'Please try again.'));
    } finally {
      setWorkingId(null);
    }
  };

  const columns: Column<PendingMovement>[] = [
    {
      key: 'productName',
      label: 'Product',
      render: (_: unknown, row) => (
        <div>
          <p className="font-medium text-gray-900 dark:text-gray-100">{row.productName ?? '—'}</p>
          <p className="font-mono text-xs text-gray-500">{row.productSku}</p>
        </div>
      ),
    },
    {
      key: 'type',
      label: 'Request',
      render: (v: unknown) => (
        <Badge variant={String(v) === 'STOCK_COUNT' ? 'info' : 'default'}>
          {String(v) === 'STOCK_COUNT' ? 'Stock count' : 'Adjustment'}
        </Badge>
      ),
    },
    {
      key: 'quantity',
      label: 'Change',
      render: (v: unknown) => {
        const qty = Number(v ?? 0);
        return (
          <span className={`font-semibold ${qty < 0 ? 'text-red-600' : 'text-emerald-600'}`}>
            {qty > 0 ? '+' : ''}{qty}
          </span>
        );
      },
    },
    { key: 'warehouseName', label: 'Warehouse', render: (v: unknown) => (v ? String(v) : '—') },
    {
      key: 'createdBy',
      label: 'Submitted by',
      render: (v: unknown, row) => (
        <div>
          <p className="text-sm">{row.createdBy ?? '—'}</p>
          <p className="text-xs text-gray-500">{row.date ?? ''}</p>
        </div>
      ),
    },
    {
      key: 'notes',
      label: 'Note',
      render: (v: unknown) => (v ? String(v) : <span className="text-gray-400">—</span>),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Stock Approvals"
        subtitle="Adjustments and stock counts waiting for sign-off. Stock does not move until approved."
      />

      {error ? (
        <ErrorState message={error} retry={load} />
      ) : (
        <div className="space-y-4">
          <div className="flex items-center gap-3 rounded-xl border border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-900/20 px-4 py-3">
            <ClipboardCheck size={18} className="text-blue-600 dark:text-blue-300 shrink-0" />
            <p className="text-sm text-blue-800 dark:text-blue-200">
              {movements.length === 0
                ? 'Nothing is waiting for approval.'
                : `${movements.length} request${movements.length === 1 ? '' : 's'} waiting for approval.`}
            </p>
          </div>

          <DataTable
            columns={columns}
            data={movements}
            loading={loading}
            searchable
            searchPlaceholder="Search by product or SKU..."
            actions={(row) => (
              <div className="flex items-center justify-end gap-1">
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={workingId === row.id}
                  title="Approve and apply to stock"
                  onClick={() => review(row, 'approve')}
                >
                  <Check size={15} className="text-emerald-600" />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={workingId === row.id}
                  title="Reject and leave stock unchanged"
                  onClick={() => review(row, 'reject')}
                >
                  <X size={15} className="text-red-500" />
                </Button>
              </div>
            )}
          />
        </div>
      )}
    </div>
  );
}