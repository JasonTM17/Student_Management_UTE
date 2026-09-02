package io.campuscore.restfulapi.thesis.assistant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final boolean allowLegacyFallback;

    public ThesisAssistantKnowledgeRepository(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, false);
    }

    @Autowired
    public ThesisAssistantKnowledgeRepository(NamedParameterJdbcTemplate jdbc,
            @Value("${assistant.legacy-retrieval-fallback:false}") boolean allowLegacyFallback) {
        this.jdbc = jdbc;
        this.allowLegacyFallback = allowLegacyFallback;
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
            predicates.add("(LOWER(p.title) LIKE :" + parameter
                    + " OR LOWER(p.content) LIKE :" + parameter + ")");
        }

        String sql = "SELECT p.source_id AS id, p.slug, p.locale, p.title, p.content, p.source, p.domain, p.revision_id, p.version AS revision_version, "
                + "rel.id AS release_id, rel.corpus_version, rel.corpus_hash "
                + "FROM assistant.knowledge_runtime_state s "
                + "JOIN assistant.knowledge_release rel ON rel.id = s.active_release_id AND rel.status = 'PUBLISHED' "
                + "JOIN assistant.knowledge_runtime_document p ON p.release_id = rel.id "
                + "WHERE s.singleton = TRUE AND p.active = TRUE AND p.visibility = 'PUBLIC' "
                + "AND p.locale IN (:locale, 'both') "
                + "AND (" + String.join(" OR ", predicates) + ") "
                + "ORDER BY CASE WHEN p.locale = :locale THEN 0 ELSE 1 END, "
                + "p.priority ASC, p.published_at DESC, p.slug ASC LIMIT :limit";
        try {
            return jdbc.query(sql, params, ROW_MAPPER);
        } catch (org.springframework.jdbc.BadSqlGrammarException missingProjection) {
            // Focused pre-V16 fixtures can still exercise lexical retrieval.
            if (!allowLegacyFallback) throw missingProjection;
            String legacySql = "SELECT CAST(d.id AS VARCHAR) AS id, d.slug, r.locale, r.title, r.content, r.source, 'THESIS' AS domain, r.id AS revision_id, r.version AS revision_version, "
                    + "NULL AS release_id, NULL AS corpus_version, NULL AS corpus_hash "
                    + "FROM assistant.knowledge_document d "
                    + "JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED' "
                    + "WHERE d.active = TRUE AND d.visibility = 'PUBLIC' "
                    + "AND r.locale IN (:locale, 'both') "
                    + "AND (" + String.join(" OR ", predicates).replace("p.", "r.") + ") "
                    + "ORDER BY CASE WHEN r.locale = :locale THEN 0 ELSE 1 END, "
                    + "r.priority ASC, r.published_at DESC, d.slug ASC LIMIT :limit";
            return jdbc.query(legacySql, params, ROW_MAPPER);
        }
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
                resultSet.getString("domain"),
                null,
                null,
                null,
                resultSet.getObject("revision_id", UUID.class),
                resultSet.getInt("revision_version"),
                resultSet.getString("corpus_version"),
                resultSet.getString("corpus_hash"),
                resultSet.getObject("release_id", UUID.class));
    }

    public record KnowledgeDocument(
            String id,
            String slug,
            String locale,
            String title,
            String content,
            String source,
            String domain,
            String catalogEntityType,
            String catalogEntityId,
            Instant catalogUpdatedAt,
            UUID revisionId,
            Integer revisionVersion,
            String corpusVersion,
            String corpusHash,
            UUID releaseId) {
        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source) {
            this(id, slug, locale, title, content, source, "THESIS", null, null, null, null, null, null, null, null);
        }

        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
            String catalogEntityType, String catalogEntityId, Instant catalogUpdatedAt) {
            this(id, slug, locale, title, content, source, "ACADEMIC_CATALOG", catalogEntityType, catalogEntityId, catalogUpdatedAt, null, null, null, null, null);
        }

        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
                String domain, UUID revisionId, Integer revisionVersion) {
            this(id, slug, locale, title, content, source, domain, null, null, null, revisionId, revisionVersion, null, null, null);
        }
    }
}
