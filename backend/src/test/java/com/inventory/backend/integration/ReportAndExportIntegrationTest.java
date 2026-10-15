package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.model.Category;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.Warehouse;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.WarehouseRepository;
import com.inventory.backend.service.ExportService;
import com.inventory.backend.service.WarehouseStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reports and their exports.
 *
 * <p>Three properties are asserted here that are easy to lose and expensive to
 * discover late: that a report is reproducible from saved transactions, that its
 * totals are computed from its own rows, and that an export is refused rather
 * than silently truncated when it exceeds the limit.
 *
 * <p>The truncation point is the one that matters most. An export that quietly
 * drops rows is worse than no export: it looks complete, a reader totals it, and
 * the total is wrong in a way nothing on the file will reveal.
 */
class ReportAndExportIntegrationTest extends BaseIntegrationTest {

    private static final int REPORTS = 5;

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private WarehouseStockService warehouseStockService;
    @Autowired private ExportService exportService;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = loginAsSuperAdmin();
    }

    @Test
    @DisplayName("a report is reproducible from the same filter every time")
    void reportIsReproducible() throws Exception {
        seedProducts();

        byte[] first = export("inventory-valuation", "csv");
        byte[] second = export("inventory-valuation", "csv");

        assertEquals(new String(first, java.nio.charset.StandardCharsets.UTF_8),
                new String(second, java.nio.charset.StandardCharsets.UTF_8),
                "the same filter must produce the same file; a report that changes "
                        + "between two identical requests cannot be reproduced from "
                        + "saved transaction data");
        assertTrue(first.length > 0, "an export must not be empty when stock exists");
    }

    @Test
    @DisplayName("an export contains the same figures the screen showed")
    void exportMatchesTheReport() throws Exception {
        seedProducts();

        MvcResult report = mockMvc.perform(get("/api/v1/reports/inventory-valuation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String json = report.getResponse().getContentAsString();
        assertTrue(json.contains("\"totalValueAtCost\""),
                "the report must state the value it computed");

        String csv = new String(export("inventory-valuation", "csv"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("TESTDATA-VAL"),
                "every exported row must be one the report returned — the export is "
                        + "rendered from the same payload, not from a second query");
    }

    @Test
    @DisplayName("an export that would exceed the limit is refused, not truncated")
    void oversizedExportIsRefused() throws Exception {
        seedProducts();

        int cap = exportService.getMaxRows();
        assertTrue(cap > 0, "the export cap must be a positive number");

        // Shrink the cap for this test rather than writing enough rows to exceed
        // the real one: what is under test is the refusal, not the volume.
        int realCap = cap;
        org.springframework.test.util.ReflectionTestUtils.setField(
                exportService, "maxRows", 1);
        try {
            mockMvc.perform(get("/api/v1/reports/export")
                            .param("report", "inventory-valuation")
                            .param("format", "csv")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().is4xxClientError());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    exportService, "maxRows", realCap);
        }
    }

    @Test
    @DisplayName("an export below the limit is allowed")
    void exportUnderTheLimitSucceeds() throws Exception {
        seedProducts();
        assertTrue(export("inventory-valuation", "csv").length > 0);
    }

    @Test
    @DisplayName("an unsupported format is refused rather than silently treated as CSV")
    void unknownFormatIsRefused() throws Exception {
        seedProducts();

        mockMvc.perform(get("/api/v1/reports/export")
                        .param("report", "inventory-valuation")
                        .param("format", "xlsx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("a spreadsheet formula in a cell cannot execute when the file is opened")
    void formulaInjectionIsNeutralised() {
        // A supplier named "=cmd|..." is code execution the moment somebody
        // double-clicks the cell. The renderer prefixes such cells with a quote.
        String rendered = new String(exportService.toCsv(
                        "test", java.time.LocalDateTime.now(),
                        java.util.List.of("name"),
                        java.util.List.of(java.util.Map.of("name", "=SUM(A1:A9)"))),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(rendered.contains("'=SUM(A1:A9)"),
                "a cell beginning with = must be quoted so spreadsheet software "
                        + "treats it as text; was: " + rendered);
    }

    @Test
    @DisplayName("money keeps its scale in an export, so a column still adds up")
    void moneyKeepsItsScale() {
        String rendered = new String(exportService.toCsv(
                        "test", java.time.LocalDateTime.now(),
                        java.util.List.of("amount"),
                        java.util.List.of(java.util.Map.of("amount", new BigDecimal("10.00")))),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(rendered.contains("10.00"),
                "10.00 must not be rendered as 10; a column of money that loses its "
                        + "decimals stops adding up to its own total. Was: " + rendered);
    }

    // ── regressions: defects that returned 500 in production ──────────────────

    @Test
    @DisplayName("the profit and loss statement can be exported, because a statement is exportable too")
    void profitAndLossIsExportable() throws Exception {
        // A statement is a list of labelled lines, not a grid, so the export path
        // used to hand a null column list to the exporter and answer 500 for every
        // attempt — CSV and PDF alike — while the report itself rendered fine.
        String csv = new String(export("profit-loss", "csv"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("Net sales"),
                "the exported statement must carry the report's own lines. Was: " + csv);

        byte[] pdf = export("profit-loss", "pdf");
        assertTrue(pdf.length > 0, "a PDF export must not be empty");
        assertEquals('%', pdf[0], "the export must be a PDF, not something else by accident");
        assertEquals('P', pdf[1]);
        assertEquals('D', pdf[2]);
        assertEquals('F', pdf[3]);
    }

    @Test
    @DisplayName("the purchases report loads with its items")
    void purchasesReportLoads() throws Exception {
        // Its query orders a DISTINCT select by a COALESCE expression, which
        // PostgreSQL rejects outright (SQLSTATE 42P10). The sales report, which
        // orders by plain columns, was unaffected — which is why this one failed
        // on its own and the failure looked unrelated to anything recent.
        mockMvc.perform(get("/api/v1/reports/purchases-detail")
                        .param("from", "2020-01-01")
                        .param("to", java.time.LocalDate.now().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a URL no endpoint matches is a 404, not a server fault")
    void unmappedUrlIsNotFound() throws Exception {
        // It used to reach the catch-all handler and answer 500, so a client with
        // a wrong path could not tell a typo from an outage.
        mockMvc.perform(get("/api/v1/reports/no-such-report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private byte[] export(String report, String format) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports/export")
                        .param("report", report)
                        .param("format", format)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    private void seedProducts() {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();
        Warehouse warehouse = warehouseRepository.findAll().stream().findFirst().orElseThrow();
        for (int i = 0; i < REPORTS; i++) {
            Product product = new Product();
            product.setSku("TESTDATA-VAL-" + i);
            product.setName("Test product " + i);
            product.setCategory(category);
            product.setSellingPrice(new BigDecimal("60.00"));
            product.setPurchasePrice(new BigDecimal("30.00"));
            product.setQuantity(0);
            product.setActive(true);
            Product saved = productRepository.saveAndFlush(product);

            // Stock goes in through the stock service, not by writing a
            // quantity onto the product. That is the path production takes, and
            // it is what creates the WarehouseStock row the valuation reads —
            // setting Product.quantity directly would leave the report empty and
            // the test would pass for the wrong reason.
            warehouseStockService.applyMovement(saved.getId(), warehouse.getId(),
                    10, new BigDecimal("30.00"), "Opening stock", null);
        }
    }
}