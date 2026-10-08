"use client";

import React, { useMemo } from "react";

import PageHeader from "@/components/ui/PageHeader";
import DataTable, { type Column } from "@/components/ui/DataTable";
import Badge from "@/components/ui/Badge";
import ErrorState from "@/components/ui/ErrorState";
import StatCard from "@/components/ui/StatCard";

import { formatCurrency, formatDate } from "@/lib/utils";
import { usePayments } from "@/hooks/useFinance";
import type { Payment as ApiPayment } from "@/lib/api/paymentsApi";

interface Payment {
  id: string | number;
  date: string | null;
  reference: string | null;
  type: string | null;
  party: string | null;
  partyType: string | null;
  method: string | null;
  amount: number;
  status: string | null;
}

/**
 * The API records money in as `type: "IN"` and out as `"OUT"`; anything else is
 * treated as outgoing so an unfamiliar value cannot silently read as income.
 */
function isReceived(type: string | null): boolean {
  const t = String(type ?? "").toLowerCase();
  return t === "received" || t === "in" || t === "payment_received";
}

/**
 * Whether the money has actually changed hands.
 *
 * <p>Direction alone is not enough. A credit sale records a payment row with
 * `type: "IN"` and `status: "PENDING"` — that is a record of what is *owed*, not
 * cash in the till. Counting it as received overstated cash on hand by the full
 * amount of every outstanding invoice, which is precisely the number a business
 * checks first and can least afford to have inflated.
 */
function isSettled(status: string | null): boolean {
  const s = String(status ?? "").toUpperCase();
  // Absent status is treated as settled: the API omits it on older rows that were
  // in fact taken, and assuming otherwise would understate every historical total.
  return s === "" || s === "COMPLETED" || s === "PAID" || s === "SETTLED";
}

/**
 * Maps one API row to the shape the table renders.
 *
 * Every field is read with a fallback chain because the endpoint has carried
 * different aliases over time (`orderReference` vs `reference` vs
 * `paymentReference`), and a page that renders "—" for a field the API did send
 * is indistinguishable from one that is broken.
 */
function normalizePayment(payment: ApiPayment): Payment {
  return {
    id: payment.id,

    date: payment.date ?? payment.createdAt ?? null,

    reference: payment.reference ?? null,

    // A payment row carries no customer or supplier name — only the order it
    // settles. Without this fallback the Party column was always an em dash,
    // which is the one column that could have identified the counterparty.
    party:
      payment.party ??
      payment.customerName ??
      payment.supplierName ??
      payment.orderReference ??
      null,

    partyType: payment.partyType ?? null,

    type: payment.type ?? null,

    method: payment.method ?? payment.paymentMethod ?? null,

    amount: Number(payment.amount ?? payment.totalAmount ?? 0),

    status: payment.status ?? null,
  };
}

export default function PaymentsPage() {
  /**
   * This used to be `useMemo(() => [], [])` — a hardcoded empty array. The
   * endpoint existed and `usePayments` existed and both went unused, so the page
   * reported "No records found" no matter how many payments had been recorded.
   * Every figure below is now real.
   */
  const { data, isLoading, error, refetch } = usePayments();

  const payments = useMemo<Payment[]>(
    () => (data ?? []).map((p) => normalizePayment(p as ApiPayment)),
    [data],
  );

  const { received, paid, pending } = useMemo(() => {
    let receivedTotal = 0;
    let paidTotal = 0;
    let pendingTotal = 0;

    for (const payment of payments) {
      const incoming = isReceived(payment.type);

      if (!isSettled(payment.status)) {
        // Owed but not received. Kept out of both the received and paid-out
        // totals so the cash figures stay true.
        if (incoming) pendingTotal += payment.amount;
        continue;
      }

      if (incoming) receivedTotal += payment.amount;
      else paidTotal += payment.amount;
    }

    return { received: receivedTotal, paid: paidTotal, pending: pendingTotal };
  }, [payments]);

  const columns: Column<Payment>[] = useMemo(
    () => [
      {
        key: "date",
        label: "Date",
        render: (value) =>
          formatDate(value as string | null | undefined),
      },
      {
        key: "reference",
        label: "Reference",
        // The counterparty is not repeated here. When it falls back to the order
        // reference — which it does for every row the API produces, since a
        // payment carries no customer name — the same string appeared under both
        // Reference and Party.
        render: (value) => (
          <span className="font-mono text-xs">{String(value ?? "—")}</span>
        ),
      },
      {
        key: "type",
        label: "Type",
        render: (value) => (
          <Badge variant={isReceived(value as string | null) ? "success" : "danger"}>
            {isReceived(value as string | null) ? "Received (In)" : "Made (Out)"}
          </Badge>
        ),
      },
      {
        key: "party",
        label: "Party",
        render: (_value, row) => (
          <div>
            {/* Falls back to the order the payment settles, so the column is
                never blank for a payment that clearly has a counterparty. */}
            <p className="font-medium">{row.party ?? "—"}</p>
            {row.status && (
              <p className="text-xs capitalize text-gray-500">{row.status}</p>
            )}
          </div>
        ),
      },
      {
        key: "method",
        label: "Method",
        render: (value) => (
          <span className="capitalize">{String(value ?? "—")}</span>
        ),
      },
      {
        key: "amount",
        label: "Amount",
        render: (value, row) => (
          <span
            className={
              isReceived(row.type)
                ? "font-bold text-emerald-600"
                : "font-bold text-red-600"
            }
          >
            {formatCurrency(Number(value ?? 0))}
          </span>
        ),
      },
    ],
    [],
  );

  if (error) return <ErrorState error={error} retry={refetch} />;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payments"
        subtitle="Track money received from customers and payments made to suppliers"
      />

      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <StatCard title="Total Received" value={formatCurrency(received)} />
        <StatCard title="Total Paid Out" value={formatCurrency(paid)} />
        <StatCard title="Net Movement" value={formatCurrency(received - paid)} />
        {/* Outstanding, not received — shown separately so the cash total is
            never inflated by money that has not arrived. */}
        <StatCard title="Pending Collection" value={formatCurrency(pending)} />
      </div>

      <DataTable<Payment>
        columns={columns}
        data={payments}
        loading={isLoading}
        searchable
        searchPlaceholder="Search by reference or party..."
      />
    </div>
  );
}
