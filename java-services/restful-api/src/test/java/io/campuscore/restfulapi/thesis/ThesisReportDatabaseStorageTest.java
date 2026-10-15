package io.campuscore.restfulapi.thesis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Regression for the production file-loss defect: the default 'local' storage
 * provider writes report bytes to the container filesystem, which an ephemeral
 * host wipes on every redeploy — the metadata row survives while the document
 * is gone forever (REPORT_STORAGE_UNAVAILABLE on download). The 'database'
 * provider keeps the bytes in the row itself so the document survives any
 * redeploy with zero external storage dependency.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_report_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "thesis.report.storage.provider=database"
})
class ThesisReportDatabaseStorageTest {

    private static final byte[] DOCX_BYTES = {'P', 'K', 0x03, 0x04, 'd', 'b', '-', 'd', 'o', 'c', 'x'};
    private static final byte[] V2_BYTES = {'P', 'K', 0x03, 0x04, 'v', '2', '-', 'b', 'y', 't', 'e', 's'};

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
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'db-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'db-user-%'");
    }

    @Test
    void uploadedBytesLiveInTheRowAndSurviveWithoutObjectStorage() throws Exception {
        UUID groupId = seedApprovedGroup();

        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "Bao-cao.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES))
                        .with(studentJwt("db-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Bao-cao.docx"));

        String storageProvider = jdbc.queryForObject(
                "SELECT storage_provider FROM thesis.thesis_group_report WHERE group_id = ?", String.class, groupId);
        String storageKey = jdbc.queryForObject(
                "SELECT storage_key FROM thesis.thesis_group_report WHERE group_id = ?", String.class, groupId);
        byte[] databaseBytes = jdbc.queryForObject(
                "SELECT file_data FROM thesis.thesis_group_report WHERE group_id = ?", byte[].class, groupId);
        org.junit.jupiter.api.Assertions.assertEquals("database", storageProvider);
        org.junit.jupiter.api.Assertions.assertNull(storageKey);
        org.junit.jupiter.api.Assertions.assertArrayEquals(DOCX_BYTES, databaseBytes);

        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(studentJwt("db-peer")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DOCX_BYTES));
    }

    @Test
    void resubmissionReplacesTheStoredBytes() throws Exception {
        UUID groupId = seedApprovedGroup();

        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "v1.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, DOCX_BYTES))
                        .with(studentJwt("db-leader")))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/thesis/groups/{id}/report/file", groupId)
                        .file(new MockMultipartFile("file", "v2.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, V2_BYTES))
                        .with(studentJwt("db-leader")))
                .andExpect(status().isOk());

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = ?", Integer.class, groupId);
        org.junit.jupiter.api.Assertions.assertEquals(1, rows);
        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", groupId).with(studentJwt("db-leader")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(V2_BYTES));
    }

    @Test
    void studentFindsTheirGroupAcrossRoundsThroughMine() throws Exception {
        UUID firstRound = UUID.randomUUID();
        UUID secondRound = UUID.randomUUID();
        seedRound(firstRound, "REGISTRATION_CLOSED", Instant.now().minusSeconds(86_400));
        seedRound(secondRound, "PROPOSAL_OPEN", Instant.now().plusSeconds(86_400));
        UUID groupId = seedApprovedGroupIn(firstRound, "db-leader");

        // The student lands on their own group round even when it is not the
        // newest or currently open one — the defect that hid the group card.
        mvc.perform(get("/api/v1/thesis/groups/mine").with(studentJwt("db-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId.toString()))
                .andExpect(jsonPath("$[0].roundId").value(firstRound.toString()));

        mvc.perform(get("/api/v1/thesis/groups/mine").with(studentJwt("db-peer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId.toString()));
    }

    @Test
    void mineIsEmptyForUnrelatedStudentAndForbiddenWithoutStudentRole() throws Exception {
        seedApprovedGroup();
        seedStudent("db-outsider", "db-user-outsider");

        mvc.perform(get("/api/v1/thesis/groups/mine").with(studentJwt("db-outsider")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // A bare member-id JWT without the STUDENT role must not enumerate.
        mvc.perform(get("/api/v1/thesis/groups/mine")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("ghost-user"))
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("ROLE_GUEST"))))
                .andExpect(status().isUnauthorized());
    }

    private UUID seedApprovedGroup() {
        UUID roundId = UUID.randomUUID();
        seedRound(roundId, "REGISTRATION_CLOSED", Instant.now().minusSeconds(86_400));
        return seedApprovedGroupIn(roundId, "db-leader");
    }

    private void seedRound(UUID roundId, String status, Instant registrationStart) {
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status, gvpb_deadline) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?)",
                roundId, "Round " + status,
                Timestamp.from(registrationStart.minusSeconds(7_200)),
                Timestamp.from(registrationStart.minusSeconds(3_600)),
                Timestamp.from(registrationStart), Timestamp.from(registrationStart.plusSeconds(3_600)),
                status, Timestamp.from(Instant.now().plusSeconds(172_800)));
    }

    private UUID seedApprovedGroupIn(UUID roundId, String leaderId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'DB Storage Topic', 'fixture', 1, 'PUBLISHED', ?)",
                topicId, roundId, UUID.randomUUID());
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, leaderId, topicId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, leaderId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, 'db-peer', 2, FALSE)",
                UUID.randomUUID(), groupId, roundId);
        seedStudent(leaderId, "db-user-leader");
        seedStudent("db-peer", "db-user-peer");
        return groupId;
    }

    private void seedStudent(String studentId, String userId) {
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'DB', 'Member', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
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
