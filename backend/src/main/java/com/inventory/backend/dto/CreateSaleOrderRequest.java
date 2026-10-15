package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateSaleOrderRequest {
    private String customerName;
    private Long customerId;

    /**
     * Which warehouse the goods come from.
     *
     * <p>Optional when the business has exactly one active warehouse, which is
     * then resolved automatically. With two or more the sale is refused rather
     * than guessed: taking stock from an arbitrary warehouse is how a shop ends
     * up showing stock in a place it is not.
     */
    private Long warehouseId;

    private BigDecimal discount;
    private BigDecimal tax;
    private String paymentMethod;
    private String paymentStatus; // PAID, PARTIAL, UNPAID — defaults to PAID
    private String idempotencyKey; // client-generated key; protects against double submission
    @NotNull
    private List<CreateSaleOrderItem> items;
}