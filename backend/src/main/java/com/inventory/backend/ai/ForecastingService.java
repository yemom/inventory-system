package com.inventory.backend.ai;

import com.inventory.backend.model.SaleOrderItem;
import com.inventory.backend.model.WarehouseStock;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.StockMovementRepository;
import com.inventory.backend.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
}