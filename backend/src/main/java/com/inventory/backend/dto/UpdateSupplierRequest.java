package com.inventory.backend.dto;

import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class UpdateSupplierRequest {
    private String companyName;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String taxNumber;
    private String paymentTerms;
    private String status;
}
