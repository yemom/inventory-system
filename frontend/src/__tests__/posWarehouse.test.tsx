/**
 * POS Warehouse-Selection Tests
 *
 * These tests pin the multi-warehouse rule enforced by the POS page:
 *
 *   1. If exactly ONE active warehouse exists → resolve automatically, no picker shown.
 *   2. If MORE THAN ONE active warehouse exists → show an explicit picker with
 *      aria-label "Select Warehouse". Checkout is blocked until the cashier picks one.
 *   3. Closed (inactive) warehouses are never offered for selection.
 *   4. The resolved warehouse ID reaches the backend API on every completed sale.
 */
import {
  render,
  screen,
  waitFor,
  fireEvent,
} from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PosPage from '@/app/(dashboard)/sales/pos/page';
import { salesApi } from '@/lib/api/salesApi';
import { warehousesApi } from '@/lib/api/warehousesApi';

// ── Navigation ──────────────────────────────────────────────────────────────
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/sales/pos',
}));

// ── API mocks ────────────────────────────────────────────────────────────────
jest.mock('@/lib/api/salesApi');
jest.mock('@/lib/api/warehousesApi');
jest.mock('@/lib/api/customersApi', () => ({
  customersApi: { list: jest.fn().mockResolvedValue([]) },
}));

// ── Products hook ────────────────────────────────────────────────────────────
jest.mock('@/hooks/useProducts', () => ({
  useProducts: () => ({
    data: [
      {
        id: 5,
        sku: 'DEL-INSP-002',
        name: 'Dell Inspiron',
        sellingPrice: 100,
        quantity: 12,
        active: true,
        categoryName: 'Laptops',
      },
    ],
    isLoading: false,
  }),
}));

// ── Toast ────────────────────────────────────────────────────────────────────
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));

// ── Auth — mock useAuth so the component can mount without AuthProvider ───────
jest.mock('@/lib/auth/AuthProvider', () => ({
  useAuth: () => ({
    user: { id: 1, username: 'cashier1', fullName: 'Test Cashier', roles: ['CASHIER'] },
    hasPermission: (_perm: string) => true,
    isAuthenticated: true,
    isLoading: false,
    login: jest.fn(),
    logout: jest.fn(),
  }),
}));

// ── Typed mock refs ──────────────────────────────────────────────────────────
const mockedCreate = salesApi.create as jest.MockedFunction<typeof salesApi.create>;
const mockedWarehouses = warehousesApi.list as jest.MockedFunction<
  typeof warehousesApi.list
>;

// ── Render helper ────────────────────────────────────────────────────────────
function renderPos() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <PosPage />
    </QueryClientProvider>,
  );
}

/** Adds "Dell Inspiron" to the cart. */
async function addToCart() {
  const card = await screen.findByText('Dell Inspiron');
  card.click();
}

/**
 * Fills the cash-tendered field and clicks "Complete Sale".
 * Waits for the button to become enabled first.
 */
async function completeCheckout() {
  const tendered = screen.queryByPlaceholderText(/cash given/i);
  if (tendered) {
    const setter = Object.getOwnPropertyDescriptor(
      window.HTMLInputElement.prototype,
      'value',
    )?.set;
    setter?.call(tendered, '999999');
    tendered.dispatchEvent(new Event('input', { bubbles: true }));
  }
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: /complete sale/i }),
    ).toBeEnabled(),
  );
  screen
    .getByRole('button', { name: /complete sale/i })
    .dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
}

/**
 * Selects a warehouse from the picker (multi-warehouse scenario),
 * then fills the cash field and clicks "Complete Sale".
 */
async function selectWarehouseAndCompleteCheckout(warehouseId: number) {
  // Wait for the warehouse selector to appear
  const selector = await screen.findByLabelText('Select Warehouse');
  fireEvent.change(selector, { target: { value: String(warehouseId) } });

  await completeCheckout();
}

