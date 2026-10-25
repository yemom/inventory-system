package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Units ordered. The ceiling for {@link #quantityReceived}. */
    private Integer quantity;

    /**
     * Units actually delivered and added to stock so far.
     *
     * <p>Nullable so the column can be added to a populated table by
     * {@code ddl-auto=update}; treated as {@code quantity} for legacy rows,
     * which by construction were fully received when they were written.
     */
    @Column(name = "quantity_received")
    private Integer quantityReceived;

    /** Units sent back to the supplier against this line. */
    @Column(name = "quantity_returned")
    private Integer quantityReturned;

    @Column(precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    /** Tax charged on this line. */
    @Column(name = "tax", precision = 15, scale = 2)
    private BigDecimal tax;

    /** Discount agreed on this line. */
    @Column(name = "discount", precision = 15, scale = 2)
    private BigDecimal discount;

    /** Units still to arrive. Never negative. */
    @Transient
    public int getQuantityOutstanding() {
        int ordered = quantity != null ? quantity : 0;
        int received = getEffectiveReceived();
        int returned = quantityReturned != null ? quantityReturned : 0;
        int outstanding = ordered - received - returned;
        return outstanding < 0 ? 0 : outstanding;
    }

    /**
     * Units received, treating a legacy row as fully received.
     *
     * <p>Rows written before {@code quantity_received} existed were created only
     * at the moment stock was added, so a null there means "all of it", not
     * "none of it". Reading it as zero would silently re-open those orders for
     * receiving and let the same goods be received twice.
     */
    @Transient
    public int getEffectiveReceived() {
        if (quantityReceived != null) {
            return quantityReceived;
        }
        return quantity != null ? quantity : 0;
    }

    /** Whether every ordered unit has arrived. */
    @Transient
    public boolean isFullyReceived() {
        return getQuantityOutstanding() == 0;
    }
}