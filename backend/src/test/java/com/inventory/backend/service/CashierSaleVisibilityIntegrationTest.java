package com.inventory.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreateSaleOrderItem;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.model.Category;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Role;
import com.inventory.backend.model.SaleOrder;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Cashier may read sales, but only their own.
 *
 * <p>The role matrix says a Cashier "cannot view others' sales", and before this
 * was enforced the API did not agree: {@code SALE_READ} alone let a Cashier list
 * and fetch every order in the business, including each colleague's takings and
 * customer names. Hiding a column in the UI would not have fixed it — the token
 * is valid and the endpoint answers.
 *
 * <p>So the rule lives in {@code SaleOrderService}: {@code SALE_READ} means "sales
 * you can look at", and the wider {@code SALES_REPORT_VIEW} is what turns it into
 * "every sale". Every role that legitimately needs the whole ledger already holds
 * the reporting permission, so nobody who was meant to see everything loses it.
 */
class CashierSaleVisibilityIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "VisibilityTest#2026";

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private SaleOrderRepository saleOrderRepository;
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
    private Long otherSaleId;
    private Long ownSaleId;

    private String firstCashierToken;
    private String secondCashierToken;
    private String supervisorToken;

    @BeforeEach
    void seed() throws Exception {
        Long categoryId = categoryRepository.findAll().stream()
                .filter(c -> "Visibility Test".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        Category.builder().name("Visibility Test").build()))
                .getId();

        ensureCashier("till.one", "cashier.one@visibility.local");
        ensureCashier("till.two", "cashier.two@visibility.local");
        ensureStaff("SUPERVISOR", "supervisor.visibility@stockflow.local", "supervisor_visibility");

        firstCashierToken = tokenFor("CASHIER", "cashier.one@visibility.local");
        secondCashierToken = tokenFor("CASHIER", "cashier.two@visibility.local");
        supervisorToken = tokenFor("SUPERVISOR", "supervisor.visibility@stockflow.local");

        CreateProductRequest product = new CreateProductRequest();
        product.setSku("VIS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        product.setName("Sold at the till");
        product.setCategoryId(categoryId);
        product.setSellingPrice(new BigDecimal("100.00"));
        product.setPurchasePrice(new BigDecimal("60.00"));
        product.setReorderLevel(5);
        product.setInitialQuantity(100);
        productId = productService.createProduct(product).getId();

        otherSaleId = sell(secondCashierToken);
        ownSaleId = sell(firstCashierToken);
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private void ensureCashier(String username, String email) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .username(username)
                .firstName("Till")
                .lastName("Operator")
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(roleFor("CASHIER"))
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .build());
    }

    private void ensureStaff(String roleName, String email, String username) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .username(username)
                .firstName(roleName)
                .lastName("Tester")
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

    /**
     * Points {@code roleName} at the shipped matrix, then logs in.
     *
     * <p>The role is named explicitly rather than inferred from the email: the
     * matrix is re-applied on each call, so getting this wrong would leave a
     * Cashier holding a Supervisor's permissions for the rest of the class.
     */
    private String tokenFor(String roleName, String email) throws Exception {
        Role role = roleFor(roleName);
        role.setPermissions(resolve(RolePermissionCatalog.permissionsFor(roleName)));
        roleRepository.save(role);
        return login(email, PASSWORD);
    }

    private Set<Permission> resolve(Set<String> names) {
        return names.stream()
                .map(name -> permissionRepository.findByName(name)
                        .orElseGet(() -> permissionRepository.save(
                                Permission.builder().name(name).build())))
                .collect(Collectors.toSet());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Long sell(String token) throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("100.00"));

        CreateSaleOrderRequest request = new CreateSaleOrderRequest();
        request.setItems(List.of(item));
        request.setPaymentMethod("CASH");
        request.setPaymentStatus("PAID");
        request.setIdempotencyKey(UUID.randomUUID().toString());

        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── the list ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a Cashier's sale list contains their own sales and nobody else's")
    void cashierSeesOnlyTheirOwnSales() throws Exception {
        String response = mockMvc.perform(get("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstCashierToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(response.contains("\"id\":" + ownSaleId), "their own sale must be listed");
        assertFalse(response.contains("\"id\":" + otherSaleId),
                "a colleague's sale must not appear in a Cashier's list");

        // The stronger form of the same check: count what came back.
        var content = json.readTree(response).path("data").path("content");
        assertEquals(1, content.size(), "the seeded data has one sale for this Cashier");
    }

    @Test
    @DisplayName("a Cashier's pagination total reflects their own sales, not the business's")
    void cashierTotalIsScopedToo() throws Exception {
        // A total taken from `count()` of the whole table would leak the size of
        // the ledger even when every row is filtered out of the page.
        String response = mockMvc.perform(get("/api/v1/sales?size=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstCashierToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // `totalElements`, not `total`: this is what the API actually emits, and
        // reading the wrong key would silently assert 0 and pass for the wrong
        // reason.
        var data = json.readTree(response).path("data");
        assertEquals(1, data.path("totalElements").asLong(),
                "the count must cover the Cashier's sales, not the whole ledger");
        assertEquals(1, data.path("content").size(),
                "the page itself is already scoped to them");
    }

    // ── a single sale ───────────────────────────────────────────────────────

    @Test
    @DisplayName("a Cashier cannot fetch a colleague's sale by id")
    void cashierCannotFetchAnotherCashiersSale() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sales/" + otherSaleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstCashierToken))
                .andReturn();

        // Reported as missing rather than forbidden: confirming that an order
        // number exists is itself a small disclosure to someone who may not see it.
        assertEquals(404, result.getResponse().getStatus(),
                "body was: " + body(result));
    }

    @Test
    @DisplayName("a Cashier can still fetch their own sale")
    void cashierCanFetchTheirOwnSale() throws Exception {
        mockMvc.perform(get("/api/v1/sales/" + ownSaleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstCashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ownSaleId));
    }

    // ── nobody who needs the whole ledger loses it ───────────────────────────

    @Test
    @DisplayName("a Supervisor still sees every sale")
    void supervisorSeesEverySale() throws Exception {
        String response = mockMvc.perform(get("/api/v1/sales?size=100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(response.contains("\"id\":" + ownSaleId),
                "a Supervisor holds SALES_REPORT_VIEW and must see the ledger");
        assertTrue(response.contains("\"id\":" + otherSaleId),
                "a Supervisor must see every cashier's sales, not one of them");

        mockMvc.perform(get("/api/v1/sales/" + otherSaleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk());
    }

    // ── the request endpoints must not leak either ──────────────────────────

    @Test
    @DisplayName("a Cashier cannot raise a void request against a colleague's sale")
    void voidRequestDoesNotLeakAnotherSale() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sales/" + otherSaleId + "/void-request")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstCashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"curiosity\"}"))
                .andReturn();

        // The endpoint returns the whole order DTO, so refusing it here is what
        // stops the response handing over the customer and the total.
        assertEquals(404, result.getResponse().getStatus(), "body was: " + body(result));
        assertTrue(body(result).contains("error")
                        || body(result).contains("message"),
                "the refusal should be an ordinary error envelope");
    }

    @Test
    @DisplayName("a Cashier can raise a void request against their own sale")
    void cashierCanRequestAVoidOfTheirOwnSale() throws Exception {
        mockMvc.perform(post("/api/v1/sales/" + ownSaleId + "/void-request")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstCashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer changed their mind\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the underlying rows are untouched — scoping is a read-time rule")
    void scopingDoesNotDeleteAnything() throws Exception {
        mockMvc.perform(get("/api/v1/sales/" + otherSaleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstCashierToken))
                .andExpect(status().isNotFound());

        // A Supervisor, who may see the ledger, still finds it: the sale was
        // hidden from one reader, not removed from the business.
        SaleOrder stillThere = saleOrderRepository.findById(otherSaleId).orElseThrow();
        assertEquals(otherSaleId, stillThere.getId());
        assertEquals(100 - 10, productRepository.findById(productId).orElseThrow().getQuantity(),
                "both sales still left the shelf; hiding a sale from a reader does not "
                        + "put the goods back");
    }
}
