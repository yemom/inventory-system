package com.inventory.backend.chatbot;

import java.util.Map;
import java.util.Set;

import com.inventory.backend.model.ChatConversation;

/**
 * A read-only capability the chatbot can use.
 *
 * <h2>Why a tool is an object with a permission</h2>
 *
 * <p>A tool is the contract between the phrasing layer and the data layer. It
 * carries four things:
 * <ul>
 *   <li>its name and description, which is what a caller sees;</li>
 *   <li>the permission needed to run it — checked <em>before</em> it runs, on the
 *       caller's own authorities, not the role's name;</li>
 *   <li>a parameter schema, so a caller knows what to pass;</li>
 *   <li>the warehouse scope it is allowed to see, resolved from the caller.</li>
 * </ul>
 *
 * <p>The permission is on the tool rather than sprinkled across the service
 * because a permission that is a property of the capability cannot be forgotten
 * when the capability is added. A new tool that forgets its {@code requiredPermission}
 * is visible in the code; a missing check in an orchestrator is not.
 *
 * <h2>Read-only by construction</h2>
 *
 * <p>Nothing in this package writes. There is no tool for a payment, a refund, a
 * stock change, an approval or a transfer, because the requirement is that the
 * chatbot never silently executes a consequential action. A tool that does not
 * exist cannot be called by a prompt-injected instruction, by a hallucinated tool
 * name, or by a future refactor that assumes "the chatbot can probably do that".
 *
 * <p>The one exception is raising a support request, which is a record of a
 * customer asking for a human — not a change to money or stock.
 */
public interface ChatbotTool {

    /** Unique name. Stable: it is persisted in tool-call logs. */
    String name();

    /** One-line description for the UI and for an LLM's tool list. */
    String description();

    /**
     * Permission the caller must hold.
     *
     * <p>Customer tools use {@link #CUSTOMER_TOOL}, which is not a staff
     * permission at all — it marks the tool as available to any authenticated
     * customer session, and the real control is the customer's own id.
     */
    String requiredPermission();

    /**
     * Parameter names this tool understands.
     *
     * <p>Returned as a set of names rather than a schema object: the parameters
     * are resolved by the deterministic path, and an LLM is given the names in the
     * tool description. A schema object would imply validation that does not exist.
     */
    Set<String> parameters();

    /**
     * Runs the tool.
     *
     * @param params  the caller's arguments
     * @param context who is asking, and what they are scoped to
     * @return a JSON-serialisable result. Never contains credentials.
     * @throws SecurityException when the caller may not run this tool
     */
    Map<String, Object> execute(Map<String, Object> params, ToolContext context);

    /**
     * Who is asking.
     *
     * <p>Everything a tool returns is derived from this, so a tool has no need to
     * re-check a conversation's owner.
     */
    record ToolContext(
            ChatConversation.ActorType actorType,
            Long actorId,
            String sessionTokenHash,
            Set<String> authorities) {

        /** A staff member's authorities. */
        public static ToolContext staff(Long userId, Set<String> authorities) {
            return new ToolContext(ChatConversation.ActorType.STAFF, userId, null, authorities);
        }

        /** A customer's context. The id is the isolation key for every lookup. */
        public static ToolContext customer(Long customerId, String tokenHash, Set<String> authorities) {
            return new ToolContext(ChatConversation.ActorType.CUSTOMER, customerId, tokenHash, authorities);
        }

        public boolean isStaff() {
            return actorType == ChatConversation.ActorType.STAFF;
        }

        public boolean isCustomer() {
            return actorType == ChatConversation.ActorType.CUSTOMER;
        }

        /** Whether the caller holds a permission. Super Admin holds them all. */
        public boolean can(String permission) {
            return authorities != null && authorities.contains(permission);
        }
    }

    /**
     * Marker for tools a customer may use.
     *
     * <p>Not a real permission: no role is granted it, so {@code can()} never
     * returns true for it from the staff catalogue. It exists so a tool can say
     * "customers may call me" without inventing a permission that would then have
     * to be granted to somebody.
     */
    String CUSTOMER_TOOL = "CUSTOMER_CHAT_TOOL";
}
