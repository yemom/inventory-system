'use client';
import React, { useState } from 'react';
import { Plus, Edit, Trash2, Mail, Phone, User as UserIcon } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency } from '@/lib/utils';
import { useSuppliers, useCreateSupplier, useUpdateSupplier, useDeleteSupplier } from '@/hooks/useSuppliers';
import { useToast } from '@/components/ui/ToastProvider';
import type { Supplier } from '@/lib/api/mockData';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const schema = z.object({
  name: z.string().min(2, 'Name required'),
  contact: z.string().min(2, 'Contact person required'),
  phone: z.string().min(6, 'Phone required'),
  email: z.string().email().or(z.literal('')),
  address: z.string(),
  status: z.enum(['active', 'inactive']),
});
type FormData = z.infer<typeof schema>;

export default function SuppliersPage() {
  const { data: suppliers = [], isLoading, error, refetch } = useSuppliers();
  const createSupplier = useCreateSupplier();
  const updateSupplier = useUpdateSupplier();
  const deleteSupplier = useDeleteSupplier();
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Supplier | null>(null);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({ resolver: zodResolver(schema) });

  const openCreate = () => { setEditing(null); reset({ status: 'active', email: '', address: '' }); setModalOpen(true); };
  const openEdit = (s: Supplier) => { setEditing(s); reset({ ...s }); setModalOpen(true); };

  const onSubmit = async (data: FormData) => {
    try {
      if (editing) { await updateSupplier.mutateAsync({ id: editing.id, data }); toast('success', 'Supplier updated'); }
      else { await createSupplier.mutateAsync({ ...data, balance: 0, totalOrders: 0 }); toast('success', 'Supplier created'); }
      setModalOpen(false);
    } catch { toast('error', 'Operation failed'); }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try { await deleteSupplier.mutateAsync(deleteTarget.id); toast('success', 'Supplier deleted'); }
    catch { toast('error', 'Delete failed'); }
    finally { setDeleteTarget(null); }
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'name', label: 'Supplier', render: (_, row) => <span className="font-medium text-gray-900 dark:text-gray-100">{String(row.name)}</span> },
    { key: 'contact', label: 'Contact', render: (_, row) => (
      <div className="text-xs text-gray-500 space-y-0.5">
        <div className="flex items-center gap-1"><UserIcon size={12} /> {String(row.contact)}</div>
        <div className="flex items-center gap-1"><Phone size={12} /> {String(row.phone)}</div>
      </div>
    )},
    { key: 'totalOrders', label: 'Total Orders', render: v => <span className="font-medium">{String(v)}</span> },
    { key: 'balance', label: 'Payable Balance', render: v => {
      const bal = Number(v);
      return <span className={bal > 0 ? 'text-red-600 font-semibold' : 'text-gray-500'}>{formatCurrency(bal)}</span>;
    }},
    { key: 'status', label: 'Status', render: v => <Badge variant={v === 'active' ? 'success' : 'default'}>{String(v)}</Badge> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader title="Suppliers" subtitle={`${suppliers.length} active vendors`} actions={<Button onClick={openCreate}><Plus size={16} /> Add Supplier</Button>} />
      <DataTable
        columns={columns} data={suppliers as unknown as Record<string, unknown>[]} loading={isLoading} searchable searchPlaceholder="Search suppliers..."
        actions={row => (
          <div className="flex justify-end gap-1">
            <button onClick={() => openEdit(row as unknown as Supplier)} className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-blue-600"><Edit size={15} /></button>
            <button onClick={() => setDeleteTarget(row as unknown as Supplier)} className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-red-600"><Trash2 size={15} /></button>
          </div>
        )}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Edit Supplier' : 'Add Supplier'}>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <Input label="Company Name" error={errors.name?.message} {...register('name')} />
          <Input label="Contact Person" error={errors.contact?.message} {...register('contact')} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Phone" error={errors.phone?.message} {...register('phone')} />
            <Input label="Email" error={errors.email?.message} {...register('email')} />
          </div>
          <Input label="Address" error={errors.address?.message} {...register('address')} />
          <Select label="Status" options={[{ value: 'active', label: 'Active' }, { value: 'inactive', label: 'Inactive' }]} {...register('status')} />
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={createSupplier.isPending || updateSupplier.isPending}>Save</Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={confirmDelete} title="Delete Supplier" message={`Delete ${deleteTarget?.name}?`} confirmLabel="Delete" loading={deleteSupplier.isPending} />
    </div>
  );
}
