package com.inventory.backend.repository;

import com.inventory.backend.model.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /**
     * Purchase orders in a period, newest first, with items and supplier loaded.
     *
     * <p>Ordered by {@code createdAt} rather than by
     * {@code COALESCE(transactionDate, createdAt)}: the item fetch join makes
     * this a {@code SELECT DISTINCT}, and PostgreSQL requires every ORDER BY
     * expression to appear in the select list. A computed expression does not,
     * and the statement fails outright (SQLSTATE 42P10, "for SELECT DISTINCT,
     * ORDER BY expressions must appear in select list") rather than returning
     * the wrong rows — which is what made the purchases report return 500 for
     * every request while the identical sales report worked.
     */
    @Query("""
            SELECT DISTINCT p FROM PurchaseOrder p
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH p.supplier
            LEFT JOIN FETCH p.warehouse
            WHERE (CAST(:from AS java.time.LocalDateTime) IS NULL OR COALESCE(p.transactionDate, p.createdAt) >= :from)
              AND (CAST(:to AS java.time.LocalDateTime) IS NULL OR COALESCE(p.transactionDate, p.createdAt) <= :to)
              AND (CAST(:supplierId AS java.lang.Long) IS NULL OR p.supplier.id = :supplierId)
              AND (CAST(:status AS java.lang.String) IS NULL OR p.status = :status)
              AND (CAST(:paymentStatus AS java.lang.String) IS NULL OR p.paymentStatus = :paymentStatus)
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PurchaseOrder> findForPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("supplierId") Long supplierId,
            @Param("status") String status,
            @Param("paymentStatus") String paymentStatus);

    /**
     * Received orders that still owe money, for the payables report.
     *
     * <p>Only RECEIVED and PARTIALLY_RECEIVED rows appear: goods that have not
     * arrived are not yet a liability, they are stock on order. Counting an
     * un-received purchase order as a payable overstates what the business owes.
     *
     * <p>{@code amountPaid} rather than {@code paymentStatus}, because the
     * status is a label somebody typed while the amount is maintained from
     * completed payments.
     */
    @Query("""
            SELECT p FROM PurchaseOrder p
            WHERE p.status IN ('RECEIVED', 'PARTIALLY_RECEIVED')
              AND COALESCE(p.amountPaid, 0) < COALESCE(p.totalAmount, 0)
              AND (CAST(:asOf AS java.time.LocalDateTime) IS NULL OR COALESCE(p.transactionDate, p.createdAt) <= :asOf)
            ORDER BY COALESCE(p.transactionDate, p.createdAt) ASC
            """)
    List<PurchaseOrder> findOutstandingPayables(@Param("asOf") LocalDateTime asOf);

    /** Cost of goods received in a period — the purchase side of cash and payables. */
    @Query("""
            SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseOrder p
            WHERE p.status IN ('RECEIVED', 'PARTIALLY_RECEIVED')
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
              AND COALESCE(p.transactionDate, p.createdAt) <= :to
            """)
    BigDecimal sumReceivedBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseOrder p
            WHERE p.status IN ('ORDERED', 'DRAFT')
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
              AND COALESCE(p.transactionDate, p.createdAt) <= :to
            """)
    BigDecimal sumOrderedNotReceivedBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Purchases per day, for the purchasing trend and anomaly detection. */
    @Query("""
            SELECT CAST(COALESCE(p.transactionDate, p.createdAt) AS DATE), COALESCE(SUM(p.totalAmount), 0), COUNT(p)
            FROM PurchaseOrder p
            WHERE p.status <> 'CANCELLED'
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
              AND COALESCE(p.transactionDate, p.createdAt) <= :to
            GROUP BY CAST(COALESCE(p.transactionDate, p.createdAt) AS DATE)
            ORDER BY 1
            """)
    List<Object[]> sumTotalPerDay(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(i.unitCost * COALESCE(i.quantityReceived, i.quantity)), 0)
            FROM PurchaseOrderItem i
            JOIN i.purchaseOrder p
            WHERE p.status IN ('RECEIVED', 'PARTIALLY_RECEIVED')
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
              AND COALESCE(p.transactionDate, p.createdAt) <= :to
            """)
    BigDecimal sumReceivedCostBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Orders awaiting delivery, newest first — "purchases received but not fully
     * paid" is the inverse of this and is answered by the payables query.
     */
    /**
     * Orders with goods still outstanding, newest first.
     *
     * <p>Ordered by {@code createdAt} rather than by
     * {@code COALESCE(transactionDate, createdAt)}: the item fetch join makes
     * this a {@code SELECT DISTINCT}, and PostgreSQL requires every ORDER BY
     * expression to appear in the select list. A computed expression does not,
     * and the statement fails outright rather than returning the wrong rows.
     */
    @Query("""
            SELECT DISTINCT p FROM PurchaseOrder p
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH p.supplier
            WHERE p.status IN ('ORDERED', 'DRAFT', 'PARTIALLY_RECEIVED')
            ORDER BY p.createdAt DESC
            """)
    Page<PurchaseOrder> findNotFullyReceived(Pageable pageable);

    /**
     * Average whole days between an order being raised and its goods being
     * received, per supplier.
     *
     * <p>{@code timestampdiff} rather than {@code extract(epoch from a - b)}:
     * the latter is a SQL expression being forced through a JPQL parser that
     * reads the subtraction as a duration and then rejects it as the wrong type
     * for {@code extract}. Truncating to whole days is also what the advice
     * needs — a lead time of 6.4 days is booked as seven.
     */
    @Query("""
            SELECT p.supplier.id, AVG(timestampdiff(day, p.createdAt, p.approvedAt))
            FROM PurchaseOrder p
            WHERE p.supplier.id IS NOT NULL AND p.approvedAt IS NOT NULL
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
            GROUP BY p.supplier.id
            """)
    List<Object[]> averageLeadTimeDaysPerSupplier(@Param("from") LocalDateTime from);
}