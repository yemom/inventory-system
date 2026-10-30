package com.inventory.backend.controller;

import com.inventory.backend.dto.*;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.PayrollPeriod;
import com.inventory.backend.repository.PayrollPeriodRepository;
import com.inventory.backend.service.PayrollService;
import com.inventory.backend.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance figures, from the central calculation layer.
 */
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final FinancialCalculationService calculations;
    private final TaxService taxService;
    private final PayrollService payrollService;
    private final PayrollPeriodRepository payrollPeriodRepository;

    /** Every financial figure for a period, with the period's comparison. */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) throw new IllegalArgumentException("from must be on or before to.");

        FinancialSummary current = calculations.summarise(start, end);
        FinancialSummary previous = calculations.previousPeriod(start, end);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("period", current);
        payload.put("previousPeriod", previous);
        payload.put("comparison", Map.of(
                "netSalesChange", com.inventory.backend.finance.FinancialCalculations
                        .change(current.netSales(), previous.netSales()),
                "grossProfitChange", com.inventory.backend.finance.FinancialCalculations
                        .change(current.grossProfit(), previous.grossProfit()),
                "netProfitChange", com.inventory.backend.finance.FinancialCalculations
                        .change(current.netProfit(), previous.netProfit()),
                "cashReceivedChange", com.inventory.backend.finance.FinancialCalculations
                        .change(current.cashReceived(), previous.cashReceived()),
                "netSalesPercentChange", percent(current.netSales(), previous.netSales()),
                "netProfitPercentChange", percent(current.netProfit(), previous.netProfit())));
        return ResponseEntity.ok(ApiResponse.ok("Financial summary", payload));
    }

    /** Dedicated Tax & Payroll Intelligence endpoint with charts and summaries */
    @GetMapping("/intelligence")
    @PreAuthorize("hasAnyAuthority('FINANCIAL_REPORT_VIEW','TAX_DASHBOARD_VIEW','PAYROLL_SUMMARY_VIEW')")
    public ResponseEntity<ApiResponse<TaxPayrollFinanceDTO>> intelligence(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        final YearMonth targetMonth;
        try {
            targetMonth = period == null || period.isBlank()
                    ? YearMonth.from(to != null ? to : LocalDate.now())
                    : YearMonth.parse(period);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("period must use YYYY-MM format.");
        }
        LocalDate end = to != null ? to : targetMonth.atEndOfMonth();
        LocalDate start = from != null ? from : targetMonth.atDay(1);
        if (start.isAfter(end)) throw new IllegalArgumentException("from must be on or before to.");
        if (period != null && !period.isBlank() && (YearMonth.from(start).isBefore(targetMonth)
                || YearMonth.from(end).isAfter(targetMonth))) {
            throw new IllegalArgumentException("from and to must fall within the selected period.");
        }

        // 1. Tax Summary
        TaxSummaryDTO tax = taxService.summary(targetMonth);
        TaxPayrollFinanceDTO.TaxSummarySection taxSec = new TaxPayrollFinanceDTO.TaxSummarySection(
                tax.taxableSales(),
                tax.outputVat(),
                tax.eligibleInputVat(),
                tax.estimatedNetVat(),
                tax.taxPaymentsMade(),
                tax.outstandingLiabilities(),
                tax.estimatedIncomeTax(),
                "ETB"
        );

        // 2. Payroll Summary
        PayrollDTO.Dashboard payrollDash = payrollService.dashboard(targetMonth);
        BigDecimal empPension = payrollDash.employeePension() != null ? payrollDash.employeePension() : ZERO;
        BigDecimal empTax = payrollDash.employmentTax() != null ? payrollDash.employmentTax() : ZERO;
        BigDecimal totalDeductions = empPension.add(empTax);

        TaxPayrollFinanceDTO.PayrollSummarySection payrollSec = new TaxPayrollFinanceDTO.PayrollSummarySection(
                payrollDash.grossPayroll(),
                empTax,
                empPension,
                payrollDash.employerPension(),
                totalDeductions,
                payrollDash.netPayroll(),
                payrollDash.totalPayrollPaid(),
                payrollDash.totalPayrollOutstanding(),
                payrollDash.employerCost(),
                "ETB",
                payrollDash.hasData()
        );

        // 3. Inventory & Business Summary
        FinancialSummary fin = calculations.summarise(start, end);
        TaxPayrollFinanceDTO.InventoryBusinessSection invSec = new TaxPayrollFinanceDTO.InventoryBusinessSection(
                fin.inventoryValue(),
                fin.costOfGoodsSold(),
                fin.netSales(),
                fin.salesReturns(),
                fin.purchaseCostReceived(),
                fin.operatingExpenses(),
                fin.grossProfit(),
                fin.netProfit(),
                "ETB",
                fin.basis()
        );

        // 4. Numerical Visualizations (6-month historical series from real records)
        List<TaxPayrollFinanceDTO.MonthlyVatPoint> monthlyVat = new ArrayList<>();
        List<TaxPayrollFinanceDTO.TaxLiabilityPaymentPoint> taxLiabPay = new ArrayList<>();
        List<TaxPayrollFinanceDTO.PayrollComparisonPoint> payrollGrossNet = new ArrayList<>();
        List<TaxPayrollFinanceDTO.PensionTaxTrendPoint> pensionTaxTrends = new ArrayList<>();
        List<TaxPayrollFinanceDTO.EmployerCostPoint> employerCostTrends = new ArrayList<>();
        List<TaxPayrollFinanceDTO.SalesCogsProfitPoint> salesCogsTrends = new ArrayList<>();
        List<TaxPayrollFinanceDTO.InventoryVsSalesPoint> invVsSales = new ArrayList<>();

        List<PayrollPeriod> allPeriods = payrollPeriodRepository.findAllByOrderByYearDescMonthDesc();

        for (int i = 5; i >= 0; i--) {
            YearMonth ym = targetMonth.minusMonths(i);
            String monthLabel = ym.toString();
            LocalDate f = ym.atDay(1), t = ym.atEndOfMonth();

            // Financial summary for this month
            FinancialSummary monthFin = calculations.summarise(f, t);
            salesCogsTrends.add(new TaxPayrollFinanceDTO.SalesCogsProfitPoint(
                    monthLabel, monthFin.netSales(), monthFin.costOfGoodsSold(), monthFin.grossProfit()));
            invVsSales.add(new TaxPayrollFinanceDTO.InventoryVsSalesPoint(
                    monthLabel, monthFin.inventoryValue(), monthFin.netSales(), monthFin.costOfGoodsSold()));

            // Tax for this month
            TaxSummaryDTO mTax = taxService.summary(ym);
            monthlyVat.add(new TaxPayrollFinanceDTO.MonthlyVatPoint(
                    monthLabel, mTax.outputVat(), mTax.eligibleInputVat(), mTax.estimatedNetVat()));
            taxLiabPay.add(new TaxPayrollFinanceDTO.TaxLiabilityPaymentPoint(
                    monthLabel, mTax.outstandingLiabilities(), mTax.taxPaymentsMade()));

            // Payroll for this month (from real payroll period record)
            PayrollPeriod pp = allPeriods.stream()
                    .filter(p -> p.getYear() == ym.getYear() && p.getMonth() == ym.getMonthValue())
                    .findFirst().orElse(null);

            BigDecimal gross = pp != null ? pp.getGrossPayroll() : ZERO;
            BigDecimal net = pp != null ? pp.getNetPayroll() : ZERO;
            BigDecimal incTax = pp != null ? pp.getEmploymentTax() : ZERO;
            BigDecimal eePension = pp != null ? pp.getEmployeePension() : ZERO;
            BigDecimal erPension = pp != null ? pp.getEmployerPension() : ZERO;
            BigDecimal erCost = pp != null ? pp.getEmployerCost() : ZERO;

            payrollGrossNet.add(new TaxPayrollFinanceDTO.PayrollComparisonPoint(
                    monthLabel, gross, net, gross.subtract(net).max(ZERO)));
            pensionTaxTrends.add(new TaxPayrollFinanceDTO.PensionTaxTrendPoint(
                    monthLabel, incTax, eePension, erPension));
            employerCostTrends.add(new TaxPayrollFinanceDTO.EmployerCostPoint(
                    monthLabel, gross, erPension, erCost));
        }

        // Liabilities by status
        List<TaxPayrollFinanceDTO.LiabilityStatusPoint> liabilities = List.of(
                new TaxPayrollFinanceDTO.LiabilityStatusPoint(
                        "VAT Liability",
                        ZERO,
                        tax.estimatedNetVat().max(ZERO),
                        tax.taxPaymentsMade(),
                        tax.outstandingLiabilities()
                ),
                new TaxPayrollFinanceDTO.LiabilityStatusPoint(
                        "Payroll Obligations",
                        allPeriods.stream().filter(p -> "DRAFT".equals(p.getStatus())).map(PayrollPeriod::getNetPayroll).reduce(ZERO, BigDecimal::add),
                        allPeriods.stream().filter(p -> "FINALIZED".equals(p.getStatus())).map(PayrollPeriod::getNetPayroll).reduce(ZERO, BigDecimal::add),
                        payrollDash.totalPayrollPaid(),
                        payrollDash.totalPayrollOutstanding()
                )
        );

        TaxPayrollFinanceDTO.ChartSeriesSection charts = new TaxPayrollFinanceDTO.ChartSeriesSection(
                monthlyVat, taxLiabPay, payrollGrossNet, pensionTaxTrends,
                employerCostTrends, salesCogsTrends, liabilities, invVsSales
        );

        return ResponseEntity.ok(ApiResponse.ok("Finance intelligence retrieved",
                new TaxPayrollFinanceDTO(taxSec, payrollSec, invSec, charts)));
    }

    private static Object percent(java.math.BigDecimal current, java.math.BigDecimal previous) {
        java.math.BigDecimal value =
                com.inventory.backend.finance.FinancialCalculations.percentChange(current, previous);
        return value == null ? "not comparable — no earlier figure" : value.toPlainString();
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAuthority('FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cashFlow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) throw new IllegalArgumentException("from must be on or before to.");
        FinancialSummary summary = calculations.summarise(start, end);

        Map<String, Object> cash = new LinkedHashMap<>();
        cash.put("from", start.toString());
        cash.put("to", end.toString());
        cash.put("cashReceived", summary.cashReceived());
        cash.put("cashPaidToSuppliers", summary.cashPaidToSuppliers());
        cash.put("cashRefunded", summary.cashRefunded());
        cash.put("operatingExpensesPaid", summary.operatingExpenses());
        cash.put("netCashFlow", summary.netCashFlow());
        cash.put("netSales", summary.netSales());
        cash.put("salesNotYetCollected",
                summary.netSales().subtract(summary.cashReceived()));
        cash.put("basis", "Only COMPLETED payments count as cash. "
                + "Approved expenses are treated as paid on their transaction date.");
        return ResponseEntity.ok(ApiResponse.ok("Cash flow", cash));
    }

    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('RECEIVABLE_READ')")
    public ResponseEntity<ApiResponse<FinancialSummary>> receivables(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate end = asOf != null ? asOf : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Receivables",
                calculations.summarise(end.minusDays(3650), end)));
    }

    @GetMapping("/periods")
    @PreAuthorize("hasAnyAuthority('FINANCIAL_REPORT_VIEW','REPORT_VIEW','SALES_REPORT_VIEW','DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<List<String>>> periods() {
        return ResponseEntity.ok(ApiResponse.ok("Available period presets", List.of(
                "today", "yesterday", "this_week", "last_week",
                "this_month", "last_month", "this_quarter", "last_quarter",
                "year_to_date", "last_30_days", "last_90_days")));
    }
}
