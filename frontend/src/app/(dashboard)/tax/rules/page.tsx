'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, ExternalLink, Plus, ShieldCheck, X } from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import { useToast } from '@/components/ui/ToastProvider';
import { taxApi, type TaxRule } from '@/lib/api/taxApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const INITIAL: TaxRule = {
  code: '', name: '', type: 'VAT', rate: 0,
  effectiveFrom: new Date().toISOString().slice(0, 10),
  verificationStatus: 'ACCOUNTANT_REVIEW_REQUIRED', active: true,
};

const TYPE_LABELS: Record<string, string> = {
  VAT: 'VAT',
  EMPLOYMENT_INCOME_TAX: 'Employment income tax',
  WITHHOLDING: 'Withholding',
  EMPLOYEE_PENSION: 'Employee pension',
  EMPLOYER_PENSION: 'Employer pension',
};

const STATUS_COLOR: Record<string, string> = {
  VERIFIED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  ACCOUNTANT_REVIEW_REQUIRED: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
};

export default function TaxRulesPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('TAX_RULE_MANAGE');

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<TaxRule>(INITIAL);

  const rules = useQuery({
    queryKey: ['tax-rules'],
    queryFn: taxApi.rules,
    retry: 1,
  });

  const verify = useMutation({
    mutationFn: taxApi.verifyRule,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tax-rules'] });
      toast('success', 'Rule verified', 'Tax rule has been officially verified by accountant.');
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? String(err);
      toast('error', 'Verification failed', msg);
    },
  });

  const save = useMutation({
    mutationFn: taxApi.saveRule,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tax-rules'] });
      toast('success', 'Rule saved', 'Effective-dated tax rule has been recorded.');
      setForm(INITIAL);
      setShowForm(false);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? String(err);
      toast('error', 'Save failed', msg);
    },
  });

  const f = (k: keyof TaxRule, v: string | number | boolean) =>
    setForm(p => ({ ...p, [k]: v }));

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tax Rules & Classifications"
        subtitle="Rules are effective-dated. Verify source, taxpayer applicability, and exemptions before setting a rule to VERIFIED. Payroll finalisation requires all rules to be VERIFIED."
        actions={
          canManage ? (
            <Button onClick={() => setShowForm(s => !s)} variant={showForm ? 'outline' : 'primary'} size="sm">
              {showForm ? <><X size={14} /> Cancel</> : <><Plus size={14} /> Add Rule</>}
            </Button>
          ) : null
        }
      />

      {/* Add rule form */}
      {showForm && canManage && (
        <Card>
          <CardHeader>
            <h2 className="font-semibold">New Effective-Dated Rule</h2>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
              Always cite a statutory source and set verification status to Accountant Review Required
              unless the rule has been legally verified.
            </p>
          </CardHeader>
          <CardContent>
            <form
              className="grid gap-3 md:grid-cols-2 xl:grid-cols-3"
              onSubmit={e => {
                e.preventDefault();
                save.mutate({ ...form, rate: Number(form.rate) });
              }}
            >
              <Input required placeholder="Rule code (e.g. VAT_STANDARD)" value={form.code}
                onChange={e => f('code', e.target.value)} />
              <Input required placeholder="Rule name" value={form.name}
                onChange={e => f('name', e.target.value)} />
              <Select
                options={Object.entries(TYPE_LABELS).map(([v, l]) => ({ value: v, label: l }))}
                value={form.type}
                onChange={e => f('type', e.target.value)}
              />
              <Input required type="number" min="0" max="1" step="0.000001"
                placeholder="Rate e.g. 0.15 for 15%" value={form.rate}
                onChange={e => f('rate', Number(e.target.value))} />
              <Input required type="date" value={form.effectiveFrom}
                onChange={e => f('effectiveFrom', e.target.value)} />
              <Input type="date" value={form.effectiveTo ?? ''}
                placeholder="Effective to (optional)"
                onChange={e => f('effectiveTo', e.target.value || '')} />
              <Select
                options={[
                  { value: 'ACCOUNTANT_REVIEW_REQUIRED', label: 'Accountant review required' },
                  { value: 'VERIFIED', label: 'Verified' },
                ]}
                value={form.verificationStatus}
                onChange={e => f('verificationStatus', e.target.value)}
              />
              <div className="md:col-span-2 xl:col-span-2">
                <Input placeholder="Statutory source URL" value={form.sourceUrl ?? ''}
                  onChange={e => f('sourceUrl', e.target.value)} />
              </div>
              <div className="md:col-span-2 xl:col-span-3">
                <Input placeholder="Notes / bracket details (e.g. upperBound=4000)" value={form.notes ?? ''}
                  onChange={e => f('notes', e.target.value)} />
              </div>
              <div className="md:col-span-2 xl:col-span-3 flex gap-2">
                <Button type="submit" disabled={save.isPending}>
                  {save.isPending ? 'Saving…' : 'Save effective-dated rule'}
                </Button>
                <Button type="button" variant="outline" onClick={() => setShowForm(false)}>Cancel</Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Rules table */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <ShieldCheck size={18} className="text-blue-600" />
            <h2 className="font-semibold">Active Tax Rules</h2>
            {rules.data && (
              <span className="ml-auto text-xs text-gray-400">{rules.data.length} rule{rules.data.length !== 1 ? 's' : ''}</span>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {rules.isLoading ? (
            <p className="text-sm text-gray-400">Loading rules…</p>
          ) : rules.error ? (
            <div className="flex items-center gap-2 text-sm text-red-500">
              <AlertCircle size={16} />
              Unable to load rules. Start the backend to fetch live data.
            </div>
          ) : !rules.data?.length ? (
            <p className="text-sm text-gray-400">No active tax rules. The backend seeds Ethiopian defaults on first startup.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-xs text-gray-500">
                    <th className="p-3">Rule</th>
                    <th className="p-3">Type</th>
                    <th className="p-3">Rate</th>
                    <th className="p-3">Effective period</th>
                    <th className="p-3">Status</th>
                    <th className="p-3">Source</th>
                    {canManage && <th className="p-3 text-right">Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {rules.data.map(r => (
                    <tr key={r.id} className="border-b hover:bg-gray-50 dark:hover:bg-gray-800/50">
                      <td className="p-3">
                        <p className="font-medium">{r.name}</p>
                        <p className="text-xs text-gray-500">{r.code}</p>
                        {r.notes && (
                          <p className="text-xs text-gray-400 mt-0.5">{r.notes}</p>
                        )}
                      </td>
                      <td className="p-3">
                        <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs font-medium dark:bg-gray-700">
                          {TYPE_LABELS[r.type] ?? r.type}
                        </span>
                      </td>
                      <td className="p-3 font-semibold">{(r.rate * 100).toFixed(2)} %</td>
                      <td className="p-3 text-xs">
                        {r.effectiveFrom}
                        {r.effectiveTo ? ` → ${r.effectiveTo}` : ' → ongoing'}
                      </td>
                      <td className="p-3">
                        <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${STATUS_COLOR[r.verificationStatus] ?? 'bg-gray-100 text-gray-600'}`}>
                          {r.verificationStatus === 'VERIFIED' ? '✓ Verified' : '⚠ Review required'}
                        </span>
                      </td>
                      <td className="p-3">
                        {r.sourceUrl ? (
                          <a href={r.sourceUrl} target="_blank" rel="noopener noreferrer"
                            className="flex items-center gap-1 text-xs text-blue-600 hover:underline">
                            <ExternalLink size={11} /> Source
                          </a>
                        ) : '—'}
                      </td>
                      {canManage && (
                        <td className="p-3 text-right">
                          {r.verificationStatus !== 'VERIFIED' && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => r.id && verify.mutate(r.id)}
                              loading={verify.isPending}
                              className="text-xs text-emerald-700 border-emerald-300 hover:bg-emerald-50"
                            >
                              <ShieldCheck size={12} className="mr-1 inline" /> Verify
                            </Button>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-800/40 dark:bg-amber-950/30 dark:text-amber-300">
        <AlertCircle size={15} className="mr-1.5 inline" />
        <strong>Important:</strong> All rules are seeded with <em>Accountant Review Required</em> status.
        An authorised accountant must set each rule to <em>Verified</em> before payroll can be finalised.
        Do not verify a rule without checking the statutory source.
      </div>
    </div>
  );
}
