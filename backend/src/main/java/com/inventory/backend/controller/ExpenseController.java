package com.inventory.backend.controller;
import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.model.Expense;
import com.inventory.backend.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseRepository repo;

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public ResponseEntity<ApiResponse<Page<Expense>>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("Expenses retrieved", repo.findAll(pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public ResponseEntity<ApiResponse<Expense>> create(@RequestBody Expense expense) {
        if (expense.getReference() == null) expense.setReference("EXP-" + System.currentTimeMillis());
        if (expense.getStatus() == null) expense.setStatus("APPROVED");
        return ResponseEntity.ok(ApiResponse.ok("Expense recorded", repo.save(expense)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}
