'use client';

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, Package, TrendingUp, Warehouse } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent } from '@/components/ui/Card';
import ReportScreen from '@/components/reports/ReportScreen';
import { reportsApi } from '@/lib/api/aiApi';
import { formatCurrency } from '@/lib/utils';

/**
 * Stock: where it is, how it got there, and what went missing.
 *
 * <h2>What changed and why it mattered</h2>
 *
 * <p>The category summary this screen used to show was built in the browser by
 * multiplying each product's quantity by its {@code purchasePrice}. That is the
 * product's <em>current</em> purchase price, so the valuation moved whenever a
 * price was edited, and it ignored the fact that stock sits in several
 * warehouses at different average costs. It also could not say where any of the
 * stock was.
 *
 * <p>Everything on this screen now reads from the reports service, which values
 * stock at the moving weighted average actually held per warehouse and keeps the
 * movement ledger as the record of how it got there. Four views:
 * movements, transfers, shrinkage and slow-moving stock.
 */

type View = 'movements' | 'transfers' | 'shrinkage' | 'slow-moving';

const VIEWS: Array<{ key: View; label: string; note: string }> = [
  {
    key: 'movements',
    label: 'Movement history',
    note: 'Every approved movement, with where the units came from and went to.',
  },
  {
    key: 'transfers',
    label: 'Warehouse transfers',
    note:
      'Stock moving between warehouses the business already owns. A transfer is neither a sale nor a purchase, and it changes no total.',
  },
  {
    key: 'shrinkage',
    label: 'Adjustments & losses',
    note:
      'Damage, expiry and stock adjustments — the movements that took stock away.',
  },
  {
    key: 'slow-moving',
    label: 'Slow-moving',
    note: 'Stock with a value sitting in the warehouse that nothing has sold.',
  },
];

