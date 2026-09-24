'use client';
import React, { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import apiClient from '@/lib/api/apiClient';

export type Role = 'SUPER_ADMIN' | 'MANAGER' | 'SUPERVISOR' | 'STOREKEEPER' | 'INVENTORY_STAFF' | 'CASHIER' | 'ACCOUNTANT';

export interface AuthUser {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: Role;
  permissions: string[];
}

interface AuthContextProps {
  user: AuthUser | null;
  login: (identifier: string, password: string) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
  hasPermission: (permission: string) => boolean;
}

const AuthContext = createContext<AuthContextProps | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    try {
      const stored = localStorage.getItem('auth_user');
      if (stored) setUser(JSON.parse(stored) as AuthUser);
    } catch {
      localStorage.removeItem('auth_user');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const login = async (identifier: string, password: string): Promise<void> => {
    const response = await apiClient.post('/auth/login', { identifier, password });
    // AuthController returns AuthResponse directly; also support ApiResponse wrapper
    const data = response.data?.data ?? response.data;
    if (!data?.token) {
      throw new Error(response.data?.message || 'Login failed: no token received');
    }

    localStorage.setItem('auth_token', data.token);

    const loggedIn: AuthUser = {
      id: data.id,
      username: data.username,
      email: data.email,
      fullName: data.name,
      role: data.role as Role,
      permissions: data.permissions ?? [],
    };
    setUser(loggedIn);
    localStorage.setItem('auth_user', JSON.stringify(loggedIn));
    router.push('/dashboard');
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    router.push('/login');
  };

  const hasPermission = (permission: string): boolean => {
    if (!user) return false;
    if (user.role === 'SUPER_ADMIN') return true;
    return user.permissions.includes(permission);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, isLoading, hasPermission }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextProps => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
