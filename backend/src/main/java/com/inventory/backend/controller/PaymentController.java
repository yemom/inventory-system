package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.Payment;
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
import java.util.Map;

/**
 * Money in and money out.
 *
 * <p>The previous version accepted a {@code Payment} entity straight from the
 * request body. That let a client decide its own status, direction and amount
 * without any of them being checked against a document, which is precisely how an
 * unpaid invoice ends up reported as collected cash. Every write here goes
 * through {@link FinanceService}, which derives all of those from validated state
 * and refuses amounts that do not make sense.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final FinanceService financeService;

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<ApiResponse<Page<Payment>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String partyType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Payments retrieved",
                financeService.listPayments(startOf(from), endOf(to), direction, partyType,
                        status, pageOf(page, size))));
    }

    /**
     * Records money received from a customer against a sale.
     *
     * <p>Partial payments are ordinary: the sale's amount paid and payment status
     * are updated from the payment ledger, so "sold but not yet paid" is a state
     * the system knows rather than a label somebody typed.
     */
    @PostMapping("/sales/{saleOrderId}")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<ApiResponse<Payment>> recordForSale(
            @PathVariable Long saleOrderId,
            @RequestBody Map<String, Object> body) {
        Payment payment = financeService.recordSalePayment(
                saleOrderId,
                amount(body),
                string(body, "method"),
                string(body, "reference"),
                string(body, "notes"));
        return ResponseEntity.ok(ApiResponse.ok("Payment recorded", payment));
    }

    /** Records money paid to a supplier against a purchase order. */
    @PostMapping("/purchases/{purchaseOrderId}")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<ApiResponse<Payment>> recordForPurchase(
            @PathVariable Long purchaseOrderId,
            @RequestBody Map<String, Object> body) {
        Payment payment = financeService.recordSupplierPayment(
                purchaseOrderId,
                amount(body),
                string(body, "method"),
                string(body, "reference"),
                string(body, "notes"));
        return ResponseEntity.ok(ApiResponse.ok("Supplier payment recorded", payment));
    }

    /**
     * Records a refund paid back to a customer.
     *
     * <p>A separate endpoint because a refund is money leaving the business
     * because a sale was undone, which is not the same event as paying a
     * supplier and must not be filed under one.
     */
    @PostMapping("/refunds")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<ApiResponse<Payment>> refund(@RequestBody Map<String, Object> body) {
        Object saleId = body.get("saleOrderId");
        Payment payment = financeService.recordRefund(
                saleId == null ? null : Long.valueOf(String.valueOf(saleId)),
                amount(body),
                string(body, "method"),
                string(body, "notes"));
        return ResponseEntity.ok(ApiResponse.ok("Refund recorded", payment));
    }

    private static BigDecimal amount(Map<String, Object> body) {
        Object value = body.get("amount");
        if (value == null) {
            throw new IllegalArgumentException("A payment amount is required.");
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
            throw new IllegalArgumentException("Payment amount '" + value + "' is not a number.");
        }
    }

    private static String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private static LocalDateTime endOf(LocalDate date) {
        return date == null ? null : date.atTime(LocalTime.MAX);
    }

    private static Pageable pageOf(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }

    /** LocalTime, imported here to keep the static helpers above readable. */
    private static final class LocalTime {
        static final java.time.LocalTime MAX = java.time.LocalTime.MAX;
    }
}