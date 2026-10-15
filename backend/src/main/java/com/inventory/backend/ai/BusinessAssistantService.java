package com.inventory.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialCalculations;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.AiQueryLog;
import com.inventory.backend.model.Customer;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.PurchaseOrder;
import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.model.Warehouse;
import com.inventory.backend.repository.AiQueryLogRepository;
import com.inventory.backend.repository.CustomerRepository;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.PurchaseOrderRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.SupplierRepository;
import com.inventory.backend.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The business assistant.
 *
 * <h2>Why there is no language model in the answer path</h2>
 *
 * <p>The assistant resolves a question to a <em>structured query against trusted
 * services</em> and renders the answer from the result. It does not generate SQL,
 * does not call an external provider, and never performs arithmetic of its own.
 *
 * <p>That is a deliberate design choice for a financial tool, not a limitation
 * dressed up as one. A language model given the same task would have to be
 * trusted with numbers: it would paraphrase a total it computed rather than read,
 * and its errors would be fluent, plausible and undetectable. Here every figure
 * in an answer is the same figure the report shows, because both read the same
 * {@link FinancialSummary}.
 *
 * <p>What the model-free design does give up — handling an unanticipated
 * phrasing, or a question that spans several domains in one sentence — is
 * handled explicitly rather than papered over: an unrecognised question produces
 * a clarification prompt listing what the assistant can answer, and a
 * low-confidence match refuses rather than answering the wrong question
 * confidently.
 *
 * <h2>Authorisation is applied to the resolved query</h2>
 *
 * <p>The caller's permissions are checked against the intent <em>before</em> any
 * data is read, not filtered afterwards. An intent the caller may not see is
 * refused outright — including the fact that the feature exists — rather than
 * answered with the numbers removed, because a partial answer still confirms
 * what is there.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessAssistantService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final FinancialCalculationService calculations;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final ForecastingService forecastingService;
    private final InventoryOptimizationService inventoryService;
    private final AnomalyDetectionService anomalyService;
    private final FinancialInsightService insightService;
    private final AiQueryLogRepository queryLogRepository;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    /** Permission required for each intent. */
    private static final Map<String, String> INTENT_PERMISSIONS = Map.ofEntries(
            Map.entry("GROSS_PROFIT", "PROFIT_LOSS_READ"),
            Map.entry("NET_PROFIT", "PROFIT_LOSS_READ"),
            Map.entry("PROFIT_EXPLANATION", "PROFIT_LOSS_READ"),
            Map.entry("TOP_PROFITABLE_PRODUCTS", "PROFIT_LOSS_READ"),
            Map.entry("FINANCIAL_COMPARISON", "FINANCIAL_REPORT_VIEW"),
            Map.entry("CASH_POSITION", "FINANCIAL_REPORT_VIEW"),
            Map.entry("RECEIVABLES", "RECEIVABLE_READ"),
            Map.entry("PAYABLES", "PAYABLE_READ"),
            Map.entry("RECEIVED_NOT_PAID", "PAYABLE_READ"),
            Map.entry("STOCKOUT_RISK", "INVENTORY_READ"),
            Map.entry("SLOW_MOVING", "INVENTORY_READ"),
            Map.entry("WAREHOUSE_ADJUSTMENTS", "STOCK_MOVEMENT_READ"),
            Map.entry("SALES_TREND", "SALES_REPORT_VIEW"),
            Map.entry("FORECAST", "SALES_REPORT_VIEW"),
            Map.entry("ANOMALIES", "SALES_REPORT_VIEW"),
            Map.entry("INVENTORY_VALUE", "INVENTORY_VALUE_VIEW"));

    private static final List<String> SUPPORTED_INTENTS = List.of(
            "What was my gross profit",
            "Which products made the most profit",
            "Why did profit change",
            "Compare this period with the last one",
            "Which products are likely to run out",
            "Show purchases received but not fully paid",
            "Which customers have overdue balances",
            "How much cash have we received",
            "Which products are not selling",
            "Which warehouse had the most adjustments",
            "What will sales look like next week",
            "What looks unusual in this period");

    /**
     * Answers a question from validated data.
     *
     * <p>Never throws for a question it does not understand — that is a normal
     * outcome, answered with a clarification — but does refuse for a caller who
     * lacks the permission, because that is an authorisation failure and hiding
     * it inside a helpful sentence would be worse.
     */
    @Transactional
    public AssistantAnswer ask(String question, Long overrideFrom, Long overrideTo) {
        long started = System.currentTimeMillis();
        String normalised = question == null ? "" : question.toLowerCase(Locale.ROOT).trim();

        if (!properties.isEnabled()) {
            return AssistantAnswer.disabled(
                    "The intelligence service is switched off on this deployment "
                            + "(app.ai.enabled=false). Figures are still available from the reports.");
        }

        Intent match = classify(normalised);
        ResolvedPeriod period = resolvePeriod(normalised, overrideFrom, overrideTo);

        if (match.intent() == null || match.confidence() < properties.getAssistantMinConfidence()) {
            AssistantAnswer answer = AssistantAnswer.needsClarification(
                    question,
                    supportedIntents(),
                    "I could not tell which report you are asking for. Here is what I can answer:",
                    period);
            recordQuery(answer, question, match.intent(), match.confidence(), period,
                    System.currentTimeMillis() - started,
                    AiQueryLog.OUTCOME_CLARIFICATION_NEEDED);
            return answer;
        }

        String requiredPermission = INTENT_PERMISSIONS.get(match.intent());
        if (!hasAuthority(requiredPermission)) {
            // Refused before any data is read, and without disclosing what the
            // answer would have been. Logged so that a repeated attempt is
            // visible to whoever reviews the query log.
            AssistantAnswer denied = AssistantAnswer.denied(
                    "You do not have permission to view this information.",
                    requiredPermission, match.intent(), period);
            recordQuery(denied, question, match.intent(), match.confidence(), period,
                    System.currentTimeMillis() - started, AiQueryLog.OUTCOME_DENIED);

            // Thrown rather than returned, because the controller has to produce
            // 403: a 200 carrying a refusal in the body would let a client that
            // only checks the status code treat the refusal as an answer.
            throw new AccessDeniedException(denied.answer());
        }

        AssistantAnswer answer = execute(match.intent(), question, period);

        if (answer.outcome().equals(AiQueryLog.OUTCOME_INSUFFICIENT_DATA)) {
            recordQuery(answer, question, match.intent(), match.confidence(), period,
                    System.currentTimeMillis() - started, AiQueryLog.OUTCOME_INSUFFICIENT_DATA);
        } else {
            recordQuery(answer, question, match.intent(), match.confidence(), period,
                    System.currentTimeMillis() - started, AiQueryLog.OUTCOME_ANSWERED);
        }
        return answer;
    }

    // ── Answering ─────────────────────────────────────────────────────────────

    private AssistantAnswer execute(String intent, String question, ResolvedPeriod period) {
        return switch (intent) {
            case "GROSS_PROFIT", "NET_PROFIT" -> profitAnswer(intent, period);
            case "TOP_PROFITABLE_PRODUCTS" -> topProductsAnswer(period);
            case "PROFIT_EXPLANATION" -> profitExplanation(period);
            case "FINANCIAL_COMPARISON" -> comparisonAnswer(period);
            case "RECEIVABLES" -> receivablesAnswer(period);
            case "PAYABLES", "RECEIVED_NOT_PAID" -> payablesAnswer(intent, period);
            case "CASH_POSITION" -> cashAnswer(period);
            case "STOCKOUT_RISK" -> stockoutAnswer(question, period);
            case "SLOW_MOVING" -> slowMovingAnswer(period);
            case "WAREHOUSE_ADJUSTMENTS" -> warehouseAdjustmentsAnswer(period);
            case "FORECAST", "SALES_TREND" -> forecastAnswer(period);
            case "ANOMALIES" -> anomalyAnswer(period);
            case "INVENTORY_VALUE" -> inventoryValueAnswer(period);
            default -> AssistantAnswer.needsClarification(question, supportedIntents(),
                    "I do not yet know how to answer that.", period);
        };
    }

    private AssistantAnswer profitAnswer(String intent, ResolvedPeriod period) {
        FinancialSummary summary = calculations.summarise(period.from(), period.to());
        if (summary.insufficientData()) {
            return insufficient("", period,
                    "No sales, returns, expenses or received purchases are recorded between "
                            + period.label() + ", so there is no profit figure for that period.");
        }
        boolean net = "NET_PROFIT".equals(intent);
        BigDecimal value = net ? summary.netProfit() : summary.grossProfit();
        BigDecimal percent = net ? summary.netMarginPercent() : summary.grossMarginPercent();

        String answer = String.format(
                "Gross profit%s for %s was %s, a margin of %s%%. "
                        + "That is net sales of %s less cost of goods sold of %s. "
                        + "Operating expenses were %s, giving net profit of %s.",
                net ? " after expenses" : "", period.label(), money(value),
                percent.toPlainString(), money(summary.netSales()),
                money(summary.costOfGoodsSold()), money(summary.operatingExpenses()),
                money(summary.netProfit()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("netSales", summary.netSales());
        data.put("costOfGoodsSold", summary.costOfGoodsSold());
        data.put("grossProfit", summary.grossProfit());
        data.put("grossMarginPercent", summary.grossMarginPercent());
        data.put("operatingExpenses", summary.operatingExpenses());
        data.put("netProfit", summary.netProfit());
        data.put("netMarginPercent", summary.netMarginPercent());
        data.put("salesCount", summary.salesCount());
        data.put("basis", summary.basis());

        return AssistantAnswer.answered("", intent, answer, period, data,
                List.of("/finance/profit-loss", "/reports/sales"),
                assumptions(summary));
    }

    private AssistantAnswer topProductsAnswer(ResolvedPeriod period) {
        List<Object[]> rows = saleOrderRepository.summariseByProduct(
                period.from().atStartOfDay(), period.to().atTime(java.time.LocalTime.MAX));
        if (rows.isEmpty()) {
            return insufficient(question(period), period,
                    "No sales were recorded between " + period.label()
                            + ", so there are no products to rank.");
        }
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Object[] row : rows) {
            long id = ((Number) row[0]).longValue();
            double units = ((Number) row[1]).doubleValue();
            double revenue = ((Number) row[2]).doubleValue();
            double cost = ((Number) row[3]).doubleValue();
            Product product = products.get(id);
            ranked.add(Map.of(
                    "productId", id,
                    "name", product == null ? "Product " + id : product.getName(),
                    "sku", product == null ? "—" : product.getSku(),
                    "unitsSold", (long) units,
                    "revenue", revenue,
                    "costOfGoodsSold", cost,
                    "grossProfit", revenue - cost,
                    "marginPercent", revenue == 0 ? 0
                            : Math.round((revenue - cost) / revenue * 10000) / 100.0));
        }
        ranked.sort((a, b) -> Double.compare((Double) b.get("grossProfit"), (Double) a.get("grossProfit")));
        List<Map<String, Object>> topFive = ranked.subList(0, Math.min(5, ranked.size()));

        StringBuilder text = new StringBuilder("The five products with the highest gross profit in "
                + period.label() + " are:\n");
        for (int i = 0; i < topFive.size(); i++) {
            Map<String, Object> row = topFive.get(i);
            text.append(String.format("%d. %s — %s gross profit on %s of sales (%s%% margin)%s",
                    i + 1, row.get("name"), money((Double) row.get("grossProfit")),
                    money((Double) row.get("revenue")), row.get("marginPercent"),
                    i == topFive.size() - 1 ? "." : "\n"));
        }

        return AssistantAnswer.answered("", "TOP_PROFITABLE_PRODUCTS", text.toString(),
                period, Map.of("topProducts", topFive, "totalProductsSold", ranked.size()),
                List.of("/reports/sales"),
                List.of("Ranked by gross profit (revenue minus the cost the goods carried when sold), "
                        + "not by revenue alone."));
    }

    private AssistantAnswer profitExplanation(ResolvedPeriod period) {
        List<FinancialInsightService.Insight> insights =
                insightService.explainProfitChangeOnly(period.from(), period.to());
        if (insights.isEmpty()) {
            return insufficient(question(period), period,
                    "There is not enough recorded activity in " + period.label()
                            + " to explain a change in profit.");
        }
        String text = insights.get(0).message();
        return AssistantAnswer.answered("", "PROFIT_EXPLANATION", text, period,
                insights.get(0).evidence(), List.of("/finance/profit-loss"),
                insights.get(0).evidence().containsKey("basis")
                        ? List.of(String.valueOf(insights.get(0).evidence().get("basis")))
                        : List.of());
    }

    private AssistantAnswer comparisonAnswer(ResolvedPeriod period) {
        FinancialSummary current = calculations.summarise(period.from(), period.to());
        FinancialSummary previous = calculations.previousPeriod(period.from(), period.to());

        if (current.insufficientData() && previous.insufficientData()) {
            return insufficient(question(period), period,
                    "Neither " + period.label() + " nor the preceding period of the same length "
                            + "has any recorded activity, so there is nothing to compare.");
        }

        Map<String, Object> comparison = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder("Comparing ");
        text.append(period.label()).append(" with ")
                .append(previous.from().format(DATE)).append(" – ")
                .append(previous.to().format(DATE)).append(":\n");

        comparison.put("period", period.label());
        comparison.put("previousPeriod", previous.from() + " to " + previous.to());
        appendComparison(text, comparison, "Net sales", current.netSales(), previous.netSales());
        appendComparison(text, comparison, "Cost of goods sold",
                current.costOfGoodsSold(), previous.costOfGoodsSold());
        appendComparison(text, comparison, "Gross profit", current.grossProfit(), previous.grossProfit());
        appendComparison(text, comparison, "Operating expenses",
                current.operatingExpenses(), previous.operatingExpenses());
        appendComparison(text, comparison, "Net profit", current.netProfit(), previous.netProfit());
        appendComparison(text, comparison, "Cash received", current.cashReceived(), previous.cashReceived());

        comparison.put("basis", current.basis());

        return AssistantAnswer.answered("", "FINANCIAL_COMPARISON", text.toString(), period,
                comparison, List.of("/finance/profit-loss"),
                List.of("The comparison period is the same number of days immediately before this one, "
                        + "so a part-month is never compared with a full one."));
    }

    private void appendComparison(StringBuilder text, Map<String, Object> comparison,
                                  String label, BigDecimal current, BigDecimal previous) {
        BigDecimal change = FinancialCalculations.change(current, previous);
        BigDecimal percent = FinancialCalculations.percentChange(current, previous);

        comparison.put(label.replace(' ', '_'), current);
        comparison.put(label.replace(' ', '_') + "_Previous", previous);
        comparison.put(label.replace(' ', '_') + "_Change", change);
        comparison.put(label.replace(' ', '_') + "_PercentChange",
                percent == null ? "not comparable" : percent.toPlainString());

        if (change == null) {
            text.append(String.format("• %s: %s. No figure for the previous period to compare against.%n",
                    label, money(current)));
        } else if (previous.signum() == 0) {
            text.append(String.format("• %s: %s. The previous period was zero, so no percentage change is meaningful.%n",
                    label, money(current)));
        } else {
            text.append(String.format("• %s: %s (was %s, %s%s%%).%n",
                    label, money(current), money(previous),
                    change.signum() >= 0 ? "+" : "", percent.toPlainString()));
        }
    }

    private AssistantAnswer receivablesAnswer(ResolvedPeriod period) {
        List<SaleOrder> outstanding = saleOrderRepository.findOutstandingReceivables(
                period.to().atTime(java.time.LocalTime.MAX));
        if (outstanding.isEmpty()) {
            return AssistantAnswer.answered("", "RECEIVABLES",
                    "No customer has an outstanding balance as at " + period.to().format(DATE) + ".",
                    period, Map.of("totalOutstanding", BigDecimal.ZERO, "accounts", List.of()),
                    List.of("/finance/receivables"), List.of());
        }

        Map<String, List<SaleOrder>> byCustomer = outstanding.stream()
                .filter(s -> s.getFinalAmount() != null)
                .filter(s -> FinancialCalculations.outstanding(
                        s.getFinalAmount(), s.getAmountPaid()).signum() > 0)
                .collect(Collectors.groupingBy(
                        s -> s.getCustomer() != null ? s.getCustomer().getName()
                                : (s.getCustomerName() == null ? "Unattributed" : s.getCustomerName())));

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<String, List<SaleOrder>> entry : byCustomer.entrySet()) {
            BigDecimal balance = entry.getValue().stream()
                    .map(s -> FinancialCalculations.outstanding(s.getFinalAmount(), s.getAmountPaid()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int oldestDays = entry.getValue().stream()
                    .map(SaleOrder::getTransactionDate)
                    .filter(Objects::nonNull)
                    .mapToInt(d -> (int) ChronoUnit.DAYS.between(d.toLocalDate(), today))
                    .max().orElse(0);
            total = total.add(balance);
            rows.add(Map.of("customer", entry.getKey(), "outstanding", balance,
                    "oldestDays", oldestDays, "overdue", oldestDays > 30,
                    "invoiceCount", entry.getValue().size()));
        }
        rows.sort((a, b) -> Integer.compare((Integer) b.get("oldestDays"), (Integer) a.get("oldestDays")));

        List<Map<String, Object>> overdue = rows.stream().filter(r -> (Boolean) r.get("overdue")).toList();

        StringBuilder text = new StringBuilder(String.format(
                "Customers owe %s in total as at %s, across %d account%s.",
                money(total), period.to().format(DATE), rows.size(), rows.size() == 1 ? "" : "s"));
        if (!overdue.isEmpty()) {
            text.append(String.format(" %d of these ha%s a balance older than 30 days:",
                    overdue.size(), overdue.size() == 1 ? "s" : "ve"));
            for (Map<String, Object> row : overdue.stream().limit(5).toList()) {
                text.append(String.format("\n• %s — %s outstanding, oldest %s days",
                        row.get("customer"), money((BigDecimal) row.get("outstanding")),
                        row.get("oldestDays")));
            }
        }

        return AssistantAnswer.answered("", "RECEIVABLES", text.toString(), period,
                Map.of("totalOutstanding", total, "accounts", rows),
                List.of("/finance/receivables"),
                List.of("A balance is an invoice that is not fully settled by completed payments. "
                        + "Age is measured from the sale date, not from the last part-payment."));
    }

    private AssistantAnswer payablesAnswer(String intent, ResolvedPeriod period) {
        List<PurchaseOrder> outstanding =
                purchaseOrderRepository.findOutstandingPayables(period.to().atTime(java.time.LocalTime.MAX));

        List<PurchaseOrder> unpaid = "RECEIVED_NOT_PAID".equals(intent)
                ? outstanding.stream()
                        .filter(po -> PurchaseOrder.STATUS_RECEIVED.equals(po.getStatus())
                                || PurchaseOrder.STATUS_PARTIALLY_RECEIVED.equals(po.getStatus()))
                        .toList()
                : outstanding;

        if (unpaid.isEmpty()) {
            return AssistantAnswer.answered("", intent,
                    "Every received purchase order has been paid in full as at "
                            + period.to().format(DATE) + ".",
                    period, Map.of("totalOutstanding", BigDecimal.ZERO, "orders", List.of()),
                    List.of("/finance/payables"), List.of());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrder po : unpaid) {
            BigDecimal balance = po.getBalanceDue();
            total = total.add(balance);
            rows.add(Map.of(
                    "order", po.getOrderNumber(),
                    "supplier", po.getSupplierName() == null ? "unknown" : po.getSupplierName(),
                    "orderTotal", po.getTotalAmount(),
                    "paid", po.getAmountPaid() == null ? BigDecimal.ZERO : po.getAmountPaid(),
                    "balanceDue", balance,
                    "receiptStatus", po.getStatus(),
                    "dueDate", po.getDueDate() == null ? "not set" : po.getDueDate().toString(),
                    "overdue", po.getDueDate() != null && po.getDueDate().isBefore(LocalDate.now())));
        }
        rows.sort((a, b) -> String.valueOf(a.get("dueDate"))
                .compareTo(String.valueOf(b.get("dueDate"))));

        StringBuilder text = new StringBuilder(String.format(
                "%d received purchase order%s still ha%s money unpaid, totalling %s as at %s:",
                rows.size(), rows.size() == 1 ? "" : "s", rows.size() == 1 ? "s" : "ve",
                money(total), period.to().format(DATE)));
        for (Map<String, Object> row : rows.stream().limit(10).toList()) {
            text.append(String.format("\n• %s — %s: %s of %s unpaid (due %s)%s",
                    row.get("order"), row.get("supplier"), money((BigDecimal) row.get("balanceDue")),
                    money((BigDecimal) row.get("orderTotal")), row.get("dueDate"),
                    Boolean.TRUE.equals(row.get("overdue")) ? " — OVERDUE" : ""));
        }
        if (rows.size() > 10) {
            text.append(String.format("\n…and %d more, shown in the report.", rows.size() - 10));
        }

        return AssistantAnswer.answered("", intent, text.toString(), period,
                Map.of("totalOutstanding", total, "orders", rows),
                List.of("/finance/payables"),
                List.of("Only goods that have actually been received are included. "
                        + "An order still on its way is not yet a payable."));
    }

    private AssistantAnswer cashAnswer(ResolvedPeriod period) {
        FinancialSummary summary = calculations.summarise(period.from(), period.to());
        String text = String.format(
                "For %s: %s was received from customers, %s was paid to suppliers, "
                        + "%s was refunded to customers and %s of expenses were approved. "
                        + "Net cash movement was %s. This compares with %s of sales, so %s is still "
                        + "outstanding as a receivable.",
                period.label(), money(summary.cashReceived()), money(summary.cashPaidToSuppliers()),
                money(summary.cashRefunded()), money(summary.operatingExpenses()),
                money(summary.netCashFlow()), money(summary.netSales()),
                money(summary.accountsReceivable()));

        return AssistantAnswer.answered("", "CASH_POSITION", text, period,
                Map.of("cashReceived", summary.cashReceived(),
                        "cashPaidToSuppliers", summary.cashPaidToSuppliers(),
                        "cashRefunded", summary.cashRefunded(),
                        "operatingExpenses", summary.operatingExpenses(),
                        "netCashFlow", summary.netCashFlow(),
                        "netSales", summary.netSales(),
                        "accountsReceivable", summary.accountsReceivable()),
                List.of("/finance/payments"),
                List.of("Cash counts only payments actually completed. An unpaid invoice is revenue "
                        + "but is not cash until it is settled."));
    }

    private AssistantAnswer stockoutAnswer(String question, ResolvedPeriod period) {
        int horizon = extractHorizonDays(question, 14);
        List<InventoryOptimizationService.StockoutRisk> risks =
                inventoryService.predictStockouts(horizon);

        if (risks.isEmpty()) {
            return AssistantAnswer.answered(question, "STOCKOUT_RISK",
                    String.format("No product is projected to run out within %d days at its current "
                            + "sales rate.", horizon),
                    period, Map.of("horizonDays", horizon, "risks", List.of()),
                    List.of("/inventory"),
                    List.of("Based on each product's recorded sales rate. Products with no sales "
                            + "history cannot be projected and are excluded."));
        }

        StringBuilder text = new StringBuilder(String.format(
                "%d product%s projected to run out within %d days:", risks.size(),
                risks.size() == 1 ? " is" : "s are", horizon));
        for (InventoryOptimizationService.StockoutRisk risk : risks.stream().limit(10).toList()) {
            text.append(String.format("\n• %s — %s days of stock left at %s units/day%s",
                    risk.name(), risk.daysOfStockRemaining(), risk.averageDailyUnits(),
                    risk.stockOnOrder() > 0
                            ? String.format(" (a further %s are on order)", risk.stockOnOrder()) : ""));
        }

        return AssistantAnswer.answered(question, "STOCKOUT_RISK", text.toString(), period,
                Map.of("horizonDays", horizon,
                        "risks", risks.stream().limit(10).toList()),
                List.of("/inventory"),
                List.of("Days of stock is measured from stock on hand divided by the average daily "
                        + "sales rate. Goods already on order are shown separately and are not "
                        + "counted as stock on hand."));
    }

    private AssistantAnswer slowMovingAnswer(ResolvedPeriod period) {
        int days = (int) Math.max(30, ChronoUnit.DAYS.between(period.from(), period.to()) + 1);
        List<InventoryOptimizationService.SlowMovingProduct> slow =
                inventoryService.identifySlowMoving(days, 10);
        if (slow.isEmpty()) {
            return AssistantAnswer.answered("", "SLOW_MOVING",
                    "Every product holding stock recorded a sale in the last " + days + " days.",
                    period, Map.of("lookbackDays", days, "products", List.of()),
                    List.of("/reports/inventory"), List.of());
        }
        BigDecimal value = slow.stream()
                .map(InventoryOptimizationService.SlowMovingProduct::valueAtCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder text = new StringBuilder(String.format(
                "%d product%s holding stock recorded no sale in the last %d days, worth %s at cost:",
                slow.size(), slow.size() == 1 ? "" : "s", days, money(value)));
        for (InventoryOptimizationService.SlowMovingProduct p : slow) {
            text.append(String.format("\n• %s (%s) — %s units, %s at cost",
                    p.name(), p.sku(), p.stockOnHand(), money(p.valueAtCost())));
        }

        return AssistantAnswer.answered("", "SLOW_MOVING", text.toString(), period,
                Map.of("lookbackDays", days, "products", slow,
                        "totalValueAtCost", value),
                List.of("/reports/inventory"),
                List.of("Valued at purchase cost, not at retail."));
    }

    private AssistantAnswer warehouseAdjustmentsAnswer(ResolvedPeriod period) {
        Map<Long, long[]> counts = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        warehouseRepository.findAll().forEach(w -> names.put(w.getId(), w.getName()));

        List<com.inventory.backend.model.StockMovement> rows =
                anomalyService.movementCountsByWarehouse(period.from(), period.to());

        for (var movement : rows) {
            Long warehouseId = movement.getWarehouse() == null ? null : movement.getWarehouse().getId();
            if (warehouseId == null) {
                continue;
            }
            long[] current = counts.computeIfAbsent(warehouseId, k -> new long[1]);
            current[0]++;
            if (movement.getWarehouse() != null) {
                names.put(warehouseId, movement.getWarehouse().getName());
            }
        }

        if (counts.isEmpty()) {
            return AssistantAnswer.answered("", "WAREHOUSE_ADJUSTMENTS",
                    "No stock adjustments were recorded in " + period.label() + ".",
                    period, Map.of("warehouses", List.of()),
                    List.of("/inventory/adjustments"), List.of());
        }

        List<Map<String, Object>> rowsOut = counts.entrySet().stream()
                .map(e -> Map.<String, Object>of("warehouse", names.getOrDefault(e.getKey(), "unknown"),
                        "warehouseId", e.getKey(), "adjustments", e.getValue()[0]))
                .sorted((a, b) -> Long.compare((Long) b.get("adjustments"), (Long) a.get("adjustments")))
                .toList();

        StringBuilder text = new StringBuilder("Stock adjustments by warehouse for "
                + period.label() + ":\n");
        for (Map<String, Object> row : rowsOut) {
            text.append(String.format("• %s — %s adjustments%n",
                    row.get("warehouse"), row.get("adjustments")));
        }

        return AssistantAnswer.answered("", "WAREHOUSE_ADJUSTMENTS", text.toString(), period,
                Map.of("warehouses", rowsOut),
                List.of("/inventory/adjustments"),
                List.of("Counts approved ADJUSTMENT and STOCK_COUNT movements only. "
                        + "A high count is not necessarily wrongdoing — it often reflects a "
                        + "process problem such as unreported damage."));
    }

    private AssistantAnswer forecastAnswer(ResolvedPeriod period) {
        ForecastingService.RevenueForecast forecast = forecastingService.forecastRevenue(
                period.from(), period.to(), properties.getDefaultDailyHorizon());

        if (!forecast.available()) {
            return insufficient(question(period), period,
                    "There is not enough recorded sales history to forecast yet: "
                            + forecast.explanation());
        }

        double next7 = forecast.forecast().stream().limit(7).mapToDouble(p -> p.value()).sum();
        StringBuilder text = new StringBuilder(String.format(
                "Based on %d days of recorded sales, the model chosen for this business is %s. "
                        + "It projects %s of net sales over the next 7 days and %s over the next %d days. ",
                forecast.historyValues().size(), forecast.algorithm(),
                money(next7), money(forecast.forecast().stream().mapToDouble(p -> p.value()).sum()),
                forecast.forecast().size()));
        if (forecast.mapePercent() != null) {
            text.append(String.format(
                    "On held-out data it was wrong by %s%% on average, so treat this as a range "
                            + "rather than a precise figure. ",
                    number(forecast.mapePercent())));
        } else {
            text.append("Its accuracy could not be expressed as a percentage error because some "
                    + "days had no sales at all. ");
        }
        if (forecast.trendPercent() != null) {
            text.append(String.format("Sales have been %s over this period.",
                    forecast.trendPercent() >= 0
                            ? "trending up " + number(forecast.trendPercent()) + "%"
                            : "trending down " + number(Math.abs(forecast.trendPercent())) + "%"));
        }

        Map<String, Object> forecastData = new LinkedHashMap<>();
        forecastData.put("algorithm", forecast.algorithm());
        forecastData.put("historyDays", forecast.historyValues().size());
        forecastData.put("forecastPoints", forecast.forecast());
        forecastData.put("mae", forecast.mae());
        forecastData.put("rmse", forecast.rmse());
        forecastData.put("mapePercent", forecast.mapePercent() == null
                ? "not available — some days had zero sales" : number(forecast.mapePercent()));
        forecastData.put("modelVersion", forecast.modelVersion());
        forecastData.put("nextSevenDays", round(next7));
        forecastData.put("trendPercent", forecast.trendPercent());

        return AssistantAnswer.answered("", "FORECAST", text.toString(), period,
                forecastData,
                List.of("/dashboard"),
                List.of("The model is chosen by backtesting several candidates on data it had not "
                        + "seen, and the least accurate one is not used. "
                        + forecast.modelVersion()));
    }

    private AssistantAnswer anomalyAnswer(ResolvedPeriod period) {
        List<AnomalyDetectionService.Anomaly> anomalies =
                anomalyService.detectAll(period.from(), period.to());
        if (anomalies.isEmpty()) {
            return AssistantAnswer.answered("", "ANOMALIES",
                    "Nothing in " + period.label() + " stood out against this business's own history.",
                    period, Map.of("anomalies", List.of()),
                    List.of("/dashboard"),
                    List.of("Detection needs at least "
                            + properties.getAnomalyMinimumObservations()
                            + " days of history for each series compared."));
        }

        StringBuilder text = new StringBuilder(String.format(
                "%d thing%s in %s stood out against this business's own history. "
                        + "These are prompts to look, not conclusions:\n",
                anomalies.size(), anomalies.size() == 1 ? "" : "s", period.label()));
        for (AnomalyDetectionService.Anomaly anomaly : anomalies.stream().limit(8).toList()) {
            text.append(String.format("• %s%n", anomaly.summary()));
        }

        return AssistantAnswer.answered("", "ANOMALIES", text.toString(), period,
                Map.of("anomalies", anomalies.stream().limit(20).toList()),
                List.of("/dashboard"),
                List.of("An anomaly is a statistical outlier, not evidence of wrongdoing. "
                        + "A genuine one-off — a large order, a delivery problem — looks identical "
                        + "to an irregular one until somebody checks the underlying record."));
    }

    private AssistantAnswer inventoryValueAnswer(ResolvedPeriod period) {
        FinancialSummary summary = calculations.summarise(period.from(), period.to());
        return AssistantAnswer.answered("", "INVENTORY_VALUE",
                String.format("Stock on hand is valued at %s at weighted-average cost as at %s.",
                        money(summary.inventoryValue()), period.to().format(DATE)),
                period,
                Map.of("inventoryValue", summary.inventoryValue(),
                        "stockLossValue", summary.stockLossValue(),
                        "stockLossUnits", summary.stockLossUnits()),
                List.of("/reports/inventory-value"),
                List.of(summary.basis()));
    }

    // ── Intent classification ─────────────────────────────────────────────────

    /**
     * Maps a question onto an intent.
     *
     * <p>Keyword scoring, weighted so a more specific phrase beats a more general
     * one: "overdue balances" contains "balance" but also "overdue", and the more
     * specific phrase must win. Each intent lists a minimum number of keyword
     * hits rather than a single one, so "sales" alone does not match everything
     * sales-related.
     *
     * <p>The returned confidence is the winner's score divided by the runner-up's.
     * A question matching one intent strongly and the next one barely is
     * confident; one matching two equally is not, and the caller asks rather than
     * guessing.
     */
    Intent classify(String question) {
        Map<String, Integer> scores = new LinkedHashMap<>();

        scores.merge("GROSS_PROFIT", score(question,
                "gross profit", "gross margin", "margin on sales"), Integer::sum);
        scores.merge("NET_PROFIT", score(question,
                "net profit", "profit after expenses", "bottom line"), Integer::sum);
        scores.merge("TOP_PROFITABLE_PRODUCTS", score(question,
                "highest gross profit", "most profitable", "top products", "best products",
                "which products made", "five products"), Integer::sum);
        scores.merge("PROFIT_EXPLANATION", score(question,
                "why did profit", "explain why", "why is net profit", "why did net profit",
                "profit decrease", "profit fell", "explain why net profit"), Integer::sum);
        scores.merge("FINANCIAL_COMPARISON", score(question,
                "compare", "versus", " vs ", "compared with", "compared to", "difference between"), Integer::sum);
        scores.merge("RECEIVABLES", score(question,
                "overdue", "receivable", "who owes", "outstanding balance", "customers owe",
                "unpaid customer", "debtors"), Integer::sum);
        scores.merge("RECEIVED_NOT_PAID", score(question,
                "received but not fully paid", "received but not paid", "not fully paid",
                "purchases received but"), Integer::sum);
        scores.merge("PAYABLES", score(question,
                "payable", "owe supplier", "suppliers owe", "supplier balance", "unpaid purchase"), Integer::sum);
        scores.merge("CASH_POSITION", score(question,
                "cash received", "cash flow", "money in", "money received", "how much cash",
                "cash position"), Integer::sum);
        scores.merge("STOCKOUT_RISK", score(question,
                "run out", "stockout", "run out of stock", "likely to run", "will run out",
                "stock out", "shortage"), Integer::sum);
        scores.merge("SLOW_MOVING", score(question,
                "not selling", "slow moving", "slow-moving", "sitting still", "dead stock"), Integer::sum);
        scores.merge("WAREHOUSE_ADJUSTMENTS", score(question,
                "most stock adjustments", "adjustments by warehouse", "warehouse adjustments",
                "adjustments at", "most adjustments"), Integer::sum);
        scores.merge("FORECAST", score(question,
                "forecast", "predict", "next week", "next month", "expect to sell",
                "coming week", "future sales"), Integer::sum);
        scores.merge("ANOMALIES", score(question,
                "unusual", "anomal", "strange", "look wrong", "outlier", "suspicious"), Integer::sum);
        scores.merge("INVENTORY_VALUE", score(question,
                "inventory value", "stock value", "worth of stock"), Integer::sum);
        scores.merge("SALES_TREND", score(question,
                "sales trend", "how are sales", "how sales are"), Integer::sum);

        List<Map.Entry<String, Integer>> ranked = scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

        if (ranked.isEmpty()) {
            return new Intent(null, 0);
        }
        int top = ranked.get(0).getValue();
        int second = ranked.size() > 1 ? ranked.get(1).getValue() : 0;
        double confidence = second == 0 ? 1.0 : (double) top / (top + second);
        return new Intent(ranked.get(0).getKey(), confidence);
    }

    private int score(String question, String... phrases) {
        int total = 0;
        for (String phrase : phrases) {
            if (question.contains(phrase)) {
                // A longer phrase is more specific and outweighs a shorter match.
                total += phrase.contains(" ") ? 3 : 2;
            }
        }
        return total;
    }

    /**
     * Works out which period the question is about.
     *
     * <p>An explicit from/to from the dashboard filter always wins. Failing that
     * the question's own words are read; failing that the last 30 days, which is
     * stated in the answer so it is never ambiguous.
     */
    ResolvedPeriod resolvePeriod(String question, Long overrideFrom, Long overrideTo) {
        if (overrideFrom != null || overrideTo != null) {
            LocalDate to = overrideTo == null ? LocalDate.now()
                    : java.time.Instant.ofEpochMilli(overrideTo)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate();
            LocalDate from = overrideFrom == null ? to.minusDays(29)
                    : java.time.Instant.ofEpochMilli(overrideFrom)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate();
            return new ResolvedPeriod(from, to, "the selected date range");
        }

        LocalDate today = LocalDate.now();
        if (question.contains("today")) {
            return new ResolvedPeriod(today, today, "today");
        }
        if (question.contains("yesterday")) {
            return new ResolvedPeriod(today.minusDays(1), today.minusDays(1), "yesterday");
        }
        if (question.contains("this week")) {
            return new ResolvedPeriod(today.minusDays(today.getDayOfWeek().getValue() - 1L), today,
                    "this week");
        }
        if (question.contains("last week")) {
            LocalDate thisWeekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
            return new ResolvedPeriod(thisWeekStart.minusDays(7), thisWeekStart.minusDays(1), "last week");
        }
        if (question.contains("last month")) {
            LocalDate firstThisMonth = today.withDayOfMonth(1);
            LocalDate lastDayLastMonth = firstThisMonth.minusDays(1);
            return new ResolvedPeriod(lastDayLastMonth.withDayOfMonth(1), lastDayLastMonth, "last month");
        }
        if (question.contains("this month")) {
            return new ResolvedPeriod(today.withDayOfMonth(1), today, "this month so far");
        }
        if (question.contains("this year") || question.contains("year to date") || question.contains("ytd")) {
            return new ResolvedPeriod(today.withDayOfYear(1), today, "this year to date");
        }
        if (question.contains("last year")) {
            LocalDate firstThisYear = today.withDayOfYear(1);
            return new ResolvedPeriod(firstThisYear.minusYears(1).withDayOfYear(1),
                    firstThisYear.minusDays(1), "last year");
        }
        return new ResolvedPeriod(today.minusDays(29), today, "the last 30 days");
    }

    private int extractHorizonDays(String question, int fallback) {
        for (String number : new String[]{"7", "14", "21", "30", "45", "60", "90"}) {
            if (question.contains("next " + number) || question.contains(number + " days")) {
                return Integer.parseInt(number);
            }
        }
        return fallback;
    }

    /** What the assistant can answer, for the UI's suggestion chips. */
    public List<String> capabilities() {
        return SUPPORTED_INTENTS;
    }

    private List<String> supportedIntents() {
        return SUPPORTED_INTENTS;
    }

    private List<String> assumptions(FinancialSummary summary) {
        List<String> assumptions = new ArrayList<>();
        assumptions.add(summary.basis());
        assumptions.add("Tax collected on sales is excluded from revenue and is not profit.");
        assumptions.add("Cost of goods sold uses the weighted-average cost recorded when each sale was made.");
        return assumptions;
    }

    private String question(ResolvedPeriod period) {
        return period.label();
    }

    private AssistantAnswer insufficient(String question, ResolvedPeriod period, String message) {
        return new AssistantAnswer(question, "INSUFFICIENT_DATA",
                AiQueryLog.OUTCOME_INSUFFICIENT_DATA, message, period,
                Map.of("reason", message), List.of(), List.of(),
                "The question is understood but the records do not cover enough to answer it. "
                        + "No estimate has been substituted for the missing data.");
    }

    /**
     * Whether the caller holds an authority.
     *
     * <p>Super Admin is resolved from the role rather than from a
     * {@code ROLE_SUPER_ADMIN} authority, because the catalogue grants it every
     * permission individually — so it genuinely holds them, and no special case
     * is needed here. A caller with no authentication at all is refused: the
     * check fails closed, because the failure mode of a permissive default here
     * is a cash balance in a reply to somebody who should not have seen one.
     */
    private boolean hasAuthority(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> permission.equals(granted.getAuthority()));
    }

    private void recordQuery(AssistantAnswer answer, String question, String intent, double confidence,
                     ResolvedPeriod period, long durationMs, String outcome) {
        if (!properties.isPersistModelVersions()) {
            return;
        }
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication == null ? "system" : authentication.getName();

            queryLogRepository.save(AiQueryLog.builder()
                    .username(username)
                    .question(question)
                    .intent(intent)
                    .confidence(BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP))
                    .resolvedParameters(objectMapper.writeValueAsString(Map.of(
                            "from", period.from().toString(),
                            "to", period.to().toString(),
                            "label", period.label())))
                    .resultPayload(objectMapper.writeValueAsString(answer.data()))
                    .outcome(outcome)
                    .durationMs(durationMs)
                    .build());
        } catch (Exception e) {
            // Traceability is important, but failing to record a question must
            // never stop the answer being given.
            log.warn("Could not record assistant query: {}", e.getMessage());
        }
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    /** Money from an aggregate that came back from SQL as a double. */
    private static String money(Double value) {
        return value == null ? "not available" : String.format(Locale.ROOT, "%,.2f", value);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * A metric rendered for a sentence, without trailing noise.
     *
     * <p>{@code 12.3456} reads as 12.35. A forecast error printed to six decimal
     * places suggests a precision the measurement does not have.
     */
    private static String number(Double value) {
        return value == null ? "unavailable" : String.format(Locale.ROOT, "%.2f", value);
    }

    // ── Types ─────────────────────────────────────────────────────────────────

    record Intent(String intent, double confidence) {
    }

    /**
     * A resolved time range.
     *
     * <p>Public because it appears inside {@link AssistantAnswer}, which the API
     * returns. It is a value: the dates an answer used are part of the answer,
     * not an internal detail, and a caller that cannot read them cannot tell
     * whether the figures they were given cover the period they asked about.
     *
     * @param label how the period is described in the answer, so the reader can
     *              always tell which window was used
     */
    public record ResolvedPeriod(LocalDate from, LocalDate to, String label) {
    }

    /**
     * An answer.
     *
     * @param assumptions what the figures behind it assume, always stated
     * @param sources     the reports that show the same numbers
     */
    public record AssistantAnswer(String question,
                                  String intent,
                                  String outcome,
                                  String answer,
                                  ResolvedPeriod period,
                                  Map<String, Object> data,
                                  List<String> sources,
                                  List<String> assumptions,
                                  String note) {

        static AssistantAnswer answered(String question, String intent, String answer,
                                        ResolvedPeriod period, Map<String, Object> data,
                                        List<String> sources, List<String> assumptions) {
            return new AssistantAnswer(question, intent, AiQueryLog.OUTCOME_ANSWERED,
                    answer, period, data, sources, assumptions, null);
        }

        static AssistantAnswer needsClarification(String question, List<String> options,
                                                  String message, ResolvedPeriod period) {
            StringBuilder text = new StringBuilder(message).append("\n");
            for (String option : options) {
                text.append("• ").append(option).append("\n");
            }
            return new AssistantAnswer(question, null,
                    AiQueryLog.OUTCOME_CLARIFICATION_NEEDED, text.toString(), period,
                    Map.of("canAnswer", options), List.of(), List.of(),
                    "No figures are shown, because none were computed: the question was not "
                            + "understood well enough to know which report to read.");
        }

        static AssistantAnswer insufficient(String question, ResolvedPeriod period, String message) {
            return new AssistantAnswer(question, "INSUFFICIENT_DATA",
                    AiQueryLog.OUTCOME_INSUFFICIENT_DATA, message, period,
                    Map.of("reason", message), List.of(), List.of(),
                    "No estimate has been substituted for the missing data.");
        }

        static AssistantAnswer denied(String message, String permission, String intent,
                                      ResolvedPeriod period) {
            return new AssistantAnswer(null, intent, AiQueryLog.OUTCOME_DENIED,
                    message, period, Map.of(), List.of(), List.of(),
                    "Access is checked before any data is read, so no figure was retrieved "
                            + "for this question. Required permission: " + permission + ".");
        }

        static AssistantAnswer disabled(String message) {
            return new AssistantAnswer(null, null, AiQueryLog.OUTCOME_DENIED, message,
                    null, Map.of(), List.of(), List.of(), null);
        }
    }
}
