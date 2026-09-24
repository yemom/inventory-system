package com.inventory.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStockMovementRequest {
    @NotNull(message = "Product ID is required")
    private Long productId;
    private Long warehouseId;
    @NotNull(message = "Type is required")
    private String type; // IN, OUT, TRANSFER, ADJUSTMENT
    @NotNull(message = "Quantity is required")
    private Integer quantity;
    private String reference;
    private String notes;
}
