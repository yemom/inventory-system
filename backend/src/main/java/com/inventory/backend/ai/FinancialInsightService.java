package com.inventory.backend.ai;

import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialCalculations;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.Customer;
import com.inventory.backend.model.PurchaseOrder;
import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.model.SaleOrderItem;
import com.inventory.backend.repository.CustomerRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.PurchaseOrderRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Explains the numbers, using only figures the calculation layer produced.
 *
 * <h2>No number originates here</h2>
 *
 * <p>Every amount in an explanation is read from a {@link FinancialSummary} or
 * from a validated aggregate. This service compares them and writes sentences
 * about them; it never adds up a different set of numbers to reach a different
 * answer. That is the whole point of a central calculation layer: the dashboard,
 * the report, the assistant and these explanations cannot disagree, because none
 * of them computes anything of its own.
 *
 * <h2>Explanations state their assumptions</h2>
 *
 * <p>Each insight carries the date range it covers, the comparison it used and
 * the source report to open. Where the data cannot support a conclusion the
 * insight says so instead of offering a plausible guess — "no expenses recorded
 * last period, so this cannot be compared" is a better answer than a percentage
 * change computed against zero.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialInsightService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final FinancialCalculationService calculations;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ForecastingService forecastingService;
    private final InventoryOptimizationService inventoryService;

    /**
     * The insight set for a period: what changed, why, and what needs attention.
     */
    @Transactional(readOnly = true)
    public List<Insight> insightsFor(LocalDate from, LocalDate to) {
        FinancialSummary current = calculations.summarise(from, to);
        FinancialSummary previous = calculations.previousPeriod(from, to);

        List<Insight> insights = new ArrayList<>();

        if (current.insufficientData()) {
            insights.add(new Insight(
                    "DATA",
                    "Not enough recorded activity",
                    "There are no sales, returns, expenses or received purchases recorded between "
                            + from.format(DATE) + " and " + to.format(DATE)
                            + ", so no conclusion can be drawn about this period. "
                            + "Nothing here is being reported as a change, because there is nothing to compare against.",
                    Severity.INFO,
                    Map.of("from", from.toString(), "to", to.toString(),
                            "basis", current.basis()),
                    "/reports/sales"));
            return insights;
        }

        insights.addAll(explainProfitChange(current, previous, from, to));
        insights.addAll(explainExpenseChange(current, previous));
        insights.addAll(topProfitableProducts(from, to));
        insights.addAll(overdueReceivables());
        insights.addAll(payableObligations());
        insights.addAll(cashPosition(current, previous));

        return insights;
    }

    /**
     * Just the profit decomposition, for the assistant's "why did profit change"
     * question.
     *
     * <p>Separate from {@link #insightsFor} because the assistant needs the one
     * explanation with its evidence, not the whole insight set — the dashboard
     * shows everything, the assistant answers what was asked.
     */
    @Transactional(readOnly = true)
    public List<Insight> explainProfitChangeOnly(LocalDate from, LocalDate to) {
        FinancialSummary current = calculations.summarise(from, to);
        if (current.insufficientData()) {
            return List.of();
        }
        FinancialSummary previous = calculations.previousPeriod(from, to);
        return explainProfitChange(current, previous, from, to);
    }

    // ── Profit ────────────────────────────────────────────────────────────────

    /**
     * "Why did profit move?" answered by decomposing the move.
     *
     * <p>Gross profit is revenue minus cost of goods, and net profit is gross
     * profit minus expenses. Each component's change is named separately, so the
     * answer distinguishes "we sold less" from "the goods cost more" from "our
     * overheads rose" — which need entirely different responses.
     */
    private List<Insight> explainProfitChange(FinancialSummary current, FinancialSummary previous,
                                              LocalDate from, LocalDate to) {
        List<Insight> insights = new ArrayList<>();

        BigDecimal netSalesChange = FinancialCalculations.change(current.netSales(), previous.netSales());
        BigDecimal cogsChange = FinancialCalculations.change(current.costOfGoodsSold(),
                previous.costOfGoodsSold());
        BigDecimal expenseChange = FinancialCalculations.change(current.operatingExpenses(),
                previous.operatingExpenses());
        BigDecimal profitChange = FinancialCalculations.change(current.netProfit(), previous.netProfit());

        StringBuilder explanation = new StringBuilder();
        Map<String, Object> evidence = new LinkedHashMap<>();

        if (netSalesChange == null) {
            return insights;
        }

        if (netSalesChange.signum() > 0) {
            explanation.append(String.format(
                    "Net sales rose by %s (from %s to %s). ",
                    money(netSalesChange), money(previous.netSales()), money(current.netSales())));
        } else if (netSalesChange.signum() < 0) {
            explanation.append(String.format(
                    "Net sales fell by %s (from %s to %s). ",
                    money(netSalesChange.abs()), money(previous.netSales()), money(current.netSales())));
        } else {
            explanation.append("Net sales were unchanged. ");
        }

        if (cogsChange.signum() > 0) {
            explanation.append(String.format(
                    "The cost of the goods sold rose by %s, ", money(cogsChange)));
        } else if (cogsChange.signum() < 0) {
            explanation.append(String.format(
                    "The cost of the goods sold fell by %s, ", money(cogsChange.abs())));
        }

        if (expenseChange.signum() > 0) {
            explanation.append(String.format(
                    "and operating expenses rose by %s. ", money(expenseChange)));
        } else if (expenseChange.signum() < 0) {
            explanation.append(String.format(
                    "and operating expenses fell by %s. ", money(expenseChange.abs())));
        }

        explanation.append(String.format(
                "Net profit for the period is %s (margin %s%%), %s the previous period.",
                money(current.netProfit()), current.netMarginPercent().toPlainString(),
                profitChange.signum() >= 0 ? "up from" : "down from"));
        explanation.append(String.format(" %s.",
                money(profitChange.abs())));

        evidence.put("netSales", current.netSales());
        evidence.put("netSalesPrevious", previous.netSales());
        evidence.put("netSalesChange", netSalesChange);
        evidence.put("netSalesPercentChange",
                FinancialCalculations.percentChange(current.netSales(), previous.netSales()));
        evidence.put("costOfGoodsSold", current.costOfGoodsSold());
        evidence.put("costOfGoodsSoldChange", cogsChange);
        evidence.put("operatingExpenses", current.operatingExpenses());
        evidence.put("operatingExpensesChange", expenseChange);
        evidence.put("netProfit", current.netProfit());
        evidence.put("netProfitChange", profitChange);
        evidence.put("previousPeriod", previous.from().format(DATE) + " to " + previous.to().format(DATE));
        evidence.put("salesCount", current.salesCount());
        evidence.put("itemsSold", current.itemsSold());
        evidence.put("basis", current.basis());

        boolean profitDown = profitChange.signum() < 0;
        insights.add(new Insight(
                profitDown ? "PROFIT_DECLINED" : "PROFIT",
                profitDown ? "Net profit decreased" : "Profit movement",
                explanation.toString(),
                profitDown ? Severity.WARNING : Severity.INFO,
                evidence,
                "/finance/profit-loss"));
        return insights;
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    private List<Insight> explainExpenseChange(FinancialSummary current, FinancialSummary previous) {
        List<Insight> insights = new ArrayList<>();
        BigDecimal change = FinancialCalculations.change(current.operatingExpenses(),
                previous.operatingExpenses());
        if (change == null || previous.operatingExpenses().signum() == 0) {
            if (current.operatingExpenses().signum() > 0) {
                insights.add(new Insight("EXPENSES", "Operating expenses",
                        String.format("Approved operating expenses for this period total %s. "
                                        + "No expenses were recorded in the previous period, so no change "
                                        + "can be calculated.",
                                money(current.operatingExpenses())),
                        Severity.INFO,
                        Map.of("operatingExpenses", current.operatingExpenses(),
                                "categories", current.expensesByCategory().stream()
                                        .map(c -> Map.of("category", c.category(),
                                                "amount", c.amount()))
                                        .toList()),
                        "/finance/expenses"));
            }
            return insights;
        }

        BigDecimal percentChange = FinancialCalculations.percentChange(
                current.operatingExpenses(), previous.operatingExpenses());

        StringBuilder breakdown = new StringBuilder();
        for (FinancialSummary.CategoryTotal category : current.expensesByCategory()) {
            breakdown.append(String.format("%s %s; ", category.category(), money(category.amount())));
        }

        insights.add(new Insight("EXPENSES",
                change.signum() > 0 ? "Operating expenses increased" : "Operating expenses decreased",
                String.format("Operating expenses %s by %s (%s), from %s to %s. Breakdown: %s",
                        change.signum() > 0 ? "rose" : "fell",
                        money(change.abs()),
                        percentChange == null ? "no comparable prior figure"
                                : percentChange.toPlainString() + "%",
                        money(previous.operatingExpenses()), money(current.operatingExpenses()),
                        breakdown.toString().trim()),
                change.signum() > 0 && percentChange != null
                        && percentChange.abs().compareTo(BigDecimal.valueOf(25)) >= 0
                        ? Severity.WARNING : Severity.INFO,
                Map.of("operatingExpenses", current.operatingExpenses(),
                        "previousOperatingExpenses", previous.operatingExpenses(),
                        "change", change,
                        "percentChange", percentChange == null ? "not comparable" : percentChange.toPlainString(),
                        "categories", current.expensesByCategory().stream()
                                .map(c -> Map.of("category", c.category(), "amount", c.amount()))
                                .toList(),
                        "expenseCount", current.expenseCount()),
                "/finance/expenses"));
        return insights;
    }

    // ── Product profitability ─────────────────────────────────────────────────

    /**
     * Which products actually made money.
     *
     * <p>Ranked by gross profit rather than by revenue, because the best-selling
     * product is frequently not the most profitable one and reordering on revenue
     * alone is how a business ends up stocking what it does not profit from.
     */
    @Transactional(readOnly = true)
    public List<Insight> topProfitableProducts(LocalDate from, LocalDate to) {
        List<Object[]> rows = saleOrderRepository.summariseByProduct(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, String> names = productRepository.findAll().stream()
                .collect(Collectors.toMap(p -> p.getId(), p -> p.getSku() + " — " + p.getName()));

        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Object[] row : rows) {
            long productId = ((Number) row[0]).longValue();
            double units = ((Number) row[1]).doubleValue();
            double revenue = ((Number) row[2]).doubleValue();
            double cost = ((Number) row[3]).doubleValue();
            ranked.add(Map.of(
                    "productId", productId,
                    "product", names.getOrDefault(productId, "product " + productId),
                    "unitsSold", (long) units,
                    "revenue", revenue,
                    "costOfGoodsSold", cost,
                    "grossProfit", revenue - cost,
                    "marginPercent", revenue == 0 ? 0
                            : Math.round((revenue - cost) / revenue * 10000) / 100.0));
        }
        ranked.sort((a, b) -> Double.compare((Double) b.get("grossProfit"), (Double) a.get("grossProfit")));

        List<Map<String, Object>> top = ranked.subList(0, Math.min(5, ranked.size()));

        StringBuilder text = new StringBuilder("The five products contributing most gross profit in this period are: ");
        for (int i = 0; i < top.size(); i++) {
            Map<String, Object> row = top.get(i);
            text.append(String.format("%d) %s (%s, %s%% margin); ",
                    i + 1, row.get("product"), money((Double) row.get("grossProfit")),
                    row.get("marginPercent")));
        }

        List<Map<String, Object>> lossMakers = ranked.stream()
                .filter(r -> (Double) r.get("grossProfit") < 0)
                .limit(3)
                .toList();
        if (!lossMakers.isEmpty()) {
            text.append("Note: ");
            for (int i = 0; i < lossMakers.size(); i++) {
                Map<String, Object> row = lossMakers.get(i);
                text.append(String.format("%s is selling below cost (%s margin)",
                        row.get("product"), money((Double) row.get("grossProfit"))));
                text.append(i < lossMakers.size() - 1 ? ", " : ".");
            }
        }

        return List.of(new Insight("PRODUCTS", "Products by gross profit",
                text.toString(),
                lossMakers.isEmpty() ? Severity.INFO : Severity.WARNING,
                Map.of("topProducts", top,
                        "lossMakingProducts", lossMakers,
                        "period", from.format(DATE) + " to " + to.format(DATE)),
                "/reports/sales"));
    }

    // ── Balances ──────────────────────────────────────────────────────────────

    /**
     * Customers who owe money, and how long.
     *
     * <p>Ageing is computed from the sale's own date. A balance's age is a fact
     * about when the debt was incurred, and using the payment date would make a
     * debt look fresh simply because somebody recently paid part of it.
     */
    @Transactional(readOnly = true)
    public List<Insight> overdueReceivables() {
        List<SaleOrder> outstanding = saleOrderRepository.findAllOutstandingReceivables();
        if (outstanding.isEmpty()) {
            return List.of();
        }

        Map<String, BigDecimal> byCustomer = new LinkedHashMap<>();
        Map<String, Integer> ageByCustomer = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (SaleOrder sale : outstanding) {
            BigDecimal due = FinancialCalculations.outstanding(sale.getFinalAmount(), sale.getAmountPaid());
            if (due.signum() <= 0) {
                continue;
            }
            String key = sale.getCustomer() != null
                    ? sale.getCustomer().getName()
                    : (sale.getCustomerName() == null ? "Unattributed" : sale.getCustomerName());
            byCustomer.merge(key, due, BigDecimal::add);

            LocalDateTime saleDate = sale.getTransactionDate() != null
                    ? sale.getTransactionDate() : sale.getCreatedAt();
            if (saleDate != null) {
                int days = (int) ChronoUnit.DAYS.between(saleDate.toLocalDate(), now.toLocalDate());
                ageByCustomer.merge(key, days, Math::max);
            }
        }

        BigDecimal total = byCustomer.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> rows = byCustomer.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "customer", e.getKey(),
                        "outstanding", e.getValue(),
                        "oldestDays", ageByCustomer.getOrDefault(e.getKey(), 0)))
                .sorted((a, b) -> Integer.compare((Integer) b.get("oldestDays"),
                        (Integer) a.get("oldestDays")))
                .toList();

        List<Map<String, Object>> overdue = rows.stream()
                .filter(r -> ((Integer) r.get("oldestDays")) > 30)
                .toList();

        String message;
        Severity severity;
        if (overdue.isEmpty()) {
            message = String.format("Customers owe %s in total across %d open invoices, none older than 30 days.",
                    money(total), rows.size());
            severity = Severity.INFO;
        } else {
            message = String.format("Customers owe %s in total. %d account%s been outstanding for more than 30 days: %s.",
                    money(total), overdue.size(), overdue.size() == 1 ? " has" : "s have",
                    overdue.stream().limit(3)
                            .map(r -> r.get("customer") + " (" + money((BigDecimal) r.get("outstanding"))
                                    + ", " + r.get("oldestDays") + " days)")
                            .collect(Collectors.joining(", ")));
            severity = Severity.WARNING;
        }

        return List.of(new Insight("RECEIVABLES",
                overdue.isEmpty() ? "Customer balances" : "Overdue receivables",
                message, severity,
                Map.of("totalOutstanding", total, "accounts", rows,
                        "thresholdDays", 30),
                "/finance/receivables"));
    }

    /** What the business owes suppliers, and what falls due soon. */
    @Transactional(readOnly = true)
    public List<Insight> payableObligations() {
        List<PurchaseOrder> outstanding =
                purchaseOrderRepository.findOutstandingPayables(null);
        if (outstanding.isEmpty()) {
            return List.of();
        }

        BigDecimal total = outstanding.stream()
                .map(po -> FinancialCalculations.payable(po.getTotalAmount(), po.getAmountPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> dueSoon = outstanding.stream()
                .filter(po -> po.getDueDate() != null)
                .filter(po -> !po.getDueDate().isBefore(today)
                        && po.getDueDate().isBefore(today.plusDays(14)))
                .map(po -> Map.<String, Object>of(
                        "order", po.getOrderNumber(),
                        "supplier", po.getSupplierName() == null ? "unknown" : po.getSupplierName(),
                        "balanceDue", po.getBalanceDue(),
                        "dueDate", po.getDueDate().toString()))
                .toList();

        String message = String.format("Suppliers are owed %s across %d received purchase order%s.",
                money(total), outstanding.size(), outstanding.size() == 1 ? "" : "s");
        if (!dueSoon.isEmpty()) {
            message += String.format(" %d fall%s due in the next 14 days.",
                    dueSoon.size(), dueSoon.size() == 1 ? "s" : "");
        }

        return List.of(new Insight("PAYABLES", "Supplier payment obligations",
                message, dueSoon.isEmpty() ? Severity.INFO : Severity.WARNING,
                Map.of("totalOutstanding", total,
                        "orderCount", outstanding.size(),
                        "dueWithin14Days", dueSoon),
                "/finance/payables"));
    }

    // ── Cash ──────────────────────────────────────────────────────────────────

    /**
     * Cash against revenue.
     *
     * <p>The gap between the two is the number a business actually feels. Sales
     * of 100,000 with 40,000 collected is revenue of 100,000 and 60,000 of
     * working capital tied up in other people's hands; reporting only the first
     * hides the second entirely.
     */
    private List<Insight> cashPosition(FinancialSummary current, FinancialSummary previous) {
        BigDecimal revenueMinusCash = current.netSales().subtract(current.cashReceived());
        if (revenueMinusCash.signum() <= 0) {
            return List.of();
        }
        BigDecimal ratio = current.netSales().signum() == 0 ? BigDecimal.ZERO
                : revenueMinusCash.multiply(BigDecimal.valueOf(100))
                        .divide(current.netSales().abs(), 1, java.math.RoundingMode.HALF_UP);

        return List.of(new Insight("CASH",
                "Sales not yet collected",
                String.format("Net sales for the period were %s but only %s has actually been received "
                                + "in cash — %s (%s%% of the period's sales) is still outstanding. "
                                + "Total receivables stand at %s.",
                        money(current.netSales()), money(current.cashReceived()),
                        money(revenueMinusCash), ratio.toPlainString(),
                        money(current.accountsReceivable())),
                ratio.compareTo(BigDecimal.valueOf(30)) >= 0 ? Severity.WARNING : Severity.INFO,
                Map.of("netSales", current.netSales(),
                        "cashReceived", current.cashReceived(),
                        "notYetCollected", revenueMinusCash,
                        "percentOfSales", ratio.toPlainString(),
                        "accountsReceivable", current.accountsReceivable(),
                        "previousCashReceived", previous.cashReceived(),
                        "cashPaidToSuppliers", current.cashPaidToSuppliers(),
                        "cashRefunded", current.cashRefunded(),
                        "netCashFlow", current.netCashFlow()),
                "/finance/payments"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return String.format(java.util.Locale.ROOT, "%,.2f", value);
    }

    /**
     * Money from a double, for the product-ranking aggregates that come out of
     * SQL as numbers.
     *
     * <p>Null when the aggregate is absent rather than 0.00: "we do not have this
     * figure" and "this figure is zero" are different facts, and an average
     * silently coerced to zero would be read as a real number.
     */
    private static String money(Double value) {
        return value == null ? "not available" : String.format(java.util.Locale.ROOT, "%,.2f", value);
    }

    /**
     * One explanation.
     *
     * @param category  what sort of thing this is, for filtering
     * @param headline  a short label for a card
     * @param message   the explanation in plain language
     * @param severity  how urgently it needs a look
     * @param evidence  the figures and records the sentence was built from
     * @param sourceUrl the report that shows the same figures
     */
    public record Insight(String category, String headline, String message,
                          Severity severity, Map<String, Object> evidence,
                          String sourceUrl) {
    }

    /** How loudly an insight should be presented. Never "error" — these are observations. */
    public enum Severity {
        /** A normal fact about the business. */
        INFO,
        /** Worth checking this week. */
        WARNING,
        /** Worth checking today: data is missing or a figure could not be produced. */
        CRITICAL
    }
}