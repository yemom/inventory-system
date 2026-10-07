package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.service.InventoryReportService;
import com.inventory.backend.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Reporting endpoints, each guarded by the permission for the specific report
 * rather than a single broad "report" authority.
 *
 * <p>This is what makes "a Supervisor may see sales and low stock but not profit
 * or inventory value" enforceable rather than aspirational. The sales report
 * previously accepted {@code SALE_READ}, which every Cashier holds, so the
 * whole sales ledger — every cashier's takings — was readable by the till staff
 * it reports on. Sales reporting now needs {@code SALES_REPORT_VIEW}.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class SalesReportController {

    private final SalesReportService salesReportService;
    private final InventoryReportService inventoryReportService;

    /**
     * Sales report.
     *
     * <p>{@code SALE_READ} is deliberately not accepted: reading your own sale is
     * not the same authority as reading everyone's.
     */
    @GetMapping("/sales")
    @PreAuthorize("hasAnyAuthority('SALES_REPORT_VIEW','REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> salesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok("Sales report retrieved successfully",
                salesReportService.salesReport(from, to, customerId, productId, sellerId, paymentMethod, status)));
    }

    /** Low-stock and out-of-stock lines. A Supervisor may read this. */
    @GetMapping("/inventory")
    @PreAuthorize("hasAnyAuthority('INVENTORY_REPORT_VIEW','LOW_STOCK_REPORT_VIEW','REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inventoryReport() {
        return ResponseEntity.ok(ApiResponse.ok("Inventory report retrieved successfully",
                inventoryReportService.lowStockReport()));
    }

    /**
     * Inventory valuation.
     *
     * <p>Separate from {@code /inventory} on purpose: a Supervisor may see which
     * products need reordering without also seeing what the stock is worth.
     */
    @GetMapping("/inventory-value")
    @PreAuthorize("hasAuthority('INVENTORY_VALUE_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inventoryValueReport() {
        return ResponseEntity.ok(ApiResponse.ok("Inventory valuation retrieved successfully",
                inventoryReportService.inventoryValueReport()));
    }

    /** Count and value summary for the inventory overview. */
    @GetMapping("/inventory-summary")
    @PreAuthorize("hasAnyAuthority('INVENTORY_READ','INVENTORY_REPORT_VIEW','REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inventorySummary() {
        return ResponseEntity.ok(ApiResponse.ok("Inventory summary retrieved successfully",
                inventoryReportService.inventorySummary()));
    }

    /**
     * Profit and loss.
     *
     * <p>Gated on {@code PROFIT_LOSS_READ}, which a Supervisor does not hold: it
     * is the single most commercially sensitive figure in the system.
     */
    @GetMapping("/profit-loss")
    @PreAuthorize("hasAuthority('PROFIT_LOSS_READ')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> profitLossReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.ok("Profit and loss retrieved successfully",
                salesReportService.profitLossReport(from, to)));
    }
}