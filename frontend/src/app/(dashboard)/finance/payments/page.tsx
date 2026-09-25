"use client";

import React, { useMemo } from "react";

import PageHeader from "@/components/ui/PageHeader";
import DataTable, { type Column } from "@/components/ui/DataTable";

import Badge from "@/components/ui/Badge";

import { formatCurrency, formatDate } from "@/lib/utils";

interface Payment {
  id: number | string;

  date?: string | Date | null;

  reference?: string | null;

  type?: "received" | "made" | string | null;

  party?: string | null;

  partyType?: string | null;

  method?: string | null;

  amount?: number | string | null;

  [key: string]: unknown;
}

/*
 * Convert unknown API data into
 * a safe Payment object.
 */
function normalizePayment(payment: any): Payment {
  return {
    id: payment?.id ?? payment?.paymentId ?? crypto.randomUUID(),

    date: payment?.date ?? payment?.paymentDate ?? payment?.createdAt ?? null,

    reference: payment?.reference ?? payment?.paymentReference ?? null,

    type: payment?.type ?? payment?.paymentType ?? null,

    party:
      payment?.party ??
      payment?.partyName ??
      payment?.customerName ??
      payment?.supplierName ??
      null,

    partyType: payment?.partyType ?? null,

    method: payment?.method ?? payment?.paymentMethod ?? null,

    amount: payment?.amount ?? payment?.totalAmount ?? 0,
  };
}

export default function PaymentsPage() {
  /*
   * Replace this with your real payment
   * hook/API once the backend payment
   * endpoint is connected.
   *
   * Keeping it empty is safer than inventing
   * database data.
   */
  const payments: Payment[] = useMemo(() => [], []);

  const columns: Column<Payment>[] = useMemo(
    () => [
      {
        key: "date",

        label: "Date",

        render: (value) =>
          formatDate(value as string | Date | null | undefined),
      },

      {
        key: "reference",

        label: "Reference",

        render: (value) => (
          <span className="font-mono text-xs">{String(value ?? "—")}</span>
        ),
      },

      {
        key: "type",

        label: "Type",

        render: (value) => {
          const type = String(value ?? "").toLowerCase();

          const received =
            type === "received" || type === "in" || type === "payment_received";

          return (
            <Badge variant={received ? "success" : "danger"}>
              {received ? "Received (In)" : "Made (Out)"}
            </Badge>
          );
        },
      },

      {
        key: "party",

        label: "Party",

        render: (_value, row) => (
          <div>
            <p className="font-medium">{String(row.party ?? "—")}</p>

            <p className="text-xs capitalize text-gray-500">
              {String(row.partyType ?? "—")}
            </p>
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

        render: (value, row) => {
          const type = String(row.type ?? "").toLowerCase();

          const received =
            type === "received" || type === "in" || type === "payment_received";

          return (
            <span
              className={
                received
                  ? "font-bold text-emerald-600"
                  : "font-bold text-red-600"
              }
            >
              {formatCurrency(Number(value ?? 0))}
            </span>
          );
        },
      },
    ],
    [],
  );

  const normalizedPayments = useMemo(
    () => payments.map(normalizePayment),
    [payments],
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payments"
        subtitle="Track money received from customers and payments made to suppliers"
      />

      <DataTable<Payment> columns={columns} data={normalizedPayments} />
    </div>
  );
}
