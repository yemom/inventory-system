/**
 * The shared report screen.
 *
 * One test here is a regression test for a defect the browser caught before
 * anyone did: the totals row was rendered as `<td>` elements directly inside
 * `<tfoot>`, with no `<tr>` between them. That is invalid table markup — a
 * `<tfoot>` may only contain rows — and React logged a hydration error on
 * every report page ("In HTML, <td> cannot be a child of <tfoot>"). The
 * numbers were right; the structure the client produced and the structure the
 * browser expects differed, which is exactly the class of bug that surfaces as
 * a console warning nobody reads and a DOM that quietly diverges after
 * hydration.
 *
 * So this suite asserts the table's *structure*, not just its text: every
 * section holds rows, every row holds cells. It also pins the states a report
 * page can be in — loaded, empty, failed, forbidden — and the export gate,
 * because a report that silently stops showing its totals is indistinguishable
 * from one that computed none.
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import ReportScreen, { ReportColumn } from '@/components/reports/ReportScreen';
import { useAuth } from '@/lib/auth/AuthProvider';
import { reportsApi } from '@/lib/api/aiApi';

jest.mock('@/lib/auth/AuthProvider', () => ({ useAuth: jest.fn() }));
jest.mock('@/lib/api/aiApi', () => ({
  reportsApi: { get: jest.fn(), download: jest.fn() },
}));

const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockedGet = reportsApi.get as jest.MockedFunction<typeof reportsApi.get>;

const columns: ReportColumn[] = [
  { key: 'order', label: 'Order' },
  { key: 'net', label: 'Net', money: true },
];

const payload = {
  report: 'Sales and gross profit',
  columns: ['order', 'net'],
  rows: [
    { order: 'SO-000001', net: 1500.5 },
    { order: 'SO-000002', net: 2000.25 },
  ],
  // Keyed by the column the footer looks up. Deliberately NOT the sum of the
  // two rows above (3,500.75): the footer must show what the server sent.
  totals: { net: 4000 },
};

function withPermission(...permissions: string[]) {
  mockedUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission),
  } as any);
}

function report(extra: Record<string, unknown> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ReportScreen
        title="Sales &amp; gross profit"
        report="sales"
        columns={columns}
        {...extra}
      />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  withPermission('REPORT_EXPORT');
  mockedGet.mockResolvedValue(payload as any);
});

// ─────────────────────────────────────────────────────────────────────────────
// Table structure — the hydration regression
// ─────────────────────────────────────────────────────────────────────────────

describe('table structure', () => {
  it('puts every total cell inside a row of the footer', async () => {
    const { container } = report();

    await screen.findByText('SO-000001');

    const foot = container.querySelector('tfoot');
    expect(foot).not.toBeNull();

    // The defect: <td> as a direct child of <tfoot>. A footer holds rows only.
    Array.from(foot!.children).forEach((child) => {
      expect(child.tagName).toBe('TR');
    });

    const totalRow = foot!.querySelector('tr') as HTMLElement;
    expect(totalRow.querySelectorAll('td').length).toBe(columns.length);
    expect(within(totalRow).getByText('Total')).toBeInTheDocument();
  });

  it('never renders a cell outside a row, or a row outside a table section', async () => {
    const { container } = report();

    await screen.findByText('SO-000001');

    container.querySelectorAll('td, th').forEach((cell) => {
      expect(cell.parentElement?.tagName).toBe('TR');
    });
    container.querySelectorAll('tr').forEach((row) => {
      expect(['THEAD', 'TBODY', 'TFOOT']).toContain(row.parentElement?.tagName);
    });
  });

  it('renders header cells in a thead row, one per column', async () => {
    const { container } = report();

    await screen.findByText('SO-000001');

    const head = container.querySelector('thead tr') as HTMLElement;
    expect(head.querySelectorAll('th').length).toBe(columns.length);
    expect(within(head).getByText('Order')).toBeInTheDocument();
    expect(within(head).getByText('Net')).toBeInTheDocument();
  });

  it('shows the server totals as sent, without recomputing them', async () => {
    report();

    await screen.findByText('SO-000001');

    // The payload's own figure (4,000), not the sum of the two visible rows
    // (3,500.75), and not the component's own arithmetic. Matched by pattern:
    // Intl formats currency with a non-breaking space, which a string matcher
    // does not treat as the same character.
    expect(screen.getByText(/4,000\.00/)).toBeInTheDocument();
    expect(screen.queryByText(/3,500\.75/)).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// States
// ─────────────────────────────────────────────────────────────────────────────

describe('states', () => {
  it('shows an empty result as an empty report, not as an error', async () => {
    mockedGet.mockResolvedValue({ ...payload, rows: [], totals: null } as any);

    report();

    await waitFor(() => {
      expect(screen.getByText('Nothing recorded in this period')).toBeInTheDocument();
    });
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows a failure with a retry, and does not render a half-built table', async () => {
    mockedGet.mockRejectedValue(new Error('boom'));

    const { container } = report();

    await waitFor(() => {
      expect(screen.getByText('This report could not be loaded')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Try again/ })).toBeInTheDocument();
    expect(container.querySelector('tfoot')).toBeNull();
  });

  it('tells a refusal apart from a failure, and offers no retry for it', async () => {
    mockedGet.mockRejectedValue(Object.assign(new Error('forbidden'), {
      response: { status: 403 },
    }));

    report();

    await waitFor(() => {
      expect(
        screen.getByText('You do not have permission to view this report'),
      ).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: /Try again/ })).not.toBeInTheDocument();
  });

  it('hides the export buttons without REPORT_EXPORT', async () => {
    withPermission();

    report();

    await screen.findByText('SO-000001');
    expect(screen.queryByRole('button', { name: 'CSV' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'PDF' })).not.toBeInTheDocument();
  });

  it('offers CSV and PDF with REPORT_EXPORT', async () => {
    report();

    await screen.findByText('SO-000001');
    expect(screen.getAllByRole('button', { name: 'CSV' }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('button', { name: 'PDF' }).length).toBeGreaterThan(0);
  });
});