/**
 * Role ordering decides what a person sees *first*, and must never decide what
 * they are allowed to see.
 *
 * That distinction is the whole point of these tests. An ordering bug that put
 * a supervisor's approvals below the fold is a nuisance; an ordering bug that
 * dropped a section entirely would remove information somebody is entitled to,
 * silently, and the page would look complete while lying by omission.
 *
 * So: two claims, both tested.
 *   1. A role's list changes the sequence.
 *   2. A section the role's list omits still renders.
 *
 * The second is the one worth having. `ROLE_SECTIONS` is hand-maintained, and
 * the failure mode of forgetting to update it — a card quietly vanishing for
 * one role and not another — is exactly the kind that ships unnoticed.
 */
import {
  ROLE_SECTIONS,
  defaultSectionOrder,
  homeForRole,
  sectionOrderFor,
} from '@/lib/dashboard/roleDashboard';
import type { Role } from '@/lib/auth/AuthProvider';

const ALL_ROLES: Role[] = [
  'SUPER_ADMIN',
  'MANAGER',
  'SUPERVISOR',
  'CASHIER',
  'STOREKEEPER',
  'INVENTORY_STAFF',
  'ACCOUNTANT',
];

describe('role dashboard configuration', () => {
  it('gives every supported role a landing page', () => {
    for (const role of ALL_ROLES) {
      expect(homeForRole(role)).toMatch(/^\//);
    }
  });

  it('falls back to the dashboard for an unrecognised role', () => {
    // A role added to the backend but not yet to this map must still land
    // somewhere they can read, rather than on a blank route.
    expect(homeForRole(undefined)).toBe('/dashboard');
    expect(homeForRole('SOMETHING_NEW' as Role)).toBe('/dashboard');
  });

  it('puts a cashier\'s own trading before business-wide intelligence', () => {
    const order = sectionOrderFor('CASHIER');
    expect(order.indexOf('sales-summary')).toBeLessThan(order.indexOf('forecast'));
    expect(order.indexOf('orders')).toBeLessThan(order.indexOf('financials'));
  });

  it('never omits a section, whatever the role', () => {
    // The guarantee: ordering is a preference, never a filter.
    for (const role of ALL_ROLES) {
      const order = sectionOrderFor(role);
      for (const section of defaultSectionOrder) {
        expect(order).toContain(section);
      }
    }
  });

  it('contains no duplicates for any role', () => {
    // A repeated entry would render a section twice.
    for (const role of ALL_ROLES) {
      const order = sectionOrderFor(role);
      expect(new Set(order).size).toBe(order.length);
    }
  });

  it('never lists a section name the page does not render', () => {
    // Guards against a typo in ROLE_SECTIONS silently doing nothing: an
    // unknown name would be appended last and look ordered when it is not.
    for (const role of ALL_ROLES) {
      for (const section of ROLE_SECTIONS[role] ?? []) {
        expect(defaultSectionOrder).toContain(section);
      }
    }
  });
});