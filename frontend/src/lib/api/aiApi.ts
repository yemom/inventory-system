import apiClient, { fetchAllPages } from './apiClient';

/**
 * Client for the intelligence service.
 *
 * <p>Every call here is read-only, and every financial figure arrives from the
 * backend's calculation service rather than being recomputed in the browser.
 * That is the whole point: the dashboard, the reports and the assistant all
 * read the same summary, so they cannot print different gross profit for the
 * same month.
 *
 * <p>The assistant is treated as a query, not as a chat: a question goes out, a
 * structured answer comes back with its date range, its assumptions and links
 * to the same reports. Nothing here interprets or rewrites a number.
 */

/** One forecast point with its prediction interval. */
export interface ForecastPoint {
  date: string;
  value: number;
  lower: number;
  upper: number;
}

/** A revenue forecast with the history it was derived from. */
export interface RevenueForecast {
  available: boolean;
  from: string;
  to: string;
  historyDates: string[] | null;
  historyValues: number[] | null;
  forecast: ForecastPoint[] | null;
  algorithm: string | null;
  mae: number | null;
  rmse: number | null;
  mapePercent: number | null;
  explanation: string | null;
  modelVersion: string | null;
  trendPercent: number | null;
  basis: string | null;
}

/** Demand for one product, or a refusal with the reason. */
export interface DemandForecast {
  productId: number;
  available: boolean;
  historyDates: string[] | null;
  historyValues: number[] | null;
  forecast: ForecastPoint[] | null;
  algorithm: string | null;
  mae: number | null;
  rmse: number | null;
  mapePercent: number | null;
  averageDailyUnits: number | null;
  coefficientOfVariation: number | null;
  trendPercent: number | null;
  explanation: string | null;
  observationCount: number;
}

export type InsightSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

/** One explanation, with the figures it was built from. */
export interface Insight {
  category: string;
  headline: string;
  message: string;
  severity: InsightSeverity;
  evidence: Record<string, unknown>;
  sourceUrl: string | null;
}

/** One investigation alert. Never a conclusion. */
export interface Anomaly {
  source: string;
  period: string;
  value: number;
  baseline: number;
  score: number;
  kind: string;
  summary: string;
  evidence: Record<string, unknown>;
}

/** Reorder advice, with the arithmetic that produced it. */
export interface ReorderRecommendation {
  productId: number;
  sku: string;
  name: string;
  hasSufficientHistory: boolean;
  stockOnHand: number;
  stockOnOrder: number;
  averageDailyUnits: number;
  leadTimeDays: number;
  safetyStock: number;
  reorderPoint: number;
  suggestedQuantity: number;
  daysOfStockRemaining: number | null;
  urgency: string;
  stockoutImminent: boolean;
  observationCount: number;
  coefficientOfVariation: number;
  evidence: Record<string, unknown>;
  authorityNote: string;
}

/** A product projected to run out. */
export interface StockoutRisk {
  productId: number;
  sku: string;
  name: string;
  stockOnHand: number;
  stockOnOrder: number;
  averageDailyUnits: number;
  daysOfStockRemaining: number;
  beforeLeadTime: boolean;
  summary: string;
  evidence: Record<string, unknown>;
}

/** Everything the AI dashboard needs, in one consistent snapshot. */
export interface AiDashboard {
  enabled: boolean;
  from: string;
  to: string;
  horizonDays: number;
  insights: Insight[];
  forecast: RevenueForecast | null;
  anomalies: Anomaly[];
  recommendations: ReorderRecommendation[];
  stockoutRisks: StockoutRisk[];
  models: Array<{
    modelKey: string;
    algorithm: string;
    version: number;
    sampleSize: number;
    mae: string;
    mapePercent: string;
    trainedTo: string;
  }>;
  disabled?: string;
}

/** An answer from the assistant, with its period and assumptions. */
export interface AssistantAnswer {
  question: string | null;
  intent: string | null;
  outcome:
    | 'ANSWERED'
    | 'INSUFFICIENT_DATA'
    | 'CLARIFICATION_NEEDED'
    | 'DENIED';
  answer: string;
  period: {
    from: string;
    to: string;
    label: string;
  } | null;
  data: Record<string, unknown>;
  sources: string[];
  assumptions: string[];
  note: string | null;
}

