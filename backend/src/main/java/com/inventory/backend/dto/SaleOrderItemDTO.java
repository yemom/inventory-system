package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SaleOrderItemDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private BigDecimal subtotal;
    // "total" alias for subtotal
    private BigDecimal total;
}
