package com.inventory.backend.service;

import com.inventory.backend.dto.TaxReceiptDTO;
import com.inventory.backend.dto.TaxSummaryDTO;
import com.inventory.backend.dto.TaxTransactionDTO;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxCalculationUnitTest {

    @Mock
    private SaleOrderRepository saleOrderRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private ReturnOrderRepository returnOrderRepository;

    @Mock
    private TaxReceiptRepository taxReceiptRepository;

    @Mock
    private TaxRuleRepository taxRuleRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private TaxService taxService;

    private static BigDecimal bd(String val) {
        return new BigDecimal(val).setScale(2, RoundingMode.HALF_UP);
    }

    @Nested
    @DisplayName("VAT & Return Order Calculations (Proclamation No. 1341/2024)")
    class VatAndReturns {

        @Test
        @DisplayName("Output VAT: Sale total 115,000 with 15% VAT yields 15,000 VAT & 100,000 base")
        void outputVatCalculation() {
            SaleOrder sale = SaleOrder.builder()
                    .id(1L)
                    .orderNumber("SO-1001")
                    .status(SaleOrderStatus.COMPLETED)
                    .tax(bd("15000.00"))
                    .totalAmount(bd("115000.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(saleOrderRepository.findAll()).thenReturn(List.of(sale));
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());
            when(returnOrderRepository.findAll()).thenReturn(Collections.emptyList());
            when(taxReceiptRepository.findAll()).thenReturn(Collections.emptyList());
            when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

            TaxSummaryDTO summary = taxService.summary(null);

            assertNotNull(summary);
            assertEquals(bd("15000.00"), summary.outputVat());
            assertEquals(bd("100000.00"), summary.taxableSales());
            assertEquals(bd("15000.00"), summary.estimatedNetVat());
        }

        @Test
        @DisplayName("Sales Returns adjust output VAT downward accurately")
        void returnsReduceOutputVat() {
            SaleOrder sale = SaleOrder.builder()
                    .id(1L)
                    .orderNumber("SO-1001")
                    .status(SaleOrderStatus.COMPLETED)
                    .tax(bd("15000.00"))
                    .totalAmount(bd("115000.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            // Return order of 10,000 goods -> 10,000 * 0.15 = 1,500 VAT credit
            ReturnOrder ret = ReturnOrder.builder()
                    .id(1L)
                    .returnNumber("RET-001")
                    .status(ReturnStatus.COMPLETED)
                    .totalAmount(bd("10000.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(saleOrderRepository.findAll()).thenReturn(List.of(sale));
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());
            when(returnOrderRepository.findAll()).thenReturn(List.of(ret));
            when(taxReceiptRepository.findAll()).thenReturn(Collections.emptyList());
            when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

            TaxSummaryDTO summary = taxService.summary(null);

            assertNotNull(summary);
            // 15,000 - 1,500 = 13,500.00 Output VAT
            assertEquals(bd("13500.00"), summary.outputVat());
            // Net VAT = 13,500 - 0 = 13,500
            assertEquals(bd("13500.00"), summary.estimatedNetVat());
        }

        @Test
        @DisplayName("Recoverable Input VAT from purchases offsets Output VAT in Net VAT formula")
        void inputVatOffsetsOutputVat() {
            SaleOrder sale = SaleOrder.builder()
                    .id(1L)
                    .orderNumber("SO-1001")
                    .status(SaleOrderStatus.COMPLETED)
                    .tax(bd("30000.00"))
                    .totalAmount(bd("230000.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            PurchaseOrder purchase = PurchaseOrder.builder()
                    .id(1L)
                    .orderNumber("PO-2001")
                    .status(PurchaseOrderStatus.RECEIVED)
                    .taxAmount(bd("12000.00"))
                    .totalAmount(bd("92000.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(saleOrderRepository.findAll()).thenReturn(List.of(sale));
            when(purchaseOrderRepository.findAll()).thenReturn(List.of(purchase));
            when(returnOrderRepository.findAll()).thenReturn(Collections.emptyList());
            when(taxReceiptRepository.findAll()).thenReturn(Collections.emptyList());
            when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

            TaxSummaryDTO summary = taxService.summary(null);

            assertNotNull(summary);
            assertEquals(bd("30000.00"), summary.outputVat());
            assertEquals(bd("12000.00"), summary.eligibleInputVat());
            // Net VAT Payable = 30,000 - 12,000 = 18,000.00
            assertEquals(bd("18000.00"), summary.estimatedNetVat());
            assertEquals(bd("18000.00"), summary.netVatPayableOrCredit());
        }

        @Test
        @DisplayName("Receipt tax calculation supports inclusive and exclusive tax base")
        void receiptTaxCalculations() {
            // Exclusive: 1,000 at 15% -> 150 tax, 1,150 total
            TaxReceiptDTO excl = taxService.calculateReceipt(bd("1000.00"), "STANDARD", false, "Office Supplies");
            assertEquals(bd("1000.00"), excl.taxableAmount());
            assertEquals(bd("150.00"), excl.taxAmount());
            assertEquals(bd("1150.00"), excl.totalAmount());

            // Inclusive: 1,150 inclusive of 15% -> 1,000 base, 150 tax
            TaxReceiptDTO incl = taxService.calculateReceipt(bd("1150.00"), "STANDARD", true, "Fuel Receipt");
            assertEquals(bd("1000.00"), incl.taxableAmount());
            assertEquals(bd("150.00"), incl.taxAmount());
            assertEquals(bd("1150.00"), incl.totalAmount());
        }

        @Test
        @DisplayName("Transactions audit list aggregates sales, purchases, and returns with details")
        void transactionsAuditList() {
            SaleOrder sale = SaleOrder.builder()
                    .id(1L)
                    .orderNumber("SO-101")
                    .status(SaleOrderStatus.COMPLETED)
                    .tax(bd("150.00"))
                    .totalAmount(bd("1150.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(saleOrderRepository.findAll()).thenReturn(List.of(sale));
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());
            when(returnOrderRepository.findAll()).thenReturn(Collections.emptyList());
            when(taxReceiptRepository.findAll()).thenReturn(Collections.emptyList());
            when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

            List<TaxTransactionDTO> txList = taxService.transactions(null);

            assertNotNull(txList);
            assertEquals(1, txList.size());
            TaxTransactionDTO tx = txList.get(0);
            assertEquals("SALE", tx.transactionType());
            assertEquals("SO-101", tx.referenceNumber());
            assertEquals(bd("150.00"), tx.taxAmount());
            assertEquals("STANDARD_RATED", tx.taxTreatment());
        }
    }
}
