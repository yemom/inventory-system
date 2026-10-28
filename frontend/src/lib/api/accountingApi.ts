import apiClient from './apiClient';

export interface Account {
  id: number;
  code: string;
  name: string;
  category: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';
  subCategory?: string;
  description?: string;
  active: boolean;
  systemAccount: boolean;
  balance: number;
}

export interface JournalLine {
  id: number;
  accountId: number;
  accountCode: string;
  accountName: string;
  debit: number;
  credit: number;
  description?: string;
  lineNumber: number;
}

export interface JournalEntry {
  id: number;
  entryNumber: string;
  entryDate: string;
  accountingPeriod: string;
  reference?: string;
  description: string;
  sourceType: string;
  sourceId?: string;
  status: 'DRAFT' | 'POSTED' | 'REVERSED';
  totalDebit: number;
  totalCredit: number;
  postedAt?: string;
  postedByName?: string;
  reversedAt?: string;
  reversedByName?: string;
  reversalEntryId?: number;
  reversalReason?: string;
  lines: JournalLine[];
}

export interface CreateJournalLineRequest {
  accountId?: number;
  accountCode?: string;
  debit: number;
  credit: number;
  description?: string;
}

export interface CreateJournalEntryRequest {
  entryDate: string;
  reference?: string;
  description: string;
  sourceType?: string;
  sourceId?: string;
  lines: CreateJournalLineRequest[];
}

export interface LedgerTransaction {
  date: string;
  entryId: number;
  entryNumber: string;
  reference?: string;
  description: string;
  debit: number;
  credit: number;
  runningBalance: number;
}

export interface GeneralLedgerReport {
  account: Account;
  periodStart: string;
  periodEnd: string;
  openingBalance: number;
  transactions: LedgerTransaction[];
  totalDebit: number;
  totalCredit: number;
  closingBalance: number;
}

export interface TrialBalanceItem {
  accountCode: string;
  accountName: string;
  category: string;
  debitBalance: number;
  creditBalance: number;
}

export interface TrialBalanceReport {
  asOfDate: string;
  items: TrialBalanceItem[];
  totalDebit: number;
  totalCredit: number;
  balanced: boolean;
  discrepancy: number;
}

export interface StatementItem {
  code: string;
  name: string;
  subCategory?: string;
  amount: number;
}

export interface ProfitLossReport {
  periodStart: string;
  periodEnd: string;
  salesRevenue: number;
  salesReturnsAndDiscounts: number;
  netSales: number;
  costOfGoodsSold: number;
  grossProfit: number;
  grossMarginPercent: number;
  operatingExpenseItems: StatementItem[];
  totalOperatingExpenses: number;
  payrollExpenses: number;
  netProfitOrLoss: number;
  basis: string;
}

export interface BalanceSheetReport {
  asOfDate: string;
  currentAssets: StatementItem[];
  nonCurrentAssets: StatementItem[];
  totalAssets: number;
  currentLiabilities: StatementItem[];
  nonCurrentLiabilities: StatementItem[];
  totalLiabilities: number;
  equityItems: StatementItem[];
  retainedEarnings: number;
  totalEquity: number;
  totalLiabilitiesAndEquity: number;
  balanced: boolean;
  discrepancy: number;
  basis: string;
}

export interface ReconciliationItem {
  category: string;
  reference: string;
  description: string;
  expectedAmount: number;
  actualAmount: number;
  variance: number;
  status: 'RECONCILED' | 'DISCREPANCY' | 'PENDING_DISBURSEMENT' | 'INVESTIGATE';
  notes?: string;
}

export interface ReconciliationReport {
  asOfDate: string;
  items: ReconciliationItem[];
  totalChecked: number;
  discrepanciesCount: number;
  totalDiscrepancyAmount: number;
  reviewStatus: string;
}

export interface AiAccountingInsight {
  title: string;
  summary: string;
  keyTrends: string[];
  anomalies: string[];
  recommendations: string[];
  period: string;
  timestamp: string;
}

const data = <T>(response: { data: { data: T } }) => response.data.data;

export const accountingApi = {
  accounts: async (): Promise<Account[]> =>
    data<Account[]>(await apiClient.get('/accounting/accounts')),

  createAccount: async (body: {
    code: string;
    name: string;
    category: string;
    subCategory?: string;
    description?: string;
  }): Promise<Account> =>
    data<Account>(await apiClient.post('/accounting/accounts', body)),

  updateAccount: async (id: number, body: {
    name: string;
    subCategory?: string;
    description?: string;
    active?: boolean;
  }): Promise<Account> =>
    data<Account>(await apiClient.put(`/accounting/accounts/${id}`, body)),

  deleteAccount: async (id: number): Promise<void> => {
    await apiClient.delete(`/accounting/accounts/${id}`);
  },

  journalEntries: async (params?: {
    status?: string;
    period?: string;
    from?: string;
    to?: string;
  }): Promise<JournalEntry[]> =>
    data<JournalEntry[]>(await apiClient.get('/accounting/journal-entries', { params })),

  journalEntry: async (id: number): Promise<JournalEntry> =>
    data<JournalEntry>(await apiClient.get(`/accounting/journal-entries/${id}`)),

  createJournalEntry: async (body: CreateJournalEntryRequest): Promise<JournalEntry> =>
    data<JournalEntry>(await apiClient.post('/accounting/journal-entries', body)),

  postJournalEntry: async (id: number): Promise<JournalEntry> =>
    data<JournalEntry>(await apiClient.post(`/accounting/journal-entries/${id}/post`)),

  reverseJournalEntry: async (id: number, reason: string): Promise<JournalEntry> =>
    data<JournalEntry>(await apiClient.post(`/accounting/journal-entries/${id}/reverse`, { reason })),

  sync: async (): Promise<number> =>
    data<number>(await apiClient.post('/accounting/sync')),

  ledger: async (accountId: number, from?: string, to?: string): Promise<GeneralLedgerReport> =>
    data<GeneralLedgerReport>(await apiClient.get('/accounting/ledger', {
      params: { accountId, from, to },
    })),

  trialBalance: async (asOf?: string): Promise<TrialBalanceReport> =>
    data<TrialBalanceReport>(await apiClient.get('/accounting/trial-balance', {
      params: asOf ? { asOf } : {},
    })),

  profitLoss: async (from?: string, to?: string): Promise<ProfitLossReport> =>
    data<ProfitLossReport>(await apiClient.get('/accounting/profit-loss', {
      params: { from, to },
    })),

  balanceSheet: async (asOf?: string): Promise<BalanceSheetReport> =>
    data<BalanceSheetReport>(await apiClient.get('/accounting/balance-sheet', {
      params: asOf ? { asOf } : {},
    })),

  reconciliation: async (asOf?: string): Promise<ReconciliationReport> =>
    data<ReconciliationReport>(await apiClient.get('/accounting/reconciliation', {
      params: asOf ? { asOf } : {},
    })),

  aiInsights: async (from?: string, to?: string): Promise<AiAccountingInsight> =>
    data<AiAccountingInsight>(await apiClient.get('/accounting/ai-insights', {
      params: { from, to },
    })),
};
