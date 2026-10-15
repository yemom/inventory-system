package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.*;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import com.inventory.backend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A sale is persisted as a complete business fact.
 *
 * <p>The chain this class walks is: stock leaves a named warehouse, the cost of
 * the goods is frozen on the sale line, the movement is written to the ledger,
 * and only then is the money recorded. Each of those was previously absent or
 * wrong: there was no warehouse, there was no cost snapshot, and the payment was
 * written with the invoice total whatever had actually been handed over.
 *
 * <p>It also covers the two mistakes that compound most expensively — selling
 * the same basket twice, and treating an unpaid sale as cash received.
 */
class SalesPersistenceIntegrationTest extends BaseIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private SaleOrderRepository saleOrderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private WarehouseStockRepository warehouseStockRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private FinancialCalculationService calculations;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;

    private Long productId;
    private Long warehouseId;
    private String cashierToken;

    @BeforeEach
    void seed() throws Exception {
        cashierToken = loginAsSuperAdmin();
        warehouseId = warehouseRepository.findAll().get(0).getId();

        Long categoryId = categoryRepository.findAll().stream()
                .findFirst().orElseThrow().getId();

        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SALE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        request.setName("Widget");
        request.setCategoryId(categoryId);
        request.setSellingPrice(new BigDecimal("150.00"));
        request.setPurchasePrice(new BigDecimal("90.00"));
        request.setInitialQuantity(100);
        productId = productService.createProduct(request).getId();
    }

    @Test
    @DisplayName("a sale names its warehouse, freezes the cost and writes the ledger")
    void saleIsFullyPersisted() throws Exception {
        SaleOrderDTO sale = sell(4, "150.00", null, "PAID", "CASH");

        assertNotNull(sale.getId());
        assertNotNull(sale.getOrderNumber());
        assertEquals(warehouseId, sale.getWarehouseId(), "a sale must name where the goods came from");

        SaleOrder stored = saleOrderRepository.findById(sale.getId()).orElseThrow();
        SaleOrderItem line = stored.getItems().get(0);

        // Compared by value, not by scale: the cost is stored at four decimal
        // places for costing precision while money is carried at two, and
        // 90.0000 and 90.00 are the same amount.
        assertEquals(0, new BigDecimal("90.00").compareTo(line.getUnitCost()),
                "the cost must be frozen at the moment of sale, not read from the product later");
        assertEquals(0, new BigDecimal("600.00").compareTo(stored.getFinalAmount()));
        assertEquals(0, new BigDecimal("360.00").compareTo(stored.getCostOfGoodsSold()));

        assertEquals(96, quantityOnHand(), "the goods must leave the warehouse");

        List<StockMovement> movements = stockMovementRepository
                .findApprovedHistoryForProduct(productId,
                        org.springframework.data.domain.Pageable.unpaged()).getContent();
        StockMovement saleMovement = movements.stream()
                .filter(m -> StockMovement.TYPE_SALE.equals(m.getType()))
                .findFirst().orElseThrow();
        assertEquals(-4, saleMovement.getQuantity());
        assertEquals(warehouseId, saleMovement.getWarehouse().getId());
        assertNotNull(saleMovement.getUnitCost(), "the ledger must carry the cost, not just the count");
    }

    @Test
    @DisplayName("an unpaid sale is revenue but not cash, and is reported as both")
    void unpaidSaleIsRevenueNotCash() throws Exception {
        SaleOrderDTO sale = sell(2, "150.00", null, "UNPAID", "CREDIT");
        LocalDate today = LocalDate.now();

        FinancialSummary summary = calculations.summarise(today.minusDays(1), today);

        assertEquals(0, new BigDecimal("300.00").compareTo(summary.netSales()),
                "an unpaid sale is still revenue");
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.cashReceived()),
                "an unpaid sale is not cash, and must not be reported as cash");
        assertEquals(0, new BigDecimal("300.00").compareTo(summary.accountsReceivable()),
                "the unpaid amount is a receivable");
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.netCashFlow()));
        assertNotNull(sale);
    }

    @Test
    @DisplayName("a part payment settles part of the debt and leaves the rest owed")
    void partialPaymentIsRecorded() throws Exception {
        SaleOrderDTO sale = sell(4, "150.00", null, "UNPAID", "CREDIT");

        MvcResult result = mockMvc.perform(post("/api/v1/payments/sales/" + sale.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.00,\"method\":\"CASH\"}"))
                .andExpect(status().isOk()).andReturn();

        // Asserted against the stored row rather than the response body: the
        // state that matters is what the ledger holds, and testing the JSON
        // would only prove the serialiser has a field called "status".
        assertTrue(paymentRepository.findBySaleOrderIdOrderByCreatedAtAsc(sale.getId())
                        .stream()
                        .anyMatch(p -> Payment.STATUS_COMPLETED.equals(p.getStatus())
                                && p.getAmount().compareTo(new BigDecimal("250.00")) == 0
                                && Payment.DIRECTION_IN.equals(p.getEffectiveDirection())
                                && Payment.PARTY_CUSTOMER.equals(p.getPartyType())),
                "a completed payment for 250.00 must be on the ledger");

        assertNotNull(result);

        SaleOrder stored = saleOrderRepository.findById(sale.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("250.00").compareTo(stored.getAmountPaid()));
        assertEquals("PARTIAL", stored.getPaymentStatus());

        FinancialSummary summary = calculations.summarise(
                LocalDate.now().minusDays(1), LocalDate.now());
        assertEquals(0, new BigDecimal("250.00").compareTo(summary.cashReceived()),
                "250 of cash against a 600 invoice");
        assertEquals(0, new BigDecimal("350.00").compareTo(summary.accountsReceivable()));
    }

    @Test
    @DisplayName("paying more than is owed is refused, not silently absorbed")
    void overPaymentIsRefused() throws Exception {
        SaleOrderDTO sale = sell(1, "150.00", null, "PAID", "CASH");

        mockMvc.perform(post("/api/v1/payments/sales/" + sale.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":9999.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("selling more than is in stock is refused and leaves stock alone")
    void cannotOversell() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(500, "150.00", null, "PAID", "CASH", null)))
                .andExpect(status().isBadRequest());

        assertEquals(100, quantityOnHand(),
                "a refused sale must not have moved any stock");
    }

    @Test
    @DisplayName("a receipt is reproducible from the stored sale, not from the browser")
    void receiptComesFromTheServer() throws Exception {
        SaleOrderDTO created = sell(2, "150.00", null, "PAID", "CASH");

        // Re-reading the sale from the server must produce the same figures the
        // till displayed. A receipt that only ever existed in the browser is not
        // a record of anything.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/sales/" + created.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse()
                            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    assertTrue(body.contains("\"unitCost\":90.0"),
                            "the stored cost must be readable back; was: " + body);
                    assertTrue(body.contains("\"finalAmount\":300.0"),
                            "the stored total must be readable back; was: " + body);
                });
    }

    @Test
    @DisplayName("the same basket submitted twice is recorded once")
    void duplicateSubmissionIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = saleBody(3, "150.00", null, "PAID", "CASH", key);

        mockMvc.perform(post("/api/v1/sales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/sales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated());

        assertEquals(97, quantityOnHand(),
                "the retry must not sell the basket a second time");
        assertEquals(1, saleOrderRepository.findAll().stream()
                .filter(s -> key.equals(s.getIdempotencyKey()))
                .count());
    }

    @Test
    @DisplayName("the cost of a closed period does not change when a price does")
    void historicalCostIsNotRestated() throws Exception {
        SaleOrderDTO sale = sell(5, "150.00", null, "PAID", "CASH");
        LocalDate today = LocalDate.now();

        FinancialSummary before = calculations.summarise(today, today);
        assertEquals(0, new BigDecimal("450.00").compareTo(before.costOfGoodsSold()),
                "5 units at a cost of 90.00");

        // The supplier raises their price tomorrow. A report that recomputed
        // COGS from the product's current price would retroactively restate
        // today's profit.
        Product product = productRepository.findById(productId).orElseThrow();
        product.setPurchasePrice(new BigDecimal("200.00"));
        productRepository.save(product);

        FinancialSummary after = calculations.summarise(today, today);
        assertEquals(0, before.costOfGoodsSold().compareTo(after.costOfGoodsSold()),
                "a closed period's cost must not move when a purchase price does");
        assertEquals(0, new BigDecimal("300.00").compareTo(after.grossProfit()),
                "gross profit for the day is unchanged by a later price rise");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int quantityOnHand() {
        Product product = productRepository.findById(productId).orElseThrow();
        return product.getQuantity() != null ? product.getQuantity() : 0;
    }

    private SaleOrderDTO sell(int quantity, String unitPrice, String discount,
                              String paymentStatus, String paymentMethod) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(quantity, unitPrice, discount, paymentStatus,
                                paymentMethod, null)))
                .andExpect(status().isCreated()).andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readValue(json.readTree(body).path("data").toString(), SaleOrderDTO.class);
    }

    private String saleBody(int quantity, String unitPrice, String discount,
                            String paymentStatus, String paymentMethod,
                            String idempotencyKey) throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));

        CreateSaleOrderRequest request = new CreateSaleOrderRequest();
        request.setItems(List.of(item));
        request.setWarehouseId(warehouseId);
        request.setPaymentStatus(paymentStatus);
        request.setPaymentMethod(paymentMethod);
        if (discount != null) {
            request.setDiscount(new BigDecimal(discount));
        }
        if (idempotencyKey != null) {
            request.setIdempotencyKey(idempotencyKey);
        }
        return json.writeValueAsString(request);
    }
}