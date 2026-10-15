'use client';

import React from 'react';
import ReportScreen from '@/components/reports/ReportScreen';
import StatCard from '@/components/ui/StatCard';
import { useWarehouses } from '@/hooks/useWarehouses';
import { formatCurrency } from '@/lib/utils';

/**
 * What the stock on hand is worth.
 *
 * <p>Valued at a moving weighted average, recalculated every time goods are
 * received: the average cost of everything held, not today's purchase price and
 * not the price the oldest batch was bought at. The method is named on the
 * screen rather than left implicit, because "what is this worth" has more than
 * one defensible answer and a reader is entitled to know which one is being
 * given.
 *
 * <p>Value at retail and potential margin are shown alongside, clearly labelled
 * as what <em>would</em> be realised if everything sold at list. It is not
 * money, and it is not in the financial reports.
 */
export default function InventoryValuePage() {
  const { data: warehouses = [] } = useWarehouses();
  const [warehouseId, setWarehouseId] = React.useState('');

  const filter = warehouseId
    ? (
        <label className="flex items-center gap-2 text-xs text-gray-500">
          Warehouse
          <select
            value={warehouseId}
            onChange={(event) => setWarehouseId(event.target.value)}
            className="rounded-md border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 px-2 py-1 text-xs"
          >
            <option value="">All warehouses</option>
            {(warehouses as any[]).map((w) => (
              <option key={w.id} value={String(w.id)}>
                {w.name ?? `Warehouse ${w.id}`}
              </option>
            ))}
          </select>
        </label>
      )
    : null;

  return (
    <ReportScreen
      title="Inventory Valuation"
      subtitle="Stock on hand at moving weighted-average cost, per warehouse"
      report="inventory-valuation"
      dateFiltered={false}
      extraParams={warehouseId ? { warehouseId } : {}}
      filters={filter}
      columns={[
        { key: 'sku', label: 'SKU' },
        { key: 'product', label: 'Product' },
        { key: 'warehouse', label: 'Warehouse' },
        { key: 'quantity', label: 'On hand', numeric: true },
        { key: 'unitCost', label: 'Unit cost', money: true },
        { key: 'valueAtCost', label: 'Value at cost', money: true },
        { key: 'unitPrice', label: 'List price', money: true },
        { key: 'valueAtRetail', label: 'Value at retail', money: true },
        { key: 'potentialMargin', label: 'Potential margin', money: true },
      ]}
      summary={(payload) => {
        const rows: any[] = payload.rows ?? [];
        const retail = rows.reduce(
          (sum, r) => sum + Number(r.valueAtRetail ?? 0),
          0,
        );
        const cost = Number(payload.totals?.totalValueAtCost ?? 0);
        const units = Number(payload.totals?.totalUnits ?? 0);
        return (
          <div className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <StatCard
                title="Stock at Cost"
                value={formatCurrency(cost)}
                changeLabel="what the business paid"
                color="blue"
              />
              <StatCard
                title="Units on Hand"
                value={units.toLocaleString()}
                changeLabel={`${rows.length} product/warehouse lines`}
                color="purple"
              />
              <StatCard
                title="Value at Retail"
                value={formatCurrency(retail)}
                changeLabel="if everything sold at list"
                color="emerald"
              />
              <StatCard
                title="Gross Margin on Hand"
                value={retail > 0 ? `${(((retail - cost) / retail) * 100).toFixed(1)}%` : '—'}
                changeLabel="not yet realised"
                color="amber"
              />
            </div>
            {payload.method && (
              <p className="text-xs text-gray-400">
                Costing method: {String(payload.method)}.
              </p>
            )}
          </div>
        );
      }}
    />
  );
}