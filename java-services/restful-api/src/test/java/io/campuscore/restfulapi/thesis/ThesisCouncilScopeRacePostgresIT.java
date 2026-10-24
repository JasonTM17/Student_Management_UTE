package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.exception.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Real-PostgreSQL proof for the council scope gate under concurrency: a dean
 * restaffing a council serializes with a topic assignment on the shared
 * round -> council lock order, so the dean's write either lands first or
 * reads the committed foreign topic after the locks and is refused — never a
 * deadlock, and never a foreign topic graded by a panel the dean restaffed
 * behind the assignment's back.
 *
 * <p><b>Environment contract</b> (opt-in, same convention as
 * {@code ThesisRosterLockOrderPostgresIT}): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database. Fixture rows use {@code csr-} prefixed
 * markers and are deleted after every test.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisCouncilScopeRacePostgresIT {

    private static final String ROUND_NAME = "csr-scope-race";
    private static final String ASSIGN_NAME = "csr-assign-worker";
    private static final String REMOVE_NAME = "csr-remove-worker";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisCouncilService councils;
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
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'csr-%'"
                        + " OR \"entityId\" IN (SELECT CAST(c.id AS text) FROM thesis.thesis_council c"
                        + " JOIN thesis.thesis_registration_round r ON r.id = c.round_id WHERE r.name = ?)",
                ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_council WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name = ?", ROUND_NAME);
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'csr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'csr-user-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'csr-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'csr-%'");
    }

    /**
     * The assignment writer parks right after its council FOR UPDATE while
     * holding the round and council locks — the same two acquisitions every
     * staffing write makes in order. The dean's removeMember queues on the
     * round lock, and once the foreign topic commits, its post-lock scope
     * read must see it and refuse: the dean's roster must be exactly what it
     * was before the race.
     */
    @Test
    void deanRestaffQueuedBehindForeignTopicAssignmentSeesItAndRefuses() throws Exception {
        Jwt ownDean = dean("csr-dean-own", "department-demo", "faculty-demo");
        dean("csr-dean-foreign", "csr-dept-foreign", "csr-fac-foreign");
        for (String lecturer : List.of("csr-chair", "csr-sec", "csr-m1", "csr-m2")) {
            ensureLecturer(lecturer);
        }
        UUID home = insertTopic("Csr Race Home", "department-demo");
        UUID foreign = insertTopic("Csr Race Foreign", "csr-dept-foreign");
        UUID council = insertCouncil("csr-user-csr-dean-own");
        seat(council, "csr-chair", "CHAIR");
        seat(council, "csr-sec", "SECRETARY");
        seat(council, "csr-m1", "MEMBER");
        seat(council, "csr-m2", "MEMBER");
        assignCouncilTopic(council, home, "csr-admin");

        CountDownLatch assignHoldsLocks = new CountDownLatch(1);
        CountDownLatch releaseAssign = new CountDownLatch(1);
        AtomicInteger assignPid = new AtomicInteger();
        AtomicInteger removerPid = new AtomicInteger();
        barrierAfterCouncilLock(assignHoldsLocks, releaseAssign, assignPid, removerPid);

        var pool = Executors.newFixedThreadPool(2);
        try {
            var assigner = pool.submit(() -> {
                Thread.currentThread().setName(ASSIGN_NAME);
                try {
                    councils.assignTopic(council, foreign, admin());
                    return "OK";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(assignHoldsLocks.await(10, TimeUnit.SECONDS))
                    .as("assigner parked holding the round and council locks")
                    .isTrue();
            var remover = pool.submit(() -> {
                Thread.currentThread().setName(REMOVE_NAME);
                try {
                    councils.removeMember(council, "csr-m2", ownDean);
                    return "REMOVED";
                } catch (DomainException refusal) {
                    return refusal.code();
                }
            });
            // The dean's write must wait on the assigner's locks — prove the
            // wait on the real blocker list rather than a timing guess.
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!remover.isDone() && !blocked && System.nanoTime() < deadline) {
                if (removerPid.get() != 0 && assignPid.get() != 0) {
                    blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT ? = ANY(pg_blocking_pids(?))",
                            Boolean.class, assignPid.get(), removerPid.get()));
                }
                if (!blocked && !remover.isDone()) {
                    Thread.sleep(25);
                }
            }
            assertThat(blocked)
                    .as("the dean's restaff queues behind the assigner's locks")
                    .isTrue();
            releaseAssign.countDown();
            assertThat(assigner.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
            // The scope check runs after the locks, so the committed foreign
            // topic is visible: the dean's write refuses and changes nothing.
            assertThat(remover.get(20, TimeUnit.SECONDS)).isEqualTo("TOPIC_NOT_FOUND");
            assertThat(councilMemberIds(council))
                    .containsExactlyInAnyOrder("csr-chair", "csr-sec", "csr-m1", "csr-m2");
            assertThat(councilTopicIds(council))
                    .containsExactlyInAnyOrder(home, foreign);
        } finally {
            releaseAssign.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * The other serialized order: when the dean's restaffing commits before
     * the assignment lands, it stands — the foreign topic then grades a
     * panel the administrator knowingly assigned it to. Both verbs behave
     * the same: removal and addition each commit first or refuse, never
     * half-applied.
     */
    @Test
    void deanRestaffCommittedBeforeForeignAssignmentStands() {
        Jwt ownDean = dean("csr-dean-own", "department-demo", "faculty-demo");
        dean("csr-dean-foreign", "csr-dept-foreign", "csr-fac-foreign");
        for (String lecturer : List.of("csr-chair", "csr-sec", "csr-m1", "csr-m2", "csr-crony")) {
            ensureLecturer(lecturer);
        }
        UUID home = insertTopic("Csr Order Home", "department-demo");
        UUID foreign = insertTopic("Csr Order Foreign", "csr-dept-foreign");
        UUID council = insertCouncil("csr-user-csr-dean-own");
        seat(council, "csr-chair", "CHAIR");
        seat(council, "csr-sec", "SECRETARY");
        seat(council, "csr-m1", "MEMBER");
        seat(council, "csr-m2", "MEMBER");
        assignCouncilTopic(council, home, "csr-admin");

        // While the council grades only the home topic the owning dean keeps
        // both staffing verbs; they commit before the foreign topic exists.
        councils.removeMember(council, "csr-m2", ownDean);
        councils.addMember(council, "csr-crony", "MEMBER", ownDean);
        assertThat(councilMemberIds(council))
                .containsExactlyInAnyOrder("csr-chair", "csr-sec", "csr-m1", "csr-crony");

        councils.assignTopic(council, foreign, admin());
        assertThat(councilTopicIds(council))
                .containsExactlyInAnyOrder(home, foreign);
    }

    /**
     * Parks the assigner thread immediately after its council FOR UPDATE
     * returns — round and council locks already held — and captures each
     * worker's backend pid so the main thread can prove the dean's write
     * waits on the assigner's locks through pg_blocking_pids.
     */
    private void barrierAfterCouncilLock(CountDownLatch assignHoldsLocks, CountDownLatch releaseAssign,
            AtomicInteger assignPid, AtomicInteger removerPid) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            String thread = Thread.currentThread().getName();
            Object result = call.callRealMethod();
            if (ASSIGN_NAME.equals(thread)
                    && sql.contains("FROM thesis.thesis_council WHERE") && sql.contains("FOR UPDATE")) {
                assignPid.compareAndSet(0, namedJdbc.getJdbcOperations()
                        .queryForObject("SELECT pg_backend_pid()", Integer.class));
                assignHoldsLocks.countDown();
                if (!releaseAssign.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("race barrier timeout");
                }
            } else if (REMOVE_NAME.equals(thread)) {
                removerPid.compareAndSet(0, namedJdbc.getJdbcOperations()
                        .queryForObject("SELECT pg_backend_pid()", Integer.class));
            }
            return result;
        };
        doAnswer(pause).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));
        doAnswer(pause).when(namedJdbc).queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class));
    }

    private UUID insertTopic(String title, String departmentId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, 'fixture', 2, 'PUBLISHED', 'csr-admin')",
                id, roundId, departmentId, title);
        return id;
    }

    private UUID insertCouncil(String createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?)",
                id, roundId, "Council csr", createdBy);
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

    private List<String> councilMemberIds(UUID councilId) {
        return jdbc.queryForList(
                "SELECT lecturer_id FROM thesis.thesis_council_member WHERE council_id = ?"
                        + " ORDER BY lecturer_id",
                String.class, councilId);
    }

    private List<UUID> councilTopicIds(UUID councilId) {
        return jdbc.queryForList(
                "SELECT topic_id FROM thesis.thesis_council_topic WHERE council_id = ?",
                UUID.class, councilId);
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
        String userId = "csr-user-" + lecturerId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\","
                        + "\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                        + "VALUES (?,'" + lecturerId + "@test.invalid','test','Faculty','Head','ACTIVE',FALSE,FALSE,0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                userId);
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                        + "VALUES (?,?,?,?,TRUE)",
                lecturerId, userId, departmentId, "GV-" + lecturerId);
        return Jwt.withTokenValue("council-scope-fixture").header("alg", "none")
                .subject(userId)
                .claim("roles", List.of("TRUONG_KHOA", "LECTURER"))
                .claim("lecturerId", lecturerId).build();
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "csr-user-" + lecturerId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\","
                        + "\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                        + "VALUES (?,'" + lecturerId + "@test.invalid','test','Scope','Race','ACTIVE',FALSE,FALSE,0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                userId);
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                        + "VALUES (?,?,'department-demo',?,TRUE)",
                lecturerId, userId, "GV-" + lecturerId);
    }

    private Jwt admin() {
        return Jwt.withTokenValue("council-scope-fixture").header("alg", "none")
                .subject("csr-admin").claim("roles", List.of("ADMIN")).build();
    }
}
