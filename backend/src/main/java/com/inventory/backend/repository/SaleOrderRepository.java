package com.inventory.backend.repository;

import com.inventory.backend.model.SaleOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {

    @Query("SELECT COALESCE(SUM(s.finalAmount), 0) FROM SaleOrder s WHERE s.status <> 'CANCELLED'")
    BigDecimal sumFinalAmount();

    Optional<SaleOrder> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT DISTINCT s.id FROM SaleOrder s
            LEFT JOIN s.items i
            LEFT JOIN i.product p
            LEFT JOIN s.customer c
            LEFT JOIN s.createdBy u
            WHERE (:from IS NULL OR s.createdAt >= :from)
              AND (:to IS NULL OR s.createdAt <= :to)
              AND (:customerId IS NULL OR c.id = :customerId)
              AND (:productId IS NULL OR p.id = :productId)
              AND (:sellerId IS NULL OR u.id = :sellerId)
              AND (:paymentMethod IS NULL OR s.paymentMethod = :paymentMethod)
              AND (:status IS NULL OR s.status = :status)
            """)
    List<Long> findSaleIdsForReport(
            @org.springframework.data.repository.query.Param("from") LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") LocalDateTime to,
            @org.springframework.data.repository.query.Param("customerId") Long customerId,
            @org.springframework.data.repository.query.Param("productId") Long productId,
            @org.springframework.data.repository.query.Param("sellerId") Long sellerId,
            @org.springframework.data.repository.query.Param("paymentMethod") String paymentMethod,
            @org.springframework.data.repository.query.Param("status") String status);

    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            WHERE s.id IN :ids
            """)
    List<SaleOrder> findByIdsWithItems(@org.springframework.data.repository.query.Param("ids") List<Long> ids);
}
