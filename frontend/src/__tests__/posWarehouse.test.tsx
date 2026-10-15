/**
 * The till must be able to take money.
 *
 * A cashier who cannot complete a sale is not a degraded experience, it is a
 * closed shop — and that is exactly what happened: the business had two active
 * warehouses, the till sent neither, and the backend answered 500. The picker
 * below is the fix, and these tests exist so the fix cannot quietly regress
 * into a button that does nothing.
 *
 * Three things are pinned:
 *  1. The picker appears only when the choice is real (more than one warehouse).
 *  2. "Complete Sale" is refused until a warehouse is chosen, so the failure
 *     happens before the customer is waiting rather than after.
 *  3. The chosen warehouse is sent on the request.
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PosPage from '@/app/(dashboard)/sales/pos/page';
import { salesApi } from '@/lib/api/salesApi';
import { warehousesApi } from '@/lib/api/warehousesApi';
import { useToast } from '@/components/ui/ToastProvider';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/sales/pos',
}));
jest.mock('@/lib/api/salesApi');
jest.mock('@/lib/api/warehousesApi');
jest.mock('@/lib/api/customersApi', () => ({
  customersApi: { list: jest.fn().mockResolvedValue([]) },
}));
jest.mock('@/hooks/useProducts', () => ({
  useProducts: () => ({ data: [], isLoading: false }),
}));
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));

const mockedCreate = salesApi.create as jest.MockedFunction<typeof salesApi.create>;
const mockedWarehouses = warehousesApi.list as jest.MockedFunction<
  typeof warehousesApi.list
>;

const PRODUCT = {
  id: 5,
  sku: 'DEL-INSP-002',
  name: 'Dell Inspiron',
  sellingPrice: 100,
  quantity: 12,
  category: { id: 1, name: 'Laptops' },
};

function product(overrides: Record<string, unknown> = {}) {
  return { ...PRODUCT, ...overrides };
}

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

describe('POS warehouse selection', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
      { id: 2, name: 'Branch Warehouse', active: true },
    ]);
    mockedCreate.mockResolvedValue({
      ...product(),
      id: 99,
      orderNumber: 'SO-000099',
      createdAt: new Date().toISOString(),
      totalAmount: 100,
      finalAmount: 100,
    } as never);
  });

  it('offers a warehouse picker when there is more than one location', async () => {
    renderPos();

    const picker = await screen.findByLabelText(/sell from/i);
    expect(picker).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Main Warehouse' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Branch Warehouse' })).toBeInTheDocument();
  });

  it('hides the picker when the business has a single warehouse', async () => {
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
    ]);

    renderPos();

    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    // The backend resolves one location on its own; asking would have no answer.
    expect(screen.queryByLabelText(/sell from/i)).not.toBeInTheDocument();
    expect(screen.getByText(/Selling from/i)).toBeInTheDocument();
  });

  it('ignores inactive warehouses', async () => {
    // Three warehouses, one of them closed: the picker must still appear
    // because two active locations remain a real choice, and the closed one
    // must not be among them.
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
      { id: 2, name: 'Branch Warehouse', active: true },
      { id: 3, name: 'Closed Warehouse', active: false },
    ]);

    renderPos();

    await screen.findByLabelText(/sell from/i);
    // Offering a location that cannot hold stock trades one dead end for another.
    expect(screen.queryByRole('option', { name: 'Closed Warehouse' })).not.toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Main Warehouse' })).toBeInTheDocument();
  });

  it('will not complete a sale until a warehouse is chosen', async () => {
    renderPos();

    const button = await screen.findByRole('button', { name: /complete sale/i });
    // Refused up front with a reason, rather than after the customer is waiting
    // and the server has refused.
    expect(button).toBeDisabled();
    // Exact string: this jest-dom build compares attribute values by equality
    // rather than by pattern, so a regex here would fail on a matching value.
    expect(button.getAttribute('title')).toBe('Choose a warehouse to sell from');
    expect(mockedCreate).not.toHaveBeenCalled();
  });
});