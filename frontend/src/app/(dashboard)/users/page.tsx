'use client';
import React, { useState } from 'react';
import { Plus, Edit, Trash2, Shield } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import ErrorState from '@/components/ui/ErrorState';
import { useUsers, useCreateUser, useUpdateUser, useDeleteUser } from '@/hooks/useUsers';
import { useToast } from '@/components/ui/ToastProvider';
import type { User } from '@/lib/api/mockData';
import { formatDate } from '@/lib/utils';
import { useForm } from 'react-hook-form';

export default function UsersPage() {
  const { data: users = [], isLoading, error, refetch } = useUsers();
  const create = useCreateUser();
  const update = useUpdateUser();
  const remove = useDeleteUser();
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null);

  const { register, handleSubmit, reset } = useForm();

  const openCreate = () => { setEditing(null); reset({ status: 'active', role: 'CASHIER' }); setModalOpen(true); };
  const openEdit = (u: User) => { setEditing(u); reset({ ...u }); setModalOpen(true); };

  const onSubmit = async (data: any) => {
    try {
      if (editing) { await update.mutateAsync({ id: editing.id, data }); toast('success', 'User updated'); }
      else { await create.mutateAsync(data); toast('success', 'User created'); }
      setModalOpen(false);
    } catch { toast('error', 'Operation failed'); }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try { await remove.mutateAsync(deleteTarget.id); toast('success', 'User deleted'); } catch { toast('error', 'Delete failed'); } finally { setDeleteTarget(null); }
  };

  const roleColors: Record<string, string> = {
    ADMIN: 'bg-purple-100 text-purple-700', MANAGER: 'bg-blue-100 text-blue-700',
    STOREKEEPER: 'bg-emerald-100 text-emerald-700', CASHIER: 'bg-amber-100 text-amber-700', ACCOUNTANT: 'bg-cyan-100 text-cyan-700'
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'fullName', label: 'Name', render: (_, row) => (
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center font-bold text-gray-600 dark:text-gray-300">
          {String(row.fullName).charAt(0)}
        </div>
        <div>
          <p className="font-medium text-gray-900 dark:text-gray-100">{String(row.fullName)}</p>
          <p className="text-xs text-gray-500">{String(row.email)}</p>
        </div>
      </div>
    )},
    { key: 'username', label: 'Username' },
    { key: 'role', label: 'Role', render: v => <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${roleColors[String(v)] ?? 'bg-gray-100 text-gray-700'}`}><Shield size={10} className="inline mr-1" />{String(v)}</span> },
    { key: 'lastLogin', label: 'Last Login', render: v => v === '-' ? '-' : formatDate(String(v)) },
    { key: 'status', label: 'Status', render: v => <Badge variant={v === 'active' ? 'success' : 'default'}>{String(v)}</Badge> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader title="Users & Roles" actions={<Button onClick={openCreate}><Plus size={16} /> Add User</Button>} />
      <DataTable columns={columns} data={users as unknown as Record<string, unknown>[]} loading={isLoading} searchable
        actions={row => (
          <div className="flex justify-end gap-1">
            <button onClick={() => openEdit(row as unknown as User)} className="p-1.5 rounded hover:bg-gray-100 text-blue-500"><Edit size={15} /></button>
            <button onClick={() => setDeleteTarget(row as unknown as User)} className="p-1.5 rounded hover:bg-gray-100 text-red-500" disabled={String(row.role) === 'ADMIN'}><Trash2 size={15} /></button>
          </div>
        )}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Edit User' : 'Add User'}>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <Input label="Full Name" {...register('fullName', { required: true })} />
          <Input label="Username" {...register('username', { required: true })} disabled={!!editing} />
          <Input label="Email" type="email" {...register('email', { required: true })} />
          <Select label="Role" options={['ADMIN', 'MANAGER', 'STOREKEEPER', 'CASHIER', 'ACCOUNTANT'].map(r => ({ value: r, label: r }))} {...register('role')} />
          <Select label="Status" options={[{ value: 'active', label: 'Active' }, { value: 'inactive', label: 'Inactive' }]} {...register('status')} />
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={create.isPending || update.isPending}>Save</Button>
          </div>
        </form>
      </Modal>
      <ConfirmDialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={confirmDelete} title="Delete User" loading={remove.isPending} />
    </div>
  );
}
