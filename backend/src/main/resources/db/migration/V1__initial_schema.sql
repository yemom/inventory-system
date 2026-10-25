-- =============================================================================
-- StockFlow - Database Migration V1: Initial Schema
-- Target Engine: PostgreSQL 14+
-- Idempotency: Uses IF NOT EXISTS to support clean and existing environments
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Identity & Authorization Domain
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS roles (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(50) NOT NULL UNIQUE,
    description         VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS permissions (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id             BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id       BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS users (
    id                      BIGSERIAL PRIMARY KEY,
    username                VARCHAR(60) NOT NULL UNIQUE,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    email                   VARCHAR(120) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    phone                   VARCHAR(30),
    gender                  VARCHAR(20),
    date_of_birth           DATE,
    address                 VARCHAR(1000),
    profile_photo_url       VARCHAR(500),
    employee_id             VARCHAR(50) UNIQUE,
    job_title               VARCHAR(100),
    department              VARCHAR(100),
    branch                  VARCHAR(100),
    warehouse               VARCHAR(100),
    tenant_id               VARCHAR(100),
    date_joined             DATE,
    role_id                 BIGINT REFERENCES roles(id) ON DELETE RESTRICT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at           TIMESTAMP,
    password_reset_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted              BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 2. Product Catalog & Inventory Domain
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS categories (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(255),
    parent_id           BIGINT REFERENCES categories(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS products (
    id                  BIGSERIAL PRIMARY KEY,
    sku                 VARCHAR(50) NOT NULL UNIQUE,
    barcode             VARCHAR(100),
    name                VARCHAR(200) NOT NULL,
    unit                VARCHAR(30),
    description         TEXT,
    category_id         BIGINT REFERENCES categories(id) ON DELETE RESTRICT,
    purchase_price      NUMERIC(15,2),
    selling_price       NUMERIC(15,2),
    min_selling_price   NUMERIC(15,2),
    min_stock_level     INT DEFAULT 0,
    max_stock_level     INT,
    reorder_level       INT DEFAULT 0,
    batch_tracked       BOOLEAN NOT NULL DEFAULT FALSE,
    expiry_tracked      BOOLEAN NOT NULL DEFAULT FALSE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    quantity            INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS warehouses (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL UNIQUE,
    location            VARCHAR(255),
    manager_name        VARCHAR(100),
    contact_phone       VARCHAR(30),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS warehouse_stock (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    warehouse_id        BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
    quantity            INT NOT NULL DEFAULT 0,
    average_cost        NUMERIC(15,4) DEFAULT 0.0000,
    last_movement_at    TIMESTAMP,
    CONSTRAINT uk_warehouse_stock_product_warehouse UNIQUE (product_id, warehouse_id)
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id                          BIGSERIAL PRIMARY KEY,
    product_id                  BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    warehouse_id                BIGINT REFERENCES warehouses(id) ON DELETE RESTRICT,
    destination_warehouse_id    BIGINT REFERENCES warehouses(id) ON DELETE RESTRICT,
    type                        VARCHAR(40) NOT NULL,
    quantity                    INT NOT NULL,
    reference                   VARCHAR(80),
    reason                      VARCHAR(255),
    movement_group              VARCHAR(80),
    unit_cost                   NUMERIC(15,4),
    notes                       VARCHAR(500),
    created_by_id               BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    approved_by_id              BIGINT REFERENCES users(id) ON DELETE SET NULL,
    approved_at                 TIMESTAMP,
    review_note                 VARCHAR(500),
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 3. Customers & Suppliers Domain
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS customers (
    id                  BIGSERIAL PRIMARY KEY,
    customer_number     VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    phone               VARCHAR(30),
    email               VARCHAR(120),
    address             VARCHAR(500),
    customer_type       VARCHAR(30) NOT NULL DEFAULT 'RETAIL',
    credit_limit        NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    outstanding_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS suppliers (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_number     VARCHAR(50) NOT NULL UNIQUE,
    company_name        VARCHAR(150) NOT NULL,
    contact_person      VARCHAR(100),
    phone               VARCHAR(30),
    email               VARCHAR(120),
    address             VARCHAR(500),
    tax_number          VARCHAR(50),
    payment_terms       VARCHAR(60),
    outstanding_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 4. Purchasing Domain
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS purchase_orders (
    id                      BIGSERIAL PRIMARY KEY,
    order_number            VARCHAR(50) NOT NULL UNIQUE,
    supplier_name           VARCHAR(150),
    supplier_id             BIGINT REFERENCES suppliers(id) ON DELETE RESTRICT,
    warehouse_id            BIGINT REFERENCES warehouses(id) ON DELETE RESTRICT,
    total_amount            NUMERIC(15,2),
    status                  VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    payment_status          VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    amount_paid             NUMERIC(15,2) DEFAULT 0.00,
    payment_terms           VARCHAR(60),
    due_date                DATE,
    expected_delivery_date  DATE,
    tax                     NUMERIC(15,2) DEFAULT 0.00,
    discount                NUMERIC(15,2) DEFAULT 0.00,
    currency                VARCHAR(3) NOT NULL DEFAULT 'ETB',
    created_by_id           BIGINT REFERENCES users(id) ON DELETE SET NULL,
    transaction_date        TIMESTAMP,
    approved_by_id          BIGINT REFERENCES users(id) ON DELETE SET NULL,
    approved_at             TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id                  BIGSERIAL PRIMARY KEY,
    purchase_order_id   BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity            INT NOT NULL,
    quantity_received   INT DEFAULT 0,
    quantity_returned   INT DEFAULT 0,
    unit_cost           NUMERIC(15,4) NOT NULL,
    subtotal            NUMERIC(15,2) NOT NULL,
    tax                 NUMERIC(15,2) DEFAULT 0.00,
    discount            NUMERIC(15,2) DEFAULT 0.00
);

-- -----------------------------------------------------------------------------
-- 5. Sales Domain
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sale_orders (
    id                      BIGSERIAL PRIMARY KEY,
    order_number            VARCHAR(50) NOT NULL UNIQUE,
    customer_name           VARCHAR(150),
    customer_id             BIGINT REFERENCES customers(id) ON DELETE RESTRICT,
    warehouse_id            BIGINT REFERENCES warehouses(id) ON DELETE RESTRICT,
    total_amount            NUMERIC(15,2) NOT NULL,
    discount                NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    tax                     NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    final_amount            NUMERIC(15,2) NOT NULL,
    amount_paid             NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cost_of_goods_sold      NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status                  VARCHAR(20) NOT NULL,
    payment_status          VARCHAR(20) NOT NULL,
    payment_method          VARCHAR(30) NOT NULL,
    transaction_date        TIMESTAMP NOT NULL DEFAULT NOW(),
    currency                VARCHAR(3) NOT NULL DEFAULT 'ETB',
    created_by_id           BIGINT REFERENCES users(id) ON DELETE SET NULL,
    idempotency_key         VARCHAR(100) UNIQUE,
    void_status             VARCHAR(20),
    void_reason             VARCHAR(255),
    void_requested_by_id    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    void_requested_at       TIMESTAMP,
    void_approved_by_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    void_approved_at        TIMESTAMP,
    void_review_note        VARCHAR(500),
    refund_status           VARCHAR(20),
    refund_reason           VARCHAR(255),
    refund_requested_by_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    refund_requested_at     TIMESTAMP,
    refund_approved_by_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,
    refund_approved_at      TIMESTAMP,
    refund_review_note      VARCHAR(500),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sale_order_items (
    id                  BIGSERIAL PRIMARY KEY,
    sale_order_id       BIGINT NOT NULL REFERENCES sale_orders(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity            INT NOT NULL,
    unit_price          NUMERIC(15,2) NOT NULL,
    discount            NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    subtotal            NUMERIC(15,2) NOT NULL,
    unit_cost           NUMERIC(15,4) NOT NULL,
    quantity_returned   INT NOT NULL DEFAULT 0
);

-- -----------------------------------------------------------------------------
-- 6. Returns & Exchanges Domain
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS return_orders (
    id                  BIGSERIAL PRIMARY KEY,
    return_number       VARCHAR(50) NOT NULL UNIQUE,
    return_type         VARCHAR(20) NOT NULL,
    original_reference  VARCHAR(80),
    party_name          VARCHAR(150),
    customer_id         BIGINT REFERENCES customers(id) ON DELETE RESTRICT,
    supplier_id         BIGINT REFERENCES suppliers(id) ON DELETE RESTRICT,
    warehouse_id        BIGINT REFERENCES warehouses(id) ON DELETE RESTRICT,
    total_amount        NUMERIC(15,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason              VARCHAR(255),
    notes               VARCHAR(1000),
    financial_settled   BOOLEAN NOT NULL DEFAULT FALSE,
    requested_by_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    approved_by_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    approved_at         TIMESTAMP,
    review_note         VARCHAR(1000),
    transaction_date    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS return_order_items (
    id                      BIGSERIAL PRIMARY KEY,
    return_order_id         BIGINT NOT NULL REFERENCES return_orders(id) ON DELETE CASCADE,
    product_id              BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    sale_order_item_id      BIGINT REFERENCES sale_order_items(id) ON DELETE RESTRICT,
    purchase_order_item_id  BIGINT REFERENCES purchase_order_items(id) ON DELETE RESTRICT,
    quantity                INT NOT NULL,
    unit_price              NUMERIC(15,2) NOT NULL,
    unit_cost               NUMERIC(15,4) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    tax                     NUMERIC(15,2) DEFAULT 0.00
);

-- -----------------------------------------------------------------------------
-- 7. Finance & Payments Domain
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS payments (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(80) NOT NULL,
    order_reference     VARCHAR(80),
    type                VARCHAR(20),
    direction           VARCHAR(3) NOT NULL,
    party_type          VARCHAR(20) NOT NULL,
    customer_id         BIGINT REFERENCES customers(id) ON DELETE RESTRICT,
    supplier_id         BIGINT REFERENCES suppliers(id) ON DELETE RESTRICT,
    sale_order_id       BIGINT REFERENCES sale_orders(id) ON DELETE RESTRICT,
    purchase_order_id   BIGINT REFERENCES purchase_orders(id) ON DELETE RESTRICT,
    amount              NUMERIC(15,2) NOT NULL,
    payment_method      VARCHAR(40) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    transaction_date    TIMESTAMP NOT NULL DEFAULT NOW(),
    external_reference  VARCHAR(120),
    notes               VARCHAR(500),
    created_by          VARCHAR(100),
    created_by_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS expenses (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(80),
    category            VARCHAR(50) NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    description         VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_date    TIMESTAMP NOT NULL DEFAULT NOW(),
    notes               TEXT,
    created_by          VARCHAR(100),
    created_by_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    approved_by_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    approved_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 8. Governance, AI & Chatbot Domain
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(100),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       VARCHAR(100),
    previous_value  TEXT,
    new_value       TEXT,
    ip_address      VARCHAR(50),
    timestamp       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_model_versions (
    id                      BIGSERIAL PRIMARY KEY,
    model_key               VARCHAR(120) NOT NULL,
    algorithm               VARCHAR(60) NOT NULL,
    candidate_metrics       TEXT,
    version                 INT NOT NULL,
    training_period_start   DATE,
    training_period_end     DATE,
    sample_size             INT NOT NULL,
    mae                     NUMERIC(14,4),
    rmse                    NUMERIC(14,4),
    mape                    NUMERIC(14,4),
    hyperparameters         TEXT,
    active                  BOOLEAN NOT NULL DEFAULT FALSE,
    trained_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_query_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(100),
    question        VARCHAR(1000) NOT NULL,
    intent          VARCHAR(60),
    confidence      NUMERIC(5,4),
    service_called  VARCHAR(80),
    parameters      TEXT,
    result_summary  TEXT,
    response_ms     BIGINT,
    error_message   VARCHAR(1000),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_conversations (
    id                  BIGSERIAL PRIMARY KEY,
    actor_type          VARCHAR(20) NOT NULL DEFAULT 'STAFF',
    actor_id            BIGINT NOT NULL,
    session_token_hash  VARCHAR(64),
    title               VARCHAR(120),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    last_message_at     TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP,
    staff_handoff       BOOLEAN NOT NULL DEFAULT FALSE,
    handoff_user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    role                VARCHAR(20) NOT NULL DEFAULT 'USER',
    content             TEXT,
    tool_name           VARCHAR(64),
    deterministic       BOOLEAN NOT NULL DEFAULT TRUE,
    guard_intervened    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_customer_sessions (
    id                      BIGSERIAL PRIMARY KEY,
    customer_id             BIGINT NOT NULL,
    customer_name           VARCHAR(200),
    token_hash              VARCHAR(64) NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMP NOT NULL,
    verified_order_number   VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS chat_support_tickets (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     BIGINT REFERENCES chat_conversations(id) ON DELETE SET NULL,
    customer_id         BIGINT NOT NULL,
    customer_name       VARCHAR(200),
    reason              TEXT NOT NULL,
    summary             TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    raised_by_type      VARCHAR(20) NOT NULL DEFAULT 'CHATBOT',
    raised_by_id        BIGINT,
    assigned_to_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,
    resolved_at         TIMESTAMP
);
