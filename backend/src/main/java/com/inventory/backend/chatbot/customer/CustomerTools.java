package com.inventory.backend.chatbot.customer;

import com.inventory.backend.chatbot.ChatbotTool;
import com.inventory.backend.chatbot.product.ProductTools;
import com.inventory.backend.model.ChatSupportTicket;
import com.inventory.backend.model.Customer;
import com.inventory.backend.model.SaleOrder;
import com.inventory.backend.repository.ChatSupportTicketRepository;
import com.inventory.backend.repository.CustomerRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * What a customer may ask about their own orders.
 *
 * <h2>Every query is scoped by the customer's own id</h2>
 *
 * <p>That is the whole point of this class. A customer's id comes from the
 * session token, never from the message, and every lookup here filters on it.
 * A customer cannot ask about another customer's orders because the query cannot
 * be expressed without the other customer's id, which the customer does not have
 * and the service will not supply.
 *
 * <h2>Payment detail is withheld even from its owner</h2>
 *
 * <p>A customer asking "how much did I pay" gets the order total and the amount
 * outstanding — never a card number, a bank reference or another customer's
 * figure. The payment method is named, because that is useful; the instrument is
 * not, because it is not needed to answer anything and its presence would make a
 * leaked conversation a payment leak.
 *
 * <h2>Order status is reported, not promised</h2>
 *
 * <p>{@code PAID}, {@code PENDING} and {@code CANCELLED} are states the sale is
 * recorded in. Delivery is <b>not</b> tracked by this system, so a question about
 * delivery is answered by saying so rather than by inventing a courier status.
 */
@Component
@RequiredArgsConstructor
public class CustomerTools {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final SaleOrderRepository saleOrderRepository;
    private final ProductTools productTools;
    private final CustomerRepository customerRepository;
    private final ChatSupportTicketRepository supportTicketRepository;
    private final AuditLogService auditLogService;

    /**
     * Searches the catalogue for a shopper.
     *
     * <p>The catalogue is the same one staff search: a product
     * a customer can ask about is a product staff can see. What
     * differs is what the answer may contain — a customer is
     * never shown a purchase price, which is why the row
     * builder omits it.
     */
    public Map<String, Object> search(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        return productTools.search(params, ctx);
    }

    /**
     * Checks stock for a shopper.
     *
     * <p>See {@link #search}: the same lookup staff use,
     * answered in the terms a customer needs.
     */
    public Map<String, Object> availability(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        return productTools.availability(params, ctx);
    }

