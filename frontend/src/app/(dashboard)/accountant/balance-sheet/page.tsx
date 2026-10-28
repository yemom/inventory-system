'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  Building2, RefreshCw, AlertCircle, Shield, Download, CheckCircle2, XCircle,
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { accountingApi, type BalanceSheetReport } from '@/lib/api/accountingApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

export default function BalanceSheetPage() {
  const { hasPermission } = useAuth();
  const [report, setReport] = useState<BalanceSheetReport | null>(null);
  const [asOf, setAsOf] = useState(new Date().toISOString().slice(0, 10));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canView = hasPermission('ACCOUNTING_VIEW') || hasPermission('FINANCIAL_REPORT_VIEW');

  const fetchBalanceSheet = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await accountingApi.balanceSheet(asOf);
      setReport(data);
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? 'Failed to load balance sheet');
    } finally {
      setLoading(false);
    }
  }, [asOf]);

  useEffect(() => {
    if (canView) fetchBalanceSheet();
  }, [canView, fetchBalanceSheet]);

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Shield className="mx-auto mb-2 text-amber-500" size={32} />
        <p>You do not have permission to view Balance Sheets.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Balance Sheet"
        subtitle="Statement of financial position — Assets, Liabilities, and Owner's Equity"
        actions={
          <div className="flex items-center gap-3">
            <input
              type="date"
              value={asOf}
              onChange={e => setAsOf(e.target.value)}
              className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            />
            {report && (
              <button
                onClick={() => {
                  const rows = [
                    ...report.currentAssets.map(a => ['Asset', a.code, a.name, a.amount]),
                    ['Total Assets', '', '', report.totalAssets],
                    ...report.currentLiabilities.map(l => ['Liability', l.code, l.name, l.amount]),
                    ['Total Liabilities', '', '', report.totalLiabilities],
                    ...report.equityItems.map(e => ['Equity', e.code, e.name, e.amount]),
                    ['Total Equity', '', '', report.totalEquity],
                    ['Total Liabilities & Equity', '', '', report.totalLiabilitiesAndEquity],
                  ];
                  const csv = ['Classification,Code,Account Name,Amount (ETB)', ...rows.map(r => r.join(','))].join('\n');
                  const a = document.createElement('a');
                  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
                  a.download = `balance-sheet-${asOf}.csv`;
                  a.click();
                }}
                className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700"
              >
                <Download size={14} /> Export CSV
              </button>
            )}
            <button
              onClick={fetchBalanceSheet}
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

      {/* Accounting Equation Verification Banner */}
      {report && (
        <div className={`flex items-center justify-between rounded-xl p-5 border shadow-sm ${
          report.balanced
            ? 'border-emerald-200 bg-emerald-50 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200'
            : 'border-red-200 bg-red-50 text-red-900 dark:border-red-800 dark:bg-red-950/30 dark:text-red-200'
        }`}>
          <div className="flex items-center gap-3">
            {report.balanced ? (
              <CheckCircle2 size={32} className="text-emerald-600 dark:text-emerald-400" />
            ) : (
              <XCircle size={32} className="text-red-600 dark:text-red-400" />
            )}
            <div>
              <h3 className="text-base font-bold">
                {report.balanced ? 'Accounting Equation Balanced: Assets = Liabilities + Equity' : 'Equation Discrepancy Detected!'}
              </h3>
              <p className="text-xs opacity-80 mt-0.5">
                {report.balanced
                  ? `Total Assets (ETB ${ETB(report.totalAssets)}) fully match Total Liabilities & Equity as of ${report.asOfDate}.`
                  : `Discrepancy of ETB ${ETB(report.discrepancy)} requires investigation.`}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-6 text-right">
            <div>
              <p className="text-xs uppercase tracking-wide opacity-70">Total Assets</p>
              <p className="font-mono text-lg font-bold">{ETB(report.totalAssets)}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide opacity-70">Liabilities + Equity</p>
              <p className="font-mono text-lg font-bold">{ETB(report.totalLiabilitiesAndEquity)}</p>
            </div>
          </div>
        </div>
      )}

      {/* Two Column Layout: Assets vs Liabilities & Equity */}
      {report && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {/* Assets Section */}
          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <h3 className="font-bold text-gray-900 dark:text-gray-100 uppercase text-xs tracking-wider border-b border-gray-200 pb-2 dark:border-gray-700">
              Assets
            </h3>
            <div className="mt-4 space-y-3">
              <div>
                <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Current Assets</h4>
                <div className="space-y-1.5">
                  {report.currentAssets.map(a => (
                    <div key={a.code} className="flex justify-between text-xs py-1 text-gray-700 dark:text-gray-300">
                      <span>[{a.code}] {a.name}</span>
                      <span className="font-mono font-medium">{ETB(a.amount)}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="border-t-2 border-gray-300 pt-3 dark:border-gray-600">
                <div className="flex justify-between items-center text-sm font-bold text-gray-900 dark:text-gray-100">
                  <span>TOTAL ASSETS</span>
                  <span className="font-mono text-emerald-600 dark:text-emerald-400">{ETB(report.totalAssets)}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Liabilities & Equity Section */}
          <div className="space-y-6">
            {/* Liabilities */}
            <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <h3 className="font-bold text-gray-900 dark:text-gray-100 uppercase text-xs tracking-wider border-b border-gray-200 pb-2 dark:border-gray-700">
                Liabilities
              </h3>
              <div className="mt-4 space-y-3">
                <div>
                  <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Current Liabilities</h4>
                  <div className="space-y-1.5">
                    {report.currentLiabilities.map(l => (
                      <div key={l.code} className="flex justify-between text-xs py-1 text-gray-700 dark:text-gray-300">
                        <span>[{l.code}] {l.name}</span>
                        <span className="font-mono font-medium">{ETB(l.amount)}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="border-t border-gray-200 pt-2 dark:border-gray-700">
                  <div className="flex justify-between text-xs font-bold text-gray-800 dark:text-gray-200">
                    <span>Total Liabilities</span>
                    <span className="font-mono">{ETB(report.totalLiabilities)}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Equity */}
            <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <h3 className="font-bold text-gray-900 dark:text-gray-100 uppercase text-xs tracking-wider border-b border-gray-200 pb-2 dark:border-gray-700">
                Owner&apos;s Equity
              </h3>
              <div className="mt-4 space-y-3">
                <div className="space-y-1.5">
                  {report.equityItems.map(e => (
                    <div key={e.code} className="flex justify-between text-xs py-1 text-gray-700 dark:text-gray-300">
                      <span>[{e.code}] {e.name}</span>
                      <span className="font-mono font-medium">{ETB(e.amount)}</span>
                    </div>
                  ))}
                </div>

                <div className="border-t border-gray-200 pt-2 dark:border-gray-700">
                  <div className="flex justify-between text-xs font-bold text-gray-800 dark:text-gray-200">
                    <span>Total Equity</span>
                    <span className="font-mono">{ETB(report.totalEquity)}</span>
                  </div>
                </div>

                <div className="border-t-2 border-gray-300 pt-3 dark:border-gray-600">
                  <div className="flex justify-between items-center text-sm font-bold text-gray-900 dark:text-gray-100">
                    <span>TOTAL LIABILITIES &amp; EQUITY</span>
                    <span className="font-mono text-emerald-600 dark:text-emerald-400">{ETB(report.totalLiabilitiesAndEquity)}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
