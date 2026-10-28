package com.inventory.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TaxSummaryDTO(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalSales,
        BigDecimal taxableSales,
        BigDecimal zeroRatedSales,
        BigDecimal exemptSales,
        BigDecimal outputVat,
        BigDecimal eligibleInputVat,
        BigDecimal nonRecoverableInputVat,
        BigDecimal estimatedNetVat,
        BigDecimal withholdingTax,
        BigDecimal estimatedIncomeTax,
        BigDecimal taxPaymentsMade,
        BigDecimal outstandingLiabilities,
        BigDecimal salesWithTaxData,
        BigDecimal purchasesWithTaxData,
        String currency,
        String calculationStatus,
        String classificationNotice,
        List<ComplianceDeadline> upcomingDeadlines,
        List<MonthlyTaxTotal> trend) {

    public record ComplianceDeadline(
            String taxType,
            LocalDate dueDate,
            String description,
            boolean overdue) {}

    public record MonthlyTaxTotal(
            String period,
            BigDecimal outputVat,
            BigDecimal inputVat,
            BigDecimal netVat) {}
}
