/**
 * A sale order that is missing its cashier or branch must not break the page.
 *
 * The requirement: the Sales page shows the cashier's name and the branch name
 * for each order, and an order that has neither still renders.
 *
 * That second half is the one that matters. `cashier` comes from the user who
 * recorded the sale and `branch` from that user's free-text branch, so both are
 * legitimately absent — an order imported from elsewhere, one created before the
 * fields existed, or one rung by a user who has no branch recorded. The page
 * must show a dash and carry on, not throw.
 *
 * This also pins the contract itself. `SaleOrder` used to end in
 * `[key: string]: any`, so a missing field produced `undefined` at some render
 * site and nothing failed: not the type checker, not a test, not the build.
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SalesPage from '@/app/(dashboard)/sales/page';
import { saleOrderSchema, ApiContractError, salesApi } from '@/lib/api/salesApi';
import { useSales, useDeleteSale } from '@/hooks/useSales';
import apiClient from '@/lib/api/apiClient';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/sales',
}));
jest.mock('@/hooks/useSales');
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));

const mockedUseSales = useSales as jest.MockedFunction<typeof useSales>;
const mockedUseDeleteSale = useDeleteSale as jest.MockedFunction<typeof useDeleteSale>;

/** A complete order, as the API sends it when a cashier is recorded. */
function fullOrder(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    orderNumber: 'SO-000001',
    reference: 'SO-000001',
    customerName: 'Walk-in Customer',
    totalAmount: 500,
    finalAmount: 500,
    total: 500,
    discount: 0,
    tax: 0,
    paid: 500,
    status: 'PAID',
    paymentStatus: 'PAID',
    paymentMethod: 'CASH',
    date: '2026-10-07',
    createdAt: '2026-10-07T10:00:00',
    cashier: { id: 7, name: 'Eyas Sentayew' },
    branch: { id: null, name: 'Main Street' },
    items: [{ id: 1, productId: 1, productName: 'Rice', quantity: 5, unitPrice: 100 }],
    ...overrides,
  };
}

function renderSalesPage(orders: unknown[]) {
  mockedUseSales.mockReturnValue({
    data: orders,
    isLoading: false,
    error: null,
    refetch: jest.fn(),
  } as any);
  mockedUseDeleteSale.mockReturnValue({
    mutateAsync: jest.fn(),
    isPending: false,
  } as any);
  return render(<SalesPage />);
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('Sales page — cashier and branch', () => {
  it('shows the cashier name and the branch name in the list', async () => {
    renderSalesPage([fullOrder()]);

    expect(await screen.findByText('Eyas Sentayew')).toBeInTheDocument();
    expect(screen.getByText('Main Street')).toBeInTheDocument();
  });

  it('labels the columns', async () => {
    renderSalesPage([fullOrder()]);
    expect(await screen.findByText('Cashier')).toBeInTheDocument();
    expect(screen.getByText('Branch')).toBeInTheDocument();
  });

  it('shows both in the detail view', async () => {
    const user = userEvent.setup();
    renderSalesPage([fullOrder()]);

    await user.click(await screen.findByTitle('View order'));
    const dialog = await screen.findByRole('dialog');

    // Scoped to the dialog: the name is also in the row behind the modal, so an
    // unscoped query would match twice and fail for the wrong reason.
    expect(within(dialog).getByText('Cashier')).toBeInTheDocument();
    expect(within(dialog).getByText('Eyas Sentayew')).toBeInTheDocument();
    expect(within(dialog).getByText('Branch')).toBeInTheDocument();
    expect(within(dialog).getByText('Main Street')).toBeInTheDocument();
  });

  it('shows the pre-discount subtotal, not zero', async () => {
    const user = userEvent.setup();
    renderSalesPage([fullOrder({ totalAmount: 600, discount: 100, finalAmount: 500, total: 500 })]);

    await user.click(await screen.findByTitle('View order'));
    // Regression: the page read `viewOrder.subtotal`, which the API never sends,
    // so this line always rendered ETB 0.00 even though the order had 600.
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Subtotal:')).toBeInTheDocument();
    await waitFor(() => {
      expect(within(dialog).getByText(/600/)).toBeInTheDocument();
    });
  });
});

describe('Sales page — an order with no cashier or branch', () => {
  it('renders a dash instead of a blank cell', async () => {
    renderSalesPage([fullOrder({ cashier: null, branch: null })]);

    await screen.findByText('SO-000001');
    expect(screen.queryByText('Eyas Sentayew')).not.toBeInTheDocument();
    // Two dashes: one for cashier, one for branch.
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(2);
  });

  it('renders a dash when the keys are absent entirely', async () => {
    const { cashier, branch, ...withoutRef } = fullOrder();
    renderSalesPage([withoutRef]);

    await screen.findByText('SO-000001');
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(2);
  });

  it('renders a dash for a blank branch on an otherwise complete order', async () => {
    renderSalesPage([fullOrder({ branch: { id: null, name: null } })]);

    await screen.findByText('Eyas Sentayew');
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1);
  });

  it('does not crash the page when the order has no items', async () => {
    const user = userEvent.setup();
    renderSalesPage([fullOrder({ items: null })]);

    // Regression: the detail view called `viewOrder.items.map(...)`, and a sale
    // with no lines legitimately omits the field, so this threw and unmounted
    // the page.
    await user.click(await screen.findByTitle('View order'));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Eyas Sentayew')).toBeInTheDocument();
  });

  it('does not crash when items is an empty array', async () => {
    const user = userEvent.setup();
    renderSalesPage([fullOrder({ items: [] })]);

    await user.click(await screen.findByTitle('View order'));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Eyas Sentayew')).toBeInTheDocument();
  });

  it('survives a mix of complete and incomplete orders', async () => {
    renderSalesPage([
      fullOrder({ id: 1, orderNumber: 'SO-1', reference: 'SO-1' }),
      fullOrder({ id: 2, orderNumber: 'SO-2', reference: 'SO-2', cashier: null, branch: null }),
      fullOrder({
        id: 3,
        orderNumber: 'SO-3',
        reference: 'SO-3',
        cashier: { id: 9, name: 'Someone Else' },
        branch: { id: null, name: 'Branch Two' },
      }),
    ]);

    await screen.findByText('SO-1');
    expect(screen.getByText('Eyas Sentayew')).toBeInTheDocument();
    expect(screen.getByText('Someone Else')).toBeInTheDocument();
    expect(screen.getByText('Branch Two')).toBeInTheDocument();
  });
});

