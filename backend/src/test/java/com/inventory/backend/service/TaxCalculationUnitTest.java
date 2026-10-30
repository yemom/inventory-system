package com.inventory.backend.service;

import com.inventory.backend.dto.TaxCalculationDTO;
import com.inventory.backend.model.TaxRule;
import com.inventory.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxCalculationUnitTest {
    @Mock private SaleOrderRepository saleOrderRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private ReturnOrderRepository returnOrderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private TaxReceiptRepository taxReceiptRepository;
    @Mock private TaxRuleRepository taxRuleRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private TaxService taxService;

    private void configureVerifiedStandardVat() {
        TaxRule vat = TaxRule.builder()
                .code("VAT_STANDARD")
                .name("Standard VAT")
                .type("VAT")
                .rate(new BigDecimal("0.15"))
                .effectiveFrom(LocalDate.of(2020, 1, 1))
                .verificationStatus("VERIFIED")
                .active(true)
                .build();
        when(taxRuleRepository.findEffective(eq("VAT_STANDARD"), any(LocalDate.class)))
                .thenReturn(List.of(vat));
    }

    @Test
    void exclusiveStandardVatUsesVerifiedRate() {
        configureVerifiedStandardVat();
        TaxCalculationDTO result = taxService.calculateReceipt(
                new BigDecimal("1000.00"), "STANDARD", false, "Office supplies");

        assertEquals(new BigDecimal("1000.00"), result.taxableAmount());
        assertEquals(new BigDecimal("150.00"), result.taxAmount());
        assertEquals(new BigDecimal("1150.00"), result.totalAmount());
    }

    @Test
    void inclusiveStandardVatSeparatesTaxFromTotal() {
        configureVerifiedStandardVat();
        TaxCalculationDTO result = taxService.calculateReceipt(
                new BigDecimal("1150.00"), "STANDARD", true, "Office supplies");

        assertEquals(new BigDecimal("1000.00"), result.taxableAmount());
        assertEquals(new BigDecimal("150.00"), result.taxAmount());
        assertEquals(new BigDecimal("1150.00"), result.totalAmount());
    }

    @Test
    void exemptTreatmentDoesNotApplyVat() {
        TaxCalculationDTO result = taxService.calculateReceipt(
                new BigDecimal("1150.00"), "EXEMPT", false, "Exempt purchase");

        assertEquals(new BigDecimal("0.00"), result.taxAmount());
        assertEquals(new BigDecimal("1150.00"), result.totalAmount());
    }

    @Test
    void negativeReceiptAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> taxService.calculateReceipt(
                new BigDecimal("-1.00"), "STANDARD", false, "Invalid receipt"));
    }
}
