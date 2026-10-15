/**
 * The order table has one job beyond showing rows: it must not overstate what it
 * has, and it must not understate what the caller is allowed to see.
 *
 * 1. **Scope is the server's.** The cashier column appears only for a caller
 *    holding SALES_REPORT_VIEW, and the header states which view is in play. If
 *    the header said "every order in the business" to a cashier, the rows could
 *    be right and the claim about them still false.
 * 2. **A missing field is shown as missing.** `branch` is free text on the user
 *    and genuinely can be absent; an order with no branch must render a dash,
 *    not an invented name.
 * 3. **Order status and payment status are not the same fact.** An order can be
 *    PENDING while its payment has cleared, and collapsing those hides the case
 *    a supervisor is looking for.
 * 4. **Empty is empty.** A filter matching nothing says so and offers a way out,
 *    rather than rendering an empty table that looks broken.
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SalesOrderTable from '@/components/dashboard/SalesOrderTable';
import { salesApi } from '@/lib/api/salesApi';
import { useAuth } from '@/lib/auth/AuthProvider';
import { formatCurrency } from '@/lib/utils';

jest.mock('@/lib/api/salesApi');
jest.mock('@/lib/auth/AuthProvider');
jest.mock('next/link', () => ({
  __esModule: true,
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const mockedList = salesApi.page as jest.MockedFunction<typeof salesApi.page>;
const mockedAuth = useAuth as jest.MockedFunction<typeof useAuth>;

/**
 * The table reads its orders through react-query, so it needs a real provider.
 * Retries are off so a deliberately rejected request surfaces as the error
 * state instead of being retried into a passing test.
 */
function renderTable() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <SalesOrderTable />
    </QueryClientProvider>,
  );
}

function grantPermissions(permissions: string[]) {
  mockedAuth.mockReturnValue({
    user: {
      id: 7,
      username: 'cashier',
      email: 'c@x.com',
      fullName: 'Eyas Sentayew',
      role: 'CASHIER',
      permissions,
    },
    hasPermission: (p: string) => permissions.includes(p),
    login: jest.fn(),
    logout: jest.fn(),
    isLoading: false,
  });
}

function pageOf(orders: unknown[], totalElements = orders.length, totalPages = 1) {
  return {
    content: orders,
    totalElements,
    totalPages,
    number: 0,
    size: 8,
  } as Awaited<ReturnType<typeof salesApi.page>>;
}

/** A complete order as the API sends it. */
function order(overrides: Record<string, unknown> = {}) {
  return {
    id: 13,
    orderNumber: 'SO-000013',
    customerName: 'Almaz Trading',
    total: 500,
    status: 'PAID',
    paymentStatus: 'PAID',
    paymentMethod: 'CASH',
    createdAt: '2026-10-08T08:10:15',
    cashier: { id: 7, name: 'Eyas Sentayew' },
    branch: { id: 2, name: 'Main Street' },
    items: [{ id: 1 }, { id: 2 }],
    ...overrides,
  };
}

