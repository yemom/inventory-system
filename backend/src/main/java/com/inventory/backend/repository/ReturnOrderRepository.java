package com.inventory.backend.repository;

import com.inventory.backend.model.ReturnOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReturnOrderRepository extends JpaRepository<ReturnOrder, Long> {

    Optional<ReturnOrder> findByReturnNumber(String returnNumber);

    @Query("""
            SELECT DISTINCT r FROM ReturnOrder r
            LEFT JOIN FETCH r.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH r.customer
            LEFT JOIN FETCH r.supplier
            WHERE (CAST(:type AS java.lang.String) IS NULL OR r.returnType = :type)
              AND (CAST(:status AS java.lang.String) IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    Page<ReturnOrder> search(@Param("type") String type,
                             @Param("status") String status,
                             Pageable pageable);

    /** Approved sales returns in a period — the figure that reduces net sales. */
    @Query("""
            SELECT COALESCE(SUM(r.totalAmount), 0) FROM ReturnOrder r
            WHERE r.returnType = :type AND r.status = :status
              AND COALESCE(r.transactionDate, r.createdAt) >= :from
              AND COALESCE(r.transactionDate, r.createdAt) <= :to
            """)
    BigDecimal sumTotalAmountByPeriodAndTypeAndStatus(
            @Param("type") String type,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT DISTINCT r FROM ReturnOrder r
            LEFT JOIN FETCH r.items i
            LEFT JOIN FETCH i.product
            WHERE r.returnType = :type AND r.status = :status
              AND COALESCE(r.transactionDate, r.createdAt) >= :from
              AND COALESCE(r.transactionDate, r.createdAt) <= :to
            """)
    List<ReturnOrder> findApprovedByPeriod(
            @Param("type") String type,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Sales still awaiting a return decision. */
    Page<ReturnOrder> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}