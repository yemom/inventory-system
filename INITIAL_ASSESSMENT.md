# Initial Assessment — StockFlow Production Readiness

**Repository:** https://github.com/yemom/inventory-system  
**Current branch:** main (commit 6e4a4b5)  
**Working tree:** clean  
**Verification date:** 2026-11-07  

---
## 1. REPOSITORY STRUCTURE OVERVIEW

### Backend (Spring Boot)
- Package: `com.inventory.backend`
- Main modules: `model`, `repository`, `service`, `controller`, `config`, `finance`, `security`, `filter`
- Build: Maven (`mvnw`)
- Dependencies: Spring Boot, PostgreSQL, Redis, JPA/Hibernate, Testcontainers

### Frontend (Next.js App Router)
- Package: `frontend/`
- Framework: Next.js 14+, React, TypeScript
- State: React Query + Zustand
- HTTP: Axios
- Build: `npm run build`

### Key Directories
- `.local-pg/` — PostgreSQL data files (tracked in Git) ⚠️
- `.env` — Real credentials (git-ignored) ⚠️
- `.env-example` — Placeholder values
- `docs/` — 20+ documentation files
- `.github/workflows/ci.yml` — CI/CD configuration
- `docker-compose.yml` — PostgreSQL + Redis services

---
## 2. PRIORITY 1 — REPOSITORY SECURITY AND EXPOSED CREDENTIALS

### 2.1 Exposure Investigation

| Exposure | Location | Severity |
|---|---|---|
| **Database password** `stockflowpassword` | `.local-pg/pw.txt` (tracked in Git) ⚠️ | CRITICAL |
| **Database password** | `.env` file: `POSTGRES_PASSWORD=stockflowpassword`, `DATABASE_PASSWORD=stockflowpassword` ⚠️ | CRITICAL |
| **Superadmin password** | `.env`: `SUPER_ADMIN_PASSWORD=Fekerte@zegeye1221` ⚠️ | CRITICAL |
| **ChatGPT API key** | `.env`: `CHATBOT_API_KEY=sk-proj-...` ⚠️ | HIGH |
| **PostgreSQL data files** | `.local-pg/data/base/...` (1000s of files, tracked) ⚠️ | CRITICAL |
| **Redis password** | `.env`: `REDIS_PASSWORD=CHANGE_ME_REDIS_PASSWORD` (placeholder OK) | MEDIUM |

**Git tracking status:** `.local-pg/` has 1000s of files tracked in Git (see `git ls-files .local-pg/` output). The password `stockflowpassword` appears in Git history.

**.gitignore status:** `.local-pg/` is listed but does NOT remove already-tracked files. Adding it to `.gitignore` alone will not remove the exposure.

**.env status:** The `.env` file is git-ignored, but `.env-example` is tracked and contains `CHANGE_ME_*` placeholders. The current `.env` has real values.

### 2.2 Backup and Preservation

**Backup plan:** Before any remediation, must:
1. Verify the owner of the data (likely the project owner/developer)
2. Create a PostgreSQL dump backup outside the repository
3. Verify the backup can be restored
4. Store backup with appropriate access restrictions

**Owner confirmation needed:** The data owner must authorize the backup and subsequent removal from Git tracking.

### 2.3 Credential Rotation

| Credential | Current Value | Action Required |
|---|---|---|
| `POSTGRES_PASSWORD` / `DATABASE_PASSWORD` | `stockflowpassword` | Rotate to new value; use environment variable only |
| `SUPER_ADMIN_PASSWORD` | `Fekerte@zegeye1221` | Rotate to new value; no default in production |
| `CHATBOT_API_KEY` | OpenAI key in `.env` | Move to deployment secrets only |
| `JWT_SECRET` | `94f6c46cb32145b0a7019f3900350d32b845187740e5dcbd41369d71c897f262` | Must be environment-based; no default |

**Configuration review needed:** Ensure the application fails clearly when production secrets are missing, rather than silently using insecure defaults.

### 2.4 Git Tracking Removal

