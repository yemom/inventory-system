package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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

    /**
     * Reductions applied to the invoice.
     *
     * <p>Bounded at zero and non-null in {@code BigDecimal} terms because the
     * invoice is {@code subtotal − discount + tax}. A negative discount does not
     * reduce anything: it inflates the invoice, and the payment recorded against
     * it is written as completed cash and flows into the cash-flow report as an
     * inflow. Rejected here rather than clamped, so a caller is told what it
     * sent instead of quietly getting a different number back.
     */
    @DecimalMin(value = "0.00", message = "discount cannot be negative")
    private BigDecimal discount;

    @DecimalMin(value = "0.00", message = "tax cannot be negative")
    private BigDecimal tax;

    private String paymentMethod;
    private String paymentStatus; // PAID, PARTIAL, UNPAID — defaults to PAID
    private String idempotencyKey; // client-generated key; protects against double submission

    /**
     * The lines of the sale.
     *
     * <p>{@code @Valid} is what makes each line's own constraints run. Without
     * it the nested {@code @NotNull productId} and {@code @Min(1) quantity} are
     * declared but never enforced, and a line with no product reaches the
     * service, where the product lookup returns nothing and the request fails as
     * a server error instead of the 400 it is.
     */
    @NotNull(message = "a sale must have at least one line")
    @Valid
    private List<@NotNull(message = "a sale line cannot be null") CreateSaleOrderItem> items;
}