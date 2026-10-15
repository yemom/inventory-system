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
import java.util.List;

/**
 * Recording money in and money out.
 *
 * <p>The previous {@code PaymentController} took a {@code Payment} entity straight
 * from the request body and saved it. That made four things wrong at once:
 *
 * <ul>
 *   <li>A caller could set {@code status} to COMPLETED on a payment that never
 *       happened, and it would count as cash.</li>
 *   <li>Nothing tied a payment to the invoice it settled, so receivables and
 *       payables could not be computed and the frontend reconstructed them by
 *       guessing from sale status labels.</li>
 *   <li>{@code createdBy} was free text, so "who received this" was whatever the
 *       caller typed.</li>
 *   <li>An expense defaulted to PENDING but the controller wrote APPROVED only
 *       when the field was absent, so a client could create an expense that
 *       counted as an operating cost while the database said it was not
 *       approved.</li>
 * </ul>
 *
 * <p>Each of those is now decided by the service, from validated state.
 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // ── Payments ──────────────────────────────────────────────────────────────

    /**
     * Records money received from a customer against a sale.
     *
     * <p>Refuses to record more than the invoice is worth. An overpayment is a
     * real thing — a part payment followed by a return leaves a credit — but
     * typing a larger number is a data-entry slip, and silently accepting it
     * makes the outstanding balance go negative and quietly hide a mistake.
     *
     * <p>Partial payments are first-class: the sale's amount paid is updated and
     * its status recomputed, so "sold but not yet paid" is a state the system
     * knows about rather than a label.
     */
    @Transactional
    public Payment recordSalePayment(Long saleOrderId, BigDecimal amount, String method,
                                     String externalReference, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A payment must be for a positive amount.");
        }
        SaleOrder sale = saleOrderRepository.findById(saleOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: id=" + saleOrderId));

        BigDecimal already = paymentRepository.sumCompletedForSale(saleOrderId);
        BigDecimal invoiceTotal = nz(sale.getFinalAmount());
        BigDecimal outstanding = invoiceTotal.subtract(already);
        if (amount.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException(String.format(
                    "That payment is %s but only %s is outstanding on %s.",
                    amount.toPlainString(), outstanding.toPlainString(), sale.getOrderNumber()));
        }

        User actor = currentUser();
        Payment payment = Payment.builder()
                .reference(nextPaymentReference())
                .orderReference(sale.getOrderNumber())
                .direction(Payment.DIRECTION_IN)
                .type(Payment.DIRECTION_IN)
                .partyType(Payment.PARTY_CUSTOMER)
                .customer(sale.getCustomer())
                .saleOrder(sale)
                .amount(amount)
                .paymentMethod(method == null ? "CASH" : method.toUpperCase())
                .status(Payment.STATUS_COMPLETED)
                .transactionDate(LocalDateTime.now())
                .externalReference(externalReference)
                .notes(notes)
                .createdBy(actor == null ? "system" : actor.getUsername())
                .createdByUser(actor)
                .build();
        Payment saved = paymentRepository.save(payment);

        BigDecimal paidNow = already.add(amount);
        sale.setAmountPaid(paidNow);
        sale.setPaymentStatus(paidNow.compareTo(invoiceTotal) >= 0 ? "PAID" : "PARTIAL");
        saleOrderRepository.save(sale);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "PAYMENT_RECEIVED", "PAYMENT", String.valueOf(saved.getId()),
                String.format("%s against %s (%s)", amount.toPlainString(),
                        sale.getOrderNumber(), saved.getPaymentMethod()));
        return saved;
    }

    /**
     * Records money paid to a supplier against a purchase order.
     *
     * <p>Same over-payment guard as {@link #recordSalePayment}, and the order's
     * payment status is recomputed so "received but not fully paid" is a state
     * the payables report can trust.
     */
    @Transactional
    public Payment recordSupplierPayment(Long purchaseOrderId, BigDecimal amount, String method,
                                         String externalReference, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A payment must be for a positive amount.");
        }
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Purchase not found: id=" + purchaseOrderId));

        BigDecimal already = paymentRepository.sumCompletedForPurchase(purchaseOrderId);
        BigDecimal orderTotal = nz(order.getTotalAmount());
        BigDecimal outstanding = orderTotal.subtract(already);
        if (amount.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException(String.format(
                    "That payment is %s but only %s is outstanding on %s.",
                    amount.toPlainString(), outstanding.toPlainString(), order.getOrderNumber()));
        }

        User actor = currentUser();
        Payment payment = Payment.builder()
                .reference(nextPaymentReference())
                .orderReference(order.getOrderNumber())
                .direction(Payment.DIRECTION_OUT)
                .type(Payment.DIRECTION_OUT)
                .partyType(Payment.PARTY_SUPPLIER)
                .supplier(order.getSupplier())
                .purchaseOrder(order)
                .amount(amount)
                .paymentMethod(method == null ? "BANK_TRANSFER" : method.toUpperCase())
                .status(Payment.STATUS_COMPLETED)
                .transactionDate(LocalDateTime.now())
                .externalReference(externalReference)
                .notes(notes)
                .createdBy(actor == null ? "system" : actor.getUsername())
                .createdByUser(actor)
                .build();
        Payment saved = paymentRepository.save(payment);

        BigDecimal paidNow = already.add(amount);
        order.setAmountPaid(paidNow);
        order.setPaymentStatus(paidNow.compareTo(orderTotal) >= 0 ? "PAID" : "PARTIAL");
        purchaseOrderRepository.save(order);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "PAYMENT_MADE", "PAYMENT", String.valueOf(saved.getId()),
                String.format("%s against %s (%s)", amount.toPlainString(),
                        order.getOrderNumber(), saved.getPaymentMethod()));
        return saved;
    }

    /**
     * Records a refund paid back to a customer.
     *
     * <p>An outflow with a customer as the counterparty — not a negative sale.
     * Counting it as negative revenue would double-count it: the sales-return
     * document already reduces net sales, and the cash movement is a separate
     * fact about the bank account.
     */
    @Transactional
    public Payment recordRefund(Long saleOrderId, BigDecimal amount, String method, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A refund must be for a positive amount.");
        }
        SaleOrder sale = saleOrderId == null ? null : saleOrderRepository.findById(saleOrderId).orElse(null);
        User actor = currentUser();

        Payment payment = Payment.builder()
                .reference("REF-" + System.currentTimeMillis())
                .orderReference(sale != null ? sale.getOrderNumber() : null)
                .direction(Payment.DIRECTION_OUT)
                .type(Payment.DIRECTION_OUT)
                .partyType(Payment.PARTY_CUSTOMER)
                .customer(sale != null ? sale.getCustomer() : null)
                .saleOrder(sale)
                .amount(amount)
                .paymentMethod(method == null ? "CASH" : method.toUpperCase())
                .status(Payment.STATUS_COMPLETED)
                .transactionDate(LocalDateTime.now())
                .notes(notes)
                .createdBy(actor == null ? "system" : actor.getUsername())
                .createdByUser(actor)
                .build();
        Payment saved = paymentRepository.save(payment);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "REFUND_PAID", "PAYMENT", String.valueOf(saved.getId()),
                amount.toPlainString() + " refunded to customer");
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Payment> listPayments(LocalDateTime from, LocalDateTime to, String direction,
                                      String partyType, String status, Pageable pageable) {
        return paymentRepository.search(from, to, direction, partyType, status, pageable);
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    /**
     * Records an operating cost.
     *
     * <p>Always PENDING on creation. An expense is somebody's claim that the
     * business spent money; it becomes an operating cost when a manager accepts
     * it. The previous code defaulted the entity to PENDING but the controller
     * wrote APPROVED when the caller omitted the field, so the status the caller
     * sent was what mattered — a client could record an approved expense without
     * anybody approving it.
     */
    @Transactional
    public Expense recordExpense(BigDecimal amount, String category, String description,
                                 LocalDateTime transactionDate, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("An expense must be for a positive amount.");
        }
        User actor = currentUser();
        Expense expense = Expense.builder()
                .reference("EXP-" + System.currentTimeMillis())
                .category(category == null || category.isBlank() ? "OTHER" : category.toUpperCase())
                .amount(amount)
                .description(description)
                .notes(notes)
                // PENDING always. Approval is a separate, attributed decision.
                .status(Expense.STATUS_PENDING)
                .transactionDate(transactionDate == null ? LocalDateTime.now() : transactionDate)
                .createdBy(actor == null ? "system" : actor.getUsername())
                .createdByUser(actor)
                .build();
        Expense saved = expenseRepository.save(expense);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "EXPENSE_RECORDED", "EXPENSE", String.valueOf(saved.getId()),
                amount.toPlainString() + " — " + saved.getCategory());
        return saved;
    }

    /**
     * Accepts an expense so it counts as an operating cost.
     *
     * <p>Requires {@code EXPENSE_APPROVE} and records who approved it. This is
     * the control that keeps a cashier from quietly writing down the profit.
     */
    @Transactional
    public Expense approveExpense(Long id, String note) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: id=" + id));
        if (Expense.STATUS_APPROVED.equals(expense.getStatus())) {
            throw new IllegalArgumentException("This expense has already been approved.");
        }
        if (Expense.STATUS_REJECTED.equals(expense.getStatus())) {
            throw new IllegalArgumentException("This expense has been rejected and cannot be approved.");
        }
        User actor = currentUser();
        expense.setStatus(Expense.STATUS_APPROVED);
        expense.setApprovedBy(actor);
        expense.setApprovedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            expense.setNotes(expense.getNotes() == null ? note : expense.getNotes() + " | " + note);
        }
        Expense saved = expenseRepository.save(expense);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "EXPENSE_APPROVED", "EXPENSE", String.valueOf(saved.getId()),
                expense.getAmount().toPlainString() + " — " + expense.getCategory());
        return saved;
    }

    /** Refuses an expense. It never becomes an operating cost. */
    @Transactional
    public Expense rejectExpense(Long id, String note) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: id=" + id));
        User actor = currentUser();
        expense.setStatus(Expense.STATUS_REJECTED);
        expense.setApprovedBy(actor);
        expense.setApprovedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            expense.setNotes(expense.getNotes() == null ? note : expense.getNotes() + " | " + note);
        }
        Expense saved = expenseRepository.save(expense);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "EXPENSE_REJECTED", "EXPENSE", String.valueOf(saved.getId()),
                expense.getAmount().toPlainString() + " — " + expense.getCategory());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Expense> listExpenses(LocalDateTime from, LocalDateTime to, String status,
                                      String category, Pageable pageable) {
        return expenseRepository.search(from, to, status, category, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Expense> pendingExpenses(Pageable pageable) {
        return expenseRepository.findByStatusOrderByCreatedAtAsc(Expense.STATUS_PENDING, pageable);
    }

    /**
     * Deletes an expense.
     *
     * <p>Refused once approved. Deleting an approved cost would leave a period
     * that was reported on no longer reproducible from the records — the whole
     * point of reporting from saved transactions. Reject it instead: that keeps
     * the row and the reason.
     */
    @Transactional
    public void deleteExpense(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: id=" + id));
        if (Expense.STATUS_APPROVED.equals(expense.getStatus())) {
            throw new IllegalArgumentException(
                    "An approved expense cannot be deleted; reject it instead so the record survives.");
        }
        expenseRepository.delete(expense);
    }

    private String nextPaymentReference() {
        return "PAY-" + System.currentTimeMillis();
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

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}