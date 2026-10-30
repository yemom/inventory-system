# StockFlow financial system audit

Audit date: 2026-10-30. Scope: current workspace source, not a connected production database.

## Source-to-screen map

| Screen | Source API | Calculation / persisted source |
| --- | --- | --- |
| Accountant overview | `/api/v1/dashboard/financial` | `FinancialCalculationService.summarise`: sale lines and returns, approved expenses, completed payments, open sales and received purchases, warehouse weighted-average inventory |
| Finance dashboard | `/api/v1/finance/summary`, `/intelligence` | Same financial calculation service; tax month summary; payroll period matching `YearMonth(end)` |
| Tax compliance | `/api/v1/tax/summary?period=YYYY-MM`, `/api/v1/compliance/summary?period=YYYY-MM` | Saved tax snapshots, approved returns, received purchase records, documented tax receipts and completed tax payments |
| Payroll dashboard | `/api/v1/payroll/dashboard`, `/periods` | Persisted payroll periods, profile snapshots and payroll items |
| Ledger | `/api/v1/accounting/*` | Posted journal entries and journal lines; operational sync supports only sales, approved expenses and payroll |
| Compliance AI | `/api/v1/compliance/ai-analyze`, `/ask` | Existing compliance findings and tax summary; external provider with deterministic fallback |

## Confirmed root causes and severity

### Critical

- `.local-pg/` is tracked and contains PostgreSQL cluster table files and `.local-pg/pw.txt`. Repository history includes the literal password. `.gitignore` now excludes the directory, but files remain tracked because `git rm --cached` was denied when Git tried to write `.git/index.lock`. Local data was not deleted. Rotate the exposed credential, back up and inspect the database with its owner, then untrack the directory in a controlled commit and consider history cleanup under repository-owner approval.
- Payroll used unverified fallback tax and pension rules. Calculations now require configured, effective, verified rates/brackets; missing or incomplete rules fail closed. Existing seeded brackets need authorized legal review before a payroll can be created/finalized.
- Payroll payment marked the period PAID before attempting a voucher save and swallowed save exceptions. Payment persistence now happens first in the same transaction, so failure prevents a paid state.

### High

- Tax summary classified any zero-tax sale as exempt; missing tax snapshots are now left unclassified and surfaced in its note. Persisted zero still cannot distinguish exempt from zero-rated because no explicit sales classification exists.
- Return VAT was reconstructed using a hard-coded 15% rate despite return item tax snapshots. Return tax now uses the saved item snapshot, or the linked original order snapshot prorated by quantity where legacy item tax is absent.
- Generic purchase tax was counted as recoverable input VAT. It is now excluded from eligible input VAT and reported as unverified/non-recoverable until the purchase classification/evidence model supports recoverability. Documented standard receipts remain eligible estimates.
- Business income tax was estimated as a flat 30% of sales less input VAT. That fabricated figure is removed; taxpayer classification and taxable-profit data are not represented.
- Accountant UI fields disagreed with actual DTO contracts (modal `open`, `reversalReason`, trial-balance `items/totalDebit`, payroll period fields, compliance `summary`). These contracts now align. Independent API failures are visible on Accountant sections instead of rendering indefinite skeletons.
- Compliance tax findings were unscoped to month and equated zero tax with exemption. Findings now use the selected period and request classification review. Payroll summary and compliance states are period scoped; paid periods without matching completed payroll vouchers produce findings.
- Accountant had journal reversal and tax-rule mutation permissions by default. Those grants were removed; Super Admin role assignment already supported ACCOUNTANT in the staff workflow.
- Journal posting checked header equality but not line totals. Posting now verifies both; reversals require a reason; manual source-linked duplicate creation is rejected; operational sync refuses to proceed while source-linked drafts exist.
- New database installs receive a partial unique index for non-manual source journals; existing databases with duplicate source rows need a dry-run cleanup report before applying the migration.
- Operational sync posted pending/rejected expenses. It now syncs approved expenses only. It still omits purchase, payment, return, tax settlement and inventory postings, so it is not a complete ledger integration.

### Medium / configuration gaps

- Finance intelligence payroll was using the latest period rather than the selected reporting month. It now requests the period matching the end date.
- Date range endpoints reject `from > to`.
- Tax summary no longer catches payment lookup failures and reports a fabricated zero.
- Monthly tax trend now uses saved sale tax and only received purchase orders.
- Profile effective dates are checked when creating payroll periods. Existing historical pay changes overwrite one profile rather than keeping effective-dated versions, so retrospective salary changes still need a model migration.

## Formula and data notes

- Financial summaries: net sales = rounded line sales less order discounts and approved sales returns; COGS uses sale line unit-cost snapshots and reverses returned unit cost; gross profit = net sales − COGS; net profit = gross profit − approved expenses. Cash uses completed customer/supplier payments, refunds, plus approved expenses (the current policy treats approved expenses as paid).
- Tax: output VAT sums sale tax snapshots less return item tax snapshots. Eligible input VAT comes from standard tax receipts only, less purchase-return snapshots; generic purchase order VAT is shown as unverified and excluded. Outstanding VAT is `max(output VAT − eligible input VAT − matched tax payments, 0)`, still an estimate rather than filing reconciliation.
- Payroll: gross minus income tax, employee pension and other deductions equals net pay; employer pension is added to employer cost and never deducted from net pay. The current calculation only creates basic salary as gross; taxable allowances and other deductions are not configured by the payroll API.
- Inventory valuation comes from current `warehouse_stock` weighted-average cost. It is a point-in-time balance and does not yet reconcile against a full posted inventory subledger.

## Legal validation and production limits

Tax/payroll rule source links are recorded in `docs/ethiopian-tax-payroll-rule-register.md`; they are not a legal opinion. VAT classification, current filing deadlines, taxpayer thresholds, income tax bands/exemptions, and pension applicability/effective dates need validation against current official gazette/Ministry/Revenue Authority and pension authority materials by a qualified Ethiopian accountant or tax professional. Filing use remains disabled by verified-rule gating. No authoritative production database snapshot or bank statement was provided; no live reconciliation was run.

## Verification and remaining work

- Backend: `mvnw -q -DskipTests compile` passed. Unit/integration tests were not run; existing tax/payroll unit tests appear stale relative to current service signatures and behavior and need updating.
- Frontend: `npx tsc --noEmit` passed. `next build` hit a Turbopack worker panic; `next build --webpack` failed on `EPERM` while creating a route output directory under `.next`, so production build remains unverified.
- `git diff --check` passed. Git index is read-only under the current workspace permission profile; `.local-pg/` could not be removed from the index.
- Before deployment: rotate the exposed local database password, preserve and inspect local data, untrack `.local-pg/` and clean repository history under repository-owner policy, review and verify effective rules, reconcile sale/purchase/return/payment/payroll sources to the journal using a dry-run report, and complete route build/tests in a writable CI workspace.
