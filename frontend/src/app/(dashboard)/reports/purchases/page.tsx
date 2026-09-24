'use client';
import React from 'react';
import { Truck, CheckCircle2, Clock, Download } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency } from '@/lib/utils';
import { usePurchases } from '@/hooks/usePurchases';
import { useSuppliers } from '@/hooks/useSuppliers';

function purchaseTotal(p: any) {
  return Number(p.totalAmount ?? p.total ?? 0);
}

function purchasePaid(p: any) {
  return Number(p.paid ?? 0);
}

function isCancelled(p: any) {
  return String(p.status || '').toUpperCase() === 'CANCELLED';
}

function supplierName(s: any) {
  return s.companyName || s.name || '—';
}

function supplierContact(s: any) {
  return s.contactPerson || s.contact || '—';
}

function balanceOf(s: any) {
  return Number(s.outstandingBalance ?? s.balance ?? 0);
}

export default function PurchasesReportPage() {
  const { data: purchases = [], isLoading: loadingPurchases, error: purchasesError, refetch: refetchPurchases } = usePurchases();
  const { data: suppliers = [], isLoading: loadingSuppliers, error: suppliersError, refetch: refetchSuppliers } = useSuppliers();

  const active = purchases.filter(p => !isCancelled(p));
  const totalSpent = active.reduce((acc, p) => acc + purchaseTotal(p), 0);
  const totalPaid = active.reduce((acc, p) => acc + purchasePaid(p), 0);
  const outstandingPayables = totalSpent - totalPaid;

  const receivedOrders = purchases.filter(p => {
    const st = String(p.status || '').toUpperCase();
    return st === 'RECEIVED' || st === 'received';
  }).length;
  const pendingOrders = purchases.filter(p => {
    const st = String(p.status || '').toUpperCase();
    return st === 'PENDING' || st === 'ORDERED' || st === 'DRAFT' || st === 'ordered' || st === 'draft';
  }).length;

  if (purchasesError || suppliersError) {
    return <ErrorState retry={() => { refetchPurchases(); refetchSuppliers(); }} />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Purchases"
        subtitle="Historical procurement volume, vendor delivery status, and payable commitments"
        actions={
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Download size={14} /> Export Procurement Report
          </Button>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          title="Total Procurement Volume"
          value={loadingPurchases ? '…' : formatCurrency(totalSpent)}
          changeLabel={outstandingPayables > 0 ? `${formatCurrency(outstandingPayables)} unpaid` : undefined}
          color="blue"
          icon={<Truck size={20} />}
        />
        <StatCard
          title="Stock Received"
          value={`${receivedOrders} Orders`}
          color="emerald"
          icon={<CheckCircle2 size={20} />}
        />
        <StatCard
          title="Pending Deliveries"
          value={`${pendingOrders} Orders`}
          color="amber"
          icon={<Clock size={20} />}
        />
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
          <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">Supplier Order Volumes & Balances</h3>
          <span className="text-xs text-gray-400">{suppliers.length} suppliers</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-xs text-left">
            <thead className="bg-gray-50 dark:bg-gray-700/60 text-gray-500 uppercase font-semibold">
              <tr>
                <th className="p-3">Supplier Name</th>
                <th className="p-3">Contact Person</th>
                <th className="p-3 text-center">Lifetime Orders</th>
                <th className="p-3 text-right">Current Payable Balance</th>
                <th className="p-3 text-center">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {loadingSuppliers ? (
                <tr>
                  <td colSpan={5} className="p-8 text-center text-gray-400">Loading suppliers…</td>
                </tr>
              ) : suppliers.length === 0 ? (
                <tr>
                  <td colSpan={5} className="p-8 text-center text-gray-400">No suppliers yet.</td>
                </tr>
              ) : (
                suppliers.map((s: any) => {
                  const orderCount = purchases.filter(
                    p => !isCancelled(p) && (p.supplierName === supplierName(s) || p.supplierId === s.id)
                  ).length;
                  const status = String(s.status || '').toUpperCase() || '—';
                  return (
                    <tr key={s.id} className="hover:bg-gray-50/50 dark:hover:bg-gray-700/30">
                      <td className="p-3 font-semibold text-gray-900 dark:text-gray-100">{supplierName(s)}</td>
                      <td className="p-3 text-gray-500">{supplierContact(s)} ({s.phone || '—'})</td>
                      <td className="p-3 text-center font-mono">{orderCount}</td>
                      <td className="p-3 text-right font-bold text-red-600 dark:text-red-400">
                        {formatCurrency(balanceOf(s))}
                      </td>
                      <td className="p-3 text-center">
                        <Badge variant={status === 'ACTIVE' ? 'success' : 'default'}>{status}</Badge>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
