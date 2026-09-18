package com.inventory.backend.controller;

import com.inventory.backend.dto.*;
import com.inventory.backend.service.CustomerService;
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
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public ResponseEntity<ApiResponse<Page<CustomerDTO>>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.listCustomers(search, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    public ResponseEntity<ApiResponse<CustomerDTO>> create(@Valid @RequestBody CreateCustomerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Customer created", service.createCustomer(req)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public ResponseEntity<ApiResponse<CustomerDTO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getCustomer(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    public ResponseEntity<ApiResponse<CustomerDTO>> update(@PathVariable Long id, @RequestBody UpdateCustomerRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Customer updated", service.updateCustomer(id, req)));
    }
}
