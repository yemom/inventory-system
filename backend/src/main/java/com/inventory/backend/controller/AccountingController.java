package com.inventory.backend.controller;

import com.inventory.backend.dto.AccountingDTO.*;
import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.User;
import com.inventory.backend.security.CustomUserDetails;
import com.inventory.backend.service.AccountingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService accountingService;

    // ─── Chart of Accounts ───────────────────────────────────────────────────────

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> listAccounts() {
        return ResponseEntity.ok(ApiResponse.ok("Chart of accounts", accountingService.listAccounts()));
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('ACCOUNTING_JOURNAL_CREATE')")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(@Valid @RequestBody CreateAccountRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.ok("Account created", accountingService.createAccount(req)));
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_JOURNAL_CREATE')")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Account updated", accountingService.updateAccount(id, req)));
    }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_JOURNAL_CREATE')")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        accountingService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.ok("Account deleted", null));
    }

    // ─── Journal Entries ─────────────────────────────────────────────────────────

    @GetMapping("/journal-entries")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<List<JournalEntryResponse>>> listJournalEntries(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Journal entries", accountingService.listJournalEntries(status, period, from, to)));
    }

    @GetMapping("/journal-entries/{id}")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'FINANCIAL_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> getJournalEntry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Journal entry details", accountingService.getJournalEntry(id)));
    }

    @PostMapping("/journal-entries")
    @PreAuthorize("hasAuthority('ACCOUNTING_JOURNAL_CREATE')")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> createJournalEntry(
            @Valid @RequestBody CreateJournalEntryRequest req,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails != null ? userDetails.getUser() : null;
        return ResponseEntity.status(201).body(ApiResponse.ok("Draft journal entry created", accountingService.createJournalEntry(req, user)));
    }

    @PostMapping("/journal-entries/{id}/post")
    @PreAuthorize("hasAuthority('ACCOUNTING_JOURNAL_POST')")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> postJournalEntry(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails != null ? userDetails.getUser() : null;
        return ResponseEntity.ok(ApiResponse.ok("Journal entry posted to ledger", accountingService.postJournalEntry(id, user)));
    }

    @PostMapping("/journal-entries/{id}/reverse")
    @PreAuthorize("hasAuthority('ACCOUNTING_JOURNAL_REVERSE')")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> reverseJournalEntry(
            @PathVariable Long id,
            @Valid @RequestBody ReverseJournalRequest req,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails != null ? userDetails.getUser() : null;
        return ResponseEntity.ok(ApiResponse.ok("Journal entry reversed", accountingService.reverseJournalEntry(id, req.reason(), user)));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ACCOUNTING_JOURNAL_POST')")
    public ResponseEntity<ApiResponse<Integer>> syncOperationalTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails != null ? userDetails.getUser() : null;
        int count = accountingService.syncOperationalTransactions(user);
        return ResponseEntity.ok(ApiResponse.ok("Synchronized operational transactions into journal", count));
    }

    // ─── Financial Statements & Reports ──────────────────────────────────────────

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'ACCOUNTING_REPORTS_VIEW')")
    public ResponseEntity<ApiResponse<GeneralLedgerReport>> getGeneralLedger(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("General ledger", accountingService.getGeneralLedger(accountId, from, to)));
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'ACCOUNTING_REPORTS_VIEW')")
    public ResponseEntity<ApiResponse<TrialBalanceReport>> getTrialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.ok("Trial balance", accountingService.getTrialBalance(asOf)));
    }

    @GetMapping("/profit-loss")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'ACCOUNTING_REPORTS_VIEW', 'PROFIT_LOSS_READ')")
    public ResponseEntity<ApiResponse<ProfitLossReport>> getProfitLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Profit and loss statement", accountingService.getProfitLoss(from, to)));
    }

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'ACCOUNTING_REPORTS_VIEW')")
    public ResponseEntity<ApiResponse<BalanceSheetReport>> getBalanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.ok("Balance sheet", accountingService.getBalanceSheet(asOf)));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'ACCOUNTING_RECONCILE')")
    public ResponseEntity<ApiResponse<ReconciliationReport>> getReconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.ok("Reconciliation report", accountingService.getReconciliation(asOf)));
    }

    @GetMapping("/ai-insights")
    @PreAuthorize("hasAnyAuthority('ACCOUNTING_VIEW', 'ACCOUNTING_REPORTS_VIEW')")
    public ResponseEntity<ApiResponse<AiAccountingInsight>> getAiInsights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("AI Accounting Insights", accountingService.getAiAccountingInsights(from, to)));
    }
}
