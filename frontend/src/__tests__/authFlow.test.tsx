/**
 * authFlow.test.ts
 *
 * Unit tests for AuthProvider state management and permission checks.
 * Tests:
 *  1. hasPermission returns true for SUPER_ADMIN regardless of permissions array
 *  2. hasPermission returns false when user is null
 *  3. hasPermission returns true when permission exists in array (non-SUPER_ADMIN)
 *  4. hasPermission returns false when permission missing from array
 *  5. login() stores token + user in localStorage and calls router.push
 *  6. logout() clears token + user from localStorage
 *  7. Initial state is restored from localStorage on mount
 */

// ── Mock next/navigation ─────────────────────────────────────────────────────
const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

// ── Mock apiClient so login() does not make real HTTP calls ──────────────────
jest.mock('../lib/api/apiClient', () => ({
  __esModule: true,
  default: {
    post: jest.fn(),
    get: jest.fn(),
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
  },
}));

import React from 'react';
import { render, screen, act, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { AuthProvider, useAuth, AuthUser } from '../lib/auth/AuthProvider';
import apiClient from '../lib/api/apiClient';

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Renders a component that exposes the AuthContext via data-testid attributes */
function AuthConsumer() {
  const { user, hasPermission, isLoading } = useAuth();
  return (
    <div>
      <span data-testid="loading">{String(isLoading)}</span>
      <span data-testid="role">{user?.role ?? 'null'}</span>
      <span data-testid="can-create-user">{String(hasPermission('USER_CREATE'))}</span>
      <span data-testid="can-create-customer">{String(hasPermission('CUSTOMER_CREATE'))}</span>
      <span data-testid="can-delete-product">{String(hasPermission('PRODUCT_DELETE'))}</span>
    </div>
  );
}

function renderWithAuth() {
  return render(
    <AuthProvider>
      <AuthConsumer />
    </AuthProvider>
  );
}

// ── Setup / Teardown ─────────────────────────────────────────────────────────

afterEach(() => {
  localStorage.clear();
  jest.clearAllMocks();
});

// ── Tests ────────────────────────────────────────────────────────────────────

describe('hasPermission — SUPER_ADMIN', () => {
  it('returns true for any permission when role is SUPER_ADMIN', async () => {
    const superAdmin: AuthUser = {
      id: 1,
      username: 'superadmin',
      email: 'admin@stockflow.local',
      fullName: 'Super Admin',
      role: 'SUPER_ADMIN',
      permissions: [], // empty — SUPER_ADMIN bypasses list
    };
    localStorage.setItem('auth_user', JSON.stringify(superAdmin));

    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('can-create-user').textContent).toBe('true');
    expect(screen.getByTestId('can-create-customer').textContent).toBe('true');
    expect(screen.getByTestId('can-delete-product').textContent).toBe('true');
  });
});

describe('hasPermission — non-SUPER_ADMIN', () => {
  it('returns false for all permissions when user is null (not logged in)', async () => {
    // No localStorage entry
    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('can-create-user').textContent).toBe('false');
    expect(screen.getByTestId('can-create-customer').textContent).toBe('false');
  });

  it('returns true for granted permissions and false for missing ones', async () => {
    const cashier: AuthUser = {
      id: 2,
      username: 'cashier1',
      email: 'cashier@test.com',
      fullName: 'Cashier One',
      role: 'CASHIER',
      permissions: ['CUSTOMER_CREATE', 'CUSTOMER_READ', 'SALE_CREATE'],
    };
    localStorage.setItem('auth_user', JSON.stringify(cashier));

    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));

    // Cashier has CUSTOMER_CREATE
    expect(screen.getByTestId('can-create-customer').textContent).toBe('true');
    // Cashier does NOT have USER_CREATE
    expect(screen.getByTestId('can-create-user').textContent).toBe('false');
    // Cashier does NOT have PRODUCT_DELETE
    expect(screen.getByTestId('can-delete-product').textContent).toBe('false');
  });

  it('returns false for all when permissions array is empty', async () => {
    const restricted: AuthUser = {
      id: 3,
      username: 'newstaff',
      email: 'staff@test.com',
      fullName: 'New Staff',
      role: 'CASHIER',
      permissions: [],
    };
    localStorage.setItem('auth_user', JSON.stringify(restricted));

    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('can-create-user').textContent).toBe('false');
    expect(screen.getByTestId('can-create-customer').textContent).toBe('false');
  });
});

