package io.campuscore.restfulapi.engagement.service;

import io.campuscore.restfulapi.engagement.repository.SupportTicketWriteRepository;
import io.campuscore.restfulapi.engagement.repository.SupportTicketWriteRepository.CreateTicketCommand;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketWriteDtos.CreateSupportTicketRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded write service for feature-gated support-ticket creation. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-write", name = "enabled", havingValue = "true")
public class SupportTicketWriteService {

    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final SupportTicketWriteRepository tickets;
    private final Clock clock = Clock.systemUTC();

    public SupportTicketWriteService(SupportTicketWriteRepository tickets) {
        this.tickets = tickets;
    }

    @Transactional
    public SupportTicketResponse create(CurrentTicketUser user, CreateSupportTicketRequest request) {
        String priority = request.priority() == null ? "MEDIUM" : request.priority();
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("priority must be LOW, MEDIUM, HIGH, or CRITICAL");
        }
        String id = UUID.randomUUID().toString();
        String ticketNumber = "TKT-%05d".formatted(tickets.countTickets() + 1);
        Instant createdAt = Instant.now(clock);
        return tickets.create(new CreateTicketCommand(
                id,
                ticketNumber,
                requireText(user.id(), "user id"),
                requireText(user.email(), "email"),
                user.displayName(),
                request.subject(),
                request.description(),
                request.category(),
                priority,
                createdAt));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public record CurrentTicketUser(String id, String email, String displayName) {
    }
}
