package com.inventory.backend.chatbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The default provider: no network, no key, no nondeterminism.
 *
 * <p>This is the provider that keeps the chatbot honest, and it is also the
 * fallback. Its answers are rendered by the same code the reports use, so the
 * words are never the source of a number.
 *
 * <h2>Why it exists as a provider at all</h2>
 *
 * <p>Because the alternative is a conditional scattered through the service —
 * "if there is a key, call the model, else render the template" — which is exactly
 * how you end up with two answer paths that slowly diverge and only one of them
 * being tested. There is one answer path. The provider only decides how the
 * sentence is phrased, and here the phrasing is already fixed.
 */
@Slf4j
@Component
public class DeterministicProvider implements ChatbotProvider {

    @Override
    public ChatCompletionResponse generate(ChatRequest request) {
        // There is no phrasing to add: the service renders the answer from the
        // tool results it already holds. Returning empty-with-usable tells the
        // service to use its own rendering, which is what "deterministic" means.
        return ChatCompletionResponse.ok("", "deterministic");
    }

    @Override
    public String name() {
        return "deterministic";
    }

    @Override
    public boolean available() {
        return true;
    }
}
