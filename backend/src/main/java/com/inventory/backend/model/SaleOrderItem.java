package com.inventory.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
 * Jackson breaks the cycle here rather than at the point of serialisation.
 *
 * <p>{@code SaleOrder.items → SaleOrderItem.saleOrder → SaleOrder.items} has no
 * end: any endpoint returning an entity graph reaches the nesting limit and
 * throws, rather than producing a document. Ignoring the back-reference leaves
 * the parent reachable from the child and the child reachable from the parent,
 * which is everything a client actually needs.
 */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_order_id", nullable = false)
    private SaleOrder saleOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Integer quantity;

    @Column(precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal discount;

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    /**
     * Unit cost of the goods at the moment they were sold.
     *
     * <p>This is the fix for the historical-COGS problem. Reporting
     * {@code product.purchasePrice × quantity} treats the price the business
     * happens to pay today as the cost of goods sold last quarter, so every sale
     * from before the last price change is restated the day the price moves.
     * Snapshotting the weighted-average cost on the line makes the figure for a
     * closed period permanent.
     *
     * <p>Null on rows created before this column existed; those rows fall back
     * to the product's current cost and are flagged as legacy in the report.
     */
    @Column(name = "unit_cost", precision = 15, scale = 4)
    private BigDecimal unitCost;

    /**
     * Units of this line already returned by the customer. Guards over-returns.
     *
     * <p>The SQL {@code default 0} is not decoration — it is what makes this
     * column migratable. Declared as {@code nullable = false} alone, Hibernate's
     * schema update emits {@code add column quantity_returned integer not null},
     * which PostgreSQL refuses on a table that already holds rows ("column …
     * contains null values"). Hibernate logs that failure as a warning, starts
     * anyway, and every query that reads sale lines then fails on the missing
     * column — the defect this annotation's default is here to prevent.
     */
    @Column(name = "quantity_returned", nullable = false,
            columnDefinition = "integer default 0")
    private Integer quantityReturned = 0;
}
