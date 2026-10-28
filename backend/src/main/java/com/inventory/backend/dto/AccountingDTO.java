package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AccountingDTO {

    private AccountingDTO() {}

    public record AccountResponse(
            Long id,
            String code,
            String name,
            String category,
            String subCategory,
            String description,
            Boolean active,
            Boolean systemAccount,
            BigDecimal balance
    ) {}

    public record CreateAccountRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String category,
            String subCategory,
            String description
    ) {}

    public record UpdateAccountRequest(
            @NotBlank String name,
            String subCategory,
            String description,
            Boolean active
    ) {}

    public record JournalLineResponse(
            Long id,
            Long accountId,
            String accountCode,
            String accountName,
            BigDecimal debit,
            BigDecimal credit,
            String description,
            Integer lineNumber
    ) {}

    public record JournalEntryResponse(
            Long id,
            String entryNumber,
            LocalDate entryDate,
            String accountingPeriod,
            String reference,
            String description,
            String sourceType,
            String sourceId,
            String status,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            LocalDateTime postedAt,
            String postedByName,
            LocalDateTime reversedAt,
            String reversedByName,
            Long reversalEntryId,
            String reversalReason,
            List<JournalLineResponse> lines
    ) {}

    public record CreateJournalLineRequest(
            Long accountId,
            String accountCode,
            BigDecimal debit,
            BigDecimal credit,
            String description
    ) {}

    public record CreateJournalEntryRequest(
            @NotNull LocalDate entryDate,
            String reference,
            @NotBlank String description,
            String sourceType,
            String sourceId,
            @NotEmpty List<CreateJournalLineRequest> lines
    ) {}

    public record ReverseJournalRequest(
            @NotBlank String reason
    ) {}

    public record LedgerTransaction(
            LocalDate date,
            Long entryId,
            String entryNumber,
            String reference,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance
    ) {}

    public record GeneralLedgerReport(
            AccountResponse account,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal openingBalance,
            List<LedgerTransaction> transactions,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal closingBalance
    ) {}

    public record TrialBalanceItem(
            String accountCode,
            String accountName,
            String category,
            BigDecimal debitBalance,
            BigDecimal creditBalance
    ) {}

    public record TrialBalanceReport(
            LocalDate asOfDate,
            List<TrialBalanceItem> items,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced,
            BigDecimal discrepancy
    ) {}

    public record FinancialStatementItem(
            String code,
            String name,
            String subCategory,
            BigDecimal amount
    ) {}

    public record ProfitLossReport(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal salesRevenue,
            BigDecimal salesReturnsAndDiscounts,
            BigDecimal netSales,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal grossMarginPercent,
            List<FinancialStatementItem> operatingExpenseItems,
            BigDecimal totalOperatingExpenses,
            BigDecimal payrollExpenses,
            BigDecimal netProfitOrLoss,
            String basis
    ) {}

    public record BalanceSheetReport(
            LocalDate asOfDate,
            List<FinancialStatementItem> currentAssets,
            List<FinancialStatementItem> nonCurrentAssets,
            BigDecimal totalAssets,
            List<FinancialStatementItem> currentLiabilities,
            List<FinancialStatementItem> nonCurrentLiabilities,
            BigDecimal totalLiabilities,
            List<FinancialStatementItem> equityItems,
            BigDecimal retainedEarnings,
            BigDecimal totalEquity,
            BigDecimal totalLiabilitiesAndEquity,
            boolean balanced,
            BigDecimal discrepancy,
            String basis
    ) {}

    public record ReconciliationItem(
            String category,
            String reference,
            String description,
            BigDecimal expectedAmount,
            BigDecimal actualAmount,
            BigDecimal variance,
            String status,
            String notes
    ) {}

    public record ReconciliationReport(
            LocalDate asOfDate,
            List<ReconciliationItem> items,
            int totalChecked,
            int discrepanciesCount,
            BigDecimal totalDiscrepancyAmount,
            String reviewStatus
    ) {}

    public record AiAccountingInsight(
            String title,
            String summary,
            List<String> keyTrends,
            List<String> anomalies,
            List<String> recommendations,
            String period,
            String timestamp
    ) {}
}
