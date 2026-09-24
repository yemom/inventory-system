'use client';
import React, { useMemo, useState } from 'react';
import { DollarSign, Clock, AlertTriangle } from 'lucide-react';
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
import { useCustomers } from '@/hooks/useCustomers';
import { useSales } from '@/hooks/useSales';
import { useCreatePayment } from '@/hooks/useFinance';
import { useToast } from '@/components/ui/ToastProvider';
import type { Customer } from '@/lib/api/customersApi';

function unpaidOfSale(sale: any): number {
  const status = String(sale.status || '').toUpperCase();
  if (status === 'CANCELLED') return 0;
  const paymentStatus = String(sale.paymentStatus || '').toUpperCase();
  if (paymentStatus === 'PAID') return 0;
  const total = Number(sale.finalAmount ?? sale.total ?? sale.totalAmount ?? 0);
  const paid = Number(sale.paid ?? 0);
  return Math.max(0, total - paid);
}

function daysSince(sale: any): number {
  const d = new Date(sale.createdAt || sale.date || '');
  if (isNaN(d.getTime())) return 0;
  return Math.floor((Date.now() - d.getTime()) / 86400000);
}

function balanceOf(c: Customer) {
  return Number(c.outstandingBalance ?? (c as any).balance ?? 0);
}

export default function ReceivablesPage() {
  const { data: customers = [], isLoading, error, refetch } = useCustomers();
  const { data: sales = [], error: salesError, refetch: refetchSales } = useSales();
  const createPayment = useCreatePayment();
  const { toast } = useToast();

  const [paymentModalOpen, setPaymentModalOpen] = useState(false);
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<'cash' | 'bank' | 'mobile'>('bank');
  const [ref, setRef] = useState('');

  const debtors = customers.filter(c => balanceOf(c) > 0);
  const totalReceivables = debtors.reduce((sum, c) => sum + balanceOf(c), 0);

  // Aging from unpaid sales (real dates), not a fixed percentage split
  const aging = useMemo(() => {
    let current = 0; // 0–30
    let mid = 0;     // 31–60
    let overdue = 0; // 60+
    for (const sale of sales) {
      const unpaid = unpaidOfSale(sale);
      if (unpaid <= 0) continue;
      const age = daysSince(sale);
      if (age <= 30) current += unpaid;
      else if (age <= 60) mid += unpaid;
      else overdue += unpaid;
    }
    return { current, mid, overdue };
  }, [sales]);

  const openRecordPayment = (c: Customer) => {
    setSelectedCustomer(c);
    setAmount(String(balanceOf(c)));
    setRef(`REC-${Date.now().toString().slice(-8)}`);
    setPaymentModalOpen(true);
  };

  const handleSettle = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCustomer) return;
    try {
      await createPayment.mutateAsync({
        reference: ref,
        type: 'received',
        amount: parseFloat(amount) || 0,
        date: new Date().toISOString().slice(0, 10),
        method,
        party: selectedCustomer.name,
        partyType: 'customer',
        note: `Collection against outstanding balance`
      });
      toast('success', 'Payment received', `${formatCurrency(parseFloat(amount))} recorded from ${selectedCustomer.name}`);
      setPaymentModalOpen(false);
      refetch();
      refetchSales();
    } catch {
      toast('error', 'Error', 'Failed to record payment');
    }
  };

  const columns: Column<any>[] = [
    {
      key: 'name',
      label: 'Customer',
      render: (_, row) => (
        <div>
          <span className="font-semibold text-gray-900 dark:text-gray-100">{String(row.name)}</span>
          <span className="block text-[11px] text-gray-400">{String(row.phone || '')}</span>
        </div>
      )
    },
    { key: 'creditLimit', label: 'Credit Limit', render: v => formatCurrency(Number(v || 0)) },
    {
      key: 'outstandingBalance',
      label: 'Outstanding Balance',
      render: (_, row) => (
        <span className="font-bold text-amber-600 dark:text-amber-400">
          {formatCurrency(balanceOf(row as Customer))}
        </span>
      )
    },
    {
      key: 'utilization',
      label: 'Credit Used %',
      render: (_, row) => {
        const lim = Number(row.creditLimit) || 1;
        const bal = balanceOf(row as Customer);
        const pct = Math.min(100, Math.round((bal / lim) * 100));
        return (
          <div className="flex items-center gap-2">
            <div className="w-16 bg-gray-200 dark:bg-gray-700 h-1.5 rounded-full overflow-hidden">
              <div className={`h-full ${pct > 80 ? 'bg-red-500' : 'bg-blue-600'}`} style={{ width: `${pct}%` }} />
            </div>
            <span className="text-xs font-mono">{pct}%</span>
          </div>
        );
      }
    },
    {
      key: 'status',
      label: 'Status',
      render: (_, row) => {
        const bal = balanceOf(row as Customer);
        const lim = Number(row.creditLimit) || 0;
        return bal > lim && lim > 0 ? (
          <Badge variant="danger">Over Limit</Badge>
        ) : (
          <Badge variant="warning">Pending Due</Badge>
        );
      }
    },
  ];

  if (error || salesError) return <ErrorState retry={() => { refetch(); refetchSales(); }} />;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Receivables"
        subtitle="Track outstanding money owed by clients, payment aging from unpaid sales, and credit risk"
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Receivables"
          value={formatCurrency(totalReceivables)}
          changeLabel="from customer balances"
          color="amber"
          icon={<DollarSign size={20} />}
        />
        <StatCard
          title="Current (0–30 Days)"
          value={formatCurrency(aging.current)}
          changeLabel="unpaid sales"
          color="blue"
          icon={<Clock size={20} />}
        />
        <StatCard
          title="Overdue (31–60 Days)"
          value={formatCurrency(aging.mid)}
          changeLabel="unpaid sales"
          color="purple"
          icon={<Clock size={20} />}
        />
        <StatCard
          title="High Risk (60+ Days)"
          value={formatCurrency(aging.overdue)}
          changeLabel="unpaid sales"
          color="red"
          icon={<AlertTriangle size={20} />}
        />
      </div>

      <DataTable
        columns={columns}
        data={debtors as unknown as Record<string, unknown>[]}
        loading={isLoading}
        searchable
        searchPlaceholder="Search debtors..."
        actions={row => (
          <Button
            size="sm"
            variant="outline"
            onClick={() => openRecordPayment(row as unknown as Customer)}
          >
            Collect Payment
          </Button>
        )}
      />

      <Modal open={paymentModalOpen} onClose={() => setPaymentModalOpen(false)} title={`Collect Payment — ${selectedCustomer?.name}`}>
        <form onSubmit={handleSettle} className="space-y-4">
          <div className="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg text-xs space-y-1">
            <div className="flex justify-between">
              <span className="text-gray-400">Total Outstanding:</span>
              <strong className="text-amber-600">{formatCurrency(selectedCustomer ? balanceOf(selectedCustomer) : 0)}</strong>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-400">Credit Limit:</span>
              <span>{formatCurrency(selectedCustomer?.creditLimit || 0)}</span>
            </div>
          </div>

          <Input
            label="Payment Reference / Receipt No."
            value={ref}
            onChange={e => setRef(e.target.value)}
            required
          />
          <Input
            label="Amount Collected (ETB)"
            type="number"
            min={1}
            value={amount}
            onChange={e => setAmount(e.target.value)}
            required
          />
          <Select
            label="Payment Channel"
            options={[
              { value: 'bank', label: 'Commercial Bank Transfer (CBE / Awash / Dashen)' },
              { value: 'mobile', label: 'Telebirr / CBE Birr' },
              { value: 'cash', label: 'Cash in Register' },
            ]}
            value={method}
            onChange={e => setMethod(e.target.value as any)}
          />
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setPaymentModalOpen(false)}>Cancel</Button>
            <Button type="submit">Record Collection</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
