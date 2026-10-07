package com.inventory.backend.service;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreateStockMovementRequest;
import com.inventory.backend.model.Category;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.StockMovement;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.PermissionRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.RoleRepository;
import com.inventory.backend.repository.StockMovementRepository;
import com.inventory.backend.repository.UserRepository;
import com.inventory.backend.security.RolePermissionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A stock adjustment does not move stock until somebody approves it.
 *
 * <p>The rule exists because a stock count is a claim by whoever counted, not
 * proof of what is on the shelf. If the counter's own entry changed quantity on
 * hand, the ledger would be self-certifying: anyone able to record an
 * adjustment could also rewrite history without anyone else looking.
 *
 * <p>What is asserted here is the state machine, not who is allowed to press the
 * buttons — {@code RolePermissionEnforcementIntegrationTest} covers that. The
 * property under test is that quantity on hand does not move until an approve
 * call succeeds, and that a rejection is recorded rather than silently dropped.
 */
class StockAdjustmentApprovalIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "ApprovalTest#2026";

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired
    private ProductService productService;

    private Long categoryId;
    private Long productId;
    private String inventoryStaffToken;
    private String supervisorToken;

    @BeforeEach
    void seed() throws Exception {
        categoryId = categoryRepository.findAll().stream()
                .filter(c -> "Approval Test".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        Category.builder().name("Approval Test").build()))
                .getId();

        ensureStaff("INVENTORY_STAFF");
        ensureStaff("SUPERVISOR");
        inventoryStaffToken = tokenFor("INVENTORY_STAFF");
        supervisorToken = tokenFor("SUPERVISOR");

        CreateProductRequest request = new CreateProductRequest();
        request.setSku("APPR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        request.setName("Adjustment subject");
        request.setCategoryId(categoryId);
        request.setSellingPrice(new BigDecimal("100.00"));
        request.setPurchasePrice(new BigDecimal("60.00"));
        request.setReorderLevel(5);
        request.setInitialQuantity(20);
        // Created through the service, not by saving the entity directly: the
        // opening balance is only real if it leaves a movement behind, and this
        // test asserts on that ledger.
        productId = productService.createProduct(request).getId();
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private void ensureStaff(String roleName) {
        String email = roleName.toLowerCase() + ".approval@stockflow.local";
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .username(roleName.toLowerCase() + "_approval")
                .firstName(roleName)
                .lastName("Approver")
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(roleFor(roleName))
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .build());
    }

    private Role roleFor(String roleName) {
        return roleRepository.findByName(roleName).orElseGet(() -> roleRepository.save(Role.builder()
                .name(roleName)
                .description(RolePermissionCatalog.descriptionFor(roleName))
                .permissions(Set.of())
                .build()));
    }

    /** Points the role at the shipped matrix before logging in, so the test exercises it. */
    private String tokenFor(String roleName) throws Exception {
        Role role = roleFor(roleName);
        role.setPermissions(resolve(RolePermissionCatalog.permissionsFor(roleName)));
        roleRepository.save(role);
        return login(roleName.toLowerCase() + ".approval@stockflow.local", PASSWORD);
    }

    private Set<Permission> resolve(Set<String> names) {
        return names.stream()
                .map(name -> permissionRepository.findByName(name)
                        .orElseGet(() -> permissionRepository.save(
                                Permission.builder().name(name).build())))
                .collect(Collectors.toSet());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private int quantityOnHand() {
        Product product = productRepository.findById(productId).orElseThrow();
        return product.getQuantity() != null ? product.getQuantity() : 0;
    }

    private CreateStockMovementRequest movement(String type, int quantity) {
        CreateStockMovementRequest request = new CreateStockMovementRequest();
        request.setProductId(productId);
        request.setType(type);
        request.setQuantity(quantity);
        request.setNotes("Cycle count");
        return request;
    }

    /** Records a movement through the API as Inventory Staff and returns its id. */
    private long recordAsInventoryStaff(String type, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/inventory/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + inventoryStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(movement(type, quantity))))
                .andReturn();
        assertStatus(result, 200);
        return new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private StockMovement reload(long movementId) {
        return stockMovementRepository.findById(movementId).orElseThrow();
    }

    /**
     * Asserts the status, attaching the response body when it does not match.
     *
     * <p>A bare "expected 200 but was 500" sends you hunting; the body usually
     * names the cause outright.
     */
    private void assertStatus(MvcResult result, int expected) throws Exception {
        int actual = result.getResponse().getStatus();
        // Explicit UTF-8: getContentAsString() falls back to the container's
        // default charset, which is not UTF-8 on every host.
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(expected, actual, "expected " + expected + " but got " + actual + "; body: " + body);
    }

    private MvcResult review(long movementId, String decision, String token, String note) throws Exception {
        return mockMvc.perform(post("/api/v1/inventory/movements/" + movementId + "/" + decision)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + note + "\"}"))
                .andReturn();
    }

    // ── the state machine ───────────────────────────────────────────────────

    @Test
    @DisplayName("an adjustment is recorded but stock does NOT move until it is approved")
    void adjustmentIsPendingUntilApproved() throws Exception {
        long id = recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, -5);

        StockMovement movement = reload(id);
        assertEquals(StockMovement.Status.PENDING, movement.getStatus(),
                "an adjustment starts as a claim, not as a fact");
        assertEquals(20, quantityOnHand(), "stock must be untouched while the claim is unapproved");

        assertStatus(review(id, "approve", supervisorToken, "Counted"), 200);

        assertEquals(15, quantityOnHand(), "approving must apply the adjustment");
        assertEquals(StockMovement.Status.APPROVED, reload(id).getStatus());
    }

    @Test
    @DisplayName("a rejected adjustment leaves stock exactly as it was")
    void rejectionLeavesStockUnchanged() throws Exception {
        long id = recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, -5);

        assertStatus(review(id, "reject", supervisorToken, "Wrong shelf"), 200);

        assertEquals(20, quantityOnHand(), "a refused claim must not move stock");
        StockMovement movement = reload(id);
        assertEquals(StockMovement.Status.REJECTED, movement.getStatus());
        assertEquals("Wrong shelf", movement.getReviewNote(), "the reason must be kept");
    }

    @Test
    @DisplayName("a stock count waits for approval like an adjustment")
    void stockCountIsPendingUntilApproved() throws Exception {
        assertTrue(StockMovement.requiresApproval(StockMovement.TYPE_STOCK_COUNT));

        long id = recordAsInventoryStaff(StockMovement.TYPE_STOCK_COUNT, -20);
        assertEquals(20, quantityOnHand(), "the count is a claim until it is signed off");

        assertStatus(review(id, "approve", supervisorToken, "Shelf emptied"), 200);
        assertEquals(0, quantityOnHand());
    }

    @Test
    @DisplayName("a pending adjustment appears in the approver's queue and not in the submitter's")
    void pendingAppearsInTheQueue() throws Exception {
        long id = recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, -2);

        String queue = mockMvc.perform(get("/api/v1/inventory/movements/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(queue.contains("\"id\":" + id),
                "the approver must be able to find the request they are meant to review");

        // Inventory Staff hold no approve permission, so the queue is closed to
        // them: the request is visible as data on the product, not as a task.
        mockMvc.perform(get("/api/v1/inventory/movements/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + inventoryStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("approving twice is refused rather than applied twice")
    void doubleApprovalIsRefused() throws Exception {
        long id = recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, -5);

        assertStatus(review(id, "approve", supervisorToken, "First look"), 200);
        assertEquals(15, quantityOnHand());

        // A second approval would silently deduct another 5 units. It must be a
        // visible refusal, not a no-op that hides a duplicated click.
        MvcResult second = review(id, "approve", supervisorToken, "Clicked twice");
        assertEquals(400, second.getResponse().getStatus());
        assertEquals(15, quantityOnHand(), "a refused re-approval must not move stock again");
    }

    @Test
    @DisplayName("an approver records who signed it off and when")
    void approvalIsAttributed() throws Exception {
        long id = recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, 10);

        assertStatus(review(id, "approve", supervisorToken, "Recount confirmed"), 200);

        StockMovement movement = reload(id);
        assertEquals(30, quantityOnHand());
        assertNotNull(movement.getApprovedBy(), "an approved movement must name its approver");
        assertNotNull(movement.getApprovedAt(), "an approved movement must be timestamped");
        assertEquals("Recount confirmed", movement.getReviewNote(),
                "the approver's own note is kept verbatim");
    }

    @Test
    @DisplayName("someone who already holds approval is not made to queue their own work")
    void approverDoesNotQueueTheirOwnSubmission() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper()
                                .writeValueAsString(movement(StockMovement.TYPE_ADJUSTMENT, -4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(StockMovement.Status.APPROVED));

        assertEquals(16, quantityOnHand(),
                "a Supervisor may adjust stock, so the entry applies immediately");
    }

    @Test
    @DisplayName("an ordinary receipt applies at once — it is not a claim about a count")
    void receiptAppliesImmediately() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + inventoryStaffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper()
                                .writeValueAsString(movement(StockMovement.TYPE_IN, 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(StockMovement.Status.APPROVED));

        assertEquals(50, quantityOnHand(), "goods arriving are goods in hand");
    }

    @Test
    @DisplayName("an adjustment is refused when it would drive stock negative")
    void adjustmentCannotGoBelowZero() throws Exception {
        long id = recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, -500);

        assertStatus(review(id, "approve", supervisorToken, "Approved anyway"), 200);

        assertEquals(0, quantityOnHand(), "stock stops at zero rather than going negative");
        StockMovement movement = reload(id);
        // The clamp is a fact about the movement, so it goes on the movement's
        // own narrative. `reviewNote` stays the approver's words, which are a
        // different thing and must not be overwritten.
        assertNotNull(movement.getNotes(), "clamping at zero has to be recorded somewhere");
        assertTrue(movement.getNotes().toLowerCase().contains("negative"),
                "the note should record that the movement would have gone negative; was: "
                        + movement.getNotes());
        assertEquals(0, quantityOnHand());
    }

    @Test
    @DisplayName("submitter and approver cannot be the same person by accident")
    void theRequestIsNotSelfApproving() throws Exception {
        // Inventory Staff submitted the claim in every other test and could not
        // approve it. This asserts the other half: holding INVENTORY_ADJUST is
        // not the same thing as holding STOCK_ADJUSTMENT_APPROVE, and the
        // submitter's own token must not be accepted on the approve endpoint.
        long id = recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, -5);

        assertStatus(review(id, "approve", inventoryStaffToken, "Self approval"), 403);

        assertEquals(20, quantityOnHand());
        assertEquals(StockMovement.Status.PENDING, reload(id).getStatus());
    }

    @Test
    @DisplayName("an unauthenticated caller cannot approve anything")
    void approvalRequiresAuthentication() throws Exception {
        long id = recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, -5);

        MvcResult result = mockMvc.perform(post("/api/v1/inventory/movements/" + id + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status == 401 || status == 403,
                "an anonymous caller must be refused, but the endpoint answered " + status);
        assertEquals(20, quantityOnHand());
        assertEquals(StockMovement.Status.PENDING, reload(id).getStatus());
    }

    @Test
    @DisplayName("every movement for the product is retained, so the ledger can be reconciled")
    void movementsAccountForTheBalance() throws Exception {
        recordAsInventoryStaff(StockMovement.TYPE_ADJUSTMENT, -5);
        recordAsInventoryStaff(StockMovement.TYPE_IN, 12);
        recordAsInventoryStaff(StockMovement.TYPE_STOCK_COUNT, -2);

        List<StockMovement> movements = stockMovementRepository.findAll().stream()
                .filter(m -> m.getProduct() != null && productId.equals(m.getProduct().getId()))
                .toList();

        // One OPENING of 20 from product creation, plus the three above.
        assertEquals(4, movements.size(),
                "pending claims are still recorded — the history is not rewritten when "
                        + "they are approved or dismissed");

        // Only APPROVED movements count toward the balance. The opening (20) and
        // the receipt (+12) are approved; the adjustment (-5) and the stock count
        // (-2) are still claims, so they are absent from the arithmetic even
        // though they are present in the history.
        int approvedTotal = movements.stream()
                .filter(m -> StockMovement.Status.APPROVED.equals(m.getStatus()))
                .mapToInt(m -> m.getQuantity() == null ? 0 : m.getQuantity())
                .sum();
        assertEquals(32, approvedTotal,
                "a pending claim must not appear in the balance");

        // And the product agrees with the arithmetic, not with the raw history.
        assertEquals(32, quantityOnHand());
    }
}
