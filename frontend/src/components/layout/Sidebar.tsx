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

/**
 * Navigation is gated on *permissions*, not role names.
 *
 * The previous shape was `roles?: string[]`, which had two problems: it
 * duplicated the backend's authority rules in the UI (so they could disagree),
 * and it could not express a rule like "may see sales but not profit" — the
 * whole Reports group was one role list, so a Supervisor had to be shown the
 * entire section or none of it.
 *
 * `anyOf` means "needs at least one of these". A Super Admin satisfies
 * everything through `hasPermission`, so no item needs to name that role.
 */
interface NavChild {
  label: string;
  href: string;
  /** Permissions that grant access to this entry. Empty means visible to all. */
  anyOf?: string[];
}
interface NavItem {
  label: string;
  href?: string;
  icon: React.ReactNode;
  anyOf?: string[];
  children?: NavChild[];
}

const navItems: NavItem[] = [
  { label: 'Dashboard', href: '/dashboard', icon: <LayoutDashboard size={18} />, anyOf: ['DASHBOARD_VIEW'] },
  {
    label: 'Inventory', icon: <Warehouse size={18} />,
    children: [
      { label: 'Products', href: '/products', anyOf: ['PRODUCT_READ', 'INVENTORY_READ'] },
      { label: 'Categories', href: '/categories', anyOf: ['PRODUCT_READ', 'INVENTORY_READ', 'CATEGORY_MANAGE'] },
      { label: 'Stock Overview', href: '/inventory', anyOf: ['INVENTORY_READ'] },
      { label: 'Stock Movements', href: '/inventory/movements', anyOf: ['INVENTORY_READ', 'STOCK_MOVEMENT_READ'] },
      // Submitting an adjustment; the approval queue is a separate entry below.
      { label: 'Adjustments', href: '/inventory/adjustments', anyOf: ['INVENTORY_ADJUST', 'STOCK_ADJUSTMENT_APPROVE'] },
      { label: 'Approvals', href: '/inventory/approvals', anyOf: ['STOCK_ADJUSTMENT_APPROVE'] },
      { label: 'Warehouses', href: '/warehouses', anyOf: ['WAREHOUSE_READ', 'INVENTORY_READ'] },
      { label: 'Transfers', href: '/inventory/transfers', anyOf: ['INVENTORY_TRANSFER'] },
    ],
  },
  {
    label: 'Sales', icon: <ShoppingCart size={18} />,
    children: [
      { label: 'New Sale / POS', href: '/sales/pos', anyOf: ['SALE_CREATE'] },
      { label: 'Sales Orders', href: '/sales', anyOf: ['SALE_READ'] },
      { label: 'Approvals', href: '/sales/approvals', anyOf: ['SALE_VOID_APPROVE', 'SALE_REFUND_APPROVE'] },
      { label: 'Customers', href: '/customers', anyOf: ['CUSTOMER_READ', 'CUSTOMER_CREATE'] },
    ],
  },
  {
    label: 'Purchases', icon: <TruckIcon size={18} />,
    children: [
      { label: 'New Purchase', href: '/purchases/new', anyOf: ['PURCHASE_CREATE'] },
      { label: 'Purchase Orders', href: '/purchases', anyOf: ['PURCHASE_READ'] },
      { label: 'Returns', href: '/purchases/returns', anyOf: ['PURCHASE_READ'] },
      { label: 'Suppliers', href: '/suppliers', anyOf: ['SUPPLIER_READ'] },
    ],
  },
  {
    // Split so that a Manager or Accountant sees money but not necessarily the
    // P&L; previously the whole group shared one role list.
    label: 'Finance', icon: <DollarSign size={18} />,
    children: [
      { label: 'Payments', href: '/finance/payments', anyOf: ['PAYMENT_READ', 'PAYMENT_CREATE'] },
      { label: 'Expenses', href: '/finance/expenses', anyOf: ['EXPENSE_READ', 'EXPENSE_CREATE'] },
      { label: 'Receivables', href: '/finance/receivables', anyOf: ['RECEIVABLE_READ'] },
      { label: 'Payables', href: '/finance/payables', anyOf: ['PAYABLE_READ'] },
      // Profit is the single most sensitive figure, so it stands on its own.
      { label: 'Profit & Loss', href: '/finance/profit-loss', anyOf: ['PROFIT_LOSS_READ'] },
    ],
  },
  {
    label: 'Reports', icon: <BarChart3 size={18} />,
    children: [
      { label: 'Sales Report', href: '/reports/sales', anyOf: ['SALES_REPORT_VIEW', 'REPORT_VIEW'] },
      { label: 'Inventory Report', href: '/reports/inventory', anyOf: ['INVENTORY_REPORT_VIEW', 'LOW_STOCK_REPORT_VIEW', 'REPORT_VIEW'] },
      { label: 'Inventory Value', href: '/reports/inventory-value', anyOf: ['INVENTORY_VALUE_VIEW'] },
      { label: 'Purchase Report', href: '/reports/purchases', anyOf: ['PURCHASE_READ'] },
    ],
  },
  { label: 'Staff Management', href: '/staff', icon: <Shield size={18} />, anyOf: ['STAFF_VIEW', 'USER_READ'] },
  { label: 'Audit Logs', href: '/audit-logs', icon: <Activity size={18} />, anyOf: ['AUDIT_LOG_VIEW'] },
  { label: 'Settings', href: '/settings', icon: <Settings size={18} />, anyOf: ['SYSTEM_SETTINGS_MANAGE', 'SETTINGS_VIEW'] },
];

