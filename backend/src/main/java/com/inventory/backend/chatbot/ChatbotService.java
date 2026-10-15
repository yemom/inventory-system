package com.inventory.backend.chatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.model.ChatConversation;
import com.inventory.backend.model.ChatCustomerSession;
import com.inventory.backend.model.ChatMessage;
import com.inventory.backend.model.Customer;
import com.inventory.backend.model.CustomerStatus;
import com.inventory.backend.repository.ChatConversationRepository;
import com.inventory.backend.repository.ChatCustomerSessionRepository;
import com.inventory.backend.repository.ChatMessageRepository;
import com.inventory.backend.repository.SaleOrderRepository;
import com.inventory.backend.security.CustomUserDetails;
import com.inventory.backend.security.RolePermissionCatalog;
import com.inventory.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The chatbot.
 *
 * <h2>The pipeline</h2>
 *
 * <ol>
 *   <li><b>Authorise.</b> Who is asking, and what is their scope? Resolved from
 *       the security context or from a customer session token, never from the
 *       message.</li>
 *   <li><b>Classify.</b> Map the question to an intent. Keyword scoring, so it
 *       needs no network and cannot be talked out of an answer.</li>
 *   <li><b>Select tools.</b> From the tools this caller may use, pick the ones
 *       the intent needs. A caller's own permission is checked here, before any
 *       data is read.</li>
 *   <li><b>Run tools.</b> Through the same services the reports use. Read-only.</li>
 *   <li><b>Phrase.</b> Render the answer from the tool results. If a provider is
 *       configured and allowed, it may re-word them.</li>
 *   <li><b>Verify.</b> Every number in the prose must be in the tool results.
 *       Otherwise the prose is discarded and the deterministic wording is used.</li>
 *   <li><b>Persist and audit.</b> The exchange is written to the conversation,
 *       and the tool calls are logged.</li>
 * </ol>
 *
 * <h2>Why the last two steps matter</h2>
 *
 * <p>Step 6 is the one that keeps the chatbot honest. A model asked to write a
 * sentence about a tool result will sometimes write a figure that was not in the
 * result — a rounding slip, a number from an earlier question. Without the check,
 * that figure is fluent, confident and wrong. With it, the worst case is a
 * plainer sentence.
 *
 * <h2>Authorisation is checked per tool, not per request</h2>
 *
 * <p>The tools a caller may use are resolved from their own authorities at the
 * start of every request, and each tool is re-checked against that list before it
 * runs. So a question that would need a permission the caller lacks is refused
 * before any query is issued, and a caller cannot widen their scope by asking for
 * several things at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ToolRegistry toolRegistry;
    private final ChatbotProperties properties;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final RateLimitGuard rateLimitGuard;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /** Both providers are beans; which one is used is a config value. */
    private final ChatbotProvider deterministicProvider;
    private final OpenAiCompatibleProvider openAiProvider;
    private final ChatCustomerSessionRepository customerSessionRepository;
    private final SaleOrderRepository saleOrderRepository;

    /**
     * An order number written in a question, e.g. {@code SO-000123}.
     *
     * <p>"Where is my order SO-000123?" is the whole question, so the number
     * is read out of it rather than asked for a second time. It is only ever
     * a lookup key: every tool that receives it still scopes the query to the
     * caller's own id, so knowing a number confers no access on its own.
     */
    private static final Pattern ORDER_NUMBER =
            Pattern.compile("\\bSO-\\d{3,}\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Asks the chatbot something.
     *
     * @param question the caller's words, untrusted
     * @param conversationId which conversation this belongs to, if it continues one
     * @param range the reporting period, if the caller set one
     * @param ctx who is asking, resolved by the controller. Staff come from
     *            the security context; customers from a session token that
     *            was validated against their own id
     */
    @Transactional
    public Reply ask(String question, Long conversationId, DateRange range,
                     ChatbotTool.ToolContext ctx) {
        long started = System.currentTimeMillis();

        if (!properties.isEnabled()) {
            return Reply.of(null, "The assistant is switched off on this deployment. "
                    + "Figures are still available from the reports.", true,
                    "The chatbot is disabled (app.chatbot.enabled=false).",
                    false, false);
        }

        if (ctx == null) {
            throw new AccessDeniedException("You do not have permission to view this information.");
        }

        if (!rateLimitGuard.allow(ctx)) {
            return Reply.of(null, "You have asked several questions in a short time. "
                    + "Wait a moment and ask again.", true,
                    "Rate limited: " + properties.getMessagesPerWindow() + " messages per "
                            + properties.getWindowSeconds() + " seconds.",
                    false, false);
        }

        // The conversation is checked for ownership before it is used, so a caller
        // cannot append to somebody else's conversation by knowing its id.
        ChatConversation conversation = resolveConversation(conversationId, ctx);

        String clean = PromptSanitiser.sanitise(question);
        if (clean == null || clean.isBlank()) {
            return Reply.of(null, "I could not read that question. Please try again.", true,
                    "Empty after sanitising.", false, false);
        }

        messageRepository.save(ChatMessage.builder()
                .conversation(conversation)
                .role(ChatMessage.Role.USER)
                .content(truncate(clean))
                .build());

        // The list is ordered by last message, so the conversation
        // must carry one from the moment it has one.
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        Intent intent = classify(clean);
        if (intent.intent() == null || intent.confidence() < properties.getAssistantMinConfidence()) {
            List<String> suggestions = toolRegistry.capabilityNames(ctx);
            String text = "I could not tell what you are asking about. Here is what I can help with:"
                    + renderSuggestions(suggestions);
            return record(conversation, text, null, ctx, started,
                    false, "The question did not match a known capability.");
        }

        List<String> tools = Tools.forIntent(intent.intent());
        if (tools.isEmpty()) {
            return record(conversation, "I do not know how to answer that yet.", null, ctx,
                    started, false, "No tools for intent " + intent.intent());
        }

        // An explicit period from the caller's own filter wins over the
        // question's words: the reader has already said which window they
        // mean, and every financial tool reads its period from these params.
        // Without this the selected range would decorate the answer's last
        // line while the figures underneath covered some other window.
        Map<String, Object> params = new LinkedHashMap<>(intent.params());
        if (range != null && range.from() != null && range.to() != null) {
            params.put("from", range.from());
            params.put("to", range.to());
        }
        // An order number the question names becomes the lookup key, uppercased
        // because that is how the records store it. Absent a number the tool
        // asks for one instead of guessing at a nearby order.
        Matcher named = ORDER_NUMBER.matcher(clean);
        if (named.find()) {
            params.put("orderNumber", named.group(0).toUpperCase(Locale.ROOT));
        }
        // The support tool files its ticket against the customer's own words,
        // and the question is those words — asking them to restate a reason
        // they have already given would be theatre. "reason" is read by that
        // one tool and nothing else, so carrying it for every intent is inert.
        params.putIfAbsent("reason", clean);

        Map<String, Object> toolResults = new LinkedHashMap<>();
        for (String tool : tools) {
            if (!toolRegistry.isPermitted(tool, ctx)) {
                if (ChatbotTool.CUSTOMER_TOOL.equals(toolRegistry.permissionOf(tool))) {
                    // Matched a customer tool while staff. Not a permission
                    // problem — a question that belongs to the other channel.
                    return record(conversation,
                            "That is a customer question, and the staff assistant "
                            + "does not answer it. Ask about sales, stock, profit, "
                            + "balances or a customer's own order.",
                            null, ctx, started, false,
                            "The question matched a customer-only tool and the caller is staff");
                }
                return record(conversation,
                        "You do not have permission to view the information that answer needs.",
                        null, ctx, started, false,
                        "Tool " + tool + " needs a permission this caller lacks");
            }
            Map<String, Object> result;
            try {
                result = toolRegistry.execute(tool, params, ctx);
            } catch (UnknownToolException e) {
                // A tool that was classified for but does not exist is a defect in
                // the intent mapping, not a caller error.
                log.error("Intent {} mapped to unknown tool {}", intent.intent(), e.getMessage());
                return record(conversation, "I could not complete that answer. Please try again.",
                        null, ctx, started, false, "Unknown tool " + e.getMessage());
            } catch (SecurityException e) {
                return record(conversation,
                        "You do not have permission to view the information that answer needs.",
                        null, ctx, started, false, "Refused by tool " + tool);
            }
            toolResults.put(tool, result);
            messageRepository.save(ChatMessage.builder()
                    .conversation(conversation)
                    .role(ChatMessage.Role.TOOL)
                    .toolName(tool)
                    .content(truncate(write(result)))
                    .build());
        }

        String rendered = render(intent.intent(), toolResults, range);
        Set<BigDecimal> allowed = NumberGuard.collect(toolResults);
        Set<String> allowedText = new HashSet<>(toolResults.keySet());

        String phrased = null;
        boolean deterministic = true;
        boolean guardIntervened = false;

        if (properties.isLlmPhrasingAllowed()) {
            ChatbotProvider provider = providerFor(properties.getProvider());
            if (provider != null && provider.available()) {
                String context = write(toolResults);
                if (context.length() > properties.getMaxContextChars()) {
                    context = context.substring(0, properties.getMaxContextChars());
                }
                String audience = ctx.isCustomer() ? "a customer of this store" : "a member of staff";
                ChatbotProvider.ChatCompletionResponse response = provider.generate(
                        new ChatbotProvider.ChatRequest(
                                PromptSanitiser.systemPrompt(audience),
                                PromptSanitiser.sanitise(clean),
                                transcript(conversation),
                                Map.of("tool_results", context)));

                // DeterministicProvider answers usable-and-empty to mean "use
                // your own rendering". A blank reply must not replace the
                // rendered answer with nothing — that contract has to be
                // honoured here, or the default provider silently blanks every
                // end-of-answer reply while appearing to succeed.
                if (response != null && response.usable() && response.text() != null
                        && !response.text().isBlank()) {
                    if (NumberGuard.numbersAreSafe(response.text(), allowed, allowedText)) {
                        phrased = response.text();
                        deterministic = false;
                    } else {
                        // The model invented a number. That is exactly what the
                        // check is for, and it is logged rather than quietly fixed.
                        guardIntervened = true;
                        log.warn("Phrasing layer produced a figure not present in the tool "
                                + "results for conversation {}. Using the deterministic reply. "
                                + "Offending text: {}", conversation.getId(),
                                NumberGuard.scrub(response.text()));
                        auditLogService.log(actorId(ctx), actorName(ctx),
                                "CHATBOT_GUARD_INTERVENED", "CHAT_CONVERSATION",
                                String.valueOf(conversation.getId()),
                                "A number in the phrased answer was not in the tool results");
                    }
                }
            }
        }

        String text = deterministic ? rendered : phrased;
        if (guardIntervened) {
            text = rendered;
        }

        return record(conversation, text, intent.intent(), ctx, started, deterministic,
                guardIntervened
                        ? "The phrasing layer was replaced because it introduced a figure "
                        + "the tools did not return."
                        : null,
                guardIntervened, phrased, toolResults);
    }

    // ── Answering ──────────────────────────────────────────────────────────────

    /**
     * Renders an answer from tool results.
     *
     * <p>This is the deterministic phrasing path, and it is the one that always
     * runs when no provider is configured. It contains every figure in the answer,
     * which is what lets {@link NumberGuard} check a provider's prose against it.
     */
    private String render(String intent, Map<String, Object> toolResults, DateRange range) {
        StringBuilder out = new StringBuilder();

        switch (intent) {
            case "NET_PROFIT", "GROSS_PROFIT" -> {
                Map<String, Object> profit = first(toolResults, "profit");
                if (profit == null) {
                    return "There is no recorded activity for that period, so there is no profit figure.";
                }
                boolean net = "NET_PROFIT".equals(intent);
                BigDecimal value = (BigDecimal) (net ? profit.get("netProfit") : profit.get("grossProfit"));
                BigDecimal margin = (BigDecimal) (net ? profit.get("netMarginPercent")
                        : profit.get("grossMarginPercent"));
                out.append(String.format("%s for %s was ETB %s, a margin of %s%%.",
                        net ? "Net profit" : "Gross profit",
                        String.valueOf(profit.get("period")),
                        NumberGuard.money(value), margin));
                out.append(String.format(" That is net sales of ETB %s less cost of goods sold of ETB %s,",
                        NumberGuard.money((BigDecimal) profit.get("netSales")),
                        NumberGuard.money((BigDecimal) profit.get("costOfGoodsSold"))));
                if (net) {
                    out.append(String.format(" after operating expenses of ETB %s.",
                            NumberGuard.money((BigDecimal) profit.get("operatingExpenses"))));
                }
                appendBasis(out, profit);
            }
            case "FINANCIAL_COMPARISON" -> {
                Map<String, Object> compare = first(toolResults, "financial_comparison");
                if (compare == null) {
                    return "There is not enough recorded activity to compare periods.";
                }
                out.append(String.format("Comparing %s with %s:",
                        compare.get("period"), compare.get("previousPeriod")));
                appendChange(out, "Net sales", (BigDecimal) compare.get("netSales"),
                        (BigDecimal) compare.get("netSales_Previous"));
                appendChange(out, "Gross profit", (BigDecimal) compare.get("grossProfit"),
                        (BigDecimal) compare.get("grossProfit_Previous"));
                appendChange(out, "Operating expenses", (BigDecimal) compare.get("operatingExpenses"),
                        (BigDecimal) compare.get("operatingExpenses_Previous"));
                appendChange(out, "Net profit", (BigDecimal) compare.get("netProfit"),
                        (BigDecimal) compare.get("netProfit_Previous"));
                appendChange(out, "Cash received", (BigDecimal) compare.get("cashReceived"),
                        (BigDecimal) compare.get("cashReceived_Previous"));
                appendNote(out, compare, "note");
            }
            case "CASH_POSITION" -> {
                Map<String, Object> cash = first(toolResults, "cash_position");
                if (cash == null) {
                    return "There is no recorded cash movement for that period.";
                }
                out.append(String.format("For %s: ETB %s was received from customers, ETB %s was paid "
                                + "to suppliers, ETB %s was refunded, and ETB %s of approved expenses were paid. "
                                + "Net cash movement was ETB %s.",
                        cash.get("period"),
                        NumberGuard.money((BigDecimal) cash.get("cashReceived")),
                        NumberGuard.money((BigDecimal) cash.get("cashPaidToSuppliers")),
                        NumberGuard.money((BigDecimal) cash.get("cashRefunded")),
                        NumberGuard.money((BigDecimal) cash.get("operatingExpenses")),
                        NumberGuard.money((BigDecimal) cash.get("netCashFlow"))));
                out.append(String.format(" Of that, ETB %s is still owed by customers.",
                        NumberGuard.money((BigDecimal) cash.get("accountsReceivable"))));
                appendNote(out, cash, "note");
            }
            case "RECEIVABLES" -> {
                Map<String, Object> rec = first(toolResults, "receivables");
                if (rec == null) {
                    return "There are no outstanding customer balances.";
                }
                out.append(String.format("Customers owe ETB %s in total as at %s, across %s account%s.",
                        NumberGuard.money((BigDecimal) rec.get("totalOutstanding")),
                        rec.get("asOf"), rec.get("accountCount"),
                        Objects.equals(rec.get("accountCount"), 1) ? "" : "s"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> overdue = (List<Map<String, Object>>) rec.get("overdueCount");
                if (overdue != null && !overdue.isEmpty()) {
                    out.append(String.format(" %s of those are older than 30 days.", overdue.size()));
                }
                appendList(out, rec, "accounts", "customer", "outstanding");
                appendNote(out, rec, "note");
            }
            case "PAYABLES" -> {
                Map<String, Object> pay = first(toolResults, "payables");
                if (pay == null) {
                    return "Every received purchase order has been paid in full.";
                }
                out.append(String.format("%s received purchase order%s still ha%s money unpaid, "
                                + "totalling ETB %s as at %s.",
                        pay.get("orderCount"),
                        Objects.equals(pay.get("orderCount"), 1) ? "" : "s",
                        Objects.equals(pay.get("orderCount"), 1) ? "s" : "ve",
                        NumberGuard.money((BigDecimal) pay.get("totalOutstanding")),
                        pay.get("asOf")));
                appendList(out, pay, "orders", "supplier", "balanceDue");
                appendNote(out, pay, "note");
            }
            case "STOCKOUT_RISK" -> {
                Map<String, Object> risk = first(toolResults, "stockout_risk");
                if (risk == null) {
                    return "No product is projected to run out in that period.";
                }
                out.append(String.format("%s product%s projected to run out within %s days:",
                        risk.get("riskCount"),
                        Objects.equals(risk.get("riskCount"), 1) ? " is" : "s are",
                        risk.get("horizonDays")));
                appendList(out, risk, "risks", "name", "daysOfStockRemaining");
                appendNote(out, risk, "note");
            }
            case "SLOW_MOVING" -> {
                Map<String, Object> slow = first(toolResults, "slow_moving");
                if (slow == null) {
                    return "Every product holding stock recorded a sale recently.";
                }
                out.append(String.format("%s product%s holding stock recorded no sale in the last %s days, "
                                + "worth ETB %s at cost:",
                        slow.get("productCount"),
                        Objects.equals(slow.get("productCount"), 1) ? "" : "s",
                        slow.get("lookbackDays"),
                        NumberGuard.money((BigDecimal) slow.get("totalValueAtCost"))));
                appendList(out, slow, "products", "name", "valueAtCost");
                appendNote(out, slow, "note");
            }
            case "INVENTORY_VALUE" -> {
                Map<String, Object> value = first(toolResults, "inventory_value");
                if (value == null) {
                    return "There is no stock to value.";
                }
                out.append(String.format("Stock on hand is worth ETB %s at weighted-average cost as at %s.",
                        NumberGuard.money((BigDecimal) value.get("inventoryValue")), value.get("asOf")));
                appendBasis(out, value);
            }
            case "SEARCH_PRODUCTS" -> {
                Map<String, Object> search = first(toolResults, "search_products");
                if (search == null) {
                    return "No product matched that search.";
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) search.get("results");
                if (results == null || results.isEmpty()) {
                    out.append("No active product matches ")
                            .append(String.valueOf(search.get("searched")))
                            .append(". Try a different word, or ask me to list categories.");
                } else {
                    out.append(String.format("%s product%s matched:",
                            search.get("matched"),
                            Objects.equals(search.get("matched"), 1) ? "" : "s"));
                    for (Map<String, Object> row : results) {
                        out.append(String.format("\n• %s (%s) — ETB %s%s",
                                row.get("name"), row.get("sku"),
                                NumberGuard.money((BigDecimal) row.get("price")),
                                Boolean.TRUE.equals(row.get("inStock")) ? "" : " — out of stock"));
                    }
                }
                appendNote(out, search, "note");
            }
            case "CUSTOMER_ORDER_STATUS" -> {
                Map<String, Object> order = first(toolResults, "customer_order_status");
                if (order == null || !Boolean.TRUE.equals(order.get("found"))) {
                    return order == null
                            ? "There is no order by that number on your account."
                            : String.valueOf(order.get("note"));
                }
                out.append(String.format("Order %s was placed on %s and is recorded as %s, "
                                + "with payment status %s.",
                        order.get("orderNumber"), order.get("orderDate"),
                        order.get("status"), order.get("paymentStatus")));
                out.append(String.format(" The total is ETB %s, of which ETB %s is still outstanding.",
                        NumberGuard.money((BigDecimal) order.get("total")),
                        NumberGuard.money((BigDecimal) order.get("outstanding"))));
                if (Boolean.FALSE.equals(order.get("deliveryTracked"))) {
                    out.append(" Delivery is not tracked here, so I cannot say where a delivery is.");
                }
                appendNote(out, order, "note");
            }
            case "CUSTOMER_ORDER_HISTORY" -> {
                Map<String, Object> history = first(toolResults, "customer_order_history");
                if (history == null) {
                    return "There are no orders on your account yet.";
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> orders = (List<Map<String, Object>>) history.get("orders");
                if (orders == null || orders.isEmpty()) {
                    out.append("There are no orders on your account yet.");
                } else {
                    out.append(String.format("You have %s order%s:",
                            history.get("orderCount"),
                            Objects.equals(history.get("orderCount"), 1) ? "" : "s"));
                    for (Map<String, Object> row : orders) {
                        out.append(String.format("\n• %s, %s — ETB %s, %s",
                                row.get("orderNumber"), row.get("date"),
                                NumberGuard.money((BigDecimal) row.get("total")),
                                row.get("paymentStatus")));
                    }
                }
                appendNote(out, history, "note");
            }
            case "STORE_POLICIES" -> {
                Map<String, Object> policies = first(toolResults, "store_policies");
                if (policies == null) {
                    return "I do not have a written policy for that topic.";
                }
                if (policies.get("text") != null) {
                    out.append(policies.get("text"));
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, String> available = (Map<String, String>) policies.get("available");
                    if (available != null) {
                        out.append("I can tell you about: ")
                                .append(String.join(", ", available.keySet()))
                                .append(".");
                    }
                }
            }
            case "PAYMENT_METHODS" -> {
                Map<String, Object> methods = first(toolResults, "payment_methods");
                if (methods == null) {
                    return "I do not have that information.";
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) methods.get("methods");
                if (list != null) {
                    out.append("The store records these ways to pay:");
                    for (Map<String, Object> method : list) {
                        out.append(String.format("\n• %s — %s", method.get("method"), method.get("note")));
                    }
                }
                appendNote(out, methods, "note");
            }
            case "HOW_TO_ORDER" -> {
                Map<String, Object> how = first(toolResults, "how_to_order");
                if (how == null) {
                    return "I do not have that information.";
                }
                @SuppressWarnings("unchecked")
                List<String> steps = (List<String>) how.get("steps");
                if (steps != null) {
                    out.append("To place an order:");
                    int i = 1;
                    for (String step : steps) {
                        out.append(String.format("\n%d. %s", i++, step));
                    }
                }
                appendNote(out, how, "note");
            }
            case "PRODUCT_AVAILABILITY" -> {
                Map<String, Object> stock = first(toolResults, "product_availability");
                if (stock == null || !Boolean.TRUE.equals(stock.get("available"))) {
                    return stock == null
                            ? "That product is not currently sold."
                            : "That product is not currently in stock.";
                }
                out.append(String.format("%s is in stock: %s units in total",
                        stock.get("name"), stock.get("totalUnits")));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> byWarehouse = (List<Map<String, Object>>) stock.get("byWarehouse");
                if (byWarehouse != null && !byWarehouse.isEmpty()) {
                    out.append(" (");
                    List<String> parts = new ArrayList<>();
                    for (Map<String, Object> row : byWarehouse) {
                        parts.add(row.get("warehouse") + ": " + row.get("units"));
                    }
                    out.append(String.join(", ", parts)).append(")");
                }
                out.append(String.format(". The catalogue price is ETB %s.",
                        NumberGuard.money((BigDecimal) stock.get("price"))));
                appendNote(out, stock, "note");
            }
            case "PRODUCT_ALTERNATIVES" -> {
                Map<String, Object> alt = first(toolResults, "product_alternatives");
                if (alt == null) {
                    return "I could not find alternatives for that product.";
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> same = (List<Map<String, Object>>) alt.get("sameCategory");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> budget = (List<Map<String, Object>>) alt.get("withinBudget");
                if ((same == null || same.isEmpty()) && (budget == null || budget.isEmpty())) {
                    out.append("There is nothing else in that category and nothing else within that budget.");
                } else {
                    if (same != null && !same.isEmpty()) {
                        out.append("Other products in the same category:");
                        for (Map<String, Object> row : same) {
                            out.append(String.format("\n• %s — ETB %s",
                                    row.get("name"),
                                    NumberGuard.money((BigDecimal) row.get("price"))));
                        }
                    }
                    if (budget != null && !budget.isEmpty()) {
                        out.append("\nWithin your budget:");
                        for (Map<String, Object> row : budget) {
                            out.append(String.format("\n• %s — ETB %s",
                                    row.get("name"),
                                    NumberGuard.money((BigDecimal) row.get("price"))));
                        }
                    }
                }
                appendNote(out, alt, "note");
            }
            case "RAISE_SUPPORT_REQUEST" -> {
                Map<String, Object> raised = first(toolResults, "raise_support_request");
                if (raised == null || !Boolean.TRUE.equals(raised.get("raised"))) {
                    return raised == null
                            ? "I could not raise that request. Please ask a member of staff directly."
                            : String.valueOf(raised.get("note"));
                }
                out.append("I have asked a member of staff to look at this.")
                        .append(String.valueOf(raised.get("note")));
            }
            default -> out.append("I could not complete that answer. Please try asking it another way.");
        }

        // The reporting period and a link to the source report are appended to
        // every answer that has them, so a reader can check the figure.
        if (range != null) {
            out.append(String.format("\n\nPeriod: %s to %s.", range.from(), range.to()));
        }
        for (Object value : toolResults.values()) {
            if (value instanceof Map<?, ?> map && map.get("reportUrl") != null) {
                out.append("\nSource: ").append(map.get("reportUrl"));
                break;
            }
        }
        return out.toString();
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private void appendBasis(StringBuilder out, Map<String, Object> result) {
        Object basis = result.get("basis");
        if (basis != null && !String.valueOf(basis).isBlank()) {
            out.append("\n\nBasis: ").append(basis);
        }
    }

    private void appendNote(StringBuilder out, Map<String, Object> result, String key) {
        Object note = result.get(key);
        if (note != null && !String.valueOf(note).isBlank()) {
            out.append("\n").append(note);
        }
    }

    private void appendChange(StringBuilder out, String label, BigDecimal current, BigDecimal previous) {
        if (previous == null) {
            out.append(String.format("\n• %s: ETB %s. No figure for the previous period.",
                    label, NumberGuard.money(current)));
            return;
        }
        BigDecimal change = current.subtract(previous);
        if (previous.signum() == 0) {
            out.append(String.format("\n• %s: ETB %s. The previous period was zero, so no percentage "
                    + "change is meaningful.", label, NumberGuard.money(current)));
            return;
        }
        BigDecimal percent = change.multiply(new BigDecimal("100"))
                .divide(previous.abs(), 1, java.math.RoundingMode.HALF_UP);
        out.append(String.format("\n• %s: ETB %s (was ETB %s, %s%s%%).",
                label, NumberGuard.money(current), NumberGuard.money(previous),
                change.signum() >= 0 ? "+" : "", percent.toPlainString()));
    }

    private void appendList(StringBuilder out, Map<String, Object> result, String key,
                            String nameField, String valueField) {
        Object list = result.get(key);
        if (!(list instanceof List<?> items) || items.isEmpty()) {
            return;
        }
        for (Object item : items) {
            if (item instanceof Map<?, ?> row) {
                Object name = row.get(nameField);
                Object value = row.get(valueField);
                out.append(String.format("\n• %s — ETB %s",
                        name, value instanceof BigDecimal decimal
                                ? NumberGuard.money(decimal) : value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> first(Map<String, Object> results, String tool) {
        Object value = results.get(tool);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    // ── Intent ────────────────────────────────────────────────────────────────

    /**
     * Maps a question onto an intent.
     *
     * <p>Keyword scoring, weighted so a more specific phrase beats a more general
     * one: "overdue balances" contains "balance" but also "overdue", and the more
     * specific phrase must win. Each intent requires at least two hits, so a single
     * common word cannot match everything.
     */
    Intent classify(String question) {
        // Matching is case-insensitive. A person writes "Place an order",
        // "Where is my order" and "Net profit" — an intent that only fires
        // in lowercase is an intent that does not fire for most questions.
        String q = question.toLowerCase(Locale.ROOT);
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.merge("NET_PROFIT", score(q, "net profit", "profit after expenses",
                "bottom line", "net income"), Integer::sum);
        scores.merge("GROSS_PROFIT", score(q, "gross profit", "gross margin",
                "margin on sales"), Integer::sum);
        scores.merge("FINANCIAL_COMPARISON", score(q, "compare", "versus", " vs ",
                "compared with", "compared to", "difference between", "against last"), Integer::sum);
        scores.merge("CASH_POSITION", score(q, "cash received", "cash flow", "money in",
                "money received", "how much cash", "cash position", "cash in", "cash out"), Integer::sum);
        scores.merge("RECEIVABLES", score(q, "overdue", "receivable", "who owes",
                "outstanding balance", "customers owe", "unpaid customer", "debtors"), Integer::sum);
        scores.merge("PAYABLES", score(q, "payable", "owe supplier", "suppliers owe",
                "supplier balance", "unpaid purchase"), Integer::sum);
        scores.merge("INVENTORY_VALUE", score(q, "inventory value", "stock value",
                "worth of stock", "value of stock"), Integer::sum);
        scores.merge("STOCKOUT_RISK", score(q, "run out", "stockout", "run out of stock",
                "likely to run", "will run out", "stock out", "shortage"), Integer::sum);
        scores.merge("SLOW_MOVING", score(q, "not selling", "slow moving", "slow-moving",
                "sitting still", "dead stock"), Integer::sum);
        scores.merge("SEARCH_PRODUCTS", score(q, "do you have", "do you sell", "looking for",
                "find me", "search for", "is there", "in stock"), Integer::sum);
        scores.merge("PRODUCT_AVAILABILITY", score(q, "in stock", "available", "do you have any",
                "how many do you have"), Integer::sum);
        scores.merge("PRODUCT_ALTERNATIVES", score(q, "alternative", "something else",
                "similar", "instead of", "cheaper"), Integer::sum);
        scores.merge("CUSTOMER_ORDER_STATUS", score(q, "my order", "order status", "where is my order",
                "order number", "my parcel", "delivery"), Integer::sum);
        scores.merge("CUSTOMER_ORDER_HISTORY", score(q, "my orders", "order history",
                "what have i bought", "my purchases", "my account"), Integer::sum);
        scores.merge("STORE_POLICIES", score(q, "policy", "policies", "refund", "returns",
                "can i return", "store policy"), Integer::sum);
        scores.merge("PAYMENT_METHODS", score(q, "how do i pay", "payment method", "ways to pay",
                "can i pay by", "accept"), Integer::sum);
        scores.merge("HOW_TO_ORDER", score(q, "how do i order", "how to order", "place an order",
                "place my order", "buy"), Integer::sum);
        scores.merge("RAISE_SUPPORT_REQUEST", score(q, "speak to someone", "talk to someone",
                "human", "person", "support", "help me with a problem"), Integer::sum);

        List<Map.Entry<String, Integer>> ranked = scores.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

        if (ranked.isEmpty()) {
            return new Intent(null, 0, Map.of());
        }
        int top = ranked.get(0).getValue();
        int second = ranked.size() > 1 ? ranked.get(1).getValue() : 0;
        double confidence = second == 0 ? 1.0 : (double) top / (top + second);
        return new Intent(ranked.get(0).getKey(), confidence, Map.of());
    }

    private int score(String question, String... phrases) {
        int total = 0;
        for (String phrase : phrases) {
            if (question.contains(phrase.toLowerCase(Locale.ROOT))) {
                // Weighted by length, because length is what makes a phrase
                // specific: "speak to someone" names an intent outright, while
                // "my order" is a fragment that appears in a request for help
                // about an order just as readily as a request for its status.
                // Without this the two score alike and the tie falls to
                // whichever intent happened to be written down first.
                int words = phrase.trim().split("\\s+").length;
                total += words >= 3 ? 4 : words == 2 ? 3 : 2;
            }
        }
        return total;
    }

    // ── Identity and scoping ──────────────────────────────────────────────────

    /**
     * Who is asking, when the caller is a member of staff.
     *
     * <p>Resolved from the security context, so the authorities are the
     * ones the backend actually granted at login — never the ones the
     * message claims. Returns null when there is no staff session,
     * which is how the controller tells a staff call from a customer one.
     */
    public ChatbotTool.ToolContext staffContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            return null;
        }
        String roleName = user.getUser().getRole().getName();
        return ChatbotTool.ToolContext.staff(user.getId(),
                RolePermissionCatalog.permissionsFor(roleName));
    }

    /**
     * The context for a validated customer session.
     *
     * <p>The customer's id and token hash come from the session the
     * controller resolved, never from the message. Every customer tool
     * filters on that id, which is what keeps one customer's orders out
     * of another's answer.
     */
    public ChatbotTool.ToolContext customerContext(Long customerId, String tokenHash) {
        return ChatbotTool.ToolContext.customer(customerId, tokenHash,
                Set.of(ChatbotTool.CUSTOMER_TOOL));
    }

    private ChatConversation resolveConversation(Long conversationId, ChatbotTool.ToolContext ctx) {
        if (conversationId == null) {
            ChatConversation created = conversationRepository.save(ChatConversation.builder()
                    .actorType(ctx.actorType())
                    .actorId(ctx.actorId())
                    .sessionTokenHash(hash(ctx.sessionTokenHash()))
                    .title("Conversation " + LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(ctx.isCustomer()
                            ? properties.getCustomerRetentionDays()
                            : properties.getStaffRetentionDays()))
                    .build());
            created.setLastMessageAt(LocalDateTime.now());
            return conversationRepository.save(created);
        }
        ChatConversation conversation;
        if (ctx.isCustomer()) {
            conversation = conversationRepository
                    .findCustomerConversation(conversationId, ctx.actorId(), hash(ctx.sessionTokenHash()))
                    .orElse(null);
        } else {
            conversation = conversationRepository
                    .findStaffConversation(conversationId, ctx.actorId())
                    .orElse(null);
        }
        if (conversation == null) {
            // A conversation that does not belong to this caller is refused rather
            // than replaced, so a caller probing ids learns nothing.
            throw new AccessDeniedException("You do not have permission to view this information.");
        }
        return conversation;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private Reply record(ChatConversation conversation, String text, String intent,
                         ChatbotTool.ToolContext ctx, long started,
                         boolean deterministic, String note, boolean guardIntervened,
                         String providerText, Map<String, Object> toolResults) {
        messageRepository.save(ChatMessage.builder()
                .conversation(conversation)
                .role(ChatMessage.Role.ASSISTANT)
                .content(text)
                .deterministic(deterministic)
                .guardIntervened(guardIntervened)
                .build());

        if (providerText != null) {
            // Kept separately from the answer that was shown, so a review can see
            // both what the caller read and what the provider wanted them to read.
            messageRepository.save(ChatMessage.builder()
                    .conversation(conversation)
                    .role(ChatMessage.Role.TOOL)
                    .toolName("phrasing_draft")
                    .content(truncate(providerText))
                    .build());
        }

        auditLogService.log(actorId(ctx), actorName(ctx), "CHATBOT_QUESTION",
                "CHAT_CONVERSATION", String.valueOf(conversation.getId()),
                "intent=" + intent + ", tools=" + toolResults.keySet());

        return new Reply(conversation.getId(), text, deterministic, note,
                toolResults, true, guardIntervened);
    }

    private Reply record(ChatConversation conversation, String text, String intent,
                         ChatbotTool.ToolContext ctx, long started,
                         boolean deterministic, String note) {
        return record(conversation, text, intent, ctx, started, deterministic,
                note, false, null, Map.of());
    }

    // ── Conversation history ─────────────────────────────────────────────────

    /** A caller's conversations, newest first. Ownership is in the query. */
    @Transactional(readOnly = true)
    public List<ChatConversation> conversations(ChatbotTool.ToolContext ctx) {
        return conversationRepository
                .findByActorTypeAndActorIdOrderByLastMessageAtDesc(ctx.actorType(), ctx.actorId());
    }

    /** One conversation's messages, oldest first, after an ownership check. */
    @Transactional(readOnly = true)
    public List<ChatMessage> history(Long conversationId, ChatbotTool.ToolContext ctx) {
        ChatConversation conversation = resolveConversation(conversationId, ctx);
        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());
    }

    /** Starts a new conversation for this caller. */
    @Transactional
    public ChatConversation startConversation(ChatbotTool.ToolContext ctx, String title) {
        return conversationRepository.save(ChatConversation.builder()
                .actorType(ctx.actorType())
                .actorId(ctx.actorId())
                .sessionTokenHash(hash(ctx.sessionTokenHash()))
                .title(truncate(title) == null ? "Conversation" : truncate(title))
                .expiresAt(LocalDateTime.now().plusDays(ctx.isCustomer()
                        ? properties.getCustomerRetentionDays()
                        : properties.getStaffRetentionDays()))
                .build());
    }

    /**
     * Deletes a caller's own conversation, messages and all.
     *
     * <p>The retention policy sets how long a conversation is
     * kept; this is the other half of it — the right to have one
     * gone now. Ownership is checked first, so an id is not a
     * delete key.
     */
    @Transactional
    public void deleteConversation(Long conversationId, ChatbotTool.ToolContext ctx) {
        ChatConversation conversation = resolveConversation(conversationId, ctx);
        messageRepository.deleteByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }

    // ── Customer sessions ──────────────────────────────────────────────

    /**
     * Opens a customer session.
     *
     * <p>A shopper proves they can see a given order by naming the
     * email and the order number on it — both printed on a receipt.
     * From then on the session is scoped to that customer's own id,
     * so every question is answered from their rows alone and no
     * other customer's data is reachable.
     *
     * <p>The check is a match on the pair, not on either alone: an
     * order number is on a receipt, an email is in a CRM, but
     * together they are evidence the caller is this customer. A
     * mismatch returns the same failure as a nonexistent order, so
     * neither can be probed for the other.
     *
     * @return the session, carrying the token exactly once
     * @throws InvalidCustomerCredentialsException when the pair does
     *         not resolve to an active customer
     */
    @Transactional
    public ChatCustomerSession openSession(String email, String orderNumber) {
        if (email == null || email.isBlank() || orderNumber == null || orderNumber.isBlank()) {
            throw new InvalidCustomerCredentialsException();
        }

        Customer customer = saleOrderRepository
                .findCustomerByEmailAndOrderNumber(email.trim(), orderNumber.trim()).stream()
                .filter(c -> c.getStatus() == CustomerStatus.ACTIVE)
                .findFirst()
                .orElseThrow(InvalidCustomerCredentialsException::new);

        String token = randomToken();
        ChatCustomerSession session = customerSessionRepository.save(ChatCustomerSession.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .tokenHash(hash(token))
                .verifiedOrderNumber(orderNumber.trim().toUpperCase(Locale.ROOT))
                .expiresAt(LocalDateTime.now().plusHours(properties.getSessionTtlHours()))
                .build());

        auditLogService.log(null, "chatbot", "CUSTOMER_SESSION_OPENED",
                "CHAT_CUSTOMER_SESSION", String.valueOf(session.getId()),
                "customer=" + customer.getId());

        // The plain token is attached here and nowhere else. The stored
        // value is only ever the hash, so this is the one moment the
        // caller can read it.
        session.setTokenPlain(token);
        return session;
    }

    /**
     * Resolves a customer session token to a context.
     *
     * <p>The hash is looked up with its expiry, so an expired or
     * unknown token resolves to empty and the controller answers
     * 401. The caller never learns which of those it was.
     */
    public ChatbotTool.ToolContext resolveCustomerSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return customerSessionRepository
                .findByTokenHashAndExpiresAtAfter(hash(token), LocalDateTime.now())
                .map(session -> ChatbotTool.ToolContext.customer(
                        session.getCustomerId(),
                        session.getTokenHash(),
                        Set.of(ChatbotTool.CUSTOMER_TOOL)))
                .orElse(null);
    }

    /** A customer's open sessions, for a "you are signed in as" view. */
    @Transactional(readOnly = true)
    public List<ChatCustomerSession> openSessions(Long customerId) {
        return customerSessionRepository
                .findByCustomerIdAndExpiresAtAfter(customerId, LocalDateTime.now());
    }

    /** Ends a customer session, on sign-out or on request. */
    @Transactional
    public void closeSession(String token) {
        if (token == null) {
            return;
        }
        customerSessionRepository
                .findByTokenHashAndExpiresAtAfter(hash(token), LocalDateTime.now())
                .ifPresent(customerSessionRepository::delete);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Which provider answers, given the configuration.
     *
     * <p>Anything other than a configured {@code openai-compatible}
     * provider resolves to the deterministic one, so a typo in
     * {@code app.chatbot.provider} degrades to working rather than
     * to broken.
     */
    private ChatbotProvider providerFor(String name) {
        if ("openai-compatible".equalsIgnoreCase(name) && properties.hasApiKey()) {
            return openAiProvider;
        }
        return deterministicProvider;
    }

    private List<ChatbotProvider.ChatRequest.Turn> transcript(ChatConversation conversation) {
        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId())
                .stream()
                .filter(message -> message.getRole() == ChatMessage.Role.USER
                        || message.getRole() == ChatMessage.Role.ASSISTANT)
                .limit(10)
                .map(message -> new ChatbotProvider.ChatRequest.Turn(
                        message.getRole() == ChatMessage.Role.USER ? "user" : "assistant",
                        truncate(PromptSanitiser.stripSecrets(message.getContent()))))
                .toList();
    }

    private static String write(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        int max = 4000;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String renderSuggestions(List<String> suggestions) {
        if (suggestions.isEmpty()) {
            return "";
        }
        return suggestions.stream().map(s -> "\n• " + s).reduce("", String::concat);
    }

    private Long actorId(ChatbotTool.ToolContext ctx) {
        return ctx.actorId();
    }

    private String actorName(ChatbotTool.ToolContext ctx) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "customer-" + ctx.actorId() : authentication.getName();
    }

    /**
     * Hashes a session token for storage.
     *
     * <p>Stored hashed so a database leak does not hand over working tokens. The
     * salt is fixed rather than per-token because the token itself is already a
     * high-entropy random value; what matters here is that the stored value is not
     * usable as a credential.
     */
    private static String hash(String token) {
        if (token == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    ("chatbot-session:" + token).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * A session token.
     *
     * <p>128 bits from a secure random, hex-encoded. Entropy matters
     * here: the token is the only thing standing between a stranger
     * and a customer's order history, and it has to be unguessable
     * rather than merely inconvenient.
     */
    private static String randomToken() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    // ── Records ────────────────────────────────────────────────────────────────

    /**
     * The period an answer covers.
     *
     * <p>Named and dated rather than implicit, because "net profit" without a range
     * is not an answer.
     */
    public record DateRange(String from, String to) {
    }

    /**
     * A chatbot reply.
     *
     * @param conversationId the conversation this belongs to, so the
     *                       caller can continue it. Null only when no
     *                       conversation was reached — a disabled
     *                       assistant or a rate-limited caller.
     * @param text           what the caller reads
     * @param deterministic  true when the deterministic phrasing was used
     * @param note           why, when the deterministic phrasing was substituted
     * @param toolResults    the figures behind the answer, for structured cards
     */
    public record Reply(Long conversationId, String text, boolean deterministic,
                        String note, Map<String, Object> toolResults,
                        boolean answered, boolean guardIntervened) {

        public static Reply of(Long conversationId, String text, boolean deterministic,
                              String note, boolean answered, boolean guardIntervened) {
            return new Reply(conversationId, text, deterministic, note,
                    Map.of(), answered, guardIntervened);
        }
    }

    /** An intent a question resolved to. */
    record Intent(String intent, double confidence, Map<String, Object> params) {
    }

    /** A question no capability matched. */
    public static class UnknownToolException extends RuntimeException {
        public UnknownToolException(String tool) {
            super(tool);
        }
    }

    /**
     * A customer session could not be opened.
     *
     * <p>Deliberately carries no detail, so the failure message is the
     * same whether the order number is wrong, the email is wrong, or
     * the pair belongs to a closed account. Distinguishing them would
     * let somebody probe for a customer's order numbers.
     */
    public static class InvalidCustomerCredentialsException extends RuntimeException {
        public InvalidCustomerCredentialsException() {
            super("No active account matches that email and order number.");
        }
    }
}
