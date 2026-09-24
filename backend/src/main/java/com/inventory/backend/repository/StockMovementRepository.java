package com.inventory.backend.repository;

import com.inventory.backend.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Page<StockMovement> findByProductId(Long productId, Pageable pageable);
    Page<StockMovement> findByWarehouseId(Long warehouseId, Pageable pageable);
}
