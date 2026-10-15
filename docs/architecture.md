# Architecture

A Spring Boot 3.2.3 API and a Next.js 15 frontend, PostgreSQL for persistence
and Redis for rate limiting and caching.

```
┌──────────────────────────┐
│  Next.js frontend (3005) │
└────────────┬─────────────┘
             │ HTTPS / JSON, JWT bearer token
┌────────────▼─────────────────────────────────────────────┐
│  Spring Boot API (8080)                                   │
│                                                          │
│  controller/   HTTP, authorisation, serialisation        │
│  service/      business rules, transactions              │
│  finance/      ← the single source of financial truth    │
│  ai/           ← read-only: forecast, alerts, assistant  │
│  security/     roles → permissions, JWT                  │
│  repository/   Spring Data JPA                           │
└──────┬─────────────────────────────┬─────────────────────┘
       │                             │
┌──────▼────────┐            ┌───────▼────────┐
│  PostgreSQL   │            │  Redis         │
│  (transactions│            │  rate limits,  │
│   + ledger)   │            │  cache         │
└───────────────┘            └────────────────┘
```

## The three layers that matter

Most of the application's value is concentrated in three places. Everything else
is ordinary CRUD around them.

### 1. `finance/FinancialCalculations` — the only place a formula lives

Pure, static, no database, no clock. Every figure on every screen comes from
here. Unit-tested directly.

This exists because the previous implementation calculated financials in the
browser, once per screen, slightly differently each time. The full reasoning is
in [financial-calculations.md](financial-calculations.md).

`FinancialCalculationService` assembles a `FinancialSummary` — one fetch per
source record, no N+1 — and every report, the dashboard and the assistant read
that same summary.

### 2. `service/StockLedgerService` — the only place a movement row is written

Every quantity change in the system goes through it, in one transaction: sale,
transfer, issue, adjustment, count, damage, expiry, return.

A transfer writes two legs sharing a `movementGroup`, so the report shows one
transfer with both ends rather than two unexplained rows.

`WarehouseStockService` maintains per-warehouse stock with locking, recalculating
the weighted-average cost on every goods receipt and republishing
`Product.quantity` from those rows so every existing screen keeps working.

`StockShortfallPolicy` holds a distinction that predates this work: approved
adjustments and counts clamp at zero (a count must be correctable), while
operational movements refuse a shortfall (letting a sale drive stock negative
would quietly understate cost).

### 3. `ai/` — read-only analysis

Forecasting, anomaly detection, inventory advice, financial explanations and the
assistant. No write methods, no outbound calls, no provider credentials. It reads
the same tables under the same permissions as the reports.

Fully described in [intelligence.md](intelligence.md).

## Data model

Entities are grouped by what they are for.

**Trading documents** — `SaleOrder`, `SaleOrderItem`, `PurchaseOrder`,
`PurchaseOrderItem`, `ReturnOrder`, `ReturnOrderItem`, `Payment`, `Expense`.
Each carries `transactionDate` as well as `createdAt`, because the date a
transaction happened is not the date it was keyed in.

**Stock** — `WarehouseStock` (per-warehouse quantity and average cost),
`StockMovement` (the ledger, written only by `StockLedgerService`),
`Product`, `Warehouse`.

**Intelligence bookkeeping** — `AiModelVersion` (training period, sample size,
measured error, candidate scores) and `AiQueryLog` (question, intent, resolved
parameters, outcome). Both exist so a figure already reported can be traced back
to the model and the records that produced it.

### Cost snapshots

`SaleOrderItem.unitCost` and `StockMovement.unitCost` are written at the moment
of the transaction and never recomputed. A price change affects future sales;
it cannot rewrite history, and a closed period cannot be restated.

### Reconciliation of `Product.quantity`

`Product.quantity` is a business-wide total, republished from the
`WarehouseStock` rows on every movement. It is kept rather than removed because
every existing screen and test reads it. The warehouse rows are the record; this
is a projection of them.

## Security

Authentication is JWT; authorisation is role → permission, resolved in
`RolePermissionCatalog` and enforced with `@PreAuthorize` on every endpoint. The
frontend's navigation is gated on the same permission strings, read from the
token — it decides what to *show*, while the endpoint's own check is what
actually protects the data.

