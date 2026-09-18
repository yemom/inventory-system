'use client';
import React, { useState, useEffect } from 'react';
import { Bell, Moon, Sun, LogOut, ChevronDown } from 'lucide-react';
import { useAuth } from '@/lib/auth/AuthProvider';
import { usePathname } from 'next/navigation';

function useTheme() {
  const [dark, setDark] = useState(false);
  useEffect(() => {
    setDark(document.documentElement.classList.contains('dark'));
  }, []);
  const toggle = () => {
    document.documentElement.classList.toggle('dark');
    setDark(d => !d);
  };
  return { dark, toggle };
}

function breadcrumbs(pathname: string) {
  const segments = pathname.split('/').filter(Boolean);
  return segments.map(s => s.charAt(0).toUpperCase() + s.slice(1).replace(/-/g, ' '));
}

const ROLE_COLORS: Record<string, string> = {
  SUPER_ADMIN: 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300',
  ADMIN: 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300',
  MANAGER: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
  STOREKEEPER: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  CASHIER: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  ACCOUNTANT: 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/40 dark:text-cyan-300',
};

export default function TopNavbar() {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const { dark, toggle } = useTheme();
  const crumbs = breadcrumbs(pathname);

  return (
    <header className="sticky top-0 z-30 h-14 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between px-6 shrink-0">
      {/* Breadcrumbs */}
      <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-sm">
        <span className="text-gray-400 dark:text-gray-500">StockFlow</span>
        {crumbs.map((crumb, i) => (
          <React.Fragment key={i}>
            <span className="text-gray-300 dark:text-gray-600">/</span>
            <span className={i === crumbs.length - 1 ? 'text-gray-900 dark:text-gray-100 font-semibold' : 'text-gray-400 dark:text-gray-500'}>
              {crumb}
            </span>
          </React.Fragment>
        ))}
      </nav>

      {/* Right actions */}
      <div className="flex items-center gap-2">
        {/* Dark mode toggle */}
        <button
          onClick={toggle}
          className="p-2 rounded-lg text-gray-500 hover:text-gray-800 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          {dark ? <Sun size={17} /> : <Moon size={17} />}
        </button>

        {/* Notifications */}
        <button className="relative p-2 rounded-lg text-gray-500 hover:text-gray-800 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
          <Bell size={17} />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full ring-2 ring-white dark:ring-gray-800" />
        </button>

        <div className="h-6 w-px bg-gray-200 dark:bg-gray-700 mx-1" />

        {/* User info */}
        {user && (
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 bg-gradient-to-br from-blue-500 to-blue-700 rounded-full flex items-center justify-center text-xs font-bold text-white shadow-sm">
              {(user.fullName || user.username)[0].toUpperCase()}
            </div>
            <div className="hidden md:block">
              <p className="text-xs font-semibold text-gray-800 dark:text-gray-100 leading-tight">{user.fullName || user.username}</p>
              <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${ROLE_COLORS[user.role] ?? 'bg-gray-100 text-gray-600'}`}>
                {user.role}
              </span>
            </div>
          </div>
        )}

        {/* Logout */}
        <button
          onClick={logout}
          className="p-2 rounded-lg text-gray-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors ml-1"
          title="Sign out"
        >
          <LogOut size={17} />
        </button>
      </div>
    </header>
  );
}
