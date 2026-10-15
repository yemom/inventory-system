package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One question asked of the business assistant, and what was answered.
 *
 * <p>The assistant reads structured results from the trusted calculation and
 * reporting services — it never runs its own SQL and never invents a number.
 * That makes it safe to keep, but it also means a wrong answer is either a wrong
 * calculation or a wrong reading of the result, and the only way to tell those
 * apart afterwards is a record of what was asked, which service answered, and
 * what that service returned. This is that record.
 *
 * <p>The question text and the answer are kept so an analyst can audit a figure
 * that later looks wrong. The parameters are kept so the query can be re-run.
 * No credentials, tokens or customer contact details are stored here; the answer
 * payload holds aggregates and references, which is all the assistant is ever
 * given access to.
 */
@Entity
@Table(name = "ai_query_log",
        indexes = {
                @Index(name = "idx_ai_query_log_user", columnList = "user_id"),
                @Index(name = "idx_ai_query_log_created", columnList = "created_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiQueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String username;

    /** The question as typed. */
    @Lob
    @Column(name = "question")
    private String question;

    /** The intent the classifier matched, e.g. GROSS_PROFIT. */
    @Column(name = "intent", length = 60)
    private String intent;

    /**
     * Confidence in the intent match, 0..1.
     *
     * <p>Below the configured threshold the assistant answers with the question
     * it thinks was asked and asks for confirmation instead of answering, because
     * a confidently-wrong reading of an ambiguous sentence is worse than a
     * clarification.
     */
    @Column(name = "confidence", precision = 6, scale = 4)
    private java.math.BigDecimal confidence;

    /** Resolved date range and filters, as JSON. */
    @Lob
    @Column(name = "resolved_parameters")
    private String resolvedParameters;

    /** The structured result, as JSON — the only source the wording is drawn from. */
    @Lob
    @Column(name = "result_payload")
    private String resultPayload;

    /** Model versions consulted while answering, comma-separated. */
    @Column(name = "model_versions", length = 255)
    private String modelVersions;

    /** ANSWERED, INSUFFICIENT_DATA, CLARIFICATION_NEEDED or DENIED. */
    @Column(name = "outcome", length = 30)
    private String outcome;

    @Column(name = "duration_ms")
    private Long durationMs;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static final String OUTCOME_ANSWERED = "ANSWERED";
    public static final String OUTCOME_INSUFFICIENT_DATA = "INSUFFICIENT_DATA";
    public static final String OUTCOME_CLARIFICATION_NEEDED = "CLARIFICATION_NEEDED";
    public static final String OUTCOME_DENIED = "DENIED";
}