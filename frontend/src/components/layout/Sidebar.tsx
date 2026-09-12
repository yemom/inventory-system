'use client';
import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard, Package, Warehouse, ShoppingCart, TruckIcon,
  Users, Building2, DollarSign, BarChart3, Shield, Settings,
  ChevronDown, ChevronRight, Menu, X, Activity, Tag,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuth } from '@/lib/auth/AuthProvider';

interface NavChild { label: string; href: string; roles?: string[]; }
interface NavItem {
  label: string;
  href?: string;
  icon: React.ReactNode;
  roles?: string[];
  children?: NavChild[];
}

const navItems: NavItem[] = [
  { label: 'Dashboard', href: '/dashboard', icon: <LayoutDashboard size={18} /> },
  {
    label: 'Inventory', icon: <Warehouse size={18} />,
    children: [
      { label: 'Products', href: '/products' },
      { label: 'Categories', href: '/categories' },
      { label: 'Stock Overview', href: '/inventory' },
      { label: 'Stock Movements', href: '/inventory/movements' },
      { label: 'Adjustments', href: '/inventory/adjustments' },
      { label: 'Warehouses', href: '/warehouses' },
      { label: 'Transfers', href: '/inventory/transfers' },
    ],
  },
  {
    label: 'Sales', icon: <ShoppingCart size={18} />,
    children: [
      { label: 'New Sale / POS', href: '/sales/pos' },
      { label: 'Sales Orders', href: '/sales' },
      { label: 'Returns', href: '/sales/returns' },
      { label: 'Customers', href: '/customers' },
    ],
  },
  {
    label: 'Purchases', icon: <TruckIcon size={18} />,
    children: [
      { label: 'New Purchase', href: '/purchases/new' },
      { label: 'Purchase Orders', href: '/purchases' },
      { label: 'Returns', href: '/purchases/returns' },
      { label: 'Suppliers', href: '/suppliers' },
    ],
  },
  {
    label: 'Finance', icon: <DollarSign size={18} />, roles: ['ADMIN', 'ACCOUNTANT', 'MANAGER'],
    children: [
      { label: 'Payments', href: '/finance/payments' },
      { label: 'Expenses', href: '/finance/expenses' },
      { label: 'Receivables', href: '/finance/receivables' },
      { label: 'Payables', href: '/finance/payables' },
      { label: 'Profit & Loss', href: '/finance/profit-loss' },
    ],
  },
  {
    label: 'Reports', icon: <BarChart3 size={18} />, roles: ['ADMIN', 'ACCOUNTANT', 'MANAGER'],
    children: [
      { label: 'Sales Report', href: '/reports/sales' },
      { label: 'Inventory Report', href: '/reports/inventory' },
      { label: 'Financial Report', href: '/reports/financial' },
      { label: 'Purchase Report', href: '/reports/purchases' },
    ],
  },
  { label: 'Users & Roles', href: '/users', icon: <Shield size={18} />, roles: ['ADMIN'] },
  { label: 'Audit Logs', href: '/audit-logs', icon: <Activity size={18} />, roles: ['ADMIN', 'MANAGER'] },
  { label: 'Settings', href: '/settings', icon: <Settings size={18} />, roles: ['ADMIN'] },
];

const DEFAULT_EXPANDED = ['Inventory', 'Sales', 'Finance', 'Purchases'];

export default function Sidebar() {
  const pathname = usePathname();
  const { user } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [expanded, setExpanded] = useState<string[]>(DEFAULT_EXPANDED);

  const canSee = (roles?: string[]) => !roles || (!!user && roles.includes(user.role));
  const toggleExpanded = (label: string) =>
    setExpanded(prev => prev.includes(label) ? prev.filter(l => l !== label) : [...prev, label]);
  const isActive = (href: string) => pathname === href || pathname.startsWith(href + '/');

  return (
    <aside className={cn(
      'fixed left-0 top-0 z-40 flex flex-col bg-gray-900 text-gray-100 h-screen transition-all duration-300',
      collapsed ? 'w-16' : 'w-64'
    )}>
      {/* Logo */}
      <div className="flex items-center justify-between px-4 py-4 border-b border-gray-700/60 shrink-0">
        {!collapsed && (
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
              <Package size={16} className="text-white" />
            </div>
            <span className="text-base font-bold text-white tracking-tight">StockFlow</span>
          </div>
        )}
        <button
          onClick={() => setCollapsed(c => !c)}
          className={cn(
            'p-1.5 rounded-lg text-gray-400 hover:text-white hover:bg-gray-700 transition-colors',
            collapsed && 'mx-auto'
          )}
        >
          {collapsed ? <Menu size={18} /> : <X size={18} />}
        </button>
      </div>

      {/* Nav */}
      <nav className="flex-1 py-3 overflow-y-auto scrollbar-hide">
        <ul className="space-y-0.5 px-2">
          {navItems.map(item => {
            if (!canSee(item.roles)) return null;

            if (item.children) {
              const isExp = expanded.includes(item.label);
              const childActive = item.children.some(c => isActive(c.href));
              return (
                <li key={item.label}>
                  <button
                    onClick={() => !collapsed && toggleExpanded(item.label)}
                    title={collapsed ? item.label : undefined}
                    className={cn(
                      'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                      childActive ? 'bg-blue-600/15 text-blue-300' : 'text-gray-400 hover:text-white hover:bg-gray-700/60',
                      collapsed && 'justify-center'
                    )}
                  >
                    {item.icon}
                    {!collapsed && (
                      <>
                        <span className="flex-1 text-left">{item.label}</span>
                        {isExp ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
                      </>
                    )}
                  </button>
                  {!collapsed && isExp && (
                    <ul className="mt-0.5 ml-6 space-y-0.5 border-l border-gray-700/40 pl-3">
                      {item.children.map(child => {
                        if (!canSee(child.roles)) return null;
                        const active = isActive(child.href);
                        return (
                          <li key={child.href}>
                            <Link
                              href={child.href}
                              className={cn(
                                'block px-3 py-1.5 rounded-lg text-xs transition-colors',
                                active ? 'bg-blue-600 text-white font-semibold' : 'text-gray-400 hover:text-white hover:bg-gray-700/50'
                              )}
                            >
                              {child.label}
                            </Link>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </li>
              );
            }

            const active = isActive(item.href!);
            return (
              <li key={item.href}>
                <Link
                  href={item.href!}
                  title={collapsed ? item.label : undefined}
                  className={cn(
                    'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                    active ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-700/60',
                    collapsed && 'justify-center'
                  )}
                >
                  {item.icon}
                  {!collapsed && <span>{item.label}</span>}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* User footer */}
      {user && (
        <div className={cn('px-3 py-3 border-t border-gray-700/60 shrink-0', collapsed && 'flex justify-center')}>
          {collapsed ? (
            <div className="w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center text-xs font-bold text-white">
              {(user.fullName || user.username)[0].toUpperCase()}
            </div>
          ) : (
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0">
                {(user.fullName || user.username)[0].toUpperCase()}
              </div>
              <div className="min-w-0">
                <p className="text-xs font-semibold text-white truncate">{user.fullName || user.username}</p>
                <p className="text-xs text-gray-400 truncate">{user.role}</p>
              </div>
            </div>
          )}
        </div>
      )}
    </aside>
  );
}
