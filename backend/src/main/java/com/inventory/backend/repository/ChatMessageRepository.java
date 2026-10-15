package com.inventory.backend.repository;

import com.inventory.backend.model.ChatConversation;
import com.inventory.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * A conversation's messages, oldest first.
     *
     * <p>Ordered by {@code createdAt} and then by {@code id}: two messages written
     * in the same transaction share a timestamp, and without a tiebreak the
     * ordering is whatever the database feels like, which makes a conversation
     * replay unreproducible.
     */
    List<ChatMessage> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    /** How many messages a conversation holds, for the cap. */
    long countByConversationId(Long conversationId);

    /**
     * Messages to drop when a conversation is over its cap.
     *
     * <p>Ordered the same way the reader orders them, so the caller can drop the
     * tail of the newest ids and remove exactly the messages that would have
     * scrolled off the top of the screen anyway.
     */
    @Query("""
            select m.id from ChatMessage m
            where m.conversation.id = :conversationId
            order by m.createdAt desc, m.id desc
            """)
    List<Long> findRecentIds(@Param("conversationId") Long conversationId);

    @Modifying
    @Query("delete from ChatMessage m where m.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);

    /**
     * An actor's messages since a moment, for rate limiting.
     *
     * <p>Counted in SQL. Loading every message in the system to count one actor's
     * would make the limiter itself the expensive thing it exists to prevent.
     */
    @Query("""
            select count(m) from ChatMessage m
            where m.conversation.actorId = :actorId
              and m.conversation.actorType = :actorType
              and m.createdAt > :since
            """)
    long countByActorSince(@Param("actorId") Long actorId,
                           @Param("actorType") ChatConversation.ActorType actorType,
                           @Param("since") LocalDateTime since);

    /** Conversations whose last message is older than the cutoff, for retention. */
    @Query("""
            select m.conversation.id from ChatMessage m
            where m.createdAt < :cutoff
            group by m.conversation.id
            """)
    List<Long> findConversationIdsWithMessagesOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
