'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  Users, DollarSign, TrendingDown, Shield, AlertTriangle,
  RefreshCw, ExternalLink, ChevronRight, FileText, CheckCircle2,
} from 'lucide-react';
import Link from 'next/link';
import PageHeader from '@/components/ui/PageHeader';
import { payrollApi, type PayrollDashboard, type PayrollPeriod } from '@/lib/api/payrollApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

export default function AccountantPayrollPage() {
  const { hasPermission } = useAuth();
  const canView = hasPermission('PAYROLL_SUMMARY_VIEW') || hasPermission('ACCOUNTING_VIEW') || hasPermission('SUPER_ADMIN');

  const [dashboard, setDashboard] = useState<PayrollDashboard | null>(null);
  const [periods, setPeriods] = useState<PayrollPeriod[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [dashRes, periodsRes] = await Promise.allSettled([
        payrollApi.dashboard(),
        payrollApi.periods(),
      ]);

      if (dashRes.status === 'fulfilled') setDashboard(dashRes.value);
      else setError(`Payroll summary unavailable: ${(dashRes.reason as Error)?.message ?? 'request failed'}`);
      if (periodsRes.status === 'fulfilled') setPeriods(periodsRes.value);
      else setError(`Payroll periods unavailable: ${(periodsRes.reason as Error)?.message ?? 'request failed'}`);
    } catch (e: unknown) {
      const msg = (e as { message?: string })?.message ?? 'Failed to load payroll data';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canView) {
      fetchData();
    }
  }, [canView, fetchData]);

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Shield className="mx-auto mb-2 text-amber-500" size={40} />
        <p className="text-lg font-semibold">Access Restricted</p>
        <p className="mt-1 text-sm">You need PAYROLL_SUMMARY_VIEW or ACCOUNTING_VIEW permission to access this page.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Payroll & Employee Cost Accounting"
        subtitle="Gross compensation, employment tax withholding, private & public pension obligations"
        actions={
          <div className="flex items-center gap-3">
            <button
              onClick={fetchData}
              disabled={loading}
              className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700"
            >
              <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
              Refresh
            </button>
            <Link
              href="/payroll"
              className="flex items-center gap-1.5 rounded-lg bg-blue-600 px-3.5 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
            >
              <Users size={14} />
              Manage Payroll
            </Link>
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
            <span className="text-xs font-medium text-gray-500">Gross Payroll</span>
            <span className="rounded-full bg-purple-50 p-2 text-purple-600 dark:bg-purple-950/40"><Users size={16} /></span>
          </div>
          <p className="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">{ETB(dashboard?.grossPayroll ?? 0)}</p>
          <p className="mt-1 text-xs text-gray-500">{dashboard?.activeProfiles ?? 0} active employees</p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">Employment Tax Withheld</span>
            <span className="rounded-full bg-red-50 p-2 text-red-600 dark:bg-red-950/40"><TrendingDown size={16} /></span>
          </div>
          <p className="mt-2 text-2xl font-bold text-red-600 dark:text-red-400">{ETB(dashboard?.employmentTax ?? 0)}</p>
          <p className="mt-1 text-xs text-gray-500">Schedule A progressive tax brackets</p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">Total Pension Fund</span>
            <span className="rounded-full bg-amber-50 p-2 text-amber-600 dark:bg-amber-950/40"><Shield size={16} /></span>
          </div>
          <p className="mt-2 text-2xl font-bold text-amber-600 dark:text-amber-400">
            {ETB((dashboard?.employeePension ?? 0) + (dashboard?.employerPension ?? 0))}
          </p>
          <p className="mt-1 text-xs text-gray-500">
            Emp: {ETB(dashboard?.employeePension ?? 0)} (7%) + Org: {ETB(dashboard?.employerPension ?? 0)} (11%)
          </p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">Net Take-Home Pay</span>
            <span className="rounded-full bg-emerald-50 p-2 text-emerald-600 dark:bg-emerald-950/40"><DollarSign size={16} /></span>
          </div>
          <p className="mt-2 text-2xl font-bold text-emerald-600 dark:text-emerald-400">{ETB(dashboard?.netPayroll ?? 0)}</p>
          <p className="mt-1 text-xs text-gray-500">Total employer cost: {ETB(dashboard?.employerCost ?? 0)}</p>
        </div>
      </div>

      {/* Direct Workspace Quick Links */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Link
          href="/payroll"
          className="group rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
        >
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 dark:text-gray-100">Payroll Run & Calculation</h3>
            <ExternalLink size={16} className="text-gray-400 group-hover:text-blue-600" />
          </div>
          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
            Process monthly payroll runs, compute net pay, verify statutory deductions, and mark disbursement.
          </p>
          <span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-blue-600 dark:text-blue-400">
            Go to Payroll Management <ChevronRight size={14} />
          </span>
        </Link>

        <Link
          href="/payroll/profiles"
          className="group rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
        >
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 dark:text-gray-100">Employee Profiles & Compensation</h3>
            <ExternalLink size={16} className="text-gray-400 group-hover:text-blue-600" />
          </div>
          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
            View staff basic salaries, taxable/non-taxable allowances, pension participation, and bank payout details.
          </p>
          <span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-blue-600 dark:text-blue-400">
            View Staff Profiles <ChevronRight size={14} />
          </span>
        </Link>

        <Link
          href="/reports/payroll"
          className="group rounded-2xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-blue-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
        >
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 group-hover:text-blue-600 dark:text-gray-100">Payroll Reports & Analytics</h3>
            <ExternalLink size={16} className="text-gray-400 group-hover:text-blue-600" />
          </div>
          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
            Detailed monthly payroll breakdowns, ERCA withholding reports, pension declaration schedules, and CSV exports.
          </p>
          <span className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-blue-600 dark:text-blue-400">
            View Payroll Reports <ChevronRight size={14} />
          </span>
        </Link>
      </div>

      {/* Payroll Periods Table */}
      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div className="border-b border-gray-200 px-6 py-4 dark:border-gray-700">
          <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">
            Processed Payroll Periods ({periods.length})
          </h2>
          <p className="mt-0.5 text-xs text-gray-500">
            Historical payroll periods and GL sync status.
          </p>
        </div>

        {periods.length === 0 ? (
          <div className="p-8 text-center text-sm text-gray-500">
            No payroll periods recorded. Visit <Link href="/payroll" className="font-semibold text-blue-600 hover:underline">Payroll Management</Link> to generate a period.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase tracking-wider text-gray-500 dark:border-gray-700 dark:bg-gray-900/50 dark:text-gray-400">
                <tr>
                  <th className="px-6 py-3">Period</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3 text-right">Gross Pay</th>
                  <th className="px-6 py-3 text-right">Income Tax</th>
                  <th className="px-6 py-3 text-right">Pension (7%+11%)</th>
                  <th className="px-6 py-3 text-right">Net Pay</th>
                  <th className="px-6 py-3 text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                {periods.map((p) => (
                  <tr key={p.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                    <td className="whitespace-nowrap px-6 py-3.5 font-medium text-gray-900 dark:text-gray-200">
                      {`${p.year}-${String(p.month).padStart(2, '0')}`}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5">
                      <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${
                        p.status === 'PAID'
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300'
                          : 'bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300'
                      }`}>
                        {p.status === 'PAID' && <CheckCircle2 size={12} />}
                        {p.status}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-right font-mono text-gray-900 dark:text-gray-100">
                      {ETB(p.grossPayroll)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-right font-mono text-red-600 dark:text-red-400">
                      {ETB(p.employmentTax)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-right font-mono text-amber-600 dark:text-amber-400">
                      {ETB(p.employeePension + p.employerPension)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-right font-mono font-medium text-emerald-600 dark:text-emerald-400">
                      {ETB(p.netPayroll)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-center">
                      <Link
                        href="/payroll"
                        className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:underline dark:text-blue-400"
                      >
                        <FileText size={12} /> Details
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
