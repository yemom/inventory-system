package com.inventory.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the role/permission matrix and the role-grant rules.
 *
 * <p>This is the specification, expressed as assertions. The matrix used to live
 * inline in {@code DataInitializer}, where nothing checked it: SUPERVISOR and
 * INVENTORY_STAFF were holding {@code PRODUCT_UPDATE} and
 * {@code PRODUCT_DEACTIVATE} (so a Supervisor could re-price and delete products),
 * and CASHIER held {@code SALES_REPORT_VIEW} (so the till could read everyone's
 * takings). Each of those is asserted against below.
 */
class RolePermissionCatalogTest {

    private static Set<String> forRole(String role) {
        return RolePermissionCatalog.permissionsFor(role);
    }

    @Test
    @DisplayName("SUPER_ADMIN holds every permission, including ones added later")
    void superAdminHoldsEverything() {
        assertEquals(Set.copyOf(RolePermissionCatalog.ALL_PERMISSIONS), forRole("SUPER_ADMIN"));
        // Resolved dynamically, not copied, so it cannot drift.
        assertTrue(forRole("SUPER_ADMIN").containsAll(RolePermissionCatalog.ALL_PERMISSIONS));
    }

    @Test
    @DisplayName("every permission named in the matrix exists in the catalogue")
    void matrixOnlyUsesKnownPermissions() {
        Set<String> all = Set.copyOf(RolePermissionCatalog.ALL_PERMISSIONS);
        for (String role : new String[]{"MANAGER", "SUPERVISOR", "CASHIER", "INVENTORY_STAFF", "ACCOUNTANT"}) {
            for (String permission : forRole(role)) {
                assertTrue(all.contains(permission),
                        role + " references " + permission + " which is not in ALL_PERMISSIONS");
            }
        }
    }

    @Nested
    @DisplayName("MANAGER")
    class Manager {

        @Test
        @DisplayName("has full product, category and warehouse CRUD")
        void hasFullCrud() {
            Set<String> p = forRole("MANAGER");
            assertTrue(p.containsAll(Set.of("PRODUCT_CREATE", "PRODUCT_READ", "PRODUCT_UPDATE",
                    "PRODUCT_DEACTIVATE", "CATEGORY_MANAGE", "WAREHOUSE_CREATE", "WAREHOUSE_READ",
                    "WAREHOUSE_UPDATE", "WAREHOUSE_MANAGE")));
        }

        @Test
        @DisplayName("approves stock adjustments and price changes")
        void approvesAdjustmentsAndPrices() {
            Set<String> p = forRole("MANAGER");
            assertTrue(p.contains("STOCK_ADJUSTMENT_APPROVE"));
            assertTrue(p.contains("PRICE_CHANGE_APPROVE"));
            assertTrue(p.contains("SALE_VOID_APPROVE"));
            assertTrue(p.contains("SALE_REFUND_APPROVE"));
        }

        @Test
        @DisplayName("sees every report including profit and inventory value")
        void seesAllReports() {
            Set<String> p = forRole("MANAGER");
            assertTrue(p.containsAll(Set.of("REPORT_VIEW", "SALES_REPORT_VIEW", "INVENTORY_REPORT_VIEW",
                    "INVENTORY_VALUE_VIEW", "PROFIT_LOSS_READ", "FINANCIAL_REPORT_VIEW")));
        }

        @Test
        @DisplayName("can view audit logs and manage staff")
        void seesAuditAndManagesStaff() {
            Set<String> p = forRole("MANAGER");
            assertTrue(p.contains("AUDIT_LOG_VIEW"));
            assertTrue(p.containsAll(Set.of("STAFF_VIEW", "STAFF_CREATE", "STAFF_UPDATE", "STAFF_DELETE")));
        }

        @Test
        @DisplayName("cannot change system settings")
        void cannotChangeSystemSettings() {
            Set<String> p = forRole("MANAGER");
            assertFalse(p.contains("SYSTEM_SETTINGS_MANAGE"), "Manager must not manage system settings");
            assertFalse(p.contains("SETTINGS_UPDATE"), "Manager must not update settings");
        }
    }

    @Nested
    @DisplayName("SUPERVISOR")
    class Supervisor {

        @Test
        @DisplayName("views products and stock")
        void viewsProductsAndStock() {
            Set<String> p = forRole("SUPERVISOR");
            assertTrue(p.containsAll(Set.of("PRODUCT_READ", "INVENTORY_READ", "STOCK_MOVEMENT_READ")));
        }

