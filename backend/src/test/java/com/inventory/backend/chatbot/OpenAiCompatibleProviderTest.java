package com.inventory.backend.chatbot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The phrasing layer's behaviour and its failure modes.
 *
 * <p>Every test here is either a way the provider could fail —
 * unreachable, timed out, malformed, unauthorized, rate limited,
 * wrong model — or a property of the request that must hold: the
 * right endpoint, the right header, no doubled path, and no call
 * at all when phrasing is disabled. A provider failure must never
 * become a made-up answer or a hung chatbot, and a disabled
 * provider must make no request whatsoever.
 *
 * <p>The mock server answers both endpoints a real deployment
 * calls: {@code GET /v1/models} for the startup availability check
 * and {@code POST /v1/chat/completions} for answers. A test that
 * wanted only one of them would be testing a different provider
 * from the one that ships.
 */
class OpenAiCompatibleProviderTest {

    /** A model list containing the model the tests configure. */
    private static final String MODEL_LIST_OK = """
            {"data":[{"id":"gpt-4o-mini"},{"id":"gpt-4o"}]}
            """;

    private static final String CHAT_OK = """
            {"choices":[{"message":{"role":"assistant","content":"Net profit was ETB 12,400.00."}}]}
            """;

    private RecordingServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private ChatbotProperties propertiesWith(String url, String key, long timeoutMs) {
        ChatbotProperties properties = new ChatbotProperties();
        properties.setProvider("openai-compatible");
        properties.setApiUrl(url);
        properties.setApiKey(key);
        properties.setTimeoutMs(timeoutMs);
        properties.setMaxTokens(100);
        properties.setLlmPhrasingAllowed(true);
        return properties;
    }

    private ChatbotProvider.ChatRequest request(String question) {
        return new ChatbotProvider.ChatRequest(
                PromptSanitiser.systemPrompt("a member of staff"),
                question,
                List.of(),
                Map.of("tool_results", "{}"));
    }

    // ── The request itself ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a full endpoint URL is used as given, without a doubled suffix")
    void fullEndpointIsNotDoubled() throws IOException {
        // The shape OpenAI's own documentation gives. The provider must
        // POST to exactly this URL, not to .../chat/completions/chat/completions.
        server = new RecordingServer(MODEL_LIST_OK, 200, CHAT_OK);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1/chat/completions", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));

        assertTrue(response.usable(),
                "the provider must answer when both endpoints are healthy: "
                        + response.refusalReason());
        assertEquals("/v1/chat/completions", server.lastPath(),
                "the request must land on the configured endpoint exactly once");
    }

