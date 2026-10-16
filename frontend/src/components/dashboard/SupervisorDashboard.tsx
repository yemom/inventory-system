'use client';
import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  Ban,
  Calendar,
  CheckCircle2,
  Clock,
  CreditCard,
  DollarSign,
  Eye,
  Package,
  Receipt,
  RefreshCw,
  ShieldCheck,
  ShoppingCart,
  Sparkles,
  TrendingUp,
  Undo2,
  Users,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
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
import { useProducts } from '@/hooks/useProducts';
import { useAuth } from '@/lib/auth/AuthProvider';
import apiClient, { fetchAllPages } from '@/lib/api/apiClient';
import { formatCurrency, formatDateTime } from '@/lib/utils';
import { useToast } from '@/components/ui/ToastProvider';

interface AwaitingApproval {
  id: number;
  orderNumber?: string | null;
  reference?: string | null;
  customerName?: string | null;
  total?: number | null;
  finalAmount?: number | null;
  status?: string | null;
  date?: string | null;
  createdBy?: string | null;
  cashier?: { id?: number; name?: string } | null;
  voidStatus?: string | null;
  voidReason?: string | null;
  refundStatus?: string | null;
  refundReason?: string | null;
}

export default function SupervisorDashboard() {
  const { user, hasPermission } = useAuth();
  const { toast } = useToast();
  const todayStr = useMemo(() => new Date().toISOString().slice(0, 10), []);
  const todayRange = useMemo(() => ({ from: todayStr, to: todayStr }), [todayStr]);

  const salesSummaryQuery = useSalesSummary(todayRange);
  const operationalForecastQuery = useOperationalForecast();
  const { data: products = [], isLoading: loadingProducts } = useProducts();

  // Approvals queue
  const approvalsQuery = useQuery({
    queryKey: ['sales', 'approvals-summary'],
    queryFn: () => fetchAllPages<AwaitingApproval>('/sales/approvals'),
    enabled: hasPermission('SALE_VOID_APPROVE') || hasPermission('SALE_REFUND_APPROVE'),
    staleTime: 30_000,
  });

  const summary = salesSummaryQuery.data;
  const forecast = operationalForecastQuery.data;
  const pendingApprovals = approvalsQuery.data ?? [];

  const lowStock = useMemo(() => {
    return products.filter(
      (p) => p.quantity > 0 && p.reorderLevel && p.quantity <= p.reorderLevel,
    );
  }, [products]);

  const outOfStock = useMemo(() => {
    return products.filter((p) => p.quantity === 0);
  }, [products]);

  const hourlyData = useMemo(() => {
    if (!summary?.hourlySales) return [];
    const points = [];
    for (let h = 8; h <= 21; h++) {
      const hStr = String(h);
      points.push({
        hour: `${h}:00`,
        todaySales: summary.hourlySales[hStr] ?? 0,
        // Peak hours are what the API actually reports. The previous code asked
        // for `hourlyDistribution`, which no endpoint returns, so this was
        // always 0 and the "Historical Average" series drew a flat line at zero
        // labelled as history.
        isPeak: (forecast?.peakHours ?? []).includes(h),
      });
    }
    return points;
  }, [summary?.hourlySales, forecast?.peakHours]);

  const handleApprove = async (id: number, type: 'void' | 'refund') => {
    try {
      if (type === 'void') {
        await apiClient.post(`/sales/${id}/approve-void`, { note: 'Approved via Supervisor Dashboard' });
      } else {
        await apiClient.post(`/sales/${id}/approve-refund`, { note: 'Approved via Supervisor Dashboard' });
      }
      toast('success', `${type === 'void' ? 'Void' : 'Refund'} approved`);
      approvalsQuery.refetch();
      salesSummaryQuery.refetch();
    } catch {
      toast('error', 'Action failed');
    }
  };

  const handleReject = async (id: number, type: 'void' | 'refund') => {
    try {
      if (type === 'void') {
        await apiClient.post(`/sales/${id}/reject-void`, { note: 'Rejected via Supervisor Dashboard' });
      } else {
        await apiClient.post(`/sales/${id}/reject-refund`, { note: 'Rejected via Supervisor Dashboard' });
      }
      toast('info', `${type === 'void' ? 'Void' : 'Refund'} rejected`);
      approvalsQuery.refetch();
    } catch {
      toast('error', 'Action failed');
    }
  };

  return (
    <div className="space-y-6">
      {/* ── Supervisor Header ───────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-indigo-100 bg-gradient-to-r from-indigo-50/70 via-blue-50/50 to-white dark:border-indigo-900/30 dark:from-indigo-950/20 dark:via-gray-900 dark:to-gray-900 p-4 sm:p-5">
        <div className="flex items-center gap-3 sm:gap-4">
          <div className="rounded-xl bg-indigo-600 p-3 text-white shadow-sm">
            <ShieldCheck size={24} />
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl sm:text-2xl font-bold text-gray-900 dark:text-gray-100">
                Floor Operations: {user?.fullName || 'Shift Supervisor'}
              </h1>
              <Badge variant="info">Branch Supervisor View</Badge>
              {summary?.branch && (
                <span className="text-xs font-medium text-gray-500 dark:text-gray-400">
                  Branch: {summary.branch}
                </span>
              )}
            </div>
            <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
              Cashier floor monitoring, pending void/refund sign-offs, and store alerts for today ({todayStr}).
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              salesSummaryQuery.refetch();
              operationalForecastQuery.refetch();
              approvalsQuery.refetch();
            }}
            disabled={salesSummaryQuery.isFetching || approvalsQuery.isFetching}
          >
            <RefreshCw
              size={14}
              className={salesSummaryQuery.isFetching ? 'animate-spin' : ''}
            />
            <span className="ml-1">Refresh Floor</span>
          </Button>
        </div>
      </div>

      {/* ── Floor Operational Metrics ───────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {salesSummaryQuery.isLoading || !summary ? (
          Array.from({ length: 4 }).map((_, i) => <StatCardSkeleton key={i} />)
        ) : (
          <>
            <StatCard
              title="Today Floor Sales"
              value={formatCurrency(summary.netSales)}
              changeLabel={`${summary.completedSales} completed (${summary.ordersCreated} created)`}
              icon={<ShoppingCart size={22} />}
              color="blue"
            />
            <StatCard
              title="Amount Collected"
              value={formatCurrency(summary.amountCollected)}
              changeLabel={
                summary.outstanding > 0
                  ? `Credit outstanding: ${formatCurrency(summary.outstanding)}`
                  : 'All transactions settled'
              }
              icon={<DollarSign size={22} />}
              color="emerald"
            />
            <StatCard
              title="Discounts & Returns"
              value={formatCurrency(summary.discounts + summary.returns)}
              changeLabel={`Disc: ${formatCurrency(summary.discounts)} • Ret: ${formatCurrency(summary.returns)}`}
              icon={<AlertCircle size={22} />}
              color={summary.discounts + summary.returns > 0 ? 'amber' : 'blue'}
            />
            <StatCard
              title="Pending Approvals"
              value={pendingApprovals.length}
              changeLabel="Requires supervisor sign-off"
              icon={<Clock size={22} />}
              color={pendingApprovals.length > 0 ? 'red' : 'emerald'}
            />
          </>
        )}
      </div>

      {/* ── Pending Void / Refund Approval Queue ────────────────────────── */}
      <Card className={pendingApprovals.length > 0 ? 'border-amber-300 dark:border-amber-800' : ''}>
        <CardHeader>
          <div className="flex items-center justify-between">
            <h2 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
              <Clock size={16} className={pendingApprovals.length > 0 ? 'text-amber-500' : 'text-gray-400'} />
              Pending Void & Refund Authorization Queue ({pendingApprovals.length})
            </h2>
            <Link
              href="/sales/approvals"
              className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
            >
              Full Approvals Page <ArrowRight size={13} />
            </Link>
          </div>
        </CardHeader>
        <CardContent>
          {approvalsQuery.isLoading ? (
            <p className="py-4 text-center text-xs text-gray-500">Checking pending requests...</p>
          ) : pendingApprovals.length === 0 ? (
            <div className="py-6 text-center text-sm text-gray-500 flex flex-col items-center gap-1">
              <CheckCircle2 size={24} className="text-emerald-500 mb-1" />
              <p className="font-medium text-gray-700 dark:text-gray-300">Approval queue is clear</p>
              <p className="text-xs text-gray-400">All cashier void and refund requests have been resolved.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-200 text-left text-xs uppercase tracking-wide text-gray-500 dark:border-gray-700 dark:text-gray-400">
                    <th scope="col" className="py-2 pr-3">Order</th>
                    <th scope="col" className="py-2 pr-3">Type</th>
                    <th scope="col" className="py-2 pr-3">Amount</th>
                    <th scope="col" className="py-2 pr-3">Reason</th>
                    <th scope="col" className="py-2 pr-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingApprovals.map((req) => {
                    const isVoid = req.voidStatus === 'PENDING';
                    const reason = (isVoid ? req.voidReason : req.refundReason) || '—';
                    const amount = req.finalAmount ?? req.total ?? 0;
                    return (
                      <tr
                        key={req.id}
                        className="border-b border-gray-100 dark:border-gray-800 last:border-0 hover:bg-gray-50/50"
                      >
                        <td className="py-2.5 pr-3 font-mono font-medium text-blue-600 dark:text-blue-400">
                          <Link href={`/sales/${req.id}`} className="hover:underline">
                            {req.orderNumber ?? req.reference ?? `#${req.id}`}
                          </Link>
                          {req.customerName && (
                            <span className="block text-xs font-normal text-gray-500">
                              {req.customerName}
                            </span>
                          )}
                        </td>
                        <td className="py-2.5 pr-3">
                          <Badge variant={isVoid ? 'danger' : 'warning'}>
                            {isVoid ? 'VOID REQUEST' : 'REFUND REQUEST'}
                          </Badge>
                        </td>
                        <td className="py-2.5 pr-3 font-mono font-semibold">
                          {formatCurrency(amount)}
                        </td>
                        <td className="py-2.5 pr-3 text-xs text-gray-600 dark:text-gray-300 max-w-xs truncate">
                          {reason}
                        </td>
                        <td className="py-2.5 text-right space-x-1.5 whitespace-nowrap">
                          <Button
                            size="sm"
                            variant="primary"
                            onClick={() => handleApprove(req.id, isVoid ? 'void' : 'refund')}
                            className="bg-emerald-600 hover:bg-emerald-700 text-xs px-2.5 py-1"
                          >
                            Approve
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleReject(req.id, isVoid ? 'void' : 'refund')}
                            className="text-xs text-red-600 border-red-200 hover:bg-red-50 px-2.5 py-1"
                          >
                            Reject
                          </Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* ── Floor Forecasting & Low-Stock Alerts ────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Hourly Floor Activity Chart */}
        <Card>
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
              <Clock size={16} className="text-blue-500" />
              Floor Sales Hourly Activity
            </h3>
          </CardHeader>
          <CardContent>
            <div className="h-60 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={hourlyData} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} opacity={0.3} />
                  <XAxis dataKey="hour" fontSize={11} />
                  <YAxis fontSize={11} />
                  <Tooltip formatter={(val: any) => [formatCurrency(Number(val)), 'Sales']} />
                  <Legend wrapperStyle={{ fontSize: '12px' }} />
                  <Bar dataKey="todaySales" name="Today Sales" fill="#4f46e5" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Low Stock Operational Warning */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                <AlertTriangle size={16} className="text-amber-500" />
                Floor Stock Warnings ({lowStock.length + outOfStock.length})
              </h3>
              <Link href="/inventory" className="text-xs text-blue-600 hover:underline">
                View Inventory
              </Link>
            </div>
          </CardHeader>
          <CardContent>
            {loadingProducts ? (
              <p className="py-8 text-center text-xs text-gray-500">Checking product levels...</p>
            ) : lowStock.length === 0 && outOfStock.length === 0 ? (
              <p className="py-8 text-center text-xs text-gray-500">All product stock levels are healthy.</p>
            ) : (
              <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                {outOfStock.slice(0, 5).map((p) => (
                  <div
                    key={p.id}
                    className="flex items-center justify-between rounded-lg border border-red-100 bg-red-50/50 p-2 text-xs dark:border-red-900/30 dark:bg-red-950/20"
                  >
                    <div>
                      <p className="font-medium text-red-900 dark:text-red-200">{p.name}</p>
                      <p className="text-[11px] text-gray-500">SKU: {p.sku}</p>
                    </div>
                    <Badge variant="danger">Out of Stock</Badge>
                  </div>
                ))}
                {lowStock.slice(0, 5).map((p) => (
                  <div
                    key={p.id}
                    className="flex items-center justify-between rounded-lg border border-amber-100 bg-amber-50/50 p-2 text-xs dark:border-amber-900/30 dark:bg-amber-950/20"
                  >
                    <div>
                      <p className="font-medium text-amber-900 dark:text-amber-200">{p.name}</p>
                      <p className="text-[11px] text-gray-500">
                        SKU: {p.sku} • Stock: {p.quantity} (Reorder: {p.reorderLevel})
                      </p>
                    </div>
                    <Badge variant="warning">Low Stock</Badge>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* ── AI Operational Recommendations ─────────────────────────────── */}
      {forecast?.aiInsights && (
        <Card className="bg-gradient-to-r from-purple-50/40 via-blue-50/30 to-white dark:from-purple-950/10 dark:via-blue-950/10 dark:to-gray-900">
          <CardContent className="p-4">
            <div className="flex items-start gap-3">
              <Sparkles size={18} className="text-purple-600 mt-0.5 shrink-0" />
              <div className="space-y-1">
                <p className="text-xs font-semibold uppercase tracking-wider text-purple-900 dark:text-purple-300">
                  Supervisor Operational Briefing
                </p>
                <p className="text-xs text-gray-700 dark:text-gray-300">
                  {forecast.aiInsights.peakHoursExplanation} {forecast.aiInsights.operationalRecommendation}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* ── Floor Sales Orders ──────────────────────────────────────────── */}
      <div>
        <SalesOrderTable
          limit={10}
          defaultFromDate={todayStr}
          defaultToDate={todayStr}
          title="Floor Sales Orders Today"
        />
      </div>
    </div>
  );
}
