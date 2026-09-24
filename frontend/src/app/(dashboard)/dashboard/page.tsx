'use client';
import React, { useEffect, useState } from 'react';
import { ShoppingCart, TruckIcon, AlertTriangle, DollarSign, TrendingUp, Package } from 'lucide-react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useProducts } from '@/hooks/useProducts';
import { useSales } from '@/hooks/useSales';
import { usePurchases } from '@/hooks/usePurchases';
import { StatCardSkeleton } from '@/components/ui/Skeleton';
import apiClient from '@/lib/api/apiClient';

// Derive monthly sales trend from real sale orders
function buildSalesTrend(sales: any[], purchases: any[]) {
  const monthNames = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  const now = new Date();
  const months: Record<string, { month: string; sales: number; purchases: number }> = {};

  // Build last 6 months buckets
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const key = `${d.getFullYear()}-${d.getMonth()}`;
    months[key] = { month: monthNames[d.getMonth()], sales: 0, purchases: 0 };
  }

  for (const sale of sales) {
    const d = new Date(sale.createdAt || sale.date || '');
    if (!isNaN(d.getTime())) {
      const key = `${d.getFullYear()}-${d.getMonth()}`;
      if (months[key]) months[key].sales += Number(sale.finalAmount || sale.total || 0);
    }
  }

  for (const po of purchases) {
    const d = new Date(po.createdAt || po.date || '');
    if (!isNaN(d.getTime())) {
      const key = `${d.getFullYear()}-${d.getMonth()}`;
      if (months[key]) months[key].purchases += Number(po.totalAmount || po.total || 0);
    }
  }

  return Object.values(months);
}

// Derive top products by total sales value from real sale orders
function buildTopProducts(sales: any[]) {
  const tally: Record<string, { name: string; sales: number }> = {};
  for (const order of sales) {
    for (const item of order.items || []) {
      const name = item.productName || 'Unknown';
      if (!tally[name]) tally[name] = { name, sales: 0 };
      tally[name].sales += Number(item.subtotal || item.total || 0);
    }
  }
  return Object.values(tally)
    .sort((a, b) => b.sales - a.sales)
    .slice(0, 5);
}

function errorMessage(err: unknown, fallback: string): string {
  if (!err) return fallback;
  const anyErr = err as { message?: string; response?: { data?: { message?: string } } };
  return anyErr.response?.data?.message || anyErr.message || fallback;
}

