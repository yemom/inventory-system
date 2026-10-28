'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { Users, DollarSign, AlertCircle, Download, RefreshCw } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { payrollApi, type PayrollPeriod } from '@/lib/api/payrollApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

const MONTH_NAMES = [
  '', 'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  FINALIZED: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  PAID: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
};

function KpiCard({ label, value, sub, color }: { label: string; value: string; sub?: string; color?: string }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <p className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">{label}</p>
      <p className={`mt-1 text-xl font-bold ${color ?? 'text-gray-800 dark:text-gray-100'}`}>{value}</p>
      {sub && <p className="mt-1 text-xs text-gray-400">{sub}</p>}
    </div>
  );
}

export default function PayrollReportPage() {
  const { hasPermission } = useAuth();
  const currentYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState(currentYear);
  const [periods, setPeriods] = useState<PayrollPeriod[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canView = hasPermission('PAYROLL_SUMMARY_VIEW');

  const fetchPeriods = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const all = await payrollApi.periods();
      setPeriods(Array.isArray(all) ? all : []);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } }; message?: string })
        ?.response?.data?.message ?? (e as { message?: string })?.message ?? 'Failed to load payroll periods';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canView) fetchPeriods();
  }, [canView, fetchPeriods]);

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <AlertCircle className="mx-auto mb-2 text-amber-500" size={32} />
        <p>You do not have permission to view Payroll Reports.</p>
      </div>
    );
  }

  const yearPeriods = periods.filter(p => p.year === selectedYear).sort((a, b) => a.month - b.month);

  const annualTotals = yearPeriods.reduce(
    (acc, p) => ({
      gross: acc.gross + p.grossPayroll,
      net: acc.net + p.netPayroll,
      employmentTax: acc.employmentTax + p.employmentTax,
      employeePension: acc.employeePension + p.employeePension,
      employerPension: acc.employerPension + p.employerPension,
      employerCost: acc.employerCost + p.employerCost,
    }),
    { gross: 0, net: 0, employmentTax: 0, employeePension: 0, employerPension: 0, employerCost: 0 },
  );

  const chartData = yearPeriods.map(p => ({
    label: MONTH_NAMES[p.month]?.slice(0, 3) ?? `M${p.month}`,
    gross: p.grossPayroll,
    net: p.netPayroll,
    incomeTax: p.employmentTax,
    pension: p.employeePension + p.employerPension,
  }));

  const years = [...new Set(periods.map(p => p.year))].sort((a, b) => b - a);
  if (!years.includes(currentYear)) years.unshift(currentYear);

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Monthly Payroll Report"
        subtitle="Gross, net, income tax, pension, and deductions — from finalized payroll periods"
        actions={
          <div className="flex items-center gap-3">
            <select
              value={selectedYear}
              onChange={e => setSelectedYear(Number(e.target.value))}
              className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            >
              {years.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
            <button
              onClick={fetchPeriods}
              disabled={loading}
              className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700"
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

      {loading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          {[...Array(6)].map((_, i) => <div key={i} className="h-24 animate-pulse rounded-xl bg-gray-100 dark:bg-gray-700" />)}
        </div>
      ) : yearPeriods.length === 0 ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-8 text-center text-amber-800 dark:border-amber-700/40 dark:bg-amber-950/20 dark:text-amber-200">
          <Users className="mx-auto mb-3" size={32} />
          <p className="text-lg font-semibold">No payroll records for {selectedYear}</p>
          <p className="mt-1 text-sm">
            Payroll periods will appear here once employee payroll profiles are created and payroll is processed and finalized under Payroll Management.
          </p>
        </div>
      ) : (
        <>
          {/* Annual KPIs */}
          <section>
            <h2 className="mb-3 flex items-center gap-1.5 text-sm font-semibold uppercase tracking-wide text-gray-500">
              <DollarSign size={14} />
              Annual Totals — {selectedYear}
            </h2>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
              <KpiCard label="Gross Payroll" value={ETB(annualTotals.gross)} />
              <KpiCard label="Income Tax Withheld" value={ETB(annualTotals.employmentTax)} color="text-red-600 dark:text-red-400" />
              <KpiCard label="Employee Pension" value={ETB(annualTotals.employeePension)} color="text-amber-600 dark:text-amber-400" />
              <KpiCard label="Employer Pension" value={ETB(annualTotals.employerPension)} color="text-amber-700 dark:text-amber-300" />
              <KpiCard label="Net Payroll" value={ETB(annualTotals.net)} color="text-green-600 dark:text-green-400" />
              <KpiCard label="Total Employer Cost" value={ETB(annualTotals.employerCost)} />
            </div>
          </section>

          {/* Chart */}
          <section>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">Payroll Trends</h2>
            <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis tickFormatter={v => `${(v / 1000).toFixed(0)}k`} tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(v: number) => ETB(v)} />
                  <Legend />
                  <Bar dataKey="gross" name="Gross Payroll" fill="#8b5cf6" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="net" name="Net Payroll" fill="#22c55e" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="incomeTax" name="Income Tax" fill="#ef4444" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="pension" name="Total Pension" fill="#f59e0b" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          {/* Table */}
          <section>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-500">Monthly Detail</h2>
              <button
                className="flex items-center gap-1 text-xs text-blue-600 hover:underline"
                onClick={() => {
                  const rows = yearPeriods.map(p =>
                    [MONTH_NAMES[p.month], p.grossPayroll, p.employmentTax, p.employeePension,
                     p.employerPension, p.grossPayroll - p.netPayroll, p.netPayroll,
                     p.employerCost, p.status].join(',')
                  );
                  const csv = ['Month,Gross,Income Tax,Employee Pension,Employer Pension,Total Deductions,Net Payroll,Employer Cost,Status', ...rows].join('\n');
                  const a = document.createElement('a');
                  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
                  a.download = `payroll-report-${selectedYear}.csv`;
                  a.click();
                }}
              >
                <Download size={12} /> Export CSV
              </button>
            </div>
            <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
              <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-700">
                <thead className="bg-gray-50 dark:bg-gray-800">
                  <tr>
                    {['Month','Gross Payroll','Income Tax','Emp. Pension','Employer Pension','Deductions','Net Payroll','Employer Cost','Status'].map(h => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 bg-white dark:divide-gray-700 dark:bg-gray-900">
                  {yearPeriods.map(p => {
                    const totalDeductions = p.grossPayroll - p.netPayroll;
                    return (
                      <tr key={p.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                        <td className="px-4 py-2.5 font-medium">{MONTH_NAMES[p.month]}</td>
                        <td className="px-4 py-2.5 text-right">{ETB(p.grossPayroll)}</td>
                        <td className="px-4 py-2.5 text-right text-red-600">{ETB(p.employmentTax)}</td>
                        <td className="px-4 py-2.5 text-right text-amber-600">{ETB(p.employeePension)}</td>
                        <td className="px-4 py-2.5 text-right text-amber-700">{ETB(p.employerPension)}</td>
                        <td className="px-4 py-2.5 text-right">{ETB(totalDeductions)}</td>
                        <td className="px-4 py-2.5 text-right font-medium text-green-700">{ETB(p.netPayroll)}</td>
                        <td className="px-4 py-2.5 text-right">{ETB(p.employerCost)}</td>
                        <td className="px-4 py-2.5">
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[p.status] ?? 'bg-gray-100 text-gray-600'}`}>
                            {p.status}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                  {/* Annual totals row */}
                  <tr className="border-t-2 border-gray-300 bg-gray-50 font-bold dark:border-gray-600 dark:bg-gray-800">
                    <td className="px-4 py-3">Annual Total</td>
                    <td className="px-4 py-3 text-right">{ETB(annualTotals.gross)}</td>
                    <td className="px-4 py-3 text-right text-red-600">{ETB(annualTotals.employmentTax)}</td>
                    <td className="px-4 py-3 text-right text-amber-600">{ETB(annualTotals.employeePension)}</td>
                    <td className="px-4 py-3 text-right text-amber-700">{ETB(annualTotals.employerPension)}</td>
                    <td className="px-4 py-3 text-right">{ETB(annualTotals.gross - annualTotals.net)}</td>
                    <td className="px-4 py-3 text-right text-green-700">{ETB(annualTotals.net)}</td>
                    <td className="px-4 py-3 text-right">{ETB(annualTotals.employerCost)}</td>
                    <td className="px-4 py-3" />
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <p className="text-xs text-gray-400 dark:text-gray-500">
            All figures from finalized payroll periods. Income tax per Ethiopian Income Tax Proclamation No. 979/2016. Pension per Private Organization Employees Pension Proclamation No. 715/2011. Only finalized periods contribute to annual totals; draft periods are shown but excluded.
          </p>
        </>
      )}
    </div>
  );
}
