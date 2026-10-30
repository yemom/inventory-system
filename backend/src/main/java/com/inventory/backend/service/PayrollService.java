package com.inventory.backend.service;

import com.inventory.backend.dto.PayrollDTO;
import com.inventory.backend.exception.BusinessRuleViolationException;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

@Service
@RequiredArgsConstructor
public class PayrollService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PayrollProfileRepository profileRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollItemRepository itemRepository;
    private final UserRepository userRepository;
    private final TaxRuleRepository ruleRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<PayrollDTO.Profile> profiles() {
        return profileRepository.findAll().stream().map(this::profile).toList();
    }

    @Transactional
    public PayrollDTO.Profile saveProfile(Long userId, BigDecimal basicSalary, LocalDate effectiveFrom,
                                           boolean pensionEligible, String maskedBank) {
        if (basicSalary == null || basicSalary.signum() < 0 || effectiveFrom == null)
            throw new IllegalArgumentException("basicSalary and effectiveFrom are required.");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found: id=" + userId));
        PayrollProfile existing = profileRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(userId)).findFirst().orElse(null);
        PayrollProfile value = existing == null ? PayrollProfile.builder().user(user).build() : existing;
        value.setBasicSalary(money(basicSalary));
        value.setEffectiveFrom(effectiveFrom);
        value.setPensionEligible(pensionEligible);
        value.setActive(true);
        value.setBankAccountMasked(maskedBank);
        PayrollProfile saved = profileRepository.save(value);
        audit("PAYROLL_PROFILE_SAVED", "PAYROLL_PROFILE", saved.getId(), user.getEmployeeId());
        return profile(saved);
    }

    @Transactional
    public PayrollDTO.Period createPeriod(int year, int month) {
        if (month < 1 || month > 12) throw new IllegalArgumentException("month must be between 1 and 12.");
        if (periodRepository.existsByYearAndMonth(year, month)) {
            throw new BusinessRuleViolationException("PAYROLL_PERIOD_EXISTS",
                    "A payroll period already exists for " + year + "-" + String.format("%02d", month)
                            + ". Open the existing period to review it.");
        }
        YearMonth targetMonth = YearMonth.of(year, month);
        List<PayrollProfile> activeProfiles = profileRepository.findByActiveTrue().stream()
                .filter(p -> !p.getEffectiveFrom().isAfter(targetMonth.atEndOfMonth())).toList();
        boolean rulesReady = payrollRulesReady(targetMonth, activeProfiles);
        PayrollPeriod period;
        try {
            period = periodRepository.saveAndFlush(PayrollPeriod.builder()
                    .year(year).month(month).status("DRAFT")
                    .ruleVersion(ruleVersion(targetMonth) + (rulesReady ? "" : "; awaiting-accountant-verification"))
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessRuleViolationException("PAYROLL_PERIOD_EXISTS",
                    "A payroll period already exists for " + year + "-" + String.format("%02d", month)
                            + ". Open the existing period to review it.");
        }

        if (rulesReady) {
            for (PayrollProfile profile : activeProfiles) {
                itemRepository.save(calculate(period, profile, targetMonth));
            }
        }
        refreshTotals(period);
        audit("PAYROLL_PERIOD_CREATED", "PAYROLL_PERIOD", period.getId(), year + "-" + month);
        return period(period, true);
    }

    @Transactional(readOnly = true)
    public List<PayrollDTO.Period> periods() {
        return periodRepository.findAllByOrderByYearDescMonthDesc().stream()
                .map(p -> period(p, false)).toList();
    }

    @Transactional(readOnly = true)
    public PayrollDTO.Period period(Long id) {
        return period(periodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payroll period not found: id=" + id)), true);
    }

    @Transactional
    public PayrollDTO.Period finalizePeriod(Long id) {
        PayrollPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payroll period not found: id=" + id));
        if ("FINALIZED".equals(period.getStatus()) || "PAID".equals(period.getStatus())) {
            throw new IllegalArgumentException("This payroll period is already " + period.getStatus().toLowerCase() + ".");
        }
        YearMonth month = YearMonth.of(period.getYear(), period.getMonth());
        List<TaxRule> activeRules = effectiveRules(month.atEndOfMonth());
        List<PayrollProfile> periodProfiles = profileRepository.findByActiveTrue().stream()
                .filter(p -> !p.getEffectiveFrom().isAfter(month.atEndOfMonth())).toList();
        if (!payrollRulesReady(month, periodProfiles)) {
            throw new IllegalStateException("Payroll cannot finalize until the effective employment-tax brackets and applicable pension rates are verified.");
        }
        Set<String> required = periodProfiles.stream()
                .anyMatch(PayrollProfile::isPensionEligible)
                ? Set.of("PENSION_EMPLOYEE", "PENSION_EMPLOYER") : Set.of();
        if (!activeRules.stream().filter(r -> required.contains(r.getCode()))
                .filter(r -> "VERIFIED".equals(r.getVerificationStatus()))
                .map(TaxRule::getCode).collect(java.util.stream.Collectors.toSet()).containsAll(required)
                || required.stream().anyMatch(code -> ruleRepository.findEffective(code, month.atEndOfMonth())
                        .stream().anyMatch(r -> !"VERIFIED".equals(r.getVerificationStatus())))) {
            throw new IllegalStateException("Payroll cannot finalize until effective employment-tax brackets and applicable pension rules are verified.");
        }
        List<TaxRule> verifiedBrackets = activeRules.stream()
                .filter(r -> r.getCode().startsWith("EMPLOYMENT_BRACKET_"))
                .filter(r -> "VERIFIED".equals(r.getVerificationStatus()))
                .sorted(Comparator.comparing(this::upperBound, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        boolean hasTaxablePayroll = itemRepository.findByPayrollPeriodId(id).stream()
                .anyMatch(i -> i.getTaxableIncome() != null && i.getTaxableIncome().signum() > 0);
        if (hasTaxablePayroll && (verifiedBrackets.isEmpty()
                || verifiedBrackets.get(verifiedBrackets.size() - 1).getNotes() == null
                || !verifiedBrackets.get(verifiedBrackets.size() - 1).getNotes().contains("upperBound=INFINITY"))) {
            throw new IllegalStateException("Payroll cannot finalize without verified effective employment-tax brackets.");
        }
        period.setStatus("FINALIZED");
        period.setFinalizedAt(LocalDateTime.now());
        period.setFinalizedBy(currentUser());
        period.setRuleVersion(ruleVersion(month));
        periodRepository.save(period);
        audit("PAYROLL_FINALIZED", "PAYROLL_PERIOD", id, period.getYear() + "-" + period.getMonth());
        return period(period, true);
    }

    @Transactional
    public PayrollDTO.Period payPeriod(Long id) {
        PayrollPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payroll period not found: id=" + id));
        if ("PAID".equals(period.getStatus())) {
            throw new IllegalArgumentException("This payroll period has already been paid.");
        }
        if (!"FINALIZED".equals(period.getStatus())) {
            throw new IllegalStateException("Payroll period must be finalized before recording payment.");
        }

        User actor = currentUser();
        Payment p = Payment.builder()
                    .reference("PR-" + period.getYear() + "-" + String.format("%02d", period.getMonth()))
                    .orderReference("PAYROLL-" + period.getYear() + "-" + String.format("%02d", period.getMonth()))
                    .direction("OUT")
                    .type("OUT")
                    .partyType("PAYROLL")
                    .amount(period.getNetPayroll())
                    .paymentMethod("BANK_TRANSFER")
                    .status("COMPLETED")
                    .transactionDate(LocalDateTime.now())
                    .notes("Net payroll disbursement for " + period.getYear() + "-" + period.getMonth())
                    .createdBy(actor == null ? "system" : actor.getUsername())
                    .createdByUser(actor)
                    .build();
        paymentRepository.save(p);
        period.setStatus("PAID");
        period.setPaidAt(LocalDateTime.now());
        period.setPaidBy(actor);
        periodRepository.save(period);

        audit("PAYROLL_PAID", "PAYROLL_PERIOD", id, period.getYear() + "-" + period.getMonth());
        return period(period, true);
    }

    @Transactional(readOnly = true)
    public PayrollDTO.Item payslip(Long itemId) {
        PayrollItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip item not found: id=" + itemId));
        User actor = currentUser();
        if (actor != null && !isPrivilegedPayrollViewer(actor) && !actor.getId().equals(item.getUser().getId())) {
            throw new AccessDeniedException("You are not authorized to view this employee's payslip.");
        }
        return item(item);
    }

    @Transactional(readOnly = true)
    public PayrollDTO.Dashboard dashboard() {
        List<PayrollPeriod> periods = periodRepository.findAllByOrderByYearDescMonthDesc();
        List<PayrollProfile> profiles = profileRepository.findByActiveTrue();
        boolean hasData = !periods.isEmpty() || !profiles.isEmpty();

        if (periods.isEmpty()) {
            return new PayrollDTO.Dashboard(
                    ZERO, ZERO, ZERO, ZERO, ZERO, ZERO,
                    profiles.size(), 0, 0, 0, ZERO, ZERO, "ETB", hasData);
        }
        PayrollPeriod latest = periods.get(0);
        long draftCount = periods.stream().filter(p -> "DRAFT".equals(p.getStatus())).count();
        long finalizedCount = periods.stream().filter(p -> "FINALIZED".equals(p.getStatus())).count();
        long paidCount = periods.stream().filter(p -> "PAID".equals(p.getStatus())).count();

        BigDecimal totalPaid = periods.stream()
                .filter(p -> "PAID".equals(p.getStatus()))
                .map(PayrollPeriod::getNetPayroll)
                .map(PayrollService::money)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = periods.stream()
                .filter(p -> !"PAID".equals(p.getStatus()))
                .map(PayrollPeriod::getNetPayroll)
                .map(PayrollService::money)
                .reduce(ZERO, BigDecimal::add);

        return new PayrollDTO.Dashboard(
                money(latest.getGrossPayroll()),
                money(latest.getNetPayroll()),
                money(latest.getEmploymentTax()),
                money(latest.getEmployeePension()),
                money(latest.getEmployerPension()),
                money(latest.getEmployerCost()),
                profiles.size(),
                draftCount,
                finalizedCount,
                paidCount,
                totalPaid,
                totalOutstanding,
                "ETB",
                true);
    }

    @Transactional(readOnly = true)
    public PayrollDTO.Dashboard dashboard(YearMonth month) {
        List<PayrollPeriod> periods = periodRepository.findAllByOrderByYearDescMonthDesc();
        List<PayrollProfile> profiles = profileRepository.findByActiveTrue().stream()
                .filter(p -> !p.getEffectiveFrom().isAfter(month.atEndOfMonth())).toList();
        PayrollPeriod selected = periods.stream().filter(p -> p.getYear() == month.getYear()
                && p.getMonth() == month.getMonthValue()).findFirst().orElse(null);
        BigDecimal paid = periods.stream().filter(p -> "PAID".equals(p.getStatus()))
                .filter(p -> p.getYear() == month.getYear() && p.getMonth() == month.getMonthValue())
                .map(PayrollPeriod::getNetPayroll).map(PayrollService::money).reduce(ZERO, BigDecimal::add);
        BigDecimal outstanding = selected != null && !"PAID".equals(selected.getStatus())
                ? money(selected.getNetPayroll()) : ZERO;
        return selected == null
                ? new PayrollDTO.Dashboard(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, profiles.size(), 0, 0, 0,
                        paid, outstanding, "ETB", !profiles.isEmpty())
                : new PayrollDTO.Dashboard(money(selected.getGrossPayroll()), money(selected.getNetPayroll()),
                        money(selected.getEmploymentTax()), money(selected.getEmployeePension()),
                        money(selected.getEmployerPension()), money(selected.getEmployerCost()), profiles.size(),
                        "DRAFT".equals(selected.getStatus()) ? 1 : 0,
                        "FINALIZED".equals(selected.getStatus()) ? 1 : 0,
                        "PAID".equals(selected.getStatus()) ? 1 : 0, paid, outstanding, "ETB", true);
    }

    // ── Calculations & Helpers ───────────────────────────────────────────────

    private PayrollItem calculate(PayrollPeriod period, PayrollProfile profile, YearMonth month) {
        BigDecimal gross = money(profile.getBasicSalary());
        boolean eligible = profile.isPensionEligible();
        BigDecimal employeePension = eligible
                ? gross.multiply(requiredRate("PENSION_EMPLOYEE", month)).setScale(2, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal employerPension = eligible
                ? gross.multiply(requiredRate("PENSION_EMPLOYER", month)).setScale(2, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal tax = progressiveTax(gross, month);
        BigDecimal net = gross.subtract(employeePension).subtract(tax).setScale(2, RoundingMode.HALF_UP);

        // Verification invariant: Gross - (EmployeePension + Tax) == Net
        BigDecimal deductions = employeePension.add(tax);
        if (gross.subtract(deductions).compareTo(net) != 0) {
            throw new IllegalStateException("Payroll invariant violated: Gross " + gross + " - Deductions " + deductions + " != Net " + net);
        }

        return PayrollItem.builder()
                .payrollPeriod(period)
                .user(profile.getUser())
                .basicSalary(gross)
                .taxableIncome(gross)
                .incomeTax(tax)
                .employeePension(employeePension)
                .employerPension(employerPension)
                .otherDeductions(ZERO)
                .netPay(net)
                .calculationBreakdown(String.format("Basic=%s; Taxable=%s; IncomeTax=%s; EmpPension(7%%)=%s; EmprPension(11%%)=%s; Net=%s (Proc. 1395/2025 & 1268/2022)",
                        gross, gross, tax, employeePension, employerPension, net))
                .build();
    }

    /** Progressive Employment Income Tax (Ethiopian Proclamation No. 1395/2025 Art. 11 / 979/2016) */
    public BigDecimal progressiveTax(BigDecimal income, YearMonth month) {
        if (income == null || income.signum() <= 0) return ZERO;

        if (ruleRepository.findEffective("PENSION_EMPLOYEE", month.atEndOfMonth()).isEmpty()
                || ruleRepository.findEffective("PENSION_EMPLOYER", month.atEndOfMonth()).isEmpty()) {
            throw new IllegalStateException("Effective employee and employer pension rules are required for " + month + ".");
        }

        // Try to load configured effective tax brackets from database
        List<TaxRule> bracketRules = effectiveRules(month.atEndOfMonth()).stream()
                .filter(r -> r.getCode().startsWith("EMPLOYMENT_BRACKET_"))
                .sorted(Comparator.comparing(this::upperBound, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (!bracketRules.isEmpty()) {
            if (bracketRules.stream().anyMatch(r -> !"VERIFIED".equals(r.getVerificationStatus()))) {
                throw new IllegalStateException("Employment income-tax rules require verification for " + month + ".");
            }
            if (bracketRules.get(bracketRules.size() - 1).getNotes() == null
                    || !bracketRules.get(bracketRules.size() - 1).getNotes().contains("upperBound=INFINITY")) {
                throw new IllegalStateException("Employment income-tax brackets are incomplete for " + month + ".");
            }
            BigDecimal lower = BigDecimal.ZERO;
            BigDecimal total = ZERO;
            for (TaxRule rule : bracketRules) {
                BigDecimal upper = upperBound(rule);
                BigDecimal taxable = upper == null
                        ? income.subtract(lower).max(BigDecimal.ZERO)
                        : income.min(upper).subtract(lower).max(BigDecimal.ZERO);
                total = total.add(taxable.multiply(rule.getRate()));
                if (upper == null || income.compareTo(upper) <= 0) break;
                lower = upper;
            }
            return money(total);
        }

        if (income.signum() > 0) {
            throw new IllegalStateException("No effective employment income-tax brackets are configured for " + month + ".");
        }
        return ZERO;
    }

    private BigDecimal upperBound(TaxRule rule) {
        if (rule.getNotes() == null) return null;
        Matcher m = Pattern.compile("upperBound=([0-9]+)").matcher(rule.getNotes());
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private BigDecimal requiredRate(String code, YearMonth month) {
        List<TaxRule> rules = ruleRepository.findEffective(code, month.atEndOfMonth());
        if (rules.isEmpty() || !"VERIFIED".equals(rules.get(0).getVerificationStatus())) {
            throw new IllegalStateException("No verified effective rule " + code + " for " + month + ".");
        }
        return rules.get(0).getRate();
    }

    private boolean payrollRulesReady(YearMonth month, List<PayrollProfile> profiles) {
        boolean needsPension = profiles.stream().anyMatch(PayrollProfile::isPensionEligible);
        if (needsPension) {
            List<TaxRule> employee = ruleRepository.findEffective("PENSION_EMPLOYEE", month.atEndOfMonth());
            List<TaxRule> employer = ruleRepository.findEffective("PENSION_EMPLOYER", month.atEndOfMonth());
            if (employee.isEmpty() || employer.isEmpty()
                    || !"VERIFIED".equals(employee.get(0).getVerificationStatus())
                    || !"VERIFIED".equals(employer.get(0).getVerificationStatus())) return false;
        }
        if (profiles.stream().noneMatch(p -> p.getBasicSalary().signum() > 0)) return true;
        List<TaxRule> brackets = effectiveRules(month.atEndOfMonth()).stream()
                .filter(r -> r.getCode().startsWith("EMPLOYMENT_BRACKET_"))
                .sorted(Comparator.comparing(this::upperBound, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return !brackets.isEmpty()
                && brackets.stream().allMatch(r -> "VERIFIED".equals(r.getVerificationStatus()))
                && brackets.get(brackets.size() - 1).getNotes() != null
                && brackets.get(brackets.size() - 1).getNotes().contains("upperBound=INFINITY");
    }

    private List<TaxRule> effectiveRules(LocalDate date) {
        return ruleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc().stream()
                .filter(r -> !r.getEffectiveFrom().isAfter(date) && (r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(date)))
                .toList();
    }

    private String ruleVersion(YearMonth month) {
        return "effective-" + month + "; rules=" + effectiveRules(month.atEndOfMonth()).stream()
                .map(TaxRule::getCode).sorted().reduce((a, b) -> a + "," + b).orElse("statutory-baseline-1395-2025");
    }

    private void refreshTotals(PayrollPeriod period) {
        List<PayrollItem> items = itemRepository.findByPayrollPeriodId(period.getId());
        period.setGrossPayroll(sum(items, PayrollItem::getBasicSalary));
        period.setNetPayroll(sum(items, PayrollItem::getNetPay));
        period.setEmploymentTax(sum(items, PayrollItem::getIncomeTax));
        period.setEmployeePension(sum(items, PayrollItem::getEmployeePension));
        period.setEmployerPension(sum(items, PayrollItem::getEmployerPension));
        period.setEmployerCost(period.getGrossPayroll().add(period.getEmployerPension()));
        periodRepository.save(period);
    }

    private BigDecimal sum(List<PayrollItem> items, java.util.function.Function<PayrollItem, BigDecimal> getter) {
        return items.stream().map(getter).map(PayrollService::money).reduce(ZERO, BigDecimal::add);
    }

    private PayrollDTO.Profile profile(PayrollProfile p) {
        User u = p.getUser();
        return new PayrollDTO.Profile(
                p.getId(), u.getId(), u.getEmployeeId(), u.getFullName(),
                u.getDepartment(), u.getJobTitle(), p.getBasicSalary(),
                p.getEffectiveFrom(), p.isPensionEligible(), p.isActive());
    }

    private PayrollDTO.Period period(PayrollPeriod p, boolean detail) {
        List<PayrollDTO.Item> items = detail
                ? itemRepository.findByPayrollPeriodId(p.getId()).stream().map(this::item).toList()
                : List.of();
        String finalizedByName = p.getFinalizedBy() != null ? p.getFinalizedBy().getFullName() : null;
        String paidByName = p.getPaidBy() != null ? p.getPaidBy().getFullName() : null;
        return new PayrollDTO.Period(
                p.getId(), p.getYear(), p.getMonth(), p.getStatus(),
                money(p.getGrossPayroll()), money(p.getNetPayroll()), money(p.getEmploymentTax()),
                money(p.getEmployeePension()), money(p.getEmployerPension()), money(p.getEmployerCost()),
                p.getRuleVersion(), p.getFinalizedAt(), p.getPaidAt(), finalizedByName, paidByName, items);
    }

    private PayrollDTO.Item item(PayrollItem i) {
        User u = i.getUser();
        return new PayrollDTO.Item(
                i.getId(), u.getId(), u.getEmployeeId(), u.getFullName(), u.getDepartment(),
                money(i.getBasicSalary()), money(i.getTaxableIncome()), money(i.getIncomeTax()),
                money(i.getEmployeePension()), money(i.getEmployerPension()), money(i.getOtherDeductions()),
                money(i.getNetPay()), i.getCalculationBreakdown());
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private User currentUser() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        return userRepository.findByEmail(a.getName()).or(() -> userRepository.findByUsername(a.getName())).orElse(null);
    }

    private boolean isPrivilegedPayrollViewer(User user) {
        if (user == null || user.getRole() == null) return false;
        String role = user.getRole().getName();
        return "SUPER_ADMIN".equals(role) || "MANAGER".equals(role) || "ACCOUNTANT".equals(role);
    }

    private void audit(String action, String type, Long id, String details) {
        User user = currentUser();
        auditLogService.log(user == null ? null : user.getId(), user == null ? "system" : user.getUsername(), action, type, String.valueOf(id), details);
    }
}
