'use client';

import React, { useState, useEffect } from 'react';
import { Plus } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { Card, CardContent } from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Badge from '@/components/ui/Badge';
import DataTable, { Column } from '@/components/ui/DataTable';
import Modal from '@/components/ui/Modal';
import { useAuth } from '@/lib/auth/AuthProvider';
import { suppliersApi } from '@/lib/api/suppliersApi';
import { useToast } from '@/components/ui/ToastProvider';

export default function SuppliersPage() {
  const { hasPermission } = useAuth();
  const { toast } = useToast();
  const [suppliers, setSuppliers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState({
    companyName: '', contactPerson: '', phone: '', email: '', address: '', taxNumber: ''
  });

  const loadSuppliers = async () => {
    try {
      setLoading(true);
      const res = await suppliersApi.list();
      setSuppliers(res.data?.content || []);
    } catch (e: any) {
      toast('error', 'Failed to load suppliers');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (hasPermission('SUPPLIER_READ')) loadSuppliers();
    else setLoading(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await suppliersApi.create(formData);
      toast('success', 'Supplier created successfully');
      setIsModalOpen(false);
      loadSuppliers();
    } catch (e: any) {
      toast('error', 'Creation failed');
    }
  };

  const columns: Column<any>[] = [
    { label: 'ID', key: 'supplierNumber' },
    { label: 'Company Name', key: 'companyName' },
    { label: 'Contact', key: 'contactPerson' },
    { label: 'Phone', key: 'phone' },
    { label: 'Status', key: 'status', render: (val) => (
      <Badge variant={(val as string) === 'ACTIVE' ? 'success' : 'default'}>{val as string}</Badge>
    )}
  ];

  if (!hasPermission('SUPPLIER_READ')) return <div className="p-8">Access Denied</div>;

  return (
    <div className="space-y-6">
      <PageHeader title="Suppliers" subtitle="Manage vendors" actions={
        hasPermission('SUPPLIER_CREATE') && (
          <Button onClick={() => setIsModalOpen(true)}>
            <Plus size={16} className="mr-2" /> New Supplier
          </Button>
        )
      } />
      
      <Card>
        <CardContent className="p-0">
          <DataTable columns={columns} data={suppliers} loading={loading} />
        </CardContent>
      </Card>

      <Modal open={isModalOpen} onClose={() => setIsModalOpen(false)} title="Create New Supplier">
        <form onSubmit={handleCreate} className="space-y-4">
          <Input label="Company Name" required value={formData.companyName} onChange={e => setFormData({...formData, companyName: e.target.value})} />
          <Input label="Contact Person" value={formData.contactPerson} onChange={e => setFormData({...formData, contactPerson: e.target.value})} />
          <Input label="Phone" value={formData.phone} onChange={e => setFormData({...formData, phone: e.target.value})} />
          <Input label="Email" type="email" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} />
          <Input label="Address" value={formData.address} onChange={e => setFormData({...formData, address: e.target.value})} />
          <Input label="Tax Number" value={formData.taxNumber} onChange={e => setFormData({...formData, taxNumber: e.target.value})} />
          <div className="flex justify-end gap-3 mt-6">
            <Button variant="outline" type="button" onClick={() => setIsModalOpen(false)}>Cancel</Button>
            <Button type="submit">Create Supplier</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}





