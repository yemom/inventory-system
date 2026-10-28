package com.inventory.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record TaxPayrollFinanceDTO(
        TaxSummarySection taxSummary,
        PayrollSummarySection payrollSummary,
        InventoryBusinessSection inventorySummary,
        ChartSeriesSection charts
) {
    public record TaxSummarySection(
            BigDecimal taxableSales,
            BigDecimal outputVat,
            BigDecimal recoverableInputVat,
            BigDecimal netVatPayableOrCredit,
            BigDecimal taxPaymentsMade,
            BigDecimal outstandingTaxLiabilities,
            BigDecimal estimatedIncomeTax,
            String currency
    ) {}

    public record PayrollSummarySection(
            BigDecimal grossPayroll,
            BigDecimal employmentTaxWithheld,
            BigDecimal employeePension,
            BigDecimal employerPension,
            BigDecimal totalDeductions,
            BigDecimal netPayrollPayable,
            BigDecimal payrollPaid,
            BigDecimal payrollOutstanding,
            BigDecimal totalEmployerPayrollCost,
            String currency,
            boolean hasPayrollData
    ) {}

    public record InventoryBusinessSection(
            BigDecimal inventoryValuation,
            BigDecimal costOfGoodsSold,
            BigDecimal netSales,
            BigDecimal salesReturns,
            BigDecimal purchaseTotals,
            BigDecimal recordedExpenses,
            BigDecimal grossProfit,
            BigDecimal netProfit,
            String currency,
            String accountingBasis
    ) {}

    public record ChartSeriesSection(
            List<MonthlyVatPoint> monthlyVat,
            List<TaxLiabilityPaymentPoint> taxLiabilitiesVsPayments,
            List<PayrollComparisonPoint> payrollGrossVsNet,
            List<PensionTaxTrendPoint> employeeTaxPensionTrends,
            List<EmployerCostPoint> employerCostTrends,
            List<SalesCogsProfitPoint> salesCogsProfitTrends,
            List<LiabilityStatusPoint> liabilitiesByStatus,
            List<InventoryVsSalesPoint> inventoryVsSales
    ) {}

    public record MonthlyVatPoint(String month, BigDecimal outputVat, BigDecimal inputVat, BigDecimal netVat) {}
    public record TaxLiabilityPaymentPoint(String month, BigDecimal liability, BigDecimal payment) {}
    public record PayrollComparisonPoint(String month, BigDecimal grossPayroll, BigDecimal netPayroll, BigDecimal deductions) {}
    public record PensionTaxTrendPoint(String month, BigDecimal incomeTax, BigDecimal employeePension, BigDecimal employerPension) {}
    public record EmployerCostPoint(String month, BigDecimal grossSalary, BigDecimal employerPension, BigDecimal totalEmployerCost) {}
    public record SalesCogsProfitPoint(String month, BigDecimal sales, BigDecimal cogs, BigDecimal grossProfit) {}
    public record LiabilityStatusPoint(String category, BigDecimal draft, BigDecimal approved, BigDecimal paid, BigDecimal outstanding) {}
    public record InventoryVsSalesPoint(String month, BigDecimal inventoryValue, BigDecimal sales, BigDecimal cogs) {}
}
