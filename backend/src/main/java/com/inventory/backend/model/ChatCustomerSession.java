package com.inventory.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A customer's chatbot session.
 *
 * <h2>Why a session at all</h2>
 *
 * <p>A customer has no staff account, so there is no JWT to hold.
 * Instead, a shopper proves they can see one order — by naming the
 * email and the order number that appear on their receipt — and is
 * handed a random token that stands for "this customer, for the next
 * few hours".
 *
 * <h2>The token is stored hashed, or not at all</h2>
 *
 * <p>Only the hash is kept. A database leak therefore hands over
 * nothing usable, and the token itself is shown to the customer once
 * and lives only in their browser.
 *
 * <h2>Why it expires</h2>
 *
 * <p>Because the session is a proof, not an identity. It says "whoever
 * holds this can see this customer's own orders" — a statement that
 * should stop being true on its own, without the customer having to
 * remember to sign out of a shop assistant.
 */
@Entity
@Table(name = "chat_customer_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatCustomerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The customer this session is scoped to. Every lookup filters on it. */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** The customer's name, so a conversation can be labelled without a join. */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /**
     * Hash of the session token.
     *
     * <p>Compared, never reversed. The plain token leaves the server
     * exactly once, in the response that created the session.
     */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** After this, the token resolves to nothing. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** The order number the customer proved they could see. */
    @Column(name = "verified_order_number", length = 50)
    private String verifiedOrderNumber;

    /**
     * The plain token, present only on the object that just created
     * the session.
     *
     * <p>{@code transient}: it is never written to the database. It
     * exists so the response that opened the session can show the
     * token exactly once, and no loaded entity ever carries a
     * working token with it afterwards.
     */
    @Transient
    private String tokenPlain;
}
