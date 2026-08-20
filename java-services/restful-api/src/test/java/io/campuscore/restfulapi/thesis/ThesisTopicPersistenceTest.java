package io.campuscore.restfulapi.thesis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisTopicPersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ThesisTopicRepository topics;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_defense_council");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        topics.deleteAll();
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
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
        UUID olderGroup = insertGroup(roundId, Instant.parse("2026-01-02T00:00:00Z"), UUID.randomUUID(), "topic", "REJECTED", "REJECTED", "Needs revision");
        UUID newerGroup = insertGroup(roundId, Instant.parse("2026-01-03T00:00:00Z"), UUID.randomUUID(), null, "DRAFT", "PENDING", null);
        UUID firstMember = UUID.randomUUID();
        UUID secondMember = UUID.randomUUID();
        insertMember(newerGroup, roundId, secondMember, 2);
        insertMember(newerGroup, roundId, firstMember, 1);

        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", roundId.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newerGroup.toString()))
                .andExpect(jsonPath("$[0].topicId").doesNotExist())
                .andExpect(jsonPath("$[0].rejectionReason").doesNotExist())
                .andExpect(jsonPath("$[0].memberStudentIds[0]").value(firstMember.toString()))
                .andExpect(jsonPath("$[0].memberStudentIds[1]").value(secondMember.toString()))
                .andExpect(jsonPath("$[1].id").value(olderGroup.toString()));

        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", UUID.randomUUID().toString()).with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/thesis/groups/{id}", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void groupsAllowAnEmptyExistingRoundAndRejectMalformedOrAnonymousRequests() throws Exception {
        UUID roundId = insertRound();

        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", roundId.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", "not-a-uuid").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", roundId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void councilsPreservePostgresSortMemberOrderNullableFieldsAndReadBoundaries() throws Exception {
        UUID roundId = insertRound();
        UUID firstCouncil = insertCouncil(roundId, Instant.parse("2026-05-10T09:00:00Z"), "A-101", "SCHEDULED");
        UUID secondCouncil = insertCouncil(roundId, Instant.parse("2026-05-11T09:00:00Z"), "A-102", "SCORING_OPEN");
        UUID unscheduledCouncil = insertCouncil(roundId, null, null, "DRAFT");
        UUID firstLecturer = UUID.randomUUID();
        UUID secondLecturer = UUID.randomUUID();
        insertCouncilMember(firstCouncil, secondLecturer, "SECRETARY", 2);
        insertCouncilMember(firstCouncil, firstLecturer, "CHAIR", 1);

        mvc.perform(get("/api/v1/thesis/councils").queryParam("roundId", roundId.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstCouncil.toString()))
                .andExpect(jsonPath("$[0].members[0].lecturerId").value(firstLecturer.toString()))
                .andExpect(jsonPath("$[0].members[0].memberRole").value("CHAIR"))
                .andExpect(jsonPath("$[0].members[1].lecturerId").value(secondLecturer.toString()))
                .andExpect(jsonPath("$[1].id").value(secondCouncil.toString()))
                .andExpect(jsonPath("$[2].id").value(unscheduledCouncil.toString()))
                .andExpect(jsonPath("$[2].scheduledAt").doesNotExist())
                .andExpect(jsonPath("$[2].room").doesNotExist());

        mvc.perform(get("/api/v1/thesis/councils").queryParam("roundId", UUID.randomUUID().toString()).with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/thesis/councils").queryParam("roundId", "not-a-uuid").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/thesis/councils").queryParam("roundId", roundId.toString()))
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
                .andExpect(jsonPath("$.code").value("HTTP_404"));
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

    private UUID insertGroup(UUID roundId, Instant createdAt, UUID leaderId, String topicId, String status, String approvalStatus, String rejectionReason) {
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status, rejection_reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", groupId, roundId, leaderId, "topic".equals(topicId) ? UUID.randomUUID() : null, status, approvalStatus, rejectionReason, Timestamp.from(createdAt));
        return groupId;
    }

    private void insertMember(UUID groupId, UUID roundId, UUID studentId, int memberOrder) {
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), groupId, roundId, studentId, memberOrder, memberOrder == 1);
    }

    private UUID insertCouncil(UUID roundId, Instant scheduledAt, String room, String status) {
        UUID councilId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_defense_council "
                        + "(id, round_id, department_id, scheduled_at, room, status) VALUES (?, ?, ?, ?, ?, ?)",
                councilId,
                roundId,
                UUID.randomUUID(),
                scheduledAt == null ? null : Timestamp.from(scheduledAt),
                room,
                status);
        return councilId;
    }

    private void insertCouncilMember(UUID councilId, UUID lecturerId, String memberRole, int memberOrder) {
        jdbc.update(
                "INSERT INTO thesis.thesis_council_member "
                        + "(id, council_id, lecturer_id, member_role, member_order) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), councilId, lecturerId, memberRole, memberOrder);
    }
}
