"use client";

import React, { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, CheckCircle, Plus, Trash2 } from "lucide-react";

import PageHeader from "@/components/ui/PageHeader";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import { formatCurrency } from "@/lib/utils";

import { useProducts } from "@/hooks/useProducts";
import { useCustomers } from "@/hooks/useCustomers";
import { useWarehouses } from "@/hooks/useWarehouses";

import { salesApi } from "@/lib/api/salesApi";
import { useToast } from "@/components/ui/ToastProvider";

interface SaleLine {
  productId: string;
  productName: string;
  sku: string;
  availableStock: number;
  quantity: number;
  unitPrice: number;
}

type PaymentMethod = "CASH" | "BANK" | "CARD" | "MOBILE_MONEY";

type PaymentStatus = "PAID" | "UNPAID";

export default function NewSalePage() {
  const router = useRouter();
  const { toast } = useToast();

  // --------------------------------------------------
  // Data
  // --------------------------------------------------

  const { data: products = [], isLoading: productsLoading } = useProducts();

  const { data: customers = [], isLoading: customersLoading } = useCustomers();

  const { data: warehouses = [], isLoading: warehousesLoading } =
    useWarehouses();

  // --------------------------------------------------
  // Form state
  // --------------------------------------------------

  const [customerId, setCustomerId] = useState<string>("");

  const [warehouseId, setWarehouseId] = useState<string>("");

  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");

  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus>("PAID");

  const [discount, setDiscount] = useState<number>(0);

  const [lines, setLines] = useState<SaleLine[]>([]);

  const [submitting, setSubmitting] = useState<boolean>(false);

  // --------------------------------------------------
  // Automatically select first customer
  // --------------------------------------------------

  useEffect(() => {
    if (!customerId && customers.length > 0) {
      setCustomerId(String(customers[0].id));
    }
  }, [customers, customerId]);

  // --------------------------------------------------
  // Automatically select first warehouse
  // --------------------------------------------------

  useEffect(() => {
    if (!warehouseId && warehouses.length > 0) {
      setWarehouseId(String(warehouses[0].id));
    }
  }, [warehouses, warehouseId]);

  // --------------------------------------------------
  // Automatically add first product
  // --------------------------------------------------

  useEffect(() => {
    if (lines.length === 0 && products.length > 0) {
      const product =
        products.find((item: any) => Number(item.quantity ?? 0) > 0) ??
        products[0];

      setLines([
        {
          productId: String(product.id),
          productName: product.name ?? "",
          sku: product.sku ?? "",
          availableStock: Number(product.quantity ?? 0),
          quantity: Number(product.quantity ?? 0) > 0 ? 1 : 0,
          unitPrice: Number(product.sellingPrice ?? 0),
        },
      ]);
    }
  }, [products, lines.length]);

  // --------------------------------------------------
  // Loading state
  // --------------------------------------------------

  const loading = productsLoading || customersLoading || warehousesLoading;

  // --------------------------------------------------
  // Add product line
  // --------------------------------------------------

  const addLine = () => {
    const availableProducts = products.filter(
      (product: any) => Number(product.quantity ?? 0) > 0,
    );

    if (availableProducts.length === 0) {
      toast(
        "error",
        "No stock available",
        "There are no products with available stock.",
      );
      return;
    }

    const usedIds = new Set(lines.map((line) => line.productId));

    const product =
      availableProducts.find((item: any) => !usedIds.has(String(item.id))) ??
      availableProducts[0];

    setLines((current) => [
      ...current,
      {
        productId: String(product.id),
        productName: product.name ?? "",
        sku: product.sku ?? "",
        availableStock: Number(product.quantity ?? 0),
        quantity: 1,
        unitPrice: Number(product.sellingPrice ?? 0),
      },
    ]);
  };

  // --------------------------------------------------
  // Update sale line
  // --------------------------------------------------

  const updateLine = (
    index: number,
    field: keyof SaleLine,
    value: string | number,
  ) => {
    setLines((current) =>
      current.map((line, lineIndex) => {
        if (lineIndex !== index) {
          return line;
        }

        // Product changed
        if (field === "productId") {
          const product = products.find(
            (item: any) => String(item.id) === String(value),
          );

          if (!product) {
            return line;
          }

          const stock = Number(product.quantity ?? 0);

          return {
            ...line,
            productId: String(product.id),
            productName: product.name ?? "",
            sku: product.sku ?? "",
            availableStock: stock,
            quantity:
              stock > 0 ? Math.min(Math.max(line.quantity, 1), stock) : 0,
            unitPrice: Number(product.sellingPrice ?? 0),
          };
        }

        // Quantity changed
        if (field === "quantity") {
          const requested = Math.max(1, Math.floor(Number(value) || 1));

          if (line.availableStock <= 0) {
            return {
              ...line,
              quantity: 0,
            };
          }

          return {
            ...line,
            quantity: Math.min(requested, line.availableStock),
          };
        }

        // Unit price changed
        if (field === "unitPrice") {
          return {
            ...line,
            unitPrice: Math.max(0, Number(value) || 0),
          };
        }

        return {
          ...line,
          [field]: value,
        };
      }),
    );
  };

  // --------------------------------------------------
  // Remove line
  // --------------------------------------------------

  const removeLine = (index: number) => {
    if (lines.length === 1) {
      toast(
        "error",
        "Cannot remove product",
        "A sale must contain at least one product.",
      );
      return;
    }

    setLines((current) =>
      current.filter((_, lineIndex) => lineIndex !== index),
    );
  };

  // --------------------------------------------------
  // Calculations
  // --------------------------------------------------

  const subtotal = useMemo(() => {
    return lines.reduce(
      (total, line) => total + line.quantity * line.unitPrice,
      0,
    );
  }, [lines]);

  const safeDiscount = Math.min(Math.max(0, Number(discount) || 0), subtotal);

  const finalTotal = Math.max(0, subtotal - safeDiscount);

  const totalUnits = useMemo(() => {
    return lines.reduce((total, line) => total + line.quantity, 0);
  }, [lines]);

  // --------------------------------------------------
  // Submit sale
  // --------------------------------------------------

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (submitting) {
      return;
    }

    // Customer validation
    if (!customerId) {
      toast(
        "error",
        "Customer required",
        "Select a customer before recording the sale.",
      );
      return;
    }

    // Warehouse validation
    if (!warehouseId) {
      toast(
        "error",
        "Warehouse required",
        "Select the warehouse from which the stock is sold.",
      );
      return;
    }

    // Product validation
    if (lines.length === 0) {
      toast("error", "No products", "Add at least one product.");
      return;
    }

    // Validate every line
    const invalidLine = lines.find(
      (line) =>
        !line.productId ||
        line.quantity <= 0 ||
        line.unitPrice < 0 ||
        line.quantity > line.availableStock,
    );

    if (invalidLine) {
      toast(
        "error",
        "Invalid sale items",
        "Check quantities and make sure every product has enough stock.",
      );
      return;
    }

    // Find selected customer
    const customer = customers.find(
      (item: any) => String(item.id) === customerId,
    );

    const customerName = customer?.name?.trim() ?? "";

    if (!customerName) {
      toast(
        "error",
        "Invalid customer",
        "The selected customer does not have a valid name.",
      );
      return;
    }

    /*
     * IMPORTANT:
     *
     * Current backend sale request accepts:
     *
     * customerName
     * discount
     * paymentMethod
     * paymentStatus
     * items[]
     *
     * The current backend does NOT accept warehouseId.
     *
     * Therefore warehouseId is used by the UI for
     * validation/selection, but is not sent here.
     */

    const payload = {
      customerName,

      discount: safeDiscount,

      paymentMethod,

      paymentStatus,

      items: lines.map((line) => ({
        productId: Number(line.productId),
        quantity: Number(line.quantity),
        unitPrice: Number(line.unitPrice),
      })),
    };

    try {
      setSubmitting(true);

      const created = await salesApi.create(payload);

      const reference = created?.reference ?? created?.orderNumber ?? "Sale";

      toast(
        "success",
        "Sale recorded",
        `${reference} was successfully saved and inventory was updated.`,
      );

      router.push("/sales");
    } catch (error: unknown) {
      const responseError = (
        error as {
          response?: {
            data?: {
              message?: string;
              error?: string;
            };
          };
          message?: string;
        }
      )?.response?.data;

      const message =
        responseError?.message ??
        responseError?.error ??
        (
          error as {
            message?: string;
          }
        )?.message ??
        "Unable to record the sale.";

      toast("error", "Sale failed", String(message));
    } finally {
      setSubmitting(false);
    }
  };

  // --------------------------------------------------
  // UI
  // --------------------------------------------------

  return (
    <div className="max-w-6xl space-y-6">
      {/* Back button */}
      <div className="flex items-center gap-3">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => router.push("/sales")}
        >
          <ArrowLeft size={16} />
          Back to Sales
        </Button>
      </div>

      {/* Page header */}
      <PageHeader
        title="New Sale"
        subtitle="Create a sale, validate stock and automatically reduce inventory."
      />

      {/* Main form */}
      <form
        onSubmit={handleSubmit}
        className="space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800"
      >
        {/* Customer / Warehouse / Payment */}
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {/* Customer */}
          <Select
            label="Customer"
            options={customers.map((customer: any) => ({
              value: String(customer.id),
              label: customer.name ?? `Customer #${customer.id}`,
            }))}
            value={customerId}
            onChange={(event) => setCustomerId(event.target.value)}
            required
          />

          {/* Warehouse */}
          <Select
            label="Warehouse"
            options={warehouses.map((warehouse: any) => ({
              value: String(warehouse.id),

              label: warehouse.location
                ? `${warehouse.name} — ${warehouse.location}`
                : warehouse.name,
            }))}
            value={warehouseId}
            onChange={(event) => setWarehouseId(event.target.value)}
            required
          />

          {/* Payment Method */}
          <Select
            label="Payment Method"
            options={[
              {
                value: "CASH",
                label: "Cash",
              },
              {
                value: "BANK",
                label: "Bank Transfer",
              },
              {
                value: "CARD",
                label: "Card",
              },
              {
                value: "MOBILE_MONEY",
                label: "Mobile Money",
              },
            ]}
            value={paymentMethod}
            onChange={(event) =>
              setPaymentMethod(event.target.value as PaymentMethod)
            }
          />
        </div>

        {/* Products */}
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
              disabled={loading}
            >
              <Plus size={14} />
              Add Product
            </Button>
          </div>

          <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-gray-50 dark:bg-gray-700/60">
                <tr>
                  <th className="p-3">Product</th>

                  <th className="p-3">Available</th>

                  <th className="p-3">Quantity</th>

                  <th className="p-3">Unit Price</th>

                  <th className="p-3 text-right">Subtotal</th>

                  <th className="p-3 text-center">Action</th>
                </tr>
              </thead>

              <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
                {lines.map((line, index) => (
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
                        {products.map((product: any) => (
                          <option
                            key={product.id}
                            value={String(product.id)}
                            disabled={Number(product.quantity ?? 0) <= 0}
                          >
                            {product.name}

                            {product.sku ? ` (${product.sku})` : ""}
                          </option>
                        ))}
                      </select>
                    </td>

                    {/* Available stock */}
                    <td className="p-3">
                      <span
                        className={
                          line.availableStock <= 0
                            ? "font-semibold text-red-600"
                            : "font-semibold text-green-600"
                        }
                      >
                        {line.availableStock}
                      </span>
                    </td>

                    {/* Quantity */}
                    <td className="p-3">
                      <input
                        type="number"
                        min={line.availableStock > 0 ? 1 : 0}
                        max={line.availableStock}
                        step="1"
                        value={line.quantity}
                        onChange={(event) =>
                          updateLine(index, "quantity", event.target.value)
                        }
                        className="w-28 rounded-lg border border-gray-200 bg-gray-50 p-2 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
                      />
                    </td>

                    {/* Unit price */}
                    <td className="p-3">
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        value={line.unitPrice}
                        onChange={(event) =>
                          updateLine(index, "unitPrice", event.target.value)
                        }
                        className="w-36 rounded-lg border border-gray-200 bg-gray-50 p-2 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
                      />
                    </td>

                    {/* Subtotal */}
                    <td className="p-3 text-right font-semibold">
                      {formatCurrency(line.quantity * line.unitPrice)}
                    </td>

                    {/* Remove */}
                    <td className="p-3 text-center">
                      <button
                        type="button"
                        onClick={() => removeLine(index)}
                        disabled={lines.length === 1}
                        className="rounded p-2 text-gray-400 hover:text-red-500 disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        <Trash2 size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Payment and totals */}
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          {/* Payment details */}
          <div className="space-y-4">
            <Select
              label="Payment Status"
              options={[
                {
                  value: "PAID",
                  label: "Paid",
                },
                {
                  value: "UNPAID",
                  label: "Unpaid",
                },
              ]}
              value={paymentStatus}
              onChange={(event) =>
                setPaymentStatus(event.target.value as PaymentStatus)
              }
            />

            <Input
              label="Discount"
              type="number"
              min="0"
              max={subtotal}
              step="0.01"
              value={discount}
              onChange={(event) =>
                setDiscount(Math.max(0, Number(event.target.value) || 0))
              }
            />
          </div>

          {/* Summary */}
          <div className="rounded-xl border border-gray-200 bg-gray-50 p-5 dark:border-gray-700 dark:bg-gray-700/40">
            <div className="flex justify-between text-sm">
              <span>Total Products</span>

              <strong>{lines.length}</strong>
            </div>

            <div className="mt-3 flex justify-between text-sm">
              <span>Total Units</span>

              <strong>{totalUnits}</strong>
            </div>

            <div className="mt-3 flex justify-between text-sm">
              <span>Subtotal</span>

              <strong>{formatCurrency(subtotal)}</strong>
            </div>

            <div className="mt-3 flex justify-between text-sm">
              <span>Discount</span>

              <strong>{formatCurrency(safeDiscount)}</strong>
            </div>

            <div className="mt-4 flex items-center justify-between border-t pt-4">
              <span className="font-bold">Final Total</span>

              <span className="text-xl font-extrabold text-green-600">
                {formatCurrency(finalTotal)}
              </span>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 border-t pt-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push("/sales")}
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

            {submitting ? "Saving Sale..." : "Complete Sale"}
          </Button>
        </div>
      </form>
    </div>
  );
}
