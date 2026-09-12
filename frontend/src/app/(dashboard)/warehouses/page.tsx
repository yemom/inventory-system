'use client';
import React, { useState } from 'react';
import { Plus, Edit, Warehouse as WarehouseIcon, MapPin, Phone, User, Package } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Modal from '@/components/ui/Modal';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { warehouses as initialWarehouses, Warehouse } from '@/lib/api/mockData';
import { useToast } from '@/components/ui/ToastProvider';

export default function WarehousesPage() {
  const { toast } = useToast();
  const [warehouseList, setWarehouseList] = useState<Warehouse[]>(initialWarehouses);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Warehouse | null>(null);

  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [location, setLocation] = useState('');
  const [manager, setManager] = useState('');
  const [phone, setPhone] = useState('');
  const [status, setStatus] = useState<'active' | 'inactive'>('active');

  const openCreate = () => {
    setEditing(null);
    setCode(`WH-BR${warehouseList.length}`);
    setName('');
    setLocation('');
    setManager('');
    setPhone('');
    setStatus('active');
    setModalOpen(true);
  };

  const openEdit = (wh: Warehouse) => {
    setEditing(wh);
    setCode(wh.code);
    setName(wh.name);
    setLocation(wh.location);
    setManager(wh.manager);
    setPhone(wh.phone);
    setStatus(wh.status);
    setModalOpen(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (editing) {
      setWarehouseList(prev => prev.map(w => w.id === editing.id ? {
        ...w, code, name, location, manager, phone, status
      } : w));
      toast('success', 'Warehouse updated', `${name} updated.`);
    } else {
      const newWh: Warehouse = {
        id: `wh-${Date.now()}`,
        code,
        name,
        location,
        manager,
        phone,
        capacityUtilization: 10,
        totalProducts: 0,
        status
      };
      setWarehouseList([...warehouseList, newWh]);
      toast('success', 'Warehouse added', `${name} registered into network.`);
    }
    setModalOpen(false);
  };

  const columns: Column<Record<string, unknown>>[] = [
    {
      key: 'name',
      label: 'Warehouse / Store',
      render: (_, row) => (
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
            <WarehouseIcon size={16} />
          </div>
          <div>
            <span className="font-semibold text-gray-900 dark:text-gray-100">{String(row.name)}</span>
            <span className="block font-mono text-[10px] text-gray-400">{String(row.code)}</span>
          </div>
        </div>
      )
    },
    { 
      key: 'location', 
      label: 'Location', 
      render: v => (
        <span className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-300">
          <MapPin size={12} className="text-gray-400" /> {String(v)}
        </span>
      )
    },
    { 
      key: 'manager', 
      label: 'Supervisor', 
      render: (_, row) => (
        <div className="text-xs">
          <p className="font-medium text-gray-800 dark:text-gray-200 flex items-center gap-1">
            <User size={12} className="text-gray-400" /> {String(row.manager)}
          </p>
          <p className="text-gray-400 flex items-center gap-1 mt-0.5">
            <Phone size={11} /> {String(row.phone)}
          </p>
        </div>
      )
    },
    { 
      key: 'capacityUtilization', 
      label: 'Capacity Usage', 
      render: v => {
        const val = Number(v);
        return (
          <div className="w-32 space-y-1">
            <div className="flex justify-between text-[11px] font-medium">
              <span>{val}%</span>
            </div>
            <div className="w-full bg-gray-200 dark:bg-gray-700 h-1.5 rounded-full overflow-hidden">
              <div 
                className={`h-full rounded-full ${val > 80 ? 'bg-red-500' : val > 60 ? 'bg-amber-500' : 'bg-emerald-500'}`} 
                style={{ width: `${val}%` }} 
              />
            </div>
          </div>
        );
      }
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
        title="Warehouses & Store Locations" 
        subtitle="Manage physical depots, branches, retail store fronts, and capacity distribution"
        actions={
          <Button onClick={openCreate}>
            <Plus size={15} /> Add Warehouse
          </Button>
        }
      />

      <DataTable
        columns={columns}
        data={warehouseList as unknown as Record<string, unknown>[]}
        searchable
        searchPlaceholder="Search store locations..."
        actions={row => (
          <button 
            onClick={() => openEdit(row as unknown as Warehouse)} 
            className="p-1.5 rounded hover:bg-gray-100 text-gray-500 hover:text-blue-600"
          >
            <Edit size={14} />
          </button>
        )}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Edit Warehouse' : 'Add Warehouse Location'}>
        <form onSubmit={handleSave} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Input 
              label="Location Code" 
              placeholder="e.g. WH-BR3" 
              value={code} 
              onChange={e => setCode(e.target.value)} 
              required 
            />
            <Select
              label="Status"
              options={[{ value: 'active', label: 'Active' }, { value: 'inactive', label: 'Inactive' }]}
              value={status}
              onChange={e => setStatus(e.target.value as any)}
            />
          </div>
          <Input 
            label="Warehouse / Branch Name" 
            placeholder="e.g. Hawassa Regional Depot" 
            value={name} 
            onChange={e => setName(e.target.value)} 
            required 
          />
          <Input 
            label="Physical Address / City / Subcity" 
            placeholder="e.g. Hawassa, Industrial Park Road" 
            value={location} 
            onChange={e => setLocation(e.target.value)} 
            required 
          />
          <div className="grid grid-cols-2 gap-3">
            <Input 
              label="Branch Manager" 
              placeholder="Manager full name" 
              value={manager} 
              onChange={e => setManager(e.target.value)} 
              required 
            />
            <Input 
              label="Phone Number" 
              placeholder="0911..." 
              value={phone} 
              onChange={e => setPhone(e.target.value)} 
              required 
            />
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
