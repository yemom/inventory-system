'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, Plus, X } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Modal from '@/components/ui/Modal';
import { useToast } from '@/components/ui/ToastProvider';
import { payrollApi } from '@/lib/api/payrollApi';
import { usersApi } from '@/lib/api/usersApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const etb = (v: number | null | undefined) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', maximumFractionDigits: 2 }).format(v ?? 0);

interface ProfileForm {
  userId: string;
  basicSalary: string;
  effectiveFrom: string;
  pensionEligible: boolean;
  bankAccountMasked: string;
}

const INITIAL_FORM: ProfileForm = {
  userId: '',
  basicSalary: '',
  effectiveFrom: new Date().toISOString().slice(0, 10),
  pensionEligible: true,
  bankAccountMasked: '',
};

export default function PayrollProfilesPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const { hasPermission } = useAuth();
  const canProcess = hasPermission('PAYROLL_PROCESS');

  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState<ProfileForm>(INITIAL_FORM);

  const profiles = useQuery({
    queryKey: ['payroll-profiles'],
    queryFn: payrollApi.profiles,
    retry: 1,
  });

  // Load staff list so accountant can pick from a dropdown
  const staff = useQuery({
    queryKey: ['users-list-payroll'],
    queryFn: () => usersApi.list(0, 200, ''),
    retry: 1,
    enabled: showModal,
  });

  const save = useMutation({
    mutationFn: () =>
      payrollApi.saveProfile({
        userId: Number(form.userId),
        basicSalary: Number(form.basicSalary),
        effectiveFrom: form.effectiveFrom,
        pensionEligible: form.pensionEligible,
        bankAccountMasked: form.bankAccountMasked || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payroll-profiles'] });
      qc.invalidateQueries({ queryKey: ['payroll-dashboard'] });
      toast('success', 'Profile saved', 'Employee payroll profile has been recorded.');
      setForm(INITIAL_FORM);
      setShowModal(false);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? String(err);
      toast('error', 'Save failed', msg);
    },
  });

  const f = (k: keyof ProfileForm, v: string | boolean) =>
    setForm(p => ({ ...p, [k]: v }));

  const staffList: { id: number; fullName: string; employeeId?: string }[] = (() => {
    const d = staff.data as { data?: { content?: unknown[] } } | undefined;
    const content = d?.data?.content;
    if (Array.isArray(content)) return content as { id: number; fullName: string; employeeId?: string }[];
    if (Array.isArray(staff.data)) return staff.data as { id: number; fullName: string; employeeId?: string }[];
    return [];
  })();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Employee Payroll Profiles"
        subtitle="Salary and tax-profile information is restricted to authorised payroll staff. A profile must be active for the employee to be included in a payroll period calculation."
        actions={
          canProcess ? (
            <Button onClick={() => setShowModal(true)}>
              <Plus size={14} /> Add Profile
            </Button>
          ) : null
        }
      />

      <Card>
        <CardHeader>
          <h2 className="font-semibold">Active Payroll Profiles</h2>
        </CardHeader>
        <CardContent>
          {profiles.isLoading ? (
            <p className="text-sm text-gray-400">Loading profiles…</p>
          ) : profiles.error ? (
            <div className="flex items-center gap-2 text-sm text-red-500">
              <AlertCircle size={16} />
              Unable to load profiles. Backend may be unavailable.
            </div>
          ) : !profiles.data?.length ? (
            <div className="py-10 text-center">
              <p className="text-sm text-gray-500">No payroll profiles configured yet.</p>
              {canProcess && (
                <Button className="mt-3" size="sm" onClick={() => setShowModal(true)}>
                  <Plus size={14} /> Add first profile
                </Button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-xs text-gray-500">
                    <th className="p-3">Employee</th>
                    <th className="p-3">Department</th>
                    <th className="p-3">Job title</th>
                    <th className="p-3">Basic salary</th>
                    <th className="p-3">Pension eligible</th>
                    <th className="p-3">Effective from</th>
                    <th className="p-3">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {profiles.data.map(p => (
                    <tr key={p.id} className="border-b hover:bg-gray-50 dark:hover:bg-gray-800/50">
                      <td className="p-3">
                        <p className="font-medium">{p.fullName}</p>
                        <p className="text-xs text-gray-400">{p.employeeId ?? 'No employee ID'}</p>
                      </td>
                      <td className="p-3">{p.department ?? '—'}</td>
                      <td className="p-3">{p.jobTitle ?? '—'}</td>
                      <td className="p-3 font-semibold">{etb(p.basicSalary)}</td>
                      <td className="p-3">
                        <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                          p.pensionEligible
                            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300'
                            : 'bg-gray-100 text-gray-500 dark:bg-gray-700'
                        }`}>
                          {p.pensionEligible ? 'Eligible' : 'Not eligible'}
                        </span>
                      </td>
                      <td className="p-3">{p.effectiveFrom}</td>
                      <td className="p-3">
                        <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                          p.active
                            ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'
                            : 'bg-gray-100 text-gray-400 dark:bg-gray-700'
                        }`}>
                          {p.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Add profile modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="Add Payroll Profile" size="md">
        <div className="space-y-4 p-1">
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Select a staff member and enter their gross basic salary. Ethiopian employment income tax and
            pension contributions will be calculated automatically using the active effective-dated rules.
          </p>

          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              Staff member <span className="text-red-500">*</span>
            </label>
            <select
              required
              value={form.userId}
              onChange={e => f('userId', e.target.value)}
              className="w-full rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
            >
              <option value="">— Select employee —</option>
              {staffList.map(u => (
                <option key={u.id} value={u.id}>
                  {u.fullName}{u.employeeId ? ` (${u.employeeId})` : ''}
                </option>
              ))}
            </select>
            {staff.isLoading && showModal && (
              <p className="text-xs text-gray-400 mt-1">Loading staff list…</p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                Basic salary (ETB/month) <span className="text-red-500">*</span>
              </label>
              <Input
                type="number"
                min="0"
                step="0.01"
                placeholder="e.g. 15000.00"
                value={form.basicSalary}
                onChange={e => f('basicSalary', e.target.value)}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                Effective from <span className="text-red-500">*</span>
              </label>
              <Input
                type="date"
                value={form.effectiveFrom}
                onChange={e => f('effectiveFrom', e.target.value)}
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              Bank account (masked, optional)
            </label>
            <Input
              placeholder="e.g. **** **** 4521"
              value={form.bankAccountMasked}
              onChange={e => f('bankAccountMasked', e.target.value)}
            />
          </div>

          <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
            <input
              type="checkbox"
              checked={form.pensionEligible}
              onChange={e => f('pensionEligible', e.target.checked)}
              className="h-4 w-4 rounded text-blue-600"
            />
            Eligible for pension contribution (7% employee + 11% employer)
          </label>

          {form.basicSalary && Number(form.basicSalary) > 0 && (
            <div className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-xs dark:border-blue-800/40 dark:bg-blue-950/30">
              <p className="font-semibold text-blue-700 dark:text-blue-300 mb-1">Estimated deductions (indicative)</p>
              <div className="grid grid-cols-2 gap-1 text-gray-700 dark:text-gray-300">
                <span>Gross salary:</span>
                <span className="font-medium">{etb(Number(form.basicSalary))}</span>
                <span>Employee pension (7%):</span>
                <span>{form.pensionEligible ? etb(Number(form.basicSalary) * 0.07) : 'Not applicable'}</span>
                <span>Employer pension (11%):</span>
                <span>{form.pensionEligible ? etb(Number(form.basicSalary) * 0.11) : 'Not applicable'}</span>
              </div>
              <p className="mt-2 text-gray-500">Exact income tax is calculated progressively on payroll creation using verified rules.</p>
            </div>
          )}

          <div className="flex gap-2 pt-2">
            <Button
              onClick={() => save.mutate()}
              disabled={save.isPending || !form.userId || !form.basicSalary || !form.effectiveFrom}
            >
              {save.isPending ? 'Saving…' : 'Save profile'}
            </Button>
            <Button variant="outline" onClick={() => { setShowModal(false); setForm(INITIAL_FORM); }}>
              <X size={14} /> Cancel
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
