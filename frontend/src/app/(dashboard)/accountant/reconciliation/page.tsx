'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  CheckCircle2, AlertTriangle, Clock, HelpCircle,
  RefreshCw, Download, Filter, Search, ArrowRight, ShieldCheck,
} from 'lucide-react';
import Link from 'next/link';
import PageHeader from '@/components/ui/PageHeader';
import { accountingApi, type ReconciliationReport, type ReconciliationItem } from '@/lib/api/accountingApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

export default function ReconciliationPage() {
  const { hasPermission } = useAuth();
  const canReconcile = hasPermission('ACCOUNTING_RECONCILE') || hasPermission('ACCOUNTING_VIEW') || hasPermission('SUPER_ADMIN');

  const today = new Date().toISOString().split('T')[0];
  const [asOfDate, setAsOfDate] = useState(today);
  const [report, setReport] = useState<ReconciliationReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  const fetchReconciliation = useCallback(async (date: string) => {
    setLoading(true);
    setError(null);
    try {
      const data = await accountingApi.reconciliation(date);
      setReport(data);
    } catch (e: unknown) {
      const msg = (e as { message?: string })?.message ?? 'Failed to load reconciliation report';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canReconcile) {
      fetchReconciliation(asOfDate);
    }
  }, [asOfDate, canReconcile, fetchReconciliation]);

  const filteredItems = (report?.items ?? []).filter((item) => {
    if (categoryFilter !== 'ALL' && item.category !== categoryFilter) return false;
    if (statusFilter !== 'ALL' && item.status !== statusFilter) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return (
        item.description.toLowerCase().includes(q) ||
        item.reference.toLowerCase().includes(q) ||
        item.category.toLowerCase().includes(q)
      );
    }
    return true;
  });

  const exportCsv = () => {
    if (!report || !report.items.length) return;
    const headers = ['Category', 'Reference', 'Description', 'Expected Amount (ETB)', 'Actual Amount (ETB)', 'Variance (ETB)', 'Status', 'Notes'];
    const rows = filteredItems.map(i => [
      `"${i.category}"`,
      `"${i.reference}"`,
      `"${i.description.replace(/"/g, '""')}"`,
      i.expectedAmount.toFixed(2),
      i.actualAmount.toFixed(2),
      i.variance.toFixed(2),
      `"${i.status}"`,
      `"${(i.notes ?? '').replace(/"/g, '""')}"`,
    ]);
    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `reconciliation_${asOfDate}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const getStatusBadge = (status: ReconciliationItem['status']) => {
    switch (status) {
      case 'RECONCILED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300">
            <CheckCircle2 size={12} /> Reconciled
          </span>
        );
      case 'DISCREPANCY':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-800 dark:bg-red-950/50 dark:text-red-300">
            <AlertTriangle size={12} /> Discrepancy
          </span>
        );
      case 'PENDING_DISBURSEMENT':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800 dark:bg-amber-950/50 dark:text-amber-300">
            <Clock size={12} /> Pending Disbursement
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-purple-100 px-2.5 py-0.5 text-xs font-semibold text-purple-800 dark:bg-purple-950/50 dark:text-purple-300">
            <HelpCircle size={12} /> Investigate
          </span>
        );
    }
  };

  const categories = Array.from(new Set(report?.items?.map(i => i.category) ?? []));

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Reconciliation Workflow"
        subtitle="Cross-verify subledger transactions (Sales, Expenses, VAT, Payroll) with the General Ledger"
        actions={
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2">
              <label htmlFor="asOf" className="text-xs font-medium text-gray-500 dark:text-gray-400">
                As of Date:
              </label>
              <input
                id="asOf"
                type="date"
                value={asOfDate}
                onChange={e => setAsOfDate(e.target.value)}
                className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
              />
            </div>
            <button
              onClick={() => fetchReconciliation(asOfDate)}
              disabled={loading}
              className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700"
            >
              <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
              Refresh
            </button>
            <button
              onClick={exportCsv}
              disabled={!report || report.items.length === 0}
              className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700"
            >
              <Download size={14} />
              Export CSV
            </button>
          </div>
        }
      />

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300">
          <AlertTriangle className="mr-2 inline" size={16} />
          {error}
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <p className="text-xs font-medium text-gray-500 dark:text-gray-400">Items Verified</p>
          <p className="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {report?.totalChecked ?? 0}
          </p>
          <p className="mt-1 text-xs text-gray-500">Across VAT, Payroll & Subledgers</p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <p className="text-xs font-medium text-gray-500 dark:text-gray-400">Discrepancy Count</p>
          <p className={`mt-2 text-2xl font-bold ${(report?.discrepanciesCount ?? 0) > 0 ? 'text-red-600 dark:text-red-400' : 'text-emerald-600 dark:text-emerald-400'}`}>
            {report?.discrepanciesCount ?? 0}
          </p>
          <p className="mt-1 text-xs text-gray-500">Items requiring investigation</p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <p className="text-xs font-medium text-gray-500 dark:text-gray-400">Net Discrepancy Amount</p>
          <p className={`mt-2 text-2xl font-bold ${(report?.totalDiscrepancyAmount ?? 0) !== 0 ? 'text-red-600 dark:text-red-400' : 'text-gray-900 dark:text-gray-100'}`}>
            {ETB(report?.totalDiscrepancyAmount ?? 0)}
          </p>
          <p className="mt-1 text-xs text-gray-500">Subledger variance to GL</p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <p className="text-xs font-medium text-gray-500 dark:text-gray-400">Review Status</p>
          <div className="mt-2 flex items-center gap-2">
            {(report?.discrepanciesCount ?? 0) === 0 ? (
              <span className="flex items-center gap-1 text-lg font-bold text-emerald-600 dark:text-emerald-400">
                <ShieldCheck size={22} /> Fully Reconciled
              </span>
            ) : (
              <span className="flex items-center gap-1 text-lg font-bold text-amber-600 dark:text-amber-400">
                <AlertTriangle size={22} /> Attention Needed
              </span>
            )}
          </div>
          <p className="mt-1 text-xs text-gray-500">As of {asOfDate}</p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between dark:border-gray-700 dark:bg-gray-800">
        <div className="flex flex-1 items-center gap-2">
          <Search size={16} className="text-gray-400" />
          <input
            type="text"
            placeholder="Search reference, description, category..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full bg-transparent text-sm placeholder-gray-400 focus:outline-none dark:text-gray-200"
          />
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1 text-xs text-gray-500">
            <Filter size={14} /> Category:
            <select
              value={categoryFilter}
              onChange={e => setCategoryFilter(e.target.value)}
              className="rounded-lg border border-gray-300 bg-white px-2.5 py-1 text-xs dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            >
              <option value="ALL">All Categories</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-1 text-xs text-gray-500">
            Status:
            <select
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
              className="rounded-lg border border-gray-300 bg-white px-2.5 py-1 text-xs dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            >
              <option value="ALL">All Statuses</option>
              <option value="RECONCILED">Reconciled</option>
              <option value="DISCREPANCY">Discrepancy</option>
              <option value="PENDING_DISBURSEMENT">Pending Disbursement</option>
              <option value="INVESTIGATE">Investigate</option>
            </select>
          </div>
        </div>
      </div>

      {/* Reconciliation Table */}
      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div className="border-b border-gray-200 px-6 py-4 dark:border-gray-700">
          <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">
            Reconciliation Items ({filteredItems.length})
          </h2>
          <p className="mt-0.5 text-xs text-gray-500">
            Subledger items matched against corresponding general ledger accounts and verified for balanced postings.
          </p>
        </div>

        {loading ? (
          <div className="p-8 text-center text-sm text-gray-500">
            <RefreshCw size={24} className="mx-auto mb-2 animate-spin text-blue-500" />
            Verifying reconciliation items...
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="p-8 text-center text-sm text-gray-500">
            {report?.items?.length === 0
              ? 'No reconciliation records found. Ensure operational transactions have been synced to the General Ledger.'
              : 'No items match the active filters.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:border-gray-700 dark:bg-gray-900/50 dark:text-gray-400">
                <tr>
                  <th className="px-6 py-3">Category</th>
                  <th className="px-6 py-3">Reference</th>
                  <th className="px-6 py-3">Description</th>
                  <th className="px-6 py-3 text-right">Expected (Subledger)</th>
                  <th className="px-6 py-3 text-right">Actual (GL)</th>
                  <th className="px-6 py-3 text-right">Variance</th>
                  <th className="px-6 py-3 text-center">Status</th>
                  <th className="px-6 py-3">Notes & Next Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                {filteredItems.map((item, idx) => (
                  <tr key={idx} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                    <td className="whitespace-nowrap px-6 py-3.5 font-medium text-gray-900 dark:text-gray-200">
                      <span className="rounded bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                        {item.category}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 font-mono text-xs text-gray-600 dark:text-gray-400">
                      {item.reference}
                    </td>
                    <td className="px-6 py-3.5 text-gray-800 dark:text-gray-200">
                      {item.description}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-right font-mono text-gray-900 dark:text-gray-100">
                      {ETB(item.expectedAmount)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-right font-mono text-gray-900 dark:text-gray-100">
                      {ETB(item.actualAmount)}
                    </td>
                    <td className={`whitespace-nowrap px-6 py-3.5 text-right font-mono font-medium ${item.variance !== 0 ? 'text-red-600 dark:text-red-400' : 'text-gray-500'}`}>
                      {ETB(item.variance)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-center">
                      {getStatusBadge(item.status)}
                    </td>
                    <td className="px-6 py-3.5 text-xs text-gray-500 dark:text-gray-400">
                      {item.notes || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Helpful links */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-gray-200 bg-gray-50 p-4 text-xs text-gray-600 dark:border-gray-700 dark:bg-gray-800/50 dark:text-gray-400">
        <div>
          <span>Need to reconcile recent sales or purchases? </span>
          <Link href="/accountant/journal" className="font-semibold text-blue-600 hover:underline dark:text-blue-400">
            Sync operational transactions in Journal Entries <ArrowRight size={12} className="inline" />
          </Link>
        </div>
        <div>
          <Link href="/accountant/trial-balance" className="font-semibold text-blue-600 hover:underline dark:text-blue-400">
            View Trial Balance Equilibrium <ArrowRight size={12} className="inline" />
          </Link>
        </div>
      </div>
    </div>
  );
}
