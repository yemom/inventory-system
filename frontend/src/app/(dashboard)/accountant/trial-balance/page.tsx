'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  Scale, RefreshCw, AlertCircle, Shield, Download, CheckCircle2, XCircle,
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { accountingApi, type TrialBalanceReport } from '@/lib/api/accountingApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

export default function TrialBalancePage() {
  const { hasPermission } = useAuth();
  const [report, setReport] = useState<TrialBalanceReport | null>(null);
  const [asOf, setAsOf] = useState(new Date().toISOString().slice(0, 10));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canView = hasPermission('ACCOUNTING_VIEW') || hasPermission('ACCOUNTING_REPORTS_VIEW');

  const fetchTrialBalance = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await accountingApi.trialBalance(asOf);
      setReport(data);
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? 'Failed to load trial balance');
    } finally {
      setLoading(false);
    }
  }, [asOf]);

  useEffect(() => {
    if (canView) fetchTrialBalance();
  }, [canView, fetchTrialBalance]);

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Shield className="mx-auto mb-2 text-amber-500" size={32} />
        <p>You do not have permission to view the Trial Balance.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Trial Balance"
        subtitle="Verification of general ledger debit and credit equality"
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
                  const rows = report.items.map(i =>
                    [i.accountCode, `"${i.accountName.replace(/"/g, '""')}"`, i.category, i.debitBalance, i.creditBalance].join(',')
                  );
                  const csv = ['Account Code,Account Name,Category,Debit,Credit', ...rows, `,Total,,${report.totalDebit},${report.totalCredit}`].join('\n');
                  const a = document.createElement('a');
                  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
                  a.download = `trial-balance-${asOf}.csv`;
                  a.click();
                }}
                className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700"
              >
                <Download size={14} /> Export CSV
              </button>
            )}
            <button
              onClick={fetchTrialBalance}
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

      {/* Equality Banner */}
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
                {report.balanced ? 'Trial Balance is in Perfect Equilibrium' : 'Trial Balance Imbalance Detected!'}
              </h3>
              <p className="text-xs opacity-80 mt-0.5">
                {report.balanced
                  ? `Total Debits equal Total Credits as of ${report.asOfDate}. Standard accounting invariant holds.`
                  : `Discrepancy of ${ETB(report.discrepancy)} between debits and credits requires immediate ledger review.`}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-6 text-right">
            <div>
              <p className="text-xs uppercase tracking-wide opacity-70">Total Debits</p>
              <p className="font-mono text-lg font-bold">{ETB(report.totalDebit)}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-wide opacity-70">Total Credits</p>
              <p className="font-mono text-lg font-bold">{ETB(report.totalCredit)}</p>
            </div>
          </div>
        </div>
      )}

      {/* Trial Balance Table */}
      <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-700">
          <thead className="bg-gray-50 dark:bg-gray-800">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Account Code</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Account Title</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Category</th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-gray-500">Debit Balance (ETB)</th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-gray-500">Credit Balance (ETB)</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-gray-700 text-xs">
            {loading ? (
              [...Array(6)].map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td colSpan={5} className="px-4 py-3.5"><div className="h-4 bg-gray-100 rounded dark:bg-gray-700" /></td>
                </tr>
              ))
            ) : !report || report.items.length === 0 ? (
              <tr>
                <td colSpan={5} className="p-8 text-center text-gray-400">
                  <Scale size={32} className="mx-auto mb-2 opacity-40" />
                  No account balances found.
                </td>
              </tr>
            ) : (
              report.items.map(item => (
                <tr key={item.accountCode} className="hover:bg-gray-50/80 dark:hover:bg-gray-800/60">
                  <td className="px-4 py-2.5 font-mono font-bold text-gray-800 dark:text-gray-200">{item.accountCode}</td>
                  <td className="px-4 py-2.5 font-medium text-gray-900 dark:text-gray-100">{item.accountName}</td>
                  <td className="px-4 py-2.5">
                    <span className="rounded bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                      {item.category}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-right font-mono font-medium text-gray-900 dark:text-gray-100">
                    {item.debitBalance > 0 ? ETB(item.debitBalance) : '—'}
                  </td>
                  <td className="px-4 py-2.5 text-right font-mono font-medium text-gray-900 dark:text-gray-100">
                    {item.creditBalance > 0 ? ETB(item.creditBalance) : '—'}
                  </td>
                </tr>
              ))
            )}

            {/* Total Row */}
            {report && (
              <tr className="border-t-2 border-gray-300 bg-gray-50/80 font-bold dark:border-gray-600 dark:bg-gray-800/80 text-sm">
                <td colSpan={3} className="px-4 py-3 text-right">Trial Balance Totals:</td>
                <td className="px-4 py-3 text-right font-mono text-emerald-700 dark:text-emerald-400">
                  {ETB(report.totalDebit)}
                </td>
                <td className="px-4 py-3 text-right font-mono text-emerald-700 dark:text-emerald-400">
                  {ETB(report.totalCredit)}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
