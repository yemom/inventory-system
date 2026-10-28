package com.inventory.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PayrollDTO {
    private PayrollDTO() {}

    public record Profile(
            Long id,
            Long userId,
            String employeeId,
            String fullName,
            String department,
            String jobTitle,
            BigDecimal basicSalary,
            LocalDate effectiveFrom,
            boolean pensionEligible,
            boolean active) {}

    public record Period(
            Long id,
            int year,
            int month,
            String status,
            BigDecimal grossPayroll,
            BigDecimal netPayroll,
            BigDecimal employmentTax,
            BigDecimal employeePension,
            BigDecimal employerPension,
            BigDecimal employerCost,
            String ruleVersion,
            LocalDateTime finalizedAt,
            LocalDateTime paidAt,
            String finalizedByName,
            String paidByName,
            List<Item> items) {}

    public record Item(
            Long id,
            Long userId,
            String employeeId,
            String fullName,
            String department,
            BigDecimal basicSalary,
            BigDecimal taxableIncome,
            BigDecimal incomeTax,
            BigDecimal employeePension,
            BigDecimal employerPension,
            BigDecimal otherDeductions,
            BigDecimal netPay,
            String calculationBreakdown) {}

    public record Dashboard(
            BigDecimal grossPayroll,
            BigDecimal netPayroll,
            BigDecimal employmentTax,
            BigDecimal employeePension,
            BigDecimal employerPension,
            BigDecimal employerCost,
            long activeProfiles,
            long draftPeriods,
            long finalizedPeriods,
            long paidPeriods,
            BigDecimal totalPayrollPaid,
            BigDecimal totalPayrollOutstanding,
            String currency,
            boolean hasData) {

        // Legacy constructor for backward compatibility
        public Dashboard(
                BigDecimal grossPayroll, BigDecimal netPayroll, BigDecimal employmentTax,
                BigDecimal employeePension, BigDecimal employerPension, BigDecimal employerCost,
                long activeProfiles, long draftPeriods, String currency) {
            this(grossPayroll, netPayroll, employmentTax, employeePension, employerPension, employerCost,
                    activeProfiles, draftPeriods, 0, 0, BigDecimal.ZERO, netPayroll, currency, activeProfiles > 0 || grossPayroll.signum() > 0);
        }
    }
}
