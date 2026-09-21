package io.campuscore.restfulapi.site;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Single-row KV store for the site appearance payload (accent, homepage hero
 * copy, homepage post order). The payload is the sanitized JSON object the
 * frontend already validates on read; the store only enforces existence and a
 * hard size bound so an administrator cannot wedge the table.
 */
@Service
public class SiteAppearanceStore {

    /** Single logical row; more ids are never written. */
    public static final String ROW_ID = "default";

    /** Matches the payload CHECK constraint in V73. */
    static final int MAX_PAYLOAD_CHARS = 131_072;

    private static final String TABLE = "site.\"Appearance\"";

    private final NamedParameterJdbcTemplate jdbc;

    public SiteAppearanceStore(NamedParameterJdbcTemplate jdbc) {
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
     * Upserts the payload, keeping a single row and the acting administrator
     * label for accountability. Written as update-then-insert rather than
     * INSERT .. ON CONFLICT so the statement stays portable across PostgreSQL
     * and the H2 test engine.
     *
     * @param payload raw JSON object text; must already be parsed and within
     *                {@link #MAX_PAYLOAD_CHARS}
     * @param updatedBy immutable actor id (JWT subject) of the writer
     * @return the exact payload text that was stored
     */
    public String write(String payload, String updatedBy) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must be a JSON object");
        }
        if (payload.length() > MAX_PAYLOAD_CHARS) {
            throw new IllegalArgumentException("payload exceeds the size limit");
        }
        int updated = jdbc.update(
                "UPDATE " + TABLE + " SET \"payload\" = :payload, \"updatedBy\" = :updatedBy, "
                        + "\"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource()
                        .addValue("payload", payload)
                        .addValue("updatedBy", updatedBy)
                        .addValue("id", ROW_ID));
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO " + TABLE + " (\"id\", \"payload\", \"updatedBy\", \"updatedAt\") "
                            + "VALUES (:id, :payload, :updatedBy, CURRENT_TIMESTAMP)",
                    new MapSqlParameterSource()
                            .addValue("id", ROW_ID)
                            .addValue("payload", payload)
                            .addValue("updatedBy", updatedBy));
        }
        return payload;
    }
}
