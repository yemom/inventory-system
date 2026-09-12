'use client';
import React, { useState } from 'react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Textarea from '@/components/ui/Textarea';
import ErrorState from '@/components/ui/ErrorState';
import { useInventory, useStockAdjust } from '@/hooks/useInventory';
import { useToast } from '@/components/ui/ToastProvider';
import { Plus } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import type { Product } from '@/lib/api/mockData';

const schema = z.object({
  productId: z.string().min(1, 'Product required'),
  qty: z.coerce.number().int().refine(v => v !== 0, 'Quantity cannot be zero'),
  note: z.string().min(2, 'Note required'),
});
type FormData = z.infer<typeof schema>;

export default function AdjustmentsPage() {
  const { data: products = [], isLoading, error, refetch } = useInventory();
  const adjust = useStockAdjust();
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: FormData) => {
    try {
      await adjust.mutateAsync(data);
      toast('success', 'Stock adjusted successfully');
      setModalOpen(false);
      reset();
    } catch {
      toast('error', 'Adjustment failed');
    }
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'name', label: 'Product', render: (_, row) => (
      <div><p className="font-medium">{String(row.name)}</p><p className="text-xs text-gray-400">{String(row.sku)}</p></div>
    )},
    { key: 'category', label: 'Category' },
    { key: 'quantity', label: 'Current Stock', render: (v, row) => {
      const qty = Number(v); const rl = Number(row.reorderLevel);
      return <Badge variant={qty === 0 ? 'danger' : qty <= rl ? 'warning' : 'success'}>{qty} {String(row.unit)}</Badge>;
    }},
    { key: 'reorderLevel', label: 'Reorder At' },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader
        title="Stock Adjustments"
        subtitle="Manually adjust stock for losses, damages, or corrections"
        actions={<Button onClick={() => setModalOpen(true)}><Plus size={16} /> New Adjustment</Button>}
      />
      <DataTable columns={columns} data={products as unknown as Record<string, unknown>[]} loading={isLoading} searchable />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="New Stock Adjustment">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <Select
            label="Product"
            options={products.map(p => ({ value: p.id, label: `${p.name} (${p.quantity} ${p.unit})` }))}
            placeholder="Select product"
            error={errors.productId?.message}
            {...register('productId')}
          />
          <Input
            label="Quantity (positive = add, negative = remove)"
            type="number"
            placeholder="e.g. -5 for loss, +10 for found"
            error={errors.qty?.message}
            {...register('qty')}
          />
          <Textarea label="Reason / Note" placeholder="e.g. Damaged goods, inventory count correction..." error={errors.note?.message} {...register('note')} />
          <div className="flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={adjust.isPending}>Apply Adjustment</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
