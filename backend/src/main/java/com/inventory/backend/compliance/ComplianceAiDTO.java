package com.inventory.backend.compliance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ComplianceAiDTO {
    private ComplianceAiDTO() {}

    public record AnalysisResponse(
            String analysisTitle,
            String executiveSummary,
            String riskLevel, // LOW, MEDIUM, HIGH, CRITICAL
            int overallComplianceScore,
            BigDecimal totalFinancialExposure,
            List<AiFindingDetail> findings,
            List<String> prioritizedActions,
            String providerUsed, // e.g. "openai-compatible" or "deterministic-fallback"
            String period,
            LocalDateTime timestamp
    ) {}

    public record AiFindingDetail(
            String id,
            String title,
            String severity,
            String category,
            String explanation,
            String evidence,
            String numericalImpact,
            String recommendedAction,
            boolean humanApprovalRequired
    ) {}

    public record QuestionRequest(
            String question,
            String period
    ) {}

    public record QuestionResponse(
            String answer,
            List<String> evidenceSources,
            String providerUsed,
            LocalDateTime timestamp
    ) {}
}
