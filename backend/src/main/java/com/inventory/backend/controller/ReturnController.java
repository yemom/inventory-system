package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.ReturnOrder;
import com.inventory.backend.service.ReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Customer returns and supplier returns.
 *
 * <p>Both used to live in the browser's {@code localStorage}, which meant they
 * existed on one device, appeared in no report and left the stock ledger holding
 * an unexplained adjustment. They are documents here, subject to approval, and
 * every one of them leaves a trail in the movement ledger and the cash figures.
 */
@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @GetMapping
    @PreAuthorize("hasAuthority('RETURN_READ')")
    public ResponseEntity<ApiResponse<Page<ReturnOrder>>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Returns retrieved",
                returnService.search(type, status, pageable(page, size))));
    }

    /** Returns awaiting a decision. */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('RETURN_APPROVE')")
    public ResponseEntity<ApiResponse<Page<ReturnOrder>>> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Returns awaiting approval",
                returnService.pending(pageable(page, size))));
    }

    /**
     * Raises a return of goods from a customer against an existing sale.
     *
     * <p>The quantities are capped at what is left of each sale line, so the same
     * unit cannot be returned twice.
     */
    @PostMapping("/sales/{saleOrderId}")
    @PreAuthorize("hasAuthority('RETURN_CREATE')")
    public ResponseEntity<ApiResponse<ReturnOrder>> salesReturn(
            @PathVariable Long saleOrderId,
            @RequestBody Map<String, Object> body) {
        ReturnOrder created = returnService.createSalesReturn(
                saleOrderId, lines(body), text(body, "reason"), text(body, "notes"));
        return ResponseEntity.ok(ApiResponse.ok(
                "Sales return recorded (awaiting approval)", created));
    }

    /** Raises a return of goods back to a supplier. */
    @PostMapping("/purchases/{purchaseOrderId}")
    @PreAuthorize("hasAuthority('RETURN_CREATE')")
    public ResponseEntity<ApiResponse<ReturnOrder>> purchaseReturn(
            @PathVariable Long purchaseOrderId,
            @RequestBody Map<String, Object> body) {
        ReturnOrder created = returnService.createPurchaseReturn(
                purchaseOrderId, lines(body), text(body, "reason"), text(body, "notes"));
        return ResponseEntity.ok(ApiResponse.ok(
                "Purchase return recorded (awaiting approval)", created));
    }

    /**
     * Accepts a return.
     *
     * <p>Stock moves and the financial reversal is posted here, not when the
     * return was raised: goods being accepted back is a judgement about whether
     * they are resellable, and an unreviewed return should not have changed any
     * balances.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('RETURN_APPROVE')")
    public ResponseEntity<ApiResponse<ReturnOrder>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Return approved",
                returnService.approve(id, body == null ? null : body.get("note"))));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('RETURN_APPROVE')")
    public ResponseEntity<ApiResponse<ReturnOrder>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Return rejected",
                returnService.reject(id, body == null ? null : body.get("note"))));
    }

    @SuppressWarnings("unchecked")
    private static List<ReturnService.LineRequest> lines(Map<String, Object> body) {
        Object raw = body.get("lines");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(
                    "A return needs a 'lines' array of {itemId, quantity}.");
        }
        List<ReturnService.LineRequest> lines = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Each return line must be an object.");
            }
            Object itemId = map.get("itemId");
            Object quantity = map.get("quantity");
            if (itemId == null || quantity == null) {
                throw new IllegalArgumentException(
                        "Each return line needs both 'itemId' and 'quantity'.");
            }
            lines.add(new ReturnService.LineRequest(
                    Long.valueOf(String.valueOf(itemId)),
                    (int) Double.parseDouble(String.valueOf(quantity))));
        }
        return lines;
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }
}