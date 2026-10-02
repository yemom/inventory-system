package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateSaleOrderRequest {
    private String customerName;
    private Long customerId;
    private BigDecimal discount;
    private BigDecimal tax;
    private String paymentMethod;
    private String paymentStatus; // PAID, PARTIAL, UNPAID – defaults to PAID
    private String idempotencyKey; // client-generated key; protects against double submission
    @NotNull
    private List<CreateSaleOrderItem> items;
}