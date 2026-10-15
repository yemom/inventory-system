package com.inventory.backend.ai;

import com.inventory.backend.model.StockMovement;
import com.inventory.backend.repository.ExpenseRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Anomaly detection across revenue, expenses and stock movements.
 *
 * <h2>What an "anomaly" means here</h2>
 *
 * <p>An investigation alert: "this is unlike the rest of your data, look at it".
 * Not a conclusion. A single large sale on the day a wholesaler placed one order
 * is unusual and entirely legitimate; the system flags it so a human confirms it,
 * and does not assert that anything is wrong.
 *
 * <p>That framing is deliberate. A tool that reports "fraud detected" from a
 * statistical outlier is making an accusation it cannot support, and people
 * rightly stop reading it. Every alert here carries the evidence that produced
 * it — the value, the period it is unusual against, and how far out it is — so
 * the judgement can be made by someone who can see the underlying records.
 *
 * <h2>Why the median and MAD, not mean and standard deviation</h2>
 *
 * <p>The outlier inflates the very statistic used to judge it. One day of
 * quadruple sales raises the standard deviation enough to make that day look
 * ordinary. The median absolute deviation is computed from deviations around the
 * median, so the outlier contributes one of the deviations and cannot widen its
 * own yardstick.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Days in a week. Daily trading is strongly weekly — a shop that is busy on
     * Saturday is quiet on Tuesday — and comparing a Saturday against a
     * yardstick built from every day produces an alert every weekend.
     */
    private static final int WEEKLY_CYCLE = 7;

    private final SaleOrderRepository saleOrderRepository;
    private final ExpenseRepository expenseRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AiProperties properties;

    // ── Revenue ───────────────────────────────────────────────────────────────

    /**
     * Days of net sales that stand out against this business's own history.
     *
     * <p>The comparison is against a trailing window, not against the whole
     * period, so a slow drift upwards is not flagged every day once the business
     * has grown — it would fire permanently and be ignored.
     */
    @Transactional(readOnly = true)
    public List<Anomaly> detectRevenueAnomalies(LocalDate from, LocalDate to) {
        List<Object[]> rows = saleOrderRepository.sumNetSalesPerDay(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));

        Map<LocalDate, Double> byDate = new HashMap<>();
        for (Object[] row : rows) {
            byDate.put(((java.sql.Date) row[0]).toLocalDate(), ((Number) row[1]).doubleValue());
        }

        List<LocalDate> dates = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            dates.add(d);
            values.add(byDate.getOrDefault(d, 0.0));
        }

        double[] series = values.stream().mapToDouble(Double::doubleValue).toArray();
        if (series.length < properties.getAnomalyMinimumObservations()) {
            return List.of();
        }

        // The weekly pattern is removed first, so a shop that is busy every weekend
            // is not judged against a yardstick built from its quiet weekdays.
            // Anomalies are then found in the residuals.
            double[] residuals = TimeSeries.deseasonalise(series, WEEKLY_CYCLE);

            double median = TimeSeries.median(series);
        double mad = TimeSeries.medianAbsoluteDeviation(series);
        double mean = TimeSeries.mean(series);
        double sd = TimeSeries.standardDeviation(series);

        List<Anomaly> anomalies = new ArrayList<>();
        for (int i = 0; i < series.length; i++) {
            // Leave the last 7 days unjudged: a day at the end of the series has
            // no following days, so an unusual value there could equally be the
            // start of a trend rather than a spike.
            if (i < 14 || i >= series.length - 7) {
                continue;
            }
            // Compared against the trailing window of residuals, which
            // excludes the day itself so that a large spike cannot widen the
            // yardstick being asked to catch it.
            double[] history = TimeSeries.residualsAsHistory(residuals, i);
            if (history.length < properties.getAnomalyMinimumObservations()) {
                continue;
            }
            double z = TimeSeries.modifiedZScore(residuals[i], history);
            if (Math.abs(z) < properties.getAnomalyThreshold()) {
                continue;
            }

            // What this same weekday normally does. Recovered from the
            // residual rather than recomputed, so the expectation quoted to the
            // reader is exactly the one the score was judged against. The
            // residual is defined as series minus the weekday's own level plus
            // the series-wide level, so the weekday level comes back out as
            // series minus residual plus the overall level.
            double expected = series[i] - residuals[i] + median;

            // And the second, practical test: a day that is statistically odd
            // but barely different in money is not worth interrupting anyone
            // over. This is what keeps a quiet business's ordinary variation
            // from filling the alerts panel and training people to ignore it.
            double relativeDeviation = expected <= 0
                    ? Double.POSITIVE_INFINITY
                    : Math.abs(series[i] - expected) / expected;
            if (relativeDeviation < properties.getAnomalyMinimumRelativeDeviation()) {
                continue;
            }

            anomalies.add(new Anomaly(
                    "REVENUE",
                    dates.get(i).toString(),
                    series[i],
                    expected,
                    z,
                    z >= 0 ? "UNUSUALLY_HIGH" : "UNUSUALLY_LOW",
                    "Net sales on " + dates.get(i)
                            + (z >= 0 ? " were unusually high" : " were unusually low")
                            + " for that day of the week, against the recent norm of "
                            + money(expected) + ".",
                    buildEvidence(series[i], residuals[i], history, expected,
                            relativeDeviation, mad, mean, sd)));
        }
        return anomalies;
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    /**
     * Expense categories whose total moved sharply against the period before.
     *
     * <p>Assessed per category rather than on the total, because a 40% jump in
     * the total is only interesting if it is one line item that moved: transport
     * rising while salaries hold steady is a different conversation from every
     * category rising at once.
     *
     * <p>Approved expenses only. A pending claim is not yet a cost, and
     * forecasting a business's costs from claims nobody has agreed to would
     * produce confident nonsense.
     */
    @Transactional(readOnly = true)
    public List<Anomaly> detectExpenseAnomalies(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);

        Map<String, BigDecimalHolder> current = expensesByCategory(from, to);
        Map<String, BigDecimalHolder> previous = expensesByCategory(previousFrom, previousTo);

        List<Anomaly> anomalies = new ArrayList<>();
        for (Map.Entry<String, BigDecimalHolder> entry : current.entrySet()) {
            String category = entry.getKey();
            double currentValue = entry.getValue().value;
            double previousValue = previous.getOrDefault(category, new BigDecimalHolder()).value;

            // No baseline means nothing to be unusual against.
            if (previousValue <= 0) {
                continue;
            }
            double change = (currentValue - previousValue) / previousValue * 100.0;
            if (Math.abs(change) < 25.0) {
                continue;
            }
            anomalies.add(new Anomaly(
                    "EXPENSE",
                    from + " to " + to,
                    currentValue,
                    previousValue,
                    change,
                    change > 0 ? "INCREASE" : "DECREASE",
                    String.format("%s costs %s this period against %s in the previous %d days (%+.0f%%).",
                            titleCase(category), money(currentValue), money(previousValue), days, change),
                    Map.of("category", category,
                            "currentPeriodTotal", currentValue,
                            "previousPeriodTotal", previousValue,
                            "changePercent", round(change),
                            "previousPeriod", previousFrom + " to " + previousTo)));
        }
        anomalies.sort((a, b) -> Double.compare(Math.abs(b.score()), Math.abs(a.score())));
        return anomalies;
    }

    private Map<String, BigDecimalHolder> expensesByCategory(LocalDate from, LocalDate to) {
        List<Object[]> rows = expenseRepository.sumApprovedByCategory(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));
        Map<String, BigDecimalHolder> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String category = row[0] == null ? "UNCATEGORISED" : String.valueOf(row[0]);
            result.put(category, new BigDecimalHolder(((Number) row[1]).doubleValue()));
        }
        return result;
    }

    private static class BigDecimalHolder {
        private final double value;

        BigDecimalHolder() {
            this(0);
        }

        BigDecimalHolder(double value) {
            this.value = value;
        }
    }

    // ── Stock movements ───────────────────────────────────────────────────────

    /**
     * Movements that warrant a look.
     *
     * <p>Three distinct signals, all reported as alerts rather than conclusions:
     * a large adjustment with no stated reason, a quantity far outside what this
     * product normally moves in one line, and a pair of identical postings in
     * the same period that may be a double submission.
     *
     * <p>The last is deliberately weak evidence. Two identical deliveries on the
     * same day from the same supplier is entirely normal in a warehouse; it is
     * only worth confirming that the second one was not entered twice.
     */
    @Transactional(readOnly = true)
    public List<Anomaly> detectMovementAnomalies(LocalDate from, LocalDate to) {
        List<StockMovement> movements = stockMovementRepository.findApprovedBetween(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));

        List<Anomaly> anomalies = new ArrayList<>();

        // Group by product so "unusual" is relative to what that product does.
        Map<Long, List<StockMovement>> byProduct = movements.stream()
                .filter(m -> m.getProduct() != null)
                .collect(Collectors.groupingBy(m -> m.getProduct().getId()));

        for (Map.Entry<Long, List<StockMovement>> entry : byProduct.entrySet()) {
            List<StockMovement> productMovements = entry.getValue();
            double[] magnitudes = productMovements.stream()
                    .filter(m -> m.getQuantity() != null)
                    .mapToDouble(m -> Math.abs(m.getQuantity()))
                    .toArray();
            if (magnitudes.length < 5) {
                // Too little of this product's own history to judge against.
                continue;
            }
            String sku = productMovements.get(0).getProduct().getSku();
            String name = productMovements.get(0).getProduct().getName();

            for (StockMovement movement : productMovements) {
                int quantity = movement.getQuantity() == null ? 0 : Math.abs(movement.getQuantity());
                String reference = movement.getReference();

                if (quantity >= properties.getLargeMovementThreshold()
                        && (movement.getReason() == null || movement.getReason().isBlank())
                        && (movement.getNotes() == null || movement.getNotes().isBlank())) {
                    anomalies.add(new Anomaly(
                            "STOCK_MOVEMENT", String.valueOf(movement.getId()),
                            quantity, TimeSeries.median(magnitudes),
                            TimeSeries.modifiedZScore(quantity, magnitudes),
                            "LARGE_UNEXPLAINED_ADJUSTMENT",
                            "A movement of " + quantity + " units of " + name
                                    + " was recorded with no stated reason.",
                            Map.of("productId", entry.getKey(), "sku", sku,
                                    "type", String.valueOf(movement.getType()),
                                    "quantity", quantity,
                                    "movementId", movement.getId(),
                                    "typicalForThisProduct", round(TimeSeries.median(magnitudes)))));
                    continue;
                }

                if (reference != null && !reference.isBlank()) {
                    long duplicates = stockMovementRepository.countMatching(
                            entry.getKey(), movement.getType(), reference, movement.getQuantity());
                    if (duplicates > 1) {
                        anomalies.add(new Anomaly(
                                "STOCK_MOVEMENT", String.valueOf(movement.getId()),
                                quantity, TimeSeries.median(magnitudes), 0,
                                "POSSIBLE_DUPLICATE",
                                "Movement " + movement.getId() + " matches " + duplicates
                                        + " other postings of the same quantity against reference '"
                                        + reference + "'. Two deliveries of the same size in one day is "
                                        + "normal; this is only a prompt to confirm it was not entered twice.",
                                Map.of("movementId", movement.getId(), "reference", reference,
                                        "matches", duplicates, "sku", sku)));
                    }
                }
            }
        }

        // Recurring shrinkage, which is a pattern rather than a single outlier.
        Map<String, long[]> lossCounts = new LinkedHashMap<>();
        for (StockMovement movement : movements) {
            if (!StockMovement.isLoss(movement.getType()) || movement.getProduct() == null) {
                continue;
            }
            long[] counts = lossCounts.computeIfAbsent(movement.getProduct().getSku(),
                    k -> new long[1]);
            counts[0]++;
        }
        for (Map.Entry<String, long[]> entry : lossCounts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue()[0] < 3) {
                continue;
            }
            anomalies.add(new Anomaly(
                    "STOCK_MOVEMENT", entry.getKey(),
                    entry.getValue()[0], 0, entry.getValue()[0],
                    "RECURRING_LOSS",
                    entry.getKey() + " has been written off "
                            + entry.getValue()[0] + " times in this period. A recurring pattern is "
                            + "usually a process problem rather than a one-off.",
                    Map.of("sku", entry.getKey(), "occurrences", entry.getValue()[0],
                            "from", from.toString(), "to", to.toString())));
        }

        return anomalies;
    }

    /**
     * Approved stock movements in a period, for counting by warehouse.
 *
     * <p>Exposed so the assistant can answer "which warehouse had the most
 * *adjustments*" from the same records the anomaly detector uses, rather than
 * from a second query that could disagree with it.
 */
