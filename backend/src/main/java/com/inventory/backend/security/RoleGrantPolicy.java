package com.inventory.backend.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Decides whether a caller may give somebody else a role.
 *
 * <p>This exists because hiding a role from a dropdown is not a control. The
 * staff form offers a filtered list, but a caller can always
 * {@code POST /api/v1/staff} or {@code PATCH /api/v1/users/{id}/role} with any
 * role name they like, so the rule has to hold at the service layer too.
 *
 * <p>The rule is data in {@link RolePermissionCatalog}:
 * <ul>
 *   <li>{@code SUPER_ADMIN} may grant Manager, Supervisor, Cashier, Inventory Staff.</li>
 *   <li>{@code MANAGER} may grant Supervisor, Cashier and Inventory Staff — never
 *       Manager, never Super Admin.</li>
 *   <li>Everyone else may grant nothing.</li>
 *   <li>{@code SUPER_ADMIN} is never grantable by anybody, including Super Admin.</li>
 * </ul>
 */
@Component
public class RoleGrantPolicy {

    /**
     * @throws AccessDeniedException when {@code actorRole} may not hand out
     *         {@code targetRole}. Thrown as an access-denied rather than a bad
     *         request so the API answers 403, matching how a missing authority on
     *         the same operation would be reported.
     */
    public void requireAssignable(String actorRole, String targetRole) {
        if (targetRole == null || targetRole.isBlank()) {
            throw new IllegalArgumentException("Role is required.");
        }
        if (RolePermissionCatalog.isSuperAdmin(targetRole)) {
            // Deliberately denied for Super Admin too: the role is provisioned,
            // never handed out from a staff screen.
            throw new AccessDeniedException(
                    "The Super Admin role cannot be assigned from staff management.");
        }
        if (!RolePermissionCatalog.canAssignRole(actorRole, targetRole)) {
            throw new AccessDeniedException(describeRefusal(actorRole, targetRole));
        }
    }

    /**
     * Guards a role <em>change</em> on an existing account.
     *
     * <p>A change is only permitted when the caller could equally have created
     * the account with the new role. Without that symmetry, a Manager could
     * promote an existing Cashier to Manager even though creating one is denied.
     */
    public void requireChangeAllowed(String actorRole, String currentRole, String newRole) {
        requireAssignable(actorRole, newRole);
        if (RolePermissionCatalog.isSuperAdmin(currentRole)) {
            throw new AccessDeniedException(
                    "The Super Admin role cannot be changed from staff management.");
        }
    }

    /**
     * A caller may only act on accounts whose role they are allowed to manage,
     * and never on their own account through the staff screens.
     */
    public void requireManageableTarget(String actorRole, String targetRole) {
        if (RolePermissionCatalog.isSuperAdmin(targetRole)) {
            throw new AccessDeniedException(
                    "Super Admin accounts cannot be managed from staff management.");
        }
        if (!RolePermissionCatalog.assignableRoles(actorRole).contains(targetRole)) {
            throw new AccessDeniedException(describeRefusal(actorRole, targetRole));
        }
    }

    private String describeRefusal(String actorRole, String targetRole) {
        String actor = RolePermissionCatalog.isSuperAdmin(actorRole) ? "Super Admin" : actorRole;
        return "A " + actor + " cannot assign the " + targetRole + " role.";
    }
}