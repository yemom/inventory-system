package com.inventory.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreateSaleOrderItem;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.model.Category;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.model.StockMovement;
import com.inventory.backend.model.User;
import com.inventory.backend.model.UserStatus;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.PermissionRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.RoleRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.UserRepository;
import com.inventory.backend.security.RolePermissionCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Cashier can ask for a sale to be voided; only a Supervisor can grant it.
 *
 * <p>The separation is the control. Whoever is at the till should not also be
 * the person who authorises handing the money back — that is what makes a void
 * request worth reviewing rather than a formality. So the request leaves stock
 * where it is, and nothing moves until an approve call succeeds.
 *
 * <p>Who may press which button is covered by
 * {@code RolePermissionEnforcementIntegrationTest}. This class is about the
 * consequence: does the stock actually come back, and exactly once.
 */
class SaleApprovalWorkflowIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "SaleApproval#2026";

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private SaleOrderRepository saleOrderRepository;
    @Autowired
    private com.inventory.backend.repository.StockMovementRepository stockMovementRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper json;

    private Long productId;
    private String cashierToken;
    private String supervisorToken;

    @BeforeEach
    void seed() throws Exception {
        Long categoryId = categoryRepository.findAll().stream()
                .filter(c -> "Sale Approval Test".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        Category.builder().name("Sale Approval Test").build()))
                .getId();

        ensureStaff("CASHIER");
        ensureStaff("SUPERVISOR");
        cashierToken = tokenFor("CASHIER");
        supervisorToken = tokenFor("SUPERVISOR");

        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SALE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        request.setName("Sold item");
        request.setCategoryId(categoryId);
        request.setSellingPrice(new BigDecimal("100.00"));
        request.setPurchasePrice(new BigDecimal("60.00"));
        request.setReorderLevel(5);
        request.setInitialQuantity(40);
        productId = productService.createProduct(request).getId();
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private void ensureStaff(String roleName) {
        String email = roleName.toLowerCase() + ".saleapproval@stockflow.local";
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .username(roleName.toLowerCase() + "_saleapproval")
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

    private String tokenFor(String roleName) throws Exception {
        Role role = roleFor(roleName);
        role.setPermissions(resolve(RolePermissionCatalog.permissionsFor(roleName)));
        roleRepository.save(role);
        return login(roleName.toLowerCase() + ".saleapproval@stockflow.local", PASSWORD);
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

    /** Sells 10 units at the till. Stock drops immediately; a sale is a fact. */
    private Long sellTen(String paymentMethod) throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(10);
        item.setUnitPrice(new BigDecimal("100.00"));

        CreateSaleOrderRequest request = new CreateSaleOrderRequest();
        request.setItems(List.of(item));
        request.setPaymentMethod(paymentMethod);
        request.setPaymentStatus("PAID");
        // Distinct per call so the idempotency guard does not collapse two
        // deliberate sales into one.
        request.setIdempotencyKey(UUID.randomUUID().toString());

        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(body).path("data").path("id").asLong();
    }

    private MvcResult call(String path, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sales" + path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private void assertStatus(MvcResult result, int expected) throws Exception {
        int actual = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(expected, actual, "expected " + expected + " but got " + actual + "; body: " + body);
    }

    private SaleOrder reload(Long id) {
        return saleOrderRepository.findById(id).orElseThrow();
    }

    // ── void ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a Cashier's void request leaves stock exactly where the sale put it")
    void voidRequestDoesNotMoveStock() throws Exception {
        Long saleId = sellTen("CASH");
        assertEquals(30, quantityOnHand(), "the sale already took the goods");

        assertStatus(call("/" + saleId + "/void-request", cashierToken,
                "{\"reason\":\"Customer changed their mind\"}"), 200);

        assertEquals(30, quantityOnHand(),
                "a request is a claim; the goods do not come back until it is approved");

        SaleOrder order = reload(saleId);
        assertEquals(StockMovement.Status.PENDING, order.getVoidStatus());
        assertEquals("Customer changed their mind", order.getVoidReason());
        assertNotNull(order.getVoidRequestedBy(), "the request must name who made it");
        assertNotEquals("CANCELLED", order.getStatus(),
                "asking for a void must not itself cancel the sale");
    }

    @Test
    @DisplayName("a Supervisor approving a void returns the stock")
    void supervisorCanApproveAVoid() throws Exception {
        Long saleId = sellTen("CASH");

        assertStatus(call("/" + saleId + "/void-request", cashierToken, "{\"reason\":\"Wrong item\"}"), 200);
        assertStatus(call("/" + saleId + "/void-approve", supervisorToken,
                "{\"note\":\"Confirmed with the customer\"}"), 200);

        assertEquals(40, quantityOnHand(), "approving a void must put the goods back");
        SaleOrder order = reload(saleId);
        assertEquals("CANCELLED", order.getStatus());
        assertEquals(StockMovement.Status.APPROVED, order.getVoidStatus());
        assertNotNull(order.getVoidApprovedBy());
        assertNotNull(order.getVoidApprovedAt());
    }

    @Test
    @DisplayName("a rejected void changes nothing at all")
    void rejectedVoidChangesNothing() throws Exception {
        Long saleId = sellTen("CASH");

        assertStatus(call("/" + saleId + "/void-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);
        assertStatus(call("/" + saleId + "/void-reject", supervisorToken,
                "{\"note\":\"Goods were consumed\"}"), 200);

        assertEquals(30, quantityOnHand(), "a refused void must not restock");
        SaleOrder order = reload(saleId);
        assertEquals(StockMovement.Status.REJECTED, order.getVoidStatus());
        assertNotEquals("CANCELLED", order.getStatus(), "the sale stands");
    }

    // ── refund ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a refund works like a void: request, then approve, then restock")
    void supervisorCanApproveARefund() throws Exception {
        Long saleId = sellTen("CARD");

        assertStatus(call("/" + saleId + "/refund-request", cashierToken,
                "{\"reason\":\"Card machine declined twice\"}"), 200);
        assertEquals(30, quantityOnHand(), "asking for a refund does not restock");

        assertStatus(call("/" + saleId + "/refund-approve", supervisorToken,
                "{\"note\":\"Refund issued\"}"), 200);

        assertEquals(40, quantityOnHand());
        SaleOrder order = reload(saleId);
        assertEquals(StockMovement.Status.APPROVED, order.getRefundStatus());
    }

    @Test
    @DisplayName("a rejected refund leaves the sale and the stock intact")
    void rejectedRefundChangesNothing() throws Exception {
        Long saleId = sellTen("CARD");

        assertStatus(call("/" + saleId + "/refund-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);
        assertStatus(call("/" + saleId + "/refund-reject", supervisorToken, "{\"note\":\"Outside window\"}"), 200);

        assertEquals(30, quantityOnHand());
        assertEquals(StockMovement.Status.REJECTED, reload(saleId).getRefundStatus());
    }

    // ── the separation that matters ─────────────────────────────────────────

    @Test
    @DisplayName("a Cashier cannot approve the void they just requested")
    void requestIsNotSelfApproving() throws Exception {
        Long saleId = sellTen("CASH");

        assertStatus(call("/" + saleId + "/void-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);
        assertStatus(call("/" + saleId + "/void-approve", cashierToken, "{\"note\":\"Self approving\"}"), 403);

        assertEquals(30, quantityOnHand(), "the refused approval must not restock");
        assertEquals(StockMovement.Status.PENDING, reload(saleId).getVoidStatus());
    }

    @Test
    @DisplayName("a Cashier cannot approve a refund either")
    void refundIsNotSelfApproving() throws Exception {
        Long saleId = sellTen("CASH");

        assertStatus(call("/" + saleId + "/refund-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);
        assertStatus(call("/" + saleId + "/refund-approve", cashierToken, "{}"), 403);

        assertEquals(30, quantityOnHand());
    }

    // ── exactly once ────────────────────────────────────────────────────────

    @Test
    @DisplayName("approving twice is refused rather than restocking twice")
    void doubleApprovalIsRefused() throws Exception {
        Long saleId = sellTen("CASH");

        assertStatus(call("/" + saleId + "/void-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);
        assertStatus(call("/" + saleId + "/void-approve", supervisorToken, "{\"note\":\"First\"}"), 200);
        assertEquals(40, quantityOnHand());

        // A duplicate click would otherwise hand back 20 units that were only
        // ever sold once.
        assertStatus(call("/" + saleId + "/void-approve", supervisorToken, "{\"note\":\"Clicked twice\"}"), 400);
        assertEquals(40, quantityOnHand(), "stock must not be credited a second time");
    }

    @Test
    @DisplayName("approving a void that was never requested is refused")
    void approvingWithoutARequestIsRefused() throws Exception {
        Long saleId = sellTen("CASH");

        assertStatus(call("/" + saleId + "/void-approve", supervisorToken, "{}"), 400);
        assertEquals(30, quantityOnHand());
    }

    // ── the queue ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("the approval queue lists pending requests and a Cashier cannot open it")
    void approvalQueueIsRestricted() throws Exception {
        Long saleId = sellTen("CASH");
        assertStatus(call("/" + saleId + "/void-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);

        String queue = mockMvc.perform(get("/api/v1/sales/approvals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(queue.contains("\"id\":" + saleId),
                "the approver must be able to find the request they are meant to review");

        mockMvc.perform(get("/api/v1/sales/approvals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a settled sale leaves the queue")
    void settledSaleLeavesTheQueue() throws Exception {
        Long saleId = sellTen("CASH");
        assertStatus(call("/" + saleId + "/void-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);
        assertStatus(call("/" + saleId + "/void-approve", supervisorToken, "{\"note\":\"Done\"}"), 200);

        String queue = mockMvc.perform(get("/api/v1/sales/approvals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(!queue.contains("\"id\":" + saleId),
                "an approved request is no longer waiting and must not still be listed");
    }

    // ── the ledger ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a void leaves a stock movement, so the ledger still explains the balance")
    void voidIsRecordedAsAMovement() throws Exception {
        Long saleId = sellTen("CASH");
        assertStatus(call("/" + saleId + "/void-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);
        assertStatus(call("/" + saleId + "/void-approve", supervisorToken, "{\"note\":\"Done\"}"), 200);

        List<StockMovement> movements = stockMovementRepository.findAll().stream()
                .filter(m -> m.getProduct() != null && productId.equals(m.getProduct().getId()))
                .toList();

        // Opening 40, the sale -10, the restock +10.
        assertEquals(3, movements.size(),
                "restocking must leave a movement; otherwise the product row and the "
                        + "ledger disagree and nobody can say why the number changed");

        int approvedTotal = movements.stream()
                .mapToInt(m -> m.getQuantity() == null ? 0 : m.getQuantity())
                .sum();
        assertEquals(quantityOnHand(), approvedTotal,
                "quantity on hand must equal the sum of its movements");
    }

    @Test
    @DisplayName("the restock movement names the sale it came from")
    void restockIsAttributable() throws Exception {
        Long saleId = sellTen("CASH");
        assertStatus(call("/" + saleId + "/void-request", cashierToken, "{\"reason\":\"Changed mind\"}"), 200);
        assertStatus(call("/" + saleId + "/void-approve", supervisorToken, "{\"note\":\"Done\"}"), 200);

        String orderNumber = reload(saleId).getOrderNumber();
        assertTrue(stockMovementRepository.findAll().stream()
                        .filter(m -> m.getProduct() != null && productId.equals(m.getProduct().getId()))
                        .anyMatch(m -> orderNumber != null
                                && m.getNotes() != null
                                && m.getNotes().contains(orderNumber)),
                "the restock must reference the sale, or it is an unexplained credit");
    }
}
