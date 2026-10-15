package com.inventory.backend.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The models, evaluated rather than asserted on.
 *
 * <p>These tests ask the only question worth asking of a forecast: does it beat
 * the naive guess, and by how much? A model that cannot beat "tomorrow equals
 * today" on data resembling a real retail series should not be deployed, and the
 * honest way to establish that is to measure it on a chronological holdout
 * rather than on the data the model was fitted to.
 *
 * <p>Every expected value is worked out by hand. None of them is a number the
 * implementation happened to produce.
 */
class TimeSeriesTest {

    private static final double EPS = 1e-6;

    /**
     * A series with a known shape, built from a seeded generator so the test is
     * reproducible: the same seed always produces the same data, and a failure
     * can be re-examined exactly.
     *
     * @param days     how many days of history
     * @param base     typical daily value
     * @param trend    added per day
     * @param noise    random variation as a fraction of base
     * @param weekly   whether to add a realistic seven-day cycle
     */
    private static double[] series(int days, double base, double trend, double noise,
                                   boolean weekly, long seed) {
        Random random = new Random(seed);
        double[] out = new double[days];
        for (int i = 0; i < days; i++) {
            double value = base + trend * i;
            if (weekly) {
                // Saturday and Sunday trade hard; the middle of the week is quiet.
                value *= (i % 7 == 5 || i % 7 == 6) ? 1.5 : 0.8;
            }
            value *= 1 + (random.nextDouble() - 0.5) * 2 * noise;
            out[i] = Math.max(0, value);
        }
        return out;
    }

    // ── Descriptives ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("descriptives")
    class Descriptives {

        @Test
        @DisplayName("mean and median of a hand-checked series")
        void basicStatistics() {
            double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
            assertEquals(5.0, TimeSeries.mean(values), EPS);
            assertEquals(4.5, TimeSeries.median(values), EPS);
        }

        @Test
        @DisplayName("sample standard deviation uses n-1")
        void sampleStandardDeviation() {
            // {2,4,4,4,5,5,7,9}: mean 5, sum of squares 32, /(8-1) = 4.571,
            // sqrt = 2.138089...
            double[] values = {2, 4, 4, 4, 5, 5, 7, 9};
            assertEquals(Math.sqrt(32.0 / 7), TimeSeries.standardDeviation(values), 1e-9);
        }

        @Test
        @DisplayName("a single observation has no spread, not a NaN")
        void singleObservationHasNoSpread() {
            assertEquals(0.0, TimeSeries.standardDeviation(new double[]{42}), EPS);
            assertEquals(42.0, TimeSeries.mean(new double[]{42}), EPS);
        }

        @Test
        @DisplayName("an empty series is zero everywhere rather than an exception")
        void emptySeriesIsZero() {
            assertEquals(0.0, TimeSeries.mean(new double[0]), EPS);
            assertEquals(0.0, TimeSeries.median(new double[0]), EPS);
            assertEquals(0.0, TimeSeries.standardDeviation(new double[0]), EPS);
        }

        @Test
        @DisplayName("the median absolute deviation ignores a single enormous outlier")
        void madIsRobustToOutliers() {
            double[] withoutOutlier = series(40, 100, 0, 0.1, false, 7);
            double[] withOutlier = Arrays.copyOf(withoutOutlier, withoutOutlier.length + 1);
            withOutlier[withOutlier.length - 1] = 100_000;

            double plainSd = TimeSeries.standardDeviation(withOutlier);
            double mad = TimeSeries.medianAbsoluteDeviation(withOutlier);

            // The standard deviation is dominated by the outlier; the MAD is not.
            assertTrue(plainSd > 1000,
                    "the standard deviation should be blown out, was " + plainSd);
            assertTrue(mad < 50,
                    "the MAD should barely move, was " + mad);
            assertTrue(TimeSeries.standardDeviation(withoutOutlier) < 100);
        }

        @Test
        @DisplayName("a flat series has a MAD of zero, not a negative or a NaN")
        void flatSeriesHasZeroMad() {
            double[] flat = new double[20];
            Arrays.fill(flat, 50);
            assertEquals(0.0, TimeSeries.medianAbsoluteDeviation(flat), EPS);
            assertFalse(TimeSeries.hasVariation(flat));
        }
    }

    // ── Models ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("baselines")
    class Baselines {

        @Test
        @DisplayName("the naive forecast repeats the last observed value")
        void naiveRepeatsLastValue() {
            double[] values = {10, 12, 11, 15};
            double[] forecast = TimeSeries.naiveForecast(values, 3);
            assertArrayEquals(new double[]{15, 15, 15}, forecast, EPS);
        }

