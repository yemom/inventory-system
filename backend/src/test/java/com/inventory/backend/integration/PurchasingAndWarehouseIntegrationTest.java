package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreatePurchaseOrderItem;
import com.inventory.backend.dto.CreatePurchaseOrderRequest;
import com.inventory.backend.dto.CreateStockMovementRequest;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Purchases, warehouse transfers and per-warehouse stock.
 *
 * <p>These three used to share one number — {@code products.quantity} — which
 * meant a transfer balanced the books without moving anything anywhere, and a
 * purchase could inflate stock by being raised. Both are checked here against
 * the warehouse the goods are actually in.
 */
class PurchasingAndWarehouseIntegrationTest extends BaseIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private WarehouseStockRepository warehouseStockRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private FinancialCalculationService calculations;
    @Autowired private ObjectMapper json;

    private Long productId;
    private Long mainWarehouseId;
    private Long branchWarehouseId;
    private String token;

    @BeforeEach
    void seed() throws Exception {
        token = loginAsSuperAdmin();

        Warehouse main = warehouseRepository.findById(
                        warehouseRepository.findAll().get(0).getId()).orElseThrow();
        mainWarehouseId = main.getId();

        Warehouse branch = new Warehouse();
        branch.setName("Branch " + UUID.randomUUID().toString().substring(0, 6));
        branch.setLocation("Out of town");
        branch.setActive(true);
        branchWarehouseId = warehouseRepository.save(branch).getId();

        CreateProductRequest request = new CreateProductRequest();
        request.setSku("PUR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        request.setName("Bulk item");
        request.setCategoryId(categoryRepository.findAll().get(0).getId());
        request.setSellingPrice(new BigDecimal("200.00"));
        request.setPurchasePrice(new BigDecimal("100.00"));
        request.setInitialQuantity(0);
        productId = productService.createProduct(request).getId();
    }

    // ── Purchases ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("raising an order adds no stock; receiving it does")
    void stockArrivesOnlyOnReceipt() throws Exception {
        long orderId = raiseOrder(50, "100.00", mainWarehouseId);

        assertEquals(0, totalStock(),
                "raising an order is proposing one; no goods have arrived yet");
        assertEquals(PurchaseOrder.STATUS_ORDERED,
                purchaseOrderRepository.findById(orderId).orElseThrow().getStatus());

        receive(orderId);

        assertEquals(50, totalStock(), "receiving the goods must add them");
        assertEquals(PurchaseOrder.STATUS_RECEIVED,
                purchaseOrderRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("receiving the same order twice is refused, not silently repeated")
    void doubleReceivingIsRefused() throws Exception {
        long orderId = raiseOrder(20, "100.00", mainWarehouseId);
        receive(orderId);

        mockMvc.perform(post("/api/v1/purchases/" + orderId + "/receive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertEquals(20, totalStock(),
                "a refused second receipt must not have added the goods again");
    }

    @Test
    @DisplayName("a part delivery leaves the remainder outstanding")
    void partialReceiptLeavesTheRest() throws Exception {
        long orderId = raiseOrder(60, "100.00", mainWarehouseId);
        PurchaseOrder order = purchaseOrderRepository.findById(orderId).orElseThrow();
        long lineId = order.getItems().get(0).getId();

        mockMvc.perform(post("/api/v1/purchases/" + orderId + "/receive-partial")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + lineId + "\":25}"))
                .andExpect(status().isOk());

        assertEquals(25, totalStock());
        PurchaseOrder received = purchaseOrderRepository.findById(orderId).orElseThrow();
        assertEquals(PurchaseOrder.STATUS_PARTIALLY_RECEIVED, received.getStatus());
        assertEquals(35, received.getItems().get(0).getQuantityOutstanding());
    }

    @Test
    @DisplayName("receiving more than was ordered is refused")
    void cannotReceiveMoreThanOrdered() throws Exception {
        long orderId = raiseOrder(10, "100.00", mainWarehouseId);
        PurchaseOrder order = purchaseOrderRepository.findById(orderId).orElseThrow();
        long lineId = order.getItems().get(0).getId();

        mockMvc.perform(post("/api/v1/purchases/" + orderId + "/receive-partial")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + lineId + "\":999}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, totalStock());
    }

    @Test
    @DisplayName("goods received but unpaid are a payable, not yet a cost paid")
    void receivedButUnpaidIsAPayable() throws Exception {
        long orderId = raiseOrder(30, "100.00", mainWarehouseId);
        receive(orderId);

        PurchaseOrder order = purchaseOrderRepository.findById(orderId).orElseThrow();
        assertEquals(0, new BigDecimal("3000.00").compareTo(order.getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getAmountPaid()),
                "nothing has been paid, whatever status the order was created with");

        FinancialSummary summary = calculations.summarise(
                LocalDate.now().minusDays(1), LocalDate.now());
        assertEquals(0, new BigDecimal("3000.00").compareTo(summary.accountsPayable()),
                "goods received and not paid are owed to the supplier");
        assertEquals(0, new BigDecimal("3000.00").compareTo(summary.purchaseCostReceived()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.cashPaidToSuppliers()),
                "an unpaid purchase is not a cash movement");
    }

    // ── Transfers ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a transfer moves stock between warehouses without changing the total")
    void transferMovesStockBetweenWarehouses() throws Exception {
        receive(raiseOrder(40, "100.00", mainWarehouseId));

        assertEquals(40, stockAt(mainWarehouseId));
        assertEquals(0, stockAt(branchWarehouseId));

        transfer(15);

        assertEquals(25, stockAt(mainWarehouseId), "the source must be deducted");
        assertEquals(15, stockAt(branchWarehouseId), "the destination must be credited");
        assertEquals(40, totalStock(),
                "a transfer is not a purchase or a sale; the total must not change");
    }

    @Test
    @DisplayName("a transfer is neither revenue nor cost")
    void transferIsNotASaleOrPurchase() throws Exception {
        receive(raiseOrder(40, "100.00", mainWarehouseId));
        transfer(20);

        FinancialSummary summary = calculations.summarise(
                LocalDate.now().minusDays(1), LocalDate.now());

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.netSales()),
                "moving goods you already own is not a sale");
        // The purchase above was real and is counted. What must not change is that
        // the transfer added nothing to it: it is the same figure before and
        // after the move.
        assertEquals(0, new BigDecimal("4000.00").compareTo(summary.purchaseCostReceived()),
                "the transfer must not add to the cost of goods received");
        assertEquals(0, new BigDecimal("4000.00").compareTo(summary.inventoryValue()),
                "the stock still belongs to the business and is still worth what it cost");
    }

    @Test
    @DisplayName("a transfer writes both legs so the ledger explains itself")
    void transferWritesBothLegs() throws Exception {
        receive(raiseOrder(40, "100.00", mainWarehouseId));
        transfer(10);

        List<StockMovement> movements = stockMovementRepository
                .findApprovedHistoryForProduct(productId,
                        org.springframework.data.domain.Pageable.unpaged()).getContent();

        StockMovement out = movements.stream()
                .filter(m -> StockMovement.TYPE_TRANSFER_OUT.equals(m.getType()))
                .findFirst().orElseThrow();
        StockMovement in = movements.stream()
                .filter(m -> StockMovement.TYPE_TRANSFER_IN.equals(m.getType()))
                .findFirst().orElseThrow();

        assertEquals(-10, out.getQuantity());
        assertEquals(10, in.getQuantity());
        assertEquals(mainWarehouseId, out.getWarehouse().getId());
        assertEquals(branchWarehouseId, out.getDestinationWarehouse().getId(),
                "the outgoing leg names its destination, so it reads on its own");
        assertEquals(branchWarehouseId, in.getWarehouse().getId(),
                "the incoming leg's own warehouse is the destination");
        assertEquals(out.getMovementGroup(), in.getMovementGroup(),
                "both legs belong to the same transfer and must be identifiable as one");
    }

    @Test
    @DisplayName("stock cannot leave a warehouse it does not hold")
    void cannotTransferMoreThanTheSourceHolds() throws Exception {
        receive(raiseOrder(10, "100.00", mainWarehouseId));

        mockMvc.perform(post("/api/v1/inventory/movements/transfer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(transferBody(50))))
                .andExpect(status().isBadRequest());

        assertEquals(10, stockAt(mainWarehouseId),
                "a refused transfer must leave the source alone");
        assertEquals(0, stockAt(branchWarehouseId),
                "and must not have credited the destination");
    }

    @Test
    @DisplayName("a warehouse cannot transfer to itself")
    void cannotTransferToItself() throws Exception {
        receive(raiseOrder(10, "100.00", mainWarehouseId));

        CreateStockMovementRequest body = transferBody(5);
        body.setDestinationWarehouseId(mainWarehouseId);

        mockMvc.perform(post("/api/v1/inventory/movements/transfer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the product total equals the sum of the warehouses")
    void productTotalEqualsWarehouseSum() throws Exception {
        receive(raiseOrder(40, "100.00", mainWarehouseId));
        transfer(12);
        receive(raiseOrder(25, "120.00", branchWarehouseId));

        int productTotal = productRepository.findById(productId).orElseThrow().getQuantity();
        assertEquals(stockAt(mainWarehouseId) + stockAt(branchWarehouseId), productTotal,
                "the business-wide quantity is the sum of the warehouses, and is "
                        + "republished on every movement so the two cannot drift");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private long raiseOrder(int quantity, String unitCost, Long warehouseId) throws Exception {
        CreatePurchaseOrderItem item = new CreatePurchaseOrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitCost(new BigDecimal(unitCost));

        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest();
        request.setSupplierName("Test Suppliers Ltd");
        request.setWarehouseId(warehouseId);
        request.setPaymentStatus("UNPAID");
        request.setPaymentTerms("NET 30");
        request.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();

        return json.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    private void receive(long orderId) throws Exception {
        mockMvc.perform(post("/api/v1/purchases/" + orderId + "/receive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void transfer(int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/movements/transfer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(transferBody(quantity))))
                .andExpect(status().isOk());
    }

    private CreateStockMovementRequest transferBody(int quantity) {
        CreateStockMovementRequest request = new CreateStockMovementRequest();
        request.setProductId(productId);
        request.setWarehouseId(mainWarehouseId);
        request.setDestinationWarehouseId(branchWarehouseId);
        request.setType(StockMovement.TYPE_TRANSFER);
        request.setQuantity(quantity);
        request.setReference("TRF-" + UUID.randomUUID().toString().substring(0, 8));
        request.setReason("Rebalancing stock");
        return request;
    }

    private int stockAt(Long warehouseId) {
        return warehouseStockRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .map(WarehouseStock::getQuantity).orElse(0);
    }

    private int totalStock() {
        return productRepository.findById(productId).orElseThrow().getQuantity();
    }
}