package io.campuscore.engagement.repository;

import io.campuscore.engagement.domain.SupportTicket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Optional<SupportTicket> findById(UUID id);

    Optional<SupportTicket> findByTicketNumber(String ticketNumber);

    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SupportTicket> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("select ticket from SupportTicket ticket where ticket.id = :id and ticket.userId = :userId")
    Optional<SupportTicket> findByIdAndUserId(UUID id, UUID userId);
}