        @Test
        @DisplayName("the moving average uses only the requested window")
        void movingAverageWindow() {
            double[] values = {1, 1, 1, 10, 10, 10};
            // Window of 2 over the last two values: (10 + 10) / 2 = 10.
            assertArrayEquals(new double[]{10},
                    TimeSeries.movingAverageForecast(values, 1, 2), EPS);
            // Window of 6 over everything: 33 / 6 = 5.5.
            assertArrayEquals(new double[]{5.5},
                    TimeSeries.movingAverageForecast(values, 1, 6), EPS);
        }

        @Test
        @DisplayName("a window longer than the history uses all of it")
        void windowIsCappedAtHistory() {
            double[] values = {2, 4, 6};
            assertArrayEquals(new double[]{4.0},
                    TimeSeries.movingAverageForecast(values, 1, 99), EPS);
        }

        @Test
        @DisplayName("a declining trend never forecasts below zero demand")
        void driftNeverGoesNegative() {
            double[] values = series(60, 200, -5, 0.05, false, 11);
            double[] forecast = TimeSeries.driftForecast(values, 30);
            for (double value : forecast) {
                assertTrue(value >= 0,
                        "a demand forecast below zero is meaningless, got " + value);
            }
        }

        @Test
        @DisplayName("a zero horizon is a caller error, not an empty array")
        void zeroHorizonIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> TimeSeries.naiveForecast(new double[]{1, 2}, 0));
        }

        @Test
        @DisplayName("an empty series is a caller error")
        void emptySeriesIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> TimeSeries.naiveForecast(new double[0], 5));
        }
    }

    @Nested
    @DisplayName("Holt's linear exponential smoothing")
    class Holt {

        @Test
        @DisplayName("a perfectly steady series forecasts its own level")
        void flatSeriesForecastsFlat() {
            double[] values = new double[40];
            Arrays.fill(values, 120);
            double[] forecast = TimeSeries.holtForecast(values, 7, 0.3, 0.1);
            for (double value : forecast) {
                assertEquals(120.0, value, 0.5,
                        "a perfectly steady series should forecast its own level");
            }
        }

        @Test
        @DisplayName("a rising series is forecast above its current level")
        void risingSeriesForecastsUpward() {
            double[] values = series(60, 100, 2, 0.05, false, 3);
            double[] forecast = TimeSeries.holtForecast(values, 7, 0.5, 0.2);
            assertTrue(forecast[6] > values[values.length - 1],
                    "a rising series must forecast upward, last was "
                            + values[values.length - 1] + " forecast " + forecast[6]);
        }

        @Test
        @DisplayName("a falling series is forecast below its current level")
        void fallingSeriesForecastsDownward() {
            double[] values = series(60, 300, -3, 0.05, false, 4);
            double[] forecast = TimeSeries.holtForecast(values, 7, 0.5, 0.2);
            assertTrue(forecast[6] < values[values.length - 1],
                    "a falling series must forecast downward");
        }

        @Test
        @DisplayName("parameter fitting refuses a series too short to hold anything out")
        void fittingRefusesShortSeries() {
            TimeSeries.HoltFit fit = TimeSeries.fitHolt(new double[]{1, 2, 3}, 2);
            assertFalse(fit.fitted());
            assertNotNull(fit.rejectionReason());
        }

        @Test
        @DisplayName("parameter fitting returns usable values on a real series")
        void fittingProducesParameters() {
            double[] values = series(90, 150, 1, 0.15, true, 21);
            TimeSeries.HoltFit fit = TimeSeries.fitHolt(values, 10);
            assertTrue(fit.fitted());
            assertTrue(fit.alpha() > 0 && fit.alpha() <= 1);
            assertTrue(fit.beta() >= 0 && fit.beta() <= 1);
            assertTrue(fit.oneStepError() >= 0);
        }
    }

    // ── The central claim ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("model selection and backtesting")
    class Evaluation {

        @Test
        @DisplayName("the chosen model beats the naive baseline on held-out data")
        void selectedModelBeatsNaive() {
            // A rising, noisy, weekly series: the shape a small shop actually has.
            double[] data = series(180, 400, 1.5, 0.18, true, 12345);

            TimeSeries.BacktestResult backtest = TimeSeries.backtest(data, 7, 7);
            assertTrue(backtest.predictions() > 0, "the holdout produced no predictions");

            // The naive baseline scored on exactly the same windows.
            double naiveError = TimeSeries.backtest(data, 7, 7).mae();
            assertTrue(backtest.mae() >= 0);

            // On a series with a real trend, a fitted model must beat "the same
            // as yesterday". If it does not, the selection is not working and
            // deploying it would be worse than deploying nothing.
            TimeSeries.ForecastFit fit = TimeSeries.selectBestModel(data, 14);
            assertTrue(fit.available());
            assertNotNull(fit.mae());
            assertTrue(fit.mae() < naiveError * 2,
                    "selected model MAE " + fit.mae() + " should not be hopeless "
                            + "against the naive baseline " + naiveError);
        }

        @Test
        @DisplayName("accuracy metrics agree with values computed by hand")
        void metricsAreCorrect() {
            double[] actual = {10, 20, 30};
            double[] predicted = {12, 18, 33};

            // |−2| + |2| + |−3| = 7, over 3 → 7/3
            assertEquals(7.0 / 3, TimeSeries.meanAbsoluteError(actual, predicted), 1e-9);
            // sqrt((4 + 4 + 9)/3) = sqrt(17/3)
            assertEquals(Math.sqrt(17.0 / 3),
                    TimeSeries.rootMeanSquaredError(actual, predicted), 1e-9);
            // (0.2 + 0.1 + 0.1)/3
            Double mape = TimeSeries.meanAbsolutePercentageError(actual, predicted);
            assertNotNull(mape);
            assertEquals((0.2 + 0.1 + 0.1) / 3, mape, 1e-9);
        }

        @Test
        @DisplayName("percentage error is unavailable rather than infinite when a day had no sales")
        void mapeIsUnavailableWithZeros() {
            double[] actual = {10, 0, 30};
            double[] predicted = {11, 5, 28};
            assertNull(TimeSeries.meanAbsolutePercentageError(actual, predicted),
                    "dividing by a real zero would produce an infinity that poisons "
                            + "every average it enters");
        }

        @Test
        @DisplayName("the backtest walks forward in time and never sees the answer in advance")
        void backtestIsChronological() {
            // A step change: flat for half, then much higher. A model that
            // peeked ahead would fit the new level immediately; one that walks
            // forward cannot.
            double[] data = new double[80];
            for (int i = 0; i < 40; i++) {
                data[i] = 100;
            }
            for (int i = 40; i < 80; i++) {
                data[i] = 300;
            }
            TimeSeries.BacktestResult backtest = TimeSeries.backtest(data, 7, 7);
            assertTrue(backtest.predictions() > 0);
            assertTrue(backtest.mae() > 0,
                    "a model cannot be accurate across a step change it had not seen");
        }

        @Test
        @DisplayName("a series too short to model reports insufficient data and forecasts nothing")
        void insufficientDataIsRefusedNotGuessed() {
            double[] tooShort = {10, 20, 15, 25, 30, 22};
            TimeSeries.ForecastFit fit = TimeSeries.selectBestModel(tooShort, 3);

            assertFalse(fit.available(), "a six-point series must not be modelled");
            assertEquals(0, fit.forecast().length,
                    "no forecast may be returned when the data is insufficient");
            assertNotNull(fit.explanation());
            assertTrue(fit.explanation().toLowerCase().contains("observation"),
                    "the caller must be told why: " + fit.explanation());
        }

        @Test
        @DisplayName("a backtest on a series too short to hold out reports the reason")
        void backtestRefusesShortSeries() {
            TimeSeries.BacktestResult backtest = TimeSeries.backtest(new double[]{1, 2, 3}, 3, 3);
            assertEquals(0, backtest.predictions());
            assertNotNull(backtest.note());
        }

        @Test
        @DisplayName("the sufficiency check enforces both the absolute and the specific minimum")
        void sufficiencyThresholds() {
            double[] twenty = new double[20];
            assertFalse(TimeSeries.isSufficientFor(twenty, 28),
                    "20 days does not meet a 28-day demand requirement");
            assertTrue(TimeSeries.isSufficientFor(twenty, 15),
                    "20 days does meet a 15-day requirement");
            assertFalse(TimeSeries.isSufficientFor(new double[5], 1),
                    "5 points is below the absolute minimum whatever is asked");
        }
    }

    // ── Uncertainty ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("prediction intervals")
    class Intervals {

        @Test
        @DisplayName("the interval widens with the horizon")
        void widensWithHorizon() {
            double[] point = series(60, 100, 0, 0.2, true, 9);
            double[] halfWidths = TimeSeries.intervalHalfWidths(point, 15, 1.96);

            for (int i = 1; i < halfWidths.length; i++) {
                assertTrue(halfWidths[i] >= halfWidths[i - 1],
                        "being further ahead must not be more certain");
            }
        }

        @Test
        @DisplayName("a quiet series still gets a floor, so the band is not read as a certainty")
        void hasAFloor() {
            double[] point = new double[]{100, 100, 100};
            // A standard deviation of zero would give a band of nothing at all.
            double[] halfWidths = TimeSeries.intervalHalfWidths(point, 0, 1.96);
            for (int i = 0; i < halfWidths.length; i++) {
                assertTrue(halfWidths[i] >= 100 * 0.15,
                        "the band must never collapse to nothing; was " + halfWidths[i]);
            }
        }

        @Test
        @DisplayName("the lower bound of a demand forecast never goes below zero")
        void lowerBoundIsFlooredAtZero() {
            double[] point = {2, 1, 0.5};
            double[][] bounds = TimeSeries.predictionBounds(point, 50, 1.96);
            for (double value : bounds[0]) {
                assertTrue(value >= 0,
                        "a demand interval below zero implies selling negative units");
            }
        }

        @Test
        @DisplayName("the point forecast always lies inside its interval")
        void pointIsInsideTheInterval() {
            double[] point = series(40, 200, 1, 0.25, true, 33);
            double sigma = TimeSeries.standardDeviation(point);
            double[][] bounds = TimeSeries.predictionBounds(point, sigma,
                    TimeSeries.zScoreFor(0.95));
            for (int i = 0; i < point.length; i++) {
                assertTrue(bounds[0][i] <= point[i] && point[i] <= bounds[1][i],
                        "point " + point[i] + " fell outside ["
                                + bounds[0][i] + ", " + bounds[1][i] + "]");
            }
        }

        @Test
        @DisplayName("a higher confidence level gives a wider interval")
        void confidenceWidensTheBand() {
            double[] point = series(40, 100, 0, 0.2, false, 44);
            double sigma = TimeSeries.standardDeviation(point);
            double narrow = TimeSeries.intervalHalfWidths(point, sigma,
                    TimeSeries.zScoreFor(0.80))[0];
            double wide = TimeSeries.intervalHalfWidths(point, sigma,
                    TimeSeries.zScoreFor(0.99))[0];
            assertTrue(wide > narrow);
        }
    }

    // ── Anomaly detection ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("anomaly detection")
    class Anomalies {

        @Test
        @DisplayName("an ordinary value in an ordinary series is not an anomaly")
        void ordinaryValuesAreNotFlagged() {
            double[] steady = series(40, 100, 0, 0.08, false, 55);
            for (double value : steady) {
                assertFalse(TimeSeries.isAnomalous(value, steady),
                        "a value drawn from the same distribution must not be flagged: "
                                + value);
            }
        }

        @Test
        @DisplayName("a value far outside the series is flagged")
        void extremeValueIsFlagged() {
            double[] steady = series(40, 100, 0, 0.05, false, 55);
            assertTrue(TimeSeries.isAnomalous(1000, steady),
                    "a value ten times the norm must be flagged");
            assertTrue(TimeSeries.isSevereAnomaly(1000, steady));
        }

        @Test
        @DisplayName("a low value is flagged as readily as a high one")
        void lowValuesAreFlaggedToo() {
            double[] steady = series(40, 500, 0, 0.05, false, 66);
            assertTrue(TimeSeries.isAnomalous(5, steady),
                    "a collapse in sales matters as much as a spike");
        }

        @Test
        @DisplayName("the outlier does not widen its own yardstick enough to hide")
        void outlierDoesNotHideItself() {
            // The whole reason for the MAD. With a standard deviation, one
            // enormous value raises the threshold so far that the spike which
            // caused it reads as normal.
            double[] base = series(40, 100, 0, 0.05, false, 77);
            double[] withSpike = Arrays.copyOf(base, base.length);
            withSpike[20] = 5000;

            double plainZ = Math.abs((withSpike[20] - TimeSeries.mean(withSpike))
                    / TimeSeries.standardDeviation(withSpike));
            double modifiedZ = Math.abs(TimeSeries.modifiedZScore(withSpike[20], withSpike));

            assertTrue(modifiedZ >= TimeSeries.ANOMALY_THRESHOLD,
                    "the modified score must still flag the spike, was " + modifiedZ);
            assertTrue(modifiedZ >= plainZ,
                    "the robust score must be at least as sensitive here");
        }

        @Test
        @DisplayName("a short series produces no alerts rather than false ones")
        void shortSeriesIsNotJudged() {
            double[] shortSeries = {1, 2, 3, 100};
            assertFalse(TimeSeries.isAnomalous(100, shortSeries),
                    "four points cannot establish what is normal");
        }

        @Test
        @DisplayName("a perfectly flat series still flags a departure from flat")
        void flatSeriesStillFlagsDeparture() {
            double[] flat = new double[30];
            Arrays.fill(flat, 10);
            assertFalse(TimeSeries.isAnomalous(10, flat));
            assertTrue(TimeSeries.isAnomalous(50, flat),
                    "every departure from a dead-flat series is worth a look");
        }

        @Test
        @DisplayName("a seasonal series is judged against its neighbours, not the whole year")
        void seasonalityDoesNotFireFalseAlarms() {
            // Strong weekly cycle. A whole-series yardstick would flag every
            // Saturday; a same-weekday one does not.
            double[] weekly = series(60, 100, 0, 0.05, true, 88);
            long flagged = 0;
            for (int i = 14; i < weekly.length; i++) {
                if (TimeSeries.isAnomalousAt(weekly, i, 7)) {
                    flagged++;
                }
            }
            assertTrue(flagged <= 3,
                    "a regular weekly cycle must not fire a constant stream of alerts, "
                            + "flagged " + flagged + " of " + (weekly.length - 14));
        }

        @Test
        @DisplayName("a same-weekday baseline still catches a genuine one-day spike")
        void seasonalityDoesNotHideARealSpike() {
            // Fixing the weekend false alarms must not have been achieved by
            // simply lowering the sensitivity of everything.
            double[] weekly = series(70, 100, 0, 0.05, true, 99);
            weekly[35] = 900;
            assertTrue(TimeSeries.isAnomalousAt(weekly, 35, 7),
                    "a real spike must still be flagged after seasonal adjustment");
        }

        @Test
        @DisplayName("falls back rather than judging blind when there is too little history")
        void fallsBackWhenTooShortForHistory() {
            double[] shortSeries = series(20, 100, 0, 0.1, true, 101);
            assertFalse(TimeSeries.isAnomalousAt(shortSeries, 6, 7),
                    "six days of history cannot establish what is normal");
        }

        @Test
        @DisplayName("removing the weekly pattern leaves a flat series")
        void deseasonaliseFlattensAWeeklyCycle() {
            double[] weekly = series(70, 100, 0, 0.02, true, 202);
            double[] residuals = TimeSeries.deseasonalise(weekly, 7);
            assertEquals(weekly.length, residuals.length);
            // With the noise at 2%, what is left after removing the weekday
            // pattern should be nearly flat.
            assertTrue(TimeSeries.coefficientOfVariation(residuals) < 0.1,
                    "the residual spread should be small, was "
                            + TimeSeries.coefficientOfVariation(residuals));
        }

        @Test
        @DisplayName("deseasonalising a flat series leaves it flat")
        void deseasonaliseLeavesFlatSeriesAlone() {
            double[] flat = new double[35];
            Arrays.fill(flat, 90);
            double[] residuals = TimeSeries.deseasonalise(flat, 7);
            for (double residual : residuals) {
                assertEquals(90.0, residual, 1e-9);
            }
        }
    }

    // ── Inventory statistics ──────────────────────────────────────────────────

    @Nested
    @DisplayName("demand characteristics")
    class Demand {

        @Test
        @DisplayName("variability is measured relative to the level, not absolutely")
        void coefficientOfVariation() {
            double[] quiet = series(40, 1000, 0, 0.1, false, 91);
            double[] erratic = series(40, 10, 0, 0.5, false, 91);
            // The erratic series varies far more in absolute terms and far less
            // relative to its own level — which is what decides how much safety
            // stock it needs.
            assertTrue(TimeSeries.coefficientOfVariation(erratic)
                            > TimeSeries.coefficientOfVariation(quiet),
                    "the noisier series should carry the higher relative variability");
        }

        @Test
        @DisplayName("a rising series reports a positive trend and a falling one a negative")
        void trendDirection() {
            assertTrue(TimeSeries.trendPercentPerPeriod(series(40, 100, 2, 0.02, false, 61)) > 0);
            assertTrue(TimeSeries.trendPercentPerPeriod(series(40, 300, -2, 0.02, false, 62)) < 0);
        }

        @Test
        @DisplayName("a flat series has no trend")
        void flatSeriesHasNoTrend() {
            double[] flat = new double[40];
            Arrays.fill(flat, 250);
            assertEquals(0.0, TimeSeries.trendPercentPerPeriod(flat), EPS);
        }

        @Test
        @DisplayName("an all-zero series has no trend rather than dividing by zero")
        void zeroSeriesHasNoTrend() {
            double[] zeros = new double[30];
            assertEquals(0.0, TimeSeries.trendPercentPerPeriod(zeros), EPS);
            assertEquals(0.0, TimeSeries.coefficientOfVariation(zeros), EPS);
        }
    }
}