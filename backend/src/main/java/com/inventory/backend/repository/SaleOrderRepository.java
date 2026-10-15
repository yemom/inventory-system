package com.inventory.backend.repository;

import com.inventory.backend.model.Customer;
import com.inventory.backend.model.SaleOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {

    @Query("SELECT COALESCE(SUM(s.finalAmount), 0) FROM SaleOrder s WHERE s.status <> 'CANCELLED'")
    BigDecimal sumFinalAmount();

    Optional<SaleOrder> findByIdempotencyKey(String idempotencyKey);

    /**
     * Sales with their lines, products, customer, cashier and warehouse.
     *
     * <p>The period filter is on {@code COALESCE(transactionDate, createdAt)}:
     * rows written before the business-date column existed have no
     * transactionDate, and filtering on it directly would silently drop every
     * historical sale from every report while the dashboard still counted them.
     *
     * <p>Ordered by {@code createdAt} rather than by the same coalesced expression
     * used in the filter. The fetch joins make this a {@code SELECT DISTINCT},
     * and PostgreSQL requires every ORDER BY expression to appear in the select
     * list — a computed expression does not. {@code createdAt} is a plain column
     * of the selected entity, so it is accepted, and it gives the same ordering
     * for every row written since the business-date column was added.
     *
     * <p>The optional id filters are wrapped in {@code CAST(... AS Long)}. A bare
     * {@code :id IS NULL} reaches PostgreSQL as a parameter of unknown type and,
     * with no column to infer from, it refuses the whole statement with "could
     * not determine data type of parameter $1" — an optional filter that breaks
     * the report precisely when it is not being used. The same applies to the
     * optional date and string filters.
     */
    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            LEFT JOIN FETCH s.warehouse
            WHERE (CAST(:from AS java.time.LocalDateTime) IS NULL OR COALESCE(s.transactionDate, s.createdAt) >= :from)
              AND (CAST(:to AS java.time.LocalDateTime) IS NULL OR COALESCE(s.transactionDate, s.createdAt) <= :to)
              AND (CAST(:customerId AS java.lang.Long) IS NULL OR s.customer.id = :customerId)
              AND (CAST(:warehouseId AS java.lang.Long) IS NULL OR s.warehouse.id = :warehouseId)
              AND (CAST(:sellerId AS java.lang.Long) IS NULL OR s.createdBy.id = :sellerId)
              AND (CAST(:paymentStatus AS java.lang.String) IS NULL OR s.paymentStatus = :paymentStatus)
              AND (:includeCancelled = true OR s.status <> 'CANCELLED')
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    List<SaleOrder> findSalesForPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("customerId") Long customerId,
            @Param("warehouseId") Long warehouseId,
            @Param("sellerId") Long sellerId,
            @Param("paymentStatus") String paymentStatus,
            @Param("includeCancelled") boolean includeCancelled);

    @Query("""
            SELECT DISTINCT s.id FROM SaleOrder s
            LEFT JOIN s.items i
            LEFT JOIN i.product p
            LEFT JOIN s.customer c
            LEFT JOIN s.createdBy u
            WHERE (CAST(:from AS java.time.LocalDateTime) IS NULL
                   OR COALESCE(s.transactionDate, s.createdAt) >= :from)
              AND (CAST(:to AS java.time.LocalDateTime) IS NULL
                   OR COALESCE(s.transactionDate, s.createdAt) <= :to)
              AND (CAST(:customerId AS java.lang.Long) IS NULL OR c.id = :customerId)
              AND (CAST(:productId AS java.lang.Long) IS NULL OR p.id = :productId)
              AND (CAST(:sellerId AS java.lang.Long) IS NULL OR u.id = :sellerId)
              AND (CAST(:paymentMethod AS java.lang.String) IS NULL OR s.paymentMethod = :paymentMethod)
              AND (CAST(:status AS java.lang.String) IS NULL OR s.status = :status)
            """)
    List<Long> findSaleIdsForReport(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("customerId") Long customerId,
            @Param("productId") Long productId,
            @Param("sellerId") Long sellerId,
            @Param("paymentMethod") String paymentMethod,
            @Param("status") String status);

    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            WHERE s.id IN :ids
            """)
    List<SaleOrder> findByIdsWithItems(@Param("ids") List<Long> ids);

    /**
     * One sale by its order number, with the rows the caller displays.
     *
     * <p>Scoped in the query for the same reason the customer lookups below are:
     * loading every sale in the business to pick one out of it in memory means
     * an accidental print, a debugger or a future fetch join becomes a
     * disclosure — and it is simply more expensive.
     */
    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            WHERE UPPER(s.orderNumber) = UPPER(:orderNumber)
            """)
    Optional<SaleOrder> findByOrderNumberWithDetails(@Param("orderNumber") String orderNumber);

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
            @Param("status") String status,
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
            @Param("sellerId") Long sellerId,
            Pageable pageable);

    /**
     * A page of sales, filtered in the database.
     *
     * <p>Search and status filters belong here rather than in the browser: a
     * table that loads every order and hides rows client-side cannot page
     * correctly and sends the whole ledger over the wire to show ten of them.
     * Every filter is nullable and simply absent when not supplied, so an
     * unfiltered call is the same query with no predicates.
     *
     * <p>The caller is <b>not</b> scoped here. That is deliberate and is applied
     * by {@link SaleOrderService}, which knows who is asking; putting the seller
     * in this signature as well would make it too easy to forget on one of the
     * two paths and silently widen a cashier's view.
     *
     * <p>The filters are cast to {@code varchar} because a bare {@code :search IS NULL}
     * comparison leaves Hibernate with nothing to infer the parameter's type
     * from, and it binds the null as {@code bytea} — which PostgreSQL then
     * refuses with "function lower(bytea) does not exist". The cast gives the
     * driver the type up front.
     */
    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            WHERE (CAST(:search AS string) IS NULL
                   OR LOWER(s.orderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(COALESCE(s.customer.name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
              AND (CAST(:status AS string) IS NULL OR s.status = CAST(:status AS string))
              AND (CAST(:paymentStatus AS string) IS NULL
                   OR s.paymentStatus = CAST(:paymentStatus AS string))
            """)
    Page<SaleOrder> searchWithDetails(
            @Param("search") String search,
            @Param("status") String status,
            @Param("paymentStatus") String paymentStatus,
            Pageable pageable);

    /**
     * The same filters, scoped to one seller.
     *
     * <p>The count comes from this query rather than from a table-wide one, so
     * the pagination metadata cannot disclose how large the whole ledger is to a
     * caller who is only entitled to their own page of it.
     */
    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH s.customer
            LEFT JOIN FETCH s.createdBy
            WHERE s.createdBy.id = :sellerId
              AND (CAST(:search AS string) IS NULL
                   OR LOWER(s.orderNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(COALESCE(s.customer.name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
              AND (CAST(:status AS string) IS NULL OR s.status = CAST(:status AS string))
              AND (CAST(:paymentStatus AS string) IS NULL
                   OR s.paymentStatus = CAST(:paymentStatus AS string))
            """)
    Page<SaleOrder> searchBySellerWithDetails(
            @Param("sellerId") Long sellerId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("paymentStatus") String paymentStatus,
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

    /**
     * Sales that still owe money, for the receivables report.
     *
     * <p>{@code amountPaid} is used rather than {@code paymentStatus}: the status
     * is a label somebody typed, while {@code amountPaid} is maintained from
     * completed payments. A sale whose status says UNPAID but which has actually
     * been settled must not appear as a receivable.
     *
     * <p>Refund and void are excluded — a voided sale owes nothing and a
     * refunded one has already had its money returned.
     */
    @Query("""
            SELECT s FROM SaleOrder s
            WHERE s.status <> 'CANCELLED'
              AND (s.refundStatus IS NULL OR s.refundStatus <> 'APPROVED')
              AND COALESCE(s.amountPaid, 0) < COALESCE(s.finalAmount, 0)
              AND (CAST(:asOf AS java.time.LocalDateTime) IS NULL OR COALESCE(s.transactionDate, s.createdAt) <= :asOf)
            ORDER BY COALESCE(s.transactionDate, s.createdAt) ASC
            """)
    List<SaleOrder> findOutstandingReceivables(@Param("asOf") LocalDateTime asOf);

    // ── Customer-scoped lookups ────────────────────────────────────────────────
    //
    // Every query here is scoped to a customer id in SQL rather than filtered in
    // memory afterwards. The chatbot answers a customer's questions about their
    // own orders, and loading every sale in the business to pick one customer's
    // out of it would mean a leak elsewhere in the process — a future fetch join,
    // a debugger, an accidental toString — becomes a disclosure. Scoping in the
    // query is also simply cheaper.

    /** One customer's orders, newest first, with their items. */
    @Query("""
            SELECT DISTINCT s FROM SaleOrder s
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.product
            WHERE s.customer.id = :customerId
            ORDER BY s.createdAt DESC
            """)
    List<SaleOrder> findByCustomerId(@Param("customerId") Long customerId);

    /** Whether an order number belongs to this customer. */
    @Query("""
            SELECT COUNT(s) FROM SaleOrder s
            WHERE s.customer.id = :customerId
              AND UPPER(s.orderNumber) = UPPER(:orderNumber)
            """)
    long countByCustomerIdAndOrderNumber(@Param("customerId") Long customerId,
                                         @Param("orderNumber") String orderNumber);

    /**
     * The customer who placed an order, matched by the email on
     * their account and the order number on the receipt.
     *
     * <p>Used to open a chatbot session: the pair is the proof of
     * ownership, because both appear on a receipt the customer
     * already holds. The query returns the customer rather than a
     * boolean so the caller learns the id it must scope every
     * later question to.
     */
    @Query("""
            SELECT s.customer FROM SaleOrder s
            WHERE UPPER(s.orderNumber) = UPPER(:orderNumber)
              AND UPPER(s.customer.email) = UPPER(:email)
            """)
    List<Customer> findCustomerByEmailAndOrderNumber(@Param("email") String email,
                                                     @Param("orderNumber") String orderNumber);

    /** Sales still owed beyond a date, for the overdue ageing report. */
    @Query("""
            SELECT s FROM SaleOrder s
            WHERE s.status <> 'CANCELLED'
              AND (s.refundStatus IS NULL OR s.refundStatus <> 'APPROVED')
              AND COALESCE(s.amountPaid, 0) < COALESCE(s.finalAmount, 0)
            ORDER BY COALESCE(s.transactionDate, s.createdAt) ASC
            """)
    List<SaleOrder> findAllOutstandingReceivables();

    // ── Daily aggregates, used by forecasting and trend reporting ─────────────
    // Grouping happens in SQL rather than in Java so a year of sales does not
    // have to be materialised as objects before being reduced to one number per
    // day.

    /** Net sales (excluding tax) per day. Cancelled sales are excluded. */
    @Query("""
            SELECT CAST(COALESCE(s.transactionDate, s.createdAt) AS DATE), COALESCE(SUM(s.finalAmount), 0)
            FROM SaleOrder s
            WHERE s.status <> 'CANCELLED'
              AND (s.refundStatus IS NULL OR s.refundStatus <> 'APPROVED')
              AND COALESCE(s.transactionDate, s.createdAt) >= :from
              AND COALESCE(s.transactionDate, s.createdAt) <= :to
            GROUP BY CAST(COALESCE(s.transactionDate, s.createdAt) AS DATE)
            ORDER BY 1
            """)
    List<Object[]> sumNetSalesPerDay(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Transaction count per day. */
    @Query("""
            SELECT CAST(COALESCE(s.transactionDate, s.createdAt) AS DATE), COUNT(s)
            FROM SaleOrder s
            WHERE s.status <> 'CANCELLED'
              AND (s.refundStatus IS NULL OR s.refundStatus <> 'APPROVED')
              AND COALESCE(s.transactionDate, s.createdAt) >= :from
              AND COALESCE(s.transactionDate, s.createdAt) <= :to
            GROUP BY CAST(COALESCE(s.transactionDate, s.createdAt) AS DATE)
            ORDER BY 1
            """)
    List<Object[]> countSalesPerDay(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Units sold per product per day, for per-product demand forecasting.
     *
     * <p>Revenue is not a proxy for demand: a product whose price was raised 40%
     * looks like it sold four times better. Quantity is what the reorder advice
     * actually depends on.
     */
    @Query("""
            SELECT CAST(COALESCE(s.transactionDate, s.createdAt) AS DATE), i.product.id, SUM(i.quantity)
            FROM SaleOrder s
            JOIN s.items i
            WHERE s.status <> 'CANCELLED'
              AND (s.refundStatus IS NULL OR s.refundStatus <> 'APPROVED')
              AND COALESCE(i.quantityReturned, 0) = 0
              AND COALESCE(s.transactionDate, s.createdAt) >= :from
              AND COALESCE(s.transactionDate, s.createdAt) <= :to
              AND (CAST(:productId AS java.lang.Long) IS NULL OR i.product.id = :productId)
            GROUP BY CAST(COALESCE(s.transactionDate, s.createdAt) AS DATE), i.product.id
            ORDER BY 1, 2
            """)
    List<Object[]> sumUnitsPerProductPerDay(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("productId") Long productId);

    /** Revenue and cost per product in a period, for the product profitability report. */
    @Query("""
            SELECT i.product.id,
                   COALESCE(SUM(i.quantity), 0),
                   COALESCE(SUM(i.subtotal), 0),
                   COALESCE(SUM(COALESCE(i.unitCost, 0) * i.quantity), 0)
            FROM SaleOrder s
            JOIN s.items i
            WHERE s.status <> 'CANCELLED'
              AND (s.refundStatus IS NULL OR s.refundStatus <> 'APPROVED')
              AND COALESCE(s.transactionDate, s.createdAt) >= :from
              AND COALESCE(s.transactionDate, s.createdAt) <= :to
            GROUP BY i.product.id
            """)
    List<Object[]> summariseByProduct(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Product ids that had at least one sale line in a period. */
    @Query("""
            SELECT DISTINCT i.product.id FROM SaleOrderItem i
            JOIN i.saleOrder s
            WHERE COALESCE(i.quantityReturned, 0) = 0
              AND s.status <> 'CANCELLED'
              AND (s.refundStatus IS NULL OR s.refundStatus <> 'APPROVED')
              AND COALESCE(s.transactionDate, s.createdAt) >= :from
              AND COALESCE(s.transactionDate, s.createdAt) <= :to
            """)
    List<Long> findProductIdsSoldInPeriod(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}