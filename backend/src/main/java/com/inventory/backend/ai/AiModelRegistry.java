package com.inventory.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.model.AiModelVersion;
import com.inventory.backend.repository.AiModelVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Keeps the history of every model that has answered a question.
 *
 * <p>A number on a dashboard is only trustworthy if you can find out what
 * produced it. This records, for each training run: which algorithm won, the
 * period it learned from, how many observations it had, and how accurate it
 * proved to be on data it had not seen. Earlier versions are deactivated, never
 * overwritten, so the metrics that were true when a decision was taken remain
 * readable afterwards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiModelRegistry {

    private final AiModelVersionRepository repository;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Records a training result and makes it the active version for its key.
     *
     * <p>Deactivating the previous version happens in the same transaction as
     * activating the new one. Two active versions would make "which model
     * produced this" ambiguous, which defeats the point of recording it at all.
     *
     * <p>{@code REQUIRES_NEW}, not the default {@code REQUIRED}, and the reason
     * is specific. Forecasting runs inside a {@code readOnly = true} transaction;
     * joining it would make this INSERT illegal ("cannot execute INSERT in a
     * read-only transaction"), and worse, Postgres marks the whole transaction
     * aborted — so every later statement in the caller's request fails with
     * "current transaction is aborted" and a dashboard that only wanted to read
     * figures returns 500. A separate transaction makes this write roll back on
     * its own, which is what the catch block below has always assumed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiModelVersion register(String modelKey, String algorithm,
                                    LocalDate periodStart, LocalDate periodEnd,
                                    int sampleSize, TimeSeries.ForecastFit fit,
                                    Map<String, Object> hyperparameters) {
        if (!properties.isPersistModelVersions()) {
            return null;
        }
        try {
            repository.findByModelKeyAndActiveTrueOrderByVersionDesc(modelKey)
                    .ifPresent(previous -> {
                        previous.setActive(false);
                        repository.save(previous);
                    });

            AiModelVersion version = AiModelVersion.builder()
                    .modelKey(modelKey)
                    .algorithm(algorithm == null ? "NONE" : algorithm)
                    .version(repository.maxVersionForKey(modelKey) + 1)
                    .trainingPeriodStart(periodStart)
                    .trainingPeriodEnd(periodEnd)
                    .sampleSize(sampleSize)
                    .mae(fit != null && fit.mae() != null ? decimal(fit.mae()) : null)
                    .rmse(fit != null && fit.rmse() != null ? decimal(fit.rmse()) : null)
                    .mape(fit != null && fit.mape() != null
                            ? decimal(fit.mape() * 100) : null)
                    .hyperparameters(hyperparameters == null ? null : objectMapper.writeValueAsString(hyperparameters))
                    .candidateMetrics(fit != null ? objectMapper.writeValueAsString(
                            Map.of("explanation", fit.explanation() == null
                                    ? "not recorded" : fit.explanation())) : null)
                    .active(true)
                    .build();
            AiModelVersion saved = repository.save(version);
            log.info("Registered model {} v{} ({}), {} observations, MAE {}",
                    modelKey, saved.getVersion(), saved.getAlgorithm(),
                    saved.getSampleSize(), saved.getMae());
            return saved;
        } catch (Exception e) {
            // A failure to record provenance must never stop a forecast being
            // shown. Losing the audit trail is bad; losing the answer is worse.
            log.warn("Could not record model version for {}: {}", modelKey, e.getMessage());
            return null;
        }
    }

    /** The version currently answering for a model key, if one has been trained. */
    @Transactional(readOnly = true)
    public AiModelVersion activeVersion(String modelKey) {
        return repository.findFirstByModelKeyAndActiveTrueOrderByVersionDesc(modelKey).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AiModelVersion> history(String modelKey) {
        return repository.findByModelKeyOrderByVersionDesc(modelKey);
    }

    @Transactional(readOnly = true)
    public List<AiModelVersion> allActive() {
        return repository.findByActiveTrueOrderByTrainedAtDesc();
    }

    /** A short "model v3, trained to 2026-09-30" string for a report footer. */
    @Transactional(readOnly = true)
    public String describe(String modelKey) {
        AiModelVersion version = activeVersion(modelKey);
        if (version == null) {
            return "no trained model recorded for " + modelKey;
        }
        return String.format("model v%d (%s), trained %s to %s, MAE %s",
                version.getVersion(), version.getAlgorithm(),
                version.getTrainingPeriodStart(), version.getTrainingPeriodEnd(),
                version.getMae() == null ? "n/a" : version.getMae().toPlainString());
    }

    private static BigDecimal decimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}