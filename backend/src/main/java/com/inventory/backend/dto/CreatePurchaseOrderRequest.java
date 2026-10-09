package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotNull;

@Data
public class CreatePurchaseOrderRequest {
    private String supplierName;
    private String status;

    /**
     * PAID, PARTIAL, or UNPAID.  Defaults to PAID when omitted so that
     * callers that do not send the field keep their existing behaviour.
     */
    private String paymentStatus;

    /**
     * How much has already been paid (for PARTIAL orders).
     * Ignored when paymentStatus is PAID or UNPAID.
     */
    private BigDecimal amountPaid;

    @NotNull
    private List<CreatePurchaseOrderItem> items;
}