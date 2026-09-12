'use client';
import React, { useState } from 'react';
import { 
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, AreaChart, Area 
} from 'recharts';
import { Download, Calendar, TrendingUp, ShoppingBag, DollarSign, Filter } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import Select from '@/components/ui/Select';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useSales } from '@/hooks/useSales';

const salesTrendData = [
  { date: 'Sep 01', sales: 8500, orders: 1 },
  { date: 'Sep 02', sales: 15600, orders: 1 },
  { date: 'Sep 03', sales: 22500, orders: 1 },
  { date: 'Sep 04', sales: 4550, orders: 1 },
  { date: 'Sep 05', sales: 12500, orders: 1 },
  { date: 'Sep 06', sales: 18400, orders: 2 },
  { date: 'Sep 07', sales: 29000, orders: 3 },
  { date: 'Sep 08', sales: 34100, orders: 4 },
  { date: 'Sep 09', sales: 21500, orders: 2 },
  { date: 'Sep 10', sales: 38900, orders: 5 },
  { date: 'Sep 11', sales: 42000, orders: 4 },
  { date: 'Sep 12', sales: 48500, orders: 6 },
];

const topProductsData = [
  { name: 'Basmati Rice 25kg', revenue: 25600, units: 18 },
  { name: 'Cooking Oil 20L', revenue: 10840, units: 12 },
  { name: 'Baby Diapers M (50pcs)', revenue: 8400, units: 16 },
  { name: 'Coffee 500g', revenue: 6560, units: 8 },
  { name: 'Sugar 50kg', revenue: 5200, units: 4 },
];

export default function SalesReportPage() {
  const { data: sales = [] } = useSales();
  const [timeframe, setTimeframe] = useState('30d');

  const totalSales = sales.reduce((acc, s) => acc + s.total, 0);
  const totalOrders = sales.length;
  const avgOrderValue = totalOrders > 0 ? Math.round(totalSales / totalOrders) : 0;

  return (
    <div className="space-y-6">
      <PageHeader 
        title="Sales Analytics & Reports" 
        subtitle="In-depth analysis of sales volume, order averages, top selling items, and revenue trends"
        actions={
          <div className="flex items-center gap-2">
            <Select
              options={[
                { value: '7d', label: 'Last 7 Days' },
                { value: '30d', label: 'Last 30 Days' },
                { value: '90d', label: 'This Quarter' },
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

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard 
          title="Total Sales Volume" 
          value={formatCurrency(totalSales)} 
          change={18.2} 
          changeLabel="vs last period"
          color="blue" 
          icon={<DollarSign size={20} />} 
        />
        <StatCard 
          title="Total Orders Completed" 
          value={`${totalOrders} Orders`} 
          change={12.0} 
          color="emerald" 
          icon={<ShoppingBag size={20} />} 
        />
        <StatCard 
          title="Average Order Value (AOV)" 
          value={formatCurrency(avgOrderValue)} 
          change={5.4} 
          color="purple" 
          icon={<TrendingUp size={20} />} 
        />
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Revenue Trend */}
        <div className="lg:col-span-8 bg-white dark:bg-gray-800 p-5 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm">
          <div className="flex justify-between items-center mb-4">
            <div>
              <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">Revenue Trajectory</h3>
              <p className="text-xs text-gray-400">Daily gross turnover (ETB)</p>
            </div>
          </div>
          <div className="h-72">
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
          </div>
        </div>

        {/* Top Selling Products */}
        <div className="lg:col-span-4 bg-white dark:bg-gray-800 p-5 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100 mb-1">Top Selling Items</h3>
            <p className="text-xs text-gray-400 mb-4">Ranked by total revenue generated</p>

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
                      style={{ width: `${(item.revenue / 26000) * 100}%` }} 
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
