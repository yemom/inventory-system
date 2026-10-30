package com.inventory.backend.compliance;

import com.inventory.backend.chatbot.ChatbotProvider;
import com.inventory.backend.chatbot.OpenAiCompatibleProvider;
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
import java.time.LocalDate;
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

    private static TaxSummaryDTO taxSummary(String outputVat, String inputVat, String netVat) {
        return new TaxSummaryDTO(
                LocalDate.now().withDayOfMonth(1), LocalDate.now(),
                bd("0.00"), bd("0.00"), bd("0.00"), bd("0.00"),
                bd(outputVat), bd(inputVat), bd("0.00"), bd(netVat), bd("0.00"), bd("0.00"),
                bd("0.00"), bd(netVat), bd("0.00"), bd("0.00"), "ETB", "CALCULATED", "",
                List.of(), List.of());
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

            TaxSummaryDTO dummyTax = taxSummary("0.00", "0.00", "0.00");
            when(taxService.summary(any(YearMonth.class))).thenReturn(dummyTax);

            when(payrollPeriodRepository.findAllByOrderByYearDescMonthDesc()).thenReturn(Collections.emptyList());
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            ComplianceSummaryDTO summary = complianceService.getSummary(YearMonth.now());

            assertNotNull(summary);
            assertTrue(summary.findings().stream().anyMatch(f -> "RULE-001".equals(f.id())));
            ComplianceFinding finding = summary.findings().stream()
                    .filter(f -> "RULE-001".equals(f.id()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("HIGH", finding.severity());
            assertTrue(finding.humanApprovalRequired());
        }

        @Test
        @DisplayName("Identifies outstanding Net VAT liabilities under Proclamation 1341/2024")
        void detectsNetVatLiability() {
            TaxSummaryDTO taxWithLiability = taxSummary("50000.00", "10000.00", "40000.00");

            when(taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc()).thenReturn(Collections.emptyList());
            when(taxService.summary(any(YearMonth.class))).thenReturn(taxWithLiability);

            when(payrollPeriodRepository.findAllByOrderByYearDescMonthDesc()).thenReturn(Collections.emptyList());
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            ComplianceSummaryDTO summary = complianceService.getSummary(YearMonth.now());

            assertNotNull(summary);
            assertEquals(bd("40000.00"), summary.outstandingTaxLiability());
            assertTrue(summary.findings().stream().anyMatch(f -> "TAX-001".equals(f.id())));
        }

        @Test
        @DisplayName("Identifies draft and finalized but unpaid payroll liabilities")
        void detectsUnpaidPayroll() {
            when(taxRuleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc()).thenReturn(Collections.emptyList());

            TaxSummaryDTO dummyTax = taxSummary("0.00", "0.00", "0.00");
            when(taxService.summary(any(YearMonth.class))).thenReturn(dummyTax);

            PayrollPeriod finalizedPeriod = PayrollPeriod.builder()
                    .id(1L)
                    .year(YearMonth.now().getYear())
                    .month(YearMonth.now().getMonthValue())
                    .status("FINALIZED")
                    .grossPayroll(bd("25000.00"))
                    .netPayroll(bd("19000.00"))
                    .build();

            when(payrollPeriodRepository.findAllByOrderByYearDescMonthDesc()).thenReturn(List.of(finalizedPeriod));
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            ComplianceSummaryDTO summary = complianceService.getSummary(YearMonth.now());

            assertNotNull(summary);
            assertEquals(bd("19000.00"), summary.unpaidPayrollLiability());
            assertTrue(summary.findings().stream().anyMatch(f -> "PAYROLL-UNPAID-1".equals(f.id())));
        }
    }
}
