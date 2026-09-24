package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateSaleOrderRequest {
    private String customerName;
    private BigDecimal discount;
    private String paymentMethod;
    private String paymentStatus; // PAID, PARTIAL, UNPAID – defaults to PAID
    @NotNull
    private List<CreateSaleOrderItem> items;
}