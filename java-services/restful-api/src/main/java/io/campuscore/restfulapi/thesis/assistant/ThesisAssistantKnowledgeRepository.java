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

    /**
     * Characters that end a word in the published corpus. They are folded to
     * spaces so "term flanked by spaces" is an exact whole-word test.
     */
    private static final String[] WORD_DELIMITERS = {
            ".", ",", ":", ";", "!", "?", "(", ")", "[", "]", "\"", "'"};

    /**
     * A column as a space-delimited token stream, padded so a first or last word
     * is still flanked by spaces. Only functions both PostgreSQL 15 and the H2
     * test schema provide are used: neither engine's word-boundary regex syntax
     * is portable to the other, and a dialect branch here would make retrieval in
     * tests differ silently from retrieval in production.
     */
    private static String spaceDelimited(String column) {
        String expression = "(' ' || LOWER(" + column + ") || ' ')";
        for (String delimiter : WORD_DELIMITERS) {
            expression = "REPLACE(" + expression + ", '" + delimiter.replace("'", "''") + "', ' ')";
        }
        return "REPLACE(REPLACE(REPLACE(" + expression + ", CHR(10), ' '), CHR(13), ' '), CHR(9), ' ')";
    }

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
        return search(locale, terms, limit, null);
    }

    /**
     * Lexical retrieval over the active published release. A non-blank scope
     * NARROWS the corpus — it can never widen access beyond the existing
     * active+PUBLIC gates. {@code specialized} restricts results to documents
     * published in the SPECIALIZED domain (the curated professional corpus).
     */
    public List<KnowledgeDocument> search(String locale, List<String> terms, int limit, String scope) {
        // The service budget is 16 terms and puts folded-phrase aliases first;
        // an 8-term cap here silently dropped exactly those aliases, so
        // unaccented Vietnamese queries degraded to NO_MATCH.
        List<String> usableTerms = terms.stream()
                .filter(term -> term.length() >= 2)
                .distinct()
                .limit(16)
                .toList();
        if (usableTerms.isEmpty()) {
            return List.of();
        }
        boolean specializedScope = "specialized".equalsIgnoreCase(scope);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("locale", locale)
                .addValue("limit", limit);
        List<String> predicates = new ArrayList<>();
        List<String> scoreTerms = new ArrayList<>();
        String boundedTitle = spaceDelimited("p.title");
        String boundedContent = spaceDelimited("p.content");
        for (int index = 0; index < usableTerms.size(); index++) {
            String parameter = "term" + index;
            String wordParameter = "word" + index;
            params.addValue(parameter, "%" + usableTerms.get(index) + "%");
            params.addValue(wordParameter, " " + usableTerms.get(index) + " ");
            predicates.add("(LOWER(p.title) LIKE :" + parameter
                    + " OR LOWER(p.content) LIKE :" + parameter + ")");
            // A whole-word hit outranks an incidental substring inside an
            // unrelated word ("ci" inside "decision"), which is what let a
            // two-character token bury the document that answers the question.
            // The substring score stays as the floor so a stem that only ever
            // appears inside a longer word is still retrievable.
            scoreTerms.add("(CASE WHEN LOWER(p.title) LIKE :" + parameter + " THEN 3 ELSE 0 END + "
                    + "CASE WHEN LOWER(p.content) LIKE :" + parameter + " THEN 1 ELSE 0 END + "
                    + "CASE WHEN POSITION(:" + wordParameter + " IN " + boundedTitle + ") > 0 THEN 4 ELSE 0 END + "
                    + "CASE WHEN POSITION(:" + wordParameter + " IN " + boundedContent + ") > 0 THEN 2 ELSE 0 END)");
        }
        String scoreExpression = String.join(" + ", scoreTerms);

        String sql = "SELECT p.source_id AS id, p.slug, p.locale, p.title, p.content, p.source, p.domain, p.revision_id, p.version AS revision_version, "
                + "rel.id AS release_id, rel.corpus_version, rel.corpus_hash "
                + "FROM assistant.knowledge_runtime_state s "
                + "JOIN assistant.knowledge_release rel ON rel.id = s.active_release_id AND rel.status = 'PUBLISHED' "
                + "JOIN assistant.knowledge_runtime_document p ON p.release_id = rel.id "
                + "WHERE s.singleton = TRUE AND p.active = TRUE AND p.visibility = 'PUBLIC' "
                + (specializedScope ? "AND p.domain = 'SPECIALIZED' " : "")
                + "AND p.locale IN (:locale, 'both') "
                + "AND (" + String.join(" OR ", predicates) + ") "
                + "ORDER BY CASE WHEN p.locale = :locale THEN 0 ELSE 1 END, "
                + "(" + scoreExpression + ") DESC, "
                + "p.priority ASC, p.published_at DESC, p.slug ASC LIMIT :limit";
        try {
            return jdbc.query(sql, params, ROW_MAPPER);
        } catch (org.springframework.jdbc.BadSqlGrammarException missingProjection) {
            // Focused pre-V16 fixtures can still exercise lexical retrieval.
            if (!allowLegacyFallback) throw missingProjection;
            // The legacy projection has no domain column, so a specialized
            // scope cannot be honored there; an empty result is the honest answer.
            if (specializedScope) {
                return List.of();
            }
            String legacyScoreExpression = scoreExpression.replace("p.", "r.");
            String legacySql = "SELECT CAST(d.id AS VARCHAR) AS id, d.slug, r.locale, r.title, r.content, r.source, 'THESIS' AS domain, r.id AS revision_id, r.version AS revision_version, "
                    + "NULL AS release_id, NULL AS corpus_version, NULL AS corpus_hash "
                    + "FROM assistant.knowledge_document d "
                    + "JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED' "
                    + "WHERE d.active = TRUE AND d.visibility = 'PUBLIC' "
                    + "AND r.locale IN (:locale, 'both') "
                    + "AND (" + String.join(" OR ", predicates).replace("p.", "r.") + ") "
                    + "ORDER BY CASE WHEN r.locale = :locale THEN 0 ELSE 1 END, "
                    + "(" + legacyScoreExpression + ") DESC, "
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
