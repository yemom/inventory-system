package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.dto.WarehouseDTO;
import com.inventory.backend.dto.CreateWarehouseRequest;
import com.inventory.backend.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<ApiResponse<List<WarehouseDTO>>> listWarehouses() {
        return ResponseEntity.ok(ApiResponse.ok("Warehouses retrieved successfully", warehouseService.listWarehouses()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_CREATE')")
    public ResponseEntity<ApiResponse<WarehouseDTO>> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse created successfully", warehouseService.createWarehouse(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<WarehouseDTO>> updateWarehouse(@PathVariable Long id, @Valid @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse updated successfully", warehouseService.updateWarehouse(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.ok(ApiResponse.ok("Warehouse deleted successfully", null));
    }
}
