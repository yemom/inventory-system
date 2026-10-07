package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.dto.StockMovementDTO;
import com.inventory.backend.dto.CreateStockMovementRequest;
import com.inventory.backend.service.StockMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory/movements")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockMovementService stockMovementService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INVENTORY_READ', 'STOCK_MOVEMENT_READ')")
    public ResponseEntity<ApiResponse<Page<StockMovementDTO>>> listMovements(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Movements retrieved successfully", stockMovementService.listMovements(pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('INVENTORY_ADJUST', 'INVENTORY_TRANSFER', 'INVENTORY_RECEIVE', 'STOCK_ADJUSTMENT_APPROVE')")
    public ResponseEntity<ApiResponse<StockMovementDTO>> recordMovement(@Valid @RequestBody CreateStockMovementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Movement recorded successfully", stockMovementService.recordMovement(request)));
    }

    /**
     * Approves a pending stock adjustment or stock count.
     *
     * <p>Supervisor and Manager only. Until this succeeds the product's quantity
     * on hand is unchanged — a stock count is a claim by whoever counted, not
     * proof of what is on the shelf.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('STOCK_ADJUSTMENT_APPROVE')")
    public ResponseEntity<ApiResponse<StockMovementDTO>> approve(@PathVariable Long id,
                                                                  @RequestBody(required = false) Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return ResponseEntity.ok(ApiResponse.ok("Stock movement approved",
                stockMovementService.approve(id, note)));
    }

    /** Refuses a pending stock adjustment or stock count. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('STOCK_ADJUSTMENT_APPROVE')")
    public ResponseEntity<ApiResponse<StockMovementDTO>> reject(@PathVariable Long id,
                                                                @RequestBody(required = false) Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return ResponseEntity.ok(ApiResponse.ok("Stock movement rejected",
                stockMovementService.reject(id, note)));
    }

    /** Movements waiting for a Supervisor or Manager to sign them off. */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('STOCK_ADJUSTMENT_APPROVE')")
    public ResponseEntity<ApiResponse<Page<StockMovementDTO>>> pending(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Pending movements retrieved successfully",
                stockMovementService.listPending(pageable)));
    }
}
