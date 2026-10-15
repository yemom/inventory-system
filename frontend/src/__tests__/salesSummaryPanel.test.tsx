/**
 * The dashboard's sales panel must tell the truth about three separate things,
 * and must not quietly merge any of them.
 *
 * 1. **Scope.** The figures a cashier sees are their own, and the panel says so.
 *    If it said "across the business" to a cashier, the numbers would still be
 *    right but the claim about them would be false — which is how a person ends
 *    up believing they took the whole day's takings.
 * 2. **Order is not sale.** An order counted only as recorded, a cancelled order
 *    reported apart from revenue, and unpaid-but-invoiced money shown as
 *    outstanding rather than collected.
 * 3. **The empty state is honest.** No orders means the panel says there were
 *    none. It does not fall back to zeros that look like a failed request, and it
 *    does not offer a retry for a period that genuinely has nothing in it.
 *
 * The hook is mocked so the contract with the backend response is pinned here:
 * a real response for a real cashier is the only input this panel accepts, and
 * these tests fail if that shape drifts.
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SalesSummaryPanel from '@/components/dashboard/SalesSummaryPanel';
import { useSalesSummary } from '@/hooks/useAiDashboard';
import { SalesSummary } from '@/lib/api/aiApi';

/**
 * The figure in the "how these figures add up" row with this label.
 *
 * <p>Found through the row's own label rather than by scanning for a formatted
 * amount: several rows can carry the same number, and matching the row by its
 * caption is what makes the assertion about the relationship between them.
 */
function rowValue(label: string): string {
  const cell = screen.getByRole('rowheader', { name: label }).parentElement
    ?.querySelector('td');
  return cell?.textContent ?? '';
}

jest.mock('@/hooks/useAiDashboard');

const mockedUseSalesSummary = useSalesSummary as jest.MockedFunction<
  typeof useSalesSummary
>;

/** A response as the backend sends it to a cashier. */
function cashierSummary(overrides: Partial<SalesSummary> = {}): SalesSummary {
  return {
    from: '2026-10-15',
    to: '2026-10-15',
    scope: 'OWN_SALES',
    ordersCreated: 3,
    completedSales: 2,
    cancelledOrders: 1,
    voidedOrders: 0,
    grossSales: 750,
    discounts: 50,
    returns: 0,
    netSales: 700,
    amountCollected: 700,
    outstanding: 0,
    unitsSold: 5,
    averageOrderValue: 350,
    paymentMethods: { CASH: 500, CARD: 200 },
    hourlySales: { '9': 300, '14': 400 },
    basis:
      'Revenue net of discount and of returned units, from the orders themselves. ' +
      'Cancelled and voided orders are reported separately and are not counted as sales.',
    ...overrides,
  };
}

function renderPanel() {
  return render(<SalesSummaryPanel range={{ from: '2026-10-15', to: '2026-10-15' }} />);
}

