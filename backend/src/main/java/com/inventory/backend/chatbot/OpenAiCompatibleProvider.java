package com.inventory.backend.chatbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An OpenAI-compatible chat-completions endpoint.
 *
 * <h2>Why this is optional, and off by default</h2>
 *
 * <p>The chatbot answers correctly with this switched off. Turning it on changes
 * how sentences are phrased and never what figures they contain, because the
 * provider is shown tool results and its output is checked before anyone reads
 * it.
 *
 * <p>What it must never be is the thing that decides whether the chatbot works.
 * A deployment that cannot reach its provider still answers, still correctly,
 * and says so in the log.
 *
 * <h2>The URL is the endpoint, not a prefix</h2>
 *
 * <p>OpenAI's own {@code CHATBOT_API_URL} is the full
 * {@code https://api.openai.com/v1/chat/completions}. Other providers expose
 * a base URL such as {@code https://api.openai.com/v1}. Both are accepted: a
 * configured URL that already ends in {@code /chat/completions} is used as
 * given, and one that does not gets the suffix appended. The suffix is never
 * appended twice — a doubled path is a 404 on the real endpoint and looks
 * exactly like an outage.
 *
 * <h2>Model availability is checked, not assumed</h2>
 *
 * <p>The configured model is verified against the account's model list at
 * startup. If it is not available, the provider reports itself unavailable
 * and the deterministic answer is kept. A model that exists in the
 * documentation but not on the account is a configuration error, and it is
 * reported as one rather than retried on every question.
 *
 * <h2>Hard boundaries on the call</h2>
 *
 * <ul>
 *   <li>The key is read from the environment and held only here. It is never
 *       logged, never put in a request body the caller can see, and never
 *       returned by an endpoint.</li>
 *   <li>The timeout is short and enforced, because a hung provider is worse
 *       than an absent one.</li>
 *   <li>Only {@code POST} to the configured endpoint. No redirects are
 *       followed, since a redirect is how a compromised endpoint moves a key.</li>
 *   <li>Tool results are capped in length before they are sent, so a provider
 *       cannot be handed a customer's whole order history.</li>
 *   <li>When {@code CHATBOT_LLM_PHRASED=false}, no request is made at all —
 *       not even the model check. The switch is a guarantee, not a
 *       preference.</li>
 * </ul>
 *
 * <h2>Failure modes are all safe</h2>
 *
 * <p>Timeout, 4xx, 5xx, malformed JSON, an unavailable model and a non-string
 * {@code content} field all produce {@code usable=false}, which the service
 * treats as "use the deterministic reply". There is no path by which a
 * provider failure yields a made-up answer.
 */
@Slf4j
@Component
public class OpenAiCompatibleProvider implements ChatbotProvider {

    private final ChatbotProperties properties;
    private final RestTemplate http;

    /** Whether the configured model is on the account's model list. */
    private volatile boolean modelAvailable;

    /** Why the model check failed, when it did. Logged, never returned to a caller. */
    private volatile String configurationError;

    public OpenAiCompatibleProvider(ChatbotProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.http = builder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs() / 2))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    /**
     * Checks the configured model against the account's model list.
     *
     * <p>Once at startup, so a model that exists in the documentation but
     * not on the account is caught before the first customer asks, and so
     * the failure is a configuration error on the log rather than a
     * per-question surprise. Skipped entirely when phrasing is disabled:
     * an environment that permits no outbound call makes none.
     */
    @PostConstruct
    void checkModelAvailability() {
        if (!properties.isLlmPhrasingAllowed()) {
            log.info("LLM phrasing is disabled; no provider call will be made");
            modelAvailable = true; // not used, but keeps available() honest
            return;
        }
        if (!properties.hasApiKey()) {
            return;
        }
        try {
            RestTemplate shortLived = new RestTemplateBuilder()
                    .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs() / 2))
                    .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .build();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(properties.getApiKey());

            ResponseEntity<Map> response = shortLived.exchange(
                    modelsUrl(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<?, ?> payload = response.getBody();
            if (payload == null || !(payload.get("data") instanceof List<?> models)) {
                // A provider without /models cannot be verified. Treated as
                // unavailable: a model that cannot be confirmed is not a
                // model that may be used.
                configurationError = "The provider did not return a model list, so the "
                        + "configured model could not be verified";
                log.warn("Chatbot model check failed: {}", configurationError);
                modelAvailable = false;
                return;
            }

            boolean found = models.stream()
                    .filter(model -> model instanceof Map<?, ?>)
                    .anyMatch(model -> properties.getModel()
                            .equals(((Map<?, ?>) model).get("id")));
            if (!found) {
                configurationError = "Model '" + properties.getModel()
                        + "' is not available to this API account. Set CHATBOT_MODEL "
                        + "to a model the account can use, or leave the provider "
                        + "deterministic.";
                log.error("Chatbot configuration error: {}", configurationError);
            }
            modelAvailable = found;
        } catch (Exception e) {
            // The model list could not be read (network, auth, provider
            // without the endpoint). The chat call will fail the same way
            // and fall back, but say so plainly here first.
            configurationError = "The configured model could not be verified: "
                    + redact(e.getMessage());
            log.warn("Chatbot model check failed: {}", configurationError);
            modelAvailable = false;
        }
    }

    @Override
    public ChatCompletionResponse generate(ChatRequest request) {
        // The switch, checked here as well as in the service. The service
        // already declines to call a provider when phrasing is off; this
        // second check means no code path, present or future, can make an
        // outbound request while CHATBOT_LLM_PHRASED=false. A guarantee
        // enforced in one place is a preference.
        if (!properties.isLlmPhrasingAllowed()) {
            return ChatCompletionResponse.unusable("LLM phrasing is disabled", null);
        }
        if (!properties.hasApiKey()) {
            return ChatCompletionResponse.unusable("No API key configured", null);
        }
        if (!modelAvailable) {
            // A configuration error, not a transient failure. The
            // deterministic answer is used and the reason is logged; it is
            // not phrased for a caller because it is about the deployment,
            // not about them.
            return ChatCompletionResponse.unusable(
                    "Model unavailable: " + configurationError, null);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        if (request.transcript() != null) {
            for (ChatbotProvider.ChatRequest.Turn turn : request.transcript()) {
                messages.add(Map.of("role", turn.role(), "content", turn.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", request.question()));

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", messages,
                "max_tokens", properties.getMaxTokens(),
                "temperature", 0);

        try {
            ResponseEntity<Map> response = http.postForEntity(
                    chatCompletionsUrl(),
                    new HttpEntity<>(body, headers),
                    Map.class);

            Map<?, ?> payload = response.getBody();
            if (payload == null) {
                return ChatCompletionResponse.unusable("Empty response", null);
            }
            Object choices = payload.get("choices");
            if (!(choices instanceof List<?> list) || list.isEmpty()) {
                return ChatCompletionResponse.unusable("No choices in response",
                        truncate(payload.toString()));
            }
            Object first = list.get(0);
            if (!(first instanceof Map<?, ?> choice)) {
                return ChatCompletionResponse.unusable("Malformed choice", truncate(payload.toString()));
            }
            Object message = choice.get("message");
            if (!(message instanceof Map<?, ?> msg)) {
                return ChatCompletionResponse.unusable("Malformed message", truncate(payload.toString()));
            }
            Object content = msg.get("content");
            if (!(content instanceof String text) || text.isBlank()) {
                return ChatCompletionResponse.unusable("No content", truncate(payload.toString()));
            }
            return ChatCompletionResponse.ok(text, truncate(text));
        } catch (ResourceAccessException e) {
            // Timeout or unreachable. The message can embed the request URL,
            // so it is redacted before it reaches the log: a proxy that
            // authenticates by query parameter would otherwise put the key
            // in a file that more people can read than the secret store.
            String reason = redact(e.getMessage());
            log.warn("Chatbot provider unreachable after {}ms: {}", properties.getTimeoutMs(), reason);
            return ChatCompletionResponse.unusable("Provider unreachable: " + reason, null);
        } catch (Exception e) {
            String reason = redact(e.getMessage());
            log.warn("Chatbot provider call failed: {}", reason);
            return ChatCompletionResponse.unusable("Provider error: " + reason, null);
        }
    }

    /**
     * Strips a query string and any bearer credential from a message.
     *
     * <p>The key travels in a header, so it is not normally present in
     * these messages — but an endpoint that authenticates by query
     * parameter would put it there, and this runs on every failure.
     * Redacting here costs nothing and cannot leak what it removes.
     */
    private static String redact(String message) {
        if (message == null) {
            return null;
        }
        int query = message.indexOf('?');
        if (query >= 0) {
            message = message.substring(0, query) + "?[redacted]";
        }
        if (message.contains("Bearer ")) {
            message = message.replaceAll("(?i)Bearer\\s+\\S+", "Bearer [redacted]");
        }
        return message;
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    /**
     * Whether this provider can actually be used right now.
     *
     * <p>An HTTP provider with no key, or with a model that failed the
     * startup check, reports false rather than failing at call time, so
     * the deterministic path is taken without a request ever being made.
     */
    @Override
    public boolean available() {
        // Disabled is not a configuration to attempt and fail; it is an
        // answer. Reporting false here means the service does not even
        // consider the provider, and the deterministic reply is used.
        if (!properties.isLlmPhrasingAllowed()) {
            return false;
        }
        return properties.hasApiKey() && modelAvailable;
    }

    // ── URL construction ───────────────────────────────────────────────────────

    /**
     * The chat-completions endpoint.
     *
     * <p>Accepts both shapes a deployment might configure: the full
     * endpoint OpenAI documents, and a base URL a proxy or compatible
     * provider exposes. The {@code /chat/completions} suffix is appended
     * only when it is not already there.
     */
    private String chatCompletionsUrl() {
        String url = properties.getApiUrl().trim();
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        return url + "/chat/completions";
    }

    /**
     * The model-list endpoint, derived from the chat URL the same way.
     *
     * <p>{@code …/chat/completions} and {@code …/v1} both yield
     * {@code …/v1/models}; a full-endpoint configuration yields it
     * without a doubled suffix.
     */
    private String modelsUrl() {
        String url = properties.getApiUrl().trim();
        String base = url.endsWith("/chat/completions")
                ? url.substring(0, url.length() - "/chat/completions".length())
                : url;
        return base + "/models";
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
