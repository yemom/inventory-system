package com.inventory.backend.repository;

import com.inventory.backend.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /**
     * Approved operating costs in a period.
     *
     * <p>Only APPROVED rows count. A PENDING expense is a claim somebody has
     * made, not a cost the business has agreed to, and counting it understates
     * profit before it overstates it once approved.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.status = 'APPROVED'
              AND COALESCE(e.transactionDate, e.createdAt) >= :from
              AND COALESCE(e.transactionDate, e.createdAt) <= :to
            """)
    BigDecimal sumApprovedBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT e FROM Expense e
            WHERE e.status = 'APPROVED'
              AND COALESCE(e.transactionDate, e.createdAt) >= :from
              AND COALESCE(e.transactionDate, e.createdAt) <= :to
            ORDER BY COALESCE(e.transactionDate, e.createdAt) DESC
            """)
    List<Expense> findApprovedBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Approved costs grouped by category, for the expense breakdown. */
    @Query("""
            SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.status = 'APPROVED'
              AND COALESCE(e.transactionDate, e.createdAt) >= :from
              AND COALESCE(e.transactionDate, e.createdAt) <= :to
            GROUP BY e.category
            ORDER BY SUM(e.amount) DESC
            """)
    List<Object[]> sumApprovedByCategory(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT e FROM Expense e
            WHERE (CAST(:from AS java.time.LocalDateTime) IS NULL OR COALESCE(e.transactionDate, e.createdAt) >= :from)
              AND (CAST(:to AS java.time.LocalDateTime) IS NULL OR COALESCE(e.transactionDate, e.createdAt) <= :to)
              AND (CAST(:status AS java.lang.String) IS NULL OR e.status = :status)
              AND (CAST(:category AS java.lang.String) IS NULL OR e.category = :category)
            ORDER BY COALESCE(e.transactionDate, e.createdAt) DESC
            """)
    Page<Expense> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") String status,
            @Param("category") String category,
            Pageable pageable);

    Page<Expense> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}