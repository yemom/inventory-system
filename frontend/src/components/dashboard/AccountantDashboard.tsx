'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import {
  AlertCircle,
  AlertTriangle,
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Banknote,
  BarChart3,
  CheckCircle2,
  CreditCard,
  DollarSign,
  FileText,
  HelpCircle,
  Info,
  Layers,
  Loader2,
  Lock,
  Package,
  PieChart as PieChartIcon,
  Receipt,
  RefreshCw,
  Scale,
  ShieldAlert,
  ShieldCheck,
  ShoppingCart,
  Sparkles,
  TrendingDown,
  TrendingUp,
  Truck,
  Undo2,
  Users,
  Wallet,
} from 'lucide-react';
import {
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
  PieChart,
  Pie,
} from 'recharts';
import { Card, CardHeader, CardContent } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import { StatCardSkeleton, Skeleton } from '@/components/ui/Skeleton';
import DateRangeFilter, {
  DateRange,
  lastNDays,
} from '@/components/ui/DateRangeFilter';
import AiInsightPanel from '@/components/ai/AiInsightPanel';
import { useDashboardFinancial, useAiDashboard } from '@/hooks/useAiDashboard';
import { useAuth } from '@/lib/auth/AuthProvider';
import { formatCurrency, formatDate } from '@/lib/utils';
import { labelApiError } from '@/lib/api/apiErrors';

const EXPENSE_PALETTE = [
  '#3b82f6', // blue
  '#10b981', // emerald
  '#f59e0b', // amber
  '#8b5cf6', // purple
  '#ec4899', // pink
  '#06b6d4', // cyan
  '#f97316', // orange
  '#64748b', // slate
];

