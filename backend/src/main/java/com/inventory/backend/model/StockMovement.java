package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_moves_prod_wh_stat", columnList = "product_id, warehouse_id, status"),
        @Index(name = "idx_stock_moves_group", columnList = "movement_group")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /**
     * The receiving warehouse of a transfer. Null for every other movement type.
     *
     * <p>A transfer is written as <b>two</b> ledger rows sharing a
     * {@link #movementGroup}: an outgoing leg from {@code warehouse} and an
     * incoming leg into {@code destinationWarehouse}. One row cannot describe
     * both, and a single row is what let a transfer look like a sale in the
     * movement history.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_id")
    private Warehouse destinationWarehouse;

    @Column(nullable = false)
    private String type; // e.g. IN, OUT, TRANSFER, ADJUSTMENT

    @Column(nullable = false)
    private Integer quantity; // Positive for IN, Negative for OUT

    private String reference; // E.g., PO-1234, SO-9876

    /**
     * Why the movement happened, in business words ("Goods damaged in transit",
     * "Supplier over-delivered").
     *
     * <p>Separate from {@link #notes} because the anomaly model reads this
     * column: a reason that is missing on a large ADJUSTMENT is itself a signal.
     */
    @Column(name = "reason", length = 255)
    private String reason;

    /**
     * Idempotency / correlation key.
     *
     * <p>Both legs of a transfer carry the same value, and re-posting a movement
     * with a value already present is refused by
     * {@code StockLedgerService}. {@code UNIQUE} is deliberately <b>not</b>
     * declared at the column level: {@code ddl-auto=update} cannot add a unique
     * constraint to a populated table, so a constraint declared here would be
     * silently skipped on an existing database and give a false sense of
     * protection. The check is enforced in the service, where it also works for
     * legacy rows.
     */
    @Column(name = "movement_group", length = 80)
    private String movementGroup;

    /**
     * Unit cost of the units moved, snapshotted at posting time.
     *
     * <p>This is what makes the movement ledger an accounting record rather than
     * a quantity log: the cost of stock that has left can be reconstructed long
     * after the product's purchase price has changed. Null on rows written
     * before this existed, and on movements that carry no cost (an approval
     * request that has not been accepted yet).
     */
    @Column(name = "unit_cost", precision = 15, scale = 4)
    private java.math.BigDecimal unitCost;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Approval state.
     *
     * <p>Movements that change stock on their own authority (IN, OUT, TRANSFER)
     * are written APPROVED, so existing rows and existing behaviour are
     * unchanged.
     *
     * <p>ADJUSTMENT and STOCK_COUNT are the two a human must sign off: a stock
     * count is a claim by whoever counted the shelf, not proof of what is on it.
     * Those are written PENDING and the product quantity is left untouched until
     * an approver accepts them.
     *
     * <p>A String rather than an enum so ddl-auto=update adds a plain VARCHAR
     * without an ordinal conversion on an existing table.
     *
     * <p><b>Must stay nullable.</b> This column was added to a table that
     * already held rows, and {@code ddl-auto=update} emits
     * {@code ALTER TABLE stock_movements ADD COLUMN status varchar(16) NOT NULL}.
     * On a populated table PostgreSQL rejects that with "column status contains
     * null values" — and Hibernate only logs the failure and carries on booting,
     * so the service comes up healthy with the column missing and every query
     * that touches it fails at runtime. Nullable lets the ALTER succeed;
     * {@code StockMovementStatusBackfill} then fills the legacy rows in.
     */
    @Column(length = 16)
    @Builder.Default
    private String status = Status.APPROVED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    private LocalDateTime approvedAt;

    private String reviewNote;

    /** Approval states for a movement that alters quantity on hand. */
    public static final class Status {
        /** Written by someone who already holds the approval permission. */
        public static final String APPROVED = "APPROVED";
        /** Requested; quantity on hand is unchanged until approved. */
        public static final String PENDING = "PENDING";
        /** Refused; quantity on hand is unchanged. */
        public static final String REJECTED = "REJECTED";

        private Status() {
        }
    }

    /** Movement types a supervisor or manager must approve before they count. */
    public static boolean requiresApproval(String type) {
        return TYPE_ADJUSTMENT.equals(type) || TYPE_STOCK_COUNT.equals(type);
    }

    public static final String TYPE_ADJUSTMENT = "ADJUSTMENT";
    public static final String TYPE_STOCK_COUNT = "STOCK_COUNT";

    /** Recorded automatically when a product is created with opening stock. */
    public static final String TYPE_OPENING = "OPENING";
    /**
     * Goods arriving.
     *
     * <p>Aliases "RECEIPT"/"RECEIVE" are accepted by the service for callers that
     * use those words; the constant names the canonical spelling.
     */
    public static final String TYPE_IN = "IN";
    /** Goods leaving stock for a reason other than a sale. Alias: "ISSUE". */
    public static final String TYPE_OUT = "OUT";
    /** Moving stock between warehouses. */
    public static final String TYPE_TRANSFER = "TRANSFER";

    // ── Precise movement types ───────────────────────────────────────────────
    // The three types above predate multi-warehouse support and are kept so that
    // existing rows and existing filters keep working. New code writes the
    // specific type below, because a receipt that says only "IN" cannot be
    // separated from a return, and a withdrawal that says only "OUT" cannot be
    // separated from a sale. Without that separation the loss, damage and
    // shrinkage figures in the financial report are simply unavailable.

    /** Goods arriving against a purchase order. */
    public static final String TYPE_PURCHASE_RECEIPT = "PURCHASE_RECEIPT";
    /** Goods leaving because a customer bought them. */
    public static final String TYPE_SALE = "SALE";
    /** Outgoing leg of a warehouse transfer. */
    public static final String TYPE_TRANSFER_OUT = "TRANSFER_OUT";
    /** Incoming leg of a warehouse transfer. */
    public static final String TYPE_TRANSFER_IN = "TRANSFER_IN";
    /** Goods coming back from a customer. */
    public static final String TYPE_SALES_RETURN = "SALES_RETURN";
    /** Goods going back to a supplier. */
    public static final String TYPE_PURCHASE_RETURN = "PURCHASE_RETURN";
    /** Stock written off as damaged. */
    public static final String TYPE_DAMAGE = "DAMAGE";
    /** Stock written off because it passed its expiry date. */
    public static final String TYPE_EXPIRY = "EXPIRY";
    /** Stock lost to theft, spillage or an unaccounted difference. */
    public static final String TYPE_LOSS = "LOSS";

    /** Types that increase stock. */
    public static boolean isInbound(String type) {
        return TYPE_OPENING.equals(type)
                || TYPE_IN.equals(type)
                || TYPE_PURCHASE_RECEIPT.equals(type)
                || TYPE_TRANSFER_IN.equals(type)
                || TYPE_SALES_RETURN.equals(type)
                || "RECEIPT".equals(type)
                || "RECEIVE".equals(type);
    }

    /** Types that decrease stock. */
    public static boolean isOutbound(String type) {
        return TYPE_OUT.equals(type)
                || TYPE_SALE.equals(type)
                || TYPE_TRANSFER_OUT.equals(type)
                || TYPE_PURCHASE_RETURN.equals(type)
                || TYPE_DAMAGE.equals(type)
                || TYPE_EXPIRY.equals(type)
                || TYPE_LOSS.equals(type)
                || "ISSUE".equals(type);
    }

    /**
     * Types whose loss is shrinkage the business has to absorb.
     *
     * <p>A sale is not shrinkage — it is the cost of goods sold. A transfer out
     * is not either — the stock still exists, at another warehouse. These three
     * are: goods that left and will not come back.
     */
    public static boolean isLoss(String type) {
        return TYPE_DAMAGE.equals(type)
                || TYPE_EXPIRY.equals(type)
                || TYPE_LOSS.equals(type);
    }

    /** Every movement type the ledger understands, for validation and docs. */
    public static final java.util.List<String> ALL_TYPES = java.util.List.of(
            TYPE_OPENING, TYPE_IN, TYPE_PURCHASE_RECEIPT, TYPE_SALE,
            TYPE_OUT, TYPE_TRANSFER_OUT, TYPE_TRANSFER_IN, TYPE_TRANSFER,
            TYPE_SALES_RETURN, TYPE_PURCHASE_RETURN,
            TYPE_DAMAGE, TYPE_EXPIRY, TYPE_LOSS,
            TYPE_ADJUSTMENT, TYPE_STOCK_COUNT);

    public boolean isPending() {
        return Status.PENDING.equals(status);
    }
}
