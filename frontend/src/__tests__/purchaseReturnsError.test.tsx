/**
 * A permission denial must not be reported as a broken server.
 *
 * A Supervisor opened Purchase Returns and was told: "Returns could not be
 * loaded. That is a failure to reach the server, not an absence of returns."
 * The server was answering perfectly — it returned 403, because the endpoint
 * requires RETURN_READ and a Supervisor holds PURCHASE_READ without it.
 *
 * The message sent people to debug a healthy service, and offered a Try again
 * button for a denial that can never succeed on retry. Both halves are pinned
 * here.
 */
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ReturnsPage from '@/app/(dashboard)/purchases/returns/page';
import { returnsApi } from '@/lib/api/aiApi';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/purchases/returns',
}));
jest.mock('@/lib/api/aiApi');
jest.mock('@/lib/auth/AuthProvider', () => ({
  useAuth: () => ({
    user: {
      id: 3,
      username: 'sup',
      email: 's@x.local',
      fullName: 'Nahom Basazinaw',
      role: 'SUPERVISOR',
      permissions: ['PURCHASE_READ'],
    },
    hasPermission: (p: string) => p === 'PURCHASE_READ',
    login: jest.fn(),
    logout: jest.fn(),
    isLoading: false,
  }),
}));
jest.mock('@/components/ui/ToastProvider', () => ({
  useToast: () => ({ toast: jest.fn() }),
}));
jest.mock('@/hooks/usePurchases', () => ({ usePurchases: () => ({ data: [] }) }));

const mockedList = returnsApi.list as jest.MockedFunction<typeof returnsApi.list>;

/** A 403 as axios surfaces it. */
function forbidden() {
  const error: Record<string, unknown> = {
    isAxiosError: true,
    message: 'Request failed with status code 403',
    response: { status: 403, data: { message: 'Forbidden' } },
  };
  Object.defineProperty(error, 'name', { value: 'AxiosError' });
  return error;
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ReturnsPage />
    </QueryClientProvider>,
  );
}

describe('Purchase Returns — failure message', () => {
  beforeEach(() => jest.clearAllMocks());

  it('says a 403 is a permission problem, not a server failure', async () => {
    mockedList.mockRejectedValue(forbidden());

    renderPage();

    expect(
      await screen.findByText(/You don't have permission/i, {}, { timeout: 5000 }),
    ).toBeInTheDocument();

    // The old text asserted the server was unreachable. It was not.
    expect(screen.queryByText(/failure to reach the server/i)).not.toBeInTheDocument();
  });

  it('does not offer a retry for a permission denial', async () => {
    mockedList.mockRejectedValue(forbidden());

    renderPage();

    await screen.findByText(/Retrying will not change this result/, {}, { timeout: 5000 });
    // A 403 is deterministic; pressing Try again cannot change the answer.
    expect(screen.queryByRole('button', { name: /try again/i })).not.toBeInTheDocument();
  });

  it('still offers a retry when the server is genuinely unreachable', async () => {
    mockedList.mockRejectedValue(new Error('Network Error'));

    renderPage();

    // A transport failure is worth trying again, so the button must be there.
    const retry = await screen.findByRole('button', { name: /try again/i }, { timeout: 5000 });
    expect(retry).toBeInTheDocument();
  });
});