package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "sale_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    private String customerName;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(precision = 10, scale = 2)
    private BigDecimal finalAmount;

    private String status; // PAID, PENDING, CANCELLED
    private String paymentStatus; // PAID, PARTIAL, UNPAID
    private String paymentMethod; // CASH, CARD, TRANSFER, BANK, MOBILE_MONEY, CREDIT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

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
