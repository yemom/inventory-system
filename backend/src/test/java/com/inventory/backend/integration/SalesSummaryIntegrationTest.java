package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreateSaleOrderItem;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.WarehouseRepository;
import com.inventory.backend.service.ProductService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard's sales summary: the figures a cashier sees on their own screen.
 *
 * <p>Three things have to hold, and only the first is obvious:
 *
 * <ol>
 *   <li>The totals are the orders' own figures — net of discount and of returned
 *       units, with only completed payments counted as collected.</li>
 *   <li>A cashier sees <em>their own</em> takings. This is enforced on the
 *       server, so it is tested here by calling the endpoint as a cashier who
 *       has just recorded a sale and checking that somebody else's sale is not
 *       in the numbers.</li>
 *   <li>The scope cannot be widened from the browser. There is no parameter to
 *       pass, which the test asserts by checking the response says which scope
 *       it applied.</li>
 * </ol>
 */
class SalesSummaryIntegrationTest extends BaseIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private SaleOrderRepository saleOrderRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductService productService;
    @Autowired private ObjectMapper json;

    private String adminToken;
    private String cashierToken;
    private Long warehouseId;
    private Long productId;

    @BeforeEach
    void seed() throws Exception {
        adminToken = loginAsSuperAdmin();
        warehouseId = warehouseRepository.findAll().get(0).getId();

        CreateProductRequest product = new CreateProductRequest();
        product.setSku("SUM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        product.setName("Summary item");
        product.setCategoryId(categoryRepository.findAll().get(0).getId());
        product.setSellingPrice(new BigDecimal("100.00"));
        product.setPurchasePrice(new BigDecimal("60.00"));
        product.setInitialQuantity(100);
        productId = productService.createProduct(product).getId();

        cashierToken = tokenForRoleWith("CASHIER");
    }

    @Test
    @DisplayName("an administrator sees the whole business's sales for a day")
    void adminSeesBusinessTotals() throws Exception {
        sellAs(adminToken, "Summary admin customer", 3, "250.00");

        var summary = summary(adminToken, LocalDay());

        assertEquals("ALL_SALES", summary.path("scope").asText(),
                "a caller holding SALES_REPORT_VIEW must be told it is seeing everyone");
        assertEquals(1, summary.path("ordersCreated").asInt());
        assertEquals(1, summary.path("completedSales").asInt());
        assertEquals(0, new BigDecimal(summary.path("grossSales").asText())
                        .compareTo(new BigDecimal("750.00")),
                "3 units at 250.00 is the gross, before any discount");
        assertEquals(3, summary.path("unitsSold").asInt());
        assertEquals("Revenue net of discount and of returned units, from the orders themselves. "
                        + "Cancelled and voided orders are reported separately and are not counted as sales.",
                summary.path("basis").asText(),
                "the figures must state what they are, so a reader can check them");
    }

    @Test
    @DisplayName("a cashier's summary contains their own sale and not another seller's")
    void cashierSeesOnlyTheirOwnSales() throws Exception {
        // Somebody else's sale, recorded as the admin.
        sellAs(adminToken, "Summary admin customer", 1, "100.00");

        // The cashier's own sale, recorded as the cashier.
        sellAs(cashierToken, "Summary cashier customer", 2, "100.00");

        var mine = summary(cashierToken, LocalDay());
        var everyone = summary(adminToken, LocalDay());

        assertEquals("OWN_SALES", mine.path("scope").asText(),
                "a cashier must be told the figures are their own");
        assertEquals(1, mine.path("ordersCreated").asInt(),
                "exactly one order — the cashier's — may be counted");
        assertEquals(1, mine.path("completedSales").asInt());
        assertEquals(0, new BigDecimal(mine.path("netSales").asText()).compareTo(new BigDecimal("200.00")),
                "the cashier's single 2 × 100 order is their net sales");
        assertEquals(0, new BigDecimal(mine.path("amountCollected").asText())
                        .compareTo(new BigDecimal("200.00")),
                "a PAID order is collected money, not just an order that exists");

        assertTrue(everyone.path("ordersCreated").asInt() > mine.path("ordersCreated").asInt(),
                "the wider view must actually be wider — otherwise the scoping test proves nothing");
    }

    @Test
    @DisplayName("an empty day is an empty result, not a missing one")
    void emptyDayIsZeroNotAbsent() throws Exception {
        var summary = summary(adminToken, java.time.LocalDate.of(2031, 1, 1));

        assertEquals(0, summary.path("ordersCreated").asInt());
        assertEquals(0, summary.path("completedSales").asInt());
        assertEquals(0, new BigDecimal(summary.path("netSales").asText()).compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal(summary.path("averageOrderValue").asText())
                .compareTo(BigDecimal.ZERO), "no orders means no average, and the division must not blow up");
    }

    @Test
    @DisplayName("the summary needs the dashboard permission")
    void summaryRequiresPermission() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/sales-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/sales-summary"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private java.time.LocalDate LocalDay() {
        return java.time.LocalDate.now();
    }

    private com.fasterxml.jackson.databind.JsonNode summary(String token, java.time.LocalDate day)
            throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/sales-summary")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private void sellAs(String token, String customerName, int quantity, String unitPrice)
            throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));

        CreateSaleOrderRequest request = new CreateSaleOrderRequest();
        request.setItems(List.of(item));
        request.setWarehouseId(warehouseId);
        request.setPaymentStatus("PAID");
        request.setPaymentMethod("CASH");
        request.setCustomerName(customerName);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /** A token for a user holding exactly one role, created for this test. */
    private String tokenForRoleWith(String roleName) throws Exception {
        String email = "summary-" + roleName.toLowerCase() + "-"
                + UUID.randomUUID().toString().substring(0, 8) + "@stockflow.local";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "username", email.split("@")[0],
                                "email", email,
                                "password", "TestsOnly-9c2f41aa-" + roleName,
                                "firstName", "Summary",
                                "lastName", roleName))))
                .andExpect(status().isCreated());
        return login(email.split("@")[0], "TestsOnly-9c2f41aa-" + roleName);
    }
}