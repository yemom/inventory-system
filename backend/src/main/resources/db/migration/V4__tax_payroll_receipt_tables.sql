-- V4: Tax, Payroll, and Receipt tables for Ethiopian compliance
-- Created to ensure all tables exist with correct schemas, foreign keys, and indexes.

-- 1. Tax Rules (Effective-dated statutory tax rates and rules)
CREATE TABLE IF NOT EXISTS tax_rules (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(100) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    type                    VARCHAR(50) NOT NULL, -- VAT, EMPLOYMENT_INCOME_TAX, EMPLOYEE_PENSION, EMPLOYER_PENSION, WITHHOLDING
    rate                    NUMERIC(9,6) NOT NULL,
    effective_from          DATE NOT NULL,
    effective_to            DATE,
    verification_status     VARCHAR(50) NOT NULL DEFAULT 'ACCOUNTANT_REVIEW_REQUIRED', -- ACCOUNTANT_REVIEW_REQUIRED, VERIFIED
    source_url              VARCHAR(1000),
    notes                   VARCHAR(4000),
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_tax_rule_version UNIQUE (code, effective_from)
);

CREATE INDEX IF NOT EXISTS idx_tax_rules_code ON tax_rules(code);
CREATE INDEX IF NOT EXISTS idx_tax_rules_type ON tax_rules(type);
CREATE INDEX IF NOT EXISTS idx_tax_rules_effective ON tax_rules(effective_from, effective_to);

-- 2. Tax Receipts (Accountant receipt scanning and input tax records)
CREATE TABLE IF NOT EXISTS tax_receipts (
    id                  BIGSERIAL PRIMARY KEY,
    receipt_name        VARCHAR(255),
    file_path           VARCHAR(500),
    file_type           VARCHAR(50),
    detected_amount     NUMERIC(15,2),
    taxable_amount      NUMERIC(15,2),
    tax_amount          NUMERIC(15,2),
    total_amount        NUMERIC(15,2),
    treatment           VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    tax_inclusive       BOOLEAN NOT NULL DEFAULT FALSE,
    currency            VARCHAR(10) NOT NULL DEFAULT 'ETB',
    ai_extracted        BOOLEAN NOT NULL DEFAULT FALSE,
    ai_confidence       NUMERIC(5,4),
    ai_raw_response     TEXT,
    vendor_name         VARCHAR(255),
    receipt_date        DATE,
    notes               TEXT,
    accountant_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tax_receipts_accountant ON tax_receipts(accountant_id);
CREATE INDEX IF NOT EXISTS idx_tax_receipts_created_at ON tax_receipts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tax_receipts_receipt_date ON tax_receipts(receipt_date DESC);

-- 3. Payroll Profiles (Employee salary configuration)
CREATE TABLE IF NOT EXISTS payroll_profiles (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    basic_salary                NUMERIC(15,2) NOT NULL,
    effective_from              DATE NOT NULL,
    pension_eligible            BOOLEAN NOT NULL DEFAULT TRUE,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    bank_account_masked         VARCHAR(200),
    tax_identification_number   VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_payroll_profiles_user ON payroll_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_payroll_profiles_active ON payroll_profiles(active);

-- 4. Payroll Periods (Monthly payroll runs)
CREATE TABLE IF NOT EXISTS payroll_periods (
    id                  BIGSERIAL PRIMARY KEY,
    period_year         INT NOT NULL,
    period_month        INT NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, FINALIZED, PAID
    gross_payroll       NUMERIC(15,2) DEFAULT 0,
    net_payroll         NUMERIC(15,2) DEFAULT 0,
    employment_tax      NUMERIC(15,2) DEFAULT 0,
    employee_pension    NUMERIC(15,2) DEFAULT 0,
    employer_pension    NUMERIC(15,2) DEFAULT 0,
    employer_cost       NUMERIC(15,2) DEFAULT 0,
    rule_version        VARCHAR(255),
    finalized_at        TIMESTAMP,
    finalized_by_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    paid_at             TIMESTAMP,
    paid_by_id          BIGINT REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_payroll_period_month UNIQUE (period_year, period_month)
);

CREATE INDEX IF NOT EXISTS idx_payroll_periods_ym ON payroll_periods(period_year DESC, period_month DESC);
CREATE INDEX IF NOT EXISTS idx_payroll_periods_status ON payroll_periods(status);

-- 5. Payroll Items (Per-employee line items in a payroll period)
CREATE TABLE IF NOT EXISTS payroll_items (
    id                      BIGSERIAL PRIMARY KEY,
    payroll_period_id       BIGINT NOT NULL REFERENCES payroll_periods(id) ON DELETE CASCADE,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    basic_salary            NUMERIC(15,2) NOT NULL,
    taxable_income          NUMERIC(15,2) NOT NULL,
    income_tax              NUMERIC(15,2) NOT NULL DEFAULT 0,
    employee_pension        NUMERIC(15,2) NOT NULL DEFAULT 0,
    employer_pension        NUMERIC(15,2) NOT NULL DEFAULT 0,
    other_deductions        NUMERIC(15,2) NOT NULL DEFAULT 0,
    net_pay                 NUMERIC(15,2) NOT NULL,
    calculation_breakdown   VARCHAR(4000),
    CONSTRAINT uk_payroll_item_period_user UNIQUE (payroll_period_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_payroll_items_period ON payroll_items(payroll_period_id);
CREATE INDEX IF NOT EXISTS idx_payroll_items_user ON payroll_items(user_id);
