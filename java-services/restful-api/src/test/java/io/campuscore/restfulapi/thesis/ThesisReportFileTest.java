package io.campuscore.restfulapi.thesis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import io.campuscore.restfulapi.thesis.service.ThesisReportStorage;

/**
 * Feedback item 7: the leader can attach the report as a Word/PDF document.
 * Content validation is signature-based, the 20 MB cap holds, only the leader
 * writes, and the download matrix matches the report metadata read matrix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_report_file;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "thesis.report.storage.local-root=target/test-report-storage"
})
class ThesisReportFileTest {

    private static final byte[] DOCX_BYTES = {'P', 'K', 0x03, 0x04, 'f', 'a', 'k', 'e', '-', 'd', 'o', 'c', 'x'};
    private static final byte[] EXE_BYTES = {'M', 'Z', (byte) 0x90, 0x00, 'p', 'e', 'a', 'y', 'l', 'o', 'a', 'd'};

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ThesisReportStorage storage;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rf-member-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rf-user-%'");
    }

    @Test
    void leaderUploadsDocxAndAuthorizedMembersDownloadIdenticalBytes() throws Exception {
        UUID groupId = seedApprovedGroup();
        MockMultipartFile file = new MockMultipartFile(
                "file", "Báo cáo final.docx", MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES);

        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(file)
                        .param("title", "Final report")
                        .param("note", "attached document")
                        .with(studentJwt("rf-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Báo cáo final.docx"))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.fileSize").value(DOCX_BYTES.length));

        String storageProvider = jdbc.queryForObject(
                "SELECT storage_provider FROM thesis.thesis_group_report WHERE group_id = ?", String.class, groupId);
        String storageKey = jdbc.queryForObject(
                "SELECT storage_key FROM thesis.thesis_group_report WHERE group_id = ?", String.class, groupId);
        byte[] databaseBytes = jdbc.queryForObject(
                "SELECT file_data FROM thesis.thesis_group_report WHERE group_id = ?", byte[].class, groupId);
        org.junit.jupiter.api.Assertions.assertEquals("local", storageProvider);
        org.junit.jupiter.api.Assertions.assertNotNull(storageKey);
        org.junit.jupiter.api.Assertions.assertNull(databaseBytes);
        org.junit.jupiter.api.Assertions.assertArrayEquals(DOCX_BYTES, storage.read(storageKey));

        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(studentJwt("rf-peer")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"));

        // Resubmission replaces the single current version.
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "v2.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES))
                        .with(studentJwt("rf-leader")))
                .andExpect(status().isOk());
        Integer versions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = ?", Integer.class, groupId);
        org.junit.jupiter.api.Assertions.assertEquals(1, versions);
    }

    @Test
    void executableRenamedToPdfIsRejectedBySignature() throws Exception {
        UUID groupId = seedApprovedGroup();
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "report.pdf",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, EXE_BYTES))
                        .with(studentJwt("rf-leader")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_CONTENT"));

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = ?", Integer.class, groupId);
        org.junit.jupiter.api.Assertions.assertEquals(0, rows);
    }

    @Test
    void disallowedExtensionIsRejected() throws Exception {
        UUID groupId = seedApprovedGroup();
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "sheet.xlsx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES))
                        .with(studentJwt("rf-leader")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void oversizeDocumentIsRejected() throws Exception {
        UUID groupId = seedApprovedGroup();
        byte[] blob = new byte[20 * 1024 * 1024 + 1];
        blob[0] = (byte) 'P';
        blob[1] = (byte) 'K';
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "big.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, blob))
                        .with(studentJwt("rf-leader")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void nonLeaderMemberCannotSubmitButMemberCanRead() throws Exception {
        UUID groupId = seedApprovedGroup();
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "v1.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES))
                        .with(studentJwt("rf-peer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"));
    }

    @Test
    void unrelatedStudentCannotReadTheReport() throws Exception {
        UUID groupId = seedApprovedGroup();
        seedStudent("rf-outsider", "rf-user-outsider");
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "v1.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES))
                        .with(studentJwt("rf-leader")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(studentJwt("rf-outsider")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_REQUIRED"));
    }

    @Test
    void legacyLinkReportStaysReadableAndFileDownloadReportsMissing() throws Exception {
        UUID groupId = seedApprovedGroup();
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, (SELECT round_id FROM thesis.thesis_group WHERE id = ?), 'rf-leader', "
                        + "'Link report', 'https://drive.example.com/report.pdf')",
                UUID.randomUUID(), groupId, groupId);
        mvc.perform(get("/api/v1/thesis/groups/{id}/report", groupId).with(studentJwt("rf-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://drive.example.com/report.pdf"));
        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(studentJwt("rf-leader")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_FILE_NOT_FOUND"));
    }

    @Test
    void lecturerAndAdminCanAccessRepositoryAndDownloadAnyReport() throws Exception {
        UUID groupId = seedApprovedGroup();
        UUID roundId = jdbc.queryForObject(
                "SELECT round_id FROM thesis.thesis_group WHERE id = ?", UUID.class, groupId);
        MockMultipartFile file = new MockMultipartFile(
                "file", "Thesis-Archive.docx", MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES);

        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(file)
                        .param("title", "Archive Thesis Report")
                        .param("note", "repository archival check")
                        .with(studentJwt("rf-leader")))
                .andExpect(status().isOk());

        // Any lecturer can list reports in the round
        mvc.perform(get("/api/v1/thesis/rounds/{id}/reports", roundId).with(lecturerJwt("lec-other")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(groupId.toString()))
                .andExpect(jsonPath("$[0].fileName").value("Thesis-Archive.docx"));

        // Admin can list reports in the round
        mvc.perform(get("/api/v1/thesis/rounds/{id}/reports", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(groupId.toString()));

        // Lecturer can download the report file
        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(lecturerJwt("lec-other")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));

        // Admin can download the report file
        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));
    }

    @Test
    void studentCannotAccessRoundReportsList() throws Exception {
        UUID groupId = seedApprovedGroup();
        UUID roundId = jdbc.queryForObject(
                "SELECT round_id FROM thesis.thesis_group WHERE id = ?", UUID.class, groupId);

        mvc.perform(get("/api/v1/thesis/rounds/{id}/reports", roundId).with(studentJwt("rf-leader")))
                .andExpect(status().isForbidden());
    }

    private UUID seedApprovedGroup() {
        UUID roundId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status, gvpb_deadline) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, 'REGISTRATION_CLOSED', ?)",
                roundId, "Report File Round",
                Timestamp.from(Instant.now().minusSeconds(3_600)), Timestamp.from(Instant.now().plusSeconds(3_600)),
                Timestamp.from(Instant.now().minusSeconds(3_600)), Timestamp.from(Instant.now().plusSeconds(3_600)),
                Timestamp.from(Instant.now().plusSeconds(86_400)));
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, 'Report File Topic', 'fixture', 1, 'PUBLISHED', ?)",
                topicId, roundId, UUID.randomUUID(), UUID.randomUUID());
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, "rf-leader", topicId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, 'rf-leader', 1, TRUE)",
                UUID.randomUUID(), groupId, roundId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, 'rf-peer', 2, FALSE)",
                UUID.randomUUID(), groupId, roundId);
        seedStudent("rf-leader", "rf-user-leader");
        seedStudent("rf-peer", "rf-user-peer");
        return groupId;
    }

    private void seedStudent(String studentId, String userId) {
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'RF', 'Member', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return jwt().jwt(token -> token
                        .subject("rf-user-" + studentId.replace("rf-", ""))
                        .claim("roles", List.of("STUDENT"))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private RequestPostProcessor lecturerJwt(String lecturerId) {
        return jwt().jwt(token -> token
                        .subject("rf-user-lecturer-" + lecturerId)
                        .claim("roles", List.of("LECTURER"))
                        .claim("lecturerId", lecturerId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("rf-user-admin")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
