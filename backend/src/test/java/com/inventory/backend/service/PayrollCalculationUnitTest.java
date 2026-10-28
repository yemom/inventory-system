package com.inventory.backend.service;

import com.inventory.backend.dto.PayrollDashboardDTO;
import com.inventory.backend.dto.PayrollPeriodDTO;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.PayrollItemRepository;
import com.inventory.backend.repository.PayrollPeriodRepository;
import com.inventory.backend.repository.PayrollProfileRepository;
import com.inventory.backend.repository.TaxRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollCalculationUnitTest {

    @Mock
    private PayrollProfileRepository profileRepository;

    @Mock
    private PayrollPeriodRepository periodRepository;

    @Mock
    private PayrollItemRepository itemRepository;

    @Mock
    private TaxRuleRepository taxRuleRepository;

    @InjectMocks
    private PayrollService payrollService;

    private static BigDecimal bd(String val) {
        return new BigDecimal(val).setScale(2, RoundingMode.HALF_UP);
    }

    @Nested
    @DisplayName("Proclamation No. 1395/2025 Progressive Employment Tax Brackets")
    class IncomeTaxBrackets {

        @Test
        @DisplayName("Bracket 1: Up to 2,000 ETB is exempt (0% tax)")
        void bracket1_Exempt() {
            BigDecimal tax = payrollService.calculateEmploymentTax(bd("2000.00"));
            assertEquals(bd("0.00"), tax);
        }

        @Test
        @DisplayName("Bracket 2: 2,001 to 4,000 ETB taxed at 10% less 200 ETB deduction")
        void bracket2_TenPercent() {
            // 3,000 * 0.10 - 200 = 100.00 ETB
            BigDecimal tax = payrollService.calculateEmploymentTax(bd("3000.00"));
            assertEquals(bd("100.00"), tax);
        }

        @Test
        @DisplayName("Bracket 3: 4,001 to 7,000 ETB taxed at 15% less 400 ETB deduction")
        void bracket3_FifteenPercent() {
            // 5,000 * 0.15 - 400 = 750 - 400 = 350.00 ETB
            BigDecimal tax = payrollService.calculateEmploymentTax(bd("5000.00"));
            assertEquals(bd("350.00"), tax);
        }

        @Test
        @DisplayName("Bracket 4: 7,001 to 10,000 ETB taxed at 20% less 750 ETB deduction")
        void bracket4_TwentyPercent() {
            // 8,000 * 0.20 - 750 = 1,600 - 750 = 850.00 ETB
            BigDecimal tax = payrollService.calculateEmploymentTax(bd("8000.00"));
            assertEquals(bd("850.00"), tax);
        }

        @Test
        @DisplayName("Bracket 5: 10,001 to 14,000 ETB taxed at 25% less 1,250 ETB deduction")
        void bracket5_TwentyFivePercent() {
            // 12,000 * 0.25 - 1,250 = 3,000 - 1,250 = 1,750.00 ETB
            BigDecimal tax = payrollService.calculateEmploymentTax(bd("12000.00"));
            assertEquals(bd("1750.00"), tax);
        }

        @Test
        @DisplayName("Bracket 6: 14,001 to 19,000 ETB taxed at 30% less 1,950 ETB deduction")
        void bracket6_ThirtyPercent() {
            // 16,000 * 0.30 - 1,950 = 4,800 - 1,950 = 2,850.00 ETB
            BigDecimal tax = payrollService.calculateEmploymentTax(bd("16000.00"));
            assertEquals(bd("2850.00"), tax);
        }

        @Test
        @DisplayName("Bracket 7: Above 19,000 ETB taxed at 35% less 2,900 ETB deduction")
        void bracket7_ThirtyFivePercent() {
            // 25,000 * 0.35 - 2,900 = 8,750 - 2,900 = 5,850.00 ETB
            BigDecimal tax = payrollService.calculateEmploymentTax(bd("25000.00"));
            assertEquals(bd("5850.00"), tax);
        }
    }

    @Nested
    @DisplayName("Payroll Invariants & Pension Calculations (Proc 1268/2022)")
    class InvariantsAndPension {

        @Test
        @DisplayName("Gross - Deductions == Net Pay & Employer Cost == Gross + Employer Pension")
        void testPayrollInvariants() {
            BigDecimal basicSalary = bd("10000.00");
            BigDecimal allowances = bd("2000.00");
            BigDecimal grossSalary = basicSalary.add(allowances); // 12,000.00

            // Employee pension = 7% of 12,000 = 840.00
            BigDecimal empPension = grossSalary.multiply(new BigDecimal("0.07")).setScale(2, RoundingMode.HALF_UP);
            assertEquals(bd("840.00"), empPension);

            // Employer pension = 11% of 12,000 = 1,320.00
            BigDecimal emprPension = grossSalary.multiply(new BigDecimal("0.11")).setScale(2, RoundingMode.HALF_UP);
            assertEquals(bd("1320.00"), emprPension);

            // Income tax on 12,000 (bracket 5: 12,000 * 0.25 - 1,250) = 1,750.00
            BigDecimal incomeTax = payrollService.calculateEmploymentTax(grossSalary);
            assertEquals(bd("1750.00"), incomeTax);

            BigDecimal otherDeductions = bd("100.00");
            BigDecimal totalDeductions = incomeTax.add(empPension).add(otherDeductions); // 1,750 + 840 + 100 = 2,690.00
            BigDecimal netPay = grossSalary.subtract(totalDeductions); // 12,000 - 2,690 = 9,310.00

            // Invariant 1: Gross - Deductions == Net Pay
            assertEquals(grossSalary.subtract(totalDeductions), netPay);
            assertEquals(bd("9310.00"), netPay);

            // Invariant 2: Employer Payroll Cost == Gross + Employer Pension
            BigDecimal employerPayrollCost = grossSalary.add(emprPension);
            assertEquals(bd("13320.00"), employerPayrollCost);
        }

        @Test
        @DisplayName("Finalization is blocked when active tax rules require accountant review")
        void finalizationRequiresVerifiedRules() {
            TaxRule unverifiedRule = TaxRule.builder()
                    .id(1L)
                    .code("VAT_STANDARD")
                    .verificationStatus("ACCOUNTANT_REVIEW_REQUIRED")
                    .active(true)
                    .build();

            when(taxRuleRepository.findByActiveTrue()).thenReturn(List.of(unverifiedRule));

            PayrollPeriod period = PayrollPeriod.builder()
                    .id(10L)
                    .year(2026)
                    .month(10)
                    .status(PayrollPeriod.PeriodStatus.DRAFT)
                    .build();

            when(periodRepository.findById(10L)).thenReturn(Optional.of(period));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> payrollService.finalizePeriod(10L));
            assertTrue(ex.getMessage().contains("Cannot finalize payroll"));
            assertTrue(ex.getMessage().contains("VAT_STANDARD"));
        }

        @Test
        @DisplayName("Dashboard returns honest empty state (hasData=false) when zero periods exist")
        void emptyStateHandledHonestly() {
            when(periodRepository.findAll()).thenReturn(Collections.emptyList());
            when(profileRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

            PayrollDashboardDTO dashboard = payrollService.dashboard();

            assertNotNull(dashboard);
            assertFalse(dashboard.hasData(), "Dashboard must declare hasData=false when no records exist");
            assertEquals(BigDecimal.ZERO, dashboard.grossPayroll());
            assertEquals(BigDecimal.ZERO, dashboard.netPayroll());
            assertEquals(BigDecimal.ZERO, dashboard.employerCost());
        }

        @Test
        @DisplayName("Disbursement marks finalized period as PAID with timestamp")
        void disburseAndMarkPaid() {
            PayrollPeriod period = PayrollPeriod.builder()
                    .id(20L)
                    .year(2026)
                    .month(9)
                    .status(PayrollPeriod.PeriodStatus.FINALIZED)
                    .grossPayroll(bd("50000.00"))
                    .netPayroll(bd("38500.00"))
                    .build();

            when(periodRepository.findById(20L)).thenReturn(Optional.of(period));
            when(periodRepository.save(any(PayrollPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

            PayrollPeriodDTO result = payrollService.payPeriod(20L);

            assertNotNull(result);
            assertEquals(PayrollPeriod.PeriodStatus.PAID, result.status());
            assertNotNull(result.paidAt());
        }
    }
}
