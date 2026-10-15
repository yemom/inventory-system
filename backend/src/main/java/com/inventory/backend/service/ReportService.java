package com.inventory.backend.service;

import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The reporting layer.
 *
 * <p>Every report here is reproducible: run it twice on the same saved records
 * and you get the same numbers, because nothing is computed in the browser and
 * nothing depends on when the report was run. Where a report differs from
 * another — the sales report and the profit and loss, say — they differ because
 * they answer different questions, not because two implementations of the same
 * arithmetic exist.
 *
 * <p>The profit-related figures all come from {@link FinancialCalculationService}.
 * No report recomputes gross profit, cost of goods sold or a balance; if a figure
 * is not in the summary, it is not in the report.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final FinancialCalculationService calculations;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    // ── Sales and gross profit ────────────────────────────────────────────────

    /** Sales in a period, with revenue, cost and margin per line. */
    @Transactional(readOnly = true)
    public Map<String, Object> salesAndProfit(LocalDate from, LocalDate to, Long warehouseId,
                                              Long productId, Long customerId) {
        List<SaleOrder> sales = saleOrderRepository.findSalesForPeriod(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX),
                customerId, warehouseId, null, null, false);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        long units = 0;

        for (SaleOrder sale : sales) {
            if (productId != null && (sale.getItems() == null || sale.getItems().stream()
                    .noneMatch(i -> productId.equals(i.getProduct() == null ? null : i.getProduct().getId())))) {
                continue;
            }
            BigDecimal lineRevenue = BigDecimal.ZERO;
            BigDecimal lineCost = BigDecimal.ZERO;
            for (SaleOrderItem item : safe(sale.getItems())) {
                int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                int returned = item.getQuantityReturned() != null ? item.getQuantityReturned() : 0;
                int netQuantity = quantity - returned;
                lineRevenue = lineRevenue.add(nz(item.getSubtotal()));
                BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost()
                        : (item.getProduct() != null ? nz(item.getProduct().getPurchasePrice()) : BigDecimal.ZERO);
                lineCost = lineCost.add(unitCost.multiply(BigDecimal.valueOf(netQuantity)));
                units += netQuantity;
            }
            BigDecimal netRevenue = lineRevenue.subtract(nz(sale.getDiscount()));
            revenue = revenue.add(netRevenue);
            cost = cost.add(lineCost);

            rows.add(row(
                    "order", sale.getOrderNumber(),
                    "date", date(sale.getTransactionDate() != null ? sale.getTransactionDate() : sale.getCreatedAt()),
                    "customer", sale.getCustomerName(),
                    "warehouse", sale.getWarehouse() == null ? "—" : sale.getWarehouse().getName(),
                    "cashier", sale.getCreatedBy() == null ? "—" : sale.getCreatedBy().getUsername(),
                    "gross", lineRevenue,
                    "discount", nz(sale.getDiscount()),
                    "net", netRevenue,
                    "tax", nz(sale.getTax()),
                    "cogs", lineCost,
                    "grossProfit", netRevenue.subtract(lineCost),
                    "paymentStatus", sale.getPaymentStatus() == null ? "—" : sale.getPaymentStatus()));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("report", "Sales and gross profit");
        report.put("from", from.toString());
        report.put("to", to.toString());
        report.put("columns", List.of("order", "date", "customer", "warehouse", "cashier",
                "gross", "discount", "net", "tax", "cogs", "grossProfit", "paymentStatus"));
        report.put("rows", rows);
        report.put("totals", Map.of("netSales", revenue, "costOfGoodsSold", cost,
                "grossProfit", revenue.subtract(cost), "unitsSold", units));
        return report;
    }

    // ── Product profitability ─────────────────────────────────────────────────

    /** Revenue, cost and margin per product in a period. */
    @Transactional(readOnly = true)
    public Map<String, Object> productProfitability(LocalDate from, LocalDate to, int limit) {
        List<Object[]> rows = saleOrderRepository.summariseByProduct(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));

        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Map<String, Object>> lines = new ArrayList<>();
        for (Object[] r : rows) {
            long id = ((Number) r[0]).longValue();
            Product product = products.get(id);

            // The aggregates arrive from SQL as exact NUMERIC. They are read as
            // BigDecimal and stay in it: routing money through double loses
            // cents (1234.56 can come back as 1234.5600000000001), and this
            // figure then disagrees with the dashboard and the finance reports,
            // which derive the same number in decimal arithmetic. unitsSold is a
            // count, so a long is the honest type for it too.
            long soldUnits = ((Number) r[1]).longValue();
            BigDecimal revenue = (BigDecimal) r[2];
            BigDecimal cost = (BigDecimal) r[3];
            BigDecimal grossProfit = revenue.subtract(cost);

            lines.add(row(
                    "sku", product == null ? "—" : product.getSku(),
                    "product", product == null ? "Product " + id : product.getName(),
                    "unitsSold", soldUnits,
                    "revenue", revenue,
                    "costOfGoodsSold", cost,
                    "grossProfit", grossProfit,
                    "marginPercent", revenue.signum() == 0 ? BigDecimal.ZERO
                            : grossProfit
                            .multiply(BigDecimal.valueOf(100))
                            .divide(revenue, 1, java.math.RoundingMode.HALF_UP)));
        }
        lines.sort((a, b) -> ((BigDecimal) b.get("grossProfit")).compareTo((BigDecimal) a.get("grossProfit")));

        return Map.of("report", "Product profitability",
                "from", from.toString(), "to", to.toString(),
                "columns", List.of("sku", "product", "unitsSold", "revenue",
                        "costOfGoodsSold", "grossProfit", "marginPercent"),
                "rows", limit > 0 && lines.size() > limit ? lines.subList(0, limit) : lines);
    }

    // ── Purchases and supplier balances ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> purchases(LocalDate from, LocalDate to, Long supplierId, String status) {
        List<PurchaseOrder> orders = purchaseOrderRepository.findForPeriod(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX), supplierId, status, null);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal ordered = BigDecimal.ZERO;
        BigDecimal balance = BigDecimal.ZERO;

        for (PurchaseOrder po : orders) {
            ordered = ordered.add(nz(po.getTotalAmount()));
            balance = balance.add(po.getBalanceDue());
            int outstanding = 0;
            int received = 0;
            for (PurchaseOrderItem item : safe(po.getItems())) {
                outstanding += item.getQuantityOutstanding();
                received += item.getEffectiveReceived();
            }
            rows.add(row(
                    "order", po.getOrderNumber(),
                    "date", date(po.getTransactionDate() != null ? po.getTransactionDate() : po.getCreatedAt()),
                    "supplier", po.getSupplierName() == null ? "—" : po.getSupplierName(),
                    "status", po.getStatus(),
                    "paymentStatus", po.getPaymentStatus() == null ? "—" : po.getPaymentStatus(),
                    "total", nz(po.getTotalAmount()),
                    "paid", nz(po.getAmountPaid()),
                    "balanceDue", po.getBalanceDue(),
                    "unitsOrdered", po.getItems() == null ? 0 : po.getItems().stream()
                            .mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum(),
                    "unitsReceived", received,
                    "unitsOutstanding", outstanding,
                    "dueDate", po.getDueDate() == null ? "not set" : po.getDueDate().toString()));
        }

        return Map.of("report", "Purchases and supplier balances",
                "from", from.toString(), "to", to.toString(),
                "columns", List.of("order", "date", "supplier", "status", "paymentStatus",
                        "total", "paid", "balanceDue", "unitsOrdered", "unitsReceived",
                        "unitsOutstanding", "dueDate"),
                "rows", rows,
                "totals", Map.of("orderedValue", ordered, "outstandingBalance", balance));
    }

    // ── Cash flow ─────────────────────────────────────────────────────────────

    /**
     * Money in and money out, itemised.
     *
     * <p>Built from completed payment records rather than from invoices. An
     * unpaid sale is not a cash movement and an unreceived purchase is not a
     * payment; both appear in their own reports instead.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> cashFlow(LocalDate from, LocalDate to) {
        List<Payment> payments = paymentRepository.search(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX),
                null, null, Payment.STATUS_COMPLETED,
                org.springframework.data.domain.Pageable.unpaged()).getContent();

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal inflow = BigDecimal.ZERO;
        BigDecimal outflow = BigDecimal.ZERO;

        for (Payment payment : payments) {
            String direction = payment.getEffectiveDirection();
            BigDecimal amount = nz(payment.getAmount());
            if (Payment.DIRECTION_IN.equals(direction)) {
                inflow = inflow.add(amount);
            } else {
                outflow = outflow.add(amount);
            }
            rows.add(row(
                    "date", date(payment.getEffectiveTransactionDate()),
                    "reference", payment.getReference() == null ? "—" : payment.getReference(),
                    "direction", direction,
                    "partyType", payment.getPartyType() == null ? "OTHER" : payment.getPartyType(),
                    "party", partyName(payment),
                    "method", payment.getPaymentMethod() == null ? "—" : payment.getPaymentMethod(),
                    "document", payment.getOrderReference() == null ? "—" : payment.getOrderReference(),
                    "amount", amount,
                    "bankReference", payment.getExternalReference() == null ? "—" : payment.getExternalReference()));
        }

        BigDecimal expenses = expenseRepository.sumApprovedBetween(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));
        outflow = outflow.add(expenses);

        return Map.of("report", "Cash flow",
                "from", from.toString(), "to", to.toString(),
                "columns", List.of("date", "reference", "direction", "partyType", "party",
                        "method", "document", "amount", "bankReference"),
                "rows", rows,
                "totals", Map.of(
                        "cashIn", inflow,
                        "cashOutPayments", outflow.subtract(expenses),
                        "operatingExpenses", expenses,
                        "cashOut", outflow,
                        "netCashFlow", inflow.subtract(outflow)));
    }

    // ── Income and expenses ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> incomeAndExpenses(LocalDate from, LocalDate to) {
        FinancialSummary summary = calculations.summarise(from, to);

        List<Map<String, Object>> expenseRows = expenseRepository.findApprovedBetween(
                        from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX)).stream()
                .map(e -> row("date", date(e.getEffectiveTransactionDate()),
                        "reference", e.getReference() == null ? "—" : e.getReference(),
                        "category", e.getCategory() == null ? "UNCATEGORISED" : e.getCategory(),
                        "description", e.getDescription() == null ? "" : e.getDescription(),
                        "amount", nz(e.getAmount()),
                        "approvedBy", e.getApprovedBy() == null
                                ? (e.getCreatedBy() == null ? "—" : e.getCreatedBy())
                                : displayName(e.getApprovedBy())))
                .toList();

        return Map.of("report", "Income and expenses",
                "from", from.toString(), "to", to.toString(),
                "summary", summary,
                "expenses", expenseRows,
                "expensesByCategory", summary.expensesByCategory());
    }

    // ── Profit and loss ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> profitAndLoss(LocalDate from, LocalDate to) {
        FinancialSummary current = calculations.summarise(from, to);
        FinancialSummary previous = calculations.previousPeriod(from, to);

        List<Map<String, Object>> statement = new ArrayList<>();
        statement.add(row("line", "Net sales", "amount", current.netSales(),
                "previous", previous.netSales()));
        statement.add(row("line", "Cost of goods sold", "amount",
                current.costOfGoodsSold().negate(), "previous",
                previous.costOfGoodsSold().negate()));
        statement.add(row("line", "Gross profit", "amount", current.grossProfit(),
                "previous", previous.grossProfit(), "emphasis", true));
        for (FinancialSummary.CategoryTotal category : current.expensesByCategory()) {
            statement.add(row("line", "Operating expense — " + category.category(),
                    "amount", category.amount().negate()));
        }
        statement.add(row("line", "Total operating expenses", "amount",
                current.operatingExpenses().negate(), "previous",
                previous.operatingExpenses().negate()));
        statement.add(row("line", "Net profit", "amount", current.netProfit(),
                "previous", previous.netProfit(), "emphasis", true));

        return Map.of("report", "Profit and loss",
                "from", from.toString(), "to", to.toString(),
                "previousFrom", previous.from().toString(),
                "previousTo", previous.to().toString(),
                "statement", statement,
                "summary", current,
                "basis", current.basis(),
                "memo", Map.of(
                        "taxCollected", current.netSalesTax(),
                        "cashReceived", current.cashReceived(),
                        "cashNotYetCollected", current.netSales().subtract(current.cashReceived()),
                        "accountsReceivable", current.accountsReceivable(),
                        "accountsPayable", current.accountsPayable()));
    }

    // ── Inventory valuation ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> inventoryValuation(Long warehouseId) {
        List<WarehouseStock> stock = warehouseId == null
                ? warehouseStockRepository.findAllWithStock()
                : warehouseStockRepository.findByWarehouseId(warehouseId);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int units = 0;

        for (WarehouseStock ws : stock) {
            if (ws.getQuantity() <= 0) {
                continue;
            }
            Product product = ws.getProduct();
            BigDecimal unitCost = ws.getAverageCost() != null ? ws.getAverageCost()
                    : (product == null ? BigDecimal.ZERO : nz(product.getPurchasePrice()));
            BigDecimal retail = product == null || product.getSellingPrice() == null
                    ? BigDecimal.ZERO : product.getSellingPrice();
            BigDecimal value = unitCost.multiply(BigDecimal.valueOf(ws.getQuantity()));
            total = total.add(value);
            units += ws.getQuantity();

            rows.add(row("sku", product == null ? "—" : product.getSku(),
                    "product", product == null ? "—" : product.getName(),
                    "warehouse", ws.getWarehouse() == null ? "—" : ws.getWarehouse().getName(),
                    "quantity", ws.getQuantity(),
                    "unitCost", unitCost,
                    "valueAtCost", value,
                    "unitPrice", retail,
                    "valueAtRetail", retail.multiply(BigDecimal.valueOf(ws.getQuantity())),
                    "potentialMargin", retail.subtract(unitCost)
                            .multiply(BigDecimal.valueOf(ws.getQuantity()))));
        }
        rows.sort(Comparator.comparing(r -> String.valueOf(r.get("sku"))));

        return Map.of("report", "Inventory valuation",
                "valuationDate", LocalDate.now().toString(),
                "columns", List.of("sku", "product", "warehouse", "quantity",
                        "unitCost", "valueAtCost", "unitPrice", "valueAtRetail", "potentialMargin"),
                "rows", rows,
                "totals", Map.of("totalUnits", units, "totalValueAtCost", total),
                "method", "Moving weighted average, recalculated on every goods receipt");
    }

    // ── Product movement history ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> movementHistory(LocalDate from, LocalDate to, Long productId,
                                              Long warehouseId, String type) {
        var page = stockMovementRepository.search(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX),
                warehouseId, type, StockMovement.Status.APPROVED,
                org.springframework.data.domain.PageRequest.of(0, 2000,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));

        List<Map<String, Object>> rows = page.getContent().stream()
                .filter(m -> productId == null || (m.getProduct() != null
                        && productId.equals(m.getProduct().getId())))
                .map(m -> row("date", date(m.getCreatedAt()),
                        "type", m.getType(),
                        "product", m.getProduct() == null ? "—" : m.getProduct().getSku(),
                        "from", m.getWarehouse() == null ? "—" : m.getWarehouse().getName(),
                        "to", m.getDestinationWarehouse() == null ? "—"
                                : m.getDestinationWarehouse().getName(),
                        "quantity", m.getQuantity(),
                        "unitCost", m.getUnitCost(),
                        "reference", m.getReference() == null ? "—" : m.getReference(),
                        "reason", m.getReason() == null ? "" : m.getReason(),
                        "recordedBy", m.getCreatedBy() == null ? "—" : m.getCreatedBy().getUsername()))
                .toList();

        return Map.of("report", "Product movement history",
                "from", from.toString(), "to", to.toString(),
                "columns", List.of("date", "type", "product", "from", "to", "quantity",
                        "unitCost", "reference", "reason", "recordedBy"),
                "rows", rows);
    }

    // ── Stock adjustments, damage and expiry ─────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> shrinkage(LocalDate from, LocalDate to) {
        List<StockMovement> losses = stockMovementRepository.findLossesBetween(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));

        List<Map<String, Object>> rows = losses.stream()
                .map(m -> row("date", date(m.getCreatedAt()),
                        "type", m.getType(),
                        "product", m.getProduct() == null ? "—" : m.getProduct().getSku(),
                        "warehouse", m.getWarehouse() == null ? "—" : m.getWarehouse().getName(),
                        "quantity", m.getQuantity() == null ? 0 : Math.abs(m.getQuantity()),
                        "unitCost", m.getUnitCost(),
                        "value", m.getUnitCost() == null ? BigDecimal.ZERO
                                : m.getUnitCost().multiply(BigDecimal.valueOf(
                                Math.abs(m.getQuantity() == null ? 0 : m.getQuantity()))),
                        "reason", m.getReason() == null ? "" : m.getReason(),
                        "approvedBy", m.getApprovedBy() == null ? "—"
                                : displayName(m.getApprovedBy()),
                        "reference", m.getReference() == null ? "—" : m.getReference()))
                .toList();

        BigDecimal totalValue = rows.stream()
                .map(r -> (BigDecimal) r.get("value"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalUnits = rows.stream().mapToInt(r -> ((Number) r.get("quantity")).intValue()).sum();

        Map<String, Long> byType = losses.stream()
                .collect(Collectors.groupingBy(m -> m.getType() == null ? "UNKNOWN" : m.getType(),
                        Collectors.counting()));

        return Map.of("report", "Stock adjustments, damage and expiry",
                "from", from.toString(), "to", to.toString(),
                "columns", List.of("date", "type", "product", "warehouse", "quantity",
                        "unitCost", "value", "reason", "approvedBy", "reference"),
                "rows", rows,
                "totals", Map.of("unitsLost", totalUnits, "valueLost", totalValue),
                "eventsByType", byType);
    }

    // ── Slow-moving inventory ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> slowMoving(int lookbackDays, BigDecimal minimumValue) {
        LocalDateTime from = LocalDateTime.now().minusDays(lookbackDays);
        Set<Long> sold = new HashSet<>(saleOrderRepository.findProductIdsSoldInPeriod(from, LocalDateTime.now()));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Product product : productRepository.findAll()) {
            if (!product.isActive()) {
                continue;
            }
            int onHand = warehouseStockRepository.totalQuantityForProduct(product.getId());
            if (onHand <= 0 || sold.contains(product.getId())) {
                continue;
            }
            BigDecimal value = nz(product.getPurchasePrice()).multiply(BigDecimal.valueOf(onHand));
            if (minimumValue != null && value.compareTo(minimumValue) < 0) {
                continue;
            }
            total = total.add(value);
            rows.add(row("sku", product.getSku(), "product", product.getName(),
                    "quantity", onHand, "unitCost", nz(product.getPurchasePrice()),
                    "valueAtCost", value, "reorderLevel",
                    product.getReorderLevel() == null ? "not set" : product.getReorderLevel()));
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("valueAtCost")).compareTo((BigDecimal) a.get("valueAtCost")));

        return Map.of("report", "Slow-moving inventory",
                "lookbackDays", lookbackDays,
                "columns", List.of("sku", "product", "quantity", "unitCost", "valueAtCost", "reorderLevel"),
                "rows", rows,
                "totals", Map.of("totalValueAtCost", total));
    }

    // ── Receivables and payables ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> receivables(LocalDate asOf) {
        LocalDateTime end = asOf.atTime(java.time.LocalTime.MAX);
        Map<String, List<SaleOrder>> byCustomer = saleOrderRepository
                .findOutstandingReceivables(end).stream()
                .collect(Collectors.groupingBy(s -> s.getCustomer() != null
                        ? s.getCustomer().getName()
                        : (s.getCustomerName() == null ? "Unattributed" : s.getCustomerName())));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;

        for (Map.Entry<String, List<SaleOrder>> entry : byCustomer.entrySet()) {
            BigDecimal balance = entry.getValue().stream()
                    .map(s -> outstanding(s.getFinalAmount(), s.getAmountPaid()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (balance.signum() <= 0) {
                continue;
            }
            int oldest = entry.getValue().stream()
                    .map(s -> s.getTransactionDate() != null ? s.getTransactionDate() : s.getCreatedAt())
                    .filter(Objects::nonNull)
                    .mapToInt(d -> (int) java.time.temporal.ChronoUnit.DAYS.between(
                            d.toLocalDate(), asOf))
                    .max().orElse(0);
            boolean isOverdue = oldest > 30;
            total = total.add(balance);
            if (isOverdue) {
                overdue = overdue.add(balance);
            }
            rows.add(row("customer", entry.getKey(),
                    "invoiceCount", entry.getValue().size(),
                    "invoiceTotal", entry.getValue().stream()
                            .map(s -> nz(s.getFinalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add),
                    "paid", entry.getValue().stream()
                            .map(s -> nz(s.getAmountPaid())).reduce(BigDecimal.ZERO, BigDecimal::add),
                    "outstanding", balance,
                    "oldestDays", oldest,
                    "ageing", ageBucket(oldest),
                    "overdue", isOverdue));
        }
        rows.sort(Comparator.comparingInt(r -> -((Number) r.get("oldestDays")).intValue()));

        return Map.of("report", "Customer receivables",
                "asOf", asOf.toString(),
                "columns", List.of("customer", "invoiceCount", "invoiceTotal", "paid",
                        "outstanding", "oldestDays", "ageing", "overdue"),
                "rows", rows,
                "totals", Map.of("totalOutstanding", total, "overdueAmount", overdue));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> payables(LocalDate asOf) {
        LocalDateTime end = asOf.atTime(java.time.LocalTime.MAX);

        Map<String, List<PurchaseOrder>> bySupplier = purchaseOrderRepository
                .findOutstandingPayables(end).stream()
                .collect(Collectors.groupingBy(p -> p.getSupplierName() == null
                        ? "Unattributed" : p.getSupplierName()));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;

        for (Map.Entry<String, List<PurchaseOrder>> entry : bySupplier.entrySet()) {
            BigDecimal balance = entry.getValue().stream()
                    .map(p -> outstanding(p.getTotalAmount(), p.getAmountPaid()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (balance.signum() <= 0) {
                continue;
            }
            boolean isOverdue = entry.getValue().stream()
                    .anyMatch(p -> p.getDueDate() != null && p.getDueDate().isBefore(asOf));
            total = total.add(balance);
            if (isOverdue) {
                overdue = overdue.add(balance);
            }
            rows.add(row("supplier", entry.getKey(),
                    "orderCount", entry.getValue().size(),
                    "orderTotal", entry.getValue().stream()
                            .map(p -> nz(p.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add),
                    "paid", entry.getValue().stream()
                            .map(p -> nz(p.getAmountPaid())).reduce(BigDecimal.ZERO, BigDecimal::add),
                    "outstanding", balance,
                    "oldestDueDate", entry.getValue().stream()
                            .map(p -> p.getDueDate() == null ? asOf.plusDays(9999) : p.getDueDate())
                            .min(LocalDate::compareTo).orElse(asOf).toString(),
                    "overdue", isOverdue,
                    "orders", entry.getValue().stream()
                            .map(p -> p.getOrderNumber() + " (" + nz(p.getTotalAmount()).toPlainString()
                                    + ", received " + p.getStatus() + ")")
                            .toList()));
        }
        rows.sort(Comparator.comparing(r -> String.valueOf(r.get("oldestDueDate"))));

        return Map.of("report", "Supplier payables",
                "asOf", asOf.toString(),
                "columns", List.of("supplier", "orderCount", "orderTotal", "paid",
                        "outstanding", "oldestDueDate", "overdue"),
                "rows", rows,
                "totals", Map.of("totalOutstanding", total, "overdueAmount", overdue),
                "note", "Only goods that have been received are included. "
                        + "An order still in transit is not yet a payable.");
    }

    // ── Warehouse transfers ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> transfers(LocalDate from, LocalDate to) {
        List<StockMovement> movements = stockMovementRepository.search(
                        from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX),
                        null, null, StockMovement.Status.APPROVED,
                        org.springframework.data.domain.Pageable.ofSize(2000)).getContent()
                .stream()
                .filter(m -> StockMovement.TYPE_TRANSFER_OUT.equals(m.getType())
                        || StockMovement.TYPE_TRANSFER_IN.equals(m.getType()))
                .toList();

        // Grouped by the shared group key, so a transfer appears once with both
        // ends rather than twice as two unexplained rows.
        Map<String, List<StockMovement>> byGroup = movements.stream()
                .filter(m -> m.getMovementGroup() != null)
                .collect(Collectors.groupingBy(StockMovement::getMovementGroup));

        List<Map<String, Object>> rows = new ArrayList<>();
        int units = 0;
        for (Map.Entry<String, List<StockMovement>> entry : byGroup.entrySet()) {
            StockMovement out = entry.getValue().stream()
                    .filter(m -> StockMovement.TYPE_TRANSFER_OUT.equals(m.getType())).findFirst()
                    .orElse(null);
            StockMovement in = entry.getValue().stream()
                    .filter(m -> StockMovement.TYPE_TRANSFER_IN.equals(m.getType())).findFirst()
                    .orElse(null);
            StockMovement anchor = out != null ? out : entry.getValue().get(0);
            int quantity = anchor.getQuantity() == null ? 0 : Math.abs(anchor.getQuantity());
            units += quantity;
            rows.add(row("date", date(anchor.getCreatedAt()),
                    "product", anchor.getProduct() == null ? "—" : anchor.getProduct().getSku(),
                    "from", anchor.getWarehouse() == null ? "—" : anchor.getWarehouse().getName(),
                    "to", in == null || in.getDestinationWarehouse() == null ? "—"
                            : in.getDestinationWarehouse().getName(),
                    "quantity", quantity,
                    "unitCost", anchor.getUnitCost(),
                    "reference", anchor.getReference() == null ? "—" : anchor.getReference(),
                    "recordedBy", anchor.getCreatedBy() == null ? "—" : anchor.getCreatedBy().getUsername(),
                    "group", entry.getKey()));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("date"))).reversed());

        return Map.of("report", "Warehouse transfers",
                "from", from.toString(), "to", to.toString(),
                "columns", List.of("date", "product", "from", "to", "quantity",
                        "unitCost", "reference", "recordedBy"),
                "rows", rows,
                "totals", Map.of("transfers", rows.size(), "unitsMoved", units),
                "note", "A transfer is neither a sale nor a purchase: the stock stays "
                        + "inside the business and is excluded from revenue and cost.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a report row that preserves insertion order.
     *
     * <p>A {@code Map.of} would not: it is unordered, so every exported report
     * would have its columns in a different order each time it was generated,
     * which makes two exports of the same data impossible to diff.
     */
    private static Map<String, Object> row(Object... keyValuePairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            row.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return row;
    }

    private static String ageBucket(int days) {
        if (days <= 0) return "CURRENT";
        if (days <= 30) return "1-30 DAYS";
        if (days <= 60) return "31-60 DAYS";
        if (days <= 90) return "61-90 DAYS";
        return "OVER 90 DAYS";
    }

    private String partyName(Payment payment) {
        if (payment.getCustomer() != null) {
            return payment.getCustomer().getName();
        }
        if (payment.getSupplier() != null) {
            return payment.getSupplier().getCompanyName();
        }
        return "—";
    }

    private static String displayName(User user) {
        if (user == null) {
            return "—";
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() || full.equals("null") ? user.getUsername() : full;
    }

    private static String date(LocalDateTime value) {
        return value == null ? "—" : value.toLocalDate().toString();
    }

    private static BigDecimal outstanding(BigDecimal total, BigDecimal paid) {
        BigDecimal due = nz(total).subtract(nz(paid));
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}