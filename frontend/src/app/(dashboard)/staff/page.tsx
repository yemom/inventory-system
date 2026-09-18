'use client';

import React, { useEffect, useMemo, useState } from 'react';
import {
  Eye,
  KeyRound,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  UserCheck,
  UserX,
  Users,
} from 'lucide-react';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import { Card, CardContent } from '@/components/ui/Card';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Input from '@/components/ui/Input';
import Modal from '@/components/ui/Modal';
import PageHeader from '@/components/ui/PageHeader';
import Select from '@/components/ui/Select';
import Textarea from '@/components/ui/Textarea';
import StatCard from '@/components/ui/StatCard';
import { useToast } from '@/components/ui/ToastProvider';
import { useAuth } from '@/lib/auth/AuthProvider';
import { usersApi } from '@/lib/api/usersApi';

type StaffStatus = 'ACTIVE' | 'INACTIVE';

interface StaffMember {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone?: string;
  gender?: string;
  dateOfBirth?: string;
  address?: string;
  profilePhotoUrl?: string;
  employeeId?: string;
  jobTitle?: string;
  department?: string;
  branch?: string;
  warehouse?: string;
  dateJoined?: string;
  role: string;
  permissions?: string[];
  status: StaffStatus;
  passwordResetRequired?: boolean;
  lastLoginAt?: string;
  createdAt?: string;
}

const roles = [
  { label: 'Manager', value: 'MANAGER' },
  { label: 'Supervisor', value: 'SUPERVISOR' },
  { label: 'Cashier', value: 'CASHIER' },
  { label: 'Inventory Staff', value: 'INVENTORY_STAFF' },
];

const sortOptions = [
  { label: 'Name', value: 'firstName,asc' },
  { label: 'Date Joined', value: 'dateJoined,desc' },
  { label: 'Last Login', value: 'lastLoginAt,desc' },
  { label: 'Status', value: 'status,asc' },
];

const emptyForm = {
  firstName: '',
  lastName: '',
  gender: '',
  dateOfBirth: '',
  phone: '',
  email: '',
  address: '',
  profilePhotoUrl: '',
  employeeId: '',
  jobTitle: '',
  department: '',
  roleName: 'CASHIER',
  dateJoined: new Date().toISOString().slice(0, 10),
  branch: '',
  warehouse: '',
  username: '',
  password: '',
  status: 'ACTIVE',
};

const formatDate = (value?: string) => value ? new Date(value).toLocaleDateString() : '-';
const formatDateTime = (value?: string) => value ? new Date(value).toLocaleString() : 'Never';
const labelize = (value?: string) => value ? value.replaceAll('_', ' ') : '-';

