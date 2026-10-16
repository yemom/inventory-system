package com.inventory.backend.service;

import com.inventory.backend.model.Payment;
import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.model.SaleOrderItem;
import com.inventory.backend.model.User;
import com.inventory.backend.repository.PaymentRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sales figures for a dashboard, scoped to whoever is asking.
 *
 * <p>Built for the staff dashboard, which has to answer "how did today go" in
 * one request rather than by loading a ledger and totalling it in the browser.
 * Two rules govern everything here:
 *
 * <ul>
 *   <li><b>The scope is decided by the server.</b> A caller holding
 *       {@code SALES_REPORT_VIEW} sees the business; anyone else sees only the
 *       orders they recorded. There is no parameter that widens this — the
 *       browser cannot ask for somebody else's takings, because there is nothing
 *       to ask with. This mirrors the rule {@link SaleOrderService} applies to
 *       the sales list.</li>
 *   <li><b>An order is not a sale.</b> Cancelled and voided orders are counted
 *       and reported separately rather than quietly excluded, so a cashier can
 *       see that a transaction happened and why it is not in their takings.
 *       Revenue is net of the order discount and of anything returned.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SalesSummaryService {

    private final SaleOrderRepository saleOrderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Totals for a period, for the caller and as much of the business as they
     * are entitled to see.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summarise(LocalDate from, LocalDate to, Long warehouseId) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end;
        User actor = currentUser();

        // null sellerId = every seller. Anything else is this caller's own.
        Long sellerScope = canSeeEveryonesSales() ? null : (actor == null ? -1L : actor.getId());

        List<SaleOrder> orders = saleOrderRepository.findSalesForPeriod(
                start.atStartOfDay(), end.atTime(LocalTime.MAX),
                null, warehouseId, sellerScope, null, true);

        Map<Long, BigDecimal> collectedByOrder = collectedByOrder(orders);

        int ordersCreated = orders.size();
        int cancelled = 0;
        int voided = 0;
        int pending = 0;
        int completed = 0;
        int unitsSold = 0;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal returns = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        Map<String, BigDecimal> byMethod = new TreeMap<>();
        Map<Integer, BigDecimal> byHour = new TreeMap<>();

        for (SaleOrder order : orders) {
            if (order == null) {
                continue;
            }
            String status = order.getStatus() == null ? "" : order.getStatus();
            if ("CANCELLED".equalsIgnoreCase(status)) {
                cancelled++;
                continue;
            }

            BigDecimal lineRevenue = BigDecimal.ZERO;
            int orderUnits = 0;
            BigDecimal returnedValue = BigDecimal.ZERO;
            for (SaleOrderItem item : safe(order.getItems())) {
                int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                int returned = item.getQuantityReturned() == null ? 0 : item.getQuantityReturned();
                int netQuantity = Math.max(0, quantity - returned);
                lineRevenue = lineRevenue.add(nz(item.getSubtotal()));
                orderUnits += netQuantity;
                BigDecimal unitPrice = nz(item.getUnitPrice());
                returnedValue = returnedValue.add(unitPrice.multiply(BigDecimal.valueOf(returned)));
            }

            BigDecimal discount = nz(order.getDiscount());
            BigDecimal net = lineRevenue.subtract(discount);
            BigDecimal paid = collectedByOrder.getOrDefault(order.getId(), BigDecimal.ZERO);

            // A voided order was reversed: the goods came back, so it is not a
            // sale — but it is still a transaction the cashier took, and it is
            // reported on its own line rather than disappearing.
            if (isVoided(order)) {
                voided++;
                returns = returns.add(net);
                continue;
            }

            // Pending orders are not completed sales. Any collected deposit or
            // payment is accounted for, but the order is reported as pending.
            if ("PENDING".equalsIgnoreCase(status)) {
                pending++;
                collected = collected.add(paid);
                outstanding = outstanding.add(net.subtract(paid).max(BigDecimal.ZERO));
                continue;
            }

            completed++;
            unitsSold += orderUnits;
            gross = gross.add(lineRevenue);
            discounts = discounts.add(discount);
            tax = tax.add(nz(order.getTax()));
            returns = returns.add(returnedValue);
            collected = collected.add(paid);
            outstanding = outstanding.add(net.subtract(paid).max(BigDecimal.ZERO));

            String method = order.getPaymentMethod() == null
                    ? "UNSPECIFIED" : order.getPaymentMethod();
            byMethod.merge(method, net, BigDecimal::add);

            int hour = order.getCreatedAt() == null ? -1 : order.getCreatedAt().getHour();
            if (hour >= 0) {
                byHour.merge(hour, net, BigDecimal::add);
            }
        }

        BigDecimal netSales = gross.subtract(discounts).subtract(returns);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", start.toString());
        out.put("to", end.toString());
        out.put("scope", sellerScope == null ? "ALL_SALES" : "OWN_SALES");
        out.put("cashierName", actor != null ? actor.getFullName() : null);
        out.put("branch", actor != null ? actor.getBranch() : null);
        out.put("ordersCreated", ordersCreated);
        out.put("completedSales", completed);
        out.put("pendingOrders", pending);
        out.put("cancelledOrders", cancelled);
        out.put("voidedOrders", voided);
        out.put("grossSales", gross);
        out.put("discounts", discounts);
        out.put("tax", tax);
        out.put("returns", returns);
        out.put("netSales", netSales);
        out.put("amountCollected", collected);
        out.put("outstanding", outstanding);
        out.put("unitsSold", unitsSold);
        out.put("averageOrderValue", completed == 0
                ? BigDecimal.ZERO
                : netSales.divide(BigDecimal.valueOf(completed), 2, java.math.RoundingMode.HALF_UP));
        out.put("paymentMethods", byMethod);
        out.put("hourlySales", byHour);
        out.put("basis", "Revenue net of discount and of returned units, from the orders themselves. "
                + "Cancelled, voided, and pending orders are reported separately and pending orders are not counted as completed sales.");
        return out;
    }

    /** Collected cash per order, in one query for the whole period. */
    private Map<Long, BigDecimal> collectedByOrder(List<SaleOrder> orders) {
        Map<Long, BigDecimal> collected = new LinkedHashMap<>();
        if (orders.isEmpty()) {
            return collected;
        }
        List<Long> ids = orders.stream()
                .filter(o -> o != null && o.getId() != null)
                .map(SaleOrder::getId)
                .toList();
        if (ids.isEmpty()) {
            return collected;
        }
        for (Payment payment : paymentRepository.findBySaleOrderIdIn(ids)) {
            if (payment.getSaleOrder() == null || payment.getSaleOrder().getId() == null) {
                continue;
            }
            // Only completed payments are cash in hand. A pending or failed
            // payment is an intention, not money.
            if (Payment.STATUS_COMPLETED.equals(payment.getStatus())) {
                collected.merge(payment.getSaleOrder().getId(), nz(payment.getAmount()), BigDecimal::add);
            }
        }
        return collected;
    }

    private boolean isVoided(SaleOrder order) {
        return order.getVoidStatus() != null && "APPROVED".equals(order.getVoidStatus());
    }

    private boolean canSeeEveryonesSales() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> "SALES_REPORT_VIEW".equals(granted.getAuthority())
                        || "REPORT_VIEW".equals(granted.getAuthority()));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            return null;
        }
        return principal.getUser();
    }

    private static List<SaleOrderItem> safe(List<SaleOrderItem> items) {
        return items == null ? List.of() : items;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}