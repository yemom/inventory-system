'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  TrendingUp, RefreshCw, AlertCircle, Shield, Download, DollarSign,
} from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import PageHeader from '@/components/ui/PageHeader';
import { accountingApi, type ProfitLossReport } from '@/lib/api/accountingApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

function StatCard({ label, value, sub, isProfit }: { label: string; value: string; sub?: string; isProfit?: boolean }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{label}</p>
      <p className={`mt-1 font-mono text-xl font-bold ${isProfit === undefined ? 'text-gray-900 dark:text-gray-100' : isProfit ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
        {value}
      </p>
      {sub && <p className="mt-0.5 text-xs text-gray-400">{sub}</p>}
    </div>
  );
}

export default function ProfitLossPage() {
  const { hasPermission } = useAuth();
  const [report, setReport] = useState<ProfitLossReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [from, setFrom] = useState(new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10));
  const [to, setTo] = useState(new Date().toISOString().slice(0, 10));

  const canView = hasPermission('ACCOUNTING_VIEW') || hasPermission('PROFIT_LOSS_READ') || hasPermission('FINANCIAL_REPORT_VIEW');

  const fetchReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await accountingApi.profitLoss(from, to);
      setReport(data);
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? 'Failed to load profit and loss statement');
    } finally {
      setLoading(false);
    }
  }, [from, to]);

  useEffect(() => {
    if (canView) fetchReport();
  }, [canView, fetchReport]);

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Shield className="mx-auto mb-2 text-amber-500" size={32} />
        <p>You do not have permission to view Profit &amp; Loss statements.</p>
      </div>
    );
  }

  const chartData = report ? [
    { name: 'Net Sales', amount: report.netSales, fill: '#3b82f6' },
    { name: 'COGS', amount: report.costOfGoodsSold, fill: '#f59e0b' },
    { name: 'Gross Profit', amount: report.grossProfit, fill: '#10b981' },
    { name: 'Operating Exp', amount: report.totalOperatingExpenses, fill: '#ec4899' },
    { name: 'Payroll Exp', amount: report.payrollExpenses, fill: '#8b5cf6' },
    { name: 'Net Profit', amount: report.netProfitOrLoss, fill: report.netProfitOrLoss >= 0 ? '#059669' : '#dc2626' },
  ] : [];

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Profit &amp; Loss Statement"
        subtitle="Accrual basis statement of comprehensive income and operational profitability"
        actions={
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <input
                type="date"
                value={from}
                onChange={e => setFrom(e.target.value)}
                className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
              />
              <span className="text-xs text-gray-400">to</span>
              <input
                type="date"
                value={to}
                onChange={e => setTo(e.target.value)}
                className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
              />
            </div>
            {report && (
              <button
                onClick={() => {
                  const rows = [
                    ['Gross Sales', report.salesRevenue],
                    ['Less: Returns & Discounts', report.salesReturnsAndDiscounts],
                    ['Net Sales Revenue', report.netSales],
                    ['Cost of Goods Sold (COGS)', report.costOfGoodsSold],
                    ['Gross Profit', report.grossProfit],
                    ['Operating Expenses', report.totalOperatingExpenses],
                    ['Payroll Expenses', report.payrollExpenses],
                    ['Net Profit / Loss', report.netProfitOrLoss],
                  ];
                  const csv = ['Line Item,Amount (ETB)', ...rows.map(r => `"${r[0]}",${r[1]}`)].join('\n');
                  const a = document.createElement('a');
                  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
                  a.download = `pnl-statement-${from}-to-${to}.csv`;
                  a.click();
                }}
                className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700"
              >
                <Download size={14} /> Export CSV
              </button>
            )}
            <button
              onClick={fetchReport}
              disabled={loading}
              className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700"
            >
              <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
              Refresh
            </button>
          </div>
        }
      />

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <AlertCircle className="mr-2 inline" size={16} />{error}
        </div>
      )}

      {/* KPI Cards */}
      {report && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          <StatCard label="Gross Sales" value={ETB(report.salesRevenue)} />
          <StatCard label="Net Sales" value={ETB(report.netSales)} sub={`Less ${ETB(report.salesReturnsAndDiscounts)} returns`} />
          <StatCard label="Cost of Goods Sold" value={ETB(report.costOfGoodsSold)} />
          <StatCard label="Gross Profit" value={ETB(report.grossProfit)} sub={`${report.grossMarginPercent}% margin`} isProfit={report.grossProfit >= 0} />
          <StatCard label="Total Operating Expenses" value={ETB(report.totalOperatingExpenses + report.payrollExpenses)} />
          <StatCard label="Net Profit" value={ETB(report.netProfitOrLoss)} isProfit={report.netProfitOrLoss >= 0} />
        </div>
      )}

      {/* Chart */}
      {report && (
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">Income &amp; Expense Distribution</h3>
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={chartData} margin={{ top: 10, right: 20, left: 20, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} />
              <YAxis tickFormatter={v => `${(v / 1000).toFixed(0)}k`} tick={{ fontSize: 11 }} />
              <Tooltip formatter={(v: number) => ETB(v)} />
              <Bar dataKey="amount" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Detailed Financial Statement Table */}
      {report && (
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="border-b border-gray-200 bg-gray-50 px-5 py-3 dark:border-gray-700 dark:bg-gray-800/80">
            <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">
              Statement of Comprehensive Income — {report.periodStart} to {report.periodEnd}
            </h3>
            <p className="text-xs text-gray-500 mt-0.5">{report.basis}</p>
          </div>

          <div className="divide-y divide-gray-100 p-5 dark:divide-gray-700 text-sm">
            {/* 1. Revenues */}
            <div className="py-2.5">
              <h4 className="font-bold text-gray-900 dark:text-gray-100 uppercase text-xs tracking-wider mb-2">1. Operating Revenue</h4>
              <div className="flex justify-between py-1 text-gray-700 dark:text-gray-300">
                <span>Merchandise Sales Revenue</span>
                <span className="font-mono">{ETB(report.salesRevenue)}</span>
              </div>
              <div className="flex justify-between py-1 text-gray-500">
                <span>Less: Sales Returns &amp; Discounts</span>
                <span className="font-mono">({ETB(report.salesReturnsAndDiscounts)})</span>
              </div>
              <div className="flex justify-between py-1 font-bold text-gray-900 dark:text-gray-100 border-t border-gray-200 dark:border-gray-700 mt-1">
                <span>Net Sales Revenue</span>
                <span className="font-mono">{ETB(report.netSales)}</span>
              </div>
            </div>

            {/* 2. COGS */}
            <div className="py-2.5">
              <h4 className="font-bold text-gray-900 dark:text-gray-100 uppercase text-xs tracking-wider mb-2">2. Cost of Sales</h4>
              <div className="flex justify-between py-1 text-gray-700 dark:text-gray-300">
                <span>Cost of Goods Sold (Inventory Outflows)</span>
                <span className="font-mono">({ETB(report.costOfGoodsSold)})</span>
              </div>
              <div className="flex justify-between py-1 font-bold text-emerald-700 dark:text-emerald-400 border-t border-gray-200 dark:border-gray-700 mt-1">
                <span>Gross Profit (Gross Margin: {report.grossMarginPercent}%)</span>
                <span className="font-mono">{ETB(report.grossProfit)}</span>
              </div>
            </div>

            {/* 3. Operating Expenses */}
            <div className="py-2.5">
              <h4 className="font-bold text-gray-900 dark:text-gray-100 uppercase text-xs tracking-wider mb-2">3. Operating Expenses</h4>
              {report.operatingExpenseItems.map(item => (
                <div key={item.code} className="flex justify-between py-1 text-gray-700 dark:text-gray-300">
                  <span>[{item.code}] {item.name}</span>
                  <span className="font-mono">{ETB(item.amount)}</span>
                </div>
              ))}
              <div className="flex justify-between py-1 font-bold text-gray-900 dark:text-gray-100 border-t border-gray-200 dark:border-gray-700 mt-1">
                <span>Total Operating Expenses</span>
                <span className="font-mono">({ETB(report.totalOperatingExpenses + report.payrollExpenses)})</span>
              </div>
            </div>

            {/* 4. Net Profit */}
            <div className="py-3 bg-gray-50/70 -mx-5 px-5 dark:bg-gray-800/80">
              <div className="flex justify-between items-center">
                <span className="text-base font-bold text-gray-900 dark:text-gray-100">Net Profit / (Loss) for Period</span>
                <span className={`text-xl font-bold font-mono ${report.netProfitOrLoss >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                  {ETB(report.netProfitOrLoss)}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