// ── Default mock response for a successful sale ──────────────────────────────
const MOCK_SALE_RESPONSE = {
  id: 99,
  orderNumber: 'SO-000099',
  createdAt: new Date().toISOString(),
  totalAmount: 100,
  finalAmount: 115,
  status: 'PAID',
  items: [],
} as never;

// ═══════════════════════════════════════════════════════════════════════════
describe('POS — warehouse resolution', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCreate.mockResolvedValue(MOCK_SALE_RESPONSE);
  });

  // ── Multi-warehouse: picker must be shown ──────────────────────────────
  it('shows a warehouse picker when multiple active warehouses exist', async () => {
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
      { id: 2, name: 'Branch Warehouse', active: true },
    ]);

    renderPos();

    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());

    // The picker must be visible so the cashier can make a choice.
    expect(await screen.findByLabelText('Select Warehouse')).toBeInTheDocument();
  });

  // ── Multi-warehouse: checkout blocked until selection ─────────────────
  it('disables checkout when multiple warehouses exist and none is selected', async () => {
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
      { id: 2, name: 'Branch Warehouse', active: true },
    ]);

    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    await addToCart();

    // Cart has an item. Button should still be disabled because no warehouse chosen.
    await waitFor(() => {
      const btn = screen.queryByRole('button', { name: /select warehouse to checkout/i });
      expect(btn).toBeInTheDocument();
      expect(btn).toBeDisabled();
    });
  });

  // ── Multi-warehouse: selecting a warehouse enables checkout and sends it ─
  it('enables checkout and sends the selected warehouseId when a warehouse is chosen', async () => {
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
      { id: 2, name: 'Branch Warehouse', active: true },
    ]);

    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    await addToCart();
    await selectWarehouseAndCompleteCheckout(2);

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled());
    const payload = mockedCreate.mock.calls[0][0] as { warehouseId?: number };
    expect(payload.warehouseId).toBe(2);
  });

  // ── Single warehouse: auto-resolved, no picker shown ─────────────────
  it('auto-resolves a single warehouse without showing a picker', async () => {
    mockedWarehouses.mockResolvedValue([{ id: 7, name: 'Only Warehouse', active: true }]);

    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());

    // No picker for single-warehouse deployments.
    expect(screen.queryByLabelText('Select Warehouse')).not.toBeInTheDocument();
  });

  // ── Single warehouse: sale goes through and sends the correct id ──────
  it('sends the auto-resolved warehouseId for a single-warehouse setup', async () => {
    mockedWarehouses.mockResolvedValue([{ id: 7, name: 'Only Warehouse', active: true }]);

    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    await addToCart();
    await completeCheckout();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled());
    const payload = mockedCreate.mock.calls[0][0] as { warehouseId?: number };
    expect(payload.warehouseId).toBe(7);
  });

  // ── Closed warehouses must never appear in the picker ─────────────────
  it('never offers a closed (inactive) warehouse as a selection option', async () => {
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
      { id: 3, name: 'Closed Annex', active: false },
      { id: 2, name: 'Branch Warehouse', active: true },
    ]);

    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());

    const selector = await screen.findByLabelText('Select Warehouse');
    // Only the two active warehouses should appear as options.
    expect(selector).not.toHaveTextContent('Closed Annex');
    expect(selector).toHaveTextContent('Main Warehouse');
    expect(selector).toHaveTextContent('Branch Warehouse');
  });

  // ── Closed warehouse is never sent in the API payload ────────────────
  it('never sends a closed warehouse id to the API', async () => {
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
      { id: 3, name: 'Closed Annex', active: false },
    ]);

    // Only 1 active warehouse → auto-resolved to id 1.
    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    await addToCart();
    await completeCheckout();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled());
    const payload = mockedCreate.mock.calls[0][0] as { warehouseId?: number };
    // Must be the active warehouse (1), never the closed one (3).
    expect(payload.warehouseId).toBe(1);
    expect(payload.warehouseId).not.toBe(3);
  });
});
