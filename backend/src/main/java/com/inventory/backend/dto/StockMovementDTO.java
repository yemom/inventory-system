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
    private String type;       // IN, OUT, ADJUSTMENT, TRANSFER
    private Integer quantity;  // signed: positive=IN, negative=OUT
    private String direction;  // "in" or "out" – convenience alias for frontend
    private String reference;
    private String notes;
    private String note;       // alias for notes
    private String createdBy;
    private LocalDateTime createdAt;
    private String date;       // formatted createdAt for frontend
}
