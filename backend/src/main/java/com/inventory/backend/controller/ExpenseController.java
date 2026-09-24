package com.inventory.backend.controller;
import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.Expense;
import com.inventory.backend.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseRepository repo;
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Expense>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(repo.findAll(pageable)));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<Expense>> create(@RequestBody Expense expense) {
        if (expense.getReference() == null) expense.setReference("EXP-" + System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.ok(repo.save(expense)));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}
