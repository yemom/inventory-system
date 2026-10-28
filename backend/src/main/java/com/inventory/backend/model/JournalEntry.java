package com.inventory.backend.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_entries", indexes = {
        @Index(name = "idx_journal_entries_date", columnList = "entry_date"),
        @Index(name = "idx_journal_entries_period", columnList = "accounting_period"),
        @Index(name = "idx_journal_entries_status", columnList = "status"),
        @Index(name = "idx_journal_entries_source", columnList = "source_type, source_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_number", nullable = false, unique = true, length = 100)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "accounting_period", nullable = false, length = 20)
    private String accountingPeriod;

    @Column(length = 255)
    private String reference;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** MANUAL, SALE, PURCHASE, EXPENSE, PAYMENT, PAYROLL, TAX_SETTLEMENT, INVENTORY_ADJUSTMENT */
    @Builder.Default
    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType = "MANUAL";

    @Column(name = "source_id", length = 100)
    private String sourceId;

    /** DRAFT, POSTED, REVERSED */
    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    @Builder.Default
    @Column(name = "total_debit", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_credit", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by_id")
    private User postedBy;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_by_id")
    private User reversedBy;

    @Column(name = "reversal_entry_id")
    private Long reversalEntryId;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Builder.Default
    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<JournalLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addLine(JournalLine line) {
        lines.add(line);
        line.setJournalEntry(this);
    }
}
