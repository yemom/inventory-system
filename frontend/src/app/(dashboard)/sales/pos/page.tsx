'use client';
import React, { useState } from 'react';
import { 
  Search, Barcode, ShoppingCart, Trash2, Plus, Minus, CreditCard, 
  Banknote, Smartphone, Receipt, CheckCircle, User, AlertCircle, RefreshCw
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Modal from '@/components/ui/Modal';
import Badge from '@/components/ui/Badge';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useProducts } from '@/hooks/useProducts';
import { useCustomers } from '@/hooks/useCustomers';
import { useToast } from '@/components/ui/ToastProvider';
import type { Product, Customer } from '@/lib/api/mockData';

interface CartItem {
  product: Product;
  quantity: number;
  discount: number;
}

export default function POSPage() {
  const { data: products = [], isLoading: loadingProducts } = useProducts();
  const { data: customers = [] } = useCustomers();
  const { toast } = useToast();

  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('All');
  const [cart, setCart] = useState<CartItem[]>([]);
  const [selectedCustomerId, setSelectedCustomerId] = useState<string>('walk-in');
  const [discountPercent, setDiscountPercent] = useState<number>(0);
  const [taxEnabled, setTaxEnabled] = useState<boolean>(true);
  const [paymentMethod, setPaymentMethod] = useState<'cash' | 'bank' | 'mobile'>('cash');
  const [tenderedAmount, setTenderedAmount] = useState<string>('');
  const [receiptOrder, setReceiptOrder] = useState<any | null>(null);

  const categories = ['All', ...Array.from(new Set(products.map(p => p.category)))];

  const filteredProducts = products.filter(p => {
    const matchSearch = p.name.toLowerCase().includes(search.toLowerCase()) || 
                        p.sku.toLowerCase().includes(search.toLowerCase());
    const matchCat = selectedCategory === 'All' || p.category === selectedCategory;
    return matchSearch && matchCat && p.status === 'active';
  });

  const addToCart = (product: Product) => {
    if (product.quantity <= 0) {
      toast('error', 'Out of stock', `${product.name} has 0 units available.`);
      return;
    }
    setCart(prev => {
      const existing = prev.find(item => item.product.id === product.id);
      if (existing) {
        if (existing.quantity >= product.quantity) {
          toast('warning', 'Stock limit reached', `Only ${product.quantity} units available in stock.`);
          return prev;
        }
        return prev.map(item =>
          item.product.id === product.id ? { ...item, quantity: item.quantity + 1 } : item
        );
      }
      return [...prev, { product, quantity: 1, discount: 0 }];
    });
  };

  const updateQuantity = (productId: string, delta: number) => {
    setCart(prev => {
      return prev.map(item => {
        if (item.product.id === productId) {
          const newQty = item.quantity + delta;
          if (newQty <= 0) return null;
          if (newQty > item.product.quantity) {
            toast('warning', 'Stock limit', `Only ${item.product.quantity} available.`);
            return item;
          }
          return { ...item, quantity: newQty };
        }
        return item;
      }).filter(Boolean) as CartItem[];
    });
  };

  const removeFromCart = (productId: string) => {
    setCart(prev => prev.filter(item => item.product.id !== productId));
  };

  const clearCart = () => {
    setCart([]);
    setDiscountPercent(0);
    setTenderedAmount('');
  };

  // Calculations
  const subtotal = cart.reduce((sum, item) => sum + item.product.sellingPrice * item.quantity, 0);
  const discountAmount = (subtotal * discountPercent) / 100;
  const taxableAmount = Math.max(0, subtotal - discountAmount);
  const taxAmount = taxEnabled ? taxableAmount * 0.15 : 0; // 15% VAT Ethiopia standard
  const total = taxableAmount + taxAmount;
  const tendered = parseFloat(tenderedAmount) || 0;
  const change = Math.max(0, tendered - total);

  const handleCheckout = () => {
    if (cart.length === 0) {
      toast('error', 'Cart is empty', 'Add products to cart before checkout.');
      return;
    }
    if (paymentMethod === 'cash' && tendered < total && tendered > 0) {
      toast('warning', 'Insufficient payment', 'Tendered amount is less than total.');
    }

    const customerObj = customers.find((c: any) => c.id === selectedCustomerId);
    const reference = `SALE-${new Date().getFullYear()}-${Math.floor(100000 + Math.random() * 900000)}`;

    const orderData = {
      reference,
      customerName: customerObj ? customerObj.name : 'Walk-in Customer',
      date: new Date().toISOString(),
      items: [...cart],
      subtotal,
      discountAmount,
      taxAmount,
      total,
      paymentMethod,
      tendered: tendered > 0 ? tendered : total,
      change,
    };

    setReceiptOrder(orderData);
    toast('success', 'Sale completed', `Transaction ${reference} created.`);
    clearCart();
  };

  return (
    <div className="space-y-4">
      <PageHeader 
        title="Point of Sale (POS)" 
        subtitle="Rapid barcode checkout and instant transaction recording"
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={clearCart}>
              <RefreshCw size={14} /> Clear Cart
            </Button>
          </div>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Product Catalog & Fast Search */}
        <div className="lg:col-span-7 xl:col-span-8 space-y-4">
          <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm flex flex-col sm:flex-row gap-3">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
              <input
                type="text"
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder="Search by product name, SKU or scan barcode..."
                className="w-full pl-10 pr-4 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
              {categories.map(cat => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
                    selectedCategory === cat
                      ? 'bg-blue-600 text-white shadow-sm'
                      : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          {/* Products Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-3 max-h-[620px] overflow-y-auto pr-1">
            {loadingProducts ? (
              Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="h-36 bg-gray-100 dark:bg-gray-800 animate-pulse rounded-xl" />
              ))
            ) : filteredProducts.length === 0 ? (
              <div className="col-span-full py-16 text-center text-gray-400">
                No products match your search or filter.
              </div>
            ) : (
              filteredProducts.map(product => {
                const inCart = cart.find(i => i.product.id === product.id);
                const isOutOfStock = product.quantity <= 0;
                return (
                  <div
                    key={product.id}
                    onClick={() => !isOutOfStock && addToCart(product)}
                    className={`relative p-3.5 bg-white dark:bg-gray-800 rounded-xl border transition-all cursor-pointer flex flex-col justify-between ${
                      isOutOfStock
                        ? 'opacity-50 border-gray-200 dark:border-gray-700 cursor-not-allowed'
                        : 'border-gray-200 dark:border-gray-700 hover:border-blue-500 hover:shadow-md'
                    }`}
                  >
                    <div>
                      <div className="flex justify-between items-start gap-1 mb-1">
                        <span className="text-[10px] uppercase font-mono tracking-wider text-gray-400">
                          {product.sku}
                        </span>
                        <Badge variant={isOutOfStock ? 'danger' : product.quantity <= product.reorderLevel ? 'warning' : 'success'}>
                          {product.quantity} {product.unit}
                        </Badge>
                      </div>
                      <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 line-clamp-2 leading-tight">
                        {product.name}
                      </h4>
                      <p className="text-xs text-gray-400 mt-0.5">{product.category}</p>
                    </div>

                    <div className="mt-3 flex items-center justify-between pt-2 border-t border-gray-100 dark:border-gray-700/50">
                      <span className="text-sm font-bold text-blue-600 dark:text-blue-400">
                        {formatCurrency(product.sellingPrice)}
                      </span>
                      {inCart && (
                        <span className="w-6 h-6 bg-blue-600 text-white text-xs font-bold rounded-full flex items-center justify-center">
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

        {/* Right Column: Checkout Cart & Summary */}
        <div className="lg:col-span-5 xl:col-span-4 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm flex flex-col h-[700px]">
          {/* Cart Header */}
          <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShoppingCart size={18} className="text-blue-600" />
              <h3 className="font-bold text-gray-900 dark:text-gray-100">Active Cart</h3>
              <span className="text-xs text-gray-400">({cart.length} items)</span>
            </div>
            {/* Customer select */}
            <div className="flex items-center gap-1.5">
              <User size={14} className="text-gray-400" />
              <select
                value={selectedCustomerId}
                onChange={e => setSelectedCustomerId(e.target.value)}
                className="text-xs bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg px-2 py-1 text-gray-800 dark:text-gray-200 focus:outline-none"
              >
                <option value="walk-in">Walk-in Customer</option>
                {customers.map((c: any) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Cart Items List */}
          <div className="flex-1 overflow-y-auto p-4 space-y-2.5 divide-y divide-gray-100 dark:divide-gray-700/50">
            {cart.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center text-center text-gray-400 py-16">
                <ShoppingCart size={40} className="stroke-1 text-gray-300 mb-2" />
                <p className="text-sm font-medium">Cart is empty</p>
                <p className="text-xs text-gray-500">Scan or select items from catalog</p>
              </div>
            ) : (
              cart.map(item => (
                <div key={item.product.id} className="pt-2.5 first:pt-0 flex items-center justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold text-gray-900 dark:text-gray-100 truncate">
                      {item.product.name}
                    </p>
                    <p className="text-[11px] text-gray-500">
                      {formatCurrency(item.product.sellingPrice)} × {item.quantity}
                    </p>
                  </div>

                  {/* Quantity controls */}
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => updateQuantity(item.product.id, -1)}
                      className="w-6 h-6 rounded bg-gray-100 dark:bg-gray-700 flex items-center justify-center text-gray-600 dark:text-gray-300 hover:bg-gray-200"
                    >
                      <Minus size={12} />
                    </button>
                    <span className="w-7 text-center font-mono text-xs font-bold text-gray-800 dark:text-gray-200">
                      {item.quantity}
                    </span>
                    <button
                      onClick={() => updateQuantity(item.product.id, 1)}
                      className="w-6 h-6 rounded bg-gray-100 dark:bg-gray-700 flex items-center justify-center text-gray-600 dark:text-gray-300 hover:bg-gray-200"
                    >
                      <Plus size={12} />
                    </button>
                  </div>

                  <span className="font-semibold text-xs text-gray-900 dark:text-gray-100 w-16 text-right">
                    {formatCurrency(item.product.sellingPrice * item.quantity)}
                  </span>

                  <button
                    onClick={() => removeFromCart(item.product.id)}
                    className="text-gray-400 hover:text-red-500 p-1"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              ))
            )}
          </div>

          {/* Cart Bill Details */}
          <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 space-y-2.5">
            <div className="flex justify-between text-xs text-gray-600 dark:text-gray-400">
              <span>Subtotal</span>
              <span className="font-medium text-gray-900 dark:text-gray-100">{formatCurrency(subtotal)}</span>
            </div>

            {/* Discount & Tax Options */}
            <div className="flex items-center justify-between text-xs">
              <span className="text-gray-600 dark:text-gray-400">Discount (%)</span>
              <div className="flex items-center gap-1">
                {[0, 5, 10, 15].map(d => (
                  <button
                    key={d}
                    onClick={() => setDiscountPercent(d)}
                    className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                      discountPercent === d ? 'bg-blue-600 text-white' : 'bg-gray-200 dark:bg-gray-700 text-gray-600'
                    }`}
                  >
                    {d}%
                  </button>
                ))}
              </div>
            </div>

            <div className="flex items-center justify-between text-xs text-gray-600 dark:text-gray-400">
              <label className="flex items-center gap-1.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={taxEnabled}
                  onChange={e => setTaxEnabled(e.target.checked)}
                  className="rounded text-blue-600 focus:ring-blue-500 w-3.5 h-3.5"
                />
                <span>Include VAT (15%)</span>
              </label>
              <span className="font-medium">{formatCurrency(taxAmount)}</span>
            </div>

            <div className="pt-2 border-t border-gray-200 dark:border-gray-700 flex justify-between items-baseline">
              <span className="text-sm font-bold text-gray-900 dark:text-gray-100">Payable Total</span>
              <span className="text-xl font-extrabold text-blue-600 dark:text-blue-400">
                {formatCurrency(total)}
              </span>
            </div>

            {/* Payment Method Selector */}
            <div className="grid grid-cols-3 gap-1.5 pt-1">
              {[
                { id: 'cash', label: 'Cash', icon: Banknote },
                { id: 'bank', label: 'Bank', icon: CreditCard },
                { id: 'mobile', label: 'Mobile', icon: Smartphone },
              ].map(m => {
                const Icon = m.icon;
                return (
                  <button
                    key={m.id}
                    onClick={() => setPaymentMethod(m.id as any)}
                    className={`py-1.5 px-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 border transition-all ${
                      paymentMethod === m.id
                        ? 'bg-blue-600 text-white border-blue-600 shadow-sm'
                        : 'bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 border-gray-200 dark:border-gray-600'
                    }`}
                  >
                    <Icon size={14} />
                    {m.label}
                  </button>
                );
              })}
            </div>

            {/* Cash Tendered Input */}
            {paymentMethod === 'cash' && (
              <div className="flex gap-2 items-center pt-1">
                <Input
                  type="number"
                  placeholder="Cash given (ETB)"
                  value={tenderedAmount}
                  onChange={e => setTenderedAmount(e.target.value)}
                  className="h-8 text-xs"
                />
                {tendered > total && (
                  <div className="text-right whitespace-nowrap">
                    <span className="text-[10px] text-gray-400 block">Change:</span>
                    <span className="text-xs font-bold text-emerald-600">{formatCurrency(change)}</span>
                  </div>
                )}
              </div>
            )}

            {/* Checkout Action Button */}
            <Button
              onClick={handleCheckout}
              disabled={cart.length === 0}
              className="w-full py-2.5 text-sm font-bold shadow-md gap-2"
            >
              <CheckCircle size={16} /> Complete Sale ({formatCurrency(total)})
            </Button>
          </div>
        </div>
      </div>

      {/* Printable Receipt Modal */}
      <Modal open={!!receiptOrder} onClose={() => setReceiptOrder(null)} title="Transaction Receipt" size="md">
        {receiptOrder && (
          <div className="space-y-4 text-xs font-mono">
            <div className="text-center border-b pb-3 space-y-1">
              <h2 className="text-base font-bold font-sans">StockFlow Trading PLC</h2>
              <p className="text-gray-500">TIN: 0012345678 | Addis Ababa, Ethiopia</p>
              <p className="text-gray-500">Ref: {receiptOrder.reference}</p>
              <p className="text-gray-400">{new Date(receiptOrder.date).toLocaleString()}</p>
            </div>

            <div className="flex justify-between">
              <span className="text-gray-500">Customer:</span>
              <span className="font-bold font-sans">{receiptOrder.customerName}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Payment:</span>
              <span className="uppercase font-bold">{receiptOrder.paymentMethod}</span>
            </div>

            <div className="border-y py-2 space-y-1.5">
              {receiptOrder.items.map((i: CartItem, idx: number) => (
                <div key={idx} className="flex justify-between">
                  <span className="truncate max-w-[200px]">{i.product.name} × {i.quantity}</span>
                  <span>{formatCurrency(i.product.sellingPrice * i.quantity)}</span>
                </div>
              ))}
            </div>

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
              <div className="flex justify-between text-sm font-bold border-t pt-1 font-sans">
                <span>TOTAL PAID:</span>
                <span>{formatCurrency(receiptOrder.total)}</span>
              </div>
              {receiptOrder.change > 0 && (
                <div className="flex justify-between text-emerald-600 pt-1">
                  <span>Change Given:</span>
                  <span>{formatCurrency(receiptOrder.change)}</span>
                </div>
              )}
            </div>

            <div className="text-center pt-4 border-t text-[11px] text-gray-400 font-sans">
              Thank you for your business!
            </div>

            <div className="flex gap-2 pt-2 font-sans">
              <Button variant="outline" className="flex-1" onClick={() => window.print()}>
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


