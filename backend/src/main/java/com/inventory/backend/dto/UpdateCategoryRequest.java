package com.inventory.backend.dto;

import lombok.Data;

@Data
public class UpdateCategoryRequest {
    private String name;
    private String description;
    private Long parentId;
}