describe('login() — state management and storage', () => {
  it('stores token and user in localStorage, sets user state, and navigates to /dashboard', async () => {
    const mockLoginResponse = {
      data: {
        token: 'jwt.token.here',
        id: 10,
        username: 'manager1',
        email: 'manager@test.com',
        name: 'Manager One',
        role: 'MANAGER',
        permissions: ['USER_READ', 'PRODUCT_CREATE'],
      },
    };
    (apiClient.post as jest.Mock).mockResolvedValueOnce(mockLoginResponse);

    function LoginTrigger() {
      const { login, user } = useAuth();
      return (
        <div>
          <button onClick={() => login('manager@test.com', 'Pass@123')}>Login</button>
          <span data-testid="username">{user?.username ?? 'none'}</span>
        </div>
      );
    }

    render(
      <AuthProvider>
        <LoginTrigger />
      </AuthProvider>
    );

    // Trigger login
    await act(async () => {
      screen.getByRole('button', { name: 'Login' }).click();
    });

    await waitFor(() => expect(screen.getByTestId('username').textContent).toBe('manager1'));

    expect(apiClient.post).toHaveBeenCalledWith('/auth/login', {
      identifier: 'manager@test.com',
      password: 'Pass@123',
    });
    expect(localStorage.getItem('auth_token')).toBe('jwt.token.here');

    const stored = JSON.parse(localStorage.getItem('auth_user')!);
    expect(stored.username).toBe('manager1');
    expect(stored.role).toBe('MANAGER');
    expect(stored.permissions).toContain('USER_READ');

    expect(mockPush).toHaveBeenCalledWith('/dashboard');
  });
});

describe('logout() — cleanup', () => {
  it('clears auth state and localStorage then navigates to /login', async () => {
    const manager: AuthUser = {
      id: 10,
      username: 'manager1',
      email: 'manager@test.com',
      fullName: 'Manager One',
      role: 'MANAGER',
      permissions: ['USER_READ'],
    };
    localStorage.setItem('auth_token', 'old-token');
    localStorage.setItem('auth_user', JSON.stringify(manager));

    function LogoutTrigger() {
      const { logout, user } = useAuth();
      return (
        <div>
          <button onClick={logout}>Logout</button>
          <span data-testid="username">{user?.username ?? 'none'}</span>
        </div>
      );
    }

    render(
      <AuthProvider>
        <LogoutTrigger />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId('username').textContent).toBe('manager1'));

    await act(async () => {
      screen.getByRole('button', { name: 'Logout' }).click();
    });

    expect(screen.getByTestId('username').textContent).toBe('none');
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
    expect(mockPush).toHaveBeenCalledWith('/login');
  });
});

describe('State restoration from localStorage on mount', () => {
  it('restores user from localStorage when the page loads', async () => {
    const storekeeper: AuthUser = {
      id: 5,
      username: 'storekeeper1',
      email: 'store@test.com',
      fullName: 'Storekeeper One',
      role: 'STOREKEEPER',
      permissions: ['PRODUCT_READ', 'INVENTORY_READ'],
    };
    localStorage.setItem('auth_user', JSON.stringify(storekeeper));

    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('role').textContent).toBe('STOREKEEPER'));
    expect(screen.getByTestId('loading').textContent).toBe('false');
  });

  it('handles corrupted localStorage gracefully and renders as null user', async () => {
    localStorage.setItem('auth_user', 'INVALID_JSON{{{');

    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));
    expect(screen.getByTestId('role').textContent).toBe('null');
    expect(localStorage.getItem('auth_user')).toBeNull(); // cleared
  });
});
