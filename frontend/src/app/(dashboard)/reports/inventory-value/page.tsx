'use client';
/**
 * ============================================================
 * INVENTORY VALUATION
 * ============================================================
 *
 * What the stock on hand is worth.
 *
 * This is a separate page from the inventory report on purpose. A Supervisor
 * may see which products need reordering — that is operational and they act on
 * it — but the valuation is the business's balance sheet and is gated on
 * INVENTORY_VALUE_VIEW, which the Supervisor role does not hold. The sidebar
 * hides the link, and the API refuses the call regardless.
 *
 * The figures come from GET /reports/inventory-value rather than being derived
 * in the browser from the product list: the endpoint is where the permission is
 * enforced, and computing valuation client-side would both bypass that and mean
 * a denied fetch still leaked every price to the page.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Boxes, Coins, TrendingUp, ShieldAlert } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import DataTable, { Column } from '@/components/ui/DataTable';
import ErrorState from '@/components/ui/ErrorState';
import Badge from '@/components/ui/Badge';
import apiClient, { fetchAllPages } from '@/lib/api/apiClient';
import { extractErrorMessage } from '@/lib/api/response';
import { formatCurrency } from '@/lib/utils';

interface ValuationLine {
  id: number;
  sku: string;
  name: string;
  unit?: string | null;
  quantity: number;
  reorderLevel?: number | null;
  categoryName?: string | null;
  purchasePrice: number;
  sellingPrice: number;
  costValue: number;
  retailValue: number;
}

interface InventoryValueReport {
  lines: ValuationLine[];
  totalUnits: number;
  totalCostValue: number;
  totalRetailValue: number;
  potentialMargin: number;
  skuCount: number;
}

export default function InventoryValueReportPage() {
  const [report, setReport] = useState<InventoryValueReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await apiClient.get('/reports/inventory-value');
      setReport(response.data?.data ?? response.data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load the inventory valuation.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const columns: Column<ValuationLine>[] = [
    {
      key: 'sku',
      label: 'SKU',
      render: (_: unknown, row) => (
        <span className="font-mono text-xs text-gray-500 dark:text-gray-400">{row.sku}</span>
      ),
    },
    { key: 'name', label: 'Product' },
    {
      key: 'categoryName',
      label: 'Category',
      render: (v: unknown) => (v ? String(v) : <span className="text-gray-400">—</span>),
    },
    {
      key: 'quantity',
      label: 'On Hand',
      render: (v: unknown, row) => (
        <span className="font-semibold">
          {Number(v ?? 0)} {row.unit || ''}
        </span>
      ),
    },
    {
      key: 'purchasePrice',
      label: 'Cost',
      render: (v: unknown) => formatCurrency(Number(v ?? 0)),
    },
    {
      key: 'costValue',
      label: 'Value at Cost',
      render: (v: unknown) => <span className="font-semibold">{formatCurrency(Number(v ?? 0))}</span>,
    },
    {
      key: 'retailValue',
      label: 'Value at Retail',
      render: (v: unknown) => <span className="text-emerald-600 font-semibold">{formatCurrency(Number(v ?? 0))}</span>,
    },
    {
      key: 'margin',
      label: 'Margin',
      render: (_: unknown, row) => {
        const margin = Number(row.retailValue ?? 0) - Number(row.costValue ?? 0);
        return (
          <Badge variant={margin > 0 ? 'success' : 'default'}>
            {formatCurrency(margin)}
          </Badge>
        );
      },
    },
  ];

  return (
    <div>
      <PageHeader
        title="Inventory Valuation"
        subtitle="What the stock on hand is worth at cost and at retail."
      />

      {error ? (
        <ErrorState message={error} retry={load} />
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              title="Stock Lines"
              value={String(report?.skuCount ?? 0)}
              icon={<Boxes size={20} />}
            />
            <StatCard
              title="Units on Hand"
              value={String(report?.totalUnits ?? 0)}
              icon={<Boxes size={20} />}
            />
            <StatCard
              title="Value at Cost"
              value={formatCurrency(Number(report?.totalCostValue ?? 0))}
              icon={<Coins size={20} />}
            />
            <StatCard
              title="Potential Margin"
              value={formatCurrency(Number(report?.potentialMargin ?? 0))}
              icon={<TrendingUp size={20} />}
            />
          </div>

          <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3">
            <ShieldAlert size={18} className="text-amber-600 dark:text-amber-300 shrink-0 mt-0.5" />
            <p className="text-sm text-amber-800 dark:text-amber-200">
              This report reveals cost prices and margin on every line. It is limited to roles that hold
              the inventory-value permission; Supervisors and Cashiers cannot open it.
            </p>
          </div>

          <DataTable
            columns={columns}
            data={report?.lines ?? []}
            loading={loading}
            searchable
            searchPlaceholder="Search by name or SKU..."
          />
        </div>
      )}
    </div>
  );
}