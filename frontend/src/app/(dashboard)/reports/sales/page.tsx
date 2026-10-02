'use client';
import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, AreaChart, Area
} from 'recharts';
import { Download, TrendingUp, ShoppingBag, DollarSign } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import Select from '@/components/ui/Select';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency } from '@/lib/utils';
import { salesApi } from '@/lib/api/salesApi';

function saleTotal(s: any) {
  return Number(s.finalAmount ?? s.total ?? s.totalAmount ?? 0);
}

function isCancelled(s: any) {
  const st = String(s.status || '').toUpperCase();
  return st === 'CANCELLED';
}

function daysInTimeframe(timeframe: string) {
  if (timeframe === '7d') return 7;
  if (timeframe === '90d') return 90;
  return 30;
}

function buildDailyTrend(sales: any[], days: number) {
  const buckets: Record<string, { date: string; sales: number; orders: number; sortKey: number }> = {};
  const now = new Date();
  now.setHours(0, 0, 0, 0);

  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now);
    d.setDate(now.getDate() - i);
    const key = d.toISOString().slice(0, 10);
    const label = d.toLocaleDateString(undefined, { month: 'short', day: '2-digit' });
    buckets[key] = { date: label, sales: 0, orders: 0, sortKey: d.getTime() };
  }

  for (const sale of sales) {
    if (isCancelled(sale)) continue;
    const d = new Date(sale.createdAt || sale.date || '');
    if (isNaN(d.getTime())) continue;
    const key = d.toISOString().slice(0, 10);
    if (buckets[key]) {
      buckets[key].sales += saleTotal(sale);
      buckets[key].orders += 1;
    }
  }

  return Object.values(buckets).sort((a, b) => a.sortKey - b.sortKey);
}

function buildTopProducts(sales: any[]) {
  const tally: Record<string, { name: string; revenue: number; units: number }> = {};
  for (const order of sales) {
    if (isCancelled(order)) continue;
    for (const item of order.items || []) {
      const name = item.productName || 'Unknown';
      if (!tally[name]) tally[name] = { name, revenue: 0, units: 0 };
      tally[name].revenue += Number(item.subtotal ?? item.total ?? 0);
      tally[name].units += Number(item.quantity ?? 0);
    }
  }
  return Object.values(tally)
    .sort((a, b) => b.revenue - a.revenue)
    .slice(0, 5);
}

function filterByTimeframe(sales: any[], days: number) {
  const cutoff = Date.now() - days * 86400000;
  return sales.filter(s => {
    const d = new Date(s.createdAt || s.date || '');
    return !isNaN(d.getTime()) && d.getTime() >= cutoff;
  });
}

