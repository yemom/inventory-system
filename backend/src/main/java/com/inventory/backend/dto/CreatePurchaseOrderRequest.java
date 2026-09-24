package com.inventory.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotNull;

@Data
public class CreatePurchaseOrderRequest {
    private String supplierName;
    private String status;
    @NotNull
    private List<CreatePurchaseOrderItem> items;
}