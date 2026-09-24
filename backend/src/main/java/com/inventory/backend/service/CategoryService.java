package com.inventory.backend.service;

import com.inventory.backend.dto.CategoryDTO;
import com.inventory.backend.dto.CreateCategoryRequest;
import com.inventory.backend.dto.UpdateCategoryRequest;
import com.inventory.backend.model.Category;
import com.inventory.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryDTO> listAllCategories() {
        return categoryRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<CategoryDTO> listRootCategories() {
        return categoryRepository.findByParentIsNull().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public CategoryDTO getCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        return toDTO(category);
    }

    public CategoryDTO createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Category with this name already exists");
        }
        
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            category.setParent(parent);
        }
        
        return toDTO(categoryRepository.save(category));
    }

    public CategoryDTO updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
                
        if (request.getName() != null) {
            if (!category.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName())) {
                throw new IllegalArgumentException("Category with this name already exists");
            }
            category.setName(request.getName());
        }
        
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new IllegalArgumentException("Category cannot be its own parent");
            }
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            category.setParent(parent);
        } else {
            category.setParent(null); // Allows clearing parent
        }
        
        return toDTO(categoryRepository.save(category));
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (!category.getSubCategories().isEmpty()) {
            throw new IllegalArgumentException("Cannot delete category with subcategories");
        }
        // TODO: check if products are attached to this category
        categoryRepository.delete(category);
    }

    private CategoryDTO toDTO(Category category) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        if (category.getParent() != null) {
            dto.setParentId(category.getParent().getId());
        }
        if (category.getSubCategories() != null) {
            dto.setSubCategories(category.getSubCategories().stream().map(sub -> {
                CategoryDTO subDto = new CategoryDTO();
                subDto.setId(sub.getId());
                subDto.setName(sub.getName());
                subDto.setDescription(sub.getDescription());
                subDto.setParentId(category.getId());
                return subDto;
            }).collect(Collectors.toList()));
        }
        return dto;
    }
}
