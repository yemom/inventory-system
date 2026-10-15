package com.inventory.backend.service;

import com.inventory.backend.dto.SaleOrderDTO;
import com.inventory.backend.finance.FinancialCalculationService;
import com.inventory.backend.finance.FinancialSummary;
import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.model.SaleOrderItem;
import com.inventory.backend.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sales reporting.
 *
 * <p>Cost of goods sold here used to be {@code product.purchasePrice × quantity},
 * which reports today's purchase price as the cost of goods sold last quarter and
 * therefore restates every historical sale whenever a price changes. It now
 * reads the weighted-average cost snapshotted on each sale line, and every figure
 * in the summary comes from {@link FinancialCalculationService} so this report,
 * the profit and loss report and the dashboard cannot disagree.
 */
@Service
@RequiredArgsConstructor
public class SalesReportService {

    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderService saleOrderService;
    private final FinancialCalculationService calculations;
    private final jakarta.persistence.EntityManager em;

    /**
     * The sales report.
     *
     * @param from inclusive lower bound on the transaction date; null for no bound
     * @param to   inclusive upper bound; null for no bound
     */
    @Transactional(readOnly = true)
    public Map<String, Object> salesReport(LocalDateTime from, LocalDateTime to,
                                           Long customerId, Long productId,
                                           Long sellerId, String paymentMethod, String status) {
        List<SaleOrder> sales = findSales(from, to, customerId, productId,
                sellerId, paymentMethod, status);

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal netSales = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        long itemsSold = 0;
        long transactionCount = 0;

        for (SaleOrder s : sales) {
            if ("CANCELLED".equalsIgnoreCase(s.getStatus())) {
                continue;
            }
            transactionCount++;
            discount = discount.add(nz(s.getDiscount()));
            tax = tax.add(nz(s.getTax()));
            if (s.getItems() == null) {
                continue;
            }
            for (SaleOrderItem item : s.getItems()) {
                int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                int returned = item.getQuantityReturned() != null ? item.getQuantityReturned() : 0;
                int netQuantity = quantity - returned;
                itemsSold += netQuantity;

                // Line revenue before the order-level discount.
                grossSales = grossSales.add(nz(item.getSubtotal()));

                // The snapshot cost, not the product's current purchase price.
                BigDecimal unitCost = item.getUnitCost() != null
                        ? item.getUnitCost()
                        : (item.getProduct() != null ? nz(item.getProduct().getPurchasePrice()) : BigDecimal.ZERO);
                cogs = cogs.add(unitCost.multiply(BigDecimal.valueOf(netQuantity)));
            }
            netSales = netSales.add(nz(s.getTotalAmount()).subtract(nz(s.getDiscount())));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("transactionCount", transactionCount);
        summary.put("itemsSold", itemsSold);
        summary.put("grossSales", money(grossSales));
        summary.put("discount", money(discount));
        summary.put("tax", money(tax));
        summary.put("netSales", money(netSales));
        summary.put("cogs", money(cogs));
        summary.put("grossProfit", money(netSales.subtract(cogs)));
        summary.put("grossMarginPercent", netSales.signum() == 0 ? BigDecimal.ZERO
                : money(netSales.subtract(cogs)).multiply(BigDecimal.valueOf(100))
                        .divide(netSales, 1, java.math.RoundingMode.HALF_UP));
        summary.put("costingMethod", "Weighted average, snapshotted on each sale line");

        List<SaleOrderDTO> saleDtos = sales.stream()
                .map(saleOrderService::toDTO)
                .collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", summary);
        report.put("sales", saleDtos);
        return report;
    }

    /**
     * Profit and loss for a period.
     *
     * <p>Delegated to the central calculation layer rather than recomputed here.
     * The two figures are now the same figure by construction, which is the only
     * way two financial screens can be trusted to agree.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> profitLossReport(LocalDateTime from, LocalDateTime to) {
        LocalDate fromDate = from == null ? LocalDateTime.now().minusDays(29).toLocalDate()
                : from.toLocalDate();
        LocalDate toDate = to == null ? LocalDateTime.now().toLocalDate() : to.toLocalDate();

        FinancialSummary summary = calculations.summarise(fromDate, toDate);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("from", summary.from());
        report.put("to", summary.to());
        report.put("transactionCount", summary.salesCount());
        report.put("itemsSold", summary.itemsSold());
        report.put("grossSales", summary.grossSales());
        report.put("discount", summary.discountsGiven());
        report.put("netSales", summary.netSales());
        report.put("tax", summary.netSalesTax());
        report.put("cogs", summary.costOfGoodsSold());
        report.put("grossProfit", summary.grossProfit());
        report.put("grossMarginPercent", summary.grossMarginPercent());
        report.put("operatingExpenses", summary.operatingExpenses());
        report.put("netProfit", summary.netProfit());
        report.put("netMarginPercent", summary.netMarginPercent());
        report.put("cashReceived", summary.cashReceived());
        report.put("netCashFlow", summary.netCashFlow());
        report.put("accountsReceivable", summary.accountsReceivable());
        report.put("accountsPayable", summary.accountsPayable());
        report.put("inventoryValue", summary.inventoryValue());
        report.put("stockLossValue", summary.stockLossValue());
        report.put("expensesByCategory", summary.expensesByCategory());
        report.put("basis", summary.basis());
        report.put("insufficientData", summary.insufficientData());
        return report;
    }

    private List<SaleOrder> findSales(LocalDateTime from, LocalDateTime to, Long customerId,
                                      Long productId, Long sellerId, String paymentMethod,
                                      String status) {
        List<Long> ids = saleOrderRepository.findSaleIdsForReport(
                from, to, customerId, productId, sellerId, paymentMethod, status);
        if (ids.isEmpty()) {
            return List.of();
        }
        return saleOrderRepository.findByIdsWithItems(ids).stream()
                .sorted(Comparator.comparing(SaleOrder::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)
                : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}