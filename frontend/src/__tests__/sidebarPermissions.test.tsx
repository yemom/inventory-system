/**
 * The sidebar must reflect the same rules as the API.
 *
 * Regression context: navigation was gated on a hardcoded `roles: string[]` per
 * item. That duplicated the backend's authority rules in the UI so the two could
 * disagree, and it could not express "may see sales but not profit" — the whole
 * Reports group shared one role list, so a Supervisor either saw everything or
 * nothing. It now asks for the permissions the endpoint actually requires.
 *
 * Hiding a link is presentation, not security: `RolePermissionCatalogTest` and
 * `RolePermissionEnforcementIntegrationTest` are what prove the API refuses the
 * request. This test only checks that the UI does not offer the action.
 */
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { usePathname } from 'next/navigation';
import Sidebar from '@/components/layout/Sidebar';
import { useAuth } from '@/lib/auth/AuthProvider';

// A module-level value so a test can move the app between routes.
let currentPath = '/dashboard';
jest.mock('next/navigation', () => ({ usePathname: () => currentPath }));
jest.mock('@/lib/auth/AuthProvider', () => ({ useAuth: jest.fn() }));

const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

function asUser(role: string, permissions: string[]) {
  mockedUseAuth.mockReturnValue({
    user: { id: 1, username: 'u', email: 'u@x.local', fullName: 'U', role, permissions },
    hasPermission: (p: string) => permissions.includes(p),
    login: jest.fn(),
    logout: jest.fn(),
    isLoading: false,
  } as any);
}

function asSuperAdmin() {
  mockedUseAuth.mockReturnValue({
    user: { id: 1, username: 'root', email: 'r@x.local', fullName: 'R', role: 'SUPER_ADMIN', permissions: [] },
    hasPermission: () => true,
    login: jest.fn(),
    logout: jest.fn(),
    isLoading: false,
  } as any);
}

/**
 * Expands every group that is present but collapsed.
 *
 * Inventory, Sales, Finance and Purchases start expanded; Reports does not. It
 * is simpler and less brittle to expand whatever is collapsed than to hard-code
 * which groups those are.
 */
async function expandAll(user: ReturnType<typeof userEvent.setup>) {
  const groups = ['Inventory', 'Sales', 'Purchases', 'Reports', 'Finance'];
  for (const group of groups) {
    const header = screen.queryByRole('button', { name: new RegExp(`^${group}$`, 'i') });
    if (header) {
      await user.click(header);
      // Clicking a collapsed group expands it; clicking an already-expanded one
      // would collapse it. Toggle back so every group ends up expanded.
      const childrenAfter = screen.queryAllByRole('link').length;
      await user.click(header);
      if (screen.queryAllByRole('link').length < childrenAfter) {
        await user.click(header);
      }
    }
  }
}

async function renderExpanded(role: string, permissions: string[]) {
  const user = userEvent.setup();
  asUser(role, permissions);
  render(<Sidebar />);
  await expandAll(user);
  return user;
}

