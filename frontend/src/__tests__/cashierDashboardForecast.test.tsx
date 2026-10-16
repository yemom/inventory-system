/**
 * The cashier dashboard must survive a forecast with no AI explanations.
 *
 * This is a real crash, caught in production: the page read
 * `forecast.aiExplanations.baselineComparison`, but the backend sends
 * `aiInsights` and omits the whole block when no AI provider is configured. The
 * property was typed as always present, so TypeScript agreed and the page threw
 * "Cannot read properties of undefined (reading 'baselineComparison')" — taking
 * the entire dashboard down, not just one card, at the moment a cashier opened
 * the app.
 *
 * The contract in `aiApi.ts` did not match `ForecastingService`. These tests
 * pin the field names to what the backend actually sends, so the mismatch cannot
 * come back silently, and pin the guard so a missing AI block hides one panel
 * rather than the whole page.
 */
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CashierDashboard from '@/components/dashboard/CashierDashboard';
import * as apiModule from '@/lib/api/aiApi';
import { useAuth } from '@/lib/auth/AuthProvider';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/dashboard',
}));
jest.mock('@/lib/api/aiApi');
jest.mock('@/lib/auth/AuthProvider', () => ({ useAuth: jest.fn() }));
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));
jest.mock('@/lib/api/warehousesApi', () => ({
  warehousesApi: { list: jest.fn().mockResolvedValue([]) },
}));

/*
 * jsdom gives every element a width of 0, and Recharts refuses to draw a
 * ResponsiveContainer with nothing to measure — which throws before the test can
 * assert anything. Rendered as a plain div instead: the chart internals are not
 * what these tests are about, the data contract feeding them is.
 */
jest.mock('recharts', () => {
  const actual = jest.requireActual('recharts');
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children?: React.ReactNode }) => (
      <div>{children}</div>
    ),
  };
});

const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockedDashboard = apiModule.aiApi.dashboard as jest.MockedFunction<
  typeof apiModule.aiApi.dashboard
>;
const mockedOperational =
  apiModule.dashboardApi.operationalForecast as jest.MockedFunction<
    typeof apiModule.dashboardApi.operationalForecast
  >;
const mockedSalesSummary = apiModule.dashboardApi.salesSummary as jest.MockedFunction<
  typeof apiModule.dashboardApi.salesSummary
>;

function asCashier() {
  mockedUseAuth.mockReturnValue({
    user: {
      id: 7,
      username: 'cashier',
      email: 'c@x.local',
      fullName: 'Eyas Sentayew',
      role: 'CASHIER',
      permissions: ['DASHBOARD_VIEW', 'SALE_READ'],
    },
    hasPermission: () => true,
    login: jest.fn(),
    logout: jest.fn(),
    isLoading: false,
  });
}

/**
 * A forecast exactly as the backend serialises it: `aiInsights`, not
 * `aiExplanations`, and every field named as the Java record names it.
 */
function backendForecast(overrides: Record<string, unknown> = {}) {
  return {
    available: true,
    scope: 'OWN_SALES',
    cashierName: 'Eyas Sentayew',
    branch: 'Main Street',
    asOfDate: '2026-10-16',
    generatedAt: '2026-10-16T09:00:00',
    algorithm: 'HOLT_LINEAR_EXP_SMOOTHING',
    historyDays: 30,
    observationCount: 15,
    confidenceLevel: 0.82,
    mae: null,
    rmse: null,
    mapePercent: null,
    isReliable: true,
    reliabilityExplanation: 'Enough recorded trading to model.',
    todaySalesSoFar: 8050,
    expectedRemainderToday: 12000,
    expectedTodayTotal: 20050,
    expectedNextDaySales: 21000,
    expected7DaySalesTotal: 140000,
    next7DaysDailyForecast: [
      { date: '2026-10-17', expectedSales: 21000, lowerBound: 18000, upperBound: 24000, expectedOrders: 3 },
    ],
    todayOrdersSoFar: 2,
    expectedRemainderTodayOrders: 1,
    expectedTodayTotalOrders: 3,
    expectedNextDayOrders: 3,
    next7DaysOrderForecast: [3, 3, 2, 3, 4, 2, 3],
    currentAov: 4025,
    expectedAov: 4200,
    productTrends: [
      {
        productId: 2,
        sku: 'DEL-INSP-002',
        name: 'Dell Inspiron',
        unitsSoldRecent: 12,
        unitsSoldPrior: 4,
        trendPercent: 200,
        direction: 'INCREASING',
      },
    ],
    peakHours: [9, 14],
    peakHoursDescription: 'Mornings and mid-afternoon trade hardest.',
    aiInsights: {
      baselineComparison: 'Today is running ahead of the recent baseline.',
      peakHoursExplanation: 'Mornings and mid-afternoon trade hardest.',
      productDemandExplanation: 'Dell Inspiron demand is rising.',
      operationalRecommendation: 'Stock the fast movers early.',
      decisionReadiness: 'Good enough to plan stock.',
      aiEnriched: false,
    },
    ...overrides,
  };
}