/** The dashboard's financial figures, all from the calculation service. */
export interface DashboardFinancial {
  from: string;
  to: string;
  totalRevenue: number;
  grossSales: number;
  netSales: number;
  totalPurchaseAmount: number;
  cogs: number;
  grossProfit: number;
  grossMarginPercent: number;
  operatingExpenses: number;
  netProfit: number;
  netMarginPercent: number;
  cashReceived: number;
  cashPaidToSuppliers: number;
  cashRefunded?: number;
  netCashFlow: number;
  accountsReceivable: number;
  accountsPayable: number;
  purchaseCostReceived?: number;
  purchaseCostOrderedNotReceived: number;
  inventoryValue: number;
  stockLossValue: number;
  stockLossUnits: number;
  salesCount: number;
  itemsSold: number;
  expenseCount?: number;
  paymentCount?: number;
  expensesByCategory?: Array<{ category: string; amount: number }>;
  previousNetSales: number;
  previousGrossProfit: number;
  previousNetProfit: number;
  previousCashReceived: number;
  basis: string;
  insufficientData: boolean;
}

/** A period summary, with the preceding period for comparison. */
export interface FinancialSummaryPayload {
  period: Record<string, unknown>;
  previousPeriod: Record<string, unknown>;
  comparison: Record<string, unknown>;
}

/** Query parameters shared by the date-filtered endpoints. */
export interface DateRangeParams {
  from?: string;
  to?: string;
}

const unwrap = <T,>(response: { data?: { data?: T } | T }): T => {
  const payload = response.data as { data?: T };
  return (payload?.data ?? (response.data as T)) as T;
};

export const aiApi = {
  /** One request for the whole AI dashboard, so the page is a single snapshot. */
  dashboard: async (params: DateRangeParams & { horizon?: number } = {}) => {
    const response = await apiClient.get('/ai/dashboard', { params });
    return unwrap<AiDashboard>(response);
  },

  insights: async (params: DateRangeParams = {}) => {
    const response = await apiClient.get('/ai/insights', { params });
    return unwrap<Insight[]>(response);
  },

  forecast: async (params: DateRangeParams & { horizon?: number } = {}) => {
    const response = await apiClient.get('/ai/forecast', { params });
    return unwrap<RevenueForecast>(response);
  },

  productForecast: async (productId: number | string, horizon = 14) => {
    const response = await apiClient.get(`/ai/forecast/product/${productId}`, {
      params: { horizon },
    });
    return unwrap<DemandForecast>(response);
  },

  productForecasts: async (horizon = 14, limit?: number) => {
    const response = await apiClient.get('/ai/forecast/products', {
      params: { horizon, limit },
    });
    return unwrap<DemandForecast[]>(response);
  },

  anomalies: async (params: DateRangeParams = {}) => {
    const response = await apiClient.get('/ai/anomalies', { params });
    return unwrap<Anomaly[]>(response);
  },

  reorder: async (limit?: number) => {
    const response = await apiClient.get('/ai/reorder', { params: { limit } });
    return unwrap<ReorderRecommendation[]>(response);
  },

  stockouts: async (horizonDays = 30) => {
    const response = await apiClient.get('/ai/stockouts', {
      params: { horizonDays },
    });
    return unwrap<StockoutRisk[]>(response);
  },

  slowMoving: async (lookbackDays = 60, limit?: number) => {
    const response = await apiClient.get('/ai/slow-moving', {
      params: { lookbackDays, limit },
    });
    return unwrap<unknown[]>(response);
  },

  overstocked: async (thresholdDays = 90, limit?: number) => {
    const response = await apiClient.get('/ai/overstocked', {
      params: { thresholdDays, limit },
    });
    return unwrap<unknown[]>(response);
  },

  warehouses: async (days = 30) => {
    const response = await apiClient.get('/ai/warehouses', { params: { days } });
    return unwrap<
      Array<{
        warehouseId: number;
        unitsOnHand: number;
        unitsIssued: number;
        unitsReceived: number;
        unitsTransferred: number;
        turnoverRatio: number;
        periodDays: number;
      }>
    >(response);
  },

  /**
   * Asks the assistant a question.
   *
   * <p>When a date range is given it is passed as epoch milliseconds so the
   * assistant uses the dashboard's filter rather than re-reading "last month"
   * out of the question and potentially meaning a different window.
   */
  ask: async (
    question: string,
    range?: { from: string; to: string },
  ): Promise<AssistantAnswer> => {
    const body: Record<string, string> = { question };
    if (range) {
      body.from = String(Date.parse(range.from));
      body.to = String(Date.parse(range.to));
    }
    const response = await apiClient.post('/ai/assistant', body);
    return unwrap<AssistantAnswer>(response);
  },

  assistantCapabilities: async () => {
    const response = await apiClient.get('/ai/assistant/capabilities');
    return unwrap<string[]>(response);
  },

  /** Every trained model with the accuracy it was measured at. */
  models: async (modelKey?: string) => {
    const response = await apiClient.get('/ai/models', {
      params: modelKey ? { modelKey } : undefined,
    });
    return unwrap<Array<Record<string, unknown>>>(response);
  },

  /**
   * The assistant's query log.
   *
   * <p>Every question, what it resolved to, whether it was answered, and how
   * long it took. Kept so a figure the assistant quoted can be traced back to
   * the record it came from, and so repeated attempts to ask for something the
   * caller is not permitted to see are visible.
   */
  queryLog: async (page = 0, size = 25) => {
    const response = await apiClient.get('/ai/assistant/log', {
      params: { page, size },
    });
    const payload = unwrap<{
      content?: Array<Record<string, unknown>>;
      totalElements?: number;
      totalPages?: number;
      number?: number;
    }>(response);
    return {
      entries: payload?.content ?? [],
      total: payload?.totalElements ?? 0,
      totalPages: payload?.totalPages ?? 0,
      page: payload?.number ?? page,
    };
  },
};