describe('Sidebar — permission gating', () => {
  it('shows a Supervisor the sales and low-stock reports', async () => {
    await renderExpanded('SUPERVISOR', ['SALES_REPORT_VIEW', 'LOW_STOCK_REPORT_VIEW', 'DASHBOARD_VIEW']);
    expect(screen.getByText('Sales Report')).toBeInTheDocument();
    expect(screen.getByText('Inventory Report')).toBeInTheDocument();
  });

  it('hides inventory value from a Supervisor', async () => {
    await renderExpanded('SUPERVISOR', ['SALES_REPORT_VIEW', 'LOW_STOCK_REPORT_VIEW', 'DASHBOARD_VIEW']);
    expect(screen.queryByText('Inventory Value')).not.toBeInTheDocument();
  });

  it('hides the whole Finance group from a Supervisor, since none of it is theirs', async () => {
    await renderExpanded('SUPERVISOR', ['SALES_REPORT_VIEW', 'LOW_STOCK_REPORT_VIEW', 'DASHBOARD_VIEW']);
    expect(screen.queryByText('Profit & Loss')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Finance$/i })).not.toBeInTheDocument();
  });

  it('shows inventory value and profit to a Manager', async () => {
    await renderExpanded('MANAGER', ['PROFIT_LOSS_READ', 'INVENTORY_VALUE_VIEW', 'DASHBOARD_VIEW']);
    expect(screen.getByText('Inventory Value')).toBeInTheDocument();
    expect(screen.getByText('Profit & Loss')).toBeInTheDocument();
  });

  it('hides the sales report and all financials from a Cashier', async () => {
    await renderExpanded('CASHIER', ['SALE_CREATE', 'SALE_READ', 'PRODUCT_READ', 'DASHBOARD_VIEW']);
    expect(screen.queryByText('Sales Report')).not.toBeInTheDocument();
    expect(screen.queryByText('Inventory Value')).not.toBeInTheDocument();
    expect(screen.queryByText('Profit & Loss')).not.toBeInTheDocument();
  });

  it('still gives a Cashier the till and the product list', async () => {
    await renderExpanded('CASHIER', ['SALE_CREATE', 'SALE_READ', 'PRODUCT_READ', 'DASHBOARD_VIEW']);
    expect(screen.getByText('New Sale / POS')).toBeInTheDocument();
    expect(screen.getByText('Products')).toBeInTheDocument();
  });

  it('hides staff management from a Cashier', async () => {
    await renderExpanded('CASHIER', ['SALE_CREATE', 'SALE_READ', 'DASHBOARD_VIEW']);
    expect(screen.queryByText('Staff Management')).not.toBeInTheDocument();
  });

  it('gives a Supervisor the stock approval queue', async () => {
    await renderExpanded('SUPERVISOR', ['STOCK_ADJUSTMENT_APPROVE', 'SALE_VOID_APPROVE', 'DASHBOARD_VIEW']);
    // Two groups (Inventory and Sales) each have an Approvals entry.
    expect(screen.getAllByText('Approvals').length).toBe(2);
  });

  it('does not give Inventory Staff the approval queue, but does give them adjustments', async () => {
    await renderExpanded('INVENTORY_STAFF', ['INVENTORY_ADJUST', 'DASHBOARD_VIEW']);
    expect(screen.getByText('Adjustments')).toBeInTheDocument();
    expect(screen.queryByText('Approvals')).not.toBeInTheDocument();
  });

  it('hides Settings from a Manager', async () => {
    await renderExpanded('MANAGER', ['PRODUCT_CREATE', 'PRODUCT_READ', 'DASHBOARD_VIEW']);
    expect(screen.queryByText('Settings')).not.toBeInTheDocument();
  });

  it('shows everything to a Super Admin, because hasPermission grants all', async () => {
    const user = userEvent.setup();
    asSuperAdmin();
    render(<Sidebar />);
    await expandAll(user);
    expect(screen.getByText('Settings')).toBeInTheDocument();
    expect(screen.getByText('Staff Management')).toBeInTheDocument();
    expect(screen.getByText('Audit Logs')).toBeInTheDocument();
    expect(screen.getByText('Profit & Loss')).toBeInTheDocument();
  });

  it('renders no navigation at all for a user with no permissions', () => {
    asUser('CASHIER', []);
    const { container } = render(<Sidebar />);
    expect(container.querySelectorAll('nav a').length).toBe(0);
  });

  it('does not leave an empty expandable group behind', () => {
    // A group whose every child is hidden must not render as a clickable header
    // that expands to nothing.
    asUser('CASHIER', ['DASHBOARD_VIEW', 'SALE_CREATE', 'SALE_READ']);
    render(<Sidebar />);
    expect(screen.queryByText('Finance')).not.toBeInTheDocument();
    expect(screen.queryByText('Purchases')).not.toBeInTheDocument();
    expect(screen.queryByText('Reports')).not.toBeInTheDocument();
  });
});

describe('Sidebar — highlighting the current page', () => {
  beforeEach(() => {
    currentPath = '/dashboard';
    asSuperAdmin();
  });

  /**
   * Labels of the nav entries the sidebar is currently highlighting.
   *
   * Two styles mark an active entry - a child link gets `bg-blue-600/15`, a
   * top-level one gets solid `bg-blue-600` - so match the shared prefix and
   * restrict to anchors, which excludes the logo and avatar divs.
   */
  function highlightedLabels(container: HTMLElement): string[] {
    return Array.from(container.querySelectorAll('a'))
      .filter(a => a.className.includes('bg-blue-600'))
      .map(a => a.textContent?.trim() ?? '');
  }

  it('highlights exactly one entry on a nested route', async () => {
    currentPath = '/inventory/movements';
    const user = userEvent.setup();
    const { container } = render(<Sidebar />);
    await expandAll(user);

    // "Stock Overview" is /inventory, so a plain prefix test matched it as well
    // as "Stock Movements" and two entries looked selected at once.
    const highlighted = highlightedLabels(container);
    expect(highlighted).toHaveLength(1);
    expect(highlighted[0]).toBe('Stock Movements');
  });

  it('highlights the parent entry when you are on the parent route', async () => {
    currentPath = '/inventory';
    const user = userEvent.setup();
    const { container } = render(<Sidebar />);
    await expandAll(user);

    expect(highlightedLabels(container)).toEqual(['Stock Overview']);
  });

  it('highlights the deepest match, not the ancestor', async () => {
    currentPath = '/finance/profit-loss';
    const user = userEvent.setup();
    const { container } = render(<Sidebar />);
    await expandAll(user);

    expect(highlightedLabels(container)).toEqual(['Profit & Loss']);
  });

  it('highlights nothing on a route with no entry', async () => {
    currentPath = '/somewhere-else';
    const { container } = render(<Sidebar />);
    expect(highlightedLabels(container)).toHaveLength(0);
  });

  it('does not treat a sibling with a shared prefix as the current page', async () => {
    // /sales and /sales/approvals share a prefix; the shorter one must not win.
    currentPath = '/sales/approvals';
    const user = userEvent.setup();
    const { container } = render(<Sidebar />);
    await expandAll(user);

    expect(highlightedLabels(container)).toEqual(['Approvals']);
  });
});
