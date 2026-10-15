package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A cost of running the business that is not the cost of the goods sold.
 *
 * <p>An expense only reaches the profit and loss statement once it has been
 * approved. Previously {@code status} defaulted to {@code PENDING} on the entity
 * and the controller then wrote {@code APPROVED} only when the field was
 * absent — so a caller that sent {@code status:"PENDING"} created a pending
 * expense that was still counted as an operating cost by the frontend report.
 * Approval is now an explicit transition with an approver recorded.
 */
@Entity
@Table(name = "expenses",
        indexes = @Index(name = "idx_expenses_transaction_date", columnList = "transaction_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable voucher number. */
    private String reference;

    /** RENT, UTILITIES, SALARIES, TRANSPORT, MARKETING, TAX, OTHER. */
    private String category;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private String description;

    /** PENDING, APPROVED or REJECTED. Only APPROVED is an operating cost. */
    @Builder.Default
    private String status = "PENDING";

    /**
     * When the cost is attributed for reporting. Separate from
     * {@link #createdAt} so a bill covering last month can be recorded this
     * month and still land in the right period.
     */
    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    private String notes;

    /** Username of whoever recorded it. Retained for existing screens. */
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    private LocalDateTime approvedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** True when this expense counts as an operating cost in the period. */
    @Transient
    public boolean isApproved() {
        return STATUS_APPROVED.equals(status);
    }

    @Transient
    public LocalDateTime getEffectiveTransactionDate() {
        return transactionDate != null ? transactionDate : createdAt;
    }
}