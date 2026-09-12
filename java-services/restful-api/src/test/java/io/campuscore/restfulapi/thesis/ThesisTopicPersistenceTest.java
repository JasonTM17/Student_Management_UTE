package io.campuscore.restfulapi.thesis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
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
        Integer supervisorTableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE LOWER(TABLE_SCHEMA) = 'thesis' AND LOWER(TABLE_NAME) = 'thesis_topic_supervisor'",
                Integer.class);
        Integer migrationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.flyway_schema_history WHERE version = '1'",
                Integer.class);

        assertEquals(1, topicTableCount);
        assertEquals(1, supervisorTableCount);
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

        addMember(groupId, "test-member-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[1].studentId").value("test-member-2"))
                .andExpect(jsonPath("$.members[1].studentNumber").value("CODE-test-member-2"))
                .andExpect(jsonPath("$.members[1].displayName").value("Test Member"))
                .andExpect(jsonPath("$.members[1].contact").value("member2@campuscore.edu"))
                .andExpect(jsonPath("$.members[1].isExternal").value(false));
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
    void approvedGroupsCannotBeDemotedByTheirLeader() throws Exception {
        UUID roundId = UUID.randomUUID();
        insertRound(roundId, "Open round", Instant.parse("2026-03-01T00:00:00Z"), "REGISTRATION_OPEN");
        UUID studentId = UUID.randomUUID();
        UUID approvedGroup = insertGroup(
                roundId,
                Instant.parse("2026-01-03T00:00:00Z"),
                studentId,
                null,
                "SUBMITTED",
                "APPROVED",
                null);

        // approveGroup flips approval_status while status stays SUBMITTED; the leader
        // must not demote or cancel the group behind the reviewer's back.
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", approvedGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRAFT\"}")
                        .with(studentJwt(studentId.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATUS_INVALID"));

        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", approvedGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}")
                        .with(studentJwt(studentId.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATUS_INVALID"));

        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", approvedGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}")
                        .with(studentJwt(studentId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void roundLifecycleFollowsTheTwoPhaseBriefOrder() throws Exception {
        String body = "{\"name\":\"2027 Graduation Thesis\",\"thesisType\":\"KLTN\"," +
                "\"lecturerSubmitStart\":\"2026-12-01T00:00:00Z\"," +
                "\"lecturerSubmitEnd\":\"2026-12-31T00:00:00Z\"," +
                "\"registrationStart\":\"2027-01-01T00:00:00Z\"," +
                "\"registrationEnd\":\"2027-02-01T00:00:00Z\"," +
                "\"gvpbDeadline\":\"2027-03-01T00:00:00Z\"," +
                "\"reportDate\":\"2027-03-10T00:00:00Z\"}";
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.thesisType").value("KLTN"))
                .andExpect(jsonPath("$.gvpbDeadline").value("2027-03-01T00:00:00Z"));

        UUID roundId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE name = '2027 Graduation Thesis'",
                UUID.class);

        // The two-phase brief order: proposals first, published catalog second,
        // student registration third. Jumping straight to registration is refused.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-proposals", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSAL_OPEN"));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-proposals", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSALS_PUBLISHED"));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/close-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_CLOSED"));
    }

    @Test
    void topicMutationsEnforceLecturerOwnershipAndDraftState() throws Exception {
        UUID roundId = insertProposalRound("Ownership Round");
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

    @Test
    void lecturerTopicCreationRegistersSupervisorAndAllowsGroupReview() throws Exception {
        UUID roundId = insertProposalRound("Open Round 2026");
        ensureStudent("student-review-1", "user-student-1", "student1@campuscore.edu");

        // 1. Lecturer creates topic
        String createBody = "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\"," +
                "\"title\":\"Supervisor Flow Topic\",\"description\":\"Testing supervisor workflow\",\"maxGroups\":2}";
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .with(lecturerJwt("lecturer-supervisor-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Supervisor Flow Topic'",
                UUID.class);

        // Verify supervisor was automatically inserted
        Integer supervisorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = ? AND lecturer_id = ? AND supervisor_order = 1",
                Integer.class,
                topicId,
                "lecturer-supervisor-1");
        assertEquals(1, supervisorCount);

        // Publish topic
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId)
                        .with(lecturerJwt("lecturer-supervisor-1")))
                .andExpect(status().isOk());

        // Open the student phase before groups may register (brief phase two).
        driveRoundFromProposalToRegistration(roundId);

        // 2. Student creates group and assigns topic
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("student-review-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"));

        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ? AND leader_student_id = ?",
                UUID.class,
                roundId,
                "student-review-1");

        // A one-leader group is not a group: add a member before approval.
        ensureStudent("student-review-1b", "user-student-1b", "student1b@campuscore.edu");
        addMember(groupId, "student-review-1b").andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("student-review-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"));

        // 3. Unauthorized reviewer is rejected (different lecturer)
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .with(lecturerJwt("different-lecturer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_REVIEWER_REQUIRED"));

        // 4. Assigned supervisor / creator approves group
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .with(lecturerJwt("lecturer-supervisor-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        // Approving again triggers conflict
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .with(lecturerJwt("lecturer-supervisor-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_APPROVAL_STATE_CONFLICT"));
    }

    @Test
    void lecturerRejectionFlowAndTopicReassignment() throws Exception {
        UUID roundId = insertProposalRound("Open Round 2026-B");
        ensureStudent("student-review-2", "user-student-2", "student2@campuscore.edu");

        // 1. Lecturer creates topic
        String createBody = "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\"," +
                "\"title\":\"Topic for Rejection Test\",\"description\":\"Testing rejection\",\"maxGroups\":2}";
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .with(lecturerJwt("lecturer-reviewer-2")))
                .andExpect(status().isOk());

        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Topic for Rejection Test'",
                UUID.class);

        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId)
                        .with(lecturerJwt("lecturer-reviewer-2")))
                .andExpect(status().isOk());

        driveRoundFromProposalToRegistration(roundId);

        // 2. Student creates group & assigns topic
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("student-review-2")))
                .andExpect(status().isOk());

        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ? AND leader_student_id = ?",
                UUID.class,
                roundId,
                "student-review-2");

        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("student-review-2")))
                .andExpect(status().isOk());

        // 3. Lecturer rejects with blank reason -> 400 VALIDATION_ERROR
        ensureStudent("student-review-2b", "user-student-2b", "student2b@campuscore.edu");
        addMember(groupId, "student-review-2b").andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/reject", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}")
                        .with(lecturerJwt("lecturer-reviewer-2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 4. Lecturer rejects with valid reason
        mvc.perform(post("/api/v1/thesis/groups/{id}/reject", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Proposal needs more detailed architecture.\"}")
                        .with(lecturerJwt("lecturer-reviewer-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Proposal needs more detailed architecture."));

        // 5. Student reassigns topic -> resets approvalStatus to PENDING
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("student-review-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        // 6. Supervisor can now approve
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .with(lecturerJwt("lecturer-reviewer-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    @Test
    void approvedGroupCannotChangeTopicAndRejectedSlotIsFreed() throws Exception {
        UUID roundId = insertProposalRound("Open Round 2026-C");
        ensureStudent("student-review-3", "user-student-3", "student3@campuscore.edu");
        ensureStudent("student-review-4", "user-student-4", "student4@campuscore.edu");

        // 1. Lecturer creates topic with maxGroups = 1
        String createBody = "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\"," +
                "\"title\":\"Topic Max 1 Slot\",\"description\":\"Testing slot release\",\"maxGroups\":1}";
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .with(lecturerJwt("lecturer-reviewer-3")))
                .andExpect(status().isOk());

        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Topic Max 1 Slot'",
                UUID.class);
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId)
                        .with(lecturerJwt("lecturer-reviewer-3")))
                .andExpect(status().isOk());

        driveRoundFromProposalToRegistration(roundId);

        // 2. Student 3 creates group and assigns topic (takes the only slot)
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("student-review-3")))
                .andExpect(status().isOk());
        UUID group3Id = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ? AND leader_student_id = ?",
                UUID.class, roundId, "student-review-3");

        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", group3Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("student-review-3")))
                .andExpect(status().isOk());

        // 3. Student 4 creates group and attempts to assign the same full topic -> 409 TOPIC_FULL
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("student-review-4")))
                .andExpect(status().isOk());
        UUID group4Id = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ? AND leader_student_id = ?",
                UUID.class, roundId, "student-review-4");

        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", group4Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("student-review-4")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_FULL"));

        // 4. Lecturer rejects Group 3 -> releases slot
        mvc.perform(post("/api/v1/thesis/groups/{id}/reject", group3Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Incomplete prerequisites.\"}")
                        .with(lecturerJwt("lecturer-reviewer-3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("REJECTED"));

        // 5. Student 4 can now successfully claim the freed slot
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", group4Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("student-review-4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"));

        // 6. Lecturer approves Group 4
        ensureStudent("student-review-4b", "user-student-4b", "student4b@campuscore.edu");
        addMember(group4Id, "student-review-4b").andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", group4Id)
                        .with(lecturerJwt("lecturer-reviewer-3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        // 7. Student 4 attempts to change topic while approved -> 409 GROUP_STATE_CONFLICT
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", group4Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + UUID.randomUUID() + "\"}")
                        .with(studentJwt("student-review-4")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));
    }

    @Test
    void coSupervisorCanReviewAndApproveGroup() throws Exception {
        UUID roundId = insertProposalRound("Open Round 2026-D");
        ensureStudent("student-review-5", "user-student-5", "student5@campuscore.edu");

        // 1. Primary supervisor creates topic
        String createBody = "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\"," +
                "\"title\":\"Topic Co-Supervised\",\"description\":\"Testing co-supervisors\",\"maxGroups\":2}";
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .with(lecturerJwt("lecturer-lead")))
                .andExpect(status().isOk());

        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Topic Co-Supervised'",
                UUID.class);
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId)
                        .with(lecturerJwt("lecturer-lead")))
                .andExpect(status().isOk());

        driveRoundFromProposalToRegistration(roundId);

        // 2. Add co-supervisor (order = 2)
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) VALUES (?, ?, ?, 2)",
                UUID.randomUUID(), topicId, "lecturer-co");

        // 3. Student creates group & assigns topic
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("student-review-5")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ? AND leader_student_id = ?",
                UUID.class, roundId, "student-review-5");

        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("student-review-5")))
                .andExpect(status().isOk());

        // 4. Unauthorized lecturer cannot review
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .with(lecturerJwt("lecturer-random-unauthorized")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_REVIEWER_REQUIRED"));

        // 5. Co-supervisor can review and approve group
        ensureStudent("student-review-5b", "user-student-5b", "student5b@campuscore.edu");
        addMember(groupId, "student-review-5b").andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .with(lecturerJwt("lecturer-co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    @Test
    void adminTopicCreationDoesNotRegisterAdminAsSupervisor() throws Exception {
        UUID roundId = insertProposalRound("Admin Round 2026");
        ensureStudent("student-review-6", "user-student-6", "student6@campuscore.edu");

        // Admin creates topic
        String createBody = "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\"," +
                "\"title\":\"Admin Governed Topic\",\"description\":\"Created by administrator\",\"maxGroups\":2}";
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .with(adminJwt()))
                .andExpect(status().isOk());

        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Admin Governed Topic'",
                UUID.class);

        // Verify admin was NOT inserted into thesis_topic_supervisor
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = ? AND lecturer_id = 'admin-user'",
                Integer.class, topicId);
        assertEquals(0, count);

        // Admin publishes topic
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId)
                        .with(adminJwt()))
                .andExpect(status().isOk());

        driveRoundFromProposalToRegistration(roundId);

        // Student creates group & assigns topic
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("student-review-6")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ? AND leader_student_id = ?",
                UUID.class, roundId, "student-review-6");

        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("student-review-6")))
                .andExpect(status().isOk());

        // Admin can still approve group
        ensureStudent("student-review-6b", "user-student-6b", "student6b@campuscore.edu");
        addMember(groupId, "student-review-6b").andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    /**
     * Regression guard for the PostgreSQL failure
     * {@code PSQLException: Can't infer the SQL type to use for an instance of java.time.Instant}
     * that made {@code POST /api/v1/thesis/rounds} return HTTP 500 for every payload — found by
     * the Wukong adversarial pass on 2026-09-12. The service must bind {@link java.sql.Timestamp}
     * (see {@code ThesisMutationService.tsOf}) rather than a raw {@link Instant}.
     *
     * <p>Caveat: this suite runs on H2 in PostgreSQL mode, whose driver is more permissive than
     * the real PostgreSQL driver, so this test is a contract guard, not sufficient proof. The
     * authoritative evidence is the live PostgreSQL probe recorded in
     * {@code plans/20260911-active-students-bigdata-and-thesis-lifecycle/reports/acceptance-walkthrough.md}.
     */
    @Test
    void roundCreationThroughTheApiPersistsTheRoundAndItsWindow() throws Exception {
        mvc.perform(post("/api/v1/thesis/rounds")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Regression Round",
                                  "thesisType": "TLCN",
                                  "registrationStart": "2026-09-01T00:00:00Z",
                                  "registrationEnd": "2026-12-31T00:00:00Z",
                                  "gvpbDeadline": "2027-03-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Regression Round"))
                .andExpect(jsonPath("$.thesisType").value("TLCN"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        Integer persisted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_registration_round WHERE name = 'Regression Round'",
                Integer.class);
        assertEquals(1, persisted);
    }

    private UUID insertRound() {
        UUID roundId = UUID.randomUUID();
        insertRound(roundId, "2026 Capstone", Instant.parse("2026-01-01T00:00:00Z"), "DRAFT");
        return roundId;
    }

    /** A round in its lecturer proposal phase with a live submission window. */
    private UUID insertProposalRound(String name) {
        UUID roundId = UUID.randomUUID();
        insertRound(roundId, name, Instant.now(), "PROPOSAL_OPEN");
        return roundId;
    }

    /** Walks DRAFT -> PROPOSAL_OPEN -> PROPOSALS_PUBLISHED -> REGISTRATION_OPEN. */
    private void driveRoundToRegistration(UUID roundId) throws Exception {
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-proposals", roundId).with(adminJwt()))
                .andExpect(status().isOk());
        driveRoundFromProposalToRegistration(roundId);
    }

    /** Walks PROPOSAL_OPEN -> PROPOSALS_PUBLISHED -> REGISTRATION_OPEN. */
    private void driveRoundFromProposalToRegistration(UUID roundId) throws Exception {
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-proposals", roundId).with(adminJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk());
    }

    private void insertRound(UUID roundId, String name, Instant start, String status) {
        // Registration mutations now enforce the stored window, so open rounds
        // are seeded with a valid relative window instead of a past fixture date.
        Instant effectiveStart = "REGISTRATION_OPEN".equals(status) && !start.isAfter(Instant.now())
                ? Instant.now().minusSeconds(3_600)
                : start;
        Instant end = effectiveStart.plusSeconds(31L * 24 * 60 * 60);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                roundId,
                name,
                "KLTN",
                Timestamp.from(effectiveStart),
                Timestamp.from(end),
                Timestamp.from(effectiveStart),
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

    @Test
    void registrationWindowAndApprovedMembershipGuardsHold() throws Exception {
        // Open status but an expired window: registration mutations must fail closed.
        UUID expiredRound = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round (id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, registration_start, registration_end, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                expiredRound,
                "Expired window",
                "KLTN",
                Timestamp.from(Instant.now().minusSeconds(2 * 86_400)),
                Timestamp.from(Instant.now().minusSeconds(86_400)),
                Timestamp.from(Instant.now().minusSeconds(2 * 86_400)),
                Timestamp.from(Instant.now().minusSeconds(86_400)),
                "REGISTRATION_OPEN");

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + expiredRound + "\"}")
                        .with(studentJwt(UUID.randomUUID().toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_CLOSED"));

        // An approved group's membership is frozen for its leader.
        UUID openRound = UUID.randomUUID();
        insertRound(openRound, "Open round", Instant.now(), "REGISTRATION_OPEN");
        UUID leader = UUID.randomUUID();
        UUID approvedGroup = insertGroup(
                openRound,
                Instant.now(),
                leader,
                null,
                "SUBMITTED",
                "APPROVED",
                null);
        insertMember(approvedGroup, openRound, leader, 1);

        mvc.perform(post("/api/v1/thesis/groups/{id}/members", approvedGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"" + UUID.randomUUID() + "\"}")
                        .with(studentJwt(leader.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));

        // Admins keep a coordination escape hatch, but cannot write approval
        // semantics through progress updates.
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", approvedGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
                .with(studentJwt(groupLeader(groupId))));
    }

    private String groupLeader(UUID groupId) {
        return jdbc.queryForObject(
                "SELECT leader_student_id FROM thesis.thesis_group WHERE id = ?", String.class, groupId);
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