    @Test
    @DisplayName("a base URL gets the chat/completions suffix appended")
    void baseUrlGetsSuffix() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 200, CHAT_OK);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));

        assertTrue(response.usable(), response.refusalReason());
        assertEquals("/v1/chat/completions", server.lastPath());
    }

    @Test
    @DisplayName("the request carries the Bearer key and the configured model")
    void requestCarriesAuthAndModel() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 200,
                """
                {"choices":[{"message":{"role":"assistant","content":"ok"}}]}
                """);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "sk-test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));

        assertTrue(response.usable(), response.refusalReason());
        assertEquals("Bearer sk-test-key", server.lastAuthorization(),
                "the key must travel as a bearer credential");
        assertTrue(server.lastBody().contains("\"model\":\"gpt-4o-mini\""),
                "the configured model must be the one requested: " + server.lastBody());
        assertTrue(server.lastBody().contains("\"messages\""),
                "the question and its context must be in the body");
    }

    // ── Model availability ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a model on the account's list is accepted")
    void availableModelIsAccepted() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 200, CHAT_OK);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());

        provider.checkModelAvailability();

        assertTrue(provider.available(), "the configured model is on the list");
        assertEquals(1, server.totalRequestCount(),
                "the check is one GET to /models and nothing else");
    }

    @Test
    @DisplayName("a model that is not on the account is a configuration error")
    void unavailableModelIsAConfigurationError() throws IOException {
        server = new RecordingServer("""
                {"data":[{"id":"gpt-4o"}]}
                """, 200, CHAT_OK);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());

        provider.checkModelAvailability();

        assertFalse(provider.available(),
                "a model the account cannot use must not be called");
        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));
        assertFalse(response.usable());
        assertTrue(response.refusalReason() != null
                        && response.refusalReason().contains("not available"),
                "the failure must name the problem: " + response.refusalReason());
        assertEquals(0, server.chatRequestCount(),
                "and no chat request is made for a model that cannot answer");
    }

    @Test
    @DisplayName("a provider without a model list is treated as unverifiable")
    void missingModelListIsUnverifiable() throws IOException {
        // No /v1/models context: the GET falls through to a 404, which is
        // what a provider that does not implement the endpoint returns.
        server = new RecordingServer(null, 200, CHAT_OK);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());

        provider.checkModelAvailability();

        assertFalse(provider.available(),
                "a model that cannot be confirmed is not a model that may be used");
    }

    // ── Disabled phrasing makes no call at all ─────────────────────────────────

    @Test
    @DisplayName("with phrasing disabled the provider makes no request whatsoever")
    void disabledPhrasingMakesNoCall() throws IOException {
        // The server records every request on either endpoint. Both the
        // availability check and a direct call to generate() are attempted
        // below; neither may put a byte on the wire.
        server = new RecordingServer(MODEL_LIST_OK, 200,
                """
                {"choices":[{"message":{"role":"assistant","content":"should never be read"}}]}
                """);
        ChatbotProperties properties = propertiesWith(
                server.baseUrl() + "/v1", "test-key", 5000);
        properties.setLlmPhrasingAllowed(false);

        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                properties, new RestTemplateBuilder());
        provider.checkModelAvailability();

        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));

        assertFalse(response.usable(),
                "a disabled provider must not produce an answer");
        assertFalse(provider.available(),
                "and must not present itself as usable");
        assertEquals(0, server.totalRequestCount(),
                "CHATBOT_LLM_PHRASED=false makes zero outbound calls, "
                        + "even with a key configured");
    }

    // ── Failure modes ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("with no key the provider reports itself unavailable")
    void unavailableWithoutKey() {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith("http://127.0.0.1:1/v1", null, 1000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        assertFalse(provider.available(),
                "a provider with no key must not be tried at call time");
    }

    @Test
    @DisplayName("an endpoint that goes away returns unusable rather than throwing")
    void unreachableEndpointIsUnusable() throws IOException {
        // The model check succeeds against a healthy server; the server is
        // then stopped, so the chat call itself meets a refused connection.
        // That is the path a deployment takes when its provider dies.
        server = new RecordingServer(MODEL_LIST_OK, 200, CHAT_OK);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();
        assertTrue(provider.available(), "precondition: the check passed");
        server.stop();
        server = null; // already stopped; @AfterEach must not stop it twice

        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));

        assertFalse(response.usable(),
                "an unreachable provider must not produce an answer");
        assertNull(response.text(), "and no text to show");
    }

    @Test
    @DisplayName("a timeout returns unusable rather than hanging")
    void timeoutIsUnusable() throws IOException {
        // /models answers at once so the availability check passes and the
        // chat call is genuinely attempted; the chat handler then never
        // answers, and the read timeout must fire.
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/v1/models", exchange -> {
            byte[] bytes = MODEL_LIST_OK.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
        slow.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        slow.start();

        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(
                        "http://127.0.0.1:" + slow.getAddress().getPort() + "/v1",
                        "test-key", 500),
                new RestTemplateBuilder());
        provider.checkModelAvailability();
        assertTrue(provider.available(),
                "precondition: the model check passed, so the chat call is attempted");

        long began = System.nanoTime();
        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));
        long elapsedMs = (System.nanoTime() - began) / 1_000_000;

        slow.stop(0);
        assertFalse(response.usable(), "a timed-out provider must not produce an answer");
        assertTrue(elapsedMs < 4000,
                "the read timeout must fire well before the handler wakes: "
                        + elapsedMs + "ms");
    }

    @Test
    @DisplayName("malformed JSON returns unusable")
    void malformedJsonIsUnusable() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 200, "this is not json at all");
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        assertFalse(provider.generate(request("What was our net profit?")).usable());
    }

    @Test
    @DisplayName("a well-formed response is returned")
    void wellFormedResponseIsUsable() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 200, CHAT_OK);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));

        assertTrue(response.usable(), response.refusalReason());
        assertNotNull(response.text());
        assertTrue(response.text().contains("12,400.00"));
    }

    @Test
    @DisplayName("a response with no content field is unusable")
    void missingContentIsUnusable() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 200, """
                {"choices":[{"message":{"role":"assistant","content":null}}]}
                """);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        assertFalse(provider.generate(request("What was our net profit?")).usable());
    }

    @Test
    @DisplayName("a response with no choices is unusable")
    void emptyChoicesIsUnusable() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 200, """
                {"choices":[]}
                """);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        assertFalse(provider.generate(request("What was our net profit?")).usable());
    }

    @Test
    @DisplayName("an invalid API key (401) is unusable")
    void invalidKeyIsUnusable() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 401, """
                {"error":{"message":"invalid api key","type":"invalid_request_error"}}
                """);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "sk-wrong", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        assertFalse(provider.generate(request("What was our net profit?")).usable());
    }

    @Test
    @DisplayName("a rate limit (429) is unusable, so the deterministic reply stands")
    void rateLimitIsUnusable() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 429, """
                {"error":{"message":"rate limit exceeded","type":"rate_limit_error"}}
                """);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        assertFalse(provider.generate(request("What was our net profit?")).usable());
    }

    @Test
    @DisplayName("a server error (500) is unusable")
    void serverErrorIsUnusable() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 500, """
                {"error":{"message":"internal error"}}
                """);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "test-key", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        assertFalse(provider.generate(request("What was our net profit?")).usable());
    }

    @Test
    @DisplayName("a failure message never contains the key")
    void failureNeverLeaksTheKey() throws IOException {
        server = new RecordingServer(MODEL_LIST_OK, 401, """
                {"error":{"message":"invalid api key sk-super-secret-value"}}
                """);
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                propertiesWith(server.baseUrl() + "/v1", "sk-super-secret-value", 5000),
                new RestTemplateBuilder());
        provider.checkModelAvailability();

        ChatbotProvider.ChatCompletionResponse response =
                provider.generate(request("What was our net profit?"));

        assertFalse(response.usable());
        String surfaced = response.refusalReason() == null ? "" : response.refusalReason();
        assertFalse(surfaced.contains("sk-super-secret-value"),
                "the configured key must not appear in anything a caller sees: "
                        + surfaced);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * A mock provider with both endpoints a real deployment calls.
     *
     * <p>{@code GET /v1/models} answers with the given list (or 404s when
     * the list is null, for a provider that does not implement it) and
     * {@code POST /v1/chat/completions} answers with the given status and
     * body. The recorded fields are the ones the tests assert on: the
     * chat request's path (to prove the suffix was not doubled), its
     * authorization header (to prove the key travelled as a bearer
     * credential) and its body (to prove the configured model was
     * requested). {@link #totalRequestCount()} counts both endpoints, so
     * a test can prove that a disabled provider made no call of any kind.
     */
    private static final class RecordingServer {

        private final HttpServer server;
        private final AtomicInteger totalRequests = new AtomicInteger();
        private final AtomicInteger chatRequests = new AtomicInteger();
        private final AtomicReference<String> chatPath = new AtomicReference<>();
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> body = new AtomicReference<>();

        RecordingServer(String modelsJson, int chatStatus, String chatBody) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

            if (modelsJson != null) {
                this.server.createContext("/v1/models", exchange -> {
                    totalRequests.incrementAndGet();
                    respond(exchange, 200, modelsJson);
                });
            }

            this.server.createContext("/v1/chat/completions", exchange -> {
                totalRequests.incrementAndGet();
                chatRequests.incrementAndGet();
                chatPath.set(exchange.getRequestURI().getPath());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                body.set(readBody(exchange));
                respond(exchange, chatStatus, chatBody);
            });

            this.server.start();
        }

        private static String readBody(HttpExchange exchange) {
            try {
                return new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        }

        private static void respond(HttpExchange exchange, int status, String responseBody)
                throws IOException {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        }

        /** The base URL of this server, e.g. {@code http://127.0.0.1:12345}. */
        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /** The path of the last chat request, e.g. {@code /v1/chat/completions}. */
        String lastPath() {
            return chatPath.get();
        }

        String lastAuthorization() {
            return authorization.get();
        }

        String lastBody() {
            return body.get();
        }

        int chatRequestCount() {
            return chatRequests.get();
        }

        int totalRequestCount() {
            return totalRequests.get();
        }

        void stop() {
            server.stop(0);
        }
    }
}
