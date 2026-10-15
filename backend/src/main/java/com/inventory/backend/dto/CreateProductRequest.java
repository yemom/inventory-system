package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateProductRequest {
    @NotBlank(message = "SKU is required")
    private String sku;
    private String barcode;
    @NotBlank(message = "Name is required")
    private String name;
    private String unit;
    private String description;
    @NotNull(message = "Category ID is required")
    private Long categoryId;
    private BigDecimal purchasePrice;
    @NotNull(message = "Selling price is required")
    private BigDecimal sellingPrice;
    private BigDecimal minSellingPrice;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Integer reorderLevel;
    private Integer initialQuantity; // optional opening stock

    /**
     * Where the opening stock sits, when the business has more than one
     * warehouse.
     *
     * <p>Optional, and omitting it is still valid — but in a multi-warehouse
     * business an unnamed opening balance cannot be sold from any location, so
     * the product is created with stock that no till can draw on. Naming the
     * warehouse here is what makes a new product immediately sellable.
     */
    private Long warehouseId;

    private boolean batchTracked;
    private boolean expiryTracked;
}
