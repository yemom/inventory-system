package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreateSaleOrderItem;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.model.Payment;
import com.inventory.backend.model.ReturnOrder;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.ReturnOrderRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.WarehouseRepository;
import com.inventory.backend.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The return endpoints, tested the way production calls them.
 *
 * <p>Every other return test runs inside the transactional test harness, and that
 * harness hid two defects that only exist in production:
 *
 * <ol>
 *   <li>{@code ReturnService.approve} had no {@code @Transactional}. The stock
 *       ledger posts with {@code Propagation.MANDATORY}, so outside a
 *       transaction the approval threw and answered 500 — goods never restocked,
 *       the return never approved. Inside the test's own transaction it passed.</li>
 *   <li>The controller returned the {@code ReturnOrder} entity, so Jackson
 *       serialised it <em>after</em> the service transaction had closed and hit
 *       a detached lazy proxy: {@code could not initialize proxy [Customer#1] -
 *       no Session}. The test's open session made that impossible to see.</li>
 * </ol>
 *
 * <p>{@code NOT_SUPPORTED} suspends the test's transaction so each request opens
 * and closes its own, exactly as a request from the browser does. The rows this
 * creates are removed afterwards, since nothing will roll them back.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReturnApiContractIntegrationTest extends BaseIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private ReturnOrderRepository returnOrderRepository;
    @Autowired private SaleOrderRepository saleOrderRepository;
    @Autowired private com.inventory.backend.repository.PaymentRepository paymentRepository;
    @Autowired private com.inventory.backend.repository.StockMovementRepository stockMovementRepository;
    @Autowired private com.inventory.backend.repository.WarehouseStockRepository warehouseStockRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductService productService;
    @Autowired private ObjectMapper json;
    @Autowired private org.springframework.transaction.PlatformTransactionManager txManager;

    private String token;
    private Long productId;
    private Long warehouseId;
    private Long saleId;
    private long firstLineId;

    @BeforeEach
    void seed() throws Exception {
        token = loginAsSuperAdmin();
        warehouseId = warehouseRepository.findAll().get(0).getId();

        CreateProductRequest product = new CreateProductRequest();
        product.setSku("RETC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        product.setName("Return contract item");
        product.setCategoryId(categoryRepository.findAll().get(0).getId());
        product.setSellingPrice(new BigDecimal("100.00"));
        product.setPurchasePrice(new BigDecimal("60.00"));
        product.setInitialQuantity(50);
        productId = productService.createProduct(product).getId();

        saleId = sell(3);
    }

    @AfterEach
    void cleanUp() {
        if (saleId == null && productId == null) {
            return;
        }
        // The class runs with no ambient transaction — that is the point of the
        // test — so the deletes need one of their own. Each step is ordered by
        // the foreign keys: payments reference the order, movements and stock
        // rows reference the product.
        org.springframework.transaction.support.TransactionTemplate cleanup =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        cleanup.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.executeWithoutResult(status -> {
            if (saleId != null) {
                String orderNumber = saleOrderRepository.findById(saleId)
                        .map(s -> s.getOrderNumber()).orElse(null);
                if (orderNumber != null) {
                    List<ReturnOrder> raised = returnOrderRepository.findAll().stream()
                            .filter(r -> orderNumber.equals(r.getOriginalReference()))
                            .toList();
                    for (ReturnOrder r : raised) {
                        // The refund raised by approving a return is only linked
                        // to the sale when the order was still an outstanding
                        // receivable. This one is paid in full, so the refund is
                        // recorded with no sale order at all — and a payment left
                        // behind here reads as cash leaving the business to every
                        // later test that reads cash flows.
                        paymentRepository.findAll().stream()
                                .filter(p -> isThisTestsRefund(p, orderNumber, r.getReturnNumber()))
                                .forEach(paymentRepository::delete);
                        returnOrderRepository.deleteById(r.getId());
                    }
                }
                paymentRepository.deleteAll(
                        paymentRepository.findBySaleOrderIdOrderByCreatedAtAsc(saleId));
                saleOrderRepository.deleteById(saleId);
            }
            if (productId != null) {
                stockMovementRepository.deleteAll(stockMovementRepository.findByProductId(
                        productId, org.springframework.data.domain.Pageable.unpaged()));
                warehouseStockRepository.deleteByProductId(productId);
                productRepository.deleteById(productId);
            }
        });
    }

    /**
     * Whether a payment is the refund this test caused.
     *
     * <p>Matched on the return's number in the notes, since that is the only
     * link a refund keeps when the order it came from was already settled.
     */
    private boolean isThisTestsRefund(Payment payment, String orderNumber, String returnNumber) {
        if (payment == null || returnNumber == null) {
            return false;
        }
        boolean mentionsReturn = payment.getNotes() != null
                && payment.getNotes().contains(returnNumber);
        boolean mentionsOrder = orderNumber.equals(payment.getOrderReference());
        return mentionsReturn || mentionsOrder;
    }

    @Test
    @DisplayName("raising, listing and approving a return all answer 200 outside a test transaction")
    void returnEndpointsWorkWithoutAnAmbientTransaction() throws Exception {
        long lineId = firstLineId;

        // 1. Raise. Fails if the response cannot be serialised once the
        //    transaction that raised it has closed.
        MvcResult raised = mockMvc.perform(post("/api/v1/returns/sales/" + saleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Contract test","notes":"no ambient transaction",
                                 "lines":[{"itemId":%d,"quantity":1}]}""".formatted(lineId)))
                .andExpect(status().isOk())
                .andReturn();

        var raisedBody = json.readTree(raised.getResponse().getContentAsString());
        long returnId = raisedBody.path("data").path("id").asLong();
        assertTrue(returnId > 0, "a return must be returned with an id");

        // The DTO contract the frontend already consumes: same field names as
        // before, now mapped rather than serialised off a live proxy.
        assertNotNull(raisedBody.path("data").path("returnNumber").asText(null));
        assertEquals("PENDING", raisedBody.path("data").path("status").asText());
        assertTrue(raisedBody.path("data").path("items").isArray(),
                "the lines must be present in the response, not dropped");
        assertEquals(1, raisedBody.path("data").path("items").size());

        // 2. Approve. Needs its own transaction: the ledger posts are MANDATORY.
        MvcResult approved = mockMvc.perform(post("/api/v1/returns/" + returnId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Accepted in contract test\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var approvedBody = json.readTree(approved.getResponse().getContentAsString());
        assertEquals("APPROVED", approvedBody.path("data").path("status").asText(),
                "approval must be visible in the response it returns");

        // 3. And it persisted.
        var stored = returnOrderRepository.findById(returnId).orElseThrow();
        assertEquals("APPROVED", stored.getStatus());
        assertNotNull(stored.getApprovedAt(), "an approval must record when it happened");
    }

    private long sell(int quantity) throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("100.00"));

        CreateSaleOrderRequest request = new CreateSaleOrderRequest();
        request.setItems(List.of(item));
        request.setWarehouseId(warehouseId);
        request.setPaymentStatus("PAID");
        request.setPaymentMethod("CASH");
        request.setCustomerName("Contract test customer");

        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Read the line id from the response rather than re-reading the entity:
        // there is no session open here, so touching `order.getItems()` would
        // throw a LazyInitializationException in the test itself.
        var data = json.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        firstLineId = data.path("items").get(0).path("id").asLong();

        return data.path("id").asLong();
    }
}