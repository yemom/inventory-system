package com.inventory.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderDTO {
    private Long id;
    private String orderNumber;
    private String reference;    // alias for orderNumber – populated in toDTO
    private String supplierName;
    private Long supplierId;

    /** Where received goods are put away. */
    private Long warehouseId;
    private String warehouseName;

    private BigDecimal totalAmount;
    private BigDecimal total;         // alias for totalAmount
    /**
     * DRAFT, ORDERED, PARTIALLY_RECEIVED, RECEIVED or CANCELLED.
     * Ordered and received are deliberately separate: goods that have not
     * arrived are not in stock, whatever the order says was bought.
     */
    private String status;

    /** PAID, PARTIAL or UNPAID. Independent of the receipt status. */
    private String paymentStatus;
    private BigDecimal paid;

    /** Still owed on this order: totalAmount − paid. */
    private BigDecimal balanceDue;

    private String paymentTerms;
    private LocalDate dueDate;
    private LocalDate expectedDeliveryDate;

    private String date;
    private java.time.LocalDateTime createdAt;

    private List<PurchaseOrderItemDTO> items;
}