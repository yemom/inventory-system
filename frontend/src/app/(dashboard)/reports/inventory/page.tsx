'use client';
import React, { useState } from 'react';
import { 
  Package, AlertTriangle, CheckCircle, TrendingUp, 
  Download, Warehouse, BarChart3, ShieldAlert 
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import { formatCurrency } from '@/lib/utils';
import { useProducts } from '@/hooks/useProducts';

export default function InventoryReportPage() {
  const { data: products = [], isLoading } = useProducts();

  // Valuation metrics
  const totalItemsCount = products.reduce((acc, p) => acc + p.quantity, 0);
  const totalCostValuation = products.reduce((acc, p) => acc + (p.quantity * p.costPrice), 0);
  const totalRetailValuation = products.reduce((acc, p) => acc + (p.quantity * p.sellingPrice), 0);
  const potentialProfit = totalRetailValuation - totalCostValuation;

  const lowStockItems = products.filter(p => p.quantity > 0 && p.quantity <= p.reorderLevel);
  const outOfStockItems = products.filter(p => p.quantity <= 0);

  // Group by category
  const categorySummary = products.reduce<Record<string, { count: number; qty: number; costVal: number; retailVal: number }>>((acc, p) => {
    if (!acc[p.category]) {
      acc[p.category] = { count: 0, qty: 0, costVal: 0, retailVal: 0 };
    }
    acc[p.category].count += 1;
    acc[p.category].qty += p.quantity;
    acc[p.category].costVal += p.quantity * p.costPrice;
    acc[p.category].retailVal += p.quantity * p.sellingPrice;
    return acc;
  }, {});

  return (
    <div className="space-y-6">
      <PageHeader 
        title="Inventory Valuation & Stock Status" 
        subtitle="Current asset valuation of all warehouse inventory, stock level health, and category distribution"
        actions={
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Download size={14} /> Export Inventory Sheet
          </Button>
        }
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard 
          title="Stock Asset Value (Cost)" 
          value={formatCurrency(totalCostValuation)} 
          color="blue" 
          icon={<Warehouse size={20} />} 
        />
        <StatCard 
          title="Potential Sales Value" 
          value={formatCurrency(totalRetailValuation)} 
          color="purple" 
          icon={<TrendingUp size={20} />} 
        />
        <StatCard 
          title="Unrealized Profit in Stock" 
          value={formatCurrency(potentialProfit)} 
          color="emerald" 
          icon={<Package size={20} />} 
        />
        <StatCard 
          title="Low & Depleted Stock" 
          value={`${lowStockItems.length + outOfStockItems.length} SKUs`} 
          color="red" 
          icon={<AlertTriangle size={20} />} 
        />
      </div>

      {/* Category Breakdown Table */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center bg-gray-50/50 dark:bg-gray-800/50">
          <div>
            <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">Category Valuation Summary</h3>
            <p className="text-xs text-gray-400">Total capital committed across product families</p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-xs text-left">
            <thead className="bg-gray-50 dark:bg-gray-700/60 text-gray-500 uppercase font-semibold">
              <tr>
                <th className="p-3">Category</th>
                <th className="p-3 text-center">SKU Count</th>
                <th className="p-3 text-center">Physical Units</th>
                <th className="p-3 text-right">Cost Valuation (ETB)</th>
                <th className="p-3 text-right">Retail Valuation (ETB)</th>
                <th className="p-3 text-right">Potential Gross Profit</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {Object.entries(categorySummary).map(([cat, stats]) => (
                <tr key={cat} className="hover:bg-gray-50/50 dark:hover:bg-gray-700/30">
                  <td className="p-3 font-semibold text-gray-900 dark:text-gray-100">{cat}</td>
                  <td className="p-3 text-center font-mono">{stats.count}</td>
                  <td className="p-3 text-center font-mono">{stats.qty}</td>
                  <td className="p-3 text-right font-medium">{formatCurrency(stats.costVal)}</td>
                  <td className="p-3 text-right font-bold text-blue-600 dark:text-blue-400">{formatCurrency(stats.retailVal)}</td>
                  <td className="p-3 text-right font-bold text-emerald-600">{formatCurrency(stats.retailVal - stats.costVal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-gray-50 dark:bg-gray-700 font-bold text-gray-900 dark:text-gray-100 border-t">
              <tr>
                <td className="p-3">Total Portfolio</td>
                <td className="p-3 text-center">{products.length}</td>
                <td className="p-3 text-center">{totalItemsCount}</td>
                <td className="p-3 text-right">{formatCurrency(totalCostValuation)}</td>
                <td className="p-3 text-right text-blue-600 dark:text-blue-400">{formatCurrency(totalRetailValuation)}</td>
                <td className="p-3 text-right text-emerald-600">{formatCurrency(potentialProfit)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </div>
  );
}
