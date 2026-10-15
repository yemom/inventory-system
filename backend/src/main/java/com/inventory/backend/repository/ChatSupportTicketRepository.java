package com.inventory.backend.repository;

import com.inventory.backend.model.ChatSupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSupportTicketRepository extends JpaRepository<ChatSupportTicket, Long> {

    /** Tickets for one customer, newest first. */
    List<ChatSupportTicket> findByCustomerIdOrderByIdDesc(Long customerId);

    /** The customer's tickets a staff member would see when they pick the conversation up. */
    List<ChatSupportTicket> findByConversationIdOrderByIdDesc(Long conversationId);

    /** Queue for a staff view. */
    List<ChatSupportTicket> findByStatusOrderByIdAsc(ChatSupportTicket.Status status);
}
