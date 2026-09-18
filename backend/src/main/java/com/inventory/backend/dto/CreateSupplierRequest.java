package com.inventory.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class CreateSupplierRequest {
    @NotBlank private String companyName;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String taxNumber;
    private String paymentTerms;
}
