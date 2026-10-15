package com.inventory.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Goods coming back — from a customer, or back to a supplier.
 *
 * <p>Sales returns and purchase returns were recorded in the browser's
 * {@code localStorage} and never reached the database, so they vanished with the
 * browser, were invisible to every report, and the stock movement they caused
 * was a bare ADJUSTMENT with no way to tell a customer return from a shrinkage.
 *
 * <p>A return is a document here for the same reason a sale is: it has to be
 * reviewable, it has to be able to reverse revenue or a payable, and it has to
 * leave a trail in the ledger.
 */
@Entity
@Table(name = "return_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReturnOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String returnNumber;

    /** SALES_RETURN (from a customer) or PURCHASE_RETURN (to a supplier). */
    @Column(name = "return_type", nullable = false, length = 20)
    private String returnType;

    /** The order being returned against, e.g. SO-000123. */
    @Column(name = "original_reference")
    private String originalReference;

    /** Customer or supplier name, denormalised for display and search. */
    @Column(name = "party_name")
    private String partyName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Where the goods physically went, for a sales return. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /** Net total of the returned goods, excluding tax. */
    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** PENDING, APPROVED or REJECTED. Only APPROVED touches stock or money. */
    @Column(name = "status", length = 16)
    private String status;

    /** Why: DAMAGED, WRONG_ITEM, EXPIRED, OVER_DELIVERED, CUSTOMER_DECISION. */
    private String reason;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** Whether the refund/payable reversal was posted. */
    @Column(name = "financial_settled", nullable = false)
    private Boolean financialSettled = false;

    /** Ignored on serialisation: the cycle back through the items is unbounded. */
    @JsonIgnore
    @OneToMany(mappedBy = "returnOrder", cascade = CascadeType.ALL)
    private List<ReturnOrderItem> items;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    private LocalDateTime approvedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public static final String TYPE_SALES_RETURN = "SALES_RETURN";
    public static final String TYPE_PURCHASE_RETURN = "PURCHASE_RETURN";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Transient
    public boolean isSalesReturn() {
        return TYPE_SALES_RETURN.equals(returnType);
    }
}