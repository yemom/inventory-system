package com.inventory.backend.repository;

import com.inventory.backend.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseOrder p WHERE p.status <> 'CANCELLED'")
    BigDecimal sumTotalAmount();

    /**
     * Fetch all purchase orders with their items and products in a single
     * query to avoid N+1 problems.
     */
    @Query("""
            SELECT DISTINCT p FROM PurchaseOrder p
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.product
            ORDER BY p.createdAt DESC
            """)
    List<PurchaseOrder> findAllWithDetails(org.springframework.data.domain.Pageable pageable);
}
