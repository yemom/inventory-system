package com.inventory.backend.service;

import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Customer returns and supplier returns as persisted, reviewable documents.
 *
 * <p>Both used to be an array in the browser's {@code localStorage}. A return
 * therefore vanished when the browser was cleared, never appeared in a report,
 * and left the stock ledger holding a bare ADJUSTMENT with no way to tell a
 * customer return from shrinkage — so the shrinkage figure could not be trusted
 * even where one existed.
 *
 * <p>A return here reverses both halves of the original document: stock goes back
 * (or goes out, for a supplier return) and the financial effect is recorded — a
 * sales return reduces net sales and its cost leaves COGS; a purchase return
 * reduces the stock value and creates a credit with the supplier. Neither
 * happens before approval, because accepting goods back is a judgement about
 * whether they are resellable.
 */
@Service
@RequiredArgsConstructor
public class ReturnService {

    private final ReturnOrderRepository returnOrderRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SaleOrderService saleOrderService;
    private final FinanceService financeService;
    private final StockLedgerService ledger;
    private final WarehouseStockService warehouseStockService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * Raises a sales return against an existing sale.
     *
     * <p>Capped at what is left of the line. Without that, the same unit can be
     * returned three times, each time adding stock back, and the inventory grows
     * out of nothing.
     */
    @Transactional
    public ReturnOrder createSalesReturn(Long saleOrderId, List<LineRequest> lines,
                                         String reason, String notes) {
        SaleOrder sale = saleOrderRepository.findById(saleOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: id=" + saleOrderId));
        if ("CANCELLED".equals(sale.getStatus())) {
            throw new IllegalArgumentException("A cancelled sale cannot have goods returned against it.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A return must contain at least one line.");
        }

        User actor = currentUser();
        ReturnOrder ret = newReturn(ReturnOrder.TYPE_SALES_RETURN, sale.getOrderNumber(),
                sale.getCustomerName(), sale.getCustomer(), null, sale.getWarehouse(),
                reason, notes, actor);

        BigDecimal total = BigDecimal.ZERO;
        for (LineRequest request : lines) {
            SaleOrderItem line = sale.getItems() == null ? null : sale.getItems().stream()
                    .filter(i -> i.getId().equals(request.itemId()))
                    .findFirst()
                    .orElse(null);
            if (line == null) {
                throw new IllegalArgumentException("Line " + request.itemId()
                        + " does not belong to sale " + sale.getOrderNumber() + ".");
            }
            int returned = line.getQuantityReturned() != null ? line.getQuantityReturned() : 0;
            int returnable = line.getQuantity() - returned;
            if (request.quantity() <= 0 || request.quantity() > returnable) {
                throw new IllegalArgumentException(String.format(
                        "Cannot return %d of '%s': %d of the %d sold are still returnable.",
                        request.quantity(), line.getProduct().getName(), returnable, line.getQuantity()));
            }
            // The cost that comes back is the cost that left, not today's price.
            BigDecimal unitCost = line.getUnitCost() != null
                    ? line.getUnitCost() : nz(line.getProduct().getPurchasePrice());
            BigDecimal amount = nz(line.getUnitPrice()).multiply(BigDecimal.valueOf(request.quantity()));

            ReturnOrderItem item = ReturnOrderItem.builder()
                    .returnOrder(ret)
                    .product(line.getProduct())
                    .saleOrderItem(line)
                    .quantity(request.quantity())
                    .unitPrice(line.getUnitPrice())
                    .unitCost(unitCost)
                    .amount(amount)
                    .tax(BigDecimal.ZERO)
                    .build();
            ret.getItems().add(item);

            line.setQuantityReturned(returned + request.quantity());
            total = total.add(amount);
        }

        ret.setTotalAmount(total);
        ReturnOrder saved = returnOrderRepository.save(ret);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "SALES_RETURN_RAISED", "RETURN", String.valueOf(saved.getId()),
                saved.getReturnNumber() + " against " + sale.getOrderNumber());
        return saved;
    }

