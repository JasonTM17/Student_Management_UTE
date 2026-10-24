package io.campuscore.restfulapi.site.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Single-row KV persistence for the site appearance payload (accent, homepage
 * hero copy, homepage post order). The stored payload is the sanitized JSON
 * object the frontend already validates on read; the repository only owns the
 * SQL and the row shape (single id, actor column width). Validation, the
 * payload-size rule and write stamping live in the service.
 */
@Repository
public class SiteAppearanceRepository {

    /** Single logical row; more ids are never written. */
    public static final String ROW_ID = "default";

    /** Matches the "updatedBy" column width in V73. */
    public static final int MAX_UPDATED_BY_CHARS = 255;

    private static final String TABLE = "site.\"Appearance\"";

    private final NamedParameterJdbcTemplate jdbc;

    public SiteAppearanceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return the stored payload string, or {@code null} when no appearance has
     *         been saved yet (the caller answers with an empty object and the
     *         frontend sanitizer fills the defaults).
     */
    public String read() {
        var rows = jdbc.getJdbcTemplate().queryForList(
                "SELECT \"payload\" FROM " + TABLE + " WHERE \"id\" = ?",
                String.class,
                ROW_ID);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Upserts the already-stamped payload, keeping a single row and the acting
     * administrator label for accountability. Written as update-then-insert
     * rather than INSERT .. ON CONFLICT so the statement stays portable across
     * PostgreSQL and the H2 test engine.
     *
     * @param storedPayload payload text as it is stored, stamp included
     * @param updatedBy immutable actor id (JWT subject) of the writer
     */
    public void upsert(String storedPayload, String updatedBy) {
        String actor = actorLabel(updatedBy);
        int updated = jdbc.update(
                "UPDATE " + TABLE + " SET \"payload\" = :payload, \"updatedBy\" = :updatedBy, "
                        + "\"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource()
                        .addValue("payload", storedPayload)
                        .addValue("updatedBy", actor)
                        .addValue("id", ROW_ID));
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO " + TABLE + " (\"id\", \"payload\", \"updatedBy\", \"updatedAt\") "
                            + "VALUES (:id, :payload, :updatedBy, CURRENT_TIMESTAMP)",
                    new MapSqlParameterSource()
                            .addValue("id", ROW_ID)
                            .addValue("payload", storedPayload)
                            .addValue("updatedBy", actor));
        }
    }

    /**
     * The actor label is accountability metadata, not the payload: a subject
     * longer than the column is clipped so the branding save itself never
     * fails on it.
     */
    private static String actorLabel(String updatedBy) {
        if (updatedBy == null) {
            return null;
        }
        return updatedBy.length() <= MAX_UPDATED_BY_CHARS
                ? updatedBy
                : updatedBy.substring(0, MAX_UPDATED_BY_CHARS);
    }
}
