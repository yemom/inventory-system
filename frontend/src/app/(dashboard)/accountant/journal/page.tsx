'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  FileSpreadsheet, Plus, RefreshCw, CheckCircle, AlertCircle, Shield,
  ChevronDown, ChevronRight, RotateCcw, Send, Check, Filter, Search,
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Modal from '@/components/ui/Modal';
import { accountingApi, type JournalEntry, type Account, type CreateJournalLineRequest } from '@/lib/api/accountingApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

const STATUS_BADGES: Record<string, string> = {
  DRAFT: 'bg-amber-100 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300',
  POSTED: 'bg-green-100 text-green-800 dark:bg-green-950/40 dark:text-green-300',
  REVERSED: 'bg-rose-100 text-rose-800 dark:bg-rose-950/40 dark:text-rose-300',
};

export default function JournalEntriesPage() {
  const { hasPermission } = useAuth();
  const [entries, setEntries] = useState<JournalEntry[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [expandedId, setExpandedId] = useState<number | null>(null);

  // Manual entry modal
  const [createOpen, setCreateOpen] = useState(false);
  const [entryDate, setEntryDate] = useState(new Date().toISOString().slice(0, 10));
  const [description, setDescription] = useState('');
  const [reference, setReference] = useState('');
  const [lines, setLines] = useState<CreateJournalLineRequest[]>([
    { accountId: undefined, debit: 0, credit: 0, description: '' },
    { accountId: undefined, debit: 0, credit: 0, description: '' },
  ]);
  const [submitting, setSubmitting] = useState(false);

  // Reversal modal
  const [reverseModalId, setReverseModalId] = useState<number | null>(null);
  const [reversalReason, setReversalReason] = useState('');
  const [reversing, setReversing] = useState(false);

  const canView = hasPermission('ACCOUNTING_VIEW') || hasPermission('FINANCIAL_REPORT_VIEW');
  const canCreate = hasPermission('ACCOUNTING_JOURNAL_CREATE');
  const canPost = hasPermission('ACCOUNTING_JOURNAL_POST');
  const canReverse = hasPermission('ACCOUNTING_JOURNAL_REVERSE');

  const fetchEntries = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [entriesData, accountsData] = await Promise.all([
        accountingApi.journalEntries(statusFilter !== 'ALL' ? { status: statusFilter } : undefined),
        accountingApi.accounts(),
      ]);
      setEntries(Array.isArray(entriesData) ? entriesData : []);
      setAccounts(Array.isArray(accountsData) ? accountsData : []);
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? 'Failed to load journal entries');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    if (canView) fetchEntries();
  }, [canView, fetchEntries]);

  const handleSyncOperations = async () => {
    setLoading(true);
    try {
      const count = await accountingApi.sync();
      alert(`Synchronized ${count} operational transactions into the double-entry journal!`);
      fetchEntries();
    } catch (e: unknown) {
      alert((e as { message?: string })?.message ?? 'Failed to sync operational events');
      setLoading(false);
    }
  };

  const handlePost = async (id: number) => {
    if (!confirm('Are you sure you want to post this journal entry to the general ledger? Once posted, it cannot be edited.')) return;
    try {
      await accountingApi.postJournalEntry(id);
      fetchEntries();
    } catch (err: unknown) {
      alert((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to post entry');
    }
  };

  const handleReverse = async () => {
    if (!reverseModalId || !reversalReason.trim()) return;
    setReversing(true);
    try {
      await accountingApi.reverseJournalEntry(reverseModalId, reversalReason.trim());
      setReverseModalId(null);
      setReversalReason('');
      fetchEntries();
    } catch (err: unknown) {
      alert((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to reverse entry');
    } finally {
      setReversing(false);
    }
  };

  const addLine = () => {
    setLines(prev => [...prev, { accountId: undefined, debit: 0, credit: 0, description: '' }]);
  };

  const removeLine = (index: number) => {
    if (lines.length <= 2) return;
    setLines(prev => prev.filter((_, i) => i !== index));
  };

  const updateLine = (index: number, patch: Partial<CreateJournalLineRequest>) => {
    setLines(prev => prev.map((l, i) => (i === index ? { ...l, ...patch } : l)));
  };

  const totalDebit = lines.reduce((acc, l) => acc + (Number(l.debit) || 0), 0);
  const totalCredit = lines.reduce((acc, l) => acc + (Number(l.credit) || 0), 0);
  const isBalanced = Math.abs(totalDebit - totalCredit) < 0.001 && totalDebit > 0;

  const handleCreateEntry = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isBalanced) {
      alert('Total debits must equal total credits before creating a journal entry.');
      return;
    }
    setSubmitting(true);
    try {
      await accountingApi.createJournalEntry({
        entryDate,
        description: description.trim(),
        reference: reference.trim() || undefined,
        sourceType: 'MANUAL',
        lines: lines.map(l => ({
          accountId: l.accountId,
          debit: Number(l.debit) || 0,
          credit: Number(l.credit) || 0,
          description: l.description?.trim() || undefined,
        })),
      });
      setCreateOpen(false);
      setDescription('');
      setReference('');
      setLines([
        { accountId: undefined, debit: 0, credit: 0, description: '' },
        { accountId: undefined, debit: 0, credit: 0, description: '' },
      ]);
      fetchEntries();
    } catch (err: unknown) {
      alert((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to create entry');
    } finally {
      setSubmitting(false);
    }
  };

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Shield className="mx-auto mb-2 text-amber-500" size={32} />
        <p>You do not have permission to view Journal Entries.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Journal Entries"
        subtitle="Double-entry transaction register with immutable posting and audit controls"
        actions={
          <div className="flex items-center gap-3">
            {canPost && (
              <Button variant="outline" onClick={handleSyncOperations} className="flex items-center gap-1.5">
                <RotateCcw size={15} /> Sync Operations
              </Button>
            )}
            {canCreate && (
              <Button onClick={() => setCreateOpen(true)} className="flex items-center gap-1.5">
                <Plus size={16} /> New Entry
              </Button>
            )}
            <button
              onClick={fetchEntries}
              disabled={loading}
              className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700"
            >
              <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
              Refresh
            </button>
          </div>
        }
      />

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <AlertCircle className="mr-2 inline" size={16} />{error}
        </div>
      )}

      {/* Filter toolbar */}
      <div className="flex items-center justify-between rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Showing {entries.length} entries
        </span>
        <div className="flex items-center gap-2">
          <Filter size={14} className="text-gray-400" />
          <select
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value)}
            className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          >
            <option value="ALL">All Statuses</option>
            <option value="POSTED">Posted Only</option>
            <option value="DRAFT">Drafts Only</option>
            <option value="REVERSED">Reversed</option>
          </select>
        </div>
      </div>

      {/* Entries List */}
      <div className="space-y-3">
        {loading ? (
          [...Array(4)].map((_, i) => (
            <div key={i} className="h-28 animate-pulse rounded-xl bg-gray-100 dark:bg-gray-800" />
          ))
        ) : entries.length === 0 ? (
          <div className="rounded-xl border border-gray-200 bg-white p-12 text-center text-gray-400 dark:border-gray-700 dark:bg-gray-800">
            <FileSpreadsheet size={36} className="mx-auto mb-2 opacity-40" />
            <p className="font-medium">No journal entries found</p>
            <p className="mt-1 text-sm text-gray-400">
              Click &quot;Sync Operations&quot; to auto-generate journals from completed sales, expenses, and payroll, or create a manual entry.
            </p>
          </div>
        ) : (
          entries.map(e => {
            const isExpanded = expandedId === e.id;
            return (
              <div
                key={e.id}
                className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm transition-all dark:border-gray-700 dark:bg-gray-800"
              >
                {/* Header row */}
                <div
                  className="flex flex-wrap items-center justify-between gap-3 p-4 cursor-pointer hover:bg-gray-50/60 dark:hover:bg-gray-800/80"
                  onClick={() => setExpandedId(isExpanded ? null : e.id)}
                >
                  <div className="flex items-center gap-3">
                    <button className="text-gray-400">
                      {isExpanded ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
                    </button>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-sm font-bold text-gray-900 dark:text-gray-100">{e.entryNumber}</span>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_BADGES[e.status] || 'bg-gray-100'}`}>
                          {e.status}
                        </span>
                        <span className="rounded bg-gray-100 px-2 py-0.5 text-[11px] text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                          {e.sourceType}
                        </span>
                      </div>
                      <p className="mt-0.5 text-xs text-gray-500">
                        Date: {e.entryDate} {e.reference ? `· Ref: ${e.reference}` : ''}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-6">
                    <div className="text-right">
                      <p className="text-xs text-gray-400 uppercase tracking-wide">Amount</p>
                      <p className="font-mono text-sm font-bold text-gray-900 dark:text-gray-100">{ETB(e.totalDebit)}</p>
                    </div>

                    <div className="flex items-center gap-2" onClick={ev => ev.stopPropagation()}>
                      {e.status === 'DRAFT' && canPost && (
                        <Button size="sm" onClick={() => handlePost(e.id)} className="flex items-center gap-1 text-xs">
                          <Check size={14} /> Post
                        </Button>
                      )}
                      {e.status === 'POSTED' && canReverse && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            setReverseModalId(e.id);
                            setReversalReason('');
                          }}
                          className="flex items-center gap-1 text-xs text-rose-600 hover:text-rose-700"
                        >
                          <RotateCcw size={14} /> Reverse
                        </Button>
                      )}
                    </div>
                  </div>
                </div>

                {/* Description */}
                <div className="px-4 pb-2 text-xs text-gray-600 dark:text-gray-300">
                  {e.description}
                  {e.postedByName && (
                    <span className="ml-3 text-gray-400">Posted by {e.postedByName} on {new Date(e.postedAt!).toLocaleDateString()}</span>
                  )}
                  {e.reversalReason && (
                    <span className="ml-3 text-rose-500">Reversal reason: {e.reversalReason}</span>
                  )}
                </div>

                {/* Expanded Lines */}
                {isExpanded && (
                  <div className="border-t border-gray-100 bg-gray-50/50 p-4 dark:border-gray-700/60 dark:bg-gray-900/40">
                    <table className="min-w-full text-xs">
                      <thead>
                        <tr className="text-gray-500 uppercase tracking-wide text-[11px] border-b border-gray-200 dark:border-gray-700">
                          <th className="py-2 text-left">#</th>
                          <th className="py-2 text-left">Account</th>
                          <th className="py-2 text-left">Description</th>
                          <th className="py-2 text-right">Debit (ETB)</th>
                          <th className="py-2 text-right">Credit (ETB)</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                        {e.lines.map(l => (
                          <tr key={l.id}>
                            <td className="py-2 text-gray-400">{l.lineNumber}</td>
                            <td className="py-2 font-medium">
                              <span className="font-mono text-gray-600 dark:text-gray-400 mr-1.5">[{l.accountCode}]</span>
                              {l.accountName}
                            </td>
                            <td className="py-2 text-gray-500">{l.description || '—'}</td>
                            <td className="py-2 text-right font-mono font-medium text-gray-800 dark:text-gray-200">
                              {l.debit > 0 ? ETB(l.debit) : '—'}
                            </td>
                            <td className="py-2 text-right font-mono font-medium text-gray-800 dark:text-gray-200">
                              {l.credit > 0 ? ETB(l.credit) : '—'}
                            </td>
                          </tr>
                        ))}
                        <tr className="font-bold border-t border-gray-300 dark:border-gray-600">
                          <td colSpan={3} className="py-2 text-right">Totals:</td>
                          <td className="py-2 text-right font-mono text-gray-900 dark:text-gray-100">{ETB(e.totalDebit)}</td>
                          <td className="py-2 text-right font-mono text-gray-900 dark:text-gray-100">{ETB(e.totalCredit)}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* New Journal Entry Modal */}
      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="New Double-Entry Journal">
        <form onSubmit={handleCreateEntry} className="space-y-4 max-w-2xl">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Transaction Date *</label>
              <Input type="date" value={entryDate} onChange={e => setEntryDate(e.target.value)} required />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Reference / Voucher</label>
              <Input value={reference} onChange={e => setReference(e.target.value)} placeholder="e.g. INV-9021, EXP-44" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Description *</label>
            <Input value={description} onChange={e => setDescription(e.target.value)} placeholder="Business reason for entry" required />
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-semibold uppercase tracking-wide text-gray-600 dark:text-gray-400">Debit and Credit Lines</label>
              <Button type="button" size="sm" variant="outline" onClick={addLine}>+ Add Line</Button>
            </div>

            <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
              {lines.map((l, idx) => (
                <div key={idx} className="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50/50 p-2 text-xs dark:border-gray-700 dark:bg-gray-800/50">
                  <select
                    value={l.accountId ?? ''}
                    onChange={e => updateLine(idx, { accountId: Number(e.target.value) || undefined })}
                    className="flex-1 rounded border border-gray-300 bg-white p-1.5 text-xs dark:border-gray-600 dark:bg-gray-700"
                    required
                  >
                    <option value="">Select Account *</option>
                    {accounts.map(a => (
                      <option key={a.id} value={a.id}>
                        {a.code} — {a.name} ({a.category})
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="Debit"
                    value={l.debit || ''}
                    onChange={e => updateLine(idx, { debit: parseFloat(e.target.value) || 0, credit: 0 })}
                    className="w-24 rounded border border-gray-300 bg-white p-1.5 text-right font-mono text-xs dark:border-gray-600 dark:bg-gray-700"
                  />
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="Credit"
                    value={l.credit || ''}
                    onChange={e => updateLine(idx, { credit: parseFloat(e.target.value) || 0, debit: 0 })}
                    className="w-24 rounded border border-gray-300 bg-white p-1.5 text-right font-mono text-xs dark:border-gray-600 dark:bg-gray-700"
                  />
                  {lines.length > 2 && (
                    <button type="button" onClick={() => removeLine(idx)} className="text-red-500 hover:text-red-700 p-1">×</button>
                  )}
                </div>
              ))}
            </div>

            <div className="mt-3 flex items-center justify-between rounded-lg bg-gray-100 p-3 text-xs dark:bg-gray-800">
              <div>
                <span>Total Debits: <strong className="font-mono">{ETB(totalDebit)}</strong></span>
                <span className="ml-4">Total Credits: <strong className="font-mono">{ETB(totalCredit)}</strong></span>
              </div>
              <div>
                {isBalanced ? (
                  <span className="text-green-600 font-semibold flex items-center gap-1">
                    <CheckCircle size={14} /> Invariant Balanced
                  </span>
                ) : (
                  <span className="text-red-600 font-medium">
                    Out of balance by {ETB(Math.abs(totalDebit - totalCredit))}
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setCreateOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={!isBalanced || submitting}>
              {submitting ? 'Creating…' : 'Save Draft Entry'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Reverse Modal */}
      <Modal open={reverseModalId !== null} onClose={() => setReverseModalId(null)} title="Reverse Posted Entry">
        <div className="space-y-4">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            Reversing this entry will create an auditable reversing journal with inverted debit and credit lines and link it to the original entry.
          </p>
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Reason for Reversal *</label>
            <Input
              value={reversalReason}
              onChange={e => setReversalReason(e.target.value)}
              placeholder="e.g. Erroneous account selection or transaction voided"
              required
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setReverseModalId(null)}>Cancel</Button>
            <Button onClick={handleReverse} disabled={reversing || !reversalReason.trim()} className="bg-rose-600 hover:bg-rose-700">
              {reversing ? 'Reversing…' : 'Confirm Reversal'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
