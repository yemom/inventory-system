package com.inventory.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseOrderItemDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;

    /** Units ordered. */
    private Integer quantity;

    /** Units actually delivered and added to stock so far. */
    private Integer quantityReceived;

    /** Units still to arrive. Never negative. */
    private Integer quantityOutstanding;

    private BigDecimal unitCost;
    private BigDecimal subtotal;
    private BigDecimal total;   // alias for subtotal
}