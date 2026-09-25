"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";

import { DollarSign, Clock, AlertTriangle } from "lucide-react";

import PageHeader from "@/components/ui/PageHeader";
import DataTable, { Column } from "@/components/ui/DataTable";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import StatCard from "@/components/ui/StatCard";
import Modal from "@/components/ui/Modal";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import ErrorState from "@/components/ui/ErrorState";

import { formatCurrency } from "@/lib/utils";

import { customersApi } from "@/lib/api/customersApi";

import type { Customer } from "@/lib/api/customersApi";

import { useSales } from "@/hooks/useSales";
import { useCreatePayment } from "@/hooks/useFinance";

import { useToast } from "@/components/ui/ToastProvider";

/* -------------------------------------------------------------------------- */
/* Helpers                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * Calculate the unpaid amount for a sale.
 *
 * Cancelled and fully-paid sales do not contribute to receivables.
 */
function unpaidOfSale(sale: any): number {
  const status = String(sale?.status ?? "").toUpperCase();

  if (status === "CANCELLED") {
    return 0;
  }

  const paymentStatus = String(sale?.paymentStatus ?? "").toUpperCase();

  if (paymentStatus === "PAID") {
    return 0;
  }

  const total = Number(
    sale?.finalAmount ?? sale?.total ?? sale?.totalAmount ?? 0,
  );

  const paid = Number(sale?.paid ?? sale?.paidAmount ?? 0);

  return Math.max(0, total - paid);
}

/**
 * Calculate how many days have passed since the sale date.
 */
function daysSince(sale: any): number {
  const dateValue = sale?.createdAt ?? sale?.date ?? sale?.saleDate ?? "";

  const date = new Date(dateValue);

  if (Number.isNaN(date.getTime())) {
    return 0;
  }

  return Math.floor((Date.now() - date.getTime()) / 86400000);
}

/**
 * Get customer's outstanding balance.
 *
 * Different backend versions may call this field:
 * - outstandingBalance
 * - balance
 */
function balanceOf(customer: Customer): number {
  return Number(
    customer?.outstandingBalance ?? (customer as any)?.balance ?? 0,
  );
}

/**
 * Safely extract an error message.
 */
function getErrorMessage(error: unknown, fallback: string): string {
  const err = error as {
    response?: {
      data?: {
        message?: string;
        error?: string;
      };
    };
    message?: string;
  };

  return (
    err?.response?.data?.message ??
    err?.response?.data?.error ??
    err?.message ??
    fallback
  );
}

/* -------------------------------------------------------------------------- */
/* Page                                                                       */
/* -------------------------------------------------------------------------- */

