package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * How much of one product is held in one warehouse.
 *
 * <p>This table is the authority for <em>where</em> stock is. {@link Product#getQuantity()}
 * remains the business-wide total because it predates multi-warehouse support and
 * every existing screen and query reads it; it is kept equal to
 * {@code SUM(warehouse_stock.quantity)} by {@code WarehouseStockService}. Nothing
 * outside that service is allowed to write it, which is what stops the two numbers
 * from drifting apart.
 *
 * <p>A row is created lazily on first movement rather than when the product is
 * created: a product that has never moved has no meaningful per-warehouse split,
 * and inserting a row of zero for every warehouse would be a lot of noise about
 * nothing. Rows are keyed by {@code (product_id, warehouse_id)}.
 */
@Entity
@Table(name = "warehouse_stock",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_warehouse_stock_product_warehouse",
                        columnNames = {"product_id", "warehouse_id"})
        },
        indexes = {
                @Index(name = "idx_warehouse_stock_product", columnList = "product_id"),
                @Index(name = "idx_warehouse_stock_warehouse", columnList = "warehouse_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /**
     * Units held in this warehouse. Never negative — the service refuses a
     * withdrawal that would go below zero rather than clamping it, so a
     * discrepancy has to be recorded as an explicit adjustment.
     */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Moving weighted-average unit cost of the units held here.
     *
     * <p>Kept per warehouse so a transfer carries its cost with it and a
     * warehouse that trades at a different margin still values its own stock
     * correctly. Recomputed on every receipt; see {@code CostingService}.
     */
    @Column(name = "average_cost", precision = 15, scale = 4)
    private java.math.BigDecimal averageCost;

    @Column(name = "last_movement_at")
    private LocalDateTime lastMovementAt;
}