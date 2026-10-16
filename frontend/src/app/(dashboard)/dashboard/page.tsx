'use client';
import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import {
  AlertTriangle,
  ArrowDownRight,
  ArrowUpRight,
  Banknote,
  Loader2,
  Package,
  RefreshCw,
  ShoppingCart,
  TrendingUp,
  TruckIcon,
  Wallet,
} from 'lucide-react';
import StatCard from '@/components/ui/StatCard';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import { StatCardSkeleton } from '@/components/ui/Skeleton';
import DateRangeFilter, {
  DateRange,
  lastNDays,
} from '@/components/ui/DateRangeFilter';
import AiInsightPanel from '@/components/ai/AiInsightPanel';
import ForecastChart from '@/components/ai/ForecastChart';
import AssistantPanel from '@/components/ai/AssistantPanel';
import SalesOrderTable from '@/components/dashboard/SalesOrderTable';
import CashierDashboard from '@/components/dashboard/CashierDashboard';
import SupervisorDashboard from '@/components/dashboard/SupervisorDashboard';
import InventoryStaffDashboard from '@/components/dashboard/InventoryStaffDashboard';
import AccountantDashboard from '@/components/dashboard/AccountantDashboard';
import { useAiDashboard, useDashboardFinancial } from '@/hooks/useAiDashboard';
import { useProducts } from '@/hooks/useProducts';
import { useAuth } from '@/lib/auth/AuthProvider';
import { formatCurrency, formatDate } from '@/lib/utils';
import { labelApiError } from '@/lib/api/apiErrors';
import { sectionOrderFor } from '@/lib/dashboard/roleDashboard';

/**
 * The dashboard.
 *
 * <p>Extended rather than replaced: the operational cards, stock alerts and
 * recent orders below are the ones that were here before, and they still work
 * the same way.
 *
 * <p>The financial figures at the top are new in where they come from. They used
 * to be assembled in the browser from lists of sales and purchase orders — the
 * same list the screens use, filtered the same way — which meant "gross profit"
 * was really "revenue minus everything ever purchased", and a figure on this page
 * could disagree with the one on the profit and loss report. They now come from
 * the backend's calculation layer over validated records: revenue net of
 * returns, cost of goods from the cost frozen on each sale line rather than
 * today's purchase price, cash from recorded payments, and expenses from
 * approved claims only.
 *
 * <p>Where a figure needs a permission the reader does not hold, it is not
 * shown and the reason is stated. The panel is gated on
 * {@code FINANCIAL_REPORT_VIEW} for that reason — a cashier sees the operational
 * picture and is told why the money is not there, rather than being sent to a
 * screen that would not load either.
 */