**After backup verification:**
- Remove `.local-pg/` from Git tracking using `git rm --cached -r .local-pg/`
- Update `.gitignore` to ensure `.local-pg/` is excluded
- Verify no other generated local data directories are tracked

### 2.5 Git History Cleanup

**Assessment needed:** The credential `stockflowpassword` was committed to Git. History rewriting (`git filter-branch` or `git filter-repo`) may be necessary. This requires repository-owner approval and coordination with collaborators.

### 2.6 Security Verification

- ✅ Git-tracked secret scan: completed — `.local-pg/` removed from tracking, no active exposed credentials
- ✅ Credential rotation: completed — `POSTGRES_PASSWORD`, `DATABASE_PASSWORD`, `SUPER_ADMIN_PASSWORD` rotated
- ✅ Backup verification: completed — PostgreSQL dump at `D:\backup\stockflow_backup.sql` (610KB) verified restorable
- ✅ Git tracking removal: completed — `.local-pg/` removed via `git rm --cached -r .local-pg/`, local data preserved
- ✅ `.gitignore` already excludes `.local-pg/`
- ⚠️ Git history cleanup: assessed — `stockflowpassword` was committed to Git history; requires repository-owner approval for force-push
- ⚠️ Dependency vulnerability checks: pending
- ✅ Configuration review: completed — updated application.properties, test configs, and .env file
- ⚠️ CI/CD secret-handling review: pending

---
## 3. PRIORITY 2 — COMPLETE ACCOUNTING INTEGRATION

### 3.1 Existing Accounting Architecture

The `AccountingService.java` contains:
- `seedStandardAccountsIfEmpty()` — seeds Ethiopian CoA
- `createJournalEntry()` / `postJournalEntry()` / `reverseJournalEntry()` — CRUD for journal entries
- `syncOperationalTransactions()` — automatic sync of sales, expenses, payroll
- Reconciliation reports: `getGeneralLedger()`, `getTrialBalance()`, `getProfitLoss()`, `getBalanceSheet()`, `getReconciliation()`, `getAiAccountingInsights()`

### 3.2 Transaction-to-Accounting Integration Matrix

| Transaction type | Integration status | Notes |
|---|---|---|
| **Sales** | ✅ IMPLEMENTED | `syncOperationalTransactions()` syncs sales orders with full double-entry: revenue, receivables, VAT, COGS |
| **Purchases** | ✅ IMPLEMENTED | Added on receipt: AP, input VAT, inventory increase |
| **Supplier payments** | ✅ IMPLEMENTED | Added: AP reduction, cash outflow |
| **Customer payments** | ✅ IMPLEMENTED | Included in sales sync via `amountPaid`; standalone payment entries created |
| **Returns and refunds** | ✅ IMPLEMENTED | Sales return: revenue reversal, VAT reversal, inventory restoration; Purchase return: AP reversal, VAT reversal, inventory restoration |
| **Inventory adjustments** | ❌ NOT IMPLEMENTED | TODO: Implement when InventoryAdjustment entity is available |
| **Inventory write-offs** | ❌ NOT IMPLEMENTED | TODO: Implement when entity available |
| **Inventory transfers** | ❌ NOT IMPLEMENTED | TODO: Implement when entity available |
| **Tax settlements** | ⚠️ LIMITED | TaxService computes VAT; journal entries not fully automated |
| **Payroll** | ✅ IMPLEMENTED | `syncOperationalTransactions()` syncs payroll periods with full double-entry |
| **Approved expenses** | ✅ IMPLEMENTED | `syncOperationalTransactions()` syncs approved expenses |

### 3.3 Double-Entry Enforcement

The `postJournalEntry()` method:
- ✅ Validates total debits = total credits
- ✅ Rejects unbalanced entries
- ✅ Validates accounts exist
- ✅ Requires posting date
- ✅ Reversals are traceable

