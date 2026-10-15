package com.inventory.backend.chatbot;

import com.inventory.backend.chatbot.customer.CustomerTools;
import com.inventory.backend.chatbot.product.ProductTools;
import com.inventory.backend.chatbot.staff.FinancialTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiFunction;

/**
 * The chatbot's capabilities, and who may use each one.
 *
 * <h2>A tool is the contract</h2>
 *
 * <p>Everything the chatbot can know, it knows by running one of these. That is
 * what makes it auditable — a question that cannot be mapped to a tool has no
 * data source, and a question that maps to several is visible as such.
 *
 * <p>Each entry records the permission the caller must hold, so the check is a
 * property of the capability rather than a line in the orchestrator. Adding a
 * tool that forgets its permission is visible in the code; a missing check in
 * the orchestrator is not.
 *
 * <h2>Read-only by construction</h2>
 *
 * <p>There is no tool for a payment, a refund, a stock change, an approval or a
 * transfer. The chatbot cannot do those things because there is no code path
 * here that can. A hallucinated tool name resolves to nothing, and an injected
 * instruction cannot reach a tool that does not exist.
 *
 * <p>The single exception is {@link #RAISE_SUPPORT_REQUEST}, which records that a
 * customer needs a human. That is not a change to money or stock, and it is the
 * one thing the chatbot can honestly offer when it cannot answer.
 */
@Component
public class ToolRegistry {

    private final ProductTools productTools;
    private final FinancialTools financialTools;
    private final CustomerTools customerTools;

    /**
     * Every tool, by name.
     *
     * <p>Deliberately a {@code LinkedHashMap} so the UI's capability list and an
     * LLM's tool list come out in a stable, readable order rather than hash order.
     */
    private final Map<String, Entry> tools = new LinkedHashMap<>();

