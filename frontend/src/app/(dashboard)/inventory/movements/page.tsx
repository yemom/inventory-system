'use client';
import React from 'react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import ErrorState from '@/components/ui/ErrorState';
import { useStockMovements } from '@/hooks/useInventory';
import { formatDate } from '@/lib/utils';
import type { StockMovement } from '@/lib/api/mockData';
import { ArrowDownCircle, ArrowUpCircle } from 'lucide-react';

export default function InventoryMovementsPage() {
  const { data: movements = [], isLoading, error, refetch } = useStockMovements();

  const typeVariant: Record<string, 'success' | 'danger' | 'info' | 'warning' | 'default'> = {
    purchase: 'success', sale: 'info', return: 'warning', adjustment: 'default', transfer: 'default',
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'date', label: 'Date', render: v => formatDate(String(v)) },
    { key: 'reference', label: 'Reference', render: v => <span className="font-mono text-xs text-blue-600">{String(v)}</span> },
    { key: 'productName', label: 'Product' },
    { key: 'type', label: 'Type', render: v => <Badge variant={typeVariant[String(v)] ?? 'default'}>{String(v)}</Badge> },
    { key: 'direction', label: 'Direction', render: v => (
      <div className="flex items-center gap-1">
        {v === 'in' ? <ArrowDownCircle size={14} className="text-emerald-500" /> : <ArrowUpCircle size={14} className="text-red-500" />}
        <span className={v === 'in' ? 'text-emerald-600 font-medium' : 'text-red-600 font-medium'}>{String(v).toUpperCase()}</span>
      </div>
    )},
    { key: 'quantity', label: 'Qty', render: (v, row) => <span className={row.direction === 'in' ? 'text-emerald-600 font-semibold' : 'text-red-600 font-semibold'}>{row.direction === 'in' ? '+' : '-'}{String(v)}</span> },
    { key: 'note', label: 'Note', render: v => v ? String(v) : '-' },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader title="Stock Movements" subtitle="All inventory in/out transactions" />
      <DataTable
        columns={columns}
        data={movements as unknown as Record<string, unknown>[]}
        loading={isLoading}
        searchable
        searchPlaceholder="Search movements..."
      />
    </div>
  );
}
