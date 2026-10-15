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

    /**
     * Unit cost of the goods when they were sold — the weighted-average cost
     * snapshotted at the moment of sale.
     *
     * <p>Present so the receipt and the profit report can show the margin this
     * sale made. Reading the product's current purchase price instead would
     * restate the margin of every historical sale whenever a price changes.
     */
    private BigDecimal unitCost;
}
