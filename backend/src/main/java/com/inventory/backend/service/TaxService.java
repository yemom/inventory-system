package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final TaxRuleRepository ruleRepository;
    private final SaleOrderRepository saleRepository;
    private final PurchaseOrderRepository purchaseRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final PaymentRepository paymentRepository;
    private final TaxReceiptRepository taxReceiptRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // ── Tax rules ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaxRule> rules() {
        return ruleRepository.findByActiveTrueOrderByTypeAscEffectiveFromDesc();
    }

    @Transactional
    public TaxRule saveRule(TaxRule rule) {
        if (rule.getCode() == null || rule.getCode().isBlank()
                || rule.getName() == null || rule.getName().isBlank()
                || rule.getType() == null || rule.getType().isBlank()
                || rule.getRate() == null || rule.getEffectiveFrom() == null) {
            throw new IllegalArgumentException("code, name, type, rate and effectiveFrom are required for a tax rule.");
        }
        if (rule.getRate().signum() < 0 || rule.getRate().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 1.");
        }
        TaxRule saved = ruleRepository.save(rule);
        auditLogService.log(null, "system", "TAX_RULE_SAVED", "TAX_RULE",
                String.valueOf(saved.getId()), saved.getCode());
        return saved;
    }

    @Transactional
    public TaxRule verifyRule(Long id) {
        TaxRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tax rule not found: id=" + id));
        rule.setVerificationStatus("VERIFIED");
        TaxRule saved = ruleRepository.save(rule);
        User actor = currentUser();
        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "TAX_RULE_VERIFIED", "TAX_RULE", String.valueOf(saved.getId()), saved.getCode());
        return saved;
    }

    // ── Tax summary from live transactions ───────────────────────────────────

    @Transactional(readOnly = true)
    public TaxSummaryDTO summary(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end   = month.atEndOfMonth();
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);

        // 1. Sales & Output VAT
        List<SaleOrder> sales = saleRepository.findSalesForPeriod(
                startDt, endDt, null, null, null, null, false);

        BigDecimal totalSales = ZERO;
        BigDecimal taxableSales = ZERO;
        BigDecimal zeroRatedSales = ZERO;
        BigDecimal exemptSales = ZERO;
        BigDecimal unclassifiedSales = ZERO;
        BigDecimal outputVat = ZERO;

        for (SaleOrder sale : sales) {
            BigDecimal amount = money(sale.getTotalAmount());
            BigDecimal tax = money(sale.getTax());
            totalSales = totalSales.add(amount);

            if (sale.getTax() == null) {
                // Preserve the amount as unclassified: absence of a tax snapshot
                // does not prove an exempt or zero-rated supply.
                unclassifiedSales = unclassifiedSales.add(amount);
            } else if (tax.signum() > 0) {
                taxableSales = taxableSales.add(amount);
                outputVat = outputVat.add(tax);
            } else if (sale.getTax() != null) {
                // Legacy zero snapshots do not encode exempt versus zero-rated.
                // Keep them out of both statutory classifications.
                unclassifiedSales = unclassifiedSales.add(amount);
            }
        }

        // Account for sales returns (reverses output VAT)
        List<ReturnOrder> salesReturns = returnOrderRepository.findApprovedByPeriod(
                ReturnOrder.TYPE_SALES_RETURN, ReturnOrder.STATUS_APPROVED, startDt, endDt);
        BigDecimal salesReturnVat = ZERO;
        for (ReturnOrder ret : salesReturns) {
            BigDecimal retTax = ret.getItems() == null ? ZERO : ret.getItems().stream()
                    .map(TaxService::saleReturnTax)
                    .reduce(ZERO, BigDecimal::add);
            salesReturnVat = salesReturnVat.add(retTax);
        }
        outputVat = outputVat.subtract(salesReturnVat).max(ZERO);

        // 2. Purchases & Input VAT
        List<PurchaseOrder> purchases = purchaseRepository.findAll().stream()
                .filter(p -> !PurchaseOrder.STATUS_CANCELLED.equals(p.getStatus()))
                .filter(p -> PurchaseOrder.STATUS_RECEIVED.equals(p.getStatus())
                        || PurchaseOrder.STATUS_PARTIALLY_RECEIVED.equals(p.getStatus()))
                .filter(p -> {
                    LocalDate d = p.getTransactionDate() != null
                            ? p.getTransactionDate().toLocalDate()
                            : (p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null);
                    return d != null && !d.isBefore(start) && !d.isAfter(end);
                })
                .toList();

        BigDecimal purchaseInputVat = ZERO;
        BigDecimal nonRecoverableInputVat = ZERO;
        for (PurchaseOrder po : purchases) {
            // A purchase order tax amount is not proof of recoverability. Until
            // purchase classifications and supporting evidence are represented,
            // keep this amount visible as non-recoverable/unverified.
            nonRecoverableInputVat = nonRecoverableInputVat.add(money(po.getTax()));
        }

        // Receipts in period
        List<TaxReceipt> receiptsInPeriod = taxReceiptRepository.findAll().stream()
                .filter(r -> {
                    LocalDate d = r.getReceiptDate() != null
                            ? r.getReceiptDate()
                            : (r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : null);
                    return d != null && !d.isBefore(start) && !d.isAfter(end);
                })
                .toList();

        BigDecimal receiptInputVat = ZERO;
        for (TaxReceipt r : receiptsInPeriod) {
            BigDecimal t = money(r.getTaxAmount());
            if ("STANDARD".equalsIgnoreCase(r.getTreatment())) {
                receiptInputVat = receiptInputVat.add(t);
            } else {
                nonRecoverableInputVat = nonRecoverableInputVat.add(t);
            }
        }

        BigDecimal eligibleInputVat = purchaseInputVat.add(receiptInputVat);

        // Account for purchase returns (reverses input VAT)
        List<ReturnOrder> purchaseReturns = returnOrderRepository.findApprovedByPeriod(
                ReturnOrder.TYPE_PURCHASE_RETURN, ReturnOrder.STATUS_APPROVED, startDt, endDt);
        BigDecimal purchaseReturnVat = ZERO;
        for (ReturnOrder ret : purchaseReturns) {
            BigDecimal retTax = ret.getItems() == null ? ZERO : ret.getItems().stream()
                    .map(TaxService::purchaseReturnTax)
                    .reduce(ZERO, BigDecimal::add);
            purchaseReturnVat = purchaseReturnVat.add(retTax);
        }
        eligibleInputVat = eligibleInputVat.subtract(purchaseReturnVat).max(ZERO);

        // 3. Net VAT Payable or Credit
        BigDecimal estimatedNetVat = outputVat.subtract(eligibleInputVat);

        // 4. Tax Payments Recorded (completed payments with TAX in reference or party)
        BigDecimal taxPaymentsMade = ZERO;
        {
            List<Payment> payments = paymentRepository.findAll().stream()
                    .filter(p -> "COMPLETED".equals(p.getStatus()))
                    .filter(p -> "OUT".equalsIgnoreCase(p.getEffectiveDirection()))
                    .filter(p -> {
                        LocalDate d = p.getTransactionDate() != null
                                ? p.getTransactionDate().toLocalDate()
                                : (p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null);
                        return d != null && !d.isBefore(start) && !d.isAfter(end);
                    })
                    .filter(p -> {
                        String ref = p.getOrderReference() == null ? "" : p.getOrderReference().toUpperCase();
                        String notes = p.getNotes() == null ? "" : p.getNotes().toUpperCase();
                        String party = p.getPartyType() == null ? "" : p.getPartyType().toUpperCase();
                        return party.contains("TAX") || ref.contains("TAX") || notes.contains("TAX");
                    })
                    .toList();

            for (Payment p : payments) {
                taxPaymentsMade = taxPaymentsMade.add(money(p.getAmount()));
            }
        }

        // 5. Outstanding Liabilities
        BigDecimal outstandingLiabilities = estimatedNetVat.signum() > 0
                ? estimatedNetVat.subtract(taxPaymentsMade).max(ZERO)
                : ZERO;

        // 6. Withholding Tax: 2% on applicable business purchases if withholding applies
        BigDecimal withholdingTax = ZERO;

        // 7. Estimated Business Income Tax (30% standard corporate rate on net business profit)
        // Business income tax depends on taxpayer category and taxable profit,
        // neither of which can be established from these transaction totals.
        BigDecimal estimatedIncomeTax = ZERO;

        // 8. Upcoming deadlines (Ethiopian tax calendar)
        LocalDate nextMonth = start.plusMonths(1);
        int lastDayOfNextMonth = Math.min(30, nextMonth.lengthOfMonth());
        LocalDate vatDueDate = nextMonth.withDayOfMonth(lastDayOfNextMonth);
        boolean isVatOverdue = LocalDate.now().isAfter(vatDueDate) && outstandingLiabilities.signum() > 0;

        List<TaxSummaryDTO.ComplianceDeadline> deadlines = List.of(
                new TaxSummaryDTO.ComplianceDeadline(
                        "VAT Return",
                        vatDueDate,
                        "Monthly VAT Return submission & payment (Proc. No. 1341/2024 Art. 38)",
                        isVatOverdue),
                new TaxSummaryDTO.ComplianceDeadline(
                        "Employment Withholding",
                        vatDueDate,
                        "Monthly Employment Income Tax withholding return (Proc. No. 979/2016 Art. 88)",
                        LocalDate.now().isAfter(vatDueDate))
        );

        // 9. 6-Month Trend
        List<TaxSummaryDTO.MonthlyTaxTotal> trend = new ArrayList<>();
        for (int offset = 5; offset >= 0; offset--) {
            YearMonth point = month.minusMonths(offset);
            LocalDate f = point.atDay(1), t = point.atEndOfMonth();
            List<SaleOrder> monthSales = saleRepository.findSalesForPeriod(
                    f.atStartOfDay(), t.atTime(LocalTime.MAX), null, null, null, null, false);
            BigDecimal out = tax(monthSales);

            BigDecimal in = purchaseRepository.findAll().stream()
                    .filter(p -> !PurchaseOrder.STATUS_CANCELLED.equals(p.getStatus()))
                    .filter(p -> PurchaseOrder.STATUS_RECEIVED.equals(p.getStatus())
                            || PurchaseOrder.STATUS_PARTIALLY_RECEIVED.equals(p.getStatus()))
                    .filter(p -> {
                        LocalDate d = p.getTransactionDate() != null
                                ? p.getTransactionDate().toLocalDate()
                                : (p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null);
                        return d != null && !d.isBefore(f) && !d.isAfter(t);
                    })
                    .map(p -> money(p.getTax()))
                    .reduce(ZERO, BigDecimal::add);

            trend.add(new TaxSummaryDTO.MonthlyTaxTotal(point.toString(), out, in, out.subtract(in)));
        }

        String classificationNote = (unclassifiedSales.signum() > 0
                ? "Sales totaling " + unclassifiedSales.toPlainString() + " have no tax snapshot and remain unclassified. " : "")
                + "Input VAT is limited to documented standard receipts; purchase order VAT is unverified."
                + (nonRecoverableInputVat.signum() > 0 ? " Non-recoverable or unverified input tax is excluded." : "");
        return new TaxSummaryDTO(
                start, end, totalSales, taxableSales, zeroRatedSales, exemptSales,
                outputVat, eligibleInputVat, nonRecoverableInputVat, estimatedNetVat,
                withholdingTax, estimatedIncomeTax, taxPaymentsMade, outstandingLiabilities,
                outputVat, eligibleInputVat, "ETB", "CALCULATED",
                classificationNote,
                deadlines, trend);
    }

    // ── Contributing Transactions Drilldown ─────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaxTransactionDTO> transactions(YearMonth month, String filterType) {
        LocalDate start = month.atDay(1);
        LocalDate end   = month.atEndOfMonth();
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);

        List<TaxTransactionDTO> list = new ArrayList<>();
        String filter = filterType == null ? "ALL" : filterType.toUpperCase();

        // Sales
        if ("ALL".equals(filter) || "SALES".equals(filter)) {
            List<SaleOrder> sales = saleRepository.findSalesForPeriod(
                    startDt, endDt, null, null, null, null, false);
            for (SaleOrder s : sales) {
                BigDecimal tax = money(s.getTax());
                BigDecimal total = money(s.getTotalAmount());
                String treatment = s.getTax() == null ? "UNCLASSIFIED_MISSING_SNAPSHOT"
                        : tax.signum() > 0 ? "TAXED_SNAPSHOT_CLASSIFICATION_UNVERIFIED"
                        : "UNCLASSIFIED_ZERO_SNAPSHOT";
                list.add(new TaxTransactionDTO(
                        "SALE", s.getId(), s.getOrderNumber(),
                        s.getTransactionDate() != null ? s.getTransactionDate() : s.getCreatedAt(),
                        s.getCustomerName(), total, total, tax,
                        null,
                        treatment, s.getStatus(), s.getPaymentStatus()));
            }
        }

        // Purchases
        if ("ALL".equals(filter) || "PURCHASES".equals(filter)) {
            List<PurchaseOrder> purchases = purchaseRepository.findAll().stream()
                    .filter(p -> !PurchaseOrder.STATUS_CANCELLED.equals(p.getStatus()))
                    .filter(p -> {
                        LocalDate d = p.getTransactionDate() != null
                                ? p.getTransactionDate().toLocalDate()
                                : (p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null);
                        return d != null && !d.isBefore(start) && !d.isAfter(end);
                    })
                    .toList();
            for (PurchaseOrder p : purchases) {
                BigDecimal tax = money(p.getTax());
                BigDecimal total = money(p.getTotalAmount());
                list.add(new TaxTransactionDTO(
                        "PURCHASE", p.getId(), p.getOrderNumber(),
                        p.getTransactionDate() != null ? p.getTransactionDate() : p.getCreatedAt(),
                        p.getSupplierName(), total, total, tax,
                        null, "UNVERIFIED_PURCHASE_TAX", p.getStatus(), p.getPaymentStatus()));
            }
        }

        // Receipts
        if ("ALL".equals(filter) || "RECEIPTS".equals(filter)) {
            List<TaxReceipt> receipts = taxReceiptRepository.findAll().stream()
                    .filter(r -> {
                        LocalDate d = r.getReceiptDate() != null
                                ? r.getReceiptDate()
                                : (r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : null);
                        return d != null && !d.isBefore(start) && !d.isAfter(end);
                    })
                    .toList();
            for (TaxReceipt r : receipts) {
                list.add(new TaxTransactionDTO(
                        "RECEIPT", r.getId(), r.getReceiptName(),
                        r.getReceiptDate() != null ? r.getReceiptDate().atStartOfDay() : r.getCreatedAt(),
                        r.getVendorName() != null ? r.getVendorName() : "External Vendor",
                        money(r.getTotalAmount()), money(r.getTaxableAmount()), money(r.getTaxAmount()),
                        null, r.getTreatment(), "RECORDED", r.getNotes()));
            }
        }

        list.sort(Comparator.comparing(TaxTransactionDTO::transactionDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    // ── Receipt calculation (without saving) ─────────────────────────────────

    @Transactional(readOnly = true)
    public TaxCalculationDTO calculateReceipt(BigDecimal amount, String treatment,
                                               boolean taxInclusive, String receiptName) {
        if (amount == null || amount.signum() < 0)
            throw new IllegalArgumentException("Receipt amount must be zero or greater.");
        String normalized = treatment == null ? "STANDARD" : treatment.trim().toUpperCase();
        BigDecimal rate = "STANDARD".equals(normalized)
                ? requiredRate("VAT_STANDARD", LocalDate.now())
                : BigDecimal.ZERO;
        BigDecimal total   = money(amount);
        BigDecimal taxable = taxInclusive && rate.signum() > 0
                ? total.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP) : total;
        BigDecimal tax     = taxInclusive ? total.subtract(taxable)
                : taxable.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payable = taxInclusive ? total : taxable.add(tax);
        String notice = "STANDARD".equals(normalized)
                ? "Estimate only using the configured effective VAT rule. Confirm the supply is taxable and the receipt supports recoverable input VAT."
                : "Zero-rated or exempt treatment produces no VAT estimate; confirm the legal classification before filing.";
        return new TaxCalculationDTO(receiptName, normalized, taxInclusive,
                total, taxable, money(tax), money(payable), "ETB", "VAT_STANDARD", notice);
    }

    // ── Receipt upload & persistence ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReceiptUploadDTO> receipts() {
        return taxReceiptRepository.findAllOrderByCreatedAtDesc().stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public ReceiptUploadDTO saveReceipt(MultipartFile file, BigDecimal amount, String treatment,
                                         boolean taxInclusive, String receiptName, String vendorName,
                                         String notes, LocalDate receiptDate) throws IOException {
        String uploadDir = System.getProperty("user.home") + "/stockflow-uploads/receipts";
        Files.createDirectories(Paths.get(uploadDir));
        String originalName = file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename();
        String safeFileName = System.currentTimeMillis() + "_"
                + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path filePath = Paths.get(uploadDir, safeFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        TaxCalculationDTO calc = calculateReceipt(amount, treatment, taxInclusive, receiptName);
        User accountant = currentUser();

        TaxReceipt receipt = TaxReceipt.builder()
                .receiptName(receiptName != null ? receiptName : originalName)
                .filePath(filePath.toString())
                .fileType(file.getContentType())
                .detectedAmount(money(amount))
                .taxableAmount(calc.taxableAmount())
                .taxAmount(calc.taxAmount())
                .totalAmount(calc.totalAmount())
                .treatment(calc.treatment())
                .taxInclusive(taxInclusive)
                .currency("ETB")
                .aiExtracted(false)
                .vendorName(vendorName)
                .receiptDate(receiptDate != null ? receiptDate : LocalDate.now())
                .notes(notes)
                .accountant(accountant)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        TaxReceipt saved = taxReceiptRepository.save(receipt);
        auditLogService.log(
                accountant == null ? null : accountant.getId(),
                accountant == null ? "system" : accountant.getUsername(),
                "TAX_RECEIPT_SAVED", "TAX_RECEIPT", String.valueOf(saved.getId()),
                "amount=" + amount + "; treatment=" + treatment);
        return toDto(saved);
    }

    @Transactional
    public void deleteReceipt(Long id) {
        TaxReceipt receipt = taxReceiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found."));
        if (receipt.getFilePath() != null) {
            try { Files.deleteIfExists(Paths.get(receipt.getFilePath())); } catch (IOException ignored) {}
        }
        taxReceiptRepository.deleteById(id);
        User user = currentUser();
        auditLogService.log(user == null ? null : user.getId(),
                user == null ? "system" : user.getUsername(),
                "TAX_RECEIPT_DELETED", "TAX_RECEIPT", String.valueOf(id), "");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ReceiptUploadDTO toDto(TaxReceipt r) {
        String accountantName = r.getAccountant() == null ? null : r.getAccountant().getFullName();
        String notice = "STANDARD".equals(r.getTreatment())
                ? "Estimate only. Confirm the supply is taxable and the receipt supports recoverable input VAT."
                : "Zero-rated or exempt; confirm the legal classification before filing.";
        return new ReceiptUploadDTO(r.getId(), r.getReceiptName(), r.getFileType(),
                r.getDetectedAmount(), r.getTaxableAmount(), r.getTaxAmount(), r.getTotalAmount(),
                r.getTreatment(), r.isTaxInclusive(), r.getCurrency(), r.isAiExtracted(),
                r.getAiConfidence(), r.getVendorName(), r.getReceiptDate(), r.getNotes(),
                accountantName, r.getCreatedAt(), notice);
    }

    private BigDecimal tax(List<SaleOrder> sales) {
        return sales.stream().map(s -> money(s.getTax())).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal saleReturnTax(ReturnOrderItem item) {
        if (item.getTax() != null) return money(item.getTax());
        SaleOrderItem original = item.getSaleOrderItem();
        if (original == null || item.getQuantity() == null || original.getQuantity() == null
                || original.getQuantity() == 0) return ZERO;
        SaleOrder sale = original.getSaleOrder();
        if (sale == null || sale.getTax() == null) return ZERO;
        BigDecimal ratio = BigDecimal.valueOf(item.getQuantity())
                .divide(BigDecimal.valueOf(original.getQuantity()), 8, RoundingMode.HALF_UP);
        return money(sale.getTax().multiply(ratio));
    }

    private static BigDecimal purchaseReturnTax(ReturnOrderItem item) {
        if (item.getTax() != null) return money(item.getTax());
        PurchaseOrderItem original = item.getPurchaseOrderItem();
        if (original == null || item.getQuantity() == null || original.getQuantity() == null
                || original.getQuantity() == 0) return ZERO;
        PurchaseOrder purchase = original.getPurchaseOrder();
        if (purchase == null || purchase.getTax() == null) return ZERO;
        BigDecimal ratio = BigDecimal.valueOf(item.getQuantity())
                .divide(BigDecimal.valueOf(original.getQuantity()), 8, RoundingMode.HALF_UP);
        return money(purchase.getTax().multiply(ratio));
    }

    private BigDecimal requiredRate(String code, LocalDate date) {
        List<TaxRule> rules = ruleRepository.findEffective(code, date);
        if (rules.isEmpty() || !"VERIFIED".equals(rules.get(0).getVerificationStatus())) {
            throw new IllegalStateException("No verified effective tax rule " + code + " for " + date + ".");
        }
        return rules.get(0).getRate();
    }

    private User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByEmail(auth.getName())
                .or(() -> userRepository.findByUsername(auth.getName()))
                .orElse(null);
    }
}
