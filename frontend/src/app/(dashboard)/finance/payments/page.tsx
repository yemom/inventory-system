'use client';
import React, { useState } from 'react';
import { Plus } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency, formatDate } from '@/lib/utils';
import { usePayments, useCreatePayment } from '@/hooks/useFinance';
import { useToast } from '@/components/ui/ToastProvider';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const schema = z.object({
  type: z.enum(['received', 'made']),
  partyType: z.enum(['customer', 'supplier']),
  party: z.string().min(1, 'Required'),
  amount: z.coerce.number().min(1, 'Amount > 0'),
  method: z.enum(['cash', 'bank', 'mobile']),
  reference: z.string().min(1, 'Required'),
  note: z.string().optional(),
});
type FormData = z.infer<typeof schema>;

export default function PaymentsPage() {
  const { data: payments = [], isLoading, error, refetch } = usePayments();
  const createPayment = useCreatePayment();
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);

  const { register, handleSubmit, reset, watch, formState: { errors } } = useForm<FormData>({ resolver: zodResolver(schema), defaultValues: { type: 'received', partyType: 'customer', method: 'bank' } });
  const type = watch('type');

  const onSubmit = async (data: FormData) => {
    try {
      await createPayment.mutateAsync({ ...data, date: new Date().toISOString().slice(0, 10) });
      toast('success', 'Payment recorded');
      setModalOpen(false); reset();
    } catch { toast('error', 'Failed to record payment'); }
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'date', label: 'Date', render: v => formatDate(String(v)) },
    { key: 'reference', label: 'Reference', render: v => <span className="font-mono text-xs">{String(v)}</span> },
    { key: 'type', label: 'Type', render: v => <Badge variant={v === 'received' ? 'success' : 'danger'}>{String(v) === 'received' ? 'Received (In)' : 'Made (Out)'}</Badge> },
    { key: 'party', label: 'Party', render: (_, row) => (
      <div><p className="font-medium">{String(row.party)}</p><p className="text-xs text-gray-500 capitalize">{String(row.partyType)}</p></div>
    )},
    { key: 'method', label: 'Method', render: v => <span className="capitalize">{String(v)}</span> },
    { key: 'amount', label: 'Amount', render: (v, row) => <span className={row.type === 'received' ? 'text-emerald-600 font-bold' : 'text-red-600 font-bold'}>{formatCurrency(Number(v))}</span> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader title="Payments" actions={<Button onClick={() => setModalOpen(true)}><Plus size={16} /> Record Payment</Button>} />
      <DataTable columns={columns} data={payments as unknown as Record<string, unknown>[]} loading={isLoading} searchable />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Record Payment">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Select label="Payment Type" options={[{ value: 'received', label: 'Money Received (In)' }, { value: 'made', label: 'Money Paid (Out)' }]} {...register('type')} />
            <Select label="Party Type" options={[{ value: 'customer', label: 'Customer' }, { value: 'supplier', label: 'Supplier' }]} {...register('partyType')} />
          </div>
          <Input label={type === 'received' ? 'From (Customer/Entity)' : 'To (Supplier/Entity)'} error={errors.party?.message} {...register('party')} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Amount (ETB)" type="number" error={errors.amount?.message} {...register('amount')} />
            <Select label="Method" options={[{ value: 'cash', label: 'Cash' }, { value: 'bank', label: 'Bank Transfer' }, { value: 'mobile', label: 'Mobile Money' }]} {...register('method')} />
          </div>
          <Input label="Reference / Receipt No" error={errors.reference?.message} {...register('reference')} />
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={createPayment.isPending}>Save</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
