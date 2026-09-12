'use client';
import React, { useState } from 'react';
import { Plus, Edit, Trash2, Eye, Package } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Textarea from '@/components/ui/Textarea';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency } from '@/lib/utils';
import { useProducts, useCreateProduct, useUpdateProduct, useDeleteProduct } from '@/hooks/useProducts';
import { useToast } from '@/components/ui/ToastProvider';
import type { Product } from '@/lib/api/mockData';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const schema = z.object({
  name: z.string().min(2, 'Name required'),
  sku: z.string().min(2, 'SKU required'),
  category: z.string().min(1, 'Category required'),
  unit: z.string().min(1, 'Unit required'),
  costPrice: z.coerce.number().min(0),
  sellingPrice: z.coerce.number().min(0),
  quantity: z.coerce.number().min(0),
  reorderLevel: z.coerce.number().min(0),
  status: z.enum(['active', 'inactive', 'discontinued']),
});
type FormData = z.infer<typeof schema>;

const CATEGORIES = ['Grains', 'Oil & Fat', 'Sweeteners', 'Condiments', 'Canned Goods', 'Dry Goods', 'Household', 'Snacks', 'Beverages', 'Baby Care', 'Personal Care', 'Spices', 'Dairy', 'Other'];
const UNITS = ['bag', 'pack', 'can', 'bottle', 'box', 'jerry', 'packet', 'bundle', 'piece', 'kg', 'litre'];

export default function ProductsPage() {
  const { data: products = [], isLoading, error, refetch } = useProducts();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const deleteProduct = useDeleteProduct();
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Product | null>(null);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({ resolver: zodResolver(schema) });

  const openCreate = () => { setEditing(null); reset({ status: 'active', quantity: 0, reorderLevel: 10, costPrice: 0, sellingPrice: 0 }); setModalOpen(true); };
  const openEdit = (p: Product) => { setEditing(p); reset({ ...p }); setModalOpen(true); };

  const onSubmit = async (data: FormData) => {
    try {
      if (editing) {
        await updateProduct.mutateAsync({ id: editing.id, data });
        toast('success', 'Product updated successfully');
      } else {
        await createProduct.mutateAsync(data);
        toast('success', 'Product created successfully');
      }
      setModalOpen(false);
    } catch {
      toast('error', 'Operation failed', 'Please try again');
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteProduct.mutateAsync(deleteTarget.id);
      toast('success', 'Product deleted');
    } catch {
      toast('error', 'Delete failed');
    } finally {
      setDeleteTarget(null);
    }
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'name', label: 'Product Name', render: (_, row) => (
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 bg-blue-100 dark:bg-blue-900/30 rounded-lg flex items-center justify-center">
          <Package size={14} className="text-blue-600" />
        </div>
        <div>
          <p className="font-medium text-gray-900 dark:text-gray-100">{String(row.name)}</p>
          <p className="text-xs text-gray-400 font-mono">{String(row.sku)}</p>
        </div>
      </div>
    )},
    { key: 'category', label: 'Category' },
    { key: 'unit', label: 'Unit' },
    { key: 'sellingPrice', label: 'Selling Price', render: v => formatCurrency(Number(v)) },
    { key: 'costPrice', label: 'Cost Price', render: v => formatCurrency(Number(v)) },
    { key: 'quantity', label: 'In Stock', render: (v, row) => {
      const qty = Number(v); const reorder = Number(row.reorderLevel);
      return <Badge variant={qty === 0 ? 'danger' : qty <= reorder ? 'warning' : 'success'}>{qty} {String(row.unit)}</Badge>;
    }},
    { key: 'status', label: 'Status', render: v => <Badge variant={v === 'active' ? 'success' : v === 'inactive' ? 'default' : 'danger'}>{String(v)}</Badge> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader
        title="Products"
        subtitle={`${products.length} products in catalogue`}
        actions={<Button onClick={openCreate} className="gap-2"><Plus size={16} /> Add Product</Button>}
      />

      <DataTable
        columns={columns}
        data={products as unknown as Record<string, unknown>[]}
        loading={isLoading}
        searchable
        searchPlaceholder="Search products..."
        actions={(row) => (
          <div className="flex items-center justify-end gap-1">
            <button onClick={() => openEdit(row as unknown as Product)} className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 hover:text-blue-600 transition-colors">
              <Edit size={15} />
            </button>
            <button onClick={() => setDeleteTarget(row as unknown as Product)} className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 hover:text-red-600 transition-colors">
              <Trash2 size={15} />
            </button>
          </div>
        )}
      />

      {/* Create / Edit Modal */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Edit Product' : 'Add New Product'} size="lg">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input label="Product Name" error={errors.name?.message} {...register('name')} placeholder="e.g. Basmati Rice 25kg" />
            <Input label="SKU / Barcode" error={errors.sku?.message} {...register('sku')} placeholder="e.g. RICE-BAS-25" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Select label="Category" error={errors.category?.message} options={CATEGORIES.map(c => ({ value: c, label: c }))} placeholder="Select category" {...register('category')} />
            <Select label="Unit" error={errors.unit?.message} options={UNITS.map(u => ({ value: u, label: u }))} placeholder="Select unit" {...register('unit')} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Cost Price (ETB)" type="number" error={errors.costPrice?.message} {...register('costPrice')} />
            <Input label="Selling Price (ETB)" type="number" error={errors.sellingPrice?.message} {...register('sellingPrice')} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Initial Quantity" type="number" error={errors.quantity?.message} {...register('quantity')} />
            <Input label="Reorder Level" type="number" error={errors.reorderLevel?.message} {...register('reorderLevel')} />
          </div>
          <Select label="Status" options={[{ value: 'active', label: 'Active' }, { value: 'inactive', label: 'Inactive' }, { value: 'discontinued', label: 'Discontinued' }]} {...register('status')} />
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={createProduct.isPending || updateProduct.isPending}>{editing ? 'Update Product' : 'Create Product'}</Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={confirmDelete}
        title="Delete Product"
        message={`Are you sure you want to delete "${deleteTarget?.name}"? This action cannot be undone.`}
        confirmLabel="Delete"
        loading={deleteProduct.isPending}
      />
    </div>
  );
}
