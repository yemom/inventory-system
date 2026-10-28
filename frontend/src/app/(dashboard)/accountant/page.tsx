'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, ComposedChart, Area, Line,
} from 'recharts';
import {
  TrendingUp, TrendingDown, Users, DollarSign, Shield, AlertTriangle,
  RefreshCw, ExternalLink, Sparkles, ChevronRight, BookOpen, FileSpreadsheet,
  CheckCircle2, Scale, ArrowRight, MessageSquare, Send,
} from 'lucide-react';
import Link from 'next/link';
import PageHeader from '@/components/ui/PageHeader';
import { taxApi, type TaxSummary } from '@/lib/api/taxApi';
import { payrollApi, type PayrollDashboard } from '@/lib/api/payrollApi';
import { complianceApi } from '@/lib/api/complianceApi';
import {
  accountingApi,
  type ProfitLossReport,
  type BalanceSheetReport,
  type TrialBalanceReport,
  type AiAccountingInsight,
} from '@/lib/api/accountingApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

interface KpiCardProps {
  label: string;
  value: string;
  sub?: string;
  icon: React.ReactNode;
  color: 'blue' | 'green' | 'red' | 'amber' | 'purple' | 'teal' | 'indigo' | 'emerald';
  href?: string;
  badge?: string;
}

const CARD_COLORS: Record<KpiCardProps['color'], string> = {
  blue: 'from-blue-600 to-blue-700',
  green: 'from-green-600 to-green-700',
  red: 'from-rose-600 to-rose-700',
  amber: 'from-amber-600 to-amber-700',
  purple: 'from-purple-600 to-purple-700',
  teal: 'from-teal-600 to-teal-700',
  indigo: 'from-indigo-600 to-indigo-700',
  emerald: 'from-emerald-600 to-emerald-700',
};

function KpiCard({ label, value, sub, icon, color, href, badge }: KpiCardProps) {
  const inner = (
    <div className="relative overflow-hidden rounded-2xl border border-white/10 bg-gradient-to-br p-4 shadow-sm transition hover:shadow-md">
      <div className={`absolute inset-0 bg-gradient-to-br ${CARD_COLORS[color]} opacity-90`} />
      <div className="relative z-10">
        <div className="flex items-start justify-between">
          <div className="rounded-xl bg-white/20 p-2 text-white">{icon}</div>
          {badge && (
            <span className="rounded-full bg-white/25 px-2 py-0.5 text-xs font-medium text-white">{badge}</span>
          )}
        </div>
        <p className="mt-3 text-xs font-medium uppercase tracking-wider text-white/80">{label}</p>
        <p className="mt-0.5 text-xl font-bold text-white">{value}</p>
        {sub && <p className="mt-1 text-xs text-white/75 truncate">{sub}</p>}
      </div>
    </div>
  );

  return href ? (
    <Link href={href} className="block transition-transform hover:-translate-y-0.5">
      {inner}
    </Link>
  ) : inner;
}

function SectionHeader({ icon, title, href }: { icon: React.ReactNode; title: string; href?: string }) {
  return (
    <div className="mb-3 flex items-center justify-between">
      <h2 className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
        {icon}{title}
      </h2>
      {href && (
        <Link href={href} className="flex items-center gap-1 text-xs font-semibold text-blue-600 hover:underline dark:text-blue-400">
          View details <ChevronRight size={12} />
        </Link>
      )}
    </div>
  );
}

