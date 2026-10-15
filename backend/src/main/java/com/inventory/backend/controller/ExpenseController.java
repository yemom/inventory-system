package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.Expense;
import com.inventory.backend.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * Operating expenses.
 *
 * <p>An expense is always created PENDING. The previous controller wrote
 * APPROVED whenever the caller omitted the status, so a client could create an
 * expense that counted as an operating cost while the record said it had not been
 * approved. Approval is now an explicit, attributed action under
 * {@code EXPENSE_APPROVE}, and an approved expense can be rejected rather than
 * deleted so the period stays reproducible from its records.
 */
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final FinanceService financeService;

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public ResponseEntity<ApiResponse<Page<Expense>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Expenses retrieved",
                financeService.listExpenses(
                        from == null ? null : from.atStartOfDay(),
                        to == null ? null : to.atTime(LocalTime.MAX),
                        status, category, pageable(page, size))));
    }

    /** Expenses awaiting approval. */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ResponseEntity<ApiResponse<Page<Expense>>> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Expenses awaiting approval",
                financeService.pendingExpenses(pageable(page, size))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public ResponseEntity<ApiResponse<Expense>> create(@RequestBody Map<String, Object> body) {
        Expense expense = financeService.recordExpense(
                amount(body),
                text(body, "category"),
                text(body, "description"),
                transactionDate(body),
                text(body, "notes"));
        return ResponseEntity.ok(ApiResponse.ok("Expense recorded (awaiting approval)", expense));
    }

    /** Accepts an expense so it becomes an operating cost in its period. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ResponseEntity<ApiResponse<Expense>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Expense approved",
                financeService.approveExpense(id, body == null ? null : body.get("note"))));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ResponseEntity<ApiResponse<Expense>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Expense rejected",
                financeService.rejectExpense(id, body == null ? null : body.get("note"))));
    }

    /**
     * Deletes an expense that has not been approved.
     *
     * <p>An approved expense cannot be deleted — reject it instead. Deleting a
     * cost that a closed report already included would make that report
     * unreproducible, which is the one thing a financial system must never do.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        financeService.deleteExpense(id);
        return ResponseEntity.ok(ApiResponse.ok("Expense deleted", null));
    }

    private static BigDecimal amount(Map<String, Object> body) {
        Object value = body.get("amount");
        if (value == null) {
            throw new IllegalArgumentException("An expense amount is required.");
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expense amount '" + value + "' is not a number.");
        }
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * The date the cost belongs to, which may differ from the day it was typed in.
     *
     * <p>A bill covering last month, entered this morning, belongs to last
     * month. Reading the creation timestamp instead would put it in the wrong
     * period and make the comparison with the prior month meaningless.
     */
    private static LocalDateTime transactionDate(Map<String, Object> body) {
        Object value = body.get("transactionDate");
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay();
            }
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "transactionDate must be an ISO date or date-time, got '" + text + "'.");
        }
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }
}