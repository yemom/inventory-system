'use client';
import React, { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useExpenses, useCreateExpense, useDeleteExpense } from '@/hooks/useFinance';
import { useToast } from '@/components/ui/ToastProvider';
import { useForm } from 'react-hook-form';
import type { Expense } from '@/lib/api/mockData';
import { useAuth } from '@/lib/auth/AuthProvider';

const CATEGORIES = ['Rent', 'Utilities', 'Salaries', 'Transport', 'Maintenance', 'Office Supplies', 'Marketing', 'Insurance', 'Other'];

export default function ExpensesPage() {
  const { data: expenses = [], isLoading, error, refetch } = useExpenses();
  const create = useCreateExpense();
  const remove = useDeleteExpense();
  const { toast } = useToast();
  const { user } = useAuth();
  const [modalOpen, setModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Expense | null>(null);

  const { register, handleSubmit, reset } = useForm();

  const onSubmit = async (data: any) => {
    try {
      await create.mutateAsync({ ...data, amount: Number(data.amount), date: new Date().toISOString().slice(0, 10), paidBy: user?.username || 'system' });
      toast('success', 'Expense recorded');
      setModalOpen(false); reset();
    } catch { toast('error', 'Failed to record expense'); }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try { await remove.mutateAsync(deleteTarget.id); toast('success', 'Deleted'); } catch { toast('error', 'Delete failed'); } finally { setDeleteTarget(null); }
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'date', label: 'Date', render: v => formatDate(String(v)) },
    { key: 'category', label: 'Category', render: v => <span className="font-medium text-gray-900 dark:text-gray-100">{String(v)}</span> },
    { key: 'description', label: 'Description' },
    { key: 'paidBy', label: 'Paid By' },
    { key: 'amount', label: 'Amount', render: v => <span className="text-red-600 font-semibold">{formatCurrency(Number(v))}</span> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader title="Expenses" actions={<Button onClick={() => setModalOpen(true)}><Plus size={16} /> Record Expense</Button>} />
      <DataTable columns={columns} data={expenses as unknown as Record<string, unknown>[]} loading={isLoading} searchable
        actions={row => (
          <button onClick={() => setDeleteTarget(row as unknown as Expense)} className="p-1.5 rounded hover:bg-gray-100 text-red-500"><Trash2 size={15} /></button>
        )}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Record Expense">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <Select label="Category" options={CATEGORIES.map(c => ({ value: c, label: c }))} {...register('category', { required: true })} />
          <Input label="Description" {...register('description', { required: true })} />
          <Input label="Amount (ETB)" type="number" {...register('amount', { required: true, min: 1 })} />
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={create.isPending}>Save</Button>
          </div>
        </form>
      </Modal>
      <ConfirmDialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={confirmDelete} title="Delete Expense" loading={remove.isPending} />
    </div>
  );
}
