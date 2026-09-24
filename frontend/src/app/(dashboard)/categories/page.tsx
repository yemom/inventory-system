'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { Plus, Edit, Trash2, Tag, FolderTree } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { categoriesApi, Category } from '@/lib/api/categoriesApi';
import { useToast } from '@/components/ui/ToastProvider';

export default function CategoriesPage() {
  const { toast } = useToast();
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Category | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [parentId, setParentId] = useState<string>('');

  const fetchCategories = useCallback(async () => {
    try {
      setLoading(true);
      const data = await categoriesApi.list();
      setCategories(data);
    } catch (err: any) {
      toast('error', 'Failed to fetch', err.message || 'Error loading categories');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  const openCreate = () => {
    setEditing(null);
    setName('');
    setDescription('');
    setParentId('');
    setModalOpen(true);
  };

  const openEdit = (cat: Category) => {
    setEditing(cat);
    setName(cat.name);
    setDescription(cat.description || '');
    setParentId(cat.parentId ? String(cat.parentId) : '');
    setModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        name,
        description,
        parentId: parentId ? Number(parentId) : null
      };

      if (editing) {
        await categoriesApi.update(editing.id, payload);
        toast('success', 'Category updated', `${name} modified successfully.`);
      } else {
        await categoriesApi.create(payload);
        toast('success', 'Category created', `${name} added to catalog.`);
      }
      setModalOpen(false);
      fetchCategories();
    } catch (err: any) {
      toast('error', 'Save Failed', err.message || 'An error occurred.');
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try {
      await categoriesApi.delete(deleteTarget.id);
      toast('success', 'Category deleted', `${deleteTarget.name} removed.`);
      fetchCategories();
    } catch (err: any) {
      toast('error', 'Delete Failed', err.message || 'An error occurred.');
    } finally {
      setDeleteTarget(null);
    }
  };

  const columns: Column<any>[] = [
    { 
      key: 'name', 
      label: 'Category Name', 
      render: (_, row) => {
        const pId = row.parentId as number | undefined;
        const parentName = categories.find(c => c.id === pId)?.name;
        return (
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 flex items-center justify-center">
              <Tag size={15} />
            </div>
            <div>
              <span className="font-semibold text-gray-900 dark:text-gray-100">{String(row.name)}</span>
              {parentName && (
                <span className="block text-[11px] text-gray-400">Subcategory of: {parentName}</span>
              )}
            </div>
          </div>
        );
      }
    },
    { key: 'description', label: 'Description', render: v => <span className="text-gray-500 text-xs">{v ? String(v) : '-'}</span> },
    { 
      key: 'id', 
      label: 'Subcategories', 
      render: (_, row) => {
        const subs = (row.subCategories as any[])?.length || 0;
        return <span className="font-mono text-xs bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded">{subs} items</span>;
      }
    }
  ];

  return (
    <div className="space-y-4">
      <PageHeader 
        title="Product Categories" 
        subtitle="Organize your store inventory hierarchy"
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
        loading={loading}
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
              ...categories.filter(c => c.id !== editing?.id).map((c: any) => ({ value: String(c.id), label: c.name }))
            ]}
            value={parentId}
            onChange={e => setParentId(e.target.value)}
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
