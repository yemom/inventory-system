package com.inventory.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A movement of money.
 *
 * <p>This class used to be a free-form note with {@code type} of "IN"/"OUT" and
 * an {@code amount}, written by whichever screen happened to be open. Cash flow
 * therefore could not be computed: an unpaid invoice created a PENDING payment
 * row indistinguishable from a real one, and a refund was written as an OUT with
 * no link back to the sale it reversed.
 *
 * <p>It now records enough to answer the questions the financial report asks:
 * which way the money moved, which document caused it, who it was to or from,
 * when the business considers it to have moved (see {@link #transactionDate}
 * versus {@link #createdAt}), and whether it actually happened.
 */
@Entity
@Table(name = "payments",
        indexes = {
                @Index(name = "idx_payments_direction", columnList = "direction"),
                @Index(name = "idx_payments_transaction_date", columnList = "transaction_date"),
                @Index(name = "idx_payments_sale_order", columnList = "sale_order_id"),
                @Index(name = "idx_payments_purchase_order", columnList = "purchase_order_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable voucher number, unique per business. */
    private String reference;

    /** PO- or SO- the money settles. Retained for existing screens and filters. */
    private String orderReference;

    /** IN or OUT. Retained for existing rows; {@link #direction} is authoritative. */
    private String type;

    /**
     * Which way the money moved.
     *
     * <p>IN is money received from a customer (or another inflow); OUT is money
     * paid to a supplier, refunded to a customer, or paid as an expense. Null on
     * rows written before this column existed — {@link #getEffectiveDirection()}
     * falls back to {@link #type} for those.
     */
    @Column(name = "direction", length = 3)
    private String direction;

    /**
     * CUSTOMER, SUPPLIER or OTHER. Decides whether the payment settles a
     * receivable or a payable.
     */
    @Column(name = "party_type", length = 20)
    private String partyType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Ignored on serialisation: a payment references an order that references its items. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_order_id")
    private SaleOrder saleOrder;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    /** CASH, CARD, BANK_TRANSFER, MOBILE_MONEY, CHEQUE, CREDIT. */
    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    /**
     * PENDING, COMPLETED, FAILED or REVERSED.
     *
     * <p>Only COMPLETED money has moved. Counting a PENDING row as cash is how an
     * unpaid invoice gets reported as collected income.
     */
    @Builder.Default
    private String status = "COMPLETED";

    /**
     * When the money is treated as having moved for reporting.
     *
     * <p>Separate from {@link #createdAt} because a payment keyed in on the 2nd
     * for the 31st belongs to the period it is dated for, not the period it was
     * typed in. All financial queries filter on this column.
     */
    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    /**
     * The bank's or telco's own reference for this transaction.
     *
     * <p>Distinct from {@link #reference}, which is our voucher number: the
     * reconciliation question is "does the bank statement contain a line for
     * this?", and that is answered by the bank's identifier.
     */
    @Column(name = "external_reference", length = 120)
    private String externalReference;

    /** Free-text note. Retained as a name because the frontend already reads it. */
    private String notes;

    /** Username of whoever recorded it. Retained for existing screens. */
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdByUser;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Vocabulary ────────────────────────────────────────────────────────────
    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";

    public static final String PARTY_CUSTOMER = "CUSTOMER";
    public static final String PARTY_SUPPLIER = "SUPPLIER";
    public static final String PARTY_OTHER = "OTHER";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REVERSED = "REVERSED";

    /** True only when real money changed hands. */
    @Transient
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /**
     * Direction for this row, whatever the column holds.
     *
     * <p>Legacy rows have a null {@code direction} but a populated {@code type},
     * so reading only the new column would make every historical payment
     * direction-less and silently drop it from cash flow.
     */
    @Transient
    public String getEffectiveDirection() {
        if (direction != null && !direction.isBlank()) {
            return direction;
        }
        if (type != null) {
            String t = type.trim().toUpperCase();
            if (DIRECTION_IN.equals(t) || DIRECTION_OUT.equals(t)) {
                return t;
            }
            // The frontend's finance screen wrote type="made" for money going out.
            if ("MADE".equals(t) || "PAID".equals(t)) {
                return DIRECTION_OUT;
            }
        }
        return DIRECTION_IN;
    }

    /** Business date for period filtering, falling back to the creation time. */
    @Transient
    public LocalDateTime getEffectiveTransactionDate() {
        return transactionDate != null ? transactionDate : createdAt;
    }
}