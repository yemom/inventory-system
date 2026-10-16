'use client';
import React, { useMemo, useState } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  Banknote,
  Calendar,
  CheckCircle2,
  Clock,
  CreditCard,
  DollarSign,
  HelpCircle,
  Info,
  Loader2,
  Package,
  Receipt,
  RefreshCw,
  ShoppingCart,
  Sparkles,
  Store,
  TrendingDown,
  TrendingUp,
  User,
  XCircle,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import { StatCardSkeleton } from '@/components/ui/Skeleton';
import SalesOrderTable from '@/components/dashboard/SalesOrderTable';
import { useSalesSummary, useOperationalForecast } from '@/hooks/useAiDashboard';
import { useAuth } from '@/lib/auth/AuthProvider';
import { formatCurrency } from '@/lib/utils';
import type { SalesSummary, OperationalSalesForecast } from '@/lib/api/aiApi';

const PAYMENT_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#6366f1'];

export default function CashierDashboard() {
  const { user } = useAuth();
  const todayStr = useMemo(() => new Date().toISOString().slice(0, 10), []);
  const todayRange = useMemo(() => ({ from: todayStr, to: todayStr }), [todayStr]);

  // Scoped to current cashier's own orders today
  const salesSummaryQuery = useSalesSummary(todayRange);
  const operationalForecastQuery = useOperationalForecast();

  const summary = salesSummaryQuery.data;
  const forecast = operationalForecastQuery.data;
  const isRefreshing = salesSummaryQuery.isFetching || operationalForecastQuery.isFetching;

  const handleRefresh = () => {
    salesSummaryQuery.refetch();
    operationalForecastQuery.refetch();
  };

  // Prepare hourly data combining actual observed sales and forecast distribution
  const hourlyData = useMemo(() => {
    if (!summary?.hourlySales) return [];
    const points = [];
    const currentHour = new Date().getHours();

    for (let h = 7; h <= 21; h++) {
      const hStr = String(h);
      const actualSales = summary.hourlySales[hStr] ?? 0;
      const dist = forecast?.hourlyDistribution?.find((d) => d.hour === h);
      const historicalAvg = dist?.historicalAvgSales ?? 0;

      points.push({
        hour: `${h.toString().padStart(2, '0')}:00`,
        hourNum: h,
        actualSales: h <= currentHour ? actualSales : null,
        historicalAvg: Math.round(historicalAvg),
        isCurrent: h === currentHour,
      });
    }
    return points;
  }, [summary?.hourlySales, forecast?.hourlyDistribution]);

  // Prepare payment method distribution data
  const paymentData = useMemo(() => {
    if (!summary?.paymentMethods) return [];
    return Object.entries(summary.paymentMethods)
      .filter(([_, amount]) => amount > 0)
      .map(([method, amount], idx) => ({
        name: method,
        value: amount,
        color: PAYMENT_COLORS[idx % PAYMENT_COLORS.length],
      }));
  }, [summary?.paymentMethods]);

  return (
    <div className="space-y-6">
      {/* ── Cashier Header & Identity Banner ────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-blue-100 bg-gradient-to-r from-blue-50/70 via-indigo-50/50 to-white dark:border-blue-900/30 dark:from-blue-950/20 dark:via-gray-900 dark:to-gray-900 p-4 sm:p-5">
        <div className="flex items-center gap-3 sm:gap-4">
          <div className="rounded-xl bg-blue-600 p-3 text-white shadow-sm">
            <Store size={24} />
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl sm:text-2xl font-bold text-gray-900 dark:text-gray-100">
                Cashier Station: {user?.fullName || 'Shift Active'}
              </h1>
              <Badge variant="info">
                {summary?.scope === 'ALL_SALES' ? 'Branch Scope' : 'Personal Scope'}
              </Badge>
              {summary?.branch && (
                <span className="text-xs font-medium text-gray-500 dark:text-gray-400">
                  Branch: {summary.branch}
                </span>
              )}
            </div>
            <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
              Real-time shift summary and sales orders for today ({todayStr}). All metrics reflect verified persisted records.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="flex items-center gap-1.5"
          >
            <RefreshCw size={14} className={isRefreshing ? 'animate-spin' : ''} />
            <span>{isRefreshing ? 'Refreshing...' : 'Refresh Shift'}</span>
          </Button>
        </div>
      </div>

      {/* ── Error Banner if any ─────────────────────────────────────────── */}
      {salesSummaryQuery.error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 dark:border-red-900/40 dark:bg-red-950/20 dark:text-red-300">
          <div className="flex items-center gap-2 font-medium">
            <AlertCircle size={16} />
            Today&apos;s shift summary could not be retrieved
          </div>
          <p className="mt-1 text-xs">
            {salesSummaryQuery.error instanceof Error
              ? salesSummaryQuery.error.message
              : 'Please check connection or contact supervisor.'}
          </p>
        </div>
      )}

      {/* ── Today's Exact Sales Summary Cards ───────────────────────────── */}
      <div>
        <h2 className="text-sm font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-3 flex items-center gap-2">
          <Receipt size={16} className="text-blue-500" />
          Today&apos;s Shift Metrics (Persisted Records)
        </h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {salesSummaryQuery.isLoading || !summary ? (
            Array.from({ length: 8 }).map((_, i) => <StatCardSkeleton key={i} />)
          ) : (
            <>
              {/* Card 1: Completed Sales & Orders Created */}
              <StatCard
                title="Completed Sales"
                value={summary.completedSales}
                changeLabel={`${summary.ordersCreated} orders created today`}
                icon={<CheckCircle2 size={22} />}
                color="blue"
              />

              {/* Card 2: Gross Sales */}
              <StatCard
                title="Gross Sales"
                value={formatCurrency(summary.grossSales)}
                changeLabel={`Excludes deductions`}
                icon={<DollarSign size={22} />}
                color="emerald"
              />

              {/* Card 3: Net Sales */}
              <StatCard
                title="Net Sales"
                value={formatCurrency(summary.netSales)}
                changeLabel={`Gross - Disc (${formatCurrency(summary.discounts)}) - Ret (${formatCurrency(summary.returns)})`}
                icon={<TrendingUp size={22} />}
                color="purple"
              />

              {/* Card 4: Amount Collected */}
              <StatCard
                title="Amount Collected"
                value={formatCurrency(summary.amountCollected)}
                changeLabel={
                  summary.outstanding > 0
                    ? `Outstanding credit: ${formatCurrency(summary.outstanding)}`
                    : 'Fully settled'
                }
                icon={<Banknote size={22} />}
                color={summary.outstanding > 0 ? 'amber' : 'emerald'}
              />

              {/* Card 5: Average Order Value */}
              <StatCard
                title="Average Order Value"
                value={formatCurrency(summary.averageOrderValue)}
                changeLabel={`${summary.unitsSold} total units rung up`}
                icon={<ShoppingCart size={22} />}
                color="blue"
              />

              {/* Card 6: Discounts & Tax */}
              <StatCard
                title="Discounts Given"
                value={formatCurrency(summary.discounts)}
                changeLabel={summary.tax ? `Taxes collected: ${formatCurrency(summary.tax)}` : 'Reported separately'}
                icon={<CreditCard size={22} />}
                color="amber"
              />

              {/* Card 7: Returns & Refunds */}
              <StatCard
                title="Sales Returns"
                value={formatCurrency(summary.returns)}
                changeLabel="Subtracted from net sales"
                icon={<TrendingDown size={22} />}
                color={summary.returns > 0 ? 'red' : 'blue'}
              />

              {/* Card 8: Pending / Voided / Cancelled */}
              <StatCard
                title="Pending & Voids"
                value={`${summary.pendingOrders ?? 0} pend / ${summary.voidedOrders + summary.cancelledOrders} void`}
                changeLabel="Pending not counted in completed"
                icon={<Clock size={22} />}
                color={summary.pendingOrders ? 'amber' : 'blue'}
              />
            </>
          )}
        </div>
      </div>

      {/* ── AI Sales Forecasting & Operational Intelligence ──────────────── */}
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400 flex items-center gap-2">
            <Sparkles size={16} className="text-purple-600" />
            AI-Assisted Sales Forecasting & Performance Insights
          </h2>
          {forecast && (
            <span className="text-xs text-gray-400">
              Model: {forecast.algorithm} • Historical: {forecast.historicalDaysSampled} days ({forecast.historicalOrdersCount} orders)
            </span>
          )}
        </div>

        {operationalForecastQuery.isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, i) => (
              <StatCardSkeleton key={i} />
            ))}
          </div>
        ) : forecast ? (
          <>
            {/* Forecast Projection KPI Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <Card className="border-l-4 border-l-blue-500">
                <CardContent className="p-4">
                  <p className="text-xs font-medium text-gray-500 dark:text-gray-400">
                    Expected Remaining Today
                  </p>
                  <p className="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100 font-mono">
                    {formatCurrency(forecast.todayExpectedRemainingNetSales)}
                  </p>
                  <p className="mt-1 text-xs text-blue-600 dark:text-blue-400 flex items-center gap-1">
                    <Clock size={12} /> ~{forecast.todayExpectedRemainingOrders} more orders expected
                  </p>
                </CardContent>
              </Card>

              <Card className="border-l-4 border-l-indigo-500">
                <CardContent className="p-4">
                  <p className="text-xs font-medium text-gray-500 dark:text-gray-400">
                    Projected Full Day Total
                  </p>
                  <p className="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100 font-mono">
                    {formatCurrency(forecast.todayExpectedTotalNetSales)}
                  </p>
                  <p className="mt-1 text-xs text-gray-500">
                    Observed so far: {formatCurrency(forecast.todayObservedNetSales)}
                  </p>
                </CardContent>
              </Card>

              <Card className="border-l-4 border-l-emerald-500">
                <CardContent className="p-4">
                  <p className="text-xs font-medium text-gray-500 dark:text-gray-400">
                    Next Business Day Forecast
                  </p>
                  <p className="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100 font-mono">
                    {formatCurrency(forecast.nextDayExpectedNetSales)}
                  </p>
                  <p className="mt-1 text-xs text-emerald-600 dark:text-emerald-400">
                    Target: ~{forecast.nextDayExpectedOrders} completed orders
                  </p>
                </CardContent>
              </Card>

              <Card className="border-l-4 border-l-purple-500">
                <CardContent className="p-4">
                  <p className="text-xs font-medium text-gray-500 dark:text-gray-400">
                    AOV Benchmark
                  </p>
                  <p className="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100 font-mono">
                    {formatCurrency(forecast.currentAverageOrderValue)}
                  </p>
                  <p className="mt-1 text-xs text-gray-500">
                    Historical baseline: {formatCurrency(forecast.historicalAverageOrderValue)}
                  </p>
                </CardContent>
              </Card>
            </div>

            {/* AI Explanations & Operational Recommendations */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <Card>
                <CardHeader>
                  <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                    <Sparkles size={16} className="text-purple-600" />
                    AI Shift Analysis & Trends
                  </h3>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div className="rounded-lg bg-blue-50/70 p-3 dark:bg-blue-950/20">
                    <p className="font-medium text-blue-900 dark:text-blue-300 text-xs">
                      Baseline Performance
                    </p>
                    <p className="mt-0.5 text-xs text-blue-800 dark:text-blue-200">
                      {forecast.aiExplanations.baselineComparison}
                    </p>
                  </div>

                  <div className="rounded-lg bg-purple-50/70 p-3 dark:bg-purple-950/20">
                    <p className="font-medium text-purple-900 dark:text-purple-300 text-xs">
                      Peak Rush Hours
                    </p>
                    <p className="mt-0.5 text-xs text-purple-800 dark:text-purple-200">
                      {forecast.aiExplanations.peakHoursExplanation}
                    </p>
                  </div>

                  <div className="rounded-lg bg-emerald-50/70 p-3 dark:bg-emerald-950/20">
                    <p className="font-medium text-emerald-900 dark:text-emerald-300 text-xs">
                      High-Velocity Product Demand
                    </p>
                    <p className="mt-0.5 text-xs text-emerald-800 dark:text-emerald-200">
                      {forecast.aiExplanations.productDemandSummary}
                    </p>
                  </div>

                  <div className="rounded-lg bg-amber-50/70 p-3 dark:bg-amber-950/20">
                    <p className="font-medium text-amber-900 dark:text-amber-300 text-xs">
                      Actionable Cashier Recommendation
                    </p>
                    <p className="mt-0.5 text-xs text-amber-800 dark:text-amber-200">
                      {forecast.aiExplanations.operationalRecommendation}
                    </p>
                  </div>

                  <p className="text-[11px] text-gray-400 italic">
                    Reliability: {forecast.aiExplanations.decisionReadiness}
                  </p>
                </CardContent>
              </Card>

              {/* Product Demand Velocity Trends */}
              <Card>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                      <TrendingUp size={16} className="text-emerald-600" />
                      Trending Items (Velocity Shift)
                    </h3>
                    <span className="text-xs text-gray-400">14-Day Velocity</span>
                  </div>
                </CardHeader>
                <CardContent>
                  {(!forecast.productTrends || forecast.productTrends.length === 0) ? (
                    <p className="py-6 text-center text-xs text-gray-400">
                      Product trend history requires at least 14 days of logged line items.
                    </p>
                  ) : (
                    <div className="space-y-2.5 max-h-[300px] overflow-y-auto pr-1">
                      {forecast.productTrends.map((p) => (
                        <div
                          key={p.productId}
                          className="flex items-center justify-between rounded-lg border border-gray-100 p-2.5 text-xs dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50"
                        >
                          <div>
                            <p className="font-medium text-gray-900 dark:text-gray-100">
                              {p.productName}
                            </p>
                            <p className="text-[11px] font-mono text-gray-400">
                              SKU: {p.sku} • {p.recent14DaysUnits} units recently (vs {p.prior14DaysUnits} prior)
                            </p>
                          </div>
                          <Badge
                            variant={
                              p.trend === 'INCREASING'
                                ? 'success'
                                : p.trend === 'DECREASING'
                                ? 'danger'
                                : 'default'
                            }
                          >
                            {p.trend === 'INCREASING' ? '▲ High Demand' : p.trend === 'DECREASING' ? '▼ Slowing' : '● Steady'}
                            {p.growthPercent ? ` (${p.growthPercent > 0 ? '+' : ''}${p.growthPercent.toFixed(0)}%)` : ''}
                          </Badge>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          </>
        ) : null}
      </div>

      {/* ── Charts: Intraday Hourly Sales & Payment Breakdown ──────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Hourly sales distribution chart */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                <Clock size={16} className="text-blue-500" />
                Hourly Sales: Today vs Historical Shift Average
              </h3>
              {forecast?.peakHours && forecast.peakHours.length > 0 && (
                <span className="text-xs text-purple-600 dark:text-purple-400 font-medium">
                  Peak times: {forecast.peakHours.join(', ')}
                </span>
              )}
            </div>
          </CardHeader>
          <CardContent>
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={hourlyData} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} opacity={0.3} />
                  <XAxis dataKey="hour" fontSize={11} tickLine={false} />
                  <YAxis fontSize={11} tickLine={false} tickFormatter={(v) => `${v}`} />
                  <Tooltip
                    formatter={(val: any, name: any) => [
                      formatCurrency(Number(val)),
                      name === 'actualSales' ? "Today's Sales" : 'Historical Hourly Average',
                    ]}
                  />
                  <Legend wrapperStyle={{ fontSize: '12px', paddingTop: '10px' }} />
                  <Bar
                    dataKey="actualSales"
                    name="Today's Sales"
                    fill="#3b82f6"
                    radius={[4, 4, 0, 0]}
                  />
                  <Bar
                    dataKey="historicalAvg"
                    name="Historical Average"
                    fill="#cbd5e1"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Payment Methods Distribution */}
        <Card>
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
              <CreditCard size={16} className="text-emerald-500" />
              Payment Methods
            </h3>
          </CardHeader>
          <CardContent>
            {paymentData.length === 0 ? (
              <p className="py-16 text-center text-xs text-gray-400">
                No payment transactions recorded today yet.
              </p>
            ) : (
              <div className="space-y-4">
                <div className="h-44 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={paymentData}
                        dataKey="value"
                        nameKey="name"
                        cx="50%"
                        cy="50%"
                        innerRadius={40}
                        outerRadius={70}
                        paddingAngle={4}
                      >
                        {paymentData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(val: any) => [formatCurrency(Number(val)), 'Total']} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <div className="space-y-1.5 text-xs">
                  {paymentData.map((p) => (
                    <div key={p.name} className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span
                          className="h-2.5 w-2.5 rounded-full"
                          style={{ backgroundColor: p.color }}
                        />
                        <span className="text-gray-700 dark:text-gray-300">{p.name}</span>
                      </div>
                      <span className="font-mono font-medium text-gray-900 dark:text-gray-100">
                        {formatCurrency(p.value)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* ── 7-Day Forward Horizon Forecast ──────────────────────────────── */}
      {forecast?.next7DaysForecast && forecast.next7DaysForecast.length > 0 && (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                <Calendar size={16} className="text-blue-600" />
                7-Day Operational Sales Trajectory (With 80% Prediction Confidence Bounds)
              </h3>
              <span className="text-xs text-gray-400">Deterministic Holt-Winters / Daily Pattern</span>
            </div>
          </CardHeader>
          <CardContent>
            <div className="h-60 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart
                  data={forecast.next7DaysForecast}
                  margin={{ top: 10, right: 20, left: -10, bottom: 0 }}
                >
                  <CartesianGrid strokeDasharray="3 3" vertical={false} opacity={0.3} />
                  <XAxis dataKey="date" fontSize={11} tickLine={false} />
                  <YAxis fontSize={11} tickLine={false} />
                  <Tooltip
                    formatter={(val: any, name: any) => [
                      formatCurrency(Number(val)),
                      name === 'expectedSales'
                        ? 'Expected Sales'
                        : name === 'upperBound'
                        ? 'Upper Bound'
                        : 'Lower Bound',
                    ]}
                  />
                  <Legend wrapperStyle={{ fontSize: '12px' }} />
                  <Line
                    type="monotone"
                    dataKey="upperBound"
                    name="Upper Bound"
                    stroke="#93c5fd"
                    strokeDasharray="3 3"
                    dot={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="expectedSales"
                    name="Expected Sales"
                    stroke="#2563eb"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="lowerBound"
                    name="Lower Bound"
                    stroke="#93c5fd"
                    strokeDasharray="3 3"
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      )}

      {/* ── Exact Sales Order Table (Cashier Scoped) ────────────────────── */}
      <div>
        <SalesOrderTable
          limit={10}
          defaultFromDate={todayStr}
          defaultToDate={todayStr}
          title="Today's Shift Sales Orders"
        />
      </div>
    </div>
  );
}
