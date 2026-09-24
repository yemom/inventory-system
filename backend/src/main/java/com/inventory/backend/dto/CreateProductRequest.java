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
    private boolean batchTracked;
    private boolean expiryTracked;
}
