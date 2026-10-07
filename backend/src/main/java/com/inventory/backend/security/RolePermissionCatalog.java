package com.inventory.backend.security;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for who may do what.
 *
 * <p>Both halves of the application read this class and nothing else:
 * {@code DataInitializer} seeds {@code role_permissions} from
 * {@link #permissionsFor(String)}, and {@code RoleGrantPolicy} decides which
 * roles a caller may hand out. Keeping the matrix in one place is what stops the
 * classic drift where a role grows a permission that no endpoint enforces, or
 * loses one that an endpoint assumes.
 *
 * <p>Enforcement itself stays where it has always been — {@code @PreAuthorize} on
 * the controller, or an explicit check in a service. This class is data, not a
 * gate; it never grants access on its own.
 *
 * <h2>New permissions added for the approval workflows</h2>
 * <ul>
 *   <li>{@code STOCK_ADJUSTMENT_APPROVE} — approve an adjustment or stock count</li>
 *   <li>{@code PRICE_CHANGE_APPROVE} — change a product's price</li>
 *   <li>{@code SALE_VOID_REQUEST} / {@code SALE_REFUND_REQUEST} — ask for a void/refund</li>
 *   <li>{@code SALE_VOID_APPROVE} / {@code SALE_REFUND_APPROVE} — approve one</li>
 *   <li>{@code INVENTORY_VALUE_VIEW} — inventory valuation report</li>
 *   <li>{@code LOW_STOCK_REPORT_VIEW} — low-stock report</li>
 *   <li>{@code STAFF_ACTIVITY_VIEW} — a supervisor's view of their shift</li>
 * </ul>
 *
 * <h2>Roles</h2>
 * {@code STOREKEEPER} is retained as a deprecated alias of {@code INVENTORY_STAFF}
 * so accounts created before the rename keep working; it is not assignable and
 * holds exactly the same permissions, so the two cannot disagree.
 */
public final class RolePermissionCatalog {

    // ── Role names ────────────────────────────────────────────────────────────
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String SUPERVISOR = "SUPERVISOR";
    public static final String CASHIER = "CASHIER";
    public static final String INVENTORY_STAFF = "INVENTORY_STAFF";
    public static final String ACCOUNTANT = "ACCOUNTANT";

    /** @deprecated legacy name for {@link #INVENTORY_STAFF}; kept only for existing rows. */
    @Deprecated(forRemoval = true)
    public static final String STOREKEEPER = "STOREKEEPER";

    /**
     * Roles a caller may assign through staff management.
     *
     * <p>{@code SUPER_ADMIN} is absent everywhere on purpose: it is never
     * handed out from a staff-management screen, so the rule is enforced by the
     * data rather than by remembering to check it in every code path.
     *
     * <p>{@code MANAGER} appears only for {@link #SUPER_ADMIN}: a Manager may
     * create Supervisors, Cashiers and Inventory Staff, but never another Manager
     * — that is the privilege-escalation boundary.
     */
    private static final Map<String, Set<String>> ASSIGNABLE_ROLES = Map.of(
            SUPER_ADMIN, orderedSet(MANAGER, SUPERVISOR, CASHIER, INVENTORY_STAFF),
            MANAGER, orderedSet(SUPERVISOR, CASHIER, INVENTORY_STAFF));

    /** Every permission that exists, so {@code DataInitializer} creates the rows. */
    public static final List<String> ALL_PERMISSIONS = List.of(
            // ── Staff and roles ──
            "USER_CREATE", "USER_READ", "USER_UPDATE", "USER_DEACTIVATE",
            "STAFF_CREATE", "STAFF_VIEW", "STAFF_UPDATE", "STAFF_DELETE", "STAFF_ACTIVITY_VIEW",
            "ROLE_READ", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE",
            "PERMISSION_READ", "PERMISSION_ASSIGN",

            // ── Products and reference data ──
            "PRODUCT_CREATE", "PRODUCT_READ", "PRODUCT_UPDATE", "PRODUCT_DEACTIVATE",
            "PRICE_CHANGE_APPROVE",
            "CATEGORY_MANAGE",

            // ── Warehouses ──
            "WAREHOUSE_CREATE", "WAREHOUSE_READ", "WAREHOUSE_UPDATE", "WAREHOUSE_MANAGE",

            // ── Inventory ──
            "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_TRANSFER", "INVENTORY_RECEIVE",
            "STOCK_ADJUSTMENT_APPROVE", "STOCK_MOVEMENT_READ",
            "DAMAGE_RECORD", "EXPIRY_RECORD",

            // ── Sales ──
            "SALE_CREATE", "SALE_READ", "SALE_UPDATE", "SALE_CANCEL",
            "SALE_VOID_REQUEST", "SALE_VOID_APPROVE",
            "SALE_REFUND_REQUEST", "SALE_REFUND_APPROVE",
            "SALES_RETURN_CREATE",

            // ── Purchases ──
            "PURCHASE_CREATE", "PURCHASE_READ", "PURCHASE_UPDATE",
            "PURCHASE_APPROVE", "PURCHASE_CANCEL",

            // ── Suppliers and customers ──
            "SUPPLIER_CREATE", "SUPPLIER_READ", "SUPPLIER_UPDATE", "SUPPLIER_MANAGE",
            "CUSTOMER_CREATE", "CUSTOMER_READ", "CUSTOMER_UPDATE", "CUSTOMER_MANAGE",

            // ── Money ──
            "PAYMENT_CREATE", "PAYMENT_READ", "PAYMENT_UPDATE",
            "EXPENSE_CREATE", "EXPENSE_READ", "EXPENSE_UPDATE", "EXPENSE_APPROVE",
            "RECEIVABLE_READ", "PAYABLE_READ",

            // ── Returns ──
            "RETURN_CREATE", "RETURN_READ", "RETURN_APPROVE",

            // ── Reporting ──
            "REPORT_VIEW", "REPORT_EXPORT",
            "SALES_REPORT_VIEW", "LOW_STOCK_REPORT_VIEW",
            "INVENTORY_REPORT_VIEW", "INVENTORY_VALUE_VIEW",
            "PROFIT_LOSS_READ",
            "FINANCIAL_REPORT_VIEW", "FINANCIAL_REPORT_EXPORT",

            // ── Platform ──
            "AUDIT_LOG_VIEW", "SYSTEM_SETTINGS_MANAGE", "SETTINGS_VIEW", "SETTINGS_UPDATE",
            "DASHBOARD_VIEW");

    // ── The matrix ────────────────────────────────────────────────────────────

    private static final Set<String> MANAGER_PERMISSIONS = orderedSet(
            "DASHBOARD_VIEW",

            // Staff: may manage Supervisors, Cashiers and Inventory Staff.
            // USER_CREATE / USER_UPDATE are deliberately absent — the staff-* set is
            // the staff-management surface, and StaffGrantPolicy separately forbids
            // handing out a Manager or Super Admin role.
            "USER_READ", "STAFF_VIEW", "STAFF_CREATE", "STAFF_UPDATE", "STAFF_DELETE",

            // Full CRUD on products, categories and warehouses.
            "PRODUCT_CREATE", "PRODUCT_READ", "PRODUCT_UPDATE", "PRODUCT_DEACTIVATE",
            "PRICE_CHANGE_APPROVE",
            "CATEGORY_MANAGE",
            "WAREHOUSE_CREATE", "WAREHOUSE_READ", "WAREHOUSE_UPDATE", "WAREHOUSE_MANAGE",

            "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_TRANSFER", "INVENTORY_RECEIVE",
            "STOCK_ADJUSTMENT_APPROVE", "STOCK_MOVEMENT_READ",

            "SALE_CREATE", "SALE_READ", "SALE_UPDATE", "SALE_CANCEL",
            "SALE_VOID_APPROVE", "SALE_REFUND_APPROVE",

            "PURCHASE_CREATE", "PURCHASE_READ", "PURCHASE_UPDATE",
            "PURCHASE_APPROVE", "PURCHASE_CANCEL",

            "SUPPLIER_CREATE", "SUPPLIER_READ", "SUPPLIER_UPDATE", "SUPPLIER_MANAGE",
            "CUSTOMER_CREATE", "CUSTOMER_READ", "CUSTOMER_UPDATE", "CUSTOMER_MANAGE",

            "PAYMENT_CREATE", "PAYMENT_READ",
            "EXPENSE_CREATE", "EXPENSE_READ",

            // Every report, including inventory valuation and profit.
            "REPORT_VIEW", "REPORT_EXPORT",
            "SALES_REPORT_VIEW", "LOW_STOCK_REPORT_VIEW",
            "INVENTORY_REPORT_VIEW", "INVENTORY_VALUE_VIEW",
            "PROFIT_LOSS_READ",
            "FINANCIAL_REPORT_VIEW", "FINANCIAL_REPORT_EXPORT",

            "AUDIT_LOG_VIEW",
            // Deliberately NOT granted: SYSTEM_SETTINGS_MANAGE, SETTINGS_UPDATE.

            "DAMAGE_RECORD", "EXPIRY_RECORD", "RECEIVABLE_READ", "PAYABLE_READ");

    private static final Set<String> SUPERVISOR_PERMISSIONS = orderedSet(
            "DASHBOARD_VIEW",

            // Read-only view of products and stock. No PRODUCT_UPDATE (so no price
            // changes) and no PRODUCT_DEACTIVATE (so no deleting products).
            "PRODUCT_READ",
            "INVENTORY_READ", "STOCK_MOVEMENT_READ",
            "WAREHOUSE_READ",

            // Approvals: cashier voids/refunds and Inventory Staff stock counts.
            "SALE_VOID_APPROVE", "SALE_REFUND_APPROVE",
            "STOCK_ADJUSTMENT_APPROVE",

            // Sales and low-stock reporting, but NOT profit or inventory valuation.
            "SALE_READ",
            "SALES_REPORT_VIEW", "LOW_STOCK_REPORT_VIEW", "INVENTORY_REPORT_VIEW",

            // Their shift's staff activity, without the ability to change staff.
            "USER_READ", "STAFF_VIEW", "STAFF_ACTIVITY_VIEW",
            // Deliberately NOT granted: STAFF_CREATE / STAFF_UPDATE / STAFF_DELETE,
            // USER_CREATE / USER_UPDATE / USER_DEACTIVATE,
            // PROFIT_LOSS_READ, INVENTORY_VALUE_VIEW, FINANCIAL_*,
            // PRODUCT_CREATE / PRODUCT_UPDATE / PRODUCT_DEACTIVATE, PRICE_CHANGE_APPROVE.

            "SUPPLIER_READ", "CUSTOMER_READ",
            "PURCHASE_READ", "DAMAGE_RECORD", "EXPIRY_RECORD");

    private static final Set<String> CASHIER_PERMISSIONS = orderedSet(
            "DASHBOARD_VIEW",

            // Look up products and check availability on the till.
            "PRODUCT_READ", "INVENTORY_READ",

            "SALE_CREATE", "SALE_READ",
            "SALE_VOID_REQUEST", "SALE_REFUND_REQUEST",

            "CUSTOMER_CREATE", "CUSTOMER_READ", "CUSTOMER_UPDATE",
            "PAYMENT_CREATE", "PAYMENT_READ",

            // Deliberately NOT granted: PRODUCT_CREATE / PRODUCT_UPDATE /
            // PRODUCT_DEACTIVATE, INVENTORY_ADJUST, SALES_REPORT_VIEW, REPORT_VIEW,
            // SALE_VOID_APPROVE / SALE_REFUND_APPROVE — a void or refund must be
            // approved by a Supervisor.

            "WAREHOUSE_READ");

    private static final Set<String> INVENTORY_STAFF_PERMISSIONS = orderedSet(
            "DASHBOARD_VIEW",

            "PRODUCT_READ",
            "INVENTORY_READ",
            // Receiving and transfers apply immediately; ADJUSTMENT and STOCK_COUNT
            // are recorded as PENDING and wait for an approver.
            "INVENTORY_RECEIVE", "INVENTORY_TRANSFER", "INVENTORY_ADJUST",
            "STOCK_MOVEMENT_READ",
            "WAREHOUSE_READ",
            "SUPPLIER_READ",
            "LOW_STOCK_REPORT_VIEW",
            "DAMAGE_RECORD", "EXPIRY_RECORD",
            // Deliberately NOT granted: PRODUCT_CREATE / PRODUCT_UPDATE (no price
            // changes) / PRODUCT_DEACTIVATE (no deleting products), SALE_* (no
            // selling), and every profit, financial or sales report.

            "CUSTOMER_READ");

    /**
     * Existing financial-reporting role, retained unchanged. Not in the requested
     * role set and not assignable from staff management, but removing it would
     * break accounts that already use it.
     */
    private static final Set<String> ACCOUNTANT_PERMISSIONS = orderedSet(
            "DASHBOARD_VIEW",
            "SALE_READ",
            "PAYMENT_CREATE", "PAYMENT_READ", "PAYMENT_UPDATE",
            "EXPENSE_CREATE", "EXPENSE_READ", "EXPENSE_UPDATE", "EXPENSE_APPROVE",
            "PURCHASE_READ", "PURCHASE_APPROVE", "PURCHASE_CANCEL",
            "RECEIVABLE_READ", "PAYABLE_READ",
            "PROFIT_LOSS_READ",
            "FINANCIAL_REPORT_VIEW", "FINANCIAL_REPORT_EXPORT",
            "INVENTORY_REPORT_VIEW", "INVENTORY_VALUE_VIEW",
            "SALES_REPORT_VIEW", "REPORT_VIEW", "REPORT_EXPORT",
            "SUPPLIER_READ", "CUSTOMER_READ");

    private static final Map<String, Set<String>> MATRIX = buildMatrix();

    private static Map<String, Set<String>> buildMatrix() {
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        // Super Admin is deliberately absent: it is resolved as "everything" so a
        // newly added permission can never be missing from it by accident.
        matrix.put(MANAGER, MANAGER_PERMISSIONS);
        matrix.put(SUPERVISOR, SUPERVISOR_PERMISSIONS);
        matrix.put(CASHIER, CASHIER_PERMISSIONS);
        matrix.put(INVENTORY_STAFF, INVENTORY_STAFF_PERMISSIONS);
        matrix.put(ACCOUNTANT, ACCOUNTANT_PERMISSIONS);
        return Map.copyOf(matrix);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static boolean isSuperAdmin(String roleName) {
        return SUPER_ADMIN.equals(roleName);
    }

    /**
     * Resolves a role's permissions. {@link #SUPER_ADMIN} yields
     * {@link #ALL_PERMISSIONS} rather than a copied list, so it is correct the
     * instant a permission is added.
     */
    public static Set<String> permissionsFor(String roleName) {
        if (isSuperAdmin(roleName)) {
            return new LinkedHashSet<>(ALL_PERMISSIONS);
        }
        if (roleName == null) {
            return Set.of();
        }
        // STOREKEEPER is the pre-rename name for INVENTORY_STAFF.
        String key = STOREKEEPER.equals(roleName) ? INVENTORY_STAFF : roleName;
        return MATRIX.getOrDefault(key, Set.of());
    }

    /**
     * Roles the given role may assign. Super Admin is never assignable.
     *
     * <p>A null actor gets nothing. The check is load-bearing rather than
     * defensive: {@code Map.of(...)} rejects a null key by throwing, so without it
     * a caller that forgot to pass an actor produced a 500 instead of the 403 that
     * actually describes the problem.
     */
    public static Set<String> assignableRoles(String actorRoleName) {
        if (actorRoleName == null) {
            return Set.of();
        }
        return ASSIGNABLE_ROLES.getOrDefault(actorRoleName, Set.of());
    }

    /**
     * Whether {@code actorRoleName} may grant {@code targetRoleName}.
     * Used by the service layer so a direct API call cannot bypass the UI's
     * filtered dropdown.
     */
    public static boolean canAssignRole(String actorRoleName, String targetRoleName) {
        if (isSuperAdmin(targetRoleName)) {
            return false;
        }
        return assignableRoles(actorRoleName).contains(targetRoleName);
    }

    /** Role descriptions, used when seeding. */
    public static String descriptionFor(String roleName) {
        return switch (roleName) {
            case SUPER_ADMIN -> "Full system access";
            case MANAGER -> "Operational management";
            case SUPERVISOR -> "Inventory and sales supervision";
            case CASHIER -> "POS and sales";
            case INVENTORY_STAFF -> "Inventory staff operations";
            case ACCOUNTANT -> "Financial reports";
            case STOREKEEPER -> "Deprecated: renamed to Inventory Staff";
            default -> roleName;
        };
    }

    /** Every role that {@code DataInitializer} should keep up to date. */
    public static List<String> managedRoles() {
        return List.of(SUPER_ADMIN, MANAGER, SUPERVISOR, CASHIER, INVENTORY_STAFF, ACCOUNTANT, STOREKEEPER);
    }

    private static Set<String> orderedSet(String... values) {
        return Set.copyOf(new LinkedHashSet<>(List.of(values)));
    }

    private RolePermissionCatalog() {
    }
}