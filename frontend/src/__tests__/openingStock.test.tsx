/**
 * Opening stock must reach the API.
 *
 * <p>Regression context: the product form had no opening-quantity field and the
 * payload it POSTed contained no stock figure at all, so every product created
 * through the UI was created with 0 — which is how a product entered with 50
 * units came to read "out of stock". The backend stored whatever it was given;
 * nothing was ever given.
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProductsPage from '@/app/(dashboard)/products/page';
import { useAuth } from '@/lib/auth/AuthProvider';
import { useProducts, useCreateProduct, useUpdateProduct, useDeleteProduct } from '@/hooks/useProducts';
import { categoriesApi } from '@/lib/api/categoriesApi';

jest.mock('@/lib/auth/AuthProvider', () => ({ useAuth: jest.fn() }));
jest.mock('@/hooks/useProducts');
jest.mock('@/lib/api/categoriesApi');
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));

const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockedUseProducts = useProducts as jest.MockedFunction<typeof useProducts>;
const mockedCreateProduct = useCreateProduct as jest.MockedFunction<typeof useCreateProduct>;
const mockedUpdateProduct = useUpdateProduct as jest.MockedFunction<typeof useUpdateProduct>;
const mockedDeleteProduct = useDeleteProduct as jest.MockedFunction<typeof useDeleteProduct>;
const mockedCategories = categoriesApi as jest.Mocked<typeof categoriesApi>;

function allowAll() {
  mockedUseAuth.mockReturnValue({
    user: {
      id: 1,
      username: 'admin',
      email: 'admin@stockflow.local',
      fullName: 'Admin',
      role: 'SUPER_ADMIN',
      permissions: [],
    },
    hasPermission: () => true,
    login: jest.fn(),
    logout: jest.fn(),
    isLoading: false,
  } as any);
}

describe('ProductsPage — opening stock', () => {
  let mutateAsync: jest.Mock;

  beforeEach(() => {
    jest.clearAllMocks();
    mutateAsync = jest.fn().mockResolvedValue({ id: 99, sku: 'RICE-1' });
    allowAll();
    mockedUseProducts.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as any);
    mockedCreateProduct.mockReturnValue({ mutateAsync } as any);
    mockedUpdateProduct.mockReturnValue({ mutateAsync: jest.fn() } as any);
    mockedDeleteProduct.mockReturnValue({ mutateAsync: jest.fn() } as any);
    mockedCategories.list.mockResolvedValue([{ id: 7, name: 'Grains', parentId: null }] as any);
  });

  async function openCreateForm(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole('button', { name: /add product/i }));
    await screen.findByLabelText(/opening quantity/i);
  }

  it('offers an opening-quantity field when creating a product', async () => {
    const user = userEvent.setup();
    render(<ProductsPage />);
    await openCreateForm(user);

    const field = screen.getByLabelText(/opening quantity/i);
    expect(field).toBeInTheDocument();
    expect(field).toHaveValue(0);
  });

  it('sends initialQuantity: 50 when 50 units are entered', async () => {
    const user = userEvent.setup();
    render(<ProductsPage />);
    await openCreateForm(user);

    await user.type(screen.getByLabelText(/product name/i), 'Basmati Rice');
    await user.type(screen.getByLabelText(/sku/i), 'RICE-BAS-25');
    await user.selectOptions(screen.getByLabelText(/category/i), '7');
    await user.clear(screen.getByLabelText(/opening quantity/i));
    await user.type(screen.getByLabelText(/opening quantity/i), '50');

    await user.click(screen.getByRole('button', { name: /save product/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    const payload = mutateAsync.mock.calls[0][0];
    expect(payload.initialQuantity).toBe(50);
  });

  it('sends a zero opening quantity when the field is left alone', async () => {
    const user = userEvent.setup();
    render(<ProductsPage />);
    await openCreateForm(user);

    await user.type(screen.getByLabelText(/product name/i), 'Plain Sugar');
    await user.type(screen.getByLabelText(/sku/i), 'SUG-1');
    await user.selectOptions(screen.getByLabelText(/category/i), '7');

    await user.click(screen.getByRole('button', { name: /save product/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync.mock.calls[0][0].initialQuantity).toBe(0);
  });

  it('refuses a negative opening quantity instead of sending it', async () => {
    const user = userEvent.setup();
    render(<ProductsPage />);
    await openCreateForm(user);

    await user.type(screen.getByLabelText(/product name/i), 'Negative Stock');
    await user.type(screen.getByLabelText(/sku/i), 'NEG-1');
    await user.selectOptions(screen.getByLabelText(/category/i), '7');
    await user.clear(screen.getByLabelText(/opening quantity/i));
    await user.type(screen.getByLabelText(/opening quantity/i), '-5');

    await user.click(screen.getByRole('button', { name: /save product/i }));

    await waitFor(() => expect(mutateAsync).not.toHaveBeenCalled());
  });

  it('does not send initialQuantity when editing, so stock cannot be retyped', async () => {
    const user = userEvent.setup();
    mockedUseProducts.mockReturnValue({
      data: [
        {
          id: 5,
          sku: 'EXIST-1',
          name: 'Existing Product',
          quantity: 40,
          reorderLevel: 10,
          categoryId: 7,
          purchasePrice: 10,
          sellingPrice: 20,
          active: true,
        },
      ],
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as any);
    const updateMutate = jest.fn().mockResolvedValue({});
    mockedUpdateProduct.mockReturnValue({ mutateAsync: updateMutate } as any);

    render(<ProductsPage />);
    await user.click(await screen.findByTitle(/edit/i));

    // Shows the current balance, but read-only.
    const field = await screen.findByLabelText(/quantity on hand/i);
    expect(field).toHaveValue(40);
    expect(field).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /save product/i }));
    await waitFor(() => expect(updateMutate).toHaveBeenCalled());
    expect(updateMutate.mock.calls[0][0].data.initialQuantity).toBeUndefined();
  });
});

describe('ProductsPage — stock status', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    allowAll();
    mockedCreateProduct.mockReturnValue({ mutateAsync: jest.fn() } as any);
    mockedUpdateProduct.mockReturnValue({ mutateAsync: jest.fn() } as any);
    mockedDeleteProduct.mockReturnValue({ mutateAsync: jest.fn() } as any);
    mockedCategories.list.mockResolvedValue([{ id: 7, name: 'Grains', parentId: null }] as any);
  });

  function withProducts(products: unknown[]) {
    mockedUseProducts.mockReturnValue({
      data: products,
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    } as any);
  }

  it('does not label a product opened at 50 as out of stock', async () => {
    withProducts([{ id: 1, sku: 'OPEN-50', name: 'Opened Fifty', quantity: 50, reorderLevel: 10, active: true }]);
    render(<ProductsPage />);

    await screen.findByText('Opened Fifty');
    expect(screen.queryByText('Out of Stock')).not.toBeInTheDocument();
    expect(screen.getByText('50 on hand')).toBeInTheDocument();
  });

  it('labels a product below its reorder level as low stock', async () => {
    withProducts([{ id: 2, sku: 'LOW-1', name: 'Nearly Out', quantity: 3, reorderLevel: 10, active: true }]);
    render(<ProductsPage />);

    await screen.findByText('Nearly Out');
    expect(screen.getByText('Low Stock')).toBeInTheDocument();
  });

  it('labels a genuinely empty product as out of stock', async () => {
    withProducts([{ id: 3, sku: 'ZERO-1', name: 'Nothing Left', quantity: 0, reorderLevel: 10, active: true }]);
    render(<ProductsPage />);

    await screen.findByText('Nothing Left');
    expect(screen.getByText('Out of Stock')).toBeInTheDocument();
  });
});