describe('saleOrderSchema — the contract is checked once, at the boundary', () => {
  it('accepts a complete order', () => {
    expect(saleOrderSchema.safeParse(fullOrder()).success).toBe(true);
  });

  it('accepts an order with no cashier and no branch', () => {
    const result = saleOrderSchema.safeParse(fullOrder({ cashier: null, branch: null }));
    expect(result.success).toBe(true);
  });

  it('accepts an order with no items', () => {
    const result = saleOrderSchema.safeParse(fullOrder({ items: null }));
    expect(result.success).toBe(true);
  });

  it('accepts a branch with a null id, because branch is free text', () => {
    const result = saleOrderSchema.safeParse(fullOrder({ branch: { id: null, name: 'Main' } }));
    expect(result.success).toBe(true);
  });

  it('rejects an order with no id — there is no sensible fallback for that', () => {
    const { id, ...noId } = fullOrder();
    const result = saleOrderSchema.safeParse(noId);
    expect(result.success).toBe(false);
  });

  it('rejects an id of the wrong type rather than coercing it', () => {
    const result = saleOrderSchema.safeParse(fullOrder({ id: { nope: true } }));
    expect(result.success).toBe(false);
  });

  it('rejects an items array holding something that is not an object', () => {
    const result = saleOrderSchema.safeParse(fullOrder({ items: 'not-an-array' }));
    expect(result.success).toBe(false);
  });

  it('reports which field was wrong, so the failure is diagnosable', () => {
    const { id, ...noId } = fullOrder();
    const parsed = saleOrderSchema.safeParse(noId);
    expect(parsed.success).toBe(false);
    if (!parsed.success) {
      expect(parsed.error.issues.map((i) => i.path.join('.'))).toContain('id');
    }
  });
});

describe('salesApi — a contract violation is reported as such', () => {
  it('returns a valid order untouched', async () => {
    const spy = jest.spyOn(apiClient, 'get').mockResolvedValue({
      data: { data: fullOrder() },
    } as any);

    await expect(salesApi.get('SO-1')).resolves.toMatchObject({
      cashier: { id: 7, name: 'Eyas Sentayew' },
      branch: { id: null, name: 'Main Street' },
    });

    spy.mockRestore();
  });

  it('throws ApiContractError rather than a vague failure', async () => {
    const spy = jest.spyOn(apiClient, 'get').mockResolvedValue({
      data: { data: { nope: true } }, // no id: unusable as a row
    } as any);

    await expect(salesApi.get('SO-1')).rejects.toBeInstanceOf(ApiContractError);

    spy.mockRestore();
  });

  it('names the offending field so the failure is diagnosable', async () => {
    const spy = jest.spyOn(apiClient, 'get').mockResolvedValue({
      data: { data: { nope: true } },
    } as any);

    const error = await salesApi.get('SO-1').catch((e) => e);
    expect(error).toBeInstanceOf(ApiContractError);
    expect(error.issues.join(' ')).toContain('id');

    spy.mockRestore();
  });
});
