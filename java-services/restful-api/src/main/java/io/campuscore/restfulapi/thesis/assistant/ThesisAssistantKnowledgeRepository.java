package io.campuscore.restfulapi.thesis.assistant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
            predicates.add("(LOWER(title) LIKE :" + parameter
                    + " OR LOWER(content) LIKE :" + parameter + ")");
        }

        String sql = "SELECT CAST(id AS VARCHAR) AS id, slug, locale, title, content, source "
                + "FROM assistant.knowledge_document "
                + "WHERE active = TRUE AND visibility = 'PUBLIC' "
                + "AND locale IN (:locale, 'both') "
                + "AND (" + String.join(" OR ", predicates) + ") "
                + "ORDER BY CASE WHEN locale = :locale THEN 0 ELSE 1 END, "
                + "priority ASC, updated_at DESC, slug ASC LIMIT :limit";
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
                resultSet.getString("source"));
    }

    public record KnowledgeDocument(
            String id,
            String slug,
            String locale,
            String title,
            String content,
            String source) {
    }
}
