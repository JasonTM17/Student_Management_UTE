package io.campuscore.restfulapi.thesis.assistant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bounded, database-side lexical retrieval for the curated thesis corpus. */
@Repository
@Profile("persistence")
public class ThesisAssistantKnowledgeRepository {

    private static final RowMapper<KnowledgeDocument> ROW_MAPPER =
            ThesisAssistantKnowledgeRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisAssistantKnowledgeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeDocument> search(String locale, List<String> terms, int limit) {
        List<String> usableTerms = terms.stream()
                .filter(term -> term.length() >= 2)
                .distinct()
                .limit(8)
                .toList();
        if (usableTerms.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("locale", locale)
                .addValue("limit", limit);
        List<String> predicates = new ArrayList<>();
        for (int index = 0; index < usableTerms.size(); index++) {
            String parameter = "term" + index;
            params.addValue(parameter, "%" + usableTerms.get(index) + "%");
            predicates.add("(LOWER(r.title) LIKE :" + parameter
                    + " OR LOWER(r.content) LIKE :" + parameter + ")");
        }

        String sql = "SELECT CAST(d.id AS VARCHAR) AS id, d.slug, r.locale, r.title, r.content, r.source, r.id AS revision_id, r.version AS revision_version "
                + "FROM assistant.knowledge_document d "
                + "JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED' "
                + "WHERE d.active = TRUE AND d.visibility = 'PUBLIC' "
                + "AND r.locale IN (:locale, 'both') "
                + "AND (" + String.join(" OR ", predicates) + ") "
                + "ORDER BY CASE WHEN r.locale = :locale THEN 0 ELSE 1 END, "
                + "r.priority ASC, r.published_at DESC, d.slug ASC LIMIT :limit";
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    private static KnowledgeDocument mapRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new KnowledgeDocument(
                resultSet.getString("id"),
                resultSet.getString("slug"),
                resultSet.getString("locale"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("source"),
                null,
                null,
                null,
                resultSet.getObject("revision_id", UUID.class),
                resultSet.getInt("revision_version"));
    }

    public record KnowledgeDocument(
            String id,
            String slug,
            String locale,
            String title,
            String content,
            String source,
            String catalogEntityType,
            String catalogEntityId,
            Instant catalogUpdatedAt,
            UUID revisionId,
            Integer revisionVersion) {
        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source) {
            this(id, slug, locale, title, content, source, null, null, null, null, null);
        }

        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
                String catalogEntityType, String catalogEntityId, Instant catalogUpdatedAt) {
            this(id, slug, locale, title, content, source, catalogEntityType, catalogEntityId, catalogUpdatedAt, null, null);
        }
    }
}
