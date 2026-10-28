'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  BookText, RefreshCw, AlertCircle, Shield, Download, Filter,
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { accountingApi, type GeneralLedgerReport, type Account } from '@/lib/api/accountingApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

export default function GeneralLedgerPage() {
  const { hasPermission } = useAuth();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);
  const [report, setReport] = useState<GeneralLedgerReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [from, setFrom] = useState(new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10));
  const [to, setTo] = useState(new Date().toISOString().slice(0, 10));

  const canView = hasPermission('ACCOUNTING_VIEW') || hasPermission('ACCOUNTING_REPORTS_VIEW');

  // Load accounts
  useEffect(() => {
    if (!canView) return;
    accountingApi.accounts().then(data => {
      setAccounts(data);
      if (data.length > 0 && !selectedAccountId) {
        setSelectedAccountId(data[0].id);
      }
    }).catch(() => {});
  }, [canView, selectedAccountId]);

  const fetchLedger = useCallback(async () => {
    if (!selectedAccountId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await accountingApi.ledger(selectedAccountId, from, to);
      setReport(data);
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? 'Failed to load general ledger');
    } finally {
      setLoading(false);
    }
  }, [selectedAccountId, from, to]);

  useEffect(() => {
    if (canView && selectedAccountId) fetchLedger();
  }, [canView, selectedAccountId, fetchLedger]);

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Shield className="mx-auto mb-2 text-amber-500" size={32} />
        <p>You do not have permission to view the General Ledger.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="General Ledger"
        subtitle="Individual account transaction activity and running balance trail"
        actions={
          <div className="flex items-center gap-3">
            <button
              onClick={fetchLedger}
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

      {/* Account and Date Filter Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div className="flex flex-wrap items-center gap-3">
          <div className="min-w-[260px]">
            <label className="block text-xs font-medium text-gray-500 mb-1">Select Account</label>
            <select
              value={selectedAccountId ?? ''}
              onChange={e => setSelectedAccountId(Number(e.target.value))}
              className="w-full rounded-lg border border-gray-300 bg-white p-2 text-sm font-medium dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            >
              {accounts.map(a => (
                <option key={a.id} value={a.id}>
                  {a.code} — {a.name} ({a.category})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">From Date</label>
            <input
              type="date"
              value={from}
              onChange={e => setFrom(e.target.value)}
              className="rounded-lg border border-gray-300 bg-white p-2 text-xs dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">To Date</label>
            <input
              type="date"
              value={to}
              onChange={e => setTo(e.target.value)}
              className="rounded-lg border border-gray-300 bg-white p-2 text-xs dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            />
          </div>
        </div>

        {report && (
          <button
            onClick={() => {
              const rows = report.transactions.map(t =>
                [t.date, t.entryNumber, t.reference, `"${t.description.replace(/"/g, '""')}"`, t.debit, t.credit, t.runningBalance].join(',')
              );
              const csv = ['Date,Entry,Reference,Description,Debit,Credit,Running Balance', ...rows].join('\n');
              const a = document.createElement('a');
              a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
              a.download = `ledger-${report.account.code}-${from}-to-${to}.csv`;
              a.click();
            }}
            className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          >
            <Download size={14} /> Export CSV
          </button>
        )}
      </div>

      {/* Account Balance Summary Cards */}
      {report && (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <p className="text-xs uppercase font-medium text-gray-500">Opening Balance</p>
            <p className="mt-1 text-xl font-bold font-mono text-gray-800 dark:text-gray-100">{ETB(report.openingBalance)}</p>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <p className="text-xs uppercase font-medium text-gray-500">Period Debits</p>
            <p className="mt-1 text-xl font-bold font-mono text-blue-600 dark:text-blue-400">{ETB(report.totalDebit)}</p>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <p className="text-xs uppercase font-medium text-gray-500">Period Credits</p>
            <p className="mt-1 text-xl font-bold font-mono text-amber-600 dark:text-amber-400">{ETB(report.totalCredit)}</p>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <p className="text-xs uppercase font-medium text-gray-500">Closing Balance</p>
            <p className="mt-1 text-xl font-bold font-mono text-emerald-600 dark:text-emerald-400">{ETB(report.closingBalance)}</p>
          </div>
        </div>
      )}

      {/* Ledger Table */}
      <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-700">
          <thead className="bg-gray-50 dark:bg-gray-800">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Date</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Entry #</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Reference</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Description</th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-gray-500">Debit (ETB)</th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-gray-500">Credit (ETB)</th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-gray-500">Running Balance (ETB)</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
            {loading ? (
              [...Array(5)].map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td colSpan={7} className="px-4 py-4"><div className="h-4 bg-gray-100 rounded dark:bg-gray-700" /></td>
                </tr>
              ))
            ) : !report || report.transactions.length === 0 ? (
              <tr>
                <td colSpan={7} className="p-8 text-center text-gray-400">
                  <BookText size={32} className="mx-auto mb-2 opacity-40" />
                  No transactions recorded for this account in the selected period.
                </td>
              </tr>
            ) : (
              report.transactions.map((t, idx) => (
                <tr key={idx} className="hover:bg-gray-50 dark:hover:bg-gray-800/60 text-xs">
                  <td className="px-4 py-3 font-medium text-gray-700 dark:text-gray-300 whitespace-nowrap">{t.date}</td>
                  <td className="px-4 py-3 font-mono font-bold text-gray-900 dark:text-gray-100">{t.entryNumber}</td>
                  <td className="px-4 py-3 text-gray-500">{t.reference || '—'}</td>
                  <td className="px-4 py-3 text-gray-700 dark:text-gray-300 max-w-xs truncate">{t.description}</td>
                  <td className="px-4 py-3 text-right font-mono font-medium text-gray-900 dark:text-gray-100">
                    {t.debit > 0 ? ETB(t.debit) : '—'}
                  </td>
                  <td className="px-4 py-3 text-right font-mono font-medium text-gray-900 dark:text-gray-100">
                    {t.credit > 0 ? ETB(t.credit) : '—'}
                  </td>
                  <td className="px-4 py-3 text-right font-mono font-bold text-gray-900 dark:text-gray-100">
                    {ETB(t.runningBalance)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
