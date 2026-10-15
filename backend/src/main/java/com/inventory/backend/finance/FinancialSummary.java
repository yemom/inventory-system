package com.inventory.backend.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every financial figure for one period, produced in one place.
 *
 * <p>The frontend previously computed these independently — the dashboard did
 * {@code revenue − purchases} and called it gross profit, while the P&L page
 * used {@code purchasePrice × quantity sold}. Both could be wrong, and they were
 * wrong by different amounts. There is now one record that the dashboard, the
 * reports, the assistant and the AI insights all read, so they cannot disagree.
 */
public record FinancialSummary(

        /** Inclusive start of the period. */
        LocalDate from,
        /** Inclusive end of the period. */
        LocalDate to,

        // ── Revenue ───────────────────────────────────────────────────────────
        /** Sum of line revenue before order-level discount, excluding tax. */
        BigDecimal grossSales,
        /** Order-level discounts given to customers. */
        BigDecimal discountsGiven,
        /** Value of goods returned by customers in the period. */
        BigDecimal salesReturns,
        /** grossSales − discountsGiven − salesReturns. Excludes tax. */
        BigDecimal netSales,
        /** Tax collected on sales, net of tax refunded. A liability, not income. */
        BigDecimal netSalesTax,

        // ── Cost and profit ───────────────────────────────────────────────────
        /** Cost of the goods that were sold or returned, at the cost they carried. */
        BigDecimal costOfGoodsSold,
        BigDecimal grossProfit,
        BigDecimal grossMarginPercent,
        BigDecimal operatingExpenses,
        BigDecimal netProfit,
        BigDecimal netMarginPercent,

        // ── Cash ──────────────────────────────────────────────────────────────
        /** Money actually received from customers. */
        BigDecimal cashReceived,
        /** Money actually paid to suppliers. */
        BigDecimal cashPaidToSuppliers,
        /** Money refunded to customers. */
        BigDecimal cashRefunded,
        BigDecimal netCashFlow,

        // ── Balances (as at the end of the period) ────────────────────────────
        /** What customers owe the business. */
        BigDecimal accountsReceivable,
        /** What the business owes suppliers, for goods actually received. */
        BigDecimal accountsPayable,

        // ── Purchases ─────────────────────────────────────────────────────────
        /** Cost of goods actually received in the period. */
        BigDecimal purchaseCostReceived,
        /** Value of goods ordered but not yet delivered. Not a payable yet. */
        BigDecimal purchaseCostOrderedNotReceived,

        // ── Inventory ─────────────────────────────────────────────────────────
        /** Stock on hand at weighted-average cost. */
        BigDecimal inventoryValue,
        /** Units lost to damage, expiry or unaccounted difference. */
        BigDecimal stockLossUnits,
        /** Value of those losses. */
        BigDecimal stockLossValue,

        // ── Counts ────────────────────────────────────────────────────────────
        long salesCount,
        long itemsSold,
        long expenseCount,
        long paymentCount,

        /** Approved expenses grouped by category. */
        List<CategoryTotal> expensesByCategory,

        /**
         * Whether the period contains too little recorded history for the
         * figures to be meaningful. Set when there are no sales at all, so an
         * AI explanation says "no data" instead of analysing a zero.
         */
        boolean insufficientData,

        /** How this summary was assembled, in words, for the report header. */
        String basis) {

    /** An expense category and its total. */
    public record CategoryTotal(String category, BigDecimal amount) {
    }
}