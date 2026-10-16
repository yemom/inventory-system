/**
 * What each role is shown on the dashboard.
 *
 * <p>The sidebar is gated on permissions rather than role names, and this file
 * follows the same rule where it can. A role here is a *starting point* for
 * reading the dashboard, never a grant: every section below is additionally
 * gated on the permission the data behind it requires, and the backend checks
 * that permission again on every request.
 *
 * <p>The distinction matters because the two can drift. Adding a section for a
 * role without the permission to read it would show an empty card; adding the
 * permission check without the role entry would hide a card somebody should
 * see. Keeping them in one place makes both reviewable at once.
 *
 * <p>Nothing here grants access. {@code ROLE_HOMES} only decides where someone
 * lands after signing in — a convenience — while {@code hasPermission} decides
 * what renders.
 */

import type { Role } from '@/lib/auth/AuthProvider';

/**
 * Where each role lands after signing in.
 *
 * <p>Every role has a real landing page rather than falling through to a shared
 * one, because the first screen after login should be the one that answers that
 * person's most likely question. A cashier's is their own orders; a supervisor's
 * is the floor's activity.
 */
export const ROLE_HOMES: Record<Role, string> = {
  SUPER_ADMIN: '/dashboard',
  MANAGER: '/dashboard',
  SUPERVISOR: '/dashboard',
  CASHIER: '/dashboard',
  STOREKEEPER: '/dashboard',
  INVENTORY_STAFF: '/dashboard',
  ACCOUNTANT: '/dashboard',
};

/**
 * The order in which sections appear for a role.
 *
 * <p>Most specific first: a cashier's own trading is their question, and the
 * business-wide intelligence below it is a secondary thing they are allowed to
 * see rather than the point of the page. An empty list means "show the default
 * set", which is what {@code defaultSectionOrder} gives.
 */
export const ROLE_SECTIONS: Partial<Record<Role, string[]>> = {
  CASHIER: ['sales-summary', 'orders', 'forecast', 'alerts'],
  INVENTORY_STAFF: ['stock', 'alerts', 'forecast', 'orders'],
  STOREKEEPER: ['stock', 'alerts', 'forecast', 'orders'],
  SUPERVISOR: ['orders', 'approvals', 'alerts', 'forecast', 'stock'],
  MANAGER: ['forecast', 'financials', 'stock', 'approvals', 'orders'],
  ACCOUNTANT: ['financials', 'approvals', 'orders'],
  SUPER_ADMIN: [
    'financials',
    'forecast',
    'stock',
    'approvals',
    'orders',
    'alerts',
  ],
};

/** The order used when a role has no preference of its own. */
export const defaultSectionOrder = [
  'sales-summary',
  'orders',
  'financials',
  'forecast',
  'stock',
  'approvals',
  'alerts',
];

/**
 * The section order for a role.
 *
 * <p>Falls back to the default for a role not listed, and appends any default
 * section the role's own list omits. Without that, adding a new section would
 * silently hide it from every role with a custom order — the kind of omission
 * that is invisible until someone reports the missing card.
 */
export function sectionOrderFor(role: Role | undefined): string[] {
  const preferred = role ? ROLE_SECTIONS[role] : undefined;
  if (!preferred) return defaultSectionOrder;
  return [...preferred, ...defaultSectionOrder.filter((s) => !preferred.includes(s))];
}

/**
 * The landing page for a role.
 *
 * <p>Falls back to the dashboard for anything unrecognised rather than
 * redirecting somewhere the person may not have permission to view.
 */
export function homeForRole(role: Role | undefined): string {
  if (!role) return '/dashboard';
  return ROLE_HOMES[role] ?? '/dashboard';
}