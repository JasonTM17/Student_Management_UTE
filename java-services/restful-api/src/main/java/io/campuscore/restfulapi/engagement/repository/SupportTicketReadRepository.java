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
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read adapter for Prisma-owned support-ticket tables.
 *
 * <p>It intentionally performs SELECTs only. Support-ticket creation,
 * assignment, responses, updates, and deletion remain owned by the legacy
 * engagement service until writer handoff and rollback gates pass.</p>
 */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.engagement-read", name = "enabled", havingValue = "true")
public class SupportTicketReadRepository {

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

    public SupportTicketReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SupportTicketResponse> findByUser(String userId, long offset, int limit) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("offset", offset)
                .addValue("limit", limit);
        List<TicketRow> tickets = jdbc.query(
                "SELECT " + TICKET_COLUMNS + " FROM " + TICKET
                        + " WHERE \"userId\" = :userId"
                        + " ORDER BY \"createdAt\" DESC LIMIT :limit OFFSET :offset",
                parameters,
                SupportTicketReadRepository::mapTicketRow);
        return hydrate(tickets, false);
    }

    public long countByUser(String userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TICKET + " WHERE \"userId\" = :userId",
                new MapSqlParameterSource("userId", userId),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<SupportTicketResponse> findOneByUser(String id, String userId) {
        List<TicketRow> tickets = jdbc.query(
                "SELECT " + TICKET_COLUMNS + " FROM " + TICKET
                        + " WHERE \"id\" = :id AND \"userId\" = :userId",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", userId),
                SupportTicketReadRepository::mapTicketRow);
        return hydrate(tickets, false).stream().findFirst();
    }

    public List<SupportTicketResponse> findAll(SupportTicketFilter filter, long offset, int limit) {
        SqlWhere where = adminWhere(filter);
        where.parameters().addValue("offset", offset).addValue("limit", limit);
        List<TicketRow> tickets = jdbc.query(
                "SELECT " + TICKET_COLUMNS + " FROM " + TICKET + where.sql()
                        + " ORDER BY \"createdAt\" DESC LIMIT :limit OFFSET :offset",
                where.parameters(),
                SupportTicketReadRepository::mapTicketRow);
        return hydrate(tickets, true);
    }

    public long countAll(SupportTicketFilter filter) {
        SqlWhere where = adminWhere(filter);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TICKET + where.sql(),
                where.parameters(),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<SupportTicketResponse> findOne(String id) {
        List<TicketRow> tickets = jdbc.query(
                "SELECT " + TICKET_COLUMNS + " FROM " + TICKET + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                SupportTicketReadRepository::mapTicketRow);
        return hydrate(tickets, true).stream().findFirst();
    }

    private List<SupportTicketResponse> hydrate(List<TicketRow> tickets, boolean includeInternalResponses) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        List<String> ids = tickets.stream().map(TicketRow::id).toList();
        Map<String, List<TicketResponse>> responsesByTicket = new LinkedHashMap<>();
        String responseVisibilityPredicate = includeInternalResponses ? "" : " AND \"isInternal\" = FALSE";
        for (TicketResponse response : jdbc.query(
                "SELECT " + RESPONSE_COLUMNS + " FROM " + RESPONSE
                        + " WHERE \"ticketId\" IN (:ids)" + responseVisibilityPredicate
                        + " ORDER BY \"ticketId\" ASC, \"createdAt\" ASC",
                new MapSqlParameterSource("ids", ids),
                SupportTicketReadRepository::mapResponse)) {
            responsesByTicket.computeIfAbsent(response.ticketId(), ignored -> new ArrayList<>()).add(response);
        }
        return tickets.stream()
                .map(ticket -> ticket.response(responsesByTicket.getOrDefault(ticket.id(), List.of())))
                .toList();
    }

    private static SqlWhere adminWhere(SupportTicketFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        addEquals(conditions, parameters, "status", filter.status());
        addEquals(conditions, parameters, "priority", filter.priority());
        addEquals(conditions, parameters, "category", filter.category());
        return new SqlWhere(conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions), parameters);
    }

    private static void addEquals(
            List<String> conditions,
            MapSqlParameterSource parameters,
            String column,
            String value) {
        if (value != null) {
            conditions.add("\"" + column + "\" = :" + column);
            parameters.addValue(column, value);
        }
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
        String displayName = displayName(resultSet.getString("userDisplayName"), userEmail);
        return new TicketResponse(
                resultSet.getString("id"),
                resultSet.getString("ticketId"),
                userId,
                userEmail,
                resultSet.getString("userDisplayName"),
                resultSet.getString("message"),
                resultSet.getBoolean("isInternal"),
                instant(resultSet, "createdAt"),
                new TicketUser(userId, userEmail, displayName));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String displayName(String displayName, String email) {
        return displayName == null || displayName.isBlank() ? email : displayName;
    }

    public record SupportTicketFilter(String status, String priority, String category) {
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

    private record SqlWhere(String sql, MapSqlParameterSource parameters) {
    }
}
