package com.inventory.backend.service;

import com.inventory.backend.dto.SaleOrderDTO;
import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.model.SaleOrderItem;
import com.inventory.backend.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesReportService {

    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderService saleOrderService;
    private final jakarta.persistence.EntityManager em;

    @Transactional(readOnly = true)
    public Map<String, Object> salesReport(LocalDateTime from, LocalDateTime to,
                                           Long customerId, Long productId,
                                           Long sellerId, String paymentMethod, String status) {
        StringBuilder jpql = new StringBuilder("""
                SELECT DISTINCT s.id FROM SaleOrder s
                LEFT JOIN s.items i
                LEFT JOIN i.product p
                LEFT JOIN s.customer c
                LEFT JOIN s.createdBy u
                WHERE 1 = 1
                """);
        if (from != null) jpql.append(" AND s.createdAt >= :from");
        if (to != null) jpql.append(" AND s.createdAt <= :to");
        if (customerId != null) jpql.append(" AND c.id = :customerId");
        if (productId != null) jpql.append(" AND p.id = :productId");
        if (sellerId != null) jpql.append(" AND u.id = :sellerId");
        if (paymentMethod != null && !paymentMethod.isBlank()) jpql.append(" AND s.paymentMethod = :paymentMethod");
        if (status != null && !status.isBlank()) jpql.append(" AND s.status = :status");

        jakarta.persistence.Query q = em.createQuery(jpql.toString());
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
        if (customerId != null) q.setParameter("customerId", customerId);
        if (productId != null) q.setParameter("productId", productId);
        if (sellerId != null) q.setParameter("sellerId", sellerId);
        if (paymentMethod != null && !paymentMethod.isBlank()) q.setParameter("paymentMethod", paymentMethod);
        if (status != null && !status.isBlank()) q.setParameter("status", status);

        @SuppressWarnings("unchecked")
        List<Long> ids = q.getResultList();

        List<SaleOrder> sales = ids.isEmpty()
                ? List.of()
                : saleOrderRepository.findByIdsWithItems(ids);
        sales = sales.stream()
                .sorted(Comparator.comparing(SaleOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal netSales = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        long itemsSold = 0;
        long transactionCount = 0;

        for (SaleOrder s : sales) {
            if ("CANCELLED".equalsIgnoreCase(s.getStatus())) continue;
            transactionCount++;
            grossSales = grossSales.add(nz(s.getTotalAmount()));
            discount = discount.add(nz(s.getDiscount()));
            tax = tax.add(nz(s.getTax()));
            netSales = netSales.add(nz(s.getFinalAmount()));
            if (s.getItems() != null) {
                for (SaleOrderItem item : s.getItems()) {
                    int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                    itemsSold += qty;
                    if (item.getProduct() != null && item.getProduct().getPurchasePrice() != null) {
                        cogs = cogs.add(item.getProduct().getPurchasePrice().multiply(new BigDecimal(qty)));
                    }
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("transactionCount", transactionCount);
        summary.put("itemsSold", itemsSold);
        summary.put("grossSales", grossSales);
        summary.put("discount", discount);
        summary.put("tax", tax);
        summary.put("netSales", netSales);
        summary.put("cogs", cogs);
        summary.put("grossProfit", netSales.subtract(cogs));

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
     * <p>Derived from the same figures as the sales report but presented as the
     * commercial view: what came in, what the goods cost, what is left. Kept as a
     * separate method so the controller can gate it on {@code PROFIT_LOSS_READ}
     * alone — a Supervisor is allowed the sales report and not this one.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> profitLossReport(LocalDateTime from, LocalDateTime to) {
        Map<String, Object> sales = salesReport(from, to, null, null, null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) sales.get("summary");

        BigDecimal netSales = nz((BigDecimal) summary.get("netSales"));
        BigDecimal cogs = nz((BigDecimal) summary.get("cogs"));
        BigDecimal grossProfit = netSales.subtract(cogs);
        BigDecimal tax = nz((BigDecimal) summary.get("tax"));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("from", from);
        report.put("to", to);
        report.put("transactionCount", summary.get("transactionCount"));
        report.put("itemsSold", summary.get("itemsSold"));
        report.put("netSales", netSales);
        report.put("cogs", cogs);
        report.put("grossProfit", grossProfit);
        report.put("grossMarginPercent", netSales.signum() == 0
                ? BigDecimal.ZERO
                : grossProfit.multiply(BigDecimal.valueOf(100))
                        .divide(netSales, 2, java.math.RoundingMode.HALF_UP));
        report.put("tax", tax);
        report.put("discount", summary.get("discount"));
        return report;
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
