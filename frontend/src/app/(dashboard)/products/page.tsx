'use client';
import React, { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, Package } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import ErrorState from '@/components/ui/ErrorState';
import { formatCurrency } from '@/lib/utils';
import { useProducts, useCreateProduct, useUpdateProduct, useDeleteProduct } from '@/hooks/useProducts';
import { useToast } from '@/components/ui/ToastProvider';
import { categoriesApi, Category } from '@/lib/api/categoriesApi';
import type { Product } from '@/lib/api/productsApi';

export default function ProductsPage() {
  const { data: products = [], isLoading, error, refetch } = useProducts();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const deleteProduct = useDeleteProduct();
  const { toast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Product | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);

  useEffect(() => {
    categoriesApi.list().then(setCategories).catch(console.error);
  }, []);

  // Form states
  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [barcode, setBarcode] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [purchasePrice, setPurchasePrice] = useState('0');
  const [sellingPrice, setSellingPrice] = useState('0');
  const [reorderLevel, setReorderLevel] = useState('10');
  const [initialQuantity, setInitialQuantity] = useState('0');
  const [active, setActive] = useState('true');

  const openCreate = () => { 
    setEditing(null); 
    setName(''); setSku(''); setBarcode(''); setCategoryId(''); 
    setPurchasePrice('0'); setSellingPrice('0'); setReorderLevel('10');
    setInitialQuantity('0');
    setActive('true');
    setModalOpen(true); 
  };
  
  const openEdit = (p: Product) => { 
    setEditing(p); 
    setName(p.name); setSku(p.sku); setBarcode(p.barcode || ''); 
    setCategoryId(p.categoryId ? String(p.categoryId) : ''); 
    // `!= null` rather than `|| 0`: an absent price used to render as "NaN".
    setPurchasePrice(p.purchasePrice != null ? String(p.purchasePrice) : '0');
    setSellingPrice(p.sellingPrice != null ? String(p.sellingPrice) : '0');
    setReorderLevel(p.reorderLevel != null ? String(p.reorderLevel) : '0');
    // Read-only reference while editing. Quantity on hand moves through stock
    // movements, never by editing the product row.
    setInitialQuantity(String(p.quantity ?? 0));
    setActive(p.active ? 'true' : 'false');
    setModalOpen(true); 
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!categoryId) {
      toast('error', 'Validation Error', 'Please select a category');
      return;
    }
    const openingQuantity = Number(initialQuantity);
    if (!Number.isFinite(openingQuantity) || openingQuantity < 0) {
      toast('error', 'Validation Error', 'Opening quantity cannot be negative');
      return;
    }

    const payload = {
      name, sku, barcode, categoryId: Number(categoryId),
      purchasePrice: Number(purchasePrice), sellingPrice: Number(sellingPrice),
      reorderLevel: Number(reorderLevel), active: active === 'true',
      // Create only. The backend turns this into quantity on hand and records an
      // OPENING movement; after that quantity moves only via stock movements.
      ...(editing ? {} : { initialQuantity: openingQuantity }),
    };

    try {
      if (editing) {
        await updateProduct.mutateAsync({ id: String(editing.id), data: payload });
        toast('success', 'Product updated successfully');
      } else {
        await createProduct.mutateAsync(payload);
        toast('success',
          openingQuantity > 0
            ? `Product created with ${openingQuantity} unit${openingQuantity === 1 ? '' : 's'} in stock`
            : 'Product created successfully');
      }
      setModalOpen(false);
    } catch (err: any) {
      toast('error', 'Operation failed', err.message || 'Please try again');
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteProduct.mutateAsync(String(deleteTarget.id));
      toast('success', 'Product deleted');
    } catch {
      toast('error', 'Delete failed');
    } finally {
      setDeleteTarget(null);
    }
  };

  const columns: Column<any>[] = [
    { key: 'name', label: 'Product Name', render: (_, row) => (
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 bg-blue-100 dark:bg-blue-900/30 rounded-lg flex items-center justify-center">
          <Package size={14} className="text-blue-600" />
        </div>
        <div>
          <p className="font-medium text-gray-900 dark:text-gray-100">{String(row.name)}</p>
          <p className="text-xs text-gray-400 font-mono">{String(row.sku)}</p>
        </div>
      </div>
    )},
    { key: 'categoryName', label: 'Category' },
    { key: 'purchasePrice', label: 'Cost Price', render: v => formatCurrency(Number(v)) },
    { key: 'sellingPrice', label: 'Selling Price', render: v => formatCurrency(Number(v)) },
    {
      key: 'quantity',
      label: 'Qty On Hand',
      render: (_: unknown, row: Product) => {
        const qty = Number(row.quantity ?? 0);
        const reorder = row.reorderLevel != null ? Number(row.reorderLevel) : null;
        if (qty <= 0) return <Badge variant="danger">Out of Stock</Badge>;
        if (reorder !== null && qty <= reorder) return <Badge variant="warning">Low Stock</Badge>;
        return <Badge variant="success">{qty} on hand</Badge>;
      },
    },
    { key: 'active', label: 'Status', render: v => <Badge variant={v ? 'success' : 'default'}>{v ? 'Active' : 'Inactive'}</Badge> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader
        title="Products"
        subtitle={`${products.length} products in catalogue`}
        actions={<Button onClick={openCreate} className="gap-2"><Plus size={16} /> Add Product</Button>}
      />

      <DataTable
        columns={columns}
        data={products}
        loading={isLoading}
        searchable
        searchPlaceholder="Search products..."
        actions={(row) => (
          <div className="flex items-center justify-end gap-1">
            {/* Icon-only buttons need an accessible name; they had none, so a
                screen reader announced them as bare buttons. */}
            <button
              type="button"
              title="Edit product"
              aria-label={`Edit ${(row as unknown as Product).name ?? 'product'}`}
              onClick={() => openEdit(row as unknown as Product)}
              className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 hover:text-blue-600 transition-colors"
            >
              <Edit size={15} />
            </button>
            <button
              type="button"
              title="Delete product"
              aria-label={`Delete ${(row as unknown as Product).name ?? 'product'}`}
              onClick={() => setDeleteTarget(row as unknown as Product)}
              className="p-1.5 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 hover:text-red-600 transition-colors"
            >
              <Trash2 size={15} />
            </button>
          </div>
        )}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Edit Product' : 'Add New Product'} size="lg">
        <form onSubmit={handleSave} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Input label="Product Name" value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Basmati Rice 25kg" required />
            <Input label="SKU" value={sku} onChange={e => setSku(e.target.value)} placeholder="e.g. RICE-BAS-25" required />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Select label="Category" options={[{ value: '', label: 'Select Category' }, ...categories.map((c: any) => ({ value: String(c.id), label: c.name }))]} value={categoryId} onChange={e => setCategoryId(e.target.value)} required />
            <Input label="Barcode (Optional)" value={barcode} onChange={e => setBarcode(e.target.value)} placeholder="Scan barcode" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Purchase Price (ETB)" type="number" value={purchasePrice} onChange={e => setPurchasePrice(e.target.value)} required />
            <Input label="Selling Price (ETB)" type="number" value={sellingPrice} onChange={e => setSellingPrice(e.target.value)} required />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input
  label={editing ? 'Quantity on Hand' : 'Opening Quantity (Initial Stock)'}
  type="number"
  min="0"
  step="1"
  value={initialQuantity}
  onChange={e => setInitialQuantity(e.target.value)}
  disabled={!!editing}
  hint={editing
    ? 'Stock moves through stock movements (receive, adjust, count) — not by editing the product.'
    : 'Stock on hand from day one. Saved as quantity on hand and recorded as an opening movement.'}
 />
 <Input label="Reorder Level" type="number" value={reorderLevel} onChange={e => setReorderLevel(e.target.value)} />
            <Select label="Status" options={[{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }]} value={active} onChange={e => setActive(e.target.value)} required />
          </div>
          
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit">Save Product</Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog 
        open={!!deleteTarget} 
        onClose={() => setDeleteTarget(null)} 
        onConfirm={confirmDelete} 
        title="Delete Product" 
        message="Are you sure you want to delete this product?"
      />
    </div>
  );
}
