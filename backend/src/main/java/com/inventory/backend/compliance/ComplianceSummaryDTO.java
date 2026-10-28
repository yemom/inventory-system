package com.inventory.backend.compliance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ComplianceSummaryDTO(
        String overallStatus, // COMPLIANT, ACTION_REQUIRED, HIGH_RISK
        int complianceScore, // 0 - 100
        BigDecimal outstandingTaxLiability,
        BigDecimal unpaidPayrollLiability,
        int totalFindingsCount,
        int highPriorityCount,
        String nextDeadline,
        List<ComplianceFinding> findings,
        String taxFilingStatus,
        String payrollStatus,
        LocalDateTime evaluatedAt
) {}
