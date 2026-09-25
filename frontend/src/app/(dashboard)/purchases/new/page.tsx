"use client";

import React, { useEffect, useMemo, useState } from "react";

import { useRouter } from "next/navigation";

import { ArrowLeft, CheckCircle, Plus, Trash2 } from "lucide-react";

import PageHeader from "@/components/ui/PageHeader";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";

import { formatCurrency } from "@/lib/utils";

import { useSuppliers } from "@/hooks/useSuppliers";
import { useProducts } from "@/hooks/useProducts";
import { useWarehouses } from "@/hooks/useWarehouses";

import { purchasesApi } from "@/lib/api/purchasesApi";

import { useToast } from "@/components/ui/ToastProvider";

/* =========================================================
   Types
========================================================= */

interface PurchaseLine {
  productId: string;
  productName: string;
  sku: string;
  quantity: number;
  unitCost: number;
}

type PaymentStatus = "paid" | "partial" | "unpaid";

/* =========================================================
   Page
========================================================= */

export default function NewPurchasePage() {
  const router = useRouter();

  const { toast } = useToast();

  /* =======================================================
     API data
  ======================================================= */

  const { data: suppliers = [], isLoading: suppliersLoading } = useSuppliers();

  const { data: products = [], isLoading: productsLoading } = useProducts();

  const { data: warehouses = [], isLoading: warehousesLoading } =
    useWarehouses();

  /* =======================================================
     Form state
  ======================================================= */

  const [supplierId, setSupplierId] = useState("");

  const [warehouseId, setWarehouseId] = useState("");

  const [orderDate, setOrderDate] = useState(
    new Date().toISOString().slice(0, 10),
  );

  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>("unpaid");

  const [notes, setNotes] = useState("");

  const [lines, setLines] = useState<PurchaseLine[]>([]);

  const [submitting, setSubmitting] = useState(false);

  /* =======================================================
     Initialize supplier
  ======================================================= */

  useEffect(() => {
    if (!supplierId && suppliers.length > 0) {
      setSupplierId(String(suppliers[0].id));
    }
  }, [suppliers, supplierId]);

  /* =======================================================
     Initialize warehouse
  ======================================================= */

  useEffect(() => {
    if (!warehouseId && warehouses.length > 0) {
      setWarehouseId(String(warehouses[0].id));
    }
  }, [warehouses, warehouseId]);

  /* =======================================================
     Initialize first product
  ======================================================= */

  useEffect(() => {
    if (lines.length === 0 && products.length > 0) {
      const product = products[0];

      setLines([
        {
          productId: String(product.id),
          productName: product.name,
          sku: product.sku ?? "",
          quantity: 1,
          unitCost: Number(product.purchasePrice ?? 0),
        },
      ]);
    }
  }, [products, lines.length]);

  /* =======================================================
     Add purchase line
  ======================================================= */

  const addLine = () => {
    if (products.length === 0) {
      toast(
        "error",
        "No products",
        "Create a product before creating a purchase.",
      );

      return;
    }

    const usedProductIds = new Set(lines.map((line) => line.productId));

    const product =
      products.find((item) => !usedProductIds.has(String(item.id))) ??
      products[0];

    setLines((current) => [
      ...current,
      {
        productId: String(product.id),
        productName: product.name,
        sku: product.sku ?? "",
        quantity: 1,
        unitCost: Number(product.purchasePrice ?? 0),
      },
    ]);
  };

  /* =======================================================
     Update purchase line
  ======================================================= */

  const updateLine = (
    index: number,
    field: keyof PurchaseLine,
    value: string | number,
  ) => {
    setLines((current) =>
      current.map((line, lineIndex) => {
        if (lineIndex !== index) {
          return line;
        }

        /* Product changed */
        if (field === "productId") {
          const product = products.find(
            (item) => String(item.id) === String(value),
          );

          if (!product) {
            return line;
          }

          return {
            ...line,
            productId: String(product.id),
            productName: product.name,
            sku: product.sku ?? "",
            unitCost: Number(product.purchasePrice ?? 0),
          };
        }

        /* Quantity changed */
        if (field === "quantity") {
          const quantity = Math.max(1, Math.floor(Number(value) || 1));

          return {
            ...line,
            quantity,
          };
        }

        /* Unit cost changed */
        if (field === "unitCost") {
          const unitCost = Math.max(0, Number(value) || 0);

          return {
            ...line,
            unitCost,
          };
        }

        return {
          ...line,
          [field]: value,
        };
      }),
    );
  };

  /* =======================================================
     Remove purchase line
  ======================================================= */

  const removeLine = (index: number) => {
    if (lines.length === 1) {
      toast(
        "error",
        "Cannot remove product",
        "A purchase must contain at least one product.",
      );

      return;
    }

    setLines((current) =>
      current.filter((_, lineIndex) => lineIndex !== index),
    );
  };

  /* =======================================================
     Calculate total amount
  ======================================================= */

  const totalAmount = useMemo(
    () =>
      lines.reduce((total, line) => total + line.quantity * line.unitCost, 0),
    [lines],
  );

  /* =======================================================
     Calculate total units
  ======================================================= */

  const totalUnits = useMemo(
    () => lines.reduce((total, line) => total + line.quantity, 0),
    [lines],
  );

  /* =======================================================
     Submit purchase
  ======================================================= */

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (submitting) {
      return;
    }

    /* -----------------------------------------------------
       Validate supplier
    ----------------------------------------------------- */

    if (!supplierId) {
      toast(
        "error",
        "Supplier required",
        "Select a supplier before submitting the purchase.",
      );

      return;
    }

    /* -----------------------------------------------------
       Validate warehouse
    ----------------------------------------------------- */

    if (!warehouseId) {
      toast("error", "Warehouse required", "Select a destination warehouse.");

      return;
    }

    /* -----------------------------------------------------
       Validate date
    ----------------------------------------------------- */

    if (!orderDate) {
      toast("error", "Date required", "Select the purchase date.");

      return;
    }

    /* -----------------------------------------------------
       Validate products
    ----------------------------------------------------- */

    if (lines.length === 0) {
      toast("error", "No products", "Add at least one product.");

      return;
    }

    /* -----------------------------------------------------
       Validate individual lines
    ----------------------------------------------------- */

    const invalidLine = lines.find(
      (line) => !line.productId || line.quantity <= 0 || line.unitCost < 0,
    );

    if (invalidLine) {
      toast(
        "error",
        "Invalid purchase items",
        "Check product, quantity and unit cost.",
      );

      return;
    }

    /* -----------------------------------------------------
       Find supplier
    ----------------------------------------------------- */

    const supplier = suppliers.find((item) => String(item.id) === supplierId);

    if (!supplier) {
      toast(
        "error",
        "Supplier not found",
        "The selected supplier could not be found.",
      );

      return;
    }

    /* -----------------------------------------------------
       Supplier uses companyName
    ----------------------------------------------------- */

    const supplierName = supplier.companyName.trim();

    if (!supplierName) {
      toast(
        "error",
        "Invalid supplier",
        "The selected supplier does not have a company name.",
      );

      return;
    }

    /* -----------------------------------------------------
       Find warehouse

       We use the warehouse here for validation and
       future backend support.

       IMPORTANT:
       The current backend purchase request does not
       accept warehouseId yet.
    ----------------------------------------------------- */

    const warehouse = warehouses.find(
      (item) => String(item.id) === warehouseId,
    );

    if (!warehouse) {
      toast(
        "error",
        "Warehouse not found",
        "The selected destination warehouse could not be found.",
      );

      return;
    }

    /* -----------------------------------------------------
       Current backend purchase payload

       Supported fields:

       supplierName
       status
       items[]

       The current backend does NOT accept:

       warehouseId
       paymentStatus
       orderDate
       notes
    ----------------------------------------------------- */

    const payload = {
      supplierName,
      status: "RECEIVED",

      items: lines.map((line) => ({
        productId: Number(line.productId),

        quantity: Number(line.quantity),

        unitCost: Number(line.unitCost),
      })),
    };

    try {
      setSubmitting(true);

      const created = await purchasesApi.create(payload);

      const reference =
        created?.reference ?? created?.orderNumber ?? created?.id ?? "Purchase";

      toast(
        "success",
        "Purchase recorded",
        `${reference} was successfully saved and stock was updated.`,
      );

      router.push("/purchases");
    } catch (error: unknown) {
      const axiosError = error as {
        response?: {
          data?: {
            message?: string;
            error?: string;
          };
        };
        message?: string;
      };

      const message =
        axiosError.response?.data?.message ??
        axiosError.response?.data?.error ??
        axiosError.message ??
        "Unable to record the purchase.";

      toast("error", "Purchase failed", String(message));
    } finally {
      setSubmitting(false);
    }
  };

  /* =======================================================
     Loading state
  ======================================================= */

  const loading = suppliersLoading || productsLoading || warehousesLoading;

  /* =======================================================
     Render
  ======================================================= */

  return (
    <div className="max-w-6xl space-y-6">
      {/* ===================================================
          Back button
      =================================================== */}

      <div className="flex items-center gap-3">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => router.push("/purchases")}
        >
          <ArrowLeft size={16} />
          Back to Purchases
        </Button>
      </div>

      {/* ===================================================
          Header
      =================================================== */}

      <PageHeader
        title="New Purchase Order"
        subtitle="Record goods received from a supplier and update inventory."
      />

      {/* ===================================================
          Main form
      =================================================== */}

      <form
        onSubmit={handleSubmit}
        className="space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800"
      >
        {/* =================================================
            Purchase information
        ================================================= */}

        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {/* Supplier */}

          <Select
            label="Supplier / Vendor"
            options={suppliers.map((supplier) => ({
              value: String(supplier.id),

              label: supplier.companyName,
            }))}
            value={supplierId}
            onChange={(event) => setSupplierId(event.target.value)}
            required
          />

          {/* Warehouse */}

          <Select
            label="Destination Warehouse"
            options={warehouses.map((warehouse) => ({
              value: String(warehouse.id),

              /*
               * Warehouse does NOT have `code`.
               *
               * Correct fields:
               * - name
               * - location
               * - managerName
               * - contactPhone
               */
              label: warehouse.location
                ? `${warehouse.name} — ${warehouse.location}`
                : warehouse.name,
            }))}
            value={warehouseId}
            onChange={(event) => setWarehouseId(event.target.value)}
            required
          />

          {/* Purchase date */}

          <Input
            label="Purchase Date"
            type="date"
            value={orderDate}
            onChange={(event) => setOrderDate(event.target.value)}
            required
          />
        </div>

        {/* =================================================
            Products
        ================================================= */}

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-gray-800 dark:text-gray-200">
              Products
            </h2>

            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={addLine}
              disabled={loading || products.length === 0}
            >
              <Plus size={14} />
              Add Product
            </Button>
          </div>

          {/* Product table */}

          <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-gray-50 dark:bg-gray-700/60">
                <tr>
                  <th className="p-3">Product</th>

                  <th className="p-3">Quantity</th>

                  <th className="p-3">Unit Cost</th>

                  <th className="p-3 text-right">Subtotal</th>

                  <th className="p-3 text-center">Action</th>
                </tr>
              </thead>

              <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                {lines.length === 0 ? (
                  <tr>
                    <td
                      colSpan={5}
                      className="p-8 text-center text-sm text-gray-500"
                    >
                      No products added. Click "Add Product" to add an item.
                    </td>
                  </tr>
                ) : (
                  lines.map((line, index) => (
                    <tr key={`${line.productId}-${index}`}>
                      {/* Product */}

                      <td className="p-3">
                        <select
                          className="w-full rounded-lg border border-gray-200 bg-gray-50 p-2 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-white"
                          value={line.productId}
                          onChange={(event) =>
                            updateLine(index, "productId", event.target.value)
                          }
                        >
                          {products.map((product) => (
                            <option key={product.id} value={String(product.id)}>
                              {product.name}

                              {product.sku ? ` (${product.sku})` : ""}
                            </option>
                          ))}
                        </select>
                      </td>

                      {/* Quantity */}

                      <td className="p-3">
                        <input
                          type="number"
                          min="1"
                          step="1"
                          value={line.quantity}
                          onChange={(event) =>
                            updateLine(index, "quantity", event.target.value)
                          }
                          className="w-28 rounded-lg border border-gray-200 bg-gray-50 p-2 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
                        />
                      </td>

                      {/* Unit cost */}

                      <td className="p-3">
                        <input
                          type="number"
                          min="0"
                          step="0.01"
                          value={line.unitCost}
                          onChange={(event) =>
                            updateLine(index, "unitCost", event.target.value)
                          }
                          className="w-36 rounded-lg border border-gray-200 bg-gray-50 p-2 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
                        />
                      </td>

                      {/* Subtotal */}

                      <td className="p-3 text-right font-semibold">
                        {formatCurrency(line.quantity * line.unitCost)}
                      </td>

                      {/* Remove */}

                      <td className="p-3 text-center">
                        <button
                          type="button"
                          onClick={() => removeLine(index)}
                          disabled={lines.length === 1}
                          className="rounded p-2 text-gray-400 hover:text-red-500 disabled:cursor-not-allowed disabled:opacity-30"
                          title="Remove product"
                        >
                          <Trash2 size={16} />
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* =================================================
            Payment + notes
        ================================================= */}

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          <div className="space-y-4">
            <Select
              label="Payment Status"
              options={[
                {
                  value: "unpaid",
                  label: "Unpaid",
                },
                {
                  value: "partial",
                  label: "Partially Paid",
                },
                {
                  value: "paid",
                  label: "Paid",
                },
              ]}
              value={paymentStatus}
              onChange={(event) =>
                setPaymentStatus(event.target.value as PaymentStatus)
              }
            />

            <Input
              label="Notes"
              placeholder="Purchase notes"
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
            />
          </div>

          {/* =================================================
              Summary
          ================================================= */}

          <div className="rounded-xl border border-gray-200 bg-gray-50 p-5 dark:border-gray-700 dark:bg-gray-700/40">
            <div className="flex justify-between text-sm">
              <span>Total Products</span>

              <strong>{lines.length}</strong>
            </div>

            <div className="mt-3 flex justify-between text-sm">
              <span>Total Units</span>

              <strong>{totalUnits}</strong>
            </div>

            <div className="mt-4 flex items-center justify-between border-t pt-4">
              <span className="font-bold">Purchase Total</span>

              <span className="text-xl font-extrabold text-blue-600">
                {formatCurrency(totalAmount)}
              </span>
            </div>
          </div>
        </div>

        {/* =================================================
            Actions
        ================================================= */}

        <div className="flex justify-end gap-3 border-t pt-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/purchases")}
            disabled={submitting}
          >
            Cancel
          </Button>

          <Button
            type="submit"
            className="gap-2"
            disabled={submitting || loading || lines.length === 0}
          >
            <CheckCircle size={16} />

            {submitting ? "Saving Purchase..." : "Save Purchase"}
          </Button>
        </div>
      </form>
    </div>
  );
}