export default function DashboardPage() {
  const { hasPermission, user } = useAuth();
  const [range, setRange] = useState<DateRange>(() => lastNDays(30));
  const [horizon, setHorizon] = useState(14);

  const maySeeMoney = hasPermission('FINANCIAL_REPORT_VIEW');
  const maySeeAi = hasPermission('AI_INSIGHT_VIEW') || hasPermission('AI_FORECAST_VIEW');

  const { data: products = [], isLoading: loadingProducts } = useProducts();

  const financial = useDashboardFinancial(range, maySeeMoney);
  const ai = useAiDashboard(range, horizon, maySeeAi);

  const money = financial.data;

  const stock = useMemo(() => {
    const low = products.filter(
      (p) => p.quantity > 0 && p.reorderLevel && p.quantity <= p.reorderLevel,
    );
    const out = products.filter((p) => p.quantity === 0);
    return { low, out };
  }, [products]);

  const percentChange = (current: number, previous: number) => {
    if (!previous) return null;
    return ((current - previous) / Math.abs(previous)) * 100;
  };

  const userRole = user?.role;
  const isElevatedRole =
    userRole === 'SUPER_ADMIN' || userRole === 'MANAGER' || userRole === 'ACCOUNTANT';
  const [activeTab, setActiveTab] = useState<'default' | 'cashier' | 'supervisor' | 'inventory' | 'accountant'>('default');

  if (userRole === 'CASHIER') {
    return <CashierDashboard />;
  }

  if (userRole === 'SUPERVISOR') {
    return <SupervisorDashboard />;
  }

  if (userRole === 'INVENTORY_STAFF' || userRole === 'STOREKEEPER') {
    return <InventoryStaffDashboard />;
  }

  if (userRole === 'ACCOUNTANT') {
    return <AccountantDashboard />;
  }

  return (
    <div className="space-y-6">
      {/* ── Role View Switcher for Administrators & Managers ──────────────── */}
      {isElevatedRole && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-200 dark:border-gray-800 pb-3">
          <div className="flex items-center gap-1.5 rounded-lg bg-gray-100 p-1 dark:bg-gray-800 text-xs font-medium">
            <button
              type="button"
              onClick={() => setActiveTab('default')}
              className={`rounded-md px-3 py-1.5 transition-colors ${
                activeTab === 'default'
                  ? 'bg-white shadow-sm text-blue-600 dark:bg-gray-700 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
              }`}
            >
              Executive BI
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('cashier')}
              className={`rounded-md px-3 py-1.5 transition-colors ${
                activeTab === 'cashier'
                  ? 'bg-white shadow-sm text-blue-600 dark:bg-gray-700 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
              }`}
            >
              Cashier Shift View
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('supervisor')}
              className={`rounded-md px-3 py-1.5 transition-colors ${
                activeTab === 'supervisor'
                  ? 'bg-white shadow-sm text-blue-600 dark:bg-gray-700 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
              }`}
            >
              Floor Supervisor View
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('inventory')}
              className={`rounded-md px-3 py-1.5 transition-colors ${
                activeTab === 'inventory'
                  ? 'bg-white shadow-sm text-blue-600 dark:bg-gray-700 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
              }`}
            >
              Inventory Staff View
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('accountant')}
              className={`rounded-md px-3 py-1.5 transition-colors ${
                activeTab === 'accountant'
                  ? 'bg-white shadow-sm text-blue-600 dark:bg-gray-700 dark:text-blue-400 font-semibold'
                  : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
              }`}
            >
              Accountant View
            </button>
          </div>
          <span className="text-xs text-gray-500">
            Active Role: <strong className="text-gray-800 dark:text-gray-200">{user?.role}</strong>
          </span>
        </div>
      )}

      {activeTab === 'cashier' && <CashierDashboard />}
      {activeTab === 'supervisor' && <SupervisorDashboard />}
      {activeTab === 'inventory' && <InventoryStaffDashboard />}
      {activeTab === 'accountant' && <AccountantDashboard />}

      {activeTab === 'default' && (
        <>
          {/* ── Controls ─────────────────────────────────────────────────────── */}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <DateRangeFilter value={range} onChange={setRange} />
            {financial.isFetching && (
              <span className="flex items-center gap-1.5 text-xs text-gray-400">
                <Loader2 size={13} className="animate-spin" /> Updating
              </span>
            )}
          </div>

      {/*
        Sections are declared once and ordered by role. A cashier's own trading
        comes first because that is their question; a supervisor's floor comes
        first because that is theirs. Every section is still gated on the
        permission its data requires, so ordering is presentation only — it
        cannot make an unreadable figure appear.
      */}
      <RoleOrderedSections order={sectionOrderFor(user?.role)}>
        <Section name="orders">
          <SalesOrderTable limit={8} />
        </Section>

      {financial.error && (
        <ErrorBanner
          title="Financial figures could not be loaded"
          error={financial.error}
          onRetry={() => financial.refetch()}
        >
          The operational figures below are unaffected — only the money is
          missing.
        </ErrorBanner>
      )}

      {/* ── Money ────────────────────────────────────────────────────────── */}
      <Section name="financials">
      {!maySeeMoney ? (
        <Card>
          <CardContent className="py-6 text-center">
            <Wallet size={20} className="mx-auto text-gray-400 mb-2" />
            <p className="text-sm font-medium text-gray-700 dark:text-gray-300">
              Financial figures need the financial reports permission
            </p>
            <p className="mt-1 text-xs text-gray-400 max-w-md mx-auto">
              Sales, costs, margins and balances are restricted to roles that are
              accountable for them. The operational picture below is still accurate.
            </p>
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
            {financial.isLoading || !money ? (
              Array.from({ length: 4 }).map((_, i) => <StatCardSkeleton key={i} />)
            ) : (
              <>
                <StatCard
                  title="Net Sales"
                  value={formatCurrency(money.netSales)}
                  change={percentChange(money.netSales, money.previousNetSales) ?? undefined}
                  changeLabel={`vs ${formatCurrency(money.previousNetSales)}`}
                  icon={<ShoppingCart size={22} />}
                  color="blue"
                />
                <StatCard
                  title="Gross Profit"
                  value={formatCurrency(money.grossProfit)}
                  change={
                    percentChange(money.grossProfit, money.previousGrossProfit) ??
                    undefined
                  }
                  changeLabel={`${money.grossMarginPercent.toFixed(1)}% margin`}
                  icon={<TrendingUp size={22} />}
                  color="emerald"
                />
                <StatCard
                  title="Net Profit"
                  value={formatCurrency(money.netProfit)}
                  change={
                    percentChange(money.netProfit, money.previousNetProfit) ??
                    undefined
                  }
                  changeLabel={`${money.netMarginPercent.toFixed(1)}% margin`}
                  icon={<TrendingUp size={22} />}
                  color="purple"
                />
                <StatCard
                  title="Net Cash Flow"
                  value={formatCurrency(money.netCashFlow)}
                  changeLabel={`${formatCurrency(money.cashReceived)} in, ${formatCurrency(money.cashPaidToSuppliers)} out`}
                  icon={<Banknote size={22} />}
                  color={money.netCashFlow >= 0 ? 'emerald' : 'red'}
                />
              </>
            )}
          </div>

          {money?.insufficientData && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-xs text-amber-800 dark:text-amber-200">
              <p className="font-medium">
                Very little trading is recorded in this period
              </p>
              <p className="mt-1">
                The figures above are calculated correctly, but they rest on too
                few transactions to mean much, and percentages built on them will
                swing wildly. {money.basis}
              </p>
            </div>
          )}

          <FinancialDetail money={money} range={range} />
        </>
      )}
      </Section>

      {/* ── Intelligence ─────────────────────────────────────────────────── */}
      <Section name="forecast">
      {maySeeAi && (
        <>
          {ai.error && (
            <ErrorBanner
              title="Analysis could not be produced"
              error={ai.error}
              onRetry={() => ai.refetch()}
            >
              Every figure above comes from the same records, so they are
              unaffected — only the forecasts and commentary are missing.
            </ErrorBanner>
          )}

          <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
            <div className="xl:col-span-2">
              <ForecastChart
                forecast={ai.data?.forecast ?? undefined}
                isLoading={ai.isLoading}
                horizon={horizon}
                onHorizonChange={setHorizon}
              />
            </div>
            <AssistantPanel range={range} />
          </div>

          <AiInsightPanel
            insights={ai.data?.insights}
            anomalies={ai.data?.anomalies}
            recommendations={ai.data?.recommendations}
            isLoading={ai.isLoading}
            error={ai.error}
            onRetry={() => ai.refetch()}
          />
        </>
      )}
      </Section>

      {/* ── Stock and operations ─────────────────────────────────────────── */}
      <Section name="stock">
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <StockAlerts low={stock.low} out={stock.out} loading={loadingProducts} />

        <WarehouseMovement enabled={maySeeAi} range={range} />

        <Card>
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
              <Package size={16} className="text-blue-500" /> Where to go next
            </h3>
          </CardHeader>
          <CardContent className="space-y-1.5 text-sm">
            <ReportLink href="/reports/financial" label="Income, expenses and cash flow" />
            <ReportLink href="/reports/sales" label="Sales and gross profit" />
            <ReportLink href="/reports/purchases" label="Purchases and supplier balances" />
            <ReportLink href="/reports/inventory-value" label="Inventory valuation" />
            <ReportLink href="/reports/inventory" label="Stock movements and transfers" />
            {maySeeMoney && (
              <ReportLink href="/finance/profit-loss" label="Profit and loss" />
            )}
          </CardContent>
        </Card>
      </div>
      </Section>
      </RoleOrderedSections>
        </>
      )}
    </div>
  );
}

