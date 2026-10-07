# Roles and permissions

Who may do what, and why the answers are where they are.

## The source of truth is one class

`backend/src/main/java/com/inventory/backend/security/RolePermissionCatalog.java`

Nothing else defines the matrix. `DataInitializer` seeds `role_permissions` from it on every boot, and `RoleGrantPolicy` reads it to decide which roles a caller may hand out. Enforcement itself stays where it has always been — `@PreAuthorize` on the controller, or an explicit check inside a service — so the catalogue is data, never a gate.

**The catalogue replaces each role's permissions on boot rather than merging.** That is deliberate: merging would mean a permission removed from the matrix kept existing in the database forever, and the seed could never be trusted to undo a mistake.

`SUPER_ADMIN` is not in the matrix at all. It resolves to `ALL_PERMISSIONS` at the moment it is asked, so a permission added tomorrow is included automatically and the role cannot drift.

## The matrix

| | Super Admin | Manager | Supervisor | Cashier | Inventory Staff |
|---|---|---|---|---|---|
| Dashboard | all | yes | yes | yes | yes |
| Products | all | CRUD | **read only** | **read only** | **read only** |
| Change prices / delete products | yes | yes | **no** | **no** | **no** |
| Categories, warehouses | all | manage | read | read | read |
| Receive stock / transfers | yes | yes | — | — | yes |
| Adjust stock | yes | yes | — | **no** | yes (pending) |
| Approve adjustments & counts | yes | yes | yes | — | — |
| Record a sale | yes | yes | — | yes | **no** |
| **Request** a void / refund | yes | yes | — | yes | — |
| **Approve** a void / refund | yes | yes | yes | **no** | — |
| Read sales | yes | yes | yes | own only | **no** |
| Sales report | yes | yes | yes | **no** | **no** |
| Low-stock report | yes | yes | yes | **no** | yes |
| Inventory valuation | yes | yes | **no** | **no** | **no** |
| Profit & loss | yes | yes | **no** | **no** | **no** |
| Create / edit staff | yes | Supervisors, Cashiers, Inventory Staff | **no** | **no** | **no** |
| Delete staff | yes | yes | **no** | **no** | **no** |
| System settings | yes | **no** | **no** | **no** | **no** |
| Audit logs | yes | yes | **no** | **no** | **no** |

The full, exact list is `RolePermissionCatalog.ALL_PERMISSIONS` and the per-role sets in that file. This table is a summary — the code is the contract, and `RolePermissionCatalogTest` pins it.

### Decisions worth knowing about

**A Manager cannot create a Manager or a Super Admin.** `SUPER_ADMIN` is absent from every assignable set, so the rule lives in data rather than in a check someone might forget. A Manager's assignable set is Supervisors, Cashiers and Inventory Staff.

**A Supervisor cannot see profit or inventory valuation.** Those are the two most commercially sensitive figures in the system. A Supervisor can see the sales report and what needs reordering, because they act on that; the balance sheet is not theirs.

**A Cashier can see their own sales but not everyone's.** `SALE_READ` is scoped to sales they recorded; `SALES_REPORT_VIEW` is what widens it to the whole ledger. This is enforced in `SaleOrderService` across the list, the count, the single fetch and the void/refund request endpoints — all four returned a colleague's order before it was fixed, and a valid Cashier token could call any of them directly.

**Inventory Staff cannot sell, re-price, delete products, or read any sales or financial report.** Their adjustments are the only thing held pending, because a stock count is a claim by whoever counted, not proof of what is on the shelf.

**`ACCOUNTANT` is retained but not assignable.** It predates this matrix and real accounts use it. Removing it would break them; offering it in a dropdown would put a sixth role into a five-role model. It exists, and no one can create it from staff management.

**`STOREKEEPER` is a deprecated alias of `INVENTORY_STAFF`.** Rows created before the rename keep working because the two resolve to the same permission set, so they cannot disagree. It is not assignable.

## Workflows that need two people

### Stock adjustments and counts

