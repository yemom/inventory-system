'use client';

import React from 'react';
import { ArrowDownRight, ArrowUpRight, Wallet } from 'lucide-react';
import ReportScreen from '@/components/reports/ReportScreen';
import StatCard from '@/components/ui/StatCard';
import { formatCurrency } from '@/lib/utils';

/**
 * Income, expenses and cash flow.
 *
 * <h2>Why this screen is a ledger and not a calculation</h2>
 *
 * <p>It used to sum two lists in the browser: payments filtered by a
 * {@code type} field reading "received" or "made", and expenses filtered by
 * date. Both figures were therefore only as complete as the pages of those
 * lists that had loaded, and the expense side counted claims that had never
 * been approved — a pending claim is not yet a cost.
 *
 * <p>Both now come from completed payment records and approved expenses, read
 * server-side across every page. The distinction matters: an unpaid invoice is
 * not a cash movement, so it appears in receivables instead of here, and an
 * order placed but not received is neither stock nor a payment.
 */
export default function FinancialReportPage() {
  return (
    <ReportScreen
      title="Income & Cash Flow"
      subtitle="Money actually received and actually paid — completed payments and approved expenses only"
      report="cash-flow"
      columns={[
        { key: 'date', label: 'Date' },
        { key: 'reference', label: 'Reference' },
        { key: 'direction', label: 'Direction' },
        { key: 'partyType', label: 'Party type' },
        { key: 'party', label: 'Party' },
        { key: 'document', label: 'Against' },
        { key: 'method', label: 'Channel' },
        { key: 'amount', label: 'Amount', money: true },
        { key: 'bankReference', label: 'Bank reference' },
      ]}
      summary={(payload) => {
        const totals = payload.totals ?? {};
        const cashIn = Number(totals.cashIn ?? 0);
        const cashOut = Number(totals.cashOut ?? 0);
        return (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              title="Cash In"
              value={formatCurrency(cashIn)}
              changeLabel="collections recorded"
              color="emerald"
              icon={<ArrowUpRight size={20} />}
            />
            <StatCard
              title="Paid to Suppliers"
              value={formatCurrency(Number(totals.cashOutPayments ?? 0))}
              changeLabel="supplier payments recorded"
              color="blue"
              icon={<ArrowDownRight size={20} />}
            />
            <StatCard
              title="Operating Expenses"
              value={formatCurrency(Number(totals.operatingExpenses ?? 0))}
              changeLabel="approved claims only"
              color="amber"
              icon={<ArrowDownRight size={20} />}
            />
            <StatCard
              title="Net Cash Flow"
              value={formatCurrency(Number(totals.netCashFlow ?? 0))}
              changeLabel={`${formatCurrency(cashOut)} out of ${formatCurrency(cashIn)} in`}
              color={cashIn - cashOut >= 0 ? 'emerald' : 'red'}
              icon={<Wallet size={20} />}
            />
          </div>
        );
      }}
    />
  );
}