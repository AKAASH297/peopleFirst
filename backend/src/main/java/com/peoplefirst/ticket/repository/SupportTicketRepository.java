package com.peoplefirst.ticket.repository;

import com.peoplefirst.ticket.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
