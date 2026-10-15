package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
public class CreatePurchaseOrderItem {
    @NotNull(message = "productId is required on every purchase line")
    private Long productId;

    @NotNull(message = "quantity is required on every purchase line")
    @jakarta.validation.constraints.Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    /**
     * What the business pays for one unit.
     *
     * <p>Strictly positive, and the reason is the moving average: receiving
     * stock blends this cost into the warehouse's weighted average
     * ({@code WarehouseStockService.weightedAverage}). A negative cost therefore
     * does not merely mis-state this order — it drags the average below zero,
     * and every future valuation for that warehouse inherits the error.
     */
    @NotNull(message = "unitCost is required on every purchase line")
    @Positive(message = "unitCost must be greater than zero")
    private BigDecimal unitCost;
}