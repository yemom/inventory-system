package com.inventory.backend.integration;

import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.model.Warehouse;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreateSaleOrderItem;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.model.Warehouse;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.WarehouseRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A sale must be *possible*, and when it is refused the refusal must say so.
 *
 * <p>Two failures met at once on a real till. The business had two active
 * warehouses and the till did not send one, so the backend correctly refused —
 * and did so by throwing {@code IllegalStateException}, which had no handler and
 * became a 500. The cashier saw "Sale failed / An unexpected error occurred"
 * while holding a customer waiting to pay. Nothing in that told them what to do,
 * and no sale could be completed at all.
 *
 * <p>So both halves are pinned here: the happy path works when the caller says
 * where the goods come from, and the unhappy path reports {@code 409} with a
 * message that names the problem. A 500 would mean the server broke; a 409 means
 * the request needs different input. Staff must be able to tell those apart.
 */
class SaleWarehouseSelectionIntegrationTest extends BaseIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private com.inventory.backend.repository.CategoryRepository categoryRepository;
    @Autowired private com.inventory.backend.service.ProductService productService;
    @Autowired private ObjectMapper json;

    private String adminToken;
    private Long productId;
    private List<Warehouse> warehouses;

    @BeforeEach
    void seed() throws Exception {
        adminToken = loginAsSuperAdmin();

        // The refusal only exists when the choice is ambiguous. With one
        // active warehouse the backend resolves it silently, which is the
        // documented single-site behaviour — so a second is created to reach
        // the branch this test is about.
        while (warehouseRepository.findByActiveTrueOrderByIdAsc().size() < 2) {
            Warehouse branch = new Warehouse();
            branch.setName("Branch Warehouse " + UUID.randomUUID()
                    .toString().substring(0, 6));
            branch.setActive(true);
            warehouseRepository.save(branch);
        }
        warehouses = warehouseRepository.findByActiveTrueOrderByIdAsc();

        // Testcontainers starts from an empty database, so the product this
        // test sells has to be created here. It carries opening stock, which is
        // also what gives warehouse_stock a balance to sell against.
        if (productRepository.findAll().isEmpty()) {
            CreateProductRequest product = new CreateProductRequest();
            product.setSku("WSEL-" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase());
            product.setName("Warehouse selection item");
            product.setCategoryId(categoryRepository.findAll().get(0).getId());
            product.setSellingPrice(new BigDecimal("10.00"));
            product.setPurchasePrice(new BigDecimal("6.00"));
            product.setInitialQuantity(50);
            // Naming the warehouse is what makes the opening stock sellable.
            // Left unnamed in a multi-warehouse business it is recorded
            // unattributed, and the product exists with stock no till can reach.
            product.setWarehouseId(warehouses.get(0).getId());
            productId = productService.createProduct(product).getId();
        } else {
            productId = productRepository.findAll().get(0).getId();
        }
    }

    @Test
    @DisplayName("a sale succeeds when it names the warehouse it is taken from")
    void saleSucceedsWithWarehouse() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(warehouses.get(0).getId())))
                .andDo(r -> System.out.println("WDEBUG req=" + r.getRequest().getContentAsString()))
                .andExpect(status().isCreated())
                .andReturn();

        var data = json.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");

        assertTrue(data.path("orderNumber").asText().startsWith("SO-"),
                "the sale must be a real recorded order, not a stub");
    }

    @Test
    @DisplayName("a sale with no warehouse is refused as 409, never as a 500")
    void missingWarehouseIsAConflictNotAServerError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(null)))
                .andExpect(status().isConflict())
                .andReturn();

        var body = json.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        // The code is what a client branches on; the message is what a person
        // reads. Both have to be actionable, and neither may carry internals.
        assertEquals("WAREHOUSE_REQUIRED", body.path("code").asText(),
                "a machine-readable code so the till can offer the picker");
        assertTrue(body.path("message").asText().toLowerCase().contains("warehouse"),
                "the message must name the missing input");
        assertTrue(!body.path("message").asText().contains("Exception"),
                "no stack trace or internal type may reach the caller");
    }

    @Test
    @DisplayName("a sale naming a warehouse that does not exist is a 400, not a 500")
    void unknownWarehouseIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(999999L)))
                .andExpect(status().isBadRequest());
    }

    private String saleBody(Long warehouseId) throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("10.00"));

        CreateSaleOrderRequest request = new CreateSaleOrderRequest();
        request.setItems(List.of(item));
        request.setWarehouseId(warehouseId);
        request.setPaymentMethod("CASH");
        request.setPaymentStatus("PAID");
        request.setCustomerName("Warehouse selection test");
        request.setIdempotencyKey("wsel-" + UUID.randomUUID());
        return json.writeValueAsString(request);
    }
}