export default function InventoryReportPage() {
  const [view, setView] = useState<View>('movements');

  // The portfolio summary is taken from the valuation report rather than
  // recomputed here, so the number on this page and the number on the valuation
  // report are the same number.
  const { data: valuation } = useQuery({
    queryKey: ['report', 'inventory-valuation'],
    queryFn: () => reportsApi.get('inventory-valuation', {}),
    retry: false,
  });

  const rows: any[] = valuation?.rows ?? [];
  const retail = rows.reduce((sum, r) => sum + Number(r.valueAtRetail ?? 0), 0);
  const cost = Number(valuation?.totals?.totalValueAtCost ?? 0);
  const units = Number(valuation?.totals?.totalUnits ?? 0);

  // Reorder levels are not part of a valuation — it answers "what is this
  // worth", not "is this enough" — so the low-stock figure is read from the
  // report that does compare quantity to each product's reorder level. Reading
  // it off a valuation row could only ever compare against `undefined`, which
  // silently reduced "low or out of stock" to "quantity is zero".
  const { data: stock } = useQuery({
    queryKey: ['report', 'inventory-low-stock'],
    queryFn: () => reportsApi.get('inventory', {}),
    retry: false,
  });
  const needsReordering = stock
    ? Number(stock.lowStockCount ?? 0) + Number(stock.outOfStockCount ?? 0)
    : null;

  // Grouped by the warehouse the stock actually sits in. This keyed on the first
  // word of the product *name*, so a panel headed "Where the value sits — stock
  // is valued per warehouse" was really ranking products called "Coffee…",
  // "Tea…" and "Sugar…" by value.
  const byWarehouse = rows.reduce<Record<string, { units: number; cost: number }>>(
    (acc, row) => {
      const key = String(row.warehouse ?? 'Unassigned');
      acc[key] ??= { units: 0, cost: 0 };
      acc[key].units += Number(row.quantity ?? 0);
      acc[key].cost += Number(row.valueAtCost ?? 0);
      return acc;
    },
    {},
  );

  const active = VIEWS.find((v) => v.key === view)!;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Inventory"
        subtitle="Where stock is, how it got there, and what it is worth — all from the movement ledger"
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Stock at Cost"
          value={valuation ? formatCurrency(cost) : '…'}
          changeLabel="moving weighted average"
          color="blue"
          icon={<Warehouse size={20} />}
        />
        <StatCard
          title="Units on Hand"
          value={valuation ? units.toLocaleString() : '…'}
          changeLabel={`${rows.length} product/warehouse lines`}
          color="purple"
          icon={<Package size={20} />}
        />
        <StatCard
          title="Unrealised Margin"
          value={valuation ? formatCurrency(retail - cost) : '…'}
          changeLabel="if everything sold at list"
          color="emerald"
          icon={<TrendingUp size={20} />}
        />
        <StatCard
          title="Low or Out of Stock"
          value={needsReordering === null ? '…' : String(needsReordering)}
          changeLabel="products at or below their reorder level"
          color="red"
          icon={<AlertTriangle size={20} />}
        />
      </div>

      {/* ── Portfolio by warehouse line count ──────────────────────────── */}
      <Card>
        <CardContent className="p-0">
          <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
            <h3 className="text-sm font-bold text-gray-900 dark:text-gray-100">
              Where the value sits
            </h3>
            <p className="text-xs text-gray-400">
              Stock is valued per warehouse at the average cost actually paid there.
            </p>
          </div>
          <div className="px-4 py-3">
            {Object.keys(byWarehouse).length === 0 ? (
              <p className="py-4 text-center text-xs text-gray-400">
                Nothing is currently held in stock.
              </p>
            ) : (
              <dl className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-x-6 gap-y-2 text-sm">
                {Object.entries(byWarehouse)
                  .sort((a, b) => b[1].cost - a[1].cost)
                  .slice(0, 10)
                  .map(([key, stats]) => (
                    <div key={key} className="min-w-0">
                      <dt className="text-xs text-gray-400 truncate">{key}</dt>
                      <dd className="tabular-nums text-gray-800 dark:text-gray-200">
                        {formatCurrency(stats.cost)}
                      </dd>
                      <dd className="text-[11px] text-gray-400">
                        {stats.units.toLocaleString()} units
                      </dd>
                    </div>
                  ))}
              </dl>
            )}
          </div>
        </CardContent>
      </Card>

      {/* ── Views ───────────────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center gap-1 border-b border-gray-200 dark:border-gray-700">
        {VIEWS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setView(item.key)}
            aria-pressed={view === item.key}
            className={`px-3 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              view === item.key
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      <p className="text-xs text-gray-400">{active.note}</p>

      {view === 'movements' && (
        <ReportScreen
          title="Movement History"
          hideHeader
          report="movements"
          columns={[
            { key: 'date', label: 'Date' },
            { key: 'type', label: 'Type' },
            { key: 'product', label: 'Product' },
            { key: 'from', label: 'From' },
            { key: 'to', label: 'To' },
            { key: 'quantity', label: 'Quantity', numeric: true },
            { key: 'unitCost', label: 'Unit cost', money: true },
            { key: 'reference', label: 'Reference' },
            { key: 'reason', label: 'Reason' },
            { key: 'recordedBy', label: 'Recorded by' },
          ]}
        />
      )}

      {view === 'transfers' && (
        <ReportScreen
          title="Warehouse Transfers"
          hideHeader
          report="transfers"
          columns={[
            { key: 'date', label: 'Date' },
            { key: 'product', label: 'Product' },
            { key: 'from', label: 'From' },
            { key: 'to', label: 'To' },
            { key: 'quantity', label: 'Quantity', numeric: true },
            { key: 'reference', label: 'Reference' },
            { key: 'recordedBy', label: 'Recorded by' },
          ]}
        />
      )}

      {view === 'shrinkage' && (
        <ReportScreen
          title="Adjustments, Damage & Expiry"
          hideHeader
          report="shrinkage"
          columns={[
            { key: 'date', label: 'Date' },
            { key: 'type', label: 'Type' },
            { key: 'product', label: 'Product' },
            { key: 'warehouse', label: 'Warehouse' },
            { key: 'quantity', label: 'Units lost', numeric: true },
            { key: 'unitCost', label: 'Unit cost', money: true },
            { key: 'value', label: 'Value lost', money: true },
            { key: 'reason', label: 'Reason' },
            { key: 'approvedBy', label: 'Approved by' },
            { key: 'reference', label: 'Reference' },
          ]}
        />
      )}

      {view === 'slow-moving' && (
        <ReportScreen
          title="Slow-moving Stock"
          hideHeader
          report="slow-moving"
          dateFiltered={false}
          extraParams={{ lookbackDays: 60 }}
          columns={[
            { key: 'sku', label: 'SKU' },
            { key: 'product', label: 'Product' },
            { key: 'quantity', label: 'On hand', numeric: true },
            { key: 'unitCost', label: 'Unit cost', money: true },
            { key: 'valueAtCost', label: 'Value at cost', money: true },
            { key: 'reorderLevel', label: 'Reorder level' },
          ]}
        />
      )}
    </div>
  );
}