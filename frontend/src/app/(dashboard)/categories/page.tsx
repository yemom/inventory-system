'use client';
import React, { useState } from 'react';
import { Plus, Edit, Trash2, Tag, FolderTree } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { categories as initialCategories, Category } from '@/lib/api/mockData';
import { useToast } from '@/components/ui/ToastProvider';

export default function CategoriesPage() {
  const { toast } = useToast();
  const [categories, setCategories] = useState<Category[]>(initialCategories);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Category | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [parentCategory, setParentCategory] = useState('');
  const [status, setStatus] = useState<'active' | 'inactive'>('active');

  const openCreate = () => {
    setEditing(null);
    setName('');
    setDescription('');
    setParentCategory('');
    setStatus('active');
    setModalOpen(true);
  };

  const openEdit = (cat: Category) => {
    setEditing(cat);
    setName(cat.name);
    setDescription(cat.description);
    setParentCategory(cat.parentCategory || '');
    setStatus(cat.status);
    setModalOpen(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (editing) {
      setCategories(prev => prev.map(c => c.id === editing.id ? {
        ...c, name, description, parentCategory: parentCategory || undefined, status
      } : c));
      toast('success', 'Category updated', `${name} modified successfully.`);
    } else {
      const newCat: Category = {
        id: `cat-${Date.now()}`,
        name,
        description,
        parentCategory: parentCategory || undefined,
        productCount: 0,
        status
      };
      setCategories([newCat, ...categories]);
      toast('success', 'Category created', `${name} added to catalog.`);
    }
    setModalOpen(false);
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    setCategories(prev => prev.filter(c => c.id !== deleteTarget.id));
    toast('success', 'Category deleted', `${deleteTarget.name} removed.`);
    setDeleteTarget(null);
  };

  const columns: Column<Record<string, unknown>>[] = [
    { 
      key: 'name', 
      label: 'Category Name', 
      render: (_, row) => (
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 flex items-center justify-center">
            <Tag size={15} />
          </div>
          <div>
            <span className="font-semibold text-gray-900 dark:text-gray-100">{String(row.name)}</span>
            {Boolean(row.parentCategory) && (
              <span className="block text-[11px] text-gray-400">Subcategory of: {String(row.parentCategory)}</span>
            )}
          </div>
        </div>
      )
    },
    { key: 'description', label: 'Description', render: v => <span className="text-gray-500 text-xs">{String(v)}</span> },
    { 
      key: 'productCount', 
      label: 'Assigned Products', 
      render: v => <span className="font-mono text-xs font-bold bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded">{String(v)} items</span> 
    },
    { 
      key: 'status', 
      label: 'Status', 
      render: v => <Badge variant={v === 'active' ? 'success' : 'default'}>{String(v)}</Badge> 
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader 
        title="Product Categories" 
        subtitle="Organize your store inventory hierarchy and category-based reports"
        actions={
          <Button onClick={openCreate}>
            <Plus size={15} /> Add Category
          </Button>
        }
      />

      <DataTable
        columns={columns}
        data={categories as unknown as Record<string, unknown>[]}
        searchable
        searchPlaceholder="Search product categories..."
        actions={row => (
          <div className="flex justify-end gap-1">
            <button 
              onClick={() => openEdit(row as unknown as Category)} 
              className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-blue-600"
            >
              <Edit size={14} />
            </button>
            <button 
              onClick={() => setDeleteTarget(row as unknown as Category)} 
              className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-red-600"
            >
              <Trash2 size={14} />
            </button>
          </div>
        )}
      />

      {/* Modal */}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Edit Category' : 'Create Category'}>
        <form onSubmit={handleSave} className="space-y-4">
          <Input 
            label="Category Name" 
            placeholder="e.g. Dairy & Eggs" 
            value={name} 
            onChange={e => setName(e.target.value)} 
            required 
          />
          <Input 
            label="Description" 
            placeholder="Brief description of product line" 
            value={description} 
            onChange={e => setDescription(e.target.value)} 
          />
          <Select
            label="Parent Category (Optional for nesting)"
            options={[
              { value: '', label: 'None (Top-level Category)' },
              ...categories.filter(c => c.id !== editing?.id).map(c => ({ value: c.name, label: c.name }))
            ]}
            value={parentCategory}
            onChange={e => setParentCategory(e.target.value)}
          />
          <Select
            label="Status"
            options={[
              { value: 'active', label: 'Active' },
              { value: 'inactive', label: 'Inactive' },
            ]}
            value={status}
            onChange={e => setStatus(e.target.value as any)}
          />
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit">Save Category</Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog 
        open={!!deleteTarget} 
        onClose={() => setDeleteTarget(null)} 
        onConfirm={confirmDelete} 
        title="Delete Category" 
        message={`Are you sure you want to delete ${deleteTarget?.name}? Products assigned will need recategorization.`} 
      />
    </div>
  );
}
