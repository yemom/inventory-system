package com.inventory.backend.finance;

import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.inventory.backend.finance.FinancialCalculations.*;

/**
 * Assembles a {@link FinancialSummary} from persisted records.
 *
 * <p>All arithmetic is delegated to {@link FinancialCalculations}; this class
 * only decides <em>which records count</em>, and that decision is written down at
 * each step. Every figure comes from a row that actually happened — a
 * completed payment, an approved expense, a received purchase — never from a
 * status label typed by a user or recomputed in a browser.
 */
@Service
@RequiredArgsConstructor
public class FinancialCalculationService {

    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * A period summary. {@code from} and {@code to} are inclusive dates; the
     * day bounds are applied here so every caller gets the same period.
     */
    @Transactional(readOnly = true)
    public FinancialSummary summarise(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(java.time.LocalTime.MAX);

        // ── Revenue ───────────────────────────────────────────────────────────
        List<SaleOrder> sales = saleOrderRepository.findSalesForPeriod(
                start, end, null, null, null, null, false);

        BigDecimal grossSales = ZERO;
        BigDecimal discountsGiven = ZERO;
        BigDecimal taxCollected = ZERO;
        BigDecimal cogs = ZERO;
        long itemsSold = 0;

        for (SaleOrder sale : sales) {
            discountsGiven = discountsGiven.add(nz(sale.getDiscount()));
            taxCollected = taxCollected.add(nz(sale.getTax()));
            if (sale.getItems() == null) {
                continue;
            }
            for (SaleOrderItem item : sale.getItems()) {
                int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                itemsSold += quantity;

                grossSales = grossSales.add(lineNet(item.getUnitPrice(), quantity, item.getDiscount()));
                // Cost of the FULL line, deliberately. A return is accounted for
                // once, below, as a single reversal — exactly as it is on the
                // revenue side. Subtracting the returned units here as well would
                // double-count: the return's cost would be removed twice and the
                // period would show a smaller cost of goods sold than ever left
                // the shelf.
                //
                // The snapshot cost is what this sale actually cost. Falling back
                // to the product's current price is only correct for rows written
                // before the snapshot existed.
                cogs = cogs.add(lineCost(item.getUnitCost(),
                        item.getProduct() != null ? item.getProduct().getPurchasePrice() : null,
                        quantity));
            }
        }

        // ── Sales returns reverse both revenue and cost ───────────────────────
        List<ReturnOrder> salesReturns = returnOrderRepository.findApprovedByPeriod(
                ReturnOrder.TYPE_SALES_RETURN, ReturnOrder.STATUS_APPROVED, start, end);

        BigDecimal salesReturnsValue = ZERO;
        BigDecimal taxRefunded = ZERO;
        for (ReturnOrder ret : salesReturns) {
            salesReturnsValue = salesReturnsValue.add(nz(ret.getTotalAmount()));
            if (ret.getItems() == null) {
                continue;
            }
            for (ReturnOrderItem item : ret.getItems()) {
                int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                // The goods came back, so their cost leaves COGS with them.
                cogs = cogs.subtract(lineCost(item.getUnitCost(),
                        item.getProduct() != null ? item.getProduct().getPurchasePrice() : null,
                        quantity));
                taxRefunded = taxRefunded.add(nz(item.getTax()));
            }
        }

        BigDecimal netSalesValue = netSales(money(grossSales), money(discountsGiven),
                money(salesReturnsValue));
        BigDecimal grossProfitValue = grossProfit(netSalesValue, money(cogs));
        BigDecimal netSalesTax = money(taxCollected.subtract(taxRefunded));

        // ── Expenses ──────────────────────────────────────────────────────────
        List<Expense> expenses = expenseRepository.findApprovedBetween(start, end);
        BigDecimal operatingExpenses = ZERO;
        List<FinancialSummary.CategoryTotal> expensesByCategory = new ArrayList<>();
        expenses.stream()
                .map(e -> e.getCategory() == null || e.getCategory().isBlank()
                        ? "UNCATEGORISED" : e.getCategory())
                .distinct()
                .forEach(category -> {
                    BigDecimal total = expenses.stream()
                            .filter(e -> category.equals(e.getCategory() == null || e.getCategory().isBlank()
                                    ? "UNCATEGORISED" : e.getCategory()))
                            .map(e -> nz(e.getAmount()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    expensesByCategory.add(new FinancialSummary.CategoryTotal(category, money(total)));
                });
        expensesByCategory.sort(Comparator.comparing(
                (FinancialSummary.CategoryTotal c) -> c.amount()).reversed());

        for (Expense expense : expenses) {
            operatingExpenses = operatingExpenses.add(nz(expense.getAmount()));
        }
        operatingExpenses = money(operatingExpenses);

        BigDecimal netProfitValue = netProfit(grossProfitValue, operatingExpenses);

        // ── Cash ──────────────────────────────────────────────────────────────
        BigDecimal cashReceived = money(paymentRepository.sumCustomerReceipts(start, end));
        BigDecimal cashRefunded = money(paymentRepository.sumCustomerRefunds(start, end));
        BigDecimal cashPaidToSuppliers = money(paymentRepository.sumSupplierPayments(start, end));
        BigDecimal cashFlow = netCashFlow(cashReceived, cashRefunded, cashPaidToSuppliers,
                operatingExpenses);

        // ── Balances ──────────────────────────────────────────────────────────
        BigDecimal receivables = ZERO;
        for (SaleOrder sale : saleOrderRepository.findOutstandingReceivables(end)) {
            receivables = receivables.add(outstanding(sale.getFinalAmount(), sale.getAmountPaid()));
        }
        receivables = money(receivables);

        BigDecimal payables = ZERO;
        for (PurchaseOrder po : purchaseOrderRepository.findOutstandingPayables(end)) {
            payables = payables.add(payable(po.getTotalAmount(), po.getAmountPaid()));
        }
        payables = money(payables);

        // ── Purchases ─────────────────────────────────────────────────────────
        BigDecimal purchaseReceived = money(
                purchaseOrderRepository.sumReceivedBetween(start, end));
        BigDecimal purchaseOrdered = money(
                purchaseOrderRepository.sumOrderedNotReceivedBetween(start, end));

        // ── Inventory ─────────────────────────────────────────────────────────
        List<StockValuationLine> valuationLines = new ArrayList<>();
        for (WarehouseStock stock : warehouseStockRepository.findAllWithStock()) {
            BigDecimal cost = stock.getAverageCost() != null
                    ? stock.getAverageCost()
                    : (stock.getProduct() != null ? stock.getProduct().getPurchasePrice() : null);
            valuationLines.add(new StockValuationLine(stock.getQuantity(), nz(cost)));
        }
        BigDecimal inventoryValueValue = inventoryValue(valuationLines);

        BigDecimal lossUnits = ZERO;
        BigDecimal lossValue = ZERO;
        for (StockMovement movement : stockMovementRepository.findLossesBetween(start, end)) {
            int units = movement.getQuantity() == null ? 0 : Math.abs(movement.getQuantity());
            lossUnits = lossUnits.add(BigDecimal.valueOf(units));
            BigDecimal unitCost = movement.getUnitCost() != null
                    ? movement.getUnitCost()
                    : (movement.getProduct() != null ? movement.getProduct().getPurchasePrice() : null);
            lossValue = lossValue.add(nz(unitCost).multiply(BigDecimal.valueOf(units)));
        }

        long paymentCount = paymentRepository.search(start, end, null, null,
                Payment.STATUS_COMPLETED, org.springframework.data.domain.Pageable.unpaged()).getTotalElements();

        boolean insufficient = sales.isEmpty() && salesReturns.isEmpty()
                && expenses.isEmpty() && purchaseReceived.signum() == 0;

        return new FinancialSummary(
                from, to,
                money(grossSales), money(discountsGiven), money(salesReturnsValue),
                netSalesValue, netSalesTax,
                money(cogs), grossProfitValue, percent(grossProfitValue, netSalesValue),
                operatingExpenses, netProfitValue, percent(netProfitValue, netSalesValue),
                cashReceived, cashPaidToSuppliers, cashRefunded, cashFlow,
                receivables, payables,
                purchaseReceived, purchaseOrdered,
                inventoryValueValue, money(lossUnits), money(lossValue),
                sales.size(), itemsSold, expenses.size(), paymentCount,
                expensesByCategory,
                insufficient,
                "Revenue recognised on sale, cost recognised on receipt; "
                        + "weighted-average costing; tax excluded from revenue; "
                        + "unpaid invoices are revenue but not cash.");
    }

    /** The same period immediately before this one, for comparison. */
    @Transactional(readOnly = true)
    public FinancialSummary previousPeriod(LocalDate from, LocalDate to) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        return summarise(previousFrom, previousTo);
    }

    /**
     * Weighted-average cost per unit for one product, across warehouses.
     *
     * <p>Used by the assistant and the reorder advice, which need a single
     * meaningful cost for a product rather than a per-warehouse split.
     */
    @Transactional(readOnly = true)
    public BigDecimal averageCostOf(Long productId) {
        int totalQuantity = 0;
        BigDecimal totalValue = ZERO;
        for (WarehouseStock stock : warehouseStockRepository.findByProductId(productId)) {
            if (stock.getQuantity() <= 0) {
                continue;
            }
            BigDecimal cost = stock.getAverageCost() != null
                    ? stock.getAverageCost()
                    : (stock.getProduct() != null ? stock.getProduct().getPurchasePrice() : ZERO);
            totalQuantity += stock.getQuantity();
            totalValue = totalValue.add(nz(cost).multiply(BigDecimal.valueOf(stock.getQuantity())));
        }
        if (totalQuantity <= 0) {
            return null;
        }
        return money(totalValue.divide(BigDecimal.valueOf(totalQuantity), 4,
                java.math.RoundingMode.HALF_UP));
    }

    /** Convenience for the reporting layer: the period's net sales only. */
    @Transactional(readOnly = true)
    public BigDecimal netSalesBetween(LocalDate from, LocalDate to) {
        return summarise(from, to).netSales();
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}