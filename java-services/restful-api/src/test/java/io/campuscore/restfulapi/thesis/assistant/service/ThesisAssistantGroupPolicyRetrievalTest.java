package io.campuscore.restfulapi.thesis.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.thesis.ThesisAuthorityFixture;
import io.campuscore.restfulapi.thesis.assistant.dto.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.model.KnowledgeDocument;
import io.campuscore.restfulapi.thesis.assistant.model.SnapshotRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import io.campuscore.restfulapi.thesis.assistant.client.RagAssistantGateway;
import io.campuscore.restfulapi.thesis.assistant.dto.ThesisAssistantDtos;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantKnowledgeRepository;

/**
 * Replays the complete PUBLIC V113 corpus through the real repository snapshot
 * scorer and service/controller. No fabricated ranks, cache injection, legacy
 * fallback, live PostgreSQL connection or provider call belongs to this test.
 * Each fresh context owns its H2 projection and real persisted JWT principal.
 */
@SpringBootTest(properties = {
        "assistant.lexical-fast-path=true",
        "assistant.legacy-retrieval-fallback=false",
        "spring.datasource.url=jdbc:h2:mem:thesis_group_policy_retrieval;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ThesisAssistantGroupPolicyRetrievalTest {
    private static final String RELEASE = "00000000-0000-0000-0000-000000000113";
    private static final String CORPUS_HASH = "e29f11cd656a60c626099e1b888155fe3bacb9067787cc48f90ef013ad20f551";
    private static JsonNode corpus;
    private static List<SnapshotRow> snapshot;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private RagAssistantGateway gateway;

    @BeforeAll
    static void capturedPublicCorpus() throws Exception {
        try (var input = ThesisAssistantGroupPolicyRetrievalTest.class.getResourceAsStream(
                "/assistant/group-policy-v113-public-corpus.json")) {
            assertThat(input).isNotNull();
            corpus = tools.jackson.databind.json.JsonMapper.builder().build().readTree(input);
        }
        assertThat(corpus.path("release").path("id").asText()).isEqualTo(RELEASE);
        assertThat(corpus.path("release").path("corpusHash").asText()).isEqualTo(CORPUS_HASH);
        assertThat(corpus.path("documents").size()).isEqualTo(166);
        List<SnapshotRow> rows = new ArrayList<>();
        for (JsonNode row : corpus.path("documents")) {
            assertThat(row.path("active").asBoolean()).isTrue();
            assertThat(row.path("visibility").asText()).isEqualTo("PUBLIC");
            assertThat(row.path("release_id").asText()).isEqualTo(RELEASE);
            KnowledgeDocument document = new KnowledgeDocument(row.path("source_id").asText(),
                    row.path("slug").asText(), row.path("locale").asText(), row.path("title").asText(),
                    row.path("content").asText(), row.path("source").asText(), row.path("domain").asText(),
                    null, null, null, row.path("revision_id").isNull() ? null : UUID.fromString(row.path("revision_id").asText()),
                    row.path("version").asInt(), "local-demo-v113", CORPUS_HASH, UUID.fromString(RELEASE), 0);
            rows.add(new SnapshotRow(document, row.path("priority").asInt(),
                    Instant.parse(row.path("published_at").asText())));
        }
        snapshot = List.copyOf(rows);
    }

    @BeforeEach
    void loadRealPublishedProjectionIntoPrivateH2() {
        // H2's existing test migrations predate the immutable projection. Use
        // the same tables/columns and real runtime join, with fallback disabled.
        jdbc.execute("CREATE TABLE IF NOT EXISTS assistant.knowledge_release (id UUID PRIMARY KEY, "
                + "status VARCHAR(32), corpus_version VARCHAR(120), corpus_hash VARCHAR(64))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS assistant.knowledge_runtime_state (singleton BOOLEAN PRIMARY KEY, active_release_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS assistant.knowledge_runtime_document (release_id UUID, source_id VARCHAR(120), "
                + "revision_id UUID, version INT, domain VARCHAR(64), slug VARCHAR(240), locale VARCHAR(8), title VARCHAR(1024), "
                + "content CLOB, source VARCHAR(240), priority INT, active BOOLEAN, visibility VARCHAR(32), "
                + "published_at TIMESTAMP WITH TIME ZONE, PRIMARY KEY (release_id, source_id))");
        jdbc.update("DELETE FROM assistant.knowledge_runtime_document");
        jdbc.update("DELETE FROM assistant.knowledge_runtime_state");
        jdbc.update("DELETE FROM assistant.knowledge_release");
        jdbc.update("INSERT INTO assistant.knowledge_release VALUES (?, 'PUBLISHED', 'local-demo-v113', ?)", UUID.fromString(RELEASE), CORPUS_HASH);
        jdbc.update("INSERT INTO assistant.knowledge_runtime_state VALUES (TRUE, ?)", UUID.fromString(RELEASE));
        for (JsonNode row : corpus.path("documents")) {
            jdbc.update("INSERT INTO assistant.knowledge_runtime_document VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.fromString(RELEASE), row.path("source_id").asText(),
                    row.path("revision_id").isNull() ? null : UUID.fromString(row.path("revision_id").asText()),
                    row.path("version").asInt(), row.path("domain").asText(), row.path("slug").asText(),
                    row.path("locale").asText(), row.path("title").asText(), row.path("content").asText(),
                    row.path("source").asText(), row.path("priority").asInt(), true, "PUBLIC",
                    OffsetDateTime.parse(row.path("published_at").asText()));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_runtime_document", Integer.class)).isEqualTo(166);
        when(gateway.enabled()).thenReturn(false);
    }

    @ParameterizedTest(name = "Policy headcount cites its actual V113 rule: {1}")
    @CsvSource(delimiter = '|', value = {
            "vi|Nhóm thực hiện đề tài có bao nhiêu thành viên?",
            "en|How many members can a thesis project group have?",
            "vi|Nhom khoa luan co bao nhieu thanh vien?",
            "vi|Nhom khoa luan co may thanh vien?"
    })
    void groupHeadcountUsesQualifiedPolicyInsteadOfTopicGallery(String locale, String question) throws Exception {
        printMeasuredRanking(question, locale);
        JsonNode response = chat(question, locale, null);
        assertThat(response.path("reasonCode").asText()).isEqualTo("ANSWERED");
        assertThat(response.path("model").asText()).isEqualTo(ThesisAssistantService.FAST_PATH_MODEL);
        assertThat(response.path("degraded").asBoolean()).isFalse();
        assertThat(response.path("citations")).hasSize(1);
        JsonNode citation = response.path("citations").get(0);
        assertThat(citation.path("slug").asText()).isEqualTo("thesis-group-members-rules-" + locale);
        assertThat(citation.path("releaseId").asText()).isEqualTo(RELEASE);
        assertThat(citation.path("corpusHash").asText()).isEqualTo(CORPUS_HASH);
        assertThat(citation.path("revisionId").asText()).isNotBlank();
        String answer = response.path("answer").asText();
        if ("vi".equals(locale)) {
            assertThat(answer).contains("3 đến 5", "được duyệt", "1 hoặc 2", "lịch sử");
        } else {
            assertThat(answer).contains("3 to 5", "approval", "1 or 2", "read-only");
        }
        verify(gateway, never()).chat(any(), anyString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "vi|Nhóm thực hiện đề tài có bao nhiêu thành viên?",
            "en|How many members can a thesis project group have?"
    })
    void absentPolicyFailsClosedInsteadOfCitingGallery(String locale, String question) throws Exception {
        jdbc.update("DELETE FROM assistant.knowledge_runtime_document WHERE source = 'campuscore-thesis-rubric-correction-v113'");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assistant.knowledge_runtime_document", Integer.class)).isEqualTo(160);
        JsonNode response = chat(question, locale, null);
        assertThat(response.path("reasonCode").asText()).isEqualTo("NO_MATCH");
        assertThat(response.path("citations")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "vi|Kho đề tài khóa luận tiêu biểu của các khóa trước ở đâu?",
            "en|Where is the repository of exemplary past graduation theses and reference projects?",
            "vi|Kho đề tài khóa luận của các nhóm sinh viên ở đâu?",
            "en|Where is the repository of past thesis projects by student groups?"
    })
    void actualRepositoryQuestionKeepsItsGallery(String locale, String question) throws Exception {
        JsonNode response = chat(question, locale, null);
        assertThat(response.path("reasonCode").asText()).isEqualTo("ANSWERED");
        assertThat(response.path("citations").get(0).path("slug").asText()).isEqualTo("past-thesis-repository-and-reference-topics-" + locale);
    }

    @Test
    void englishPermissionModalDoesNotTurnGalleryRequestIntoVietnameseHeadcount() throws Exception {
        JsonNode response = chat("May student thesis groups use the repository of past projects for reference?", "en", null);
        assertThat(response.path("reasonCode").asText()).isEqualTo("ANSWERED");
        assertThat(response.path("citations").get(0).path("slug").asText())
                .isEqualTo("past-thesis-repository-and-reference-topics-en");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "vi|Nhóm thực hiện đề tài có bao nhiêu thành viên, và lịch thi cuối kỳ khi nào?",
            "en|How many members can a thesis project group have, and when are final exams?"
    })
    void joinedQuestionUsesCompositionAuthorityInsteadOfSingleFastCitation(String locale, String question) throws Exception {
        when(gateway.enabled()).thenReturn(true);
        when(gateway.chat(any(), anyString())).thenReturn(new ChatResponse(
                "Composed authority response", "composition-fixture", false, "ANSWERED", locale, List.of()));
        JsonNode response = chat(question, locale, null);
        assertThat(response.path("model").asText()).isEqualTo("composition-fixture");
        verify(gateway).chat(any(), anyString());
    }

    @Test
    void specializedScopeDoesNotBorrowAcademicGroupPolicy() throws Exception {
        JsonNode response = chat("How many members can a thesis project group have?", "en", "specialized");
        assertThat(response.path("citations").findValuesAsString("slug"))
                .noneMatch(slug -> slug.startsWith("thesis-group-members-rules-"));
    }

    @Test
    void sameGroupQuestionStillRequiresAnAuthenticatedCurrentAccount() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat").contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of("message", "Nhóm thực hiện đề tài có bao nhiêu thành viên?",
                                "locale", "vi", "clientRequestId", UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode chat(String question, String locale, String scope) throws Exception {
        String subject = "group-retrieval-" + UUID.randomUUID();
        var payload = new java.util.LinkedHashMap<String,Object>();
        payload.put("message",question); payload.put("locale",locale); payload.put("clientRequestId",UUID.randomUUID());
        if (scope != null) payload.put("scope",scope);
        var result = mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(ThesisAuthorityFixture.student(jdbc,subject)).contentType("application/json")
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void printMeasuredRanking(String question, String locale) {
        List<String> terms = ThesisAssistantService.retrievalTerms(question);
        List<KnowledgeDocument> ranked = ThesisAssistantKnowledgeRepository.searchSnapshot(snapshot,locale,terms,10,false);
        System.out.println("GROUP_POLICY_MEASURED_TERMS " + locale + " " + terms);
        for (KnowledgeDocument document : ranked) {
            System.out.println("GROUP_POLICY_MEASURED_RANK " + locale + " " + document.lexicalScore() + " " + document.slug());
        }
    }
}
