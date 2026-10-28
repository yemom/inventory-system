package com.inventory.backend.compliance;

import com.inventory.backend.chatbot.ChatbotProvider;
import com.inventory.backend.chatbot.OpenAiCompatibleProvider;
import com.inventory.backend.dto.PayrollDTO;
import com.inventory.backend.dto.TaxSummaryDTO;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import com.inventory.backend.service.PayrollService;
import com.inventory.backend.service.TaxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceServiceUnitTest {

    @Mock
    private TaxService taxService;

    @Mock
    private PayrollService payrollService;

    @Mock
    private TaxRuleRepository taxRuleRepository;

    @Mock
    private PayrollPeriodRepository payrollPeriodRepository;

    @Mock
    private PayrollProfileRepository payrollProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SaleOrderRepository saleOrderRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private ComplianceService complianceService;

    private static BigDecimal bd(String val) {
        return new BigDecimal(val).setScale(2, RoundingMode.HALF_UP);
    }

    @Nested
    @DisplayName("Compliance Findings Generator")
    class FindingsEngine {

        @Test
        @DisplayName("Identifies unverified rules requiring accountant review")
        void detectsUnverifiedRules() {
            TaxRule unverified = TaxRule.builder()
                    .id(1L)
                    .code("VAT_STANDARD")
                    .name("Standard VAT 15%")
                    .verificationStatus("ACCOUNTANT_REVIEW_REQUIRED")
                    .active(true)
                    .build();

            when(taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc()).thenReturn(List.of(unverified));

            TaxSummaryDTO dummyTax = TaxSummaryDTO.builder()
                    .outputVat(bd("0.00"))
                    .eligibleInputVat(bd("0.00"))
                    .estimatedNetVat(bd("0.00"))
                    .build();
            when(taxService.summary(any(YearMonth.class))).thenReturn(dummyTax);

            PayrollDTO.Dashboard dummyPayroll = PayrollDTO.Dashboard.builder()
                    .grossPayroll(bd("0.00"))
                    .netPayroll(bd("0.00"))
                    .hasData(false)
                    .build();
            when(payrollService.dashboard()).thenReturn(dummyPayroll);
            when(payrollPeriodRepository.findAllByOrderByYearDescMonthDesc()).thenReturn(Collections.emptyList());
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(saleOrderRepository.findAll()).thenReturn(Collections.emptyList());

            ComplianceSummaryDTO summary = complianceService.getSummary(YearMonth.now());

            assertNotNull(summary);
            assertTrue(summary.findings().stream().anyMatch(f -> "UNVERIFIED_TAX_RULES".equals(f.id())));
            ComplianceFinding finding = summary.findings().stream()
                    .filter(f -> "UNVERIFIED_TAX_RULES".equals(f.id()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("HIGH", finding.severity());
            assertTrue(finding.humanApprovalRequired());
        }

        @Test
        @DisplayName("Identifies outstanding Net VAT liabilities under Proclamation 1341/2024")
        void detectsNetVatLiability() {
            TaxSummaryDTO taxWithLiability = TaxSummaryDTO.builder()
                    .outputVat(bd("50000.00"))
                    .eligibleInputVat(bd("10000.00"))
                    .estimatedNetVat(bd("40000.00"))
                    .build();

            when(taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc()).thenReturn(Collections.emptyList());
            when(taxService.summary(any(YearMonth.class))).thenReturn(taxWithLiability);

            PayrollDTO.Dashboard dummyPayroll = PayrollDTO.Dashboard.builder()
                    .grossPayroll(bd("0.00"))
                    .netPayroll(bd("0.00"))
                    .hasData(false)
                    .build();
            when(payrollService.dashboard()).thenReturn(dummyPayroll);
            when(payrollPeriodRepository.findAllByOrderByYearDescMonthDesc()).thenReturn(Collections.emptyList());
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(saleOrderRepository.findAll()).thenReturn(Collections.emptyList());

            ComplianceSummaryDTO summary = complianceService.getSummary(YearMonth.now());

            assertNotNull(summary);
            assertEquals(bd("40000.00"), summary.outstandingTaxLiability());
            assertTrue(summary.findings().stream().anyMatch(f -> "NET_VAT_PAYABLE".equals(f.id())));
        }

        @Test
        @DisplayName("Identifies draft and finalized but unpaid payroll liabilities")
        void detectsUnpaidPayroll() {
            when(taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc()).thenReturn(Collections.emptyList());

            TaxSummaryDTO dummyTax = TaxSummaryDTO.builder()
                    .outputVat(bd("0.00"))
                    .eligibleInputVat(bd("0.00"))
                    .estimatedNetVat(bd("0.00"))
                    .build();
            when(taxService.summary(any(YearMonth.class))).thenReturn(dummyTax);

            PayrollDTO.Dashboard dummyPayroll = PayrollDTO.Dashboard.builder()
                    .grossPayroll(bd("25000.00"))
                    .netPayroll(bd("19000.00"))
                    .hasData(true)
                    .build();
            when(payrollService.dashboard()).thenReturn(dummyPayroll);

            PayrollPeriod finalizedPeriod = PayrollPeriod.builder()
                    .id(1L)
                    .year(2026)
                    .month(9)
                    .status(PayrollPeriod.PeriodStatus.FINALIZED)
                    .grossPayroll(bd("25000.00"))
                    .netPayroll(bd("19000.00"))
                    .build();

            when(payrollPeriodRepository.findAllByOrderByYearDescMonthDesc()).thenReturn(List.of(finalizedPeriod));
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(saleOrderRepository.findAll()).thenReturn(Collections.emptyList());

            ComplianceSummaryDTO summary = complianceService.getSummary(YearMonth.now());

            assertNotNull(summary);
            assertEquals(bd("19000.00"), summary.unpaidPayrollLiability());
            assertTrue(summary.findings().stream().anyMatch(f -> "FINALIZED_UNPAID_PAYROLL".equals(f.id())));
        }
    }
}
