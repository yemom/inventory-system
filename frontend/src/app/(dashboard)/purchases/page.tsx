'use client';
import React, { useState } from 'react';
import { Plus, Eye, Trash2 } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency, formatDate } from '@/lib/utils';
import { usePurchases, useDeletePurchase } from '@/hooks/usePurchases';
import { useToast } from '@/components/ui/ToastProvider';
import type { PurchaseOrder } from '@/lib/api/purchasesApi';

const statusVariant: Record<string, 'success' | 'info' | 'default' | 'danger'> = {
  received: 'success', ordered: 'info', draft: 'default', cancelled: 'danger',
};
const payVariant: Record<string, 'success' | 'warning' | 'danger'> = {
  paid: 'success', partial: 'warning', unpaid: 'danger',
};

export default function PurchasesPage() {
  const { data: purchases = [], isLoading, error, refetch } = usePurchases();
  const deletePurchase = useDeletePurchase();
  const { toast } = useToast();
  const [viewOrder, setViewOrder] = useState<PurchaseOrder | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<PurchaseOrder | null>(null);

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try { await deletePurchase.mutateAsync(String(deleteTarget.id)); toast('success', 'Order deleted'); }
    catch { toast('error', 'Delete failed'); }
    finally { setDeleteTarget(null); }
  };

  const columns: Column<any>[] = [
    { key: 'reference', label: 'Reference', render: v => <span className="font-mono text-xs font-semibold text-emerald-600">{String(v)}</span> },
    { key: 'supplierName', label: 'Supplier' },
    { key: 'date', label: 'Date', render: v => formatDate(String(v || '')) },
    { key: 'total', label: 'Total', render: v => <span className="font-semibold">{formatCurrency(Number(v))}</span> },
    { key: 'paymentStatus', label: 'Payment', render: v => <Badge variant={payVariant[String(v)] ?? 'default'}>{String(v)}</Badge> },
    { key: 'status', label: 'Status', render: v => <Badge variant={statusVariant[String(v)] ?? 'default'}>{String(v)}</Badge> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader
        title="Purchases"
        subtitle={`${purchases.length} orders total`}
        actions={<Button onClick={() => toast('info', 'New purchase form coming soon')}><Plus size={16} /> New PO</Button>}
      />
      <DataTable
        columns={columns} data={purchases as unknown as Record<string, unknown>[]} loading={isLoading} searchable
        searchPlaceholder="Search orders..."
        actions={row => (
          <div className="flex items-center justify-end gap-1">
            <button onClick={() => setViewOrder(row as unknown as PurchaseOrder)} className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 hover:text-emerald-600">
              <Eye size={15} />
            </button>
            <button onClick={() => setDeleteTarget(row as unknown as PurchaseOrder)} className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 hover:text-red-600">
              <Trash2 size={15} />
            </button>
          </div>
        )}
      />

      <Modal open={!!viewOrder} onClose={() => setViewOrder(null)} title={`Purchase Order — ${viewOrder?.reference}`} size="xl">
        {viewOrder && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div><p className="text-gray-500">Supplier</p><p className="font-medium">{viewOrder.supplierName}</p></div>
              <div><p className="text-gray-500">Date</p><p className="font-medium">{formatDate(viewOrder.date || '')}</p></div>
              <div><p className="text-gray-500">Status</p><Badge variant={statusVariant[viewOrder.status] ?? 'default'}>{viewOrder.status}</Badge></div>
              <div><p className="text-gray-500">Payment</p><Badge variant={payVariant[viewOrder.paymentStatus || ''] ?? 'default'}>{viewOrder.paymentStatus}</Badge></div>
            </div>
            <table className="w-full text-sm border rounded-lg overflow-hidden">
              <thead className="bg-gray-50 dark:bg-gray-700">
                <tr>{['Product', 'Qty', 'Unit Cost', 'Total'].map((h: any) => <th key={h} className="px-3 py-2 text-left text-xs font-semibold text-gray-500">{h}</th>)}</tr>
              </thead>
              <tbody>{viewOrder.items.map((item: any, i: number) => (
                <tr key={i} className="border-t">
                  <td className="px-3 py-2">{item.productName}</td>
                  <td className="px-3 py-2">{item.quantity}</td>
                  <td className="px-3 py-2">{formatCurrency(item.unitCost || 0)}</td>
                  <td className="px-3 py-2 font-semibold">{formatCurrency(item.total || 0)}</td>
                </tr>
              ))}</tbody>
            </table>
            <div className="flex justify-end gap-6 text-sm border-t pt-3">
              <span className="text-gray-900 dark:text-gray-100 font-bold text-base">Total: {formatCurrency(viewOrder.total || 0)}</span>
            </div>
          </div>
        )}
      </Modal>
      <ConfirmDialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={confirmDelete} title="Delete PO" message={`Delete PO ${deleteTarget?.reference}?`} confirmLabel="Delete" loading={deletePurchase.isPending} />
    </div>
  );
}
