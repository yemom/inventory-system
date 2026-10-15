package com.inventory.backend.chatbot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Chatbot configuration.
 *
 * <h2>Credentials are server-side only</h2>
 *
 * <p>The API key is read from the environment and never returned by any endpoint,
 * never logged, and never placed in a response. There is no
 * {@code GET /api/v1/chatbot/config} that would echo it back.
 *
 * <h2>Why the provider is optional</h2>
 *
 * <p>The chatbot is fully functional with no key at all: the deterministic provider
 * answers from the same services the reports use. A language model is a phrasing
 * layer over that, not a data source, so its absence degrades the prose and never
 * the figures.
 *
 * <p>That ordering is the whole design. A system where the model is required for
 * correctness is a system where a model outage is a data outage.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.chatbot")
public class ChatbotProperties {

    /** Master switch. Off means every chatbot endpoint refuses. */
    private boolean enabled = true;

    /**
     * Which provider answers.
     *
     * <p>{@code deterministic} (default) uses no network and no key.
     * {@code openai-compatible} calls an HTTP endpoint and needs a key.
     */
    private String provider = "deterministic";

    // ── OpenAI-compatible provider ────────────────────────────────────────────

    /** Base URL, e.g. {@code https://api.openai.com/v1}. No default: an unset URL is a config error. */
    private String apiUrl;

    /** Read from {@code CHATBOT_API_KEY}. Never logged, never returned. */
    private String apiKey;

    /**
     * The same secret under OpenAI's own variable name, {@code OPENAI_API_KEY}.
     *
     * <p>Bound as a second property rather than nested inside one placeholder,
     * because a nested {@code ${CHATBOT_API_KEY:${OPENAI_API_KEY:}}} never
     * reaches its fallback when the first name is present-but-empty — which is
     * exactly what a pass-through using {@code ${VAR:-}} produces. Two
     * properties and a blank check in the getter do fall through, so a
     * deployment that supplies both names, one of them blank, still resolves.
     */
    private String openaiApiKey;

    /** Model name. */
    private String model = "gpt-4o-mini";

    /**
     * Hard timeout for one provider call, in milliseconds.
     *
     * <p>Deliberately short. A chatbot that hangs is worse than one that says it
     * could not answer, and the deterministic path is always available as the
     * fallback.
     */
    private long timeoutMs = 15000;

    /** Maximum tokens the model may return. Bounds both cost and runaway output. */
    private int maxTokens = 900;

    // ── Safety ────────────────────────────────────────────────────────────────

    /**
     * Minimum confidence for a question to be answered rather than
     * clarified.
     *
     * <p>Below this, the assistant says what it can help with instead
     * of answering the wrong question confidently. The classifier's
     * confidence is the best intent's score over the total, so two
     * intents that tie score 0.5 and read as ambiguous.
     */
    private double assistantMinConfidence = 0.45;

    /**
     * Whether the phrasing layer may be used at all.
     *
     * <p>When false, every answer is produced deterministically and the provider
     * is never called. Useful in an environment where no outbound call is
     * permitted, and the switch that makes the guarantee explicit rather than
     * assumed.
     */
    private boolean llmPhrasingAllowed = true;

    /**
     * Maximum characters of tool output handed to the phrasing layer.
     *
     * <p>Bounds what a provider can see. A tool result is already a summary, so
     * there is no reason to send a customer's full order history to a third party
     * in order to answer "has my order shipped".
     */
    private int maxContextChars = 6000;

    // ── Conversations ─────────────────────────────────────────────────────────

    /** Staff conversations live this long. */
    private int staffRetentionDays = 90;

    /** Customer conversations live this long. Shorter: a shopper is not asking to be remembered. */
    private int customerRetentionDays = 30;

    /**
     * How long a customer session token stays valid, in hours.
     *
     * <p>Long enough to finish a shopping conversation, short
     * enough that a token left on a shared computer stops
     * working.
     */
    private int sessionTtlHours = 12;

    /** Messages kept per conversation. Older ones are dropped, not archived. */
    private int maxMessagesPerConversation = 100;

    // ── Rate limiting ─────────────────────────────────────────────────────────

    /** Messages per window per actor. */
    private int messagesPerWindow = 30;

    /** Window length in seconds. */
    private int windowSeconds = 60;

    /**
     * The key in force: {@code CHATBOT_API_KEY} first, {@code OPENAI_API_KEY}
     * when the first is absent or empty. Only a non-empty value wins.
     *
     * <p>Deliberately not the raw field — callers want the secret that will
     * actually be used, not one particular spelling of where it came from.
     */
    public String getApiKey() {
        return apiKey != null && !apiKey.isBlank() ? apiKey : openaiApiKey;
    }

    /** Whether the key is configured. Exposed so the UI can say which mode it is in. */
    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }
}
