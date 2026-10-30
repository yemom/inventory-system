-- V5: Accounting Ledger, Chart of Accounts, Journal Entries, and Lines
-- Double-entry accounting system integrated with StockFlow inventory, sales, purchases, payments, expenses, tax, and payroll.

-- 1. Chart of Accounts
CREATE TABLE IF NOT EXISTS accounts (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    category            VARCHAR(50) NOT NULL, -- ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    sub_category        VARCHAR(100),         -- CURRENT_ASSET, CURRENT_LIABILITY, COGS, etc.
    description         VARCHAR(500),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    system_account      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_accounts_category ON accounts(category);
CREATE INDEX IF NOT EXISTS idx_accounts_code ON accounts(code);
CREATE INDEX IF NOT EXISTS idx_accounts_active ON accounts(active);

-- 2. Journal Entries
CREATE TABLE IF NOT EXISTS journal_entries (
    id                  BIGSERIAL PRIMARY KEY,
    entry_number        VARCHAR(100) NOT NULL UNIQUE,
    entry_date          DATE NOT NULL,
    accounting_period   VARCHAR(20) NOT NULL,
    reference           VARCHAR(255),
    description         TEXT NOT NULL,
    source_type         VARCHAR(50) NOT NULL DEFAULT 'MANUAL', -- MANUAL, SALE, PURCHASE, EXPENSE, PAYMENT, PAYROLL, TAX_SETTLEMENT, INVENTORY_ADJUSTMENT
    source_id           VARCHAR(100),
    status              VARCHAR(50) NOT NULL DEFAULT 'DRAFT',  -- DRAFT, POSTED, REVERSED
    total_debit         NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_credit        NUMERIC(15,2) NOT NULL DEFAULT 0,
    posted_at           TIMESTAMP,
    posted_by_id        BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reversed_at         TIMESTAMP,
    reversed_by_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reversal_entry_id   BIGINT,
    reversal_reason     VARCHAR(500),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_journal_entries_date ON journal_entries(entry_date);
CREATE INDEX IF NOT EXISTS idx_journal_entries_period ON journal_entries(accounting_period);
CREATE INDEX IF NOT EXISTS idx_journal_entries_status ON journal_entries(status);
CREATE INDEX IF NOT EXISTS idx_journal_entries_source ON journal_entries(source_type, source_id);

-- One operational source document can have at most one journal. Manual entries
-- may legitimately omit a source identifier. Legacy duplicates must be
-- reconciled before applying this constraint on an existing database.
CREATE UNIQUE INDEX IF NOT EXISTS uk_journal_entries_source
    ON journal_entries(source_type, source_id)
    WHERE source_id IS NOT NULL AND source_type <> 'MANUAL';

-- 3. Journal Lines
CREATE TABLE IF NOT EXISTS journal_lines (
    id                  BIGSERIAL PRIMARY KEY,
    journal_entry_id    BIGINT NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id          BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    debit               NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit              NUMERIC(15,2) NOT NULL DEFAULT 0,
    description         VARCHAR(500),
    line_number         INT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_journal_lines_entry ON journal_lines(journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_journal_lines_account ON journal_lines(account_id);
