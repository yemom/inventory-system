"use client";

import React, { useState } from "react";

import PageHeader from "@/components/ui/PageHeader";
import DataTable, { Column } from "@/components/ui/DataTable";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import ErrorState from "@/components/ui/ErrorState";

import { useTransfers, useInventory } from "@/hooks/useInventory";

import { useWarehouses } from "@/hooks/useWarehouses";

import { useToast } from "@/components/ui/ToastProvider";

import { formatDate } from "@/lib/utils";

import { Plus } from "lucide-react";

import { inventoryApi } from "@/lib/api/inventoryApi";

import { useQueryClient } from "@tanstack/react-query";

const statusVariant: Record<string, "success" | "info" | "default" | "danger"> =
  {
    completed: "success",
    in_transit: "info",
    draft: "default",
    cancelled: "danger",

    COMPLETED: "success",
    IN_TRANSIT: "info",
    DRAFT: "default",
    CANCELLED: "danger",
  };

interface TransferForm {
  from: string;
  to: string;
  productId: string;
  qty: string;
}

export default function TransfersPage() {
  const { data: transfers = [], isLoading, error, refetch } = useTransfers();

  const { data: products = [] } = useInventory();

  const {
    data: warehouses = [],
    isLoading: loadingWarehouses,
    error: warehousesError,
    refetch: refetchWarehouses,
  } = useWarehouses();

  const { toast } = useToast();

  const queryClient = useQueryClient();

  const [modalOpen, setModalOpen] = useState(false);

  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState<TransferForm>({
    from: "",
    to: "",
    productId: "",
    qty: "",
  });

  const warehouseOptions = warehouses.map((warehouse) => ({
    value: String(warehouse.id),
    label: warehouse.name,
  }));

  const productOptions = products.map((product: any) => ({
    value: String(product.id),
    label: `${product.name} (${product.quantity ?? 0} available)`,
  }));

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!form.from || !form.to || !form.productId || !form.qty) {
      toast(
        "warning",
        "Missing information",
        "Please fill in all transfer fields.",
      );

      return;
    }

    if (form.from === form.to) {
      toast(
        "warning",
        "Invalid transfer",
        "Source and destination warehouses must be different.",
      );

      return;
    }

    const quantity = Number(form.qty);

    if (!Number.isInteger(quantity) || quantity <= 0) {
      toast(
        "warning",
        "Invalid quantity",
        "Quantity must be a positive whole number.",
      );

      return;
    }

    const product = products.find(
      (item: any) => String(item.id) === String(form.productId),
    );

    if (!product) {
      toast("error", "Product not found", "Please select a valid product.");

      return;
    }

    const availableQuantity = Number(product.quantity ?? 0);

    if (quantity > availableQuantity) {
      toast(
        "warning",
        "Insufficient stock",
        `Only ${availableQuantity} units are available.`,
      );

      return;
    }

    const destinationWarehouse = warehouses.find(
      (warehouse) => String(warehouse.id) === String(form.to),
    );

    if (!destinationWarehouse) {
      toast(
        "error",
        "Warehouse not found",
        "Please select a valid destination warehouse.",
      );

      return;
    }

    setSubmitting(true);

    try {
      /*
       * IMPORTANT:
       *
       * The current backend CreateStockMovementRequest
       * supports:
       *
       * productId
       * warehouseId
       * type
       * quantity
       * reference
       * notes
       *
       * It does NOT support fromLocation/toLocation.
       *
       * Therefore this records a TRANSFER movement
       * against the destination warehouse.
       *
       * A complete source -> destination stock transfer
       * requires a backend transfer transaction endpoint.
       */

      await inventoryApi.recordMovement({
        productId: Number(form.productId),
        warehouseId: Number(destinationWarehouse.id),
        type: "TRANSFER",
        quantity,
        reference: `TR-${Date.now()}`,
        notes: `Transfer from warehouse ${form.from} to ${form.to}`,
      });

      await queryClient.invalidateQueries({
        queryKey: ["transfers"],
      });

      await queryClient.invalidateQueries({
        queryKey: ["inventory"],
      });

      await queryClient.invalidateQueries({
        queryKey: ["products"],
      });

      toast(
        "success",
        "Transfer recorded",
        `${quantity} unit(s) of ${product.name} were recorded as a transfer.`,
      );

      setModalOpen(false);

      setForm({
        from: "",
        to: "",
        productId: "",
        qty: "",
      });
    } catch (error: any) {
      toast(
        "error",
        "Transfer failed",
        error?.response?.data?.message ??
          error?.response?.data?.error ??
          error?.message ??
          "Unable to record the transfer.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const columns: Column<any>[] = [
    {
      key: "reference",
      label: "Reference",

      render: (value) => (
        <span className="font-mono text-xs text-blue-600">
          {String(value || "—")}
        </span>
      ),
    },

    {
      key: "date",
      label: "Date",

      render: (_, row) => formatDate(String(row.date || row.createdAt || "")),
    },

    {
      key: "warehouseName",
      label: "Warehouse",

      render: (value) => String(value || "—"),
    },

    {
      key: "productName",
      label: "Product",

      render: (value) => String(value || "—"),
    },

    {
      key: "quantity",
      label: "Quantity",

      render: (value) => String(value ?? "0"),
    },

    {
      key: "type",
      label: "Type",

      render: (value) => {
        const type = String(value || "TRANSFER");

        return <Badge variant="info">{type}</Badge>;
      },
    },

    {
      key: "status",
      label: "Status",

      render: (value) => {
        const status = String(value || "completed");

        return (
          <Badge variant={statusVariant[status] ?? "default"}>
            {status.replace("_", " ")}
          </Badge>
        );
      },
    },
  ];

  if (error || warehousesError) {
    return (
      <ErrorState
        retry={() => {
          void refetch();
          void refetchWarehouses();
        }}
      />
    );
  }

  return (
    <div>
      <PageHeader
        title="Transfers"
        subtitle="Record stock transfers between warehouses"
        actions={
          <Button onClick={() => setModalOpen(true)}>
            <Plus size={16} />
            New Transfer
          </Button>
        }
      />

      <DataTable
        columns={columns}
        data={transfers as unknown as Record<string, unknown>[]}
        loading={isLoading}
        searchable
      />

      <Modal
        open={modalOpen}
        onClose={() => !submitting && setModalOpen(false)}
        title="New Stock Transfer"
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          {warehouseOptions.length < 2 && (
            <p className="text-sm text-amber-600 dark:text-amber-400">
              {loadingWarehouses
                ? "Loading warehouses…"
                : "Add at least two warehouses before creating a transfer."}
            </p>
          )}

          <Select
            label="From Warehouse"
            options={warehouseOptions}
            placeholder="Select source warehouse"
            value={form.from}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                from: event.target.value,
              }))
            }
          />

          <Select
            label="To Warehouse"
            options={warehouseOptions}
            placeholder="Select destination warehouse"
            value={form.to}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                to: event.target.value,
              }))
            }
          />

          <Select
            label="Product"
            options={productOptions}
            placeholder="Select product"
            value={form.productId}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                productId: event.target.value,
              }))
            }
          />

          <Input
            label="Quantity"
            type="number"
            min="1"
            step="1"
            value={form.qty}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                qty: event.target.value,
              }))
            }
          />

          <div className="flex justify-end gap-3">
            <Button
              type="button"
              variant="outline"
              onClick={() => setModalOpen(false)}
              disabled={submitting}
            >
              Cancel
            </Button>

            <Button
              type="submit"
              loading={submitting}
              disabled={submitting || warehouseOptions.length < 2}
            >
              Create Transfer
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
