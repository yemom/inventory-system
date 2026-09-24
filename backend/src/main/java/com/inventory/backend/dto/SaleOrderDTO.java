package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleOrderDTO {
    private Long id;
    private String orderNumber;
    private String customerName;
    private BigDecimal totalAmount;
    private BigDecimal discount;
    private BigDecimal finalAmount;
    private String status;
    private String paymentMethod;
    private List<SaleOrderItemDTO> items;
    private LocalDateTime createdAt;
}

@Data
class SaleOrderItemDTO {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
