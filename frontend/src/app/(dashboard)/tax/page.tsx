'use client';

import { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  BarChart3, ReceiptText, Scale, TrendingUp, Upload, FileImage,
  X, Trash2, ChevronDown, ChevronUp, AlertCircle, CheckCircle2,
  Calculator, RefreshCw,
} from 'lucide-react';
import {
  Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer,
  Tooltip, XAxis, YAxis,
} from 'recharts';
import PageHeader from '@/components/ui/PageHeader';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import Select from '@/components/ui/Select';
import { useToast } from '@/components/ui/ToastProvider';
import { taxApi, type ReceiptTaxCalculation } from '@/lib/api/taxApi';
import { useAuth } from '@/lib/auth/AuthProvider';

// ── Helpers ──────────────────────────────────────────────────────────────────
const etb = (v: number | null | undefined) =>
  new Intl.NumberFormat('en-ET', {
    style: 'currency', currency: 'ETB', maximumFractionDigits: 2,
  }).format(v ?? 0);

// ── Types ────────────────────────────────────────────────────────────────────
interface TaxReceipt {
  id: number;
  receiptName?: string;
  fileType?: string;
  detectedAmount?: number;
  taxableAmount?: number;
  taxAmount?: number;
  totalAmount?: number;
  treatment: string;
  taxInclusive: boolean;
  currency: string;
  aiExtracted: boolean;
  vendorName?: string;
  receiptDate?: string;
  notes?: string;
  accountantName?: string;
  createdAt: string;
  notice?: string;
}

