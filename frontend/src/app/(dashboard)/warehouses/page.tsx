'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { Plus, Edit, Warehouse as WarehouseIcon, MapPin, Phone, User, Package } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { warehousesApi, Warehouse } from '@/lib/api/warehousesApi';
import { useToast } from '@/components/ui/ToastProvider';

export default function WarehousesPage() {
  const { toast } = useToast();
  const [warehouseList, setWarehouseList] = useState<Warehouse[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Warehouse | null>(null);

  const [name, setName] = useState('');
  const [location, setLocation] = useState('');
  const [managerName, setManagerName] = useState('');
  const [contactPhone, setContactPhone] = useState('');

  const fetchWarehouses = useCallback(async () => {
    try {
      setLoading(true);
      const data = await warehousesApi.list();
      setWarehouseList(data);
    } catch (err: any) {
      toast('error', 'Fetch Error', err.message || 'Error loading warehouses');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchWarehouses();
  }, [fetchWarehouses]);

  const openCreate = () => {
    setEditing(null);
    setName('');
    setLocation('');
    setManagerName('');
    setContactPhone('');
    setModalOpen(true);
  };

  const openEdit = (wh: Warehouse) => {
    setEditing(wh);
    setName(wh.name);
    setLocation(wh.location || '');
    setManagerName(wh.managerName || '');
    setContactPhone(wh.contactPhone || '');
    setModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    const payload = { name, location, managerName, contactPhone };
    try {
      if (editing) {
        await warehousesApi.update(editing.id, payload);
        toast('success', 'Warehouse updated', `${name} updated.`);
      } else {
        await warehousesApi.create(payload);
        toast('success', 'Warehouse added', `${name} registered into network.`);
      }
      setModalOpen(false);
      fetchWarehouses();
    } catch (err: any) {
      toast('error', 'Save Failed', err.message || 'Operation failed');
    }
  };

  const columns: Column<any>[] = [
    {
      key: 'name',
      label: 'Warehouse / Store',
      render: (_, row) => (
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
            <WarehouseIcon size={16} />
          </div>
          <span className="font-semibold text-gray-900 dark:text-gray-100">{String(row.name)}</span>
        </div>
      )
    },
    { 
      key: 'location', 
      label: 'Location', 
      render: v => v ? (
        <span className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-300">
          <MapPin size={12} className="text-gray-400" /> {String(v)}
        </span>
      ) : '-'
    },
    { 
      key: 'managerName', 
      label: 'Supervisor', 
      render: (_, row) => (row.managerName || row.contactPhone) ? (
        <div className="text-xs">
          {row.managerName && <p className="font-medium text-gray-800 dark:text-gray-200 flex items-center gap-1"><User size={12} className="text-gray-400" /> {String(row.managerName)}</p>}
          {row.contactPhone && <p className="text-gray-400 flex items-center gap-1 mt-0.5"><Phone size={11} /> {String(row.contactPhone)}</p>}
        </div>
      ) : '-'
    },
    { 
      key: 'active', 
      label: 'Status', 
      render: v => <Badge variant={v ? 'success' : 'default'}>{v ? 'Active' : 'Inactive'}</Badge> 
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader 
        title="Warehouses" 
        subtitle="Manage physical depots, branches, retail store fronts, and capacity distribution"
        actions={<Button onClick={openCreate}><Plus size={15} /> Add Warehouse</Button>}
      />

      <DataTable
        columns={columns}
        data={warehouseList as unknown as Record<string, unknown>[]}
        loading={loading}
        searchable
        searchPlaceholder="Search locations..."
        actions={row => (
          <div className="flex justify-end gap-1">
            <button onClick={() => openEdit(row as unknown as Warehouse)} className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-blue-600">
              <Edit size={14} />
            </button>
          </div>
        )}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Edit Warehouse' : 'Create Warehouse'}>
        <form onSubmit={handleSave} className="space-y-4">
          <Input label="Name" placeholder="e.g. Main Distribution Center" value={name} onChange={e => setName(e.target.value)} required />
          <Input label="Location" placeholder="Address or City" value={location} onChange={e => setLocation(e.target.value)} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Manager Name" placeholder="Contact person" value={managerName} onChange={e => setManagerName(e.target.value)} />
            <Input label="Phone Number" placeholder="+251..." value={contactPhone} onChange={e => setContactPhone(e.target.value)} />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit">Save Location</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