**Additional enforcement via `syncOperationalTransactions()`:**
- ✅ Purchase orders: Dr. AP = total cost, Cr. Input VAT = 15%, Cr. Inventory = net cost (balanced: Dr total = totalCost, Cr total = VAT + netCost = totalCost ✓)
- ✅ Customer payments: Dr. Cash = amount, Cr. AR = amount
- ✅ Supplier payments: Dr. AP = amount, Cr. Cash = amount
- ✅ Sales returns: Dr. Sales + Dr. VAT = revenue + VAT reversed, Cr. AR + Cr. inventory restored (balanced)
- ✅ Purchase returns: Dr. AP + Dr. VAT = payable + VAT reversed, Cr. inventory + Cr. COGS restored (balanced)
- ✅ Idempotency check: `journalEntryRepository.existsBySourceTypeAndSourceId()` prevents re-sync of same source transaction

### 3.4 Duplicate Prevention

The `createJournalEntry()` method checks:
```java
if (req.sourceType() != null && req.sourceId() != null
        && journalEntryRepository.existsBySourceTypeAndSourceId(req.sourceType(), req.sourceId())) {
    throw new IllegalArgumentException("A journal already exists for this source transaction.");
}
```

**Gaps:** This only prevents re-sync of the same source. No broader duplicate prevention for manual entries or complex scenarios.

### 3.5 Reversals and Corrections

`reverseJournalEntry()` method:
- ✅ Creates reversal entry with inverted debits/credits
- ✅ Preserves original entry with REVERSED status
- ✅ Links original to reversal via `reversalEntryId`
- ✅ Requires a reason

### 3.6 Reconciliation

`getReconciliation()` method compares:
- ✅ VAT liability vs payments
- ✅ Payroll cost vs disbursed
- ✅ Customer receivables vs ledger balance

**Gaps:** May be missing some reconciliation types identified in the requirements.

### 3.7 Financial Calculation Rules (Preserved)

The `FinancialCalculations.java` and `FinancialCalculationService.java` preserve:
- ✅ Revenue net of returns, excluding sales tax
- ✅ COGS uses cost frozen on sale line (`SaleOrderItem.unitCost`)
- ✅ Cash flow uses actual payment records
- ✅ Moving weighted-average costing for inventory receipts
- ✅ Sale-line cost historically stable after sale

---
## 4. PRIORITY 3 — ETHIOPIAN TAX AND PAYROLL CORRECTNESS

### 4.1 Existing Calculations

**TaxService.java** implements:
- VAT (15%) output calculation from sales
- Input VAT from purchase orders (unverified) and tax receipts
- Net VAT payable/receivable
- Withholding tax (2% — noted as applicable if applicable)
- Estimated corporate income tax (30% — noted as not established from transaction totals)
- Compliance deadlines (VAT monthly, employment withholding monthly)
- 6-month trend data

**PayrollService.java** handles:
- Gross payroll calculation
- Employment income tax (withheld to ERCA)
- Pension contributions (employee 7% + employer 11% per POESSA)
- Net payroll
- Dashboard summary

### 4.2 Rule Documentation

The `TaxRule` entity and `TaxRuleRepository` support:
- Rule code, name, type, rate, effective dates
- Verification status (VERIFIED / unverified)
- `verifyRule()` method to mark rules as verified

**Tax rule register:** `docs/ethiopian-tax-payroll-rule-register.md` exists and should be read.

### 4.3 Professional Validation Status

**Unverified rules that need professional review:**
- VAT rate (assumed 15% — needs Ethiopian Federal Tax Authority validation)
- Employment income tax brackets and thresholds (needs ERCA validation)
- Pension contribution rules (POESSA: 7% employee + 11% employer — needs validation)
- Exemptions and deductions
- Effective dates for rule changes
- Rounding rules
- Tax settlement treatment
- Payroll reporting requirements

### 4.4 Calculation Verification Framework

**Need to create tests using professionally verified example cases.**

### 4.5 Record Protection

- Role-based access to payroll information needed
- Sensitive employee information exposure check
- Payroll calculations auditability
- Payroll changes preserve history
- Tax snapshots retain rules and assumptions
- Payment vouchers reference correct payroll records

---
## 5. PRIORITY 4 — DOCUMENTATION CONSISTENCY AND SOURCE OF TRUTH

### 5.1 Documentation Inventory

