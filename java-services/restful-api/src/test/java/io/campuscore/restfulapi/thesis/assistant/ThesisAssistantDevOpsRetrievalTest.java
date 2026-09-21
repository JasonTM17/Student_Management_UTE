package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * DEEP-P3-3 regression gate for assistant retrieval of the seeded DevOps/CI-CD
 * document.
 *
 * Three defects stacked up: the scope pre-gate refused any question that did
 * not name a campus topic (so CI/CD never reached the database), the term
 * splitter shredded "CI/CD" into the two-character pair "ci"/"cd", and the
 * scoring query weighted a substring inside an unrelated word the same as a
 * whole-word hit.
 *
 * The score model in the last test mirrors the predicates this class emits for
 * PostgreSQL; it is not a live query, and the SQL text itself is asserted
 * separately so the model cannot drift from the emitted statement unnoticed.
 */
class ThesisAssistantDevOpsRetrievalTest {

    private static final String DEVOPS_SLUG = "specialized-devops-cicd-docker-kubernetes-en";

    private static final String CI_CD_QUESTION = "What is a CI/CD pipeline?";

    /** A document that only ever contains "ci" inside unrelated English words. */
    private static final String SUBSTRING_NOISE = "Campus conduct rules"
            + " Special decisions about capacity in every province, and the necessity"
            + " of social conduct codes for each facility.";

    @Test
    @DisplayName("retrieval terms keep a multi-part acronym intact instead of shredding it")
    void retrievalTermsKeepMultiPartAcronymsIntact() {
        assertEquals(List.of("ci/cd", "pipeline"), ThesisAssistantService.retrievalTerms(CI_CD_QUESTION));

        // Slash, hyphen, space and closed spellings are one concept in the corpus.
        assertTrue(ThesisAssistantService.retrievalTerms("CI-CD là gì?").contains("ci/cd"));
        assertTrue(ThesisAssistantService.retrievalTerms("giải thích cicd giúp tôi").contains("ci/cd"));
        assertTrue(ThesisAssistantService.retrievalTerms("Quy trình ci cd của trường").contains("ci/cd"));
        assertTrue(ThesisAssistantService.retrievalTerms("Dev Ops cho sinh viên").contains("devops"));

        // The two-character remnants would match inside "decision" and "proceed".
        List<String> terms = ThesisAssistantService.retrievalTerms(CI_CD_QUESTION);
        assertFalse(terms.contains("ci"), "bare \"ci\" is substring noise");
        assertFalse(terms.contains("cd"), "bare \"cd\" is substring noise");
    }

    @Test
    @DisplayName("the scope pre-gate admits the DevOps vocabulary the corpus publishes")
    void scopeGateAdmitsDevOpsVocabulary() {
        assertTrue(ThesisAssistantService.PUBLIC_SCOPE_TOPICS.stream().anyMatch(topic -> topic.contains("ci")),
                "the curated topic list still has no CI/CD vocabulary");
        assertTrue(ThesisAssistantService.hasPublicScopeSignal(CI_CD_QUESTION));
        assertTrue(ThesisAssistantService.hasPublicScopeSignal("Quy trình CI/CD là gì?"));
        assertTrue(ThesisAssistantService.hasPublicScopeSignal("DevOps cho sinh viên"));
        assertTrue(ThesisAssistantService.hasPublicScopeSignal("docker pipeline bị lỗi"));

        // Widening the gate must not dissolve it: the refusal cases stay refused.
        assertFalse(ThesisAssistantService.hasPublicScopeSignal("chào bạn nhé"));
        assertFalse(ThesisAssistantService.hasPublicScopeSignal("hello there"));
    }