export const financeApi = {
  /** The period summary, with the comparison the dashboard shows. */
  summary: async (params: DateRangeParams = {}) => {
    const response = await apiClient.get('/finance/summary', { params });
    return unwrap<FinancialSummaryPayload>(response);
  },

  cashFlow: async (params: DateRangeParams = {}) => {
    const response = await apiClient.get('/finance/cash-flow', { params });
    return unwrap<Record<string, unknown>>(response);
  },

  receivables: async (asOf?: string) => {
    const response = await apiClient.get('/finance/receivables', {
      params: asOf ? { asOf } : undefined,
    });
    return unwrap<Record<string, unknown>>(response);
  },

  /**
   * Supplier balances.
   *
   * <p>Served from the reports controller rather than a dedicated one: the
   * balances are derived from the same supplier ledger the payables report
   * reads, and a second endpoint computing them separately could disagree with
   * it.
   */
  payables: async (asOf?: string) => {
    const response = await apiClient.get('/reports/payables', {
      params: asOf ? { asOf } : undefined,
    });
    return unwrap<Record<string, unknown>>(response);
  },

  periods: async () => {
    const response = await apiClient.get('/finance/periods');
    return unwrap<string[]>(response);
  },
};

/**
 * Sales totals for the caller's dashboard.
 *
 * <p>The scope is decided by the server: `OWN_SALES` for anyone without
 * SALES_REPORT_VIEW, `ALL_SALES` for those who hold it. There is no parameter
 * to widen it, so a browser cannot ask for another cashier's takings.
 */
export interface SalesSummary {
  from: string;
  to: string;
  scope: 'OWN_SALES' | 'ALL_SALES';
  ordersCreated: number;
  completedSales: number;
  pendingOrders?: number;
  cancelledOrders: number;
  voidedOrders: number;
  grossSales: number;
  discounts: number;
  tax?: number;
  returns: number;
  netSales: number;
  amountCollected: number;
  outstanding: number;
  unitsSold: number;
  averageOrderValue: number;
  cashierName?: string | null;
  branch?: string | null;
  /** Net sales per payment method. */
  paymentMethods: Record<string, number>;
  /** Net sales per hour of the day, keyed "0".."23". */
  hourlySales: Record<string, number>;
  basis: string;
}

