# How the financial figures are calculated

This document is the contract between the database and every number the
application shows. If a figure appears on two screens, both read it from the
code described here.

The single source of truth is
`backend/src/main/java/com/inventory/backend/finance/FinancialCalculations.java`.
It is pure — no database, no clock, no configuration — and it is unit-tested
directly. `FinancialCalculationService` assembles those results into a
`FinancialSummary` from the repositories; every report, the dashboard and the
assistant all consume that same summary.

## Why the arithmetic moved to the server

It used to happen in the browser. Each screen pulled the sale list, the purchase
list and the expense list, filtered them by date, and added them up. Four things
were wrong with that, and none of them were visible on the page:

| Problem | Consequence |
|---|---|
| The lists are paginated | A sale on page three did not appear, so the total was wrong by an amount the page could not reveal. |
| Cost used `product.purchasePrice` | The cost of goods sold was *today's* price applied to *last month's* sales. Editing a price silently rewrote historical profit. |
| Expenses were summed unfiltered | A pending claim nobody had approved was treated as a cost. |
| Tax was inside `finalAmount` | Revenue included money held for the tax authority, inflating every margin on the page. |

The dashboard's "gross profit" was not gross profit at all — it was
`revenue − purchases`, which subtracts stock that was never sold from stock that
was never bought.

## Costing method: moving weighted average

Stock is valued at a **moving weighted average cost**, recalculated on every
goods receipt:

```
new average cost = (value on hand + cost of this receipt) / (units on hand + units received)
```

Weighted average was chosen over FIFO deliberately:

- **There is no lot or batch tracking.** Nothing in the domain records which
  units came from which delivery, so FIFO could not be computed honestly from
  the data that exists.
- **Goods are fungible retail.** A shop's inventory does not preserve the order
  in which units arrived in any way an accountant could audit.
- **It matches the model already in use.** `Product.purchasePrice` is a single
  moving figure, not a queue of lots.

**The cost is frozen at the point of sale.** `SaleOrderItem.unitCost` is written
when the sale is made and never recomputed. Changing a product's price
afterwards changes what future sales cost; it cannot change what past sales
cost. Ledger rows carry the same snapshot, so a closed period cannot be restated.

## The formulas

Let `from` and `to` be inclusive dates.

### Revenue

```
gross sales  = Σ (line subtotal − order discount)   over non-cancelled sales in [from, to]
sales returns= Σ (approved sales returns in period)
net sales    = gross sales − sales returns
```

Sales tax is **excluded from revenue**. Tax collected is a liability owed to
the authority, not income. It is reported separately as `netSalesTax`.

### Cost of goods sold

```
COGS = Σ (line.unitCost × (line.quantity − line.quantityReturned))
```

Read from the cost frozen on the sale line. Where a line predates the cost
snapshot, the product's then-current purchase price is used and the line is
counted in `linesWithoutFrozenCost`, which the summary reports so the
proportion of COGS that is estimated rather than recorded is visible.

### Gross profit

```
gross profit  = net sales − COGS
gross margin  = gross profit ÷ net sales
```

### Operating expenses

```
operating expenses = Σ approved expenses dated in [from, to]
```

**Approved only.** A pending claim is not yet a cost. Approved expenses are
treated as paid on their transaction date; that is a stated policy, not an
accident, and it is why cash paid and expenses paid can differ for a period.

### Net profit

```
net profit  = gross profit − operating expenses
net margin  = net profit ÷ net sales
```

### Cash

```
cash received     = Σ completed inbound payments in period
cash paid out     = Σ completed outbound payments in period
net cash flow     = cash received − cash paid out
```

Cash comes from the **payment ledger**, never from invoices. An unpaid invoice
is not collected income and does not appear here; it appears in receivables.
This is the single most important distinction on the page, and it is why
payments cannot be created without a document — see *Payments* below.

### Balances

```
accounts receivable = Σ (finalAmount − amountPaid) over sales with a balance, as at the date
accounts payable    = Σ (balanceDue) over purchase orders with received goods, as at the date
```

