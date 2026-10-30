'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  BookOpen, Plus, Search, Filter, RefreshCw, CheckCircle, AlertCircle, Shield,
} from 'lucide-react';
import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Modal from '@/components/ui/Modal';
import { accountingApi, type Account } from '@/lib/api/accountingApi';
import { useAuth } from '@/lib/auth/AuthProvider';

const ETB = (n: number) =>
  new Intl.NumberFormat('en-ET', { style: 'currency', currency: 'ETB', minimumFractionDigits: 2 }).format(n);

const CATEGORY_COLORS: Record<string, string> = {
  ASSET: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300',
  LIABILITY: 'bg-amber-100 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300',
  EQUITY: 'bg-purple-100 text-purple-800 dark:bg-purple-950/40 dark:text-purple-300',
  REVENUE: 'bg-blue-100 text-blue-800 dark:bg-blue-950/40 dark:text-blue-300',
  EXPENSE: 'bg-rose-100 text-rose-800 dark:bg-rose-950/40 dark:text-rose-300',
};

export default function ChartOfAccountsPage() {
  const { hasPermission } = useAuth();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [newCategory, setNewCategory] = useState<'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE'>('ASSET');
  const [newSubCategory, setNewSubCategory] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [saving, setSaving] = useState(false);

  const canView = hasPermission('ACCOUNTING_VIEW') || hasPermission('FINANCIAL_REPORT_VIEW');
  const canCreate = hasPermission('ACCOUNTING_JOURNAL_CREATE');

  const fetchAccounts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await accountingApi.accounts();
      setAccounts(Array.isArray(data) ? data : []);
    } catch (e: unknown) {
      setError((e as { message?: string })?.message ?? 'Failed to load chart of accounts');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canView) fetchAccounts();
  }, [canView, fetchAccounts]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCode.trim() || !newName.trim()) return;
    setSaving(true);
    try {
      await accountingApi.createAccount({
        code: newCode.trim(),
        name: newName.trim(),
        category: newCategory,
        subCategory: newSubCategory.trim() || undefined,
        description: newDescription.trim() || undefined,
      });
      setCreateModalOpen(false);
      setNewCode('');
      setNewName('');
      setNewDescription('');
      fetchAccounts();
    } catch (err: unknown) {
      alert((err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to create account');
    } finally {
      setSaving(false);
    }
  };

  const filtered = accounts.filter(a => {
    const matchSearch =
      a.code.toLowerCase().includes(search.toLowerCase()) ||
      a.name.toLowerCase().includes(search.toLowerCase()) ||
      (a.description ?? '').toLowerCase().includes(search.toLowerCase());
    const matchCategory = categoryFilter === 'ALL' || a.category === categoryFilter;
    return matchSearch && matchCategory;
  });

  if (!canView) {
    return (
      <div className="p-8 text-center text-gray-500">
        <Shield className="mx-auto mb-2 text-amber-500" size={32} />
        <p>You do not have permission to view the Chart of Accounts.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Chart of Accounts"
        subtitle="Configurable accounts hierarchy with real-time debit/credit balances"
        actions={
          <div className="flex items-center gap-3">
            {canCreate && (
              <Button onClick={() => setCreateModalOpen(true)} className="flex items-center gap-1.5">
                <Plus size={16} /> New Account
              </Button>
            )}
            <button
              onClick={fetchAccounts}
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

      {/* Filter and Search toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div className="flex flex-1 items-center gap-2 max-w-md">
          <Search size={16} className="text-gray-400" />
          <input
            type="text"
            placeholder="Search by code or account name..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full bg-transparent text-sm outline-none dark:text-gray-200"
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter size={14} className="text-gray-400" />
          <select
            value={categoryFilter}
            onChange={e => setCategoryFilter(e.target.value)}
            className="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          >
            <option value="ALL">All Categories</option>
            <option value="ASSET">Assets</option>
            <option value="LIABILITY">Liabilities</option>
            <option value="EQUITY">Equity</option>
            <option value="REVENUE">Revenue</option>
            <option value="EXPENSE">Expenses</option>
          </select>
        </div>
      </div>

      {/* Accounts Table */}
      <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-700">
          <thead className="bg-gray-50 dark:bg-gray-800">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Code</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Account Name</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Category</th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">Sub-Category</th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-gray-500">Balance (ETB)</th>
              <th className="px-4 py-3 text-center text-xs font-medium uppercase tracking-wide text-gray-500">Type</th>
              <th className="px-4 py-3 text-center text-xs font-medium uppercase tracking-wide text-gray-500">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
            {loading ? (
              [...Array(6)].map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td colSpan={7} className="px-4 py-4"><div className="h-4 bg-gray-100 rounded dark:bg-gray-700" /></td>
                </tr>
              ))
            ) : filtered.length === 0 ? (
              <tr>
                <td colSpan={7} className="p-8 text-center text-gray-400">
                  <BookOpen size={28} className="mx-auto mb-2 opacity-40" />
                  No accounts found matching your criteria.
                </td>
              </tr>
            ) : (
              filtered.map(a => (
                <tr key={a.id} className="hover:bg-gray-50/80 dark:hover:bg-gray-800/60">
                  <td className="px-4 py-3 font-mono text-xs font-bold text-gray-800 dark:text-gray-200">{a.code}</td>
                  <td className="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                    {a.name}
                    {a.description && <p className="text-xs text-gray-400">{a.description}</p>}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${CATEGORY_COLORS[a.category] || 'bg-gray-100 text-gray-700'}`}>
                      {a.category}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">{a.subCategory?.replace('_', ' ') || '—'}</td>
                  <td className="px-4 py-3 text-right font-mono font-medium text-gray-900 dark:text-gray-100">
                    {ETB(a.balance)}
                  </td>
                  <td className="px-4 py-3 text-center">
                    {a.systemAccount ? (
                      <span className="rounded bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">System</span>
                    ) : (
                      <span className="rounded bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-300">Custom</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-center">
                    {a.active ? (
                      <span className="inline-flex items-center gap-1 text-xs text-green-600 dark:text-green-400">
                        <CheckCircle size={12} /> Active
                      </span>
                    ) : (
                      <span className="text-xs text-gray-400">Inactive</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Create Account Modal */}
      <Modal open={createModalOpen} onClose={() => setCreateModalOpen(false)} title="Create New Account">
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Account Code *</label>
            <Input
              value={newCode}
              onChange={e => setNewCode(e.target.value)}
              placeholder="e.g. 1030"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Account Name *</label>
            <Input
              value={newName}
              onChange={e => setNewName(e.target.value)}
              placeholder="e.g. Petty Cash Register"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Category *</label>
            <select
              value={newCategory}
              onChange={e => setNewCategory(e.target.value as any)}
              className="mt-1 w-full rounded-lg border border-gray-300 bg-white p-2 text-sm dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            >
              <option value="ASSET">ASSET</option>
              <option value="LIABILITY">LIABILITY</option>
              <option value="EQUITY">EQUITY</option>
              <option value="REVENUE">REVENUE</option>
              <option value="EXPENSE">EXPENSE</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Sub-Category</label>
            <Input
              value={newSubCategory}
              onChange={e => setNewSubCategory(e.target.value)}
              placeholder="e.g. CURRENT_ASSET, OPERATING_EXPENSE"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300">Description</label>
            <Input
              value={newDescription}
              onChange={e => setNewDescription(e.target.value)}
              placeholder="Optional notes or purpose"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" type="button" onClick={() => setCreateModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Creating…' : 'Save Account'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