const DEFAULT_EXPANDED = ['Inventory', 'Sales', 'Finance', 'Purchases'];

export default function Sidebar() {
  const pathname = usePathname();
  const { user, hasPermission } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [expanded, setExpanded] = useState<string[]>(DEFAULT_EXPANDED);

  /**
   * Visible when the user holds at least one of the required permissions.
   * Delegates to `hasPermission`, which grants everything to SUPER_ADMIN, so the
   * backend stays the single source of truth for who may see what.
   */
  const canSee = (anyOf?: string[]) => !anyOf?.length || anyOf.some(permission => hasPermission(permission));
  const visibleChildren = (item: NavItem): NavChild[] =>
    item.children?.filter(child => canSee(child.anyOf)) ?? [];
  const toggleExpanded = (label: string) =>
    setExpanded(prev => prev.includes(label) ? prev.filter(l => l !== label) : [...prev, label]);

  /**
   * The one entry that represents the current page, or null.
   *
   * <p>A plain prefix test lights up every ancestor: on `/inventory/movements`
   * both "Stock Overview" (`/inventory`) and "Stock Movements"
   * (`/inventory/movements`) matched, so two entries appeared selected at once
   * and neither looked wrong on its own. Only the longest match is the page you
   * are actually on, so that is the only one that gets highlighted.
   *
   * <p>Computed once per render rather than inside `isActive`, since answering
   * "which entry wins" for each candidate in turn would give the same answer
   * every time.
   */
  const activeHref = (() => {
    const hrefs = [
      ...navItems.flatMap(item => [item.href, ...(item.children ?? []).map(c => c.href)]),
    ].filter((href): href is string => Boolean(href));

    return hrefs
      .filter(href => pathname === href || pathname.startsWith(href + '/'))
      .sort((a, b) => b.length - a.length)[0] ?? null;
  })();

  const isActive = (href: string) => activeHref === href;

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
            if (!canSee(item.anyOf)) return null;

            if (item.children) {
              // A group whose children are all hidden must not render as an empty
              // clickable header that expands to nothing.
              const children = visibleChildren(item);
              if (children.length === 0) return null;

              const isExp = expanded.includes(item.label);
              const childActive = children.some(c => isActive(c.href));
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
                      {children.map(child => {
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