Receivables consider sales up to the date. Payables consider only orders whose
**goods have been received** — an order in transit is neither stock nor a
liability, and counting it as one overstates what the business owes.

### Inventory

```
inventory value = Σ (averageCost × quantityOnHand)  per warehouse
```

Held per warehouse in `WarehouseStock`, summed for the business total.
`Product.quantity` is republished from those rows on every movement so that
every existing screen keeps working, but the warehouse rows are the record.

```
purchaseCostOrderedNotReceived = Σ (balanceDue) over orders with goods outstanding
```

Reported separately from payables for the same reason: it is a commitment, not
yet a liability.

### Loss and shrinkage

```
stock loss units = Σ |quantity| over DAMAGE, EXPIRY, LOSS, SHRINKAGE movements
stock loss value = Σ |quantity| × unitCost  over the same
```

## What is deliberately *not* counted

These are the distinctions the previous implementation got wrong. Each has a
test.

| Not counted as | Why |
|---|---|
| A warehouse **transfer** as a sale or purchase | The business already owns the stock. It moves; nothing is bought or sold, and no cash changes hands. |
| A **purchase order** as stock | An order is an intention. Goods are stock when received. |
| An **unpaid invoice** as income | Revenue is recognised on sale; cash is recognised on payment. Both are reported, separately, always. |
| A **pending expense** as a cost | Nobody has agreed to it. |
| **Sales tax** as revenue | Held on behalf of a third party. |
| A **pending return** as a reversal | Raising a return changes nothing. Approving it posts the stock movement and the financial reversal together. |

## Insufficient data

`FinancialSummary.insufficientData` is true when the period holds too little
trading for its percentages to mean anything. When it is set, the UI says so
next to the figures rather than presenting confident margins derived from three
sales. `basis` carries the exact rule, and is displayed.

## Payments cannot be free-standing

`POST /api/v1/payments` does not exist. The previous controller accepted a
`Payment` entity straight from the request body, which let a client choose its
own direction, party, status and amount — which is precisely how an unpaid
invoice gets reported as collected cash.

Payments are now recorded against a document:

| Endpoint | Effect |
|---|---|
| `POST /api/v1/payments/sales/{saleOrderId}` | Direction, party and status derived from the sale; `amountPaid` and `paymentStatus` updated from the ledger. |
| `POST /api/v1/payments/purchases/{purchaseOrderId}` | Same, from the purchase order. |
| `POST /api/v1/payments/refunds` | Money leaving because a sale was undone — a different event from a collection, and filed separately. |

Amounts are validated against the document. An amount larger than the balance is
refused.

## Returns

A return is a document with two states:

- **Raised** — records what is being returned and against which sale or order.
  Changes no balance. Quantities are capped at what is left of each line, so the
  same unit cannot be returned twice.
- **Approved** — posts the stock movement *and* the financial reversal
  together. Approving is a human decision, and it is the point at which the
  business accepts the goods back.

Returns are stored on the server. They previously lived in `localStorage`,
which meant they existed on one machine, appeared in no report, and were lost
when the browser cleared its storage.

## Stock movements

`StockLedgerService` is the only place a `StockMovement` row is written. Every
quantity change in the system — sale, transfer, issue, adjustment, count,
damage, expiry, return — goes through it, in one transaction.

A transfer writes two legs (out and in) sharing a `movementGroup`, so the report
shows one transfer with both ends rather than two unexplained rows.

### Shortfall policy

`StockShortfallPolicy` records a distinction that predates this work and is
preserved deliberately:

- **Approved ADJUSTMENT and STOCK_COUNT** clamp at zero, with a note saying the
  adjustment was floored. This is a deliberate override: a counted shelf of 20
  where the system holds 25 must be correctable, or the count is worthless.
- **Operational movements** (sale, transfer, issue, return) **refuse** a
  shortfall. Refusing is right here: the goods are gone and letting the
  quantity go negative would quietly understate cost and make the valuation
  wrong in a way nobody could trace.

## Comparing periods

Every summary is accompanied by the immediately preceding period of equal
length, so a percentage change always compares like with like. A "month on
month" figure computed against a different number of days is not a comparison.