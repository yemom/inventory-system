-- ─────────────────────────────────────────────────────────────────────────────
-- StockFlow performance indexes
--
-- These are applied by PerformanceIndexInitializer AFTER Hibernate has created
-- the schema (spring.jpa.hibernate.ddl-auto=update), because they reference
-- tables/columns that only exist once Hibernate has created them.
--
-- Every statement is idempotent (IF NOT EXISTS), so this file is safe to
-- re-run on every startup. Indexes are chosen from the actual repository
-- query patterns (filters + sorts + joins), not created speculatively.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Products: case-insensitive search on name / sku / barcode ───────────────
CREATE INDEX IF NOT EXISTS idx_products_name_lower      ON products (LOWER(name));
CREATE INDEX IF NOT EXISTS idx_products_sku_lower      ON products (LOWER(sku));
CREATE INDEX IF NOT EXISTS idx_products_barcode_lower  ON products (LOWER(barcode));

-- Dashboard stock alerts: filter active, order/threshold by quantity
CREATE INDEX IF NOT EXISTS idx_products_active_qty      ON products (active, quantity);

-- Product list filter by category
CREATE INDEX IF NOT EXISTS idx_products_category       ON products (category_id);

-- ── Sale orders: status / date / relation filters used by list + reports ────
CREATE INDEX IF NOT EXISTS idx_sale_orders_status       ON sale_orders (status);
CREATE INDEX IF NOT EXISTS idx_sale_orders_created_at  ON sale_orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sale_orders_customer    ON sale_orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_sale_orders_created_by  ON sale_orders (created_by_id);
CREATE INDEX IF NOT EXISTS idx_sale_orders_pay_method  ON sale_orders (payment_method);

-- Idempotency lookup on create-sale (nullable column, so plain index)
CREATE INDEX IF NOT EXISTS idx_sale_orders_idempotency  ON sale_orders (idempotency_key);

-- ── Sale order items: joins back to order and product ──────────────────────
CREATE INDEX IF NOT EXISTS idx_sale_items_sale_order    ON sale_order_items (sale_order_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product       ON sale_order_items (product_id);

-- ── Purchase orders: status / date filters ─────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status       ON purchase_orders (status);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_created_at  ON purchase_orders (created_at DESC);

-- ── Purchase order items: joins back to order and product ──────────────────
CREATE INDEX IF NOT EXISTS idx_purchase_items_order   ON purchase_order_items (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_purchase_items_product ON purchase_order_items (product_id);

-- ── Stock movements: history by product / warehouse / type, newest first ───
CREATE INDEX IF NOT EXISTS idx_stock_moves_product     ON stock_movements (product_id);
CREATE INDEX IF NOT EXISTS idx_stock_moves_warehouse   ON stock_movements (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stock_moves_type        ON stock_movements (type);
CREATE INDEX IF NOT EXISTS idx_stock_moves_created_at  ON stock_movements (created_at DESC);

-- ── Customers: search + status filter ──────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_customers_name_lower    ON customers (LOWER(name));
CREATE INDEX IF NOT EXISTS idx_customers_status        ON customers (status);
CREATE INDEX IF NOT EXISTS idx_customers_created_at    ON customers (created_at DESC);

-- ── Suppliers: search + status filter ─────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_suppliers_company_lower ON suppliers (LOWER(company_name));
CREATE INDEX IF NOT EXISTS idx_suppliers_status        ON suppliers (status);

-- ── Users: role / status / tenant scoping ─────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_role              ON users (role_id);
CREATE INDEX IF NOT EXISTS idx_users_status            ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_tenant            ON users (tenant_id);

-- ── Payments: lookup by order reference + reporting filters ────────────────
CREATE INDEX IF NOT EXISTS idx_payments_order_ref      ON payments (order_reference);
CREATE INDEX IF NOT EXISTS idx_payments_type           ON payments (type);
CREATE INDEX IF NOT EXISTS idx_payments_created_at     ON payments (created_at DESC);

-- ── Audit logs: filter by user, newest first ───────────────────────────────
CREATE INDEX IF NOT EXISTS idx_audit_logs_user         ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp    ON audit_logs (timestamp DESC);

-- ── Categories / warehouses: cheap reference-data filters ─────────────────
CREATE INDEX IF NOT EXISTS idx_categories_parent      ON categories (parent_id);
CREATE INDEX IF NOT EXISTS idx_warehouses_active      ON warehouses (active);