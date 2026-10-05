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
@AssistantRlsBoundary
public class ThesisAssistantKnowledgeRepository {

    private static final RowMapper<KnowledgeDocument> ROW_MAPPER =
            ThesisAssistantKnowledgeRepository::mapRow;

    /**
     * Characters that end a word in the published corpus. They are folded to
     * spaces so "term flanked by spaces" is an exact whole-word test.
     */
    // Round-4: markdown markers join the set — the V93 corpus is bulleted
    // markdown ("- **Label**: text"), and without folding "*"/"#"/">"/"-"
    // a term wrapped in emphasis or opening a heading would lose its
    // whole-word bonus (POSITION(' term ')) and could drop out of the
    // retrieval window entirely.
    private static final String[] WORD_DELIMITERS = {
            ".", ",", ":", ";", "!", "?", "(", ")", "[", "]", "\"", "'",
            "*", "#", ">", "-"};


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

    /**
     * Plain-text twin of {@link #spaceDelimited(String)} — folds one
     * materialized value into the same padded token stream the SQL builds
     * inside the query, so a Java-side recomputation cannot drift from the
     * retrieval engine's whole-word semantics.
     */
    private static String delimitedTokens(String value) {
        String tokens = " " + (value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)) + " ";
        for (String delimiter : WORD_DELIMITERS) {
            tokens = tokens.replace(delimiter, " ");
        }
        return tokens.replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    /**
     * Per-term contribution identical to the scoring CASE expression in
     * {@link #search}: title substring 3, content substring 1, title
     * whole-word 4, content whole-word 2. The lexical fast path uses this to
     * demand corroboration from more than one term before trusting a top
     * document — a single generic morpheme ("công" inside "Công nghệ") can
     * reach the full 10 alone, and summing those used to let off-topic
     * questions like "công thức nấu phở bò" answer from the IT-services
     * document.
     */
    static int termContribution(String title, String content, String term) {
        if (term == null || term.length() < 2) return 0;
        String loweredTitle = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
        String loweredContent = content == null ? "" : content.toLowerCase(java.util.Locale.ROOT);
        int score = 0;
        if (loweredTitle.contains(term)) score += 3;
        if (loweredContent.contains(term)) score += 1;
        if (delimitedTokens(loweredTitle).contains(" " + term + " ")) score += 4;
        if (delimitedTokens(loweredContent).contains(" " + term + " ")) score += 2;
        return score;
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AssistantRlsTransactionRunner transactions;
    private final boolean allowLegacyFallback;
    private final boolean snapshotCacheEnabled;
    private final long snapshotCacheTtlNanos;

    public ThesisAssistantKnowledgeRepository(
            @org.springframework.beans.factory.annotation.Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE)
            NamedParameterJdbcTemplate jdbc) {
        this(jdbc, false, true, DEFAULT_SNAPSHOT_TTL_MS, null);
    }

