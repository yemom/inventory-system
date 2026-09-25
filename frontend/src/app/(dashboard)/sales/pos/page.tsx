"use client";

import React, { useEffect, useMemo, useState } from "react";

import {
  Search,
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  CreditCard,
  Banknote,
  Smartphone,
  RefreshCw,
  CheckCircle,
  User,
} from "lucide-react";

import PageHeader from "@/components/ui/PageHeader";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Modal from "@/components/ui/Modal";
import Badge from "@/components/ui/Badge";

import { formatCurrency } from "@/lib/utils";

import { useProducts } from "@/hooks/useProducts";

import { customersApi, type Customer } from "@/lib/api/customersApi";

import type { Product } from "@/lib/api/productsApi";

import { useToast } from "@/components/ui/ToastProvider";

interface CartItem {
  product: Product;
  quantity: number;
  discount: number;
}

interface ReceiptOrder {
  reference: string;
  customerName: string;
  date: string;
  items: CartItem[];
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  total: number;
  paymentMethod: "cash" | "bank" | "mobile";
  tendered: number;
  change: number;
}

export default function POSPage() {
  const { data: products = [], isLoading: loadingProducts } = useProducts();

  const { toast } = useToast();

  /*
   * Customers are loaded directly from customersApi.
   *
   * This avoids the previous build error:
   * "Export useCustomers doesn't exist in target module"
   */
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loadingCustomers, setLoadingCustomers] = useState(false);

  const [search, setSearch] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string>("All");

  const [cart, setCart] = useState<CartItem[]>([]);

  const [selectedCustomerId, setSelectedCustomerId] =
    useState<string>("walk-in");

  const [discountPercent, setDiscountPercent] = useState<number>(0);

  const [taxEnabled, setTaxEnabled] = useState<boolean>(true);

  const [paymentMethod, setPaymentMethod] = useState<
    "cash" | "bank" | "mobile"
  >("cash");

  const [tenderedAmount, setTenderedAmount] = useState<string>("");

  const [receiptOrder, setReceiptOrder] = useState<ReceiptOrder | null>(null);

  /*
   * Load customers.
   */
  useEffect(() => {
    let mounted = true;

    const loadCustomers = async () => {
      try {
        setLoadingCustomers(true);

        const data = await customersApi.list();

        if (mounted) {
          setCustomers(Array.isArray(data) ? data : []);
        }
      } catch (error: unknown) {
        if (!mounted) return;

        const err = error as {
          response?: {
            data?: {
              message?: string;
              error?: string;
            };
          };
          message?: string;
        };

        toast(
          "error",
          "Customer Load Error",
          err.response?.data?.message ??
            err.response?.data?.error ??
            err.message ??
            "Unable to load customers.",
        );
      } finally {
        if (mounted) {
          setLoadingCustomers(false);
        }
      }
    };

    void loadCustomers();

    return () => {
      mounted = false;
    };
  }, [toast]);

  /*
   * Build category list from actual products.
   */
  const categories = useMemo(() => {
    const categoryNames = products
      .map((product) => product.categoryName)
      .filter((category): category is string => Boolean(category));

    return ["All", ...Array.from(new Set(categoryNames))];
  }, [products]);

  /*
   * Filter active products by search and category.
   */
  const filteredProducts = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();

    return products.filter((product) => {
      const productName = product.name?.toLowerCase() ?? "";

      const sku = product.sku?.toLowerCase() ?? "";

      const matchesSearch =
        !normalizedSearch ||
        productName.includes(normalizedSearch) ||
        sku.includes(normalizedSearch);

      const matchesCategory =
        selectedCategory === "All" || product.categoryName === selectedCategory;

      return matchesSearch && matchesCategory && product.active === true;
    });
  }, [products, search, selectedCategory]);

  /*
   * Add product to cart.
   */
  const addToCart = (product: Product) => {
    if (product.quantity <= 0) {
      toast("error", "Out of stock", `${product.name} has 0 units available.`);

      return;
    }

    setCart((previousCart) => {
      const existingItem = previousCart.find(
        (item) => item.product.id === product.id,
      );

      if (existingItem) {
        if (existingItem.quantity >= product.quantity) {
          toast(
            "warning",
            "Stock limit reached",
            `Only ${product.quantity} units available in stock.`,
          );

          return previousCart;
        }

        return previousCart.map((item) =>
          item.product.id === product.id
            ? {
                ...item,
                quantity: item.quantity + 1,
              }
            : item,
        );
      }

      return [
        ...previousCart,
        {
          product,
          quantity: 1,
          discount: 0,
        },
      ];
    });
  };

  /*
   * Update cart quantity.
   */
  const updateQuantity = (productId: number, delta: number) => {
    setCart((previousCart) =>
      previousCart
        .map((item) => {
          if (item.product.id !== productId) {
            return item;
          }

          const newQuantity = item.quantity + delta;

          if (newQuantity <= 0) {
            return null;
          }

          if (newQuantity > item.product.quantity) {
            toast(
              "warning",
              "Stock limit",
              `Only ${item.product.quantity} available.`,
            );

            return item;
          }

          return {
            ...item,
            quantity: newQuantity,
          };
        })
        .filter((item): item is CartItem => item !== null),
    );
  };

  /*
   * Remove item from cart.
   */
  const removeFromCart = (productId: number) => {
    setCart((previousCart) =>
      previousCart.filter((item) => item.product.id !== productId),
    );
  };

  /*
   * Clear cart.
   */
  const clearCart = () => {
    setCart([]);
    setDiscountPercent(0);
    setTenderedAmount("");
  };

  /*
   * Financial calculations.
   */
  const subtotal = useMemo(
    () =>
      cart.reduce(
        (sum, item) =>
          sum + Number(item.product.sellingPrice || 0) * item.quantity,
        0,
      ),
    [cart],
  );

  const discountAmount = (subtotal * discountPercent) / 100;

  const taxableAmount = Math.max(0, subtotal - discountAmount);

  /*
   * Ethiopia VAT used by the existing POS design.
   */
  const taxAmount = taxEnabled ? taxableAmount * 0.15 : 0;

  const total = taxableAmount + taxAmount;

  const tendered = Number.parseFloat(tenderedAmount) || 0;

  const change = tendered > total ? tendered - total : 0;

  /*
   * Complete checkout.
   *
   * This currently creates the receipt state used by
   * the POS screen. The actual /sales API should be
   * called here if you want the transaction persisted
   * to the backend.
   */
  const handleCheckout = () => {
    if (cart.length === 0) {
      toast("error", "Cart is empty", "Add products to cart before checkout.");

      return;
    }

    /*
     * Cash payments must have enough money.
     */
    if (paymentMethod === "cash" && tendered < total) {
      toast(
        "warning",
        "Insufficient payment",
        "Tendered amount is less than the total.",
      );

      return;
    }

    /*
     * Find selected customer.
     *
     * Customer IDs can be numbers in the backend,
     * therefore everything is compared as strings.
     */
    const customerObj =
      selectedCustomerId === "walk-in"
        ? undefined
        : customers.find(
            (customer) => String(customer.id) === selectedCustomerId,
          );

    const reference = `SALE-${new Date().getFullYear()}-${Math.floor(
      100000 + Math.random() * 900000,
    )}`;

    const orderData: ReceiptOrder = {
      reference,

      customerName: customerObj?.name ?? "Walk-in Customer",

      date: new Date().toISOString(),

      items: [...cart],

      subtotal,

      discountAmount,

      taxAmount,

      total,

      paymentMethod,

      tendered: paymentMethod === "cash" ? tendered : total,

      change: paymentMethod === "cash" ? change : 0,
    };

    setReceiptOrder(orderData);

    toast("success", "Sale completed", `Transaction ${reference} created.`);

    clearCart();
  };

  return (
    <div className="space-y-4">
      <PageHeader
        title="POS"
        subtitle="Rapid barcode checkout and instant transaction recording"
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={clearCart}
              disabled={cart.length === 0}
            >
              <RefreshCw size={14} />
              Clear Cart
            </Button>
          </div>
        }
      />

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        {/* =========================================================
            LEFT SIDE - PRODUCT CATALOG
        ========================================================== */}

        <div className="space-y-4 lg:col-span-7 xl:col-span-8">
          {/* Search and categories */}
          <div className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800 sm:flex-row">
            <div className="relative flex-1">
              <Search
                className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
                size={18}
              />

              <input
                type="text"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search by product name or SKU..."
                className="w-full rounded-lg border border-gray-200 bg-gray-50 py-2 pl-10 pr-4 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
              />
            </div>

            <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
              {categories.map((category) => (
                <button
                  key={category}
                  type="button"
                  onClick={() => setSelectedCategory(category)}
                  className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                    selectedCategory === category
                      ? "bg-blue-600 text-white shadow-sm"
                      : "bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300"
                  }`}
                >
                  {category}
                </button>
              ))}
            </div>
          </div>

          {/* Product grid */}
          <div className="grid max-h-[620px] grid-cols-2 gap-3 overflow-y-auto pr-1 sm:grid-cols-3 xl:grid-cols-4">
            {loadingProducts ? (
              Array.from({ length: 8 }).map((_, index) => (
                <div
                  key={index}
                  className="h-36 animate-pulse rounded-xl bg-gray-100 dark:bg-gray-800"
                />
              ))
            ) : filteredProducts.length === 0 ? (
              <div className="col-span-full py-16 text-center text-gray-400">
                No products match your search or filter.
              </div>
            ) : (
              filteredProducts.map((product) => {
                const inCart = cart.find(
                  (item) => item.product.id === product.id,
                );

                const isOutOfStock = product.quantity <= 0;

                return (
                  <div
                    key={product.id}
                    onClick={() => !isOutOfStock && addToCart(product)}
                    className={`relative flex cursor-pointer flex-col justify-between rounded-xl border bg-white p-3.5 transition-all dark:bg-gray-800 ${
                      isOutOfStock
                        ? "cursor-not-allowed border-gray-200 opacity-50 dark:border-gray-700"
                        : "border-gray-200 hover:border-blue-500 hover:shadow-md dark:border-gray-700"
                    }`}
                  >
                    <div>
                      <div className="mb-1 flex items-start justify-between gap-1">
                        <span className="font-mono text-[10px] uppercase tracking-wider text-gray-400">
                          {product.sku}
                        </span>

                        <Badge
                          variant={
                            isOutOfStock
                              ? "danger"
                              : product.quantity <= (product.reorderLevel ?? 0)
                                ? "warning"
                                : "success"
                          }
                        >
                          {product.quantity} {product.unit}
                        </Badge>
                      </div>

                      <h4 className="line-clamp-2 text-sm font-semibold leading-tight text-gray-900 dark:text-gray-100">
                        {product.name}
                      </h4>

                      <p className="mt-0.5 text-xs text-gray-400">
                        {product.categoryName || "Uncategorized"}
                      </p>
                    </div>

                    <div className="mt-3 flex items-center justify-between border-t border-gray-100 pt-2 dark:border-gray-700/50">
                      <span className="text-sm font-bold text-blue-600 dark:text-blue-400">
                        {formatCurrency(product.sellingPrice || 0)}
                      </span>

                      {inCart && (
                        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
                          {inCart.quantity}
                        </span>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* =========================================================
            RIGHT SIDE - CART
        ========================================================== */}

        <div className="flex h-[700px] flex-col rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800 lg:col-span-5 xl:col-span-4">
          {/* Cart header */}
          <div className="flex items-center justify-between border-b border-gray-200 p-4 dark:border-gray-700">
            <div className="flex items-center gap-2">
              <ShoppingCart size={18} className="text-blue-600" />

              <h3 className="font-bold text-gray-900 dark:text-gray-100">
                Active Cart
              </h3>

              <span className="text-xs text-gray-400">
                ({cart.length} items)
              </span>
            </div>

            {/* Customer */}
            <div className="flex items-center gap-1.5">
              <User size={14} className="text-gray-400" />

              <select
                value={selectedCustomerId}
                onChange={(event) => setSelectedCustomerId(event.target.value)}
                disabled={loadingCustomers}
                className="max-w-[150px] rounded-lg border border-gray-200 bg-gray-50 px-2 py-1 text-xs text-gray-800 focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
              >
                <option value="walk-in">Walk-in Customer</option>

                {customers.map((customer) => (
                  <option key={customer.id} value={String(customer.id)}>
                    {customer.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Cart items */}
          <div className="flex-1 space-y-2.5 divide-y divide-gray-100 overflow-y-auto p-4 dark:divide-gray-700/50">
            {cart.length === 0 ? (
              <div className="flex h-full flex-col items-center justify-center py-16 text-center text-gray-400">
                <ShoppingCart
                  size={40}
                  className="mb-2 stroke-1 text-gray-300"
                />

                <p className="text-sm font-medium">Cart is empty</p>

                <p className="text-xs text-gray-500">
                  Select items from the catalog
                </p>
              </div>
            ) : (
              cart.map((item) => (
                <div
                  key={item.product.id}
                  className="flex items-center justify-between gap-2 pt-2.5 first:pt-0"
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-semibold text-gray-900 dark:text-gray-100">
                      {item.product.name}
                    </p>

                    <p className="text-[11px] text-gray-500">
                      {formatCurrency(item.product.sellingPrice)} ×{" "}
                      {item.quantity}
                    </p>
                  </div>

                  {/* Quantity controls */}
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => updateQuantity(item.product.id, -1)}
                      className="flex h-6 w-6 items-center justify-center rounded bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300"
                    >
                      <Minus size={12} />
                    </button>

                    <span className="w-7 text-center font-mono text-xs font-bold text-gray-800 dark:text-gray-200">
                      {item.quantity}
                    </span>

                    <button
                      type="button"
                      onClick={() => updateQuantity(item.product.id, 1)}
                      className="flex h-6 w-6 items-center justify-center rounded bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300"
                    >
                      <Plus size={12} />
                    </button>
                  </div>

                  <span className="w-16 text-right text-xs font-semibold text-gray-900 dark:text-gray-100">
                    {formatCurrency(item.product.sellingPrice * item.quantity)}
                  </span>

                  <button
                    type="button"
                    onClick={() => removeFromCart(item.product.id)}
                    className="p-1 text-gray-400 hover:text-red-500"
                    title="Remove item"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              ))
            )}
          </div>

          {/* Bill details */}
          <div className="space-y-2.5 border-t border-gray-200 bg-gray-50/50 p-4 dark:border-gray-700 dark:bg-gray-800/50">
            <div className="flex justify-between text-xs text-gray-600 dark:text-gray-400">
              <span>Subtotal</span>

              <span className="font-medium text-gray-900 dark:text-gray-100">
                {formatCurrency(subtotal)}
              </span>
            </div>

            {/* Discount */}
            <div className="flex items-center justify-between text-xs">
              <span className="text-gray-600 dark:text-gray-400">
                Discount (%)
              </span>

              <div className="flex items-center gap-1">
                {[0, 5, 10, 15].map((discount) => (
                  <button
                    key={discount}
                    type="button"
                    onClick={() => setDiscountPercent(discount)}
                    className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                      discountPercent === discount
                        ? "bg-blue-600 text-white"
                        : "bg-gray-200 text-gray-600 dark:bg-gray-700"
                    }`}
                  >
                    {discount}%
                  </button>
                ))}
              </div>
            </div>

            {/* VAT */}
            <div className="flex items-center justify-between text-xs text-gray-600 dark:text-gray-400">
              <label className="flex cursor-pointer items-center gap-1.5">
                <input
                  type="checkbox"
                  checked={taxEnabled}
                  onChange={(event) => setTaxEnabled(event.target.checked)}
                  className="h-3.5 w-3.5 rounded text-blue-600 focus:ring-blue-500"
                />

                <span>Include VAT (15%)</span>
              </label>

              <span className="font-medium">{formatCurrency(taxAmount)}</span>
            </div>

            {/* Total */}
            <div className="flex items-baseline justify-between border-t border-gray-200 pt-2 dark:border-gray-700">
              <span className="text-sm font-bold text-gray-900 dark:text-gray-100">
                Payable Total
              </span>

              <span className="text-xl font-extrabold text-blue-600 dark:text-blue-400">
                {formatCurrency(total)}
              </span>
            </div>

            {/* Payment methods */}
            <div className="grid grid-cols-3 gap-1.5 pt-1">
              {[
                {
                  id: "cash" as const,
                  label: "Cash",
                  icon: Banknote,
                },
                {
                  id: "bank" as const,
                  label: "Bank",
                  icon: CreditCard,
                },
                {
                  id: "mobile" as const,
                  label: "Mobile",
                  icon: Smartphone,
                },
              ].map((method) => {
                const Icon = method.icon;

                return (
                  <button
                    key={method.id}
                    type="button"
                    onClick={() => setPaymentMethod(method.id)}
                    className={`flex items-center justify-center gap-1 rounded-lg border px-2 py-1.5 text-xs font-semibold transition-all ${
                      paymentMethod === method.id
                        ? "border-blue-600 bg-blue-600 text-white shadow-sm"
                        : "border-gray-200 bg-white text-gray-700 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300"
                    }`}
                  >
                    <Icon size={14} />

                    {method.label}
                  </button>
                );
              })}
            </div>

            {/* Cash */}
            {paymentMethod === "cash" && (
              <div className="flex items-center gap-2 pt-1">
                <Input
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="Cash given (ETB)"
                  value={tenderedAmount}
                  onChange={(event) => setTenderedAmount(event.target.value)}
                  className="h-8 text-xs"
                />

                {tendered >= total && total > 0 && (
                  <div className="whitespace-nowrap text-right">
                    <span className="block text-[10px] text-gray-400">
                      Change:
                    </span>

                    <span className="text-xs font-bold text-emerald-600">
                      {formatCurrency(change)}
                    </span>
                  </div>
                )}
              </div>
            )}

            {/* Checkout */}
            <Button
              onClick={handleCheckout}
              disabled={cart.length === 0}
              className="w-full gap-2 py-2.5 text-sm font-bold shadow-md"
            >
              <CheckCircle size={16} />
              Complete Sale ({formatCurrency(total)})
            </Button>
          </div>
        </div>
      </div>

      {/* =========================================================
          RECEIPT MODAL
      ========================================================== */}

      <Modal
        open={Boolean(receiptOrder)}
        onClose={() => setReceiptOrder(null)}
        title="Transaction Receipt"
        size="md"
      >
        {receiptOrder && (
          <div className="space-y-4 font-mono text-xs">
            {/* Receipt header */}
            <div className="space-y-1 border-b pb-3 text-center">
              <h2 className="font-sans text-base font-bold">
                StockFlow Trading PLC
              </h2>

              <p className="text-gray-500">
                TIN: 0012345678 | Addis Ababa, Ethiopia
              </p>

              <p className="text-gray-500">Ref: {receiptOrder.reference}</p>

              <p className="text-gray-400">
                {new Date(receiptOrder.date).toLocaleString()}
              </p>
            </div>

            {/* Customer */}
            <div className="flex justify-between">
              <span className="text-gray-500">Customer:</span>

              <span className="font-sans font-bold">
                {receiptOrder.customerName}
              </span>
            </div>

            {/* Payment */}
            <div className="flex justify-between">
              <span className="text-gray-500">Payment:</span>

              <span className="font-bold uppercase">
                {receiptOrder.paymentMethod}
              </span>
            </div>

            {/* Items */}
            <div className="space-y-1.5 border-y py-2">
              {receiptOrder.items.map((item) => (
                <div
                  key={item.product.id}
                  className="flex justify-between gap-3"
                >
                  <span className="max-w-[200px] truncate">
                    {item.product.name} × {item.quantity}
                  </span>

                  <span>
                    {formatCurrency(item.product.sellingPrice * item.quantity)}
                  </span>
                </div>
              ))}
            </div>

            {/* Totals */}
            <div className="space-y-1 pt-1">
              <div className="flex justify-between text-gray-600">
                <span>Subtotal:</span>

                <span>{formatCurrency(receiptOrder.subtotal)}</span>
              </div>

              {receiptOrder.discountAmount > 0 && (
                <div className="flex justify-between text-emerald-600">
                  <span>Discount:</span>

                  <span>-{formatCurrency(receiptOrder.discountAmount)}</span>
                </div>
              )}

              {receiptOrder.taxAmount > 0 && (
                <div className="flex justify-between text-gray-600">
                  <span>VAT (15%):</span>

                  <span>{formatCurrency(receiptOrder.taxAmount)}</span>
                </div>
              )}

              <div className="flex justify-between border-t pt-1 font-sans text-sm font-bold">
                <span>TOTAL PAID:</span>

                <span>{formatCurrency(receiptOrder.total)}</span>
              </div>

              {receiptOrder.change > 0 && (
                <div className="flex justify-between pt-1 text-emerald-600">
                  <span>Change Given:</span>

                  <span>{formatCurrency(receiptOrder.change)}</span>
                </div>
              )}
            </div>

            {/* Footer */}
            <div className="border-t pt-4 text-center font-sans text-[11px] text-gray-400">
              Thank you for your business!
            </div>

            {/* Actions */}
            <div className="flex gap-2 pt-2 font-sans">
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => window.print()}
              >
                Print Receipt
              </Button>

              <Button className="flex-1" onClick={() => setReceiptOrder(null)}>
                New Transaction
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
