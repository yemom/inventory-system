package com.inventory.backend.service;

import com.inventory.backend.dto.CategoryDTO;
import com.inventory.backend.dto.CreateCategoryRequest;
import com.inventory.backend.dto.UpdateCategoryRequest;
import com.inventory.backend.model.Category;
import com.inventory.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * ==========================================================
     * GET ALL CATEGORIES
     * ==========================================================
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> listAllCategories() {

        return categoryRepository
                .findAll()
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        Category::getName,
                                        String.CASE_INSENSITIVE_ORDER))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * ==========================================================
     * GET ROOT CATEGORIES
     * ==========================================================
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> listRootCategories() {

        return categoryRepository
                .findByParentIsNull()
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        Category::getName,
                                        String.CASE_INSENSITIVE_ORDER))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * ==========================================================
     * GET CATEGORY BY ID
     * ==========================================================
     */
    @Transactional(readOnly = true)
    public CategoryDTO getCategory(Long id) {

        Category category = categoryRepository
                .findById(id)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Category not found"));

        return toDTO(category);
    }

    /**
     * ==========================================================
     * CREATE CATEGORY
     * ==========================================================
     */
    public CategoryDTO createCategory(
            CreateCategoryRequest request) {

        String name = request.getName() == null
                ? ""
                : request.getName().trim();

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Category name is required");
        }

        /**
         * Prevent duplicate category names.
         */
        if (categoryRepository.existsByName(name)) {

            throw new IllegalArgumentException(
                    "Category with this name already exists");
        }

        Category category = new Category();

        category.setName(name);

        if (request.getDescription() != null) {

            category.setDescription(
                    request.getDescription().trim());
        }

        /**
         * Handle optional parent category.
         */
        if (request.getParentId() != null) {

            Category parent = categoryRepository
                    .findById(
                            request.getParentId())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Parent category not found"));

            category.setParent(parent);
        }

        /**
         * Save to PostgreSQL.
         */
        Category saved = categoryRepository.save(category);

        /**
         * Flush immediately so the generated ID and
         * database state are available before returning.
         */
        categoryRepository.flush();

        return toDTO(saved);
    }

    /**
     * ==========================================================
     * UPDATE CATEGORY
     * ==========================================================
     */
    public CategoryDTO updateCategory(
            Long id,
            UpdateCategoryRequest request) {

        Category category = categoryRepository
                .findById(id)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Category not found"));

        /**
         * Update name.
         */
        if (request.getName() != null) {

            String newName = request.getName().trim();

            if (newName.isBlank()) {

                throw new IllegalArgumentException(
                        "Category name cannot be empty");
            }

            if (!category
                    .getName()
                    .equalsIgnoreCase(newName)
                    &&
                    categoryRepository
                            .existsByName(newName)) {

                throw new IllegalArgumentException(
                        "Category with this name already exists");
            }

            category.setName(newName);
        }

        /**
         * Update description.
         */
        if (request.getDescription() != null) {

            category.setDescription(
                    request.getDescription().trim());
        }

        /**
         * Update parent.
         */
        if (request.getParentId() != null) {

            if (request
                    .getParentId()
                    .equals(id)) {

                throw new IllegalArgumentException(
                        "Category cannot be its own parent");
            }

            Category parent = categoryRepository
                    .findById(
                            request.getParentId())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Parent category not found"));

            category.setParent(parent);

        } else {

            /**
             * Null means top-level category.
             */
            category.setParent(null);
        }

        Category saved = categoryRepository.save(category);

        categoryRepository.flush();

        return toDTO(saved);
    }

    /**
     * ==========================================================
     * DELETE CATEGORY
     * ==========================================================
     */
    public void deleteCategory(Long id) {

        Category category = categoryRepository
                .findById(id)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Category not found"));

        /**
         * Do not delete categories that have children.
         */
        if (category.getSubCategories() != null
                &&
                !category
                        .getSubCategories()
                        .isEmpty()) {

            throw new IllegalArgumentException(
                    "Cannot delete category with subcategories");
        }

        categoryRepository.delete(category);

        categoryRepository.flush();
    }

    /**
     * ==========================================================
     * ENTITY -> DTO
     * ==========================================================
     */
    private CategoryDTO toDTO(
            Category category) {

        CategoryDTO dto = new CategoryDTO();

        dto.setId(
                category.getId());

        dto.setName(
                category.getName());

        dto.setDescription(
                category.getDescription());

        /**
         * Parent ID.
         */
        if (category.getParent() != null) {

            dto.setParentId(
                    category
                            .getParent()
                            .getId());
        } else {

            dto.setParentId(null);
        }

        /**
         * Child categories.
         */
        if (category.getSubCategories() != null) {

            dto.setSubCategories(

                    category
                            .getSubCategories()
                            .stream()

                            .sorted(
                                    Comparator.comparing(
                                            Category::getName,
                                            String.CASE_INSENSITIVE_ORDER))

                            .map(
                                    subCategory -> {

                                        CategoryDTO subDto = new CategoryDTO();

                                        subDto.setId(
                                                subCategory
                                                        .getId());

                                        subDto.setName(
                                                subCategory
                                                        .getName());

                                        subDto.setDescription(
                                                subCategory
                                                        .getDescription());

                                        subDto.setParentId(
                                                category
                                                        .getId());

                                        return subDto;
                                    })

                            .collect(
                                    Collectors.toList()));

        } else {

            dto.setSubCategories(
                    List.of());
        }

        return dto;
    }
}