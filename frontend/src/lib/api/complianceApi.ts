import apiClient from './apiClient';

export interface ComplianceFinding {
  id: string;
  title: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  category: 'TAX' | 'PAYROLL' | 'RECONCILIATION' | 'DEADLINE' | 'AUDIT';
  amount: number | null;
  affectedPeriod: string;
  explanation: string;
  sourceRecords: string;
  recommendedAction: string;
  humanApprovalRequired: boolean;
}

export interface ComplianceSummary {
  overallStatus: 'COMPLIANT' | 'ACTION_REQUIRED' | 'HIGH_RISK';
  complianceScore: number;
  outstandingTaxLiability: number;
  unpaidPayrollLiability: number;
  totalFindingsCount: number;
  highPriorityCount: number;
  nextDeadline: string;
  findings: ComplianceFinding[];
  taxFilingStatus: string;
  payrollStatus: string;
  evaluatedAt: string;
}

export interface AiFindingDetail {
  id: string;
  title: string;
  severity: string;
  category: string;
  explanation: string;
  evidence: string;
  numericalImpact: string;
  recommendedAction: string;
  humanApprovalRequired: boolean;
}

export interface ComplianceAiAnalysis {
  analysisTitle: string;
  executiveSummary: string;
  riskLevel: string;
  overallComplianceScore: number;
  totalFinancialExposure: number;
  findings: AiFindingDetail[];
  prioritizedActions: string[];
  providerUsed: string;
  period: string;
  timestamp: string;
}

export interface ComplianceAiAnswer {
  answer: string;
  evidenceSources: string[];
  providerUsed: string;
  timestamp: string;
}

const data = <T>(response: { data: { data: T } }) => response.data.data;

export const complianceApi = {
  summary: async (period?: string): Promise<ComplianceSummary> =>
    data<ComplianceSummary>(await apiClient.get('/compliance/summary', {
      params: period ? { period } : {},
    })),

  aiAnalyze: async (period?: string): Promise<ComplianceAiAnalysis> =>
    data<ComplianceAiAnalysis>(await apiClient.post('/compliance/ai-analyze', null, {
      params: period ? { period } : {},
    })),

  ask: async (question: string, period?: string): Promise<ComplianceAiAnswer> =>
    data<ComplianceAiAnswer>(await apiClient.post('/compliance/ask', {
      question,
      period,
    })),
};
