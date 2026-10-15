package com.inventory.backend.repository;

import com.inventory.backend.model.Payment;
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
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Money that actually moved, by direction, within a period.
     *
     * <p>Three conditions matter and are all deliberate:
     *
     * <ul>
     *   <li>{@code status = 'COMPLETED'} — a PENDING payment is an intention,
     *       not a bank movement. Counting it as cash is exactly how an unpaid
     *       invoice becomes reported income.</li>
     *   <li>{@code COALESCE(transaction_date, created_at)} — rows written
     *       before the business-date column existed have to fall back to their
     *       creation time, or every historical payment vanishes from every
     *       report.</li>
     *   <li>{@code COALESCE(direction, ...) = 'IN'/'OUT'} — see
     *       {@code Payment.getEffectiveDirection}}. Filtering on the raw column
     *       would silently drop legacy rows, and the finance screen that wrote
     *       {@code type = 'made'} is itself legacy.</li>
     * </ul>
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.status = 'COMPLETED'
              AND COALESCE(p.direction, CASE
                    WHEN UPPER(p.type) IN ('IN', 'RECEIVED') THEN 'IN'
                    WHEN UPPER(p.type) IN ('OUT', 'MADE', 'PAID') THEN 'OUT'
                    ELSE 'IN' END) = :direction
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
              AND COALESCE(p.transactionDate, p.createdAt) <= :to
            """)
    BigDecimal sumCompletedAmount(
            @Param("direction") String direction,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Payments settling a customer debt, in a period. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.status = 'COMPLETED'
              AND COALESCE(p.direction, 'IN') = 'IN'
              AND p.partyType = 'CUSTOMER'
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
              AND COALESCE(p.transactionDate, p.createdAt) <= :to
            """)
    BigDecimal sumCustomerReceipts(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Payments settling a supplier debt, in a period. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.status = 'COMPLETED'
              AND COALESCE(p.direction, CASE
                    WHEN UPPER(p.type) IN ('OUT', 'MADE', 'PAID') THEN 'OUT'
                    ELSE 'IN' END) = 'OUT'
              AND p.partyType = 'SUPPLIER'
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
              AND COALESCE(p.transactionDate, p.createdAt) <= :to
            """)
    BigDecimal sumSupplierPayments(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Refunds paid back to customers, in a period. These are cash outflows. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.status = 'COMPLETED'
              AND COALESCE(p.direction, 'OUT') = 'OUT'
              AND p.partyType = 'CUSTOMER'
              AND COALESCE(p.transactionDate, p.createdAt) >= :from
              AND COALESCE(p.transactionDate, p.createdAt) <= :to
            """)
    BigDecimal sumCustomerRefunds(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Total received against one sale, counting only completed payments.
     *
     * <p>Used to decide whether a sale is fully settled, so the over-payment and
     * double-payment guards work off the ledger rather than a stored counter
     * that can drift.
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.saleOrder.id = :saleOrderId AND p.status = 'COMPLETED'
            """)
    BigDecimal sumCompletedForSale(@Param("saleOrderId") Long saleOrderId);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.purchaseOrder.id = :purchaseOrderId AND p.status = 'COMPLETED'
            """)
    BigDecimal sumCompletedForPurchase(@Param("purchaseOrderId") Long purchaseOrderId);

    List<Payment> findBySaleOrderIdOrderByCreatedAtAsc(Long saleOrderId);

    /**
     * Payments for a set of sales, in one round trip.
     *
     * <p>A dashboard that summarises a day's takings must not ask the database
     * once per order; this is the single query that answers "what was collected
     * against these orders".
     */
    List<Payment> findBySaleOrderIdIn(java.util.Collection<Long> saleOrderIds);

    List<Payment> findByPurchaseOrderIdOrderByCreatedAtAsc(Long purchaseOrderId);

    @Query("""
            SELECT p FROM Payment p
            WHERE (CAST(:from AS java.time.LocalDateTime) IS NULL OR COALESCE(p.transactionDate, p.createdAt) >= :from)
              AND (CAST(:to AS java.time.LocalDateTime) IS NULL OR COALESCE(p.transactionDate, p.createdAt) <= :to)
              AND (CAST(:direction AS java.lang.String) IS NULL OR COALESCE(p.direction, 'IN') = :direction)
              AND (CAST(:partyType AS java.lang.String) IS NULL OR p.partyType = :partyType)
              AND (CAST(:status AS java.lang.String) IS NULL OR p.status = :status)
            ORDER BY COALESCE(p.transactionDate, p.createdAt) DESC
            """)
    Page<Payment> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("direction") String direction,
            @Param("partyType") String partyType,
            @Param("status") String status,
            Pageable pageable);

    /** Distinct settled references per direction, for outstanding-balance joins. */
    @Query("""
            SELECT DISTINCT p.orderReference FROM Payment p
            WHERE p.orderReference IS NOT NULL AND p.status = 'COMPLETED'
            """)
    List<String> findSettledReferences();
}