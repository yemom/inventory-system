package com.inventory.backend.service;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.model.Category;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.StockMovement;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Opening stock must become real quantity on hand.
 *
 * <p>The reported symptom was a product created with 50 units showing 0 and
 * reading "out of stock". The backend already stored the number, but nothing in
 * the creation path wrote it to the product — and the UI had no field for it, so
 * every product was created with 0. Both halves are covered here: this asserts
 * the backend, and {@code frontend/src/__tests__} covers that the form sends it.
 *
 * <p>The quantity also has to appear in stock history. A balance that exists
 * only as a number on the product row cannot be reconciled against movements,
 * which is precisely what a stock count is for.
 */
class OpeningStockIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    private Long categoryId;

    @BeforeEach
    void ensureCategory() {
        categoryId = categoryRepository.findAll().stream()
                .filter(c -> "Opening Stock Test".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        Category.builder().name("Opening Stock Test").build()))
                .getId();
    }

    private CreateProductRequest request(String sku, Integer openingQuantity, Integer reorderLevel) {
        CreateProductRequest req = new CreateProductRequest();
        req.setSku(sku);
        req.setName("Opening stock product " + sku);
        req.setCategoryId(categoryId);
        req.setSellingPrice(new BigDecimal("100.00"));
        req.setPurchasePrice(new BigDecimal("60.00"));
        req.setReorderLevel(reorderLevel);
        req.setInitialQuantity(openingQuantity);
        return req;
    }

    private String uniqueSku() {
        return "OPEN-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    @DisplayName("an opening quantity of 50 becomes the quantity on hand")
    void openingQuantityBecomesQuantityOnHand() {
        String sku = uniqueSku();
        var dto = productService.createProduct(request(sku, 50, 10));

        assertEquals(50, dto.getQuantity(),
                "50 units entered as opening stock must be 50 on hand, not 0");

        Product reloaded = productRepository.findById(dto.getId()).orElseThrow();
        assertEquals(50, reloaded.getQuantity());
    }

    @Test
    @DisplayName("a product opened at 50 is not out of stock, and is not low stock above its reorder level")
    void stockStatusReflectsOpeningQuantity() {
        String sku = uniqueSku();
        productService.createProduct(request(sku, 50, 10));

        // The same thresholds the UI and the reports use.
        Product product = productRepository.findAll().stream()
                .filter(p -> sku.equals(p.getSku())).findFirst().orElseThrow();
        int quantity = product.getQuantity() != null ? product.getQuantity() : 0;
        Integer reorder = product.getReorderLevel();

        assertTrue(quantity > 0, "must not be reported as out of stock");
        assertTrue(reorder == null || quantity > reorder, "must not be reported as low stock");
    }

    @Test
    @DisplayName("a product opened below its reorder level IS low stock")
    void openingBelowReorderLevelIsLowStock() {
        String sku = uniqueSku();
        productService.createProduct(request(sku, 3, 10));

        Product product = productRepository.findAll().stream()
                .filter(p -> sku.equals(p.getSku())).findFirst().orElseThrow();

        assertTrue(product.getQuantity() <= product.getReorderLevel(),
                "3 units against a reorder level of 10 must read as low stock");
    }

    @Test
    @DisplayName("opening stock is recorded as a movement, so the ledger balances")
    void openingStockIsRecordedAsAMovement() {
        String sku = uniqueSku();
        var dto = productService.createProduct(request(sku, 50, 10));

        List<StockMovement> movements = stockMovementRepository.findAll().stream()
                .filter(m -> m.getProduct() != null && dto.getId().equals(m.getProduct().getId()))
                .toList();

        assertEquals(1, movements.size(), "opening stock must leave exactly one movement behind");
        StockMovement movement = movements.get(0);
        assertEquals(StockMovement.TYPE_OPENING, movement.getType());
        assertEquals(50, movement.getQuantity());
        assertEquals(StockMovement.Status.APPROVED, movement.getStatus(),
                "an opening balance is not a claim awaiting sign-off");
        assertEquals(sku, movement.getReference());
        assertNotNull(movement.getCreatedAt());
    }

    @Test
    @DisplayName("no opening quantity means zero stock and no movement")
    void zeroOpeningStockRecordsNothing() {
        String sku = uniqueSku();
        var dto = productService.createProduct(request(sku, 0, 10));

        assertEquals(0, dto.getQuantity());
        long movementCount = stockMovementRepository.findAll().stream()
                .filter(m -> m.getProduct() != null && dto.getId().equals(m.getProduct().getId()))
                .count();
        assertEquals(0L, movementCount, "there is nothing to record for a zero opening balance");
    }

    @Test
    @DisplayName("a null opening quantity is treated as zero rather than failing")
    void nullOpeningQuantityIsZero() {
        var dto = productService.createProduct(request(uniqueSku(), null, 10));
        assertEquals(0, dto.getQuantity());
    }

    @Test
    @DisplayName("a negative opening quantity is rejected rather than silently clamped")
    void negativeOpeningQuantityIsRejected() {
        String sku = uniqueSku();
        assertThrows(IllegalArgumentException.class,
                () -> productService.createProduct(request(sku, -5, 10)),
                "a request for negative stock is a mistake and must be visible, not hidden as 0");

        assertTrue(productRepository.findAll().stream().noneMatch(p -> sku.equals(p.getSku())),
                "a rejected product must not be persisted");
    }

    @Test
    @DisplayName("opening stock is created transactionally with the product")
    void productAndMovementAreConsistent() {
        String sku = uniqueSku();
        var dto = productService.createProduct(request(sku, 12, 5));

        int movementTotal = stockMovementRepository.findAll().stream()
                .filter(m -> m.getProduct() != null && dto.getId().equals(m.getProduct().getId()))
                .mapToInt(m -> m.getQuantity() == null ? 0 : m.getQuantity())
                .sum();

        assertEquals(dto.getQuantity(), movementTotal,
                "quantity on hand must equal the sum of its movements");
    }

    // ── Through the API, with a real token ──────────────────────────────────

    @Test
    @DisplayName("POST /products with initialQuantity 50 returns quantity 50")
    void apiReturnsOpeningStockAsQuantity() throws Exception {
        String sku = uniqueSku();
        String body = """
                {
                  "sku": "%s",
                  "name": "API opening stock",
                  "categoryId": %d,
                  "sellingPrice": 120.00,
                  "purchasePrice": 70.00,
                  "reorderLevel": 10,
                  "initialQuantity": 50
                }
                """.formatted(sku, categoryId);

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAsSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(50))
                .andExpect(jsonPath("$.data.sku").value(sku))
                .andReturn();

        assertEquals(50, productRepository.findAll().stream()
                .filter(p -> sku.equals(p.getSku()))
                .findFirst().orElseThrow().getQuantity());
    }

    @Test
    @DisplayName("the product list reports the opening quantity, not zero")
    void listReportsOpeningQuantity() throws Exception {
        String sku = uniqueSku();
        productService.createProduct(request(sku, 42, 10));

        String response = mockMvc.perform(get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAsSuperAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(response.contains(sku));
        // The DTO for this SKU must carry 42 so the UI badge is not "out of stock".
        var page = objectMapper.readTree(response);
        boolean found = false;
        var content = page.path("data").path("content");
        for (int i = 0; i < content.size(); i++) {
            if (sku.equals(content.get(i).path("sku").asText())) {
                assertEquals(42, content.get(i).path("quantity").asInt(),
                        "the list must report the opening quantity");
                found = true;
            }
        }
        assertTrue(found, "created product should appear in the list: " + sku);
    }

    @Test
    @DisplayName("a negative opening quantity is a 400, not a 500")
    void negativeOpeningQuantityIsBadRequest() throws Exception {
        String body = """
                {
                  "sku": "%s",
                  "name": "Negative opening stock",
                  "categoryId": %d,
                  "sellingPrice": 120.00,
                  "initialQuantity": -5
                }
                """.formatted(uniqueSku(), categoryId);

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAsSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}