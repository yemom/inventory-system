'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertCircle, Banknote, CheckCircle2, ChevronDown, ChevronUp,
  Landmark, Plus, Users, WalletCards,
} from 'lucide-react';
import Link from 'next/link';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import { useToast } from '@/components/ui/ToastProvider';
import { payrollApi, type PayrollPeriod, type PayrollItem } from '@/lib/api/payrollApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const etb = (v: number | null | undefined) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', maximumFractionDigits: 2 }).format(v ?? 0);

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  FINALIZED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  PAID: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
};

// ── Period detail rows ────────────────────────────────────────────────────────
function PeriodItems({ items }: { items: PayrollItem[] }) {
  if (!items.length) return <p className="p-4 text-sm text-gray-400">No payroll items in this period.</p>;
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs">
        <thead>
          <tr className="border-b text-left text-gray-500">
            <th className="p-2">Employee</th>
            <th className="p-2">Department</th>
            <th className="p-2">Gross</th>
            <th className="p-2">Income tax</th>
            <th className="p-2">Emp. pension</th>
            <th className="p-2">Empr. pension</th>
            <th className="p-2">Net pay</th>
            <th className="p-2">Breakdown</th>
          </tr>
        </thead>
        <tbody>
          {items.map(i => (
            <tr key={i.id} className="border-b hover:bg-gray-50 dark:hover:bg-gray-800/50">
              <td className="p-2">
                <p className="font-medium">{i.fullName}</p>
                <p className="text-gray-400">{i.employeeId}</p>
              </td>
              <td className="p-2">{i.department ?? '—'}</td>
              <td className="p-2 font-semibold">{etb(i.basicSalary)}</td>
              <td className="p-2 text-amber-700 dark:text-amber-300">{etb(i.incomeTax)}</td>
              <td className="p-2">{etb(i.employeePension)}</td>
              <td className="p-2">{etb(i.employerPension)}</td>
              <td className="p-2 font-bold text-emerald-700 dark:text-emerald-300">{etb(i.netPay)}</td>
              <td className="p-2 max-w-xs truncate text-gray-400" title={i.calculationBreakdown}>
                {i.calculationBreakdown}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ── Single period row with expand ────────────────────────────────────────────
function PeriodRow({ period }: { period: PayrollPeriod }) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const { hasPermission } = useAuth();
  const [expanded, setExpanded] = useState(false);
  const canFinalize = hasPermission('PAYROLL_FINALIZE');

  // Lazy-load items when row is expanded
  const detail = useQuery({
    queryKey: ['payroll-period', period.id],
    queryFn: () => payrollApi.period(period.id),
    enabled: expanded,
    staleTime: 60_000,
  });

  const finalize = useMutation({
    mutationFn: () => payrollApi.finalize(period.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payroll-periods'] });
      qc.invalidateQueries({ queryKey: ['payroll-dashboard'] });
      toast('success', 'Payroll finalized', `Period ${period.year}-${String(period.month).padStart(2, '0')} has been finalized.`);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? String(err);
      toast('error', 'Finalization failed', msg);
    },
  });

  const pay = useMutation({
    mutationFn: () => payrollApi.payPeriod(period.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payroll-periods'] });
      qc.invalidateQueries({ queryKey: ['payroll-dashboard'] });
      toast('success', 'Payroll disbursed', `Period ${period.year}-${String(period.month).padStart(2, '0')} marked as PAID.`);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? String(err);
      toast('error', 'Disbursement failed', msg);
    },
  });

  return (
    <>
      <tr className="border-b hover:bg-gray-50 dark:hover:bg-gray-800/50">
        <td className="p-3 font-medium">{period.year}-{String(period.month).padStart(2, '0')}</td>
        <td className="p-3">
          <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${STATUS_COLOR[period.status] ?? 'bg-gray-100 text-gray-600'}`}>
            {period.status}
          </span>
        </td>
        <td className="p-3">{etb(period.grossPayroll)}</td>
        <td className="p-3 text-amber-700 dark:text-amber-300">{etb(period.employmentTax)}</td>
        <td className="p-3">{etb(period.employeePension)}</td>
        <td className="p-3 font-semibold text-emerald-700 dark:text-emerald-300">{etb(period.netPayroll)}</td>
        <td className="p-3">{etb(period.employerCost)}</td>
        <td className="p-3">
          <div className="flex items-center gap-1">
            {period.status === 'DRAFT' && canFinalize && (
              <Button
                size="sm"
                variant="outline"
                onClick={() => finalize.mutate()}
                disabled={finalize.isPending}
                className="text-xs"
              >
                <CheckCircle2 size={12} />
                {finalize.isPending ? 'Finalizing…' : 'Finalize'}
              </Button>
            )}
            {period.status === 'FINALIZED' && canFinalize && (
              <Button
                size="sm"
                variant="outline"
                onClick={() => pay.mutate()}
                disabled={pay.isPending}
                className="text-xs text-blue-700 border-blue-300 hover:bg-blue-50"
              >
                <Banknote size={12} />
                {pay.isPending ? 'Disbursing…' : 'Disburse & Pay'}
              </Button>
            )}
            <button
              type="button"
              onClick={() => setExpanded(e => !e)}
              className="rounded p-1 text-gray-400 hover:text-blue-600"
            >
              {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            </button>
          </div>
        </td>
      </tr>
      {expanded && (
        <tr className="bg-gray-50 dark:bg-gray-800/30">
          <td colSpan={8} className="px-4 py-2">
            {detail.isLoading ? (
              <p className="text-sm text-gray-400 p-2">Loading items…</p>
            ) : (
              <PeriodItems items={detail.data?.items ?? []} />
            )}
            {period.ruleVersion && (
              period.ruleVersion.includes('awaiting-accountant-verification') ? (
                <p className="px-2 pb-2 text-sm text-amber-700 dark:text-amber-300">
                  Payroll amounts have not been calculated. Verify the effective tax and pension rules, then create this period again.
                </p>
              ) : (
                <p className="px-2 pb-2 text-xs text-gray-400">Rule snapshot: {period.ruleVersion}</p>
              )
            )}
          </td>
        </tr>
      )}
    </>
  );
}

// ── Main page ────────────────────────────────────────────────────────────────
export default function PayrollPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const { hasPermission } = useAuth();
  const canProcess = hasPermission('PAYROLL_PROCESS');
  const canViewPayroll = hasPermission('PAYROLL_SUMMARY_VIEW');

  const dashboard = useQuery({
    queryKey: ['payroll-dashboard'],
    queryFn: payrollApi.dashboard,
    retry: 1,
  });
  const periods = useQuery({
    queryKey: ['payroll-periods'],
    queryFn: payrollApi.periods,
    enabled: canViewPayroll,
    retry: 1,
  });

  const create = useMutation({
    mutationFn: () => {
      const now = new Date();
      return payrollApi.create(now.getFullYear(), now.getMonth() + 1);
    },
    onSuccess: (created) => {
      qc.invalidateQueries({ queryKey: ['payroll-periods'] });
      qc.invalidateQueries({ queryKey: ['payroll-dashboard'] });
      const awaitingVerification = created.ruleVersion?.includes('awaiting-accountant-verification');
      toast(
        awaitingVerification ? 'error' : 'success',
        awaitingVerification ? 'Payroll draft created' : 'Payroll created',
        awaitingVerification
          ? 'No payroll amounts were calculated because the effective tax or pension rules still need accountant verification.'
          : 'Draft payroll for the current month has been calculated.',
      );
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? String(err);
      toast('error', 'Creation failed', msg);
    },
  });

  const d = dashboard.data;
  const now = new Date();
  const currentPeriodExists = periods.data?.some(
    period => period.year === now.getFullYear() && period.month === now.getMonth() + 1,
  ) ?? false;

  if (!canViewPayroll) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-center text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
        Payroll data is restricted to users with payroll summary access.
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payroll Management"
        subtitle="Draft payroll uses effective-dated Ethiopian tax and pension rules. Finalisation requires all active rules to be verified by an authorised accountant."
        actions={
          <div className="flex items-center gap-3">
            <Link href="/payroll/profiles"
              className="text-sm font-medium text-blue-600 hover:text-blue-700">
              Employee profiles →
            </Link>
            {canProcess && (
              <Button onClick={() => create.mutate()} disabled={create.isPending || currentPeriodExists}>
                <Plus size={14} />
                {create.isPending ? 'Creating…' : currentPeriodExists ? 'Current-month payroll exists' : 'Create current-month payroll'}
              </Button>
            )}
          </div>
        }
      />

      {/* Empty State Banner when no payroll data is recorded yet */}
      {d && !d.hasData && (
        <div className="rounded-xl border border-blue-200 bg-blue-50/70 p-4 text-xs text-blue-900 dark:border-blue-800/40 dark:bg-blue-950/20 dark:text-blue-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Users size={16} className="text-blue-600 flex-shrink-0" />
            <span>
              <strong>No payroll records have been generated yet.</strong> Authentic metrics are derived strictly from persisted records. Configure employee payroll profiles to start.
            </span>
          </div>
          <Link
            href="/payroll/profiles"
            className="px-3 py-1 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 whitespace-nowrap ml-4"
          >
            Setup Employee Profiles
          </Link>
        </div>
      )}

      {/* Dashboard stat cards */}
      {dashboard.error ? (
        <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-600 dark:border-red-800/40 dark:bg-red-950/30">
          <AlertCircle size={16} />
          Unable to load payroll dashboard. Start the backend and ensure payroll profiles exist.
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            title="Gross payroll"
            value={d ? etb(d.grossPayroll) : '…'}
            icon={<Users size={20} />}
            color="blue"
            changeLabel={d ? `${d.activeProfiles} active profile${d.activeProfiles !== 1 ? 's' : ''}` : ''}
          />
          <StatCard
            title="Net payroll"
            value={d ? etb(d.netPayroll) : '…'}
            icon={<WalletCards size={20} />}
            color="emerald"
            changeLabel="After tax and pension deductions"
          />
          <StatCard
            title="Employment income tax"
            value={d ? etb(d.employmentTax) : '…'}
            icon={<Banknote size={20} />}
            color="amber"
            changeLabel="Progressive Ethiopian tax brackets"
          />
          <StatCard
            title="Total employer cost"
            value={d ? etb(d.employerCost) : '…'}
            icon={<Landmark size={20} />}
            color="purple"
            changeLabel={d ? `${d.draftPeriods} draft period${d.draftPeriods !== 1 ? 's' : ''} pending` : ''}
          />
        </div>
      )}

      {/* Periods table */}
      <Card>
        <CardHeader>
          <h2 className="font-semibold">Payroll Periods</h2>
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
            Expand a period to view per-employee breakdown. Finalisation requires all effective
            tax rules to be in VERIFIED status.
          </p>
        </CardHeader>
        <CardContent>
          {periods.isLoading ? (
            <p className="text-sm text-gray-400">Loading periods…</p>
          ) : periods.error ? (
            <p className="text-sm text-red-500">
              Unable to load payroll periods. Backend may be unavailable.
            </p>
          ) : !periods.data?.length ? (
            <div className="py-10 text-center">
              <p className="text-sm text-gray-500">No payroll periods yet.</p>
              <p className="mt-1 text-xs text-gray-400">
                First <Link href="/payroll/profiles" className="text-blue-600 hover:underline">add employee payroll profiles</Link>,
                then create a period above.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-xs text-gray-500">
                    <th className="p-3">Period</th>
                    <th className="p-3">Status</th>
                    <th className="p-3">Gross</th>
                    <th className="p-3">Income tax</th>
                    <th className="p-3">Employee pension</th>
                    <th className="p-3">Net pay</th>
                    <th className="p-3">Employer cost</th>
                    <th className="p-3">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {periods.data.map(p => <PeriodRow key={p.id} period={p} />)}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800 dark:border-blue-800/40 dark:bg-blue-950/30 dark:text-blue-300">
        <AlertCircle size={15} className="mr-1.5 inline" />
        <strong>Ethiopian payroll compliance note:</strong> Employment income tax uses the progressive
        brackets from Proclamation No. 1395/2025. Employee pension is 7 % and employer pension is 11 %
        of gross salary (Proclamation No. 1268/2022). All rates require accountant verification before finalisation.
      </div>
    </div>
  );
}
