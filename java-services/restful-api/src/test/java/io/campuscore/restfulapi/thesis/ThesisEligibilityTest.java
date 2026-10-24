package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Covers the accumulated-credit/CPA registration condition
 * (thesis.eligibility.*): boundary credits, boundary GPA, missing academic
 * data, enforcement at group create/member add/topic pick, the audited
 * governance roster override, and the read-model eligibility fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_eligibility;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        // application-test.yml defaults to ADVISORY for legacy fixtures; this
        // suite covers the blocking behaviour so it pins ENFORCE.
        "thesis.eligibility.enforcement=ENFORCE"
})
class ThesisEligibilityTest {

    private static final String LEADER = "eli-leader";
    private static final String ELIGIBLE = "eli-eligible";
    private static final String INELIGIBLE = "eli-ineligible";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        // The H2 twin carries no academic catalog tables; bootstrap the ones
        // the transcript query joins (same shapes as the enrollment-read
        // persistence fixture).
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "studentId" VARCHAR(120) NOT NULL,
                    "curriculumId" VARCHAR(120) NOT NULL,
                    "year" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "admissionDate" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."AcademicYear" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "year" INTEGER NOT NULL,
                    "startDate" TIMESTAMP NOT NULL,
                    "endDate" TIMESTAMP NOT NULL,
                    "isCurrent" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Semester" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(120) NOT NULL,
                    "nameEn" VARCHAR(120),
                    "nameVi" VARCHAR(120),
                    "type" VARCHAR(40) NOT NULL,
                    "academicYearId" VARCHAR(120) NOT NULL,
                    "startDate" TIMESTAMP NOT NULL,
                    "endDate" TIMESTAMP NOT NULL,
                    "registrationStart" TIMESTAMP,
                    "registrationEnd" TIMESTAMP,
                    "addDropStart" TIMESTAMP,
                    "addDropEnd" TIMESTAMP,
                    "status" VARCHAR(40) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(40) NOT NULL,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "description" VARCHAR(1000),
                    "descriptionEn" VARCHAR(1000),
                    "descriptionVi" VARCHAR(1000),
                    "credits" INTEGER NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120),
                    "isActive" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120) NOT NULL,
                    "isActive" BOOLEAN NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."GradeItem" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "name" VARCHAR(120) NOT NULL,
                    "type" VARCHAR(40) NOT NULL,
                    "maxScore" DECIMAL(5, 2) NOT NULL,
                    "weight" DECIMAL(5, 2) NOT NULL,
                    "gradedAt" TIMESTAMP,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        // The H2 twin creates academic."Section" with a lean reference-check
        // shape; add the columns the transcript join reads.
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"sectionNumber\" VARCHAR(40)");
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"courseId\" VARCHAR(120)");
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"semesterId\" VARCHAR(120)");
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"classroomId\" VARCHAR(120)");
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"status\" VARCHAR(40)");
        // The roster-override path delivers an inbox notification; the H2
        // twin does not create it (same bootstrap as the escape-hatch test).
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS notifications.notification (
                    id VARCHAR(120) PRIMARY KEY,
                    user_id VARCHAR(120) NOT NULL,
                    title VARCHAR(240) NOT NULL,
                    message VARCHAR(2000) NOT NULL,
                    type VARCHAR(40) NOT NULL,
                    link VARCHAR(500),
                    announcement_id VARCHAR(120),
                    source_key VARCHAR(160),
                    is_read BOOLEAN NOT NULL DEFAULT FALSE,
                    read_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityType\" = 'THESIS_GROUP'");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM academic.\"Enrollment\" WHERE \"id\" LIKE 'enr-eli-%'");
        jdbc.update("DELETE FROM academic.\"Section\" WHERE \"id\" LIKE 'sec-eli-%'");
        jdbc.update("DELETE FROM academic.\"Course\" WHERE \"id\" LIKE 'crs-eli-%'");
        jdbc.update("DELETE FROM academic.\"Student\" WHERE \"id\" LIKE 'eli-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'eli-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'eli-%'");
        jdbc.update("INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\") "
                        + "SELECT 'ay-eli', 2026, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE,"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP "
                        + "WHERE NOT EXISTS (SELECT 1 FROM \"academic\".\"AcademicYear\" WHERE \"id\" = 'ay-eli')");
        jdbc.update("INSERT INTO \"academic\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\", \"startDate\","
                        + " \"endDate\", \"registrationStart\", \"registrationEnd\", \"addDropStart\", \"addDropEnd\","
                        + " \"status\", \"createdAt\", \"updatedAt\") "
                        + "SELECT 'sem-eli-1', 'Eligibility Semester', 'Eligibility Semester', 'Eligibility',"
                        + " 'REGULAR', 'ay-eli', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE',"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP "
                        + "WHERE NOT EXISTS (SELECT 1 FROM \"academic\".\"Semester\" WHERE \"id\" = 'sem-eli-1')");
    }

    @Test
    void missingAcademicDataFailsClosedOnGroupCreate() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(INELIGIBLE); // no published academic rows

        createGroup(round, INELIGIBLE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THESIS_ELIGIBILITY_NOT_MET"))
                .andExpect(jsonPath("$.details.earnedCredits").value(0))
                .andExpect(jsonPath("$.details.requiredCredits").value(110))
                .andExpect(jsonPath("$.details.reasons",
                        org.hamcrest.Matchers.hasItems("INSUFFICIENT_CREDITS", "ACADEMIC_DATA_UNAVAILABLE")));
    }

    @Test
    void oneHundredNineCreditsIsJustBelowTheLine() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(INELIGIBLE);
        grantCredits(INELIGIBLE, 109, "B");

        createGroup(round, INELIGIBLE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THESIS_ELIGIBILITY_NOT_MET"))
                .andExpect(jsonPath("$.details.earnedCredits").value(109))
                .andExpect(jsonPath("$.details.reasons").value(
                        org.hamcrest.Matchers.hasItem("INSUFFICIENT_CREDITS")));
    }

    @Test
    void oneHundredTenCreditsWithTwoPointZeroCpaIsEligible() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(ELIGIBLE);
        grantCredits(ELIGIBLE, 110, "C"); // C maps to exactly 2.0 on the 4.0 scale

        createGroup(round, ELIGIBLE).andExpect(status().isOk());
    }

    @Test
    void enoughCreditsButLowCpaIsRefused() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(INELIGIBLE);
        grantCredits(INELIGIBLE, 120, "D"); // D maps to 1.0 < 2.0

        createGroup(round, INELIGIBLE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THESIS_ELIGIBILITY_NOT_MET"))
                .andExpect(jsonPath("$.details.reasons")
                        .value(org.hamcrest.Matchers.hasItem("LOW_GPA")));
    }

    @Test
    void unpublishedGradesDoNotCountTowardEligibility() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(INELIGIBLE);
        grantCreditsWithGradeStatus(INELIGIBLE, 130, "A", "DRAFT");

        createGroup(round, INELIGIBLE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THESIS_ELIGIBILITY_NOT_MET"));
    }

    @Test
    void ineligibleStudentCannotBeAddedAsMember() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(LEADER);
        ensureStudent(INELIGIBLE);
        ensureStudent(ELIGIBLE);
        grantCredits(LEADER, 115, "B");
        grantCredits(ELIGIBLE, 112, "B");

        createGroup(round, LEADER).andExpect(status().isOk());
        UUID group = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, round);

        addMember(group, INELIGIBLE, studentJwt(LEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THESIS_ELIGIBILITY_NOT_MET"));
        assertThat(memberCount(group)).isEqualTo(1);

        addMember(group, ELIGIBLE, studentJwt(LEADER)).andExpect(status().isOk());
        assertThat(memberCount(group)).isEqualTo(2);
    }

    @Test
    void ineligibleLeaderCannotPickTopic() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(INELIGIBLE);
        UUID group = insertDraftGroup(round, INELIGIBLE);
        UUID topic = insertPublishedTopic(round);

        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", group)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topic + "\"}")
                        .with(studentJwt(INELIGIBLE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THESIS_ELIGIBILITY_NOT_MET"));
    }

    @Test
    void governanceRosterOverrideAdmitsIneligibleStudentAndAuditsIt() throws Exception {
        UUID round = insertClosedRound();
        ensureStudent(INELIGIBLE);
        UUID group = insertDraftGroup(round, "eli-holder");
        ensureStudent("eli-holder");

        mvc.perform(post("/api/v1/thesis/groups/{id}/roster-override", group)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ADD\",\"studentId\":\"" + INELIGIBLE
                                + "\",\"reason\":\"late admit probe\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk());

        String summary = jdbc.queryForObject(
                "SELECT \"summary\" FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"action\" = 'THESIS_GROUP_ROSTER_OVERRIDE' AND \"entityId\" = ? "
                        + "ORDER BY \"createdAt\" DESC LIMIT 1",
                String.class, group.toString());
        assertThat(summary).contains("does not meet thesis eligibility");
        String afterState = jdbc.queryForObject(
                "SELECT \"afterState\" FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"action\" = 'THESIS_GROUP_ROSTER_OVERRIDE' AND \"entityId\" = ? "
                        + "ORDER BY \"createdAt\" DESC LIMIT 1",
                String.class, group.toString());
        assertThat(afterState).contains("studentEligible=false");
    }

    @Test
    void roundListExposesCallerEligibilityToStudentsOnly() throws Exception {
        insertLiveRound();
        ensureStudent(ELIGIBLE);
        grantCredits(ELIGIBLE, 118, "A");

        mvc.perform(get("/api/v1/thesis/rounds").with(studentJwt(ELIGIBLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eligibility.eligible").value(true))
                .andExpect(jsonPath("$[0].eligibility.earnedCredits").value(118))
                .andExpect(jsonPath("$[0].eligibility.requiredCredits").value(110));

        mvc.perform(get("/api/v1/thesis/rounds").with(studentJwt(INELIGIBLE + "-none")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eligibility.eligible").value(false));

        mvc.perform(get("/api/v1/thesis/rounds").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eligibility").doesNotExist());
    }

    @Test
    void memberEligibilityFlagMarksIneligibleRosterMembers() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(ELIGIBLE);
        ensureStudent(INELIGIBLE);
        grantCredits(ELIGIBLE, 115, "B");
        UUID group = insertDraftGroup(round, ELIGIBLE);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 2, FALSE)",
                UUID.randomUUID(), group, round, INELIGIBLE);

        // Staff keep the full roster flag view: governance uses it to see
        // which members miss the bar.
        mvc.perform(get("/api/v1/thesis/groups/{id}", group).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.studentId == '" + ELIGIBLE + "')].eligible")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.members[?(@.studentId == '" + INELIGIBLE + "')].eligible")
                        .value(org.hamcrest.Matchers.hasItem(false)));
    }

    @Test
    void tlcnRoundDoesNotEnforceTheKltnCreditRule() throws Exception {
        // The 110-credit/CPA regulation covers KLTN rounds only
        // (thesis.eligibility.round-types). A TLCN round must let the same
        // no-data student register without even evaluating the transcript.
        UUID round = insertRound("REGISTRATION_OPEN", "TLCN");
        ensureStudent(INELIGIBLE);

        createGroup(round, INELIGIBLE).andExpect(status().isOk());
    }

    @Test
    void monHocRoundDoesNotEnforceTheKltnCreditRule() throws Exception {
        UUID round = insertRound("REGISTRATION_OPEN", "MON_HOC");
        ensureStudent(INELIGIBLE);

        createGroup(round, INELIGIBLE).andExpect(status().isOk());
    }

    @Test
    void nonCoveredRoundsCarryNoCallerEligibility() throws Exception {
        insertRound("REGISTRATION_OPEN", "TLCN");
        insertRound("REGISTRATION_OPEN", "KLTN");
        ensureStudent(ELIGIBLE);
        grantCredits(ELIGIBLE, 118, "A");

        mvc.perform(get("/api/v1/thesis/rounds").with(studentJwt(ELIGIBLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Eligibility round TLCN')].eligibility")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.name == 'Eligibility round KLTN')].eligibility.enforced")
                        .value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test
    void studentReadsOnlyOwnMemberEligibilityFlag() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(ELIGIBLE);
        ensureStudent(INELIGIBLE);
        grantCredits(ELIGIBLE, 115, "B");
        UUID group = insertDraftGroup(round, ELIGIBLE);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 2, FALSE)",
                UUID.randomUUID(), group, round, INELIGIBLE);

        // A student caller sees their own flag but never a peer's academic
        // standing: other members' eligible field stays null (key omitted).
        mvc.perform(get("/api/v1/thesis/groups/{id}", group).with(studentJwt(ELIGIBLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.studentId == '" + ELIGIBLE + "')].eligible")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.members[?(@.studentId == '" + INELIGIBLE + "')].eligible")
                        .value(org.hamcrest.Matchers.empty()));
    }

    @Test
    void staffStillReadEveryMemberEligibilityFlag() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(ELIGIBLE);
        ensureStudent(INELIGIBLE);
        grantCredits(ELIGIBLE, 115, "B");
        UUID group = insertDraftGroup(round, ELIGIBLE);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 2, FALSE)",
                UUID.randomUUID(), group, round, INELIGIBLE);

        mvc.perform(get("/api/v1/thesis/groups/{id}", group).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.studentId == '" + ELIGIBLE + "')].eligible")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.members[?(@.studentId == '" + INELIGIBLE + "')].eligible")
                        .value(org.hamcrest.Matchers.hasItem(false)));
    }

    @Test
    void ineligibleInviteeErrorHidesTheirFiguresFromTheLeader() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(LEADER);
        ensureStudent(INELIGIBLE);
        grantCredits(LEADER, 115, "B");
        grantCredits(INELIGIBLE, 66, "B");

        createGroup(round, LEADER).andExpect(status().isOk());
        UUID group = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, round);

        // One student's academic standing must not leak to another student:
        // the 409 keeps the code and the reasons but drops every figure.
        addMember(group, INELIGIBLE, studentJwt(LEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THESIS_ELIGIBILITY_NOT_MET"))
                .andExpect(jsonPath("$.details.earnedCredits").doesNotExist())
                .andExpect(jsonPath("$.details.cpa").doesNotExist())
                .andExpect(jsonPath("$.details.reasons",
                        org.hamcrest.Matchers.hasItem("INSUFFICIENT_CREDITS")))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("66"))));
    }

    @Test
    void adminTopicPickErrorHidesLeaderFigures() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(INELIGIBLE);
        grantCredits(INELIGIBLE, 66, "B");
        UUID group = insertDraftGroup(round, INELIGIBLE);
        UUID topic = insertPublishedTopic(round);

        // The evaluated student is the leader, not the calling admin: the 409
        // still reports the reasons but carries none of the leader's numbers.
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", group)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topic + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THESIS_ELIGIBILITY_NOT_MET"))
                .andExpect(jsonPath("$.details.earnedCredits").doesNotExist())
                .andExpect(jsonPath("$.details.cpa").doesNotExist())
                .andExpect(jsonPath("$.details.reasons",
                        org.hamcrest.Matchers.hasItem("INSUFFICIENT_CREDITS")));
    }

    @Test
    void existingAuthorizationRulesAreUnchanged() throws Exception {
        UUID round = insertLiveRound();
        ensureStudent(INELIGIBLE);
        UUID group = insertDraftGroup(round, INELIGIBLE);

        // A non-member student is still refused before the eligibility check.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/thesis/groups/{id}/progress", group)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}")
                        .with(studentJwt("eli-outsider")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"));
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private ResultActions createGroup(UUID round, String studentId) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundId\":\"" + round + "\"}")
                .with(studentJwt(studentId)));
    }

    private ResultActions addMember(UUID groupId, String studentId, RequestPostProcessor actor)
            throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\"}")
                .with(actor));
    }

    private int memberCount(UUID groupId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId);
        return count == null ? 0 : count;
    }

    private void ensureStudent(String studentId) {
        // auth profile (ACTIVE) + academic profile for the transcript join
        String userId = "eli-user-" + studentId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Eligibility', 'Fixture', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" "
                        + "(\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "FIXTURE-" + studentId);
        jdbc.update("INSERT INTO academic.\"Student\" "
                        + "(\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\", "
                        + "\"admissionDate\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, 'ACTIVE', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                studentId, userId, "FIXTURE-" + studentId);
    }

    /** One PUBLISHED completed enrollment whose single course carries {@code credits}. */
    private void grantCredits(String studentId, int credits, String letter) {
        grantCreditsWithGradeStatus(studentId, credits, letter, "PUBLISHED");
    }

    private void grantCreditsWithGradeStatus(String studentId, int credits, String letter, String gradeStatus) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"credits\", \"departmentId\","
                        + " \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, 'Eligibility course', 'Eligibility course', 'Eligibility', ?,"
                        + " 'department-demo', 'sem-eli-1', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "crs-eli-" + suffix, "ELI" + suffix, credits);
        jdbc.update("INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"capacity\", \"enrolledCount\","
                        + " \"status\") VALUES (?, 'ELI', ?, 'sem-eli-1', 30, 1, 'OPEN')",
                "sec-eli-" + suffix, "crs-eli-" + suffix);
        jdbc.update("INSERT INTO academic.\"Enrollment\" "
                        + "(\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", "
                        + "\"gradeStatus\", \"finalGrade\", \"letterGrade\") "
                        + "VALUES (?, ?, ?, 'sem-eli-1', 'COMPLETED', CURRENT_TIMESTAMP, ?, ?, ?)",
                "enr-eli-" + suffix, studentId, "sec-eli-" + suffix, gradeStatus,
                "A".equals(letter) ? 9.0 : "B".equals(letter) ? 8.0 : "C".equals(letter) ? 6.0 : 5.0,
                letter);
    }

    private UUID insertLiveRound() {
        return insertRound("REGISTRATION_OPEN");
    }

    private UUID insertClosedRound() {
        return insertRound("REGISTRATION_CLOSED");
    }

    private UUID insertRound(String status) {
        return insertRound(status, "KLTN");
    }

    private UUID insertRound(String status, String thesisType) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                roundId, "Eligibility round " + thesisType, thesisType,
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.minusSeconds(3_600)),
                Timestamp.from(now.minusSeconds(3_600)), Timestamp.from(now.plusSeconds(3_600)),
                Timestamp.from(now.plusSeconds(7_200)), status);
        return roundId;
    }

    private UUID insertDraftGroup(UUID roundId, String leaderId) {
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) "
                        + "VALUES (?, ?, ?, 'DRAFT', 'PENDING')",
                groupId, roundId, leaderId);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, leaderId);
        return groupId;
    }

    private UUID insertPublishedTopic(UUID roundId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Eligibility topic', 'fixture', 2, 'PUBLISHED', 'eli-admin')",
                topicId, roundId);
        return topicId;
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "eli-admin", "ADMIN");
    }
}
