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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
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

    /**
     * Moves stock between warehouses.
     *
     * <p>Deducts the source and credits the destination in one transaction, and
     * writes both legs of the move to the ledger. Previously a transfer adjusted
     * the business-wide quantity and never said where the stock went, so no
     * warehouse could report its own turnover and a transfer was indistinguishable
     * from a sale in the movement history.
     */
    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public ResponseEntity<ApiResponse<List<StockMovementDTO>>> transfer(
            @Valid @RequestBody CreateStockMovementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Stock transferred",
                stockMovementService.transfer(request)));
    }

    /** Movement history for one product, straight from the ledger. */
    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyAuthority('INVENTORY_READ', 'STOCK_MOVEMENT_READ')")
    public ResponseEntity<ApiResponse<Page<StockMovementDTO>>> forProduct(
            @PathVariable Long productId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Product movement history",
                stockMovementService.historyForProduct(productId, pageable)));
    }

    /** Filtered ledger search, used by the movement report. */
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('INVENTORY_READ', 'STOCK_MOVEMENT_READ', 'INVENTORY_REPORT_VIEW')")
    public ResponseEntity<ApiResponse<Page<StockMovementDTO>>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Stock movements",
                stockMovementService.search(
                        from == null ? null : from.atStartOfDay(),
                        to == null ? null : to.atTime(java.time.LocalTime.MAX),
                        warehouseId, type, status, pageable)));
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
