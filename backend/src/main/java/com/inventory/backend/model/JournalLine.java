package com.inventory.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "journal_lines", indexes = {
        @Index(name = "idx_journal_lines_entry", columnList = "journal_entry_id"),
        @Index(name = "idx_journal_lines_account", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    @JsonBackReference
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(name = "line_number", nullable = false)
    private Integer lineNumber = 1;
}
