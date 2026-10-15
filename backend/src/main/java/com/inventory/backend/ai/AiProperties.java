package com.inventory.backend.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration for the intelligence service.
 *
 * <p>Bound from {@code app.ai.*}, so every setting is an environment variable in
 * deployment and nothing is compiled in. The defaults are conservative: the
 * service will produce no forecast it has not first been shown it can predict,
 * and it will not raise an alert below a threshold that would generate more noise
 * than signal.
 *
 * <p>Note what is <b>not</b> here. There is no API key, no model endpoint and no
 * provider configuration, because the intelligence in this application is
 * computed from the business's own records using the models in
 * {@link TimeSeries}. Nothing about a customer's finances, a supplier's invoice
 * or a staff member's salary is ever sent anywhere, and there is therefore
 * nothing to leak and no credential to rotate. If a language model is added
 * later it will be behind this same configuration boundary, with credentials
 * supplied by environment variable and never committed.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Master switch. Off means the endpoints return "not enabled" rather than guessing. */
    private boolean enabled = true;

    /**
     * Days of history fetched before a forecast is attempted.
     *
     * <p>Wide by default because monthly and weekly horizons need long series,
     * and because the fetch is cheap compared to being wrong.
     */
    private int historyDays = 540;

    /** Days of history used for the demand model behind reorder advice. */
    private int demandHistoryDays = 365;

    /** Prediction interval width, e.g. 0.95 for 95%. */
    private double confidence = 0.95;

    /** Horizon offered by the daily view. */
    private int defaultDailyHorizon = 14;

    /** How many top products get an individual demand model. */
    private int maxDemandModels = 25;

    /** Products below this many observations are reported as unforecastable, not guessed. */
    private int minimumDemandObservations = TimeSeries.MIN_OBSERVATIONS_FOR_DEMAND;

    /** Anomaly threshold in modified z-score units. Lower detects more. */
    private double anomalyThreshold = TimeSeries.ANOMALY_THRESHOLD;

    /** Minimum history before anomaly detection will run at all. */
    private int anomalyMinimumObservations = TimeSeries.MIN_OBSERVATIONS_ABSOLUTE;

    /**
     * How far a day must differ from what that weekday normally does, as a
     * fraction, before it is worth raising at all.
     *
     * <p>Statistical significance is not the same as practical significance.
     * A business trading eight units a day and a hundred a day has a small
     * spread, so ordinary variation scores high on a robust z-score. Raising an
     * alert for a Tuesday at the top of the Tuesday range trains people to
     * dismiss alerts, which is when a real one goes unread.
     *
     * <p>Both tests must pass: statistically unusual <em>and</em> materially
     * different from the same weekday's own normal. A day has to be worth
     * someone's time, not merely improbable.
     */
    private double anomalyMinimumRelativeDeviation = 0.30;

    /** An adjustment of this size or more with no stated reason is flagged. */
    private int largeMovementThreshold = 50;

    /** Days of unchanged stock after which a product is called slow-moving. */
    private int slowMovingDays = 60;

    /** Default supplier lead time, in days, when no history exists to learn from. */
    private int defaultLeadTimeDays = 7;

    /** Service level used for the safety-stock z factor, e.g. 0.95. */
    private double serviceLevel = 0.95;

    /**
     * Whether training results are written to the model registry.
     *
     * <p>On by default so a forecast can be traced to the model and metrics that
     * produced it. A deployment that only ever reads pre-computed figures can turn
     * it off.
     */
    private boolean persistModelVersions = true;

    /**
     * Confidence below which the assistant asks a clarifying question instead of
     * answering.
     *
     * <p>A confidently misread question is worse than an honest "did you mean X".
     */
    private double assistantMinConfidence = 0.35;

    /** Retain at most this many query-log rows per user, oldest pruned. */
    private int assistantLogRetention = 500;

    /** Words that name a period in a question. */
    private List<String> periodKeywords = List.of(
            "today", "yesterday", "this week", "last week", "this month", "last month",
            "this quarter", "last quarter", "this year", "last year", "year to date");

    public boolean hasEnoughHistory(int observations) {
        return observations >= minimumDemandObservations;
    }
}