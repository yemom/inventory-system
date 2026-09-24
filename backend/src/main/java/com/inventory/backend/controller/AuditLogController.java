package com.inventory.backend.controller;
import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.AuditLog;
import com.inventory.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogRepository repo;
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLog>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(repo.findAll(pageable)));
    }
}
