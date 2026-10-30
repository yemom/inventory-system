'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, LineChart, Line,
} from 'recharts';
import { FileText, TrendingUp, TrendingDown, AlertCircle, Download, RefreshCw } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { taxApi, type TaxSummary } from '@/lib/api/taxApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

function KpiCard({
  label, value, sub, positive, neutral,
}: { label: string; value: string; sub?: string; positive?: boolean; neutral?: boolean }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <p className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{label}</p>
      <p className={`mt-1 text-xl font-bold ${neutral ? 'text-gray-800 dark:text-gray-100' : positive ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
        {value}
      </p>
      {sub && <p className="mt-1 text-xs text-gray-400">{sub}</p>}
    </div>
  );
}

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

export default function TaxReportPage() {
  const { hasPermission } = useAuth();
  const currentYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState(currentYear);
  const [monthlyData, setMonthlyData] = useState<(TaxSummary & { month: number; label: string })[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canView = hasPermission('TAX_DASHBOARD_VIEW') || hasPermission('TAX_REPORT_VIEW');

  const fetchAllMonths = useCallback(async (year: number) => {
    setLoading(true);
    setError(null);
    const results: (TaxSummary & { month: number; label: string })[] = [];
    for (let m = 1; m <= 12; m++) {
      const period = `${year}-${String(m).padStart(2, '0')}`;
      try {
        const s = await taxApi.summary(period);
        results.push({ ...s, month: m, label: MONTHS[m - 1] });
      } catch {
        // Month with no data — push zeroes
        results.push({
          month: m,
          label: MONTHS[m - 1],
          periodStart: `${year}-${String(m).padStart(2, '0')}-01`,
          periodEnd: '',
          totalSales: 0,
          taxableSales: 0,
          zeroRatedSales: 0,
          exemptSales: 0,
          outputVat: 0,
          eligibleInputVat: 0,
          nonRecoverableInputVat: 0,
          estimatedNetVat: 0,
          withholdingTax: 0,
          estimatedIncomeTax: 0,
          taxPaymentsMade: 0,
          outstandingLiabilities: 0,
          salesWithTaxData: 0,
          purchasesWithTaxData: 0,
          currency: 'ETB',
          calculationStatus: 'NO_DATA',
          classificationNotice: '',
          trend: [],
        });
      }
    }
    setMonthlyData(results);
    setLoading(false);
  }, []);

  useEffect(() => {
    if (canView) fetchAllMonths(selectedYear);
  }, [selectedYear, canView, fetchAllMonths]);

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <AlertCircle className="mx-auto mb-2 text-amber-500" size={32} />
        <p>You do not have permission to view Tax Reports.</p>
      </div>
    );
  }

  const annualTotals = monthlyData.reduce(
    (acc, m) => ({
      totalSales: acc.totalSales + (m.totalSales ?? 0),
      taxableSales: acc.taxableSales + (m.taxableSales ?? 0),
      outputVat: acc.outputVat + m.outputVat,
      eligibleInputVat: acc.eligibleInputVat + m.eligibleInputVat,
      estimatedNetVat: acc.estimatedNetVat + m.estimatedNetVat,
      taxPaymentsMade: acc.taxPaymentsMade + (m.taxPaymentsMade ?? 0),
      outstandingLiabilities: acc.outstandingLiabilities + (m.outstandingLiabilities ?? 0),
    }),
    {
      totalSales: 0, taxableSales: 0, outputVat: 0,
      eligibleInputVat: 0, estimatedNetVat: 0,
      taxPaymentsMade: 0, outstandingLiabilities: 0,
    },
  );

  const hasAnyData = monthlyData.some(m => m.outputVat > 0 || (m.totalSales ?? 0) > 0);

  const years = Array.from({ length: 5 }, (_, i) => currentYear - i);

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Annual & Monthly Tax Report"
        subtitle={`Real tax calculations from persisted transaction records — ${selectedYear}`}
        actions={
          <div className="flex items-center gap-3">
            <select
              value={selectedYear}
              onChange={e => setSelectedYear(Number(e.target.value))}
              className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            >
              {years.map(y => (
                <option key={y} value={y}>{y}</option>
              ))}
            </select>
            <button
              onClick={() => fetchAllMonths(selectedYear)}
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
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-24 animate-pulse rounded-xl bg-gray-100 dark:bg-gray-700" />
          ))}
        </div>
      ) : (
        <>
          {/* Annual KPI Summary */}
          <section>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
              <FileText size={14} className="mr-1 inline" />
              Annual Totals — {selectedYear}
            </h2>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
              <KpiCard label="Total Sales" value={ETB(annualTotals.totalSales)} neutral />
              <KpiCard label="Taxable Sales" value={ETB(annualTotals.taxableSales)} neutral />
              <KpiCard label="Output VAT" value={ETB(annualTotals.outputVat)} positive={false} neutral={false} />
              <KpiCard label="Input VAT (Recoverable)" value={ETB(annualTotals.eligibleInputVat)} positive />
              <KpiCard
                label="Net VAT Liability"
                value={ETB(annualTotals.estimatedNetVat)}
                sub={annualTotals.estimatedNetVat > 0 ? 'Payable to ERCA' : annualTotals.estimatedNetVat < 0 ? 'Credit position' : 'Nil'}
                positive={annualTotals.estimatedNetVat <= 0}
              />
              <KpiCard label="Tax Payments Made" value={ETB(annualTotals.taxPaymentsMade)} positive />
              <KpiCard
                label="Outstanding Liability"
                value={ETB(annualTotals.outstandingLiabilities)}
                positive={annualTotals.outstandingLiabilities <= 0}
              />
            </div>
          </section>

          {!hasAnyData && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-center text-amber-800 dark:border-amber-700/40 dark:bg-amber-950/20 dark:text-amber-200">
              <AlertCircle className="mx-auto mb-2" size={24} />
              <p className="font-medium">No tax data recorded for {selectedYear}</p>
              <p className="mt-1 text-sm">Tax figures will appear here once sales and purchase transactions are recorded and the tax engine has processed them.</p>
            </div>
          )}

          {hasAnyData && (
            <>
              {/* VAT Chart */}
              <section>
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
                  <TrendingUp size={14} className="mr-1 inline" />
                  Monthly VAT Breakdown
                </h2>
                <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={monthlyData} margin={{ top: 10, right: 20, left: 10, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
                      <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                      <YAxis tickFormatter={v => `${(v/1000).toFixed(0)}k`} tick={{ fontSize: 11 }} />
                      <Tooltip formatter={(v: number) => ETB(v)} />
                      <Legend />
                      <Bar dataKey="outputVat" name="Output VAT" fill="#ef4444" radius={[3, 3, 0, 0]} />
                      <Bar dataKey="eligibleInputVat" name="Input VAT" fill="#22c55e" radius={[3, 3, 0, 0]} />
                      <Bar dataKey="estimatedNetVat" name="Net VAT" fill="#3b82f6" radius={[3, 3, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </section>

              {/* Sales chart */}
              <section>
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
                  <TrendingDown size={14} className="mr-1 inline" />
                  Monthly Sales &amp; Taxable Sales
                </h2>
                <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                  <ResponsiveContainer width="100%" height={220}>
                    <LineChart data={monthlyData} margin={{ top: 10, right: 20, left: 10, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
                      <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                      <YAxis tickFormatter={v => `${(v/1000).toFixed(0)}k`} tick={{ fontSize: 11 }} />
                      <Tooltip formatter={(v: number) => ETB(v)} />
                      <Legend />
                      <Line type="monotone" dataKey="totalSales" name="Total Sales" stroke="#8b5cf6" dot={false} strokeWidth={2} />
                      <Line type="monotone" dataKey="taxableSales" name="Taxable Sales" stroke="#f59e0b" dot={false} strokeWidth={2} strokeDasharray="4 2" />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </section>
            </>
          )}

          {/* Monthly Breakdown Table */}
          <section>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
                Monthly Detail Table
              </h2>
              <button
                className="flex items-center gap-1 text-xs text-blue-600 hover:underline"
                onClick={() => {
                  const rows = monthlyData.map(m =>
                    [m.label, m.totalSales ?? 0, m.taxableSales ?? 0, m.outputVat,
                     m.eligibleInputVat, m.estimatedNetVat, m.taxPaymentsMade ?? 0,
                     m.outstandingLiabilities ?? 0].join(',')
                  );
                  const csv = ['Month,Total Sales,Taxable Sales,Output VAT,Input VAT,Net VAT,Payments Made,Outstanding', ...rows].join('\n');
                  const a = document.createElement('a');
                  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
                  a.download = `tax-report-${selectedYear}.csv`;
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
                    {['Month','Total Sales','Taxable Sales','Output VAT','Input VAT','Net VAT','Payments Made','Outstanding','Status'].map(h => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 bg-white dark:divide-gray-700 dark:bg-gray-900">
                  {monthlyData.map(row => {
                    const noData = row.calculationStatus === 'NO_DATA' || (row.outputVat === 0 && (row.totalSales ?? 0) === 0);
                    return (
                      <tr key={`${selectedYear}-${String(row.month).padStart(2, '0')}`} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                        <td className="px-4 py-2.5 font-medium">{row.label}</td>
                        <td className="px-4 py-2.5 text-right">{noData ? '—' : ETB(row.totalSales ?? 0)}</td>
                        <td className="px-4 py-2.5 text-right">{noData ? '—' : ETB(row.taxableSales ?? 0)}</td>
                        <td className="px-4 py-2.5 text-right">{noData ? '—' : ETB(row.outputVat)}</td>
                        <td className="px-4 py-2.5 text-right text-green-700">{noData ? '—' : ETB(row.eligibleInputVat)}</td>
                        <td className={`px-4 py-2.5 text-right font-medium ${row.estimatedNetVat > 0 ? 'text-red-600' : row.estimatedNetVat < 0 ? 'text-green-600' : ''}`}>
                          {noData ? '—' : ETB(row.estimatedNetVat)}
                        </td>
                        <td className="px-4 py-2.5 text-right text-green-700">{noData ? '—' : ETB(row.taxPaymentsMade ?? 0)}</td>
                        <td className={`px-4 py-2.5 text-right ${(row.outstandingLiabilities ?? 0) > 0 ? 'font-medium text-amber-700' : ''}`}>
                          {noData ? '—' : ETB(row.outstandingLiabilities ?? 0)}
                        </td>
                        <td className="px-4 py-2.5">
                          {noData ? (
                            <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500 dark:bg-gray-700">No Data</span>
                          ) : (
                            <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
                              {row.calculationStatus ?? 'Calculated'}
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                  {/* Annual totals row */}
                  <tr className="border-t-2 border-gray-300 bg-gray-50 font-bold dark:border-gray-600 dark:bg-gray-800">
                    <td className="px-4 py-3">Annual Total</td>
                    <td className="px-4 py-3 text-right">{ETB(annualTotals.totalSales)}</td>
                    <td className="px-4 py-3 text-right">{ETB(annualTotals.taxableSales)}</td>
                    <td className="px-4 py-3 text-right">{ETB(annualTotals.outputVat)}</td>
                    <td className="px-4 py-3 text-right text-green-700">{ETB(annualTotals.eligibleInputVat)}</td>
                    <td className={`px-4 py-3 text-right ${annualTotals.estimatedNetVat > 0 ? 'text-red-600' : 'text-green-600'}`}>
                      {ETB(annualTotals.estimatedNetVat)}
                    </td>
                    <td className="px-4 py-3 text-right text-green-700">{ETB(annualTotals.taxPaymentsMade)}</td>
                    <td className={`px-4 py-3 text-right ${annualTotals.outstandingLiabilities > 0 ? 'text-amber-700' : ''}`}>
                      {ETB(annualTotals.outstandingLiabilities)}
                    </td>
                    <td className="px-4 py-3" />
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <p className="text-xs text-gray-400 dark:text-gray-500">
            All figures calculated from persisted transaction records using backend tax engine. VAT based on Ethiopian Proclamation No. 285/2002 and amendments. Verify with a certified tax professional before filing.
          </p>
        </>
      )}
    </div>
  );
}
