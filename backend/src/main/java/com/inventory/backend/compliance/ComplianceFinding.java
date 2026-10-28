package com.inventory.backend.compliance;

import java.math.BigDecimal;

public record ComplianceFinding(
        String id,
        String title,
        String severity, // CRITICAL, HIGH, MEDIUM, LOW, INFO
        String category, // TAX, PAYROLL, RECONCILIATION, DEADLINE, AUDIT
        BigDecimal amount,
        String affectedPeriod,
        String explanation,
        String sourceRecords,
        String recommendedAction,
        boolean humanApprovalRequired
) {}
