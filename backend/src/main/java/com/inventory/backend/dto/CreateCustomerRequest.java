package com.inventory.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class CreateCustomerRequest {
    @NotBlank private String name;
    private String phone;
    private String email;
    private String address;
    private String customerType;
    private BigDecimal creditLimit;
}