export default function StaffManagementPage() {
  const { hasPermission, user } = useAuth();
  const { toast } = useToast();
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [filters, setFilters] = useState({ search: '', role: '', department: '', branch: '', warehouse: '', status: '', sort: 'firstName,asc' });
  const [form, setForm] = useState(emptyForm);
  const [editing, setEditing] = useState<StaffMember | null>(null);
  const [viewing, setViewing] = useState<StaffMember | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [confirm, setConfirm] = useState<{ title: string; message: string; label: string; action: () => Promise<void> } | null>(null);
  const [resetting, setResetting] = useState<StaffMember | null>(null);
  const [temporaryPassword, setTemporaryPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const canManage = hasPermission('USER_CREATE') || hasPermission('STAFF_CREATE');
  const canUpdate = hasPermission('USER_UPDATE') || hasPermission('STAFF_UPDATE');
  const canRemove = hasPermission('USER_DEACTIVATE') || hasPermission('STAFF_DELETE');

  const departments = useMemo(() => [...new Set(staff.map(item => item.department).filter(Boolean))] as string[], [staff]);
  const branches = useMemo(() => [...new Set(staff.map(item => item.branch).filter(Boolean))] as string[], [staff]);
  const warehouses = useMemo(() => [...new Set(staff.map(item => item.warehouse).filter(Boolean))] as string[], [staff]);

  const loadStaff = async () => {
    if (!hasPermission('USER_READ') && !hasPermission('STAFF_VIEW')) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const [listResponse, statsResponse] = await Promise.all([
        usersApi.listStaff({ ...filters, page, size: 10 }),
        usersApi.staffStats(),
      ]);
      setStaff(listResponse.data?.content || []);
      setTotalPages(listResponse.data?.totalPages || 1);
      setStats(statsResponse.data || {});
    } catch (error: any) {
      toast('error', error.response?.data?.message || 'Failed to load staff members.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStaff();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, filters]);

  const updateFilter = (name: string, value: string) => {
    setPage(0);
    setFilters(prev => ({ ...prev, [name]: value }));
  };

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setModalOpen(true);
  };

  const openEdit = (member: StaffMember) => {
    setEditing(member);
    setForm({
      firstName: member.firstName || '',
      lastName: member.lastName || '',
      gender: member.gender || '',
      dateOfBirth: member.dateOfBirth || '',
      phone: member.phone || '',
      email: member.email || '',
      address: member.address || '',
      profilePhotoUrl: member.profilePhotoUrl || '',
      employeeId: member.employeeId || '',
      jobTitle: member.jobTitle || '',
      department: member.department || '',
      roleName: member.role || 'CASHIER',
      dateJoined: member.dateJoined || '',
      branch: member.branch || '',
      warehouse: member.warehouse || '',
      username: member.username || '',
      password: '',
      status: member.status || 'ACTIVE',
    });
    setModalOpen(true);
  };

  const validate = () => {
    const required = ['firstName', 'lastName', 'email', 'phone', 'employeeId', 'roleName', 'dateJoined'];
    if (!editing) required.push('password');
    if (required.some(key => !form[key as keyof typeof form])) return 'Please fill in all required fields.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return 'Please enter a valid email address.';
    if (!/^[+()0-9\s-]{7,20}$/.test(form.phone)) return 'Please enter a valid phone number.';
    if (!editing && form.password.length < 8) return 'Temporary password must be at least 8 characters.';
    return '';
  };

  const submitForm = async (event: React.FormEvent) => {
    event.preventDefault();
    const validationError = validate();
    if (validationError) {
      toast('error', validationError);
      return;
    }
    if (editing && editing.role !== form.roleName && !window.confirm("Are you sure you want to change this user's role? Their system permissions will be updated.")) {
      return;
    }
    setSubmitting(true);
    try {
      const payload = { ...form, username: form.username || form.email };
      if (editing) {
        const { password, ...updatePayload } = payload;
        await usersApi.update(editing.id, updatePayload);
        toast('success', editing.role !== form.roleName ? 'Role updated successfully.' : 'Staff information updated successfully.');
      } else {
        await usersApi.create(payload);
        toast('success', 'Staff member created successfully.');
      }
      setModalOpen(false);
      loadStaff();
    } catch (error: any) {
      toast('error', error.response?.data?.message || 'Unable to save staff member.');
    } finally {
      setSubmitting(false);
    }
  };

  const statusAction = (member: StaffMember) => {
    const nextActive = member.status !== 'ACTIVE';
    setConfirm({
      title: nextActive ? 'Activate Staff Account' : 'Deactivate Staff Account',
      message: nextActive
        ? `Reactivate ${member.fullName}'s account and allow login again.`
        : `Deactivate ${member.fullName}'s account. Historical sales, inventory transactions, and audit records remain intact.`,
      label: nextActive ? 'Activate' : 'Deactivate',
      action: async () => {
        nextActive ? await usersApi.activate(member.id) : await usersApi.deactivate(member.id);
        toast('success', nextActive ? 'Staff account activated successfully.' : 'Staff account deactivated successfully.');
        loadStaff();
      },
    });
  };

  const removeAction = (member: StaffMember) => {
    setConfirm({
      title: 'Remove Staff Member',
      message: `Are you sure you want to remove ${member.fullName}? This soft-removes the staff account but keeps historical transactions and audit records.`,
      label: 'Remove',
      action: async () => {
        await usersApi.remove(member.id);
        toast('success', 'Staff member removed successfully.');
        loadStaff();
      },
    });
  };

  const resetPassword = async () => {
    if (!resetting || temporaryPassword.length < 8) {
      toast('error', 'Temporary password must be at least 8 characters.');
      return;
    }
    setSubmitting(true);
    try {
      await usersApi.resetPassword(resetting.id, temporaryPassword);
      toast('success', 'Staff password reset successfully.');
      setResetting(null);
      setTemporaryPassword('');
    } catch (error: any) {
      toast('error', error.response?.data?.message || 'Unable to reset password.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!hasPermission('USER_READ') && !hasPermission('STAFF_VIEW')) {
    return <div className="p-8 text-red-500 font-medium">Access denied. Staff Management requires staff view permission.</div>;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Staff Management"
        subtitle="Manage staff members, roles, access permissions, and account status."
        actions={
          <>
            <Button variant="outline" size="sm" onClick={loadStaff}>
              <RefreshCw size={14} /> Refresh
            </Button>
            {canManage && (
              <Button onClick={openCreate}>
                <Plus size={16} /> Add Staff Member
              </Button>
            )}
          </>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-7 gap-4">
        <StatCard title="Total Staff" value={stats.totalStaff || 0} icon={<Users size={18} />} />
        <StatCard title="Active Staff" value={stats.activeStaff || 0} icon={<UserCheck size={18} />} color="emerald" />
        <StatCard title="Inactive Staff" value={stats.inactiveStaff || 0} icon={<UserX size={18} />} color="red" />
        <StatCard title="Managers" value={stats.managers || 0} icon={<ShieldCheck size={18} />} color="amber" />
        <StatCard title="Supervisors" value={stats.supervisors || 0} icon={<ShieldCheck size={18} />} color="blue" />
        <StatCard title="Cashiers" value={stats.cashiers || 0} icon={<Users size={18} />} color="purple" />
        <StatCard title="Inventory Staff" value={stats.inventoryStaff || 0} icon={<Users size={18} />} color="emerald" />
      </div>

      <Card>
        <CardContent className="p-5 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-6 gap-3">
            <Input placeholder="Search name, ID, email, phone" value={filters.search} onChange={e => updateFilter('search', e.target.value)} leftIcon={<Search size={16} />} />
            <Select placeholder="All roles" value={filters.role} onChange={e => updateFilter('role', e.target.value)} options={roles} />
            <Select placeholder="All departments" value={filters.department} onChange={e => updateFilter('department', e.target.value)} options={departments.map(value => ({ label: value, value }))} />
            <Select placeholder="All branches" value={filters.branch} onChange={e => updateFilter('branch', e.target.value)} options={branches.map(value => ({ label: value, value }))} />
            <Select placeholder="All warehouses" value={filters.warehouse} onChange={e => updateFilter('warehouse', e.target.value)} options={warehouses.map(value => ({ label: value, value }))} />
            <Select value={filters.sort} onChange={e => updateFilter('sort', e.target.value)} options={sortOptions} />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant={filters.status === '' ? 'primary' : 'outline'} size="sm" onClick={() => updateFilter('status', '')}>All</Button>
            <Button variant={filters.status === 'ACTIVE' ? 'primary' : 'outline'} size="sm" onClick={() => updateFilter('status', 'ACTIVE')}>Active</Button>
            <Button variant={filters.status === 'INACTIVE' ? 'primary' : 'outline'} size="sm" onClick={() => updateFilter('status', 'INACTIVE')}>Inactive</Button>
          </div>

          <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 dark:bg-gray-800/60 border-b border-gray-200 dark:border-gray-700">
                <tr>
                  {['Staff Name', 'Employee ID', 'Email', 'Phone', 'Role', 'Department', 'Status', 'Date Joined', 'Last Login', 'Actions'].map(header => (
                    <th key={header} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider whitespace-nowrap">{header}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
                {loading ? (
                  <tr><td className="px-4 py-8 text-center text-gray-500" colSpan={10}>Loading staff members...</td></tr>
                ) : staff.length === 0 ? (
                  <tr><td className="px-4 py-8 text-center text-gray-500" colSpan={10}>No staff members found.</td></tr>
                ) : staff.map(member => (
                  <tr key={member.id} className="bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/40">
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="font-medium text-gray-900 dark:text-white">{member.fullName}</div>
                      <div className="text-xs text-gray-500">{member.jobTitle || member.username}</div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap font-mono text-gray-700 dark:text-gray-300">{member.employeeId || '-'}</td>
                    <td className="px-4 py-3 whitespace-nowrap text-gray-700 dark:text-gray-300">{member.email}</td>
                    <td className="px-4 py-3 whitespace-nowrap text-gray-700 dark:text-gray-300">{member.phone || '-'}</td>
                    <td className="px-4 py-3 whitespace-nowrap"><Badge variant={member.role === 'SUPER_ADMIN' ? 'warning' : 'info'}>{labelize(member.role)}</Badge></td>
                    <td className="px-4 py-3 whitespace-nowrap text-gray-700 dark:text-gray-300">{member.department || '-'}</td>
                    <td className="px-4 py-3 whitespace-nowrap"><Badge variant={member.status === 'ACTIVE' ? 'success' : 'default'}>{member.status}</Badge></td>
                    <td className="px-4 py-3 whitespace-nowrap text-gray-700 dark:text-gray-300">{formatDate(member.dateJoined)}</td>
                    <td className="px-4 py-3 whitespace-nowrap text-gray-700 dark:text-gray-300">{formatDateTime(member.lastLoginAt)}</td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="flex items-center gap-1">
                        <Button variant="ghost" size="sm" onClick={() => setViewing(member)} title="View staff details"><Eye size={15} /></Button>
                        {canUpdate && <Button variant="ghost" size="sm" onClick={() => openEdit(member)} title="Edit staff"><Pencil size={15} /></Button>}
                        {canUpdate && member.role !== 'SUPER_ADMIN' && <Button variant="ghost" size="sm" onClick={() => statusAction(member)} title="Activate or deactivate">{member.status === 'ACTIVE' ? <UserX size={15} /> : <UserCheck size={15} />}</Button>}
                        {canUpdate && <Button variant="ghost" size="sm" onClick={() => setResetting(member)} title="Reset password"><KeyRound size={15} /></Button>}
                        {canRemove && member.role !== 'SUPER_ADMIN' && member.id !== user?.id && <Button variant="ghost" size="sm" onClick={() => removeAction(member)} title="Remove staff"><Trash2 size={15} className="text-red-500" /></Button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between text-sm text-gray-500">
            <span>Page {page + 1} of {totalPages}</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(prev => Math.max(0, prev - 1))}>Previous</Button>
              <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => setPage(prev => prev + 1)}>Next</Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? 'Edit Staff Member' : 'Add Staff Member'} size="xl" className="max-h-[90vh] overflow-y-auto">
        <form onSubmit={submitForm} className="space-y-6">
          <section className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Personal Information</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Input label="First Name *" value={form.firstName} onChange={e => setForm({ ...form, firstName: e.target.value })} />
              <Input label="Last Name *" value={form.lastName} onChange={e => setForm({ ...form, lastName: e.target.value })} />
              <Select label="Gender" placeholder="Select gender" value={form.gender} onChange={e => setForm({ ...form, gender: e.target.value })} options={[{ label: 'Female', value: 'Female' }, { label: 'Male', value: 'Male' }]} />
              <Input label="Date of Birth" type="date" value={form.dateOfBirth} onChange={e => setForm({ ...form, dateOfBirth: e.target.value })} />
              <Input label="Phone Number *" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} />
              <Input label="Email *" type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
              <Input label="Profile Photo URL" value={form.profilePhotoUrl} onChange={e => setForm({ ...form, profilePhotoUrl: e.target.value })} />
              <Textarea label="Address" value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} />
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Employment Information</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Input label="Employee ID *" value={form.employeeId} onChange={e => setForm({ ...form, employeeId: e.target.value })} />
              <Input label="Job Title" value={form.jobTitle} onChange={e => setForm({ ...form, jobTitle: e.target.value })} />
              <Input label="Department" value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} />
              <Select label="Role *" value={form.roleName} onChange={e => setForm({ ...form, roleName: e.target.value })} options={roles} />
              <Input label="Date Joined *" type="date" value={form.dateJoined} onChange={e => setForm({ ...form, dateJoined: e.target.value })} />
              <Input label="Branch" value={form.branch} onChange={e => setForm({ ...form, branch: e.target.value })} />
              <Input label="Warehouse" value={form.warehouse} onChange={e => setForm({ ...form, warehouse: e.target.value })} />
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Account Information</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Input label="Username or Email" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} />
              {!editing && <Input label="Temporary Password *" type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} />}
              <Select label="Account Status" value={form.status} onChange={e => setForm({ ...form, status: e.target.value })} options={[{ label: 'Active', value: 'ACTIVE' }, { label: 'Inactive', value: 'INACTIVE' }]} />
            </div>
          </section>

          <div className="flex justify-end gap-3 pt-4 border-t border-gray-100 dark:border-gray-700">
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" loading={submitting}>{editing ? 'Save Staff Member' : 'Create Staff Member'}</Button>
          </div>
        </form>
      </Modal>

      <Modal open={!!viewing} onClose={() => setViewing(null)} title="Staff Details" size="xl">
        {viewing && (
          <div className="space-y-5 text-sm">
            <div className="flex items-center gap-4">
              <div className="h-14 w-14 rounded-full bg-blue-600 text-white flex items-center justify-center text-lg font-bold">{viewing.fullName?.[0]}</div>
              <div>
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white">{viewing.fullName}</h3>
                <p className="text-gray-500">{viewing.employeeId || '-'} - {labelize(viewing.role)}</p>
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Detail title="Personal Information" items={[['Phone', viewing.phone], ['Email', viewing.email], ['Gender', viewing.gender], ['Address', viewing.address]]} />
              <Detail title="Employment" items={[['Job Title', viewing.jobTitle], ['Department', viewing.department], ['Branch', viewing.branch], ['Warehouse', viewing.warehouse], ['Date Joined', formatDate(viewing.dateJoined)]]} />
              <Detail title="Account" items={[['Status', viewing.status], ['Username', viewing.username], ['Last Login', formatDateTime(viewing.lastLoginAt)], ['Created', formatDateTime(viewing.createdAt)]]} />
            </div>
            <div>
              <h4 className="font-semibold text-gray-900 dark:text-gray-100 mb-2">Access Permissions</h4>
              <div className="flex flex-wrap gap-2">{(viewing.permissions || []).map(permission => <Badge key={permission} variant="outline">{permission}</Badge>)}</div>
            </div>
            <div>
              <h4 className="font-semibold text-gray-900 dark:text-gray-100 mb-2">Activity</h4>
              <p className="text-gray-500">Relevant sales, inventory operations, stock transfers, stock adjustments, and login activity remain preserved through the existing transaction and audit history.</p>
            </div>
          </div>
        )}
      </Modal>

      <Modal open={!!resetting} onClose={() => setResetting(null)} title="Reset Staff Password" size="sm">
        <div className="space-y-4">
          <p className="text-sm text-gray-500">Generate or enter a temporary password for {resetting?.fullName}. Existing passwords are never displayed.</p>
          <Input label="Temporary Password" type="password" value={temporaryPassword} onChange={e => setTemporaryPassword(e.target.value)} />
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => setTemporaryPassword(Math.random().toString(36).slice(2, 10) + 'A1!')}>Generate</Button>
            <Button loading={submitting} onClick={resetPassword}>Reset Password</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!confirm}
        onClose={() => setConfirm(null)}
        title={confirm?.title}
        message={confirm?.message}
        confirmLabel={confirm?.label}
        onConfirm={async () => {
          if (!confirm) return;
          setSubmitting(true);
          try {
            await confirm.action();
            setConfirm(null);
          } catch (error: any) {
            toast('error', error.response?.data?.message || 'Action failed.');
          } finally {
            setSubmitting(false);
          }
        }}
        loading={submitting}
      />
    </div>
  );
}

function Detail({ title, items }: { title: string; items: [string, string | undefined][] }) {
  return (
    <div className="rounded-xl border border-gray-200 dark:border-gray-700 p-4">
      <h4 className="font-semibold text-gray-900 dark:text-gray-100 mb-3">{title}</h4>
      <div className="space-y-2">
        {items.map(([label, value]) => (
          <div key={label}>
            <div className="text-xs uppercase tracking-wider text-gray-400">{label}</div>
            <div className="text-gray-700 dark:text-gray-300">{value || '-'}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
