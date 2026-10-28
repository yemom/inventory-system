'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  TrendingUp, TrendingDown, DollarSign, Shield, AlertTriangle,
  RefreshCw, ExternalLink, Calendar, CheckCircle2, ChevronRight,
} from 'lucide-react';
import Link from 'next/link';
import PageHeader from '@/components/ui/PageHeader';
import { taxApi, type TaxSummary } from '@/lib/api/taxApi';
import { complianceApi } from '@/lib/api/complianceApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

export default function AccountantTaxCompliancePage() {
  const { hasPermission } = useAuth();
  const canView = hasPermission('TAX_DASHBOARD_VIEW') || hasPermission('ACCOUNTING_VIEW') || hasPermission('SUPER_ADMIN');

  const currentYear = new Date().getFullYear();
  const currentMonthStr = `${currentYear}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;
  const [selectedPeriod, setSelectedPeriod] = useState(currentMonthStr);
  const [taxSummary, setTaxSummary] = useState<TaxSummary | null>(null);
  const [complianceScore, setComplianceScore] = useState<number | null>(null);
  const [pendingFilings, setPendingFilings] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async (period: string) => {
    setLoading(true);
    setError(null);
    try {
      const [taxRes, compOverview] = await Promise.allSettled([
        taxApi.summary(period),
        complianceApi.overview(),
      ]);

      if (taxRes.status === 'fulfilled' && taxRes.value) {
        setTaxSummary(taxRes.value);
      }
      if (compOverview.status === 'fulfilled' && compOverview.value) {
        setComplianceScore(compOverview.value.healthScore ?? 100);
        setPendingFilings(compOverview.value.pendingTasksCount ?? 0);
      }
    } catch (e: unknown) {
      const msg = (e as { message?: string })?.message ?? 'Failed to load tax and compliance data';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canView) {
      fetchData(selectedPeriod);
    }
  }, [selectedPeriod, canView, fetchData]);

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Shield className="mx-auto mb-2 text-amber-500" size={40} />
        <p className="text-lg font-semibold">Access Restricted</p>
        <p className="mt-1 text-sm">You need TAX_DASHBOARD_VIEW or ACCOUNTING_VIEW permission to access this page.</p>
      </div>
    );
  }

  const periodOptions = Array.from({ length: 12 }, (_, i) => {
    const d = new Date();
    d.setMonth(d.getMonth() - i);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Tax & Compliance Hub"
        subtitle="Ethiopian VAT, Turnover Tax, Withholding, and Statutory Compliance Deadlines"
        actions={
          <div className="flex items-center gap-3">
            <select
              value={selectedPeriod}
              onChange={e => setSelectedPeriod(e.target.value)}
              className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            >
              {periodOptions.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
            <button
              onClick={() => fetchData(selectedPeriod)}
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
          <AlertTriangle className="mr-2 inline" size={16} />{error}
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">Output VAT (Sales)</span>
            <span className="rounded-full bg-red-50 p-2 text-red-600 dark:bg-red-950/40"><TrendingUp size={16} /></span>
          </div>
          <p className="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">{ETB(taxSummary?.outputVat ?? 0)}</p>
          <p className="mt-1 text-xs text-gray-500">Standard 15% rate applied to sales</p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">Input VAT Recoverable</span>
            <span className="rounded-full bg-emerald-50 p-2 text-emerald-600 dark:bg-emerald-950/40"><TrendingDown size={16} /></span>
          </div>
          <p className="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">{ETB(taxSummary?.eligibleInputVat ?? 0)}</p>
          <p className="mt-1 text-xs text-gray-500">Verified purchases with tax invoices</p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">Net VAT Liability</span>
            <span className="rounded-full bg-amber-50 p-2 text-amber-600 dark:bg-amber-950/40"><DollarSign size={16} /></span>
          </div>
          <p className={`mt-2 text-2xl font-bold ${(taxSummary?.estimatedNetVat ?? 0) > 0 ? 'text-amber-600 dark:text-amber-400' : 'text-gray-900 dark:text-gray-100'}`}>
            {ETB(taxSummary?.estimatedNetVat ?? 0)}
          </p>
          <p className="mt-1 text-xs text-gray-500">
            {(taxSummary?.estimatedNetVat ?? 0) > 0 ? 'Due to ERCA by the 30th' : 'Credit position carry-forward'}
          </p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">Compliance Health Score</span>
            <span className="rounded-full bg-blue-50 p-2 text-blue-600 dark:bg-blue-950/40"><Shield size={16} /></span>
          </div>
          <p className="mt-2 text-2xl font-bold text-blue-600 dark:text-blue-400">
            {complianceScore !== null ? `${complianceScore}%` : '100%'}
          </p>
          <p className="mt-1 text-xs text-gray-500">{pendingFilings} pending compliance action(s)</p>
        </div>
      </div>

      {/* Direct Workspace Action Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Link
          href="/tax"
          className="group rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
        >
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 dark:text-gray-100">Tax Management</h3>
            <ExternalLink size={16} className="text-gray-400 group-hover:text-blue-600" />
          </div>
          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
            Log tax payments, file returns, calculate monthly VAT declarations, and view tax settlement history.
          </p>
          <span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-blue-600 dark:text-blue-400">
            Open Tax Manager <ChevronRight size={14} />
          </span>
        </Link>

        <Link
          href="/reports/tax"
          className="group rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
        >
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 dark:text-gray-100">Monthly Tax Reports</h3>
            <ExternalLink size={16} className="text-gray-400 group-hover:text-blue-600" />
          </div>
          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
            Generate monthly and annual VAT, TOT, and withholding tax summaries with drilldowns to individual invoices.
          </p>
          <span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-blue-600 dark:text-blue-400">
            View Tax Reports <ChevronRight size={14} />
          </span>
        </Link>

        <Link
          href="/tax/rules"
          className="group rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
        >
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 dark:text-gray-100">Tax Rules & Rates</h3>
            <ExternalLink size={16} className="text-gray-400 group-hover:text-blue-600" />
          </div>
          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
            Configure ERCA standard VAT (15%), TOT rates (2%/10%), withholding exemptions, and category taxability.
          </p>
          <span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-blue-600 dark:text-blue-400">
            Manage Rules <ChevronRight size={14} />
          </span>
        </Link>

        <Link
          href="/compliance"
          className="group rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
        >
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 dark:text-gray-100">AI Compliance Center</h3>
            <ExternalLink size={16} className="text-gray-400 group-hover:text-blue-600" />
          </div>
          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
            Audit logs, automated compliance health checks, statutory filing checklists, and conversational AI audit analysis.
          </p>
          <span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-blue-600 dark:text-blue-400">
            Open Compliance <ChevronRight size={14} />
          </span>
        </Link>
      </div>

      {/* Statutory Calendar & Requirements */}
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-gray-900 dark:text-gray-100">
          <Calendar size={16} className="text-blue-600 dark:text-blue-400" />
          Ethiopian Revenue and Customs Authority (ERCA) Filing Deadlines
        </h3>
        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="rounded-lg border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50">
            <p className="text-xs font-semibold uppercase text-gray-500">Value Added Tax (VAT)</p>
            <p className="mt-1 text-sm font-bold text-gray-900 dark:text-gray-100">30th of Every Month</p>
            <p className="mt-1 text-xs text-gray-500">
              Proclamation No. 285/2002. Reconcile sales invoices with purchase input VAT declarations.
            </p>
          </div>
          <div className="rounded-lg border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50">
            <p className="text-xs font-semibold uppercase text-gray-500">Employment Income Tax & Pension</p>
            <p className="mt-1 text-sm font-bold text-gray-900 dark:text-gray-100">30th of Every Month</p>
            <p className="mt-1 text-xs text-gray-500">
              Proclamations No. 979/2016 and 715/2011. Pay withholding and 7% + 11% pension contributions.
            </p>
          </div>
          <div className="rounded-lg border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50">
            <p className="text-xs font-semibold uppercase text-gray-500">Annual Business Profit Tax</p>
            <p className="mt-1 text-sm font-bold text-gray-900 dark:text-gray-100">Hamle 30 (August 6) / Sene 30</p>
            <p className="mt-1 text-xs text-gray-500">
              Category A & B taxpayers annual audited accounts filing based on Ethiopian fiscal year.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
