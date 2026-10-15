package com.inventory.backend.repository;

import com.inventory.backend.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Page<StockMovement> findByProductId(Long productId, Pageable pageable);
    Page<StockMovement> findByWarehouseId(Long warehouseId, Pageable pageable);

    /** The approval queue. */
    Page<StockMovement> findByStatusOrderByIdAsc(String status, Pageable pageable);

    /**
     * Movement history for a product, newest first.
     *
     * <p>Only APPROVED rows: a pending adjustment is a request, and reporting it
     * alongside posted movements makes stock on hand unreconstructable — the sum
     * of the ledger would not equal the quantity.
     */
    @Query("""
            SELECT m FROM StockMovement m
            LEFT JOIN FETCH m.warehouse
            LEFT JOIN FETCH m.destinationWarehouse
            WHERE m.product.id = :productId
              AND m.status = 'APPROVED'
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    Page<StockMovement> findApprovedHistoryForProduct(
            @Param("productId") Long productId, Pageable pageable);

    /**
     * Approved movements in a period — the input to shrinkage and loss reporting.
     *
     * <p>Only APPROVED rows, for the same reason as above.
     */
    @Query("""
            SELECT m FROM StockMovement m
            LEFT JOIN FETCH m.product
            LEFT JOIN FETCH m.warehouse
            WHERE m.status = 'APPROVED'
              AND m.createdAt >= :from
              AND m.createdAt <= :to
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<StockMovement> findApprovedBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Loss-type movements (DAMAGE, EXPIRY, LOSS) for the shrinkage report. */
    @Query("""
            SELECT m FROM StockMovement m
            LEFT JOIN FETCH m.product
            LEFT JOIN FETCH m.warehouse
            WHERE m.status = 'APPROVED'
              AND m.type IN ('DAMAGE', 'EXPIRY', 'LOSS')
              AND m.createdAt >= :from AND m.createdAt <= :to
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<StockMovement> findLossesBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Movements for one warehouse in a period, including transfer legs. */
    @Query("""
            SELECT m FROM StockMovement m
            LEFT JOIN FETCH m.product
            LEFT JOIN FETCH m.warehouse
            LEFT JOIN FETCH m.destinationWarehouse
            WHERE m.status = 'APPROVED'
              AND m.createdAt >= :from AND m.createdAt <= :to
              AND (m.warehouse.id = :warehouseId OR m.destinationWarehouse.id = :warehouseId)
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<StockMovement> findForWarehouseBetween(
            @Param("warehouseId") Long warehouseId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Unit count withdrawn by type in a period, for loss and turnover reporting. */
    @Query("""
            SELECT m.type, COUNT(m), COALESCE(SUM(ABS(m.quantity)), 0),
                   COALESCE(SUM(ABS(m.quantity) * COALESCE(m.unitCost, 0)), 0)
            FROM StockMovement m
            WHERE m.status = 'APPROVED'
              AND m.createdAt >= :from AND m.createdAt <= :to
            GROUP BY m.type
            """)
    List<Object[]> summariseByTypeBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Movements tagged with an idempotency key that has already been posted. */
    @Query("SELECT COUNT(m) FROM StockMovement m WHERE m.movementGroup = :group")
    long countByMovementGroup(@Param("group") String group);

    @Query("SELECT m FROM StockMovement m WHERE m.movementGroup = :group ORDER BY m.id ASC")
    List<StockMovement> findByMovementGroup(@Param("group") String group);

    @Query("""
            SELECT COUNT(m) FROM StockMovement m
            WHERE m.product.id = :productId AND m.type = :type
              AND m.reference = :reference AND m.quantity = :quantity
            """)
    long countMatching(
            @Param("productId") Long productId,
            @Param("type") String type,
            @Param("reference") String reference,
            @Param("quantity") Integer quantity);

    @Query("""
            SELECT m FROM StockMovement m
            WHERE (CAST(:from AS java.time.LocalDateTime) IS NULL OR m.createdAt >= :from)
              AND (CAST(:to AS java.time.LocalDateTime) IS NULL OR m.createdAt <= :to)
              AND (CAST(:warehouseId AS java.lang.Long) IS NULL OR m.warehouse.id = :warehouseId OR m.destinationWarehouse.id = :warehouseId)
              AND (CAST(:type AS java.lang.String) IS NULL OR m.type = :type)
              AND (CAST(:status AS java.lang.String) IS NULL OR m.status = :status)
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    Page<StockMovement> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("warehouseId") Long warehouseId,
            @Param("type") String type,
            @Param("status") String status,
            Pageable pageable);
}