export default function AccountantDashboard() {
  const { user, hasPermission } = useAuth();
  const [range, setRange] = useState<DateRange>(() => lastNDays(30));
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const maySeeMoney = hasPermission('FINANCIAL_REPORT_VIEW');
  const maySeeAi = hasPermission('AI_INSIGHT_VIEW');
  const maySeeInventory =
    hasPermission('FINANCIAL_REPORT_VIEW') ||
    hasPermission('INVENTORY_REPORT_VIEW');

  const financialQuery = useDashboardFinancial(range, maySeeMoney);
  const aiQuery = useAiDashboard(range, 14, maySeeAi);

  const money = financialQuery.data;
  const isRefreshing = financialQuery.isFetching || (maySeeAi && aiQuery.isFetching);

  React.useEffect(() => {
    if (financialQuery.dataUpdatedAt) setLastUpdated(new Date(financialQuery.dataUpdatedAt));
  }, [financialQuery.dataUpdatedAt]);

  const handleRefresh = () => {
    financialQuery.refetch();
    if (maySeeAi) {
      aiQuery.refetch();
    }
  };

  const percentChange = (current: number, previous: number): number | undefined => {
    if (!previous || previous === 0) return undefined;
    return Number((((current - previous) / Math.abs(previous)) * 100).toFixed(1));
  };

  // Waterfall dataset for Recharts
  const waterfallData = useMemo(() => {
    if (!money) return [];
    return [
      {
        name: 'Gross Sales',
        amount: money.grossSales,
        displayAmount: formatCurrency(money.grossSales),
        fill: '#10b981', // emerald
        type: 'Addition',
        pct: '100.0% of sales',
        description: 'Total revenue before discounts, returns, and taxes',
      },
      {
        name: 'Less: COGS',
        amount: -Math.abs(money.cogs),
        displayAmount: `-${formatCurrency(money.cogs)}`,
        fill: '#ef4444', // red
        type: 'Deduction',
        pct:
          money.grossSales > 0
            ? `${((money.cogs / money.grossSales) * 100).toFixed(1)}% of sales`
            : '0.0%',
        description: 'Cost of goods sold (inventory acquisition cost)',
      },
      {
        name: 'Gross Profit',
        amount: money.grossProfit,
        displayAmount: formatCurrency(money.grossProfit),
        fill: '#3b82f6', // blue
        type: 'Subtotal',
        pct: `${money.grossMarginPercent.toFixed(1)}% margin`,
        description: 'Profit remaining after direct inventory costs',
      },
      {
        name: 'Less: OpEx',
        amount: -Math.abs(money.operatingExpenses),
        displayAmount: `-${formatCurrency(money.operatingExpenses)}`,
        fill: '#f59e0b', // amber
        type: 'Deduction',
        pct:
          money.grossSales > 0
            ? `${((money.operatingExpenses / money.grossSales) * 100).toFixed(1)}% of sales`
            : '0.0%',
        description: 'Operational expenses, utilities, payroll, and overhead',
      },
      {
        name: 'Net Profit',
        amount: money.netProfit,
        displayAmount: formatCurrency(money.netProfit),
        fill: money.netProfit >= 0 ? '#8b5cf6' : '#dc2626', // purple or crimson
        type: 'Bottom Line',
        pct: `${money.netMarginPercent.toFixed(1)}% margin`,
        description: 'Final bottom-line net income for the accounting period',
      },
    ];
  }, [money]);

  const expensesByCategory = useMemo(() => {
    if (!money?.expensesByCategory || !Array.isArray(money.expensesByCategory)) {
      return [];
    }
    return money.expensesByCategory;
  }, [money?.expensesByCategory]);

  const hasExpensesList = expensesByCategory.length > 0;

  return (
    <div className="space-y-6">
      {/* ── Section A: Header ───────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-blue-100 bg-gradient-to-r from-blue-50/80 via-indigo-50/40 to-white p-4 sm:p-5 dark:border-blue-900/30 dark:from-blue-950/20 dark:via-gray-900 dark:to-gray-900">
        <div className="flex items-center gap-3 sm:gap-4">
          <div className="rounded-xl bg-blue-600 p-3 text-white shadow-sm">
            <Scale size={24} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100">
                Financial Stewardship
              </h1>
              <Badge variant="info">Accountant</Badge>
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
              P&amp;L verification, working capital, cash flows, and inventory accounting
              {range.from && range.to && (
                <span className="ml-1 font-medium text-gray-700 dark:text-gray-300">
                  ({formatDate(range.from)} – {formatDate(range.to)})
                </span>
              )}
              {lastUpdated && <span className="ml-2">Updated {lastUpdated.toLocaleString()}</span>}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5">
          <DateRangeFilter value={range} onChange={setRange} />
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="h-9 px-3"
            title="Refresh financial data"
          >
            <RefreshCw
              size={15}
              className={isRefreshing ? 'animate-spin text-blue-600' : 'text-gray-500'}
            />
            <span className="hidden sm:inline">Refresh</span>
          </Button>
        </div>
      </div>

      {/* ── Permission Check Gate ─────────────────────────────────────────── */}
      {!maySeeMoney ? (
        <Card className="border-amber-200 bg-amber-50/40 dark:border-amber-800 dark:bg-amber-950/20">
          <CardContent className="py-12 text-center">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-amber-100 text-amber-600 dark:bg-amber-900/40 dark:text-amber-400 shadow-sm">
              <Lock size={26} />
            </div>
            <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100">
              Financial Permission Required
            </h3>
            <p className="mx-auto mt-2 max-w-lg text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
              Viewing the Accountant Dashboard requires the{' '}
              <code className="rounded bg-amber-100 px-1.5 py-0.5 text-xs font-semibold text-amber-800 dark:bg-amber-900/60 dark:text-amber-300">
                FINANCIAL_REPORT_VIEW
              </code>{' '}
              permission. This ensures confidential profit margins, general ledger balances,
              and cash flow statements remain restricted to authorised personnel.
            </p>
            <p className="mt-4 text-xs text-gray-500 dark:text-gray-400">
              Please contact your system administrator to assign financial reporting authorities
              to your profile.
            </p>
          </CardContent>
        </Card>
      ) : (
        <>
          {/* ── Error Banner ────────────────────────────────────────────────── */}
          {financialQuery.isError && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4 dark:border-red-900/50 dark:bg-red-950/30">
              <div className="flex items-start gap-3">
                <AlertCircle className="mt-0.5 h-5 w-5 text-red-600 dark:text-red-400 shrink-0" />
                <div className="flex-1 text-sm text-red-800 dark:text-red-200">
                  <p className="font-semibold">Financial figures could not be loaded</p>
                  <p className="mt-1 text-xs opacity-90">
                    {labelApiError('Financials', financialQuery.error).message}
                  </p>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => financialQuery.refetch()}
                  className="shrink-0 bg-white dark:bg-gray-800 text-xs"
                >
                  <RefreshCw size={13} className="mr-1" /> Retry
                </Button>
              </div>
            </div>
          )}
          {maySeeAi && aiQuery.isError && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-200">
              AI insights are unavailable. Financial figures remain sourced from backend records.
              <Button variant="outline" size="sm" className="ml-3" onClick={() => aiQuery.refetch()}>Retry AI</Button>
            </div>
          )}

          {/* ── Section B: Revenue & Profit KPI row ─────────────────────────── */}
          <div>
            <div className="mb-2.5 flex items-center justify-between">
              <h2 className="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400 flex items-center gap-1.5">
                <DollarSign size={14} className="text-emerald-500" />
                Revenue &amp; Profit Performance
              </h2>
              {money && (
                <span className="text-xs text-gray-400">
                  {money.salesCount.toLocaleString()} completed orders ·{' '}
                  {money.itemsSold.toLocaleString()} items sold
                </span>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {financialQuery.isLoading ? (
                Array.from({ length: 4 }).map((_, i) => <StatCardSkeleton key={i} />)
              ) : financialQuery.isError || !money ? (
                <div className="col-span-full rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-100">
                  Financial figures are unavailable for this reporting period. Retry the request to load persisted data.
                </div>
              ) : (
                <>
                  <StatCard
                    title="Gross Sales"
                    value={formatCurrency(money.grossSales)}
                    changeLabel="before discounts & returns"
                    color="emerald"
                    icon={<DollarSign size={20} />}
                  />
                  <StatCard
                    title="Net Sales"
                    value={formatCurrency(money.netSales)}
                    change={percentChange(money.netSales, money.previousNetSales)}
                    changeLabel="vs previous period"
                    color="emerald"
                    icon={<ShoppingCart size={20} />}
                  />
                  <StatCard
                    title="Gross Profit"
                    value={formatCurrency(money.grossProfit)}
                    change={money.grossMarginPercent}
                    changeLabel={`${money.grossMarginPercent.toFixed(1)}% margin`}
                    color="blue"
                    icon={<Receipt size={20} />}
                  />
                  <StatCard
                    title="Net Profit"
                    value={formatCurrency(money.netProfit)}
                    change={money.netMarginPercent}
                    changeLabel={`${money.netMarginPercent.toFixed(1)}% margin`}
                    color={money.netProfit >= 0 ? 'blue' : 'red'}
                    icon={
                      money.netProfit >= 0 ? (
                        <TrendingUp size={20} />
                      ) : (
                        <TrendingDown size={20} />
                      )
                    }
                  />
                </>
              )}
            </div>
          </div>

          {/* ── Section C: Cash Flow KPI row ─────────────────────────────────── */}
          <div>
            <div className="mb-2.5 flex items-center justify-between">
              <h2 className="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400 flex items-center gap-1.5">
                <Banknote size={14} className="text-blue-500" />
                Cash Flow &amp; Liquidity
              </h2>
              {money?.paymentCount !== undefined && (
                <span className="text-xs text-gray-400">
                  {money.paymentCount.toLocaleString()} recorded payment transactions
                </span>
              )}
            </div>

            <div
              className={`grid grid-cols-1 sm:grid-cols-2 ${
                money?.cashRefunded !== undefined && money?.cashRefunded !== null
                  ? 'lg:grid-cols-4'
                  : 'lg:grid-cols-3'
              } gap-4`}
            >
              {financialQuery.isLoading ? (
                Array.from({
                  length:
                    money?.cashRefunded !== undefined && money?.cashRefunded !== null ? 4 : 3,
                }).map((_, i) => <StatCardSkeleton key={i} />)
              ) : financialQuery.isError || !money ? (
                <div className="col-span-full rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-100">
                  Cash flow is unavailable because the financial source request failed.
                </div>
              ) : (
                <>
                  <StatCard
                    title="Cash Received"
                    value={formatCurrency(money.cashReceived)}
                    change={percentChange(money.cashReceived, money.previousCashReceived)}
                    changeLabel={
                      money.previousCashReceived
                        ? 'vs previous period'
                        : 'from customer payments'
                    }
                    color="emerald"
                    icon={<ArrowDownRight size={20} />}
                  />
                  <StatCard
                    title="Cash Paid to Suppliers"
                    value={formatCurrency(money.cashPaidToSuppliers)}
                    changeLabel="settled supplier invoices"
                    color="amber"
                    icon={<ArrowUpRight size={20} />}
                  />
                  <StatCard
                    title="Net Cash Flow"
                    value={formatCurrency(money.netCashFlow)}
                    changeLabel={
                      money.netCashFlow >= 0
                        ? 'positive cash generation'
                        : 'net cash deficit'
                    }
                    color={money.netCashFlow >= 0 ? 'purple' : 'red'}
                    icon={<Wallet size={20} />}
                  />
                  {money.cashRefunded !== undefined && money.cashRefunded !== null && (
                    <StatCard
                      title="Cash Refunded"
                      value={formatCurrency(money.cashRefunded)}
                      changeLabel="customer refunds issued"
                      color="amber"
                      icon={<Undo2 size={20} />}
                    />
                  )}
                </>
              )}
            </div>
          </div>

          {/* ── Section D: Receivables & Payables panel ─────────────────────── */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-3">
              <div>
                <h3 className="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                  <CreditCard size={18} className="text-blue-600 dark:text-blue-400" />
                  Working Capital Balances (AR &amp; AP)
                </h3>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                  Outstanding trade receivables and supplier payment obligations as of period close
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Link
                  href="/reports"
                  className="text-xs font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400 inline-flex items-center gap-1"
                >
                  Reports Hub <ArrowRight size={13} />
                </Link>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {financialQuery.isLoading ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <StatCardSkeleton />
                  <StatCardSkeleton />
                </div>
              ) : financialQuery.isError || !money ? (
                <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Receivables and payables are unavailable because the financial source request failed.</p>
              ) : (
                <>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* Accounts Receivable Card */}
                    <div className="rounded-xl border border-blue-100 bg-blue-50/30 p-5 dark:border-blue-900/30 dark:bg-blue-950/10">
                      <div className="flex items-start justify-between">
                        <div>
                          <span className="inline-flex items-center gap-1 rounded-md bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-800 dark:bg-blue-900/60 dark:text-blue-300">
                            <Users size={12} /> Asset (Owed to Us)
                          </span>
                          <p className="mt-2 text-sm font-medium text-gray-600 dark:text-gray-300">
                            Accounts Receivable (AR)
                          </p>
                          <p className="mt-1 text-3xl font-extrabold text-blue-700 dark:text-blue-400">
                            {formatCurrency(money.accountsReceivable)}
                          </p>
                        </div>
                        <div className="rounded-xl bg-blue-100 p-3 text-blue-600 dark:bg-blue-900/40 dark:text-blue-400">
                          <Users size={24} />
                        </div>
                      </div>
                      <p className="mt-3 text-xs text-gray-500 dark:text-gray-400 leading-relaxed border-t border-blue-100 dark:border-blue-900/40 pt-3">
                        Total unpaid customer invoices. Uncollected revenue from completed sales
                        delivered on trade credit terms.
                      </p>
                    </div>

                    {/* Accounts Payable Card */}
                    <div className="rounded-xl border border-amber-100 bg-amber-50/30 p-5 dark:border-amber-900/30 dark:bg-amber-950/10">
                      <div className="flex items-start justify-between">
                        <div>
                          <span className="inline-flex items-center gap-1 rounded-md bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-800 dark:bg-amber-900/60 dark:text-amber-300">
                            <Truck size={12} /> Liability (Owed by Us)
                          </span>
                          <p className="mt-2 text-sm font-medium text-gray-600 dark:text-gray-300">
                            Accounts Payable (AP)
                          </p>
                          <p className="mt-1 text-3xl font-extrabold text-amber-700 dark:text-amber-400">
                            {formatCurrency(money.accountsPayable)}
                          </p>
                        </div>
                        <div className="rounded-xl bg-amber-100 p-3 text-amber-600 dark:bg-amber-900/40 dark:text-amber-400">
                          <Truck size={24} />
                        </div>
                      </div>
                      <p className="mt-3 text-xs text-gray-500 dark:text-gray-400 leading-relaxed border-t border-amber-100 dark:border-amber-900/40 pt-3">
                        Total unpaid supplier invoices for goods already received into warehouses.
                        Short-term commercial liabilities due.
                      </p>
                    </div>
                  </div>

                  {/* Explanatory Note */}
                  <div className="flex items-start gap-2.5 rounded-lg bg-gray-50 dark:bg-gray-800/60 p-3.5 text-xs text-gray-600 dark:text-gray-300 border border-gray-200 dark:border-gray-700">
                    <Info size={16} className="text-blue-500 shrink-0 mt-0.5" />
                    <div>
                      <span className="font-semibold text-gray-900 dark:text-gray-100">
                        Working Capital Standing:{' '}
                      </span>
                      AR represents what customers owe us from sales, while AP represents what we
                      owe suppliers for received stock. The net trade balance is{' '}
                      <strong className="text-gray-900 dark:text-gray-100">
                        {formatCurrency(money.accountsReceivable - money.accountsPayable)}
                      </strong>{' '}
                      ({money.accountsReceivable >= money.accountsPayable
                        ? 'net credit surplus'
                        : 'net supplier liability'}
                      ).
                    </div>
                  </div>
                </>
              )}
            </CardContent>
          </Card>

          {/* ── Section E: P&L Waterfall Chart ───────────────────────────────── */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-3">
              <div>
                <h3 className="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                  <BarChart3 size={18} className="text-indigo-600 dark:text-indigo-400" />
                  Profit &amp; Loss Waterfall (Bridge to Net Profit)
                </h3>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                  Visual margin walk from Gross Sales through COGS and Operating Expenses to Net
                  Profit
                </p>
              </div>
              <div className="flex items-center gap-2 text-xs">
                <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" /> Positive
                </span>
                <span className="inline-flex items-center gap-1 text-red-600 dark:text-red-400">
                  <span className="h-2.5 w-2.5 rounded-full bg-red-500" /> Cost/Expense
                </span>
                <span className="inline-flex items-center gap-1 text-blue-600 dark:text-blue-400">
                  <span className="h-2.5 w-2.5 rounded-full bg-blue-500" /> Subtotal
                </span>
              </div>
            </CardHeader>
            <CardContent>
              {financialQuery.isLoading ? (
                <Skeleton className="h-80 w-full rounded-xl" />
              ) : financialQuery.isError || !money ? (
                <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Profit chart data is unavailable for this period.</p>
              ) : (
                <>
                  <div className="h-80 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={waterfallData}
                        margin={{ top: 20, right: 20, left: 20, bottom: 25 }}
                      >
                        <CartesianGrid
                          strokeDasharray="3 3"
                          className="stroke-gray-200 dark:stroke-gray-700"
                          vertical={false}
                        />
                        <XAxis
                          dataKey="name"
                          tick={{ fill: 'currentColor', fontSize: 12 }}
                          className="text-xs text-gray-600 dark:text-gray-400"
                        />
                        <YAxis
                          tickFormatter={(v) => formatCurrency(v)}
                          tick={{ fill: 'currentColor', fontSize: 11 }}
                          className="text-xs text-gray-600 dark:text-gray-400"
                        />
                        <Tooltip
                          content={({ active, payload }) => {
                            if (!active || !payload?.length) return null;
                            const d = payload[0].payload;
                            return (
                              <div className="rounded-lg border border-gray-200 bg-white p-3 shadow-lg dark:border-gray-700 dark:bg-gray-800 text-xs">
                                <div className="flex items-center justify-between gap-3">
                                  <span className="font-semibold text-gray-900 dark:text-gray-100">
                                    {d.name}
                                  </span>
                                  <Badge variant="default">{d.type}</Badge>
                                </div>
                                <p
                                  className="mt-1.5 text-base font-extrabold"
                                  style={{ color: d.fill }}
                                >
                                  {d.displayAmount}
                                </p>
                                <p className="mt-0.5 text-gray-500 dark:text-gray-400 font-medium">
                                  {d.pct}
                                </p>
                                <p className="mt-1 text-gray-400 text-[11px] border-t border-gray-100 dark:border-gray-700 pt-1">
                                  {d.description}
                                </p>
                              </div>
                            );
                          }}
                        />
                        <ReferenceLine y={0} stroke="#9ca3af" strokeWidth={1.5} />
                        <Bar dataKey="amount" radius={4}>
                          {waterfallData.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={entry.fill} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>

                  {/* Equation Breakdown Strip */}
                  <div className="mt-4 flex flex-wrap items-center justify-center gap-2 border-t border-gray-100 pt-4 dark:border-gray-800 text-xs font-medium">
                    <span className="inline-flex items-center gap-1 rounded-lg bg-emerald-50 px-2.5 py-1 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/50">
                      Gross Sales: {formatCurrency(money.grossSales)}
                    </span>
                    <span className="text-gray-400 font-bold">−</span>
                    <span className="inline-flex items-center gap-1 rounded-lg bg-red-50 px-2.5 py-1 text-red-700 dark:bg-red-950/40 dark:text-red-400 border border-red-200 dark:border-red-800/50">
                      COGS: {formatCurrency(money.cogs)}
                    </span>
                    <span className="text-gray-400 font-bold">=</span>
                    <span className="inline-flex items-center gap-1 rounded-lg bg-blue-50 px-2.5 py-1 text-blue-700 dark:bg-blue-950/40 dark:text-blue-400 border border-blue-200 dark:border-blue-800/50">
                      Gross Profit: {formatCurrency(money.grossProfit)} (
                      {money.grossMarginPercent.toFixed(1)}%)
                    </span>
                    <span className="text-gray-400 font-bold">−</span>
                    <span className="inline-flex items-center gap-1 rounded-lg bg-amber-50 px-2.5 py-1 text-amber-700 dark:bg-amber-950/40 dark:text-amber-400 border border-amber-200 dark:border-amber-800/50">
                      Operating Expenses: {formatCurrency(money.operatingExpenses)}
                    </span>
                    <span className="text-gray-400 font-bold">=</span>
                    <span
                      className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1 font-semibold border ${
                        money.netProfit >= 0
                          ? 'bg-purple-50 text-purple-700 dark:bg-purple-950/40 dark:text-purple-400 border-purple-200 dark:border-purple-800/50'
                          : 'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-400 border-red-200 dark:border-red-800/50'
                      }`}
                    >
                      Net Profit: {formatCurrency(money.netProfit)} (
                      {money.netMarginPercent.toFixed(1)}%)
                    </span>
                  </div>
                </>
              )}
            </CardContent>
          </Card>

          {/* ── Section F: Expense Breakdown Section ────────────────────────── */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-3">
              <div>
                <h3 className="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                  <PieChartIcon size={18} className="text-amber-600 dark:text-amber-400" />
                  Operating Expense Allocation
                </h3>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                  Overhead expenses, administrative costs, and approved disbursement vouchers
                </p>
              </div>
              {money && (
                <div className="text-right">
                  <p className="text-xs text-gray-400">Total Operating Expenses</p>
                  <p className="text-base font-extrabold text-gray-900 dark:text-gray-100">
                    {formatCurrency(money.operatingExpenses)}
                  </p>
                </div>
              )}
            </CardHeader>
            <CardContent>
              {financialQuery.isLoading ? (
                <Skeleton className="h-48 w-full rounded-xl" />
              ) : financialQuery.isError || !money ? (
                <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Expense breakdown data is unavailable for this period.</p>
              ) : hasExpensesList ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-center">
                  {/* Left: Donut Chart */}
                  <div className="h-64 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={expensesByCategory}
                          dataKey="amount"
                          nameKey="category"
                          cx="50%"
                          cy="50%"
                          innerRadius={60}
                          outerRadius={90}
                          paddingAngle={2}
                        >
                          {expensesByCategory.map((_, index) => (
                            <Cell
                              key={`cell-${index}`}
                              fill={EXPENSE_PALETTE[index % EXPENSE_PALETTE.length]}
                            />
                          ))}
                        </Pie>
                        <Tooltip
                          content={({ active, payload }) => {
                            if (!active || !payload?.length) return null;
                            const d = payload[0].payload;
                            const share =
                              money.operatingExpenses > 0
                                ? ((d.amount / money.operatingExpenses) * 100).toFixed(1)
                                : '0.0';
                            return (
                              <div className="rounded-lg border border-gray-200 bg-white p-2.5 shadow-md dark:border-gray-700 dark:bg-gray-800 text-xs">
                                <p className="font-semibold text-gray-900 dark:text-gray-100">
                                  {d.category}
                                </p>
                                <p className="text-sm font-bold text-blue-600 dark:text-blue-400 mt-1">
                                  {formatCurrency(d.amount)}
                                </p>
                                <p className="text-[11px] text-gray-500 dark:text-gray-400">
                                  {share}% of operating expenses
                                </p>
                              </div>
                            );
                          }}
                        />
                        <Legend
                          formatter={(value) => (
                            <span className="text-xs text-gray-700 dark:text-gray-300">
                              {value}
                            </span>
                          )}
                        />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>

                  {/* Right: Progress List */}
                  <div className="space-y-3">
                    <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400 pb-1 border-b border-gray-100 dark:border-gray-800">
                      <span>Category</span>
                      <span>Amount / Share</span>
                    </div>
                    {expensesByCategory.map((item, index) => {
                      const share =
                        money.operatingExpenses > 0
                          ? (item.amount / money.operatingExpenses) * 100
                          : 0;
                      const color = EXPENSE_PALETTE[index % EXPENSE_PALETTE.length];
                      return (
                        <div key={item.category} className="space-y-1">
                          <div className="flex items-center justify-between text-xs">
                            <span className="font-medium text-gray-800 dark:text-gray-200 flex items-center gap-1.5">
                              <span
                                className="h-2 w-2 rounded-full shrink-0"
                                style={{ backgroundColor: color }}
                              />
                              {item.category}
                            </span>
                            <span className="font-semibold text-gray-900 dark:text-gray-100">
                              {formatCurrency(item.amount)}{' '}
                              <span className="font-normal text-gray-400">
                                ({share.toFixed(1)}%)
                              </span>
                            </span>
                          </div>
                          <div className="h-1.5 w-full rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                            <div
                              className="h-full rounded-full transition-all duration-300"
                              style={{ width: `${Math.min(100, share)}%`, backgroundColor: color }}
                            />
                          </div>
                        </div>
                      );
                    })}
                    {money.expenseCount !== undefined && (
                      <p className="text-[11px] text-gray-400 pt-2 text-right">
                        Based on {money.expenseCount} approved expense claims
                      </p>
                    )}
                  </div>
                </div>
              ) : (
                /* Fallback when expensesByCategory is not present or empty */
                <div className="rounded-xl bg-gray-50 p-5 dark:bg-gray-800/40 border border-gray-200 dark:border-gray-700">
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div>
                      <p className="text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                        Total Operating Expenses
                      </p>
                      <p className="text-2xl font-extrabold text-gray-900 dark:text-gray-100 mt-1">
                        {formatCurrency(money.operatingExpenses)}
                      </p>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                        Operating expense ratio:{' '}
                        <strong className="text-gray-800 dark:text-gray-200">
                          {money.netSales > 0
                            ? `${((money.operatingExpenses / money.netSales) * 100).toFixed(1)}%`
                            : '0.0%'}
                        </strong>{' '}
                        of Net Sales
                      </p>
                    </div>

                    {money.expenseCount !== undefined && (
                      <div className="rounded-lg bg-white p-3 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-center min-w-[140px]">
                        <p className="text-xs text-gray-400">Recorded Claims</p>
                        <p className="text-lg font-bold text-gray-900 dark:text-gray-100">
                          {money.expenseCount}
                        </p>
                        <p className="text-[10px] text-gray-400">approved vouchers</p>
                      </div>
                    )}
                  </div>

                  <p className="mt-4 text-xs text-gray-500 dark:text-gray-400 border-t border-gray-200 dark:border-gray-700/60 pt-3 leading-relaxed">
                    Detailed expense category breakdown is compiled in the general ledger. Operating
                    expenses encompass rent, utilities, logistics, maintenance, and administrative
                    claims approved in this period.
                  </p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* ── Section G: Inventory & Stock Loss Section ────────────────────── */}
          {maySeeInventory && (
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-3">
                <div>
                  <h3 className="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
                    <Package size={18} className="text-blue-600 dark:text-blue-400" />
                    Inventory Valuation &amp; Stock Loss Stewardship
                  </h3>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                    Balance sheet inventory asset valuation, shrinkage losses, and committed
                    purchase orders
                  </p>
                </div>
                <Badge variant="outline">Balance Sheet</Badge>
              </CardHeader>
              <CardContent className="space-y-4">
                {financialQuery.isLoading ? (
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <StatCardSkeleton />
                    <StatCardSkeleton />
                    <StatCardSkeleton />
                  </div>
                ) : financialQuery.isError || !money ? (
                  <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Inventory valuation data is unavailable for this period.</p>
                ) : (
                  <>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                      {/* Inventory Value */}
                      <StatCard
                        title="Inventory Value at Cost"
                        value={formatCurrency(money.inventoryValue)}
                        changeLabel="Stock on hand at weighted-avg cost"
                        color="blue"
                        icon={<Package size={20} />}
                      />

                      {/* Stock Loss Value */}
                      <StatCard
                        title="Stock Loss & Shrinkage"
                        value={formatCurrency(money.stockLossValue)}
                        changeLabel={`${money.stockLossUnits.toLocaleString()} units lost (damage, expiry, audit)`}
                        color="amber"
                        icon={<AlertTriangle size={20} />}
                      />

                      {/* Purchase Cost Ordered Not Received */}
                      <StatCard
                        title="Committed Purchase Orders"
                        value={formatCurrency(money.purchaseCostOrderedNotReceived)}
                        changeLabel="Ordered goods awaiting delivery"
                        color="purple"
                        icon={<Truck size={20} />}
                      />
                    </div>

                    {/* Inventory Stewardship Note */}
                    <div className="flex items-start gap-2.5 rounded-lg bg-gray-50 dark:bg-gray-800/60 p-3.5 text-xs text-gray-600 dark:text-gray-300 border border-gray-200 dark:border-gray-700">
                      <Info size={16} className="text-blue-500 shrink-0 mt-0.5" />
                      <div className="space-y-1">
                        <p>
                          <span className="font-semibold text-gray-900 dark:text-gray-100">
                            Inventory Accounting Notes:{' '}
                          </span>
                          Physical inventory on hand is valued at weighted-average acquisition cost.
                          Stock losses due to damage, expiry, or shrinkage write down inventory
                          assets directly and are excluded from gross sales.
                        </p>
                        <p className="text-gray-500 dark:text-gray-400">
                          Committed purchase orders ({formatCurrency(money.purchaseCostOrderedNotReceived)})
                          represent outstanding orders placed with suppliers that have not yet arrived
                          and are not yet recognised as Accounts Payable.
                        </p>
                      </div>
                    </div>
                  </>
                )}
              </CardContent>
            </Card>
          )}

          {/* ── Section H: Period Basis & Data Health ────────────────────────── */}
          {money && (
            <div className="space-y-3">
              {/* Insufficient Data Warning Banner */}
              {money.insufficientData && (
                <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950/30">
                  <div className="flex items-start gap-3">
                    <AlertTriangle className="mt-0.5 h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0" />
                    <div className="text-xs text-amber-900 dark:text-amber-200 space-y-1">
                      <p className="font-semibold text-sm">
                        Limited Trading Activity in Selected Period
                      </p>
                      <p>
                        The figures above are calculated accurately from validated system transactions,
                        but this period contains very few recorded sales orders. Percentage margins
                        and comparative trends may swing noticeably.
                      </p>
                      {money.basis && (
                        <p className="font-mono text-[11px] text-amber-800 dark:text-amber-300">
                          {money.basis}
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* Basis Note Info Box */}
              <div className="flex items-center gap-2.5 rounded-lg border border-gray-200 bg-gray-50/80 px-4 py-3 text-xs text-gray-500 dark:border-gray-800 dark:bg-gray-900/50 dark:text-gray-400">
                <Info size={16} className="text-blue-500 shrink-0" />
                <div className="flex-1">
                  <span className="font-semibold text-gray-700 dark:text-gray-300">
                    Accounting Basis &amp; Provenance:{' '}
                  </span>
                  <span>
                    {money.basis ||
                      'Computed on an accrual basis over verified sales records, actual supplier receipt costs, and approved operating expenses.'}
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* ── Section I: AI Insights Strip (AI_INSIGHT_VIEW gated) ──────────── */}
          {maySeeAi && (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Sparkles size={18} className="text-purple-600 dark:text-purple-400" />
                <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                  AI Financial Insights &amp; Anomaly Detection
                </h3>
              </div>

              <AiInsightPanel
                insights={aiQuery.data?.insights}
                anomalies={aiQuery.data?.anomalies}
                recommendations={aiQuery.data?.recommendations}
                isLoading={aiQuery.isLoading}
                error={aiQuery.error}
                onRetry={() => aiQuery.refetch()}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