// ── Role ordering ───────────────────────────────────────────────────────────

/**
 * Renders its children in the order the viewer's role asks for.
 *
 * <p>A child tagged with a section name goes where that name appears in the
 * order; anything untagged — the date filter, the three-column operations row —
 * stays at the end, where structural furniture belongs. Sections the role does
 * not name are still rendered: the order is a preference, not a filter, and a
 * card going missing because a role's list was not updated would be a silent
 * removal of information somebody is entitled to.
 */
function RoleOrderedSections({
  order,
  children,
}: {
  order: string[];
  children: React.ReactNode;
}) {
  const named = new Map<string, React.ReactNode>();
  const trailing: React.ReactNode[] = [];

  React.Children.forEach(children, (child) => {
    const name =
      (child as { props?: { name?: string; section?: string } })?.props?.name ??
      (child as { props?: { name?: string; section?: string } })?.props?.section;
    if (name) {
      named.set(name, child);
    } else {
      trailing.push(child);
    }
  });

  return (
    <>
      {order.map((name) => {
        const child = named.get(name);
        return child ? <React.Fragment key={name}>{child}</React.Fragment> : null;
      })}
      {trailing}
    </>
  );
}

/** Tags a block with the section name it is ordered by. Renders its children. */
function Section({
  name,
  children,
}: {
  name: string;
  children: React.ReactNode;
}) {
  return <div data-section={name}>{children}</div>;
}

