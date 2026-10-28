package com.inventory.backend.compliance;

import com.inventory.backend.dto.PayrollDTO;
import com.inventory.backend.dto.TaxSummaryDTO;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import com.inventory.backend.service.PayrollService;
import com.inventory.backend.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final TaxService taxService;
    private final PayrollService payrollService;
    private final TaxRuleRepository taxRuleRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollProfileRepository payrollProfileRepository;
    private final UserRepository userRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public ComplianceSummaryDTO getSummary(YearMonth targetMonth) {
        YearMonth month = targetMonth != null ? targetMonth : YearMonth.now();
        List<ComplianceFinding> findings = new ArrayList<>();

        TaxSummaryDTO taxSummary = taxService.summary(month);
        PayrollDTO.Dashboard payrollDashboard = payrollService.dashboard();
        List<PayrollPeriod> allPeriods = payrollPeriodRepository.findAllByOrderByYearDescMonthDesc();

        // 1. Check Unverified Tax Rules
        List<TaxRule> unverifiedRules = taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc().stream()
                .filter(r -> "ACCOUNTANT_REVIEW_REQUIRED".equals(r.getVerificationStatus()))
                .toList();
        if (!unverifiedRules.isEmpty()) {
            findings.add(new ComplianceFinding(
                    "RULE-001",
                    "Unverified Tax Rules Require Accountant Review",
                    "HIGH",
                    "TAX",
                    null,
                    month.toString(),
                    String.format("%d active tax/pension rule(s) (including %s) are pending accountant verification. Statutory confirmation required before payroll finalisation.",
                            unverifiedRules.size(), unverifiedRules.get(0).getCode()),
                    "tax_rules table (" + unverifiedRules.size() + " rules)",
                    "Review each effective rule in Tax Rules & Classifications and set status to Verified after confirming statutory proclamation source.",
                    true
            ));
        }

        // 2. Outstanding Tax Liabilities
        BigDecimal outstandingTax = taxSummary.outstandingLiabilities() != null ? taxSummary.outstandingLiabilities() : ZERO;
        if (outstandingTax.signum() > 0) {
            findings.add(new ComplianceFinding(
                    "TAX-001",
                    "Outstanding Net VAT Liability",
                    "HIGH",
                    "TAX",
                    outstandingTax,
                    month.toString(),
                    String.format("Calculated Output VAT (%s ETB) exceeds eligible Input VAT (%s ETB) and payments made. Remaining net liability: %s ETB.",
                            taxSummary.outputVat(), taxSummary.eligibleInputVat(), outstandingTax),
                    "sale_orders vs purchase_orders/tax_receipts for " + month,
                    "Reconcile input tax vouchers and remit VAT payment to Ethiopian Ministry of Revenues prior to monthly deadline.",
                    true
            ));
        }

        // 3. Tax Filing & Payment Deadlines
        LocalDate nextMonth = month.atDay(1).plusMonths(1);
        int lastDay = Math.min(30, nextMonth.lengthOfMonth());
        LocalDate deadline = nextMonth.withDayOfMonth(lastDay);
        boolean overdue = LocalDate.now().isAfter(deadline) && outstandingTax.signum() > 0;
        findings.add(new ComplianceFinding(
                "DEADLINE-001",
                overdue ? "OVERDUE: Monthly VAT Return Filing & Payment" : "Upcoming Monthly VAT Compliance Deadline",
                overdue ? "CRITICAL" : "MEDIUM",
                "DEADLINE",
                outstandingTax,
                month.toString(),
                String.format("Monthly VAT return for %s is due on %s pursuant to VAT Proclamation No. 1341/2024 Art. 38.", month, deadline),
                "Statutory Ethiopian Tax Calendar",
                overdue ? "Submit return immediately with applicable penalty calculation." : "Prepare draft declaration and verify transaction classifications.",
                false
        ));

        // 4. Draft / Unfinalized Payroll Periods
        List<PayrollPeriod> draftPeriods = allPeriods.stream()
                .filter(p -> "DRAFT".equals(p.getStatus()))
                .toList();
        for (PayrollPeriod dp : draftPeriods) {
            findings.add(new ComplianceFinding(
                    "PAYROLL-DRAFT-" + dp.getId(),
                    "Draft Payroll Run Pending Review: " + dp.getYear() + "-" + String.format("%02d", dp.getMonth()),
                    "MEDIUM",
                    "PAYROLL",
                    dp.getNetPayroll(),
                    dp.getYear() + "-" + String.format("%02d", dp.getMonth()),
                    String.format("Payroll period %d-%02d has been generated in DRAFT state with gross salary %s ETB and net pay %s ETB. It has not yet been approved or finalized.",
                            dp.getYear(), dp.getMonth(), dp.getGrossPayroll(), dp.getNetPayroll()),
                    "payroll_periods #" + dp.getId(),
                    "Authorized payroll manager must verify attendance/deductions and finalize the period.",
                    true
            ));
        }

        // 5. Unpaid Finalized Payroll
        List<PayrollPeriod> unpaidPeriods = allPeriods.stream()
                .filter(p -> "FINALIZED".equals(p.getStatus()))
                .toList();
        BigDecimal totalUnpaidPayroll = ZERO;
        for (PayrollPeriod up : unpaidPeriods) {
            totalUnpaidPayroll = totalUnpaidPayroll.add(up.getNetPayroll() != null ? up.getNetPayroll() : ZERO);
            findings.add(new ComplianceFinding(
                    "PAYROLL-UNPAID-" + up.getId(),
                    "Finalized Payroll Awaiting Payment: " + up.getYear() + "-" + String.format("%02d", up.getMonth()),
                    "HIGH",
                    "PAYROLL",
                    up.getNetPayroll(),
                    up.getYear() + "-" + String.format("%02d", up.getMonth()),
                    String.format("Payroll period %d-%02d is finalized and approved but net payroll payment of %s ETB has not been recorded as disbursed.",
                            up.getYear(), up.getMonth(), up.getNetPayroll()),
                    "payroll_periods #" + up.getId(),
                    "Record bank transfer disbursement and mark payroll period as PAID to clear employee liability.",
                    true
            ));
        }

        // 6. Active Employees Missing Payroll Profiles
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> u.isActive() && !Boolean.TRUE.equals(u.getIsDeleted()))
                .toList();
        Set<Long> profileUserIds = new HashSet<>(payrollProfileRepository.findAll().stream()
                .map(p -> p.getUser().getId()).toList());
        List<User> missingProfiles = activeUsers.stream()
                .filter(u -> !profileUserIds.contains(u.getId()))
                .toList();
        if (!missingProfiles.isEmpty()) {
            findings.add(new ComplianceFinding(
                    "STAFF-001",
                    "Active Staff Missing Payroll Profiles",
                    "MEDIUM",
                    "PAYROLL",
                    null,
                    month.toString(),
                    String.format("%d active staff member(s) (including %s) do not have a configured payroll profile. They will be omitted from monthly payroll calculations.",
                            missingProfiles.size(), missingProfiles.get(0).getFullName()),
                    "users table (" + missingProfiles.size() + " active accounts)",
                    "Configure basic salary, pension eligibility, and masked bank account under Payroll Profiles.",
                    false
            ));
        }

        // 7. Missing Tax Details on Sales or Purchases
        List<SaleOrder> taxableWithoutTax = saleOrderRepository.findAll().stream()
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .filter(s -> s.getTotalAmount() != null && s.getTotalAmount().compareTo(new BigDecimal("1000")) > 0)
                .filter(s -> s.getTax() == null || s.getTax().signum() == 0)
                .limit(5)
                .toList();
        if (!taxableWithoutTax.isEmpty()) {
            findings.add(new ComplianceFinding(
                    "TAX-EXEMPT-001",
                    "High-Value Sales with Zero Recorded Tax",
                    "LOW",
                    "TAX",
                    taxableWithoutTax.stream().map(SaleOrder::getTotalAmount).reduce(ZERO, BigDecimal::add),
                    month.toString(),
                    String.format("%d sale(s) totaling %s ETB have zero recorded tax. Confirm if these are legally exempt or zero-rated exports.",
                            taxableWithoutTax.size(), taxableWithoutTax.get(0).getOrderNumber()),
                    "sale_orders (" + taxableWithoutTax.get(0).getOrderNumber() + ")",
                    "Verify customer tax exemption certificate or apply standard 15% VAT on future invoices.",
                    false
            ));
        }

        // Calculate score: starts at 100, drops per finding severity
        int score = 100;
        int highPriority = 0;
        for (ComplianceFinding f : findings) {
            switch (f.severity()) {
                case "CRITICAL" -> { score -= 25; highPriority++; }
                case "HIGH" -> { score -= 15; highPriority++; }
                case "MEDIUM" -> score -= 7;
                case "LOW" -> score -= 3;
            }
        }
        score = Math.max(10, Math.min(100, score));

        String overallStatus = score >= 85 ? "COMPLIANT" : (score >= 60 ? "ACTION_REQUIRED" : "HIGH_RISK");
        String taxStatus = outstandingTax.signum() == 0 ? "RECONCILED" : "PAYMENT_DUE";
        String payrollStatus = draftPeriods.isEmpty() && unpaidPeriods.isEmpty() ? "CURRENT" : "ATTENTION_REQUIRED";

        return new ComplianceSummaryDTO(
                overallStatus,
                score,
                outstandingTax,
                totalUnpaidPayroll,
                findings.size(),
                highPriority,
                deadline.toString(),
                findings,
                taxStatus,
                payrollStatus,
                LocalDateTime.now()
        );
    }
}
