package com.inventory.backend;

import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.security.RolePermissionCatalog;
import com.inventory.backend.repository.PermissionRepository;
import com.inventory.backend.repository.RoleRepository;
import com.inventory.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
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

    // No inline defaults for the password: it is a credential, and a fallback
    // baked into source is a published credential. application.properties maps
    // SUPER_ADMIN_PASSWORD, and StartupConfigValidator refuses to boot when the
    // result is missing or still a known placeholder.
    @Value("${app.super-admin.username:${SUPER_ADMIN_USERNAME:superadmin}}")
    private String superAdminUsername;

    @Value("${app.super-admin.email:${SUPER_ADMIN_EMAIL:admin@stockflow.local}}")
    private String superAdminEmail;

    @Value("${app.super-admin.password}")
    private String superAdminPassword;

    @Value("${app.super-admin.first-name:${SUPER_ADMIN_FIRST_NAME:Super}}")
    private String superAdminFirstName;

    @Value("${app.super-admin.last-name:${SUPER_ADMIN_LAST_NAME:Admin}}")
    private String superAdminLastName;

    @Override
    public void run(String... args) {
        log.info("=== DataInitializer: seeding permissions, roles and users ===");

        // 1. Create Permissions
        //    The catalogue is the single source of truth for what exists, so a
        //    permission cannot be enforced by an endpoint without a matching row.
        Set<Permission> allPermissions = RolePermissionCatalog.ALL_PERMISSIONS.stream()
                .map(this::ensurePermission)
                .collect(Collectors.toSet());

        // 2. Create Roles
        //    The role -> permission matrix lives in RolePermissionCatalog, which is
        //    asserted by RolePermissionCatalogTest. It used to be spelled out here,
        //    which is how SUPERVISOR and INVENTORY_STAFF drifted into holding
        //    PRODUCT_UPDATE / PRODUCT_DEACTIVATE and CASHIER into holding
        //    SALES_REPORT_VIEW - permissions no endpoint should ever have given them.
        Role adminRole = ensureRole(RolePermissionCatalog.SUPER_ADMIN, allPermissions);
        // ensureRole persists as a side effect, so the remaining roles are called
        // for that reason alone; only the super admin entity is needed below.
        ensureRole(RolePermissionCatalog.MANAGER, allPermissions);
        ensureRole(RolePermissionCatalog.SUPERVISOR, allPermissions);
        ensureRole(RolePermissionCatalog.INVENTORY_STAFF, allPermissions);
        ensureRole(RolePermissionCatalog.CASHIER, allPermissions);
        ensureRole(RolePermissionCatalog.ACCOUNTANT, allPermissions);

        // Legacy row: STOREKEEPER predates the rename to INVENTORY_STAFF. It gets
        // the same permissions so existing accounts keep working, but it is not in
        // the assignable set, so no new account is ever created with it.
        ensureRole(RolePermissionCatalog.STOREKEEPER, allPermissions);

        // 3. Create or update initial Super Admin
        java.util.Optional<User> existingAdmin = userRepository.findByUsername(superAdminUsername);
        if (existingAdmin.isPresent()) {
            User u = existingAdmin.get();

            // The password IS refreshed on every start: that is how an operator
            // rotates the bootstrap credential, and it is why the value is never
            // written to the log.
            u.setPasswordHash(passwordEncoder.encode(superAdminPassword));
            u.setStatus(UserStatus.ACTIVE);

            // The email is NOT overwritten once set. It used to be, which meant
            // any process starting the app without SUPER_ADMIN_EMAIL silently
            // repointed the administrator's login — two instances with different
            // values would fight over it, and a stray local run against a shared
            // database could lock the real owner out. Moving the address is a
            // deliberate act, so it is done in the UI, and a mismatch is reported
            // instead of being applied.
            if (u.getEmail() == null || u.getEmail().isBlank()) {
                u.setEmail(superAdminEmail);
                log.info("  Super Admin '{}' had no email; set it to the configured SUPER_ADMIN_EMAIL.", superAdminUsername);
            } else if (!u.getEmail().equalsIgnoreCase(superAdminEmail)) {
                log.warn("  Super Admin '{}' already exists with email {}; the configured SUPER_ADMIN_EMAIL ({}) "
                                + "was NOT applied. Change the address in the UI if you intend to move it.",
                        superAdminUsername, u.getEmail(), superAdminEmail);
            }

            userRepository.save(u);
            log.info("  Refreshed Super Admin password for '{}' ({}).", superAdminUsername, u.getEmail());
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

    /**
     * Creates or re-points a role at the permissions the catalogue says it has.
     *
     * <p>Permissions are <b>replaced</b>, not merged, so a permission removed from
     * the matrix is also removed from the database on the next boot. That is the
     * behaviour that makes the fix for the drifted roles take effect on an
     * existing deployment instead of only on a fresh one.
     */
    private Role ensureRole(String name, Set<Permission> allPermissions) {
        Set<Permission> permissions = allPermissions.stream()
                .filter(p -> RolePermissionCatalog.permissionsFor(name).contains(p.getName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return roleRepository.findByName(name).map(r -> {
            if (!r.getPermissions().equals(permissions)) {
                r.setPermissions(permissions);
                r.setDescription(RolePermissionCatalog.descriptionFor(name));
                log.info("  Updated role {} permissions ({} granted)", name, permissions.size());
            }
            return roleRepository.save(r);
        }).orElseGet(() -> {
            Role r = Role.builder()
                    .name(name)
                    .description(RolePermissionCatalog.descriptionFor(name))
                    .permissions(permissions)
                    .build();
            log.info("  Created role: {} ({} permissions)", name, permissions.size());
            return roleRepository.save(r);
        });
    }
}
