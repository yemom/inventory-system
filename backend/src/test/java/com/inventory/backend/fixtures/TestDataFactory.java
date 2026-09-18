package com.inventory.backend.fixtures;

import com.inventory.backend.model.Product;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Central factory for deterministic, reusable test data.
 */
public final class TestDataFactory {

    // ---------------------------------------------------------------
    // Roles
    // ---------------------------------------------------------------

    public static Role buildSuperAdminRole() {
        return Role.builder()
                .name("SUPER_ADMIN")
                .description("Full system access")
                .permissions(Set.of())
                .build();
    }

    public static Role buildManagerRole() {
        return Role.builder()
                .name("MANAGER")
                .description("Operational management")
                .permissions(Set.of())
                .build();
    }

    public static Role buildCashierRole() {
        return Role.builder()
                .name("CASHIER")
                .description("POS and sales")
                .permissions(Set.of())
                .build();
    }

    public static Role buildStorekeeperRole() {
        return Role.builder()
                .name("STOREKEEPER")
                .description("Inventory and receiving")
                .permissions(Set.of())
                .build();
    }

    public static Role buildAccountantRole() {
        return Role.builder()
                .name("ACCOUNTANT")
                .description("Financial reports and payments")
                .permissions(Set.of())
                .build();
    }

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------
    private static final String HASHED_PASSWORD =
            "$2a$12$D3kxjBwpuM9k9Pm.iM0nT.1xJZVh3t/VTlktU3RxW5ykLJ9xKFnJC";

    public static User buildSuperAdmin(Role role) {
        return User.builder()
                .username("superadmin_test")
                .firstName("Super")
                .lastName("Admin")
                .email("superadmin@stockflow.test")
                .passwordHash(HASHED_PASSWORD)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static User buildManager(Role role) {
        return User.builder()
                .username("manager_test")
                .firstName("Store")
                .lastName("Manager")
                .email("manager@stockflow.test")
                .passwordHash(HASHED_PASSWORD)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static User buildCashier(Role role) {
        return User.builder()
                .username("cashier_test")
                .firstName("Jane")
                .lastName("Cashier")
                .email("cashier@stockflow.test")
                .passwordHash(HASHED_PASSWORD)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static User buildInactiveUser(Role role) {
        return User.builder()
                .username("disabled_test")
                .firstName("Disabled")
                .lastName("User")
                .email("disabled@stockflow.test")
                .passwordHash(HASHED_PASSWORD)
                .role(role)
                .status(UserStatus.INACTIVE)
                .build();
    }

    // ---------------------------------------------------------------
    // Products
    // ---------------------------------------------------------------

    public static Product buildProductA() {
        return Product.builder()
                .sku("PROD-001")
                .barcode("1234567890001")
                .name("Product Alpha")
                .purchasePrice(new BigDecimal("100.00"))
                .sellingPrice(new BigDecimal("150.00"))
                .minStockLevel(10)
                .reorderLevel(20)
                .active(true)
                .build();
    }

    public static Product buildProductB() {
        return Product.builder()
                .sku("PROD-002")
                .barcode("1234567890002")
                .name("Product Beta")
                .purchasePrice(new BigDecimal("200.00"))
                .sellingPrice(new BigDecimal("300.00"))
                .minStockLevel(5)
                .reorderLevel(10)
                .active(true)
                .build();
    }

    public static Product buildProductC() {
        return Product.builder()
                .sku("PROD-003")
                .barcode("1234567890003")
                .name("Product Gamma (Out of Stock)")
                .purchasePrice(new BigDecimal("50.00"))
                .sellingPrice(new BigDecimal("80.00"))
                .minStockLevel(5)
                .reorderLevel(10)
                .active(true)
                .build();
    }

    public static Product buildInactiveProduct() {
        return Product.builder()
                .sku("PROD-INACTIVE")
                .barcode("1234567890099")
                .name("Inactive Product")
                .purchasePrice(new BigDecimal("100.00"))
                .sellingPrice(new BigDecimal("150.00"))
                .active(false)
                .build();
    }

    private TestDataFactory() {}
}