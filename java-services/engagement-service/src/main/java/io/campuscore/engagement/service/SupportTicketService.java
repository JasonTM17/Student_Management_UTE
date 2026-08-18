package io.campuscore.engagement.service;

import io.campuscore.engagement.api.EngagementDtos;
import io.campuscore.engagement.domain.SupportTicket;
import io.campuscore.engagement.domain.SupportTicket.Priority;
import io.campuscore.engagement.domain.SupportTicket.Status;
import io.campuscore.engagement.domain.TicketResponse;
import io.campuscore.engagement.repository.SupportTicketRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportTicketService {

    private final SupportTicketRepository tickets;

    public SupportTicketService(SupportTicketRepository tickets) {
        this.tickets = tickets;
    }

    @Transactional
    public EngagementDtos.TicketResponse create(EngagementDtos.CreateTicketRequest request, UUID userId, String email, String displayName) {
        SupportTicket ticket = new SupportTicket(
                userId,
                email,
                displayName,
                request.subject(),
                request.description(),
                request.category(),
                request.priority() == null ? Priority.MEDIUM : Priority.valueOf(request.priority()));
        return toResponse(tickets.save(ticket));
    }

    @Transactional(readOnly = true)
    public Page<EngagementDtos.TicketResponse> list(Pageable pageable) {
        return tickets.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<EngagementDtos.TicketResponse> listByUser(UUID userId, Pageable pageable) {
        return tickets.findAllByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EngagementDtos.TicketResponse get(UUID id) {
        return toResponse(tickets.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public EngagementDtos.TicketResponse getForUser(UUID id, UUID userId) {
        return toResponse(tickets.findByIdAndUserId(id, userId).orElseThrow());
    }

    @Transactional
    public EngagementDtos.TicketResponse assign(UUID id, UUID assigneeId) {
        SupportTicket ticket = tickets.findById(id).orElseThrow();
        ticket.assign(assigneeId, null);
        return toResponse(tickets.save(ticket));
    }

    @Transactional
    public EngagementDtos.TicketResponse addResponse(UUID id, UUID userId, String email, String displayName, String message, boolean isInternal) {
        SupportTicket ticket = tickets.findById(id).orElseThrow();
        ticket.addResponse(new TicketResponse(userId, email, displayName, message, isInternal));
        return toResponse(tickets.save(ticket));
    }

    private EngagementDtos.TicketResponse toResponse(SupportTicket ticket) {
        return new EngagementDtos.TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getUserId(),
                ticket.getUserEmail(),
                ticket.getUserDisplayName(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority().name(),
                ticket.getStatus().name(),
                ticket.getAssignedTo(),
                ticket.getAssignedToDisplayName(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                ticket.getCreatedAt());
    }
}
