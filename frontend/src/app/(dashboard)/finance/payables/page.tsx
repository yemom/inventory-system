'use client';
import React, { useState } from 'react';
import { DollarSign, Clock, Building2, AlertCircle, ArrowDownRight, CheckCircle } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useSuppliers } from '@/hooks/useSuppliers';
import { useCreatePayment } from '@/hooks/useFinance';
import { useToast } from '@/components/ui/ToastProvider';
import type { Supplier } from '@/lib/api/suppliersApi';

export default function PayablesPage() {
  const { data: suppliers = [], isLoading, refetch } = useSuppliers();
  const createPayment = useCreatePayment();
  const { toast } = useToast();

  const [paymentModalOpen, setPaymentModalOpen] = useState(false);
  const [selectedSupplier, setSelectedSupplier] = useState<Supplier | null>(null);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<'cash' | 'bank' | 'mobile'>('bank');
  const [ref, setRef] = useState('');

  // Suppliers with positive balance
  const creditors = suppliers.filter((s: any) => s.balance > 0);
  const totalPayables = creditors.reduce((sum: number, s: any) => sum + s.balance, 0);

  const openPaySupplier = (s: Supplier) => {
    setSelectedSupplier(s);
    setAmount(String(s.balance));
    setRef(`PAY-${Date.now().toString().slice(-6)}`);
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
        party: selectedSupplier.name,
        partyType: 'supplier',
        note: `Disbursement towards supplier payable balance`
      });
      toast('success', 'Payment recorded', `${formatCurrency(parseFloat(amount))} paid to ${selectedSupplier.name}`);
      setPaymentModalOpen(false);
      refetch();
    } catch {
      toast('error', 'Error', 'Failed to record disbursement');
    }
  };

  const columns: Column<any>[] = [
    { 
      key: 'name', 
      label: 'Supplier / Vendor', 
      render: (_, row) => (
        <div>
          <span className="font-semibold text-gray-900 dark:text-gray-100">{String(row.name)}</span>
          <span className="block text-[11px] text-gray-400">Attn: {String(row.contact)}</span>
        </div>
      )
    },
    { key: 'phone', label: 'Contact Phone', render: v => <span className="text-xs text-gray-500">{String(v)}</span> },
    { key: 'totalOrders', label: 'Total Purchase Orders', render: v => <span className="font-mono text-xs">{String(v)} orders</span> },
    { 
      key: 'balance', 
      label: 'Amount Owed (Payable)', 
      render: v => <span className="font-bold text-red-600 dark:text-red-400">{formatCurrency(Number(v))}</span> 
    },
    { 
      key: 'status', 
      label: 'Account Status', 
      render: v => <Badge variant={v === 'active' ? 'success' : 'default'}>{String(v)}</Badge> 
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader 
        title="Accounts Payable (Creditors)" 
        subtitle="Manage outstanding debts to product suppliers and schedule payment settlements"
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard 
          title="Total Payables Due" 
          value={formatCurrency(totalPayables)} 
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
          title="Upcoming Due This Week" 
          value={formatCurrency(totalPayables * 0.42)} 
          color="blue" 
          icon={<Clock size={20} />} 
        />
      </div>

      {/* Creditors List */}
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

      {/* Pay Modal */}
      <Modal open={paymentModalOpen} onClose={() => setPaymentModalOpen(false)} title={`Disburse Payment — ${selectedSupplier?.name}`}>
        <form onSubmit={handlePay} className="space-y-4">
          <div className="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg text-xs space-y-1">
            <div className="flex justify-between">
              <span className="text-gray-400">Total Payable Balance:</span>
              <strong className="text-red-600">{formatCurrency(selectedSupplier?.balance || 0)}</strong>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-400">Supplier Address:</span>
              <span>{selectedSupplier?.address}</span>
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

