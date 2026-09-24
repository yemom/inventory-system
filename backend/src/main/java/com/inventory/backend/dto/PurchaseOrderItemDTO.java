package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PurchaseOrderItemDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
    private BigDecimal total;   // alias for subtotal
}
