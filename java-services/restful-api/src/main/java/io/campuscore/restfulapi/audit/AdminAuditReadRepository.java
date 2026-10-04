package io.campuscore.restfulapi.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read side of the admin audit trail (round-3 contract ct-2). The trail was
 * write-only — AdminAuditRecorder INSERTs and nothing in the codebase ever
 * SELECTed it, so "admin xem audit" meant opening the database directly and no
 * endpoint existed on which scoping could even be evaluated. Governance roles
 * read here; everyone else never reaches the controller.
 */
@Repository
@Profile("persistence")
public class AdminAuditReadRepository {

    private static final String AUDIT = "campuscore_audit.\"AdminAudit\"";
    static final int MAX_PAGE_SIZE = 100;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminAuditReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AuditEntry(
            String id,
            String actorId,
            String actorLabel,
            String action,
            String entityType,
            String entityId,
            String summary,
            String beforeState,
            String afterState,
            Instant createdAt) {
    }

    public record AuditPage(List<AuditEntry> data, long total, int page, int limit, long totalPages) {
    }

    /**
     * Newest first with an id tiebreaker: admin operations batch inside the
     * same transaction timestamp, and an OFFSET walk without one reshuffles
     * tied rows between pages (the announcement-feed defect, round-3 thesis-1).
     */
    public AuditPage page(
            int page,
            int limit,
            String action,
            String entityType,
            String entityId,
            String actorId) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        int effectivePage = Math.max(1, page);
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", effectiveLimit)
                .addValue("offset", (long) (effectivePage - 1) * effectiveLimit);
        // The UI labels read "contains", so action/entityType match as
        // case-insensitive substrings; entityId/actorId stay exact.
        appendContainsFilter(where, parameters, "\"action\" ILIKE :action", "action", action);
        appendContainsFilter(where, parameters, "\"entityType\" ILIKE :entityType", "entityType", entityType);
        appendFilter(where, parameters, "\"entityId\" = :entityId", "entityId", entityId);
        appendFilter(where, parameters, "\"actorId\" = :actorId", "actorId", actorId);
        String clause = where.length() == 0 ? "" : " WHERE " + where;
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + AUDIT + clause, parameters, Long.class);
        List<AuditEntry> rows = jdbc.query(
                "SELECT \"id\", \"actorId\", \"actorLabel\", \"action\", \"entityType\", \"entityId\","
                        + " \"summary\", \"beforeState\", \"afterState\", \"createdAt\""
                        + " FROM " + AUDIT + clause
                        + " ORDER BY \"createdAt\" DESC, \"id\" DESC LIMIT :limit OFFSET :offset",
                parameters,
                (rs, rowNum) -> new AuditEntry(
                        rs.getString("id"),
                        rs.getString("actorId"),
                        rs.getString("actorLabel"),
                        rs.getString("action"),
                        rs.getString("entityType"),
                        rs.getString("entityId"),
                        rs.getString("summary"),
                        rs.getString("beforeState"),
                        rs.getString("afterState"),
                        toInstant(rs.getTimestamp("createdAt"))));
        long totalValue = total == null ? 0L : total;
        long totalPages = totalValue == 0 ? 0 : ((totalValue - 1) / effectiveLimit) + 1;
        return new AuditPage(List.copyOf(rows), totalValue, effectivePage, effectiveLimit, totalPages);
    }

    private static void appendFilter(
            StringBuilder where,
            MapSqlParameterSource parameters,
            String predicate,
            String name,
            String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (where.length() > 0) {
            where.append(" AND ");
        }
        where.append(predicate);
        parameters.addValue(name, value.trim());
    }

    private static void appendContainsFilter(
            StringBuilder where,
            MapSqlParameterSource parameters,
            String predicate,
            String name,
            String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (where.length() > 0) {
            where.append(" AND ");
        }
        where.append(predicate);
        // Escape LIKE metacharacters so a typed '%' is literal, not a wildcard.
        String escaped = value.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        parameters.addValue(name, "%" + escaped + "%");
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
