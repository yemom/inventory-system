package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleOrderDTO {
    private Long id;
    // "orderNumber" is the canonical field; "reference" is the frontend alias
    private String orderNumber;
    private String reference;    // alias for orderNumber – populated in toDTO
    private String customerName;
    // totalAmount = sum of line items before discount
    private BigDecimal totalAmount;
    // discount applied on the whole order
    private BigDecimal discount;
    // finalAmount = totalAmount - discount
    private BigDecimal finalAmount;
    // "total" mirrors finalAmount for backward-compat with frontend
    private BigDecimal total;
    // payment fields
    private BigDecimal paid;
    private String paymentStatus;  // PAID, PARTIAL, UNPAID
    private String paymentMethod;
    private String status;
    // "date" mirrors createdAt date for frontend convenience
    private String date;
    private LocalDateTime createdAt;
    private List<SaleOrderItemDTO> items;
}
