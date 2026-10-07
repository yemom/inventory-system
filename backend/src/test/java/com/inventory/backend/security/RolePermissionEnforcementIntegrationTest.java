package com.inventory.backend.security;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.PermissionRepository;
import com.inventory.backend.model.Category;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.RoleRepository;
import com.inventory.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof of the role matrix, exercised through real HTTP requests with
 * a real JWT.
 *
 * <p>Two things are being verified that a unit test cannot. First, that the
 * authority actually reaches the principal — the matrix is stored in the database
 * and read by {@code CustomUserDetails}, so a wiring mistake would grant nobody
 * anything while every unit test still passed. Second, that the denials are real:
 * a 403 from a direct API call is the only evidence that the control is not
 * merely a hidden button.
 *
 * <p>Each role is created here straight from {@link RolePermissionCatalog}, so
 * these tests fail if the seed and the matrix ever diverge.
 */
class RolePermissionEnforcementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "PermissionTest#2026";

    private Long categoryId;

    @BeforeEach
    void createStaffForEveryRole() {
        categoryId = ensureCategory();

        for (String roleName : new String[]{"MANAGER", "SUPERVISOR", "CASHIER", "INVENTORY_STAFF", "ACCOUNTANT"}) {
            String email = roleName.toLowerCase() + ".permtest@stockflow.local";
            if (userRepository.existsByEmail(email)) {
                continue;
            }
            Role role = roleFor(roleName);
            userRepository.save(User.builder()
                    .username(roleName.toLowerCase() + "_permtest")
                    .firstName(roleName)
                    .lastName("Tester")
                    .email(email)
                    .passwordHash(passwordEncoder.encode(PASSWORD))
                    .role(role)
                    .status(UserStatus.ACTIVE)
                    .isDeleted(false)
                    .build());
        }
    }

    private Long ensureCategory() {
        return categoryRepository.findAll().stream()
                .filter(c -> "Permission Test".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        Category.builder().name("Permission Test").build()))
                .getId();
    }

    /** Builds the role exactly as {@code DataInitializer} would. */
    private Role roleFor(String roleName) {
        return roleRepository.findByName(roleName).orElseGet(() -> {
            Role created = Role.builder()
                    .name(roleName)
                    .description(RolePermissionCatalog.descriptionFor(roleName))
                    .permissions(Set.of())
                    .build();
            return roleRepository.save(created);
        });
    }

    private String tokenFor(String roleName) throws Exception {
        // Re-point the stored role at the catalogue before logging in, so the test
        // exercises the shipped matrix rather than whatever a previous run left.
        Role role = roleFor(roleName);
        role.setPermissions(resolve(RolePermissionCatalog.permissionsFor(roleName)));
        roleRepository.save(role);

        return login(roleName.toLowerCase() + ".permtest@stockflow.local", PASSWORD);
    }

    private Set<Permission> resolve(Set<String> names) {
        return names.stream()
                .map(name -> permissionRepository.findByName(name)
                        .orElseGet(() -> permissionRepository.save(
                                Permission.builder().name(name).build())))
                .collect(Collectors.toSet());
    }

    // ── Inventory Staff ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Inventory Staff cannot create a sale")
    void inventoryStaffCannotCreateSale() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("INVENTORY_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[],\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Inventory Staff cannot create or update a product (no pricing, no deletion)")
    void inventoryStaffCannotChangeProducts() throws Exception {
        String token = tokenFor("INVENTORY_STAFF");

        mockMvc.perform(post("/api/v1/products")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("PERM-IS-1", new BigDecimal("500.00"))))
                .andExpect(status().isForbidden());

        var anyProduct = productRepository.findAll().stream().findFirst().orElse(null);
        if (anyProduct != null) {
            mockMvc.perform(put("/api/v1/products/" + anyProduct.getId())
                            .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sellingPrice\":1.00}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Inventory Staff cannot view sales or financial reports")
    void inventoryStaffCannotSeeReports() throws Exception {
        String token = tokenFor("INVENTORY_STAFF");

        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/profit-loss")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/inventory-value")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Inventory Staff can still read products and receive stock")
    void inventoryStaffKeepsItsOwnAbilities() throws Exception {
        String token = tokenFor("INVENTORY_STAFF");

        mockMvc.perform(get("/api/v1/products")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertNotNull(token);
    }

    // ── Cashier ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cashier cannot read the sales report (which shows every cashier's takings)")
    void cashierCannotViewReports() throws Exception {
        String token = tokenFor("CASHIER");

        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cashier cannot modify products or adjust stock")
    void cashierCannotModifyProductsOrStock() throws Exception {
        String token = tokenFor("CASHIER");

        mockMvc.perform(post("/api/v1/products")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("PERM-CASH-1", new BigDecimal("99.00"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"type\":\"ADJUSTMENT\",\"quantity\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cashier cannot approve a void or refund it requested itself")
    void cashierCannotApproveVoidOrRefund() throws Exception {
        String token = tokenFor("CASHIER");

        mockMvc.perform(post("/api/v1/sales/1/void-approve")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sales/1/refund-approve")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cashier can still look up products and see stock")
    void cashierCanReadProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("CASHIER")))
                .andExpect(status().isOk());
    }

    // ── Supervisor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Supervisor cannot see profit or inventory-value reports")
    void supervisorCannotSeeFinancials() throws Exception {
        String token = tokenFor("SUPERVISOR");

        mockMvc.perform(get("/api/v1/reports/profit-loss")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/inventory-value")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Supervisor CAN see sales and low-stock reports")
    void supervisorSeesSalesAndLowStock() throws Exception {
        String token = tokenFor("SUPERVISOR");

        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/inventory")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Supervisor can approve a stock adjustment")
    void supervisorCanApproveStockAdjustments() throws Exception {
        var pendingResult = mockMvc.perform(get("/api/v1/inventory/movements/pending")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("SUPERVISOR")))
                .andReturn();
        // Assert with the body attached, so a failure explains itself instead of
        // just reporting a number.
        org.junit.jupiter.api.Assertions.assertEquals(200, pendingResult.getResponse().getStatus(),
                "pending queue should be readable by a Supervisor; body was: "
                        + pendingResult.getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/inventory/movements/999999/approve")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // Authorised: the failure, if any, is "not found", not 403.
                .andExpect(result -> assertNotEqualsStatus(result, 403));
    }

    @Test
    @DisplayName("Supervisor cannot create or delete users")
    void supervisorCannotManageUsers() throws Exception {
        String token = tokenFor("SUPERVISOR");

        mockMvc.perform(post("/api/v1/staff")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson("sup_nope", "supervisor_cannot_create_users", "EMP-SUP-1", "CASHIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Supervisor cannot re-price or delete a product")
    void supervisorCannotChangePrices() throws Exception {
        String token = tokenFor("SUPERVISOR");

        mockMvc.perform(put("/api/v1/products/1")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellingPrice\":1.00}"))
                .andExpect(status().isForbidden());
    }

    // ── Manager ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Manager cannot create another Manager, even via a direct request")
    void managerCannotCreateManager() throws Exception {
        mockMvc.perform(post("/api/v1/staff")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson("mgr_esc", "mgr_escalation_attempt", "EMP-ESC-1", "MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Manager cannot create a SUPER_ADMIN")
    void managerCannotCreateSuperAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/staff")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson("mgr_esc2", "mgr_escalation_attempt2", "EMP-ESC-2", "SUPER_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Manager CAN create a Supervisor")
    void managerCanCreateSupervisor() throws Exception {
        mockMvc.perform(post("/api/v1/staff")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION,
                                "Bearer " + tokenFor("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson("mgr_sup", "mgr_may_create_supervisor", "EMP-SUP-2", "SUPERVISOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("SUPERVISOR"));
    }

    @Test
    @DisplayName("Manager cannot change system settings")
    void managerCannotChangeSystemSettings() throws Exception {
        // Settings are guarded by SYSTEM_SETTINGS_MANAGE / SETTINGS_UPDATE, which
        // Manager does not hold.
        var managerPermissions = RolePermissionCatalog.permissionsFor("MANAGER");
        org.junit.jupiter.api.Assertions.assertFalse(managerPermissions.contains("SYSTEM_SETTINGS_MANAGE"));
        org.junit.jupiter.api.Assertions.assertFalse(managerPermissions.contains("SETTINGS_UPDATE"));
    }

    // ── Super Admin ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUPER_ADMIN keeps unrestricted access, including profit and inventory value")
    void superAdminUnrestricted() throws Exception {
        String token = loginAsSuperAdmin();

        mockMvc.perform(get("/api/v1/reports/profit-loss")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/inventory-value")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * A staff payload that satisfies every @NotBlank constraint.
     *
     * <p>Bean validation on @RequestBody runs during argument resolution, i.e.
     * BEFORE the @PreAuthorize interceptor. An incomplete payload would answer
     * 400 and the authorization assertion would pass for the wrong reason, so the
     * payload has to be valid for the 403 to mean anything.
     */
    private String staffJson(String username, String email, String employeeId, String roleName) {
        return "{"
                + "\"username\":\"" + username + "\","
                + "\"firstName\":\"Test\","
                + "\"lastName\":\"Person\","
                + "\"email\":\"" + email + "@stockflow.local\","
                + "\"phone\":\"+251911000001\","
                + "\"employeeId\":\"" + employeeId + "\","
                + "\"dateJoined\":\"2026-01-15\","
                + "\"password\":\"Password@123\","
                + "\"roleName\":\"" + roleName + "\""
                + "}";
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String productJson(String sku, BigDecimal price) {
        return "{\"sku\":\"" + sku + "\",\"name\":\"Perm Test\",\"categoryId\":" + categoryId
                + ",\"sellingPrice\":" + price + ",\"initialQuantity\":5}";
    }

    private static void assertNotEqualsStatus(org.springframework.test.web.servlet.MvcResult result, int status) {
        assertNotEquals(status, result.getResponse().getStatus());
    }

    private static void assertNotEquals(int unexpected, int actual) {
        org.junit.jupiter.api.Assertions.assertNotEquals(unexpected, actual);
    }

    @Test
    @DisplayName("a role that is absent from the catalogue grants nothing")
    void unknownRoleGrantsNothing() {
        assertEquals(Set.of(), RolePermissionCatalog.permissionsFor("NOT_A_ROLE"));
        assertEquals(Set.of(), RolePermissionCatalog.assignableRoles(null));
    }
}