@Transactional(readOnly = true)
public List<StockMovement> movementCountsByWarehouse(LocalDate from, LocalDate to) {
    return stockMovementRepository.findApprovedBetween(
            from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));
}

/**
     * Everything unusual in a period, from every source, for the dashboard.
     *
     * <p>Ordered by how far outside its context each alert sits, so the one worth
     * looking at first is first.
     */
    @Transactional(readOnly = true)
    public List<Anomaly> detectAll(LocalDate from, LocalDate to) {
        List<Anomaly> all = new ArrayList<>();
        all.addAll(detectRevenueAnomalies(from, to));
        all.addAll(detectExpenseAnomalies(from, to));
        all.addAll(detectMovementAnomalies(from, to));
        all.sort((a, b) -> Double.compare(Math.abs(b.score()), Math.abs(a.score())));
        return all;
    }

    /**
     * The numbers behind a revenue alert.
     *
     * <p>Everything here describes the calculation that actually produced the
     * score: the median and spread of the <em>residual</em> history the score was
     * computed from, the level that weekday normally trades at, and how far off
     * it this day landed. Reporting the spread of the raw series instead would
     * be tidier-looking and wrong — the raw series includes the weekly swing
     * that was removed precisely because a business busy on Saturday is not
     * unusual for being busy on Saturday.
     */
    private Map<String, Object> buildEvidence(double value, double residual,
                                              double[] history, double expected,
                                              double relativeDeviation, double rawMad,
                                              double mean, double sd) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("value", round(value));
        evidence.put("expectedForThisWeekday", round(expected));
        evidence.put("differencePercent", round(relativeDeviation * 100));
        evidence.put("medianOfHistory", round(TimeSeries.median(history)));
        evidence.put("madOfHistory", round(TimeSeries.medianAbsoluteDeviation(history)));
        evidence.put("meanOfHistory", round(TimeSeries.mean(history)));
        evidence.put("stdDevOfHistory", round(TimeSeries.standardDeviation(history)));
        evidence.put("historyLength", history.length);
        evidence.put("method",
                "modified z-score (0.6745 x deviation / MAD) on the residual after removing "
                        + "the weekly pattern, compared with the preceding "
                        + history.length + " days");
        evidence.put("baseline",
                "what this weekday normally trades at, taken from the same window");
        evidence.put("minimumDeviationPercent",
                round(properties.getAnomalyMinimumRelativeDeviation() * 100));
        evidence.put("rawSeriesMad", round(rawMad));
        evidence.put("rawSeriesMean", round(mean));
        evidence.put("rawSeriesStdDev", round(sd));
        return evidence;
    }

    private static String titleCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * One alert.
     *
     * @param source   REVENUE, EXPENSE or STOCK_MOVEMENT
     * @param period   the day, range or record the alert concerns
     * @param value    what was observed
     * @param baseline what it is being compared against
     * @param score    modified z-score, or the percentage change for expenses
     * @param kind     what kind of unusual this is
     * @param summary  plain-language description, no jargon
     * @param evidence the numbers behind the alert, for verification
     */
    public record Anomaly(String source, String period, double value, double baseline,
                          double score, String kind, String summary,
                          Map<String, Object> evidence) {
    }
}