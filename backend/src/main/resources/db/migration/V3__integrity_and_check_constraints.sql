-- =============================================================================
-- StockFlow - Database Migration V3: Data Integrity & Check Constraints
-- Target Engine: PostgreSQL 14+
-- Idempotency: Uses dynamic catalog checks so already-applied constraints are skipped
-- =============================================================================

-- 1. Inventory & Stock Integrity: Prohibit negative inventory at DB level
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_quantity_non_negative') THEN
        ALTER TABLE products ADD CONSTRAINT chk_products_quantity_non_negative CHECK (quantity >= 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_warehouse_stock_quantity_non_negative') THEN
        ALTER TABLE warehouse_stock ADD CONSTRAINT chk_warehouse_stock_quantity_non_negative CHECK (quantity >= 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_stock_movements_qty_non_zero') THEN
        ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_qty_non_zero CHECK (quantity <> 0);
    END IF;
END $$;

-- 2. Line Item Integrity: Quantities must be strictly positive
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sale_order_items_qty_positive') THEN
        ALTER TABLE sale_order_items ADD CONSTRAINT chk_sale_order_items_qty_positive CHECK (quantity > 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_purchase_order_items_qty_positive') THEN
        ALTER TABLE purchase_order_items ADD CONSTRAINT chk_purchase_order_items_qty_positive CHECK (quantity > 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_return_order_items_qty_positive') THEN
        ALTER TABLE return_order_items ADD CONSTRAINT chk_return_order_items_qty_positive CHECK (quantity > 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sale_order_items_qty_returned') THEN
        ALTER TABLE sale_order_items ADD CONSTRAINT chk_sale_order_items_qty_returned CHECK (quantity_returned >= 0 AND quantity_returned <= quantity);
    END IF;
END $$;

-- 3. Financial & Monetary Integrity: Amounts must not be negative
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_payments_amount_positive') THEN
        ALTER TABLE payments ADD CONSTRAINT chk_payments_amount_positive CHECK (amount > 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_expenses_amount_positive') THEN
        ALTER TABLE expenses ADD CONSTRAINT chk_expenses_amount_positive CHECK (amount > 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sale_orders_amounts_non_negative') THEN
        ALTER TABLE sale_orders ADD CONSTRAINT chk_sale_orders_amounts_non_negative CHECK (
            total_amount >= 0 AND final_amount >= 0 AND amount_paid >= 0 AND discount >= 0 AND tax >= 0
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_purchase_orders_amounts_non_negative') THEN
        ALTER TABLE purchase_orders ADD CONSTRAINT chk_purchase_orders_amounts_non_negative CHECK (
            (total_amount IS NULL OR total_amount >= 0) AND amount_paid >= 0 AND discount >= 0 AND tax >= 0
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_customers_balances_non_negative') THEN
        ALTER TABLE customers ADD CONSTRAINT chk_customers_balances_non_negative CHECK (
            credit_limit >= 0 AND outstanding_balance >= 0
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_suppliers_balances_non_negative') THEN
        ALTER TABLE suppliers ADD CONSTRAINT chk_suppliers_balances_non_negative CHECK (
            outstanding_balance >= 0
        );
    END IF;
END $$;

-- 4. Status Consistency: Guard enum values
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_stock_movements_status') THEN
        ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_status CHECK (
            status IN ('PENDING', 'APPROVED', 'REJECTED')
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_payments_direction') THEN
        ALTER TABLE payments ADD CONSTRAINT chk_payments_direction CHECK (
            direction IN ('IN', 'OUT')
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_payments_status') THEN
        ALTER TABLE payments ADD CONSTRAINT chk_payments_status CHECK (
            status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')
        );
    END IF;
END $$;
