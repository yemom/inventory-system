'use client';
import React from 'react';
import {
  Package, AlertTriangle, TrendingUp, Download, Warehouse
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import Button from '@/components/ui/Button';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency } from '@/lib/utils';
import { useProducts } from '@/hooks/useProducts';

export default function InventoryReportPage() {
  const { data: products = [], isLoading, error, refetch } = useProducts();

  const unitCost = (p: any) => Number(p.purchasePrice ?? p.costPrice ?? 0);
  const categoryOf = (p: any) => String(p.categoryName || p.category || 'Uncategorized');

  const totalItemsCount = products.reduce((acc, p) => acc + Number(p.quantity || 0), 0);
  const totalCostValuation = products.reduce((acc, p) => acc + Number(p.quantity || 0) * unitCost(p), 0);
  const totalRetailValuation = products.reduce(
    (acc, p) => acc + Number(p.quantity || 0) * Number(p.sellingPrice || 0),
    0
  );
  const potentialProfit = totalRetailValuation - totalCostValuation;

  const lowStockItems = products.filter(
    p => Number(p.quantity) > 0 && p.reorderLevel != null && Number(p.quantity) <= Number(p.reorderLevel)
  );
  const outOfStockItems = products.filter(p => Number(p.quantity) <= 0);

  const categorySummary = products.reduce<
    Record<string, { count: number; qty: number; costVal: number; retailVal: number }>
  >((acc, p) => {
    const cat = categoryOf(p);
    if (!acc[cat]) {
      acc[cat] = { count: 0, qty: 0, costVal: 0, retailVal: 0 };
    }
    acc[cat].count += 1;
    acc[cat].qty += Number(p.quantity || 0);
    acc[cat].costVal += Number(p.quantity || 0) * unitCost(p);
    acc[cat].retailVal += Number(p.quantity || 0) * Number(p.sellingPrice || 0);
    return acc;
  }, {});

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Inventory"
        subtitle="Current asset valuation of all warehouse inventory, stock level health, and category distribution"
        actions={
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Download size={14} /> Export Inventory Sheet
          </Button>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Stock Asset Value (Cost)"
          value={isLoading ? '…' : formatCurrency(totalCostValuation)}
          color="blue"
          icon={<Warehouse size={20} />}
        />
        <StatCard
          title="Potential Sales Value"
          value={isLoading ? '…' : formatCurrency(totalRetailValuation)}
          color="purple"
          icon={<TrendingUp size={20} />}
        />
        <StatCard
          title="Unrealized Profit in Stock"
          value={isLoading ? '…' : formatCurrency(potentialProfit)}
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
              {Object.keys(categorySummary).length === 0 ? (
                <tr>
                  <td colSpan={6} className="p-8 text-center text-gray-400">
                    {isLoading ? 'Loading products…' : 'No products yet.'}
                  </td>
                </tr>
              ) : (
                Object.entries(categorySummary).map(([cat, stats]) => (
                  <tr key={cat} className="hover:bg-gray-50/50 dark:hover:bg-gray-700/30">
                    <td className="p-3 font-semibold text-gray-900 dark:text-gray-100">{cat}</td>
                    <td className="p-3 text-center font-mono">{stats.count}</td>
                    <td className="p-3 text-center font-mono">{stats.qty}</td>
                    <td className="p-3 text-right font-medium">{formatCurrency(stats.costVal)}</td>
                    <td className="p-3 text-right font-bold text-blue-600 dark:text-blue-400">{formatCurrency(stats.retailVal)}</td>
                    <td className="p-3 text-right font-bold text-emerald-600">{formatCurrency(stats.retailVal - stats.costVal)}</td>
                  </tr>
                ))
              )}
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
