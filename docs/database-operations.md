# StockFlow Production Database Operations & Maintenance Guide

## Overview

This guide details the standard operating procedures for provisioning, migrating, backing up, maintaining, and recovering the PostgreSQL database for the StockFlow inventory system in production and staging environments.

---

## 1. Clean Database Provisioning

### 1.1 Local / Docker Compose Environment

The database is defined in `docker-compose.yml` under the `db` service. To launch a fresh, healthy PostgreSQL instance:

```bash
# 1. Start database and cache services in the background
docker compose up -d db redis

# 2. Verify PostgreSQL container health
docker compose ps db
# Wait until status is '(healthy)'

# 3. Verify connection via psql
docker compose exec db psql -U stockflow -d stockflow_db -c "SELECT version();"
```

### 1.2 Database Initialization Sequence

When the Spring Boot application boots against a newly created database:
1. **Schema Initialization:** Flyway executes all versioned scripts located in `src/main/resources/db/migration/`:
   - `V1__initial_schema.sql`: Provisions all 26 relational tables, primary keys, and foreign keys.
   - `V2__performance_indexes.sql`: Creates all query and reporting indexes idempotently (`CREATE INDEX IF NOT EXISTS`).
   - `V3__integrity_and_check_constraints.sql`: Applies PostgreSQL check constraints guarding against negative stock, negative payments, and invalid statuses.
2. **Data Seeding:** `DataInitializer` executes on `CommandLineRunner`:
   - Inspects `permissions` and inserts missing security permissions from `RolePermissionCatalog`.
   - Provisions default roles (`SUPER_ADMIN`, `MANAGER`, `SUPERVISOR`, `INVENTORY_STAFF`, `CASHIER`, `ACCOUNTANT`, `STOREKEEPER`) and associates their respective permission sets.
   - Seeds the root `Main Warehouse` and root `General` product category.
   - Creates the initial `superadmin` user account with credentials injected from `SUPER_ADMIN_PASSWORD`.

---

## 2. Versioned Schema Migration Procedures

### 2.1 Standard Migration Workflow

All future database alterations must be recorded as immutable, versioned SQL migration scripts following Flyway naming conventions:
```
backend/src/main/resources/db/migration/V<Version>__<Description>.sql
```

#### Rules for Production Migrations:
- **Never edit an already-applied migration.** Once applied to staging or production, a migration file must be considered immutable. Bugfixes require a forward migration (e.g. `V4__...`).
- **Always write idempotent DDL:**
  - `CREATE TABLE IF NOT EXISTS ...`
  - `CREATE INDEX IF NOT EXISTS ...`
  - For constraints, use defensive conditional checks:
    ```sql
    DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_name') THEN
            ALTER TABLE table_name ADD CONSTRAINT chk_name CHECK (...);
        END IF;
    END $$;
    ```
- **Avoid table-locking operations on live traffic:** Adding columns must be accompanied by sensible defaults or declared nullable, followed by backfilling, before making columns `NOT NULL`.

### 2.2 Applying Migrations Manually via CLI

If applying migrations independently of application startup:

```bash
# Set database connection parameters
export PGHOST=localhost
export PGPORT=5434
export PGDATABASE=stockflow_db
export PGUSER=stockflow
export PGPASSWORD=stockflowpassword

# Run migration scripts in strict version sequence
psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -f backend/src/main/resources/db/migration/V1__initial_schema.sql
psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -f backend/src/main/resources/db/migration/V2__performance_indexes.sql
psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -f backend/src/main/resources/db/migration/V3__integrity_and_check_constraints.sql
```

---

## 3. Production Backup & Archival Strategy

### 3.1 Logical Backup (`pg_dump`)

A nightly logical backup captures the full relational state, sequences, and constraints into a compressed custom-format archive.

```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="/var/backups/stockflow"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/stockflow_${TIMESTAMP}.dump"

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Starting StockFlow database backup..."

# Execute pg_dump with custom format (-Fc) and high compression (-Z 9)
pg_dump \
  -h "${DB_HOST:-localhost}" \
  -p "${DB_PORT:-5434}" \
  -U "${DB_USER:-stockflow}" \
  -d "${DB_NAME:-stockflow_db}" \
  -Fc \
  -Z 9 \
  -f "${BACKUP_FILE}"

echo "[$(date)] Backup completed: ${BACKUP_FILE} ($(du -h "${BACKUP_FILE}" | cut -f1))"

# Retention Policy: Delete backups older than 30 days
find "${BACKUP_DIR}" -type f -name "stockflow_*.dump" -mtime +30 -delete
```

### 3.2 Backup Verification & Integrity Testing

A backup is only as good as its tested restore. Regularly verify archives in an isolated disposable container:

```bash
# Spin up temporary PostgreSQL container
docker run --rm -d --name pg_verify -e POSTGRES_PASSWORD=verify -p 5439:5432 postgres:15-alpine

# Restore backup into temporary database
pg_restore -h localhost -p 5439 -U postgres -d postgres --clean --if-exists -v /var/backups/stockflow/latest.dump

# Run sanity check queries
psql -h localhost -p 5439 -U postgres -d postgres -c "SELECT COUNT(*) FROM products; SELECT COUNT(*) FROM sale_orders; SELECT COUNT(*) FROM stock_movements;"

# Destroy temporary verification container
docker stop pg_verify
```

---

## 4. Disaster Recovery & Restoration Runbook

### 4.1 Full Database Restore

If a catastrophic database loss occurs:

```bash
# 1. Stop backend service to prevent inbound traffic during restore
docker compose stop backend

# 2. Terminate existing database connections
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "
SELECT pg_terminate_backend(pid) 
FROM pg_stat_activity 
WHERE datname = 'stockflow_db' AND pid <> pg_backend_pid();"

# 3. Drop and recreate clean target database
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "DROP DATABASE IF EXISTS stockflow_db;"
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c "CREATE DATABASE stockflow_db OWNER stockflow;"

# 4. Restore from the latest verified backup
pg_restore \
  -h $DB_HOST \
  -p $DB_PORT \
  -U $DB_USER \
  -d stockflow_db \
  --verbose \
  --no-owner \
  /var/backups/stockflow/stockflow_latest.dump

# 5. Restart backend application
docker compose start backend
```

---

## 5. Post-Restore Data Reconciliation Queries

Run these audit queries immediately following any migration or restore:

```sql
-- 1. Verify that business-wide product quantity matches warehouse stock sums
SELECT 
    p.id AS product_id,
    p.name,
    p.quantity AS product_total_qty,
    COALESCE(SUM(ws.quantity), 0) AS warehouse_stock_sum,
    (p.quantity - COALESCE(SUM(ws.quantity), 0)) AS discrepancy
FROM products p
LEFT JOIN warehouse_stock ws ON ws.product_id = p.id
GROUP BY p.id, p.name, p.quantity
HAVING p.quantity <> COALESCE(SUM(ws.quantity), 0);

-- 2. Check for any negative balances (should return 0 rows)
SELECT 'products' AS entity, id, sku, quantity FROM products WHERE quantity < 0
UNION ALL
SELECT 'warehouse_stock', id, CAST(product_id AS VARCHAR), quantity FROM warehouse_stock WHERE quantity < 0
UNION ALL
SELECT 'customers', id, customer_number, outstanding_balance FROM customers WHERE outstanding_balance < 0;

-- 3. Verify stock movements ledger integrity for unapproved movements
SELECT status, COUNT(*) 
FROM stock_movements 
GROUP BY status;
```
