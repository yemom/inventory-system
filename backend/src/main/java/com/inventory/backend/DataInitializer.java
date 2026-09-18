package com.inventory.backend;

import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.PermissionRepository;
import com.inventory.backend.repository.RoleRepository;
import com.inventory.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.username:${SUPER_ADMIN_USERNAME:superadmin}}")
    private String superAdminUsername;

    @Value("${app.super-admin.email:${SUPER_ADMIN_EMAIL:admin@stockflow.local}}")
    private String superAdminEmail;

    @Value("${app.super-admin.password:${SUPER_ADMIN_PASSWORD:Admin@StockFlow2026!}}")
    private String superAdminPassword;

    @Value("${app.super-admin.first-name:${SUPER_ADMIN_FIRST_NAME:Super}}")
    private String superAdminFirstName;

    @Value("${app.super-admin.last-name:${SUPER_ADMIN_LAST_NAME:Admin}}")
    private String superAdminLastName;

    @Override
    public void run(String... args) {
        log.info("=== DataInitializer: seeding permissions, roles and users ===");

        // 1. Create Permissions
        List<String> allPermissionNames = Arrays.asList(
                "USER_CREATE", "USER_READ", "USER_UPDATE", "USER_DEACTIVATE",
                "ROLE_READ", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE", "PERMISSION_READ", "PERMISSION_ASSIGN",
                "PRODUCT_CREATE", "PRODUCT_READ", "PRODUCT_UPDATE", "PRODUCT_DEACTIVATE",
                "CATEGORY_MANAGE", "WAREHOUSE_CREATE", "WAREHOUSE_READ", "WAREHOUSE_UPDATE", "WAREHOUSE_MANAGE",
                "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_TRANSFER", "INVENTORY_RECEIVE",
                "SUPPLIER_CREATE", "SUPPLIER_READ", "SUPPLIER_UPDATE", "SUPPLIER_MANAGE",
                "CUSTOMER_CREATE", "CUSTOMER_READ", "CUSTOMER_UPDATE", "CUSTOMER_MANAGE",
                "PURCHASE_CREATE", "PURCHASE_READ", "PURCHASE_UPDATE", "PURCHASE_APPROVE", "PURCHASE_CANCEL",
                "SALE_CREATE", "SALE_READ", "SALE_UPDATE", "SALE_CANCEL",
                "RETURN_CREATE", "RETURN_READ", "RETURN_APPROVE",
                "PAYMENT_CREATE", "PAYMENT_READ", "PAYMENT_UPDATE",
                "EXPENSE_CREATE", "EXPENSE_READ", "EXPENSE_UPDATE", "EXPENSE_APPROVE",
                "REPORT_VIEW", "REPORT_EXPORT", "AUDIT_LOG_VIEW", "SYSTEM_SETTINGS_MANAGE",
                "DAMAGE_RECORD", "EXPIRY_RECORD", "STOCK_MOVEMENT_READ",
                "SALES_RETURN_CREATE", "SALES_REPORT_VIEW", "INVENTORY_REPORT_VIEW",
                "RECEIVABLE_READ", "PAYABLE_READ", "PROFIT_LOSS_READ", "FINANCIAL_REPORT_READ", "FINANCIAL_REPORT_EXPORT",
                "DASHBOARD_VIEW", "STAFF_VIEW", "STAFF_CREATE", "STAFF_UPDATE", "STAFF_DELETE",
                "SETTINGS_VIEW", "SETTINGS_UPDATE"
        );

        Set<Permission> allPermissions = allPermissionNames.stream()
                .map(this::ensurePermission)
                .collect(Collectors.toSet());

        // 2. Create Roles
        Role adminRole = ensureRole("SUPER_ADMIN", "Full system access", allPermissions);
        Role managerRole = ensureRole("MANAGER", "Operational management", getPermissionsFor(allPermissions,
                "DASHBOARD_VIEW", "USER_READ", "STAFF_VIEW", "PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE", "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_TRANSFER", "INVENTORY_RECEIVE", "PURCHASE_CREATE", "PURCHASE_READ", "PURCHASE_APPROVE", "SALE_READ", "SALE_CREATE", "CUSTOMER_CREATE", "CUSTOMER_READ", "CUSTOMER_UPDATE", "SUPPLIER_CREATE", "SUPPLIER_READ", "SUPPLIER_UPDATE", "PAYMENT_READ", "REPORT_VIEW", "REPORT_EXPORT", "SALES_REPORT_VIEW", "INVENTORY_REPORT_VIEW", "WAREHOUSE_READ", "AUDIT_LOG_VIEW"
        ));
        Role supervisorRole = ensureRole("SUPERVISOR", "Inventory and sales supervision", getPermissionsFor(allPermissions,
                "DASHBOARD_VIEW", "PRODUCT_READ", "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_TRANSFER", "INVENTORY_RECEIVE", "WAREHOUSE_READ", "STOCK_MOVEMENT_READ", "SALE_READ", "SALES_REPORT_VIEW", "INVENTORY_REPORT_VIEW", "USER_READ", "STAFF_VIEW"
        ));
        Role keeperRole = ensureRole("STOREKEEPER", "Inventory and receiving", getPermissionsFor(allPermissions,
                "PRODUCT_READ", "INVENTORY_READ", "INVENTORY_RECEIVE", "INVENTORY_TRANSFER", "INVENTORY_ADJUST", "WAREHOUSE_READ", "STOCK_MOVEMENT_READ", "DAMAGE_RECORD", "EXPIRY_RECORD", "INVENTORY_REPORT_VIEW"
        ));
        Role inventoryStaffRole = ensureRole("INVENTORY_STAFF", "Inventory staff operations", getPermissionsFor(allPermissions,
                "DASHBOARD_VIEW", "PRODUCT_READ", "INVENTORY_READ", "INVENTORY_RECEIVE", "INVENTORY_TRANSFER", "WAREHOUSE_READ", "STOCK_MOVEMENT_READ", "DAMAGE_RECORD", "EXPIRY_RECORD"
        ));
        Role cashierRole = ensureRole("CASHIER", "POS and sales", getPermissionsFor(allPermissions,
                "DASHBOARD_VIEW", "PRODUCT_READ", "INVENTORY_READ", "CUSTOMER_CREATE", "CUSTOMER_READ", "CUSTOMER_UPDATE", "SALE_CREATE", "SALE_READ", "SALES_RETURN_CREATE", "PAYMENT_CREATE", "PAYMENT_READ", "SALES_REPORT_VIEW"
        ));
        Role accountRole = ensureRole("ACCOUNTANT", "Financial reports", getPermissionsFor(allPermissions,
                "SALE_READ", "PURCHASE_READ", "PAYMENT_CREATE", "PAYMENT_READ", "EXPENSE_CREATE", "EXPENSE_READ", "EXPENSE_UPDATE", "CUSTOMER_READ", "SUPPLIER_READ", "RECEIVABLE_READ", "PAYABLE_READ", "PROFIT_LOSS_READ", "FINANCIAL_REPORT_READ", "FINANCIAL_REPORT_EXPORT", "REPORT_VIEW"
        ));

        // 3. Create or update initial Super Admin
        java.util.Optional<User> existingAdmin = userRepository.findByUsername(superAdminUsername);
        if (existingAdmin.isPresent()) {
            User u = existingAdmin.get();
            u.setEmail(superAdminEmail);
            u.setPasswordHash(passwordEncoder.encode(superAdminPassword));
            u.setStatus(UserStatus.ACTIVE);
            userRepository.save(u);
            log.info("  Updated Super Admin credentials: {} ({})", superAdminUsername, superAdminEmail);
        } else if (!userRepository.existsByEmail(superAdminEmail)) {
            User u = User.builder()
                    .firstName(superAdminFirstName)
                    .lastName(superAdminLastName)
                    .username(superAdminUsername)
                    .email(superAdminEmail)
                    .passwordHash(passwordEncoder.encode(superAdminPassword))
                    .role(adminRole)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(u);
            log.info("  Created initial Super Admin: {} ({})", superAdminUsername, superAdminEmail);
        }

        log.info("=== DataInitializer: done ===");
    }

    private Permission ensurePermission(String name) {
        return permissionRepository.findByName(name).orElseGet(() -> {
            Permission p = Permission.builder().name(name).build();
            return permissionRepository.save(p);
        });
    }

    private Role ensureRole(String name, String description, Set<Permission> permissions) {
        return roleRepository.findByName(name).map(r -> {
            r.setPermissions(permissions); // update permissions if they changed
            return roleRepository.save(r);
        }).orElseGet(() -> {
            Role r = Role.builder().name(name).description(description).permissions(permissions).build();
            log.info("  Created role: {}", name);
            return roleRepository.save(r);
        });
    }

    private Set<Permission> getPermissionsFor(Set<Permission> allPermissions, String... names) {
        Set<String> nameSet = new HashSet<>(Arrays.asList(names));
        return allPermissions.stream()
                .filter(p -> nameSet.contains(p.getName()))
                .collect(Collectors.toSet());
    }
}
