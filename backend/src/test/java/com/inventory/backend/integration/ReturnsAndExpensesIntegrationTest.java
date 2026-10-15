package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.*;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Returns, refunds and expenses behave as financial events.
 *
 * <p>Sales and purchase returns used to live in the browser's
 * {@code localStorage}: they were lost when the browser was cleared, appeared in
 * no report, and left the ledger holding an adjustment nobody could explain. An
 * expense could be marked approved by whoever recorded it. Each of those is a
 * control that has to be enforced on the server.
 */
class ReturnsAndExpensesIntegrationTest extends BaseIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private SaleOrderRepository saleOrderRepository;
    @Autowired private ReturnOrderRepository returnOrderRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private com.inventory.backend.service.ProductService productService;
    @Autowired private FinancialCalculationService calculations;
    @Autowired private ObjectMapper json;

    private Long productId;
    private Long warehouseId;
    private Long saleId;
    private String token;

    @BeforeEach
    void seed() throws Exception {
        token = loginAsSuperAdmin();
        warehouseId = warehouseRepository.findAll().get(0).getId();

        CreateProductRequest product = new CreateProductRequest();
        product.setSku("RET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        product.setName("Returnable");
        product.setCategoryId(categoryRepository.findAll().get(0).getId());
        product.setSellingPrice(new BigDecimal("100.00"));
        product.setPurchasePrice(new BigDecimal("60.00"));
        product.setInitialQuantity(50);
        productId = productService.createProduct(product).getId();

        saleId = sell(10);
    }

    // ── Sales returns ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a return is a document that waits for approval before anything moves")
    void returnIsPendingUntilApproved() throws Exception {
        SaleOrderItem line = lineOf(saleId);
        int afterSale = quantityOnHand();   // 50 opening − 10 sold

        long returnId = raiseSalesReturn(line.getId(), 3, "Damaged in transit");

        ReturnOrder pending = returnOrderRepository.findById(returnId).orElseThrow();
        assertEquals(ReturnOrder.STATUS_PENDING, pending.getStatus());
        assertEquals(afterSale, quantityOnHand(),
                "raising a return must not put goods back on the shelf");

        // Revenue is unchanged until the return is approved.
        assertEquals(0, new BigDecimal("1000.00").compareTo(
                calculations.summarise(LocalDate.now(), LocalDate.now()).netSales()));

        approve(returnId);

        assertEquals(afterSale + 3, quantityOnHand(),
                "an approved return puts the goods back");
        ReturnOrder approved = returnOrderRepository.findById(returnId).orElseThrow();
        assertEquals(ReturnOrder.STATUS_APPROVED, approved.getStatus());
        assertNotNull(approved.getApprovedBy(), "an approved return must name its approver");
        assertEquals(0, new BigDecimal("300.00").compareTo(approved.getTotalAmount()));
    }

    @Test
    @DisplayName("an approved return reduces net sales and its cost leaves COGS")
    void approvedReturnReducesSalesAndCost() throws Exception {
        approve(raiseSalesReturn(lineOf(saleId).getId(), 4, "Customer changed their mind"));

        FinancialSummary summary = calculations.summarise(LocalDate.now(), LocalDate.now());

        // 10 units at 100.00 sold = 1000.00; 4 of them (400.00) came back.
        assertEquals(0, new BigDecimal("600.00").compareTo(summary.netSales()));
        // 10 at a cost of 60.00 = 600.00; the 4 returned cost 240.00.
        assertEquals(0, new BigDecimal("360.00").compareTo(summary.costOfGoodsSold()));
        assertEquals(0, new BigDecimal("240.00").compareTo(summary.grossProfit()),
                "the margin on what is still sold is unchanged by the return");
    }

    @Test
    @DisplayName("a refund is cash going out, and is not double counted as negative revenue")
    void refundIsAnOutflow() throws Exception {
        approve(raiseSalesReturn(lineOf(saleId).getId(), 2, "Wrong item sent"));

        FinancialSummary summary = calculations.summarise(LocalDate.now(), LocalDate.now());

        assertEquals(0, new BigDecimal("200.00").compareTo(summary.cashRefunded()),
                "the refund must appear as cash leaving the business");
        // 1000 collected on the sale; 200 of it then went back out as a refund.
        assertEquals(0, new BigDecimal("1000.00").compareTo(summary.cashReceived()),
                "the sale itself was paid in cash");
        assertEquals(0, new BigDecimal("800.00").compareTo(summary.netCashFlow()),
                "1000 received less 200 refunded");
        // Revenue was reduced by the return document alone. The refund is the
        // cash side of the same event and must not reduce it a second time.
        assertEquals(0, new BigDecimal("800.00").compareTo(summary.netSales()));
    }

    @Test
    @DisplayName("the same goods cannot be returned twice")
    void cannotOverReturn() throws Exception {
        SaleOrderItem line = lineOf(saleId);
        int afterSale = quantityOnHand();   // 50 opening − 10 sold
        approve(raiseSalesReturn(line.getId(), 8, "Damaged"));

        mockMvc.perform(post("/api/v1/returns/sales/" + saleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(line.getId(), 5, "Still damaged")))
                .andExpect(status().isBadRequest());

        assertEquals(afterSale + 8, quantityOnHand(),
                "only the 8 that were actually returned come back");
    }

    @Test
    @DisplayName("a rejected return changes nothing and releases the goods")
    void rejectedReturnChangesNothing() throws Exception {
        SaleOrderItem line = lineOf(saleId);
        long returnId = raiseSalesReturn(line.getId(), 3, "Looks fine to me");

        mockMvc.perform(post("/api/v1/returns/" + returnId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Goods are resellable\"}"))
                .andExpect(status().isOk());

        assertEquals(40, quantityOnHand(), "a rejected return must not move stock");
        assertEquals(0, new BigDecimal("1000.00").compareTo(
                calculations.summarise(LocalDate.now(), LocalDate.now()).netSales()));

        // The quantities were reserved when the return was raised; a rejection
        // must release them, or those goods could never be returned at all.
        assertEquals(0, lineOf(saleId).getQuantityReturned(),
                "a rejected return must release the quantity it had reserved");
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an expense is pending on creation and costs nothing until approved")
    void expenseNeedsApproval() throws Exception {
        long id = raiseExpense("500.00", "RENT");

        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculations.summarise(LocalDate.now(), LocalDate.now()).operatingExpenses()),
                "an unapproved claim is not an operating cost");

        approveExpense(id);

        assertEquals(0, new BigDecimal("500.00").compareTo(
                calculations.summarise(LocalDate.now(), LocalDate.now()).operatingExpenses()));
    }

    @Test
    @DisplayName("a client cannot mark its own expense approved")
    void clientCannotSelfApprove() throws Exception {
        long id = raiseExpense("900.00", "UTILITIES");

        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000.00,\"category\":\"RENT\",\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        Expense stored = expenseRepository.findById(id).orElseThrow();
        assertEquals(Expense.STATUS_PENDING, stored.getStatus(),
                "the status the client sent must not decide whether a cost is real");

        Expense forced = expenseRepository.findAll().stream()
                .filter(e -> e.getAmount().compareTo(new BigDecimal("5000.00")) == 0)
                .findFirst().orElseThrow();
        assertEquals(Expense.STATUS_PENDING, forced.getStatus(),
                "a caller that sends status:APPROVED must still get a pending expense");
    }

    @Test
    @DisplayName("an approved expense cannot be deleted, only rejected")
    void approvedExpenseSurvives() throws Exception {
        long id = raiseExpense("300.00", "TRANSPORT");
        approveExpense(id);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/expenses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertTrue(expenseRepository.findById(id).isPresent(),
                "a cost that a closed report already included must remain "
                        + "reproducible from the records");
    }

    @Test
    @DisplayName("approved expenses reduce net profit")
    void expensesReduceProfit() throws Exception {
        approveExpense(raiseExpense("250.00", "SALARIES"));

        FinancialSummary summary = calculations.summarise(LocalDate.now(), LocalDate.now());
        // 1000 of sales, 600 of goods, 250 of overhead.
        assertEquals(0, new BigDecimal("400.00").compareTo(summary.grossProfit()));
        assertEquals(0, new BigDecimal("150.00").compareTo(summary.netProfit()));
        assertEquals(0, new BigDecimal("15.0").compareTo(summary.netMarginPercent()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int quantityOnHand() {
        return productRepository.findById(productId).orElseThrow().getQuantity();
    }

    private SaleOrderItem lineOf(Long id) {
        return saleOrderRepository.findById(id).orElseThrow().getItems().get(0);
    }

    private Long sell(int quantity) throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("100.00"));

        CreateSaleOrderRequest request = new CreateSaleOrderRequest();
        request.setItems(List.of(item));
        request.setWarehouseId(warehouseId);
        request.setPaymentStatus("PAID");
        request.setPaymentMethod("CASH");

        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();

        return json.readTree(result.getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    private long raiseSalesReturn(Long lineId, int quantity, String reason) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/returns/sales/" + saleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(lineId, quantity, reason)))
                .andExpect(status().isOk()).andReturn();

        return json.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    /**
     * Built as text rather than through the serialiser.
     *
     * <p>{@code Map.of(...)} rejects nulls and, more importantly here, the
     * serialised form of a nested map is exactly the shape that trips Jackson's
     * depth guard when a lazy association is still attached. Writing the small
     * request by hand keeps the test about the behaviour rather than about
     * serialiser configuration.
     */
    private String returnBody(Long lineId, int quantity, String reason) {
        return """
                {"reason":"%s","notes":"Integration test",
                 "lines":[{"itemId":%d,"quantity":%d}]}"""
                .formatted(reason, lineId, quantity);
    }

    private void approve(long returnId) throws Exception {
        mockMvc.perform(post("/api/v1/returns/" + returnId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Accepted\"}"))
                .andExpect(status().isOk());
    }

    private long raiseExpense(String amount, String category) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "amount", new BigDecimal(amount),
                                "category", category,
                                "description", "Integration test expense"))))
                .andExpect(status().isOk()).andReturn();

        return json.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    private void approveExpense(long id) throws Exception {
        mockMvc.perform(post("/api/v1/expenses/" + id + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Checked\"}"))
                .andExpect(status().isOk());
    }
}