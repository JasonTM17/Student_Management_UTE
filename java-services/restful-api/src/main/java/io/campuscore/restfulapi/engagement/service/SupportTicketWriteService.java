package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.engagement.repository.SupportTicketWriteRepository;
import io.campuscore.restfulapi.engagement.repository.SupportTicketWriteRepository.CreateTicketResponseCommand;
import io.campuscore.restfulapi.engagement.repository.SupportTicketWriteRepository.CreateTicketCommand;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.TicketResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketWriteDtos.AssignSupportTicketRequest;
import io.campuscore.restfulapi.engagement.web.SupportTicketWriteDtos.CreateTicketResponseRequest;
import io.campuscore.restfulapi.engagement.web.SupportTicketWriteDtos.CreateSupportTicketRequest;
import io.campuscore.restfulapi.engagement.web.SupportTicketWriteDtos.UpdateSupportTicketRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Bounded write service for feature-gated support-ticket creation. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-write", name = "enabled", havingValue = "true")
public class SupportTicketWriteService {

    private static final int MAX_TICKET_NUMBER_ATTEMPTS = 5;
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");

    private final SupportTicketWriteRepository tickets;
    private final Clock clock = Clock.systemUTC();

    public SupportTicketWriteService(SupportTicketWriteRepository tickets) {
        this.tickets = tickets;
    }

    public SupportTicketResponse create(CurrentTicketUser user, CreateSupportTicketRequest request) {
        String priority = request.priority() == null ? "MEDIUM" : request.priority();
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("priority must be LOW, MEDIUM, HIGH, or CRITICAL");
        }
        String userId = requireText(user.id(), "user id");
        String userEmail = requireText(user.email(), "email");
        for (int attempt = 0; attempt < MAX_TICKET_NUMBER_ATTEMPTS; attempt++) {
            String ticketNumber = "TKT-%05d".formatted(tickets.nextTicketSequence());
            try {
                return tickets.create(new CreateTicketCommand(
                        UUID.randomUUID().toString(),
                        ticketNumber,
                        userId,
                        userEmail,
                        user.displayName(),
                        request.subject(),
                        request.description(),
                        request.category(),
                        priority,
                        Instant.now(clock)));
            } catch (DuplicateKeyException exception) {
                if (attempt == MAX_TICKET_NUMBER_ATTEMPTS - 1) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("unable to allocate support ticket number");
    }

    @Transactional
    public TicketResponse respond(String ticketId, CurrentTicketUser user, CreateTicketResponseRequest request) {
        String id = requireText(ticketId, "ticket id");
        String status = tickets.findTicketStatus(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        TicketResponse response = tickets.addResponse(new CreateTicketResponseCommand(
                UUID.randomUUID().toString(),
                id,
                requireText(user.id(), "user id"),
                requireText(user.email(), "email"),
                user.displayName(),
                requireText(request.message(), "message"),
                Boolean.TRUE.equals(request.isInternal()),
                Instant.now(clock)));
        if ("OPEN".equals(status)) {
            tickets.markOpenTicketInProgress(id, Instant.now(clock));
        }
        return response;
    }

    @Transactional
    public SupportTicketResponse update(String ticketId, UpdateSupportTicketRequest request) {
        String id = requireText(ticketId, "ticket id");
        tickets.findTicketStatus(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        String priority = optional(request.priority());
        String status = optional(request.status());
        if (priority != null && !PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("priority must be LOW, MEDIUM, HIGH, or CRITICAL");
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be OPEN, IN_PROGRESS, RESOLVED, or CLOSED");
        }
        tickets.update(new SupportTicketWriteRepository.UpdateTicketCommand(
                id,
                optional(request.subject()),
                optional(request.description()),
                optional(request.category()),
                priority,
                status,
                Instant.now(clock)));
        return tickets.findTicket(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support ticket not found"));
    }

    @Transactional
    public SupportTicketResponse assign(String ticketId, AssignSupportTicketRequest request) {
        String id = requireText(ticketId, "ticket id");
        tickets.findTicketStatus(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support ticket not found"));
        if (request.assignedTo() == null) {
            throw new IllegalArgumentException("assignedTo is required");
        }
        tickets.assign(id, request.assignedTo(), Instant.now(clock));
        return tickets.findTicket(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support ticket not found"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record CurrentTicketUser(String id, String email, String displayName) {
    }
}
