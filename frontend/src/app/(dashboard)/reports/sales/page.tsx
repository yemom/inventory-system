'use client';

import React from 'react';
import { TrendingUp } from 'lucide-react';
import ReportScreen from '@/components/reports/ReportScreen';
import StatCard from '@/components/ui/StatCard';
import { formatCurrency } from '@/lib/utils';

/**
 * Sales and gross profit.
 *
 * <p>The cost column here is the cost recorded on each sale line at the moment
 * the sale happened. That is the whole reason this screen reads the backend
 * rather than listing orders and subtracting today's purchase price: a price
 * change must not be able to rewrite what a month was worth.
 *
 * <p>Revenue is shown net of approved returns and excludes sales tax. Tax
 * collected is money held on behalf of the authority, not income, so including
 * it here would inflate every margin on the page.
 */
export default function SalesReportPage() {
  return (
    <ReportScreen
      title="Sales & Gross Profit"
      subtitle="Revenue, cost of goods and margin per sale, from validated records"
      report="sales-detail"
      initialRange={() => {
        const to = new Date();
        const from = new Date();
        from.setDate(from.getDate() - 29);
        const iso = (d: Date) => d.toISOString().slice(0, 10);
        return { from: iso(from), to: iso(to) };
      }}
      columns={[
        { key: 'order', label: 'Order' },
        { key: 'date', label: 'Date' },
        { key: 'customer', label: 'Customer' },
        { key: 'warehouse', label: 'Warehouse' },
        { key: 'gross', label: 'Gross', money: true },
        { key: 'discount', label: 'Discount', money: true },
        { key: 'net', label: 'Net sales', money: true },
        { key: 'tax', label: 'Tax', money: true },
        { key: 'cogs', label: 'Cost', money: true },
        { key: 'grossProfit', label: 'Gross profit', money: true },
        { key: 'paymentStatus', label: 'Payment' },
      ]}
      summary={(payload) => {
        const totals = payload.totals ?? {};
        const netSales = Number(totals.netSales ?? 0);
        const grossProfit = Number(totals.grossProfit ?? 0);
        return (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              title="Net Sales"
              value={formatCurrency(netSales)}
              changeLabel="after approved returns"
              color="blue"
            />
            <StatCard
              title="Cost of Goods Sold"
              value={formatCurrency(Number(totals.costOfGoodsSold ?? 0))}
              changeLabel="cost frozen at time of sale"
              color="amber"
            />
            <StatCard
              title="Gross Profit"
              value={formatCurrency(grossProfit)}
              change={
                netSales > 0 ? (grossProfit / netSales) * 100 : undefined
              }
              changeLabel="gross margin"
              color="emerald"
              icon={<TrendingUp size={20} />}
            />
            <StatCard
              title="Units Sold"
              value={Number(totals.unitsSold ?? 0).toLocaleString()}
              changeLabel="net of returned units"
              color="purple"
            />
          </div>
        );
      }}
    />
  );
}