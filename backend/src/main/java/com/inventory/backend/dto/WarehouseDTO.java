package com.inventory.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WarehouseDTO {
    private Long id;
    private String name;
    private String location;
    private String managerName;
    private String contactPhone;
    private boolean active;
    private LocalDateTime createdAt;
}
