-- =============================================================================
-- StockFlow - Database Migration V2: Performance & Query Optimization Indexes
-- Target Engine: PostgreSQL 14+
-- Idempotency: Uses IF NOT EXISTS
-- =============================================================================

-- Products: Search, filtering, and alert indexes
CREATE INDEX IF NOT EXISTS idx_products_name_lower      ON products (LOWER(name));
CREATE INDEX IF NOT EXISTS idx_products_sku_lower       ON products (LOWER(sku));
CREATE INDEX IF NOT EXISTS idx_products_barcode_lower   ON products (LOWER(barcode));
CREATE INDEX IF NOT EXISTS idx_products_active_qty      ON products (active, quantity);
CREATE INDEX IF NOT EXISTS idx_products_category        ON products (category_id);

-- Warehouses
CREATE INDEX IF NOT EXISTS idx_warehouses_active        ON warehouses (active);

-- Categories
CREATE INDEX IF NOT EXISTS idx_categories_parent        ON categories (parent_id);

-- Warehouse Stock: Multi-warehouse lookup indexes
CREATE INDEX IF NOT EXISTS idx_warehouse_stock_product   ON warehouse_stock (product_id);
CREATE INDEX IF NOT EXISTS idx_warehouse_stock_warehouse ON warehouse_stock (warehouse_id);

-- Stock Movements: Ledger history and balance calculation indexes
CREATE INDEX IF NOT EXISTS idx_stock_moves_product      ON stock_movements (product_id);
CREATE INDEX IF NOT EXISTS idx_stock_moves_warehouse    ON stock_movements (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stock_moves_type         ON stock_movements (type);
CREATE INDEX IF NOT EXISTS idx_stock_moves_created_at   ON stock_movements (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stock_moves_group        ON stock_movements (movement_group);
CREATE INDEX IF NOT EXISTS idx_stock_moves_prod_wh_stat ON stock_movements (product_id, warehouse_id, status);

-- Customers & Suppliers: Search and status filter indexes
CREATE INDEX IF NOT EXISTS idx_customers_name_lower     ON customers (LOWER(name));
CREATE INDEX IF NOT EXISTS idx_customers_status         ON customers (status);
CREATE INDEX IF NOT EXISTS idx_customers_created_at     ON customers (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_suppliers_company_lower  ON suppliers (LOWER(company_name));
CREATE INDEX IF NOT EXISTS idx_suppliers_status         ON suppliers (status);

-- Users: Authorization and tenant lookup indexes
CREATE INDEX IF NOT EXISTS idx_users_role               ON users (role_id);
CREATE INDEX IF NOT EXISTS idx_users_status             ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_tenant             ON users (tenant_id);

-- Sale Orders: Reporting, filtering, and idempotency indexes
CREATE INDEX IF NOT EXISTS idx_sale_orders_status        ON sale_orders (status);
CREATE INDEX IF NOT EXISTS idx_sale_orders_created_at    ON sale_orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sale_orders_customer      ON sale_orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_sale_orders_created_by    ON sale_orders (created_by_id);
CREATE INDEX IF NOT EXISTS idx_sale_orders_pay_method    ON sale_orders (payment_method);
CREATE INDEX IF NOT EXISTS idx_sale_orders_idempotency   ON sale_orders (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_sale_orders_report        ON sale_orders (transaction_date, status);

-- Sale Order Items: Join back to parent and product
CREATE INDEX IF NOT EXISTS idx_sale_items_sale_order     ON sale_order_items (sale_order_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product        ON sale_order_items (product_id);

-- Purchase Orders: Status, date, and report indexes
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status    ON purchase_orders (status);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_created_at ON purchase_orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_report    ON purchase_orders (transaction_date, status);

-- Purchase Order Items: Join back to parent and product
CREATE INDEX IF NOT EXISTS idx_purchase_items_order      ON purchase_order_items (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_purchase_items_product    ON purchase_order_items (product_id);

-- Return Orders & Return Order Items
CREATE INDEX IF NOT EXISTS idx_return_orders_created_at  ON return_orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_return_orders_status      ON return_orders (status);
CREATE INDEX IF NOT EXISTS idx_return_items_order        ON return_order_items (return_order_id);
CREATE INDEX IF NOT EXISTS idx_return_items_product      ON return_order_items (product_id);

-- Payments: Cash flow and financial reporting indexes
CREATE INDEX IF NOT EXISTS idx_payments_direction        ON payments (direction);
CREATE INDEX IF NOT EXISTS idx_payments_transaction_date ON payments (transaction_date);
CREATE INDEX IF NOT EXISTS idx_payments_sale_order       ON payments (sale_order_id);
CREATE INDEX IF NOT EXISTS idx_payments_purchase_order   ON payments (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_payments_order_ref        ON payments (order_reference);
CREATE INDEX IF NOT EXISTS idx_payments_type             ON payments (type);
CREATE INDEX IF NOT EXISTS idx_payments_created_at       ON payments (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_report           ON payments (transaction_date, status, direction);

-- Expenses: P&L reporting indexes
CREATE INDEX IF NOT EXISTS idx_expenses_transaction_date ON expenses (transaction_date);
CREATE INDEX IF NOT EXISTS idx_expenses_report           ON expenses (transaction_date, status);

-- Audit Logs
CREATE INDEX IF NOT EXISTS idx_audit_logs_user          ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp     ON audit_logs (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity        ON audit_logs (entity_type, entity_id);

-- AI & Chatbot
CREATE INDEX IF NOT EXISTS idx_ai_model_key_active      ON ai_model_versions (model_key, active);
CREATE INDEX IF NOT EXISTS idx_ai_model_trained_at      ON ai_model_versions (trained_at);
CREATE INDEX IF NOT EXISTS idx_ai_query_log_user        ON ai_query_log (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_query_log_created_at  ON ai_query_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_conv       ON chat_messages (conversation_id);
CREATE INDEX IF NOT EXISTS idx_chat_tickets_status      ON chat_support_tickets (status);
