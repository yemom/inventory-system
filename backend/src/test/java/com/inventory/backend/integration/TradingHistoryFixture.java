package com.inventory.backend.integration;

import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Writes a realistic trading history directly, for tests.
 *
 * <p>Test data. Labelled as such in the type name, in this javadoc, and in the
 * {@link #TEST_DATA_PREFIX} stamped on every order number and payment reference
 * it writes. It lives only under {@code src/test/java} and is not on the
 * application's classpath in a deployment.
 *
 * <h2>Why it writes rows instead of calling the API</h2>
 *
 * <p>Evaluating a forecast needs a season of trading. Going through
 * {@code POST /sales} twenty times a day runs the entire filter chain — JWT
 * parsing, rate limiting, transaction demarcation — for each sale, and the
 * evaluation suite went from seconds to exceeding fifteen minutes. Writing the
 * rows directly produces the same database state in a fraction of the time.
 *
 * <p>What this deliberately does <b>not</b> reproduce is the stock ledger. The
 * orders it writes carry the fields the financial reports read — revenue, the
 * cost frozen on each line, the warehouse, the transaction date — which is what
 * those tests are about. Tests that assert on stock movement go through the API,
 * so that the real code path is the one under test.
 */
@Component
@RequiredArgsConstructor
public class TradingHistoryFixture {

    /** Stamped on every reference this fixture writes, so it can never be mistaken for real data. */
    public static final String TEST_DATA_PREFIX = "TESTDATA";

    private static final BigDecimal UNIT_PRICE = new BigDecimal("50.00");
    private static final BigDecimal UNIT_COST = new BigDecimal("25.00");

    private final SaleOrderRepository saleOrderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * Records {@code days} of trading ending yesterday.
     *
     * @param unitsPerDayBase typical units sold on an ordinary weekday
     * @param seed             fixed, so a failing assertion can be re-run exactly
     * @return how many sales were written
     */
    @Transactional
    public int recordTrading(Long productId, Long warehouseId, int days,
                             int unitsPerDayBase, long seed) {
        Product product = productRepository.findById(productId).orElseThrow();
        Warehouse warehouse = warehouseRepository.findById(warehouseId).orElseThrow();
        User cashier = userRepository.findAll().stream().findFirst().orElse(null);

        Random random = new Random(seed);
        List<Payment> payments = new ArrayList<>();
        int written = 0;

        // Days 1..N ago *and* today (daysAgo = 0). Stopping at yesterday left the
        // series ending on an unrecorded, zero-revenue current day, and a model
        // fitted to that extrapolates a forecast of zero — correct arithmetic on
        // an incomplete series. Tests that assert a forecast tracks the trading
        // level need a final day that is actually a trading day.
        for (int daysAgo = days; daysAgo >= 0; daysAgo--) {
            LocalDateTime at = LocalDateTime.now().minusDays(daysAgo).withHour(10);
            // Weekends trade harder. This is the pattern the anomaly detector has
            // to learn not to flag, so the fixture must produce it.
            int weekday = at.getDayOfWeek().getValue();
            double weekendFactor = (weekday == 6 || weekday == 7) ? 1.5 : 1.0;
            int units = Math.max(1, (int) Math.round(
                    unitsPerDayBase * weekendFactor * (0.85 + random.nextDouble() * 0.3)));

            SaleOrder order = baseOrder(product, warehouse, cashier, at,
                    TEST_DATA_PREFIX + "-SEED-" + daysAgo + "-" + written);
            order.setItems(List.of(line(order, product, units)));
            finishOrder(order);

            SaleOrder saved = saleOrderRepository.save(order);
            payments.add(Payment.builder()
                    .reference(TEST_DATA_PREFIX + "-PAY-" + daysAgo + "-" + written)
                    .orderReference(saved.getOrderNumber())
                    .direction(Payment.DIRECTION_IN)
                    .type(Payment.DIRECTION_IN)
                    .partyType(Payment.PARTY_CUSTOMER)
                    .saleOrder(saved)
                    .amount(saved.getFinalAmount())
                    .paymentMethod("CASH")
                    .status(Payment.STATUS_COMPLETED)
                    .transactionDate(at)
                    .createdBy("test-fixture")
                    .build());
            written++;
        }

        saleOrderRepository.flush();
        paymentRepository.saveAll(payments);
        paymentRepository.flush();
        return written;
    }

    /**
     * Records one extraordinary day on top of ordinary trading.
     *
     * <p>Dated two weeks back, and deliberately not nearer: the detector leaves
     * the most recent week unjudged, because a value there has no following
     * history to distinguish a one-day spike from the start of a trend. A spike
     * written three days ago would sit inside that blind spot and would
     * correctly not be flagged.
     */
    @Transactional
    public SaleOrder recordSpike(Long productId, Long warehouseId, int units) {
        Product product = productRepository.findById(productId).orElseThrow();
        Warehouse warehouse = warehouseRepository.findById(warehouseId).orElseThrow();
        User cashier = userRepository.findAll().stream().findFirst().orElse(null);
        LocalDateTime at = LocalDateTime.now().minusDays(14).withHour(11);

        SaleOrder order = baseOrder(product, warehouse, cashier, at,
                TEST_DATA_PREFIX + "-SPIKE-" + System.nanoTime());
        order.setItems(List.of(line(order, product, units)));
        finishOrder(order);
        return saleOrderRepository.saveAndFlush(order);
    }

    private SaleOrder baseOrder(Product product, Warehouse warehouse, User cashier,
                                LocalDateTime at, String reference) {
        SaleOrder order = new SaleOrder();
        order.setOrderNumber(reference);
        order.setCustomerName("Test Customer");
        order.setWarehouse(warehouse);
        order.setCreatedBy(cashier);
        order.setTransactionDate(at);
        order.setDiscount(BigDecimal.ZERO);
        order.setTax(BigDecimal.ZERO);
        order.setPaymentStatus("PAID");
        order.setPaymentMethod("CASH");
        order.setStatus("PAID");
        order.setIdempotencyKey(reference);
        return order;
    }

    private SaleOrderItem line(SaleOrder order, Product product, int units) {
        SaleOrderItem item = new SaleOrderItem();
        // The back-reference is set explicitly. SaleOrderItem.saleOrder is the
        // owning side of the association, so Hibernate writes it from here rather
        // than from the parent's list; leaving it null produces a row with no
        // parent, which the not-null constraint rejects.
        item.setSaleOrder(order);
        item.setProduct(product);
        item.setQuantity(units);
        item.setUnitPrice(UNIT_PRICE);
        item.setDiscount(BigDecimal.ZERO);
        item.setUnitCost(UNIT_COST);
        item.setQuantityReturned(0);
        item.setSubtotal(UNIT_PRICE.multiply(BigDecimal.valueOf(units)));
        return item;
    }

    private void finishOrder(SaleOrder order) {
        BigDecimal total = order.getItems().get(0).getSubtotal();
        int units = order.getItems().get(0).getQuantity();
        order.setTotalAmount(total);
        order.setFinalAmount(total);
        order.setCostOfGoodsSold(UNIT_COST.multiply(BigDecimal.valueOf(units)));
        order.setAmountPaid(total);
    }
}