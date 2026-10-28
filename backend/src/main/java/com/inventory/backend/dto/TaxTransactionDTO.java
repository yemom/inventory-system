package com.inventory.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaxTransactionDTO(
        String transactionType, // SALE, PURCHASE, RECEIPT, RETURN
        Long id,
        String referenceNumber,
        LocalDateTime transactionDate,
        String partyName,
        BigDecimal totalAmount,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal taxRate,
        String taxTreatment,
        String status,
        String notes
) {}
