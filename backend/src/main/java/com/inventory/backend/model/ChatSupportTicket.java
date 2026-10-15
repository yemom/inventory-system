package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A customer request for a human.
 *
 * <p>The chatbot cannot solve everything, and for a customer the honest answer is
 * often "a person needs to look at this". This is that answer, written down.
 *
 * <p>It carries the customer conversation it came from and, when it is raised by a
 * staff member on a customer's behalf, that staff member's id — so the request is
 * attributable either way and cannot be filed anonymously by a customer against an
 * arbitrary member of staff.
 *
 * <h2>What it is not</h2>
 *
 * <p>It is not a permission. Creating a ticket grants no access to anything the
 * caller could not already see. A ticket routes a question; it does not open a
 * record. Anything the staff member then does with the conversation is still
 * checked against that member's own permissions, and is still audited.
 */
@Entity
@Table(name = "chat_support_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSupportTicket {

    /** Lifecycle. */
    public enum Status {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The customer conversation this came from. Null when raised by staff directly. */
    @Column(name = "conversation_id")
    private Long conversationId;

    /** The customer it concerns. Denormalised so a ticket is answerable without a join. */
    @Column(nullable = false)
    private Long customerId;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** What the customer asked for, in their words. */
    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    /** What the customer is actually trying to achieve, as far as it can be told. */
    @Column(columnDefinition = "text")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private Status status = Status.OPEN;

    /** Who filed it: the chatbot, or a member of staff. */
    @Column(name = "raised_by_type", nullable = false, length = 8)
    @Builder.Default
    private String raisedByType = "CHATBOT";

    @Column(name = "raised_by_id")
    private Long raisedById;

    /** Staff member working it. */
    @Column(name = "assigned_to_id")
    private Long assignedToId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;
}