```
IN / RECEIPT / TRANSFER  -> applied immediately
ADJUSTMENT / STOCK_COUNT -> recorded PENDING, quantity unchanged
                            -> approve: quantity moves, approver + timestamp recorded
                            -> reject:  quantity unchanged, reason recorded
```

A submitter who already holds `STOCK_ADJUSTMENT_APPROVE` is not made to queue their own work — their entry applies at once. Anyone else submits a claim and waits.

A movement cannot be approved twice; the second attempt is refused rather than applied again, because a double-click would otherwise deduct (or credit) the quantity a second time.

Quantities stop at zero rather than going negative, and when they do the movement's own note records that it was clamped — otherwise the product row and the ledger would silently disagree.

Approving an adjustment that the caller recorded themselves is refused when they lack the approve permission. Without that, "submit, then approve" would be a two-step bypass of the whole workflow.

### Sale voids and refunds

```
Cashier: POST /sales/{id}/void-request    -> PENDING, stock stays out
Supervisor: POST /sales/{id}/void-approve -> CANCELLED, stock returned, approver recorded
Supervisor: POST /sales/{id}/void-reject  -> sale stands, reason recorded
```

Same shape for refunds. The person at the till asks; someone else decides. Approving returns the sold items to stock under the same lock a sale uses, so a void cannot race a concurrent sale into a negative quantity.

Queues: `GET /inventory/movements/pending` and `GET /sales/approvals`. Both require the matching `*_APPROVE` permission — being able to *ask* does not entitle anyone to *review*.

## Privilege escalation is blocked in the service, not the UI

Hiding `SUPER_ADMIN` in a dropdown is presentation. `RoleGrantPolicy` is the control:

- every user mutation in `UserService` requires an **actor**; the unguarded overloads (`createUser(request)`, `updateUser(id, request)`, `changeRole`, `activateUser`, `deactivateUser`) were removed rather than left as a trap
- `canAssignRole(actorRole, targetRole)` is checked on create, on update, and on role change
- self-registration goes through `registerSelfServiceUser`, which hardcodes `CASHIER` and cannot be steered by the request body
- a null actor grants nothing rather than throwing — `Map.of` rejects a null key, so the defensive check turns a 500 into the 403 that actually describes the problem

Prices are gated the same way: `PRICE_CHANGE_APPROVE` is checked inside `ProductService.updateProduct`, not only on the endpoint, so a second caller path cannot slip past it. Every grant, change, void and refund is written to the audit log.

## Tests

| File | What it proves |
|---|---|
| `security/RolePermissionCatalogTest` | the matrix itself — 44 assertions pinning every role's permissions and assignability |
| `security/RolePermissionEnforcementIntegrationTest` | HTTP 403s for each role against real endpoints with real tokens |
| `service/StockAdjustmentApprovalIntegrationTest` | quantity does not move until approved; rejection changes nothing; approvals happen exactly once |
| `service/SaleApprovalWorkflowIntegrationTest` | a Cashier cannot approve their own void or refund; approving restocks once |
| `service/CashierSaleVisibilityIntegrationTest` | a Cashier's sale list, count, single fetch and void-request all exclude other people's sales |
| `service/OpeningStockIntegrationTest` | an opening quantity of 50 is 50 on hand, with a matching movement |
| `frontend/src/__tests__/sidebarPermissions.test.tsx` | the UI offers only what the API would allow |
| `frontend/src/__tests__/openingStock.test.tsx` | the product form sends the opening quantity |

The sidebar hides links the user cannot use, but that is ergonomics. `RolePermissionEnforcementIntegrationTest` is what proves the request is refused.

## Changing the matrix

Edit `RolePermissionCatalog`, then run `mvn clean verify`. `RolePermissionCatalogTest` will fail if a permission in `ALL_PERMISSIONS` is granted to no role, or if a role gains a permission the tests assert it does not have — which is the point. Use `clean`: an incremental build can silently skip recompiling and let a broken change through.
