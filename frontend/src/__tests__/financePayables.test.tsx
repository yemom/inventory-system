/**
 * Payables page — balances come from the server, not from a browser-side sum.
 *
 * ## Why this file was rewritten
 *
 * The previous version pinned the page's own arithmetic: it passed a list of
 * suppliers and a list of purchase orders into the component and asserted that
 * the component subtracted `paid` from `totalAmount` and grouped the result by
 * supplier name. Those assertions encoded two real defects:
 *
 * 1. `paid` was not a field the purchases endpoint returned — `amountPaid` was.
 *    Any order that was not fully paid therefore looked fully paid and
 *    contributed nothing, so the creditor list was empty or wrong.
 * 2. The purchases list is paginated. A balance on page three was invisible, and
 *    the total was wrong by whatever had not loaded. Nothing on the page could
 *    reveal that.
 *
 * Both now live in `ReportService.payables`, which walks every order with a
 * balance, reads `amountPaid`, and includes only goods that have been received.
 * These tests pin that: the page renders what the report says, and the payment
 * it records is attached to a specific order rather than posted as a free-standing
 * row with an amount the client chose.
 *
 * The old behaviours are still asserted, but from the report's output — a fully
 * paid order produces no row, a cancelled order produces no row, and goods that
 * were ordered but never received are not a payable.
 */
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import PayablesPage from '@/app/(dashboard)/finance/payables/page';
import { reportsApi } from '@/lib/api/aiApi';
import { usePurchases } from '@/hooks/usePurchases';
import { useCreateSupplierPayment } from '@/hooks/useFinance';

jest.mock('@/lib/api/aiApi', () => ({
  reportsApi: { get: jest.fn() },
  aiApi: {},
  financeApi: {},
  dashboardApi: {},
  returnsApi: {},
}));

jest.mock('@/hooks/usePurchases');
jest.mock('@/hooks/useFinance', () => ({
  useCreateSupplierPayment: jest.fn(),
}));
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));

const mockedGet = reportsApi.get as jest.MockedFunction<typeof reportsApi.get>;
const mockedUsePurchases = usePurchases as jest.MockedFunction<typeof usePurchases>;
const mockedUseCreateSupplierPayment =
  useCreateSupplierPayment as jest.MockedFunction<typeof useCreateSupplierPayment>;

// ── Fixtures ─────────────────────────────────────────────────────────────────

/** One creditor row, shaped as ReportService.payables returns it. */
function payable(overrides: Record<string, unknown> = {}) {
  return {
    supplier: 'Acme Supplies',
    orderCount: 1,
    orderTotal: 50000,
    paid: 0,
    outstanding: 50000,
    oldestDueDate: '2026-11-01',
    overdue: false,
    orders: ['PO-000001 (50000, received RECEIVED)'],
    ...overrides,
  };
}

function report(rows: unknown[], totals: Record<string, unknown> = {}) {
  const summed = rows.reduce<number>(
    (sum, row) => sum + Number((row as any).outstanding ?? 0),
    0,
  );
  const overdue = rows
    .filter((r: any) => r.overdue)
    .reduce<number>((sum, row) => sum + Number((row as any).outstanding ?? 0), 0);

  return {
    report: 'Supplier payables',
    asOf: '2026-10-13',
    columns: ['supplier', 'orderCount', 'orderTotal', 'paid', 'outstanding'],
    rows,
    totals: {
      totalOutstanding: summed,
      overdueAmount: overdue,
      ...totals,
    },
  };
}

/** A purchase order as the purchases list returns it. */
function purchase(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    orderNumber: 'PO-000001',
    supplierName: 'Acme Supplies',
    status: 'RECEIVED',
    totalAmount: 50000,
    amountPaid: 0,
    ...overrides,
  };
}

function renderWith(rows: unknown[], purchases: unknown[] = []) {
  mockedGet.mockResolvedValue(report(rows) as any);
  mockedUsePurchases.mockReturnValue({
    data: purchases,
    isLoading: false,
    error: null,
    refetch: jest.fn(),
  } as any);

  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <PayablesPage />
    </QueryClientProvider>,
  );
}

/** Finds the stat card containing the given label, returns its container. */
function statCard(label: string) {
  return screen.getByText(label).closest('div')?.parentElement as HTMLElement;
}

beforeEach(() => {
  jest.clearAllMocks();
  mockedUseCreateSupplierPayment.mockReturnValue({
    mutateAsync: jest.fn().mockResolvedValue({}),
    isPending: false,
  } as any);
});

