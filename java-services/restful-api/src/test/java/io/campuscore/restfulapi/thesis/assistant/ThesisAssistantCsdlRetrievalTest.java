package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Backlog regression gate: "Tư vấn về CSDL" used to answer NO_MATCH because the
 * query produced only the acronym "csdl" while the corpus spells the concept
 * "Cơ sở dữ liệu quan hệ" — the acronym never appears in any seeded row, so the
 * LIKE sweep found nothing.
 *
 * The folded-phrase alias "csdl" now expands to the accented corpus phrase, and
 * because the lookup folds first, the same key also catches the accented phrase
 * and unaccented keyboards ("co so du lieu").
 */
class ThesisAssistantCsdlRetrievalTest {

    private static final String CSDL_SLUG = "specialized-relational-database-design-sql-optimization-vi";

    private static final String CSDL_QUESTION = "Tư vấn về CSDL";

    @Test
    @DisplayName("retrieval expands the 'CSDL' acronym to the accented corpus phrase")
    void retrievalExpandsTheCsdlAcronym() {
        assertTrue(ThesisAssistantService.retrievalTerms(CSDL_QUESTION).contains("cơ sở dữ liệu"),
                "the acronym was not expanded: " + ThesisAssistantService.retrievalTerms(CSDL_QUESTION));
        // The accented phrase and the unaccented keyboard spelling fold onto the
        // same alias key, so all three spellings reach the same term.
        assertTrue(ThesisAssistantService.retrievalTerms("cơ sở dữ liệu là gì").contains("cơ sở dữ liệu"));
        assertTrue(ThesisAssistantService.retrievalTerms("co so du lieu la gi").contains("cơ sở dữ liệu"));
    }

    @Test
    @DisplayName("the scope pre-gate admits the CSDL question before search")
    void scopeGateAdmitsTheCsdlQuestion() {
        assertTrue(ThesisAssistantService.hasPublicScopeSignal(CSDL_QUESTION));
        assertTrue(ThesisAssistantService.hasPublicScopeSignal("co so du lieu la gi"));
    }

    @Test
    @DisplayName("the CSDL question reaches retrieval and cites the seeded document")
    void csdlQuestionRetrievesTheSeededDocument() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        String seeded = seededCsdlDocumentText().toLowerCase(Locale.ROOT);
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents = List.of(
                new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                        "88888888-8888-8888-8888-888888888888", CSDL_SLUG, "vi",
                        "Thiết kế cơ sở dữ liệu quan hệ và tối ưu truy vấn SQL",
                        seeded, "office", "SPECIALIZED", null, null, null, null, null, null, null, null, 30));
        // Same containment semantics as the repository's LIKE predicates.
        when(knowledge.search(eq("vi"), anyList(), anyInt())).thenAnswer(invocation -> {
            List<String> terms = invocation.getArgument(1);
            return terms.stream().anyMatch(seeded::contains) ? documents : List.of();
        });

        ChatResponse response = new ThesisAssistantService(knowledge).answer(CSDL_QUESTION, "vi");

        ArgumentCaptor<List> terms = ArgumentCaptor.forClass(List.class);
        verify(knowledge, atLeastOnce()).search(eq("vi"), terms.capture(), anyInt());
        assertTrue(terms.getAllValues().stream().anyMatch(captured -> captured.contains("cơ sở dữ liệu")),
                "retrieval ran without the expanded phrase: " + terms.getAllValues());

        assertFalse("NO_MATCH".equals(response.reasonCode()),
                "the query still fell to NO_MATCH: " + response.answer());
        assertTrue(response.citations().stream().anyMatch(citation -> CSDL_SLUG.equals(citation.slug())),
                "expected the seeded CSDL document among the citations: " + response.citations());
    }

    @Test
    @DisplayName("the pre-fix acronym terms could never match the seeded row")
    void prefixTermsDemonstrateTheMiss() {
        String seeded = seededCsdlDocumentText().toLowerCase(Locale.ROOT);
        List<String> beforeFix = List.of("tư vấn", "csdl");
        assertFalse(matchesAnySubstring(seeded, beforeFix),
                "the seeded row now contains the old terms; this gate no longer demonstrates the miss");
        assertTrue(matchesAnySubstring(seeded, ThesisAssistantService.retrievalTerms(CSDL_QUESTION)));
    }

    private static boolean matchesAnySubstring(String document, List<String> terms) {
        String searchable = document.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(searchable::contains);
    }

    /**
     * The published CSDL row exactly as V71 seeds it, so this gate fails the
     * moment the document the fix is about stops existing.
     */
    private static String seededCsdlDocumentText() {
        String migration = readResource("db/migration/V71__specialized_domain_knowledge.sql");
        int start = migration.indexOf("'" + CSDL_SLUG + "'");
        assertTrue(start >= 0, CSDL_SLUG + " is no longer seeded by V71");
        int end = migration.indexOf("'SPECIALIZED'", start);
        assertTrue(end > start, "the seeded CSDL row is malformed");
        String row = migration.substring(start, end);
        assertTrue(row.contains("Cơ sở dữ liệu quan hệ"),
                "the seeded CSDL row no longer carries the accented phrase the alias expands to");
        return row;
    }

    private static String readResource(String path) {
        try (InputStream input = ThesisAssistantCsdlRetrievalTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertTrue(input != null, "missing classpath resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
