package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateSaleOrderItem {
    @NotNull
    private Long productId;
    @NotNull
    @jakarta.validation.constraints.Min(1)
    private Integer quantity;
    @NotNull
    private BigDecimal unitPrice;
    private BigDecimal discount;
}