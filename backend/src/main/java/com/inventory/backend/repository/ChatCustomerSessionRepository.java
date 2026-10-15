package com.inventory.backend.repository;

import com.inventory.backend.model.ChatCustomerSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatCustomerSessionRepository extends JpaRepository<ChatCustomerSession, Long> {

    /**
     * A live session by token hash.
     *
     * <p>The expiry is part of the query rather than checked afterwards,
     * so an expired token is simply absent — there is no code path that
     * can forget to check the date.
     */
    Optional<ChatCustomerSession> findByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    /** A customer's live sessions, for a "signed in as" view. */
    List<ChatCustomerSession> findByCustomerIdAndExpiresAtAfter(Long customerId, LocalDateTime now);

    /** Sessions to purge. */
    List<ChatCustomerSession> findByExpiresAtBefore(LocalDateTime cutoff);
}
