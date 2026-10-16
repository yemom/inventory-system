'use client';
import React, { useMemo } from 'react';
import Link from 'next/link';
import {
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  Boxes,
  CheckCircle2,
  Clock,
  Layers,
  Package,
  PackageCheck,
  PackageMinus,
  PackagePlus,
  PackageX,
  RefreshCw,
  Sparkles,
  TrendingDown,
  Warehouse,
} from 'lucide-react';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import { StatCardSkeleton } from '@/components/ui/Skeleton';
import { useProducts } from '@/hooks/useProducts';
import { useStockoutRisks, useReorderRecommendations } from '@/hooks/useAiDashboard';
import { useAuth } from '@/lib/auth/AuthProvider';
import { formatCurrency } from '@/lib/utils';

export default function InventoryStaffDashboard() {
  const { user } = useAuth();
  const { data: products = [], isLoading: loadingProducts, refetch: refetchProducts } = useProducts();
  const stockoutQuery = useStockoutRisks(30);
  const reorderQuery = useReorderRecommendations(20);

  const stockStats = useMemo(() => {
    let totalItems = 0;
    let totalUnits = 0;
    const lowStock = [];
    const outOfStock = [];

    for (const p of products) {
      totalItems++;
      totalUnits += p.quantity ?? 0;
      if (p.quantity === 0) {
        outOfStock.push(p);
      } else if (p.reorderLevel && p.quantity <= p.reorderLevel) {
        lowStock.push(p);
      }
    }

    return { totalItems, totalUnits, lowStock, outOfStock };
  }, [products]);

  const stockouts = stockoutQuery.data ?? [];
  const reorders = reorderQuery.data ?? [];
  const isRefreshing = loadingProducts || stockoutQuery.isFetching || reorderQuery.isFetching;

  const handleRefresh = () => {
    refetchProducts();
    stockoutQuery.refetch();
    reorderQuery.refetch();
  };

  return (
    <div className="space-y-6">
      {/* ── Inventory Staff Header ──────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-emerald-100 bg-gradient-to-r from-emerald-50/70 via-teal-50/50 to-white dark:border-emerald-900/30 dark:from-emerald-950/20 dark:via-gray-900 dark:to-gray-900 p-4 sm:p-5">
        <div className="flex items-center gap-3 sm:gap-4">
          <div className="rounded-xl bg-emerald-600 p-3 text-white shadow-sm">
            <Warehouse size={24} />
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl sm:text-2xl font-bold text-gray-900 dark:text-gray-100">
                Warehouse Inventory: {user?.fullName || 'Stock Controller'}
              </h1>
              <Badge variant="success">Inventory Operations</Badge>
            </div>
            <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
              Real-time stock on hand, replenishment advice, out-of-stock items, and AI stockout risk projections.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={isRefreshing}
          >
            <RefreshCw size={14} className={isRefreshing ? 'animate-spin' : ''} />
            <span className="ml-1">Refresh Stock</span>
          </Button>
          {/*
            Points at the adjustments list, which owns the "New Adjustment"
            form. There is no /inventory/adjustments/new route — this link
            404'd — and duplicating the form here would be a second way to
            post the same thing, free to drift from the first.
          */}
          <Link href="/inventory/adjustments">
            <Button size="sm">
              <PackagePlus size={14} className="mr-1" /> New Adjustment
            </Button>
          </Link>
        </div>
      </div>

      {/* ── Stock Level KPI Cards ───────────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {loadingProducts ? (
          Array.from({ length: 4 }).map((_, i) => <StatCardSkeleton key={i} />)
        ) : (
          <>
            <StatCard
              title="Catalog Items"
              value={stockStats.totalItems}
              changeLabel={`${stockStats.totalUnits.toLocaleString()} total units in stock`}
              icon={<Boxes size={22} />}
              color="blue"
            />
            <StatCard
              title="Healthy Stock Items"
              value={stockStats.totalItems - stockStats.lowStock.length - stockStats.outOfStock.length}
              changeLabel="Above reorder thresholds"
              icon={<CheckCircle2 size={22} />}
              color="emerald"
            />
            <StatCard
              title="Low Stock Items"
              value={stockStats.lowStock.length}
              changeLabel="At or below reorder level"
              icon={<AlertTriangle size={22} />}
              color="amber"
            />
            <StatCard
              title="Out of Stock"
              value={stockStats.outOfStock.length}
              changeLabel="Requires immediate replenishment"
              icon={<PackageX size={22} />}
              color="red"
            />
          </>
        )}
      </div>

      {/* ── Stockout Risk Projections (AI Derived) ──────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card className="border-l-4 border-l-red-500">
          <CardHeader>
            <div className="flex items-center justify-between">
              <h2 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                <TrendingDown size={16} className="text-red-500" />
                AI Stockout Risk Projections (Next 30 Days)
              </h2>
              <span className="text-xs text-gray-400">Demand Velocity vs Stock</span>
            </div>
          </CardHeader>
          <CardContent>
            {stockoutQuery.isLoading ? (
              <p className="py-8 text-center text-xs text-gray-500">Calculating burn rates...</p>
            ) : stockouts.length === 0 ? (
              <div className="py-8 text-center text-sm text-gray-500">
                <CheckCircle2 size={24} className="mx-auto text-emerald-500 mb-1" />
                <p className="font-medium">No imminent stockout risks detected</p>
                <p className="text-xs text-gray-400">Current stock levels cover forecasted 30-day demand.</p>
              </div>
            ) : (
              <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1">
                {stockouts.map((risk) => (
                  <div
                    key={risk.productId}
                    className="flex items-center justify-between rounded-lg border border-red-100 bg-red-50/40 p-2.5 text-xs dark:border-red-900/30 dark:bg-red-950/20"
                  >
                    <div>
                      <p className="font-medium text-red-900 dark:text-red-200">{risk.name}</p>
                      <p className="text-[11px] font-mono text-gray-500">
                        SKU: {risk.sku} • On hand: {risk.stockOnHand} units • Daily burn: {risk.averageDailyUnits.toFixed(1)}/day
                      </p>
                    </div>
                    <div className="text-right">
                      <Badge variant={risk.daysOfStockRemaining !== null && risk.daysOfStockRemaining <= 7 ? 'danger' : 'warning'}>
                        {risk.daysOfStockRemaining === 0 ? 'Empty' : `~${risk.daysOfStockRemaining ?? '?'} days left`}
                      </Badge>
                      {risk.summary && (
                        <p className="text-[10px] text-gray-400 mt-0.5 max-w-[140px] truncate">
                          {risk.summary}
                        </p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Reorder Recommendations */}
        <Card className="border-l-4 border-l-blue-500">
          <CardHeader>
            <div className="flex items-center justify-between">
              <h2 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
                <Sparkles size={16} className="text-blue-500" />
                Recommended Reorder Quantities
              </h2>
              <Link href="/purchases/new" className="text-xs text-blue-600 hover:underline">
                Create PO
              </Link>
            </div>
          </CardHeader>
          <CardContent>
            {reorderQuery.isLoading ? (
              <p className="py-8 text-center text-xs text-gray-500">Calculating safety stocks...</p>
            ) : reorders.length === 0 ? (
              <div className="py-8 text-center text-sm text-gray-500">
                <CheckCircle2 size={24} className="mx-auto text-emerald-500 mb-1" />
                <p className="font-medium">Inventory is properly balanced</p>
                <p className="text-xs text-gray-400">All items are above their calculated reorder points.</p>
              </div>
            ) : (
              <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1">
                {reorders.map((rec) => (
                  <div
                    key={rec.productId}
                    className="flex items-center justify-between rounded-lg border border-gray-100 p-2.5 text-xs dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50"
                  >
                    <div>
                      <p className="font-medium text-gray-900 dark:text-gray-100">{rec.name}</p>
                      <p className="text-[11px] font-mono text-gray-400">
                        SKU: {rec.sku} • Stock: {rec.stockOnHand} (Reorder point: {rec.reorderPoint})
                      </p>
                    </div>
                    <div className="text-right">
                      <span className="font-bold text-blue-600 dark:text-blue-400">
                        +{rec.suggestedQuantity} units
                      </span>
                      <p className="text-[10px] text-gray-400">
                        Lead: {rec.leadTimeDays}d • Safety: {rec.safetyStock}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* ── Operational Quick Navigation ─────────────────────────────────── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
        <Link href="/inventory" className="block">
          <Card className="hover:border-blue-400 transition-colors">
            <CardContent className="p-4 flex items-center gap-3">
              <div className="p-2.5 rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-900/30">
                <Layers size={20} />
              </div>
              <div>
                <p className="font-medium text-sm text-gray-900 dark:text-gray-100">Stock Catalog</p>
                <p className="text-xs text-gray-400">Browse all SKU balances</p>
              </div>
            </CardContent>
          </Card>
        </Link>

        <Link href="/inventory/adjustments" className="block">
          <Card className="hover:border-blue-400 transition-colors">
            <CardContent className="p-4 flex items-center gap-3">
              <div className="p-2.5 rounded-lg bg-emerald-50 text-emerald-600 dark:bg-emerald-900/30">
                <PackageCheck size={20} />
              </div>
              <div>
                <p className="font-medium text-sm text-gray-900 dark:text-gray-100">Adjustments</p>
                <p className="text-xs text-gray-400">Damaged & recount logging</p>
              </div>
            </CardContent>
          </Card>
        </Link>

        <Link href="/inventory/transfers" className="block">
          <Card className="hover:border-blue-400 transition-colors">
            <CardContent className="p-4 flex items-center gap-3">
              <div className="p-2.5 rounded-lg bg-purple-50 text-purple-600 dark:bg-purple-900/30">
                <Warehouse size={20} />
              </div>
              <div>
                <p className="font-medium text-sm text-gray-900 dark:text-gray-100">Transfers</p>
                <p className="text-xs text-gray-400">Inter-warehouse stock</p>
              </div>
            </CardContent>
          </Card>
        </Link>

        <Link href="/reports/inventory" className="block">
          <Card className="hover:border-blue-400 transition-colors">
            <CardContent className="p-4 flex items-center gap-3">
              <div className="p-2.5 rounded-lg bg-amber-50 text-amber-600 dark:bg-amber-900/30">
                <PackageMinus size={20} />
              </div>
              <div>
                <p className="font-medium text-sm text-gray-900 dark:text-gray-100">Movements Report</p>
                <p className="text-xs text-gray-400">Audit in/out ledger</p>
              </div>
            </CardContent>
          </Card>
        </Link>
      </div>
    </div>
  );
}
