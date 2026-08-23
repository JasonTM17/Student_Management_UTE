package io.campuscore.restfulapi.thesis.assistant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only knowledge adapter for the local thesis assistant RAG seed. */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-assistant", name = "enabled", havingValue = "true")
public class ThesisAssistantKnowledgeRepository {

    private static final RowMapper<KnowledgeDocument> ROW_MAPPER =
            ThesisAssistantKnowledgeRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisAssistantKnowledgeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeDocument> findForLocale(String locale) {
        return jdbc.query(
                """
                SELECT slug, locale, title, content, source, priority
                FROM assistant.knowledge_document
                WHERE locale IN (:locale, 'both')
                ORDER BY priority ASC, created_at DESC, slug ASC
                """,
                new MapSqlParameterSource("locale", locale),
                ROW_MAPPER);
    }

    private static KnowledgeDocument mapRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new KnowledgeDocument(
                resultSet.getString("slug"),
                resultSet.getString("locale"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("source"),
                resultSet.getInt("priority"));
    }

    public record KnowledgeDocument(
            String slug,
            String locale,
            String title,
            String content,
            String source,
            int priority) {
    }
}