function summary(overrides: Record<string, unknown> = {}) {
  return {
    from: '2026-10-16',
    to: '2026-10-16',
    scope: 'OWN_SALES',
    ordersCreated: 2,
    completedSales: 2,
    cancelledOrders: 0,
    voidedOrders: 0,
    grossSales: 8050,
    discounts: 0,
    returns: 0,
    netSales: 8050,
    amountCollected: 8050,
    outstanding: 0,
    unitsSold: 3,
    averageOrderValue: 4025,
    paymentMethods: { CASH: 8050 },
    hourlySales: { '9': 5000, '14': 3050 },
    basis: 'Revenue net of discount and of returned units.',
    ...overrides,
  } as never;
}

function renderDashboard() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <CashierDashboard />
    </QueryClientProvider>,
  );
}

describe('CashierDashboard — forecast contract', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    asCashier();
    mockedSalesSummary.mockResolvedValue(summary());
    mockedDashboard.mockResolvedValue({
      forecast: null,
      insights: [],
      anomalies: [],
      recommendations: [],
      stockoutRisks: [],
      modelHistory: [],
    } as never);
  });

  it('renders the AI panel when the backend sends aiInsights', async () => {
    mockedOperational.mockResolvedValue(backendForecast() as never);

    renderDashboard();

    expect(await screen.findByText(/Today is running ahead/)).toBeInTheDocument();
    expect(screen.getByText(/Dell Inspiron demand is rising/)).toBeInTheDocument();
    // Name comes from `name`, the field the backend actually sends.
    expect(screen.getByText('Dell Inspiron')).toBeInTheDocument();
  });

  it('does not crash when the backend omits the AI block entirely', async () => {
    // The production crash: no provider configured, so no explanations at all.
    mockedOperational.mockResolvedValue(
      backendForecast({ aiInsights: null }) as never,
    );

    renderDashboard();

    // The page still renders, and says why the prose is missing rather than
    // pretending it is loading.
    expect(
      await screen.findByText(/No written analysis is available/),
    ).toBeInTheDocument();
  });

  it('reads the forecast fields the backend names, not the ones it did not send', async () => {
    mockedOperational.mockResolvedValue(backendForecast() as never);

    renderDashboard();

    // expectedRemainderToday, not todayExpectedRemainingNetSales.
    expect(await screen.findByText(/ETB 12,000/)).toBeInTheDocument();
    // historyDays, not historicalDaysSampled.
    expect(screen.getByText(/Historical: 30 days \(15 orders\)/)).toBeInTheDocument();
  });

  it('states that the text is deterministic when no provider answered', async () => {
    mockedOperational.mockResolvedValue(backendForecast() as never);

    renderDashboard();

    // aiEnriched false means these numbers are real but the wording was not
    // written by a model. Saying so is the difference between a forecast and
    // a claim about a forecast.
    expect(await screen.findByText(/deterministic — no AI provider configured/)).toBeInTheDocument();
  });
});