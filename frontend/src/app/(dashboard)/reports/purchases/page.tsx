'use client';

import React from 'react';
import ReportScreen from '@/components/reports/ReportScreen';
import StatCard from '@/components/ui/StatCard';
import { formatCurrency } from '@/lib/utils';

/**
 * Purchases and supplier balances.
 *
 * <p>Shows units ordered against units received side by side, because those are
 * not the same thing and the gap is the thing worth seeing. An order placed is
 * not stock and not yet a payable; an order received is both. The outstanding
 * balance column is therefore a balance on goods in hand, not a sum of
 * everything ever ordered.
 */
export default function PurchasesReportPage() {
  return (
    <ReportScreen
      title="Purchases"
      subtitle="What was ordered against what was received, and what remains unpaid"
      report="purchases-detail"
      columns={[
        { key: 'order', label: 'Order' },
        { key: 'date', label: 'Date' },
        { key: 'supplier', label: 'Supplier' },
        { key: 'status', label: 'Status' },
        { key: 'paymentStatus', label: 'Payment' },
        { key: 'total', label: 'Ordered', money: true },
        { key: 'paid', label: 'Paid', money: true },
        { key: 'balanceDue', label: 'Balance due', money: true },
        { key: 'unitsOrdered', label: 'Units ordered', numeric: true },
        { key: 'unitsReceived', label: 'Units received', numeric: true },
        { key: 'unitsOutstanding', label: 'Still to come', numeric: true },
        { key: 'dueDate', label: 'Due' },
      ]}
      summary={(payload) => {
        const totals = payload.totals ?? {};
        const rows: any[] = payload.rows ?? [];
        const received = rows.reduce(
          (sum, r) => sum + Number(r.unitsReceived ?? 0),
          0,
        );
        const ordered = rows.reduce(
          (sum, r) => sum + Number(r.unitsOrdered ?? 0),
          0,
        );
        return (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              title="Ordered Value"
              value={formatCurrency(Number(totals.orderedValue ?? 0))}
              changeLabel="committed to suppliers"
              color="blue"
            />
            <StatCard
              title="Units Received"
              value={received.toLocaleString()}
              changeLabel={
                ordered > 0
                  ? `${((received / ordered) * 100).toFixed(0)}% of what was ordered`
                  : 'nothing ordered'
              }
              color="emerald"
            />
            <StatCard
              title="Balance Due"
              value={formatCurrency(Number(totals.outstandingBalance ?? 0))}
              changeLabel="on goods already received"
              color="red"
            />
            <StatCard
              title="Purchase Orders"
              value={String(rows.length)}
              changeLabel="in this period"
              color="purple"
            />
          </div>
        );
      }}
    />
  );
}