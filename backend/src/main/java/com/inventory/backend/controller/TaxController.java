package com.inventory.backend.controller;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.TaxRule;
import com.inventory.backend.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    // ── VAT summary ───────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('TAX_DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<TaxSummaryDTO>> summary(
            @RequestParam(required = false) String period) {
        final YearMonth month;
        try {
            month = period == null || period.isBlank() ? YearMonth.now() : YearMonth.parse(period);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("period must use YYYY-MM format.");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "Tax estimate calculated from recorded transactions",
                taxService.summary(month)));
    }

    // ── Tax rules CRUD ────────────────────────────────────────────────────────

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('TAX_RULE_VIEW')")
    public ResponseEntity<ApiResponse<List<TaxRule>>> rules() {
        return ResponseEntity.ok(ApiResponse.ok("Tax rules retrieved", taxService.rules()));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('TAX_RULE_MANAGE')")
    public ResponseEntity<ApiResponse<TaxRule>> save(@RequestBody TaxRule rule) {
        return ResponseEntity.ok(ApiResponse.ok("Tax rule saved", taxService.saveRule(rule)));
    }

    @PostMapping("/rules/{id}/verify")
    @PreAuthorize("hasAuthority('TAX_RULE_MANAGE')")
    public ResponseEntity<ApiResponse<TaxRule>> verifyRule(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Tax rule verified by accountant", taxService.verifyRule(id)));
    }

    // ── Contributing Transactions Drilldown ──────────────────────────────────

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('TAX_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<List<TaxTransactionDTO>>> transactions(
            @RequestParam(required = false) String period,
            @RequestParam(required = false, defaultValue = "ALL") String type) {
        YearMonth month = (period == null || period.isBlank())
                ? YearMonth.now() : YearMonth.parse(period);
        return ResponseEntity.ok(ApiResponse.ok(
                "Contributing tax transactions retrieved",
                taxService.transactions(month, type)));
    }

    // ── Receipt tax preview (no persistence) ─────────────────────────────────

    @PostMapping("/calculate-receipt")
    @PreAuthorize("hasAuthority('TAX_DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<TaxCalculationDTO>> calculateReceipt(
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        boolean inclusive = Boolean.parseBoolean(
                String.valueOf(body.getOrDefault("taxInclusive", false)));
        String treatment = String.valueOf(body.getOrDefault("treatment", "STANDARD"));
        String name = (String) body.get("receiptName");
        return ResponseEntity.ok(ApiResponse.ok("Receipt tax estimate calculated",
                taxService.calculateReceipt(amount, treatment, inclusive, name)));
    }

    // ── Receipt upload & storage ──────────────────────────────────────────────

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('TAX_DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<List<ReceiptUploadDTO>>> receipts() {
        return ResponseEntity.ok(ApiResponse.ok("Tax receipts retrieved", taxService.receipts()));
    }

    @PostMapping(value = "/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('TAX_DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<ReceiptUploadDTO>> uploadReceipt(
            @RequestParam("file") MultipartFile file,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "treatment", defaultValue = "STANDARD") String treatment,
            @RequestParam(value = "taxInclusive", defaultValue = "false") boolean taxInclusive,
            @RequestParam(value = "receiptName", required = false) String receiptName,
            @RequestParam(value = "vendorName", required = false) String vendorName,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "receiptDate", required = false) String receiptDate) throws IOException {
        LocalDate date = (receiptDate != null && !receiptDate.isBlank())
                ? LocalDate.parse(receiptDate) : null;
        return ResponseEntity.ok(ApiResponse.ok("Receipt saved with tax calculation",
                taxService.saveReceipt(file, amount, treatment, taxInclusive,
                        receiptName, vendorName, notes, date)));
    }

    @DeleteMapping("/receipts/{id}")
    @PreAuthorize("hasAuthority('TAX_RULE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteReceipt(@PathVariable Long id) {
        taxService.deleteReceipt(id);
        return ResponseEntity.ok(ApiResponse.ok("Receipt deleted", null));
    }
}