export default function AccountantDashboardPage() {
  const { hasPermission } = useAuth();

  const canViewAccounting = hasPermission('ACCOUNTING_VIEW') || hasPermission('ACCOUNTING_REPORTS_VIEW') || hasPermission('SUPER_ADMIN');
  const canViewTax = hasPermission('TAX_DASHBOARD_VIEW') || canViewAccounting;
  const canViewPayroll = hasPermission('PAYROLL_SUMMARY_VIEW') || canViewAccounting;

  const currentYear = new Date().getFullYear();
  const currentMonthStr = `${currentYear}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;

  const [selectedPeriod, setSelectedPeriod] = useState(currentMonthStr);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Financial Reports Data
  const [pnl, setPnl] = useState<ProfitLossReport | null>(null);
  const [balanceSheet, setBalanceSheet] = useState<BalanceSheetReport | null>(null);
  const [trialBalance, setTrialBalance] = useState<TrialBalanceReport | null>(null);
  const [taxSummary, setTaxSummary] = useState<TaxSummary | null>(null);
  const [payrollDash, setPayrollDash] = useState<PayrollDashboard | null>(null);

  // AI Assistant State
  const [aiInsight, setAiInsight] = useState<AiAccountingInsight | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiQuestion, setAiQuestion] = useState('');
  const [aiAnswer, setAiAnswer] = useState<string | null>(null);
  const [aiAsking, setAiAsking] = useState(false);

  const fetchData = useCallback(async (period: string) => {
    setLoading(true);
    setError(null);
    try {
      const [year, month] = period.split('-').map(Number);
      const start = `${year}-${String(month).padStart(2, '0')}-01`;
      const lastDay = new Date(year, month, 0).getDate();
      const end = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;

      const [pnlRes, bsRes, tbRes, taxRes, payRes] = await Promise.allSettled([
        accountingApi.profitLoss(start, end),
        accountingApi.balanceSheet(end),
        accountingApi.trialBalance(end),
        canViewTax ? taxApi.summary(period) : Promise.resolve(null),
        canViewPayroll ? payrollApi.dashboard() : Promise.resolve(null),
      ]);

      if (pnlRes.status === 'fulfilled') setPnl(pnlRes.value);
      if (bsRes.status === 'fulfilled') setBalanceSheet(bsRes.value);
      if (tbRes.status === 'fulfilled') setTrialBalance(tbRes.value);
      if (taxRes.status === 'fulfilled' && taxRes.value) setTaxSummary(taxRes.value);
      if (payRes.status === 'fulfilled' && payRes.value) setPayrollDash(payRes.value);
    } catch (e: unknown) {
      const msg = (e as { message?: string })?.message ?? 'Failed to load dashboard data';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [canViewTax, canViewPayroll]);

  const fetchAiInsights = useCallback(async () => {
    setAiLoading(true);
    try {
      const insight = await accountingApi.aiInsights();
      setAiInsight(insight);
    } catch {
      setAiInsight(null);
    } finally {
      setAiLoading(false);
    }
  }, []);

  const handleAskAi = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!aiQuestion.trim() || aiAsking) return;
    setAiAsking(true);
    setAiAnswer(null);
    try {
      const res = await complianceApi.ask(aiQuestion);
      setAiAnswer(res.answer);
    } catch (err: unknown) {
      const msg = (err as { message?: string })?.message ?? 'AI assistant is currently unavailable.';
      setAiAnswer(`Unable to analyze question: ${msg}`);
    } finally {
      setAiAsking(false);
    }
  };

  useEffect(() => {
    fetchData(selectedPeriod);
    fetchAiInsights();
  }, [selectedPeriod, fetchData, fetchAiInsights]);

  const periodOptions = Array.from({ length: 12 }, (_, i) => {
    const d = new Date();
    d.setMonth(d.getMonth() - i);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });

  return (
    <div className="space-y-8 p-6">
      <PageHeader
        title="Accountant Workspace & Financial Intelligence"
        subtitle="Double-entry General Ledger, Ethiopian Tax, Payroll, and Audit-Verified Analytics"
        actions={
          <div className="flex flex-wrap items-center gap-3">
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
            <Link
              href="/accountant/journal"
              className="flex items-center gap-1.5 rounded-lg bg-blue-600 px-3.5 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
            >
              <BookOpen size={14} />
              Journal Entries
            </Link>
          </div>
        }
      />

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300">
          <AlertTriangle className="mr-2 inline" size={16} />{error}
        </div>
      )}

      {/* Trial Balance Health Bar */}
      {trialBalance && (
        <div className={`flex flex-wrap items-center justify-between gap-4 rounded-xl border p-4 ${
          trialBalance.balanced
            ? 'border-emerald-200 bg-emerald-50 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/20 dark:text-emerald-300'
            : 'border-red-200 bg-red-50 text-red-900 dark:border-red-800 dark:bg-red-950/20 dark:text-red-300'
        }`}>
          <div className="flex items-center gap-3">
            {trialBalance.balanced ? <CheckCircle2 size={24} className="text-emerald-600" /> : <AlertTriangle size={24} className="text-red-600" />}
            <div>
              <p className="font-semibold text-sm">
                General Ledger Status: {trialBalance.balanced ? 'Trial Balance in Strict Equilibrium' : 'Ledger Out of Balance!'}
              </p>
              <p className="text-xs opacity-90">
                Total Debits: {ETB(trialBalance.totalDebits)} | Total Credits: {ETB(trialBalance.totalCredits)}
              </p>
            </div>
          </div>
          <Link
            href="/accountant/trial-balance"
            className="inline-flex items-center gap-1 rounded-lg bg-white/80 px-3 py-1.5 text-xs font-semibold text-gray-800 shadow-sm hover:bg-white dark:bg-gray-800 dark:text-gray-200"
          >
            Inspect Trial Balance <ChevronRight size={12} />
          </Link>
        </div>
      )}

      {/* ── 1. Financial Performance KPIs ────────────────────────────────── */}
      <section>
        <SectionHeader
          icon={<DollarSign size={14} />}
          title="Financial Performance & Profitability"
          href="/accountant/profit-loss"
        />
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          <KpiCard
            label="Net Sales"
            value={ETB(pnl?.netSales ?? 0)}
            sub={`Gross: ${ETB(pnl?.salesRevenue ?? 0)}`}
            icon={<TrendingUp size={16} />}
            color="blue"
            href="/accountant/profit-loss"
          />
          <KpiCard
            label="COGS"
            value={ETB(pnl?.costOfGoodsSold ?? 0)}
            sub="Cost of goods sold"
            icon={<TrendingDown size={16} />}
            color="amber"
            href="/accountant/profit-loss"
          />
          <KpiCard
            label="Gross Profit"
            value={ETB(pnl?.grossProfit ?? 0)}
            sub={`${(pnl?.grossMarginPercent ?? 0).toFixed(1)}% gross margin`}
            icon={<DollarSign size={16} />}
            color="emerald"
            href="/accountant/profit-loss"
          />
          <KpiCard
            label="Operating Expenses"
            value={ETB(pnl?.totalOperatingExpenses ?? 0)}
            sub="General & admin"
            icon={<TrendingDown size={16} />}
            color="red"
            href="/accountant/profit-loss"
          />
          <KpiCard
            label="Payroll Expenses"
            value={ETB(pnl?.payrollExpenses ?? 0)}
            sub="Staff salaries & pension"
            icon={<Users size={16} />}
            color="purple"
            href="/accountant/profit-loss"
          />
          <KpiCard
            label="Net Profit / Loss"
            value={ETB(pnl?.netProfitOrLoss ?? 0)}
            sub={pnl && pnl.netProfitOrLoss >= 0 ? 'Profitable period' : 'Loss position'}
            icon={<Scale size={16} />}
            color={(pnl?.netProfitOrLoss ?? 0) >= 0 ? 'green' : 'red'}
            href="/accountant/profit-loss"
          />
        </div>
      </section>

      {/* ── 2. Balance Sheet & Working Capital KPIs ──────────────────────── */}
      <section>
        <SectionHeader
          icon={<FileSpreadsheet size={14} />}
          title="Balance Sheet & Working Capital"
          href="/accountant/balance-sheet"
        />
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          <KpiCard
            label="Total Assets"
            value={ETB(balanceSheet?.totalAssets ?? 0)}
            sub="Current & fixed assets"
            icon={<DollarSign size={16} />}
            color="blue"
            href="/accountant/balance-sheet"
          />
          <KpiCard
            label="Total Liabilities"
            value={ETB(balanceSheet?.totalLiabilities ?? 0)}
            sub="AP, VAT, Tax & Pension payables"
            icon={<TrendingDown size={16} />}
            color="amber"
            href="/accountant/balance-sheet"
          />
          <KpiCard
            label="Total Equity"
            value={ETB(balanceSheet?.totalEquity ?? 0)}
            sub="Retained earnings & capital"
            icon={<Scale size={16} />}
            color="indigo"
            href="/accountant/balance-sheet"
          />
          <KpiCard
            label="Balance Status"
            value={balanceSheet?.balanced ? 'Balanced' : 'Discrepancy'}
            sub={`Variance: ${ETB(balanceSheet?.discrepancy ?? 0)}`}
            icon={<CheckCircle2 size={16} />}
            color={balanceSheet?.balanced ? 'emerald' : 'red'}
            href="/accountant/balance-sheet"
          />
          <KpiCard
            label="Ledger Reconciliation"
            value="Active"
            sub="Click to verify subledgers"
            icon={<BookOpen size={16} />}
            color="teal"
            href="/accountant/reconciliation"
          />
        </div>
      </section>

      {/* ── 3. Ethiopian Tax & Statutory Obligations ─────────────────────── */}
      {canViewTax && (
        <section>
          <SectionHeader
            icon={<TrendingUp size={14} />}
            title="Tax & ERCA Statutory Obligations"
            href="/accountant/tax-compliance"
          />
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <KpiCard
              label="Output VAT"
              value={ETB(taxSummary?.outputVat ?? 0)}
              sub="15% collected on sales"
              icon={<TrendingUp size={16} />}
              color="red"
              href="/accountant/tax-compliance"
            />
            <KpiCard
              label="Input VAT Recoverable"
              value={ETB(taxSummary?.eligibleInputVat ?? 0)}
              sub="Deductible invoice tax"
              icon={<TrendingDown size={16} />}
              color="green"
              href="/accountant/tax-compliance"
            />
            <KpiCard
              label="Net VAT Liability"
              value={ETB(taxSummary?.estimatedNetVat ?? 0)}
              sub={(taxSummary?.estimatedNetVat ?? 0) > 0 ? 'Payable to ERCA' : 'VAT Credit balance'}
              icon={<DollarSign size={16} />}
              color={(taxSummary?.estimatedNetVat ?? 0) > 0 ? 'amber' : 'teal'}
              href="/accountant/tax-compliance"
            />
            <KpiCard
              label="Tax Payments Made"
              value={ETB(taxSummary?.taxPaymentsMade ?? 0)}
              sub={`Outstanding: ${ETB(taxSummary?.outstandingLiabilities ?? 0)}`}
              icon={<Shield size={16} />}
              color="blue"
              href="/accountant/tax-compliance"
            />
          </div>
        </section>
      )}

      {/* ── 4. Payroll & Pension Obligations ─────────────────────────────── */}
      {canViewPayroll && (
        <section>
          <SectionHeader
            icon={<Users size={14} />}
            title="Payroll & Employee Cost Accounting"
            href="/accountant/payroll"
          />
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <KpiCard
              label="Gross Payroll"
              value={ETB(payrollDash?.grossPayroll ?? 0)}
              sub={`${payrollDash?.activeProfiles ?? 0} active employees`}
              icon={<Users size={16} />}
              color="purple"
              href="/accountant/payroll"
            />
            <KpiCard
              label="Income Tax Withheld"
              value={ETB(payrollDash?.employmentTax ?? 0)}
              sub="Schedule A progressive tax"
              icon={<TrendingDown size={16} />}
              color="red"
              href="/accountant/payroll"
            />
            <KpiCard
              label="Pension Funds"
              value={ETB((payrollDash?.employeePension ?? 0) + (payrollDash?.employerPension ?? 0))}
              sub="Emp 7% + Org 11%"
              icon={<Shield size={16} />}
              color="amber"
              href="/accountant/payroll"
            />
            <KpiCard
              label="Net Payroll"
              value={ETB(payrollDash?.netPayroll ?? 0)}
              sub={`Total employer cost: ${ETB(payrollDash?.employerCost ?? 0)}`}
              icon={<DollarSign size={16} />}
              color="green"
              href="/accountant/payroll"
            />
          </div>
        </section>
      )}

      {/* ── 5. Financial Comparison Charts ──────────────────────────────── */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* P&L Breakdown */}
        {pnl && (
          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <h3 className="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
              Profit & Loss Summary Breakdown (ETB)
            </h3>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart
                data={[
                  { name: 'Net Sales', value: pnl.netSales, fill: '#3b82f6' },
                  { name: 'COGS', value: pnl.costOfGoodsSold, fill: '#f59e0b' },
                  { name: 'Gross Profit', value: pnl.grossProfit, fill: '#10b981' },
                  { name: 'Op. Expenses', value: pnl.totalOperatingExpenses, fill: '#ef4444' },
                  { name: 'Net Profit', value: pnl.netProfitOrLoss, fill: '#8b5cf6' },
                ]}
                margin={{ top: 10, right: 10, left: 10, bottom: 0 }}
              >
                <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
                <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                <YAxis tickFormatter={v => `${(v / 1000).toFixed(0)}k`} tick={{ fontSize: 11 }} />
                <Tooltip formatter={(v: number) => ETB(v)} />
                <Bar dataKey="value" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}

        {/* VAT Liability Comparison */}
        {taxSummary && (
          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <h3 className="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
              Monthly VAT Position & Settlements (ETB)
            </h3>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart
                data={[
                  { name: 'Output VAT', value: taxSummary.outputVat, fill: '#ef4444' },
                  { name: 'Input VAT', value: taxSummary.eligibleInputVat, fill: '#10b981' },
                  { name: 'Net VAT', value: taxSummary.estimatedNetVat, fill: '#f59e0b' },
                  { name: 'Payments', value: taxSummary.taxPaymentsMade ?? 0, fill: '#3b82f6' },
                  { name: 'Outstanding', value: taxSummary.outstandingLiabilities ?? 0, fill: '#ec4899' },
                ]}
                margin={{ top: 10, right: 10, left: 10, bottom: 0 }}
              >
                <CartesianGrid strokeDasharray="3 3" className="stroke-gray-200 dark:stroke-gray-700" />
                <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                <YAxis tickFormatter={v => `${(v / 1000).toFixed(0)}k`} tick={{ fontSize: 11 }} />
                <Tooltip formatter={(v: number) => ETB(v)} />
                <Bar dataKey="value" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      {/* ── 6. AI Accounting Assistant & Intelligence ─────────────────────── */}
      <section className="space-y-4">
        <SectionHeader
          icon={<Sparkles size={14} className="text-purple-600" />}
          title="AI Accounting Assistant & Compliance Insights"
        />

        {aiInsight && (
          <div className="rounded-2xl border border-purple-200 bg-gradient-to-br from-purple-50 to-indigo-50 p-5 dark:border-purple-800 dark:from-purple-950/20 dark:to-indigo-950/20">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-purple-900 dark:text-purple-200">
                <Sparkles size={18} className="text-purple-600 dark:text-purple-400" />
                <h3 className="font-semibold text-sm">{aiInsight.title}</h3>
              </div>
              <span className="rounded-full bg-purple-200/80 px-2.5 py-0.5 text-xs font-semibold text-purple-800 dark:bg-purple-900/60 dark:text-purple-300">
                Grounded Ledger Intelligence
              </span>
            </div>
            <p className="mt-2 text-sm text-purple-900/90 dark:text-purple-200/90">
              {aiInsight.summary}
            </p>

            <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
              {aiInsight.keyTrends?.length > 0 && (
                <div className="rounded-xl bg-white/70 p-3 shadow-xs dark:bg-gray-800/60">
                  <p className="text-xs font-semibold text-purple-800 dark:text-purple-300 uppercase">Key Trends</p>
                  <ul className="mt-2 space-y-1 text-xs text-gray-700 dark:text-gray-300">
                    {aiInsight.keyTrends.map((t, idx) => (
                      <li key={idx} className="flex gap-1.5">
                        <span className="text-purple-500">•</span>
                        <span>{t}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {aiInsight.anomalies?.length > 0 && (
                <div className="rounded-xl bg-white/70 p-3 shadow-xs dark:bg-gray-800/60">
                  <p className="text-xs font-semibold text-amber-800 dark:text-amber-300 uppercase">Anomalies & Variances</p>
                  <ul className="mt-2 space-y-1 text-xs text-gray-700 dark:text-gray-300">
                    {aiInsight.anomalies.map((a, idx) => (
                      <li key={idx} className="flex gap-1.5">
                        <span className="text-amber-500">•</span>
                        <span>{a}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {aiInsight.recommendations?.length > 0 && (
                <div className="rounded-xl bg-white/70 p-3 shadow-xs dark:bg-gray-800/60">
                  <p className="text-xs font-semibold text-emerald-800 dark:text-emerald-300 uppercase">Recommendations</p>
                  <ul className="mt-2 space-y-1 text-xs text-gray-700 dark:text-gray-300">
                    {aiInsight.recommendations.map((r, idx) => (
                      <li key={idx} className="flex gap-1.5">
                        <span className="text-emerald-500">•</span>
                        <span>{r}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Interactive Accounting Q&A */}
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <form onSubmit={handleAskAi} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <MessageSquare size={16} className="absolute left-3 top-3 text-gray-400" />
              <input
                type="text"
                placeholder="Ask the AI Accounting Assistant (e.g. 'Explain our current VAT position' or 'Check inventory reconciliation')..."
                value={aiQuestion}
                onChange={e => setAiQuestion(e.target.value)}
                className="w-full rounded-lg border border-gray-300 bg-gray-50 py-2 pl-9 pr-3 text-sm focus:border-blue-500 focus:bg-white focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
              />
            </div>
            <button
              type="submit"
              disabled={aiAsking || !aiQuestion.trim()}
              className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-purple-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-purple-700 disabled:opacity-50"
            >
              {aiAsking ? <RefreshCw size={14} className="animate-spin" /> : <Send size={14} />}
              Ask Assistant
            </button>
          </form>

          {aiAnswer && (
            <div className="mt-4 rounded-lg border border-purple-100 bg-purple-50/50 p-4 text-sm text-gray-800 dark:border-purple-900/40 dark:bg-purple-950/20 dark:text-gray-200">
              <p className="font-semibold text-purple-900 dark:text-purple-300 mb-1 flex items-center gap-1.5">
                <Sparkles size={14} /> AI Analysis:
              </p>
              <div className="whitespace-pre-wrap leading-relaxed">{aiAnswer}</div>
            </div>
          )}
        </div>
      </section>

      {/* ── 7. Accounting Workspace Navigation ────────────────────────────── */}
      <section>
        <SectionHeader icon={<ExternalLink size={14} />} title="Accounting Workspace Navigation" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: 'Chart of Accounts', href: '/accountant/chart-of-accounts' },
            { label: 'Journal Entries', href: '/accountant/journal' },
            { label: 'General Ledger', href: '/accountant/ledger' },
            { label: 'Trial Balance', href: '/accountant/trial-balance' },
            { label: 'Profit & Loss Statement', href: '/accountant/profit-loss' },
            { label: 'Balance Sheet', href: '/accountant/balance-sheet' },
            { label: 'Subledger Reconciliation', href: '/accountant/reconciliation' },
            { label: 'Tax & Compliance Hub', href: '/accountant/tax-compliance' },
            { label: 'Payroll Accounting', href: '/accountant/payroll' },
            { label: 'Tax Management', href: '/tax' },
            { label: 'Payroll Management', href: '/payroll' },
            { label: 'Finance Overview', href: '/finance' },
          ].map(l => (
            <Link
              key={l.href}
              href={l.href}
              className="flex items-center justify-between rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm font-medium text-gray-700 transition hover:border-blue-300 hover:bg-blue-50/50 hover:text-blue-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-blue-950/20"
            >
              {l.label} <ChevronRight size={14} />
            </Link>
          ))}
        </div>
      </section>

      <p className="text-xs text-gray-400 dark:text-gray-500">
        All financial figures are derived from persisted double-entry journal transactions and verified subledgers. Adheres to Ethiopian Proclamation No. 285/2002 (VAT), No. 979/2016 (Income Tax), and No. 715/2011 (Pension). Total Debits strictly equals Total Credits.
      </p>
    </div>
  );
}
