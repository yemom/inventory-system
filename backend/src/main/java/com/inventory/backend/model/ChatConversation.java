package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One chatbot conversation.
 *
 * <h2>Isolation is a column, not a query convention</h2>
 *
 * <p>A conversation belongs to exactly one actor, recorded as
 * {@code (actorType, actorId)}. A staff conversation is keyed by the user's id; a
 * customer conversation by the customer's id <em>and</em> a session token hash, so
 * a customer who loses their link cannot read the conversation by guessing an id.
 *
 * <p>That matters because the requirement is that a customer must never see another
 * customer's orders, invoices or payment details. If the chatbot answered from a
 * message history without a hard ownership column, every tool call would need to
 * remember the scope; a tool that forgot would leak. The column means the check
 * cannot be forgotten.
 *
 * <h2>Retention</h2>
 *
 * <p>{@code expiresAt} is set when the conversation is created and is enforced by a
 * scheduled purge. A conversation past its retention date is not merely hidden - it
 * is deleted, because a conversation a business keeps past its stated retention
 * policy is a promise it did not keep.
 *
 * <p>Customer conversations default to a short retention: a shopper asking about an
 * order is not asking to be remembered.
 */
@Entity
@Table(name = "chat_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversation {

    /** Who this conversation belongs to. */
    public enum ActorType {
        STAFF,
        CUSTOMER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** STAFF or CUSTOMER. The isolation key's type half. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    @Builder.Default
    private ActorType actorType = ActorType.STAFF;

    /**
     * Who owns it: a user id for staff, a customer id for a customer.
     *
     * <p>Never a username or email. Those change, and an identifier that changes
     * silently breaks the ownership record.
     */
    @Column(nullable = false)
    private Long actorId;

    /**
     * Hash of the token that opened this customer conversation.
     *
     * <p>Stored hashed rather than in clear, so a database leak does not hand over
     * working session tokens. Null for staff conversations, whose isolation is the
     * user id alone.
     */
    @Column(name = "session_token_hash", length = 64)
    private String sessionTokenHash;

    /** Short label the UI shows, never built from message content. */
    @Column(length = 120)
    private String title;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime lastMessageAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * When this conversation stops being readable.
     *
     * <p>Not a display setting. Rows past this are deleted by
     * {@code ChatbotRetentionService}.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** True once a staff member has been pulled in to answer directly. */
    @Column(name = "staff_handoff", nullable = false)
    @Builder.Default
    private boolean staffHandoff = false;

    /** The staff member who took over, if any. Recorded so a handoff can be reviewed. */
    @Column(name = "handoff_user_id")
    private Long handoffUserId;
}
