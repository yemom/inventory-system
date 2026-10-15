package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance figures, from the central calculation layer.
 *
 * <p>These endpoints exist because the receivables, payables, profit and loss and
 * cash-flow screens previously recomputed their own totals in the browser from
 * whatever the list endpoints happened to return. Two screens could therefore
 * print different gross profit from the same data. Every number here comes from
 * {@link FinancialCalculationService}, which is the same source the reports, the
 * dashboard and the assistant read.
 */
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinancialCalculationService calculations;

    /** Every financial figure for a period, with the period's comparison. */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);

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

    private static Object percent(java.math.BigDecimal current, java.math.BigDecimal previous) {
        java.math.BigDecimal value =
                com.inventory.backend.finance.FinancialCalculations.percentChange(current, previous);
        return value == null ? "not comparable — no earlier figure" : value.toPlainString();
    }

    /**
     * Cash received, paid and refunded in a period.
     *
     * <p>Separate from the summary because it is the question a business asks
     * most often and the one most often answered wrongly: an unpaid invoice is
     * revenue, not cash.
     */
    @GetMapping("/cash-flow")
    @PreAuthorize("hasAuthority('FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cashFlow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
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

    /** Outstanding customer balances and their ageing. */
    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('RECEIVABLE_READ')")
    public ResponseEntity<ApiResponse<FinancialSummary>> receivables(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate end = asOf != null ? asOf : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Receivables",
                calculations.summarise(end.minusDays(3650), end)));
    }

    /**
     * The catalogues the UI needs to render a period picker.
     *
     * <p>Cheap and permission-light on purpose: a date dropdown should not require
     * a financial permission just to list years.
     */
    @GetMapping("/periods")
    @PreAuthorize("hasAnyAuthority('FINANCIAL_REPORT_VIEW','REPORT_VIEW','SALES_REPORT_VIEW','DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<List<String>>> periods() {
        return ResponseEntity.ok(ApiResponse.ok("Available period presets", List.of(
                "today", "yesterday", "this_week", "last_week",
                "this_month", "last_month", "this_quarter", "last_quarter",
                "year_to_date", "last_30_days", "last_90_days")));
    }
}