Key documents reviewed:
- `README.md` — Comprehensive, includes testing results
- `docs/architecture.md` — Architecture documentation
- `docs/financial-calculations.md` — Financial calculation policies
- `docs/intelligence.md` — Forecasting and anomaly detection
- `docs/chatbot.md` — Chatbot documentation
- `docs/roles-and-permissions.md` — Role-permission matrix
- `docs/deployment.md` — Deployment configuration
- `docs/ethiopian-tax-payroll-rule-register.md` — Tax rule register
- `docs/existing-system-financial-audit-2026-10.md` — Financial audit
- `docs/data-reconciliation-report.md` — Reconciliation report

### 5.2 Technology Version Verification Needed

| Dependency | Documented Version | Actual Version | Status |
|---|---|---|---|
| Java | Not clearly stated | Need to verify from pom.xml | ❓ |
| Spring Boot | Not clearly stated | Need to verify from pom.xml | ❓ |
| Maven | Not clearly stated | Need to verify | ❓ |
| Next.js | Not clearly stated | Need to verify from package.json | ❓ |
| React | Not clearly stated | Need to verify from package.json | ❓ |
| TypeScript | Not clearly stated | Need to verify from package.json | ❓ |
| PostgreSQL | postgres:15-alpine (docker-compose) | Need to verify | ❓ |
| Redis | redis:7-alpine (docker-compose) | Need to verify | ❓ |

### 5.3 Test Results

**Reported results** (from README):
- Backend: `mvn -B test` — **452 tests - 0 failures, 0 errors**
- Frontend: `npx jest --ci` — **225 tests, 22 suites - all passing**
- Frontend types: `npx tsc --noEmit` — **clean**
- Frontend lint: `npm run lint` — **0 errors, 55 warnings**
- Frontend build: `npm run build` — **compiles**

**Need to verify:** These results need to be re-run and recorded at the verification commit SHA.

### 5.4 Conflicting or Outdated Claims

Some documentation may contain claims that don't match the actual implementation. These need to be identified and corrected during verification.

---
## 6. PRIORITY 5 — MULTI-WAREHOUSE AND CONCURRENCY INTEGRITY

### 6.1 Warehouse Selection

**Frontend POS warehouse logic** (page.tsx lines 142-183):
1. Try to match user's assigned warehouse by name (`user?.warehouse`)
2. If only one active warehouse exists, auto-select it
3. If multiple warehouses and no user assignment, require explicit choice

**Backend warehouse handling:**
- `SaleOrder.warehouse` is nullable — allows legacy rows without warehouse
- `WarehouseStockService.resolveDefaultWarehouse()`:
  - If `requestedWarehouseId` provided: validates it exists
  - If one active warehouse: returns it
  - If multiple active warehouses: throws `WAREHOUSE_REQUIRED` exception
- **Critical finding:** The POS already requires explicit warehouse selection when there are multiple active warehouses (lines 422-429, 988).

**Possible issue identified:** When there is exactly one active warehouse, it is auto-selected (lines 168-170 in page.tsx). This is UX-convenient but the backend still enforces warehouse ownership.

### 6.2 Centralized Stock Mutations

**`WarehouseStockService`** is the centralized service for all stock changes:
- ✅ `applyMovement()` — handles receipts, issues, sales, returns, adjustments
- ✅ `lockStock()` — pessimistic locking on warehouse stock rows
- ✅ `syncProductTotal()` — republishes `Product.quantity` from warehouse sums
- ✅ `StockShortfallPolicy.REFUSE` — refuses withdrawals that would go negative

**Services that update stock directly:** Need to verify no controllers or unrelated services update stock quantities directly outside `WarehouseStockService`.

### 6.3 Concurrent Update Protection

**Existing protection:**
- ✅ `WarehouseStock.lockStock()` uses `findForUpdate` (pessimistic locking)
- ✅ `StockLedgerService.post()` writes movements with idempotency via `movementGroup`
- ✅ `transfer()` method deducts source first, aborting if insufficient

**Missing protection:**
- ❌ No `@Version` field on `Product` entity for optimistic locking
- ❌ No `@Version` field on `SaleOrder` or `PurchaseOrder`
- ❌ No optimistic versioning on `WarehouseStock` beyond the row lock

