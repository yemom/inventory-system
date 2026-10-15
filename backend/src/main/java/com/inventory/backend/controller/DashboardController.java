package com.inventory.backend.controller;

import com.inventory.backend.ai.AnomalyDetectionService;
import com.inventory.backend.ai.FinancialInsightService;
import com.inventory.backend.ai.InventoryOptimizationService;
import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.repository.*;
import com.inventory.backend.service.SalesSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The dashboard's headline figures.
 *
 * <p>Every financial number here comes from {@link FinancialCalculationService}.
 * The previous version computed "gross profit" as revenue minus total purchase
 * orders — which is not gross profit under any accounting definition, and
 * overstates it every time stock is bought but not yet sold. The dashboard now
 * reads the same figures as the reports, the assistant and the AI insights, so
 * the four can never disagree.
 *
 * <p>The cost figures are gated separately from the operational ones. A
 * Cashier can see that the till took 40,000 this month without that implying any
 * permission to see what the goods cost or what the business earned.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProductRepository productRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final FinancialCalculationService calculations;
    private final FinancialInsightService insightService;
    private final InventoryOptimizationService inventoryService;
    private final AnomalyDetectionService anomalyService;
    private final SalesSummaryService salesSummaryService;

    /**
     * Operational figures only: counts and stock alerts.
     *
     * <p>Requires {@code DASHBOARD_VIEW}, which every role holds, and contains
     * nothing commercially sensitive. No revenue, no cost, no margin.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        long totalProducts = productRepository.count();
        long activeProducts = productRepository.countByActiveTrue();
        long lowStockProducts = productRepository.countLowStock();
        long outOfStockProducts = productRepository.countOutOfStock();

        stats.put("totalProducts", totalProducts);
        stats.put("activeProducts", activeProducts);
        stats.put("lowStockProducts", lowStockProducts);
        stats.put("outOfStockProducts", outOfStockProducts);

        stats.put("totalSaleOrders", saleOrderRepository.count());
        stats.put("totalPurchaseOrders", purchaseOrderRepository.count());
        stats.put("totalCustomers", customerRepository.count());
        stats.put("totalSuppliers", supplierRepository.count());
        stats.put("note", "Financial figures are served by /dashboard/financial, "
                + "which requires the permission for the figures in question.");
        return ResponseEntity.ok(ApiResponse.ok("Dashboard stats retrieved successfully", stats));
    }

    /**
     * The full financial picture, from the calculation layer.
     *
     * <p>Requires {@code FINANCIAL_REPORT_VIEW}: net sales, gross and net profit,
     * margins, cash, receivables, payables and inventory value are the figures a
     * business does not publish, and they used to sit behind a single
     * {@code DASHBOARD_VIEW} that every role holds.
     */
    @GetMapping("/financial")
    @PreAuthorize("hasAuthority('FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> financial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);

        FinancialSummary current = calculations.summarise(start, end);
        FinancialSummary previous = calculations.previousPeriod(start, end);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("from", start.toString());
        stats.put("to", end.toString());

        // Retained key names so existing dashboard widgets keep working.
        stats.put("totalRevenue", current.netSales());
        stats.put("grossSales", current.grossSales());
        stats.put("netSales", current.netSales());
        stats.put("totalPurchaseAmount", current.purchaseCostReceived());

        stats.put("cogs", current.costOfGoodsSold());
        stats.put("grossProfit", current.grossProfit());
        stats.put("grossMarginPercent", current.grossMarginPercent());
        stats.put("operatingExpenses", current.operatingExpenses());
        stats.put("netProfit", current.netProfit());
        stats.put("netMarginPercent", current.netMarginPercent());

        stats.put("cashReceived", current.cashReceived());
        stats.put("cashPaidToSuppliers", current.cashPaidToSuppliers());
        stats.put("netCashFlow", current.netCashFlow());
        stats.put("accountsReceivable", current.accountsReceivable());
        stats.put("accountsPayable", current.accountsPayable());

        stats.put("inventoryValue", current.inventoryValue());
        stats.put("stockLossValue", current.stockLossValue());
        stats.put("stockLossUnits", current.stockLossUnits());
        stats.put("purchaseCostOrderedNotReceived", current.purchaseCostOrderedNotReceived());

        stats.put("salesCount", current.salesCount());
        stats.put("itemsSold", current.itemsSold());

        stats.put("previousNetSales", previous.netSales());
        stats.put("previousGrossProfit", previous.grossProfit());
        stats.put("previousNetProfit", previous.netProfit());
        stats.put("previousCashReceived", previous.cashReceived());
        stats.put("basis", current.basis());
        stats.put("insufficientData", current.insufficientData());

        return ResponseEntity.ok(ApiResponse.ok("Dashboard financial summary", stats));
    }

    /**
     * AI insights, alerts and recommended actions for the dashboard.
     *
     * <p>Gated on {@code AI_INSIGHT_VIEW}. Excluded when the caller lacks it, the
     * dashboard still works — the intelligence section is an addition to the
     * page, not a precondition for rendering it.
     */
    /**
     * Sales totals for a dashboard, scoped by the server to the caller.
     *
     * <p>Requires {@code DASHBOARD_VIEW}, which every role holds, because the
     * figures it returns are a count of somebody's own work — not the business's
     * financial picture. A caller holding {@code SALES_REPORT_VIEW} gets every
     * seller; anyone else, a cashier included, gets only the orders they
     * recorded. There is no parameter to widen that: the scope comes from the
     * authenticated user, so the browser cannot ask for another cashier's
     * takings.
     *
     * <p>Cancelled and voided orders are counted and returned separately rather
     * than dropped, and collected cash comes only from completed payments — an
     * order existing is not the same as it having been paid.
     */
    @GetMapping("/sales-summary")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> salesSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok("Sales summary",
                salesSummaryService.summarise(from, to, warehouseId)));
    }

    @GetMapping("/insights")
    @PreAuthorize("hasAuthority('AI_INSIGHT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> insights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", start.toString());
        payload.put("to", end.toString());
        payload.put("insights", insightService.insightsFor(start, end));
        payload.put("anomalies", anomalyService.detectAll(start, end));
        payload.put("recommendations", inventoryService.recommendReorders(10));
        payload.put("stockoutRisks", inventoryService.predictStockouts(30));
        return ResponseEntity.ok(ApiResponse.ok("Dashboard insights", payload));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("status", "UP");
        h.put("service", "StockFlow Inventory API");
        return ResponseEntity.ok(h);
    }
}