    @Test
    @DisplayName("the CI/CD question reaches retrieval and cites the seeded DevOps document")
    void ciCdQuestionRetrievesTheSeededDevOpsDocument() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        String seeded = seededDevOpsDocumentText().toLowerCase(java.util.Locale.ROOT);
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> devops = List.of(
                new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                        "77777777-7777-7777-7777-777777777777", DEVOPS_SLUG, "en",
                        "DevOps for students: Docker, CI/CD and reproducible runtime environments",
                        seeded, "office", "SPECIALIZED", (java.util.UUID) null, (Integer) null));
        // Stand in for the published corpus with the same containment semantics
        // the repository asserts below: LOWER(title/content) LIKE '%term%'.
        when(knowledge.search(eq("en"), anyList(), anyInt())).thenAnswer(invocation -> {
            List<String> terms = invocation.getArgument(1);
            return terms.stream().anyMatch(seeded::contains) ? devops : List.of();
        });

        ChatResponse response = new ThesisAssistantService(knowledge).answer(CI_CD_QUESTION, "en");

        ArgumentCaptor<List> terms = ArgumentCaptor.forClass(List.class);
        verify(knowledge).search(eq("en"), terms.capture(), anyInt());
        assertTrue(terms.getValue().contains("ci/cd"),
                "retrieval ran with the shredded terms " + terms.getValue());

        assertNotRefusedBeforeSearch(response);
        assertEquals(1, response.citations().size(), "expected the DevOps document as the citation");
        assertEquals(DEVOPS_SLUG, response.citations().get(0).slug());
    }

    /** Pre-fix this short-circuited to NO_MATCH with zero citations and no query. */
    private static void assertNotRefusedBeforeSearch(ChatResponse response) {
        assertFalse("NO_MATCH".equals(response.reasonCode()),
                "the query was still refused before retrieval: " + response.answer());
        assertFalse(response.citations().isEmpty(), "the seeded DevOps document was not retrieved");
    }

    @Test
    @DisplayName("the emitted query scores whole-word hits above substring hits")
    void emittedQueryPrefersWordBoundaryMatches() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ThesisAssistantKnowledgeRepository knowledge = new ThesisAssistantKnowledgeRepository(jdbc);

        knowledge.search("en", ThesisAssistantService.retrievalTerms(CI_CD_QUESTION), 5);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));

        String statement = sql.getValue();
        assertTrue(statement.contains("(LOWER(p.title) LIKE :term0 OR LOWER(p.content) LIKE :term0)"),
                "the recall predicate changed shape: " + statement);
        // Whole-word hits are decided in SQL, on a punctuation-folded token
        // stream, because neither PostgreSQL's nor H2's word-boundary regex is
        // portable to the other.
        assertTrue(statement.contains("POSITION(:word0 IN REPLACE("), statement);
        assertTrue(statement.contains("|| LOWER(p.title) ||") && statement.contains("|| LOWER(p.content) ||"),
                "the token stream must be padded so the first and last word count");
        assertTrue(statement.contains("THEN 4") && statement.contains("THEN 2"),
                "the word-boundary bonus is missing from the score");
        int orderBy = statement.indexOf("ORDER BY");
        assertTrue(statement.indexOf("THEN 4") > orderBy
                        && statement.indexOf("THEN 4") < statement.indexOf("LIMIT :limit"),
                "the word-boundary weight must drive the ordering, not sit in the projection");
        // The padded term is bound as data, so no delimiter can break the literal.
        assertEquals(" ci/cd ", parameters.getValue().getValue("word0"));
        assertEquals("%ci/cd%", parameters.getValue().getValue("term0"));
        assertEquals(" pipeline ", parameters.getValue().getValue("word1"));
    }

    @Test
    @DisplayName("a substring-only document stops being a candidate for the acronym")
    void substringOnlyDocumentsStopBeingCandidates() {
        String seeded = seededDevOpsDocumentText();
        List<String> beforeFixTerms = List.of("ci", "cd", "pipeline");
        List<String> afterFixTerms = ThesisAssistantService.retrievalTerms(CI_CD_QUESTION);

        // Old terms: the conduct document is retrieved as if it answered the
        // question, because "ci" is a substring of "decision" and "capacity".
        assertTrue(matchesAnySubstring(SUBSTRING_NOISE, beforeFixTerms),
                "the pre-fix term set no longer demonstrates the substring defect");
        assertFalse(matchesAnySubstring(SUBSTRING_NOISE, afterFixTerms),
                "the fixed term set still matches by substring alone");
        assertTrue(matchesAnySubstring(seeded, afterFixTerms));

        // Old weights could not separate the two; the word-boundary bonus does.
        assertTrue(score(seeded, afterFixTerms) > score(SUBSTRING_NOISE, afterFixTerms));
        assertEquals(0, score(SUBSTRING_NOISE, afterFixTerms), "noise must not earn a partial score");
    }

    private static boolean matchesAnySubstring(String document, List<String> terms) {
        String searchable = document.toLowerCase(java.util.Locale.ROOT);
        return terms.stream().anyMatch(searchable::contains);
    }

    /** Mirrors the score expression asserted in {@link #emittedQueryPrefersWordBoundaryMatches()}. */
    private static int score(String document, List<String> terms) {
        String substringSource = document.toLowerCase(java.util.Locale.ROOT);
        String tokenStream = " " + substringSource
                .replaceAll("[.,:;!?()\\[\\]\"'\\n\\r\\t]", " ") + " ";
        int total = 0;
        for (String term : terms) {
            if (substringSource.contains(term)) total += 1;
            if (tokenStream.contains(" " + term + " ")) total += 2;
        }
        return total;
    }

    /**
     * The published DevOps row exactly as V71 seeds it, so this gate fails the
     * moment the document that the fix is about stops existing.
     */
    private static String seededDevOpsDocumentText() {
        String migration = readResource("db/migration/V71__specialized_domain_knowledge.sql");
        int start = migration.indexOf("'" + DEVOPS_SLUG + "'");
        assertTrue(start >= 0, DEVOPS_SLUG + " is no longer seeded by V71");
        int end = migration.indexOf("'SPECIALIZED'", start);
        assertTrue(end > start, "the seeded DevOps row is malformed");
        String row = migration.substring(start, end);
        assertTrue(row.contains("CI/CD"), "the seeded DevOps row no longer mentions CI/CD");
        return row;
    }

    private static String readResource(String path) {
        try (InputStream input = ThesisAssistantDevOpsRetrievalTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertTrue(input != null, "missing classpath resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
