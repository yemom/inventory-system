package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.NotNull;

@Data
public class CreatePurchaseOrderRequest {
    private String supplierName;
    private Long supplierId;

    /**
     * Where the goods will be put away when they arrive.
     *
     * <p>Optional: a business with one active warehouse has it resolved at
     * receiving time. With several, naming one is required — putting goods away
     * in an arbitrary location is what makes per-warehouse reports wrong.
     */
    private Long warehouseId;

    /** Order-level tax on the whole purchase. */
    private BigDecimal tax;

    /**
     * PAID, PARTIAL, or UNPAID.
     *
     * <p>Defaults to UNPAID. The previous default was PAID, which recorded a
     * purchase as settled at the moment it was raised — before anybody had paid
     * anything, and often before the goods had arrived.
     */
    private String paymentStatus;

    /**
     * How much has already been paid (for PARTIAL orders).
     * Ignored when paymentStatus is PAID or UNPAID.
     */
    private BigDecimal amountPaid;

    /** Agreed credit terms, e.g. "NET 30". Used to derive the due date. */
    private String paymentTerms;

    private LocalDate expectedDeliveryDate;
    private LocalDate dueDate;

    @NotNull(message = "a purchase must have at least one line")
    @jakarta.validation.Valid
    private List<@NotNull(message = "a purchase line cannot be null") CreatePurchaseOrderItem> items;
}