Financial reporting moved off `DASHBOARD_VIEW` — which every role holds — onto
`FINANCIAL_REPORT_VIEW`, `PROFIT_LOSS_READ`, `RECEIVABLE_READ` and
`PAYABLE_READ`. A cashier sees the operational dashboard and is told why the
money is not there, rather than being sent to a screen that would not load.

AI permissions were added: `AI_INSIGHT_VIEW`, `AI_FORECAST_VIEW`,
`AI_ASSISTANT_USE`, `AI_MODEL_VIEW`, `AI_QUERY_LOG_VIEW`. There are deliberately
**no AI write or approve permissions**, because the AI layer has no write
methods to gate.

See [roles-and-permissions.md](roles-and-permissions.md).

## API shape

| Prefix | Purpose |
|---|---|
| `/api/v1/sales`, `/purchases`, `/returns`, `/payments`, `/expenses` | Transaction documents |
| `/api/v1/inventory`, `/stock-movements`, `/warehouses`, `/transfers` | Stock |
| `/api/v1/dashboard/stats` | Operational counts. `DASHBOARD_VIEW`. No money. |
| `/api/v1/dashboard/financial` | The financial picture. `FINANCIAL_REPORT_VIEW`. |
| `/api/v1/dashboard/insights` | Alerts and advice. `AI_INSIGHT_VIEW`. |
| `/api/v1/finance/*` | Summary, cash flow, receivables, periods |
| `/api/v1/reports/*` | Fourteen reports and CSV/PDF export |
| `/api/v1/ai/*` | Forecast, anomalies, reorder, stockouts, assistant, models |

Responses are wrapped in `ApiResponse`. Exports are produced on the server from
the same endpoint that serves the screen, so an exported file can never contain
a figure the screen did not show.

## Reporting

`ReportService` returns a consistent shape for every report:

```json
{ "report": "...", "from": "...", "to": "...",
  "columns": [...], "rows": [...], "totals": {...} }
```

One shape means one frontend component can render all fourteen
(`ReportScreen`), which is why adding a report is a controller method and a
column list rather than a new page. Every report takes a date filter, and the
totals are computed by the same code that produced the rows, so the footer always
adds up.

Exports use OpenPDF and hand-written CSV — no extra dependency for two formats,
and the output is produced from the same data as the screen.

## Frontend

Next.js App Router, React Query for server state, Tailwind for styling.

The important structural change is that financial pages no longer compute. The
dashboard, profit and loss, receivables, payables and every report now read
backend figures; the browser formats and links, and does not add. Where a figure
needs a permission the reader does not hold, the panel says so rather than
showing a number or silently rendering nothing.

`components/reports/ReportScreen.tsx` renders any report from its column list,
with pagination, search, date filtering, CSV/PDF export, and distinct loading,
empty and error states — the last being the one that matters most, because an
empty table for "no transactions" and an empty table for "the request failed"
look identical and mean opposite things.

## Testing

**Backend: 362 tests across 58 classes**, all passing. Unit tests for the
financial formulas and the time-series statistics; integration tests against a
real PostgreSQL via Testcontainers for sales persistence, purchasing and
warehousing, returns and expenses, authorisation and approval workflows, and the
intelligence layer.

**Frontend: 191 tests across 16 suites**, all passing, plus a clean
`type-check`, `lint` (0 errors) and production `build`.

Test data is written by fixtures that stamp `TESTDATA-` on every reference they
create, and live only under `src/test/java`.

## Deliberate limitations

- **No lot or batch tracking.** Goods are fungible, so costing is a moving
  weighted average. FIFO would require data that is not captured.
- **Supplier lead times are a default.** They are not yet measured from order
  dates; every recommendation says so explicitly.
- **Seasonality is weekly only.** Annual seasonality needs more than a few years
  of history, which most of this data does not yet have.
- **No multi-currency arithmetic.** `currency` is carried on orders for
  provenance; conversion is not performed.
- **Forecasts are recomputed per request** rather than on a schedule. They are
  fast at this data volume and stay correct as new sales arrive; a scheduled job
  with cached results would be the next step for a large deployment.