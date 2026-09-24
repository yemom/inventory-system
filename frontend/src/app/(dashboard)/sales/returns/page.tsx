'use client';
import React, { useEffect, useState } from 'react';
import { RotateCcw, Eye } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { formatCurrency, formatDate } from '@/lib/utils';
import { useToast } from '@/components/ui/ToastProvider';
import { useProducts } from '@/hooks/useProducts';
import { useStockAdjust } from '@/hooks/useInventory';
import { useCreatePayment } from '@/hooks/useFinance';

interface OrderReturn {
  id: string;
  reference: string;
  originalReference: string;
  partyName: string;
  type: 'customer_return';
  date: string;
  amount: number;
  status: string;
  reason: string;
  items: { productId: number | string; productName: string; quantity: number; unitPrice: number; total: number }[];
  stockAdjusted?: boolean;
  refundRecorded?: boolean;
}

const STORAGE_KEY = 'stockflow_sales_returns';

function loadReturns(): OrderReturn[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export default function SalesReturnsPage() {
  const { toast } = useToast();
  const { data: products = [] } = useProducts();
  const adjust = useStockAdjust();
  const createPayment = useCreatePayment();
  const [returnsList, setReturnsList] = useState<OrderReturn[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedReturn, setSelectedReturn] = useState<OrderReturn | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [formRef, setFormRef] = useState('');
  const [formCustomer, setFormCustomer] = useState('');
  const [formProduct, setFormProduct] = useState('');
  const [formQty, setFormQty] = useState(1);
  const [formReason, setFormReason] = useState('Damaged goods');
  const [issueRefund, setIssueRefund] = useState(true);

  useEffect(() => {
    setReturnsList(loadReturns());
  }, []);

  useEffect(() => {
    if (!formProduct && products[0]) setFormProduct(String(products[0].id));
  }, [products, formProduct]);

  const persist = (list: OrderReturn[]) => {
    setReturnsList(list);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  };

  const handleCreateReturn = async (e: React.FormEvent) => {
    e.preventDefault();
    const prod = products.find(p => String(p.id) === String(formProduct));
    if (!prod) return;

    const year = new Date().getFullYear();
    const reference = `SRET-${year}-${Date.now().toString().slice(-6)}`;
    const amount = Number(prod.sellingPrice || 0) * formQty;
    setSubmitting(true);

    try {
      // Restock via inventory adjustment (real backend)
      await adjust.mutateAsync({
        productId: Number(prod.id),
        qty: formQty,
        note: `Sales return ${reference}: ${formReason}`,
      });

      let refundRecorded = false;
      if (issueRefund && amount > 0) {
        await createPayment.mutateAsync({
          reference: `REF-${Date.now().toString().slice(-8)}`,
          type: 'made',
          amount,
          date: new Date().toISOString().slice(0, 10),
          method: 'cash',
          party: formCustomer || 'Walk-in Customer',
          partyType: 'customer',
          note: `Refund for sales return ${reference}`,
        });
        refundRecorded = true;
      }

      const newRet: OrderReturn = {
        id: `ret-${Date.now()}`,
        reference,
        originalReference: formRef,
        partyName: formCustomer || 'Walk-in Customer',
        type: 'customer_return',
        date: new Date().toISOString().slice(0, 10),
        amount,
        status: 'completed',
        reason: formReason,
        stockAdjusted: true,
        refundRecorded,
        items: [{
          productId: prod.id,
          productName: prod.name,
          quantity: formQty,
          unitPrice: Number(prod.sellingPrice || 0),
          total: amount,
        }],
      };

      persist([newRet, ...returnsList]);
      toast(
        'success',
        'Return processed',
        `Stock restocked (+${formQty}).${refundRecorded ? ' Refund payment recorded.' : ''}`
      );
      setModalOpen(false);
      setFormRef('');
      setFormCustomer('');
      setFormQty(1);
    } catch {
      toast('error', 'Return failed', 'Could not adjust stock or record refund. Check inventory API.');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: Column<any>[] = [
    { key: 'reference', label: 'Return Ref', render: v => <span className="font-mono text-xs font-bold text-blue-600">{String(v)}</span> },
    { key: 'originalReference', label: 'Invoice Ref', render: v => <span className="font-mono text-xs text-gray-500">{String(v)}</span> },
    { key: 'partyName', label: 'Customer', render: v => <span className="font-medium text-gray-900 dark:text-gray-100">{String(v)}</span> },
    { key: 'date', label: 'Date', render: v => formatDate(String(v || '')) },
    { key: 'amount', label: 'Refund Amount', render: v => <span className="font-semibold text-red-600">{formatCurrency(Number(v))}</span> },
    { key: 'reason', label: 'Reason', render: v => <span className="text-xs text-gray-500">{String(v)}</span> },
    {
      key: 'status',
      label: 'Status',
      render: v => {
        const val = String(v);
        return <Badge variant={val === 'completed' ? 'success' : val === 'pending' ? 'warning' : 'danger'}>{val}</Badge>;
      }
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="Returns"
        subtitle="Process returns via stock adjustment (and optional refund payment). No dedicated returns API — list is stored locally."
        actions={
          <Button onClick={() => setModalOpen(true)}>
            <RotateCcw size={15} /> Process Return
          </Button>
        }
      />

      {returnsList.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-300 dark:border-gray-600 p-10 text-center text-sm text-gray-500">
          No sales returns yet. Processing a return will restock via the inventory movements API
          {issueRefund ? ' and can record a refund payment' : ''}.
        </div>
      ) : null}

      <DataTable
        columns={columns}
        data={returnsList as unknown as Record<string, unknown>[]}
        searchable
        searchPlaceholder="Search customer returns..."
        actions={row => (
          <button
            onClick={() => setSelectedReturn(row as unknown as OrderReturn)}
            className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-blue-600"
          >
            <Eye size={15} />
          </button>
        )}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Process Customer Return">
        <form onSubmit={handleCreateReturn} className="space-y-4">
          <Input
            label="Original Sales Order / Invoice Reference"
            placeholder="e.g. SO-2026-001"
            value={formRef}
            onChange={e => setFormRef(e.target.value)}
            required
          />
          <Input
            label="Customer Name"
            placeholder="e.g. Abebe Kebede"
            value={formCustomer}
            onChange={e => setFormCustomer(e.target.value)}
            required
          />
          <Select
            label="Returned Product"
            options={products.map((p: any) => ({ value: String(p.id), label: `${p.name} (${formatCurrency(p.sellingPrice || 0)})` }))}
            value={formProduct}
            onChange={e => setFormProduct(e.target.value)}
          />
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Quantity Returned"
              type="number"
              min={1}
              value={formQty}
              onChange={e => setFormQty(parseInt(e.target.value) || 1)}
              required
            />
            <Select
              label="Reason for Return"
              options={[
                { value: 'Damaged goods', label: 'Damaged / Broken goods' },
                { value: 'Wrong item shipped', label: 'Wrong item shipped' },
                { value: 'Customer cancellation', label: 'Customer cancellation' },
                { value: 'Expired / Near expiry', label: 'Expired / Near expiry' },
              ]}
              value={formReason}
              onChange={e => setFormReason(e.target.value)}
            />
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
            <input type="checkbox" checked={issueRefund} onChange={e => setIssueRefund(e.target.checked)} />
            Record refund payment via payments API
          </label>
          <div className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg text-xs text-blue-700 dark:text-blue-300">
            Approving will call the inventory adjustment API to add stock back
            {issueRefund ? ' and create a refund payment' : ''}. Return metadata is kept in browser storage (no returns endpoint).
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={submitting}>Approve Return</Button>
          </div>
        </form>
      </Modal>

      <Modal open={!!selectedReturn} onClose={() => setSelectedReturn(null)} title={`Return Detail — ${selectedReturn?.reference}`}>
        {selectedReturn && (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-4">
              <div><span className="text-gray-400 block text-xs">Customer</span><strong>{selectedReturn.partyName}</strong></div>
              <div><span className="text-gray-400 block text-xs">Original Invoice</span><strong>{selectedReturn.originalReference}</strong></div>
              <div><span className="text-gray-400 block text-xs">Date</span>{formatDate(selectedReturn.date || '')}</div>
              <div><span className="text-gray-400 block text-xs">Refund Total</span><strong className="text-red-600">{formatCurrency(selectedReturn.amount || 0)}</strong></div>
            </div>
            <div>
              <span className="text-gray-400 block text-xs mb-1">Reason</span>
              <p className="p-2 bg-gray-50 dark:bg-gray-800 rounded border border-gray-200 dark:border-gray-700 text-xs">{selectedReturn.reason}</p>
            </div>
            <table className="w-full text-xs border rounded-lg overflow-hidden">
              <thead className="bg-gray-100 dark:bg-gray-700">
                <tr>
                  <th className="p-2 text-left">Item</th>
                  <th className="p-2 text-center">Qty</th>
                  <th className="p-2 text-right">Price</th>
                  <th className="p-2 text-right">Total</th>
                </tr>
              </thead>
              <tbody>
                {selectedReturn.items.map((i, idx) => (
                  <tr key={idx} className="border-t">
                    <td className="p-2 font-medium">{i.productName}</td>
                    <td className="p-2 text-center">{i.quantity}</td>
                    <td className="p-2 text-right">{formatCurrency(i.unitPrice)}</td>
                    <td className="p-2 text-right font-bold">{formatCurrency(i.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Modal>
    </div>
  );
}
