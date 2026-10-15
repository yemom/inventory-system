package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.exception.GlobalExceptionHandler;
import com.inventory.backend.service.ExportService;
import com.inventory.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Reports, and their exports.
 *
 * <p>Each report has one JSON endpoint and one export endpoint. The export reads
 * the same report through the same service method, so a CSV or PDF can never
 * contain a figure the screen did not show.
 *
 * <p>Permissions mirror the existing catalogue: operational reports need the
 * operational permission, financial reports need the financial one. Exporting is
 * gated on {@code REPORT_EXPORT} <em>in addition</em> to the read permission —
 * reading a figure on screen and taking a copy of it away are different acts and
 * a shared export button must not silently become a way round the second.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ExportService exportService;

    // ── Sales and gross profit ────────────────────────────────────────────────

    @GetMapping("/sales-detail")
    @PreAuthorize("hasAnyAuthority('SALES_REPORT_VIEW','REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok("Sales and gross profit",
                reportService.salesAndProfit(from(from), to(to), warehouseId, productId, customerId)));
    }

    // ── Purchases and supplier balances ───────────────────────────────────────

    @GetMapping("/purchases-detail")
    @PreAuthorize("hasAuthority('PURCHASE_READ')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> purchases(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok("Purchases and supplier balances",
                reportService.purchases(from(from), to(to), supplierId, status)));
    }

    // ── Income, expenses and cash flow ────────────────────────────────────────

    @GetMapping("/income-expenses")
    @PreAuthorize("hasAuthority('FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> incomeAndExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Income and expenses",
                reportService.incomeAndExpenses(from(from), to(to))));
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAuthority('FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cashFlow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Cash flow",
                reportService.cashFlow(from(from), to(to))));
    }

    @GetMapping("/profit-loss-detail")
    @PreAuthorize("hasAuthority('PROFIT_LOSS_READ')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> profitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Profit and loss",
                reportService.profitAndLoss(from(from), to(to))));
    }

    @GetMapping("/product-profitability")
    @PreAuthorize("hasAuthority('PROFIT_LOSS_READ')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> productProfitability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int limit) {
        return ResponseEntity.ok(ApiResponse.ok("Product profitability",
                reportService.productProfitability(from(from), to(to), limit)));
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    @GetMapping("/inventory-valuation")
    @PreAuthorize("hasAuthority('INVENTORY_VALUE_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inventoryValuation(
            @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok("Inventory valuation",
                reportService.inventoryValuation(warehouseId)));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyAuthority('STOCK_MOVEMENT_READ','INVENTORY_READ','INVENTORY_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> movements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.ok("Product movement history",
                reportService.movementHistory(from(from), to(to), productId, warehouseId, type)));
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAnyAuthority('INVENTORY_READ','STOCK_MOVEMENT_READ','INVENTORY_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transfers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse transfers",
                reportService.transfers(from(from), to(to))));
    }

    @GetMapping("/shrinkage")
    @PreAuthorize("hasAnyAuthority('INVENTORY_REPORT_VIEW','LOW_STOCK_REPORT_VIEW','REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> shrinkage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Stock adjustments, damage and expiry",
                reportService.shrinkage(from(from), to(to))));
    }

    @GetMapping("/slow-moving")
    @PreAuthorize("hasAnyAuthority('INVENTORY_REPORT_VIEW','LOW_STOCK_REPORT_VIEW','REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> slowMoving(
            @RequestParam(defaultValue = "60") int lookbackDays,
            @RequestParam(required = false) java.math.BigDecimal minimumValue) {
        return ResponseEntity.ok(ApiResponse.ok("Slow-moving inventory",
                reportService.slowMoving(Math.max(7, Math.min(lookbackDays, 365)), minimumValue)));
    }

    // ── Balances ──────────────────────────────────────────────────────────────

    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('RECEIVABLE_READ')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> receivables(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.ok("Customer receivables",
                reportService.receivables(asOf != null ? asOf : LocalDate.now())));
    }

    @GetMapping("/payables")
    @PreAuthorize("hasAuthority('PAYABLE_READ')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> payables(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.ok("Supplier payables",
                reportService.payables(asOf != null ? asOf : LocalDate.now())));
    }

    // ── Exports ───────────────────────────────────────────────────────────────

    /**
     * CSV or PDF for any of the reports above.
     *
     * <p>One endpoint rather than one per report: the export path is identical
     * for all of them and a per-report endpoint would be a dozen copies of the
     * same method with a different report name. The report is resolved from a
     * parameter that is matched against a fixed list — never interpolated into
     * anything.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> export(
            @RequestParam String report,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long warehouseId) {

        Map<String, Object> data = build(report, from, to, warehouseId);

        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) data.get("columns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.getOrDefault("rows", List.of());

        // A report that is not a table (a statement, a memo) cannot be rendered
        // as one. Say so instead of handing a null column list to the exporter,
        // which used to fail with a NullPointerException — a 500 for what is a
        // request the caller could have been told how to fix.
        if (columns == null) {
            throw new IllegalArgumentException(
                    "Report '" + report + "' has no exportable table.");
        }

        // Cap what a single export may contain. A report over a wide date range
        // can return every movement in the business, and rendering a hundred
        // thousand rows into a PDF holds the whole thing in memory on the
        // request thread. Refusing with a clear instruction beats a timeout or an
        // out-of-memory kill, which look like the server being broken.
        //
        // The cap is reported rather than silently applied: a truncated export
        // that looks complete is worse than no export at all, because a reader
        // would total it and get the wrong answer.
        int cap = exportService.getMaxRows();
        if (rows.size() > cap) {
            throw new IllegalArgumentException(
                    "This report matched " + rows.size() + " rows, which is over the "
                            + "export limit of " + cap + ". Narrow the date range or add a "
                            + "filter, or export in parts. Nothing has been silently "
                            + "truncated.");
        }

        String title = String.valueOf(data.getOrDefault("report", report));
        LocalDateTime now = LocalDateTime.now();

        byte[] body;
        MediaType mediaType;
        String extension;
        if ("pdf".equalsIgnoreCase(format)) {
            body = exportService.toPdf(title, now, columns, rows);
            mediaType = MediaType.APPLICATION_PDF;
            extension = "pdf";
        } else if ("csv".equalsIgnoreCase(format)) {
            body = exportService.toCsv(title, now, columns, rows);
            mediaType = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
            extension = "csv";
        } else {
            throw new IllegalArgumentException(
                    "Unsupported export format '" + format + "'. Use 'csv' or 'pdf'.");
        }

        String filename = report.replaceAll("[^a-zA-Z0-9_-]", "-") + "-"
                + LocalDate.now() + "." + extension;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(body);
    }

    private Map<String, Object> build(String report, LocalDate f, LocalDate t, Long warehouseId) {
        return switch (report.toLowerCase(java.util.Locale.ROOT)) {
            case "sales" -> reportService.salesAndProfit(from(f), to(t), warehouseId, null, null);
            case "purchases" -> reportService.purchases(from(f), to(t), null, null);
            case "cash-flow" -> reportService.cashFlow(from(f), to(t));
            case "profit-loss" -> asTable(reportService.profitAndLoss(from(f), to(t)));
            case "product-profitability" -> reportService.productProfitability(from(f), to(t), 0);
            case "inventory-valuation" -> reportService.inventoryValuation(warehouseId);
            case "movements" -> reportService.movementHistory(from(f), to(t), null, warehouseId, null);
            case "transfers" -> reportService.transfers(from(f), to(t));
            case "shrinkage" -> reportService.shrinkage(from(f), to(t));
            case "slow-moving" -> reportService.slowMoving(60, null);
            case "receivables" -> reportService.receivables(t != null ? t : LocalDate.now());
            case "payables" -> reportService.payables(t != null ? t : LocalDate.now());
            default -> throw new IllegalArgumentException("Unknown report '" + report
                    + "'. Available: sales, purchases, cash-flow, profit-loss, "
                    + "product-profitability, inventory-valuation, movements, transfers, "
                    + "shrinkage, slow-moving, receivables, payables.");
        };
    }

    /**
     * Projects a statement-shaped report into the columns/rows form the exporter
     * renders, leaving the figures untouched.
     *
     * <p>The profit-and-loss report is a statement — a list of labelled lines,
     * not a grid — because that is how a reader wants it on screen. An export is
     * necessarily a grid, so only this copy is reshaped; the report the screen
     * shows, and the figures in it, are exactly the same objects.
     */
    private static Map<String, Object> asTable(Map<String, Object> report) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> statement =
                (List<Map<String, Object>>) report.getOrDefault("statement", List.of());
        Map<String, Object> shaped = new java.util.LinkedHashMap<>(report);
        shaped.put("columns", List.of("line", "amount", "previous"));
        shaped.put("rows", statement);
        return shaped;
    }

    /**
     * The default window when a caller sends no dates.
     *
     * <p>The last 30 days, stated rather than guessed: an unqualified "sales
     * report" with no period would otherwise mean all time, which on a growing
     * business is a number nobody can use.
     */
    private static LocalDate from(LocalDate requested) {
        return requested != null ? requested : LocalDate.now().minusDays(29);
    }

    private static LocalDate to(LocalDate requested) {
        return requested != null ? requested : LocalDate.now();
    }
}