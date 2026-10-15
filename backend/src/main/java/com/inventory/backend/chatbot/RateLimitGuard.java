package com.inventory.backend.chatbot;

import com.inventory.backend.model.ChatMessage;
import com.inventory.backend.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Per-actor rate limiting for the chatbot.
 *
 * <p>The application already has a distributed token-bucket limiter for login,
 * signup, API and report traffic. The chatbot gets its own, because it has a
 * different cost profile: one question can trigger several tool calls, and a
 * caller looping questions is a bill and a load problem long before it is a
 * denial-of-service.
 *
 * <p>Counted by actor rather than by conversation, so opening a new conversation
 * does not reset the limit. A caller who can reset their own limit by clicking
 * "new chat" has no limit.
 *
 * <p>Fails closed rather than open. Unlike the API limiter — where failing open
 * keeps the business usable when Redis is down — a chatbot that cannot tell
 * whether it is being hammered should stop answering rather than answer.
 */
@Component
@RequiredArgsConstructor
public class RateLimitGuard {

    private final ChatMessageRepository messageRepository;
    private final ChatbotProperties properties;

    /**
     * Whether this actor may ask another question right now.
     *
     * <p>Counts the actor's messages in the current window. Counting chatbot
     * messages rather than calling a general limiter keeps the chatbot's budget
     * independent of everything else the caller is doing.
     */
    public boolean allow(ChatbotTool.ToolContext ctx) {
        if (!properties.isEnabled()) {
            return false;
        }
        LocalDateTime since = LocalDateTime.now().minusSeconds(properties.getWindowSeconds());
        long recent = messageRepository.countByActorSince(
                ctx.actorId(), ctx.actorType(), since);
        return recent < properties.getMessagesPerWindow();
    }

    /**
     * Trims a conversation to its message cap.
     *
     * <p>Called when a conversation is opened and when a message is added. The cap
     * is a bound on how much of a conversation a provider is ever shown, and on
     * how long a conversation grows — not a display setting.
     */
    public void enforceCap(Long conversationId) {
        long count = messageRepository.countByConversationId(conversationId);
        int cap = properties.getMaxMessagesPerConversation();
        if (count <= cap) {
            return;
        }
        List<Long> newestIds = messageRepository.findRecentIds(conversationId);
        // The repository returns newest-first; everything past the cap is the tail
        // of that list, which is the oldest messages.
        List<Long> toDelete = newestIds.size() > cap
                ? newestIds.subList(cap, newestIds.size())
                : List.of();
        if (!toDelete.isEmpty()) {
            toDelete.forEach(messageRepository::deleteById);
        }
    }
}
