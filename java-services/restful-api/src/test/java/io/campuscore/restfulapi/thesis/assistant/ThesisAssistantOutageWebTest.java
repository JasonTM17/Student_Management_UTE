package io.campuscore.restfulapi.thesis.assistant;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.thesis.ThesisAuthorityFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantOutageWebTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ThesisAssistantKnowledgeRepository knowledge;

    @MockitoBean
    private io.campuscore.restfulapi.academic.service.AcademicConductService conduct;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService enrollments;

    @Test
    void databaseOutageKeepsThePublicDegradedContractThroughTheWebLayer() throws Exception {
        when(knowledge.search(anyString(), anyList(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(student("assistant-outage-knowledge"))
                        .contentType("application/json")
                        .content("{\"message\":\"Điều kiện đăng ký đề tài là gì?\",\"locale\":\"vi\",\"clientRequestId\":\"00000000-0000-4000-8000-000000000011\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("curated-lexical-rag"))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.reasonCode").value("KNOWLEDGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.locale").value("vi"))
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    /**
     * The web console no longer answers personal-data questions locally; they now
     * reach this endpoint. Whatever the knowledge base state, each handover intent
     * (grades, conduct, tuition, announcements, registration) must keep the public
     * degraded chat contract instead of surfacing a server error.
     */
    @Test
    void personalDataHandoverQuestionsKeepTheDegradedChatContract() throws Exception {
        when(knowledge.search(anyString(), anyList(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        // Knowledge failure does not fail the independent conduct database.
        // Inject the personal-data fault explicitly instead of relying on a
        // missing schema left over from another test class. The H2 chain now
        // mirrors the academic read tables (V144), so the grades path faults
        // on its service seam the same way the conduct path does.
        when(conduct.studentSummary(anyString()))
                .thenThrow(new DataAccessResourceFailureException("conduct database unavailable"));
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("enrollment database unavailable"))
                .when(enrollments).findStudentGrades(anyString(), org.mockito.ArgumentMatchers.isNull());

        String[][] handedOverQuestions = {
                {"00000000-0000-4000-8000-000000000021", "điểm của tôi hiện tại thế nào?"},
                {"00000000-0000-4000-8000-000000000022", "điểm rèn luyện của tôi mấy điểm?"},
                {"00000000-0000-4000-8000-000000000023", "học phí học kỳ này của tôi bao nhiêu?"},
                {"00000000-0000-4000-8000-000000000024", "thông báo mới nhất dành cho tôi là gì?"},
                {"00000000-0000-4000-8000-000000000025", "tôi đủ điều kiện đăng ký học phần chưa?"},
        };

        for (String[] question : handedOverQuestions) {
            // A current student token reaches the personal advisor.  It must
            // return the degraded personal-data contract when its backing data
            // is unavailable; knowledge questions retain their own degraded
            // contract. Neither path may surface a 5xx or invent an answer.
            String body = mvc.perform(post("/api/v1/thesis/assistant/chat")
                            .with(student("assistant-outage-personal-" + question[0]))
                            .contentType("application/json")
                            .content("{\"message\":\"" + question[1]
                                    + "\",\"locale\":\"vi\",\"clientRequestId\":\"" + question[0] + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locale").value("vi"))
                    .andReturn().getResponse().getContentAsString();
            boolean personal = body.contains("\"reasonCode\":\"PERSONAL_CONTEXT_UNAVAILABLE\"")
                    && body.contains("\"model\":\"campuscore-personal-context\"")
                    && body.contains("\"degraded\":true");
            boolean outage = body.contains("\"reasonCode\":\"KNOWLEDGE_UNAVAILABLE\"")
                    && body.contains("\"model\":\"curated-lexical-rag\"")
                    && body.contains("\"degraded\":true");
            org.junit.jupiter.api.Assertions.assertTrue(personal || outage,
                    "question '" + question[1] + "' must keep the honest personal or degraded-outage contract: " + body);
        }
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor student(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }
}
