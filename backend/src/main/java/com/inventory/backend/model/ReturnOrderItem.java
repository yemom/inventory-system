package com.inventory.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "return_order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReturnOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_order_id", nullable = false)
    private ReturnOrder returnOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * The sale line being returned against, for a sales return.
     *
     * <p>Necessary rather than merely convenient: the goods that came back are
     * the goods that left, and the line is what says which sale they left in and
     * at what cost. Returning against a sale line also caps the quantity, so the
     * same unit cannot be returned twice.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_order_item_id")
    private SaleOrderItem saleOrderItem;

    /** The purchase line being returned against, for a purchase return. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id")
    private PurchaseOrderItem purchaseOrderItem;

    private Integer quantity;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Unit cost of the goods, snapshotted from the original line.
     *
     * <p>On a sales return this is what puts the cost of those goods back into
     * stock; on a purchase return it is what reduces the average cost. Reading
     * the product's <em>current</em> purchase price here would rewrite the value
     * of goods that left months ago.
     */
    @Column(name = "unit_cost", precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    /** Tax refunded with these goods, if any. */
    @Column(name = "tax", precision = 15, scale = 2)
    private BigDecimal tax;
}