        @Test
        @DisplayName("approves cashier voids and refunds")
        void approvesVoidsAndRefunds() {
            Set<String> p = forRole("SUPERVISOR");
            assertTrue(p.contains("SALE_VOID_APPROVE"));
            assertTrue(p.contains("SALE_REFUND_APPROVE"));
            // Approving is not the same as requesting on someone's behalf.
            assertFalse(p.contains("SALE_CREATE"));
        }

        @Test
        @DisplayName("approves stock counts submitted by Inventory Staff")
        void approvesStockCounts() {
            assertTrue(forRole("SUPERVISOR").contains("STOCK_ADJUSTMENT_APPROVE"));
        }

        @Test
        @DisplayName("sees sales and low-stock reports")
        void seesSalesAndLowStock() {
            Set<String> p = forRole("SUPERVISOR");
            assertTrue(p.contains("SALES_REPORT_VIEW"));
            assertTrue(p.contains("LOW_STOCK_REPORT_VIEW"));
        }

        @Test
        @DisplayName("sees staff activity for their shift")
        void seesStaffActivity() {
            assertTrue(forRole("SUPERVISOR").contains("STAFF_ACTIVITY_VIEW"));
        }

        @Test
        @DisplayName("cannot create or delete users")
        void cannotCreateOrDeleteUsers() {
            Set<String> p = forRole("SUPERVISOR");
            assertFalse(p.contains("USER_CREATE"));
            assertFalse(p.contains("USER_UPDATE"));
            assertFalse(p.contains("USER_DEACTIVATE"));
            assertFalse(p.contains("STAFF_CREATE"));
            assertFalse(p.contains("STAFF_UPDATE"));
            assertFalse(p.contains("STAFF_DELETE"));
        }

        @Test
        @DisplayName("cannot delete products")
        void cannotDeleteProducts() {
            assertFalse(forRole("SUPERVISOR").contains("PRODUCT_DEACTIVATE"));
        }

        @Test
        @DisplayName("cannot change prices")
        void cannotChangePrices() {
            Set<String> p = forRole("SUPERVISOR");
            assertFalse(p.contains("PRODUCT_UPDATE"), "Supervisor must not be able to re-price");
            assertFalse(p.contains("PRODUCT_CREATE"));
            assertFalse(p.contains("PRICE_CHANGE_APPROVE"));
        }

        @Test
        @DisplayName("cannot view profit or inventory-value reports")
        void cannotSeeFinancials() {
            Set<String> p = forRole("SUPERVISOR");
            assertFalse(p.contains("PROFIT_LOSS_READ"), "Supervisor must not see profit");
            assertFalse(p.contains("INVENTORY_VALUE_VIEW"), "Supervisor must not see inventory value");
            assertFalse(p.contains("FINANCIAL_REPORT_VIEW"));
            assertFalse(p.contains("FINANCIAL_REPORT_EXPORT"));
        }
    }

    @Nested
    @DisplayName("CASHIER")
    class Cashier {

        @Test
        @DisplayName("searches products and checks stock")
        void searchesProducts() {
            Set<String> p = forRole("CASHIER");
            assertTrue(p.contains("PRODUCT_READ"));
            assertTrue(p.contains("INVENTORY_READ"));
        }

        @Test
        @DisplayName("creates sales and takes payments")
        void createsSalesAndTakesPayments() {
            Set<String> p = forRole("CASHIER");
            assertTrue(p.containsAll(Set.of("SALE_CREATE", "SALE_READ", "PAYMENT_CREATE", "PAYMENT_READ")));
        }

        @Test
        @DisplayName("cannot edit products or prices")
        void cannotEditProducts() {
            Set<String> p = forRole("CASHIER");
            assertFalse(p.contains("PRODUCT_CREATE"));
            assertFalse(p.contains("PRODUCT_UPDATE"));
            assertFalse(p.contains("PRODUCT_DEACTIVATE"));
            assertFalse(p.contains("PRICE_CHANGE_APPROVE"));
        }

        @Test
        @DisplayName("cannot adjust stock")
        void cannotAdjustStock() {
            Set<String> p = forRole("CASHIER");
            assertFalse(p.contains("INVENTORY_ADJUST"));
            assertFalse(p.contains("STOCK_ADJUSTMENT_APPROVE"));
        }

