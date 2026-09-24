package com.inventory.backend.controller;
import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.Payment;
import com.inventory.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentRepository repo;
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Payment>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(repo.findAll(pageable)));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<Payment>> create(@RequestBody Payment p) {
        if (p.getReference() == null) p.setReference("PAY-" + System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.ok(repo.save(p)));
    }
}
