'use client';
import React, { useState } from 'react';
import { Truck, CheckCircle2, Clock, AlertTriangle, Download, Building2 } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import { formatCurrency, formatDate } from '@/lib/utils';
import { usePurchases } from '@/hooks/usePurchases';
import { useSuppliers } from '@/hooks/useSuppliers';

export default function PurchasesReportPage() {
  const { data: purchases = [] } = usePurchases();
  const { data: suppliers = [] } = useSuppliers();

  const totalSpent = purchases.reduce((acc, p) => acc + (p.status !== 'cancelled' ? p.total : 0), 0);
  const totalPaid = purchases.reduce((acc, p) => acc + (p.status !== 'cancelled' ? p.paid : 0), 0);
  const outstandingPayables = totalSpent - totalPaid;

  const receivedOrders = purchases.filter(p => p.status === 'received').length;
  const pendingOrders = purchases.filter(p => p.status === 'ordered' || p.status === 'draft').length;

  return (
    <div className="space-y-6">
      <PageHeader 
        title="Procurement & Supplier Reports" 
        subtitle="Historical procurement volume, vendor delivery status, and payable commitments"
        actions={
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Download size={14} /> Export Procurement Report
          </Button>
        }
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard 
          title="Total Procurement Volume" 
          value={formatCurrency(totalSpent)} 
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

      {/* Purchases by Vendor Table */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
          <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">Supplier Order Volumes & Balances</h3>
          <span className="text-xs text-gray-400">{suppliers.length} active suppliers</span>
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
              {suppliers.map((s: any) => (
                <tr key={s.id} className="hover:bg-gray-50/50 dark:hover:bg-gray-700/30">
                  <td className="p-3 font-semibold text-gray-900 dark:text-gray-100">{s.name}</td>
                  <td className="p-3 text-gray-500">{s.contact} ({s.phone})</td>
                  <td className="p-3 text-center font-mono">{s.totalOrders}</td>
                  <td className="p-3 text-right font-bold text-red-600 dark:text-red-400">
                    {formatCurrency(s.balance)}
                  </td>
                  <td className="p-3 text-center">
                    <Badge variant={s.status === 'active' ? 'success' : 'default'}>{s.status}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

