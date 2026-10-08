package com.inventory.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreateSaleOrderItem;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.dto.RefDTO;
import com.inventory.backend.dto.SaleOrderDTO;
import com.inventory.backend.model.Category;
import com.inventory.backend.model.Permission;
import com.inventory.backend.model.Product;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A sale must say who rang it and where, not merely carry an id.
 *
 * <p>Two things are pinned: that the names are actually sent, and that their
 * absence is a supported state rather than an accident.
 *
 * <p>The second matters more than it looks. {@code cashier} comes from the user
 * who recorded the sale and {@code branch} from that user's free-text branch, so
 * an order can legitimately have neither — imported, seeded, created before these
 * fields existed, or rung by someone with no branch recorded. A DTO that assumes
 * otherwise produces a null dereference on the render path, which is how one odd
 * order takes out a whole page.
 */
class SaleOrderAttributionIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "AttributionTest#2026";

    @Autowired
    private SaleOrderService saleOrderService;
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
    private ObjectMapper json;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Long productId;
    private String cashierToken;

    @BeforeEach
    void seed() throws Exception {
        ensureCashier("Eyas", "Sentayew", "Main Street", "attribution.withbranch@stockflow.local");
        // Empty strings, not null: the columns are NOT NULL, so a nameless
        // user is storable but getFullName() returns " " for them.
        ensureCashier("", "", null, "attribution.noname@stockflow.local");
        ensureCashier("No", "Branch", "   ", "attribution.nobranch@stockflow.local");

        cashierToken = tokenFor("attribution.withbranch@stockflow.local");

        CreateProductRequest product = new CreateProductRequest();
        product.setSku("ATTR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        product.setName("Attribution subject");
        product.setCategoryId(categoryId());
        product.setSellingPrice(new BigDecimal("100.00"));
        product.setPurchasePrice(new BigDecimal("60.00"));
        product.setReorderLevel(5);
        product.setInitialQuantity(50);
        productId = productService.createProduct(product).getId();
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private Long categoryId() {
        return categoryRepository.findAll().stream()
                .filter(c -> "Attribution Test".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        Category.builder().name("Attribution Test").build()))
                .getId();
    }

    private void ensureCashier(String first, String last, String branch, String email) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .username(email.substring(0, email.indexOf('@')))
                .firstName(first)
                .lastName(last)
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .branch(branch)
                .role(roleFor("CASHIER"))
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

    private String tokenFor(String email) throws Exception {
        Role role = roleFor("CASHIER");
        role.setPermissions(resolve(RolePermissionCatalog.permissionsFor("CASHIER")));
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

    private SaleOrderDTO sellAs(String token, int quantity) throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
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

        return readDto(result);
    }

    /** Unwraps the ApiResponse envelope and maps the payload to the DTO. */
    private SaleOrderDTO readDto(MvcResult result) throws Exception {
        com.fasterxml.jackson.databind.JsonNode root = json.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        com.fasterxml.jackson.databind.JsonNode data =
                root.has("data") ? root.get("data") : root;
        return json.treeToValue(data, SaleOrderDTO.class);
    }

    private User userFor(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    // ── the names are sent ──────────────────────────────────────────────────

    @Test
    @DisplayName("a sale carries the cashier's name and their branch")
    void saleCarriesCashierAndBranch() throws Exception {
        SaleOrderDTO dto = sellAs(cashierToken, 5);

        assertNotNull(dto.getCashier(), "the cashier must be present, not an id");
        assertEquals("Eyas Sentayew", dto.getCashier().getName());
        assertEquals(userFor("attribution.withbranch@stockflow.local").getId(),
                dto.getCashier().getId());

        assertNotNull(dto.getBranch(), "the branch must be present");
        assertEquals("Main Street", dto.getBranch().getName());
    }

    @Test
    @DisplayName("branch has a name but no id, because branch is free text on the user")
    void branchHasNoId() throws Exception {
        SaleOrderDTO dto = sellAs(cashierToken, 5);

        // Documented shape, not an oversight: there is no branches table, so
        // there is no surrogate key to report. A client rendering `name` needs no
        // change if one is introduced later.
        assertNull(dto.getBranch().getId(),
                "branch is not an entity, so its id must be null rather than fabricated");
    }

    @Test
    @DisplayName("the createdBy username is still returned for existing callers")
    void createdByUsernameIsRetained() throws Exception {
        SaleOrderDTO dto = sellAs(cashierToken, 5);

        // The new fields are additive. Anything already reading `createdBy` must
        // keep working.
        assertNotNull(dto.getCreatedBy());
        assertEquals("attribution.withbranch", dto.getCreatedBy());
    }

    // ── absence is a supported state ────────────────────────────────────────

    @Test
    @DisplayName("a cashier with no branch recorded yields no branch, and no crash")
    void missingBranchYieldsNull() throws Exception {
        SaleOrderDTO dto = sellAs(tokenFor("attribution.nobranch@stockflow.local"), 5);

        assertNotNull(dto.getCashier());
        assertNull(dto.getBranch(),
                "a blank branch is not a branch; sending whitespace would render as blank");
    }

    @Test
    @DisplayName("a cashier with a blank name falls back to the username")
    void blankNameFallsBackToUsername() throws Exception {
        SaleOrderDTO dto = sellAs(tokenFor("attribution.noname@stockflow.local"), 5);

        assertNotNull(dto.getCashier());
        // Without the fallback this renders as blank, because User.getFullName()
        // returns " " when both names are empty.
        assertEquals("attribution.noname", dto.getCashier().getName());
    }

    @Test
    @DisplayName("an order with no recorded creator has no cashier and no branch")
    void orderWithNoCreatorHasNeither() {
        SaleOrder orphan = new SaleOrder();
        orphan.setOrderNumber("SO-ORPHAN");

        SaleOrderDTO dto = saleOrderService.toDTO(orphan);

        assertNull(dto.getCashier(), "no creator means no cashier, and that must not throw");
        assertNull(dto.getBranch());
    }

    // ── over the wire ───────────────────────────────────────────────────────

    @Test
    @DisplayName("the list endpoint returns cashier and branch as objects")
    void listEndpointReturnsObjects() throws Exception {
        sellAs(cashierToken, 5);

        MvcResult result = mockMvc.perform(get("/api/v1/sales?page=0&size=50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andReturn();

        var content = json.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("content");

        assertTrue(content.size() > 0, "the sale just made should be listed");

        var first = content.get(0);
        assertTrue(first.path("cashier").isObject(),
                "cashier must be an object, not an id or absent; was: " + first.path("cashier"));
        assertEquals("Eyas Sentayew", first.path("cashier").path("name").asText());
        assertEquals("Main Street", first.path("branch").path("name").asText());
    }

    @Test
    @DisplayName("the single-order endpoint returns the same shape")
    void detailEndpointReturnsObjects() throws Exception {
        SaleOrderDTO created = sellAs(cashierToken, 5);

        MvcResult result = mockMvc.perform(get("/api/v1/sales/" + created.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andReturn();

        var data = json.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data");

        assertEquals("Eyas Sentayew", data.path("cashier").path("name").asText());
        assertEquals("Main Street", data.path("branch").path("name").asText());
    }

    @Test
    @DisplayName("RefDTO survives a round trip with a null id")
    void refDtoRoundTrips() {
        RefDTO ref = new RefDTO(null, "Main Street");

        assertNull(ref.getId());
        assertEquals("Main Street", ref.getName());
    }

    @Test
    @DisplayName("the product still reflects the sale that was just made")
    void saleDeductedStock() throws Exception {
        sellAs(cashierToken, 5);

        Product product = productRepository.findById(productId).orElseThrow();
        assertEquals(45, product.getQuantity(),
                "the attribution change must not disturb the stock movement");
    }
}
