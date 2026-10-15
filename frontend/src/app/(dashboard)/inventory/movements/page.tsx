'use client';
import React from 'react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import ErrorState from '@/components/ui/ErrorState';
import { useStockMovements } from '@/hooks/useInventory';
import { formatDate } from '@/lib/utils';
import type { StockMovement } from '@/lib/api/inventoryApi';
import { ArrowDownCircle, ArrowUpCircle } from 'lucide-react';

/**
 * Stock coming in, and stock going out.
 *
 * The colour of a movement answers one question — did stock arrive or leave —
 * so it is taken from the direction the backend recorded, with the type used
 * only when the direction is missing. It used to be computed as
 * `Number(String(type)) >= 0`, which is `NaN` for every real type and therefore
 * false for all of them: every row on this screen, including purchases and
 * sales returns, was painted as a negative movement.
 */
const INBOUND = new Set(['IN', 'OPENING', 'PURCHASE_RECEIPT', 'TRANSFER_IN', 'SALES_RETURN']);
const OUTBOUND = new Set([
  'OUT', 'SALE', 'TRANSFER_OUT', 'DAMAGE', 'EXPIRY', 'LOSS', 'PURCHASE_RETURN',
]);

function movementVariant(type: string, direction?: string): 'success' | 'danger' | 'default' {
  const d = (direction ?? '').toLowerCase();
  if (d === 'in') return 'success';
  if (d === 'out') return 'danger';
  const t = type.toUpperCase();
  if (INBOUND.has(t)) return 'success';
  if (OUTBOUND.has(t)) return 'danger';
  return 'default';
}

/** `PURCHASE_RECEIPT` reads better as `PURCHASE RECEIPT` than as a constant. */
function movementLabel(type: string): string {
  return String(type || '').replace(/_/g, ' ');
}

export default function InventoryMovementsPage() {
  const { data: movements = [], isLoading, error, refetch } = useStockMovements();

  const columns: Column<any>[] = [
    { key: 'createdAt', label: 'Date', render: v => formatDate(String(v || '')) },
    { key: 'reference', label: 'Reference', render: v => <span className="font-mono text-xs text-blue-600">{String(v || '-')}</span> },
    { key: 'productName', label: 'Product' },
    {
      key: 'type',
      label: 'Type',
      render: (v, row) => (
        <Badge variant={movementVariant(String(v), row.direction)}>{movementLabel(String(v))}</Badge>
      ),
    },
    { key: 'direction', label: 'Direction', render: v => (
      <div className="flex items-center gap-1">
        {v === 'in' ? <ArrowDownCircle size={14} className="text-emerald-500" /> : <ArrowUpCircle size={14} className="text-red-500" />}
        <span className={v === 'in' ? 'text-emerald-600 font-medium' : 'text-red-600 font-medium'}>{String(v || '').toUpperCase()}</span>
      </div>
    )},
    { key: 'quantity', label: 'Qty', render: (v, row) => <span className={row.direction === 'in' ? 'text-emerald-600 font-semibold' : 'text-red-600 font-semibold'}>{row.direction === 'in' ? '+' : ''}{String(v)}</span> },
    { key: 'notes', label: 'Note', render: v => v ? String(v) : '-' },
  ];

  if (error) return <ErrorState error={error} retry={refetch} />;

  return (
    <div>
      <PageHeader title="Movements" subtitle="All inventory in/out transactions" />
      <DataTable
        columns={columns}
        data={movements}
        loading={isLoading}
        searchable
        searchPlaceholder="Search movements..."
      />
    </div>
  );
}
