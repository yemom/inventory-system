package com.inventory.backend.controller;

import com.inventory.backend.chatbot.ChatbotService;
import com.inventory.backend.chatbot.ChatbotTool;
import com.inventory.backend.chatbot.ToolRegistry;
import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.dto.ChatAskRequest;
import com.inventory.backend.dto.ChatConversationRequest;
import com.inventory.backend.dto.ChatCustomerAskRequest;
import com.inventory.backend.dto.ChatCustomerSessionRequest;
import com.inventory.backend.model.ChatConversation;
import com.inventory.backend.model.ChatMessage;
import com.inventory.backend.model.ChatCustomerSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chatbot API.
 *
 * <h2>Two channels, one service</h2>
 *
 * <p>Staff reach it with their JWT and are answered according to
 * their own permissions, which are resolved on every request from
 * the security context — a promotion or a revocation changes what
 * the assistant can show with no code change.
 *
 * <p>Customers reach it with a session token, issued only after
 * they name the email and order number on a receipt. From that
 * point every question is scoped to their own customer id, in SQL,
 * which is what makes another customer's orders unreachable rather
 * than merely unasked-for.
 *
 * <h2>Read-only</h2>
 *
 * <p>There is no endpoint here that places an order, records a
 * payment, moves stock or approves anything. The one stateful
 * customer action is raising a support request, and that is a
 * message to a person, not a change to a record.
 *
 * <h2>What is never in a response</h2>
 *
 * <p>Another customer's data, a provider's API key, a session
 * token after the response that created it, and the provider's
 * draft of an answer that was replaced. Tool results are returned
 * with the answer that used them, because they are the figures the
 * answer is about — but never another actor's.
 */
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final ToolRegistry toolRegistry;

    // ── Staff ───────────────────────────────────────────────────────

    /**
     * What this caller may ask about.
     *
     * <p>The suggestion chips in the UI are built from this, so a
     * user is never offered a question the assistant will refuse to
     * answer. The list is derived from the caller's own authorities,
     * which is what keeps it in step with their real permissions.
     */
    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> capabilities() {
        ChatbotTool.ToolContext ctx = chatbotService.staffContext();
        List<Map<String, Object>> rows = toolRegistry.toolsFor(ctx).stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", entry.name());
                    row.put("description", entry.description());
                    row.put("requiresPermission", entry.permission());
                    return row;
                })
                .toList();
        return ResponseEntity.ok(
                ApiResponse.ok("Assistant capabilities", rows));
    }

    /**
     * Ask the assistant something.
     *
     * <p>The answer's figures come from the same services the
     * reports use, and the reporting period is stated in the answer
     * itself, so a figure can always be traced back to the report
     * it belongs to.
     */
    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ask(@RequestBody ChatAskRequest request) {
        ChatbotService.DateRange range = rangeOf(request.from(), request.to());
        ChatbotService.Reply reply = chatbotService.ask(
                request.question(), request.conversationId(), range,
                chatbotService.staffContext());
        return ResponseEntity.ok(ApiResponse.ok("Assistant answer", payload(reply)));
    }

    /** This caller's conversations, newest first. */
    @GetMapping("/conversations")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> conversations() {
        ChatbotTool.ToolContext ctx = chatbotService.staffContext();
        return ResponseEntity.ok(ApiResponse.ok("Assistant conversations",
                chatbotService.conversations(ctx).stream()
                        .map(this::summary).toList()));
    }

    /** Start a conversation. */
    @PostMapping("/conversations")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startConversation(
            @RequestBody(required = false) ChatConversationRequest request) {
        ChatbotTool.ToolContext ctx = chatbotService.staffContext();
        ChatConversation conversation = chatbotService.startConversation(
                ctx, request == null ? null : request.title());
        return ResponseEntity.ok(ApiResponse.ok("Conversation started", summary(conversation)));
    }

    /** A conversation's messages, oldest first. */
    @GetMapping("/conversations/{id}")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(
            @PathVariable Long id) {
        ChatbotTool.ToolContext ctx = chatbotService.staffContext();
        List<ChatMessage> messages = chatbotService.history(id, ctx);
        return ResponseEntity.ok(ApiResponse.ok("Conversation history",
                messages.stream().filter(this::visible).map(this::message).toList()));
    }

    /**
     * Delete a conversation.
     *
     * <p>The retention policy decides how long a conversation is
     * kept; this is the caller's half of that policy — the right to
     * have one gone now. Ownership is checked by the service, so an
     * id alone deletes nothing.
     */
    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("hasAuthority('AI_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable Long id) {
        ChatbotTool.ToolContext ctx = chatbotService.staffContext();
        chatbotService.deleteConversation(id, ctx);
        return ResponseEntity.ok(ApiResponse.ok("Conversation deleted", null));
    }

    // ── Customer ────────────────────────────────────────────────────

    /**
     * Open a customer session.
     *
     * <p>The email and order number must both match an active
     * customer's own order. The token is returned here exactly once
     * and stored only as a hash, so a database leak does not hand
     * over working sessions.
     *
     * <p>Unauthenticated by design: a customer has no staff JWT.
     * The endpoint is rate-limited per IP by the global filter, and
     * the pair it needs is printed on a receipt, so a stranger
     * cannot brute-force a customer's history without already
     * holding their paperwork.
     */
    @PostMapping("/customer/session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openCustomerSession(
            @RequestBody ChatCustomerSessionRequest request) {
        ChatCustomerSession session =
                chatbotService.openSession(request.email(), request.orderNumber());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", session.getTokenPlain());
        data.put("customerName", session.getCustomerName());
        data.put("expiresAt", session.getExpiresAt().toString());
        data.put("note", "Keep this token: it is shown once. It gives access to "
                + "this customer's own orders and account only, and expires.");
        return ResponseEntity.ok(ApiResponse.ok(
                "Session opened for " + session.getCustomerName(), data));
    }

    /** End a customer session. */
    @DeleteMapping("/customer/session")
    public ResponseEntity<ApiResponse<Void>> closeCustomerSession(
            @RequestHeader("X-Chatbot-Token") String token) {
        chatbotService.closeSession(token);
        return ResponseEntity.ok(ApiResponse.ok("Signed out", null));
    }

    /** Ask the assistant as a customer. */
    @PostMapping("/customer/ask")
    public ResponseEntity<ApiResponse<Map<String, Object>>> askCustomer(
            @RequestBody ChatCustomerAskRequest request) {
        ChatbotTool.ToolContext ctx = chatbotService.resolveCustomerSession(request.token());
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("INVALID_CUSTOMER_CREDENTIALS",
                            "This session has expired. Sign in again with your email "
                                    + "and an order number."));
        }
        ChatbotService.Reply reply = chatbotService.ask(
                request.question(), request.conversationId(), null, ctx);
        return ResponseEntity.ok(ApiResponse.ok("Assistant answer", payload(reply)));
    }

    /** The customer's conversations. */
    @GetMapping("/customer/conversations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> customerConversations(
            @RequestHeader("X-Chatbot-Token") String token) {
        ChatbotTool.ToolContext ctx = requireSession(token);
        return ResponseEntity.ok(ApiResponse.ok("Assistant conversations",
                chatbotService.conversations(ctx).stream()
                        .map(this::summary).toList()));
    }

    /** A customer conversation's messages. */
    @GetMapping("/customer/conversations/{id}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> customerHistory(
            @PathVariable Long id,
            @RequestHeader("X-Chatbot-Token") String token) {
        ChatbotTool.ToolContext ctx = requireSession(token);
        List<ChatMessage> messages = chatbotService.history(id, ctx);
        return ResponseEntity.ok(ApiResponse.ok("Conversation history",
                messages.stream().filter(this::visible).map(this::message).toList()));
    }

    /** Delete a customer conversation. */
    @DeleteMapping("/customer/conversations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomerConversation(
            @PathVariable Long id,
            @RequestHeader("X-Chatbot-Token") String token) {
        ChatbotTool.ToolContext ctx = requireSession(token);
        chatbotService.deleteConversation(id, ctx);
        return ResponseEntity.ok(ApiResponse.ok("Conversation deleted", null));
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Resolves a customer session or answers 401.
     *
     * <p>The same failure for an unknown, expired and malformed
     * token, so a caller learns nothing by probing.
     */
    private ChatbotTool.ToolContext requireSession(String token) {
        ChatbotTool.ToolContext ctx = chatbotService.resolveCustomerSession(token);
        if (ctx == null) {
            throw new com.inventory.backend.chatbot.ChatbotService
                    .InvalidCustomerCredentialsException();
        }
        return ctx;
    }

    /**
     * A reporting period from the request's own dates.
     *
     * <p>An invalid date is refused rather than quietly ignored:
     * a period the caller asked about and did not get is a wrong
     * answer, which is worse than no answer.
     */
    private static ChatbotService.DateRange rangeOf(String from, String to) {
        if (from == null && to == null) {
            return null;
        }
        try {
            if (from != null) {
                LocalDate.parse(from);
            }
            if (to != null) {
                LocalDate.parse(to);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "from and to must be ISO dates (YYYY-MM-DD)");
        }
        return new ChatbotService.DateRange(from, to);
    }

    private Map<String, Object> payload(ChatbotService.Reply reply) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", reply.conversationId());
        data.put("text", reply.text());
        data.put("deterministic", reply.deterministic());
        data.put("guardIntervened", reply.guardIntervened());
        data.put("note", reply.note());
        data.put("toolResults", reply.toolResults());
        return data;
    }

    private Map<String, Object> summary(ChatConversation conversation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", conversation.getId());
        row.put("title", conversation.getTitle());
        row.put("createdAt", conversation.getCreatedAt() == null
                ? null : conversation.getCreatedAt().toString());
        row.put("lastMessageAt", conversation.getLastMessageAt() == null
                ? null : conversation.getLastMessageAt().toString());
        return row;
    }

    private Map<String, Object> message(ChatMessage message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", message.getId());
        row.put("role", message.getRole().name().toLowerCase());
        row.put("content", message.getContent());
        row.put("toolName", message.getToolName());
        row.put("deterministic", message.isDeterministic());
        row.put("createdAt", message.getCreatedAt() == null
                ? null : message.getCreatedAt().toString());
        return row;
    }

    /**
     * Only what a reader should see in a transcript.
     *
     * <p>{@code TOOL} rows hold raw tool output and, when a provider
     * was configured, the provider's own draft — including drafts
     * that were replaced for inventing a figure. Neither belongs in
     * a conversation the caller reads back, so history shows the
     * exchange and nothing else.
     */
    private boolean visible(ChatMessage message) {
        return message.getRole() == ChatMessage.Role.USER
                || message.getRole() == ChatMessage.Role.ASSISTANT;
    }
}
