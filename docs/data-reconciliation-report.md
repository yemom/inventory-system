# StockFlow Data Reconciliation Report

## Executive Summary

This report defines the reconciliation framework and audit verification queries required to validate that existing production records remain mathematically and operationally consistent across all schema migrations, constraint additions, and indexing changes.

StockFlow operates on double-entry-style inventory accounting:
1. `products.quantity` is the business-wide aggregated quantity.
2. `warehouse_stock.quantity` is the localized quantity held at a specific physical warehouse.
3. `stock_movements` is the immutable, auditable transaction ledger of all inventory events.

Every valid state must satisfy:
$$\text{products.quantity} = \sum_{\text{warehouses}} \text{warehouse\_stock.quantity} = \sum_{\text{movements}} \text{approved\_quantity}$$

---

## 1. Domain Reconciliation Rules & Invariants

| Domain | Invariant Condition | Validation Logic |
| :--- | :--- | :--- |
| **Catalog vs Warehouse** | Global quantity must equal the sum of localized stock | `products.quantity = SUM(warehouse_stock.quantity)` |
| **Warehouse vs Ledger** | Current warehouse balance must equal sum of approved movements | `warehouse_stock.quantity = SUM(stock_movements.quantity WHERE status = 'APPROVED')` |
| **Purchase Receipts** | Received quantities cannot exceed ordered quantities | `purchase_order_items.quantity_received <= purchase_order_items.quantity` |
| **Sales Attribution** | Sale line returns cannot exceed line sold quantity | `sale_order_items.quantity_returned <= sale_order_items.quantity` |
| **Sales Settlement** | Sale payment balance must equal total minus completed payments | `sale_orders.amount_paid = SUM(payments.amount WHERE status = 'COMPLETED')` |
| **Accounts Receivable** | Customer outstanding balance must equal unpaid sales | `customer.outstanding_balance = SUM(sale_orders.final_amount - amount_paid)` |
| **Accounts Payable** | Supplier outstanding balance must equal unpaid received POs | `supplier.outstanding_balance = SUM(purchase_orders.total_amount - amount_paid)` |

---

## 2. Automated Reconciliation Queries

### 2.1 Inventory Ledger & Stock Balance Reconciliation

This query compares the aggregate quantity recorded in `products`, the physical sum recorded in `warehouse_stock`, and the chronological sum of all approved movements in `stock_movements`:

```sql
WITH ledger_summary AS (
    SELECT 
        product_id,
        warehouse_id,
        COALESCE(SUM(quantity), 0) AS ledger_qty
    FROM stock_movements
    WHERE status = 'APPROVED'
    GROUP BY product_id, warehouse_id
),
stock_summary AS (
    SELECT 
        ws.product_id,
        ws.warehouse_id,
        ws.quantity AS physical_qty,
        COALESCE(ls.ledger_qty, 0) AS ledger_qty,
        (ws.quantity - COALESCE(ls.ledger_qty, 0)) AS variance
    FROM warehouse_stock ws
    LEFT JOIN ledger_summary ls 
        ON ls.product_id = ws.product_id 
       AND ls.warehouse_id = ws.warehouse_id
)
SELECT 
    p.id AS product_id,
    p.name AS product_name,
    p.sku,
    p.quantity AS catalog_total_qty,
    COALESCE(SUM(ss.physical_qty), 0) AS total_physical_qty,
    COALESCE(SUM(ss.ledger_qty), 0) AS total_ledger_qty,
    (p.quantity - COALESCE(SUM(ss.physical_qty), 0)) AS catalog_physical_discrepancy,
    (COALESCE(SUM(ss.physical_qty), 0) - COALESCE(SUM(ss.ledger_qty), 0)) AS physical_ledger_discrepancy
FROM products p
LEFT JOIN stock_summary ss ON ss.product_id = p.id
GROUP BY p.id, p.name, p.sku, p.quantity
HAVING p.quantity <> COALESCE(SUM(ss.physical_qty), 0) 
    OR COALESCE(SUM(ss.physical_qty), 0) <> COALESCE(SUM(ss.ledger_qty), 0);
```

