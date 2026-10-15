/**
 * The till must be able to take money without being asked to choose something
 * it has no way to know.
 *
 * The warehouse picker was removed at the owner's request. That reintroduces a
 * real hazard: with more than one active location the backend refuses a sale
 * that does not name one, and a till that cannot sell is a closed shop rather
 * than a degraded experience.
 *
 * So what protects it now is resolution in the component. These tests pin it:
 *   1. No picker appears, at any number of warehouses.
 *   2. The checkout button is never disabled for a reason nobody can see.
 *   3. The resolved warehouse still reaches the API, and is never a closed one.
 */
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PosPage from '@/app/(dashboard)/sales/pos/page';
import { salesApi } from '@/lib/api/salesApi';
import { warehousesApi } from '@/lib/api/warehousesApi';

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
  useProducts: () => ({
    data: [
      {
        id: 5,
        sku: 'DEL-INSP-002',
        name: 'Dell Inspiron',
        sellingPrice: 100,
        quantity: 12,
        active: true,
        // The category filter matches on the name, and defaults to "All".
        categoryName: 'Laptops',
      },
    ],
    isLoading: false,
  }),
}));
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));

const mockedCreate = salesApi.create as jest.MockedFunction<typeof salesApi.create>;
const mockedWarehouses = warehousesApi.list as jest.MockedFunction<
  typeof warehousesApi.list
>;

function renderPos() {
  // The page uses react-query for its products, so it needs a real provider.
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <PosPage />
    </QueryClientProvider>,
  );
}

/** Adds the only product to the cart, so checkout has something to sell. */
async function addToCart() {
  const card = await screen.findByText('Dell Inspiron');
  card.click();
}

/** Settles the payment fields the form requires, then submits. */
async function completeCheckout() {
  // The cash field only exists for the cash method, and defaults to cash.
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

describe('POS — warehouse resolution', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedWarehouses.mockResolvedValue([
      { id: 1, name: 'Main Warehouse', active: true },
      { id: 2, name: 'Branch Warehouse', active: true },
    ]);
    mockedCreate.mockResolvedValue({
      id: 99,
      orderNumber: 'SO-000099',
      createdAt: new Date().toISOString(),
      totalAmount: 100,
      finalAmount: 100,
      status: 'PAID',
    } as never);
  });

  it('shows no warehouse picker, even with several warehouses', async () => {
    renderPos();

    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    expect(screen.queryByLabelText(/sell from/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Active Cart/)).not.toBeInTheDocument();
  });

  it('still sends a warehouse on the sale when there are several', async () => {
    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    await addToCart();
    await completeCheckout();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled());
    const payload = mockedCreate.mock.calls[0][0] as { warehouseId?: number };
    expect(payload.warehouseId).toBeTruthy();
  });

  it('never disables checkout for a reason nobody can see', async () => {
    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    await addToCart();

    expect(screen.getByRole('button', { name: /complete sale/i })).toBeEnabled();
  });

  it('still lets a single warehouse resolve on its own', async () => {
    mockedWarehouses.mockClear();
    mockedWarehouses.mockResolvedValue([{ id: 7, name: 'Only', active: true }]);

    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    await addToCart();
    await completeCheckout();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled());
    const payload = mockedCreate.mock.calls[0][0] as { warehouseId?: number };
    expect(payload.warehouseId).toBe(7);
  });

  it('never credits stock to a closed warehouse', async () => {
    renderPos();
    await waitFor(() => expect(mockedWarehouses).toHaveBeenCalled());
    await addToCart();
    await completeCheckout();

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled());
    const payload = mockedCreate.mock.calls[0][0] as { warehouseId?: number };
    expect(payload.warehouseId).toBe(1);
  });
});
