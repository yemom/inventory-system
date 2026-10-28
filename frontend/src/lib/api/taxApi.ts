import apiClient from './apiClient';

export interface TaxDeadline {
  taxType: string;
  dueDate: string;
  description: string;
  overdue: boolean;
}

export interface TaxSummary {
  periodStart: string;
  periodEnd: string;
  totalSales?: number;
  taxableSales?: number;
  zeroRatedSales?: number;
  exemptSales?: number;
  outputVat: number;
  eligibleInputVat: number;
  nonRecoverableInputVat?: number;
  estimatedNetVat: number;
  withholdingTax?: number;
  estimatedIncomeTax?: number;
  taxPaymentsMade?: number;
  outstandingLiabilities?: number;
  salesWithTaxData: number;
  purchasesWithTaxData: number;
  currency: string;
  calculationStatus?: string;
  classificationNotice: string;
  upcomingDeadlines?: TaxDeadline[];
  trend: { period: string; outputVat: number; inputVat: number; netVat: number }[];
}

export interface TaxRule {
  id?: number;
  code: string;
  name: string;
  type: string;
  rate: number;
  effectiveFrom: string;
  effectiveTo?: string;
  verificationStatus: string;
  sourceUrl?: string;
  notes?: string;
  active: boolean;
}

export interface TaxTransaction {
  transactionType: string;
  id: number;
  referenceNumber: string;
  transactionDate: string;
  partyName: string;
  totalAmount: number;
  taxableAmount: number;
  taxAmount: number;
  taxRate: number;
  taxTreatment: string;
  status: string;
  notes?: string;
}

export interface ReceiptTaxCalculation {
  receiptName?: string;
  treatment: string;
  taxInclusive: boolean;
  enteredAmount: number;
  taxableAmount: number;
  taxAmount: number;
  totalAmount: number;
  currency: string;
  ruleCode: string;
  notice: string;
}

export interface TaxReceipt {
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
  aiConfidence?: number;
  vendorName?: string;
  receiptDate?: string;
  notes?: string;
  accountantName?: string;
  createdAt: string;
  notice?: string;
}

const data = <T>(response: { data: { data: T } }) => response.data.data;

export const taxApi = {
  /** VAT summary from live transactions */
  summary: async (period?: string): Promise<TaxSummary> =>
    data<TaxSummary>(await apiClient.get('/tax/summary', {
      params: period ? { period } : {},
    })),

  /** All active tax rules */
  rules: async (): Promise<TaxRule[]> =>
    data<TaxRule[]>(await apiClient.get('/tax/rules')),

  /** Create or update a tax rule */
  saveRule: async (rule: TaxRule): Promise<TaxRule> =>
    data<TaxRule>(await apiClient.post('/tax/rules', rule)),

  /** Verify a tax rule */
  verifyRule: async (id: number): Promise<TaxRule> =>
    data<TaxRule>(await apiClient.post(`/tax/rules/${id}/verify`)),

  /** Contributing tax transactions */
  transactions: async (period?: string, type: string = 'ALL'): Promise<TaxTransaction[]> =>
    data<TaxTransaction[]>(await apiClient.get('/tax/transactions', {
      params: { period, type },
    })),

  /** Preview tax calculation without saving */
  calculateReceipt: async (value: {
    amount: number;
    treatment: string;
    taxInclusive: boolean;
    receiptName?: string;
  }): Promise<ReceiptTaxCalculation> =>
    data<ReceiptTaxCalculation>(await apiClient.post('/tax/calculate-receipt', value)),

  /** List all saved receipts */
  receipts: async (): Promise<TaxReceipt[]> =>
    data<TaxReceipt[]>(await apiClient.get('/tax/receipts')),

  /** Upload a receipt file and persist the tax calculation */
  uploadReceipt: async (formData: FormData): Promise<TaxReceipt> =>
    data<TaxReceipt>(
      await apiClient.post('/tax/receipts', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
    ),

  /** Delete a receipt */
  deleteReceipt: async (id: number): Promise<void> => {
    await apiClient.delete(`/tax/receipts/${id}`);
  },
};