export interface HourlyDemandDistribution {
  hour: number;
  percentage: number;
  historicalAvgSales: number;
}

/**
 * Demand trend for one product, over the two most recent 14-day windows.
 *
 * <p>Names mirror `ForecastingService.ProductDemandTrend` exactly. They did
 * not before: the page read `productName`, `recent14DaysUnits`, `growthPercent`
 * and `trend`, none of which the backend ever sent. The names it did send are
 * `name`, `unitsSoldRecent`, `unitsSoldPrior`, `trendPercent` and `direction`.
 */
export interface ProductDemandTrend {
  productId: number | null;
  sku: string;
  name: string;
  unitsSoldRecent: number;
  unitsSoldPrior: number;
  trendPercent: number;
  direction: 'INCREASING' | 'DECREASING' | 'STABLE' | string;
}

/**
 * Plain-language readings of the forecast, produced by the AI layer.
 *
 * <p>Nullable as a whole: the backend omits the block entirely when it has no
 * provider configured and no deterministic text to fall back to. It was typed
 * as always-present, so the page dereferenced it unconditionally and threw
 * "Cannot read properties of undefined (reading 'baselineComparison')" — which
 * took the whole dashboard down rather than hiding one card.
 */
export interface OperationalAiExplanations {
  baselineComparison?: string | null;
  peakHoursExplanation?: string | null;
  productDemandExplanation?: string | null;
  operationalRecommendation?: string | null;
  decisionReadiness?: string | null;
  /** False when the text was produced deterministically, not by a provider. */
  aiEnriched?: boolean;
}

/** One day of the seven-day projection. */
export interface DailyForecastPoint {
  date: string;
  expectedSales: number;
  lowerBound: number;
  upperBound: number;
  expectedOrders: number;
}

/**
 * `GET /api/v1/dashboard/operational-forecast`.
 *
 * <p>Mirrors `ForecastingService.OperationalSalesForecast` field for field.
 * `available` false means there was not enough recorded trading to model at
 * all; the numbers are then present but meaningless and the UI must say so
 * rather than plot them.
 */
export interface OperationalSalesForecast {
  available: boolean;
  scope: 'OWN_SALES' | 'ALL_SALES' | string;
  cashierName: string | null;
  branch: string | null;
  asOfDate: string;
  generatedAt: string;
  algorithm: string | null;
  historyDays: number;
  observationCount: number;
  confidenceLevel: number | null;
  mae: number | null;
  rmse: number | null;
  mapePercent: number | null;
  isReliable: boolean;
  reliabilityExplanation: string | null;
  todaySalesSoFar: number;
  expectedRemainderToday: number;
  expectedTodayTotal: number;
  expectedNextDaySales: number;
  expected7DaySalesTotal: number;
  next7DaysDailyForecast: DailyForecastPoint[] | null;
  todayOrdersSoFar: number;
  expectedRemainderTodayOrders: number;
  expectedTodayTotalOrders: number;
  expectedNextDayOrders: number;
  next7DaysOrderForecast: number[] | null;
  currentAov: number | null;
  expectedAov: number | null;
  productTrends: ProductDemandTrend[] | null;
  /** Hours of the day as integers, not labels. */
  peakHours: number[] | null;
  peakHoursDescription: string | null;
  aiInsights: OperationalAiExplanations | null;
}

export const dashboardApi = {
  /** Operational counts. No revenue, no cost, no margin. */
  stats: async () => {
    const response = await apiClient.get('/dashboard/stats');
    return unwrap<Record<string, unknown>>(response);
  },

  /**
   * Sales for the period, scoped on the server to whoever is asking.
   *
   * <p>Orders are counted apart from completed sales, and cancelled or voided
   * orders are reported separately: an order existing is not the same as it
   * having been sold or paid for.
   */
  salesSummary: async (params: DateRangeParams & { warehouseId?: number } = {}) => {
    const response = await apiClient.get('/dashboard/sales-summary', { params });
    return unwrap<SalesSummary>(response);
  },

  /** The financial picture. Gated on FINANCIAL_REPORT_VIEW. */
  financial: async (params: DateRangeParams = {}) => {
    const response = await apiClient.get('/dashboard/financial', { params });
    return unwrap<DashboardFinancial>(response);
  },

  /** Operational sales forecasting with deterministic AI commentary. */
  operationalForecast: async (params?: { warehouseId?: number; cashierId?: number }) => {
    const response = await apiClient.get('/dashboard/operational-forecast', { params });
    return unwrap<OperationalSalesForecast>(response);
  },

  /** Insights and alerts for the dashboard. */
  insights: async (params: DateRangeParams = {}) => {
    const response = await apiClient.get('/dashboard/insights', { params });
    return unwrap<{
      insights: Insight[];
      anomalies: Anomaly[];
      recommendations: ReorderRecommendation[];
      stockoutRisks: StockoutRisk[];
    }>(response);
  },
};