    /** Raises a purchase return: goods going back to a supplier. */
    @Transactional
    public ReturnOrder createPurchaseReturn(Long purchaseOrderId, List<LineRequest> lines,
                                            String reason, String notes) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Purchase not found: id=" + purchaseOrderId));
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A return must contain at least one line.");
        }

        User actor = currentUser();
        ReturnOrder ret = newReturn(ReturnOrder.TYPE_PURCHASE_RETURN, order.getOrderNumber(),
                order.getSupplierName(), null, order.getSupplier(), order.getWarehouse(),
                reason, notes, actor);

        BigDecimal total = BigDecimal.ZERO;
        for (LineRequest request : lines) {
            PurchaseOrderItem line = order.getItems() == null ? null : order.getItems().stream()
                    .filter(i -> i.getId().equals(request.itemId()))
                    .findFirst()
                    .orElse(null);
            if (line == null) {
                throw new IllegalArgumentException("Line " + request.itemId()
                        + " does not belong to purchase " + order.getOrderNumber() + ".");
            }
            // Only goods that actually arrived can go back.
            int received = line.getEffectiveReceived();
            int alreadyReturned = line.getQuantityReturned() != null ? line.getQuantityReturned() : 0;
            int returnable = received - alreadyReturned;
            if (request.quantity() <= 0 || request.quantity() > returnable) {
                throw new IllegalArgumentException(String.format(
                        "Cannot return %d of '%s': only %d of the %d received can go back.",
                        request.quantity(), line.getProduct().getName(), returnable, received));
            }
            BigDecimal amount = nz(line.getUnitCost()).multiply(BigDecimal.valueOf(request.quantity()));

            ReturnOrderItem item = ReturnOrderItem.builder()
                    .returnOrder(ret)
                    .product(line.getProduct())
                    .purchaseOrderItem(line)
                    .quantity(request.quantity())
                    .unitPrice(line.getUnitCost())
                    .unitCost(line.getUnitCost())
                    .amount(amount)
                    .tax(BigDecimal.ZERO)
                    .build();
            ret.getItems().add(item);

            line.setQuantityReturned(alreadyReturned + request.quantity());
            total = total.add(amount);
        }

        ret.setTotalAmount(total);
        ReturnOrder saved = returnOrderRepository.save(ret);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "PURCHASE_RETURN_RAISED", "RETURN", String.valueOf(saved.getId()),
                saved.getReturnNumber() + " against " + order.getOrderNumber());
        return saved;
    }

    /**
     * Accepts a return: puts the goods back (or takes them out) and records the
     * financial reversal.
     *
     * <p>Refuses an approval that would remove stock the warehouse does not
     * have — a supplier return of goods that were already sold would otherwise
     * drive stock negative and quietly distort the valuation.
     */
    public ReturnOrder approve(Long id, String note) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Return not found: id=" + id));
        if (!ReturnOrder.STATUS_PENDING.equals(ret.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a pending return can be approved; this one is " + ret.getStatus() + ".");
        }

        User actor = currentUser();
        Warehouse warehouse = ret.getWarehouse() != null
                ? ret.getWarehouse() : warehouseStockService.resolveDefaultWarehouse(null);
        String group = StockLedgerService.newGroup("RET-" + ret.getId());

        for (ReturnOrderItem item : ret.getItems()) {
            int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
            if (quantity <= 0) {
                continue;
            }
            if (ret.isSalesReturn()) {
                ledger.post(StockMovement.TYPE_SALES_RETURN, item.getProduct().getId(),
                        warehouse.getId(), quantity, ret.getOriginalReference(),
                        ret.getReason() == null ? "Customer return" : ret.getReason(),
                        ret.getReturnNumber(), item.getUnitCost(), group,
                        StockMovement.Status.APPROVED, actor);
            } else {
                ledger.post(StockMovement.TYPE_PURCHASE_RETURN, item.getProduct().getId(),
                        warehouse.getId(), -quantity, ret.getOriginalReference(),
                        ret.getReason() == null ? "Returned to supplier" : ret.getReason(),
                        ret.getReturnNumber(), item.getUnitCost(), group,
                        StockMovement.Status.APPROVED, actor);
            }
        }

        // The money side. A sales return that was paid for refunds the customer;
        // a purchase return that was paid for creates a credit with the supplier.
        if (!Boolean.TRUE.equals(ret.getFinancialSettled()) && ret.getTotalAmount() != null
                && ret.getTotalAmount().signum() > 0) {
            if (ret.isSalesReturn()) {
                SaleOrder sale = saleOrderRepository.findAllOutstandingReceivables().stream()
                        .filter(s -> ret.getOriginalReference().equals(s.getOrderNumber()))
                        .findFirst()
                        .orElse(null);
                financeService.recordRefund(sale != null ? sale.getId() : null,
                        ret.getTotalAmount(), "CASH",
                        "Refund for " + ret.getReturnNumber());
            } else {
                PurchaseOrder order = purchaseOrderRepository.findOutstandingPayables(null).stream()
                        .filter(p -> ret.getOriginalReference().equals(p.getOrderNumber()))
                        .findFirst()
                        .orElse(null);
                if (order != null) {
                    financeService.recordSupplierPayment(order.getId(),
                            min(ret.getTotalAmount(), order.getBalanceDue()), "BANK_TRANSFER",
                            null, "Credit note " + ret.getReturnNumber());
                }
            }
            ret.setFinancialSettled(true);
        }

        ret.setStatus(ReturnOrder.STATUS_APPROVED);
        ret.setApprovedBy(actor);
        ret.setApprovedAt(LocalDateTime.now());
        ret.setReviewNote(note);
        ReturnOrder saved = returnOrderRepository.save(ret);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "RETURN_APPROVED", "RETURN", String.valueOf(saved.getId()),
                saved.getReturnNumber() + " (" + saved.getTotalAmount().toPlainString() + ")");
        return saved;
    }

    /** Refuses a return. No stock moves and no money changes. */
    @Transactional
    public ReturnOrder reject(Long id, String note) {
        ReturnOrder ret = returnOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Return not found: id=" + id));
        if (!ReturnOrder.STATUS_PENDING.equals(ret.getStatus())) {
            throw new IllegalArgumentException("Only a pending return can be rejected.");
        }
        User actor = currentUser();
        ret.setStatus(ReturnOrder.STATUS_REJECTED);
        ret.setApprovedBy(actor);
        ret.setApprovedAt(LocalDateTime.now());
        ret.setReviewNote(note);

        // Undo the quantities reserved when the return was raised, so a later
        // return of the same goods is not blocked by a rejected one.
        if (ret.isSalesReturn() && ret.getItems() != null) {
            for (ReturnOrderItem item : ret.getItems()) {
                SaleOrderItem line = item.getSaleOrderItem();
                if (line != null && line.getQuantityReturned() != null) {
                    line.setQuantityReturned(Math.max(0,
                            line.getQuantityReturned() - nz(item.getQuantity())));
                }
            }
        }
        ReturnOrder saved = returnOrderRepository.save(ret);
        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "RETURN_REJECTED", "RETURN", String.valueOf(saved.getId()),
                saved.getReturnNumber() + (note == null ? "" : " — " + note));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ReturnOrder> search(String type, String status, Pageable pageable) {
        return returnOrderRepository.search(type, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ReturnOrder> pending(Pageable pageable) {
        return returnOrderRepository.findByStatusOrderByCreatedAtAsc(
                ReturnOrder.STATUS_PENDING, pageable);
    }

    private ReturnOrder newReturn(String type, String originalReference, String partyName,
                                  Customer customer, Supplier supplier, Warehouse warehouse,
                                  String reason, String notes, User actor) {
        ReturnOrder ret = ReturnOrder.builder()
                .returnNumber("RTN-TMP-" + java.util.UUID.randomUUID())
                .returnType(type)
                .originalReference(originalReference)
                .partyName(partyName)
                .customer(customer)
                .supplier(supplier)
                .warehouse(warehouse)
                .status(ReturnOrder.STATUS_PENDING)
                .reason(reason)
                .notes(notes)
                .financialSettled(false)
                .requestedBy(actor)
                .transactionDate(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();
        ReturnOrder saved = returnOrderRepository.saveAndFlush(ret);
        String prefix = ReturnOrder.TYPE_SALES_RETURN.equals(type) ? "SRET" : "PRET";
        saved.setReturnNumber(String.format("%s-%06d", prefix, saved.getId()));
        return returnOrderRepository.save(saved);
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String principal = authentication.getName();
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .orElse(null);
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        BigDecimal x = nz(a);
        BigDecimal y = nz(b);
        return x.compareTo(y) <= 0 ? x : y;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** One line of a return request. */
    public record LineRequest(Long itemId, int quantity) {
    }
}