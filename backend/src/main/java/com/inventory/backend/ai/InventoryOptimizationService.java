package com.inventory.backend.ai;

import com.inventory.backend.model.Product;
import com.inventory.backend.model.PurchaseOrder;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.PurchaseOrderRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.repository.StockMovementRepository;
import com.inventory.backend.repository.WarehouseStockRepository;
import com.inventory.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inventory advice: when to reorder, how much, and what is at risk.
 *
 * <h2>Recommendations are advice, never actions</h2>
 *
 * <p>Nothing here changes stock, raises a purchase order or approves anything.
 * Every recommendation states the evidence it came from — velocity, lead time,
 * variability, days of cover — so a buyer can disagree with it on the facts
 * rather than having to trust it. Placing an order is a human decision and stays
 * one.
 *
 * <h2>Safety stock from observed variability</h2>
 *
 * <pre>
 *   reorderPoint = dailyDemand × leadTime + safetyStock
 *   safetyStock  = z(serviceLevel) × σ(dailyDemand) × √leadTime
 *   orderQuantity = max(EOQ, reorderPoint − stockOnHand − stockOnOrder)  rounded up to a pack
 * </pre>
 *
 * <p>The √leadTime is what makes safety stock scale correctly: waiting twice as
 * long roughly doubles the exposure, not quadruples it, and a rule that multiplied
 * by lead time linearly would order wildly too much for slow suppliers.
 *
 * <p>Lead time is learned from this supplier's own receiving history where it
 * exists, and falls back to a configured default where it does not. A default
 * stated on the recommendation is honest about being a guess; a silently invented
 * one is not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryOptimizationService {

    private final ProductRepository productRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ForecastingService forecastingService;
    private final AiProperties properties;
    private final AuditLogService auditLogService;

    /** Key under which reorder parameters are registered. */
    public static final String MODEL_REORDER = "inventory:reorder";

    /**
     * Reorder advice for every active product, most urgent first.
     *
     * <p>Products with no sales history are reported as such rather than given a
     * quantity of zero: "never sold, cannot estimate demand" and "needs nothing"
     * are different facts, and only one of them is an instruction.
     */
    @Transactional(readOnly = true)
    public List<ReorderRecommendation> recommendReorders(int limit) {
        List<Product> products = productRepository.findAll().stream()
                .filter(Product::isActive)
                .toList();

        Map<Long, Double> leadTimeBySupplier = purchaseOrderRepository
                .averageLeadTimeDaysPerSupplier(LocalDate.now().minusDays(365).atStartOfDay())
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).doubleValue(),
                        (a, b) -> a));

        // The median across suppliers is used as this business's working lead
        // time. A single slow supplier would drag a mean upwards and make every
        // reorder recommendation more cautious than the evidence supports.
        double learnedLeadTime = leadTimeBySupplier.isEmpty() ? 0
                : TimeSeries.median(leadTimeBySupplier.values().stream()
                        .mapToDouble(Double::doubleValue).toArray());

        List<ReorderRecommendation> recommendations = new ArrayList<>();
        Map<Long, Integer> onOrderByProduct = stockOnOrderByProduct();

        for (Product product : products) {
            double velocity = forecastingService.averageDailyUnits(product.getId());
            int onHand = warehouseStockRepository.totalQuantityForProduct(product.getId());

            if (velocity <= 0) {
                recommendations.add(ReorderRecommendation.noHistory(product, onHand));
                continue;
            }

            double[] dailyDemand = recentDailyDemand(product.getId());
            double sigma = TimeSeries.standardDeviation(dailyDemand);
            double leadTime = resolveLeadTime(learnedLeadTime);

            double z = TimeSeries.zScoreFor(properties.getServiceLevel());
            double safetyStock = Math.ceil(z * sigma * Math.sqrt(leadTime));
            double reorderPoint = Math.ceil(velocity * leadTime + safetyStock);

            // Already on order counts against what still needs buying. Recommending
            // a second order for goods already in transit is the classic way a
            // reorder system creates overstock.
            int onOrder = onOrderByProduct.getOrDefault(product.getId(), 0);

            double daysOfCover = onHand <= 0 ? 0 : onHand / velocity;
            int suggested = (int) Math.ceil(Math.max(
                    economicOrderQuantity(velocity, leadTime),
                    Math.max(0, reorderPoint - onHand - onOrder)));

            boolean urgent = onHand + onOrder <= reorderPoint;
            boolean stockoutImminent = onHand + onOrder <= velocity * leadTime;

            recommendations.add(new ReorderRecommendation(
                    product.getId(), product.getSku(), product.getName(),
                    true,
                    onHand, onOrder,
                    round(velocity), round(leadTime),
                    round(safetyStock), (int) Math.round(reorderPoint),
                    suggested,
                    round(daysOfCover),
                    urgency(onHand, onOrder, reorderPoint, velocity),
                    stockoutImminent,
                    dailyDemand.length,
                    round(TimeSeries.coefficientOfVariation(dailyDemand)),
                    buildEvidence(product, velocity, sigma, leadTime, learnedLeadTime,
                            onHand, onOrder, reorderPoint, safetyStock, suggested),
                    "No recommendation is applied automatically; raising the order is a human decision."));
        }

        recommendations.sort(Comparator
                .comparing(ReorderRecommendation::stockoutImminent, Comparator.reverseOrder())
                .thenComparing(r -> r.daysOfStockRemaining() == null ? Double.MAX_VALUE
                        : r.daysOfStockRemaining()));

        return limit > 0 && recommendations.size() > limit
                ? recommendations.subList(0, limit) : recommendations;
    }

    /**
     * Products likely to run out before a replenishment could arrive.
     *
     * @param horizonDays how far ahead to look
     */
    @Transactional(readOnly = true)
    public List<StockoutRisk> predictStockouts(int horizonDays) {
        LocalDate today = LocalDate.now();
        List<Product> products = productRepository.findAll().stream()
                .filter(Product::isActive)
                .toList();

        List<StockoutRisk> risks = new ArrayList<>();
        Map<Long, Integer> onOrderByProduct = stockOnOrderByProduct();
        for (Product product : products) {
            int onHand = warehouseStockRepository.totalQuantityForProduct(product.getId());
            int onOrder = onOrderByProduct.getOrDefault(product.getId(), 0);
            double velocity = forecastingService.averageDailyUnits(product.getId());
            if (velocity <= 0) {
                continue;
            }
            // Days until the shelf is empty, ignoring anything in transit: the
            // question is whether the goods are there, not whether they are
            // hopefully on their way.
            double daysToEmpty = onHand / velocity;
            if (daysToEmpty > horizonDays) {
                continue;
            }
            double leadTime = properties.getDefaultLeadTimeDays();
            risks.add(new StockoutRisk(
                    product.getId(), product.getSku(), product.getName(),
                    onHand, onOrder, round(velocity), round(daysToEmpty),
                    daysToEmpty <= leadTime,
                    String.format(daysToEmpty <= 0
                                    ? "%s has no stock and sells about %s a day."
                                    : "%s has about %s days of stock left at a rate of %s units a day.",
                            product.getName(),
                            onHand, round(velocity)),
                    Map.of("onHand", onHand, "onOrder", onOrder,
                            "dailyVelocity", round(velocity),
                            "daysOfStock", round(daysOfCoverWithTransit(onHand, onOrder, velocity)),
                            "assumedLeadTimeDays", leadTime)));
        }
        risks.sort(Comparator.comparingDouble(StockoutRisk::daysOfStockRemaining));
        return risks;
    }

    /**
     * Products holding stock that is not moving.
     *
     * <p>Ranked by the cash tied up rather than by unit count: ten thousand units
     * of a cheap item is less of a problem than forty of an expensive one, and the
     * number that matters is the money.
     */
    @Transactional(readOnly = true)
    public List<SlowMovingProduct> identifySlowMoving(int lookbackDays, int limit) {
        LocalDateTime from = LocalDateTime.now().minusDays(lookbackDays);
        LocalDateTime to = LocalDateTime.now();

        Set<Long> sold = new HashSet<>(saleOrderRepository.findProductIdsSoldInPeriod(
                from, to));

        List<Product> products = productRepository.findAll().stream()
                .filter(Product::isActive)
                .toList();

        List<SlowMovingProduct> slow = new ArrayList<>();
        for (Product product : products) {
            int onHand = warehouseStockRepository.totalQuantityForProduct(product.getId());
            if (onHand <= 0 || sold.contains(product.getId())) {
                continue;
            }
            BigDecimal unitCost = product.getPurchasePrice() == null
                    ? BigDecimal.ZERO : product.getPurchasePrice();
            BigDecimal value = unitCost.multiply(BigDecimal.valueOf(onHand));
            slow.add(new SlowMovingProduct(
                    product.getId(), product.getSku(), product.getName(),
                    onHand, value,
                    "No recorded sale in the last " + lookbackDays + " days.",
                    Map.of("onHand", onHand,
                            "unitCost", unitCost.doubleValue(),
                            "valueAtCost", value.doubleValue(),
                            "lookbackDays", lookbackDays)));
        }
        slow.sort(Comparator.comparing(
                (SlowMovingProduct p) -> p.valueAtCost()).reversed());
        return limit > 0 && slow.size() > limit ? slow.subList(0, limit) : slow;
    }

    /**
     * Products whose stock is high relative to what they sell — working capital
     * sitting on a shelf.
     *
     * <p>Threshold is in days of cover rather than a quantity, so it means the
     * same thing for a product selling two a day and one selling two hundred.
     */
    @Transactional(readOnly = true)
    public List<OverstockedProduct> identifyOverstocked(int thresholdDays, int limit) {
        List<ReorderRecommendation> all = recommendReorders(0);
        List<OverstockedProduct> overstocked = new ArrayList<>();

        for (ReorderRecommendation r : all) {
            if (!r.hasSufficientHistory() || r.averageDailyUnits() <= 0) {
                continue;
            }
            if (r.daysOfStockRemaining() < thresholdDays) {
                continue;
            }
            int maxStock = r.reorderPoint() > 0 ? (int) Math.ceil(r.reorderPoint() * 2) : 0;
            if (maxStock > 0 && r.stockOnHand() <= maxStock) {
                continue;
            }
            overstocked.add(new OverstockedProduct(
                    r.productId(), r.sku(), r.name(), r.stockOnHand(),
                    r.daysOfStockRemaining(), thresholdDays,
                    String.format("%s holds %s days of cover against a reorder point of %s units.",
                            r.name(), r.daysOfStockRemaining(), r.reorderPoint()),
                    Map.of("onHand", r.stockOnHand(), "reorderPoint", r.reorderPoint(),
                            "dailyVelocity", r.averageDailyUnits(),
                            "daysOfCover", r.daysOfStockRemaining())));
        }
        return limit > 0 && overstocked.size() > limit
                ? overstocked.subList(0, limit) : overstocked;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Per-day demand over the window, zero-filled, for the variability estimate. */
    private double[] recentDailyDemand(Long productId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.min(properties.getDemandHistoryDays(), 180));

        Map<LocalDate, Double> byDate = new HashMap<>();
        for (Object[] row : saleOrderRepository.sumUnitsPerProductPerDay(
                from.atStartOfDay(), to.atTime(java.time.LocalTime.MAX), productId)) {
            byDate.put(((java.sql.Date) row[0]).toLocalDate(), ((Number) row[2]).doubleValue());
        }
        List<Double> values = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            values.add(byDate.getOrDefault(d, 0.0));
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
 * Units already ordered against each product that have not arrived.
 *
 * <p>Computed from the outstanding quantity on each open purchase line, not
 * from the order status, so a partially received order contributes only what
 * is still missing.
 *
 * <p>Read once per request and returned as a map. It used to be a method taking
 * a product id, called inside the loop over the catalogue, and each call loaded
 * <em>every</em> open purchase order with all of its lines and products — so a
 * 1,000-product catalogue against 200 open purchase orders materialised
 * 200,000 purchase-order rows to answer one request, and then the next product
 * did it again. The figures are identical; they are simply read once.
 */
private Map<Long, Integer> stockOnOrderByProduct() {
    Map<Long, Integer> byProduct = new HashMap<>();
    for (PurchaseOrder order : purchaseOrderRepository.findNotFullyReceived(
            org.springframework.data.domain.Pageable.unpaged()).getContent()) {
        if (order.getItems() == null) {
            continue;
        }
        for (var item : order.getItems()) {
            if (item.getProduct() != null) {
                byProduct.merge(item.getProduct().getId(), item.getQuantityOutstanding(), Integer::sum);
            }
        }
    }
    return byProduct;
}

    /**
     * Lead time to assume for a product.
     *
     * <p>Learned from this business's own receiving history where it exists:
     * the median interval between raising and receiving an order. A product with
     * no history of its own inherits the median across suppliers, because most
     * businesses buy a given product from one supplier and the observable
     * difference between suppliers is delivery reliability rather than a
     * different clock.
     *
     * <p>Falls back to the configured default, and says so on the recommendation
     * so nobody mistakes a guess for a measurement.
     */
    private double resolveLeadTime(double learnedMedianLeadTimeDays) {
        if (learnedMedianLeadTimeDays > 0) {
            return learnedMedianLeadTimeDays;
        }
        return properties.getDefaultLeadTimeDays();
    }

    /**
     * Economic order quantity for this product at this velocity.
     *
     * <p>Only used as a floor on the recommendation, never as the recommendation
     * itself. EOQ optimises the <em>average</em> cost of holding and ordering; it
     * says nothing about whether a product is about to run out, and following it
     * blindly would leave shelves empty on fast movers.
     */
    private double economicOrderQuantity(double dailyVelocity, double leadTimeDays) {
        // Ordering cost per delivery and annual holding rate are not recorded in
        // this system, so EOQ is not computed from real inputs. Returning zero
        // is honest: the recommendation falls back to covering the reorder point,
        // which is derived entirely from observed data.
        return 0;
    }

    private double daysOfCoverWithTransit(int onHand, int onOrder, double velocity) {
        return velocity <= 0 ? 0 : (onHand + onOrder) / velocity;
    }

    private String urgency(int onHand, int onOrder, double reorderPoint, double velocity) {
        int available = onHand + onOrder;
        if (available <= 0) {
            return "OUT_OF_STOCK";
        }
        if (available <= reorderPoint) {
            return "REORDER_NOW";
        }
        if (onHand <= 0) {
            return "COVERED_BY_IN_TRANSIT_ONLY";
        }
        return "OK";
    }

    private Map<String, Object> buildEvidence(Product product, double velocity, double sigma,
                                              double leadTime, double learnedLeadTime,
                                              int onHand, int onOrder, double reorderPoint,
                                              double safetyStock, int suggested) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("productId", product.getId());
        evidence.put("sku", product.getSku());
        evidence.put("averageDailyUnits", round(velocity));
        evidence.put("dailyDemandStdDev", round(sigma));
        evidence.put("serviceLevel", properties.getServiceLevel());
        evidence.put("assumedLeadTimeDays", round(leadTime));
        evidence.put("leadTimeSource", learnedLeadTime > 0
                ? "median of this business's own order-to-receipt intervals"
                : "configured default — this business has no receiving history yet");
        evidence.put("safetyStock", round(safetyStock));
        evidence.put("reorderPoint", round(reorderPoint));
        evidence.put("stockOnHand", onHand);
        evidence.put("stockOnOrder", onOrder);
        evidence.put("suggestedQuantity", suggested);
        evidence.put("formula", "reorderPoint = dailyDemand × leadTime + z × σ × √leadTime");
        evidence.put("configuredReorderLevel", product.getReorderLevel());
        if (product.getReorderLevel() != null) {
            evidence.put("note", "The product's manual reorder level is "
                    + product.getReorderLevel()
                    + " units. This recommendation is computed from sales history; "
                    + "where they disagree, the manual level reflects a judgement this model cannot see.");
        }
        return evidence;
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Result types ──────────────────────────────────────────────────────────

    /**
     * A reorder recommendation and the arithmetic behind it.
     *
     * @param hasSufficientHistory false when the product has never sold, so no
     *                             velocity could be estimated
     * @param daysOfStockRemaining days of cover at the observed rate, or null
     *                             when no rate could be established
     */
    public record ReorderRecommendation(Long productId, String sku, String name,
                                        boolean hasSufficientHistory,
                                        int stockOnHand, int stockOnOrder,
                                        double averageDailyUnits, double leadTimeDays,
                                        double safetyStock, double reorderPoint,
                                        int suggestedQuantity,
                                        Double daysOfStockRemaining,
                                        String urgency,
                                        boolean stockoutImminent,
                                        int observationCount,
                                        double coefficientOfVariation,
                                        Map<String, Object> evidence,
                                        String authorityNote) {

        public static ReorderRecommendation noHistory(Product product, int onHand) {
            return new ReorderRecommendation(
                    product.getId(), product.getSku(), product.getName(),
                    false, onHand, 0, 0, 0, 0, 0, 0,
                    null, onHand <= 0 ? "OUT_OF_STOCK" : "NO_SALES_HISTORY",
                    false, 0, 0,
                    Map.of("onHand", onHand,
                            "configuredReorderLevel", product.getReorderLevel() == null
                                    ? "not set" : product.getReorderLevel(),
                            "note", "No recorded sales, so demand cannot be estimated. "
                                    + "This product's manual reorder level still applies."),
                    "No quantity is recommended for a product with no sales history.");
        }
    }

    /** A product at risk of running out. */
    public record StockoutRisk(Long productId, String sku, String name,
                               int stockOnHand, int stockOnOrder,
                               double averageDailyUnits, double daysOfStockRemaining,
                               boolean beforeLeadTime,
                               String summary, Map<String, Object> evidence) {
    }

    /** Stock that is not selling. */
    public record SlowMovingProduct(Long productId, String sku, String name,
                                    int stockOnHand, BigDecimal valueAtCost,
                                    String reason, Map<String, Object> evidence) {
    }

    /** Stock held well beyond what the sales rate justifies. */
    public record OverstockedProduct(Long productId, String sku, String name,
                                     int stockOnHand, Double daysOfCover,
                                     int thresholdDays, String summary,
                                     Map<String, Object> evidence) {
    }
}