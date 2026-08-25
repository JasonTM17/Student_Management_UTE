package io.campuscore.restfulapi.thesis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_thesis;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisTopicPersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ThesisTopicRepository topics;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        topics.deleteAll();
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'test-member-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'test-user-%'");
    }

    @Test
    void flywayCreatesTheLegacyCompatibleThesisSchema() {
        Integer topicTableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE LOWER(TABLE_SCHEMA) = 'thesis' AND LOWER(TABLE_NAME) = 'thesis_topic'",
                Integer.class);
        Integer migrationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.flyway_schema_history WHERE version = '1'",
                Integer.class);

        assertEquals(1, topicTableCount);
        assertEquals(1, migrationCount);
    }

    @Test
    void publishedTopicsUseTheSingleAppRestContract() throws Exception {
        UUID roundId = insertRound();
        ThesisTopic draft = new ThesisTopic(
                roundId,
                UUID.randomUUID(),
                "A draft topic",
                "Draft description",
                2,
                UUID.randomUUID());
        topics.save(draft);

        ThesisTopic published = new ThesisTopic(
                roundId,
                UUID.randomUUID(),
                "A published topic",
                "Published description",
                3,
                UUID.randomUUID());
        published.publish();
        topics.save(published);

        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", roundId.toString())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("A published topic"))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void registrationRoundsUseTheGatedSingleAppReadContract() throws Exception {
        UUID earlier = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        insertRound(earlier, "Earlier round", Instant.parse("2026-01-01T00:00:00Z"), "DRAFT");
        insertRound(later, "Open round", Instant.parse("2026-03-01T00:00:00Z"), "REGISTRATION_OPEN");

        mvc.perform(get("/api/v1/thesis/rounds").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(later.toString()))
                .andExpect(jsonPath("$[0].status").value("REGISTRATION_OPEN"));

        mvc.perform(get("/api/v1/thesis/rounds")
                        .queryParam("status", "DRAFT")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(earlier.toString()))
                .andExpect(jsonPath("$[0].name").value("Earlier round"));
    }

    @Test
    void groupsPreserveSortMemberOrderNullableFieldsAndNotFoundSemantics() throws Exception {
        UUID roundId = insertRound();
        UUID topicId = insertTopic(roundId);
        UUID olderGroup = insertGroup(roundId, Instant.parse("2026-01-02T00:00:00Z"), UUID.randomUUID(), topicId, "REJECTED", "REJECTED", "Needs revision");
        UUID newerGroup = insertGroup(roundId, Instant.parse("2026-01-03T00:00:00Z"), UUID.randomUUID(), null, "DRAFT", "PENDING", null);
        UUID firstMember = UUID.randomUUID();
        UUID secondMember = UUID.randomUUID();
        insertMember(newerGroup, roundId, secondMember, 2);
        insertMember(newerGroup, roundId, firstMember, 1);

        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", roundId.toString()).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newerGroup.toString()))
                .andExpect(jsonPath("$[0].topicId").doesNotExist())
                .andExpect(jsonPath("$[0].rejectionReason").doesNotExist())
                .andExpect(jsonPath("$[0].memberStudentIds[0]").value(firstMember.toString()))
                .andExpect(jsonPath("$[0].memberStudentIds[1]").value(secondMember.toString()))
                .andExpect(jsonPath("$[1].id").value(olderGroup.toString()));

        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", UUID.randomUUID().toString()).with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Thesis registration round not found"));
        mvc.perform(get("/api/v1/thesis/groups/{id}", UUID.randomUUID()).with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Thesis group not found"));
    }

    @Test
    void studentGroupReadsAreLimitedToMembershipWhileStaffCanReadTheRound() throws Exception {
        UUID roundId = insertRound();
        UUID studentId = UUID.randomUUID();
        UUID ownGroup = insertGroup(
                roundId,
                Instant.parse("2026-01-03T00:00:00Z"),
                studentId,
                null,
                "DRAFT",
                "PENDING",
                null);
        UUID otherGroup = insertGroup(
                roundId,
                Instant.parse("2026-01-02T00:00:00Z"),
                UUID.randomUUID(),
                null,
                "DRAFT",
                "PENDING",
                null);
        insertMember(ownGroup, roundId, studentId, 1);

        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(studentJwt(studentId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownGroup.toString()));

        mvc.perform(get("/api/v1/thesis/groups/{id}", otherGroup)
                        .with(studentJwt(studentId.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("THESIS_GROUP_NOT_FOUND"));

        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(lecturerJwt("lecturer-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void groupsAllowAnEmptyExistingRoundAndRejectMalformedOrAnonymousRequests() throws Exception {
        UUID roundId = insertRound();

        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", roundId.toString()).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", "not-a-uuid").with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request could not be parsed"));
        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", roundId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void thesisReadPathRejectsAnonymousRequests() throws Exception {
        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void thesisReadPathRejectsUnknownRoundsInsteadOfReturningAnEmptyList() throws Exception {
        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", UUID.randomUUID().toString())
                        .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Thesis registration round not found"));
    }

    @ParameterizedTest(name = "{0} can read an existing round")
    @ValueSource(strings = {"STUDENT", "LECTURER", "ADMIN"})
    void thesisReadPathPreservesAuthenticatedRoleBaseline(String role) throws Exception {
        UUID roundId = insertRound();

        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", roundId.toString())
                        .with(jwt().jwt(token -> token.claim("roles", List.of(role)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void groupMutationsEnforceDuplicateCapacityAndLeaderOwnership() throws Exception {
        UUID roundId = UUID.randomUUID();
        insertRound(roundId, "Open round", Instant.parse("2026-03-01T00:00:00Z"), "REGISTRATION_OPEN");
        ensureStudent("test-member-2", "test-user-2", "member2@campuscore.edu");
        ensureStudent("test-member-3", "test-user-3", "member3@campuscore.edu");
        ensureStudent("test-member-4", "test-user-4", "member4@campuscore.edu");

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("student-profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderStudentId").value("student-profile"))
                .andExpect(jsonPath("$.memberStudentIds[0]").value("student-profile"));

        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?",
                UUID.class,
                roundId);

        addMember(groupId, "test-member-2").andExpect(status().isOk());
        addMember(groupId, "test-member-3").andExpect(status().isOk());
        addMember(groupId, "test-member-4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_FULL"));

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("student-profile")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_IN_GROUP"));

        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"test-member-4\"}")
                        .with(studentJwt("test-member-2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"));
    }

    @Test
    void roundLifecycleRejectsInvalidTransitionsWithStableConflictCode() throws Exception {
        String body = "{\"name\":\"2027 Capstone\",\"thesisType\":\"CAPSTONE\"," +
                "\"registrationStart\":\"2027-01-01T00:00:00Z\"," +
                "\"registrationEnd\":\"2027-02-01T00:00:00Z\"}";
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        UUID roundId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE name = '2027 Capstone'",
                UUID.class);
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
    }

    @Test
    void topicMutationsEnforceLecturerOwnershipAndDraftState() throws Exception {
        UUID roundId = insertRound();
        String createBody = "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\"," +
                "\"title\":\"Deterministic RAG\",\"description\":\"A bounded thesis topic\",\"maxGroups\":2}";
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .with(lecturerJwt("lecturer-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value("lecturer-user"));

        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Deterministic RAG'",
                UUID.class);
        String updateBody = "{\"departmentId\":\"department-demo\",\"title\":\"Updated title\"," +
                "\"description\":\"Updated description\",\"maxGroups\":2}";
        mvc.perform(put("/api/v1/thesis/topics/{id}", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .with(lecturerJwt("other-lecturer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOPIC_OWNER_REQUIRED"));

        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId)
                        .with(lecturerJwt("lecturer-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(put("/api/v1/thesis/topics/{id}", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .with(lecturerJwt("lecturer-user")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_STATE_CONFLICT"));
    }

    private UUID insertRound() {
        UUID roundId = UUID.randomUUID();
        insertRound(roundId, "2026 Capstone", Instant.parse("2026-01-01T00:00:00Z"), "DRAFT");
        return roundId;
    }

    private void insertRound(UUID roundId, String name, Instant start, String status) {
        Instant end = start.plusSeconds(31L * 24 * 60 * 60);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, registration_start, registration_end, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                roundId,
                name,
                "CAPSTONE",
                Timestamp.from(start),
                Timestamp.from(end),
                status);
    }

    private UUID insertTopic(UUID roundId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                topicId,
                roundId,
                UUID.randomUUID(),
                "Topic",
                "Topic description",
                1,
                "DRAFT",
                UUID.randomUUID());
        return topicId;
    }

    private UUID insertGroup(UUID roundId, Instant createdAt, UUID leaderId, UUID topicId, String status, String approvalStatus, String rejectionReason) {
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status, rejection_reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", groupId, roundId, leaderId, topicId, status, approvalStatus, rejectionReason, Timestamp.from(createdAt));
        return groupId;
    }

    private void insertMember(UUID groupId, UUID roundId, UUID studentId, int memberOrder) {
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), groupId, roundId, studentId, memberOrder, memberOrder == 1);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor studentJwt(String studentId) {
        return jwt().jwt(token -> token
                .subject("user-" + studentId)
                .claim("roles", List.of("STUDENT"))
                .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.subject("admin-user").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor lecturerJwt(String userId) {
        return jwt().jwt(token -> token.subject(userId).claim("roles", List.of("LECTURER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private org.springframework.test.web.servlet.ResultActions addMember(UUID groupId, String studentId) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\"}")
                .with(studentJwt("student-profile")));
    }

    private void ensureStudent(String studentId, String userId, String email) {
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") VALUES (?, ?, 'test-password', 'Test', 'Member', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId,
                email);
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId,
                userId,
                "CODE-" + studentId);
    }

}
