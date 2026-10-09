/**
 * Payables page — end-to-end tests
 *
 * What was broken (and what these tests pin):
 *
 * 1. The backend `toDTO` hardcoded `paymentStatus = "paid"` for every RECEIVED
 *    order.  A purchase made on credit (RECEIVED but UNPAID) was therefore
 *    invisible to the Payables page — the creditor list was always empty.
 *
 * 2. The frontend new-purchase form collected a "Payment Status" dropdown value
 *    but silently dropped it from the POST payload, so credit purchases could
 *    never be created at all.
 *
 * The fixes:
 * - Backend `PurchaseOrder` now carries `paymentStatus` (PAID/PARTIAL/UNPAID)
 *   and `amountPaid` as real persisted columns.
 * - `toDTO` reads those columns instead of inferring from order status.
 * - The new-purchase form sends `paymentStatus` in the POST body.
 *
 * These tests exercise the Payables page in isolation: suppliers and purchases
 * are mocked to match the shapes the real API returns.
 */
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import PayablesPage from '@/app/(dashboard)/finance/payables/page';
import { useSuppliers } from '@/hooks/useSuppliers';
import { usePurchases } from '@/hooks/usePurchases';
import { useCreatePayment } from '@/hooks/useFinance';

// ── Mocks ────────────────────────────────────────────────────────────────────

jest.mock('@/hooks/useSuppliers');
jest.mock('@/hooks/usePurchases');
jest.mock('@/hooks/useFinance');
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));

const mockedUseSuppliers  = useSuppliers  as jest.MockedFunction<typeof useSuppliers>;
const mockedUsePurchases  = usePurchases  as jest.MockedFunction<typeof usePurchases>;
const mockedUseCreatePayment = useCreatePayment as jest.MockedFunction<typeof useCreatePayment>;

// ── Fixtures ─────────────────────────────────────────────────────────────────

/** Minimal supplier shaped like GET /suppliers returns. */
function makeSupplier(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    companyName: 'Acme Supplies',
    contactPerson: 'Dawit Bekele',
    phone: '+251911000001',
    email: 'acme@example.com',
    address: 'Addis Ababa',
    status: 'ACTIVE',
    outstandingBalance: 0, // This stored column is ALWAYS 0 — never written.
    ...overrides,
  };
}

/**
 * Minimal purchase order shaped like GET /purchases returns.
 *
 * paymentStatus is now a real field sent by the backend after the fix.
 * The frontend derives outstanding balances from it, NOT from
 * Supplier.outstandingBalance.
 */
function makePurchase(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    orderNumber: 'PO-000001',
    reference: 'PO-000001',
    supplierName: 'Acme Supplies',
    totalAmount: 50000,
    total: 50000,
    paymentStatus: 'unpaid', // credit purchase — not yet paid
    paid: 0,
    status: 'RECEIVED',
    date: '2026-10-01',
    createdAt: '2026-10-01T10:00:00',
    ...overrides,
  };
}

// ── Render helper ─────────────────────────────────────────────────────────────

function renderWith(
  suppliers: unknown[],
  purchases: unknown[],
  opts: { suppliersLoading?: boolean; error?: Error; createPaymentMutateAsync?: jest.Mock } = {}
) {
  mockedUseSuppliers.mockReturnValue({
    data: suppliers,
    isLoading: opts.suppliersLoading ?? false,
    error: opts.error ?? null,
    refetch: jest.fn(),
  } as any);

  mockedUsePurchases.mockReturnValue({
    data: purchases,
    isLoading: false,
    error: null,
    refetch: jest.fn(),
  } as any);

  mockedUseCreatePayment.mockReturnValue({
    mutateAsync: opts.createPaymentMutateAsync ?? jest.fn().mockResolvedValue({}),
  } as any);

  return render(<PayablesPage />);
}

/** Finds the stat card containing the given label, returns its container. */
function statCard(label: string) {
  return screen.getByText(label).closest('div')?.parentElement as HTMLElement;
}

