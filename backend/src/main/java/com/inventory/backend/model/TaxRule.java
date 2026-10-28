package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Effective-dated, reviewed tax configuration. Never infer a statutory rule in UI code. */
@Entity
@Table(name = "tax_rules", uniqueConstraints = @UniqueConstraint(name = "uk_tax_rule_version", columnNames = {"code", "effective_from"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String name;
    /** VAT, EMPLOYMENT_INCOME_TAX, EMPLOYEE_PENSION, EMPLOYER_PENSION, WITHHOLDING. */
    @Column(nullable = false) private String type;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal rate;
    @Column(nullable = false) private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @Column(nullable = false) @Builder.Default private String verificationStatus = "ACCOUNTANT_REVIEW_REQUIRED";
    @Column(length = 1000) private String sourceUrl;
    @Column(length = 4000) private String notes;
    @Column(nullable = false) @Builder.Default private boolean active = true;
}
