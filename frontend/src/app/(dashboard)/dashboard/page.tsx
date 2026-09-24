'use client';
import React from 'react';
import { BarChart3, Package, ShoppingCart, TruckIcon, AlertTriangle, DollarSign, TrendingUp, Users } from 'lucide-react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useProducts } from '@/hooks/useProducts';
import { useSales } from '@/hooks/useSales';
import { usePurchases } from '@/hooks/usePurchases';
import { useExpenses } from '@/hooks/useFinance';
import { StatCardSkeleton } from '@/components/ui/Skeleton';

const salesTrend = [
  { month: 'Apr', sales: 42000, purchases: 28000 },
  { month: 'May', sales: 55000, purchases: 32000 },
  { month: 'Jun', sales: 48000, purchases: 29000 },
  { month: 'Jul', sales: 63000, purchases: 41000 },
  { month: 'Aug', sales: 71000, purchases: 45000 },
  { month: 'Sep', sales: 63100, purchases: 37000 },
];

const topProducts = [
  { name: 'Basmati Rice', sales: 18250 },
  { name: 'Cooking Oil', sales: 14720 },
  { name: 'Sugar 50kg', sales: 12350 },
  { name: 'Wheat Flour', sales: 10900 },
  { name: 'Coffee 500g', sales: 8200 },
];

export default function DashboardPage() {
  const { data: products, isLoading: loadingProducts } = useProducts();
  const { data: sales, isLoading: loadingSales } = useSales();
  const { data: purchases, isLoading: loadingPurchases } = usePurchases();
  const { data: expenses } = useExpenses();

  const totalSales = sales?.reduce((s, o) => s + o.total, 0) ?? 0;
  const totalPurchases = purchases?.reduce((s, o) => s + o.total, 0) ?? 0;
  const totalExpenses = expenses?.reduce((s, e) => s + e.amount, 0) ?? 0;
  const grossProfit = totalSales - totalPurchases - totalExpenses;
  const lowStock = products?.filter((p: any) => p.quantity <= p.reorderLevel && p.quantity > 0) ?? [];
  const outOfStock = products?.filter((p: any) => p.quantity === 0) ?? [];
  const recentSales = (sales ?? []).slice(0, 5);
  const loading = loadingProducts || loadingSales || loadingPurchases;

  return (
    <div className="space-y-6">
      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {loading ? (
          Array.from({ length: 4 }).map((_, i) => <StatCardSkeleton key={i} />)
        ) : (
          <>
            <StatCard title="Total Sales" value={formatCurrency(totalSales)} change={8.2} changeLabel="vs last month" icon={<ShoppingCart size={22} />} color="blue" />
            <StatCard title="Total Purchases" value={formatCurrency(totalPurchases)} change={-3.1} changeLabel="vs last month" icon={<TruckIcon size={22} />} color="emerald" />
            <StatCard title="Gross Profit" value={formatCurrency(grossProfit)} change={12.5} changeLabel="vs last month" icon={<TrendingUp size={22} />} color="purple" />
            <StatCard title="Total Expenses" value={formatCurrency(totalExpenses)} change={5.8} changeLabel="vs last month" icon={<DollarSign size={22} />} color="amber" />
          </>
        )}
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <Card className="xl:col-span-2">
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">Sales & Purchases Trend</h3>
          </CardHeader>
          <CardContent>
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
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">Top Products</h3>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={topProducts} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
                <XAxis type="number" tick={{ fontSize: 11 }} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={90} />
                <Tooltip formatter={(v: number) => formatCurrency(v)} />
                <Bar dataKey="sales" fill="#2563EB" radius={[0, 4, 4, 0]} name="Sales" />
              </BarChart>
            </ResponsiveContainer>
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
            <table className="w-full text-sm">
              <thead className="bg-gray-50 dark:bg-gray-800/50">
                <tr>
                  {['Reference', 'Customer', 'Date', 'Total', 'Status'].map((h: any) => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                {recentSales.map(sale => (
                  <tr key={sale.id} className="hover:bg-gray-50 dark:hover:bg-gray-700/30">
                    <td className="px-4 py-3 font-mono text-xs text-blue-600">{sale.reference}</td>
                    <td className="px-4 py-3 text-gray-700 dark:text-gray-300">{sale.customerName}</td>
                    <td className="px-4 py-3 text-gray-500">{formatDate(sale.date || '')}</td>
                    <td className="px-4 py-3 font-medium text-gray-800 dark:text-gray-200">{formatCurrency(sale.total || 0)}</td>
                    <td className="px-4 py-3">
                      <Badge variant={sale.status === 'delivered' ? 'success' : sale.status === 'cancelled' ? 'danger' : sale.status === 'confirmed' ? 'info' : 'default'}>
                        {sale.status}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>

        {/* Alerts */}
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
                {outOfStock.map((p: any) => (
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
                {lowStock.slice(0, 5).map((p: any) => (
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
