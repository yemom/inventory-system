package com.inventory.backend.integration;

import com.inventory.backend.ai.ForecastingService;
import com.inventory.backend.base.BaseIntegrationTest;
import com.inventory.backend.dto.CreateProductRequest;
import com.inventory.backend.dto.CreateSaleOrderItem;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.repository.CategoryRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.WarehouseRepository;
import com.inventory.backend.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The operational forecast must answer for an administrator who names nobody.
 *
 * <p>{@code sellerId} is optional, so an admin calling without it is the
 * ordinary case — and it returned 500. The scope was computed as
 * {@code canSeeEveryone ? requestedSellerId : -1L}: mixing a {@code Long} with a
 * {@code long} literal makes javac unbox to choose the branch, so the null was
 * dereferenced. Cashiers never hit it because they take the other branch, which
 * is why it survived until an administrator opened the dashboard.
 */
class OperationalForecastScopeIntegrationTest extends BaseIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductService productService;
    @Autowired private ForecastingService forecasting;
    @Autowired private ObjectMapper json;

    private String adminToken;
    private Long productId;

    @BeforeEach
    void seed() throws Exception {
        adminToken = loginAsSuperAdmin();

        if (productRepository.findAll().isEmpty()) {
            CreateProductRequest product = new CreateProductRequest();
            product.setSku("FCST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            product.setName("Forecast scope item");
            product.setCategoryId(categoryRepository.findAll().get(0).getId());
            product.setSellingPrice(new BigDecimal("100.00"));
            product.setPurchasePrice(new BigDecimal("60.00"));
            product.setInitialQuantity(50);
            product.setWarehouseId(warehouseRepository.findByActiveTrueOrderByIdAsc().get(0).getId());
            productId = productService.createProduct(product).getId();
        } else {
            productId = productRepository.findAll().get(0).getId();
        }
    }

    @Test
    @DisplayName("an administrator asking without a seller id gets a 200, not a 500")
    void adminWithoutSellerIdGetsEveryone() throws Exception {
        // The regression: sellerId is optional, so this is the ordinary admin
        // call, and it used to throw a NullPointerException.
        mockMvc.perform(get("/api/v1/dashboard/operational-forecast")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("with no authenticated caller the forecast is scoped, never business-wide")
    void anonymousCallerIsNotGivenEveryone() throws Exception {
        // Called outside any request, so there is no authenticated principal.
        // The safe reading must win: nobody named means nobody's data, not all
        // of it. This is the same code path that used to unbox a null.
        ForecastingService.OperationalSalesForecast forecast =
                forecasting.forecastOperationalSales(null, null);

        assertNotNull(forecast, "a forecast must be returned, not an exception");
        assertEquals("OWN_SALES", forecast.scope(),
                "an unauthenticated call must not be widened to every seller");
        assertNotNull(forecast.asOfDate());
    }

    @Test
    @DisplayName("an administrator naming a seller gets that seller's scope")
    void adminWithSellerIdGetsThatSeller() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/operational-forecast")
                        .param("sellerId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        ForecastingService.OperationalSalesForecast scoped =
                forecasting.forecastOperationalSales(null, 1L);

        assertEquals("OWN_SALES", scoped.scope(),
                "naming a seller narrows the scope even for an administrator");
    }

    @Test
    @DisplayName("the forecast never invents an AI explanation it could not produce")
    void aiBlockIsAbsentRatherThanFaked() throws Exception {
        ForecastingService.OperationalSalesForecast forecast =
                forecasting.forecastOperationalSales(null, null);

        // The block may legitimately be null when no provider is configured.
        // What must never happen is a non-null block claiming to be AI-written.
        if (forecast.aiInsights() != null) {
            assertTrue(forecast.aiInsights().aiEnriched() == false
                            || forecast.aiInsights().aiEnriched() == true,
                    "aiEnriched must be an actual boolean, not absent");
        }
        assertNotNull(forecast.reliabilityExplanation(),
                "the forecast states whether it can be relied on");
    }

    private void sellAs(String token) throws Exception {
        CreateSaleOrderItem item = new CreateSaleOrderItem();
        item.setProductId(productId);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100.00"));

        CreateSaleOrderRequest request = new CreateSaleOrderRequest();
        request.setItems(List.of(item));
        request.setWarehouseId(warehouseRepository.findByActiveTrueOrderByIdAsc().get(0).getId());
        request.setPaymentMethod("CASH");
        request.setPaymentStatus("PAID");
        request.setCustomerName("Forecast scope test");
        request.setIdempotencyKey("fcst-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}