package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateSaleOrderItem {
    @NotNull
    private Long productId;
    @NotNull
    private Integer quantity;
    @NotNull
    private BigDecimal unitPrice;
}