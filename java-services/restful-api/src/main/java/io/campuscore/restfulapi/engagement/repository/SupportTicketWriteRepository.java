package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.TicketUser;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Feature-gated support-ticket creation adapter for the Java monolith.
 *
 * <p>This is a bounded write candidate only. The legacy engagement service
 * remains canonical until PostgreSQL parity, canary and rollback gates pass.</p>
 */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-write", name = "enabled", havingValue = "true")
public class SupportTicketWriteRepository {

    private static final String TICKET = "\"engagement\".\"SupportTicket\"";

    private final NamedParameterJdbcTemplate jdbc;

    public SupportTicketWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long nextTicketSequence() {
        return jdbc.query(
                        "SELECT \"ticketNumber\" FROM " + TICKET + " WHERE \"ticketNumber\" LIKE 'TKT-%'",
                        new MapSqlParameterSource(),
                        (resultSet, rowNumber) -> resultSet.getString("ticketNumber"))
                .stream()
                .map(SupportTicketWriteRepository::parseTicketSequence)
                .filter(sequence -> sequence > 0)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L) + 1;
    }

    public SupportTicketResponse create(CreateTicketCommand command) {
        LocalDateTime createdAt = LocalDateTime.ofInstant(command.createdAt(), ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO " + TICKET
                        + " (\"id\", \"ticketNumber\", \"userId\", \"userEmail\", \"userDisplayName\","
                        + " \"subject\", \"description\", \"category\", \"priority\", \"status\","
                        + " \"assignedTo\", \"assignedToDisplayName\", \"resolvedAt\", \"closedAt\","
                        + " \"createdAt\", \"updatedAt\")"
                        + " VALUES (:id, :ticketNumber, :userId, :userEmail, :userDisplayName,"
                        + " :subject, :description, :category, :priority, 'OPEN',"
                        + " NULL, NULL, NULL, NULL, :createdAt, :createdAt)",
                new MapSqlParameterSource()
                        .addValue("id", command.id())
                        .addValue("ticketNumber", command.ticketNumber())
                        .addValue("userId", command.userId())
                        .addValue("userEmail", command.userEmail())
                        .addValue("userDisplayName", command.userDisplayName())
                        .addValue("subject", command.subject())
                        .addValue("description", command.description())
                        .addValue("category", command.category())
                        .addValue("priority", command.priority())
                        .addValue("createdAt", createdAt));
        return new SupportTicketResponse(
                command.id(),
                command.ticketNumber(),
                command.userId(),
                command.userEmail(),
                command.userDisplayName(),
                command.subject(),
                command.description(),
                command.category(),
                command.priority(),
                "OPEN",
                null,
                null,
                null,
                null,
                command.createdAt(),
                command.createdAt(),
                new TicketUser(command.userId(), command.userEmail(), displayName(command.userDisplayName(), command.userEmail())),
                List.of());
    }

    private static String displayName(String displayName, String email) {
        return displayName == null || displayName.isBlank() ? email : displayName;
    }

    private static long parseTicketSequence(String ticketNumber) {
        if (ticketNumber == null || !ticketNumber.startsWith("TKT-")) {
            return 0;
        }
        try {
            return Long.parseLong(ticketNumber.substring(4));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    public record CreateTicketCommand(
            String id,
            String ticketNumber,
            String userId,
            String userEmail,
            String userDisplayName,
            String subject,
            String description,
            String category,
            String priority,
            Instant createdAt) {
    }
}