// ── Financial detail ─────────────────────────────────────────────────────────

/**
 * The figures behind the headline numbers.
 *
 * <p>Each row states where its number comes from, because "cash received" and
 * "invoiced" are not the same thing and a reader who has been told that once
 * will not have to guess. The links open the report that produced each figure,
 * so nothing here is a number only this page can explain.
 */
function FinancialDetail({
  money,
  range,
}: {
  money: import('@/lib/api/aiApi').DashboardFinancial | undefined;
  range: DateRange;
}) {
  const [open, setOpen] = useState(false);
  if (!money) return null;

  const rows: Array<{ label: string; value: number; note: string }> = [
    {
      label: 'Sales before returns',
      value: money.grossSales,
      note: 'Everything invoiced in the period',
    },
    {
      label: 'Net sales',
      value: money.netSales,
      note: 'After approved sales returns. Sales tax is excluded — it is a liability, not income.',
    },
    {
      label: 'Cost of goods sold',
      value: money.cogs,
      note: 'The cost recorded on each sale line when it was sold, so a later price change cannot rewrite past profit.',
    },
    {
      label: 'Operating expenses',
      value: money.operatingExpenses,
      note: 'Approved claims only. A claim nobody has agreed to is not yet a cost.',
    },
    {
      label: 'Cash received from customers',
      value: money.cashReceived,
      note: 'Payments actually recorded. An unpaid invoice is not collected income.',
    },
    {
      label: 'Cash paid to suppliers',
      value: money.cashPaidToSuppliers,
      note: 'Supplier payments recorded as made.',
    },
    {
      label: 'Owed by customers',
      value: money.accountsReceivable,
      note: 'Invoiced and not yet paid.',
    },
    {
      label: 'Owed to suppliers',
      value: money.accountsPayable,
      note: 'Received and not yet paid. A purchase order is not a liability until the goods arrive.',
    },
    {
      label: 'Stock at cost',
      value: money.inventoryValue,
      note: 'Weighted-average cost of everything currently on hand.',
    },
    {
      label: 'Ordered, not yet received',
      value: money.purchaseCostOrderedNotReceived,
      note: 'Committed to suppliers. Shown separately because it is not yet stock and not yet a payable.',
    },
  ];

  return (
    <Card>
      <CardHeader>
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="flex w-full items-center justify-between text-left"
        >
          <div>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">
              Where these figures come from
            </h3>
            <p className="text-xs text-gray-400 mt-0.5">
              {formatDate(range.from)} – {formatDate(range.to)} ·{' '}
              {money.salesCount} sales, {money.itemsSold} units
            </p>
          </div>
          <span className="text-xs text-blue-600 shrink-0 ml-3">
            {open ? 'Hide' : 'Show'}
          </span>
        </button>
      </CardHeader>

      {open && (
        <CardContent className="pt-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs font-semibold uppercase text-gray-400">
                <th className="py-2 pr-4">Figure</th>
                <th className="py-2 pr-4 text-right">Amount</th>
                <th className="py-2">What it counts</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700/50">
              {rows.map((row) => (
                <tr key={row.label}>
                  <td className="py-2 pr-4 text-gray-700 dark:text-gray-300 whitespace-nowrap">
                    {row.label}
                  </td>
                  <td className="py-2 pr-4 text-right font-medium tabular-nums text-gray-800 dark:text-gray-200 whitespace-nowrap">
                    {formatCurrency(row.value)}
                  </td>
                  <td className="py-2 text-xs text-gray-500 dark:text-gray-400">
                    {row.note}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-3 text-xs text-gray-400">{money.basis}</p>
        </CardContent>
      )}
    </Card>
  );
}

// ── Stock alerts ─────────────────────────────────────────────────────────────

function StockAlerts({
  low,
  out,
  loading,
}: {
  low: any[];
  out: any[];
  loading: boolean;
}) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <AlertTriangle size={16} className="text-amber-500" /> Stock Alerts
          </h3>
          {low.length + out.length > 0 && (
            <Badge variant="warning">{low.length + out.length}</Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {loading ? (
          <div className="h-24 rounded-lg bg-gray-100 dark:bg-gray-700/50 animate-pulse" />
        ) : out.length === 0 && low.length === 0 ? (
          <div className="py-6 text-center">
            <p className="text-sm text-gray-500">Every product is above its reorder level</p>
            <p className="mt-1 text-xs text-gray-400">
              Nothing is out of stock and nothing is running low.
            </p>
          </div>
        ) : (
          <>
            {out.length > 0 && (
              <div className="bg-red-50 dark:bg-red-900/20 rounded-lg p-3">
                <p className="text-xs font-semibold text-red-700 dark:text-red-400 mb-2">
                  OUT OF STOCK ({out.length})
                </p>
                <ul className="space-y-1">
                  {out.slice(0, 5).map((p) => (
                    <li
                      key={p.id}
                      className="flex justify-between items-center text-sm"
                    >
                      <span className="text-gray-700 dark:text-gray-300 truncate">
                        {p.name}
                      </span>
                      <Badge variant="danger">0</Badge>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {low.length > 0 && (
              <div className="bg-amber-50 dark:bg-amber-900/20 rounded-lg p-3">
                <p className="text-xs font-semibold text-amber-700 dark:text-amber-400 mb-2">
                  LOW STOCK ({low.length})
                </p>
                <ul className="space-y-1">
                  {low.slice(0, 5).map((p) => (
                    <li
                      key={p.id}
                      className="flex justify-between items-center text-sm"
                    >
                      <span className="text-gray-700 dark:text-gray-300 truncate">
                        {p.name}
                      </span>
                      <Badge variant="warning">{p.quantity}</Badge>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {low.length + out.length > 10 && (
              <p className="text-xs text-gray-400 text-center">
                <Link href="/products" className="text-blue-600 hover:underline">
                  See all {low.length + out.length}
                </Link>
              </p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

// ── Warehouse movement ───────────────────────────────────────────────────────

/**
 * How much stock each warehouse moved in the period.
 *
 * <p>Units rather than value: a transfer moves stock between warehouses the
 * business already owns, so it changes no total and reporting it as cash in or
 * out would be wrong. Counting issued and received units per warehouse keeps
 * that distinction visible.
 */
function WarehouseMovement({
  enabled,
  range,
}: {
  enabled: boolean;
  range: DateRange;
}) {
  const [data, setData] = useState<
    Array<{
      warehouseId: number;
      unitsOnHand: number;
      unitsIssued: number;
      unitsReceived: number;
      unitsTransferred: number;
      turnoverRatio: number;
    }> | null
  >(null);
  const [warehousePanelFailed, setWarehousePanelFailed] = React.useState(false);
  const [warehousePanelReload, setWarehousePanelReload] = React.useState(0);

  // The panel honours the period the dashboard is filtered to. It asked for a
  // hardcoded 30 days while depending on `range`, so changing the date filter
  // re-fetched a number that never changed.
  const warehouseWindowDays = React.useMemo(() => {
    const from = new Date(range.from).getTime();
    const to = new Date(range.to).getTime();
    if (Number.isFinite(from) && Number.isFinite(to) && to >= from) {
      return Math.min(365, Math.max(1, Math.round((to - from) / 86_400_000) + 1));
    }
    return 30;
  }, [range.from, range.to]);

  React.useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    setWarehousePanelFailed(false);
    import('@/lib/api/aiApi')
      .then(({ aiApi }) => aiApi.warehouses(warehouseWindowDays))
      .then((rows) => {
        if (!cancelled) setData(rows);
      })
      .catch(() => {
        // A failure is not an absence of data. Leaving `data` null made the
        // panel render its loading skeleton forever, with no error and no way
        // to retry — indistinguishable from "still calculating".
        if (!cancelled) {
          setData(null);
          setWarehousePanelFailed(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [enabled, warehouseWindowDays, warehousePanelReload]);

  return (
    <Card>
      <CardHeader>
        <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
          <TruckIcon size={16} className="text-emerald-500" /> Warehouse Activity
        </h3>
      </CardHeader>
      <CardContent>
        {!enabled ? (
          <p className="py-6 text-center text-xs text-gray-400">
            Needs the inventory reporting permission.
          </p>
        ) : warehousePanelFailed ? (
          <div className="py-6 text-center">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Warehouse activity could not be loaded.
            </p>
            <button
              type="button"
              onClick={() => setWarehousePanelReload(n => n + 1)}
              className="mt-2 text-xs font-medium text-blue-600 hover:underline"
            >
              Try again
            </button>
          </div>
        ) : !data ? (
          <div className="h-24 rounded-lg bg-gray-100 dark:bg-gray-700/50 animate-pulse" />
        ) : data.length === 0 ? (
          <p className="py-6 text-center text-xs text-gray-400">
            No stock movements recorded in this period.
          </p>
        ) : (
          <ul className="space-y-2.5">
            {data.slice(0, 5).map((row) => (
              <li key={row.warehouseId} className="text-sm">
                <div className="flex justify-between items-center">
                  <span className="text-gray-700 dark:text-gray-300">
                    Warehouse {row.warehouseId}
                  </span>
                  <span className="text-xs text-gray-500 tabular-nums">
                    {row.unitsOnHand} on hand
                  </span>
                </div>
                <div className="mt-1 flex gap-2 text-[11px] text-gray-400">
                  <span className="inline-flex items-center gap-0.5">
                    <ArrowUpRight size={10} className="text-emerald-500" />
                    {row.unitsReceived} in
                  </span>
                  <span className="inline-flex items-center gap-0.5">
                    <ArrowDownRight size={10} className="text-red-400" />
                    {row.unitsIssued} out
                  </span>
                  {row.unitsTransferred > 0 && (
                    <span>{row.unitsTransferred} transferred</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
        <p className="mt-3 text-xs text-gray-400">
          A transfer moves stock between warehouses the business already owns. It
          is neither a sale nor a purchase, and it changes no total.
        </p>
      </CardContent>
    </Card>
  );
}

// ── Shared bits ──────────────────────────────────────────────────────────────

function ReportLink({ href, label }: { href: string; label: string }) {
  return (
    <Link
      href={href}
      className="flex items-center justify-between rounded-md px-2 py-1.5 text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/50 hover:text-blue-600 dark:hover:text-blue-400"
    >
      {label}
      <ArrowUpRight size={13} />
    </Link>
  );
}

function ErrorBanner({
  title,
  error,
  onRetry,
  children,
}: {
  title: string;
  error: unknown;
  onRetry: () => void;
  children?: React.ReactNode;
}) {
  const labelled = labelApiError(title, error);
  return (
    <div
      role="alert"
      className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
    >
      <p className="font-medium">
        {title}:{' '}
        {labelled.status === null
          ? 'cannot reach the server'
          : labelled.message}
      </p>
      {children && <p className="mt-1 text-xs">{children}</p>}
      <button
        type="button"
        onClick={onRetry}
        className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium hover:underline"
      >
        <RefreshCw size={12} /> Try again
      </button>
    </div>
  );
}