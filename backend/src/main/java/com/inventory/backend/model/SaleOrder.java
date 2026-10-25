package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "sale_orders", indexes = {@Index(name = "idx_sale_orders_report", columnList = "transaction_date, status")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    private String customerName;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal discount;

    @Column(precision = 15, scale = 2)
    private BigDecimal tax;

    @Column(precision = 15, scale = 2)
    private BigDecimal finalAmount;

    private String status; // PAID, PENDING, CANCELLED
    private String paymentStatus; // PAID, PARTIAL, UNPAID
    private String paymentMethod; // CASH, CARD, TRANSFER, BANK, MOBILE_MONEY, CREDIT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /**
     * The warehouse the goods were taken from.
     *
     * <p>Previously a sale had nowhere to name a location, so stock was deducted
     * from {@code products.quantity} — a single business-wide figure — and no
     * warehouse could say what it had sold. Nullable so that rows created before
     * warehouses existed, and sales through a till that has not chosen a
     * location, remain readable; the service falls back to the single warehouse
     * when exactly one exists.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /**
     * Cash actually received against this sale, summed from completed payments.
     *
     * <p>This is what distinguishes "sold on credit" from "sold and paid": an
     * invoice that has not been paid is revenue but it is not cash, and treating
     * it as cash is the single most common way a dashboard overstates a business.
     */
    @Column(name = "amount_paid", precision = 15, scale = 2)
    private BigDecimal amountPaid;

    /** Business date the sale is reported under. Defaults to {@link #createdAt}. */
    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    /** ISO 4217 code. ETB throughout; stored so a future multi-currency row is explicit. */
    @Column(name = "currency", length = 3)
    private String currency = "ETB";

    /** Cost of the goods on this order, snapshotted when the sale was written. */
    @Column(name = "cost_of_goods_sold", precision = 15, scale = 2)
    private BigDecimal costOfGoodsSold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(unique = true)
    private String idempotencyKey;

    @OneToMany(mappedBy = "saleOrder", cascade = CascadeType.ALL)
    private List<SaleOrderItem> items;


    // ── Void / refund approval ──────────────────────────────────────────────
    // A cashier may need to undo a sale but may not do so unilaterally: the goods
    // are off the shelf and the money may be banked. They raise a request and a
    // Supervisor or Manager decides. Null means "no request raised", which is also
    // the state of every row written before this existed.

    /** PENDING, APPROVED or REJECTED; null when no void was requested. */
    @Column(length = 16)
    private String voidStatus;

    private String voidReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "void_requested_by_id")
    private User voidRequestedBy;

    private LocalDateTime voidRequestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "void_approved_by_id")
    private User voidApprovedBy;

    private LocalDateTime voidApprovedAt;

    private String voidReviewNote;

    /** PENDING, APPROVED or REJECTED; null when no refund was requested. */
    @Column(length = 16)
    private String refundStatus;

    private String refundReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_requested_by_id")
    private User refundRequestedBy;

    private LocalDateTime refundRequestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_approved_by_id")
    private User refundApprovedBy;

    private LocalDateTime refundApprovedAt;

    private String refundReviewNote;
    @CreationTimestamp
    private LocalDateTime createdAt;
}