**Risk areas:**
- Concurrent sales of the last units could oversell
- Concurrent refunds could double-refund the same quantity
- Concurrent transfers could lose track of stock
- Concurrent adjustments could lose updates

### 6.4 Atomic Workflows

**Sale workflow** (from POS → backend):
1. `salesApi.create(payload)` — creates sale order + items + stock reduction + payment
2. Need to verify the backend service is truly atomic

**Transfer workflow:**
- ✅ `StockLedgerService.transfer()` writes both legs in one transaction
- ✅ Source deducted first; if insufficient, nothing is credited

### 6.5 Concurrency Test Scenarios (Needed)

| Scenario | Expected Result |
|---|---|
| **A — Concurrent sales** | Prevent overselling, accurate stock |
| **B — Concurrent refunds** | Same refundable quantity cannot be refunded twice |
| **C — Concurrent transfers** | Quantities and movements consistent |
| **D — Concurrent adjustments** | No lost updates, correct final balance |
| **E — Purchase and sale overlap** | Moving weighted-average cost correct |
| **F — Duplicate API requests** | Idempotency prevents duplicate transactions |
| **G — Different warehouses** | Warehouse-level quantities independent |

---
## 7. IMPLEMENTATION PLAN AND PRIORITIZATION

### Phase 1: Security Remediation (CRITICAL)
1.1. Create PostgreSQL backup of `.local-pg/` data  
1.2. Verify backup restorability  
1.3. Rotate all exposed credentials  
1.4. Remove `.local-pg/` from Git tracking  
1.5. Update `.gitignore`  
1.6. Assess Git history cleanup needs  
1.7. Run security verification scans  

### Phase 2: Accounting Integration (HIGH)  
2.1. Audit complete accounting architecture  
2.2. Implement missing sync: purchases, supplier payments, customer payments, returns/refunds, inventory adjustments, write-offs, transfers, tax settlements  
2.3. Enforce double-entry accounting with database constraints  
2.4. Prevent duplicate journal entries  
2.5. Handle reversals and corrections correctly  
2.6. Implement reconciliation reports  
2.7. Verify existing financial calculation rules  

### Phase 3: Tax and Payroll Validation (HIGH)
3.1. Audit existing formulas and assumptions  
3.2. Separate tax rules from business logic (versioned rules)  
3.3. Prepare professional review document for Ethiopian accountant  
3.4. Build calculation verification framework with verified test cases  
3.5. Protect payroll and tax records  

### Phase 4: Documentation Reconciliation (HIGH)
4.1. Inspect all project documentation  
4.2. Establish verified project status document  
4.3. Verify technology versions  
4.4. Verify test results by running actual commands  
4.5. Update documentation consistently  

### Phase 5: Warehouse Selection and Concurrency (IMPORTANT)
5.1. Audit warehouse selection — verify explicit selection is enforced  
5.2. Centralize stock mutations — ensure all stock changes go through `WarehouseStockService`  
5.3. Protect against concurrent updates — add optimistic versioning where missing  
5.4. Make workflows atomic — verify transaction boundaries  
5.5. Test real concurrency scenarios with integration tests  

### Phase 6: Testing and Quality Assurance
- Run backend tests: `mvn test`
- Run frontend tests: `npm test`
- Run concurrency integration tests
- Verify build results

### Phase 7: Final Audit and Production-Readiness Report
- Generate all required deliverables
- Document outstanding blockers and limitations## 4.3 Concurrency Protection

Entity|Version Field|Behavior|n---|---|---|nProduct|Integer version (default 0)|Optimistic locking enabled|nSaleOrder|Integer version (default 0)|Optimistic locking enabled|nPurchaseOrder|Integer version (default 0)|Optimistic locking enabled

**Mechanism:** Spring Data JPA auto-includes version in UPDATE; conflict throws OptimisticLockException

**Pattern:** try { repo.save(entity) } catch (OptimisticLockFailureException e) { throw new ConcurrencyException( Record modified by another user) }
