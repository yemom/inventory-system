'use client';
import React from 'react';
import { Warehouse, AlertTriangle, Package, ArrowUpDown } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import StatCard from '@/components/ui/StatCard';
import ErrorState from '@/components/ui/ErrorState';
import { useInventory } from '@/hooks/useInventory';
import type { Product } from '@/lib/api/mockData';
import { formatCurrency } from '@/lib/utils';
import { StatCardSkeleton } from '@/components/ui/Skeleton';

export default function InventoryPage() {
  const { data: products = [], isLoading, error, refetch } = useInventory();

  const totalValue = products.reduce((s, p) => s + p.quantity * p.costPrice, 0);
  const lowStock = products.filter(p => p.quantity > 0 && p.quantity <= p.reorderLevel).length;
  const outOfStock = products.filter(p => p.quantity === 0).length;
  const totalSKUs = products.length;

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'name', label: 'Product', render: (_, row) => (
      <div>
        <p className="font-medium text-gray-900 dark:text-gray-100">{String(row.name)}</p>
        <p className="text-xs text-gray-400 font-mono">{String(row.sku)}</p>
      </div>
    )},
    { key: 'category', label: 'Category' },
    { key: 'quantity', label: 'Qty On Hand', render: (v, row) => {
      const qty = Number(v); const rl = Number(row.reorderLevel);
      return (
        <span className={qty === 0 ? 'text-red-600 font-bold' : qty <= rl ? 'text-amber-600 font-semibold' : 'text-emerald-600 font-semibold'}>
          {qty} {String(row.unit)}
        </span>
      );
    }},
    { key: 'reorderLevel', label: 'Reorder At', render: (v, row) => `${v} ${row.unit}` },
    { key: 'costPrice', label: 'Unit Cost', render: v => formatCurrency(Number(v)) },
    { key: 'stockValue', label: 'Stock Value', render: (_, row) => formatCurrency(Number(row.quantity) * Number(row.costPrice)) },
    { key: 'status', label: 'Status', render: (_, row) => {
      const qty = Number(row.quantity); const rl = Number(row.reorderLevel);
      if (qty === 0) return <Badge variant="danger">Out of Stock</Badge>;
      if (qty <= rl) return <Badge variant="warning">Low Stock</Badge>;
      return <Badge variant="success">In Stock</Badge>;
    }},
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div className="space-y-6">
      <PageHeader title="Inventory" subtitle="Real-time stock levels and valuation" />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {isLoading ? Array.from({ length: 4 }).map((_, i) => <StatCardSkeleton key={i} />) : (
          <>
            <StatCard title="Total SKUs" value={totalSKUs} icon={<Package size={22} />} color="blue" />
            <StatCard title="Stock Value" value={formatCurrency(totalValue)} icon={<Warehouse size={22} />} color="emerald" />
            <StatCard title="Low Stock Items" value={lowStock} icon={<AlertTriangle size={22} />} color="amber" />
            <StatCard title="Out of Stock" value={outOfStock} icon={<Package size={22} />} color="red" />
          </>
        )}
      </div>

      <Card>
        <CardHeader>
          <h3 className="font-semibold text-gray-800 dark:text-gray-200">Stock Overview</h3>
        </CardHeader>
        <CardContent className="p-0 pb-4">
          <DataTable
            columns={columns}
            data={products as unknown as Record<string, unknown>[]}
            loading={isLoading}
            searchable
            searchPlaceholder="Search products..."
          />
        </CardContent>
      </Card>
    </div>
  );
}
