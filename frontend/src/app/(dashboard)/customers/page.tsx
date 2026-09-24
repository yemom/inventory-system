'use client';

import React, { useState, useEffect } from 'react';
import { Plus } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import { Card, CardContent } from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import Badge from '@/components/ui/Badge';
import DataTable, { Column } from '@/components/ui/DataTable';
import Modal from '@/components/ui/Modal';
import { useAuth } from '@/lib/auth/AuthProvider';
import { customersApi } from '@/lib/api/customersApi';
import { useToast } from '@/components/ui/ToastProvider';

export default function CustomersPage() {
  const { hasPermission } = useAuth();
  const { toast } = useToast();
  const [customers, setCustomers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState({
    name: '', phone: '', email: '', address: '', customerType: 'RETAIL', creditLimit: 0
  });

  const loadCustomers = async () => {
    try {
      setLoading(true);
      const data = await customersApi.list();
      setCustomers(data);
    } catch (e: any) {
      toast('error', 'Failed to load customers');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (hasPermission('CUSTOMER_READ')) loadCustomers();
    else setLoading(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await customersApi.create(formData);
      toast('success', 'Customer created successfully');
      setIsModalOpen(false);
      loadCustomers();
    } catch (e: any) {
      toast('error', 'Creation failed');
    }
  };

  const columns: Column<any>[] = [
    { label: 'ID', key: 'customerNumber' },
    { label: 'Name', key: 'name' },
    { label: 'Phone', key: 'phone' },
    { label: 'Type', key: 'customerType' },
    { label: 'Status', key: 'status', render: (val) => (
      <Badge variant={(val as string) === 'ACTIVE' ? 'success' : 'default'}>{val as string}</Badge>
    )}
  ];

  if (!hasPermission('CUSTOMER_READ')) return <div className="p-8">Access Denied</div>;

  return (
    <div className="space-y-6">
      <PageHeader title="Customers" subtitle="Manage clients" actions={
        hasPermission('CUSTOMER_CREATE') && (
          <Button onClick={() => setIsModalOpen(true)}>
            <Plus size={16} className="mr-2" /> New Customer
          </Button>
        )
      } />
      
      <Card>
        <CardContent className="p-0">
          <DataTable columns={columns} data={customers} loading={loading} />
        </CardContent>
      </Card>

      <Modal open={isModalOpen} onClose={() => setIsModalOpen(false)} title="Create New Customer">
        <form onSubmit={handleCreate} className="space-y-4">
          <Input label="Name" required value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />
          <Input label="Phone" value={formData.phone} onChange={e => setFormData({...formData, phone: e.target.value})} />
          <Input label="Email" type="email" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} />
          <Input label="Address" value={formData.address} onChange={e => setFormData({...formData, address: e.target.value})} />
          <Select label="Type" value={formData.customerType} onChange={e => setFormData({...formData, customerType: e.target.value})}
            options={[
              {label: 'Retail', value: 'RETAIL'},
              {label: 'Wholesale', value: 'WHOLESALE'},
              {label: 'Business', value: 'BUSINESS'},
              {label: 'Walk-in', value: 'WALK_IN'}
            ]}
          />
          <div className="flex justify-end gap-3 mt-6">
            <Button variant="outline" type="button" onClick={() => setIsModalOpen(false)}>Cancel</Button>
            <Button type="submit">Create Customer</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}





