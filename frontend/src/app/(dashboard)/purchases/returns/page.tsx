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
import { useSuppliers } from '@/hooks/useSuppliers';
import { useProducts } from '@/hooks/useProducts';
import { useStockAdjust } from '@/hooks/useInventory';

interface PurchaseReturn {
  id: string;
  reference: string;
  originalReference: string;
  partyName: string;
  type: 'supplier_return';
  date: string;
  amount: number;
  status: string;
  reason: string;
  items: { productId: number | string; productName: string; quantity: number; unitPrice: number; total: number }[];
  stockAdjusted?: boolean;
}

const STORAGE_KEY = 'stockflow_purchase_returns';

function loadReturns(): PurchaseReturn[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function supplierLabel(s: any) {
  return s.companyName || s.name || '';
}

export default function PurchaseReturnsPage() {
  const { toast } = useToast();
  const { data: suppliers = [] } = useSuppliers();
  const { data: products = [] } = useProducts();
  const adjust = useStockAdjust();
  const [returnsList, setReturnsList] = useState<PurchaseReturn[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedReturn, setSelectedReturn] = useState<PurchaseReturn | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [formRef, setFormRef] = useState('');
  const [formSupplier, setFormSupplier] = useState('');
  const [formProduct, setFormProduct] = useState('');
  const [formQty, setFormQty] = useState(1);
  const [formReason, setFormReason] = useState('Substandard quality');

  useEffect(() => {
    setReturnsList(loadReturns());
  }, []);

  useEffect(() => {
    if (!formSupplier && suppliers[0]) setFormSupplier(supplierLabel(suppliers[0]));
  }, [suppliers, formSupplier]);

  useEffect(() => {
    if (!formProduct && products[0]) setFormProduct(String(products[0].id));
  }, [products, formProduct]);

  const persist = (list: PurchaseReturn[]) => {
    setReturnsList(list);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  };

  const handleCreateReturn = async (e: React.FormEvent) => {
    e.preventDefault();
    const prod = products.find(p => String(p.id) === String(formProduct));
    if (!prod) return;

    const unitCost = Number(prod.purchasePrice ?? 0);
    const year = new Date().getFullYear();
    const reference = `PRET-${year}-${Date.now().toString().slice(-6)}`;
    const amount = unitCost * formQty;
    setSubmitting(true);

    try {
      // Deduct stock via inventory adjustment (real backend)
      await adjust.mutateAsync({
        productId: Number(prod.id),
        qty: -formQty,
        note: `Purchase return ${reference}: ${formReason}`,
      });

      const newRet: PurchaseReturn = {
        id: `pret-${Date.now()}`,
        reference,
        originalReference: formRef,
        partyName: formSupplier,
        type: 'supplier_return',
        date: new Date().toISOString().slice(0, 10),
        amount,
        status: 'completed',
        reason: formReason,
        stockAdjusted: true,
        items: [{
          productId: prod.id,
          productName: prod.name,
          quantity: formQty,
          unitPrice: unitCost,
          total: amount,
        }],
      };

      persist([newRet, ...returnsList]);
      toast('success', 'Supplier return processed', `Stock deducted (−${formQty}). Debit note ${formatCurrency(amount)}.`);
      setModalOpen(false);
      setFormRef('');
      setFormQty(1);
    } catch {
      toast('error', 'Return failed', 'Could not adjust stock. Check inventory API and available quantity.');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: Column<any>[] = [
    { key: 'reference', label: 'Debit Note Ref', render: v => <span className="font-mono text-xs font-bold text-emerald-600">{String(v)}</span> },
    { key: 'originalReference', label: 'PO Ref', render: v => <span className="font-mono text-xs text-gray-500">{String(v)}</span> },
    { key: 'partyName', label: 'Supplier', render: v => <span className="font-medium text-gray-900 dark:text-gray-100">{String(v)}</span> },
    { key: 'date', label: 'Date', render: v => formatDate(String(v || '')) },
    { key: 'amount', label: 'Debit Total', render: v => <span className="font-semibold text-emerald-600">{formatCurrency(Number(v))}</span> },
    { key: 'reason', label: 'Reason', render: v => <span className="text-xs text-gray-500">{String(v)}</span> },
    {
      key: 'status',
      label: 'Status',
      render: v => {
        const val = String(v);
        return <Badge variant={val === 'completed' ? 'success' : 'warning'}>{val}</Badge>;
      }
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="Returns"
        subtitle="Return goods to suppliers via stock deduction. No dedicated returns API — list is stored locally; stock changes use inventory movements."
        actions={
          <Button onClick={() => setModalOpen(true)}>
            <RotateCcw size={15} /> Issue Return / Debit Note
          </Button>
        }
      />

      {returnsList.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-300 dark:border-gray-600 p-10 text-center text-sm text-gray-500">
          No purchase returns yet. Submitting a return will deduct stock via the inventory movements API.
        </div>
      ) : null}

      <DataTable
        columns={columns}
        data={returnsList as unknown as Record<string, unknown>[]}
        searchable
        searchPlaceholder="Search purchase returns..."
        actions={row => (
          <button
            onClick={() => setSelectedReturn(row as any)}
            className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-blue-600"
          >
            <Eye size={15} />
          </button>
        )}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Issue Supplier Return (Debit Note)">
        <form onSubmit={handleCreateReturn} className="space-y-4">
          <Input
            label="Original Purchase Order Reference"
            placeholder="e.g. PO-2026-001"
            value={formRef}
            onChange={e => setFormRef(e.target.value)}
            required
          />
          <Select
            label="Supplier"
            options={suppliers.map((s: any) => ({ value: supplierLabel(s), label: supplierLabel(s) }))}
            value={formSupplier}
            onChange={e => setFormSupplier(e.target.value)}
          />
          <Select
            label="Product to Return"
            options={products.map((p: any) => ({
              value: String(p.id),
              label: `${p.name} (Cost: ${formatCurrency(p.purchasePrice || 0)})`,
            }))}
            value={formProduct}
            onChange={e => setFormProduct(e.target.value)}
          />
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Quantity"
              type="number"
              min={1}
              value={formQty}
              onChange={e => setFormQty(parseInt(e.target.value) || 1)}
              required
            />
            <Select
              label="Reason for Return"
              options={[
                { value: 'Substandard quality', label: 'Substandard / Defective quality' },
                { value: 'Damaged in transit', label: 'Damaged in transit' },
                { value: 'Incorrect shipment', label: 'Incorrect shipment' },
                { value: 'Expired batch received', label: 'Expired batch received' },
              ]}
              value={formReason}
              onChange={e => setFormReason(e.target.value)}
            />
          </div>
          <div className="p-3 bg-amber-50 dark:bg-amber-900/20 rounded-lg text-xs text-amber-700 dark:text-amber-300">
            This will deduct {formQty} unit(s) from inventory via the stock adjustment API. Debit-note metadata is kept in browser storage.
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={submitting}>Submit Return</Button>
          </div>
        </form>
      </Modal>

      <Modal open={!!selectedReturn} onClose={() => setSelectedReturn(null)} title={`Debit Note — ${selectedReturn?.reference}`}>
        {selectedReturn && (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-4">
              <div><span className="text-gray-400 block text-xs">Supplier</span><strong>{selectedReturn.partyName}</strong></div>
              <div><span className="text-gray-400 block text-xs">Original PO</span><strong>{selectedReturn.originalReference}</strong></div>
              <div><span className="text-gray-400 block text-xs">Date</span>{formatDate(selectedReturn.date || '')}</div>
              <div><span className="text-gray-400 block text-xs">Debit Amount</span><strong className="text-emerald-600">{formatCurrency(selectedReturn.amount || 0)}</strong></div>
            </div>
            <div>
              <span className="text-gray-400 block text-xs mb-1">Reason</span>
              <p className="p-2 bg-gray-50 dark:bg-gray-800 rounded border border-gray-200 dark:border-gray-700 text-xs">{selectedReturn.reason}</p>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
