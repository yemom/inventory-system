package com.inventory.backend.controller;

import com.inventory.backend.dto.*;
import com.inventory.backend.service.SupplierService;
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
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {
    private final SupplierService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SUPPLIER_READ')")
    public ResponseEntity<ApiResponse<Page<SupplierDTO>>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.listSuppliers(search, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPPLIER_CREATE')")
    public ResponseEntity<ApiResponse<SupplierDTO>> create(@Valid @RequestBody CreateSupplierRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Supplier created", service.createSupplier(req)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_READ')")
    public ResponseEntity<ApiResponse<SupplierDTO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getSupplier(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_UPDATE')")
    public ResponseEntity<ApiResponse<SupplierDTO>> update(@PathVariable Long id, @RequestBody UpdateSupplierRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Supplier updated", service.updateSupplier(id, req)));
    }
}
