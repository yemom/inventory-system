package com.inventory.backend.controller;
import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.Payment;
import com.inventory.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentRepository repo;

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<ApiResponse<Page<Payment>>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Payments retrieved", repo.findAll(pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<ApiResponse<Payment>> create(@RequestBody Payment p) {
        if (p.getReference() == null) p.setReference("PAY-" + System.currentTimeMillis());
        if (p.getStatus() == null) p.setStatus("COMPLETED");
        return ResponseEntity.ok(ApiResponse.ok("Payment recorded", repo.save(p)));
    }
}
