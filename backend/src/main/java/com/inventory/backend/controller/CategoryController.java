package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.dto.CategoryDTO;
import com.inventory.backend.dto.CreateCategoryRequest;
import com.inventory.backend.dto.UpdateCategoryRequest;
import com.inventory.backend.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> listAllCategories() {
        return ResponseEntity.ok(ApiResponse.ok("Categories retrieved successfully", categoryService.listAllCategories()));
    }

    @GetMapping("/roots")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> listRootCategories() {
        return ResponseEntity.ok(ApiResponse.ok("Root categories retrieved successfully", categoryService.listRootCategories()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ResponseEntity<ApiResponse<CategoryDTO>> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Category retrieved successfully", categoryService.getCategory(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_CREATE')")
    public ResponseEntity<ApiResponse<CategoryDTO>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Category created successfully", categoryService.createCategory(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<CategoryDTO>> updateCategory(@PathVariable Long id, @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Category updated successfully", categoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok("Category deleted successfully", null));
    }
}