    /**
     * Records that a customer needs a person.
     *
     * <p>The one thing the chatbot can do that changes state, and it
     * changes almost nothing: it writes a row saying a human should
     * look. It does not open any record the customer could not
     * already see, and it grants no access.
     *
     * <p>Customer-only. The customer's own id comes from the
     * session, so a request cannot be filed against somebody else,
     * and a member of staff does not need a chatbot to reach a
     * customer they can already see in the CRM.
     */
    public Map<String, Object> raiseSupport(Map<String, Object> params,
                                               ChatbotTool.ToolContext ctx) {
        String reason = ProductTools.text(params, "reason");
        if (reason == null || reason.isBlank()) {
            return Map.of("raised", false,
                    "note", "A reason is needed before a person is asked.");
        }

        String customerName = customerRepository.findById(ctx.actorId())
                .map(Customer::getName)
                .orElse("customer");

        ChatSupportTicket ticket = supportTicketRepository.save(ChatSupportTicket.builder()
                .customerId(ctx.actorId())
                .customerName(customerName)
                .reason(reason)
                .summary("Raised by the assistant on the customer's behalf")
                .raisedByType("CHATBOT")
                .build());

        auditLogService.log(null, "chatbot", "SUPPORT_REQUEST_CREATED",
                "CHAT_SUPPORT_TICKET", String.valueOf(ticket.getId()),
                "customer=" + ctx.actorId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("raised", true);
        out.put("ticketId", ticket.getId());
        out.put("note", "A member of staff will pick this up. Nothing about your order "
                + "has changed — this only asks somebody to look.");
        return out;
    }

    /**
     * The customer's own order by order number.
     *
     * <p>An order number is not a password — it is on a receipt and in an email. So
     * the lookup is additionally scoped to the customer's own id, which means a
     * guesser who has somebody's order number still cannot see the order unless
     * they also hold that customer's session.
     */
    public Map<String, Object> orderStatus(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        String orderNumber = ProductTools.text(params, "orderNumber");
        if (orderNumber == null || orderNumber.isBlank()) {
            return Map.of("found", false, "note", "An order number is needed, for example SO-000123.");
        }

        long matches = saleOrderRepository.countByCustomerIdAndOrderNumber(ctx.actorId(), orderNumber);

        if (matches == 0) {
            // Deliberately identical whether the order exists and belongs to
            // somebody else, or does not exist at all. Distinguishing them would
            // let somebody enumerate other customers' order numbers.
            return Map.of("found", false,
                    "note", "There is no order " + orderNumber + " on your account. "
                            + "Check the number on your receipt, or ask a member of staff.");
        }

        // Scoped to this customer in SQL, so the row returned can only be theirs.
        SaleOrder order = saleOrderRepository.findByCustomerId(ctx.actorId()).stream()
                .filter(sale -> orderNumber.equalsIgnoreCase(sale.getOrderNumber()))
                .findFirst()
                .orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("found", order != null);
        if (order != null) {
            out.put("orderNumber", order.getOrderNumber());
            out.put("status", order.getStatus());
            out.put("paymentStatus", order.getPaymentStatus());
            out.put("total", order.getFinalAmount());
            out.put("outstanding", outstanding(order));
            out.put("orderDate", order.getTransactionDate() == null
                    ? order.getCreatedAt().format(DATE)
                    : order.getTransactionDate().format(DATE));
            if (order.getRefundStatus() != null) {
                out.put("refundStatus", order.getRefundStatus());
            }
            if (order.getVoidStatus() != null) {
                out.put("voidStatus", order.getVoidStatus());
            }
            out.put("items", itemsOf(order));
        }

        // Delivery is not something this system records. Saying so is the honest
        // answer to "where is my delivery".
        out.put("deliveryTracked", false);
        out.put("note", "Payment status is what is recorded against the order. "
                + "Delivery is not tracked here — ask a member of staff for that.");
        return out;
    }

    /**
     * The customer's purchase history.
     *
     * <p>Newest first, with totals and what is still outstanding. No payment
     * instrument, no other party.
     */
    public Map<String, Object> orderHistory(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        int limit = ProductTools.integer(params, "limit", 10);
        limit = Math.max(1, Math.min(limit, 50));

        List<SaleOrder> orders = saleOrderRepository.findByCustomerId(ctx.actorId()).stream()
                .sorted(Comparator.comparing(SaleOrder::getCreatedAt).reversed())
                .limit(limit)
                .toList();

        if (orders.isEmpty()) {
            return Map.of("orders", List.of(), "note", "There are no orders on your account yet.");
        }

        List<Map<String, Object>> rows = orders.stream()
                .map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("orderNumber", order.getOrderNumber());
                    row.put("date", order.getTransactionDate() == null
                            ? order.getCreatedAt().format(DATE)
                            : order.getTransactionDate().format(DATE));
                    row.put("status", order.getStatus());
                    row.put("total", order.getFinalAmount());
                    row.put("outstanding", outstanding(order));
                    row.put("itemCount", order.getItems() == null ? 0 : order.getItems().size());
                    return row;
                })
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orders", rows);
        out.put("orderCount", orders.size());
        out.put("note", "Your own orders only, newest first. Totals exclude sales tax, "
                + "which is collected for the tax authority rather than kept as income.");
        return out;
    }

    /**
     * Whether a customer can still return something.
     *
     * <p>Read from the order, not decided here: a return is a document that has to
     * be approved, and this tool does not create it. It states the recorded state
     * and points at the process.
     */
    public Map<String, Object> returnStatus(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        String orderNumber = ProductTools.text(params, "orderNumber");
        if (orderNumber == null || orderNumber.isBlank()) {
            return Map.of("found", false, "note", "An order number is needed.");
        }

        return saleOrderRepository.findByCustomerId(ctx.actorId()).stream()
                .filter(sale -> orderNumber.equalsIgnoreCase(sale.getOrderNumber()))
                .findFirst()
                .map(order -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("found", true);
                    out.put("orderNumber", order.getOrderNumber());
                    out.put("status", order.getStatus());
                    out.put("refundStatus", order.getRefundStatus() == null ? "no request raised" : order.getRefundStatus());
                    out.put("returnProcess",
                            "A return is raised against a sale and then approved by a member of staff. "
                                    + "Approving returns the goods to stock and reverses the sale. "
                                    + "Nothing happens until somebody approves it.");
                    out.put("note", "Recorded state only. A return must be approved before stock or money moves.");
                    return out;
                })
                .orElseGet(() -> Map.of("found", false,
                        "note", "There is no order " + orderNumber + " on your account."));
    }

    /** Payment methods the system records, for the "how can I pay" question. */
    public Map<String, Object> paymentMethods(Map<String, Object> params,
                                               ChatbotTool.ToolContext ctx) {
        return Map.of(
                "methods", List.of(
                        Map.of("method", "CASH", "note", "Cash at the till."),
                        Map.of("method", "MOBILE_MONEY", "note", "Mobile money transfer."),
                        Map.of("method", "CARD", "note", "Card at the till."),
                        Map.of("method", "BANK", "note", "Bank transfer."),
                        Map.of("method", "CREDIT", "note", "Credit, if your account has been given a limit.")),
                "note", "Payment methods the system records. Whether a given till accepts one "
                        + "is a matter for the staff at that till.");
    }

    /** Store policies. Curated text, not something an LLM was asked to write. */
    public Map<String, Object> policies(Map<String, Object> params,
                                         ChatbotTool.ToolContext ctx) {
        String topic = ProductTools.text(params, "topic");
        Map<String, String> all = new LinkedHashMap<>();
        all.put("returns", """
                A return is raised against a sale and then approved by a member of staff.
                Approving returns the goods to stock and reverses the sale.
                Nothing is refunded or restocked until somebody approves it, so raising
                a request does not by itself change anything.""");
        all.put("refunds", """
                A refund is money returned because a sale was undone. It is recorded
                against the original sale and is never created by the assistant on
                its own. A member of staff must raise and approve it.""");
        all.put("payments", """
                Cash, mobile money, card, bank transfer and credit are recorded.
                Payment status on an order is what is recorded against it, not what
                somebody typed in — so a sale marked unpaid is unpaid until a payment
                is recorded.""");
        all.put("prices", """
                Prices shown are the catalogue selling price. Sales tax is added on
                top and is collected for the tax authority, not kept as income.""");
        all.put("delivery", """
                Delivery is not tracked by this system. Ask a member of staff for
                anything about a delivery, and they will have to check themselves.""");
        all.put("stock", """
                A stock figure is what is recorded, not a guarantee that the item is
                still on the shelf. Check with a member of staff before travelling
                for something specific.""");

        if (topic != null && all.containsKey(topic.toLowerCase(Locale.ROOT))) {
            return Map.of("topic", topic, "text", all.get(topic.toLowerCase(Locale.ROOT)));
        }
        return Map.of("topics", List.copyOf(all.keySet()), "available", all);
    }

    /**
     * How an order is placed.
     *
     * <p>The assistant does not take orders. That needs a payment and a stock
     * movement, both of which are consequential and both of which the chatbot must
     * not do. So it explains the route instead.
     */
    public Map<String, Object> howToOrder(Map<String, Object> params,
                                          ChatbotTool.ToolContext ctx) {
        return Map.of(
                "steps", List.of(
                        "Find the product in the catalogue below, or ask me to search for it.",
                        "Note its name, price and whether it is in stock.",
                        "Place the order at the till, or online through the storefront if one is open.",
                        "Pay at the point of sale. An order that is not paid stays recorded as unpaid."),
                "note", "The assistant cannot place an order for you. An order changes stock "
                        + "and takes payment, so it is only created by a member of staff at a till.");
    }

    private BigDecimal outstanding(SaleOrder order) {
        BigDecimal total = order.getFinalAmount() == null ? BigDecimal.ZERO : order.getFinalAmount();
        BigDecimal paid = order.getAmountPaid() == null ? BigDecimal.ZERO : order.getAmountPaid();
        return total.subtract(paid).max(BigDecimal.ZERO);
    }

    private List<Map<String, Object>> itemsOf(SaleOrder order) {
        if (order.getItems() == null) {
            return List.of();
        }
        return order.getItems().stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("product", item.getProduct() == null ? "—" : item.getProduct().getName());
                    row.put("quantity", item.getQuantity());
                    row.put("unitPrice", item.getUnitPrice());
                    row.put("returned", item.getQuantityReturned() == null ? 0 : item.getQuantityReturned());
                    row.put("returnedNoted", true);
                    return row;
                })
                .collect(Collectors.toList());
    }
}
