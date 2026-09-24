package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateProductRequest {
    private String sku;
    private String barcode;
    private String name;
    private String unit;
    private String description;
    private Long categoryId;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private BigDecimal minSellingPrice;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Integer reorderLevel;
    private Boolean batchTracked;
    private Boolean expiryTracked;
    private Boolean active;
}