    public ThesisAssistantKnowledgeRepository(
            @org.springframework.beans.factory.annotation.Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE)
            NamedParameterJdbcTemplate jdbc,
            @Value("${assistant.legacy-retrieval-fallback:false}") boolean allowLegacyFallback) {
        this(jdbc, allowLegacyFallback, true, DEFAULT_SNAPSHOT_TTL_MS, null);
    }

    public ThesisAssistantKnowledgeRepository(
            @org.springframework.beans.factory.annotation.Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE)
            NamedParameterJdbcTemplate jdbc,
            boolean allowLegacyFallback, boolean snapshotCacheEnabled, long snapshotCacheTtlMs) {
        this(jdbc, allowLegacyFallback, snapshotCacheEnabled, snapshotCacheTtlMs, null);
    }

    @Autowired
    public ThesisAssistantKnowledgeRepository(
            @org.springframework.beans.factory.annotation.Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE)
            NamedParameterJdbcTemplate jdbc,
            @Value("${assistant.legacy-retrieval-fallback:false}") boolean allowLegacyFallback,
            @Value("${assistant.knowledge.snapshot-cache.enabled:true}") boolean snapshotCacheEnabled,
            @Value("${assistant.knowledge.snapshot-cache.ttl-ms:" + DEFAULT_SNAPSHOT_TTL_MS + "}") long snapshotCacheTtlMs,
            AssistantRlsTransactionRunner transactions) {
        this.jdbc = jdbc;
        this.allowLegacyFallback = allowLegacyFallback;
        this.snapshotCacheEnabled = snapshotCacheEnabled;
        this.snapshotCacheTtlNanos = java.util.concurrent.TimeUnit.MILLISECONDS
                .toNanos(Math.max(1_000L, snapshotCacheTtlMs));
        this.transactions = transactions;
    }

    /**
     * In-memory snapshot of the active release's PUBLIC corpus. The published
     * corpus is shared across every caller (the RLS policy on
     * knowledge_runtime_document is scope-scoped, never owner-scoped), so one
     * TTL-bounded copy is a faithful substitute for per-request SQL scoring —
     * on a remote database each {@link #search} call otherwise costs a full
     * network round-trip before the fast path can even decide.
     *
     * <p>Staleness bound: a republished release propagates within
     * {@code assistant.knowledge.snapshot-cache.ttl-ms} (default
     * {@value #DEFAULT_SNAPSHOT_TTL_MS} ms). The load failure path returns
     * {@code null} so callers degrade to the unchanged SQL query — a missing
     * runtime projection or a database outage must never wedge search on the
     * cache.
     */
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(ThesisAssistantKnowledgeRepository.class);
    private static final long DEFAULT_SNAPSHOT_TTL_MS = 30_000L;
    private static final long SNAPSHOT_FAILURE_BACKOFF_NANOS =
            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(5_000L);

    private volatile List<SnapshotRow> snapshotCache;
    private volatile long snapshotLoadedAtNanos;
    private volatile long snapshotFailureAtNanos;

    record SnapshotRow(KnowledgeDocument document, int priority, Instant publishedAt) { }

    /**
     * Loads every PUBLIC + active document of the active published release in
     * one round-trip. priority/published_at ride along so the in-memory
     * ordering below is byte-for-byte the SQL ORDER BY.
     */
    private List<SnapshotRow> loadPublishedSnapshot() {
        String sql = "SELECT p.source_id AS id, p.slug, p.locale, p.title, p.content, p.source, p.domain, "
                + "p.revision_id, p.version AS revision_version, rel.id AS release_id, "
                + "rel.corpus_version, rel.corpus_hash, p.priority, p.published_at "
                + "FROM assistant.knowledge_runtime_state s "
                + "JOIN assistant.knowledge_release rel ON rel.id = s.active_release_id AND rel.status = 'PUBLISHED' "
                + "JOIN assistant.knowledge_runtime_document p ON p.release_id = rel.id "
                + "WHERE s.singleton = TRUE AND p.active = TRUE AND p.visibility = 'PUBLIC'";
        return jdbc.query(sql, (resultSet, rowNumber) -> new SnapshotRow(
                new KnowledgeDocument(
                        resultSet.getString("id"),
                        resultSet.getString("slug"),
                        resultSet.getString("locale"),
                        resultSet.getString("title"),
                        resultSet.getString("content"),
                        resultSet.getString("source"),
                        resultSet.getString("domain"),
                        null, null, null,
                        resultSet.getObject("revision_id", UUID.class),
                        resultSet.getInt("revision_version"),
                        resultSet.getString("corpus_version"),
                        resultSet.getString("corpus_hash"),
                        resultSet.getObject("release_id", UUID.class),
                        0),
                resultSet.getInt("priority"),
                publishedInstant(resultSet)));
    }

    private static Instant publishedInstant(ResultSet resultSet) throws SQLException {
        java.sql.Timestamp published = resultSet.getTimestamp("published_at");
        return published == null ? Instant.EPOCH : published.toInstant();
    }

    /**
     * A fresh snapshot, or {@code null} when the cache must not be used. The
     * load runs on a suspended REQUIRES_NEW transaction: on Postgres a failed
     * statement aborts the whole transaction (25P02), so an in-tx probe would
     * poison the caller's transaction and defeat the in-request SQL fallback
     * below — isolation keeps the failure contained.
     */
    private List<SnapshotRow> publishedSnapshot() {
        long now = System.nanoTime();
        List<SnapshotRow> cached = snapshotCache;
        if (cached != null && now - snapshotLoadedAtNanos < snapshotCacheTtlNanos) {
            return cached;
        }
        if (now - snapshotFailureAtNanos < SNAPSHOT_FAILURE_BACKOFF_NANOS) {
            // Back off regardless of cache state: a persistently failing load
            // must not serialise every search on a doomed round-trip. Serving
            // the still-resident (stale) snapshot when present is strictly
            // better availability than a hard degrade.
            return cached;
        }
        synchronized (this) {
            now = System.nanoTime();
            if (snapshotCache != null && now - snapshotLoadedAtNanos < snapshotCacheTtlNanos) {
                return snapshotCache;
            }
            if (now - snapshotFailureAtNanos < SNAPSHOT_FAILURE_BACKOFF_NANOS) {
                return snapshotCache;
            }
            try {
                List<SnapshotRow> loaded = transactions == null
                        ? loadPublishedSnapshot()
                        : transactions.executeIsolatedUnchecked(
                                AssistantRlsBoundary.Access.AUTO, this::loadPublishedSnapshot);
                snapshotCache = loaded;
                snapshotLoadedAtNanos = System.nanoTime();
                return loaded;
            } catch (RuntimeException failure) {
                LOG.warn("assistant knowledge snapshot load failed; falling back to per-request SQL: {}",
                        failure.toString());
                snapshotFailureAtNanos = now;
                // Stale-while-error: a cached copy that just expired is a
                // better answer than no cache at all — publish cadence is
                // minutes-to-days, so bounded staleness is safe.
                return snapshotCache;
            }
        }
    }

    /**
     * Replays {@link #search}'s WHERE/ORDER BY over the snapshot. The term
     * predicate is a per-term substring OR over lowered title+content (the
     * SQL's {@code LOWER(..) LIKE %term%}), and the score is the same
     * {@link #termContribution} expression the SQL CASE sums — the fast-path
     * confidence gate therefore sees identical numbers either way.
     */
    static List<KnowledgeDocument> searchSnapshot(List<SnapshotRow> snapshot, String locale,
            List<String> usableTerms, int limit, boolean specializedScope) {
        record Scored(SnapshotRow row, int score) { }
        List<Scored> scored = new ArrayList<>();
        for (SnapshotRow row : snapshot) {
            KnowledgeDocument document = row.document();
            if (specializedScope != "SPECIALIZED".equalsIgnoreCase(document.domain())) {
                continue;
            }
            if (!locale.equalsIgnoreCase(document.locale()) && !"both".equalsIgnoreCase(document.locale())) {
                continue;
            }
            String title = document.title() == null ? "" : document.title().toLowerCase(java.util.Locale.ROOT);
            String content = document.content() == null ? "" : document.content().toLowerCase(java.util.Locale.ROOT);
            boolean anyMatch = false;
            int score = 0;
            for (String term : usableTerms) {
                // retrievalTerms output is literal text — a raw caller passing
                // LIKE metacharacters would diverge from the SQL predicate, so
                // they are dropped rather than reinterpreted.
                if (term.indexOf('%') >= 0 || term.indexOf('_') >= 0 || term.indexOf('\\') >= 0) {
                    continue;
                }
                if (title.contains(term) || content.contains(term)) {
                    anyMatch = true;
                    score += termContribution(document.title(), document.content(), term);
                }
            }
            if (anyMatch) {
                scored.add(new Scored(row, score));
            }
        }
        scored.sort(java.util.Comparator
                .<Scored>comparingInt(s -> locale.equalsIgnoreCase(s.row().document().locale()) ? 0 : 1)
                .thenComparing(java.util.Comparator.<Scored>comparingInt(s -> s.score()).reversed())
                .thenComparingInt(s -> s.row().priority())
                .thenComparing((Scored s) -> s.row().publishedAt(), java.util.Comparator.reverseOrder())
                .thenComparing(s -> s.row().document().slug()));
        List<KnowledgeDocument> result = new ArrayList<>(Math.min(limit, scored.size()));
        for (Scored entry : scored.stream().limit(limit).toList()) {
            KnowledgeDocument d = entry.row().document();
            result.add(new KnowledgeDocument(d.id(), d.slug(), d.locale(), d.title(), d.content(), d.source(),
                    d.domain(), d.catalogEntityType(), d.catalogEntityId(), d.catalogUpdatedAt(),
                    d.revisionId(), d.revisionVersion(), d.corpusVersion(), d.corpusHash(), d.releaseId(),
                    entry.score()));
        }
        return result;
    }

    public List<KnowledgeDocument> search(String locale, List<String> terms, int limit) {
        return search(locale, terms, limit, null);
    }

    /**
     * Lexical retrieval over the active published release. A non-blank scope
     * NARROWS the corpus — it can never widen access beyond the existing
     * active+PUBLIC gates. {@code specialized} restricts results to documents
     * published in the SPECIALIZED domain (the curated professional corpus);
     * the DEFAULT (unscoped) view EXCLUDES that domain entirely. An OWASP
     * engineering document once surfaced as a citation for a student's
     * exam-deferral question (production audit kien-thuc) because the generic
     * search let professional-domain rows compete with campus policy docs —
     * a specialty corpus is opt-in, not part of the public student window.
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

        if (snapshotCacheEnabled) {
            List<SnapshotRow> snapshot = publishedSnapshot();
            if (snapshot != null) {
                return searchSnapshot(snapshot, locale, usableTerms, limit, specializedScope);
            }
        }

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

        // lexical_score is the same ranking expression that drives ORDER BY,
        // surfaced per row so the lexical fast path can gate on the retrieval
        // engine's own confidence instead of recomputing a second score.
        String sql = "SELECT p.source_id AS id, p.slug, p.locale, p.title, p.content, p.source, p.domain, p.revision_id, p.version AS revision_version, "
                + "rel.id AS release_id, rel.corpus_version, rel.corpus_hash, "
                + "(" + scoreExpression + ") AS lexical_score "
                + "FROM assistant.knowledge_runtime_state s "
                + "JOIN assistant.knowledge_release rel ON rel.id = s.active_release_id AND rel.status = 'PUBLISHED' "
                + "JOIN assistant.knowledge_runtime_document p ON p.release_id = rel.id "
                + "WHERE s.singleton = TRUE AND p.active = TRUE AND p.visibility = 'PUBLIC' "
                + (specializedScope ? "AND p.domain = 'SPECIALIZED' " : "AND p.domain <> 'SPECIALIZED' ")
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
                    + "NULL AS release_id, NULL AS corpus_version, NULL AS corpus_hash, "
                    + "(" + legacyScoreExpression + ") AS lexical_score "
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
                resultSet.getObject("release_id", UUID.class),
                resultSet.getInt("lexical_score"));
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
            UUID releaseId,
            int lexicalScore) {
        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source) {
            this(id, slug, locale, title, content, source, "THESIS", null, null, null, null, null, null, null, null, 0);
        }

        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
            String catalogEntityType, String catalogEntityId, Instant catalogUpdatedAt) {
            this(id, slug, locale, title, content, source, "ACADEMIC_CATALOG", catalogEntityType, catalogEntityId, catalogUpdatedAt, null, null, null, null, null, 0);
        }

        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
                String domain, UUID revisionId, Integer revisionVersion) {
            this(id, slug, locale, title, content, source, domain, null, null, null, revisionId, revisionVersion, null, null, null, 0);
        }

        /**
         * Pre-fast-path arity bridge: every fixture that predates the exposed
         * retrieval score binds {@code lexicalScore = 0}, which the confidence
         * gate reads as "never confident" so those documents can only ever feed
         * the provider path, never the lexical fast path.
         */
        public KnowledgeDocument(String id, String slug, String locale, String title, String content, String source,
                String domain, String catalogEntityType, String catalogEntityId, Instant catalogUpdatedAt,
                UUID revisionId, Integer revisionVersion, String corpusVersion, String corpusHash, UUID releaseId) {
            this(id, slug, locale, title, content, source, domain, catalogEntityType, catalogEntityId, catalogUpdatedAt,
                    revisionId, revisionVersion, corpusVersion, corpusHash, releaseId, 0);
        }
    }
}
