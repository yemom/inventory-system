package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseOrderDTO {
    private Long id;
    private String orderNumber;
    private String supplierName;
    private BigDecimal totalAmount;
    private String status;
    private List<PurchaseOrderItemDTO> items;
    private LocalDateTime createdAt;
}

@Data
class PurchaseOrderItemDTO {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
}
