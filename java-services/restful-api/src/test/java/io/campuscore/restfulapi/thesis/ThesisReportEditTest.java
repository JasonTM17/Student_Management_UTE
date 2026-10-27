package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The non-destructive report edit path (plan F4): PATCH metadata/link and
 * DELETE file must never destroy the sibling artifact, submitted_at/submitted_by
 * stay immutable after the first submission, and every edit keeps at least one
 * artifact (ck_report_artifact) under the same write gates as submit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_report_edit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "thesis.report.storage.local-root=target/test-report-storage"
})
class ThesisReportEditTest {

    private static final byte[] DOCX_BYTES = {'P', 'K', 0x03, 0x04, 'e', 'd', 'i', 't', '-', 'd', 'o', 'c', 'x'};
    private static final String LINK = "https://drive.example.com/report.pdf";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 're-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 're-user-%' OR \"id\" LIKE 'thesis-fixture-student-re-%'");
    }

    @Test
    void patchMetadataKeepsTheStoredDocumentAndSubmissionIdentity() throws Exception {
        UUID groupId = seedApprovedGroup();
        uploadFile(groupId);

        String storageKey = stringColumn(groupId, "storage_key");
        Object submittedAt = column(groupId, "submitted_at");
        String submittedBy = stringColumn(groupId, "submitted_by");
        assertThat(storageKey).isNotNull();

        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed report\",\"note\":\"edited note\"}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed report"))
                .andExpect(jsonPath("$.fileName").value("report.docx"));

        // The metadata edit must not touch the artifact, the row identity or
        // the original submission timestamp.
        assertThat(column(groupId, "storage_key")).isEqualTo(storageKey);
        assertThat(column(groupId, "file_name")).isEqualTo("report.docx");
        assertThat(column(groupId, "submitted_at")).isEqualTo(submittedAt);
        assertThat(column(groupId, "submitted_by")).isEqualTo(submittedBy);

        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(studentJwt("re-leader")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));
    }

    @Test
    void clearUrlSucceedsWhileAFileRemainsAndIsRejectedForALinkOnlyReport() throws Exception {
        UUID groupId = seedApprovedGroup();
        uploadFile(groupId);
        submitLink(groupId);
        assertThat(column(groupId, "url")).isEqualTo(LINK);
        assertThat(column(groupId, "storage_key")).isNotNull();

        // Both artifacts present: clearing the link keeps the document.
        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearUrl\":true}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.fileName").value("report.docx"));
        assertThat(column(groupId, "url")).isNull();
        assertThat(column(groupId, "storage_key")).isNotNull();

        // A link-only report cannot drop its last artifact.
        UUID linkOnly = seedApprovedGroup();
        submitLink(linkOnly);
        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", linkOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearUrl\":true}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ARTIFACT_REQUIRED"));
        assertThat(column(linkOnly, "url")).isEqualTo(LINK);
    }

    @Test
    void patchReplacesTheLinkWithoutTouchingTheFile() throws Exception {
        UUID groupId = seedApprovedGroup();
        uploadFile(groupId);
        submitLink(groupId);
        String storageKey = stringColumn(groupId, "storage_key");

        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/v2.pdf\"}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://drive.example.com/v2.pdf"));
        assertThat(column(groupId, "storage_key")).isEqualTo(storageKey);

        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"javascript:alert(1)\"}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/v3.pdf\",\"clearUrl\":true}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void detachFileKeepsTheLinkAndBlocksWhenItWouldLeaveNoArtifact() throws Exception {
        UUID groupId = seedApprovedGroup();
        uploadFile(groupId);
        submitLink(groupId);
        assertThat(column(groupId, "storage_key")).isNotNull();

        mvc.perform(delete("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(LINK))
                .andExpect(jsonPath("$.fileName").value(org.hamcrest.Matchers.nullValue()));

        assertThat(column(groupId, "file_name")).isNull();
        assertThat(column(groupId, "file_data")).isNull();
        assertThat(column(groupId, "storage_key")).isNull();
        assertThat(column(groupId, "file_sha256")).isNull();
        assertThat(column(groupId, "url")).isEqualTo(LINK);

        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(studentJwt("re-leader")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_FILE_NOT_FOUND"));

        // A file-only report refuses to detach its last artifact.
        UUID fileOnly = seedApprovedGroup();
        uploadFile(fileOnly);
        mvc.perform(delete("/api/v1/thesis/groups/{id}/report/file", fileOnly)
                        .with(studentJwt("re-leader")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ARTIFACT_REQUIRED"));
        assertThat(column(fileOnly, "storage_key")).isNotNull();
    }

    @Test
    void absentFieldsKeepTheStoredValues() throws Exception {
        UUID groupId = seedApprovedGroup();
        submitLink(groupId, "Original title", "original note");

        // An empty body keeps every stored field and only bumps updated_at.
        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original title"))
                .andExpect(jsonPath("$.note").value("original note"))
                .andExpect(jsonPath("$.url").value(LINK));

        // A blank string clears the optional title/note.
        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"note\":\"\"}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.url").value(LINK));
    }

    @Test
    void nonLeaderCannotPatchOrDetachAndAMissingReportIs404() throws Exception {
        UUID groupId = seedApprovedGroup();
        uploadFile(groupId);

        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"peer edit\"}")
                        .with(studentJwt("re-peer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"));
        mvc.perform(delete("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .with(studentJwt("re-peer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"));

        UUID empty = seedApprovedGroup();
        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", empty)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"no row\"}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void submittedAtSurvivesEveryEditPath() throws Exception {
        UUID groupId = seedApprovedGroup();
        uploadFile(groupId);
        Object submittedAt = column(groupId, "submitted_at");
        String submittedBy = stringColumn(groupId, "submitted_by");

        submitLink(groupId);
        mvc.perform(patch("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"re-edited\"}")
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk());

        assertThat(column(groupId, "submitted_at")).isEqualTo(submittedAt);
        assertThat(column(groupId, "submitted_by")).isEqualTo(submittedBy);
    }

    // ---------- fixtures ----------

    private void uploadFile(UUID groupId) throws Exception {
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "report.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES))
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk());
    }

    private void submitLink(UUID groupId) throws Exception {
        submitLink(groupId, null, null);
    }

    private void submitLink(UUID groupId, String title, String note) throws Exception {
        StringBuilder body = new StringBuilder("{\"url\":\"").append(LINK).append('\"');
        if (title != null) {
            body.append(",\"title\":\"").append(title).append('\"');
        }
        if (note != null) {
            body.append(",\"note\":\"").append(note).append('\"');
        }
        body.append('}');
        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())
                        .with(studentJwt("re-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(LINK));
    }

    private Object column(UUID groupId, String name) {
        return jdbc.queryForObject(
                "SELECT " + name + " FROM thesis.thesis_group_report WHERE group_id = ?", Object.class, groupId);
    }

    private String stringColumn(UUID groupId, String name) {
        return jdbc.queryForObject(
                "SELECT " + name + " FROM thesis.thesis_group_report WHERE group_id = ?", String.class, groupId);
    }

    private UUID seedApprovedGroup() {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status, gvpb_deadline) "
                        + "VALUES (?, 'Report edit round', 'KLTN', ?, ?, ?, ?, 'REGISTRATION_CLOSED', ?)",
                roundId,
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.minusSeconds(3_600)),
                Timestamp.from(now.minusSeconds(3_600)), Timestamp.from(now.plusSeconds(3_600)),
                Timestamp.from(now.plusSeconds(86_400)));
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Report Edit Topic', 'fixture', 1, 'PUBLISHED', ?)",
                topicId, roundId, UUID.randomUUID());
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, 're-leader', ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, topicId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, 're-leader', 1, TRUE)",
                UUID.randomUUID(), groupId, roundId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, 're-peer', 2, FALSE)",
                UUID.randomUUID(), groupId, roundId);
        seedStudent("re-leader", "re-user-leader");
        seedStudent("re-peer", "re-user-peer");
        return groupId;
    }

    private void seedStudent(String studentId, String userId) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Student\" WHERE \"id\" = ?", Integer.class, studentId);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'RE', 'Member', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }
}
