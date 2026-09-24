'use client';
import React, { useMemo, useState } from 'react';
import { DollarSign, Clock, Building2 } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency } from '@/lib/utils';
import { useSuppliers } from '@/hooks/useSuppliers';
import { usePurchases } from '@/hooks/usePurchases';
import { useCreatePayment } from '@/hooks/useFinance';
import { useToast } from '@/components/ui/ToastProvider';
import type { Supplier } from '@/lib/api/suppliersApi';

function supplierName(s: Supplier) {
  return s.companyName || (s as any).name || '';
}

function balanceOf(s: Supplier) {
  return Number(s.outstandingBalance ?? (s as any).balance ?? 0);
}

function unpaidOfPurchase(p: any): number {
  const status = String(p.status || '').toUpperCase();
  if (status === 'CANCELLED') return 0;
  const paymentStatus = String(p.paymentStatus || '').toUpperCase();
  if (paymentStatus === 'PAID') return 0;
  const total = Number(p.totalAmount ?? p.total ?? 0);
  const paid = Number(p.paid ?? 0);
  return Math.max(0, total - paid);
}

export default function PayablesPage() {
  const { data: suppliers = [], isLoading, error, refetch } = useSuppliers();
  const { data: purchases = [], error: purchasesError, refetch: refetchPurchases } = usePurchases();
  const createPayment = useCreatePayment();
  const { toast } = useToast();

  const [paymentModalOpen, setPaymentModalOpen] = useState(false);
  const [selectedSupplier, setSelectedSupplier] = useState<Supplier | null>(null);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<'cash' | 'bank' | 'mobile'>('bank');
  const [ref, setRef] = useState('');

  const creditors = suppliers.filter(s => balanceOf(s) > 0);
  const totalPayables = creditors.reduce((sum, s) => sum + balanceOf(s), 0);

  const unpaidPurchaseTotal = useMemo(
    () => purchases.reduce((sum, p) => sum + unpaidOfPurchase(p), 0),
    [purchases]
  );
  const unpaidPurchaseCount = useMemo(
    () => purchases.filter(p => unpaidOfPurchase(p) > 0).length,
    [purchases]
  );

  const openPaySupplier = (s: Supplier) => {
    setSelectedSupplier(s);
    setAmount(String(balanceOf(s)));
    setRef(`PAY-${Date.now().toString().slice(-8)}`);
    setPaymentModalOpen(true);
  };

  const handlePay = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedSupplier) return;
    try {
      await createPayment.mutateAsync({
        reference: ref,
        type: 'made',
        amount: parseFloat(amount) || 0,
        date: new Date().toISOString().slice(0, 10),
        method,
        party: supplierName(selectedSupplier),
        partyType: 'supplier',
        note: `Disbursement towards supplier payable balance`
      });
      toast('success', 'Payment recorded', `${formatCurrency(parseFloat(amount))} paid to ${supplierName(selectedSupplier)}`);
      setPaymentModalOpen(false);
      refetch();
      refetchPurchases();
    } catch {
      toast('error', 'Error', 'Failed to record disbursement');
    }
  };

  const columns: Column<any>[] = [
    {
      key: 'companyName',
      label: 'Supplier / Vendor',
      render: (_, row) => (
        <div>
          <span className="font-semibold text-gray-900 dark:text-gray-100">{supplierName(row as Supplier)}</span>
          <span className="block text-[11px] text-gray-400">
            Attn: {String((row as Supplier).contactPerson || (row as any).contact || '—')}
          </span>
        </div>
      )
    },
    { key: 'phone', label: 'Contact Phone', render: v => <span className="text-xs text-gray-500">{String(v || '—')}</span> },
    {
      key: 'outstandingBalance',
      label: 'Amount Owed (Payable)',
      render: (_, row) => (
        <span className="font-bold text-red-600 dark:text-red-400">
          {formatCurrency(balanceOf(row as Supplier))}
        </span>
      )
    },
    {
      key: 'status',
      label: 'Account Status',
      render: v => {
        const val = String(v || '').toUpperCase();
        return <Badge variant={val === 'ACTIVE' ? 'success' : 'default'}>{val || '—'}</Badge>;
      }
    },
  ];

  if (error || purchasesError) return <ErrorState retry={() => { refetch(); refetchPurchases(); }} />;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payables"
        subtitle="Manage outstanding debts to product suppliers and schedule payment settlements"
      />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          title="Total Payables Due"
          value={formatCurrency(totalPayables)}
          changeLabel="from supplier balances"
          color="red"
          icon={<DollarSign size={20} />}
        />
        <StatCard
          title="Vendors With Balances"
          value={`${creditors.length} Vendors`}
          color="amber"
          icon={<Building2 size={20} />}
        />
        <StatCard
          title="Unpaid Purchase Orders"
          value={formatCurrency(unpaidPurchaseTotal)}
          changeLabel={`${unpaidPurchaseCount} open PO(s)`}
          color="blue"
          icon={<Clock size={20} />}
        />
      </div>

      <DataTable
        columns={columns}
        data={creditors as unknown as Record<string, unknown>[]}
        loading={isLoading}
        searchable
        searchPlaceholder="Search creditors..."
        actions={row => (
          <Button
            size="sm"
            onClick={() => openPaySupplier(row as unknown as Supplier)}
          >
            Settle Bill
          </Button>
        )}
      />

      <Modal open={paymentModalOpen} onClose={() => setPaymentModalOpen(false)} title={`Disburse Payment — ${selectedSupplier ? supplierName(selectedSupplier) : ''}`}>
        <form onSubmit={handlePay} className="space-y-4">
          <div className="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg text-xs space-y-1">
            <div className="flex justify-between">
              <span className="text-gray-400">Total Payable Balance:</span>
              <strong className="text-red-600">{formatCurrency(selectedSupplier ? balanceOf(selectedSupplier) : 0)}</strong>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-400">Supplier Address:</span>
              <span>{selectedSupplier?.address || '—'}</span>
            </div>
          </div>

          <Input
            label="Payment Voucher Reference"
            value={ref}
            onChange={e => setRef(e.target.value)}
            required
          />
          <Input
            label="Disbursement Amount (ETB)"
            type="number"
            min={1}
            value={amount}
            onChange={e => setAmount(e.target.value)}
            required
          />
          <Select
            label="Payment Account"
            options={[
              { value: 'bank', label: 'Company Bank Account (Cheque / RTGS / Transfer)' },
              { value: 'mobile', label: 'Commercial Mobile Pay' },
              { value: 'cash', label: 'Petty Cash' },
            ]}
            value={method}
            onChange={e => setMethod(e.target.value as any)}
          />
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setPaymentModalOpen(false)}>Cancel</Button>
            <Button type="submit">Submit Disbursement</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
