package com.inventory.backend.repository;

import com.inventory.backend.model.SaleOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Sales carrying an unresolved void or refund request.
     *
     * <p>Used by the approval queue. The status is a parameter rather than a
     * literal so the caller and the service agree on one constant instead of a
     * string repeated in two files.
     */
    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            WHERE s.voidStatus = :status OR s.refundStatus = :status
            ORDER BY s.createdAt DESC
            """)
    Page<SaleOrder> findAwaitingApproval(
            @org.springframework.data.repository.query.Param("status") String status,
            Pageable pageable);

    /**
     * Sales recorded by one seller.
     *
     * <p>Used when the caller may read sales but not other people's takings —
     * a Cashier on the till. Same fetch-join shape as {@link #findAllWithDetails}
     * so the DTO mapper does not turn into an N+1.
     */
    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            WHERE s.createdBy.id = :sellerId
            ORDER BY s.createdAt DESC
            """)
    Page<SaleOrder> findBySellerWithDetails(
            @org.springframework.data.repository.query.Param("sellerId") Long sellerId,
            Pageable pageable);

    /**
     * Fetch all sale orders with their items, products, customers, and creators
     * in a single query to avoid N+1 problems.
     */
    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            ORDER BY s.createdAt DESC
            """)
    List<SaleOrder> findAllWithDetails(org.springframework.data.domain.Pageable pageable);
}
