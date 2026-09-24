package com.inventory.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockMovementDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Long warehouseId;
    private String warehouseName;
    private String type;
    private Integer quantity;
    private String reference;
    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
}
