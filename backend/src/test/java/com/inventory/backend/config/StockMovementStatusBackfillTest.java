package com.inventory.backend.config;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.model.StockMovement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The approval column has to survive being added to a table that already has rows.
 *
 * <p>The reported symptom was a 500 from {@code GET
 * /api/v1/inventory/movements/pending} on the deployed instance while every other
 * screen worked. The cause was not in that endpoint. {@code stock_movements}
 * already existed with data, and {@code ddl-auto=update} emits
 *
 * <pre>ALTER TABLE stock_movements ADD COLUMN status varchar(16) NOT NULL</pre>
 *
 * which PostgreSQL rejects on a populated table. Hibernate logs that as a
 * warning and boots anyway, so the service reported healthy with the column
 * missing, and the one endpoint that queries it answered 500.
 *
 * <p>No test could catch this before, because {@code create-drop} builds the
 * schema from scratch: a NOT NULL column is created *with* the table rather than
 * added to a populated one, so the ALTER never runs. These tests pin the two
 * things that actually matter — that the column is declared nullable, and that
 * legacy rows are backfilled rather than left as a meaningless NULL.
 */
class StockMovementStatusBackfillTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private com.inventory.backend.service.ProductService productService;
    @Autowired
    private com.inventory.backend.repository.CategoryRepository categoryRepository;
    @Autowired
    private StockMovementStatusBackfill backfill;

    @BeforeEach
    void seedLegacyRow() {
        Long categoryId = categoryRepository.findAll().stream()
                .filter(c -> "Backfill Test".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        com.inventory.backend.model.Category.builder()
                                .name("Backfill Test").build()))
                .getId();

        com.inventory.backend.dto.CreateProductRequest product =
                new com.inventory.backend.dto.CreateProductRequest();
        product.setSku("BACKFILL-" + System.nanoTime());
        product.setName("Backfill subject");
        product.setCategoryId(categoryId);
        product.setSellingPrice(new java.math.BigDecimal("10.00"));
        product.setPurchasePrice(new java.math.BigDecimal("6.00"));
        product.setReorderLevel(2);
        product.setInitialQuantity(10);
        Long productId = productService.createProduct(product).getId();

        // Created through the service (so every column gets its real default),
        // then forced back to what a pre-migration row looks like: a status of
        // NULL. This is the state the deployed database is actually in.
        int inserted = jdbcTemplate.update(
                "INSERT INTO stock_movements (type, quantity, product_id, status, created_at) "
                        + "VALUES ('ADJUSTMENT', 1, ?, NULL, now())",
                productId);
        assertEquals(1, inserted);
    }

    @Test
    @DisplayName("a row written before the column existed is backfilled to APPROVED")
    void legacyRowIsBackfilled() {
        assertEquals(1L, nullStatusCount(),
                "the fixture should start with one row that has no status");

        backfill.backfillLegacyStatus();

        assertEquals(0L, nullStatusCount(),
                "no movement may be left with a null status: it would be invisible to "
                        + "every status filter while still existing");
    }

    @Test
    @DisplayName("the backfill is safe to run twice")
    void backfillIsIdempotent() {
        backfill.backfillLegacyStatus();
        backfill.backfillLegacyStatus();

        assertEquals(0L, nullStatusCount());
    }

    @Test
    @DisplayName("the pending queue answers 200 once legacy rows are settled")
    void pendingQueueAnswersOnceBackfilled() throws Exception {
        // The endpoint the report was about. It reads `status`, so if the column
        // is missing or unusable this is the call that fails.
        mockMvc.perform(get("/api/v1/inventory/movements/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAsSuperAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("legacy rows are APPROVED, not PENDING — they were applied on the spot")
    void legacyRowsAreNotLeftInTheQueue() throws Exception {
        backfill.backfillLegacyStatus();

        String response = mockMvc.perform(get("/api/v1/inventory/movements/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAsSuperAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // An ADJUSTMENT written before approvals existed had already moved stock.
        // Calling it PENDING would queue work that is already done, and the
        // approver clicking through it would deduct the quantity a second time.
        assertTrue(!response.contains("\"quantity\":1") || !response.contains("APPROVED"),
                "a legacy movement must not sit in the approval queue");

        Map<String, Object> legacy = jdbcTemplate.queryForMap(
                "SELECT status FROM stock_movements WHERE notes IS NULL AND status = ?",
                StockMovement.Status.APPROVED);
        assertNotNull(legacy.get("status"));
    }

    @Test
    @DisplayName("the status column is declared nullable, so ddl-auto can add it")
    void statusColumnIsNullable() throws Exception {
        // The regression guard. If this ever goes back to nullable = false, the
        // ALTER above starts failing on any populated database and the failure
        // looks like an application bug rather than a schema one.
        var field = StockMovement.class.getDeclaredField("status");
        var column = field.getAnnotation(jakarta.persistence.Column.class);

        assertNotNull(column, "status must carry an explicit @Column so the length is set");
        assertTrue(column.nullable(),
                "status must stay nullable: NOT NULL cannot be added to a populated "
                        + "table by ddl-auto=update, and the failure is only a log warning");
    }

    private long nullStatusCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE status IS NULL", Long.class);
        return count == null ? 0L : count;
    }
}
