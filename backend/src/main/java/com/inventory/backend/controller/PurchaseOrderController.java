package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.dto.CreatePurchaseOrderRequest;
import com.inventory.backend.dto.PurchaseOrderDTO;
import com.inventory.backend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> createPurchase(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Purchase recorded successfully", purchaseOrderService.createPurchase(request)));
    }

    /**
     * Receives a draft purchase order, increasing stock.
     *
     * <p>Split from creation on purpose. Creating an order is proposing one;
     * letting the same request both propose it and make the goods appear on the
     * shelf meant stock could be inflated by anyone who could raise an order, and
     * a caller-supplied {@code status} could even mark one RECEIVED outright.
     */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('PURCHASE_APPROVE')")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> receive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Purchase order received",
                purchaseOrderService.receivePurchase(id)));
    }
}