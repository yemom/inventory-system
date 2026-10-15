package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
public class CreateSaleOrderItem {
    @NotNull(message = "productId is required on every sale line")
    private Long productId;

    @NotNull(message = "quantity is required on every sale line")
    @jakarta.validation.constraints.Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    /**
     * What the customer is charged for one unit.
     *
     * <p>Strictly positive: the calculation layer multiplies this by the
     * quantity to produce revenue, so a negative or zero price writes a sale
     * that reduces the period's net sales rather than adding to it, while still
     * moving stock out of the warehouse and being paid for.
     */
    @NotNull(message = "unitPrice is required on every sale line")
    @Positive(message = "unitPrice must be greater than zero")
    private BigDecimal unitPrice;

    @DecimalMin(value = "0.00", message = "discount cannot be negative")
    private BigDecimal discount;
}