package com.inventory.backend.repository;

import com.inventory.backend.model.WarehouseStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    Optional<WarehouseStock> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    /**
     * The balance implied by the movement ledger, per warehouse.
     *
     * <p>Rows are {@code (warehouseId, quantity)}; a null warehouse id means the
     * movements predate warehouse attribution and the caller decides where they
     * belong. Only APPROVED movements count — a pending or rejected movement is
     * not a fact about stock.
     *
     * <p>Aggregated in SQL rather than by loading the movements, because this
     * runs across the whole catalogue and the ledger is the largest table here.
     */
    @Query("""
            SELECT m.warehouse.id, SUM(m.quantity)
            FROM StockMovement m
            WHERE m.product.id = :productId
              AND m.status = com.inventory.backend.model.StockMovement$Status.APPROVED
            GROUP BY m.warehouse.id
            """)
    List<Object[]> ledgerBalanceForProduct(@Param("productId") Long productId);

    /**
     * Takes a row lock before reading, which is what makes a concurrent sale and
     * a concurrent delivery of the same product safe: the second transaction
     * blocks until the first has committed and then reads the updated quantity,
     * instead of both reading the old value and each writing their own delta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ws FROM WarehouseStock ws
            WHERE ws.product.id = :productId AND ws.warehouse.id = :warehouseId
            """)
    Optional<WarehouseStock> findForUpdate(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId);

    List<WarehouseStock> findByProductId(Long productId);

    List<WarehouseStock> findByWarehouseId(Long warehouseId);

    /**
     * Positive quantity only.
     *
     * <p>A row can only reach a negative quantity through a direct database
     * write, but the valuation reads this and must not price phantom stock.
     */
    @Query("SELECT ws FROM WarehouseStock ws WHERE ws.quantity > 0")
    List<WarehouseStock> findAllWithStock();

    @Query("SELECT COALESCE(SUM(ws.quantity), 0) FROM WarehouseStock ws WHERE ws.product.id = :productId")
    int totalQuantityForProduct(@Param("productId") Long productId);

    /**
     * Total stock for a product, counting only active warehouses, used to decide
     * whether a per-warehouse split exists yet.
     */
    @Query("SELECT COALESCE(SUM(ws.quantity), 0) FROM WarehouseStock ws "
            + "WHERE ws.product.id = :productId AND ws.warehouse.active = true")
    int totalQuantityForActiveWarehouses(@Param("productId") Long productId);

    void deleteByProductId(Long productId);
}