    public ToolRegistry(ProductTools productTools, FinancialTools financialTools,
                        CustomerTools customerTools) {
        this.productTools = productTools;
        this.financialTools = financialTools;
        this.customerTools = customerTools;

        // ── Products ──────────────────────────────────────────────────────────
        register("search_products", "Find products in the catalogue by name, SKU, category or description.",
                "PRODUCT_READ", productTools::search);
        register("product_availability", "Check whether a product is in stock, per warehouse and in total.",
                "PRODUCT_READ", productTools::availability);
        register("product_alternatives", "Find other products in the same category or within a budget.",
                "PRODUCT_READ", productTools::alternatives);

        // ── Finance ───────────────────────────────────────────────────────────
        register("profit", "Gross profit, net profit and margins for a period.",
                "PROFIT_LOSS_READ", financialTools::profit);
        register("financial_comparison", "A period against the one before it.",
                "FINANCIAL_REPORT_VIEW", financialTools::compare);
        register("cash_position", "Cash received, paid and still outstanding for a period.",
                "FINANCIAL_REPORT_VIEW", financialTools::cash);
        register("receivables", "Which customers owe money, and how old those balances are.",
                "RECEIVABLE_READ", financialTools::receivables);
        register("payables", "Which suppliers are owed money for goods already received.",
                "PAYABLE_READ", financialTools::payables);
        register("inventory_value", "What stock on hand is worth at weighted-average cost.",
                "INVENTORY_VALUE_VIEW", financialTools::inventoryValue);

        // ── Inventory ─────────────────────────────────────────────────────────
        register("stockout_risk", "Products projected to run out within a number of days.",
                "INVENTORY_READ", financialTools::stockouts);
        register("slow_moving", "Products holding stock that have not sold recently.",
                "INVENTORY_READ", financialTools::slowMoving);
        register("sale_lookup", "Find a sale by its order number.",
                "SALE_READ", this::saleLookup);

        // ── Customers ─────────────────────────────────────────────────────────
        register("customer_product_search", "Search the catalogue for a shopper.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::search);
        register("customer_product_availability", "Check stock for a shopper.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::availability);
        register("customer_order_status", "Where a shopper's own order stands.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::orderStatus);
        register("customer_order_history", "A shopper's own past orders.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::orderHistory);
        register("customer_return_status", "Whether a return has been raised or approved.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::returnStatus);
        register("store_policies", "Returns, refunds, payments, pricing, delivery and stock.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::policies);
        register("payment_methods", "Ways the store accepts payment.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::paymentMethods);
        register("how_to_order", "How a customer places an order.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::howToOrder);
        register("raise_support_request", "Ask a member of staff to take over.",
                ChatbotTool.CUSTOMER_TOOL, customerTools::raiseSupport);
    }

    private void register(String name, String description, String permission,
                          BiFunction<Map<String, Object>, ChatbotTool.ToolContext, Map<String, Object>> fn) {
        tools.put(name, new Entry(name, description, permission,
                Collections.emptySet(), fn));
    }

    /**
     * The tools a caller may use.
     *
     * <p>Staff tools are filtered on the caller's own authorities; customer tools
     * are always available to a customer session, because their real control is
     * the customer's own id, which the tool applies regardless of what it is
     * asked for.
     */
    public List<Entry> toolsFor(ChatbotTool.ToolContext ctx) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : tools.values()) {
            if (entry.permission().equals(ChatbotTool.CUSTOMER_TOOL)) {
                if (ctx.isCustomer()) {
                    out.add(entry);
                }
                continue;
            }
            if (ctx.isStaff() && ctx.can(entry.permission())) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Whether this caller may run this tool at all. */
    public boolean isPermitted(String name, ChatbotTool.ToolContext ctx) {
        Entry entry = tools.get(name);
        if (entry == null) {
            return false;
        }
        if (entry.permission().equals(ChatbotTool.CUSTOMER_TOOL)) {
            return ctx.isCustomer();
        }
        return ctx.isStaff() && ctx.can(entry.permission());
    }

    /**
     * The permission a tool requires, or null if it does not exist.
     *
     * <p>Exists so the service can tell "you may not use this
     * <em>staff</em> tool" from "that is a <em>customer</em> question" —
     * two refusals that deserve different answers.
     */
    public String permissionOf(String name) {
        Entry entry = tools.get(name);
        return entry == null ? null : entry.permission();
    }

    /** Runs a tool the caller is permitted to run. */
    public Map<String, Object> execute(String name, Map<String, Object> params,
                                       ChatbotTool.ToolContext ctx) {
        Entry entry = tools.get(name);
        if (entry == null) {
            // Unknown names are refused rather than treated as no-ops: a caller
            // that believes a tool exists should be told, and an injected
            // instruction should get nothing.
            throw new ChatbotService.UnknownToolException(name);
        }
        if (!isPermitted(name, ctx)) {
            throw new SecurityException("The assistant may not use " + name + " here.");
        }
        Map<String, Object> result = entry.function().apply(params == null ? Map.of() : params, ctx);
        return result == null ? Map.of() : result;
    }

    /** Every tool name, for logging and for an LLM's tool list. */
    public Set<String> names() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /** Capability names for the UI's suggestion chips. */
    public List<String> capabilityNames(ChatbotTool.ToolContext ctx) {
        return toolsFor(ctx).stream().map(Entry::name).toList();
    }

    /**
     * One capability.
     *
     * @param name        stable identifier, persisted in tool-call logs
     * @param description what it does
     * @param permission  what the caller must hold; {@link ChatbotTool#CUSTOMER_TOOL}
     *                    for anything a customer may use
     */
    public record Entry(String name, String description, String permission,
                        Set<String> parameters,
                        BiFunction<Map<String, Object>, ChatbotTool.ToolContext,
                                Map<String, Object>> function) {
    }

    // ── The tool that needs the registry itself ───────────────────────────

    /**
     * Finds a sale by order number.
     *
     * <p>Staff-only, and it returns the customer's name and what they bought —
     * which is why a customer must not be able to reach it, and why the
     * permission is checked by the registry before it runs.
     */
    private Map<String, Object> saleLookup(Map<String, Object> params, ChatbotTool.ToolContext ctx) {
        String orderNumber = ProductTools.text(params, "orderNumber");
        if (orderNumber == null || orderNumber.isBlank()) {
            return Map.of("found", false, "note", "An order number is needed, for example SO-000123.");
        }
        // Reuse the FinancialTools lookup rather than writing a second
        // one: a search implemented twice is a search that disagrees.
        return financialTools.searchSale(orderNumber);
    }
}