        @Test
        @DisplayName("may request a void or refund but never approve one")
        void canRequestButNotApproveVoidOrRefund() {
            Set<String> p = forRole("CASHIER");
            assertTrue(p.contains("SALE_VOID_REQUEST"));
            assertTrue(p.contains("SALE_REFUND_REQUEST"));
            assertFalse(p.contains("SALE_VOID_APPROVE"), "a cashier must not approve their own void");
            assertFalse(p.contains("SALE_REFUND_APPROVE"), "a cashier must not approve their own refund");
        }

        @Test
        @DisplayName("cannot view reports or other users' sales")
        void cannotViewReports() {
            Set<String> p = forRole("CASHIER");
            assertFalse(p.contains("REPORT_VIEW"), "Cashier must not read the sales report");
            assertFalse(p.contains("SALES_REPORT_VIEW"), "Cashier must not read the sales report");
            assertFalse(p.contains("INVENTORY_REPORT_VIEW"));
            assertFalse(p.contains("PROFIT_LOSS_READ"));
            assertFalse(p.contains("INVENTORY_VALUE_VIEW"));
        }
    }

    @Nested
    @DisplayName("INVENTORY_STAFF")
    class InventoryStaff {

        @Test
        @DisplayName("receives deliveries and transfers stock")
        void receivesAndTransfers() {
            Set<String> p = forRole("INVENTORY_STAFF");
            assertTrue(p.contains("INVENTORY_RECEIVE"));
            assertTrue(p.contains("INVENTORY_TRANSFER"));
        }

        @Test
        @DisplayName("may submit adjustments and stock counts")
        void submitsAdjustments() {
            Set<String> p = forRole("INVENTORY_STAFF");
            assertTrue(p.contains("INVENTORY_ADJUST"));
            // Submitting is not approving.
            assertFalse(p.contains("STOCK_ADJUSTMENT_APPROVE"));
        }

        @Test
        @DisplayName("views products and low-stock alerts")
        void viewsProductsAndAlerts() {
            Set<String> p = forRole("INVENTORY_STAFF");
            assertTrue(p.contains("PRODUCT_READ"));
            assertTrue(p.contains("INVENTORY_READ"));
            assertTrue(p.contains("LOW_STOCK_REPORT_VIEW"));
        }

        @Test
        @DisplayName("cannot make sales")
        void cannotSell() {
            Set<String> p = forRole("INVENTORY_STAFF");
            assertFalse(p.contains("SALE_CREATE"));
            assertFalse(p.contains("SALE_READ"));
        }

        @Test
        @DisplayName("cannot change prices or delete products")
        void cannotChangePricesOrDeleteProducts() {
            Set<String> p = forRole("INVENTORY_STAFF");
            assertFalse(p.contains("PRODUCT_CREATE"), "Inventory Staff must not create products");
            assertFalse(p.contains("PRODUCT_UPDATE"), "Inventory Staff must not re-price");
            assertFalse(p.contains("PRODUCT_DEACTIVATE"), "Inventory Staff must not delete products");
            assertFalse(p.contains("PRICE_CHANGE_APPROVE"));
            assertFalse(p.contains("CATEGORY_MANAGE"));
            assertFalse(p.contains("WAREHOUSE_CREATE"));
            assertFalse(p.contains("WAREHOUSE_MANAGE"));
        }

        @Test
        @DisplayName("cannot view sales or financial reports")
        void cannotViewFinancials() {
            Set<String> p = forRole("INVENTORY_STAFF");
            assertFalse(p.contains("SALES_REPORT_VIEW"));
            assertFalse(p.contains("REPORT_VIEW"));
            assertFalse(p.contains("PROFIT_LOSS_READ"));
            assertFalse(p.contains("INVENTORY_VALUE_VIEW"));
            assertFalse(p.contains("FINANCIAL_REPORT_VIEW"));
        }
    }

    @Nested
    @DisplayName("Role assignment")
    class Assignment {

        private final RoleGrantPolicy policy = new RoleGrantPolicy();

        @Test
        @DisplayName("SUPER_ADMIN can create Managers, Supervisors, Cashiers and Inventory Staff")
        void superAdminCanCreateStaff() {
            for (String role : new String[]{"MANAGER", "SUPERVISOR", "CASHIER", "INVENTORY_STAFF"}) {
                assertTrue(RolePermissionCatalog.canAssignRole("SUPER_ADMIN", role),
                        "Super Admin should be able to assign " + role);
            }
        }

