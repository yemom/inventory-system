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
    // Void / refund approval state. Null means no request has been raised.
    private String voidStatus;
    private String voidReason;
    private String voidReviewNote;
    private String refundStatus;
    private String refundReason;
    private String refundReviewNote;
    private Long customerId;
    private BigDecimal tax;
    private String createdBy;

    /**
     * The cashier who rang the sale, as a reference rather than an id.
     *
     * <p>Resolved from {@code createdBy} on the order. Null when the sale has no
     * recorded creator — imported or seeded orders legitimately have none, so
     * this is an expected state and the UI renders an em dash.
     */
    private RefDTO cashier;

    /**
     * The branch the sale was rung in.
     *
     * <p>Derived from {@code createdBy.branch}. Note that branch is free text on
     * the user record, not a row in its own table, so {@code id} is null — see
     * {@link RefDTO}. The name is what a branch column displays.
     */
    private RefDTO branch;

    private List<SaleOrderItemDTO> items;
}
