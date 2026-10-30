package com.inventory.backend.service;

import com.inventory.backend.dto.PayrollDTO;
import com.inventory.backend.model.PayrollPeriod;
import com.inventory.backend.model.PayrollProfile;
import com.inventory.backend.model.TaxRule;
import com.inventory.backend.model.User;
import com.inventory.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollCalculationUnitTest {
    @Mock private PayrollProfileRepository profileRepository;
    @Mock private PayrollPeriodRepository periodRepository;
    @Mock private PayrollItemRepository itemRepository;
    @Mock private UserRepository userRepository;
    @Mock private TaxRuleRepository taxRuleRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private PayrollService payrollService;

    private static TaxRule bracket(String code, String rate, String bound, String status) {
        return TaxRule.builder()
                .code(code)
                .name(code)
                .type("EMPLOYMENT_INCOME_TAX")
                .rate(new BigDecimal(rate))
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .verificationStatus(status)
                .notes("upperBound=" + bound)
                .active(true)
                .build();
    }

    private void stubPensionRules(YearMonth month) {
        TaxRule employee = TaxRule.builder().code("PENSION_EMPLOYEE").rate(new BigDecimal("0.07"))
                .effectiveFrom(LocalDate.of(2020, 1, 1)).verificationStatus("VERIFIED").build();
        TaxRule employer = TaxRule.builder().code("PENSION_EMPLOYER").rate(new BigDecimal("0.11"))
                .effectiveFrom(LocalDate.of(2020, 1, 1)).verificationStatus("VERIFIED").build();
        when(taxRuleRepository.findEffective(eq("PENSION_EMPLOYEE"), eq(month.atEndOfMonth())))
                .thenReturn(List.of(employee));
        when(taxRuleRepository.findEffective(eq("PENSION_EMPLOYER"), eq(month.atEndOfMonth())))
                .thenReturn(List.of(employer));
    }

    @Test
    void progressiveTaxUsesVerifiedEffectiveBrackets() {
        YearMonth month = YearMonth.of(2026, 10);
        stubPensionRules(month);
        when(taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc()).thenReturn(List.of(
                bracket("EMPLOYMENT_BRACKET_1", "0.00", "2000", "VERIFIED"),
                bracket("EMPLOYMENT_BRACKET_2", "0.10", "INFINITY", "VERIFIED")));

        BigDecimal tax = payrollService.progressiveTax(new BigDecimal("3000.00"), month);

        assertEquals(new BigDecimal("100.00"), tax);
    }

    @Test
    void progressiveTaxRejectsMissingEffectivePensionRules() {
        when(taxRuleRepository.findEffective(anyString(), any(LocalDate.class))).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> payrollService.progressiveTax(new BigDecimal("3000.00"), YearMonth.of(2026, 10)));

        assertTrue(error.getMessage().contains("pension rules are required"));
    }

    @Test
    void periodCreationSavesAnUncalculatedDraftUntilRulesAreVerified() {
        int year = 2026;
        int month = 10;
        PayrollProfile profile = PayrollProfile.builder()
                .user(User.builder().id(5L).build())
                .basicSalary(new BigDecimal("3000.00"))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .pensionEligible(false)
                .active(true)
                .build();
        when(periodRepository.existsByYearAndMonth(year, month)).thenReturn(false);
        when(profileRepository.findByActiveTrue()).thenReturn(List.of(profile));
        when(taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc()).thenReturn(List.of(
                bracket("EMPLOYMENT_BRACKET_1", "0.10", "INFINITY", "ACCOUNTANT_REVIEW_REQUIRED")));
        when(periodRepository.saveAndFlush(any(PayrollPeriod.class))).thenAnswer(invocation -> {
            PayrollPeriod period = invocation.getArgument(0);
            period.setId(10L);
            return period;
        });
        when(itemRepository.findByPayrollPeriodId(10L)).thenReturn(List.of());

        PayrollDTO.Period period = payrollService.createPeriod(year, month);

        assertEquals("DRAFT", period.status());
        assertTrue(period.items().isEmpty());
        assertTrue(period.ruleVersion().contains("awaiting-accountant-verification"));
        verify(itemRepository, never()).save(any());
    }

    @Test
    void finalizationIsBlockedUntilPayrollRulesAreVerified() {
        YearMonth month = YearMonth.of(2026, 10);
        PayrollPeriod period = PayrollPeriod.builder().id(10L).year(2026).month(10).status("DRAFT").build();
        PayrollProfile profile = PayrollProfile.builder()
                .basicSalary(new BigDecimal("3000.00"))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .pensionEligible(false)
                .active(true)
                .build();
        when(periodRepository.findById(10L)).thenReturn(Optional.of(period));
        when(profileRepository.findByActiveTrue()).thenReturn(List.of(profile));
        when(taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc()).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> payrollService.finalizePeriod(10L));

        assertTrue(error.getMessage().contains("verified"));
        verify(periodRepository, never()).save(any(PayrollPeriod.class));
    }
}