// ── Receipt Upload Panel ─────────────────────────────────────────────────────
function ReceiptUploadPanel({ onSaved }: { onSaved: () => void }) {
  const { toast } = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [form, setForm] = useState({
    amount: '',
    treatment: 'STANDARD',
    taxInclusive: false,
    receiptName: '',
    vendorName: '',
    receiptDate: '',
    notes: '',
  });
  const [calc, setCalc] = useState<ReceiptTaxCalculation | null>(null);
  const [calculating, setCalculating] = useState(false);
  const [uploading, setUploading] = useState(false);

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null;
    setFile(f);
    if (f && f.type.startsWith('image/')) {
      const url = URL.createObjectURL(f);
      setPreview(url);
    } else {
      setPreview(null);
    }
    // Pre-fill name from filename
    if (f && !form.receiptName) {
      setForm(prev => ({ ...prev, receiptName: f.name.replace(/\.[^.]+$/, '') }));
    }
  };

  const handleCalculate = async () => {
    const amount = parseFloat(form.amount);
    if (!amount || amount <= 0) {
      toast('warning', 'Amount required', 'Enter the receipt total before calculating tax.');
      return;
    }
    setCalculating(true);
    try {
      const result = await taxApi.calculateReceipt({
        amount,
        treatment: form.treatment,
        taxInclusive: form.taxInclusive,
        receiptName: form.receiptName || undefined,
      });
      setCalc(result);
    } catch {
      toast('error', 'Calculation failed', 'Unable to estimate tax from the backend. Check backend connectivity.');
    } finally {
      setCalculating(false);
    }
  };

  const handleSave = async () => {
    if (!file) {
      toast('warning', 'No file selected', 'Choose a receipt image or PDF to upload.');
      return;
    }
    const amount = parseFloat(form.amount);
    if (!amount || amount <= 0) {
      toast('warning', 'Amount required', 'Enter the receipt total before saving.');
      return;
    }
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('amount', String(amount));
      fd.append('treatment', form.treatment);
      fd.append('taxInclusive', String(form.taxInclusive));
      if (form.receiptName) fd.append('receiptName', form.receiptName);
      if (form.vendorName) fd.append('vendorName', form.vendorName);
      if (form.notes) fd.append('notes', form.notes);
      if (form.receiptDate) fd.append('receiptDate', form.receiptDate);

      await taxApi.uploadReceipt(fd);
      toast('success', 'Receipt saved', 'Tax calculation recorded and receipt stored.');
      setFile(null);
      setPreview(null);
      setCalc(null);
      setForm({ amount: '', treatment: 'STANDARD', taxInclusive: false, receiptName: '', vendorName: '', receiptDate: '', notes: '' });
      onSaved();
    } catch {
      toast('error', 'Upload failed', 'Unable to save receipt. The backend may be unavailable.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <FileImage size={18} className="text-blue-600" />
          <h2 className="font-semibold">Upload Receipt &amp; Calculate Tax</h2>
        </div>
        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
          Upload a receipt image or PDF, enter the total, and the system will calculate the VAT
          estimate using the current effective Ethiopian tax rules.
        </p>
      </CardHeader>
      <CardContent>
        <div className="grid gap-4 md:grid-cols-2">
          {/* Left: file drop */}
          <div>
            <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              Receipt file (image / PDF)
            </label>
            <label
              htmlFor="receipt-file"
              className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-gray-300 bg-gray-50 p-6 transition hover:border-blue-400 hover:bg-blue-50 dark:border-gray-600 dark:bg-gray-800/50"
            >
              {preview ? (
                <img src={preview} alt="Receipt preview" className="max-h-40 rounded object-contain" />
              ) : file ? (
                <div className="text-center">
                  <FileImage size={32} className="mx-auto mb-1 text-blue-500" />
                  <p className="text-xs font-medium">{file.name}</p>
                </div>
              ) : (
                <>
                  <Upload size={28} className="text-gray-400" />
                  <p className="text-sm text-gray-500">Click or drag a receipt here</p>
                  <p className="text-xs text-gray-400">JPEG, PNG, or PDF</p>
                </>
              )}
              <input id="receipt-file" type="file" accept="image/*,.pdf" className="sr-only" onChange={handleFile} />
            </label>
            {file && (
              <button
                type="button"
                onClick={() => { setFile(null); setPreview(null); }}
                className="mt-1 flex items-center gap-1 text-xs text-red-500 hover:text-red-700"
              >
                <X size={12} /> Remove file
              </button>
            )}
          </div>

          {/* Right: form */}
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                Receipt total (ETB) <span className="text-red-500">*</span>
              </label>
              <Input
                type="number"
                min="0"
                step="0.01"
                placeholder="e.g. 1150.00"
                value={form.amount}
                onChange={e => { setForm(p => ({ ...p, amount: e.target.value })); setCalc(null); }}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">Tax treatment</label>
                <Select
                  options={[
                    { value: 'STANDARD', label: 'Standard VAT (15%)' },
                    { value: 'ZERO_RATED', label: 'Zero-rated (0%)' },
                    { value: 'EXEMPT', label: 'Exempt' },
                  ]}
                  value={form.treatment}
                  onChange={e => { setForm(p => ({ ...p, treatment: e.target.value })); setCalc(null); }}
                />
              </div>
              <div className="flex items-end pb-0.5">
                <label className="flex cursor-pointer items-center gap-2 text-xs text-gray-700 dark:text-gray-300">
                  <input
                    type="checkbox"
                    checked={form.taxInclusive}
                    onChange={e => { setForm(p => ({ ...p, taxInclusive: e.target.checked })); setCalc(null); }}
                    className="h-4 w-4 rounded text-blue-600"
                  />
                  VAT already included
                </label>
              </div>
            </div>

            <Input
              placeholder="Receipt name / description"
              value={form.receiptName}
              onChange={e => setForm(p => ({ ...p, receiptName: e.target.value }))}
            />

            <div className="grid grid-cols-2 gap-3">
              <Input
                placeholder="Vendor name"
                value={form.vendorName}
                onChange={e => setForm(p => ({ ...p, vendorName: e.target.value }))}
              />
              <Input
                type="date"
                value={form.receiptDate}
                onChange={e => setForm(p => ({ ...p, receiptDate: e.target.value }))}
              />
            </div>

            <Input
              placeholder="Notes (optional)"
              value={form.notes}
              onChange={e => setForm(p => ({ ...p, notes: e.target.value }))}
            />

            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={handleCalculate}
                disabled={calculating || !form.amount}
                className="flex-1 gap-1"
              >
                <Calculator size={14} />
                {calculating ? 'Calculating…' : 'Preview Tax'}
              </Button>
              <Button
                onClick={handleSave}
                disabled={uploading || !file || !form.amount}
                className="flex-1 gap-1"
              >
                <Upload size={14} />
                {uploading ? 'Saving…' : 'Save Receipt'}
              </Button>
            </div>
          </div>
        </div>

        {/* Tax calculation result */}
        {calc && (
          <div className="mt-4 rounded-xl border border-blue-200 bg-blue-50 p-4 dark:border-blue-800/50 dark:bg-blue-950/30">
            <div className="mb-2 flex items-center gap-2">
              <CheckCircle2 size={16} className="text-blue-600" />
              <span className="text-sm font-semibold text-blue-800 dark:text-blue-200">Tax Estimate</span>
              <span className="ml-auto text-xs text-blue-600 dark:text-blue-400">{calc.ruleCode}</span>
            </div>
            <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm md:grid-cols-4">
              <div>
                <p className="text-xs text-gray-500">Entered amount</p>
                <p className="font-semibold">{etb(calc.enteredAmount)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">Taxable amount</p>
                <p className="font-semibold">{etb(calc.taxableAmount)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">VAT amount</p>
                <p className="font-semibold text-amber-700 dark:text-amber-300">{etb(calc.taxAmount)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">Total payable</p>
                <p className="font-bold text-blue-700 dark:text-blue-300">{etb(calc.totalAmount)}</p>
              </div>
            </div>
            <p className="mt-3 text-xs text-amber-700 dark:text-amber-400">
              <AlertCircle size={11} className="mr-1 inline" />
              {calc.notice}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ── Saved Receipts Table ─────────────────────────────────────────────────────
function ReceiptsTable({ receipts, onDelete }: { receipts: TaxReceipt[]; onDelete: (id: number) => void }) {
  const [expanded, setExpanded] = useState<number | null>(null);

  if (!receipts.length) {
    return (
      <div className="py-10 text-center text-sm text-gray-400">
        No receipts saved yet. Upload a receipt above to start tracking tax.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-xs text-gray-500">
            <th className="p-3">Receipt</th>
            <th className="p-3">Vendor</th>
            <th className="p-3">Date</th>
            <th className="p-3">Total</th>
            <th className="p-3">VAT</th>
            <th className="p-3">Treatment</th>
            <th className="p-3">Accountant</th>
            <th className="p-3" />
          </tr>
        </thead>
        <tbody>
          {receipts.map(r => (
            <>
              <tr key={r.id} className="border-b hover:bg-gray-50 dark:hover:bg-gray-800/50">
                <td className="p-3">
                  <p className="font-medium">{r.receiptName ?? '—'}</p>
                  <p className="text-xs text-gray-400">{r.fileType ?? ''}</p>
                </td>
                <td className="p-3">{r.vendorName ?? '—'}</td>
                <td className="p-3">{r.receiptDate ? r.receiptDate.slice(0, 10) : new Date(r.createdAt).toLocaleDateString()}</td>
                <td className="p-3 font-semibold">{etb(r.totalAmount)}</td>
                <td className="p-3 text-amber-700 dark:text-amber-300">{etb(r.taxAmount)}</td>
                <td className="p-3">
                  <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                    r.treatment === 'STANDARD'
                      ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300'
                      : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
                  }`}>{r.treatment}</span>
                </td>
                <td className="p-3 text-xs text-gray-500">{r.accountantName ?? '—'}</td>
                <td className="p-3">
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => setExpanded(expanded === r.id ? null : r.id)}
                      className="rounded p-1 text-gray-400 hover:text-blue-600"
                    >
                      {expanded === r.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </button>
                    <button
                      type="button"
                      onClick={() => onDelete(r.id)}
                      className="rounded p-1 text-gray-400 hover:text-red-600"
                      title="Delete receipt"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </td>
              </tr>
              {expanded === r.id && (
                <tr key={`${r.id}-detail`} className="bg-gray-50 dark:bg-gray-800/30">
                  <td colSpan={8} className="px-6 py-3 text-xs text-gray-600 dark:text-gray-400">
                    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                      <div><span className="font-medium">Taxable base:</span> {etb(r.taxableAmount)}</div>
                      <div><span className="font-medium">VAT inclusive:</span> {r.taxInclusive ? 'Yes' : 'No'}</div>
                      <div><span className="font-medium">Currency:</span> {r.currency}</div>
                      <div><span className="font-medium">AI extracted:</span> {r.aiExtracted ? 'Yes' : 'Manual entry'}</div>
                    </div>
                    {r.notes && <p className="mt-2"><span className="font-medium">Notes:</span> {r.notes}</p>}
                    {r.notice && (
                      <p className="mt-2 text-amber-700 dark:text-amber-400">
                        <AlertCircle size={11} className="mr-1 inline" />{r.notice}
                      </p>
                    )}
                  </td>
                </tr>
              )}
            </>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ── Main Page ────────────────────────────────────────────────────────────────
export default function TaxPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const { hasPermission } = useAuth();

  const [period, setPeriod] = useState('');
  const [showTransactions, setShowTransactions] = useState(false);
  const canManage = hasPermission('TAX_RULE_MANAGE');

  const summary = useQuery({
    queryKey: ['tax-summary', period],
    queryFn: () => taxApi.summary(period || undefined),
    retry: 1,
  });

  const transactions = useQuery({
    queryKey: ['tax-transactions', period],
    queryFn: () => taxApi.transactions(period || undefined),
    enabled: showTransactions,
    retry: 1,
  });

  const receipts = useQuery({
    queryKey: ['tax-receipts'],
    queryFn: () => taxApi.receipts(),
    retry: 1,
  });

  const deleteReceipt = useMutation({
    mutationFn: (id: number) => taxApi.deleteReceipt(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tax-receipts'] });
      toast('success', 'Receipt deleted', 'Receipt and its tax record have been removed.');
    },
    onError: () => toast('error', 'Delete failed', 'Could not delete receipt.'),
  });

  const handleDeleteReceipt = useCallback((id: number) => {
    if (window.confirm('Delete this receipt and its tax record?')) {
      deleteReceipt.mutate(id);
    }
  }, [deleteReceipt]);

  // Derive totals from receipts for summary panel
  const receiptTotals = {
    count: receipts.data?.length ?? 0,
    totalVat: receipts.data?.reduce((s, r) => s + (r.taxAmount ?? 0), 0) ?? 0,
    totalRevenue: receipts.data?.reduce((s, r) => s + (r.detectedAmount ?? 0), 0) ?? 0,
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tax Management"
        subtitle="VAT estimates from recorded transactions and uploaded receipts. All figures require accountant review before filing."
        actions={
          <div className="flex items-center gap-2">
            <input
              type="month"
              value={period}
              onChange={e => setPeriod(e.target.value)}
              className="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm dark:border-gray-600 dark:bg-gray-800"
              title="Filter by period"
            />
            <Button
              variant={showTransactions ? 'primary' : 'outline'}
              size="sm"
              onClick={() => setShowTransactions(v => !v)}
            >
              <FileImage size={14} className="mr-1" />
              {showTransactions ? 'Hide Transactions' : 'Audit Transactions'}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                qc.invalidateQueries({ queryKey: ['tax-summary'] });
                if (showTransactions) qc.invalidateQueries({ queryKey: ['tax-transactions'] });
              }}
            >
              <RefreshCw size={14} />
            </Button>
          </div>
        }
      />

      {/* Summary stat cards — from live transactions */}
      {summary.error ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center dark:border-red-800/50 dark:bg-red-950/30">
          <AlertCircle size={32} className="mx-auto mb-2 text-red-400" />
          <p className="font-medium text-red-700 dark:text-red-300">Unable to load VAT summary from transactions</p>
          <p className="mt-1 text-sm text-red-500">Start the backend to fetch live data. Receipt upload works independently.</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            title="Output VAT"
            value={summary.isLoading ? '…' : etb(summary.data?.outputVat)}
            icon={<ReceiptText size={20} />}
            color="blue"
            changeLabel="From recorded sales"
          />
          <StatCard
            title="Eligible Input VAT"
            value={summary.isLoading ? '…' : etb(summary.data?.eligibleInputVat)}
            icon={<Scale size={20} />}
            color="emerald"
            changeLabel="From recorded purchases"
          />
          <StatCard
            title="Estimated Net VAT"
            value={summary.isLoading ? '…' : etb(summary.data?.estimatedNetVat)}
            icon={<TrendingUp size={20} />}
            color={(summary.data?.estimatedNetVat ?? 0) >= 0 ? 'amber' : 'purple'}
            changeLabel={(summary.data?.estimatedNetVat ?? 0) >= 0 ? 'Estimated payable' : 'Estimated credit'}
          />
          <StatCard
            title="Receipt VAT total"
            value={etb(receiptTotals.totalVat)}
            icon={<BarChart3 size={20} />}
            color="purple"
            changeLabel={`${receiptTotals.count} receipts uploaded`}
          />
        </div>
      )}

      {/* Complete Ethiopian Tax KPI Breakdown */}
      {!summary.error && summary.data && (
        <Card className="border border-blue-100 dark:border-blue-900/30">
          <CardHeader className="pb-3 border-b">
            <div className="flex items-center justify-between">
              <h2 className="font-semibold text-sm text-gray-900 dark:text-white flex items-center gap-2">
                <ReceiptText size={16} className="text-blue-600" />
                Ethiopian Statutory Tax Liability Breakdown (Proclamation No. 1341/2024 &amp; 979/2016)
              </h2>
              {summary.data.upcomingDeadlines && summary.data.upcomingDeadlines.length > 0 && (
                <span className="text-xs font-medium text-amber-700 bg-amber-50 dark:bg-amber-950/40 px-2.5 py-1 rounded-full border border-amber-200">
                  Next Deadline: {summary.data.upcomingDeadlines[0].dueDate} ({summary.data.upcomingDeadlines[0].description})
                </span>
              )}
            </div>
          </CardHeader>
          <CardContent className="pt-4">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs">
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Total Sales (Turnover)</span>
                <span className="text-sm font-bold text-gray-900 dark:text-white mt-1 block">{etb(summary.data.totalSales)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Taxable Sales (15%)</span>
                <span className="text-sm font-bold text-blue-600 mt-1 block">{etb(summary.data.taxableSales)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Zero-Rated Supplies</span>
                <span className="text-sm font-bold text-gray-700 dark:text-gray-300 mt-1 block">{etb(summary.data.zeroRatedSales)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Exempt Supplies</span>
                <span className="text-sm font-bold text-gray-700 dark:text-gray-300 mt-1 block">{etb(summary.data.exemptSales)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Output VAT (15%)</span>
                <span className="text-sm font-bold text-blue-600 mt-1 block">{etb(summary.data.outputVat)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Recoverable Input VAT</span>
                <span className="text-sm font-bold text-emerald-600 mt-1 block">-{etb(summary.data.eligibleInputVat)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Non-Recoverable Input VAT</span>
                <span className="text-sm font-bold text-amber-600 mt-1 block">{etb(summary.data.nonRecoverableInputVat)}</span>
              </div>
              <div className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200">
                <span className="text-blue-700 dark:text-blue-300 font-medium block">Net VAT Payable / (Credit)</span>
                <span className="text-sm font-bold text-blue-800 dark:text-blue-200 mt-1 block">{etb(summary.data.estimatedNetVat)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Withholding Tax (Deducted)</span>
                <span className="text-sm font-bold text-gray-800 dark:text-gray-200 mt-1 block">{etb(summary.data.withholdingTax)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Tax Payments Remitted</span>
                <span className="text-sm font-bold text-emerald-700 mt-1 block">{etb(summary.data.taxPaymentsMade)}</span>
              </div>
              <div className="p-3 bg-red-50 dark:bg-red-900/20 rounded-lg border border-red-200">
                <span className="text-red-700 dark:text-red-300 font-medium block">Outstanding Liabilities</span>
                <span className="text-sm font-bold text-red-700 dark:text-red-200 mt-1 block">{etb(summary.data.outstandingLiabilities)}</span>
              </div>
              <div className="p-3 bg-gray-50 dark:bg-gray-800/40 rounded-lg">
                <span className="text-gray-500 block">Estimated Income Tax (30%)</span>
                <span className="text-sm font-bold text-gray-800 dark:text-gray-200 mt-1 block">{etb(summary.data.estimatedIncomeTax)}</span>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Transaction Level Audit Drill-Down Panel */}
      {showTransactions && (
        <Card className="border shadow-sm">
          <CardHeader className="border-b pb-3">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                  <Scale size={16} className="text-blue-600" />
                  Contributing Transactions &amp; Audit Trail
                </h2>
                <p className="text-xs text-gray-500">
                  Every underlying sales order, purchase order, return credit note, and tax payment contributing to the liabilities.
                </p>
              </div>
              <span className="text-xs text-gray-400">
                {transactions.data?.length ?? 0} transactions recorded
              </span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {transactions.isLoading ? (
              <div className="p-8 text-center text-sm text-gray-500">Loading ledger transaction records...</div>
            ) : !transactions.data?.length ? (
              <div className="p-8 text-center text-sm text-gray-500">No transactions recorded for the selected period.</div>
            ) : (
              <div className="overflow-x-auto max-h-96">
                <table className="w-full text-xs">
                  <thead className="bg-gray-50 dark:bg-gray-800/60 sticky top-0 border-b">
                    <tr className="text-left text-gray-500">
                      <th className="p-3">Type</th>
                      <th className="p-3">Reference</th>
                      <th className="p-3">Date</th>
                      <th className="p-3">Party</th>
                      <th className="p-3 text-right">Gross Total</th>
                      <th className="p-3 text-right">Taxable Base</th>
                      <th className="p-3">Treatment</th>
                      <th className="p-3 text-right">VAT Amount</th>
                      <th className="p-3">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                    {transactions.data.map((tx, idx) => (
                      <tr key={idx} className="hover:bg-gray-50/60 dark:hover:bg-gray-800/30">
                        <td className="p-3 font-semibold">
                          <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                            tx.transactionType === 'SALE' ? 'bg-blue-100 text-blue-700' :
                            tx.transactionType === 'PURCHASE' ? 'bg-emerald-100 text-emerald-700' :
                            tx.transactionType === 'RETURN' ? 'bg-amber-100 text-amber-700' : 'bg-purple-100 text-purple-700'
                          }`}>
                            {tx.transactionType}
                          </span>
                        </td>
                        <td className="p-3 font-medium text-gray-800 dark:text-gray-200">{tx.referenceNumber}</td>
                        <td className="p-3 text-gray-500">{tx.transactionDate ? tx.transactionDate.slice(0, 10) : '—'}</td>
                        <td className="p-3 text-gray-600 dark:text-gray-400">{tx.partyName || 'Walk-in / Cash'}</td>
                        <td className="p-3 text-right font-medium">{etb(tx.totalAmount)}</td>
                        <td className="p-3 text-right text-gray-600 dark:text-gray-400">{etb(tx.taxableAmount)}</td>
                        <td className="p-3 text-gray-500">{tx.taxTreatment}</td>
                        <td className="p-3 text-right font-semibold text-blue-600">{etb(tx.taxAmount)}</td>
                        <td className="p-3">
                          <span className="px-1.5 py-0.5 rounded text-[10px] bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
                            {tx.status}
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
      )}

      {/* Receipt totals from uploads */}
      {receiptTotals.count > 0 && (
        <div className="grid gap-4 sm:grid-cols-3">
          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <p className="text-xs text-gray-500">Total receipts revenue</p>
            <p className="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">{etb(receiptTotals.totalRevenue)}</p>
          </div>
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 shadow-sm dark:border-amber-800/50 dark:bg-amber-950/30">
            <p className="text-xs text-amber-700">Total VAT on receipts</p>
            <p className="mt-1 text-xl font-bold text-amber-700 dark:text-amber-300">{etb(receiptTotals.totalVat)}</p>
          </div>
          <div className="rounded-xl border border-blue-200 bg-blue-50 p-4 shadow-sm dark:border-blue-800/50 dark:bg-blue-950/30">
            <p className="text-xs text-blue-700">Average VAT per receipt</p>
            <p className="mt-1 text-xl font-bold text-blue-700 dark:text-blue-300">
              {etb(receiptTotals.count ? receiptTotals.totalVat / receiptTotals.count : 0)}
            </p>
          </div>
        </div>
      )}

      {/* VAT trend chart — from transactions */}
      {!summary.error && !summary.isLoading && summary.data?.trend && summary.data.trend.length > 0 && (
        <Card>
          <CardHeader>
            <h2 className="font-semibold">VAT trend — recorded transactions (6 months)</h2>
          </CardHeader>
          <CardContent>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={summary.data.trend}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="period" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} tickFormatter={v => etb(v)} width={90} />
                  <Tooltip formatter={(v: number) => etb(v)} />
                  <Legend />
                  <Bar dataKey="outputVat" name="Output VAT" fill="#2563eb" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="inputVat" name="Input VAT" fill="#10b981" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="netVat" name="Net VAT" fill="#f59e0b" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            {summary.data?.classificationNotice && (
              <p className="mt-4 text-xs text-amber-700 dark:text-amber-300">
                <AlertCircle size={11} className="mr-1 inline" />
                {summary.data.classificationNotice}
              </p>
            )}
          </CardContent>
        </Card>
      )}

      {/* Receipt upload */}
      <ReceiptUploadPanel onSaved={() => qc.invalidateQueries({ queryKey: ['tax-receipts'] })} />

      {/* Saved receipts */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <ReceiptText size={18} className="text-emerald-600" />
            <h2 className="font-semibold">Saved Receipts</h2>
            {receipts.data && (
              <span className="ml-auto text-xs text-gray-400">{receipts.data.length} receipt{receipts.data.length !== 1 ? 's' : ''}</span>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {receipts.isLoading ? (
            <p className="text-sm text-gray-400">Loading receipts…</p>
          ) : receipts.error ? (
            <p className="text-sm text-red-500">Unable to load receipts. Backend may be unavailable.</p>
          ) : (
            <ReceiptsTable
              receipts={receipts.data ?? []}
              onDelete={handleDeleteReceipt}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
