package com.inventory.backend.dto;

import java.math.BigDecimal;

public record TaxCalculationDTO(String receiptName, String treatment, boolean taxInclusive,
                                BigDecimal enteredAmount, BigDecimal taxableAmount, BigDecimal taxAmount,
                                BigDecimal totalAmount, String currency, String ruleCode, String notice) {}
