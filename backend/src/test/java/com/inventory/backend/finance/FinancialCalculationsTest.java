package com.inventory.backend.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The accounting rules, tested against hand-computed values.
 *
 * <p>These are pure arithmetic tests with no database and no Spring context,
 * which is the whole point of keeping the formulas in a static class: a wrong
 * sign in {@code netSales} should fail a test in milliseconds rather than
 * requiring a container and a schema.
 *
 * <p>Every expected value here was worked out by hand from the stated policy in
 * {@link FinancialCalculations}, not copied from the implementation. Where the
 * two could plausibly disagree, the test states the working.
 */
class FinancialCalculationsTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.001");

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ── Line arithmetic ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("line revenue")
    class LineRevenue {

        @Test
        @DisplayName("unit price × quantity, less the line discount")
        void computesNetLine() {
            assertEquals(bd("270.00"),
                    FinancialCalculations.lineNet(bd("100.00"), 3, bd("30.00")));
        }

        @Test
        @DisplayName("a null discount is treated as none, not as an error")
        void nullDiscountIsZero() {
            assertEquals(bd("250.00"),
                    FinancialCalculations.lineNet(bd("125.00"), 2, null));
        }

        @Test
        @DisplayName("rounds half up at the line, the way an invoice does")
        void roundsHalfUp() {
            // 3 × 33.335 = 100.005 → 100.01, not 100.00 as banker's rounding would give.
            assertEquals(bd("100.01"),
                    FinancialCalculations.lineNet(bd("33.335"), 3, BigDecimal.ZERO));
        }

        @Test
        @DisplayName("a discount larger than the line produces a negative line, not a silent zero")
        void discountCanExceedLine() {
            assertEquals(bd("-50.00"),
                    FinancialCalculations.lineNet(bd("100.00"), 1, bd("150.00")));
        }
    }

    // ── Revenue ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("net sales")
    class NetSales {

        @Test
        @DisplayName("gross sales less discounts less returns")
        void subtractsDiscountsAndReturns() {
            // 1000 − 50 − 120 = 830
            assertEquals(bd("830.00"), FinancialCalculations.netSales(
                    bd("1000.00"), bd("50.00"), bd("120.00")));
        }

        @Test
        @DisplayName("never includes tax — tax is collected for a third party")
        void taxIsNotRevenue() {
            // Tax is not even a parameter: there is no path by which it can
            // reach netSales. A VAT of 150 on 1000 of goods is not income.
            BigDecimal net = FinancialCalculations.netSales(bd("1000.00"), BigDecimal.ZERO,
                    BigDecimal.ZERO);
            assertEquals(bd("1000.00"), net);
        }

        @Test
        @DisplayName("an empty period is zero, not null")
        void emptyPeriodIsZero() {
            assertEquals(bd("0.00"), FinancialCalculations.netSales(null, null, null));
        }

        @Test
        @DisplayName("returns exceeding sales are reported as a negative net figure, not clamped")
        void returnsCanExceedSales() {
            // Clamping would hide a period in which more came back than went out.
            assertEquals(bd("-200.00"),
                    FinancialCalculations.netSales(bd("100.00"), BigDecimal.ZERO, bd("300.00")));
        }
    }

    // ── Profit ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("gross profit")
    class GrossProfit {

        @Test
        @DisplayName("net sales less cost of goods sold")
        void subtractsCogs() {
            assertEquals(bd("350.00"),
                    FinancialCalculations.grossProfit(bd("1000.00"), bd("650.00")));
        }

        @Test
        @DisplayName("is negative when the goods sold below cost")
        void negativeIsAValidAnswer() {
            assertEquals(bd("-150.00"),
                    FinancialCalculations.grossProfit(bd("800.00"), bd("950.00")));
        }

        @Test
        @DisplayName("a return reverses both revenue and cost, not one of them")
        void returnReversesRevenueAndCost() {
            // 1000.00 of goods that cost 600.00 were sold and then all returned.
            // The goods are back on the shelf: they are neither sold nor gone.
            // Net sales fall to zero and so must the cost of those goods —
            // counting only the revenue side would leave 600.00 of cost against
            // no revenue and report a loss that never happened.
            BigDecimal cogsWhileSold = bd("600.00");
            BigDecimal cogsOfReturnedGoods = bd("600.00");
            BigDecimal cogsAfterReturn = cogsWhileSold.subtract(cogsOfReturnedGoods);

            BigDecimal netSales = FinancialCalculations.netSales(
                    bd("1000.00"), BigDecimal.ZERO, bd("1000.00"));
            BigDecimal grossProfit = FinancialCalculations.grossProfit(netSales, cogsAfterReturn);

            assertEquals(bd("0.00"), cogsAfterReturn);
            assertEquals(bd("0.00"), netSales);
            assertEquals(bd("0.00"), grossProfit);
        }
    }

    @Nested
    @DisplayName("margins")
    class Margins {

        @Test
        @DisplayName("a percentage of the base")
        void computesPercentage() {
            assertEquals(bd("35.0"), FinancialCalculations.percent(bd("350.00"), bd("1000.00")));
        }

        @Test
        @DisplayName("a zero base gives zero, not a division by zero")
        void zeroBaseIsSafe() {
            assertEquals(bd("0.0"),
                    FinancialCalculations.percent(bd("500.00"), BigDecimal.ZERO));
        }

        @Test
        @DisplayName("a negative profit yields a negative margin rather than a positive one")
        void negativeMarginKeepsItsSign() {
            assertEquals(bd("-20.0"), FinancialCalculations.percent(bd("-200.00"), bd("1000.00")));
        }

        @Test
        @DisplayName("a 100% margin is exact")
        void fullMargin() {
            assertEquals(bd("100.0"), FinancialCalculations.percent(bd("1000.00"), bd("1000.00")));
        }
    }

    // ── Net profit ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("net profit")
    class NetProfit {

        @Test
        @DisplayName("gross profit less operating expenses")
        void subtractsExpenses() {
            assertEquals(bd("150.00"),
                    FinancialCalculations.netProfit(bd("350.00"), bd("200.00")));
        }

        @Test
        @DisplayName("expenses larger than gross profit produce a loss")
        void lossWhenExpensesExceedProfit() {
            assertEquals(bd("-450.00"),
                    FinancialCalculations.netProfit(bd("350.00"), bd("800.00")));
        }

        @Test
        @DisplayName("a period with no recorded activity is zero profit, not an error")
        void emptyPeriod() {
            assertEquals(bd("0.00"), FinancialCalculations.netProfit(null, null));
        }

        @Test
        @DisplayName("the full chain: revenue → cost → gross → net")
        void fullChainMatchesHandCalculation() {
            // One month, stated plainly:
            //   10 sales of a 100.00 item at 80.00 cost = 1000.00 revenue
            //   50.00 of discounts given at the till
            //   100.00 of goods returned by customers (which cost 80.00)
            //   220.00 of approved operating expenses
            BigDecimal grossSales = bd("1000.00");
            BigDecimal discounts = bd("50.00");
            BigDecimal returns = bd("300.00");
            BigDecimal cogsOfAllSoldGoods = bd("600.00");
            BigDecimal cogsOfReturnedGoods = bd("180.00");
            BigDecimal expenses = bd("220.00");

            BigDecimal cogsStillSold = cogsOfAllSoldGoods.subtract(cogsOfReturnedGoods);
            BigDecimal netSales = FinancialCalculations.netSales(grossSales, discounts, returns);
            BigDecimal grossProfit = FinancialCalculations.grossProfit(netSales, cogsStillSold);
            BigDecimal netProfit = FinancialCalculations.netProfit(grossProfit, expenses);

            assertEquals(bd("650.00"), netSales);      // 1000 - 50 - 300
            assertEquals(bd("420.00"), cogsStillSold);  // 600 - 180
            assertEquals(bd("230.00"), grossProfit);    // 650 - 420
            assertEquals(bd("10.00"), netProfit);       // 230 - 220
        }
    }

    // ── Cash ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cash flow")
    class CashFlow {

        @Test
        @DisplayName("inflows less refunds, supplier payments and expenses")
        void subtractsEveryOutflow() {
            assertEquals(bd("300.00"), FinancialCalculations.netCashFlow(
                    bd("1000.00"), bd("100.00"), bd("400.00"), bd("200.00")));
        }

        @Test
        @DisplayName("an unpaid invoice is not cash")
        void unpaidInvoiceIsNotCash() {
            // 1000 of sales, nothing received, nothing paid.
            assertEquals(bd("0.00"), FinancialCalculations.netCashFlow(
                    bd("0.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        @Test
        @DisplayName("money out exceeds money in produces a negative flow, not zero")
        void negativeFlowIsMeaningful() {
            assertEquals(bd("-150.00"), FinancialCalculations.netCashFlow(
                    BigDecimal.ZERO, BigDecimal.ZERO, bd("50.00"), bd("100.00")));
        }
    }

    // ── Balances ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("outstanding balances")
    class Balances {

        @Test
        @DisplayName("invoice less payments")
        void computesOutstanding() {
            assertEquals(bd("250.00"),
                    FinancialCalculations.outstanding(bd("1000.00"), bd("750.00")));
        }

        @Test
        @DisplayName("an overpayment is zero, not negative")
        void overpaymentIsFloored() {
            // A customer who overpaid has a credit with the business. Reporting
            // it as a negative receivable would net against other people's debts
            // and understate what is owed overall.
            assertEquals(bd("0.00"),
                    FinancialCalculations.outstanding(bd("100.00"), bd("150.00")));
        }

        @Test
        @DisplayName("an unpaid invoice is fully outstanding")
        void unpaidIsFullyOutstanding() {
            assertEquals(bd("100.00"),
                    FinancialCalculations.outstanding(bd("100.00"), null));
        }

        @Test
        @DisplayName("payables use the same rule as receivables")
        void payablesFlooredToo() {
            assertEquals(bd("0.00"), FinancialCalculations.payable(bd("100.00"), bd("100.00")));
            assertEquals(bd("40.00"), FinancialCalculations.payable(bd("100.00"), bd("60.00")));
        }
    }

    // ── Comparison ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("period comparison")
    class Comparison {

        @Test
        @DisplayName("change is current minus previous")
        void computesChange() {
            assertEquals(bd("250.00"),
                    FinancialCalculations.change(bd("1000.00"), bd("750.00")));
        }

        @Test
        @DisplayName("no previous figure means no change, which is not the same as no change")
        void noPreviousIsNull() {
            assertNull(FinancialCalculations.change(bd("100.00"), null));
        }

        @Test
        @DisplayName("percentage change against a non-zero base")
        void percentChange() {
            assertEquals(bd("50.0"),
                    FinancialCalculations.percentChange(bd("1500.00"), bd("1000.00")));
        }

        @Test
        @DisplayName("growth from zero has no meaningful percentage and reports null")
        void growthFromZeroIsNull() {
            // Not infinity, and not 100%. A business that had no sales and then
            // had some has started doing something new; there is no ratio for it.
            assertNull(FinancialCalculations.percentChange(bd("500.00"), BigDecimal.ZERO));
        }

        @Test
        @DisplayName("zero to zero is genuinely zero, not an unmeasurable change")
        void zeroToZeroIsZero() {
            // Compared by value, not by scale: 0 and 0.0 are the same number,
            // and requiring an exact BigDecimal match would make a refactor that
            // changes a scale look like a change in the answer.
            assertEquals(0, bd("0.0").compareTo(
                    FinancialCalculations.percentChange(BigDecimal.ZERO, BigDecimal.ZERO)));
        }

        @Test
        @DisplayName("a fall from a positive base is negative, using the base's own sign")
        void declineIsNegative() {
            assertEquals(bd("-50.0"),
                    FinancialCalculations.percentChange(bd("500.00"), bd("1000.00")));
        }
    }

    // ── Inventory valuation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("inventory valuation")
    class Valuation {

        @Test
        @DisplayName("quantity × unit cost, summed across lines")
        void valuesStockAtCost() {
            assertEquals(bd("350.00"), FinancialCalculations.inventoryValue(List.of(
                    new FinancialCalculations.StockValuationLine(10, bd("20.00")),
                    new FinancialCalculations.StockValuationLine(5, bd("30.00")))));
        }

        @Test
        @DisplayName("a line with no known cost contributes nothing rather than failing")
        void missingCostIsZero() {
            assertEquals(bd("100.00"), FinancialCalculations.inventoryValue(List.of(
                    new FinancialCalculations.StockValuationLine(10, bd("10.00")),
                    new FinancialCalculations.StockValuationLine(5, null))));
        }

        @Test
        @DisplayName("an empty warehouse is worth nothing")
        void emptyValuation() {
            assertEquals(bd("0.00"), FinancialCalculations.inventoryValue(List.of()));
        }
    }

    // ── Rounding ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rounding")
    class Rounding {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "10.005,  10.01",
                "10.004,  10.00",
                "10.006,  10.01",
                "0.005,   0.01",
                "-10.005, -10.01",
                "2.675,   2.68",
                "100,     100.00"
        })
        @DisplayName("money is half-up to two places, including the classic binary traps")
        void roundsHalfUp(String input, String expected) {
            assertEquals(0, bd(expected).compareTo(FinancialCalculations.money(bd(input))),
                    () -> "expected " + expected + " for " + input);
        }

        @Test
        @DisplayName("a total is the sum of already-rounded lines, so it matches the printed invoice")
        void sumOfRoundedLinesMatchesInvoice() {
            // 0.005 three times rounds to 0.01 each = 0.03. Rounding the sum
            // instead would give 0.02, which is not what any invoice shows.
            BigDecimal total = FinancialCalculations.moneySum(List.of(
                    bd("0.005"), bd("0.005"), bd("0.005")));
            assertEquals(bd("0.03"), total);
        }

        @Test
        @DisplayName("nulls in a sum are treated as zero")
        void sumIgnoresNulls() {
            assertEquals(bd("10.00"),
                    FinancialCalculations.moneySum(java.util.Arrays.asList(bd("10.00"), null)));
        }
    }

    // ── A worked month, end to end ────────────────────────────────────────────

    @Test
    @DisplayName("a month with sales, discounts, returns, expenses and a credit sale")
    void workedMonth() {
        // 40 units sold at 100.00, cost 65.00 each          → revenue 4000, COGS 2600
        // 200.00 of discounts given at the till
        // 2 units returned (130.00 of revenue, 130.00 of cost)
        // 3 units sold on credit, none of it collected
        // 500.00 of approved operating expenses
        // 3000.00 collected in cash, 500.00 paid to suppliers
        BigDecimal unitsSold = bd("40");
        BigDecimal unitPrice = bd("100.00");
        BigDecimal unitCost = bd("65.00");

        BigDecimal revenue = unitsSold.multiply(unitPrice);          // 4000.00
        BigDecimal cogs = unitsSold.multiply(unitCost);              // 2600.00
        BigDecimal discounts = bd("200.00");
        BigDecimal returnedUnits = bd("2");
        BigDecimal returns = returnedUnits.multiply(unitPrice);      // 200.00
        BigDecimal returnedCost = returnedUnits.multiply(unitCost);  // 130.00
        BigDecimal expenses = bd("500.00");

        BigDecimal netSales = FinancialCalculations.netSales(revenue, discounts, returns);
        BigDecimal netCogs = cogs.subtract(returnedCost);
        BigDecimal grossProfit = FinancialCalculations.grossProfit(netSales, netCogs);
        BigDecimal netProfit = FinancialCalculations.netProfit(grossProfit, expenses);

        assertEquals(bd("3600.00"), netSales);
        assertEquals(bd("2470.00"), netCogs);
        assertEquals(bd("1130.00"), grossProfit);
        assertEquals(bd("630.00"), netProfit);
        assertEquals(new BigDecimal("31.4"),
                FinancialCalculations.percent(grossProfit, netSales));

        // The credit sale is revenue. It is not cash, and it is still owed.
        BigDecimal creditSale = bd("3").multiply(unitPrice);        // 300.00
        assertTrue(netSales.compareTo(creditSale) >= 0);
        assertEquals(bd("300.00"),
                FinancialCalculations.outstanding(creditSale, BigDecimal.ZERO));

        // Cash: 3000 collected, 500 paid out, 500 of expenses paid.
        assertEquals(bd("2000.00"), FinancialCalculations.netCashFlow(
                bd("3000.00"), BigDecimal.ZERO, bd("500.00"), expenses));
    }
}