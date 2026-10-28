package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_receipts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String receiptName;
    private String filePath;
    private String fileType;
    @Column(precision = 15, scale = 2) private BigDecimal detectedAmount;
    @Column(precision = 15, scale = 2) private BigDecimal taxableAmount;
    @Column(precision = 15, scale = 2) private BigDecimal taxAmount;
    @Column(precision = 15, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false) @Builder.Default private String treatment = "STANDARD";
    @Column(nullable = false) @Builder.Default private boolean taxInclusive = false;
    @Column(nullable = false) @Builder.Default private String currency = "ETB";
    @Column(nullable = false) @Builder.Default private boolean aiExtracted = false;
    @Column(precision = 5, scale = 4) private BigDecimal aiConfidence;
    @Column(length = 5000) private String aiRawResponse;
    private String vendorName;
    private LocalDate receiptDate;
    @Column(length = 2000) private String notes;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "accountant_id") private User accountant;
    @Column(nullable = false) @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default private LocalDateTime updatedAt = LocalDateTime.now();
}
