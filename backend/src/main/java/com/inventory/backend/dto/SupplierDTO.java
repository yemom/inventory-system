package com.inventory.backend.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class SupplierDTO {
    private Long id;
    private String supplierNumber;
    private String companyName;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String taxNumber;
    private String paymentTerms;
    private BigDecimal outstandingBalance;
    private String status;
    private LocalDateTime createdAt;
}
