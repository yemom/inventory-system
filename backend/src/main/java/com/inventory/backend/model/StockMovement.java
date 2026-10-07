package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
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

    @Column(nullable = false)
    private String type; // e.g. IN, OUT, TRANSFER, ADJUSTMENT

    @Column(nullable = false)
    private Integer quantity; // Positive for IN, Negative for OUT

    private String reference; // E.g., PO-1234, SO-9876
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

    public boolean isPending() {
        return Status.PENDING.equals(status);
    }
}