// ─────────────────────────────────────────────────────────────────────────────
// 1. The report is what the page renders
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — renders the balances the server computed', () => {
  it('shows a supplier the report says is owed money', async () => {
    renderWith([payable()]);

    expect(await screen.findByText('Acme Supplies')).toBeInTheDocument();
    expect(screen.queryByText('No records found')).not.toBeInTheDocument();
  });

  it('shows no row for a supplier whose balance is zero', async () => {
    // A fully paid order produces no row — not because the page subtracted the
    // payment, but because the report never emitted one.
    renderWith([]);

    await waitFor(() =>
      expect(screen.queryByText('Acme Supplies')).not.toBeInTheDocument(),
    );
    expect(await screen.findByText('No records found')).toBeInTheDocument();
  });

  it('sends the balance date the reader chose', async () => {
    renderWith([payable()]);
    await screen.findByText('Acme Supplies');

    expect(mockedGet).toHaveBeenCalledWith(
      'payables',
      expect.objectContaining({ asOf: expect.any(String) }),
    );
  });

  it('shows every creditor the report returned, not a page of them', async () => {
    renderWith([
      payable({ supplier: 'Alpha', outstanding: 15000, orders: [] }),
      payable({ supplier: 'Beta', outstanding: 30000, orders: [] }),
      payable({ supplier: 'Gamma', outstanding: 22000, orders: [] }),
    ]);

    expect(await screen.findByText('Alpha')).toBeInTheDocument();
    expect(screen.getByText('Beta')).toBeInTheDocument();
    expect(screen.getByText('Gamma')).toBeInTheDocument();
  });

  it('collapses several orders for one supplier into a single row', async () => {
    // The grouping is the server's job now: one row per supplier with the order
    // references listed beneath it.
    renderWith([
      payable({
        supplier: 'Mega Corp',
        orderCount: 2,
        orderTotal: 80000,
        paid: 30000,
        outstanding: 50000,
        orders: ['PO-000001 (20000, received RECEIVED)', 'PO-000002 (30000, received RECEIVED)'],
      }),
    ]);

    const rows = await screen.findAllByText('Mega Corp');
    expect(rows).toHaveLength(1);

    const table = screen.getByRole('table');
    expect(within(table).getByText(/50,000/)).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2. Stat cards
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — stat cards report the totals the server gave', () => {
  it('Total Owed equals the sum the report reported', async () => {
    renderWith([
      payable({ supplier: 'Alpha', outstanding: 15000, orders: [] }),
      payable({ supplier: 'Beta', outstanding: 30000, orders: [] }),
    ]);

    await screen.findByText('Total Owed');
    expect(within(statCard('Total Owed')).getByText(/45,000/)).toBeInTheDocument();
  });

  it('counts suppliers owing, and no others', async () => {
    renderWith([
      payable({ supplier: 'Alpha', outstanding: 100, orders: [] }),
      payable({ supplier: 'Beta', outstanding: 200, orders: [] }),
    ]);

    await screen.findByText('Suppliers Owed');
    expect(within(statCard('Suppliers Owed')).getByText('2')).toBeInTheDocument();
  });

  it('shows zero, not a blank, when nothing is owed', async () => {
    renderWith([]);

    await screen.findByText('Total Owed');
    const card = statCard('Total Owed');
    expect(within(card).getByText(/0\.00/)).toBeInTheDocument();
  });

  it('separates the overdue portion from the total', async () => {
    renderWith([
      payable({ supplier: 'Alpha', outstanding: 10000, overdue: true, orders: [] }),
      payable({ supplier: 'Beta', outstanding: 20000, overdue: false, orders: [] }),
    ]);

    await screen.findByText('Overdue');
    expect(within(statCard('Total Owed')).getByText(/30,000/)).toBeInTheDocument();
    expect(within(statCard('Overdue')).getByText(/10,000/)).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. Goods not yet received
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — an order in transit is not a payable', () => {
  it('does not offer an unreceived order as something to pay against', async () => {
    renderWith([payable()], [
      purchase({ id: 1, status: 'ORDERED', totalAmount: 50000, amountPaid: 0 }),
    ]);

    fireEvent.click(await screen.findByRole('button', { name: /record payment/i }));
    const dialog = await screen.findByRole('dialog');

    // No unpaid order was offered, and the form says so rather than silently
    // disabling itself.
    expect(
      within(dialog).getByText(/not on the pages of the purchases list/i),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', { name: /^record payment$/i }),
    ).toBeDisabled();
  });

  it('offers a received order that still has a balance', async () => {
    renderWith([payable()], [
      purchase({ id: 7, orderNumber: 'PO-000007', status: 'RECEIVED', totalAmount: 50000, amountPaid: 0 }),
    ]);

    fireEvent.click(await screen.findByRole('button', { name: /record payment/i }));
    const dialog = await screen.findByRole('dialog');

    const selects = within(dialog).getAllByRole('combobox');
    fireEvent.change(selects[0], { target: { value: '7' } });

    await waitFor(() =>
      expect(
        within(dialog).getByRole('button', { name: /^record payment$/i }),
      ).not.toBeDisabled(),
    );

    // The option names the order and states what is still owed on it, so the
    // operator can see which invoice they are about to settle.
    const option = within(dialog).getByRole('option', { name: /PO-000007/ });
    expect(option).toHaveTextContent(/outstanding/);
    expect(within(dialog).getByLabelText(/amount paid/i)).toHaveValue(50000);
  });

  it('never offers a cancelled order', async () => {
    renderWith([payable()], [
      purchase({ id: 1, status: 'CANCELLED', totalAmount: 50000, amountPaid: 0 }),
    ]);

    fireEvent.click(await screen.findByRole('button', { name: /record payment/i }));
    const dialog = await screen.findByRole('dialog');

    expect(
      within(dialog).getByText(/not on the pages of the purchases list/i),
    ).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4. Recording a payment
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — a payment is attached to an order, not a free amount', () => {
  it('records against the chosen order and lets the server decide the rest', async () => {
    const mutateAsync = jest.fn().mockResolvedValue({});
    mockedUseCreateSupplierPayment.mockReturnValue({
      mutateAsync,
      isPending: false,
    } as any);

    renderWith([payable()], [
      purchase({ id: 7, orderNumber: 'PO-000007', status: 'RECEIVED', totalAmount: 50000, amountPaid: 0 }),
    ]);

    fireEvent.click(await screen.findByRole('button', { name: /record payment/i }));
    const dialog = await screen.findByRole('dialog');

    // Choose the order, then submit.
    const selects = within(dialog).getAllByRole('combobox');
    fireEvent.change(selects[0], { target: { value: '7' } });

    fireEvent.click(within(dialog).getByRole('button', { name: /^record payment$/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());

    // The order id and the amount — and nothing the server should own. In
    // particular no `party`, no `type` and no `partyType`: the server derives
    // direction and party from the order, which is what stops a client declaring
    // a payment against something it was never owed.
    expect(mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ purchaseOrderId: '7', amount: 50000 }),
    );
    const payload = mutateAsync.mock.calls[0][0];
    expect(payload).not.toHaveProperty('party');
    expect(payload).not.toHaveProperty('type');
    expect(payload).not.toHaveProperty('partyType');
  });

  it('refuses an amount larger than the order still owes', async () => {
    const mutateAsync = jest.fn().mockResolvedValue({});
    mockedUseCreateSupplierPayment.mockReturnValue({
      mutateAsync,
      isPending: false,
    } as any);

    renderWith([payable({ outstanding: 50000 })], [
      purchase({ id: 7, status: 'RECEIVED', totalAmount: 50000, amountPaid: 30000 }),
    ]);

    fireEvent.click(await screen.findByRole('button', { name: /record payment/i }));
    const dialog = await screen.findByRole('dialog');

    const selects = within(dialog).getAllByRole('combobox');
    fireEvent.change(selects[0], { target: { value: '7' } });

    // Overwrite the pre-filled amount with one beyond the balance.
    const amountInput = within(dialog).getByLabelText(/amount paid/i);
    fireEvent.change(amountInput, { target: { value: '99999' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /^record payment$/i }));

    await waitFor(() => expect(mutateAsync).not.toHaveBeenCalled());
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5. Loading and error states
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — loading and error states', () => {
  it('does not claim "No records found" before the report arrives', async () => {
    // A promise that has not settled yet: the page must show neither a table nor
    // an empty result while it is waiting.
    mockedGet.mockReturnValue(new Promise(() => {}) as any);
    mockedUsePurchases.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as any);

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <PayablesPage />
      </QueryClientProvider>,
    );

    await waitFor(() =>
      expect(screen.queryByText('No records found')).not.toBeInTheDocument(),
    );
  });

  it('shows an error state when the report fails, and offers a retry', async () => {
    mockedGet.mockRejectedValue(new Error('Server unavailable') as any);
    mockedUsePurchases.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as any);

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <PayablesPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    // Not an empty table: a failure to load and an absence of debt are different
    // situations and must not look the same.
    expect(screen.queryByText('No records found')).not.toBeInTheDocument();
  });
});