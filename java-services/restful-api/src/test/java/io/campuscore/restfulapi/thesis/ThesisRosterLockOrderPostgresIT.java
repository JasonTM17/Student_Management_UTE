package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.model.GroupStatus;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.service.ThesisSupervisorService;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RosterOverrideRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicReassignRequest;
import io.campuscore.restfulapi.exception.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Real-PostgreSQL proofs for the roster override that H2 cannot host: the V141
 * deferred size-exception validator only raises at COMMIT on a real engine,
 * and the group->round lock order only means something under real row locks.
 *
 * <p><b>Environment contract</b> (opt-in, same convention as
 * {@code ThesisRosterOverrideRacePostgresIT}): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database. Fixture rows use {@code rlo-} prefixed
 * markers and are deleted after every test.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisRosterLockOrderPostgresIT {

    private static final String WORKER_NAME = "rlo-worker";
    private static final String APPROVER_NAME = "rlo-approver";
    private static final String ROUND_NAME = "rlo-lock-order";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisMutationService mutations;
    @Autowired private ThesisCouncilService councils;
    @Autowired private ThesisSupervisorService supervisors;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private UUID roundId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "campuscore"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @BeforeEach
    void prepare() {
        Mockito.reset(namedJdbc);
        cleanFixtures();
        Instant now = Instant.now();
        roundId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, report_date, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, 'REGISTRATION_CLOSED')",
                roundId, ROUND_NAME,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(8 * 86_400)), Timestamp.from(now.minusSeconds(7 * 86_400)),
                Timestamp.from(now.plusSeconds(86_400)), Timestamp.from(now.plusSeconds(5 * 86_400)));
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'rlo-%'"
                        + " OR \"entityId\" IN (SELECT CAST(g.id AS text) FROM thesis.thesis_group g"
                        + " JOIN thesis.thesis_registration_round r ON r.id = g.round_id WHERE r.name = ?)",
                ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_group WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_council WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name = ?", ROUND_NAME);
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'rlo-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rlo-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rlo-user-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'rlo-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'rlo-%'");
    }

    /**
     * A documented exception on a PENDING group used to write size_exception_*
     * unconditionally; V141's deferred validator then aborted the COMMIT
     * because a non-approved group may not carry the marker. The exception now
     * lives on the audit row alone until the group is approved.
     */
    @Test
    void pendingGroupAcceptsDocumentedSizeExceptionWithoutTheMarker() {
        UUID topicId = insertTopic("Rlo Pending Topic");
        UUID groupId = insertGroup(topicId, "rlo-p", 1);
        String addedStudent = "rlo-added-student";
        ensureStudent(addedStudent);

        mutations.overrideGroupRoster(groupId,
                new RosterOverrideRequest(addedStudent, "documented below-minimum exception", true),
                admin());

        Integer members = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId);
        assertThat(members).isEqualTo(2);
        String marker = jdbc.queryForObject(
                "SELECT size_exception_reason FROM thesis.thesis_group WHERE id = ?",
                String.class, groupId);
        assertThat(marker)
                .as("a non-approved group keeps the exception on the audit row, never the column")
                .isNull();
        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityId\" = ? AND \"action\" = 'THESIS_GROUP_ROSTER_OVERRIDE'",
                Integer.class, groupId.toString());
        assertThat(auditRows).isEqualTo(1);
    }

    /**
     * A non-approved roster has no minimum: a one-member pending group must
     * grow without the exception flag (the deferred validator would refuse a
     * marker it anyway cannot carry), approval without the flag still
     * enforces the minimum, and a governance approval with flag + reason
     * lands the marker in the same commit the validator inspects.
     */
    @Test
    void pendingRosterGrowsFreelyAndUnderSizeApprovalNeedsTheExceptionFlag() {
        UUID topicId = insertTopic("Rlo Grow Topic");
        UUID groupId = insertGroup(topicId, "rlo-g", 1);
        ensureStudent("rlo-grow-student");

        mutations.overrideGroupRoster(groupId,
                new RosterOverrideRequest("rlo-grow-student", "post-close seat", null),
                admin());
        Integer members = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId);
        assertThat(members).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT size_exception_reason FROM thesis.thesis_group WHERE id = ?",
                String.class, groupId)).isNull();

        assertThatThrownBy(() -> mutations.approveGroup(groupId, admin()))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("GROUP_TOO_SMALL"));

        // A topic supervisor reviews the group but is not governance: the
        // flag alone cannot waive the minimum for them.
        ensureLecturer("rlo-sup");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), topicId, "rlo-sup");
        assertThatThrownBy(() -> mutations.approveGroup(groupId,
                new GroupApprovalRequest(true, "documented post-close seat"),
                lecturer("rlo-sup")))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("GOVERNANCE_ROLE_REQUIRED"));

        // Governance approval records the documented exception atomically;
        // the deferred membership validator accepts a two-member APPROVED
        // roster carrying it.
        mutations.approveGroup(groupId,
                new GroupApprovalRequest(true, "documented post-close seat"),
                admin());
        Map<String, Object> approved = jdbc.queryForMap(
                "SELECT approval_status, size_exception_reason, size_exception_by"
                        + " FROM thesis.thesis_group WHERE id = ?",
                groupId);
        assertThat(approved.get("approval_status")).isEqualTo("APPROVED");
        assertThat(approved.get("size_exception_reason")).isEqualTo("documented post-close seat");
        assertThat(approved.get("size_exception_by")).isEqualTo("rlo-admin");
        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityId\" = ? AND \"action\" = 'THESIS_GROUP_APPROVED'",
                Integer.class, groupId.toString());
        assertThat(auditRows).isEqualTo(1);
    }

    /**
     * The under-minimum approval waiver is a governance act, not a review
     * act: it belongs to a global admin or the faculty head whose faculty
     * contains the group's topic. A dean from another faculty who happens to
     * supervise the topic keeps ordinary review rights through the
     * dean-supervisor path but must not waive the minimum.
     */
    @Test
    void underSizeApprovalWaiverBelongsToTheOwningFacultyNotTheSupervisor() {
        UUID topicId = insertTopic("Rlo Waiver Scope Topic");
        UUID groupId = insertGroup(topicId, "rlo-w", 2);

        // A foreign dean supervising the topic passes reviewer authorization
        // but the waiver itself must stay closed to them.
        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), topicId, "rlo-dean-foreign");
        assertThatThrownBy(() -> mutations.approveGroup(groupId,
                new GroupApprovalRequest(true, "documented below-minimum exception"),
                foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("GOVERNANCE_ROLE_REQUIRED"));
        assertThat(jdbc.queryForObject(
                "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                String.class, groupId)).isEqualTo("PENDING");

        // The head of the faculty that owns the topic's department does hold
        // the waiver — supervising the topic is irrelevant to that.
        Jwt ownDean = dean("rlo-dean-own", "department-demo", "faculty-demo");
        mutations.approveGroup(groupId,
                new GroupApprovalRequest(true, "documented below-minimum exception"), ownDean);
        Map<String, Object> approved = jdbc.queryForMap(
                "SELECT approval_status, size_exception_reason, size_exception_by"
                        + " FROM thesis.thesis_group WHERE id = ?",
                groupId);
        assertThat(approved.get("approval_status")).isEqualTo("APPROVED");
        assertThat(approved.get("size_exception_reason"))
                .isEqualTo("documented below-minimum exception");
        assertThat(approved.get("size_exception_by")).isEqualTo("rlo-user-rlo-dean-own");

        // A global admin on a fresh under-size group approves as before.
        UUID adminGroup = insertGroup(insertTopic("Rlo Admin Waiver Topic"), "rlo-a", 2);
        mutations.approveGroup(adminGroup,
                new GroupApprovalRequest(true, "documented below-minimum exception"), admin());
        assertThat(jdbc.queryForObject(
                "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                String.class, adminGroup)).isEqualTo("APPROVED");
    }

    /**
     * A pending roster has no minimum to waive: removing a member must not
     * demand the exception flag, while the audit row still records the
     * below-minimum outcome. The same removal on an APPROVED group keeps
     * requiring the flag the deferred validator enforces.
     */
    @Test
    void removingFromAPendingGroupNeverAsksForTheExceptionFlag() {
        UUID topicId = insertTopic("Rlo Remove Topic");
        UUID groupId = insertGroup(topicId, "rlo-r", 3);

        mutations.overrideGroupRoster(groupId,
                new RosterOverrideRequest("rlo-r-student-3", "REMOVE", "stepped back", null, null),
                admin());
        Integer members = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId);
        assertThat(members).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT size_exception_reason FROM thesis.thesis_group WHERE id = ?",
                String.class, groupId))
                .as("a non-approved group can never carry the marker")
                .isNull();
        String summary = jdbc.queryForObject(
                "SELECT \"summary\" FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityId\" = ? AND \"action\" = 'THESIS_GROUP_ROSTER_OVERRIDE'"
                        + " ORDER BY \"createdAt\" DESC LIMIT 1",
                String.class, groupId.toString());
        assertThat(summary).contains("below-minimum");

        // The flag still binds where the minimum does: an APPROVED roster.
        UUID approvedGroup = insertGroup(topicId, "rlo-ap", 3);
        mutations.approveGroup(approvedGroup,
                new GroupApprovalRequest(null, "governance approval"), admin());
        assertThatThrownBy(() -> mutations.overrideGroupRoster(approvedGroup,
                new RosterOverrideRequest("rlo-ap-student-3", "REMOVE", "stepped back", null, null),
                admin()))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("GROUP_SIZE_EXCEPTION_REQUIRED"));
    }

    /**
     * Lock-order proof: the override used to take round -> group while
     * approveGroup takes group -> round — a guaranteed deadlock when the two
     * paths interleave on one group. Two barriers make the schedule
     * deterministic: the override parks immediately before its group FOR
     * UPDATE (under the old order already holding the round; under the fixed
     * order holding nothing), and the approver parks immediately before its
     * round FOR UPDATE holding the group. Releasing both at once leaves each
     * side wanting the other's row under the old order — Postgres aborts one
     * with 40P01 — while under group -> round the parked worker holds nothing,
     * the approver commits first, and the worker follows.
     */
    @Test
    void overridePausedBeforeGroupLockSerializesWithApproveWithoutDeadlock() throws Exception {
        UUID topicId = insertTopic("Rlo Deadlock Topic");
        UUID groupId = insertGroup(topicId, "rlo-d", 3);
        ensureStudent("rlo-lock-student");
        Jwt admin = admin();

        CountDownLatch workerPaused = new CountDownLatch(1);
        CountDownLatch workerResume = new CountDownLatch(1);
        CountDownLatch approverPaused = new CountDownLatch(1);
        CountDownLatch approverResume = new CountDownLatch(1);
        barrierAtLocks(workerPaused, workerResume, approverPaused, approverResume);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.overrideGroupRoster(groupId,
                            new RosterOverrideRequest("rlo-lock-student", "post-close seat", null),
                            admin);
                    return "OK";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(workerPaused.await(10, TimeUnit.SECONDS))
                    .as("override parked before its group lock")
                    .isTrue();
            var approver = pool.submit(() -> {
                Thread.currentThread().setName(APPROVER_NAME);
                try {
                    mutations.approveGroup(groupId,
                            new GroupApprovalRequest(null, "governance approval"), admin);
                    return "OK";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(approverPaused.await(10, TimeUnit.SECONDS))
                    .as("approver holds the group, parked before the round lock")
                    .isTrue();
            // Release both: under round -> group each side now wants the row
            // the other holds (40P01); under group -> round the worker held
            // nothing and serializes behind the finished approver.
            workerResume.countDown();
            approverResume.countDown();
            Object overrideOutcome = worker.get(30, TimeUnit.SECONDS);
            Object approveOutcome = approver.get(30, TimeUnit.SECONDS);
            assertThat(overrideOutcome).as("override completes: %s", overrideOutcome).isEqualTo("OK");
            assertThat(approveOutcome).as("approve completes: %s", approveOutcome).isEqualTo("OK");
        } finally {
            workerResume.countDown();
            approverResume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Post-close roster edits are governance writes, not supervision: a dean
     * who supervises the topic from another faculty used to pass the group
     * check through the reviewer escape and could rewrite — or shrink below
     * the documented minimum — an approved roster. The override shares the
     * waiver's owning-faculty rule now; ordinary review rights stay
     * unchanged.
     */
    @Test
    void rosterOverrideBelongsToTheOwningFacultyNotTheSupervisor() {
        UUID topicId = insertTopic("Rlo Override Scope Topic");
        UUID groupId = insertGroup(topicId, "rlo-ov", 2);
        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        supervise(topicId, "rlo-dean-foreign");
        ensureStudent("rlo-override-student");

        // ADD: refused before a seat or the exception marker is written.
        assertThatThrownBy(() -> mutations.overrideGroupRoster(groupId,
                new RosterOverrideRequest("rlo-override-student", "documented post-close seat", true),
                foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId)).isEqualTo(2);

        // REMOVE on an approved roster: refused before the waiver lands.
        UUID approvedGroup = insertGroup(topicId, "rlo-apx", 3);
        mutations.approveGroup(approvedGroup,
                new GroupApprovalRequest(null, "governance approval"), admin());
        assertThatThrownBy(() -> mutations.overrideGroupRoster(approvedGroup,
                new RosterOverrideRequest("rlo-apx-student-3", "REMOVE", "stepped back", true, null),
                foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, approvedGroup)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT size_exception_reason FROM thesis.thesis_group WHERE id = ?",
                String.class, approvedGroup)).isNull();

        // The owning faculty head keeps the override in both directions, and
        // a global admin writes the waiver the foreign dean attempted.
        Jwt ownDean = dean("rlo-dean-own", "department-demo", "faculty-demo");
        mutations.overrideGroupRoster(groupId,
                new RosterOverrideRequest("rlo-override-student", "documented post-close seat", true),
                ownDean);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId)).isEqualTo(3);

        mutations.overrideGroupRoster(approvedGroup,
                new RosterOverrideRequest("rlo-apx-student-3", "REMOVE", "stepped back", true, null),
                admin());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, approvedGroup)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT size_exception_reason FROM thesis.thesis_group WHERE id = ?",
                String.class, approvedGroup)).isEqualTo("stepped back");
    }

    /**
     * Re-pointing an orphaned group and naming a counter-reviewer are
     * post-close outcome governance: the dean must own the faculty on the
     * topic, not merely supervise it. A foreign dean supervising both ends
     * loses the write; the home dean keeps it.
     */
    @Test
    void orphanReassignAndReviewerAssignmentNeedTheOwningFaculty() {
        UUID orphanTopic = insertTopic("Rlo Withdrawn Topic");
        UUID groupId = insertGroup(orphanTopic, "rlo-re", 3);
        jdbc.update("UPDATE thesis.thesis_topic SET result_status = 'WITHDRAWN' WHERE id = ?",
                orphanTopic);
        UUID newTopic = insertTopic("Rlo Reassign Target");

        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        supervise(orphanTopic, "rlo-dean-foreign");
        supervise(newTopic, "rlo-dean-foreign");

        // Re-pointing between two supervised foreign topics is still a
        // governance write the supervisor escape cannot authorize.
        assertThatThrownBy(() -> mutations.reassignOrphanedTopic(groupId,
                new TopicReassignRequest(newTopic, "topic withdrawn by governance"), foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThat(jdbc.queryForObject(
                "SELECT topic_id FROM thesis.thesis_group WHERE id = ?", UUID.class, groupId))
                .isEqualTo(orphanTopic);

        // A supervised topic does not hand its counter-reviewer pick to the
        // foreign dean either.
        ensureLecturer("rlo-gvpb");
        assertThatThrownBy(() -> councils.assignReviewer(newTopic, "rlo-gvpb", foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThat(jdbc.queryForObject(
                "SELECT gvpb_lecturer_id FROM thesis.thesis_topic WHERE id = ?",
                String.class, newTopic)).isNull();

        Jwt ownDean = dean("rlo-dean-own", "department-demo", "faculty-demo");
        councils.assignReviewer(newTopic, "rlo-gvpb", ownDean);
        assertThat(jdbc.queryForObject(
                "SELECT gvpb_lecturer_id FROM thesis.thesis_topic WHERE id = ?",
                String.class, newTopic)).isEqualTo("rlo-gvpb");

        mutations.reassignOrphanedTopic(groupId,
                new TopicReassignRequest(newTopic, "topic withdrawn by governance"), ownDean);
        assertThat(jdbc.queryForObject(
                "SELECT topic_id FROM thesis.thesis_group WHERE id = ?", UUID.class, groupId))
                .isEqualTo(newTopic);
    }

    /**
     * Council writes that move a topic — assigning it to the panel, releasing
     * it, or restaffing the panel grading it — bind the topic's own faculty.
     * The council-owner rule still admits the dean who created the panel, so
     * a foreign dean reaches the per-topic check and is refused there; the
     * home dean performs all three.
     */
    @Test
    void councilTopicWritesNeedTheOwningFacultyOfTheTopic() {
        UUID topicId = insertTopic("Rlo Council Scope Topic");
        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        supervise(topicId, "rlo-dean-foreign");

        ensureLecturer("rlo-chair");
        ensureLecturer("rlo-secretary");
        ensureLecturer("rlo-member");
        ensureLecturer("rlo-replacement");

        UUID foreignCouncil = insertCouncil("rlo-user-rlo-dean-foreign");
        seat(foreignCouncil, "rlo-chair", "CHAIR");
        seat(foreignCouncil, "rlo-secretary", "SECRETARY");
        seat(foreignCouncil, "rlo-member", "MEMBER");

        assertThatThrownBy(() -> councils.assignTopic(foreignCouncil, topicId, foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));

        // A foreign topic parked on their panel cannot be released or have
        // its grading roster restaffed by the supervising dean either.
        assignCouncilTopic(foreignCouncil, topicId, "rlo-user-rlo-dean-foreign");
        assertThatThrownBy(() -> councils.unassignTopic(foreignCouncil, topicId,
                "re-seat the topic", foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThatThrownBy(() -> councils.replaceMember(foreignCouncil,
                "rlo-member", "rlo-replacement", "seat swap", foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));

        // The owning faculty head performs all three on their own panel.
        Jwt ownDean = dean("rlo-dean-own", "department-demo", "faculty-demo");
        UUID ownCouncil = insertCouncil("rlo-user-rlo-dean-own");
        seat(ownCouncil, "rlo-chair", "CHAIR");
        seat(ownCouncil, "rlo-secretary", "SECRETARY");
        seat(ownCouncil, "rlo-member", "MEMBER");
        UUID ownTopic = insertTopic("Rlo Council Own Topic");

        councils.assignTopic(ownCouncil, ownTopic, ownDean);
        councils.replaceMember(ownCouncil, "rlo-member", "rlo-replacement",
                "documented seat swap", ownDean);
        councils.unassignTopic(ownCouncil, ownTopic, "release for reassignment", ownDean);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE council_id = ?",
                Integer.class, ownCouncil)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT lecturer_id FROM thesis.thesis_council_member "
                        + "WHERE council_id = ? AND member_role = 'MEMBER'",
                String.class, ownCouncil)).isEqualTo("rlo-replacement");
    }

    /**
     * Rewriting a topic's supervisor roster after registration closed is a
     * governance write, not supervision: a dean who co-supervises a foreign
     * topic used to keep the dean branch's supervisor escape and could
     * replace the home supervisor — who then loses the approval right a
     * planted crony gains. The roster now binds the owning faculty; ordinary
     * co-supervisors keep the owner-only refusal.
     */
    @Test
    void postCloseSupervisorRosterBelongsToTheOwningFacultyNotTheCoSupervisor() {
        ensureLecturer("rlo-home");
        ensureLecturer("rlo-crony");
        ensureLecturer("rlo-cosup");
        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        Jwt ownDean = dean("rlo-dean-own", "department-demo", "faculty-demo");

        UUID controlTopic = insertTopic("Rlo Supervisor Control Topic");
        supervise(controlTopic, "rlo-home", 1);
        supervise(controlTopic, "rlo-cosup", 2);
        // A plain co-supervisor keeps the owner-only refusal, and a foreign
        // dean supervising nothing on the topic keeps the concealed 404.
        assertThatThrownBy(() -> supervisors.setSupervisors(controlTopic,
                List.of("rlo-cosup", "rlo-crony"), lecturer("rlo-cosup")))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_OWNER_REQUIRED"));
        assertThatThrownBy(() -> supervisors.setSupervisors(controlTopic,
                List.of("rlo-crony"), foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));

        UUID topicId = insertTopic("Rlo Supervisor Scope Topic");
        supervise(topicId, "rlo-home", 1);
        supervise(topicId, "rlo-dean-foreign", 2);
        UUID pending = insertGroup(topicId, "rlo-sp", 3);

        // Co-supervising the topic no longer hands a foreign dean its roster.
        assertThatThrownBy(() -> supervisors.setSupervisors(topicId,
                List.of("rlo-crony"), foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThat(supervisorIds(topicId)).containsExactly("rlo-home", "rlo-dean-foreign");

        // The read side keeps the supervisor escape: the same dean still
        // reads the roster — only the write moved to the owning faculty.
        assertThat(supervisors.list(topicId, foreignDean)).hasSize(2);

        // The owning faculty head performs the swap the foreign dean
        // attempted; review power then follows the roster — the ousted home
        // supervisor loses it and the new supervisor approves.
        supervisors.setSupervisors(topicId, List.of("rlo-crony"), ownDean);
        assertThat(supervisorIds(topicId)).containsExactly("rlo-crony");
        assertThatThrownBy(() -> mutations.approveGroup(pending, lecturer("rlo-home")))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("GROUP_REVIEWER_REQUIRED"));
        mutations.approveGroup(pending, lecturer("rlo-crony"));
        assertThat(jdbc.queryForObject(
                "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                String.class, pending)).isEqualTo("APPROVED");

        // A global admin still writes the roster outright.
        UUID adminTopic = insertTopic("Rlo Supervisor Admin Topic");
        supervise(adminTopic, "rlo-home", 1);
        supervisors.setSupervisors(adminTopic, List.of("rlo-home", "rlo-crony"), admin());
        assertThat(supervisorIds(adminTopic)).containsExactly("rlo-home", "rlo-crony");
    }

    /**
     * {@code replaceMember} already insists a dean govern every topic the
     * council grades; the same restaffing done as remove-then-add used to
     * skip that check because both calls only required council-level access
     * — a single in-faculty topic, or council creation, sufficed. The
     * per-topic scope now binds all three, while a council with no assigned
     * topics stays manageable by its owner dean.
     */
    @Test
    void councilAddAndRemoveNeedEveryAssignedTopicInScope() {
        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        Jwt ownDean = dean("rlo-dean-own", "department-demo", "faculty-demo");
        UUID ownTopic = insertTopic("Rlo Council Own Topic");
        UUID foreignTopic = insertTopic("Rlo Council Foreign Topic", "rlo-dept-foreign");
        UUID foreignTopic2 = insertTopic("Rlo Council Foreign Topic 2", "rlo-dept-foreign");
        for (String lecturer : List.of("rlo-chair", "rlo-sec", "rlo-m1", "rlo-m2", "rlo-crony", "rlo-repl")) {
            ensureLecturer(lecturer);
        }

        // A mixed council admits the home dean on the single in-faculty
        // topic, but restaffing it — in one step or two — is refused.
        UUID mixed = insertCouncil("rlo-admin");
        seat(mixed, "rlo-chair", "CHAIR");
        seat(mixed, "rlo-sec", "SECRETARY");
        seat(mixed, "rlo-m1", "MEMBER");
        seat(mixed, "rlo-m2", "MEMBER");
        assignCouncilTopic(mixed, ownTopic, "rlo-admin");
        assignCouncilTopic(mixed, foreignTopic, "rlo-admin");
        assertThatThrownBy(() -> councils.replaceMember(mixed, "rlo-m1", "rlo-repl",
                "seat swap", ownDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThatThrownBy(() -> councils.removeMember(mixed, "rlo-m2", ownDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThatThrownBy(() -> councils.addMember(mixed, "rlo-crony", "MEMBER", ownDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThat(councilMemberIds(mixed))
                .containsExactlyInAnyOrder("rlo-chair", "rlo-sec", "rlo-m1", "rlo-m2");

        // Creating the council does not widen the scope either: a dean-owned
        // panel grading only a foreign topic still refuses its owner.
        UUID created = insertCouncil("rlo-user-rlo-dean-own");
        seat(created, "rlo-chair", "CHAIR");
        seat(created, "rlo-sec", "SECRETARY");
        seat(created, "rlo-m1", "MEMBER");
        seat(created, "rlo-m2", "MEMBER");
        assignCouncilTopic(created, foreignTopic2, "rlo-admin");
        assertThatThrownBy(() -> councils.replaceMember(created, "rlo-m1", "rlo-repl",
                "seat swap", ownDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThatThrownBy(() -> councils.removeMember(created, "rlo-m2", ownDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThatThrownBy(() -> councils.addMember(created, "rlo-crony", "MEMBER", ownDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));

        // The dean whose faculty owns that panel's topic restaffs it — the
        // council-level access was never the problem, only the missing
        // per-topic check was.
        councils.removeMember(created, "rlo-m1", foreignDean);
        assertThat(councilMemberIds(created))
                .containsExactlyInAnyOrder("rlo-chair", "rlo-sec", "rlo-m2");

        // An admin keeps both verbs on the mixed council.
        councils.removeMember(mixed, "rlo-m2", admin());
        councils.addMember(mixed, "rlo-crony", "MEMBER", admin());
        assertThat(councilMemberIds(mixed))
                .containsExactlyInAnyOrder("rlo-chair", "rlo-sec", "rlo-m1", "rlo-crony");

        // A council grading nothing yet has no topics to scope against, so
        // the dean who owns it keeps staffing it.
        UUID empty = insertCouncil("rlo-user-rlo-dean-own");
        councils.addMember(empty, "rlo-crony", "CHAIR", ownDean);
        councils.removeMember(empty, "rlo-crony", ownDean);
        assertThat(councilMemberIds(empty)).isEmpty();
    }

    /**
     * Driving a group's lifecycle after registration closed is governance:
     * every status a dean writes — DRAFT and SUBMITTED included — frees the
     * members' seats or seals their outcome through the roster the leader
     * owns. A dean who merely supervises the topic from another faculty
     * used to reach the lifecycle transitions through the reviewer escape a
     * plain supervisor never had — the same concealed refusal now answers
     * them all, while the group's leader, the owning faculty head and a
     * global admin keep their writes.
     */
    @Test
    void postCloseGroupLifecycleNeedsTheLeaderOrOwningFacultyNotTheSupervisor() {
        UUID topicId = insertTopic("Rlo Progress Scope Topic");
        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        Jwt ownDean = dean("rlo-dean-own", "department-demo", "faculty-demo");
        supervise(topicId, "rlo-dean-foreign", 1);
        ensureLecturer("rlo-plainsup");
        supervise(topicId, "rlo-plainsup", 2);
        UUID pending = insertGroup(topicId, "rlo-pg", 3);
        UUID approved = insertGroup(topicId, "rlo-ag", 3);
        mutations.approveGroup(approved,
                new GroupApprovalRequest(null, "governance approval"), admin());
        ProgressRequest cancel = new ProgressRequest(GroupStatus.CANCELLED);
        ProgressRequest complete = new ProgressRequest(GroupStatus.COMPLETED);

        // A plain supervisor was never allowed here; the control keeps it.
        assertThatThrownBy(() -> mutations.updateProgress(pending, cancel,
                lecturer("rlo-plainsup")))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("GROUP_OWNER_REQUIRED"));

        // The reviewer escape can no longer cancel or complete either.
        assertThatThrownBy(() -> mutations.updateProgress(pending, cancel, foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));
        assertThatThrownBy(() -> mutations.updateProgress(approved, complete, foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_group WHERE id = ?",
                String.class, pending)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_group WHERE id = ?",
                String.class, approved)).isEqualTo("SUBMITTED");

        // DRAFT and SUBMITTED are lifecycle targets too: the foreign dean
        // is refused them exactly like cancel and complete — only the
        // review verbs stay ordinary for a dean who merely supervises.
        UUID ordinary = insertGroup(topicId, "rlo-og", 3);
        assertThatThrownBy(() -> mutations.updateProgress(ordinary,
                new ProgressRequest(GroupStatus.DRAFT), foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));
        assertThatThrownBy(() -> mutations.updateProgress(ordinary,
                new ProgressRequest(GroupStatus.SUBMITTED), foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));
        assertThat(groupStatusPair(ordinary)).isEqualTo("SUBMITTED/PENDING");

        // The leader's own transitions are untouched — including when the
        // leader identity arrives stacked on a dean-role token: the guard
        // only binds a dean who is not the group's leader.
        Jwt leaderDean = Jwt.withTokenValue("roster-lock-fixture").header("alg", "none")
                .subject("rlo-user-rlo-dean-foreign")
                .claim("roles", List.of("TRUONG_KHOA", "LECTURER", "STUDENT"))
                .claim("lecturerId", "rlo-dean-foreign")
                .claim("studentId", "rlo-og-leader").build();
        mutations.updateProgress(ordinary, new ProgressRequest(GroupStatus.DRAFT), leaderDean);
        mutations.updateProgress(ordinary, new ProgressRequest(GroupStatus.SUBMITTED),
                student("rlo-og-leader"));
        assertThat(groupStatusPair(ordinary)).isEqualTo("SUBMITTED/PENDING");

        // The owning faculty head cancels and completes both groups, and a
        // global admin still performs the same transitions on fresh rows.
        mutations.updateProgress(pending, cancel, ownDean);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_group WHERE id = ?",
                String.class, pending)).isEqualTo("CANCELLED");
        mutations.updateProgress(approved, complete, ownDean);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_group WHERE id = ?",
                String.class, approved)).isEqualTo("COMPLETED");

        UUID adminPending = insertGroup(topicId, "rlo-apg", 3);
        UUID adminApproved = insertGroup(topicId, "rlo-aag", 3);
        mutations.approveGroup(adminApproved,
                new GroupApprovalRequest(null, "governance approval"), admin());
        mutations.updateProgress(adminPending, cancel, admin());
        mutations.updateProgress(adminApproved, complete, admin());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_group WHERE id = ?",
                String.class, adminPending)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_group WHERE id = ?",
                String.class, adminApproved)).isEqualTo("COMPLETED");
    }

    /**
     * A dean who only supervises the topic from another faculty once drove
     * the leader's lifecycle through the reviewer escape: resubmitting a
     * group the leader had withdrawn, then approving it — freezing the
     * roster after close with no leader consent. Every progress target a
     * dean writes now requires the owning faculty unless the dean is the
     * group's own leader or a global admin; supervising the topic keeps
     * only the ordinary review verbs (approve and reject).
     */
    @Test
    void foreignSupervisorDeanCannotResubmitButKeepsApproveAndReject() {
        ensureLecturer("rlo-home-sup");
        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        UUID topicId = insertTopic("Rlo Resubmit Scope Topic");
        supervise(topicId, "rlo-home-sup", 1);
        supervise(topicId, "rlo-dean-foreign", 2);
        UUID group = insertGroup(topicId, "rlo-rw", 3);

        // The leader withdraws their own pending group back to DRAFT.
        mutations.updateProgress(group, new ProgressRequest(GroupStatus.DRAFT),
                student("rlo-rw-leader"));
        assertThat(groupStatusPair(group)).isEqualTo("DRAFT/PENDING");

        // A plain supervisor was never allowed; the foreign dean now
        // shares that refusal instead of the reviewer escape's OK.
        assertThatThrownBy(() -> mutations.updateProgress(group,
                new ProgressRequest(GroupStatus.SUBMITTED), lecturer("rlo-home-sup")))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("GROUP_OWNER_REQUIRED"));
        assertThatThrownBy(() -> mutations.updateProgress(group,
                new ProgressRequest(GroupStatus.SUBMITTED), foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));
        assertThatThrownBy(() -> mutations.updateProgress(group,
                new ProgressRequest(GroupStatus.DRAFT), foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));

        // The leader resubmits their own group; the foreign dean then
        // approves it — review stays an ordinary path for them.
        mutations.updateProgress(group, new ProgressRequest(GroupStatus.SUBMITTED),
                student("rlo-rw-leader"));
        mutations.approveGroup(group, foreignDean);
        assertThat(groupStatusPair(group)).isEqualTo("SUBMITTED/APPROVED");

        // Rejection is the same ordinary review right on a fresh group.
        UUID rejected = insertGroup(topicId, "rlo-rr", 3);
        mutations.rejectGroup(rejected, new GroupRejectionRequest("returned for fixes"), foreignDean);
        assertThat(groupStatusPair(rejected)).isEqualTo("SUBMITTED/REJECTED");
    }

    /**
     * The symmetric lifecycle escape: the same foreign dean could
     * un-submit a pending group ahead of the home supervisor's approval,
     * delaying it until the leader resubmitted. Un-submitting is refused
     * the same way, so the ordinary approval lands on the first try.
     */
    @Test
    void foreignSupervisorDeanCannotUnsubmitAheadOfHomeApproval() {
        ensureLecturer("rlo-home-sup");
        Jwt foreignDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        UUID topicId = insertTopic("Rlo Unsubmit Scope Topic");
        supervise(topicId, "rlo-home-sup", 1);
        supervise(topicId, "rlo-dean-foreign", 2);
        UUID group = insertGroup(topicId, "rlo-ub", 3);

        assertThatThrownBy(() -> mutations.updateProgress(group,
                new ProgressRequest(GroupStatus.DRAFT), foreignDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("THESIS_GROUP_NOT_FOUND"));
        assertThat(groupStatusPair(group)).isEqualTo("SUBMITTED/PENDING");

        // The group stayed submitted, so the home supervisor's approval
        // is no longer delayable by the foreign dean's lifecycle writes.
        mutations.approveGroup(group, lecturer("rlo-home-sup"));
        assertThat(groupStatusPair(group)).isEqualTo("SUBMITTED/APPROVED");
    }

    /**
     * Owning a topic is an ordinary owner right, not a governance grant:
     * a dean whose identity created the topic keeps the supervisor roster
     * even after the topic leaves their faculty (a department transfer or
     * an admin edit) — the same account as a plain lecturer would pass,
     * so the dean role must not strip the right. A foreign dean who only
     * co-supervises the topic stays refused.
     */
    @Test
    void deanKeepsOwnerRightsOnATopicOutsideTheirFaculty() {
        ensureLecturer("rlo-owner-co");
        // The topic lives in faculty-demo while the dean who created it now
        // belongs to rlo-fac-foreign — the post-transfer shape.
        Jwt ownerDean = dean("rlo-dean-foreign", "rlo-dept-foreign", "rlo-fac-foreign");
        Jwt coSupervisingDean = dean("rlo-dean-foreign-2", "rlo-dept-foreign-2", "rlo-fac-foreign-2");
        UUID topicId = insertTopic("Rlo Dean Owned Foreign Topic", "department-demo");
        jdbc.update("UPDATE thesis.thesis_topic SET created_by = ? WHERE id = ?",
                "rlo-dean-foreign", topicId);
        supervise(topicId, "rlo-owner-co", 1);
        supervise(topicId, "rlo-dean-foreign-2", 2);

        supervisors.setSupervisors(topicId, List.of("rlo-dean-foreign", "rlo-owner-co"), ownerDean);
        assertThat(supervisorIds(topicId)).containsExactly("rlo-dean-foreign", "rlo-owner-co");

        // The same identity as a plain lecturer passes too — the dean role
        // only removed the right, it never granted it.
        supervisors.setSupervisors(topicId, List.of("rlo-dean-foreign", "rlo-owner-co"),
                lecturer("rlo-dean-foreign"));

        // The foreign co-supervisor who is not the owner is still refused
        // — ownership, not supervision, carried the right across faculties.
        assertThatThrownBy(() -> supervisors.setSupervisors(topicId,
                List.of("rlo-owner-co"), coSupervisingDean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThat(supervisorIds(topicId)).containsExactly("rlo-dean-foreign", "rlo-owner-co");
    }

    /**
     * Parks each named thread immediately before one lock acquisition, so the
     * locks it already holds at that instant are exactly the order under
     * test: the override before {@code thesis_group ... FOR UPDATE} and the
     * approver before {@code thesis_registration_round ... FOR UPDATE}.
     * Member-row locks ({@code thesis_group_member}) are never intercepted.
     */
    private void barrierAtLocks(CountDownLatch workerPaused, CountDownLatch workerResume,
            CountDownLatch approverPaused, CountDownLatch approverResume) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            String thread = Thread.currentThread().getName();
            if (WORKER_NAME.equals(thread)
                    && sql.contains("FROM thesis.thesis_group WHERE id") && sql.contains("FOR UPDATE")) {
                await(workerPaused, workerResume);
            } else if (APPROVER_NAME.equals(thread)
                    && sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                await(approverPaused, approverResume);
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));
        doAnswer(pause).when(namedJdbc).queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class));
    }

    private static void await(CountDownLatch paused, CountDownLatch resume) throws InterruptedException {
        paused.countDown();
        if (!resume.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("race barrier timeout");
        }
    }

    private UUID insertTopic(String title) {
        return insertTopic(title, "department-demo");
    }

    private UUID insertTopic(String title, String departmentId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, 'fixture', 2, 'PUBLISHED', 'rlo-admin')",
                id, roundId, departmentId, title);
        return id;
    }

    private void supervise(UUID topicId, String lecturerId) {
        supervise(topicId, lecturerId, 1);
    }

    private void supervise(UUID topicId, String lecturerId, int order) {
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                        + " VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), topicId, lecturerId, order);
    }

    private List<String> supervisorIds(UUID topicId) {
        return jdbc.queryForList(
                "SELECT lecturer_id FROM thesis.thesis_topic_supervisor WHERE topic_id = ?"
                        + " ORDER BY supervisor_order",
                String.class, topicId);
    }

    private List<String> councilMemberIds(UUID councilId) {
        return jdbc.queryForList(
                "SELECT lecturer_id FROM thesis.thesis_council_member WHERE council_id = ?"
                        + " ORDER BY lecturer_id",
                String.class, councilId);
    }

    private UUID insertCouncil(String createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?)",
                id, roundId, "Council rlo", createdBy);
        return id;
    }

    private void seat(UUID councilId, String lecturerId, String role) {
        jdbc.update("INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) "
                        + "VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), councilId, lecturerId, role);
    }

    private void assignCouncilTopic(UUID councilId, UUID topicId, String assignedBy) {
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) "
                        + "VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), councilId, topicId, assignedBy);
    }

    private UUID insertGroup(UUID topicId, String tag, int memberCount) {
        UUID groupId = UUID.randomUUID();
        String leader = tag + "-leader";
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'PENDING')",
                groupId, roundId, leader, topicId);
        for (int order = 1; order <= memberCount; order++) {
            String student = order == 1 ? leader : tag + "-student-" + order;
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, student, order, order == 1);
        }
        return groupId;
    }

    /**
     * A faculty head bound to a live lecturer profile in the given
     * department/faculty pair, with a JWT carrying both roles: the dean
     * scope check resolves the faculty through the profile while the
     * supervisor path reads the LECTURER role.
     */
    private Jwt dean(String lecturerId, String departmentId, String facultyId) {
        if (!"faculty-demo".equals(facultyId)) {
            jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"code\", \"isActive\")"
                            + " VALUES (?, ?, ?, TRUE)",
                    facultyId, "Faculty " + facultyId, facultyId);
            jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\")"
                            + " VALUES (?, ?, ?, ?, TRUE)",
                    departmentId, "Department " + departmentId, departmentId, facultyId);
        }
        String userId = "rlo-user-" + lecturerId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\","
                        + "\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                        + "VALUES (?,'" + lecturerId + "@test.invalid','test','Faculty','Head','ACTIVE',FALSE,FALSE,0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                userId);
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                        + "VALUES (?,?,?,?,TRUE)",
                lecturerId, userId, departmentId, "GV-" + lecturerId);
        return Jwt.withTokenValue("roster-lock-fixture").header("alg", "none")
                .subject(userId)
                .claim("roles", List.of("TRUONG_KHOA", "LECTURER"))
                .claim("lecturerId", lecturerId).build();
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "rlo-user-" + lecturerId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\","
                        + "\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                        + "VALUES (?,'" + lecturerId + "@test.invalid','test','Lock','Lecturer','ACTIVE',FALSE,FALSE,0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                userId);
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                        + "VALUES (?,?,'department-demo',?,TRUE)",
                lecturerId, userId, "GV-" + lecturerId);
    }

    private Jwt lecturer(String lecturerId) {
        return Jwt.withTokenValue("roster-lock-fixture").header("alg", "none")
                .subject("rlo-user-" + lecturerId)
                .claim("roles", List.of("LECTURER"))
                .claim("lecturerId", lecturerId).build();
    }

    private Jwt student(String studentId) {
        return Jwt.withTokenValue("roster-lock-fixture").header("alg", "none")
                .subject("rlo-user-" + studentId)
                .claim("roles", List.of("STUDENT"))
                .claim("studentId", studentId).build();
    }

    private String groupStatusPair(UUID groupId) {
        return jdbc.queryForObject(
                "SELECT status || '/' || approval_status FROM thesis.thesis_group WHERE id = ?",
                String.class, groupId);
    }

    private void ensureStudent(String studentId) {
        String userId = "rlo-user-" + studentId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\","
                        + "\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                        + "VALUES (?,'" + studentId + "@test.invalid','test','Lock','Order','ACTIVE',FALSE,FALSE,0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                userId);
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\",\"admissionDate\") "
                        + "VALUES (?,?,'RLO-1','curriculum-demo',2,CURRENT_TIMESTAMP)",
                studentId, userId);
    }

    private Jwt admin() {
        return Jwt.withTokenValue("roster-lock-fixture").header("alg", "none")
                .subject("rlo-admin").claim("roles", List.of("ADMIN")).build();
    }
}