describe('SalesSummaryPanel', () => {
  beforeEach(() => jest.clearAllMocks());

  it('tells a cashier the figures are their own orders only', async () => {
    mockedUseSalesSummary.mockReturnValue({
      data: cashierSummary(),
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useSalesSummary>);

    renderPanel();

    // The scope is the server's decision; the panel reports it rather than
    // implying it. "Across the business" here would be a false claim.
    expect(await screen.findByText('Sales taken by you')).toBeInTheDocument();
    expect(screen.getByText('Your orders only')).toBeInTheDocument();
    expect(screen.queryByText('All sellers')).not.toBeInTheDocument();
  });

  it('says when the caller is seeing every seller', async () => {
    mockedUseSalesSummary.mockReturnValue({
      data: cashierSummary({ scope: 'ALL_SALES' }),
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useSalesSummary>);

    renderPanel();

    expect(await screen.findByText('Sales across the business')).toBeInTheDocument();
    expect(screen.getByText('All sellers')).toBeInTheDocument();
  });

  it('keeps orders, completed sales, revenue and cash as separate figures', async () => {
    mockedUseSalesSummary.mockReturnValue({
      data: cashierSummary(),
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useSalesSummary>);

    renderPanel();

    // Three orders, two of them sales, one cancelled: merging any of these into
    // a single "orders" number is the mistake this panel exists to avoid.
    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(screen.getByText('2 completed')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument(); // cancelled orders card

    // Net sales is 750 - 50 discount - 0 returns, and the arithmetic is shown.
    // Each row is checked by its label rather than by the formatted number:
    // Intl separates the currency from the amount with a non-breaking space,
    // so asserting on the exact string would pin the formatter's whitespace
    // instead of the relationship between the figures, which is the thing
    // worth protecting.
    expect(screen.getByText('How these figures add up')).toBeInTheDocument();
    expect(rowValue('Before discount and returns')).toMatch(/750\.00$/);
    expect(rowValue('Discounts')).toMatch(/-ETB\s*50\.00$/);
    expect(rowValue('Returns')).toMatch(/-ETB\s*0\.00$/);
    expect(rowValue('Net sales')).toMatch(/700\.00$/);
  });

  it('reports money that was invoiced but not collected as outstanding', async () => {
    mockedUseSalesSummary.mockReturnValue({
      data: cashierSummary({
        netSales: 700,
        amountCollected: 500,
        outstanding: 200,
      }),
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useSalesSummary>);

    renderPanel();

    // Counting the invoice as cash is the specific error being guarded against.
    // The collected figure must be the payments, not the invoice: 500 here, not
    // the 700 invoiced.
    await waitFor(() =>
      expect(rowValue('Amount collected')).toMatch(/500\.00$/),
    );
    expect(screen.getByText(/still owing/)).toHaveTextContent(/200\.00/);
    expect(
      screen.getByText('Completed payments only — an unpaid order is not cash'),
    ).toBeInTheDocument();
  });

  it('shows an honest empty state rather than zeros for a day with no orders', async () => {
    mockedUseSalesSummary.mockReturnValue({
      data: cashierSummary({
        scope: 'OWN_SALES',
        ordersCreated: 0,
        completedSales: 0,
        cancelledOrders: 0,
        netSales: 0,
        amountCollected: 0,
        outstanding: 0,
        averageOrderValue: 0,
        paymentMethods: {},
        hourlySales: {},
      }),
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useSalesSummary>);

    renderPanel();

    expect(await screen.findByText('No orders in this period')).toBeInTheDocument();
    expect(
      screen.getByText(/You have not recorded any orders between these dates/),
    ).toBeInTheDocument();
    // Nothing that reads as a takings figure.
    expect(screen.queryByText('Net sales')).not.toBeInTheDocument();
  });

  it('offers a retry when the request fails, and says what is missing', async () => {
    const refetch = jest.fn();
    mockedUseSalesSummary.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: { response: { status: 503, data: { message: 'Service unavailable' } } },
      refetch,
    } as unknown as ReturnType<typeof useSalesSummary>);

    renderPanel();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Sales figures could not be loaded');

    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('names an unrecorded payment method rather than dropping it', async () => {
    mockedUseSalesSummary.mockReturnValue({
      data: cashierSummary({
        paymentMethods: { UNSPECIFIED: 700 },
      }),
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useSalesSummary>);

    renderPanel();

    // Silently omitting it would make the breakdown look complete when it is not.
    expect(await screen.findByText('Not recorded')).toBeInTheDocument();
  });

  it('shows the busiest hour from the data rather than assuming one', async () => {
    mockedUseSalesSummary.mockReturnValue({
      data: cashierSummary({ hourlySales: { '9': 100, '11': 900 } }),
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useSalesSummary>);

    renderPanel();

    await waitFor(() =>
      expect(screen.getByText(/busiest hour 11:00/)).toBeInTheDocument(),
    );
    expect(screen.getByText(/First order at 09:00/)).toBeInTheDocument();
  });
});