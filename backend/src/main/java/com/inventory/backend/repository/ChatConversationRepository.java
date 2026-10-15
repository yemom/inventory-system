package com.inventory.backend.repository;

import com.inventory.backend.model.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    /**
     * Conversations owned by one staff member.
     *
     * <p>Keyed by {@code actorType = STAFF} and the user id. The type is part of
     * the key rather than assumed, so a staff id and a customer id that happen to
     * match cannot address the same conversation.
     */
    List<ChatConversation> findByActorTypeAndActorIdOrderByLastMessageAtDesc(
            ChatConversation.ActorType actorType, Long actorId);

    /**
     * A customer conversation, only if the session token matches.
     *
     * <p>The token is compared as a hash, so the caller never handles a stored
     * token and the database never holds a working one.
     */
    @Query("""
            select c from ChatConversation c
            where c.id = :id
              and c.actorType = :actorType
              and c.actorId = :customerId
              and c.sessionTokenHash = :tokenHash
            """)
    Optional<ChatConversation> findCustomerConversation(
            @Param("id") Long id,
            @Param("customerId") Long customerId,
            @Param("tokenHash") String tokenHash,
            @Param("actorType") ChatConversation.ActorType actorType);

    default Optional<ChatConversation> findCustomerConversation(Long id, Long customerId, String tokenHash) {
        return findCustomerConversation(id, customerId, tokenHash, ChatConversation.ActorType.CUSTOMER);
    }

    /**
     * A staff conversation, only if the user owns it.
     */
    @Query("""
            select c from ChatConversation c
            where c.id = :id
              and c.actorType = :actorType
              and c.actorId = :userId
            """)
    Optional<ChatConversation> findStaffConversation(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("actorType") ChatConversation.ActorType actorType);

    default Optional<ChatConversation> findStaffConversation(Long id, Long userId) {
        return findStaffConversation(id, userId, ChatConversation.ActorType.STAFF);
    }

    /** Past their retention date, for the purge. */
    List<ChatConversation> findByExpiresAtBefore(LocalDateTime cutoff);
}