export default function SalesReportPage() {
  const [timeframe, setTimeframe] = useState('30d');
  const days = daysInTimeframe(timeframe);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['sales-report', timeframe],
    queryFn: () => {
      const to = new Date();
      const from = new Date(to.getTime() - days * 86400000);
      return salesApi.report({ from: from.toISOString(), to: to.toISOString() });
    },
  });

  const sales: any[] = data?.sales ?? [];
  const summary = data?.summary ?? {};

  const activeSales = useMemo(() => sales.filter(s => !isCancelled(s)), [sales]);

  // Prefer the backend-computed summary; fall back to client aggregation.
  const totalSales = Number(summary.netSales ?? activeSales.reduce((acc, s) => acc + saleTotal(s), 0));
  const totalOrders = Number(summary.transactionCount ?? activeSales.length);
  const avgOrderValue = totalOrders > 0 ? Math.round(totalSales / totalOrders) : 0;

  const salesTrendData = useMemo(() => buildDailyTrend(activeSales, days), [activeSales, days]);
  const topProductsData = useMemo(() => buildTopProducts(activeSales), [activeSales]);
  const maxRevenue = topProductsData[0]?.revenue || 1;

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Sales"
        subtitle="In-depth analysis of sales volume, order averages, top selling items, and revenue trends"
        actions={
          <div className="flex items-center gap-2">
            <Select
              options={[
                { value: '7d', label: 'Last 7 Days' },
                { value: '30d', label: 'Last 30 Days' },
                { value: '90d', label: 'Last 90 Days' },
              ]}
              value={timeframe}
              onChange={e => setTimeframe(e.target.value)}
              className="w-40 text-xs"
            />
            <Button variant="outline" size="sm" onClick={() => window.print()}>
              <Download size={14} /> Export Report
            </Button>
          </div>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          title="Total Sales Volume"
          value={isLoading ? '…' : formatCurrency(totalSales)}
          changeLabel={`last ${days} days`}
          color="blue"
          icon={<DollarSign size={20} />}
        />
        <StatCard
          title="Total Orders Completed"
          value={isLoading ? '…' : `${totalOrders} Orders`}
          changeLabel={`last ${days} days`}
          color="emerald"
          icon={<ShoppingBag size={20} />}
        />
        <StatCard
          title="Average Order Value (AOV)"
          value={isLoading ? '…' : formatCurrency(avgOrderValue)}
          changeLabel="from filtered sales"
          color="purple"
          icon={<TrendingUp size={20} />}
        />
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatCard title="Items Sold" value={isLoading ? '…' : `${summary.itemsSold ?? 0}`} changeLabel={`last ${days} days`} color="blue" icon={<ShoppingBag size={20} />} />
        <StatCard title="Discounts" value={isLoading ? '…' : formatCurrency(Number(summary.discount ?? 0))} changeLabel={`last ${days} days`} color="purple" icon={<DollarSign size={20} />} />
        <StatCard title="Tax Collected" value={isLoading ? '…' : formatCurrency(Number(summary.tax ?? 0))} changeLabel={`last ${days} days`} color="emerald" icon={<DollarSign size={20} />} />
        <StatCard title="Gross Profit" value={isLoading ? '…' : formatCurrency(Number(summary.grossProfit ?? 0))} changeLabel="net sales − COGS" color="emerald" icon={<TrendingUp size={20} />} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-8 bg-white dark:bg-gray-800 p-5 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm">
          <div className="flex justify-between items-center mb-4">
            <div>
              <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">Revenue Trajectory</h3>
              <p className="text-xs text-gray-400">Daily gross turnover (ETB)</p>
            </div>
          </div>
          <div className="h-72">
            {salesTrendData.every(d => d.sales === 0) ? (
              <div className="h-full flex items-center justify-center text-gray-400 text-sm">
                No sales in this period — create sale orders to see the trend.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={salesTrendData}>
                  <defs>
                    <linearGradient id="salesGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#2563EB" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#2563EB" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#374151" opacity={0.15} />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} tickFormatter={v => `${v / 1000}k`} />
                  <Tooltip formatter={(v: any) => [formatCurrency(Number(v)), 'Sales']} />
                  <Area type="monotone" dataKey="sales" stroke="#2563EB" strokeWidth={2.5} fillOpacity={1} fill="url(#salesGrad)" />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        <div className="lg:col-span-4 bg-white dark:bg-gray-800 p-5 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100 mb-1">Top Selling Items</h3>
            <p className="text-xs text-gray-400 mb-4">Ranked by total revenue generated</p>

            {topProductsData.length === 0 ? (
              <p className="text-sm text-gray-400 py-8 text-center">No product sales in this period.</p>
            ) : (
              <div className="space-y-3">
                {topProductsData.map((item, idx) => (
                  <div key={idx} className="space-y-1">
                    <div className="flex justify-between text-xs">
                      <span className="font-medium text-gray-800 dark:text-gray-200 truncate max-w-[170px]">{item.name}</span>
                      <span className="font-bold text-blue-600 dark:text-blue-400">{formatCurrency(item.revenue)}</span>
                    </div>
                    <div className="flex justify-between text-[10px] text-gray-400">
                      <span>{item.units} units sold</span>
                    </div>
                    <div className="w-full bg-gray-100 dark:bg-gray-700 h-1.5 rounded-full overflow-hidden">
                      <div
                        className="bg-blue-600 h-full rounded-full"
                        style={{ width: `${(item.revenue / maxRevenue) * 100}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm">
        <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100 mb-1">Sales Detail</h3>
        <p className="text-xs text-gray-400 mb-4">Every persisted sale with its products, quantities, prices, and totals</p>

        {activeSales.length === 0 ? (
          <p className="text-sm text-gray-400 py-8 text-center">No sales in this period.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="text-left text-gray-400 border-b border-gray-100 dark:border-gray-700">
                  <th className="pb-2 pr-4 font-medium">Sale</th>
                  <th className="pb-2 pr-4 font-medium">Date</th>
                  <th className="pb-2 pr-4 font-medium">Customer</th>
                  <th className="pb-2 pr-4 font-medium">Seller</th>
                  <th className="pb-2 pr-4 font-medium">Products</th>
                  <th className="pb-2 pr-4 font-medium">Subtotal</th>
                  <th className="pb-2 pr-4 font-medium">Discount</th>
                  <th className="pb-2 pr-4 font-medium">Tax</th>
                  <th className="pb-2 pr-4 font-medium">Total</th>
                  <th className="pb-2 font-medium">Payment</th>
                </tr>
              </thead>
              <tbody>
                {activeSales.map((s: any) => (
                  <tr key={s.id} className="border-b border-gray-50 dark:border-gray-700/50 align-top">
                    <td className="py-2 pr-4 font-mono font-semibold text-gray-800 dark:text-gray-200">{s.orderNumber ?? s.reference ?? s.id}</td>
                    <td className="py-2 pr-4 text-gray-500">{s.date ?? (s.createdAt ? String(s.createdAt).slice(0, 10) : '—')}</td>
                    <td className="py-2 pr-4 text-gray-700 dark:text-gray-300">{s.customerName ?? '—'}</td>
                    <td className="py-2 pr-4 text-gray-500">{s.createdBy ?? '—'}</td>
                    <td className="py-2 pr-4 text-gray-600 dark:text-gray-400">
                      {(s.items ?? []).map((it: any, idx: number) => (
                        <div key={idx}>
                          {it.productName ?? `Product #${it.productId}`} × {it.quantity} @ {formatCurrency(Number(it.unitPrice ?? 0))}
                        </div>
                      ))}
                    </td>
                    <td className="py-2 pr-4">{formatCurrency(Number(s.totalAmount ?? 0))}</td>
                    <td className="py-2 pr-4">{formatCurrency(Number(s.discount ?? 0))}</td>
                    <td className="py-2 pr-4">{formatCurrency(Number(s.tax ?? 0))}</td>
                    <td className="py-2 pr-4 font-bold text-gray-900 dark:text-gray-100">{formatCurrency(saleTotal(s))}</td>
                    <td className="py-2 text-gray-500">{s.paymentMethod ?? '—'} / {s.paymentStatus ?? s.status ?? '—'}</td>
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