export const reportsApi = {
  /** Every report, fetched by name, with the filters it supports. */
  get: async (
    report:
      | 'sales-detail'
      | 'purchases-detail'
      | 'income-expenses'
      | 'cash-flow'
      | 'profit-loss-detail'
      | 'product-profitability'
      | 'inventory-valuation'
      | 'inventory'
      | 'movements'
      | 'transfers'
      | 'shrinkage'
      | 'slow-moving'
      | 'receivables'
      | 'payables',
    params: Record<string, unknown> = {},
  ) => {
    const response = await apiClient.get(`/reports/${report}`, { params });
    return unwrap<Record<string, any>>(response);
  },

  /**
   * A CSV or PDF export of a report.
   *
   * <p>Built from the same endpoint that serves the screen, so an export can
   * never contain a figure the screen did not show. Returns the blob and the
   * filename the server chose.
   */
  export: async (
    report: string,
    format: 'csv' | 'pdf' = 'csv',
    params: Record<string, unknown> = {},
  ): Promise<{ blob: Blob; filename: string }> => {
    const response = await apiClient.get('/reports/export', {
      params: { report, format, ...params },
      responseType: 'blob',
    });

    const disposition = String(response.headers['content-disposition'] ?? '');
    const match = disposition.match(/filename="?([^";]+)"?/);
    const filename =
      match?.[1] ??
      `${report}-${new Date().toISOString().slice(0, 10)}.${format}`;

    return { blob: response.data as Blob, filename };
  },

  /** Saves an exported file, triggering a browser download. */
  download: async (
    report: string,
    format: 'csv' | 'pdf' = 'csv',
    params: Record<string, unknown> = {},
  ): Promise<void> => {
    const { blob, filename } = await reportsApi.export(report, format, params);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    // Revoked after the click rather than immediately: revoking synchronously
    // can cancel the download in some browsers before it has started.
    window.setTimeout(() => window.URL.revokeObjectURL(url), 1000);
  },
};

export const returnsApi = {
  /**
   * Returns, following pagination to the end.
   *
   * <p>A truncated list of returns would leave the screen showing a business
   * with fewer returns than it has, and a return that only exists on a page that
   * was not fetched is a return nobody is tracking.
   */
  list: async (params: Record<string, unknown> = {}): Promise<any[]> =>
    fetchAllPages('/returns', { params }),

  salesReturns: async (saleOrderId: number | string, lines: Array<{ itemId: number; quantity: number }>, reason: string, notes?: string) => {
    const response = await apiClient.post(`/returns/sales/${saleOrderId}`, {
      lines,
      reason,
      notes,
    });
    return unwrap<any>(response);
  },
  purchaseReturns: async (purchaseOrderId: number | string, lines: Array<{ itemId: number; quantity: number }>, reason: string, notes?: string) => {
    const response = await apiClient.post(`/returns/purchases/${purchaseOrderId}`, {
      lines,
      reason,
      notes,
    });
    return unwrap<any>(response);
  },
  approve: async (id: number | string, note?: string) => {
    const response = await apiClient.post(`/returns/${id}/approve`, { note });
    return unwrap<any>(response);
  },
  reject: async (id: number | string, note?: string) => {
    const response = await apiClient.post(`/returns/${id}/reject`, { note });
    return unwrap<any>(response);
  },
  pending: async (): Promise<any[]> => fetchAllPages('/returns/pending'),
};

export default aiApi;