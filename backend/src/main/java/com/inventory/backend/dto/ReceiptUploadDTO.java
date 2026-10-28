package com.inventory.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReceiptUploadDTO(
    Long id,
    String receiptName,
    String fileType,
    BigDecimal detectedAmount,
    BigDecimal taxableAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    String treatment,
    boolean taxInclusive,
    String currency,
    boolean aiExtracted,
    BigDecimal aiConfidence,
    String vendorName,
    LocalDate receiptDate,
    String notes,
    String accountantName,
    LocalDateTime createdAt,
    String notice
) {}
