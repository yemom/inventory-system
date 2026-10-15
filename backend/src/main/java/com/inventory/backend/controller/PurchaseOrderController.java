package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.dto.CreatePurchaseOrderRequest;
import com.inventory.backend.dto.PurchaseOrderDTO;
import com.inventory.backend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_READ')")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderDTO>>> listPurchases(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseOrderService.listPurchases(pageable)));
    }

    /**
     * Raises a purchase order.
     *
     * <p>Does not add stock. Raising an order is proposing one; goods appear only
     * through {@link #receive}, which needs {@code PURCHASE_APPROVE}. The previous
     * behaviour received the goods inside this method whenever the caller held
     * that permission, which made "create" and "receive" one action with two
     * names and let anyone who could raise an order inflate stock.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> createPurchase(
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Purchase order raised", purchaseOrderService.createPurchase(request)));
    }

    /** Receives a purchase order in full, increasing stock. */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('PURCHASE_APPROVE')")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> receive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Purchase order received",
                purchaseOrderService.receivePurchase(id)));
    }

    /**
     * Receives part of an order.
     *
     * <p>A supplier delivering half an order is ordinary. Forcing all-or-nothing
     * is what makes recorded stock disagree with the delivery note.
     */
    @PostMapping("/{id}/receive-partial")
    @PreAuthorize("hasAuthority('PURCHASE_APPROVE')")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> receivePartial(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> quantitiesByLineId) {
        return ResponseEntity.ok(ApiResponse.ok("Purchase order partially received",
                purchaseOrderService.receivePartial(id, byLineId(quantitiesByLineId))));
    }

    /**
     * JSON object keys are strings; the line ids are numbers.
     *
     * <p>Converted here rather than with a generic signature so a caller sending
     * {@code {"lines": ...}} by mistake gets a clear error instead of every line
     * silently failing to match.
     */
    private static Map<Long, Integer> byLineId(Map<String, Integer> body) {
        Map<Long, Integer> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : body.entrySet()) {
            try {
                result.put(Long.parseLong(entry.getKey()), entry.getValue());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "'" + entry.getKey() + "' is not a purchase line id.");
            }
        }
        return result;
    }

    /**
     * Cancels an order.
     *
     * <p>Refused once goods have arrived: cancelling a received order would leave
     * stock with no document explaining where it came from. The right instrument
     * there is a purchase return.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CANCEL')")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Purchase order cancelled",
                purchaseOrderService.cancel(id, body == null ? null : body.get("reason"))));
    }

    /** Orders with goods still outstanding. */
    @GetMapping("/outstanding")
    @PreAuthorize("hasAuthority('PURCHASE_READ')")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderDTO>>> outstanding(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Purchase orders not fully received",
                purchaseOrderService.listOutstanding(
                        PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100))))));
    }
}