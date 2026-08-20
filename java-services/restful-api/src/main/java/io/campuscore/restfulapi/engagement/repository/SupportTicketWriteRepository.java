package io.campuscore.restfulapi.engagement.repository;

import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.SupportTicketResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.TicketResponse;
import io.campuscore.restfulapi.engagement.web.SupportTicketReadDtos.TicketUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final String RESPONSE = "\"engagement\".\"TicketResponse\"";
    private static final String TICKET_COLUMNS = """
            "id", "ticketNumber", "userId", "userEmail", "userDisplayName",
            "subject", "description", "category", "priority", "status",
            "assignedTo", "assignedToDisplayName", "resolvedAt", "closedAt",
            "createdAt", "updatedAt"
            """;
    private static final String RESPONSE_COLUMNS = """
            "id", "ticketId", "userId", "userEmail", "userDisplayName",
            "message", "isInternal", "createdAt"
            """;

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
                .stream()
                .map(sequence -> {
                    if (sequence == Long.MAX_VALUE) {
                        throw new IllegalStateException("support ticket number range exhausted");
                    }
                    return sequence + 1;
                })
                .findFirst()
                .orElse(1L);
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
                        .addValue("priority", command.priority(), Types.OTHER)
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

    public Optional<String> findTicketStatus(String ticketId) {
        List<String> statuses = jdbc.query(
                "SELECT \"status\" FROM " + TICKET + " WHERE \"id\" = :ticketId",
                new MapSqlParameterSource("ticketId", ticketId),
                (resultSet, ignored) -> resultSet.getString("status"));
        return statuses.stream().findFirst();
    }

    public TicketResponse addResponse(CreateTicketResponseCommand command) {
        LocalDateTime createdAt = LocalDateTime.ofInstant(command.createdAt(), ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO " + RESPONSE
                        + " (\"id\", \"ticketId\", \"userId\", \"userEmail\", \"userDisplayName\","
                        + " \"message\", \"isInternal\", \"createdAt\")"
                        + " VALUES (:id, :ticketId, :userId, :userEmail, :userDisplayName,"
                        + " :message, :isInternal, :createdAt)",
                new MapSqlParameterSource()
                        .addValue("id", command.id())
                        .addValue("ticketId", command.ticketId())
                        .addValue("userId", command.userId())
                        .addValue("userEmail", command.userEmail())
                        .addValue("userDisplayName", command.userDisplayName())
                        .addValue("message", command.message())
                        .addValue("isInternal", command.isInternal())
                        .addValue("createdAt", createdAt));
        return new TicketResponse(
                command.id(),
                command.ticketId(),
                command.userId(),
                command.userEmail(),
                command.userDisplayName(),
                command.message(),
                command.isInternal(),
                command.createdAt(),
                new TicketUser(command.userId(), command.userEmail(), displayName(command.userDisplayName(), command.userEmail())));
    }

    public void markOpenTicketInProgress(String ticketId, Instant updatedAt) {
        jdbc.update(
                "UPDATE " + TICKET
                        + " SET \"status\" = 'IN_PROGRESS', \"updatedAt\" = :updatedAt"
                        + " WHERE \"id\" = :ticketId AND \"status\" = 'OPEN'",
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("updatedAt", LocalDateTime.ofInstant(updatedAt, ZoneOffset.UTC)));
    }

    public void update(UpdateTicketCommand command) {
        List<String> assignments = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", command.id());
        addOptionalAssignment(assignments, parameters, "subject", command.subject(), null);
        addOptionalAssignment(assignments, parameters, "description", command.description(), null);
        addOptionalAssignment(assignments, parameters, "category", command.category(), null);
        addOptionalAssignment(assignments, parameters, "priority", command.priority(), Types.OTHER);
        addOptionalAssignment(assignments, parameters, "status", command.status(), Types.OTHER);
        if ("RESOLVED".equals(command.status())) {
            assignments.add("\"resolvedAt\" = :updatedAt");
        } else if ("CLOSED".equals(command.status())) {
            assignments.add("\"closedAt\" = :updatedAt");
        }
        assignments.add("\"updatedAt\" = :updatedAt");
        parameters.addValue("updatedAt", LocalDateTime.ofInstant(command.updatedAt(), ZoneOffset.UTC));
        jdbc.update(
                "UPDATE " + TICKET + " SET " + String.join(", ", assignments) + " WHERE \"id\" = :id",
                parameters);
    }

    public Optional<SupportTicketResponse> findTicket(String id) {
        List<TicketRow> tickets = jdbc.query(
                "SELECT " + TICKET_COLUMNS + " FROM " + TICKET + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                SupportTicketWriteRepository::mapTicketRow);
        return hydrate(tickets).stream().findFirst();
    }

    private static String displayName(String displayName, String email) {
        return displayName == null || displayName.isBlank() ? email : displayName;
    }

    private static void addOptionalAssignment(
            List<String> assignments,
            MapSqlParameterSource parameters,
            String column,
            String value,
            Integer sqlType) {
        if (value != null) {
            assignments.add("\"" + column + "\" = :" + column);
            if (sqlType == null) {
                parameters.addValue(column, value);
            } else {
                parameters.addValue(column, value, sqlType);
            }
        }
    }

    private List<SupportTicketResponse> hydrate(List<TicketRow> tickets) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        List<String> ids = tickets.stream().map(TicketRow::id).toList();
        Map<String, List<TicketResponse>> responsesByTicket = new LinkedHashMap<>();
        for (TicketResponse response : jdbc.query(
                "SELECT " + RESPONSE_COLUMNS + " FROM " + RESPONSE
                        + " WHERE \"ticketId\" IN (:ids)"
                        + " ORDER BY \"ticketId\" ASC, \"createdAt\" ASC",
                new MapSqlParameterSource("ids", ids),
                SupportTicketWriteRepository::mapResponse)) {
            responsesByTicket.computeIfAbsent(response.ticketId(), ignored -> new ArrayList<>()).add(response);
        }
        return tickets.stream()
                .map(ticket -> ticket.response(responsesByTicket.getOrDefault(ticket.id(), List.of())))
                .toList();
    }

    private static TicketRow mapTicketRow(ResultSet resultSet, int ignored) throws SQLException {
        return new TicketRow(
                resultSet.getString("id"),
                resultSet.getString("ticketNumber"),
                resultSet.getString("userId"),
                resultSet.getString("userEmail"),
                resultSet.getString("userDisplayName"),
                resultSet.getString("subject"),
                resultSet.getString("description"),
                resultSet.getString("category"),
                resultSet.getString("priority"),
                resultSet.getString("status"),
                resultSet.getString("assignedTo"),
                resultSet.getString("assignedToDisplayName"),
                instant(resultSet, "resolvedAt"),
                instant(resultSet, "closedAt"),
                instant(resultSet, "createdAt"),
                instant(resultSet, "updatedAt"));
    }

    private static TicketResponse mapResponse(ResultSet resultSet, int ignored) throws SQLException {
        String userId = resultSet.getString("userId");
        String userEmail = resultSet.getString("userEmail");
        return new TicketResponse(
                resultSet.getString("id"),
                resultSet.getString("ticketId"),
                userId,
                userEmail,
                resultSet.getString("userDisplayName"),
                resultSet.getString("message"),
                resultSet.getBoolean("isInternal"),
                instant(resultSet, "createdAt"),
                new TicketUser(userId, userEmail, displayName(resultSet.getString("userDisplayName"), userEmail)));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static long parseTicketSequence(String ticketNumber) {
        if (ticketNumber == null || !ticketNumber.startsWith("TKT-")) {
            return 0;
        }
        String suffix = ticketNumber.substring(4);
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) {
            return 0;
        }
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("support ticket number suffix exceeds supported range", exception);
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

    public record CreateTicketResponseCommand(
            String id,
            String ticketId,
            String userId,
            String userEmail,
            String userDisplayName,
            String message,
            boolean isInternal,
            Instant createdAt) {
    }

    public record UpdateTicketCommand(
            String id,
            String subject,
            String description,
            String category,
            String priority,
            String status,
            Instant updatedAt) {
    }

    private record TicketRow(
            String id,
            String ticketNumber,
            String userId,
            String userEmail,
            String userDisplayName,
            String subject,
            String description,
            String category,
            String priority,
            String status,
            String assignedTo,
            String assignedToDisplayName,
            Instant resolvedAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt) {

        private SupportTicketResponse response(List<TicketResponse> responses) {
            return new SupportTicketResponse(
                    id,
                    ticketNumber,
                    userId,
                    userEmail,
                    userDisplayName,
                    subject,
                    description,
                    category,
                    priority,
                    status,
                    assignedTo,
                    assignedToDisplayName,
                    resolvedAt,
                    closedAt,
                    createdAt,
                    updatedAt,
                    new TicketUser(userId, userEmail, displayName(userDisplayName, userEmail)),
                    responses);
        }
    }
}
