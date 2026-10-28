package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_periods", uniqueConstraints = @UniqueConstraint(name = "uk_payroll_period_month", columnNames = {"period_year", "period_month"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollPeriod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "period_year", nullable = false) private int year;
    @Column(name = "period_month", nullable = false) private int month;
    @Column(nullable = false) @Builder.Default private String status = "DRAFT"; // DRAFT, FINALIZED, PAID
    @Column(precision = 15, scale = 2) @Builder.Default private BigDecimal grossPayroll = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2) @Builder.Default private BigDecimal netPayroll = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2) @Builder.Default private BigDecimal employmentTax = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2) @Builder.Default private BigDecimal employeePension = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2) @Builder.Default private BigDecimal employerPension = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2) @Builder.Default private BigDecimal employerCost = BigDecimal.ZERO;
    private String ruleVersion;
    private LocalDateTime finalizedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "finalized_by_id") private User finalizedBy;
    private LocalDateTime paidAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "paid_by_id") private User paidBy;
}