beforeEach(() => {
  jest.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// 1. Core creditor derivation
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — derives creditors from purchase orders, not supplier.outstandingBalance', () => {
  it('shows a supplier that has an UNPAID purchase order', async () => {
    renderWith(
      [makeSupplier()],
      [makePurchase({ paymentStatus: 'unpaid', paid: 0, totalAmount: 50000, total: 50000 })]
    );

    expect(await screen.findByText('Acme Supplies')).toBeInTheDocument();
    expect(screen.queryByText('No records found')).not.toBeInTheDocument();
  });

  it('does NOT show a supplier whose only purchase is PAID', async () => {
    // This is the pre-fix bug: the old code showed nothing because
    // Supplier.outstandingBalance was always 0. After the fix, PAID purchases
    // should correctly *exclude* the supplier from the creditor list.
    renderWith(
      [makeSupplier()],
      [makePurchase({ paymentStatus: 'paid', paid: 50000, totalAmount: 50000, total: 50000 })]
    );

    await waitFor(() => expect(screen.queryByText(/loading/i)).not.toBeInTheDocument());
    expect(screen.queryByText('Acme Supplies')).not.toBeInTheDocument();
    expect(await screen.findByText('No records found')).toBeInTheDocument();
  });

  it('ignores Supplier.outstandingBalance (always 0, never written) and reads purchases instead', async () => {
    // Supplier with outstandingBalance: 0 and no unpaid purchases — must stay hidden.
    renderWith(
      [makeSupplier({ outstandingBalance: 0 })],
      [] // no purchases at all
    );

    await waitFor(() => expect(screen.queryByText(/loading/i)).not.toBeInTheDocument());
    expect(screen.queryByText('Acme Supplies')).not.toBeInTheDocument();
  });

  it('matches purchase orders to suppliers by name (case-insensitive)', async () => {
    renderWith(
      [makeSupplier({ companyName: 'ACME SUPPLIES' })],
      [makePurchase({ supplierName: 'acme supplies', paymentStatus: 'unpaid' })]
    );

    expect(await screen.findByText('ACME SUPPLIES')).toBeInTheDocument();
  });

  it('shows multiple creditors when several suppliers have unpaid orders', async () => {
    renderWith(
      [
        makeSupplier({ id: 1, companyName: 'Supplier A' }),
        makeSupplier({ id: 2, companyName: 'Supplier B' }),
      ],
      [
        makePurchase({ id: 1, supplierName: 'Supplier A', paymentStatus: 'unpaid', totalAmount: 10000, total: 10000 }),
        makePurchase({ id: 2, supplierName: 'Supplier B', paymentStatus: 'unpaid', totalAmount: 20000, total: 20000 }),
      ]
    );

    expect(await screen.findByText('Supplier A')).toBeInTheDocument();
    expect(screen.getByText('Supplier B')).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2. Partial payments
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — partial payment handling', () => {
  it('shows a supplier with a PARTIAL purchase and the remaining balance', async () => {
    // 30 000 total, 10 000 paid → 20 000 outstanding
    renderWith(
      [makeSupplier()],
      [makePurchase({ paymentStatus: 'partial', paid: 10000, totalAmount: 30000, total: 30000 })]
    );

    expect(await screen.findByText('Acme Supplies')).toBeInTheDocument();
    // 30 000 − 10 000 = 20 000 should appear in the "Amount Owed" column in the table.
    const table = screen.getByRole('table');
    expect(within(table).getByText(/20,000/)).toBeInTheDocument();
  });

  it('excludes a purchase where paid equals total even when status is partial', async () => {
    // Edge case: partial status but fully paid — no outstanding balance.
    renderWith(
      [makeSupplier()],
      [makePurchase({ paymentStatus: 'partial', paid: 50000, totalAmount: 50000, total: 50000 })]
    );

    await waitFor(() => expect(screen.queryByText(/loading/i)).not.toBeInTheDocument());
    expect(screen.queryByText('Acme Supplies')).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. Stat-card values
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — stat cards show correct totals', () => {
  it('Total Payables Due equals the sum of all unpaid amounts', async () => {
    renderWith(
      [
        makeSupplier({ id: 1, companyName: 'Alpha' }),
        makeSupplier({ id: 2, companyName: 'Beta' }),
      ],
      [
        makePurchase({ id: 1, supplierName: 'Alpha', paymentStatus: 'unpaid', totalAmount: 15000, total: 15000, paid: 0 }),
        makePurchase({ id: 2, supplierName: 'Beta',  paymentStatus: 'partial', totalAmount: 40000, total: 40000, paid: 10000 }),
      ]
    );

    // 15 000 + (40 000 − 10 000) = 45 000
    await screen.findByText('Total Payables Due');
    const card = statCard('Total Payables Due');
    expect(within(card).getByText(/45,000/)).toBeInTheDocument();
  });

  it('Vendors With Balances shows the number of creditors', async () => {
    renderWith(
      [
        makeSupplier({ id: 1, companyName: 'Alpha' }),
        makeSupplier({ id: 2, companyName: 'Beta' }),
        makeSupplier({ id: 3, companyName: 'Gamma' }), // fully paid — should not count
      ],
      [
        makePurchase({ id: 1, supplierName: 'Alpha', paymentStatus: 'unpaid' }),
        makePurchase({ id: 2, supplierName: 'Beta',  paymentStatus: 'unpaid' }),
        makePurchase({ id: 3, supplierName: 'Gamma', paymentStatus: 'paid', paid: 50000 }),
      ]
    );

    expect(await screen.findByText('2 Vendors')).toBeInTheDocument();
  });

  it('Unpaid Purchase Orders stat shows outstanding PO value', async () => {
    renderWith(
      [makeSupplier()],
      [
        makePurchase({ id: 1, paymentStatus: 'unpaid', totalAmount: 12000, total: 12000, paid: 0 }),
        makePurchase({ id: 2, paymentStatus: 'unpaid', totalAmount: 8000,  total: 8000,  paid: 0 }),
        makePurchase({ id: 3, paymentStatus: 'paid',   totalAmount: 5000,  total: 5000,  paid: 5000 }),
      ]
    );

    await screen.findByText('Unpaid Purchase Orders');
    // 12 000 + 8 000 = 20 000 — scoped to the stat card to avoid matching the table row
    const card = statCard('Unpaid Purchase Orders');
    expect(within(card).getByText(/20,000/)).toBeInTheDocument();
  });

  it('Unpaid Purchase Orders stat shows the open PO count', async () => {
    renderWith(
      [makeSupplier()],
      [
        makePurchase({ id: 1, paymentStatus: 'unpaid', totalAmount: 12000, total: 12000, paid: 0 }),
        makePurchase({ id: 2, paymentStatus: 'unpaid', totalAmount: 8000,  total: 8000,  paid: 0 }),
        makePurchase({ id: 3, paymentStatus: 'paid',   totalAmount: 5000,  total: 5000,  paid: 5000 }),
      ]
    );

    await screen.findByText('Unpaid Purchase Orders');
    // StatCard renders changeLabel as "2 open PO(s)"
    expect(screen.getByText(/2 open PO/)).toBeInTheDocument();
  });

  it('stat cards all show ETB 0.00 when there are no unpaid orders', async () => {
    renderWith(
      [makeSupplier()],
      [makePurchase({ paymentStatus: 'paid', paid: 50000, totalAmount: 50000, total: 50000 })]
    );

    await waitFor(() => expect(screen.queryByText(/loading/i)).not.toBeInTheDocument());

    const cards = screen.getAllByText(/0\.00/);
    expect(cards.length).toBeGreaterThanOrEqual(2);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4. CANCELLED orders
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — cancelled purchase orders', () => {
  it('does not count a CANCELLED order regardless of paymentStatus', async () => {
    renderWith(
      [makeSupplier()],
      [makePurchase({ status: 'CANCELLED', paymentStatus: 'unpaid', totalAmount: 50000, total: 50000 })]
    );

    await waitFor(() => expect(screen.queryByText(/loading/i)).not.toBeInTheDocument());
    expect(screen.queryByText('Acme Supplies')).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5. Loading and error states
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — loading and error states', () => {
  it('does not say "No records found" while data is still loading', async () => {
    renderWith([], [], { suppliersLoading: true });

    await waitFor(() => {
      expect(screen.queryByText('No records found')).not.toBeInTheDocument();
    });
  });

  it('shows an error state when the suppliers API fails', async () => {
    mockedUseSuppliers.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Suppliers unavailable'),
      refetch: jest.fn(),
    } as any);
    mockedUsePurchases.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as any);
    mockedUseCreatePayment.mockReturnValue({ mutateAsync: jest.fn() } as any);

    render(<PayablesPage />);

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.queryByText('No records found')).not.toBeInTheDocument();
  });

  it('shows an error state when the purchases API fails', async () => {
    mockedUseSuppliers.mockReturnValue({
      data: [makeSupplier()],
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as any);
    mockedUsePurchases.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Purchases unavailable'),
      refetch: jest.fn(),
    } as any);
    mockedUseCreatePayment.mockReturnValue({ mutateAsync: jest.fn() } as any);

    render(<PayablesPage />);

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 6. Settle-bill modal
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — settle-bill modal', () => {
  it('opens the disbursement modal with the correct outstanding balance', async () => {
    renderWith(
      [makeSupplier()],
      [makePurchase({ paymentStatus: 'unpaid', totalAmount: 75000, total: 75000, paid: 0 })]
    );

    const settleBtn = await screen.findByRole('button', { name: /settle bill/i });
    fireEvent.click(settleBtn);

    // Modal dialog should appear
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/Disburse Payment/i)).toBeInTheDocument();

    // Balance is shown inside the dialog
    expect(within(dialog).getByText(/75,000/)).toBeInTheDocument();
  });

  it('calls createPayment with the correct supplier name and amount', async () => {
    const mutateAsync = jest.fn().mockResolvedValue({});

    renderWith(
      [makeSupplier({ companyName: 'Acme Supplies' })],
      [makePurchase({ paymentStatus: 'unpaid', totalAmount: 50000, total: 50000, paid: 0 })],
      { createPaymentMutateAsync: mutateAsync }
    );

    // Open modal
    fireEvent.click(await screen.findByRole('button', { name: /settle bill/i }));

    // Wait for dialog to appear
    const dialog = await screen.findByRole('dialog');

    // Submit via the form submit button
    fireEvent.click(within(dialog).getByRole('button', { name: /submit disbursement/i }));

    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          party: 'Acme Supplies',
          partyType: 'supplier',
          type: 'made',
        })
      );
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 7. Multiple purchase orders per supplier
// ─────────────────────────────────────────────────────────────────────────────

describe('Payables page — multiple purchase orders per supplier', () => {
  it('sums all unpaid orders for the same supplier into one creditor row', async () => {
    renderWith(
      [makeSupplier({ companyName: 'Mega Corp' })],
      [
        makePurchase({ id: 1, supplierName: 'Mega Corp', paymentStatus: 'unpaid', totalAmount: 20000, total: 20000, paid: 0 }),
        makePurchase({ id: 2, supplierName: 'Mega Corp', paymentStatus: 'unpaid', totalAmount: 30000, total: 30000, paid: 0 }),
        makePurchase({ id: 3, supplierName: 'Mega Corp', paymentStatus: 'paid',   totalAmount: 10000, total: 10000, paid: 10000 }),
      ]
    );

    // Only one row for Mega Corp in the table
    const rows = await screen.findAllByText('Mega Corp');
    expect(rows).toHaveLength(1);

    // 20 000 + 30 000 = 50 000 outstanding (the paid one is excluded)
    // Scope to the table to avoid matching stat card values
    const table = screen.getByRole('table');
    expect(within(table).getByText(/50,000/)).toBeInTheDocument();
  });
});
