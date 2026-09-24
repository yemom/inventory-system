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
    @PreAuthorize("hasAnyAuthority('INVENTORY_ADJUST', 'INVENTORY_TRANSFER', 'INVENTORY_RECEIVE')")
    public ResponseEntity<ApiResponse<StockMovementDTO>> recordMovement(@Valid @RequestBody CreateStockMovementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Movement recorded successfully", stockMovementService.recordMovement(request)));
    }
}