describe('SalesOrderTable', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedList.mockResolvedValue(pageOf([order()]));
  });

  it('shows real order fields for a caller who may see everyone', async () => {
    grantPermissions(['SALE_READ', 'SALES_REPORT_VIEW']);

    renderTable();

    expect(await screen.findByText('SO-000013')).toBeInTheDocument();
    expect(screen.getByText('Almaz Trading')).toBeInTheDocument();
    expect(screen.getByText('Main Street')).toBeInTheDocument();
    // The total is formatted with a non-breaking space, so it is matched by
    // pattern: pinning Intl's exact spacing would fail on a locale change
    // without anything about the component having changed.
    expect(screen.getByText(/500\.00/)).toBeInTheDocument();
    expect(screen.getByText('Eyas Sentayew')).toBeInTheDocument();
    expect(screen.getByText(/Every order in the business/)).toBeInTheDocument();
    // Item count comes from the lines actually on the order.
    expect(screen.getByRole('row', { name: /SO-000013/ })).toHaveTextContent('2');
  });

  it('hides other people\'s cashier column from a scoped caller', async () => {
    grantPermissions(['SALE_READ']);

    renderTable();

    await screen.findByText('SO-000013');

    // The backend already scoped the rows; the table must not imply the
    // viewer is looking at the whole business.
    expect(screen.getByText(/Only orders recorded by you/)).toBeInTheDocument();
    expect(screen.queryByText('Cashier')).not.toBeInTheDocument();
    expect(screen.queryByText('Eyas Sentayew')).not.toBeInTheDocument();
  });

  it('shows a dash rather than inventing a branch that was never recorded', async () => {
    grantPermissions(['SALE_READ', 'SALES_REPORT_VIEW']);
    mockedList.mockResolvedValue(
      pageOf([order({ branch: null, cashier: null, customerName: null })]),
    );

    renderTable();

    await screen.findByText('SO-000013');
    const row = screen.getByRole('row', { name: /SO-000013/ });
    // The row still renders completely: customer and cashier fall back to a dash
    // rather than a blank cell or an invented name. The absent branch simply
    // contributes no line, so the row is not padded with an empty element.
    expect(within(row).getAllByText('—')).toHaveLength(2);
    expect(within(row).getByText(/500\.00/)).toBeInTheDocument();
  });

  it('distinguishes a pending order from an unpaid one', async () => {
    grantPermissions(['SALE_READ']);
    mockedList.mockResolvedValue(
      pageOf([
        order({ id: 1, orderNumber: 'SO-1', status: 'PENDING', paymentStatus: 'UNPAID' }),
        order({ id: 2, orderNumber: 'SO-2', status: 'PENDING', paymentStatus: 'PAID' }),
      ]),
    );

    renderTable();

    await screen.findByText('SO-1');
    const unpaid = screen.getByRole('row', { name: /SO-1/ });
    const paidButPending = screen.getByRole('row', { name: /SO-2/ });

    expect(within(unpaid).getByText('Pending')).toBeInTheDocument();
    // Paid money, order not yet approved — reported as Paid, and the row's
    // own status text is what a supervisor checks.
    expect(within(paidButPending).getByText('Paid')).toBeInTheDocument();
  });

  it('sends search and status filters to the server rather than filtering locally', async () => {
    grantPermissions(['SALE_READ', 'SALES_REPORT_VIEW']);

    renderTable();
    await screen.findByText('SO-000013');

    mockedList.mockClear();
    await userEvent.type(screen.getByLabelText('Search orders'), 'SO-0000');

    // Debounced: one request for a typed word, not one per keystroke.
    await waitFor(() => expect(mockedList).toHaveBeenCalled(), { timeout: 3000 });
    await waitFor(
      () =>
        expect(
          mockedList.mock.calls.map((call) => call[0]?.search).filter(Boolean),
        ).toEqual(['SO-0000']),
      { timeout: 3000 },
    );

    await userEvent.selectOptions(screen.getByLabelText('Filter by payment status'), 'UNPAID');
    await waitFor(
      () => expect(mockedList).toHaveBeenCalledWith(
        expect.objectContaining({ paymentStatus: 'UNPAID' }),
      ),
      { timeout: 3000 },
    );
  });

  it('offers a way out of a filter that matched nothing', async () => {
    grantPermissions(['SALE_READ', 'SALES_REPORT_VIEW']);
    mockedList.mockResolvedValue(pageOf([]));

    renderTable();

    // With no filter applied, an empty result means there are genuinely no
    // orders — a different statement from "your filter matched nothing", and
    // the wording keeps them apart.
    expect(await screen.findByText('No orders match')).toBeInTheDocument();
    expect(screen.getByText('No orders have been recorded yet.')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Search orders'), 'SO-9999');
    await waitFor(
      () => expect(screen.getByText(/Clear them to see the full list/)).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('does not say "no orders" when the request itself failed', async () => {
    grantPermissions(['SALE_READ']);
    mockedList.mockRejectedValue({ response: { status: 500 } });

    renderTable();

    // An empty table here would be a lie: it would read as "nothing recorded".
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Orders could not be loaded',
    );
    expect(screen.queryByText('No orders match')).not.toBeInTheDocument();
  });

  it('pages only when there is more than one page', async () => {
    grantPermissions(['SALE_READ', 'SALES_REPORT_VIEW']);
    mockedList.mockResolvedValue(pageOf([order()], 34, 5));

    renderTable();

    expect(await screen.findByText('Page 1 of 5')).toBeInTheDocument();
    expect(screen.getByLabelText('Previous page of orders')).toBeDisabled();

    await userEvent.click(screen.getByLabelText('Next page of orders'));
    await waitFor(() => expect(mockedList).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1 }),
    ));
  });

  it('hides paging controls entirely when everything fits on one page', async () => {
    grantPermissions(['SALE_READ', 'SALES_REPORT_VIEW']);
    mockedList.mockResolvedValue(pageOf([order()], 1, 1));

    renderTable();

    await screen.findByText('SO-000013');
    expect(screen.queryByLabelText('Next page of orders')).not.toBeInTheDocument();
    expect(screen.getByText(/1 order$/)).toBeInTheDocument();
  });
});