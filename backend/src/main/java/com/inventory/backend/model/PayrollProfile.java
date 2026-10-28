package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Restricted employment-pay profile linked to, but deliberately separate from, the login user. */
@Entity
@Table(name = "payroll_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal basicSalary;
    @Column(nullable = false) private LocalDate effectiveFrom;
    @Builder.Default private boolean pensionEligible = true;
    @Builder.Default private boolean active = true;
    @Column(length = 200) private String bankAccountMasked;
    @Column(length = 100) private String taxIdentificationNumber;
}
