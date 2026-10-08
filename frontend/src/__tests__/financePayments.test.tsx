/**
 * The Finance pages must show what the API actually holds.
 *
 * Reported state: Payments, Receivables and Payables all rendered empty or
 * ETB 0.00 while every call returned 200. Two distinct causes, and only one of
 * them was a data problem:
 *
 * 1. **Payments never asked.** `const payments = useMemo(() => [], [])` — a
 *    hardcoded empty array. `usePayments()` existed, `paymentsApi` existed, the
 *    endpoint existed and returned rows. The page reported "No records found"
 *    over a populated payments table. A stubbed-out page and an empty database
 *    look identical from the browser, which is what made this hard to spot.
 *
 * 2. **Receivables and Payables read a column nothing writes.**
 *    `Customer.outstandingBalance` and `Supplier.outstandingBalance` are stored
 *    columns defaulting to zero, and `setOutstandingBalance` appears nowhere in
 *    the codebase. Reading them returns 0 forever, so the debtor and creditor
 *    lists could never contain a row — whatever the sales or purchases said.
 *
 * These tests pin the behaviour to real rows rather than to the absence of data.
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import PaymentsPage from '@/app/(dashboard)/finance/payments/page';
import { usePayments } from '@/hooks/useFinance';

jest.mock('@/hooks/useFinance');
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));

const mockedUsePayments = usePayments as jest.MockedFunction<typeof usePayments>;

/** One row shaped exactly as GET /payments returns it. */
function apiPayment(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    reference: 'PAY-1791123285823-1',
    orderReference: 'SO-000001',
    type: 'IN',
    amount: 138000.0,
    paymentMethod: 'CASH',
    status: 'COMPLETED',
    createdBy: '12yemom@gmail.com',
    createdAt: '2026-10-04T14:14:45.831103',
    ...overrides,
  };
}

function renderWith(rows: unknown[], overrides: Record<string, unknown> = {}) {
  mockedUsePayments.mockReturnValue({
    data: rows,
    isLoading: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as any);
  return render(<PaymentsPage />);
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('Payments page — reads the API instead of a hardcoded empty array', () => {
  it('renders a payment that exists', async () => {
    renderWith([apiPayment()]);

    expect(await screen.findByText('PAY-1791123285823-1')).toBeInTheDocument();
    expect(screen.queryByText('No records found')).not.toBeInTheDocument();
  });

  it('renders every row it is given', async () => {
    renderWith([
      apiPayment({ id: 1, reference: 'PAY-1' }),
      apiPayment({ id: 2, reference: 'PAY-2' }),
      apiPayment({ id: 3, reference: 'PAY-3' }),
    ]);

    expect(await screen.findByText('PAY-1')).toBeInTheDocument();
    expect(screen.getByText('PAY-2')).toBeInTheDocument();
    expect(screen.getByText('PAY-3')).toBeInTheDocument();
  });

  it('labels an IN payment as received', async () => {
    renderWith([apiPayment({ type: 'IN' })]);

    await screen.findByText('PAY-1791123285823-1');
    expect(screen.getByText('Received (In)')).toBeInTheDocument();
  });

  it('labels an OUT payment as made', async () => {
    renderWith([apiPayment({ type: 'OUT' })]);

    await screen.findByText('PAY-1791123285823-1');
    expect(screen.getByText('Made (Out)')).toBeInTheDocument();
  });

  it('shows the amount and the method', async () => {
    renderWith([apiPayment({ amount: 138000, paymentMethod: 'BANK' })]);

    await screen.findByText('PAY-1791123285823-1');
    // Scoped to the table: the same figure also appears in the Total Received
    // card, so an unscoped query matches twice and fails for the wrong reason.
    const table = screen.getByRole('table');
    expect(within(table).getByText(/138,000/)).toBeInTheDocument();
    // `capitalize` is a CSS transform, so the text content is still upper case.
    expect(within(table).getByText('BANK')).toBeInTheDocument();
  });

  it('falls back to the order it settles for the counterparty', async () => {
    renderWith([apiPayment({ orderReference: 'SO-000042' })]);

    await screen.findByText('PAY-1791123285823-1');
    // Shown once. It previously appeared under both Reference and Party.
    expect(screen.getAllByText('SO-000042')).toHaveLength(1);
  });

  it('shows the totals rather than only a table', async () => {
    renderWith([
      apiPayment({ id: 1, type: 'IN', amount: 1000 }),
      apiPayment({ id: 2, type: 'OUT', amount: 250 }),
    ]);

    expect(await screen.findByText('Total Received')).toBeInTheDocument();
    expect(screen.getByText('Total Paid Out')).toBeInTheDocument();
    expect(screen.getByText('Net Movement')).toBeInTheDocument();

    // 1000 received - 250 paid = 750. Only a summed total produces that figure,
    // so it cannot pass by accident on an individual row.
    expect(screen.getByText(/750/)).toBeInTheDocument();

    // 1,000 and 250 each appear twice — once in a card, once in a row — so they
    // are counted rather than asserted once. The net movement is the assertion
    // that matters: it can only come from summing both.
    expect(screen.getAllByText(/1,000/).length).toBe(2);
    expect(screen.getAllByText(/250/).length).toBe(2);
  });

  it('really does say "no records" when there are genuinely none', async () => {
    // The empty state must survive the fix: it is only meaningful when the API
    // was actually consulted.
    renderWith([]);

    expect(await screen.findByText('No records found')).toBeInTheDocument();
  });

  it('shows an error when the API fails, rather than an empty table', async () => {
    mockedUsePayments.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Backend unavailable'),
      refetch: jest.fn(),
    } as any);

    render(<PaymentsPage />);

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('No records found')).not.toBeInTheDocument();
  });

  it('does not crash on a payment with no method', async () => {
    renderWith([apiPayment({ paymentMethod: undefined, amount: undefined })]);

    await screen.findByText('PAY-1791123285823-1');
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
  });

  it('renders nothing but the shell while loading, without claiming there are no records', async () => {
    renderWith([], { isLoading: true });

    await waitFor(() => {
      expect(screen.queryByText('No records found')).not.toBeInTheDocument();
    });
  });
});

