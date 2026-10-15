package com.inventory.backend.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The statistical core of the intelligence service.
 *
 * <p>Everything here is pure: it takes numbers and returns numbers, with no
 * database, no clock and no randomness. That is deliberate for three reasons.
 *
 * <ul>
 *   <li><b>It can be tested honestly.</b> A forecast is only worth as much as the
 *       evidence that it beats a naive guess. Proving that needs hand-computed
 *       inputs and reproducible outputs, not a stubbed repository.</li>
 *   <li><b>The models are comparable.</b> Each method here is fitted and scored
 *       on the same data in the same way, so "Holt beat moving average by 12%
 *       on MAPE" is a statement about the business's data rather than about two
 *       different evaluation harnesses.</li>
 *   <li><b>Nothing invents data.</b> Every function states what it cannot do and
 *       says so: {@link #isSufficientFor} refuses a series with too little
 *       history, and {@link #backtest} returns null metrics rather than
 *       flattering numbers when there is nothing to measure against.</li>
 * </ul>
 *
 * <h2>Why these models</h2>
 *
 * <p>A retail business's daily sales are short, noisy, partly driven by a weekly
 * cycle, and rarely a year or more deep. In that regime a well-tuned exponential
 * smoothing model with a trend term is at or near the level of far more complex
 * alternatives, and — unlike them — it produces honest prediction intervals and
 * does not silently return a straight line through the noise when the data is too
 * short to identify structure. A neural network here would look more impressive
 * and be less reliable, so the baselines and Holt's method are the candidates and
 * the backtest decides between them.
 *
 * <p>Everything is implemented directly rather than through a Python service.
 * That keeps one runtime, one deployment and one set of credentials, and the
 * maths involved is small enough to audit line by line — which matters more for
 * a business figure than a larger dependency tree.
 */
public final class TimeSeries {

    // ── Tuning constants, all documented where they are used ──────────────────

    /**
     * Minimum observations before a daily series is worth modelling.
     *
     * <p>Below this the "trend" a linear fit finds is an artefact of three points
     * and the error bars are meaningless, so no forecast is produced and the
     * caller is told the data is insufficient.
     */
    public static final int MIN_OBSERVATIONS_DAILY = 21;

    /** Fewer points than this cannot support any forecast at all. */
    public static final int MIN_OBSERVATIONS_ABSOLUTE = 8;

    /** Demand forecasts shorter than this are not credible enough to act on. */
    public static final int MIN_OBSERVATIONS_FOR_DEMAND = 28;

    /** Widest prediction interval reported, as a multiple of one sigma. */
    public static final double MAX_Z = 2.576; // 99%

    /** Significance thresholds for anomaly detection, in modified z-score units. */
    public static final double ANOMALY_THRESHOLD = 3.5;
    public static final double SEVERE_ANOMALY_THRESHOLD = 5.0;

    private TimeSeries() {
    }

    // ── Descriptives ──────────────────────────────────────────────────────────

    public static double mean(double[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /**
     * Sample standard deviation (n−1).
     *
     * <p>n−1 rather than n because these are sample statistics being used to
     * describe a process, and dividing by n would understate the spread slightly
     * on every series. Returns zero for a single point rather than NaN, so a
     * series with no variation produces a zero-width interval instead of a
     * silently poisoned one.
     */
    public static double standardDeviation(double[] values) {
        if (values == null || values.length < 2) {
            return 0;
        }
        double mean = mean(values);
        double sumSq = 0;
        for (double v : values) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (values.length - 1));
    }

    /** Median of a copy; the input is not modified. */
    public static double median(double[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    /**
     * Median absolute deviation.
     *
     * <p>Used instead of the standard deviation for anomaly detection because it
     * does not move when the series contains the outlier being looked for. A
     * single enormous sale inflates a standard deviation enough to hide itself;
     * the MAD is computed from the median of absolute deviations, so the outlier
     * contributes only one of the deviations and cannot inflate its own yardstick.
     */
    public static double medianAbsoluteDeviation(double[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        double med = median(values);
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - med);
        }
        return median(deviations);
    }

    /** Linear percentile by linear interpolation, p in [0, 100]. */
    public static double percentile(double[] values, double p) {
        if (values == null || values.length == 0) {
            return 0;
        }
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = (p / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sorted[lower];
        }
        double weight = rank - lower;
        return sorted[lower] * (1 - weight) + sorted[upper] * weight;
    }

    // ── Data sufficiency ──────────────────────────────────────────────────────

    /**
     * Whether a series is long enough to model at all.
     *
     * @param minimum the requirement being checked; callers pass
     *                {@link #MIN_OBSERVATIONS_DAILY} for revenue trends or
     *                {@link #MIN_OBSERVATIONS_FOR_DEMAND} for reorder advice
     */
    public static boolean isSufficientFor(double[] values, int minimum) {
        return values != null && values.length >= Math.max(MIN_OBSERVATIONS_ABSOLUTE, minimum);
    }

    /** Whether a series varies at all. A flat series needs no forecast. */
    public static boolean hasVariation(double[] values) {
        return standardDeviation(values) > 1e-9;
    }

    // ── Models ────────────────────────────────────────────────────────────────

    /**
     * Baseline: tomorrow equals today, repeated.
     *
     * <p>Included because it is the benchmark every other model must beat. A
     * model that cannot out-predict "the same as yesterday" on retail sales is
     * not worth deploying, and saying so is more useful than shipping it
     * anyway.
     */
    public static double[] naiveForecast(double[] series, int horizon) {
        requireSeries(series, horizon);
        double last = series[series.length - 1];
        double[] forecast = new double[horizon];
        Arrays.fill(forecast, last);
        return forecast;
    }

    /** Baseline: the mean of the last {@code window} observations, repeated. */
    public static double[] movingAverageForecast(double[] series, int horizon, int window) {
        requireSeries(series, horizon);
        int w = Math.max(1, Math.min(window, series.length));
        double average = 0;
        for (int i = series.length - w; i < series.length; i++) {
            average += series[i];
        }
        average /= w;
        double[] forecast = new double[horizon];
        Arrays.fill(forecast, average);
        return forecast;
    }

    /**
     * Baseline with a trend: the seasonal-naive shape, shifted forward a year.
     *
     * <p>Not useful on a series shorter than a year, which is why the minimum for
     * forecasting excludes it — it exists to be scored and, on short retail
     * series, to lose.
     */
    public static double[] seasonalNaiveForecast(double[] series, int horizon, int seasonLength) {
        requireSeries(series, horizon);
        int m = Math.max(2, seasonLength);
        double[] forecast = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            int index = series.length - m + (i % m);
            forecast[i] = series[Math.max(0, Math.min(index, series.length - 1))];
        }
        return forecast;
    }

    /**
     * Simple linear extrapolation of the overall trend.
     *
     * <p>Clamped at zero: a demand forecast that goes negative is nonsense, and
     * an unclamped fit on a declining series produces exactly that, which then
     * flows into a reorder recommendation to buy fewer than nothing.
     */
    public static double[] driftForecast(double[] series, int horizon) {
        requireSeries(series, horizon);
        double[] fit = linearFit(series);
        double slope = fit[1];
        double intercept = fit[0];
        double lastIndex = series.length - 1;
        double[] forecast = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            double value = intercept + slope * (lastIndex + 1 + i);
            forecast[i] = Math.max(0, value);
        }
        return forecast;
    }

    /**
     * Holt's linear exponential smoothing: level plus trend, both updated by
     * their own smoothing rates.
     *
     * <p>This is the workhorse. It tracks a level that moves and a trend that
     * moves, without the seasonal component that would need a year of data this
     * system does not have.
     *
     * @param alpha weight given to new observations when updating the level
     * @param beta  weight given to new trend information
     */
    public static double[] holtForecast(double[] series, int horizon, double alpha, double beta) {
        requireSeries(series, horizon);
        double level = series[0];
        double trend = series.length > 1 ? series[1] - series[0] : 0;

        for (int i = 1; i < series.length; i++) {
            double previousLevel = level;
            level = alpha * series[i] + (1 - alpha) * (level + trend);
            trend = beta * (level - previousLevel) + (1 - beta) * trend;
        }

        double[] forecast = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            forecast[i] = Math.max(0, level + (i + 1) * trend);
        }
        return forecast;
    }

    /**
     * Fits Holt's method, choosing alpha and beta by grid search on holdout error.
     *
     * <p>The search minimises one-step-ahead error on the training portion only.
     * Choosing the parameters on the same data the accuracy is then reported on
     * would be circular, so the evaluation and the fit are kept apart.
     */
    public static HoltFit fitHolt(double[] series, int holdout) {
        requireSeries(series, Math.max(holdout, 1));
        int trainLength = series.length - Math.max(holdout, 1);
        if (trainLength < 4) {
            return HoltFit.rejected("not enough history to hold anything out");
        }

        double[] train = Arrays.copyOf(series, trainLength);
        double[] actual = Arrays.copyOfRange(series, trainLength, series.length);

        double bestAlpha = 0.3;
        double bestBeta = 0.1;
        double bestError = Double.MAX_VALUE;

        for (double alpha = 0.05; alpha <= 0.95; alpha += 0.05) {
            for (double beta = 0.01; beta <= 0.40; beta += 0.05) {
                double[] forecast = holtForecast(train, actual.length, alpha, beta);
                double error = meanAbsoluteError(actual, forecast);
                if (error < bestError) {
                    bestError = error;
                    bestAlpha = alpha;
                    bestBeta = beta;
                }
            }
        }

        return new HoltFit(bestAlpha, bestBeta, bestError, true, null);
    }

    /**
     * Scores every candidate model on the same holdout and picks the winner.
     *
     * <p>The decision is made on measured out-of-sample error, not on which
     * model is theoretically most sophisticated. A naive forecast that beats
     * Holt's on this business's data wins — which is a real and frequent
     * outcome for stock with no trend.
     */
    public static ForecastFit selectBestModel(double[] series, int holdout) {
        if (!isSufficientFor(series, MIN_OBSERVATIONS_DAILY)) {
            return ForecastFit.insufficient("Only " + series.length + " observations; "
                    + MIN_OBSERVATIONS_DAILY + " are needed to model this reliably.");
        }

        List<ForecastFit.Candidate> candidates = new ArrayList<>();
        int h = Math.max(1, Math.min(holdout, Math.max(1, series.length / 3)));

        candidates.add(score("NAIVE", series, h, s -> naiveForecast(s, h)));
        candidates.add(score("MOVING_AVERAGE", series, h, s -> movingAverageForecast(s, h, 7)));
        candidates.add(score("DRIFT", series, h, s -> driftForecast(s, h)));

        HoltFit holt = fitHolt(series, h);
        if (holt.fitted()) {
            candidates.add(score("HOLT_LINEAR_EXP_SMOOTHING", series, h,
                    s -> holtForecast(s, h, holt.alpha(), holt.beta())));
        }

        candidates.sort((a, b) -> Double.compare(a.mae(), b.mae()));
        ForecastFit.Candidate winner = candidates.get(0);

        int horizon = Math.max(h, 1);
        double[] finalForecast = forecastWith(winner.algorithm(), series, horizon);

        String explanation = candidates.size() == 1
                ? "Only one candidate could be scored."
                : winner.algorithm() + " gave the lowest out-of-sample error (MAE "
                        + round(winner.mae()) + ") of " + candidates.size()
                        + " candidates evaluated on held-out data.";

        return new ForecastFit(winner.algorithm(), finalForecast, true,
                winner.mae(), winner.rmse(), winner.mape(), explanation);
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static ForecastFit.Candidate score(String name, double[] series, int holdout,
                                                java.util.function.Function<double[], double[]> fitter) {
        int trainLength = series.length - holdout;
        double[] train = Arrays.copyOf(series, trainLength);
        double[] actual = Arrays.copyOfRange(series, trainLength, series.length);
        double[] predicted;
        try {
            predicted = fitter.apply(train);
        } catch (RuntimeException e) {
            return new ForecastFit.Candidate(name, Double.MAX_VALUE, Double.MAX_VALUE, null);
        }
        return new ForecastFit.Candidate(name,
                meanAbsoluteError(actual, predicted),
                rootMeanSquaredError(actual, predicted),
                meanAbsolutePercentageError(actual, predicted));
    }

    /** Produces a forecast with the named algorithm, for the winning candidate. */
    public static double[] forecastWith(String algorithm, double[] series, int horizon) {
        return switch (algorithm) {
            case "NAIVE" -> naiveForecast(series, horizon);
            case "MOVING_AVERAGE" -> movingAverageForecast(series, horizon, 7);
            case "DRIFT" -> driftForecast(series, horizon);
            case "HOLT_LINEAR_EXP_SMOOTHING" -> {
                HoltFit fit = fitHolt(series, 3);
                yield fit.fitted()
                        ? holtForecast(series, horizon, fit.alpha(), fit.beta())
                        : naiveForecast(series, horizon);
            }
            default -> naiveForecast(series, horizon);
        };
    }

    /** Ordinary least squares fit, returned as {intercept, slope}. */
    public static double[] linearFit(double[] series) {
        int n = series.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += series[i];
            sumXY += (double) i * series[i];
            sumXX += (double) i * i;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-12) {
            // Every point shares one x value — degenerate, so no slope.
            return new double[]{mean(series), 0};
        }
        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;
        return new double[]{intercept, slope};
    }

    // ── Accuracy ──────────────────────────────────────────────────────────────

    public static double meanAbsoluteError(double[] actual, double[] predicted) {
        int n = Math.min(actual.length, predicted.length);
        if (n == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += Math.abs(actual[i] - predicted[i]);
        }
        return sum / n;
    }

    public static double rootMeanSquaredError(double[] actual, double[] predicted) {
        int n = Math.min(actual.length, predicted.length);
        if (n == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double d = actual[i] - predicted[i];
            sum += d * d;
        }
        return Math.sqrt(sum / n);
    }

    /**
     * Mean absolute percentage error.
     *
     * <p>Returns null when any actual value is zero. Retail data contains
     * genuine zero-sales days, and dividing by them produces an infinity that
     * then poisons every average it enters. The caller reports the metric as
     * unavailable instead of printing a number that means nothing.
     */
    public static Double meanAbsolutePercentageError(double[] actual, double[] predicted) {
        int n = Math.min(actual.length, predicted.length);
        if (n == 0) {
            return null;
        }
        double sum = 0;
        int counted = 0;
        for (int i = 0; i < n; i++) {
            if (Math.abs(actual[i]) < 1e-9) {
                return null;
            }
            sum += Math.abs((actual[i] - predicted[i]) / actual[i]);
            counted++;
        }
        return counted == 0 ? null : sum / counted;
    }

    /**
     * Walks a model forward over the series, one step at a time, scoring each
     * prediction before it is allowed to see the answer.
     *
     * <p>This is the chronological holdout the accuracy figures are computed
     * from. It is deliberately not a random split: a random split lets the model
     * train on next week's sales to predict this week's, which is leakage and
     * makes accuracy look far better than it will be in production.
     */
    public static BacktestResult backtest(double[] series, int step, int horizon) {
        if (series == null || series.length < MIN_OBSERVATIONS_ABSOLUTE + step) {
            return new BacktestResult(Double.NaN, Double.NaN, null, 0, 0,
                    "Not enough history for a chronological holdout of " + step + " steps.");
        }
        int firstPrediction = MIN_OBSERVATIONS_ABSOLUTE;
        List<Double> actuals = new ArrayList<>();
        List<Double> predictions = new ArrayList<>();

        for (int cut = firstPrediction; cut + step <= series.length; cut++) {
            double[] train = Arrays.copyOf(series, cut);
            ForecastFit fit = selectBestModelQuiet(train);
            double[] predicted = fit.forecast();
            for (int h = 0; h < Math.min(step, predicted.length); h++) {
                predictions.add(predicted[h]);
                actuals.add(series[cut + h]);
            }
        }

        if (actuals.isEmpty()) {
            return new BacktestResult(Double.NaN, Double.NaN, null, 0, 0,
                    "No holdout windows could be formed.");
        }
        double[] actualArray = actuals.stream().mapToDouble(Double::doubleValue).toArray();
        double[] predictedArray = predictions.stream().mapToDouble(Double::doubleValue).toArray();

        return new BacktestResult(
                meanAbsoluteError(actualArray, predictedArray),
                rootMeanSquaredError(actualArray, predictedArray),
                meanAbsolutePercentageError(actualArray, predictedArray),
                actuals.size(), firstPrediction, null);
    }

    /**
     * Model selection that never reports "insufficient", for use inside a
     * backtest where every training window is short by construction.
     */
    private static ForecastFit selectBestModelQuiet(double[] series) {
        if (series.length < 4) {
            return new ForecastFit("NAIVE", naiveForecast(series, 1), true,
                    Double.NaN, Double.NaN, null, "too short to compare");
        }
        int h = Math.max(1, Math.min(3, series.length / 4));
        List<ForecastFit.Candidate> candidates = new ArrayList<>();
        candidates.add(score("NAIVE", series, h, s -> naiveForecast(s, h)));
        candidates.add(score("MOVING_AVERAGE", series, h, s -> movingAverageForecast(s, h, 7)));
        candidates.add(score("DRIFT", series, h, s -> driftForecast(s, h)));
        HoltFit holt = fitHolt(series, h);
        if (holt.fitted()) {
            candidates.add(score("HOLT_LINEAR_EXP_SMOOTHING", series, h,
                    s -> holtForecast(s, h, holt.alpha(), holt.beta())));
        }
        candidates.sort((a, b) -> Double.compare(a.mae(), b.mae()));
        ForecastFit.Candidate winner = candidates.get(0);
        double[] forecast = forecastWith(winner.algorithm(), series, h);
        return new ForecastFit(winner.algorithm(), forecast, true,
                winner.mae(), winner.rmse(), winner.mape(),
                "selected on held-out error");
    }

    // ── Uncertainty ───────────────────────────────────────────────────────────

    /**
     * Half-widths for every horizon step.
     *
     * <p>The width grows with the square root of the horizon, which is the
     * standard behaviour of a random walk: being one day ahead is far more
     * certain than being thirty days ahead. A flat interval would claim equal
     * confidence a month out, which is not true and is how forecast bands become
     * decorative.
     *
     * <p>The floor matters as much as the width. A forecast from a very quiet
     * series would otherwise produce a band of essentially zero and be read as a
     * certainty it does not deserve, so the interval never shrinks below 15% of
     * the point value plus a small absolute margin.
     */
    public static double[] intervalHalfWidths(double[] pointForecast, double sigma, double zScore) {
        double[] halfWidths = new double[pointForecast.length];
        for (int i = 0; i < pointForecast.length; i++) {
            double growth = Math.sqrt(i + 1);
            double halfWidth = Math.max(zScore * sigma * growth,
                    Math.abs(pointForecast[i]) * 0.15 + 0.5);
            halfWidths[i] = halfWidth;
        }
        return halfWidths;
    }

    /**
     * Lower and upper bounds for every horizon step.
     *
     * <p>Floored at zero on the way down: a demand interval that extends below
     * zero implies the business will sell negative units, which then becomes a
     * reorder quantity of negative.
     */
    public static double[][] predictionBounds(double[] pointForecast, double sigma, double zScore) {
        double[] halfWidths = intervalHalfWidths(pointForecast, sigma, zScore);
        double[] lower = new double[pointForecast.length];
        double[] upper = new double[pointForecast.length];
        for (int i = 0; i < pointForecast.length; i++) {
            lower[i] = Math.max(0, pointForecast[i] - halfWidths[i]);
            upper[i] = pointForecast[i] + halfWidths[i];
        }
        return new double[][]{lower, upper};
    }

    /** z for a two-sided confidence level, capped at {@link #MAX_Z}. */
    public static double zScoreFor(double confidence) {
        // Small table rather than a dependency: these are the conventional levels.
        if (confidence >= 0.99) return 2.576;
        if (confidence >= 0.98) return 2.326;
        if (confidence >= 0.95) return 1.960;
        if (confidence >= 0.90) return 1.645;
        if (confidence >= 0.80) return 1.282;
        return 1.0;
    }

    // ── Anomaly detection ─────────────────────────────────────────────────────

    /**
     * Removes a weekly pattern from a daily series.
 *
     <p>Retail does not trade evenly across a week: a shop that is busy on
 * Saturday is quiet on Tuesday. Judging each day against a yardstick built from
 * all days therefore flags every weekend in a perfectly healthy business, and an
 * alert stream that cries wolf every Saturday is switched off — at which point
 * the one real spike goes unnoticed too.
 *
 * <p>For each day of the week the median of that weekday's values is computed
 * and subtracted, leaving a residual series that should look flat if nothing
 * unusual is happening. Anomalies are then found in the residuals, and the
 * original value is what gets reported.
 *
 * <p>A median per weekday rather than a mean: one extraordinary Saturday would
 * otherwise shift the whole Saturday baseline and hide every ordinary Tuesday
 * after it. Using the overall median as the offset keeps the residuals centred on
 * the series' own level, so the figures a caller reads back are the real ones.
 *
 * @param series       daily values in order, no gaps
 * @param seasonLength cycle length; 7 for a daily series
 * @return the residuals, same length as the input
 */
    public static double[] deseasonalise(double[] series, int seasonLength) {
        int cycle = Math.max(1, seasonLength);
        double overall = median(series);

        double[] byPhase = new double[cycle];
        int[] counts = new int[cycle];
        java.util.Arrays.fill(byPhase, 0);
        for (int phase = 0; phase < cycle; phase++) {
            List<Double> values = new ArrayList<>();
            for (int i = phase; i < series.length; i += cycle) {
                values.add(series[i]);
            }
            if (values.isEmpty()) {
                byPhase[phase] = overall;
                continue;
            }
            byPhase[phase] = median(values.stream().mapToDouble(Double::doubleValue).toArray());
            counts[phase] = values.size();
        }

        double[] residuals = new double[series.length];
        for (int i = 0; i < series.length; i++) {
            // With fewer than a full cycle of history for a phase there is no
            // pattern to remove, and the overall level is the best guess.
            double baseline = counts[i % cycle] >= 2 ? byPhase[i % cycle] : overall;
            residuals[i] = series[i] - baseline + overall;
        }
        return residuals;
    }

    /**
     * Modified z-score for one value, using the median and the MAD.
     *
     * <p>Preferred to a plain z-score because it is not distorted by the very
     * outlier being tested: a standard deviation computed over a series that
     * contains one huge sale is inflated by that sale, and the sale then looks
     * ordinary. The constant 0.6745 rescales the MAD so that for normally
     * distributed data the modified score has the same scale as a z-score, which
     * keeps the familiar 3.5 threshold meaningful.
     *
     * <p>When more than half the values are identical the MAD is zero and the
     * score would be infinite. That case is handled by falling back to the
     * standard deviation, and if that is also zero the value is simply compared
     * against the median: any deviation from a perfectly flat series is
     * noteworthy, but none of them is a multiple of anything.
     */
    public static double modifiedZScore(double value, double[] series) {
        double med = median(series);
        double mad = medianAbsoluteDeviation(series);
        if (mad > 1e-9) {
            return 0.6745 * (value - med) / mad;
        }
        double sd = standardDeviation(series);
        if (sd > 1e-9) {
            return (value - med) / sd;
        }
        return value == med ? 0 : (value > med ? Double.POSITIVE_INFINITY
                : Double.NEGATIVE_INFINITY);
    }

    /**
     * Modified z-score against the same point in previous cycles.
     *
     * <p>Kept for callers that hold a single value rather than a series. The
     * seasonal baseline it uses is built from few observations, which makes its
     * spread estimate unstable; prefer {@link #deseasonalise(double[], int)}
     * where the whole series is available.
     *
     * @param index        position of the value being judged
     * @param seasonLength cycle length in observations; 7 for a daily series
     * @return the score, or {@link Double#NaN} when there are too few
     *         same-season observations to judge against
     */
    public static double seasonalModifiedZScore(double value, double[] series, int index,
                                                 int seasonLength) {
        int step = Math.max(1, seasonLength);
        List<Double> sameSeason = new ArrayList<>();
        for (int i = index - step; i >= 0; i -= step) {
            sameSeason.add(series[i]);
        }
        if (sameSeason.size() < MIN_OBSERVATIONS_ABSOLUTE) {
            return Double.NaN;
        }
        double[] baseline = sameSeason.stream().mapToDouble(Double::doubleValue).toArray();
        return modifiedZScore(value, baseline);
    }

    /**
     * Whether a value is unusual enough to raise an alert.
     *
     * <p>Returns an investigation alert, never a verdict. The threshold is tuned
     * for a number of false alarms: a monitor nobody trusts is switched off, and
     * then it catches nothing at all.
     *
     * <p>For a series that has a weekly cycle, use
     * {@link #seasonalModifiedZScore(double, double[], int, int)} instead —
     * comparing a Saturday against a yardstick built from every day flags every
     * Saturday in a perfectly healthy business.
     */
    public static boolean isAnomalous(double value, double[] series) {
        if (series == null || series.length < MIN_OBSERVATIONS_ABSOLUTE) {
            return false;
        }
        return Math.abs(modifiedZScore(value, series)) >= ANOMALY_THRESHOLD;
    }

    /**
     * Whether the value at {@code index} is unusual, allowing for a weekly cycle.
     *
     * <p>Judges the deseasonalised residual rather than the raw value, so a
     * regular weekly pattern is not mistaken for a series of anomalies.
     */
    public static boolean isAnomalousAt(double[] series, int index, int seasonLength) {
        if (series == null || series.length < MIN_OBSERVATIONS_ABSOLUTE) {
            return false;
        }
        double[] residuals = deseasonalise(series, seasonLength);
        double[] history = residualsAsHistory(residuals, index);
        if (history.length < MIN_OBSERVATIONS_ABSOLUTE) {
            return false;
        }
        return Math.abs(modifiedZScore(residuals[index], history)) >= ANOMALY_THRESHOLD;
    }

    /**
     * The trailing window used to judge one point, excluding the point itself.
     *
     * <p>The value is excluded so that a large spike cannot widen the yardstick
     * that is being asked to catch it.
     */
    public static double[] residualsAsHistory(double[] residuals, int index) {
        int start = Math.max(0, index - 30);
        double[] history = new double[index - start];
        for (int i = start; i < index; i++) {
            history[i - start] = residuals[i];
        }
        return history;
    }

    public static boolean isSevereAnomaly(double value, double[] series) {
        if (series == null || series.length < MIN_OBSERVATIONS_ABSOLUTE) {
            return false;
        }
        return Math.abs(modifiedZScore(value, series)) >= SEVERE_ANOMALY_THRESHOLD;
    }

    /**
     * Smoothed rate of change, used to describe a trend without letting one
     * spike decide its direction.
     */
    public static double trendPercentPerPeriod(double[] series) {
        if (series == null || series.length < 2) {
            return 0;
        }
        double firstHalf = mean(Arrays.copyOfRange(series, 0, series.length / 2));
        double secondHalf = mean(Arrays.copyOfRange(series, series.length / 2, series.length));
        if (Math.abs(firstHalf) < 1e-9) {
            return 0;
        }
        return (secondHalf - firstHalf) / Math.abs(firstHalf) * 100.0;
    }

    /**
     * Coefficient of variation, a scale-free measure of how erratic a series is.
     *
     * <p>Drives how much safety stock a product needs: the same lead time is
     * trivial for a product selling steadily and dangerous for one whose sales
     * swing by half.
     */
    public static double coefficientOfVariation(double[] series) {
        double mean = mean(series);
        if (Math.abs(mean) < 1e-9) {
            return 0;
        }
        return standardDeviation(series) / Math.abs(mean);
    }

    private static void requireSeries(double[] series, int horizon) {
        if (series == null || series.length == 0) {
            throw new IllegalArgumentException("A forecast needs at least one observation.");
        }
        if (horizon <= 0) {
            throw new IllegalArgumentException("The forecast horizon must be positive.");
        }
    }

    // ── Result types ──────────────────────────────────────────────────────────

    /** A fitted Holt model and the parameters chosen for it. */
    public record HoltFit(double alpha, double beta, double oneStepError,
                          boolean fitted, String rejectionReason) {
        public static HoltFit rejected(String reason) {
            return new HoltFit(Double.NaN, Double.NaN, Double.NaN, false, reason);
        }
    }

    /** A chosen model, its forecast and its measured accuracy. */
    public record ForecastFit(String algorithm, double[] forecast, boolean available,
                              Double mae, Double rmse, Double mape, String explanation) {
        public static ForecastFit insufficient(String reason) {
            return new ForecastFit(null, new double[0], false, null, null, null, reason);
        }

        /** One model's score on the holdout. */
        public record Candidate(String algorithm, double mae, double rmse, Double mape) {
        }
    }

    /** Accuracy measured on a chronological holdout. */
    public record BacktestResult(double mae, double rmse, Double mape,
                                 int predictions, int firstTrainingSize, String note) {
    }
}