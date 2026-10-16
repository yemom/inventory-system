package com.inventory.backend.ai;

import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.model.SaleOrderItem;
import com.inventory.backend.model.User;
import com.inventory.backend.model.WarehouseStock;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.StockMovementRepository;
import com.inventory.backend.repository.WarehouseStockRepository;
import com.inventory.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Sales and demand forecasting, from validated transactions only.
 *
 * <p>Inputs are read from persisted, non-cancelled sales. Days with no recorded
 * sale are treated as zero rather than skipped: a shop that did not sell on
 * Tuesday did sell nothing on Tuesday, and omitting those days would make every
 * series look like it never dips, inflating both the average and the forecast.
 *
 * <p>Every forecast carries its own history, its own model, its measured
 * accuracy and an interval, and every one of those is reported alongside the
 * number rather than behind it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastingService {

    /** Key under which the revenue model is registered. */
    public static final String MODEL_REVENUE_DAILY = "demand:revenue:daily";
    /** Key under which each product's demand model is registered. */
    public static final String MODEL_PRODUCT_DAILY = "demand:product:daily";

    private final SaleOrderRepository saleOrderRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final AiProperties properties;
    private final AiModelRegistry registry;

    // ── Revenue forecast ──────────────────────────────────────────────────────

    /**
     * Forecasts net sales for the coming days.
     *
     * <p>Returns the forecast together with the history it came from, so the
     * chart can show actuals next to predictions. A forecast presented without
     * the data it was derived from cannot be judged by the person deciding
     * whether to act on it.
     */
    @Transactional(readOnly = true)
    public RevenueForecast forecastRevenue(LocalDate from, LocalDate to, int horizon) {
        List<Object[]> rows = saleOrderRepository.sumNetSalesPerDay(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX));

        // A dense series: every calendar day in the window, zero where nothing
        // was sold. Gaps left out would make the trend line flatter than reality.
        Map<LocalDate, Double> byDate = new LinkedHashMap<>();
        for (Object[] row : rows) {
            byDate.put(((java.sql.Date) row[0]).toLocalDate(),
                    ((Number) row[1]).doubleValue());
        }
        List<LocalDate> dates = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            dates.add(d);
            values.add(byDate.getOrDefault(d, 0.0));
        }

        double[] series = values.stream().mapToDouble(Double::doubleValue).toArray();

        // A window with no recorded sales at all produces a technically
        // valid model of a flat line at zero, and therefore a confident
        // "zero" forecast. That is worse than no forecast: a dashboard showing
        // "next 14 days: 0.00" reads as a prediction about a trading business,
        // when in fact the business has no history to predict from.
        double total = TimeSeries.mean(series) * series.length;
        if (series.length < TimeSeries.MIN_OBSERVATIONS_DAILY || total <= 0) {
            return RevenueForecast.rejected(from, to, dates, values,
                    series.length < TimeSeries.MIN_OBSERVATIONS_DAILY
                            ? "Only " + series.length + " days of history; "
                            + TimeSeries.MIN_OBSERVATIONS_DAILY + " are needed to model this "
                            + "reliably."
                            : "No sales are recorded in this period, so there is nothing to "
                            + "forecast from. A zero here would be an artefact of an empty "
                            + "period, not a prediction.");
        }

        TimeSeries.ForecastFit fit = TimeSeries.selectBestModel(series, Math.min(14, series.length / 4));

        if (!fit.available()) {
            return RevenueForecast.rejected(from, to, dates, values, fit.explanation());
        }

        TimeSeries.BacktestResult backtest = TimeSeries.backtest(series, 7, 7);

        double sigma = TimeSeries.standardDeviation(series);
        double z = TimeSeries.zScoreFor(properties.getConfidence());
        double[][] bounds = TimeSeries.predictionBounds(fit.forecast(), sigma, z);
        double[] point = fit.forecast();

        List<ForecastPoint> points = new ArrayList<>();
        for (int i = 0; i < point.length; i++) {
            points.add(new ForecastPoint(to.plusDays(i + 1L), round(point[i]),
                    round(bounds[0][i]), round(bounds[1][i])));
        }

        registry.register(MODEL_REVENUE_DAILY, fit.algorithm(),
                from, to, series.length, fit,
                Map.of("alpha_beta_grid", "0.05:0.95 / 0.01:0.40",
                        "holdout", Math.min(14, series.length / 4),
                        "confidence", properties.getConfidence(),
                        "backtestMAE", round(backtest.mae()),
                        "backtestRMSE", round(backtest.rmse())));

        return new RevenueForecast(true, from, to, dates, values,
                points, fit.algorithm(),
                round(fit.mae()), round(fit.rmse()),
                backtest.mape() == null ? null : round(backtest.mape() * 100),
                fit.explanation(),
                registry.describe(MODEL_REVENUE_DAILY),
                round(TimeSeries.trendPercentPerPeriod(series)),
                "Daily net sales, excluding tax, from recorded sales only");
    }

    // ── Per-product demand ────────────────────────────────────────────────────

    /**
     * Forecasts daily unit demand for one product.
     *
     * <p>Quantity rather than revenue, because that is what a reorder decision
     * actually depends on: a product whose price was raised 40% looks four times
     * more valuable without selling one more unit, and forecasting revenue would
     * recommend buying stock nobody asked for.
     */
    @Transactional(readOnly = true)
    public DemandForecast forecastProductDemand(Long productId, int horizon) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(properties.getDemandHistoryDays());

        List<Object[]> rows = saleOrderRepository.sumUnitsPerProductPerDay(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX), productId);

        Map<LocalDate, Double> byDate = new LinkedHashMap<>();
        for (Object[] row : rows) {
            byDate.put(((java.sql.Date) row[0]).toLocalDate(),
                    ((Number) row[2]).doubleValue());
        }

        // Only days on which the product could have been bought. Before the
        // product existed there is no demand to forecast, and counting those days
        // as zero-demand would depress the average for ever.
        LocalDate firstSale = byDate.keySet().stream().min(LocalDate::compareTo).orElse(null);
        LocalDate seriesStart = firstSale != null && firstSale.isAfter(from) ? firstSale : from;

        List<LocalDate> dates = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (LocalDate d = seriesStart; !d.isAfter(to); d = d.plusDays(1)) {
            dates.add(d);
            values.add(byDate.getOrDefault(d, 0.0));
        }
        double[] series = values.stream().mapToDouble(Double::doubleValue).toArray();

        // A product with no recorded sales yields a window of zeros. Length
        // alone would pass the sufficiency check and produce a confident
        // forecast of zero demand — which reads as a prediction about a product
        // nobody buys, rather than as an absence of information. Velocity and
        // demand forecasts require a product that has actually sold something.
        double totalUnits = TimeSeries.mean(series) * series.length;
        if (series.length < properties.getMinimumDemandObservations() || totalUnits <= 0) {
            return DemandForecast.insufficient(productId, series.length,
                    properties.getMinimumDemandObservations(),
                    totalUnits <= 0
                            ? "This product has no recorded sales, so its demand cannot be "
                            + "estimated. No quantity is forecast for it, and reorder advice "
                            + "falls back to its configured reorder level."
                            : "Only " + series.length + " days of sales history for this product; "
                            + properties.getMinimumDemandObservations()
                            + " are needed before its demand can be modelled.");
        }

        TimeSeries.ForecastFit fit = TimeSeries.selectBestModel(series, Math.min(14, series.length / 4));
        if (!fit.available()) {
            return DemandForecast.insufficient(productId, series.length,
                    properties.getMinimumDemandObservations(), fit.explanation());
        }

        double[] point = fit.forecast();
        double sigma = TimeSeries.standardDeviation(series);
        double z = TimeSeries.zScoreFor(properties.getConfidence());
        double[][] bounds = TimeSeries.predictionBounds(point, sigma, z);

        List<ForecastPoint> points = new ArrayList<>();
        for (int i = 0; i < point.length; i++) {
            points.add(new ForecastPoint(to.plusDays(i + 1L), round(point[i]),
                    round(bounds[0][i]), round(bounds[1][i])));
        }

        double dailyMean = TimeSeries.mean(series);

        return new DemandForecast(productId, true, dates, values, points,
                fit.algorithm(), round(fit.mae()), round(fit.rmse()),
                fit.mape() == null ? null : round(fit.mape() * 100),
                round(dailyMean), TimeSeries.coefficientOfVariation(series),
                round(TimeSeries.trendPercentPerPeriod(series)),
                fit.explanation(), series.length);
    }

    /**
     * Demand forecasts for the products that most deserve one.
     *
     * <p>Ranked by units sold, capped, and each carries its own history. Fitting
     * a model per product for a catalogue of thousands would be both slow and
     * mostly wasted: a product with three sales in the year cannot be forecast,
     * and saying so is more useful than a number derived from three points.
     */
    @Transactional(readOnly = true)
    public List<DemandForecast> forecastTopProducts(int horizon, int limit) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(properties.getDemandHistoryDays());

        List<Object[]> rows = saleOrderRepository.sumUnitsPerProductPerDay(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX), null);

        Map<Long, Double> unitsByProduct = new HashMap<>();
        for (Object[] row : rows) {
            long productId = ((Number) row[1]).longValue();
            unitsByProduct.merge(productId, ((Number) row[2]).doubleValue(), Double::sum);
        }

        int cap = Math.min(limit <= 0 ? properties.getMaxDemandModels() : limit,
                properties.getMaxDemandModels());

        List<DemandForecast> forecasts = new ArrayList<>();
        unitsByProduct.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(cap)
                .forEach(entry -> forecasts.add(forecastProductDemand(entry.getKey(), horizon)));
        return forecasts;
    }

    /**
     * How fast a product sells, in units a day.
     *
     * <p>Computed from the sales ledger directly rather than from the demand
     * model, so it is available even for a product with too little history to
     * model. A product that sold 10 units in 90 days has a real velocity of
     * 0.11 a day whether or not a forecast can be fitted, and the reorder
     * advice needs the velocity either way.
     */
    @Transactional(readOnly = true)
    public double averageDailyUnits(Long productId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(properties.getDemandHistoryDays());

        List<Object[]> rows = saleOrderRepository.sumUnitsPerProductPerDay(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX), productId);
        double total = 0;
        LocalDate first = null;
        for (Object[] row : rows) {
            total += ((Number) row[2]).doubleValue();
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            if (first == null || date.isBefore(first)) {
                first = date;
            }
        }
        if (total <= 0 || first == null) {
            return 0;
        }
        // Measured from the first recorded sale, not from the window start: a
        // product listed this year should not be treated as having sold nothing
        // for the months before it existed.
        long days = java.time.temporal.ChronoUnit.DAYS.between(first, to) + 1;
        return days <= 0 ? 0 : total / days;
    }

    /**
     * Turnover of one warehouse: units issued in the period divided by units held.
     *
     * <p>Used to compare warehouses. A high ratio on a tiny base is not
     * necessarily busier, so the absolute movement is reported alongside it and
     * the ratio is never presented on its own.
     */
    @Transactional(readOnly = true)
    public WarehouseTurnover warehouseTurnover(Long warehouseId, int days) {
        List<WarehouseStock> stock = warehouseStockRepository.findByWarehouseId(warehouseId);
        int onHand = stock.stream().mapToInt(WarehouseStock::getQuantity).sum();

        LocalDateTime from = LocalDateTime.now().minusDays(days);
        LocalDateTime to = LocalDateTime.now();
        List<com.inventory.backend.model.StockMovement> movements =
                stockMovementRepository.findForWarehouseBetween(warehouseId, from, to);

        // Transfers out are not turnover in the commercial sense: the stock is
        // still in the business. Counting them would make a distribution centre
        // look busier than a shop purely by supplying the shop.
        double issued = movements.stream()
                .filter(m -> m.getQuantity() != null && m.getQuantity() < 0)
                .filter(m -> !com.inventory.backend.model.StockMovement.TYPE_TRANSFER_OUT.equals(m.getType()))
                .mapToDouble(m -> Math.abs(m.getQuantity()))
                .sum();
        double received = movements.stream()
                .filter(m -> m.getQuantity() != null && m.getQuantity() > 0)
                .filter(m -> !com.inventory.backend.model.StockMovement.TYPE_TRANSFER_IN.equals(m.getType()))
                .mapToDouble(m -> m.getQuantity())
                .sum();
        double transferred = movements.stream()
                .filter(m -> com.inventory.backend.model.StockMovement.TYPE_TRANSFER_OUT.equals(m.getType())
                        || com.inventory.backend.model.StockMovement.TYPE_TRANSFER_IN.equals(m.getType()))
                .mapToDouble(m -> Math.abs(m.getQuantity() == null ? 0 : m.getQuantity()))
                .sum();

        double turnover = onHand <= 0 ? 0 : issued / onHand;
        return new WarehouseTurnover(warehouseId, onHand, issued, received,
                transferred, round(turnover), days);
    }

    /** A warehouse's activity, with the raw counts behind the ratio. */
    public record WarehouseTurnover(Long warehouseId, int unitsOnHand,
                                    double unitsIssued, double unitsReceived,
                                    double unitsTransferred,
                                    double turnoverRatio, int periodDays) {
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Result types ──────────────────────────────────────────────────────────

    /**
     * One forecast point with its interval.
     *
     * @param date  the day being forecast
     * @param value the point forecast
     * @param lower lower bound of the prediction interval
     * @param upper upper bound of the prediction interval
     */
    public record ForecastPoint(LocalDate date, double value, double lower, double upper) {
    }

    /** A revenue forecast with the history it came from. */
    public record RevenueForecast(boolean available,
                                  LocalDate from, LocalDate to,
                                  List<LocalDate> historyDates, List<Double> historyValues,
                                  List<ForecastPoint> forecast,
                                  String algorithm,
                                  Double mae, Double rmse, Double mapePercent,
                                  String explanation,
                                  String modelVersion,
                                  Double trendPercent,
                                  String basis) {

        public static RevenueForecast rejected(LocalDate from, LocalDate to,
                                               List<LocalDate> dates, List<Double> values,
                                               String reason) {
            return new RevenueForecast(false, from, to, dates, values, List.of(),
                    null, null, null, null, reason, null, null,
                    "Daily net sales, excluding tax, from recorded sales only");
        }
    }

    /** A per-product demand forecast, or a refusal with the reason. */
    public record DemandForecast(Long productId,
                                 boolean available,
                                 List<LocalDate> historyDates, List<Double> historyValues,
                                 List<ForecastPoint> forecast,
                                 String algorithm,
                                 Double mae, Double rmse, Double mapePercent,
                                 Double averageDailyUnits,
                                 double coefficientOfVariation,
                                 Double trendPercent,
                                 String explanation,
                                 int observationCount) {

        public static DemandForecast insufficient(Long productId, int observations,
                                                  int required, String reason) {
            return new DemandForecast(productId, false, List.of(), List.of(), List.of(),
                    null, null, null, null, null, 0, null, reason, observations);
        }
    }

    // ── Operational & Cashier Forecasting ─────────────────────────────────────

    /**
     * Operational forecast and AI-assisted insights for staff and cashiers.
     * Scoped server-side to the authenticated user's permissions and branch.
     */
    @Transactional(readOnly = true)
    public OperationalSalesForecast forecastOperationalSales(Long warehouseId, Long requestedSellerId) {
        User actor = currentUser();
        boolean canSeeEveryone = canSeeEveryonesSales();
        Long sellerScope = canSeeEveryone ? requestedSellerId : (actor != null ? actor.getId() : -1L);
        String scope = sellerScope == null ? "ALL_SALES" : "OWN_SALES";
        String cashierName = (sellerScope != null && actor != null && sellerScope.equals(actor.getId()))
                ? actor.getFullName() : (sellerScope != null ? "Seller #" + sellerScope : "All Staff");
        String branch = actor != null ? actor.getBranch() : null;

        LocalDate today = LocalDate.now();
        int historyLookback = properties.getHistoryDays() > 0 ? Math.min(properties.getHistoryDays(), 60) : 30;
        LocalDate historyStart = today.minusDays(historyLookback);

        List<SaleOrder> orders = saleOrderRepository.findSalesForPeriod(
                historyStart.atStartOfDay(), today.atTime(LocalTime.MAX),
                null, warehouseId, sellerScope, null, false);

        // Separate today vs historical past days
        BigDecimal todaySalesSoFar = BigDecimal.ZERO;
        int todayOrdersSoFar = 0;

        Map<LocalDate, BigDecimal> salesByDay = new LinkedHashMap<>();
        Map<LocalDate, Integer> ordersByDay = new LinkedHashMap<>();
        Map<Integer, BigDecimal> salesByHourHistory = new HashMap<>();
        BigDecimal totalHistoricalHourlySales = BigDecimal.ZERO;

        // Populate days
        for (LocalDate d = historyStart; !d.isAfter(today); d = d.plusDays(1)) {
            salesByDay.put(d, BigDecimal.ZERO);
            ordersByDay.put(d, 0);
        }

        // Product velocity tracking: last 14 days vs prior 14 days
        LocalDate recentWindowStart = today.minusDays(14);
        LocalDate priorWindowStart = today.minusDays(28);
        Map<Long, Integer> productUnitsRecent = new HashMap<>();
        Map<Long, Integer> productUnitsPrior = new HashMap<>();
        Map<Long, String> productNames = new HashMap<>();
        Map<Long, String> productSkus = new HashMap<>();

        for (SaleOrder order : orders) {
            if (order == null || "CANCELLED".equalsIgnoreCase(order.getStatus()) || isVoided(order)) {
                continue;
            }
            LocalDate orderDate = order.getTransactionDate() != null
                    ? order.getTransactionDate().toLocalDate()
                    : (order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate() : today);

            BigDecimal finalAmt = order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO;

            if (orderDate.equals(today)) {
                if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
                    todaySalesSoFar = todaySalesSoFar.add(finalAmt);
                    todayOrdersSoFar++;
                }
            } else {
                salesByDay.compute(orderDate, (k, v) -> (v == null ? BigDecimal.ZERO : v).add(finalAmt));
                ordersByDay.compute(orderDate, (k, v) -> (v == null ? 0 : v) + 1);

                int hour = order.getCreatedAt() != null ? order.getCreatedAt().getHour() : -1;
                if (hour >= 0) {
                    salesByHourHistory.merge(hour, finalAmt, BigDecimal::add);
                    totalHistoricalHourlySales = totalHistoricalHourlySales.add(finalAmt);
                }
            }

            // Products
            if (order.getItems() != null) {
                for (SaleOrderItem item : order.getItems()) {
                    if (item.getProduct() != null && item.getProduct().getId() != null) {
                        Long pId = item.getProduct().getId();
                        productNames.putIfAbsent(pId, item.getProduct().getName());
                        productSkus.putIfAbsent(pId, item.getProduct().getSku());
                        int qty = Math.max(0, (item.getQuantity() != null ? item.getQuantity() : 0)
                                - (item.getQuantityReturned() != null ? item.getQuantityReturned() : 0));
                        if (!orderDate.isBefore(recentWindowStart)) {
                            productUnitsRecent.merge(pId, qty, Integer::sum);
                        } else if (!orderDate.isBefore(priorWindowStart) && orderDate.isBefore(recentWindowStart)) {
                            productUnitsPrior.merge(pId, qty, Integer::sum);
                        }
                    }
                }
            }
        }

        // Daily series for past days
        List<Double> pastSalesValues = new ArrayList<>();
        List<Double> pastOrdersValues = new ArrayList<>();
        for (LocalDate d = historyStart; d.isBefore(today); d = d.plusDays(1)) {
            pastSalesValues.add(salesByDay.getOrDefault(d, BigDecimal.ZERO).doubleValue());
            pastOrdersValues.add(ordersByDay.getOrDefault(d, 0).doubleValue());
        }

        double[] salesSeries = pastSalesValues.stream().mapToDouble(Double::doubleValue).toArray();
        double[] ordersSeries = pastOrdersValues.stream().mapToDouble(Double::doubleValue).toArray();

        int observations = salesSeries.length;
        double totalHistoricalSales = Arrays.stream(salesSeries).sum();
        double meanDailySales = TimeSeries.mean(salesSeries);
        double meanDailyOrders = TimeSeries.mean(ordersSeries);

        boolean hasData = observations >= 5 && totalHistoricalSales > 0;
        boolean isReliable = observations >= 14 && hasData;

        // Hourly distribution & peak hours
        int currentHour = LocalDateTime.now().getHour();
        double remainingFraction = 0.0;
        double passedFraction = 0.0;
        List<Integer> peakHours = new ArrayList<>();

        if (totalHistoricalHourlySales.compareTo(BigDecimal.ZERO) > 0) {
            final double grandHourlyTotal = totalHistoricalHourlySales.doubleValue();
            salesByHourHistory.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(3)
                    .forEach(e -> peakHours.add(e.getKey()));
            Collections.sort(peakHours);

            for (int h = 0; h < 24; h++) {
                double fraction = salesByHourHistory.getOrDefault(h, BigDecimal.ZERO).doubleValue() / grandHourlyTotal;
                if (h > currentHour) {
                    remainingFraction += fraction;
                } else {
                    passedFraction += fraction;
                }
            }
        } else {
            // Default business day distribution: 9 AM to 8 PM
            int businessHours = 12;
            int startHour = 8;
            for (int h = 0; h < 24; h++) {
                double frac = (h >= startHour && h < startHour + businessHours) ? (1.0 / businessHours) : 0.0;
                if (h > currentHour) {
                    remainingFraction += frac;
                } else {
                    passedFraction += frac;
                }
            }
            peakHours.addAll(List.of(11, 12, 14, 15));
        }

        String peakHoursDesc = formatPeakHours(peakHours);

        // Expected remainder today
        BigDecimal expectedRemainderToday;
        int expectedRemainderTodayOrders;

        if (currentHour >= 21) {
            expectedRemainderToday = BigDecimal.ZERO;
            expectedRemainderTodayOrders = 0;
        } else {
            double baseDaily = Math.max(meanDailySales, todaySalesSoFar.doubleValue());
            double remEstimate = baseDaily * Math.min(1.0, remainingFraction);
            if (passedFraction > 0.20 && todaySalesSoFar.compareTo(BigDecimal.ZERO) > 0) {
                double currentPace = (todaySalesSoFar.doubleValue() / Math.max(0.1, passedFraction)) * remainingFraction;
                remEstimate = 0.6 * remEstimate + 0.4 * currentPace;
            }
            expectedRemainderToday = BigDecimal.valueOf(Math.max(0.0, round(remEstimate)));

            double remOrders = Math.max(meanDailyOrders, (double) todayOrdersSoFar) * Math.min(1.0, remainingFraction);
            expectedRemainderTodayOrders = (int) Math.round(Math.max(0.0, remOrders));
        }

        BigDecimal expectedTodayTotal = todaySalesSoFar.add(expectedRemainderToday);
        int expectedTodayTotalOrders = todayOrdersSoFar + expectedRemainderTodayOrders;

        // Next 7 days forecast
        List<ForecastPoint> next7DaysDailyForecast = new ArrayList<>();
        List<Integer> next7DaysOrderForecast = new ArrayList<>();
        BigDecimal expected7DaySalesTotal = BigDecimal.ZERO;
        BigDecimal expectedNextDaySales = BigDecimal.ZERO;
        int expectedNextDayOrders = (int) Math.round(meanDailyOrders);

        String algorithm = "Baseline Moving Average";
        Double mae = null;
        Double rmse = null;
        Double mape = null;

        if (hasData) {
            TimeSeries.ForecastFit fit = TimeSeries.selectBestModel(salesSeries, Math.min(7, salesSeries.length / 4));
            if (fit.available()) {
                algorithm = fit.algorithm();
                mae = round(fit.mae());
                rmse = round(fit.rmse());
                mape = fit.mape() != null ? round(fit.mape() * 100) : null;
            }
            double[] point = fit.forecast();
            double sigma = TimeSeries.standardDeviation(salesSeries);
            double z = TimeSeries.zScoreFor(properties.getConfidence());
            double[][] bounds = TimeSeries.predictionBounds(point, sigma, z);

            for (int i = 0; i < Math.min(7, point.length); i++) {
                LocalDate fcDate = today.plusDays(i + 1L);
                double val = Math.max(0.0, round(point[i]));
                double low = Math.max(0.0, round(bounds[0][i]));
                double up = Math.max(val, round(bounds[1][i]));
                next7DaysDailyForecast.add(new ForecastPoint(fcDate, val, low, up));
                expected7DaySalesTotal = expected7DaySalesTotal.add(BigDecimal.valueOf(val));

                // Order forecast
                int fcOrders = Math.max(1, (int) Math.round(meanDailyOrders * (meanDailySales > 0 ? (val / meanDailySales) : 1.0)));
                next7DaysOrderForecast.add(fcOrders);
            }
        } else {
            algorithm = "Simple Trend Baseline";
            for (int i = 0; i < 7; i++) {
                LocalDate fcDate = today.plusDays(i + 1L);
                next7DaysDailyForecast.add(new ForecastPoint(fcDate, round(meanDailySales), 0.0, round(meanDailySales * 1.5)));
                next7DaysOrderForecast.add(Math.max(0, (int) Math.round(meanDailyOrders)));
                expected7DaySalesTotal = expected7DaySalesTotal.add(BigDecimal.valueOf(meanDailySales));
            }
        }

        if (!next7DaysDailyForecast.isEmpty()) {
            expectedNextDaySales = BigDecimal.valueOf(next7DaysDailyForecast.get(0).value());
            expectedNextDayOrders = next7DaysOrderForecast.get(0);
        }

        // AOV
        BigDecimal currentAov = todayOrdersSoFar > 0
                ? todaySalesSoFar.divide(BigDecimal.valueOf(todayOrdersSoFar), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        int totalOrdersCount = Arrays.stream(ordersSeries).mapToInt(d -> (int) d).sum();
        BigDecimal expectedAov = totalOrdersCount > 0
                ? BigDecimal.valueOf(totalHistoricalSales).divide(BigDecimal.valueOf(totalOrdersCount), 2, RoundingMode.HALF_UP)
                : currentAov;

        // Product trends
        List<OperationalSalesForecast.ProductDemandTrend> productTrends = new ArrayList<>();
        Set<Long> allProductIds = new HashSet<>(productUnitsRecent.keySet());
        allProductIds.addAll(productUnitsPrior.keySet());

        for (Long pId : allProductIds) {
            int recent = productUnitsRecent.getOrDefault(pId, 0);
            int prior = productUnitsPrior.getOrDefault(pId, 0);
            if (recent == 0 && prior == 0) continue;
            double changePct = prior > 0
                    ? ((double) (recent - prior) / prior) * 100.0
                    : (recent > 0 ? 100.0 : 0.0);
            String direction = changePct >= 15.0 ? "INCREASING" : (changePct <= -15.0 ? "DECREASING" : "STABLE");
            productTrends.add(new OperationalSalesForecast.ProductDemandTrend(
                    pId,
                    productSkus.getOrDefault(pId, "P-" + pId),
                    productNames.getOrDefault(pId, "Product #" + pId),
                    recent,
                    prior,
                    round(changePct),
                    direction
            ));
        }

        productTrends.sort((a, b) -> Integer.compare(b.unitsSoldRecent(), a.unitsSoldRecent()));
        if (productTrends.size() > 5) {
            productTrends = productTrends.subList(0, 5);
        }

        // AI Explanations
        String reliabilityExplanation = isReliable
                ? String.format("High confidence forecast derived from %d trading days of transaction records.", observations)
                : (hasData
                ? String.format("Moderate preliminary estimate based on %d days of sales records. As more orders occur, confidence will improve.", observations)
                : "Insufficient transaction records for this scope. Estimates are based on initial trading activity.");

        String baselineComparison;
        if (meanDailySales > 0) {
            double diff = expectedTodayTotal.doubleValue() - meanDailySales;
            double diffPct = round((diff / meanDailySales) * 100.0);
            baselineComparison = diffPct >= 0
                    ? String.format("Today's projected sales total of ETB %,.2f is pacing +%.1f%% above your recent daily baseline of ETB %,.2f.",
                    expectedTodayTotal.doubleValue(), diffPct, meanDailySales)
                    : String.format("Today's projected sales total of ETB %,.2f is pacing %.1f%% below your recent daily baseline of ETB %,.2f.",
                    expectedTodayTotal.doubleValue(), Math.abs(diffPct), meanDailySales);
        } else {
            baselineComparison = String.format("Recorded sales today stand at ETB %,.2f across %d order(s).",
                    todaySalesSoFar.doubleValue(), todayOrdersSoFar);
        }

        String peakHoursExplanation = String.format(
                "Peak sales volume historically concentrates around %s, which represents the strongest trading window for this scope.",
                peakHoursDesc);

        String productDemandExplanation;
        if (!productTrends.isEmpty()) {
            OperationalSalesForecast.ProductDemandTrend topTrending = productTrends.get(0);
            productDemandExplanation = String.format(
                    "Highest observed velocity is for '%s' (%d units recent). Demand trend is %s.",
                    topTrending.name(), topTrending.unitsSoldRecent(), topTrending.direction().toLowerCase());
        } else {
            productDemandExplanation = "Product-level velocity will automatically detail top demand items as line-item history accumulates.";
        }

        String operationalRecommendation = String.format(
                "Prepare register and replenish inventory ahead of peak trading (%s). Ensure all cash/bank transaction slips are reconciled promptly.",
                peakHoursDesc);

        String decisionReadiness = isReliable
                ? "Forecast confidence is sufficient to support restocking, cash preparation, and staffing decisions."
                : "Treat forecasts as directional guidelines while building further transaction history.";

        OperationalSalesForecast.AiExplanations aiInsights = new OperationalSalesForecast.AiExplanations(
                baselineComparison,
                peakHoursExplanation,
                productDemandExplanation,
                operationalRecommendation,
                decisionReadiness,
                false
        );

        return new OperationalSalesForecast(
                true,
                scope,
                cashierName,
                branch,
                today,
                LocalDateTime.now(),
                algorithm,
                observations,
                orders.size(),
                properties.getConfidence(),
                mae,
                rmse,
                mape,
                isReliable,
                reliabilityExplanation,
                todaySalesSoFar,
                expectedRemainderToday,
                expectedTodayTotal,
                expectedNextDaySales,
                expected7DaySalesTotal,
                next7DaysDailyForecast,
                todayOrdersSoFar,
                expectedRemainderTodayOrders,
                expectedTodayTotalOrders,
                expectedNextDayOrders,
                next7DaysOrderForecast,
                currentAov,
                expectedAov,
                productTrends,
                peakHours,
                peakHoursDesc,
                aiInsights
        );
    }

    private static String formatPeakHours(List<Integer> hours) {
        if (hours == null || hours.isEmpty()) {
            return "11:00 AM – 3:00 PM";
        }
        int minH = hours.stream().min(Integer::compareTo).orElse(11);
        int maxH = hours.stream().max(Integer::compareTo).orElse(15);
        return formatHour(minH) + " – " + formatHour(maxH + 1);
    }

    private static String formatHour(int h) {
        int hour12 = h % 12 == 0 ? 12 : h % 12;
        String ampm = h < 12 || h == 24 ? "AM" : "PM";
        return hour12 + ":00 " + ampm;
    }

    private boolean isVoided(SaleOrder order) {
        return order.getVoidStatus() != null && "APPROVED".equalsIgnoreCase(order.getVoidStatus());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            return null;
        }
        return principal.getUser();
    }

    private boolean canSeeEveryonesSales() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> "SALES_REPORT_VIEW".equals(granted.getAuthority())
                        || "REPORT_VIEW".equals(granted.getAuthority()));
    }

    // ── Operational sales forecast record ─────────────────────────────────────

    public record OperationalSalesForecast(
            boolean available,
            String scope,
            String cashierName,
            String branch,
            LocalDate asOfDate,
            LocalDateTime generatedAt,
            String algorithm,
            int historyDays,
            int observationCount,
            double confidenceLevel,
            Double mae,
            Double rmse,
            Double mapePercent,
            boolean isReliable,
            String reliabilityExplanation,
            BigDecimal todaySalesSoFar,
            BigDecimal expectedRemainderToday,
            BigDecimal expectedTodayTotal,
            BigDecimal expectedNextDaySales,
            BigDecimal expected7DaySalesTotal,
            List<ForecastPoint> next7DaysDailyForecast,
            int todayOrdersSoFar,
            int expectedRemainderTodayOrders,
            int expectedTodayTotalOrders,
            int expectedNextDayOrders,
            List<Integer> next7DaysOrderForecast,
            BigDecimal currentAov,
            BigDecimal expectedAov,
            List<ProductDemandTrend> productTrends,
            List<Integer> peakHours,
            String peakHoursDescription,
            AiExplanations aiInsights
    ) {
        public record ProductDemandTrend(
                Long productId,
                String sku,
                String name,
                int unitsSoldRecent,
                int unitsSoldPrior,
                double trendPercent,
                String direction
        ) {}

        public record AiExplanations(
                String baselineComparison,
                String peakHoursExplanation,
                String productDemandExplanation,
                String operationalRecommendation,
                String decisionReadiness,
                boolean aiEnriched
        ) {}
    }
}