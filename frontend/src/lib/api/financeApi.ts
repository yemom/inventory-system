import apiClient from './apiClient';

export interface TaxSummarySection {
  taxableSales: number;
  outputVat: number;
  recoverableInputVat: number;
  netVatPayableOrCredit: number;
  taxPaymentsMade: number;
  outstandingTaxLiabilities: number;
  estimatedIncomeTax: number;
  currency: string;
}

export interface PayrollSummarySection {
  grossPayroll: number;
  employmentTaxWithheld: number;
  employeePension: number;
  employerPension: number;
  totalDeductions: number;
  netPayrollPayable: number;
  payrollPaid: number;
  payrollOutstanding: number;
  totalEmployerPayrollCost: number;
  currency: string;
  hasPayrollData: boolean;
}

export interface InventoryBusinessSection {
  inventoryValuation: number;
  costOfGoodsSold: number;
  netSales: number;
  salesReturns: number;
  purchaseTotals: number;
  recordedExpenses: number;
  grossProfit: number;
  netProfit: number;
  currency: string;
  accountingBasis: string;
}

export interface MonthlyVatPoint {
  month: string;
  outputVat: number;
  inputVat: number;
  netVat: number;
}

export interface TaxLiabilityPaymentPoint {
  month: string;
  liability: number;
  payment: number;
}

export interface PayrollComparisonPoint {
  month: string;
  grossPayroll: number;
  netPayroll: number;
  deductions: number;
}

export interface PensionTaxTrendPoint {
  month: string;
  incomeTax: number;
  employeePension: number;
  employerPension: number;
}

export interface EmployerCostPoint {
  month: string;
  grossSalary: number;
  employerPension: number;
  totalEmployerCost: number;
}

export interface SalesCogsProfitPoint {
  month: string;
  sales: number;
  cogs: number;
  grossProfit: number;
}

export interface LiabilityStatusPoint {
  category: string;
  draft: number;
  approved: number;
  paid: number;
  outstanding: number;
}

export interface InventoryVsSalesPoint {
  month: string;
  inventoryValue: number;
  sales: number;
  cogs: number;
}

export interface ChartSeriesSection {
  monthlyVat: MonthlyVatPoint[];
  taxLiabilitiesVsPayments: TaxLiabilityPaymentPoint[];
  payrollGrossVsNet: PayrollComparisonPoint[];
  employeeTaxPensionTrends: PensionTaxTrendPoint[];
  employerCostTrends: EmployerCostPoint[];
  salesCogsProfitTrends: SalesCogsProfitPoint[];
  liabilitiesByStatus: LiabilityStatusPoint[];
  inventoryVsSales: InventoryVsSalesPoint[];
}

export interface TaxPayrollFinanceDTO {
  taxSummary: TaxSummarySection;
  payrollSummary: PayrollSummarySection;
  inventorySummary: InventoryBusinessSection;
  charts: ChartSeriesSection;
}

export const financeApi = {
  getIntelligence: async (period?: string): Promise<TaxPayrollFinanceDTO> => {
    const params = period ? { period } : {};
    const res = await apiClient.get<{ data: TaxPayrollFinanceDTO }>('/finance/intelligence', { params });
    return res.data.data;
  },
};
