'use client';
import React, { useMemo, useState } from 'react';
import {
  TrendingUp, TrendingDown, DollarSign, PieChart, Download
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import Select from '@/components/ui/Select';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency } from '@/lib/utils';
import { useSales } from '@/hooks/useSales';
import { useProducts } from '@/hooks/useProducts';
import { useExpenses } from '@/hooks/useFinance';

function monthLabel(d: Date) {
  return d.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
}

function inPeriod(dateStr: string | undefined, period: string): boolean {
  if (!dateStr) return false;
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return false;
  const now = new Date();
  if (period === 'ytd') {
    return d.getFullYear() === now.getFullYear();
  }
  if (period === 'last_month') {
    const last = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    return d.getFullYear() === last.getFullYear() && d.getMonth() === last.getMonth();
  }
  // this_month
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth();
}

function isCancelled(s: any) {
  return String(s.status || '').toUpperCase() === 'CANCELLED';
}

function saleTotal(s: any) {
  return Number(s.finalAmount ?? s.total ?? s.totalAmount ?? 0);
}

export default function ProfitLossPage() {
  const { data: sales = [], isLoading: loadingSales, error: salesError, refetch: refetchSales } = useSales();
  const { data: products = [], error: productsError, refetch: refetchProducts } = useProducts();
  const { data: expenses = [], error: expensesError, refetch: refetchExpenses } = useExpenses();
  const [period, setPeriod] = useState('this_month');

  const now = new Date();
  const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const periodOptions = [
    { value: 'this_month', label: `This Month (${monthLabel(now)})` },
    { value: 'last_month', label: `Last Month (${monthLabel(lastMonth)})` },
    { value: 'ytd', label: `Year to Date (${now.getFullYear()})` },
  ];

  const costByProductId = useMemo(() => {
    const map = new Map<number, number>();
    for (const p of products) {
      map.set(Number(p.id), Number(p.purchasePrice ?? 0));
    }
    return map;
  }, [products]);

  const filteredSales = useMemo(
    () => sales.filter(s => !isCancelled(s) && inPeriod(s.createdAt || s.date, period)),
    [sales, period]
  );
  const filteredExpenses = useMemo(
    () => expenses.filter(e => inPeriod(e.date || e.createdAt, period)),
    [expenses, period]
  );

  const totalRevenue = filteredSales.reduce((acc, s) => acc + saleTotal(s), 0);

  // Real COGS: purchasePrice × quantity sold (fallback 0 if product unknown)
  const totalCogs = filteredSales.reduce((acc: number, s: any) => {
    const items: any[] = s.items || [];
    const orderCost = items.reduce((itemSum: number, item: any) => {
      const unitCost = costByProductId.get(Number(item.productId)) ?? 0;
      return itemSum + unitCost * Number(item.quantity ?? 0);
    }, 0);
    return acc + orderCost;
  }, 0);

  const grossProfit = totalRevenue - totalCogs;
  const grossMargin = totalRevenue > 0 ? Math.round((grossProfit / totalRevenue) * 100) : 0;

  const totalOperatingExpenses = filteredExpenses.reduce((acc, e) => acc + Number(e.amount || 0), 0);
  const netProfit = grossProfit - totalOperatingExpenses;
  const netMargin = totalRevenue > 0 ? Math.round((netProfit / totalRevenue) * 100) : 0;

  const expenseByCategory = filteredExpenses.reduce<Record<string, number>>((acc, exp) => {
    const cat = exp.category || 'Other';
    acc[cat] = (acc[cat] || 0) + Number(exp.amount || 0);
    return acc;
  }, {});

  if (salesError || productsError || expensesError) {
    return <ErrorState retry={() => { refetchSales(); refetchProducts(); refetchExpenses(); }} />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Profit Loss"
        subtitle="Dynamic financial statement computed from sales, product purchase cost, and operating expenses"
        actions={
          <div className="flex items-center gap-2">
            <Select
              options={periodOptions}
              value={period}
              onChange={e => setPeriod(e.target.value)}
              className="w-56 text-xs"
            />
            <Button variant="outline" size="sm" onClick={() => window.print()}>
              <Download size={14} /> Export Statement
            </Button>
          </div>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Gross Sales Revenue"
          value={loadingSales ? '…' : formatCurrency(totalRevenue)}
          changeLabel={periodOptions.find(o => o.value === period)?.label}
          color="blue"
          icon={<DollarSign size={20} />}
        />
        <StatCard
          title="Cost of Goods Sold (COGS)"
          value={formatCurrency(totalCogs)}
          changeLabel="purchasePrice × qty sold"
          color="amber"
          icon={<TrendingDown size={20} />}
        />
        <StatCard
          title="Gross Profit"
          value={formatCurrency(grossProfit)}
          change={grossMargin}
          changeLabel="gross margin"
          color="emerald"
          icon={<TrendingUp size={20} />}
        />
        <StatCard
          title="Net Profit (After Expenses)"
          value={formatCurrency(netProfit)}
          change={netMargin}
          changeLabel="net margin"
          color={netProfit >= 0 ? 'emerald' : 'red'}
          icon={<PieChart size={20} />}
        />
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 flex justify-between items-center">
          <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">
            Statement of Operations (Income & Expenses)
          </h3>
          <span className="text-xs font-mono text-gray-500">Currency: ETB</span>
        </div>

        <div className="divide-y divide-gray-100 dark:divide-gray-700/60 text-sm">
          <div className="p-4 bg-blue-50/20 dark:bg-blue-900/10">
            <div className="flex justify-between font-bold text-gray-900 dark:text-gray-100">
              <span>1. OPERATING REVENUE</span>
              <span>{formatCurrency(totalRevenue)}</span>
            </div>
            <div className="mt-2 space-y-1 text-xs text-gray-500 pl-4">
              <div className="flex justify-between">
                <span>Product Gross Sales (non-cancelled orders)</span>
                <span>{formatCurrency(totalRevenue)}</span>
              </div>
            </div>
          </div>

          <div className="p-4">
            <div className="flex justify-between font-bold text-gray-900 dark:text-gray-100">
              <span>2. COST OF GOODS SOLD (COGS)</span>
              <span className="text-amber-600">-{formatCurrency(totalCogs)}</span>
            </div>
            <div className="mt-2 space-y-1 text-xs text-gray-500 pl-4">
              <div className="flex justify-between">
                <span>Direct inventory cost (product purchase price × qty sold)</span>
                <span>-{formatCurrency(totalCogs)}</span>
              </div>
            </div>
          </div>

          <div className="p-4 bg-emerald-50/30 dark:bg-emerald-900/20 flex justify-between items-center font-bold text-emerald-700 dark:text-emerald-400">
            <span>GROSS PROFIT (Revenue − COGS)</span>
            <div className="text-right">
              <span className="text-base">{formatCurrency(grossProfit)}</span>
              <span className="block text-xs font-normal">Margin: {grossMargin}%</span>
            </div>
          </div>

          <div className="p-4">
            <div className="flex justify-between font-bold text-gray-900 dark:text-gray-100">
              <span>3. OPERATING EXPENSES (OPEX)</span>
              <span className="text-red-600">-{formatCurrency(totalOperatingExpenses)}</span>
            </div>
            <div className="mt-2 space-y-1.5 text-xs text-gray-600 dark:text-gray-400 pl-4">
              {Object.keys(expenseByCategory).length === 0 ? (
                <p className="text-gray-400">No operating expenses in this period.</p>
              ) : (
                Object.entries(expenseByCategory).map(([cat, amt]) => (
                  <div key={cat} className="flex justify-between">
                    <span>{cat}</span>
                    <span className="font-mono text-gray-700 dark:text-gray-300">-{formatCurrency(amt)}</span>
                  </div>
                ))
              )}
            </div>
          </div>

          <div className="p-5 bg-gray-900 text-white flex justify-between items-center rounded-b-xl">
            <div>
              <span className="text-base font-extrabold tracking-tight">NET PROFIT / (NET LOSS)</span>
              <span className="block text-xs text-gray-400">Net profitability after all direct & operating costs</span>
            </div>
            <div className="text-right">
              <span className={`text-2xl font-extrabold ${netProfit >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                {formatCurrency(netProfit)}
              </span>
              <span className="block text-xs text-gray-300">Net Profit Margin: {netMargin}%</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
