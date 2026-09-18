package com.inventory.backend.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class CustomerDTO {
    private Long id;
    private String customerNumber;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String customerType;
    private BigDecimal creditLimit;
    private BigDecimal outstandingBalance;
    private String status;
    private LocalDateTime createdAt;
}
