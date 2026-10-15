package com.inventory.backend.chatbot;

import java.util.List;
import java.util.Map;

/**
 * The language-model phrasing layer.
 *
 * <h2>What this is for, and what it is not for</h2>
 *
 * <p>A chatbot answer has two parts: the <em>facts</em> and the <em>words</em>. The
 * facts come from {@link com.inventory.backend.chatbot.ToolRegistry}, executed
 * through the same services the reports use, and they are non-negotiable. This
 * interface produces the words.
 *
 * <p>That separation is the point of the abstraction. A provider can be swapped,
 * disabled or unavailable without a single figure changing, because no figure
 * ever passes through here unvalidated. Whatever a provider returns is checked by
 * {@link NumberGuard} before a customer or a member of staff reads it: any number
 * in the prose that is not in the tool output it was given is treated as invented,
 * and the answer falls back to the deterministic phrasing.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link DeterministicProvider} — no network, no key, no nondeterminism.
 *       The default, and the fallback whenever anything else fails.</li>
 *   <li>{@link OpenAiCompatibleProvider} — an OpenAI-compatible chat-completions
 *       endpoint, key from the environment, server-side only.</li>
 * </ul>
 *
 * <h2>Why an interface rather than a direct call</h2>
 *
 * <p>Because the alternative is a chatbot that stops working when the model is down,
 * or one where "use the model" is scattered through the service and cannot be
 * switched off. With the seam in place, the choice is a configuration value and
 * the safety guarantee holds either way.
 */
public interface ChatbotProvider {

    /**
     * Produces a reply.
     *
     * @param request the conversation so far, the new question, and the tool
     *                results the caller already obtained
     * @return the provider's reply. Must never throw for a content problem — a
     *         provider that cannot answer is reported as
     *         {@link ChatCompletionResponse#usable()} being false, and the caller
     *         falls back.
     */
    ChatCompletionResponse generate(ChatRequest request);

    /** Provider name, for logging and for the audit trail. */
    String name();

    /**
     * Whether this provider can actually be used right now.
     *
     * <p>An HTTP provider with no key reports false rather than failing at call
     * time, so the deterministic path is taken without a request ever being made.
     */
    default boolean available() {
        return true;
    }

    // ── Request ───────────────────────────────────────────────────────────────

    /**
     * What the provider is asked.
     *
     * <p>Contains the question, the recent transcript and the tool results. It
     * deliberately does <b>not</b> contain credentials, and callers must strip
     * secrets from any text that came from a customer or from the database.
     */
    record ChatRequest(
            String systemPrompt,
            String question,
            List<Turn> transcript,
            Map<String, Object> toolResults) {

        /** One prior exchange, oldest first. */
        record Turn(String role, String content) {
        }
    }

    // ── Response ──────────────────────────────────────────────────────────────

    /**
     * A provider's answer.
     *
     * @param text        the prose
     * @param usable      false when the answer must be discarded and the
     *                    deterministic phrasing used instead
     * @param refusal     true when the provider declined to answer on safety
     *                    grounds, which is a different outcome from a failure
     * @param refusalReason why it declined, when it did
     * @param raw         the provider's raw output, kept for debugging
     */
    record ChatCompletionResponse(
            String text,
            boolean usable,
            boolean refusal,
            String refusalReason,
            String raw) {

        public static ChatCompletionResponse ok(String text, String raw) {
            return new ChatCompletionResponse(text, true, false, null, raw);
        }

        public static ChatCompletionResponse unusable(String reason, String raw) {
            return new ChatCompletionResponse(null, false, false, reason, raw);
        }

        public static ChatCompletionResponse refused(String reason, String raw) {
            return new ChatCompletionResponse(null, false, true, reason, raw);
        }
    }
}
