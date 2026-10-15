package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A trained model, recorded so a number on the dashboard can be traced back to
 * the model that produced it.
 *
 * <p>Forecasts and reorder recommendations are only trustworthy if you can say
 * which version produced them, on which data, with what measured accuracy. A
 * system that simply answers "sales will be 120 next week" gives no way to tell
 * a model that has learned something from one that is extrapolating noise, and
 * no way to detect that accuracy has degraded as the business changed.
 *
 * <p>Every training run writes a row here and marks the previous one inactive for
 * that key. Nothing is ever overwritten, so the metrics that were true when a
 * decision was made remain readable.
 */
@Entity
@Table(name = "ai_model_versions",
        indexes = {
                @Index(name = "idx_ai_model_key_active", columnList = "model_key, active"),
                @Index(name = "idx_ai_model_trained_at", columnList = "trained_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * What the model is for, e.g. {@code demand:daily:aggregate} or
     * {@code anomaly:movements}. A key groups the versions of one model.
     */
    @Column(name = "model_key", nullable = false, length = 120)
    private String modelKey;

    /** Algorithm that won the backtest, e.g. HOLT_LINEAR_EXP_SMOOTHING. */
    @Column(name = "algorithm", nullable = false, length = 60)
    private String algorithm;

    /** Every algorithm evaluated during training, with its score, as JSON. */
    @Lob
    @Column(name = "candidate_metrics")
    private String candidateMetrics;

    /** Monotonic version per key; starts at 1. */
    @Column(name = "version", nullable = false)
    private Integer version;

    /** First day of history the model was trained on. */
    @Column(name = "training_period_start")
    private LocalDate trainingPeriodStart;

    /** Last day of history the model was trained on. */
    @Column(name = "training_period_end")
    private LocalDate trainingPeriodEnd;

    /** Observations available for training. Below the threshold no model is fitted. */
    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    /** Out-of-sample mean absolute error, in units of the target. */
    @Column(name = "mae", precision = 14, scale = 4)
    private BigDecimal mae;

    /** Out-of-sample root mean squared error. */
    @Column(name = "rmse", precision = 14, scale = 4)
    private BigDecimal rmse;

    /**
     * Mean absolute percentage error. Null when any actual value is zero, which
     * happens with real retail data and makes MAPE meaningless; the evaluation
     * reports that rather than dividing by zero.
     */
    @Column(name = "mape", precision = 14, scale = 4)
    private BigDecimal mape;

    /** Parameter values of the fitted model, as JSON. */
    @Lob
    @Column(name = "hyperparameters")
    private String hyperparameters;

    /** Whether this version currently answers requests for its key. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = false;

    @CreationTimestamp
    @Column(name = "trained_at")
    private LocalDateTime trainedAt;
}