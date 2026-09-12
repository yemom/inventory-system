'use client';
import React, { useState } from 'react';
import { Plus, RotateCcw, CheckCircle, Clock, XCircle, Eye } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { formatCurrency, formatDate } from '@/lib/utils';
import { orderReturns, products, OrderReturn } from '@/lib/api/mockData';
import { useToast } from '@/components/ui/ToastProvider';

export default function SalesReturnsPage() {
  const { toast } = useToast();
  const [returnsList, setReturnsList] = useState<OrderReturn[]>(
    orderReturns.filter(r => r.type === 'customer_return')
  );
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedReturn, setSelectedReturn] = useState<OrderReturn | null>(null);

  const [formRef, setFormRef] = useState('');
  const [formCustomer, setFormCustomer] = useState('');
  const [formProduct, setFormProduct] = useState(products[0]?.id || '');
  const [formQty, setFormQty] = useState(1);
  const [formReason, setFormReason] = useState('Damaged goods');

  const handleCreateReturn = (e: React.FormEvent) => {
    e.preventDefault();
    const prod = products.find(p => p.id === formProduct);
    if (!prod) return;

    const newRet: OrderReturn = {
      id: `ret-${Date.now()}`,
      reference: `SRET-${new Date().getFullYear()}-${Math.floor(1000 + Math.random() * 9000)}`,
      originalReference: formRef || 'SO-2024-001',
      partyName: formCustomer || 'Walk-in Customer',
      type: 'customer_return',
      date: new Date().toISOString().slice(0, 10),
      amount: prod.sellingPrice * formQty,
      status: 'completed',
      reason: formReason,
      items: [{
        productId: prod.id,
        productName: prod.name,
        quantity: formQty,
        unitPrice: prod.sellingPrice,
        total: prod.sellingPrice * formQty
      }]
    };

    setReturnsList([newRet, ...returnsList]);
    toast('success', 'Return processed', `Stock restocked with ${formQty} unit(s).`);
    setModalOpen(false);
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'reference', label: 'Return Ref', render: v => <span className="font-mono text-xs font-bold text-blue-600">{String(v)}</span> },
    { key: 'originalReference', label: 'Invoice Ref', render: v => <span className="font-mono text-xs text-gray-500">{String(v)}</span> },
    { key: 'partyName', label: 'Customer', render: v => <span className="font-medium text-gray-900 dark:text-gray-100">{String(v)}</span> },
    { key: 'date', label: 'Date', render: v => formatDate(String(v)) },
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
        title="Customer Sales Returns" 
        subtitle="Process customer merchandise returns, refunds, and restock tracking"
        actions={
          <Button onClick={() => setModalOpen(true)}>
            <RotateCcw size={15} /> Process Return
          </Button>
        }
      />

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

      {/* New Return Modal */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Process Customer Return">
        <form onSubmit={handleCreateReturn} className="space-y-4">
          <Input 
            label="Original Sales Order / Invoice Reference" 
            placeholder="e.g. SO-2024-001" 
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
            options={products.map(p => ({ value: p.id, label: `${p.name} (${formatCurrency(p.sellingPrice)})` }))}
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
          <div className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg text-xs text-blue-700 dark:text-blue-300">
            ℹ️ Approving this return will automatically reverse sales revenue and add units back into warehouse inventory.
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit">Approve Return</Button>
          </div>
        </form>
      </Modal>

      {/* View Return Modal */}
      <Modal open={!!selectedReturn} onClose={() => setSelectedReturn(null)} title={`Return Detail — ${selectedReturn?.reference}`}>
        {selectedReturn && (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-4">
              <div><span className="text-gray-400 block text-xs">Customer</span><strong>{selectedReturn.partyName}</strong></div>
              <div><span className="text-gray-400 block text-xs">Original Invoice</span><strong>{selectedReturn.originalReference}</strong></div>
              <div><span className="text-gray-400 block text-xs">Date</span>{formatDate(selectedReturn.date)}</div>
              <div><span className="text-gray-400 block text-xs">Refund Total</span><strong className="text-red-600">{formatCurrency(selectedReturn.amount)}</strong></div>
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
