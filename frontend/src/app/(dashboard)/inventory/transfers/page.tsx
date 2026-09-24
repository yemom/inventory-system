'use client';
import React, { useState } from 'react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ErrorState from '@/components/ui/ErrorState';
import { useTransfers, useInventory } from '@/hooks/useInventory';
import { useToast } from '@/components/ui/ToastProvider';
import { formatDate } from '@/lib/utils';
import { Plus } from 'lucide-react';
import type { StockMovement } from '@/lib/api/inventoryApi';
import { inventoryApi } from '@/lib/api/inventoryApi';
import { useQueryClient } from '@tanstack/react-query';

const LOCATIONS = ['Main Warehouse', 'Branch 1', 'Branch 2', 'Branch 3'];
const statusVariant: Record<string, 'success' | 'info' | 'default' | 'danger'> = {
  completed: 'success', in_transit: 'info', draft: 'default', cancelled: 'danger',
};

export default function TransfersPage() {
  const { data: transfers = [], isLoading, error, refetch } = useTransfers();
  const { data: products = [] } = useInventory();
  const { toast } = useToast();
  const qc = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({ from: '', to: '', productId: '', qty: '' });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.from || !form.to || !form.productId || !form.qty) { toast('warning', 'Please fill all fields'); return; }
    const product = products.find(p => p.id === form.productId);
    setSubmitting(true);
    try {
      await inventoryApi.createTransfer({
        reference: 'TR-' + Date.now(),
        fromLocation: form.from, toLocation: form.to, date: new Date().toISOString().slice(0, 10),
        status: 'draft',
        items: [{ productId: form.productId, productName: product?.name ?? '', quantity: Number(form.qty) }],
      });
      qc.invalidateQueries({ queryKey: ['transfers'] });
      toast('success', 'Transfer created');
      setModalOpen(false); setForm({ from: '', to: '', productId: '', qty: '' });
    } catch { toast('error', 'Failed to create transfer'); }
    setSubmitting(false);
  };

  const columns: Column<any>[] = [
    { key: 'reference', label: 'Reference', render: v => <span className="font-mono text-xs text-blue-600">{String(v)}</span> },
    { key: 'date', label: 'Date', render: v => formatDate(String(v || '')) },
    { key: 'fromLocation', label: 'From' },
    { key: 'toLocation', label: 'To' },
    { key: 'items', label: 'Items', render: v => `${(v as []).length} item(s)` },
    { key: 'status', label: 'Status', render: v => <Badge variant={statusVariant[String(v)] ?? 'default'}>{String(v).replace('_', ' ')}</Badge> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader title="Inventory Transfers" subtitle="Move stock between locations"
        actions={<Button onClick={() => setModalOpen(true)}><Plus size={16} /> New Transfer</Button>} />
      <DataTable columns={columns} data={transfers as unknown as Record<string, unknown>[]} loading={isLoading} searchable />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="New Stock Transfer">
        <form onSubmit={handleSubmit} className="space-y-4">
          <Select label="From Location" options={LOCATIONS.map((l: any) => ({ value: l, label: l }))} placeholder="Select source" value={form.from} onChange={e => setForm(f => ({ ...f, from: e.target.value }))} />
          <Select label="To Location" options={LOCATIONS.map((l: any) => ({ value: l, label: l }))} placeholder="Select destination" value={form.to} onChange={e => setForm(f => ({ ...f, to: e.target.value }))} />
          <Select label="Product" options={products.map((p: any) => ({ value: p.id, label: `${p.name} (${p.quantity} available)` }))} placeholder="Select product" value={form.productId} onChange={e => setForm(f => ({ ...f, productId: e.target.value }))} />
          <Input label="Quantity" type="number" min="1" value={form.qty} onChange={e => setForm(f => ({ ...f, qty: e.target.value }))} />
          <div className="flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={submitting}>Create Transfer</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
