'use client';
import React, { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { useRouter } from 'next/navigation';

export type Role = 'ADMIN' | 'MANAGER' | 'STOREKEEPER' | 'CASHIER' | 'ACCOUNTANT';

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  fullName: string;
  role: Role;
}

interface AuthContextProps {
  user: AuthUser | null;
  login: (identifier: string, password: string) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextProps | undefined>(undefined);

// ---------------------------------------------------------------------------
// Mock user database (development only)
// Super admin credentials come from env vars read at startup.
// ---------------------------------------------------------------------------
const SUPER_ADMIN_EMAIL = process.env.NEXT_PUBLIC_SUPER_ADMIN_EMAIL ?? '12yemom@gmail.com';
const SUPER_ADMIN_PASSWORD = process.env.NEXT_PUBLIC_SUPER_ADMIN_PASSWORD ?? '12345678';

interface MockUser {
  id: string;
  email: string;
  username: string;
  fullName: string;
  password: string;
  role: Role;
}

const mockUsers: MockUser[] = [
  {
    id: '1',
    email: SUPER_ADMIN_EMAIL,
    username: 'superadmin',
    fullName: 'Super Admin',
    password: SUPER_ADMIN_PASSWORD,
    role: 'ADMIN',
  },
  {
    id: '2',
    email: 'admin@stockflow.com',
    username: 'admin',
    fullName: 'System Admin',
    password: 'admin123',
    role: 'ADMIN',
  },
  {
    id: '3',
    email: 'manager@stockflow.com',
    username: 'manager',
    fullName: 'Store Manager',
    password: 'manager123',
    role: 'MANAGER',
  },
  {
    id: '4',
    email: 'storekeeper@stockflow.com',
    username: 'storekeeper',
    fullName: 'Store Keeper',
    password: 'store123',
    role: 'STOREKEEPER',
  },
  {
    id: '5',
    email: 'cashier@stockflow.com',
    username: 'cashier',
    fullName: 'Cashier',
    password: 'cashier123',
    role: 'CASHIER',
  },
  {
    id: '6',
    email: 'accountant@stockflow.com',
    username: 'accountant',
    fullName: 'Accountant',
    password: 'account123',
    role: 'ACCOUNTANT',
  },
];

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  // Rehydrate session from localStorage on mount
  useEffect(() => {
    try {
      const stored = localStorage.getItem('auth_user');
      if (stored) {
        setUser(JSON.parse(stored) as AuthUser);
      }
    } catch {
      localStorage.removeItem('auth_user');
    } finally {
      setIsLoading(false);
    }
  }, []);

  /**
   * Login with username OR email, plus password.
   */
  const login = async (identifier: string, password: string): Promise<void> => {
    // Simulate network latency
    await new Promise(r => setTimeout(r, 400));

    const found = mockUsers.find(
      u =>
        (u.username.toLowerCase() === identifier.toLowerCase() ||
          u.email.toLowerCase() === identifier.toLowerCase()) &&
        u.password === password
    );

    if (!found) {
      throw new Error('Invalid username/email or password.');
    }

    const loggedIn: AuthUser = {
      id: found.id,
      username: found.username,
      email: found.email,
      fullName: found.fullName,
      role: found.role,
    };

    setUser(loggedIn);
    localStorage.setItem('auth_user', JSON.stringify(loggedIn));
    router.push('/dashboard');
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('auth_user');
    router.push('/login');
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, isLoading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextProps => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
