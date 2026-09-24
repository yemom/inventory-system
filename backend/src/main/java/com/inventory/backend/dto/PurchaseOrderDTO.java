package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseOrderDTO {
    private Long id;
    private String orderNumber;
    private String reference;       // alias for orderNumber
    private String supplierName;
    private BigDecimal totalAmount;
    private BigDecimal total;       // alias for totalAmount
    private BigDecimal paid;
    private String paymentStatus;   // PAID, PARTIAL, UNPAID
    private String status;
    private String date;            // formatted createdAt
    private LocalDateTime createdAt;
    private List<PurchaseOrderItemDTO> items;
}
