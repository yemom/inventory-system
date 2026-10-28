package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Immutable financial snapshot once the parent period is finalized. */
@Entity
@Table(name = "payroll_items", uniqueConstraints = @UniqueConstraint(name = "uk_payroll_item_period_user", columnNames = {"payroll_period_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payroll_period_id", nullable = false) private PayrollPeriod payrollPeriod;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(precision = 15, scale = 2) private BigDecimal basicSalary;
    @Column(precision = 15, scale = 2) private BigDecimal taxableIncome;
    @Column(precision = 15, scale = 2) private BigDecimal incomeTax;
    @Column(precision = 15, scale = 2) private BigDecimal employeePension;
    @Column(precision = 15, scale = 2) private BigDecimal employerPension;
    @Column(precision = 15, scale = 2) private BigDecimal otherDeductions;
    @Column(precision = 15, scale = 2) private BigDecimal netPay;
    @Column(length = 4000) private String calculationBreakdown;
}
