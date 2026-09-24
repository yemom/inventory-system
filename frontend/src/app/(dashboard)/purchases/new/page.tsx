'use client';
import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Plus, Trash2, ArrowLeft, Truck, CheckCircle } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { formatCurrency } from '@/lib/utils';
import { useSuppliers } from '@/hooks/useSuppliers';
import { useProducts } from '@/hooks/useProducts';
import { useWarehouses } from '@/hooks/useWarehouses';
import { useToast } from '@/components/ui/ToastProvider';

interface PurchaseLine {
  productId: string;
  productName: string;
  quantity: number;
  unitCost: number;
}

export default function NewPurchasePage() {
  const router = useRouter();
  const { toast } = useToast();
  const { data: suppliers = [] } = useSuppliers();
  const { data: products = [] } = useProducts();
  const { data: warehouses = [] } = useWarehouses();

  const [supplierId, setSupplierId] = useState(suppliers[0]?.id || '');
  const [warehouseId, setWarehouseId] = useState(warehouses[0]?.id || '');
  const [orderDate, setOrderDate] = useState(new Date().toISOString().slice(0, 10));
  const [paymentStatus, setPaymentStatus] = useState<'paid' | 'partial' | 'unpaid'>('unpaid');
  const [notes, setNotes] = useState('');

  const [lines, setLines] = useState<PurchaseLine[]>([
    { productId: products[0]?.id || 'p1', productName: products[0]?.name || '', quantity: 10, unitCost: products[0]?.costPrice || 100 }
  ]);

  const addLine = () => {
    const prod = products[0];
    if (!prod) return;
    setLines([...lines, { productId: prod.id, productName: prod.name, quantity: 5, unitCost: prod.costPrice }]);
  };

  const updateLine = (idx: number, field: keyof PurchaseLine, value: any) => {
    setLines(prev => prev.map((line, i) => {
      if (i !== idx) return line;
      if (field === 'productId') {
        const prod = products.find(p => p.id === value);
        return {
          ...line,
          productId: value,
          productName: prod ? prod.name : line.productName,
          unitCost: prod ? prod.costPrice : line.unitCost
        };
      }
      return { ...line, [field]: value };
    }));
  };

  const removeLine = (idx: number) => {
    if (lines.length === 1) return;
    setLines(lines.filter((_, i) => i !== idx));
  };

  const totalAmount = lines.reduce((acc, curr) => acc + (curr.quantity * curr.unitCost), 0);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const poRef = `PUR-${new Date().getFullYear()}-${Math.floor(100000 + Math.random() * 900000)}`;
    toast('success', 'Purchase Order Created', `Reference: ${poRef} for ${formatCurrency(totalAmount)}`);
    router.push('/purchases');
  };

  return (
    <div className="max-w-5xl space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="outline" size="sm" onClick={() => router.push('/purchases')}>
          <ArrowLeft size={16} /> Back to Purchases
        </Button>
      </div>

      <PageHeader 
        title="Create Purchase Order" 
        subtitle="Procure goods from suppliers and schedule inventory stock receiving" 
      />

      <form onSubmit={handleSubmit} className="space-y-6 bg-white dark:bg-gray-800 p-6 rounded-xl border border-gray-200 dark:border-gray-700 shadow-sm">
        {/* Supplier & Warehouse settings */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Select
            label="Supplier / Vendor"
            options={suppliers.map((s: any) => ({ value: s.id, label: s.name }))}
            value={supplierId}
            onChange={e => setSupplierId(e.target.value)}
            required
          />
          <Select
            label="Destination Warehouse"
            options={warehouses.map((w: any) => ({ value: w.id, label: `${w.name} (${w.code})` }))}
            value={warehouseId}
            onChange={e => setWarehouseId(e.target.value)}
            required
          />
          <Input
            label="Order Date"
            type="date"
            value={orderDate}
            onChange={e => setOrderDate(e.target.value)}
            required
          />
        </div>

        {/* Purchase Line Items */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-bold text-gray-800 dark:text-gray-200">Products to Purchase</h4>
            <Button type="button" variant="outline" size="sm" onClick={addLine}>
              <Plus size={14} /> Add Product
            </Button>
          </div>

          <div className="overflow-x-auto border rounded-xl border-gray-200 dark:border-gray-700">
            <table className="w-full text-xs text-left">
              <thead className="bg-gray-50 dark:bg-gray-700/60 text-gray-500 uppercase tracking-wider font-semibold border-b">
                <tr>
                  <th className="p-3">Product</th>
                  <th className="p-3 w-32">Quantity</th>
                  <th className="p-3 w-40">Unit Cost (ETB)</th>
                  <th className="p-3 w-40 text-right">Subtotal</th>
                  <th className="p-3 w-16 text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                {lines.map((line, idx) => (
                  <tr key={idx} className="bg-white dark:bg-gray-800">
                    <td className="p-2.5">
                      <select
                        className="w-full text-xs p-2 rounded-lg border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                        value={line.productId}
                        onChange={e => updateLine(idx, 'productId', e.target.value)}
                      >
                        {products.map((p: any) => (
                          <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>
                        ))}
                      </select>
                    </td>
                    <td className="p-2.5">
                      <input
                        type="number"
                        min="1"
                        className="w-full text-xs p-2 rounded-lg border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                        value={line.quantity}
                        onChange={e => updateLine(idx, 'quantity', parseInt(e.target.value) || 1)}
                      />
                    </td>
                    <td className="p-2.5">
                      <input
                        type="number"
                        min="0"
                        className="w-full text-xs p-2 rounded-lg border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                        value={line.unitCost}
                        onChange={e => updateLine(idx, 'unitCost', parseFloat(e.target.value) || 0)}
                      />
                    </td>
                    <td className="p-2.5 text-right font-bold text-gray-900 dark:text-gray-100">
                      {formatCurrency(line.quantity * line.unitCost)}
                    </td>
                    <td className="p-2.5 text-center">
                      <button
                        type="button"
                        onClick={() => removeLine(idx)}
                        disabled={lines.length === 1}
                        className="p-1 rounded text-gray-400 hover:text-red-500 disabled:opacity-30"
                      >
                        <Trash2 size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Payment & Total Summary */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-start pt-2">
          <div className="space-y-3">
            <Select
              label="Payment Status"
              options={[
                { value: 'unpaid', label: 'Credit Purchase (Unpaid - Accounts Payable)' },
                { value: 'partial', label: 'Partial Advance Payment' },
                { value: 'paid', label: 'Fully Paid (Cash / Bank)' },
              ]}
              value={paymentStatus}
              onChange={e => setPaymentStatus(e.target.value as any)}
            />
            <Input
              label="Notes & Shipping Terms"
              placeholder="e.g. FOB delivery terms, expected arrival by Friday"
              value={notes}
              onChange={e => setNotes(e.target.value)}
            />
          </div>

          <div className="bg-gray-50 dark:bg-gray-700/40 p-4 rounded-xl space-y-3 border border-gray-200 dark:border-gray-700">
            <div className="flex justify-between text-sm text-gray-600 dark:text-gray-400">
              <span>Items Total:</span>
              <span className="font-semibold text-gray-900 dark:text-gray-100">{lines.length} items</span>
            </div>
            <div className="flex justify-between text-sm text-gray-600 dark:text-gray-400">
              <span>Total Units:</span>
              <span className="font-semibold text-gray-900 dark:text-gray-100">
                {lines.reduce((acc, curr) => acc + curr.quantity, 0)} units
              </span>
            </div>
            <div className="pt-2 border-t flex justify-between items-baseline">
              <span className="font-bold text-gray-900 dark:text-gray-100">Purchase Total:</span>
              <span className="text-xl font-extrabold text-blue-600 dark:text-blue-400">
                {formatCurrency(totalAmount)}
              </span>
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t">
          <Button type="button" variant="outline" onClick={() => router.push('/purchases')}>
            Cancel
          </Button>
          <Button type="submit" className="gap-2">
            <CheckCircle size={16} /> Submit Purchase Order
          </Button>
        </div>
      </form>
    </div>
  );
}

