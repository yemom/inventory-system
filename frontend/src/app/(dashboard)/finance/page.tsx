'use client';

import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import Link from 'next/link';
import {
  Calculator,
  FileUp,
  Landmark,
  ReceiptText,
  Users,
  WalletCards,
  TrendingUp,
  TrendingDown,
  DollarSign,
  Package,
  ArrowUpRight,
  Calendar,
  Layers,
  Scale,
  Percent,
  CheckCircle,
  Clock,
  ExternalLink,
  BookOpen,
  ShieldCheck
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  LineChart,
  Line,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend
} from 'recharts';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import ErrorState from '@/components/ui/ErrorState';
import { financeApi, TaxPayrollFinanceDTO } from '@/lib/api/financeApi';
import { taxApi } from '@/lib/api/taxApi';
import { accountingApi } from '@/lib/api/accountingApi';

const etb = (v?: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', maximumFractionDigits: 2 }).format(v || 0);

const etbCompact = (v?: number) => {
  if (!v || v === 0) return '0';
  if (Math.abs(v) >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M`;
  if (Math.abs(v) >= 1_000) return `${(v / 1_000).toFixed(1)}k`;
  return v.toFixed(0);
};

export default function FinanceOverviewPage() {
  const [selectedPeriod, setSelectedPeriod] = useState<string>('');

  // 1. Fetch Tax, Payroll & Business Intelligence
  const financeQuery = useQuery({
    queryKey: ['finance-intelligence', selectedPeriod],
    queryFn: () => financeApi.getIntelligence(selectedPeriod || undefined),
  });

  // 2. Fetch Accounting Ledger & Reconciliation Status
  const tbQuery = useQuery({
    queryKey: ['finance-accounting-tb'],
    queryFn: () => accountingApi.trialBalance(),
  });
  const reconQuery = useQuery({
    queryKey: ['finance-accounting-recon'],
    queryFn: () => accountingApi.reconciliation(),
  });

  // Receipt Tax Calculator State (Preserved from original page)
  const [amount, setAmount] = useState('');
  const [treatment, setTreatment] = useState('STANDARD');
  const [inclusive, setInclusive] = useState(false);
  const [receipt, setReceipt] = useState<File | null>(null);
  const calculate = useMutation({ mutationFn: taxApi.calculateReceipt });
  const choose = (event: React.ChangeEvent<HTMLInputElement>) => setReceipt(event.target.files?.[0] ?? null);

  if (financeQuery.error) {
    return <ErrorState error={financeQuery.error} />;
  }

  const data = financeQuery.data;
  const tax = data?.taxSummary;
  const payroll = data?.payrollSummary;
  const inventory = data?.inventorySummary;
  const charts = data?.charts;

  return (
    <div className="space-y-8 pb-16">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <PageHeader
          title="Finance & Intelligence Dashboard"
          subtitle="Real-time financial performance, Ethiopian statutory tax obligations, verified payroll reconciliations, and inventory capital metrics."
        />
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 bg-white dark:bg-gray-800 border rounded-lg px-3 py-1.5 text-sm shadow-sm">
            <Calendar className="w-4 h-4 text-gray-500" />
            <input
              type="month"
              className="bg-transparent border-none text-sm focus:outline-none cursor-pointer"
              value={selectedPeriod}
              onChange={(e) => setSelectedPeriod(e.target.value)}
              placeholder="All Periods"
            />
            {selectedPeriod && (
              <button
                onClick={() => setSelectedPeriod('')}
                className="text-xs text-gray-400 hover:text-gray-600 ml-1"
              >
                Reset
              </button>
            )}
          </div>
        </div>
      </div>

      {!data ? (
        <div className="p-12 text-center text-sm text-gray-500">
          Loading financial intelligence and reconciling ledger entries...
        </div>
      ) : (
        <>
          {/* Top High-level Financial KPIs */}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              title="Net Sales (Revenue)"
              value={etb(inventory?.netSales)}
              icon={<TrendingUp size={20} />}
              color="emerald"
              changeLabel={`Gross Profit: ${etb(inventory?.grossProfit)}`}
            />
            <StatCard
              title="Net Profit (Period)"
              value={etb(inventory?.netProfit)}
              icon={<DollarSign size={20} />}
              color="blue"
              changeLabel={`Accrual Basis (${inventory?.accountingBasis || 'Actual'})`}
            />
            <StatCard
              title="Net VAT Liability"
              value={etb(tax?.netVatPayableOrCredit)}
              icon={<ReceiptText size={20} />}
              color="amber"
              changeLabel={`Output: ${etb(tax?.outputVat)}`}
            />
            <StatCard
              title="Total Employer Payroll Cost"
              value={etb(payroll?.totalEmployerPayrollCost)}
              icon={<Users size={20} />}
              color="purple"
              changeLabel={payroll?.hasPayrollData ? `Gross: ${etb(payroll?.grossPayroll)}` : 'No payroll data recorded'}
            />
          </div>

          {/* DEDICATED SECTION: Accounting Intelligence & General Ledger */}
          <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-gray-100 pb-4 dark:border-gray-700">
              <div>
                <h2 className="text-lg font-bold text-gray-900 dark:text-white flex items-center gap-2">
                  <BookOpen className="w-5 h-5 text-indigo-600" />
                  Double-Entry Accounting &amp; Ledger Intelligence
                </h2>
                <p className="text-xs text-gray-500 mt-0.5">
                  Audited Chart of Accounts, Journal Invariants, General Ledger balance, and subledger reconciliation.
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Link
                  href="/accountant"
                  className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-700 shadow-xs"
                >
                  <BookOpen className="w-3.5 h-3.5" />
                  Accountant Workspace
                </Link>
                <Link
                  href="/accountant/trial-balance"
                  className="inline-flex items-center gap-1 text-xs font-semibold text-gray-700 dark:text-gray-300 hover:text-indigo-600 bg-gray-50 dark:bg-gray-700 px-3 py-1.5 rounded-lg border border-gray-200 dark:border-gray-600"
                >
                  Trial Balance &rarr;
                </Link>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-xl border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50">
                <span className="text-xs font-medium text-gray-500">Trial Balance Status</span>
                <div className="mt-1 flex items-center gap-2">
                  {tbQuery.data?.balanced ? (
                    <span className="flex items-center gap-1 text-sm font-bold text-emerald-600 dark:text-emerald-400">
                      <ShieldCheck className="w-4 h-4" /> Strict Equilibrium
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-sm font-bold text-amber-600 dark:text-amber-400">
                      <Clock className="w-4 h-4" /> Pending Validation
                    </span>
                  )}
                </div>
                <p className="mt-1 text-xs text-gray-500">
                  Total Debits = {etb(tbQuery.data?.totalDebits)}
                </p>
              </div>

              <div className="rounded-xl border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50">
                <span className="text-xs font-medium text-gray-500">Subledger Reconciliation</span>
                <div className="mt-1 flex items-center gap-2">
                  <span className={`text-sm font-bold ${(reconQuery.data?.discrepanciesCount ?? 0) === 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}`}>
                    {(reconQuery.data?.discrepanciesCount ?? 0) === 0 ? 'Fully Reconciled' : `${reconQuery.data?.discrepanciesCount} Discrepancies`}
                  </span>
                </div>
                <p className="mt-1 text-xs text-gray-500">
                  {reconQuery.data?.totalChecked ?? 0} items verified against GL
                </p>
              </div>

              <div className="rounded-xl border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50">
                <span className="text-xs font-medium text-gray-500">Standard Chart of Accounts</span>
                <p className="mt-1 text-sm font-bold text-gray-900 dark:text-gray-100">
                  {tbQuery.data?.rows?.length ?? 13} Active Accounts
                </p>
                <Link href="/accountant/chart-of-accounts" className="mt-1 block text-xs text-indigo-600 hover:underline dark:text-indigo-400">
                  View Chart of Accounts &rarr;
                </Link>
              </div>

              <div className="rounded-xl border border-gray-100 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50">
                <span className="text-xs font-medium text-gray-500">Financial Statements</span>
                <div className="mt-2 flex flex-wrap gap-2 text-xs">
                  <Link href="/accountant/profit-loss" className="font-semibold text-blue-600 hover:underline dark:text-blue-400">
                    P&amp;L
                  </Link>
                  <span className="text-gray-300 dark:text-gray-600">|</span>
                  <Link href="/accountant/balance-sheet" className="font-semibold text-blue-600 hover:underline dark:text-blue-400">
                    Balance Sheet
                  </Link>
                  <span className="text-gray-300 dark:text-gray-600">|</span>
                  <Link href="/accountant/journal" className="font-semibold text-blue-600 hover:underline dark:text-blue-400">
                    Journals
                  </Link>
                  <span className="text-gray-300 dark:text-gray-600">|</span>
                  <Link href="/accountant/ledger" className="font-semibold text-blue-600 hover:underline dark:text-blue-400">
                    Ledger
                  </Link>
                </div>
              </div>
            </div>
          </div>

          {/* DEDICATED SECTION: Tax & Payroll Intelligence */}
          <div className="space-y-6">
            <div className="border-b pb-3 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
              <div>
                <h2 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white flex items-center gap-2">
                  <Scale className="w-6 h-6 text-blue-600" />
                  Tax &amp; Payroll Intelligence
                </h2>
                <p className="text-xs text-gray-500 mt-0.5">
                  Reconciled breakdown of actual tax filings, progressive payroll withholdings, and inventory asset valuations.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Link
                  href="/tax"
                  className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700 bg-blue-50 dark:bg-blue-900/30 px-2.5 py-1.5 rounded-md border border-blue-200 dark:border-blue-800"
                >
                  Tax Dashboard <ExternalLink className="w-3.5 h-3.5" />
                </Link>
                <Link
                  href="/payroll"
                  className="inline-flex items-center gap-1 text-xs font-semibold text-purple-600 hover:text-purple-700 bg-purple-50 dark:bg-purple-900/30 px-2.5 py-1.5 rounded-md border border-purple-200 dark:border-purple-800"
                >
                  Payroll Dashboard <ExternalLink className="w-3.5 h-3.5" />
                </Link>
              </div>
            </div>

            {/* 3 Summary Cards */}
            <div className="grid gap-6 lg:grid-cols-3">
              {/* Card 1: Tax Summary */}
              <Card className="border-t-4 border-t-amber-500 shadow-sm">
                <CardHeader className="pb-3 border-b">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                      <ReceiptText className="w-4 h-4 text-amber-600" />
                      Tax Summary
                    </h3>
                    <Link href="/tax" className="text-xs text-amber-600 hover:underline">
                      Drill-down &rarr;
                    </Link>
                  </div>
                </CardHeader>
                <CardContent className="pt-4 space-y-3 text-sm">
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Taxable Sales</span>
                    <span className="font-semibold text-gray-900 dark:text-white">{etb(tax?.taxableSales)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Output VAT (15%)</span>
                    <span className="font-semibold text-blue-600">{etb(tax?.outputVat)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Recoverable Input VAT</span>
                    <span className="font-semibold text-emerald-600">-{etb(tax?.recoverableInputVat)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800 bg-amber-50/50 dark:bg-amber-950/20 px-2 rounded">
                    <span className="font-medium text-amber-900 dark:text-amber-200">Net VAT Payable</span>
                    <span className="font-bold text-amber-700 dark:text-amber-400">{etb(tax?.netVatPayableOrCredit)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Tax Payments Recorded</span>
                    <span className="font-semibold text-emerald-700">{etb(tax?.taxPaymentsMade)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Outstanding Tax Liabilities</span>
                    <span className="font-semibold text-red-600">{etb(tax?.outstandingTaxLiabilities)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1">
                    <span className="text-gray-500">Estimated Income Tax</span>
                    <span className="font-medium text-gray-700 dark:text-gray-300">{etb(tax?.estimatedIncomeTax)}</span>
                  </div>
                </CardContent>
              </Card>

              {/* Card 2: Payroll Summary */}
              <Card className="border-t-4 border-t-purple-500 shadow-sm">
                <CardHeader className="pb-3 border-b">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                      <Users className="w-4 h-4 text-purple-600" />
                      Payroll Summary
                    </h3>
                    <Link href="/payroll" className="text-xs text-purple-600 hover:underline">
                      Drill-down &rarr;
                    </Link>
                  </div>
                </CardHeader>
                <CardContent className="pt-4 space-y-3 text-sm">
                  {!payroll?.hasPayrollData ? (
                    <div className="py-8 text-center">
                      <Users className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                      <p className="text-xs text-gray-500">No payroll records have been processed yet.</p>
                      <Link
                        href="/payroll"
                        className="mt-3 inline-block text-xs text-purple-600 font-semibold hover:underline"
                      >
                        Configure Profiles &amp; Generate Period &rarr;
                      </Link>
                    </div>
                  ) : (
                    <>
                      <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                        <span className="text-gray-500">Gross Payroll</span>
                        <span className="font-semibold text-gray-900 dark:text-white">{etb(payroll.grossPayroll)}</span>
                      </div>
                      <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                        <span className="text-gray-500">Employment Tax Withheld (Proc 1395)</span>
                        <span className="font-medium text-amber-600">-{etb(payroll.employmentTaxWithheld)}</span>
                      </div>
                      <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                        <span className="text-gray-500">Employee Pension (7%)</span>
                        <span className="font-medium text-blue-600">-{etb(payroll.employeePension)}</span>
                      </div>
                      <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                        <span className="text-gray-500">Total Deductions</span>
                        <span className="font-medium text-red-500">-{etb(payroll.totalDeductions)}</span>
                      </div>
                      <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800 bg-purple-50/50 dark:bg-purple-950/20 px-2 rounded">
                        <span className="font-medium text-purple-900 dark:text-purple-200">Net Payroll Payable</span>
                        <span className="font-bold text-purple-700 dark:text-purple-400">{etb(payroll.netPayrollPayable)}</span>
                      </div>
                      <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                        <span className="text-gray-500">Employer Pension (11%)</span>
                        <span className="font-medium text-indigo-600">+{etb(payroll.employerPension)}</span>
                      </div>
                      <div className="flex justify-between items-center py-1">
                        <span className="text-gray-500 font-medium">Total Employer Cost</span>
                        <span className="font-bold text-gray-900 dark:text-white">{etb(payroll.totalEmployerPayrollCost)}</span>
                      </div>
                    </>
                  )}
                </CardContent>
              </Card>

              {/* Card 3: Inventory & Business Summary */}
              <Card className="border-t-4 border-t-emerald-500 shadow-sm">
                <CardHeader className="pb-3 border-b">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                      <Package className="w-4 h-4 text-emerald-600" />
                      Inventory &amp; Business Summary
                    </h3>
                    <span className="text-xs text-gray-400">Ledger Metrics</span>
                  </div>
                </CardHeader>
                <CardContent className="pt-4 space-y-3 text-sm">
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Inventory Valuation</span>
                    <span className="font-semibold text-emerald-600">{etb(inventory?.inventoryValuation)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Cost of Goods Sold (COGS)</span>
                    <span className="font-semibold text-gray-700 dark:text-gray-300">{etb(inventory?.costOfGoodsSold)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Net Sales</span>
                    <span className="font-semibold text-gray-900 dark:text-white">{etb(inventory?.netSales)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Sales Returns</span>
                    <span className="font-medium text-red-500">-{etb(inventory?.salesReturns)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Purchase Totals</span>
                    <span className="font-medium text-gray-700 dark:text-gray-300">{etb(inventory?.purchaseTotals)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 border-b border-gray-100 dark:border-gray-800">
                    <span className="text-gray-500">Recorded Operating Expenses</span>
                    <span className="font-medium text-gray-700 dark:text-gray-300">{etb(inventory?.recordedExpenses)}</span>
                  </div>
                  <div className="flex justify-between items-center py-1 bg-emerald-50/50 dark:bg-emerald-950/20 px-2 rounded">
                    <span className="font-bold text-emerald-900 dark:text-emerald-200">Net Profit</span>
                    <span className="font-bold text-emerald-700 dark:text-emerald-400">{etb(inventory?.netProfit)}</span>
                  </div>
                </CardContent>
              </Card>
            </div>

            {/* 8 NUMERICAL RECHARTS VISUALIZATIONS */}
            <div className="pt-4">
              <h3 className="text-lg font-bold tracking-tight text-gray-900 dark:text-white mb-4 flex items-center gap-2">
                <Layers className="w-5 h-5 text-indigo-600" />
                Financial Analytics &amp; Statutory Trends (8 Numerical Series)
              </h3>

              <div className="grid gap-6 md:grid-cols-2">
                {/* Chart 1: Monthly Output VAT vs Recoverable Input VAT */}
                <Card className="shadow-xs">
                  <CardHeader className="pb-2">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                      1. Monthly Output VAT vs. Recoverable Input VAT
                    </h4>
                    <p className="text-xs text-gray-500">Comparison of 15% VAT on supplies vs. eligible purchase input tax.</p>
                  </CardHeader>
                  <CardContent className="h-64 pt-2">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={charts?.monthlyVat || []}>
                        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                        <XAxis dataKey="month" fontSize={11} />
                        <YAxis tickFormatter={etbCompact} fontSize={11} />
                        <Tooltip formatter={(val: any) => [etb(Number(val)), '']} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Bar dataKey="outputVat" name="Output VAT (15%)" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="inputVat" name="Recoverable Input VAT" fill="#10b981" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>

                {/* Chart 2: Tax Liabilities vs Recorded Tax Payments */}
                <Card className="shadow-xs">
                  <CardHeader className="pb-2">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                      2. Tax Liabilities vs. Recorded Tax Payments
                    </h4>
                    <p className="text-xs text-gray-500">Calculated statutory obligations vs. payments remitted to tax authority.</p>
                  </CardHeader>
                  <CardContent className="h-64 pt-2">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={charts?.taxLiabilitiesVsPayments || []}>
                        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                        <XAxis dataKey="month" fontSize={11} />
                        <YAxis tickFormatter={etbCompact} fontSize={11} />
                        <Tooltip formatter={(val: any) => [etb(Number(val)), '']} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Bar dataKey="liability" name="Tax Liability" fill="#ef4444" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="payment" name="Tax Payments" fill="#059669" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>

                {/* Chart 3: Gross Payroll vs Net Payroll Over Time */}
                <Card className="shadow-xs">
                  <CardHeader className="pb-2">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                      3. Gross Payroll vs. Net Payroll Over Time
                    </h4>
                    <p className="text-xs text-gray-500">Total contractual employee earnings vs. net disbursed compensation.</p>
                  </CardHeader>
                  <CardContent className="h-64 pt-2">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={charts?.payrollGrossVsNet || []}>
                        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                        <XAxis dataKey="month" fontSize={11} />
                        <YAxis tickFormatter={etbCompact} fontSize={11} />
                        <Tooltip formatter={(val: any) => [etb(Number(val)), '']} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Bar dataKey="grossPayroll" name="Gross Payroll" fill="#8b5cf6" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="netPayroll" name="Net Payroll" fill="#06b6d4" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="deductions" name="Total Deductions" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>

                {/* Chart 4: Employee Income Tax and Pension Contribution Trends */}
                <Card className="shadow-xs">
                  <CardHeader className="pb-2">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                      4. Employee Income Tax &amp; Pension Trends
                    </h4>
                    <p className="text-xs text-gray-500">Statutory progressive employment tax vs. 7% employee and 11% employer pension.</p>
                  </CardHeader>
                  <CardContent className="h-64 pt-2">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={charts?.employeeTaxPensionTrends || []}>
                        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                        <XAxis dataKey="month" fontSize={11} />
                        <YAxis tickFormatter={etbCompact} fontSize={11} />
                        <Tooltip formatter={(val: any) => [etb(Number(val)), '']} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Line type="monotone" dataKey="incomeTax" name="Income Tax" stroke="#f59e0b" strokeWidth={2} dot={{ r: 3 }} />
                        <Line type="monotone" dataKey="employeePension" name="Emp Pension (7%)" stroke="#3b82f6" strokeWidth={2} dot={{ r: 3 }} />
                        <Line type="monotone" dataKey="employerPension" name="Co. Pension (11%)" stroke="#8b5cf6" strokeWidth={2} dot={{ r: 3 }} />
                      </LineChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>

                {/* Chart 5: Employer Payroll Costs Over Time */}
                <Card className="shadow-xs">
                  <CardHeader className="pb-2">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                      5. Employer Payroll Costs Over Time
                    </h4>
                    <p className="text-xs text-gray-500">Total company expenditure: Gross salary + 11% mandatory employer pension contribution.</p>
                  </CardHeader>
                  <CardContent className="h-64 pt-2">
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={charts?.employerCostTrends || []}>
                        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                        <XAxis dataKey="month" fontSize={11} />
                        <YAxis tickFormatter={etbCompact} fontSize={11} />
                        <Tooltip formatter={(val: any) => [etb(Number(val)), '']} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Area type="monotone" dataKey="totalEmployerCost" name="Total Employer Cost" stroke="#4f46e5" fill="#e0e7ff" fillOpacity={0.6} />
                        <Area type="monotone" dataKey="grossSalary" name="Gross Salary" stroke="#8b5cf6" fill="#ede9fe" fillOpacity={0.6} />
                      </AreaChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>

                {/* Chart 6: Monthly Sales, Cost of Goods Sold, and Gross Profit */}
                <Card className="shadow-xs">
                  <CardHeader className="pb-2">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                      6. Monthly Sales, COGS &amp; Gross Profit
                    </h4>
                    <p className="text-xs text-gray-500">Inventory sales revenue vs. procurement cost and remaining gross margin.</p>
                  </CardHeader>
                  <CardContent className="h-64 pt-2">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={charts?.salesCogsProfitTrends || []}>
                        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                        <XAxis dataKey="month" fontSize={11} />
                        <YAxis tickFormatter={etbCompact} fontSize={11} />
                        <Tooltip formatter={(val: any) => [etb(Number(val)), '']} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Bar dataKey="sales" name="Sales" fill="#10b981" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="cogs" name="COGS" fill="#f43f5e" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="grossProfit" name="Gross Profit" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>

                {/* Chart 7: Tax and Payroll Liabilities by Status */}
                <Card className="shadow-xs">
                  <CardHeader className="pb-2">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                      7. Tax &amp; Payroll Liabilities by Status
                    </h4>
                    <p className="text-xs text-gray-500">Distribution of liabilities across Draft, Approved, Paid, and Outstanding states.</p>
                  </CardHeader>
                  <CardContent className="h-64 pt-2">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={charts?.liabilitiesByStatus || []} layout="vertical">
                        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                        <XAxis type="number" tickFormatter={etbCompact} fontSize={11} />
                        <YAxis type="category" dataKey="category" fontSize={11} width={80} />
                        <Tooltip formatter={(val: any) => [etb(Number(val)), '']} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Bar dataKey="paid" name="Paid" fill="#10b981" stackId="a" />
                        <Bar dataKey="approved" name="Approved" fill="#3b82f6" stackId="a" />
                        <Bar dataKey="draft" name="Draft" fill="#9ca3af" stackId="a" />
                        <Bar dataKey="outstanding" name="Outstanding" fill="#ef4444" stackId="a" />
                      </BarChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>

                {/* Chart 8: Inventory Value vs. Sales and Cost of Goods Sold */}
                <Card className="shadow-xs">
                  <CardHeader className="pb-2">
                    <h4 className="text-sm font-semibold text-gray-900 dark:text-white">
                      8. Inventory Capital vs. Sales &amp; COGS
                    </h4>
                    <p className="text-xs text-gray-500">Warehouse stock valuation vs. commercial outflow and turnover velocity.</p>
                  </CardHeader>
                  <CardContent className="h-64 pt-2">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={charts?.inventoryVsSales || []}>
                        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                        <XAxis dataKey="month" fontSize={11} />
                        <YAxis tickFormatter={etbCompact} fontSize={11} />
                        <Tooltip formatter={(val: any) => [etb(Number(val)), '']} />
                        <Legend wrapperStyle={{ fontSize: '12px' }} />
                        <Line type="monotone" dataKey="inventoryValue" name="Inventory Value" stroke="#10b981" strokeWidth={2} dot={{ r: 3 }} />
                        <Line type="monotone" dataKey="sales" name="Sales" stroke="#3b82f6" strokeWidth={2} dot={{ r: 3 }} />
                        <Line type="monotone" dataKey="cogs" name="COGS" stroke="#f43f5e" strokeWidth={2} dot={{ r: 3 }} />
                      </LineChart>
                    </ResponsiveContainer>
                  </CardContent>
                </Card>
              </div>
            </div>
          </div>

          {/* Interactive Utility: Receipt Tax Calculator (Preserved) */}
          <Card className="border shadow-xs">
            <CardHeader>
              <h3 className="font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                <FileUp className="w-5 h-5 text-gray-600" />
                Receipt Tax Calculator &amp; Verification
              </h3>
              <p className="text-xs text-gray-500">
                Verify receipt tax inclusive/exclusive breakdowns before submitting for input tax recovery under Proclamation 1341/2024.
              </p>
            </CardHeader>
            <CardContent>
              <form
                className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 items-end"
                onSubmit={(e) => {
                  e.preventDefault();
                  calculate.mutate({
                    amount: Number(amount),
                    treatment,
                    taxInclusive: inclusive,
                    receiptName: receipt?.name,
                  });
                }}
              >
                <div>
                  <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Receipt Attachment (Local)
                  </label>
                  <input
                    className="block w-full text-xs text-gray-500 border rounded-lg p-2"
                    type="file"
                    accept="image/*,.pdf"
                    onChange={choose}
                  />
                </div>
                <div>
                  <Input
                    required
                    label="Receipt amount (ETB)"
                    type="number"
                    min="0"
                    step="0.01"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                  />
                </div>
                <div>
                  <Select
                    label="Tax treatment"
                    options={[
                      { value: 'STANDARD', label: 'Standard-rated (15% VAT)' },
                      { value: 'ZERO_RATED', label: 'Zero-rated (0% VAT)' },
                      { value: 'EXEMPT', label: 'Exempt supply' },
                    ]}
                    value={treatment}
                    onChange={(e) => setTreatment(e.target.value)}
                  />
                </div>
                <div className="flex items-center gap-3">
                  <label className="flex items-center gap-2 text-xs text-gray-700 dark:text-gray-300">
                    <input
                      type="checkbox"
                      checked={inclusive}
                      onChange={(e) => setInclusive(e.target.checked)}
                      className="rounded text-blue-600"
                    />
                    VAT Inclusive
                  </label>
                  <Button type="submit" loading={calculate.isPending} size="sm">
                    Calculate
                  </Button>
                </div>
              </form>

              {calculate.data && (
                <div className="mt-4 rounded-lg bg-blue-50 p-4 text-xs dark:bg-blue-900/20 border border-blue-200">
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <div>
                      <span className="text-gray-500 block">Taxable Base</span>
                      <b className="text-sm font-semibold">{etb(calculate.data.taxableAmount)}</b>
                    </div>
                    <div>
                      <span className="text-gray-500 block">VAT (15%)</span>
                      <b className="text-sm font-semibold text-blue-600">{etb(calculate.data.taxAmount)}</b>
                    </div>
                    <div>
                      <span className="text-gray-500 block">Total Amount</span>
                      <b className="text-sm font-semibold text-gray-900 dark:text-white">
                        {etb(calculate.data.totalAmount)}
                      </b>
                    </div>
                    <div>
                      <span className="text-gray-500 block">Notice</span>
                      <span className="text-amber-700 dark:text-amber-400 font-medium">
                        {calculate.data.notice}
                      </span>
                    </div>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
