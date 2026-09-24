package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.dto.CreateSaleOrderRequest;
import com.inventory.backend.dto.SaleOrderDTO;
import com.inventory.backend.service.SaleOrderService;
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
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleOrderController {

    private final SaleOrderService saleOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('SALE_READ')")
    public ResponseEntity<ApiResponse<Page<SaleOrderDTO>>> listSales(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(saleOrderService.listSales(pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> createSale(@Valid @RequestBody CreateSaleOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Sale recorded successfully", saleOrderService.createSale(request)));
    }
}