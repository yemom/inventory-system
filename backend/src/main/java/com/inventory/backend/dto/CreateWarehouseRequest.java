package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWarehouseRequest {
    @NotBlank(message = "Name is required")
    private String name;
    private String location;
    private String managerName;
    private String contactPhone;
}