        @Test
        @DisplayName("SUPER_ADMIN cannot create another SUPER_ADMIN from staff management")
        void superAdminIsNotAssignable() {
            assertFalse(RolePermissionCatalog.canAssignRole("SUPER_ADMIN", "SUPER_ADMIN"));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> policy.requireAssignable("SUPER_ADMIN", "SUPER_ADMIN"));
        }

        @Test
        @DisplayName("MANAGER can create Supervisors, Cashiers and Inventory Staff")
        void managerCanCreateStaff() {
            for (String role : new String[]{"SUPERVISOR", "CASHIER", "INVENTORY_STAFF"}) {
                assertTrue(RolePermissionCatalog.canAssignRole("MANAGER", role),
                        "Manager should be able to assign " + role);
            }
        }

        @Test
        @DisplayName("MANAGER cannot create another Manager")
        void managerCannotCreateManager() {
            assertFalse(RolePermissionCatalog.canAssignRole("MANAGER", "MANAGER"));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> policy.requireAssignable("MANAGER", "MANAGER"));
        }

        @Test
        @DisplayName("MANAGER cannot create a SUPER_ADMIN")
        void managerCannotCreateSuperAdmin() {
            assertFalse(RolePermissionCatalog.canAssignRole("MANAGER", "SUPER_ADMIN"));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> policy.requireAssignable("MANAGER", "SUPER_ADMIN"));
        }

        @Test
        @DisplayName("SUPERVISOR, CASHIER and INVENTORY_STAFF cannot assign any role")
        void otherRolesCannotAssign() {
            for (String actor : new String[]{"SUPERVISOR", "CASHIER", "INVENTORY_STAFF", "ACCOUNTANT"}) {
                assertFalse(RolePermissionCatalog.canAssignRole(actor, "CASHIER"),
                        actor + " must not be able to assign roles");
            }
        }

        @Test
        @DisplayName("an unknown or missing actor is denied rather than defaulting to allowed")
        void unknownActorIsDenied() {
            assertFalse(RolePermissionCatalog.canAssignRole(null, "CASHIER"));
            assertFalse(RolePermissionCatalog.canAssignRole("NOT_A_ROLE", "CASHIER"));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> policy.requireAssignable(null, "CASHIER"));
        }

        @Test
        @DisplayName("a role change is allowed only where creating that role would be")
        void changeRequiresSameAuthorityAsCreate() {
            assertDoesNotThrowPolicy(() -> policy.requireChangeAllowed("MANAGER", "CASHIER", "SUPERVISOR"));
            // Creating a Manager is denied, so promoting somebody to Manager is too.
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> policy.requireChangeAllowed("MANAGER", "CASHIER", "MANAGER"));
        }

        @Test
        @DisplayName("a SUPER_ADMIN account cannot be edited through staff management")
        void superAdminTargetIsProtected() {
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> policy.requireManageableTarget("SUPER_ADMIN", "SUPER_ADMIN"));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> policy.requireChangeAllowed("SUPER_ADMIN", "SUPER_ADMIN", "SUPER_ADMIN"));
        }

        @Test
        @DisplayName("a Manager cannot manage an account outside their remit")
        void managerCannotManagePeers() {
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> policy.requireManageableTarget("MANAGER", "MANAGER"));
            // But can manage their own reports.
            assertDoesNotThrowPolicy(() -> policy.requireManageableTarget("MANAGER", "CASHIER"));
        }

        private void assertDoesNotThrowPolicy(Runnable action) {
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(action::run);
        }
    }

    @Test
    @DisplayName("STOREKEEPER is an exact alias of INVENTORY_STAFF, so the two cannot disagree")
    void storekeeperIsAnAlias() {
        assertEquals(forRole("INVENTORY_STAFF"), forRole("STOREKEEPER"));
        assertFalse(RolePermissionCatalog.canAssignRole("SUPER_ADMIN", "STOREKEEPER"),
                "the deprecated role must not be assignable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "SUPERVISOR", "CASHIER", "INVENTORY_STAFF", "ACCOUNTANT"})
    @DisplayName("no role may assign SUPER_ADMIN")
    void nobodyAssignsSuperAdmin(String role) {
        assertFalse(RolePermissionCatalog.canAssignRole(role, "SUPER_ADMIN"));
    }
}