'use client';
import React, { useState } from 'react';
import { 
  DollarSign, ArrowUpRight, ArrowDownRight, CreditCard, 
  Banknote, Smartphone, Download, PieChart, Wallet 
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import { formatCurrency, formatDate } from '@/lib/utils';
import { usePayments, useExpenses } from '@/hooks/useFinance';

export default function FinancialReportPage() {
  const { data: payments = [] } = usePayments();
  const { data: expenses = [] } = useExpenses();

  const totalInflow = payments.filter(p => p.type === 'received').reduce((sum, p) => sum + p.amount, 0);
  const supplierDisbursements = payments.filter(p => p.type === 'made').reduce((sum, p) => sum + p.amount, 0);
  const totalExpenses = expenses.reduce((sum, e) => sum + e.amount, 0);
  const totalOutflow = supplierDisbursements + totalExpenses;
  const netCashFlow = totalInflow - totalOutflow;

  // Breakdown by payment channel
  const cashTotal = payments.filter(p => p.method === 'cash').reduce((sum, p) => sum + p.amount, 0);
  const bankTotal = payments.filter(p => p.method === 'bank').reduce((sum, p) => sum + p.amount, 0);
  const mobileTotal = payments.filter(p => p.method === 'mobile').reduce((sum, p) => sum + p.amount, 0);

  return (
    <div className="space-y-6">
      <PageHeader 
        title="Cash Flow & Financial Summary" 
        subtitle="Consolidated cash collections, supplier payouts, and operating disbursements"
        actions={
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Download size={14} /> Export Financial Summary
          </Button>
        }
      />

      {/* Primary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard 
          title="Total Inflow (Collections)" 
          value={formatCurrency(totalInflow)} 
          color="emerald" 
          icon={<ArrowUpRight size={20} />} 
        />
        <StatCard 
          title="Total Outflow (Disbursements + Opex)" 
          value={formatCurrency(totalOutflow)} 
          color="red" 
          icon={<ArrowDownRight size={20} />} 
        />
        <StatCard 
          title="Net Cash Position" 
          value={formatCurrency(netCashFlow)} 
          color={netCashFlow >= 0 ? 'blue' : 'amber'} 
          icon={<Wallet size={20} />} 
        />
      </div>

      {/* Payment Channels Breakdown */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 flex items-center justify-center">
            <Banknote size={24} />
          </div>
          <div>
            <span className="text-xs text-gray-400 block font-medium">Cash in Register</span>
            <span className="text-lg font-bold text-gray-900 dark:text-gray-100">{formatCurrency(cashTotal)}</span>
          </div>
        </div>

        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-blue-50 dark:bg-blue-900/20 text-blue-600 flex items-center justify-center">
            <CreditCard size={24} />
          </div>
          <div>
            <span className="text-xs text-gray-400 block font-medium">Bank Accounts</span>
            <span className="text-lg font-bold text-gray-900 dark:text-gray-100">{formatCurrency(bankTotal)}</span>
          </div>
        </div>

        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-purple-50 dark:bg-purple-900/20 text-purple-600 flex items-center justify-center">
            <Smartphone size={24} />
          </div>
          <div>
            <span className="text-xs text-gray-400 block font-medium">Telebirr / Mobile Money</span>
            <span className="text-lg font-bold text-gray-900 dark:text-gray-100">{formatCurrency(mobileTotal)}</span>
          </div>
        </div>
      </div>

      {/* Recent Ledger Entries */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
          <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">Financial Ledger Entries</h3>
          <span className="text-xs text-gray-400">{payments.length} transactions recorded</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-xs text-left">
            <thead className="bg-gray-50 dark:bg-gray-700/60 text-gray-500 uppercase font-semibold">
              <tr>
                <th className="p-3">Reference</th>
                <th className="p-3">Date</th>
                <th className="p-3">Entity / Party</th>
                <th className="p-3">Channel</th>
                <th className="p-3 text-right">Amount (ETB)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {payments.map((p: any) => (
                <tr key={p.id} className="hover:bg-gray-50/50 dark:hover:bg-gray-700/30">
                  <td className="p-3 font-mono font-semibold">{p.reference}</td>
                  <td className="p-3 text-gray-500">{formatDate(p.date || '')}</td>
                  <td className="p-3 font-medium text-gray-900 dark:text-gray-100">{p.party} ({p.partyType})</td>
                  <td className="p-3 capitalize">{p.method}</td>
                  <td className={`p-3 text-right font-bold ${p.type === 'received' ? 'text-emerald-600' : 'text-red-600'}`}>
                    {p.type === 'received' ? '+' : '-'}{formatCurrency(p.amount || 0)}
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
