import apiClient from './apiClient';

export interface PayrollDashboard {
  grossPayroll: number;
  netPayroll: number;
  employmentTax: number;
  employeePension: number;
  employerPension: number;
  employerCost: number;
  activeProfiles: number;
  draftPeriods: number;
  finalizedPeriods?: number;
  paidPeriods?: number;
  totalPayrollPaid?: number;
  totalPayrollOutstanding?: number;
  currency: string;
  hasData?: boolean;
}

export interface PayrollItem {
  id: number;
  userId: number;
  employeeId: string;
  fullName: string;
  department: string;
  basicSalary: number;
  taxableIncome: number;
  incomeTax: number;
  employeePension: number;
  employerPension: number;
  otherDeductions: number;
  netPay: number;
  calculationBreakdown: string;
}

export interface PayrollPeriod {
  id: number;
  year: number;
  month: number;
  status: string; // DRAFT, FINALIZED, PAID
  grossPayroll: number;
  netPayroll: number;
  employmentTax: number;
  employeePension: number;
  employerPension: number;
  employerCost: number;
  ruleVersion: string;
  finalizedAt?: string;
  paidAt?: string;
  finalizedByName?: string;
  paidByName?: string;
  items: PayrollItem[];
}

export interface PayrollProfile {
  id: number;
  userId: number;
  employeeId: string;
  fullName: string;
  department: string;
  jobTitle: string;
  basicSalary: number;
  effectiveFrom: string;
  pensionEligible: boolean;
  active: boolean;
}

const data = <T>(response: { data: { data: T } }) => response.data.data;

export const payrollApi = {
  dashboard: async (): Promise<PayrollDashboard> =>
    data<PayrollDashboard>(await apiClient.get('/payroll/dashboard')),

  profiles: async (): Promise<PayrollProfile[]> =>
    data<PayrollProfile[]>(await apiClient.get('/payroll/profiles')),

  saveProfile: async (value: {
    userId: number;
    basicSalary: number;
    effectiveFrom: string;
    pensionEligible: boolean;
    bankAccountMasked?: string;
  }): Promise<PayrollProfile> =>
    data<PayrollProfile>(await apiClient.post('/payroll/profiles', value)),

  periods: async (): Promise<PayrollPeriod[]> =>
    data<PayrollPeriod[]>(await apiClient.get('/payroll/periods')),

  period: async (id: number): Promise<PayrollPeriod> =>
    data<PayrollPeriod>(await apiClient.get(`/payroll/periods/${id}`)),

  create: async (year: number, month: number): Promise<PayrollPeriod> =>
    data<PayrollPeriod>(await apiClient.post('/payroll/periods', { year, month })),

  finalize: async (id: number): Promise<PayrollPeriod> =>
    data<PayrollPeriod>(await apiClient.post(`/payroll/periods/${id}/finalize`)),

  payPeriod: async (id: number): Promise<PayrollPeriod> =>
    data<PayrollPeriod>(await apiClient.post(`/payroll/periods/${id}/pay`)),

  payslip: async (itemId: number): Promise<PayrollItem> =>
    data<PayrollItem>(await apiClient.get(`/payroll/items/${itemId}/payslip`)),
};
