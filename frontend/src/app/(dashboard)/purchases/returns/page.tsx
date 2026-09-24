'use client';
import React, { useState } from 'react';
import { Plus, RotateCcw, CheckCircle, Eye } from 'lucide-react';
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


export default function PurchaseReturnsPage() {
  const { toast } = useToast();
  const { data: suppliers = [] } = useSuppliers();
  const { data: products = [] } = useProducts();
  const [returnsList, setReturnsList] = useState<any[]>(
    ([] as any[]).filter(r => r.type === 'supplier_return')
  );
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedReturn, setSelectedReturn] = useState<any | null>(null);

  const [formRef, setFormRef] = useState('');
  const [formSupplier, setFormSupplier] = useState(suppliers[0]?.name || '');
  const [formProduct, setFormProduct] = useState(products[0]?.id || '');
  const [formQty, setFormQty] = useState(5);
  const [formReason, setFormReason] = useState('Substandard quality');

  const handleCreateReturn = (e: React.FormEvent) => {
    e.preventDefault();
    const prod = products.find(p => p.id === formProduct);
    if (!prod) return;

    const newRet: any = {
      id: `pret-${Date.now()}`,
      reference: `PRET-${new Date().getFullYear()}-${Math.floor(1000 + Math.random() * 9000)}`,
      originalReference: formRef || 'PO-2024-001',
      partyName: formSupplier,
      type: 'supplier_return',
      date: new Date().toISOString().slice(0, 10),
      amount: prod.costPrice * formQty,
      status: 'completed',
      reason: formReason,
      items: [{
        productId: prod.id,
        productName: prod.name,
        quantity: formQty,
        unitPrice: prod.costPrice,
        total: prod.costPrice * formQty
      }]
    };

    setReturnsList([newRet, ...returnsList]);
    toast('success', 'Supplier return processed', `Debit note generated for ${formatCurrency(newRet.amount)}`);
    setModalOpen(false);
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
        title="Purchase Returns & Debit Notes" 
        subtitle="Return defective goods to suppliers, deduct payable balances, and log stock deductions"
        actions={
          <Button onClick={() => setModalOpen(true)}>
            <RotateCcw size={15} /> Issue Return / Debit Note
          </Button>
        }
      />

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

      {/* New Modal */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Issue Supplier Return (Debit Note)">
        <form onSubmit={handleCreateReturn} className="space-y-4">
          <Input 
            label="Original Purchase Order Reference" 
            placeholder="e.g. PO-2024-001" 
            value={formRef} 
            onChange={e => setFormRef(e.target.value)} 
            required 
          />
          <Select
            label="Supplier"
            options={suppliers.map((s: any) => ({ value: s.name, label: s.name }))}
            value={formSupplier}
            onChange={e => setFormSupplier(e.target.value)}
          />
          <Select
            label="Product to Return"
            options={products.map((p: any) => ({ value: p.id, label: `${p.name} (Cost: ${formatCurrency(p.costPrice || 0)})` }))}
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
            ⚠️ This will deduct {formQty} units from inventory and reduce your Accounts Payable balance to this supplier.
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit">Submit Return</Button>
          </div>
        </form>
      </Modal>

      {/* Detail Modal */}
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
