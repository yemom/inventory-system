package com.inventory.backend.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The accounting rules, as arithmetic.
 *
 * <p>Everything here is static and side-effect free: no repository, no entity, no
 * security context. The service layer supplies validated records and this class
 * turns them into figures. That separation is the point — the formulas can be
 * unit-tested against hand-computed values, and there is exactly one place in
 * the codebase where "what counts as gross profit" is decided.
 *
 * <h2>The policy, stated once</h2>
 *
 * <p>StockFlow recognises revenue when a sale is recorded and cost when goods
 * are received. It does not defer anything.
 *
 * <pre>
 * lineNet(item)      = unitPrice × quantity − itemDiscount
 *
 * grossSales         = Σ lineNet          over sales in the period
 *                                        that are not CANCELLED and not REFUNDED
 * discountsGiven     = Σ orderDiscount    over the same sales
 * salesTaxCollected  = Σ orderTax         over the same sales
 *
 * netSales           = grossSales − discountsGiven − salesReturns
 * netSalesExTax      = netSales          (tax is never part of revenue; it is
 *                                        collected on behalf of the state and is
 *                                        a liability, not income)
 * cogs               = Σ unitCost × (quantity − quantityReturned)  over the same sales
 *                      − Σ unitCost × quantity  over approved sales returns in the period
 * grossProfit        = netSales − cogs
 *
 * operatingExpenses  = Σ amount  over APPROVED expenses in the period
 * netProfit          = grossProfit − operatingExpenses
 *
 * cashInflows        = Σ amount  over COMPLETED payments, direction IN,  party CUSTOMER
 * cashOutflows       = Σ amount  over COMPLETED payments, direction OUT, party CUSTOMER  (refunds)
 *                    + Σ amount  over COMPLETED payments, direction OUT, party SUPPLIER
 *                    + Σ amount  over APPROVED expenses
 * netCashFlow        = cashInflows − cashOutflows
 *
 * accountsReceivable = Σ (invoiceTotal − amountPaid) over sales that still owe
 * accountsPayable    = Σ (orderTotal   − amountPaid) over RECEIVED purchase orders that still owe
 * </pre>
 *
 * <h2>Decisions that are not obvious</h2>
 *
 * <ul>
 *   <li><b>Sales tax is not revenue.</b> The tax collected on a sale is held on
 *       behalf of the tax authority. Counting it as income overstates profit by
 *       the tax rate on every line, and the amount owed is then invisible.</li>
 *   <li><b>A cancelled or refunded sale contributes nothing</b> — not to gross
 *       sales, not to COGS. A refund restocks the goods, so its cost leaves
 *       COGS too; counting the reversal only on the revenue side would leave the
 *       goods both sold and returned.</li>
 *   <li><b>Transfers are neither sales nor purchases.</b> They move goods
 *       between locations the business already owns. The ledger records them,
 *       and the revenue and purchase figures ignore them entirely.</li>
 *   <li><b>An unpaid invoice is revenue but not cash.</b> It is in netSales and
 *       absent from cashInflows until a COMPLETED payment settles it. This is
 *       the distinction between "we sold" and "we were paid".</li>
 *   <li><b>An un-received purchase order is not a payable and not a cost.</b> It
 *       is stock on order. Only goods actually received create a liability.</li>
 *   <li><b>An approved expense is treated as paid on its transaction date.</b>
 *       The expense module records a voucher when a cost is incurred and paid;
 *       there is no separate accrual and cash-side ledger. This is stated rather
 *       than implied, because it is the one place the cash figure is not read
 *       from a payment record.</li>
 * </ul>
 *
 * <h2>Rounding</h2>
 *
 * <p>Line values are rounded to two decimal places with {@link RoundingMode#HALF_UP}
 * as they are computed, and totals are sums of those rounded lines. Rounding once
 * at the end would let many small lines accumulate a visible drift; rounding each
 * line means the printed invoice and the reported sales always agree, because
 * they are the same numbers.
 */
public final class FinancialCalculations {

    /** Money is carried at two decimal places throughout. */
    public static final int MONEY_SCALE = 2;

    /** Percentages are reported to one decimal place. */
    public static final int PERCENT_SCALE = 1;

    private FinancialCalculations() {
    }

    // ── Rounding ──────────────────────────────────────────────────────────────

    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
 * Adds a list of amounts.
 *
 * <p>Each element is rounded to money scale <em>before</em> being added, not the
 * total afterwards. Three items of 0.005 are three lines on three separate
 * documents, each printed as 0.01; rounding only the total gives 0.02, and the
 * reported figure then disagrees with the records it was computed from. This is
 * the same reason a till rounds each cash entry rather than the day's total.
     */
    public static BigDecimal moneySum(Iterable<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(money(value));
        }
        return money(total);
    }

    /**
     * A percentage, guarded against division by zero.
     *
     * <p>Returns zero rather than null or an exception when the base is zero:
     * "no sales" is a real state, and a margin of zero is what the business
     * should see. Null would push the branch into every caller.
     */
    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        }
        return money(part).multiply(BigDecimal.valueOf(100))
                .divide(whole, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Line revenue: what the customer pays for the goods before order-level discount. */
    public static BigDecimal lineNet(BigDecimal unitPrice, int quantity, BigDecimal lineDiscount) {
        BigDecimal gross = nz(unitPrice).multiply(BigDecimal.valueOf(quantity));
        return money(gross.subtract(nz(lineDiscount)));
    }

    /** Line cost, using the snapshot when one exists and the product cost otherwise. */
    public static BigDecimal lineCost(BigDecimal unitCost, BigDecimal fallbackCost, int quantity) {
        BigDecimal cost = unitCost != null ? unitCost : fallbackCost;
        return money(nz(cost).multiply(BigDecimal.valueOf(quantity)));
    }

    /**
     * Net sales.
     *
     * <p>Excludes tax: tax is collected for a third party, so including it here
     * would make revenue a function of the tax rate and hide the liability.
     */
    public static BigDecimal netSales(BigDecimal grossSales, BigDecimal discountsGiven,
                                      BigDecimal salesReturns) {
        return money(nz(grossSales).subtract(nz(discountsGiven)).subtract(nz(salesReturns)));
    }

    /** Gross profit. Negative is a real answer — it means the goods sold below cost. */
    public static BigDecimal grossProfit(BigDecimal netSales, BigDecimal cogs) {
        return money(nz(netSales).subtract(nz(cogs)));
    }

    /** Net operating profit: what is left after running the business for the period. */
    public static BigDecimal netProfit(BigDecimal grossProfit, BigDecimal operatingExpenses) {
        return money(nz(grossProfit).subtract(nz(operatingExpenses)));
    }

    /** Net cash movement. An expense is treated as paid when it is approved. */
    public static BigDecimal netCashFlow(BigDecimal cashInflows, BigDecimal customerRefunds,
                                         BigDecimal supplierPayments, BigDecimal expenses) {
        BigDecimal outflows = money(nz(customerRefunds)
                .add(nz(supplierPayments))
                .add(nz(expenses)));
        return money(nz(cashInflows).subtract(outflows));
    }

    /**
     * What a customer still owes on one document.
     *
     * <p>Floored at zero: an overpayment is a customer credit, and reporting it
     * as a negative receivable would net against other people's debts and
     * understate what the business is owed.
     */
    public static BigDecimal outstanding(BigDecimal invoiceTotal, BigDecimal amountPaid) {
        BigDecimal due = nz(invoiceTotal).subtract(nz(amountPaid));
        return due.signum() < 0 ? money(BigDecimal.ZERO) : money(due);
    }

    /** What is owed to a supplier on one document, floored at zero for the same reason. */
    public static BigDecimal payable(BigDecimal orderTotal, BigDecimal amountPaid) {
        return outstanding(orderTotal, amountPaid);
    }

    /**
     * Inventory value at weighted-average cost.
     *
     * <p>At cost, not at retail. Retail value would let anyone holding
     * {@code INVENTORY_READ} infer the margin on every line.
     */
    public static BigDecimal inventoryValue(List<StockValuationLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (StockValuationLine line : lines) {
            total = total.add(BigDecimal.valueOf(line.quantity()).multiply(nz(line.unitCost())));
        }
        return money(total);
    }

    /**
     * Signed change between two figures, for period-over-period comparison.
     *
     * @return null when there is no earlier figure to compare against, which is
     *         different from a change of zero and is reported as such
     */
    public static BigDecimal change(BigDecimal current, BigDecimal previous) {
        if (previous == null) {
            return null;
        }
        return money(nz(current).subtract(previous));
    }

    /**
     * Percentage change, with the divide-by-zero case made explicit.
     *
     * <p>Going from 0 to something has no meaningful percentage — it is not
     * infinite, it is a new thing appearing. Returns null so the caller can say
     * "new" instead of printing a fabricated 100% or an infinity.
     */
    public static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null) {
            return null;
        }
        if (previous.signum() == 0) {
            return current == null || current.signum() == 0 ? BigDecimal.ZERO : null;
        }
        return percent(nz(current).subtract(previous), previous.abs());
    }

    /** One line of the inventory valuation. */
    public record StockValuationLine(int quantity, BigDecimal unitCost) {
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}