*Expected Result:* **0 rows returned.** Any row returned indicates unposted movements or untracked direct modifications.

---

### 2.2 Sales Settlement & Payment Reconciliation

Verifies that `sale_orders.amount_paid` matches the exact sum of completed inbound customer payments:

```sql
SELECT 
    s.id AS sale_id,
    s.order_number,
    s.final_amount,
    s.amount_paid AS recorded_paid,
    COALESCE(SUM(p.amount), 0) AS payments_sum,
    (s.amount_paid - COALESCE(SUM(p.amount), 0)) AS discrepancy
FROM sale_orders s
LEFT JOIN payments p 
    ON p.sale_order_id = s.id 
   AND p.status = 'COMPLETED' 
   AND p.direction = 'IN'
GROUP BY s.id, s.order_number, s.final_amount, s.amount_paid
HAVING s.amount_paid <> COALESCE(SUM(p.amount), 0);
```

*Expected Result:* **0 rows returned.**

---

### 2.3 Purchasing Receipts vs Purchase Orders

Verifies that purchase orders marked `RECEIVED` have corresponding stock movement receipts and that received quantities match line items:

```sql
SELECT 
    po.id AS po_id,
    po.order_number,
    po.status AS po_status,
    poi.product_id,
    poi.quantity AS ordered_qty,
    poi.quantity_received AS line_received_qty,
    COALESCE(SUM(sm.quantity), 0) AS ledger_received_qty,
    (poi.quantity_received - COALESCE(SUM(sm.quantity), 0)) AS variance
FROM purchase_orders po
JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
LEFT JOIN stock_movements sm 
    ON sm.reference = po.order_number 
   AND sm.product_id = poi.product_id 
   AND sm.type = 'PURCHASE_RECEIPT'
   AND sm.status = 'APPROVED'
WHERE po.status IN ('RECEIVED', 'PARTIALLY_RECEIVED')
GROUP BY po.id, po.order_number, po.status, poi.product_id, poi.quantity, poi.quantity_received
HAVING poi.quantity_received <> COALESCE(SUM(sm.quantity), 0);
```

*Expected Result:* **0 rows returned.**

---

### 2.4 Sales Return Restock Audit

Ensures that approved sales returns have returned stock back into the specified warehouse:

```sql
SELECT 
    ro.id AS return_id,
    ro.return_number,
    roi.product_id,
    roi.quantity AS returned_qty,
    COALESCE(SUM(sm.quantity), 0) AS ledger_restocked_qty
FROM return_orders ro
JOIN return_order_items roi ON roi.return_order_id = ro.id
LEFT JOIN stock_movements sm 
    ON sm.reference = ro.return_number 
   AND sm.product_id = roi.product_id 
   AND sm.type = 'SALES_RETURN' 
   AND sm.status = 'APPROVED'
WHERE ro.status = 'APPROVED' AND ro.return_type = 'SALES_RETURN'
GROUP BY ro.id, ro.return_number, roi.product_id, roi.quantity
HAVING roi.quantity <> COALESCE(SUM(sm.quantity), 0);
```

*Expected Result:* **0 rows returned.**

---

## 3. Backfill Reconciliation for Legacy Records

During migration from early versions of the database, specific historical gaps are resolved systematically:

1. **Legacy Stock Movement Status:**
   - Rows written prior to the approval workflow possessed `status = NULL`.
   - `StockMovementStatusBackfill` automatically backfilled these rows to `status = 'APPROVED'` because their stock adjustments had already taken effect immediately at transaction time.
2. **Legacy Warehouse Attribution:**
   - Sales or purchases created before multi-warehouse functionality possessed `warehouse_id = NULL`.
   - `WarehouseStockBackfillRunner` assigns unassigned legacy movements to the default `Main Warehouse` (ID = 1).
3. **Legacy Sale Order Items Unit Cost:**
   - Lines created before historical COGS snapshotting carried `unit_cost = NULL`.
   - Financial reports fall back gracefully to `product.purchase_price` for legacy lines while flagging them as estimated.
