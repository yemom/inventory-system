package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A purchase order.
 *
 * <p>The distinction this class exists to hold is between <b>ordered</b> and
 * <b>received</b>. Previously a single {@code status} tried to carry both, which
 * made "goods ordered but not yet delivered" and "goods received but not yet
 * paid" the same row, and allowed stock to be added simply by raising an order.
 * Stock is now increased only by {@code receivePurchase}, and only by the
 * quantity actually received.
 */
@Entity
@Table(name = "purchase_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    /**
     * Free-text supplier name, kept for compatibility with existing rows and
     * screens. {@link #supplier} is the real link; when it is set the name is
     * derived from it so the two cannot disagree.
     */
    private String supplierName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /**
     * Where received goods are put away. Stock only increases when receiving
     * names a warehouse, so the per-warehouse split is never guessed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /**
     * DRAFT, ORDERED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED or RETURNED.
     * Legacy rows only ever hold RECEIVED, DRAFT or CANCELLED.
     */
    private String status;

    /**
     * Payment status independent of stock-receipt status.
     * Values: PAID, PARTIAL, UNPAID.
     * A purchase can be RECEIVED (goods arrived) but UNPAID (bought on credit).
     */
    @Column(name = "payment_status", length = 20)
    private String paymentStatus; // PAID, PARTIAL, UNPAID

    /**
     * Amount already paid against this purchase order.
     * Null or zero means nothing has been paid yet.
     */
    @Column(name = "amount_paid", precision = 12, scale = 2)
    private BigDecimal amountPaid;

    /** Net terms agreed with the supplier, e.g. "NET 30". */
    @Column(name = "payment_terms", length = 60)
    private String paymentTerms;

    /** Date payment falls due, derived from the transaction date and the terms. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    /** Tax charged on the order, at order level. */
    @Column(name = "tax", precision = 12, scale = 2)
    private BigDecimal tax;

    /** Discount agreed with the supplier, at order level. */
    @Column(name = "discount", precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(name = "currency", length = 3)
    private String currency = "ETB";

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL)
    private List<PurchaseOrderItem> items;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    /** Business date the order is reported under. Defaults to {@link #createdAt}. */
    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    private LocalDateTime approvedAt;

    // ── Status vocabulary ─────────────────────────────────────────────────────
    public static final String STATUS_DRAFT = "DRAFT";
    /** Raised and accepted by a supervisor; goods not yet delivered. */
    public static final String STATUS_ORDERED = "ORDERED";
    /** Some of the goods have arrived; the rest is still outstanding. */
    public static final String STATUS_PARTIALLY_RECEIVED = "PARTIALLY_RECEIVED";
    /** All ordered goods have arrived and stock has been increased. */
    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    /** Goods were sent back to the supplier. */
    public static final String STATUS_RETURNED = "RETURNED";

    /** Outstanding balance owed to the supplier on this order. */
    @Transient
    public BigDecimal getBalanceDue() {
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        BigDecimal paid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        BigDecimal due = total.subtract(paid);
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }

    /** Whether stock has already been added for this order. */
    @Transient
    public boolean isFullyReceived() {
        return STATUS_RECEIVED.equals(status);
    }
}