export default function ReceivablesPage() {
  const { toast } = useToast();

  /*
   * Customers are loaded directly through customersApi.
   *
   * We intentionally do not use:
   *
   * useCustomers()
   *
   * because the current useCustomers.ts does not export that hook.
   */
  const [customers, setCustomers] = useState<Customer[]>([]);

  const [customersLoading, setCustomersLoading] = useState(true);

  const [customersError, setCustomersError] = useState<unknown>(null);

  const {
    data: sales = [],
    isLoading: salesLoading,
    error: salesError,
    refetch: refetchSales,
  } = useSales();

  const createPayment = useCreatePayment();

  /* ------------------------------------------------------------------------ */
  /* Payment modal                                                            */
  /* ------------------------------------------------------------------------ */

  const [paymentModalOpen, setPaymentModalOpen] = useState(false);

  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(
    null,
  );

  const [amount, setAmount] = useState("");

  const [method, setMethod] = useState<"cash" | "bank" | "mobile">("bank");

  const [reference, setReference] = useState("");

  /* ------------------------------------------------------------------------ */
  /* Load customers                                                           */
  /* ------------------------------------------------------------------------ */

  const fetchCustomers = useCallback(async () => {
    try {
      setCustomersLoading(true);
      setCustomersError(null);

      const data = await customersApi.list();

      /*
       * Be defensive because APIs can return:
       *
       * 1. Customer[]
       * 2. { content: Customer[] }
       * 3. { data: Customer[] }
       * 4. { data: { content: Customer[] } }
       */
      let customerList: Customer[] = [];

      if (Array.isArray(data)) {
        customerList = data;
      } else if (Array.isArray((data as any)?.content)) {
        customerList = (data as any).content;
      } else if (Array.isArray((data as any)?.data)) {
        customerList = (data as any).data;
      } else if (Array.isArray((data as any)?.data?.content)) {
        customerList = (data as any).data.content;
      }

      setCustomers(customerList);
    } catch (error: unknown) {
      setCustomersError(error);

      toast(
        "error",
        "Customer Loading Failed",
        getErrorMessage(error, "Unable to load customers."),
      );
    } finally {
      setCustomersLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    void fetchCustomers();
  }, [fetchCustomers]);

  /* ------------------------------------------------------------------------ */
  /* Debtors                                                                  */
  /* ------------------------------------------------------------------------ */

  const debtors = useMemo(() => {
    return customers.filter((customer) => balanceOf(customer) > 0);
  }, [customers]);

  /* ------------------------------------------------------------------------ */
  /* Total receivables                                                        */
  /* ------------------------------------------------------------------------ */

  const totalReceivables = useMemo(() => {
    return debtors.reduce((sum, customer) => sum + balanceOf(customer), 0);
  }, [debtors]);

  /* ------------------------------------------------------------------------ */
  /* Aging                                                                    */
  /* ------------------------------------------------------------------------ */

  const aging = useMemo(() => {
    let current = 0;
    let mid = 0;
    let overdue = 0;

    for (const sale of sales as any[]) {
      const unpaid = unpaidOfSale(sale);

      if (unpaid <= 0) {
        continue;
      }

      const age = daysSince(sale);

      if (age <= 30) {
        current += unpaid;
      } else if (age <= 60) {
        mid += unpaid;
      } else {
        overdue += unpaid;
      }
    }

    return {
      current,
      mid,
      overdue,
    };
  }, [sales]);

  /* ------------------------------------------------------------------------ */
  /* Payment modal                                                            */
  /* ------------------------------------------------------------------------ */

  const openRecordPayment = (customer: Customer) => {
    const balance = balanceOf(customer);

    setSelectedCustomer(customer);

    setAmount(String(balance));

    setReference(`REC-${Date.now().toString().slice(-8)}`);

    setMethod("bank");

    setPaymentModalOpen(true);
  };

  const closePaymentModal = () => {
    if (createPayment.isPending) {
      return;
    }

    setPaymentModalOpen(false);
    setSelectedCustomer(null);
    setAmount("");
    setReference("");
    setMethod("bank");
  };

  /* ------------------------------------------------------------------------ */
  /* Record payment                                                           */
  /* ------------------------------------------------------------------------ */

  const handleSettle = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!selectedCustomer) {
      return;
    }

    const numericAmount = Number.parseFloat(amount);

    const outstanding = balanceOf(selectedCustomer);

    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      toast(
        "warning",
        "Invalid Amount",
        "Enter a payment amount greater than zero.",
      );
      return;
    }

    if (numericAmount > outstanding) {
      toast(
        "warning",
        "Amount Too Large",
        `The payment cannot exceed the outstanding balance of ${formatCurrency(
          outstanding,
        )}.`,
      );
      return;
    }

    if (!reference.trim()) {
      toast(
        "warning",
        "Reference Required",
        "Enter a payment reference or receipt number.",
      );
      return;
    }

    try {
      await createPayment.mutateAsync({
        reference: reference.trim(),

        type: "received",

        amount: numericAmount,

        date: new Date().toISOString().slice(0, 10),

        method,

        party:
          (selectedCustomer as any)?.name ??
          (selectedCustomer as any)?.companyName ??
          "",

        partyType: "customer",

        note: "Collection against outstanding balance",
      });

      const customerName =
        (selectedCustomer as any)?.name ??
        (selectedCustomer as any)?.companyName ??
        "Customer";

      toast(
        "success",
        "Payment Received",
        `${formatCurrency(numericAmount)} recorded from ${customerName}.`,
      );

      closePaymentModal();

      /*
       * Refresh both customer balances and
       * sales/aging data after payment.
       */
      await Promise.all([fetchCustomers(), refetchSales()]);
    } catch (error: unknown) {
      toast(
        "error",
        "Payment Failed",
        getErrorMessage(error, "Failed to record payment."),
      );
    }
  };

  /* ------------------------------------------------------------------------ */
  /* Table columns                                                            */
  /* ------------------------------------------------------------------------ */

  const columns: Column<Customer>[] = [
    {
      key: "name",
      label: "Customer",

      render: (_, row) => {
        const customer = row as Customer & {
          phone?: string;
          companyName?: string;
        };

        const displayName =
          customer.name ?? customer.companyName ?? "Unknown Customer";

        return (
          <div>
            <span className="font-semibold text-gray-900 dark:text-gray-100">
              {String(displayName)}
            </span>

            {customer.phone && (
              <span className="block text-[11px] text-gray-400">
                {String(customer.phone)}
              </span>
            )}
          </div>
        );
      },
    },

    {
      key: "creditLimit",
      label: "Credit Limit",

      render: (value) => formatCurrency(Number(value ?? 0)),
    },

    {
      key: "outstandingBalance",
      label: "Outstanding Balance",

      render: (_, row) => (
        <span className="font-bold text-amber-600 dark:text-amber-400">
          {formatCurrency(balanceOf(row))}
        </span>
      ),
    },

    {
      key: "utilization",
      label: "Credit Used %",

      render: (_, row) => {
        const limit = Number((row as any)?.creditLimit) || 0;

        const balance = balanceOf(row);

        /*
         * If there is no credit limit,
         * utilization is zero instead of
         * artificially becoming 100%.
         */
        const percentage =
          limit > 0 ? Math.min(100, Math.round((balance / limit) * 100)) : 0;

        return (
          <div className="flex items-center gap-2">
            <div className="h-1.5 w-16 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                className={`h-full ${
                  percentage > 80 ? "bg-red-500" : "bg-blue-600"
                }`}
                style={{
                  width: `${percentage}%`,
                }}
              />
            </div>

            <span className="font-mono text-xs">{percentage}%</span>
          </div>
        );
      },
    },

    {
      key: "status",
      label: "Status",

      render: (_, row) => {
        const balance = balanceOf(row);

        const limit = Number((row as any)?.creditLimit) || 0;

        if (balance > limit && limit > 0) {
          return <Badge variant="danger">Over Limit</Badge>;
        }

        return <Badge variant="warning">Pending Due</Badge>;
      },
    },
  ];

  /* ------------------------------------------------------------------------ */
  /* Error state                                                              */
  /* ------------------------------------------------------------------------ */

  if (customersError || salesError) {
    return (
      <ErrorState
        retry={() => {
          void fetchCustomers();
          void refetchSales();
        }}
      />
    );
  }

  /* ------------------------------------------------------------------------ */
  /* Render                                                                   */
  /* ------------------------------------------------------------------------ */

  return (
    <div className="space-y-6">
      <PageHeader
        title="Receivables"
        subtitle="Track outstanding money owed by clients, payment aging from unpaid sales, and credit risk"
      />

      {/* ------------------------------------------------------------------ */}
      {/* Statistics                                                        */}
      {/* ------------------------------------------------------------------ */}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Total Receivables"
          value={formatCurrency(totalReceivables)}
          changeLabel="from customer balances"
          color="amber"
          icon={<DollarSign size={20} />}
        />

        <StatCard
          title="Current (0–30 Days)"
          value={formatCurrency(aging.current)}
          changeLabel="unpaid sales"
          color="blue"
          icon={<Clock size={20} />}
        />

        <StatCard
          title="Overdue (31–60 Days)"
          value={formatCurrency(aging.mid)}
          changeLabel="unpaid sales"
          color="purple"
          icon={<Clock size={20} />}
        />

        <StatCard
          title="High Risk (60+ Days)"
          value={formatCurrency(aging.overdue)}
          changeLabel="unpaid sales"
          color="red"
          icon={<AlertTriangle size={20} />}
        />
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* Debtors table                                                     */}
      {/* ------------------------------------------------------------------ */}

      <DataTable<Customer>
        columns={columns}
        data={debtors}
        loading={customersLoading || salesLoading}
        searchable
        searchPlaceholder="Search debtors..."
        actions={(row) => (
          <Button
            size="sm"
            variant="outline"
            onClick={() => openRecordPayment(row)}
          >
            Collect Payment
          </Button>
        )}
      />

      {/* ------------------------------------------------------------------ */}
      {/* Payment modal                                                     */}
      {/* ------------------------------------------------------------------ */}

      <Modal
        open={paymentModalOpen}
        onClose={closePaymentModal}
        title={`Collect Payment — ${
          selectedCustomer
            ? String(
                (selectedCustomer as any)?.name ??
                  (selectedCustomer as any)?.companyName ??
                  "Customer",
              )
            : ""
        }`}
      >
        <form onSubmit={handleSettle} className="space-y-4">
          {/* Outstanding information */}

          <div className="space-y-1 rounded-lg bg-gray-50 p-3 text-xs dark:bg-gray-800">
            <div className="flex justify-between">
              <span className="text-gray-400">Total Outstanding:</span>

              <strong className="text-amber-600 dark:text-amber-400">
                {formatCurrency(
                  selectedCustomer ? balanceOf(selectedCustomer) : 0,
                )}
              </strong>
            </div>

            <div className="flex justify-between">
              <span className="text-gray-400">Credit Limit:</span>

              <span>
                {formatCurrency(
                  Number((selectedCustomer as any)?.creditLimit ?? 0),
                )}
              </span>
            </div>
          </div>

          {/* Reference */}

          <Input
            label="Payment Reference / Receipt No."
            value={reference}
            onChange={(event) => setReference(event.target.value)}
            required
          />

          {/* Amount */}

          <Input
            label="Amount Collected (ETB)"
            type="number"
            min={0.01}
            step="0.01"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            required
          />

          {/* Payment method */}

          <Select
            label="Payment Channel"
            options={[
              {
                value: "bank",
                label: "Commercial Bank Transfer (CBE / Awash / Dashen)",
              },
              {
                value: "mobile",
                label: "Telebirr / CBE Birr",
              },
              {
                value: "cash",
                label: "Cash in Register",
              },
            ]}
            value={method}
            onChange={(event) =>
              setMethod(event.target.value as "cash" | "bank" | "mobile")
            }
          />

          {/* Actions */}

          <div className="flex justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={closePaymentModal}
              disabled={createPayment.isPending}
            >
              Cancel
            </Button>

            <Button
              type="submit"
              loading={createPayment.isPending}
              disabled={createPayment.isPending}
            >
              Record Collection
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