export default function DashboardPage() {
  const { data: products = [], isLoading: loadingProducts, error: productsError } = useProducts();
  const { data: sales = [], isLoading: loadingSales, error: salesError } = useSales();
  const { data: purchases = [], isLoading: loadingPurchases, error: purchasesError } = usePurchases();
  const [dashStats, setDashStats] = useState<any>(null);
  const [statsError, setStatsError] = useState<string | null>(null);

  // Fetch real dashboard stats from backend
  useEffect(() => {
    setStatsError(null);
    apiClient.get('/dashboard/stats')
      .then(res => {
        setDashStats(res.data?.data);
        setStatsError(null);
      })
      .catch((err) => {
        setDashStats(null);
        setStatsError(errorMessage(err, 'Failed to load dashboard stats'));
      });
  }, []);

  // ── KPI calculations from real data ──────────────────────────────────────
  const totalSales = dashStats?.totalRevenue ?? sales.reduce((s: number, o: any) => s + Number(o.finalAmount || o.total || 0), 0);
  const totalPurchases = dashStats?.totalPurchaseAmount ?? purchases.reduce((s: number, o: any) => s + Number(o.totalAmount || o.total || 0), 0);
  const grossProfit = dashStats?.grossProfit ?? (totalSales - totalPurchases);

  const lowStock = products.filter((p: any) => p.quantity > 0 && p.reorderLevel && p.quantity <= p.reorderLevel);
  const outOfStock = products.filter((p: any) => p.quantity === 0);
  const recentSales = (sales as any[]).slice(0, 5);

  const loading = loadingProducts || loadingSales || loadingPurchases;
  const dataErrors = [
    productsError && errorMessage(productsError, 'Failed to load products'),
    salesError && errorMessage(salesError, 'Failed to load sales'),
    purchasesError && errorMessage(purchasesError, 'Failed to load purchases'),
  ].filter(Boolean) as string[];

  // ── Chart data derived from real orders ───────────────────────────────────
  const salesTrend = buildSalesTrend(sales, purchases);
  const topProducts = buildTopProducts(sales);

  const statusVariant: Record<string, 'success' | 'info' | 'default' | 'danger'> = {
    PAID: 'success', PENDING: 'info', CANCELLED: 'danger',
    paid: 'success', pending: 'info', cancelled: 'danger',
  };

  return (
    <div className="space-y-6">
      {(statsError || dataErrors.length > 0) && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-sm text-amber-800 dark:text-amber-200 space-y-1">
          {statsError && (
            <p>Dashboard stats unavailable — showing values computed from sales/products. ({statsError})</p>
          )}
          {dataErrors.map((msg) => (
            <p key={msg}>{msg}</p>
          ))}
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {loading ? (
          Array.from({ length: 4 }).map((_, i) => <StatCardSkeleton key={i} />)
        ) : (
          <>
            <StatCard title="Total Revenue" value={formatCurrency(totalSales)} changeLabel="from sales" icon={<ShoppingCart size={22} />} color="blue" />
            <StatCard title="Total Purchases" value={formatCurrency(totalPurchases)} changeLabel="cost of goods" icon={<TruckIcon size={22} />} color="emerald" />
            <StatCard title="Gross Profit" value={formatCurrency(grossProfit)} changeLabel="revenue − purchases" icon={<TrendingUp size={22} />} color="purple" />
            <StatCard title="Products in Stock" value={String(dashStats?.activeProducts ?? products.filter((p: any) => p.active).length)} changeLabel={`${outOfStock.length} out of stock`} icon={<Package size={22} />} color="amber" />
          </>
        )}
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <Card className="xl:col-span-2">
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">Sales & Purchases Trend (Last 6 Months)</h3>
          </CardHeader>
          <CardContent>
            {salesTrend.every(m => m.sales === 0 && m.purchases === 0) ? (
              <div className="h-[260px] flex items-center justify-center text-gray-400 text-sm">
                No transaction data yet — create sales and purchases to see the trend.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <AreaChart data={salesTrend}>
                  <defs>
                    <linearGradient id="sales" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#2563EB" stopOpacity={0.15} />
                      <stop offset="95%" stopColor="#2563EB" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="purchases" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#10B981" stopOpacity={0.15} />
                      <stop offset="95%" stopColor="#10B981" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
                  <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                  <Tooltip formatter={(v: number) => formatCurrency(v)} />
                  <Area type="monotone" dataKey="sales" stroke="#2563EB" fill="url(#sales)" strokeWidth={2} name="Sales" />
                  <Area type="monotone" dataKey="purchases" stroke="#10B981" fill="url(#purchases)" strokeWidth={2} name="Purchases" />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">Top Products by Revenue</h3>
          </CardHeader>
          <CardContent>
            {topProducts.length === 0 ? (
              <div className="h-[260px] flex items-center justify-center text-gray-400 text-sm text-center px-4">
                No sales data yet — complete sales orders to see top products.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={topProducts} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
                  <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                  <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={90} />
                  <Tooltip formatter={(v: number) => formatCurrency(v)} />
                  <Bar dataKey="sales" fill="#2563EB" radius={[0, 4, 4, 0]} name="Revenue" />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Bottom Row */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        {/* Recent Sales */}
        <Card className="xl:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-gray-800 dark:text-gray-200">Recent Sales Orders</h3>
              <a href="/sales" className="text-sm text-blue-600 hover:underline">View all</a>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {recentSales.length === 0 ? (
              <div className="py-12 text-center text-gray-400 text-sm">
                No sales yet. Create your first sale order.
              </div>
            ) : (
              <table className="w-full text-sm">
                <thead className="bg-gray-50 dark:bg-gray-800/50">
                  <tr>
                    {['Reference', 'Customer', 'Date', 'Total', 'Status'].map((h: string) => (
                      <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                  {recentSales.map((sale: any) => (
                    <tr key={sale.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/30">
                      <td className="px-4 py-3 font-mono text-xs text-blue-600">{sale.reference || sale.orderNumber}</td>
                      <td className="px-4 py-3 text-gray-700 dark:text-gray-300">{sale.customerName}</td>
                      <td className="px-4 py-3 text-gray-500">{formatDate(sale.date || sale.createdAt || '')}</td>
                      <td className="px-4 py-3 font-medium text-gray-800 dark:text-gray-200">{formatCurrency(Number(sale.finalAmount || sale.total || 0))}</td>
                      <td className="px-4 py-3">
                        <Badge variant={statusVariant[sale.status] ?? 'default'}>{sale.status}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardContent>
        </Card>

        {/* Stock Alerts */}
        <Card>
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
              <AlertTriangle size={16} className="text-amber-500" /> Stock Alerts
            </h3>
          </CardHeader>
          <CardContent className="space-y-3">
            {outOfStock.length > 0 && (
              <div className="bg-red-50 dark:bg-red-900/20 rounded-lg p-3">
                <p className="text-xs font-semibold text-red-700 dark:text-red-400 mb-2">OUT OF STOCK ({outOfStock.length})</p>
                {(outOfStock as any[]).map((p: any) => (
                  <div key={p.id} className="flex justify-between items-center text-sm py-1">
                    <span className="text-gray-700 dark:text-gray-300 truncate">{p.name}</span>
                    <Badge variant="danger">0 {p.unit}</Badge>
                  </div>
                ))}
              </div>
            )}
            {lowStock.length > 0 && (
              <div className="bg-amber-50 dark:bg-amber-900/20 rounded-lg p-3">
                <p className="text-xs font-semibold text-amber-700 dark:text-amber-400 mb-2">LOW STOCK ({lowStock.length})</p>
                {(lowStock as any[]).slice(0, 5).map((p: any) => (
                  <div key={p.id} className="flex justify-between items-center text-sm py-1">
                    <span className="text-gray-700 dark:text-gray-300 truncate">{p.name}</span>
                    <Badge variant="warning">{p.quantity} {p.unit}</Badge>
                  </div>
                ))}
              </div>
            )}
            {lowStock.length === 0 && outOfStock.length === 0 && (
              <p className="text-sm text-gray-500 text-center py-4">All stock levels are healthy ✓</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