describe('the empty state is distinguishable from a broken page', () => {
  it('an error and an empty list say different things', async () => {
    mockedUsePayments.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as any);
    const { unmount } = render(<PaymentsPage />);
    expect(await screen.findByText('No records found')).toBeInTheDocument();
    const emptyMessage = screen.getByText('No records found').textContent;
    unmount();

    mockedUsePayments.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('nope'),
      refetch: jest.fn(),
    } as any);
    render(<PaymentsPage />);
    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('No records found')).not.toBeInTheDocument();
    expect(emptyMessage).not.toBe('Something went wrong');
  });
});

describe('cash totals respect settlement status', () => {
  it('excludes a PENDING payment from Total Received', async () => {
    // A credit sale records a payment with type IN and status PENDING: money
    // owed, not money in hand. Counting it inflated cash on hand by the full
    // amount of every outstanding invoice.
    renderWith([
      apiPayment({ id: 1, type: 'IN', amount: 1000, status: 'COMPLETED' }),
      apiPayment({ id: 2, type: 'IN', amount: 750, status: 'PENDING' }),
    ]);

    expect(await screen.findByText('Total Received')).toBeInTheDocument();

    // 1000 settled, not 1750.
    const totals = screen.getByText('Total Received').closest('div')?.parentElement;
    expect(totals?.textContent).toContain('1,000.00');
    expect(totals?.textContent).not.toContain('1,750.00');
  });

  it('reports a pending payment as Pending Collection', async () => {
    renderWith([apiPayment({ id: 2, type: 'IN', amount: 750, status: 'PENDING' })]);

    expect(await screen.findByText('Pending Collection')).toBeInTheDocument();
    const card = screen.getByText('Pending Collection').closest('div')?.parentElement;
    expect(card?.textContent).toContain('750.00');
  });

  it('treats an absent status as settled, not as outstanding', async () => {
    // Older rows predate the status field and were genuinely taken; assuming
    // otherwise would understate every historical total.
    renderWith([apiPayment({ id: 1, type: 'IN', amount: 500, status: undefined })]);

    await screen.findByText('Total Received');
    const card = screen.getByText('Total Received').closest('div')?.parentElement;
    expect(card?.textContent).toContain('500.00');
  });

  it('still shows a pending payment as a row', async () => {
    // Excluding it from a total must not hide it from the ledger.
    renderWith([apiPayment({ id: 2, type: 'IN', amount: 750, status: 'PENDING' })]);

    await screen.findByText('PAY-1791123285823-1');
    expect(screen.getByText('PENDING')).toBeInTheDocument();
  });

  it('counts an OUT payment only when settled', async () => {
    renderWith([
      apiPayment({ id: 1, type: 'OUT', amount: 300, status: 'COMPLETED' }),
      apiPayment({ id: 2, type: 'OUT', amount: 900, status: 'PENDING' }),
    ]);

    await screen.findByText('Total Paid Out');
    const card = screen.getByText('Total Paid Out').closest('div')?.parentElement;
    expect(card?.textContent).toContain('300.00');
    expect(card?.textContent).not.toContain('1,200.00');
  });
});

describe('the payments table is rendered inside a real table', () => {
  it('headers are present once rows exist', async () => {
    renderWith([apiPayment()]);
    await screen.findByText('PAY-1791123285823-1');

    const table = screen.getByRole('table');
    const headers = within(table).getAllByRole('columnheader').map(h => h.textContent);
    expect(headers).toEqual(
      expect.arrayContaining(['Date', 'Reference', 'Type', 'Party', 'Method', 'Amount']),
    );
  });
});
