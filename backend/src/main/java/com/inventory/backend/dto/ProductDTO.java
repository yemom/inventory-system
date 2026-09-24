package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductDTO {
    private Long id;
    private String sku;
    private String barcode;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private BigDecimal minSellingPrice;
    private Integer minStockLevel;
    private Integer maxStockLevel;
    private Integer reorderLevel;
    private boolean batchTracked;
    private boolean expiryTracked;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
