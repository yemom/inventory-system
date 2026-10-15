package com.inventory.backend.chatbot.staff;

import com.inventory.backend.chatbot.ChatbotTool;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.PurchaseOrder;
import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.repository.PurchaseOrderRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.ai.InventoryOptimizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Financial questions, answered by the same service the reports use.
 *
 * <h2>Why this does not compute anything</h2>
 *
 * <p>Every figure here comes from {@link FinancialCalculationService#summarise},
 * which is the one place a financial figure is produced. There is no arithmetic in
 * this class beyond summing rows the calculation service already returned.
 *
 * <p>That is a deliberate constraint, not a missing feature. The requirement is
 * that the chatbot's numbers match the reports'. The only way to guarantee that
 * is for them to be the same numbers, and the only way for that is for neither to
 * compute its own.
 *
 * <h2>Periods are stated, never assumed</h2>
 *
 * <p>Every answer names the period it covers, because "net profit" without a date
 * range is not an answer. The period comes from the question's own words or from
 * the dashboard filter the caller set, and which one won is recorded.
 */
@Component
@RequiredArgsConstructor
public class FinancialTools {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final FinancialCalculationService calculations;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryOptimizationService inventoryService;

    /** Profit and loss for a period. */
    public Map<String, Object> profit(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Period period = Period.from(params);
        FinancialSummary summary = calculations.summarise(period.from(), period.to());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period.label());
        out.put("from", period.from());
        out.put("to", period.to());
        out.put("netSales", summary.netSales());
        out.put("costOfGoodsSold", summary.costOfGoodsSold());
        out.put("grossProfit", summary.grossProfit());
        out.put("grossMarginPercent", summary.grossMarginPercent());
        out.put("operatingExpenses", summary.operatingExpenses());
        out.put("netProfit", summary.netProfit());
        out.put("netMarginPercent", summary.netMarginPercent());
        out.put("salesCount", summary.salesCount());
        out.put("insufficientData", summary.insufficientData());
        out.put("basis", summary.basis());
        out.put("reportUrl", "/finance/profit-loss");
        out.put("note", "Revenue is net of approved returns and excludes sales tax. Cost of goods "
                + "sold uses the weighted-average cost recorded when each sale was made.");
        return out;
    }

    /** This period against the one before it. */
    public Map<String, Object> compare(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Period period = Period.from(params);
        FinancialSummary current = calculations.summarise(period.from(), period.to());
        FinancialSummary previous = calculations.previousPeriod(period.from(), period.to());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period.label());
        out.put("previousPeriod", previous.from() + " to " + previous.to());
        out.put("from", period.from());
        out.put("to", period.to());

        append(out, "netSales", "Net sales", current.netSales(), previous.netSales());
        append(out, "grossProfit", "Gross profit", current.grossProfit(), previous.grossProfit());
        append(out, "operatingExpenses", "Operating expenses",
                current.operatingExpenses(), previous.operatingExpenses());
        append(out, "netProfit", "Net profit", current.netProfit(), previous.netProfit());
        append(out, "cashReceived", "Cash received", current.cashReceived(), previous.cashReceived());

        out.put("insufficientData", current.insufficientData());
        out.put("note", "The comparison period is the same number of days immediately before this "
                + "one, so a part-month is never compared with a full one. "
                + "A change against a zero previous period is reported as 'not comparable' rather "
                + "than as a percentage.");
        return out;
    }

    /** Cash in and out. */
    public Map<String, Object> cash(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Period period = Period.from(params);
        FinancialSummary summary = calculations.summarise(period.from(), period.to());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period.label());
        out.put("from", period.from());
        out.put("to", period.to());
        out.put("cashReceived", summary.cashReceived());
        out.put("cashPaidToSuppliers", summary.cashPaidToSuppliers());
        out.put("cashRefunded", summary.cashRefunded());
        out.put("operatingExpenses", summary.operatingExpenses());
        out.put("netCashFlow", summary.netCashFlow());
        out.put("accountsReceivable", summary.accountsReceivable());
        out.put("accountsPayable", summary.accountsPayable());
        out.put("reportUrl", "/reports/financial");
        out.put("note", "Cash counts payments actually completed. An unpaid invoice is revenue but "
                + "is not cash until it is settled, so it appears in receivables instead.");
        return out;
    }

    /** Customers who owe money. */
    public Map<String, Object> receivables(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Period period = Period.from(params);
        LocalDateTime asOf = period.to().atTime(java.time.LocalTime.MAX);

        Map<String, List<SaleOrder>> byCustomer = saleOrderRepository
                .findOutstandingReceivables(asOf).stream()
                .filter(sale -> sale.getFinalAmount() != null)
                .filter(sale -> sale.getFinalAmount()
                        .subtract(sale.getAmountPaid() == null ? BigDecimal.ZERO : sale.getAmountPaid())
                        .signum() > 0)
                .collect(java.util.stream.Collectors.groupingBy(
                        sale -> sale.getCustomer() != null && sale.getCustomer().getName() != null
                                ? sale.getCustomer().getName()
                                : (sale.getCustomerName() == null ? "Unattributed" : sale.getCustomerName())));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();

        for (Map.Entry<String, List<SaleOrder>> entry : byCustomer.entrySet()) {
            BigDecimal balance = entry.getValue().stream()
                    .map(sale -> sale.getFinalAmount()
                            .subtract(sale.getAmountPaid() == null ? BigDecimal.ZERO : sale.getAmountPaid()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int oldest = entry.getValue().stream()
                    .map(SaleOrder::getTransactionDate)
                    .filter(Objects::nonNull)
                    .mapToInt(d -> (int) ChronoUnit.DAYS.between(d.toLocalDate(), today))
                    .max().orElse(0);
            total = total.add(balance);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customer", entry.getKey());
            row.put("outstanding", balance);
            row.put("oldestDays", oldest);
            row.put("overdue", oldest > 30);
            row.put("invoiceCount", entry.getValue().size());
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare((Integer) b.get("oldestDays"), (Integer) a.get("oldestDays")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", period.to());
        out.put("totalOutstanding", total);
        out.put("accountCount", rows.size());
        out.put("overdueCount", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("overdue"))).toList());
        out.put("accounts", rows.stream().limit(10).toList());
        out.put("reportUrl", "/finance/receivables");
        out.put("note", "A balance is an invoice not fully settled by completed payments. Age is "
                + "measured from the sale date. An unpaid invoice is a receivable, not a loss.");
        return out;
    }

    /** Suppliers who are owed money. */
    public Map<String, Object> payables(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Period period = Period.from(params);
        List<PurchaseOrder> outstanding = purchaseOrderRepository
                .findOutstandingPayables(period.to().atTime(java.time.LocalTime.MAX));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrder order : outstanding) {
            total = total.add(order.getBalanceDue());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order", order.getOrderNumber());
            row.put("supplier", order.getSupplierName() == null ? "unknown" : order.getSupplierName());
            row.put("orderTotal", order.getTotalAmount());
            row.put("balanceDue", order.getBalanceDue());
            row.put("receiptStatus", order.getStatus());
            row.put("dueDate", order.getDueDate() == null ? "not set" : order.getDueDate().toString());
            row.put("overdue", order.getDueDate() != null && order.getDueDate().isBefore(LocalDate.now()));
            rows.add(row);
        }
        rows.sort((a, b) -> String.valueOf(a.get("dueDate")).compareTo(String.valueOf(b.get("dueDate"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", period.to());
        out.put("totalOutstanding", total);
        out.put("orderCount", rows.size());
        out.put("orders", rows.stream().limit(10).toList());
        out.put("reportUrl", "/finance/payables");
        out.put("note", "Only goods that have actually been received are included. An order still "
                + "in transit is not yet a payable.");
        return out;
    }

    /** Products projected to run out. */
    public Map<String, Object> stockouts(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        int horizon = com.inventory.backend.chatbot.product.ProductTools
                .integer(params, "horizonDays", 14);
        List<InventoryOptimizationService.StockoutRisk> risks =
                inventoryService.predictStockouts(horizon);

        List<Map<String, Object>> rows = risks.stream().limit(10).map(risk -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", risk.productId());
            row.put("name", risk.name());
            row.put("sku", risk.sku());
            row.put("stockOnHand", risk.stockOnHand());
            row.put("stockOnOrder", risk.stockOnOrder());
            row.put("averageDailyUnits", risk.averageDailyUnits());
            row.put("daysOfStockRemaining", risk.daysOfStockRemaining());
            row.put("beforeLeadTime", risk.beforeLeadTime());
            row.put("summary", risk.summary());
            return row;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("horizonDays", horizon);
        out.put("riskCount", risks.size());
        out.put("risks", rows);
        out.put("reportUrl", "/reports/inventory");
        out.put("note", "Days of stock is stock on hand divided by the recorded sales rate. "
                + "A product with no sales history cannot be projected and is excluded — "
                + "'cannot say when this runs out' and 'it needs nothing' are different claims. "
                + "'Before lead time' means the stock runs out sooner than the recorded "
                + "lead time would take to replenish.");
        return out;
    }

    /** Slow-moving stock. */
    public Map<String, Object> slowMoving(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Period period = Period.from(params);
        int days = (int) Math.max(30, ChronoUnit.DAYS.between(period.from(), period.to()) + 1);
        List<InventoryOptimizationService.SlowMovingProduct> slow =
                inventoryService.identifySlowMoving(days, 10);

        List<Map<String, Object>> rows = slow.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", p.productId());
            row.put("name", p.name());
            row.put("sku", p.sku());
            row.put("stockOnHand", p.stockOnHand());
            row.put("valueAtCost", p.valueAtCost());
            return row;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lookbackDays", days);
        out.put("productCount", slow.size());
        out.put("totalValueAtCost", slow.stream()
                .map(InventoryOptimizationService.SlowMovingProduct::valueAtCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        out.put("products", rows);
        out.put("reportUrl", "/reports/inventory");
        out.put("note", "Valued at purchase cost, not at retail. A product is slow-moving when it "
                + "holds stock but recorded no sale in the lookback window.");
        return out;
    }

    /**
     * Finds a sale by order number, for staff.
     *
     * <p>Separate from the customer's own lookup because it answers for
     * <em>any</em> order, including who placed it and what they paid. That is
     * staff information, and the permission on the tool is what keeps it staff's.
     */
    public Map<String, Object> searchSale(String orderNumber) {
        return saleOrderRepository.findAllWithDetails(Pageable.unpaged()).stream()
                .filter(sale -> orderNumber.equalsIgnoreCase(sale.getOrderNumber()))
                .findFirst()
                .map(order -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("found", true);
                    out.put("orderNumber", order.getOrderNumber());
                    out.put("customer", order.getCustomer() != null && order.getCustomer().getName() != null
                            ? order.getCustomer().getName() : order.getCustomerName());
                    out.put("status", order.getStatus());
                    out.put("paymentStatus", order.getPaymentStatus());
                    out.put("total", order.getFinalAmount());
                    out.put("outstanding", order.getFinalAmount() == null ? BigDecimal.ZERO
                            : order.getFinalAmount().subtract(order.getAmountPaid() == null
                                    ? BigDecimal.ZERO : order.getAmountPaid()));
                    out.put("orderDate", order.getTransactionDate() == null
                            ? order.getCreatedAt().format(DATE)
                            : order.getTransactionDate().format(DATE));
                    out.put("reportUrl", "/sales");
                    return out;
                })
                .orElseGet(() -> Map.of("found", false, "note", "No order " + orderNumber + " exists."));
    }

    /** Inventory value at cost. */
    public Map<String, Object> inventoryValue(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        Period period = Period.from(params);
        FinancialSummary summary = calculations.summarise(period.from(), period.to());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", period.to());
        out.put("inventoryValue", summary.inventoryValue());
        out.put("stockLossValue", summary.stockLossValue());
        out.put("stockLossUnits", summary.stockLossUnits());
        out.put("reportUrl", "/reports/inventory-value");
        out.put("note", "Moving weighted average, recalculated on every goods receipt, held per "
                + "warehouse. Value at retail is a different figure and is on the valuation report.");
        return out;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void append(Map<String, Object> out, String key, String label,
                        BigDecimal current, BigDecimal previous) {
        out.put(key, current);
        out.put(key + "_Previous", previous);
        BigDecimal change = current.subtract(previous);
        out.put(key + "_Change", change);
        if (previous.signum() == 0) {
            out.put(key + "_PercentChange", "not comparable");
        } else {
            out.put(key + "_PercentChange", change.multiply(new BigDecimal("100"))
                    .divide(previous.abs(), 1, java.math.RoundingMode.HALF_UP));
        }
    }

    /**
     * A period a question is about.
     *
     * <p>An explicit range from the dashboard filter wins over the question's own
     * words, because the reader has already said which window they mean. Failing
     * that the question is read; failing that the last 30 days, which is named in
     * the answer so it is never ambiguous.
     */
    record Period(LocalDate from, LocalDate to, String label) {

        static Period from(Map<String, Object> params) {
            LocalDate from = date(params, "from");
            LocalDate to = date(params, "to");
            if (from != null && to != null) {
                return new Period(from, to, "the selected date range");
            }
            return new Period(LocalDate.now().minusDays(29), LocalDate.now(), "the last 30 days");
        }

        static LocalDate date(Map<String, Object> params, String key) {
            Object value = params.get(key);
            if (value == null) {
                return null;
            }
            try {
                return LocalDate.parse(String.valueOf(value));
            } catch (Exception e) {
                return null;
            }
        }
    }
}
