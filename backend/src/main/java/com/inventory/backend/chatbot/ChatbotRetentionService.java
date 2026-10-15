package com.inventory.backend.chatbot;

import com.inventory.backend.model.ChatConversation;
import com.inventory.backend.model.ChatCustomerSession;
import com.inventory.backend.repository.ChatConversationRepository;
import com.inventory.backend.repository.ChatCustomerSessionRepository;
import com.inventory.backend.repository.ChatMessageRepository;
import com.inventory.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enforces the conversation retention policy.
 *
 * <p>A retention period that is only a column is a promise the
 * application makes and does not keep: the data is still there,
 * readable by the next query. So this service deletes it.
 *
 * <h2>What is deleted, and when</h2>
 *
 * <ul>
 *   <li>Customer conversations past {@code app.chatbot.customer-retention-days}
 *       — the default is 30 days, because a shopper asking about an
 *       order is not asking to be remembered.</li>
 *   <li>Staff conversations past {@code app.chatbot.staff-retention-days}
 *       — the default is 90 days, long enough to review a dispute.</li>
 *   <li>Expired customer session tokens, every run. A token that no
 *       longer works is data with no purpose.</li>
 * </ul>
 *
 * <h2>Messages go with their conversation</h2>
 *
 * <p>A conversation is deleted only after its messages are, and in the
 * same transaction. A crash between the two would leave orphaned rows
 * that a later join could surface, so the order is: messages first,
 * then the conversation that owned them.
 *
 * <h2>Why nightly, and why not faster</h2>
 *
 * <p>Deletion runs on a schedule rather than on every request because
 * retention is a policy, not a filter — checking it per request would
 * make every chat slower to pay for a guarantee that only matters
 * once a day. The policy is still stated per conversation in
 * {@code expiresAt}, so the schedule is an enforcement detail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotRetentionService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatCustomerSessionRepository customerSessionRepository;
    private final AuditLogService auditLogService;

    /**
     * Deletes conversations past their retention date and any expired
     * customer sessions.
     *
     * <p>Runs nightly at 02:40 — after the business day's data is
     * written, before the next one starts, and not on the hour in case
     * some other job is also scheduled there.
     */
    @Scheduled(cron = "0 40 2 * * *")
    @Transactional
    public void purgeExpired() {
        LocalDateTime now = LocalDateTime.now();

        List<ChatConversation> overdue = conversationRepository.findByExpiresAtBefore(now);
        for (ChatConversation conversation : overdue) {
            // Messages before the conversation: the conversation is what
            // gives them a reason to exist, and a failure partway through
            // must not leave them readable by id.
            messageRepository.deleteByConversationId(conversation.getId());
            conversationRepository.delete(conversation);
            auditLogService.log(null, "chatbot-retention",
                    "CHAT_CONVERSATION_PURGED", "CHAT_CONVERSATION",
                    String.valueOf(conversation.getId()),
                    "actorType=" + conversation.getActorType()
                            + ", expiredAt=" + conversation.getExpiresAt());
        }

        List<ChatCustomerSession> expired =
                customerSessionRepository.findByExpiresAtBefore(now);
        customerSessionRepository.deleteAll(expired);

        if (!overdue.isEmpty() || !expired.isEmpty()) {
            log.info("Retention purge removed {} conversation(s) and {} expired session(s)",
                    overdue.size(), expired.size());
        }
    }
}
