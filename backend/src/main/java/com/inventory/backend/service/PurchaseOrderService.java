package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditLogService auditLogService;
    private final StockMovementRepository stockMovementRepository;
    private final StockLedgerService ledger;
    private final WarehouseStockService warehouseStockService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Re-exported for callers that already referenced them here. */
    public static final String STATUS_DRAFT = PurchaseOrder.STATUS_DRAFT;
    public static final String STATUS_RECEIVED = PurchaseOrder.STATUS_RECEIVED;

    @Transactional(readOnly = true)
    public Page<PurchaseOrderDTO> listPurchases(Pageable pageable) {
        List<PurchaseOrder> orders = purchaseOrderRepository.findAllWithDetails(pageable);
        List<PurchaseOrderDTO> dtos = orders.stream().map(this::toDTO).collect(Collectors.toList());
        long total = purchaseOrderRepository.count();
        return new PageImpl<>(dtos, pageable, total);
    }

    /**
     * Raises a purchase order.
     *
     * <p>Raising an order does not move stock. Goods appear only through
     * {@link #receivePurchase}, which requires {@code PURCHASE_APPROVE} — so the
     * person who asks for goods is not the person who makes them appear, and an
     * order cannot inflate stock by being raised.
     *
     * <p>The previous behaviour received the goods inside this method whenever
     * the caller held the approve permission, which meant "create" and "receive"
     * were one action with two names.
     */
    @Transactional
    public PurchaseOrderDTO createPurchase(CreatePurchaseOrderRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("A purchase order must contain at least one item.");
        }
        User actor = currentUser();

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplierName(req.getSupplierName());
        if (req.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(req.getSupplierId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Supplier not found: id=" + req.getSupplierId()));
            order.setSupplier(supplier);
            order.setSupplierName(supplier.getCompanyName());
        }
        // Derived from the saved id, like sale numbers. The previous
        // "PO-" + currentTimeMillis() had no uniqueness retry against a unique
        // column, so two orders created in the same millisecond collided.
        order.setOrderNumber("PO-TMP-" + java.util.UUID.randomUUID());
        order.setCreatedBy(actor);
        order.setTransactionDate(LocalDateTime.now());

        Warehouse warehouse = null;
        if (req.getWarehouseId() != null) {
            warehouse = warehouseRepository.findById(req.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Warehouse not found: id=" + req.getWarehouseId()));
            order.setWarehouse(warehouse);
        }

        order.setStatus(hasAuthority("PURCHASE_APPROVE")
                ? PurchaseOrder.STATUS_ORDERED : PurchaseOrder.STATUS_DRAFT);

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrderItem> items = new ArrayList<>();

        for (CreatePurchaseOrderItem i : req.getItems()) {
            if (i.getQuantity() == null || i.getQuantity() <= 0) {
                throw new IllegalArgumentException("Purchase quantity must be at least 1.");
            }
            if (i.getUnitCost() == null) {
                throw new IllegalArgumentException("A unit cost is required for each purchase line.");
            }
            Product p = productRepository.findById(i.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: id=" + i.getProductId()));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(p);
            item.setQuantity(i.getQuantity());
            // Nothing has arrived yet. Writing the ordered quantity here would
            // make the order look complete the moment it was raised.
            item.setQuantityReceived(0);
            item.setQuantityReturned(0);
            item.setUnitCost(i.getUnitCost());
            item.setSubtotal(i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity())));
            items.add(item);

            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);
        order.setItems(items);
        order.setPaymentTerms(req.getPaymentTerms());
        order.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        if (req.getTax() != null) {
            order.setTax(req.getTax());
            total = total.add(req.getTax());
            order.setTotalAmount(total);
        }
        if (req.getDueDate() != null) {
            order.setDueDate(req.getDueDate());
        } else if (req.getPaymentTerms() != null) {
            order.setDueDate(deriveDueDate(order.getTransactionDate(), req.getPaymentTerms()));
        }

        String requestedPaymentStatus = req.getPaymentStatus() == null || req.getPaymentStatus().isBlank()
                ? "UNPAID" : req.getPaymentStatus().toUpperCase();
        order.setPaymentStatus(requestedPaymentStatus);
        order.setAmountPaid(resolveOpeningPayment(req, total, requestedPaymentStatus));

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        saved.setOrderNumber(String.format("PO-%06d", saved.getId()));
        saved = purchaseOrderRepository.save(saved);

        if (hasAuthority("PURCHASE_APPROVE")) {
            saved.setApprovedBy(actor);
            saved.setApprovedAt(LocalDateTime.now());
            saved = purchaseOrderRepository.save(saved);
        }

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "PURCHASE_RAISED", "PURCHASE", String.valueOf(saved.getId()),
                String.format("Raised %s with %s for %s (status %s)",
                        saved.getOrderNumber(), saved.getSupplierName(),
                        total.toPlainString(), saved.getStatus()));

        return toDTO(saved);
    }

    /**
     * Receives a purchase order: adds the stock and marks it RECEIVED.
     *
     * <p>Requires {@code PURCHASE_APPROVE}, so the person who raised the order
     * is not the person who makes the goods appear on the shelf.
     *
     * <p>Refuses a second receipt. Previously the guard compared the status
     * string only, and a DRAFT order with a null status could be received twice;
     * now the quantity already received on each line is the authority, and a
     * line that is already complete cannot be received again.
     */
    @Transactional
    public PurchaseOrderDTO receivePurchase(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found: id=" + id));

        if (PurchaseOrder.STATUS_CANCELLED.equals(order.getStatus())) {
            throw new IllegalArgumentException("A cancelled purchase cannot be received.");
        }
        if (PurchaseOrder.STATUS_RECEIVED.equals(order.getStatus())) {
            throw new IllegalArgumentException("This purchase has already been received.");
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("This purchase order has no lines to receive.");
        }

        User actor = currentUser();
        Warehouse warehouse = order.getWarehouse() != null
                ? order.getWarehouse()
                : warehouseStockService.resolveDefaultWarehouse(null);

        String group = StockLedgerService.newGroup("PO-" + order.getId());
        BigDecimal receivedValue = BigDecimal.ZERO;

        for (PurchaseOrderItem item : order.getItems()) {
            int outstanding = item.getQuantityOutstanding();
            if (outstanding <= 0) {
                // Already complete. Refusing the whole order rather than
                // silently skipping keeps "receive twice" an error the caller
                // can see instead of a no-op that looks successful.
                throw new IllegalArgumentException(String.format(
                        "Line '%s' on %s has already been received in full. "
                                + "Receiving it again would add the same stock twice.",
                        item.getProduct() != null ? item.getProduct().getName() : "?",
                        order.getOrderNumber()));
            }
            ledger.post(StockMovement.TYPE_PURCHASE_RECEIPT, item.getProduct().getId(),
                    warehouse.getId(), outstanding, order.getOrderNumber(),
                    "Goods received from supplier",
                    "Purchase received: " + order.getOrderNumber()
                            + " — " + outstanding + " of " + item.getQuantity() + " ordered",
                    item.getUnitCost(), group, StockMovement.Status.APPROVED, actor);

            item.setQuantityReceived(item.getEffectiveReceived() + outstanding);
            receivedValue = receivedValue.add(
                    nz(item.getUnitCost()).multiply(BigDecimal.valueOf(outstanding)));

            if (item.getProduct() != null) {
                // Keep the product's own purchase price current so the purchase
                // screen shows what was last paid. The weighted average used for
                // valuation lives on the warehouse stock row.
                item.getProduct().setPurchasePrice(item.getUnitCost());
                productRepository.save(item.getProduct());
            }
        }

        boolean fullyReceived = order.getItems().stream().allMatch(PurchaseOrderItem::isFullyReceived);
        order.setStatus(fullyReceived
                ? PurchaseOrder.STATUS_RECEIVED : PurchaseOrder.STATUS_PARTIALLY_RECEIVED);
        order.setApprovedBy(actor);
        order.setApprovedAt(LocalDateTime.now());
        PurchaseOrder saved = purchaseOrderRepository.save(order);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "PURCHASE_RECEIVED", "PURCHASE", String.valueOf(saved.getId()),
                String.format("Received %s into %s (value %s)",
                        saved.getOrderNumber(), warehouse.getName(), receivedValue.toPlainString()));
        return toDTO(saved);
    }

    /**
     * Records part of an order as delivered, leaving the rest outstanding.
     *
     * <p>Distinct from {@link #receivePurchase}: a supplier delivering half an
     * order is ordinary, and forcing it all-or-nothing is what makes stock
     * levels disagree with the delivery note.
     */
    @Transactional
    public PurchaseOrderDTO receivePartial(Long id, Map<Long, Integer> quantitiesByLineId) {
        PurchaseOrder order = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found: id=" + id));
        if (PurchaseOrder.STATUS_RECEIVED.equals(order.getStatus())) {
            throw new IllegalArgumentException("This purchase has already been received in full.");
        }
        if (quantitiesByLineId == null || quantitiesByLineId.isEmpty()) {
            throw new IllegalArgumentException("Supply at least one line and a quantity to receive.");
        }

        User actor = currentUser();
        Warehouse warehouse = order.getWarehouse() != null
                ? order.getWarehouse() : warehouseStockService.resolveDefaultWarehouse(null);
        String group = StockLedgerService.newGroup("PO-" + order.getId());

        for (PurchaseOrderItem item : order.getItems()) {
            Integer requested = quantitiesByLineId.get(item.getId());
            if (requested == null || requested == 0) {
                continue;
            }
            int outstanding = item.getQuantityOutstanding();
            if (requested < 0) {
                throw new IllegalArgumentException("A receipt quantity cannot be negative.");
            }
            if (requested > outstanding) {
                throw new IllegalArgumentException(String.format(
                        "Cannot receive %d of '%s': only %d of the %d ordered are still outstanding.",
                        requested, item.getProduct() != null ? item.getProduct().getName() : "?",
                        outstanding, item.getQuantity()));
            }
            ledger.post(StockMovement.TYPE_PURCHASE_RECEIPT, item.getProduct().getId(),
                    warehouse.getId(), requested, order.getOrderNumber(),
                    "Part delivery from supplier",
                    "Partial receipt: " + order.getOrderNumber(), item.getUnitCost(), group,
                    StockMovement.Status.APPROVED, actor);
            item.setQuantityReceived(item.getEffectiveReceived() + requested);
        }

        order.setStatus(order.getItems().stream().allMatch(PurchaseOrderItem::isFullyReceived)
                ? PurchaseOrder.STATUS_RECEIVED : PurchaseOrder.STATUS_PARTIALLY_RECEIVED);
        order.setApprovedAt(LocalDateTime.now());
        return toDTO(purchaseOrderRepository.save(order));
    }

    /**
     * Marks an order cancelled.
     *
     * <p>Refused once goods have arrived: cancelling a received order would
     * leave stock with no document explaining where it came from. The correct
     * instrument for goods that arrived in error is a purchase return.
     */
    @Transactional
    public PurchaseOrderDTO cancel(Long id, String reason) {
        PurchaseOrder order = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found: id=" + id));
        if (PurchaseOrder.STATUS_RECEIVED.equals(order.getStatus())
                || PurchaseOrder.STATUS_PARTIALLY_RECEIVED.equals(order.getStatus())) {
            throw new IllegalArgumentException(
                    "This order has goods received against it. Record a purchase return instead of cancelling it.");
        }
        order.setStatus(PurchaseOrder.STATUS_CANCELLED);
        User actor = currentUser();
        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "PURCHASE_CANCELLED", "PURCHASE", String.valueOf(order.getId()),
                order.getOrderNumber() + (reason == null ? "" : " — " + reason));
        return toDTO(purchaseOrderRepository.save(order));
    }

    /** Orders with goods still outstanding, oldest first. */
    @Transactional(readOnly = true)
    public Page<PurchaseOrderDTO> listOutstanding(Pageable pageable) {
        return purchaseOrderRepository.findNotFullyReceived(pageable).map(this::toDTO);
    }

    /**
     * Days of credit taken from a "NET 30"-style term.
     */
    static LocalDate deriveDueDate(LocalDateTime transactionDate, String paymentTerms) {
        if (paymentTerms == null) {
            return null;
        }
        String digits = paymentTerms.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        LocalDate start = transactionDate != null
                ? transactionDate.toLocalDate() : LocalDate.now();
        return start.plusDays(Integer.parseInt(digits));
    }

    private boolean hasAuthority(String authority) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private User currentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String principal = authentication.getName();
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .orElse(null);
    }

    public PurchaseOrderDTO toDTO(PurchaseOrder o) {
        PurchaseOrderDTO dto = new PurchaseOrderDTO();
        dto.setId(o.getId());
        dto.setOrderNumber(o.getOrderNumber());
        dto.setReference(o.getOrderNumber());   // frontend alias
        dto.setSupplierName(o.getSupplierName());
        dto.setSupplierId(o.getSupplier() != null ? o.getSupplier().getId() : null);
        dto.setWarehouseId(o.getWarehouse() != null ? o.getWarehouse().getId() : null);
        dto.setWarehouseName(o.getWarehouse() != null ? o.getWarehouse().getName() : null);
        dto.setTotalAmount(o.getTotalAmount());
        dto.setTotal(o.getTotalAmount());        // frontend alias
        dto.setStatus(o.getStatus());
        dto.setCreatedAt(o.getCreatedAt());
        dto.setPaymentTerms(o.getPaymentTerms());
        dto.setDueDate(o.getDueDate());
        dto.setExpectedDeliveryDate(o.getExpectedDeliveryDate());
        dto.setBalanceDue(o.getBalanceDue());

        if (o.getCreatedAt() != null) {
            dto.setDate(o.getCreatedAt().format(DATE_FMT));
        }

        // Use the stored payment status. A purchase can be RECEIVED (goods
        // have arrived) but UNPAID (bought on credit), so we must not infer
        // the payment status from the order status.
        //
        // Fall back to legacy logic only for rows that predate the column:
        // those have a null paymentStatus.
        String storedPaymentStatus = o.getPaymentStatus();
        if (storedPaymentStatus != null && !storedPaymentStatus.isBlank()) {
            dto.setPaymentStatus(storedPaymentStatus.toLowerCase());
            dto.setPaid(o.getAmountPaid() != null ? o.getAmountPaid() : BigDecimal.ZERO);
        } else if ("RECEIVED".equals(o.getStatus())) {
            // Legacy row: RECEIVED → assume it was paid at the time.
            dto.setPaymentStatus("paid");
            dto.setPaid(o.getTotalAmount());
        } else {
            dto.setPaymentStatus("unpaid");
            dto.setPaid(BigDecimal.ZERO);
        }

        if (o.getItems() != null) {
            dto.setItems(o.getItems().stream().map(i -> {
                PurchaseOrderItemDTO idto = new PurchaseOrderItemDTO();
                idto.setId(i.getId());
                if (i.getProduct() != null) {
                    idto.setProductId(i.getProduct().getId());
                    idto.setProductName(i.getProduct().getName());
                    idto.setProductSku(i.getProduct().getSku());
                }
                idto.setQuantity(i.getQuantity());
                idto.setQuantityReceived(i.getEffectiveReceived());
                idto.setQuantityOutstanding(i.getQuantityOutstanding());
                idto.setUnitCost(i.getUnitCost());
                idto.setSubtotal(i.getSubtotal());
                idto.setTotal(i.getSubtotal());
                return idto;
            }).collect(Collectors.toList()));
        }

        return dto;
    }

    private static BigDecimal resolveOpeningPayment(CreatePurchaseOrderRequest req,
                                                     BigDecimal total,
                                                     String paymentStatus) {
        if ("PAID".equals(paymentStatus)) {
            return total;
        }
        if ("PARTIAL".equals(paymentStatus) && req.getAmountPaid() != null) {
            BigDecimal paid = req.getAmountPaid();
            return paid.compareTo(total) > 0 ? total : paid;
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}