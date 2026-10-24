package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.service.ThesisReportService;
import io.campuscore.restfulapi.thesis.service.ThesisReportStorage;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.service.ThesisNotificationService;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.ReportUpdateRequest;
import io.campuscore.restfulapi.exception.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real transaction regressions for report data loss. Barriers control the SQL
 * schedule; storage is an in-memory object boundary, never a live provider.
 * Optional PostgreSQL target must be a dedicated, initially empty test database.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
class ThesisReportConcurrencyTest {
    private static final int WAIT_SECONDS = 20;
    private static final String ORIGINAL_URL = "https://reports.example.invalid/original.pdf";
    private static final String NEW_URL = "https://reports.example.invalid/revised.pdf";
    private static final byte[] OLD_BYTES = {'P', 'K', 3, 4, 'o', 'l', 'd'};
    private static final byte[] NEW_BYTES = {'P', 'K', 3, 4, 'n', 'e', 'w'};

    @Autowired private ThesisReportService reports;
    @Autowired private ThesisMutationService mutations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;
    @MockitoBean private ThesisReportStorage storage;
    @MockitoBean private ThesisNotificationService notifications;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String postgresUrl = System.getenv("CAMPUSCORE_REPORT_POSTGRES_URL");
        boolean postgres = postgresUrl != null;
        if (postgres && !postgresUrl.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_regression(?:\\?.*)?")) {
            throw new IllegalArgumentException("Report tests require a dedicated database with a name ending in _regression");
        }
        registry.add("spring.datasource.url", () -> postgres
                ? postgresUrl
                : "jdbc:h2:mem:report_concurrency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000");
        registry.add("spring.datasource.username", () -> postgres
                ? env("CAMPUSCORE_REPORT_POSTGRES_USER", "campuscore") : "sa");
        registry.add("spring.datasource.password", () -> postgres
                ? env("CAMPUSCORE_REPORT_POSTGRES_PASSWORD", "postgres") : "");
        registry.add("spring.flyway.locations", () -> postgres
                ? "classpath:db/migration" : "classpath:db/migration-h2");
    }

    @BeforeEach
    void configureObjectBoundary() {
        objects.clear();
        when(storage.provider()).thenReturn("supabase");
        when(storage.bucket()).thenReturn("regression-only");
        doAnswer(invocation -> {
            byte[] data = invocation.getArgument(2);
            objects.put(invocation.getArgument(0), Arrays.copyOf(data, data.length));
            return null;
        }).when(storage).put(anyString(), anyString(), any(byte[].class));
        doAnswer(invocation -> objects.get(invocation.getArgument(0))).when(storage).read(anyString());
        doAnswer(invocation -> {
            objects.remove(invocation.getArgument(0));
            return null;
        }).when(storage).delete(anyString());
    }

    @Test
    void metadataOnlyPatchMustNotRestoreTheUrlReadBeforeAnotherCommit() throws Exception {
        Fixture fx = fixture();
        reports.submit(fx.groupId(), "Original title", ORIGINAL_URL, "Original note", fx.actor());
        CountDownLatch urlUpdated = new CountDownLatch(1);
        CountDownLatch metadataRead = new CountDownLatch(1);
        CountDownLatch commitUrl = new CountDownLatch(1);
        CountDownLatch urlCommitted = new CountDownLatch(1);

        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            String sql = invocation.getArgument(0);
            if (Thread.currentThread().getName().equals("report-metadata")
                    && sql.contains("SELECT url, storage_key")) {
                metadataRead.countDown();
                await(urlCommitted, "new URL commit");
            }
            return result;
        }).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> linkWriter = pool.submit(() -> {
                transaction().executeWithoutResult(status -> {
                    jdbc.update("UPDATE thesis.thesis_group_report SET url = ? WHERE group_id = ?", NEW_URL, fx.groupId());
                    urlUpdated.countDown();
                    await(commitUrl, "permission to commit new URL");
                });
                urlCommitted.countDown();
            });
            await(urlUpdated, "uncommitted URL update");
            Future<Throwable> metadata = pool.submit(() -> capture("report-metadata", () ->
                    reports.updateReport(fx.groupId(), new ReportUpdateRequest("Renamed", null, null, null), fx.actor())));
            await(metadataRead, "metadata snapshot before URL commit");
            commitUrl.countDown();
            linkWriter.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(metadata.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNull();
            assertThat(column(fx, "url")).as("a metadata-only PATCH must preserve a concurrent committed link")
                    .isEqualTo(NEW_URL);
            assertThat(column(fx, "title")).isEqualTo("Renamed");
            assertThat(column(fx, "note")).isEqualTo("Original note");
        } finally {
            commitUrl.countDown();
            urlCommitted.countDown();
            close(pool);
        }
    }

    enum Mutation { LINK, FILE, METADATA, DETACH }

    @Test
    void concurrentFirstSubmissionsMustSerializeInsteadOfFailingTheUniqueGroupConstraint() throws Exception {
        Fixture fx = fixture();
        CountDownLatch firstFoundNoReport = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (Thread.currentThread().getName().equals("report-first-second")
                    && sql.contains("thesis_group") && sql.contains("FOR UPDATE")) {
                // With group serialization the second transaction cannot reach
                // its upsert yet; release the first transaction before blocking.
                releaseFirst.countDown();
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));
        doAnswer(invocation -> {
            int changed = (Integer) invocation.callRealMethod();
            String sql = invocation.getArgument(0);
            if (changed == 0 && sql.startsWith("UPDATE thesis.thesis_group_report SET")) {
                if (Thread.currentThread().getName().equals("report-first-first")) {
                    firstFoundNoReport.countDown();
                    await(releaseFirst, "second transaction either locks the group or observes no report");
                } else if (Thread.currentThread().getName().equals("report-first-second")) {
                    // Baseline's two upserts both decide to INSERT. Commit the
                    // first before letting the stale second INSERT execute.
                    releaseFirst.countDown();
                    await(firstCommitted, "first submission commit before stale second insert");
                }
            }
            return changed;
        }).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = pool.submit(() -> {
                try {
                    return capture("report-first-first", () -> reports.submit(fx.groupId(), "First title", ORIGINAL_URL, "First note", fx.actor()));
                } finally {
                    firstCommitted.countDown();
                }
            });
            await(firstFoundNoReport, "first submission UPDATE found no row");
            Future<Throwable> second = pool.submit(() -> capture("report-first-second", () ->
                    reports.submit(fx.groupId(), null, NEW_URL, null, fx.actor())));
            assertThat(first.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNull();
            assertThat(second.get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("both first submissions must complete through serialized upsert").isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = ?",
                    Integer.class, fx.groupId())).isOne();
            assertThat(column(fx, "url")).isEqualTo(NEW_URL);
            assertThat(column(fx, "title")).isEqualTo("First title");
            assertThat(column(fx, "note")).isEqualTo("First note");
        } finally {
            releaseFirst.countDown();
            firstCommitted.countDown();
            close(pool);
        }
    }

    @ParameterizedTest(name = "{0} waits for publication and then refuses the write")
    @EnumSource(Mutation.class)
    void publicationCommitMustFreezeEveryReportMutation(Mutation mutation) throws Exception {
        Fixture fx = fixture();
        reports.submit(fx.groupId(), "Original title", ORIGINAL_URL, "Original note", fx.actor());
        reports.submitFile(fx.groupId(), file(OLD_BYTES), null, null, fx.actor());
        Map<String, Object> before = row(fx);
        Map<String, byte[]> beforeObjects = Map.copyOf(objects);
        CountDownLatch publishedUncommitted = new CountDownLatch(1);
        CountDownLatch writerReachedBoundary = new CountDownLatch(1);
        CountDownLatch commitPublication = new CountDownLatch(1);
        CountDownLatch publicationCommitted = new CountDownLatch(1);
        AtomicInteger writerPid = new AtomicInteger();
        AtomicInteger publisherPid = new AtomicInteger();

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            boolean writer = Thread.currentThread().getName().equals("report-after-publication");
            if (writer && sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                // Fixed service reaches the locked lifecycle row before reading
                // authorization state. Let the publisher commit and unblock it.
                if (postgres()) writerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                writerReachedBoundary.countDown();
                return invocation.callRealMethod();
            }
            Object result = invocation.callRealMethod();
            if (writer && sql.contains("r.status AS round_status")) {
                // Baseline has no lifecycle lock: freeze its stale snapshot
                // until the newer published state is fully committed.
                writerReachedBoundary.countDown();
                await(publicationCommitted, "publication commit before stale write");
            }
            return result;
        }).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> publisher = pool.submit(() -> {
                transaction().executeWithoutResult(status -> {
                    jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'RESULTS_PUBLISHED' WHERE id = ?",
                            fx.roundId());
                    if (postgres()) publisherPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    publishedUncommitted.countDown();
                    await(commitPublication, "permission to commit publication");
                });
                publicationCommitted.countDown();
            });
            await(publishedUncommitted, "publisher holding lifecycle lock");
            Future<Throwable> writer = pool.submit(() -> capture("report-after-publication", () -> {
                switch (mutation) {
                    case LINK -> reports.submit(fx.groupId(), "Changed", NEW_URL, "Changed", fx.actor());
                    case FILE -> reports.submitFile(fx.groupId(), file(NEW_BYTES), "Changed", "Changed", fx.actor());
                    case METADATA -> reports.updateReport(fx.groupId(), new ReportUpdateRequest("Changed", "Changed", NEW_URL, null), fx.actor());
                    case DETACH -> reports.removeReportFile(fx.groupId(), fx.actor());
                }
                return null;
            }));
            await(writerReachedBoundary, "report writer lifecycle boundary");
            if (postgres() && writerPid.get() > 0) assertBlockedBy(writerPid.get(), publisherPid.get());
            commitPublication.countDown();
            publisher.get(WAIT_SECONDS, TimeUnit.SECONDS);
            Throwable failure = writer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(failure).as("a report mutation must recheck the committed published lifecycle")
                    .isInstanceOf(DomainException.class);
            assertThat(((DomainException) failure).code()).isEqualTo("REPORT_WINDOW_CLOSED");
            assertThat(row(fx)).as("publication must preserve the complete report row").isEqualTo(before);
            assertThat(objects.keySet()).isEqualTo(beforeObjects.keySet());
            beforeObjects.forEach((key, bytes) -> assertThat(objects.get(key)).containsExactly(bytes));
        } finally {
            commitPublication.countDown();
            publicationCommitted.countDown();
            close(pool);
        }
    }

    @Test
    void rosterCommitMustFenceAReportWriterAndPreserveTheExistingArtifact() throws Exception {
        Fixture fx = transaction().execute(status -> seedFixture(4));
        reports.submitFile(fx.groupId(), file(OLD_BYTES), "Original title", "Original note", fx.actor());
        Map<String, Object> before = row(fx);
        Map<String, byte[]> beforeObjects = Map.copyOf(objects);
        CountDownLatch rosterChanged = new CountDownLatch(1);
        CountDownLatch writerReachedBoundary = new CountDownLatch(1);
        CountDownLatch commitRoster = new CountDownLatch(1);
        CountDownLatch rosterCommitted = new CountDownLatch(1);
        AtomicInteger writerPid = new AtomicInteger();
        AtomicInteger rosterPid = new AtomicInteger();
        Jwt admin = Jwt.withTokenValue("test-only").header("alg", "none").subject("admin-user")
                .claim("roles", List.of("ADMIN")).build();

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            boolean writer = Thread.currentThread().getName().equals("report-after-roster");
            if (writer && sql.contains("thesis_group") && sql.contains("FOR UPDATE")) {
                if (postgres()) writerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                writerReachedBoundary.countDown();
                return invocation.callRealMethod();
            }
            Object result = invocation.callRealMethod();
            if (writer && sql.contains("r.status AS round_status")) {
                writerReachedBoundary.countDown();
                await(rosterCommitted, "roster commit before stale report write");
            }
            return result;
        }).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> roster = pool.submit(() -> {
                transaction().executeWithoutResult(status -> {
                    jdbc.queryForObject("SELECT id FROM thesis.thesis_group WHERE id = ? FOR UPDATE", UUID.class, fx.groupId());
                    // Roster management is legal only in REGISTRATION_OPEN;
                    // reopen inside this transaction, retaining group→round order.
                    jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'REGISTRATION_OPEN' WHERE id = ?", fx.roundId());
                    mutations.removeMember(fx.groupId(), fx.actor().getClaimAsString("studentId") + "-4", admin);
                    if (postgres()) rosterPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    rosterChanged.countDown();
                    await(commitRoster, "permission to commit roster removal");
                });
                rosterCommitted.countDown();
            });
            await(rosterChanged, "roster transaction holding group and round");
            Future<Throwable> writer = pool.submit(() -> capture("report-after-roster", () ->
                    reports.submitFile(fx.groupId(), file(NEW_BYTES), "Changed", "Changed", fx.actor())));
            await(writerReachedBoundary, "report writer group boundary");
            if (postgres() && writerPid.get() > 0) assertBlockedBy(writerPid.get(), rosterPid.get());
            commitRoster.countDown();
            roster.get(WAIT_SECONDS, TimeUnit.SECONDS);
            Throwable failure = writer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(failure).isInstanceOf(DomainException.class);
            assertThat(((DomainException) failure).code()).isEqualTo("REPORT_WINDOW_CLOSED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                    Integer.class, fx.groupId())).isEqualTo(3);
            assertThat(row(fx)).isEqualTo(before);
            assertThat(objects.keySet()).isEqualTo(beforeObjects.keySet());
            beforeObjects.forEach((key, bytes) -> assertThat(objects.get(key)).containsExactly(bytes));
        } finally {
            commitRoster.countDown();
            rosterCommitted.countDown();
            close(pool);
        }
    }

    @Test
    void outerRollbackMustDeleteTheNewObjectAndKeepThePreviousCommittedArtifact() {
        Fixture fx = fixture();
        reports.submitFile(fx.groupId(), file(OLD_BYTES), "Original title", "Original note", fx.actor());
        Map<String, Object> before = row(fx);
        String previousKey = (String) before.get("storage_key");
        AtomicReference<String> replacementKey = new AtomicReference<>();

        transaction().executeWithoutResult(status -> {
            reports.submitFile(fx.groupId(), file(NEW_BYTES), "Replacement", null, fx.actor());
            replacementKey.set((String) column(fx, "storage_key"));
            assertThat(objects).containsKeys(previousKey, replacementKey.get());
            status.setRollbackOnly();
        });

        assertThat(row(fx)).as("outer rollback restores the committed metadata").isEqualTo(before);
        assertThat(objects.get(previousKey)).containsExactly(OLD_BYTES);
        assertThat(objects).as("a successful inner call must still remove its new object on outer rollback")
                .doesNotContainKey(replacementKey.get());
        assertThat(reports.download(fx.groupId(), fx.actor()).data()).containsExactly(OLD_BYTES);
    }

    @Test
    void firstUploadOuterRollbackMustNotLeaveAnOrphanObjectOrReportRow() {
        Fixture fx = fixture();
        transaction().executeWithoutResult(status -> {
            reports.submitFile(fx.groupId(), file(NEW_BYTES), null, null, fx.actor());
            assertThat(objects).hasSize(1);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = ?",
                Integer.class, fx.groupId())).isZero();
        assertThat(objects).as("first-upload object must be compensated when its transaction rolls back").isEmpty();
    }

    @Test
    void committedReplacementDeletesOnlyThePreviousObjectAndPreservesSubmissionIdentity() {
        Fixture fx = fixture();
        reports.submit(fx.groupId(), "Original title", ORIGINAL_URL, "Original note", fx.actor());
        reports.submitFile(fx.groupId(), file(OLD_BYTES), null, null, fx.actor());
        Map<String, Object> before = row(fx);
        String previousKey = (String) before.get("storage_key");
        reports.submitFile(fx.groupId(), file(NEW_BYTES), null, null, fx.actor());
        String newKey = (String) column(fx, "storage_key");
        assertThat(newKey).isNotEqualTo(previousKey);
        assertThat(objects).containsOnlyKeys(newKey);
        assertThat(objects.get(newKey)).containsExactly(NEW_BYTES);
        assertThat(column(fx, "url")).isEqualTo(ORIGINAL_URL);
        assertThat(column(fx, "title")).isEqualTo(before.get("title"));
        assertThat(column(fx, "note")).isEqualTo(before.get("note"));
        assertThat(column(fx, "submitted_at")).isEqualTo(before.get("submitted_at"));
        assertThat(column(fx, "submitted_by")).isEqualTo(before.get("submitted_by"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CAMPUSCORE_REPORT_POSTGRES_URL", matches = ".+")
    void actualPublicationMustWaitForAnInFlightUploadThenPreserveTheCommittedReport() throws Exception {
        Fixture fx = fixture();
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.50, final_score_finalized_at = CURRENT_TIMESTAMP "
                + "WHERE id = (SELECT topic_id FROM thesis.thesis_group WHERE id = ?)", fx.groupId());
        CountDownLatch uploadSaved = new CountDownLatch(1);
        CountDownLatch commitUpload = new CountDownLatch(1);
        CountDownLatch publisherAtLock = new CountDownLatch(1);
        AtomicInteger uploadPid = new AtomicInteger();
        AtomicInteger publisherPid = new AtomicInteger();

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (Thread.currentThread().getName().equals("report-publish-after-upload")
                    && sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                publisherPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                publisherAtLock.countDown();
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).queryForObject(anyString(), any(SqlParameterSource.class), eq(UUID.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> uploader = pool.submit(() -> capture("report-upload-before-publish", () -> {
                transaction().executeWithoutResult(status -> {
                    reports.submitFile(fx.groupId(), file(NEW_BYTES), "Committed before publication", null, fx.actor());
                    uploadPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    uploadSaved.countDown();
                    await(commitUpload, "permission to commit the in-flight upload");
                });
                return null;
            }));
            await(uploadSaved, "uploaded report saved inside uncommitted outer transaction");
            Future<Throwable> publisher = pool.submit(() -> capture("report-publish-after-upload", () ->
                    mutations.publishResults(fx.roundId(), admin())));
            await(publisherAtLock, "actual publishResults reached its lifecycle row lock");
            assertBlockedBy(publisherPid.get(), uploadPid.get());
            assertThat(jdbc.queryForObject("SELECT status FROM thesis.thesis_registration_round WHERE id = ?",
                    String.class, fx.roundId())).isEqualTo("REGISTRATION_CLOSED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = ?",
                    Integer.class, fx.groupId())).as("uncommitted upload is invisible to another transaction").isZero();
            commitUpload.countDown();
            assertThat(uploader.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNull();
            assertThat(publisher.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNull();
            assertThat(jdbc.queryForObject("SELECT status FROM thesis.thesis_registration_round WHERE id = ?",
                    String.class, fx.roundId())).isEqualTo("RESULTS_PUBLISHED");
            assertThat(column(fx, "title")).isEqualTo("Committed before publication");
            assertThat(objects).hasSize(1);
            assertThat(reports.download(fx.groupId(), fx.actor()).data()).containsExactly(NEW_BYTES);
        } finally {
            commitUpload.countDown();
            close(pool);
        }
    }

    private Fixture fixture() {
        // PostgreSQL enforces the approved roster at COMMIT through V113's
        // deferred triggers. Build all three active profiles and seats inside
        // one transaction; no intermediate deficient approval is committed.
        if (postgres()) return transaction().execute(status -> seedFixture(3));
        return seedFixture(1);
    }

    private Fixture seedFixture(int memberCount) {
        String student = "report-race-" + UUID.randomUUID().toString().substring(0, 8);
        String user = "user-" + student;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\",\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                + "VALUES (?, ?, 'test-only', 'Report', 'Race', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                user, student + "@test.invalid");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\",\"admissionDate\") "
                + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)", student, user, student);
        UUID round = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round (id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                + "registration_start, registration_end, status, gvpb_deadline) VALUES (?, 'Report race fixture', 'KLTN', ?, ?, ?, ?, 'REGISTRATION_CLOSED', ?)",
                round, Timestamp.from(now.minusSeconds(7200)), Timestamp.from(now.minusSeconds(3600)),
                Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now.plusSeconds(86400)));
        UUID topic = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                + "VALUES (?, ?, 'department-demo', 'Report race topic', 'test only', 1, 'PUBLISHED', ?)", topic, round, user);
        UUID group = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'APPROVED')", group, round, student, topic);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                + "VALUES (?, ?, ?, ?, 1, TRUE)", UUID.randomUUID(), group, round, student);
        for (int seat = 2; seat <= memberCount; seat++) {
            String peer = student + "-" + seat;
            String peerUser = "user-" + peer;
            jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\",\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                    + "VALUES (?, ?, 'test-only', 'Report', 'Peer', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    peerUser, peer + "@test.invalid");
            jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\",\"admissionDate\",\"status\") "
                    + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP, 'ACTIVE')", peer, peerUser, peer);
            jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                    + "VALUES (?, ?, ?, ?, ?, FALSE)", UUID.randomUUID(), group, round, peer, seat);
        }
        Jwt actor = Jwt.withTokenValue("test-only").header("alg", "none").subject(user)
                .claim("studentId", student).claim("roles", List.of("STUDENT")).build();
        return new Fixture(round, group, actor);
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "report.docx", "application/octet-stream", bytes);
    }

    private Map<String, Object> row(Fixture fx) {
        // No file_data is persisted with the controlled object provider, so
        // equality compares actual values rather than byte-array identities.
        return jdbc.queryForMap("SELECT * FROM thesis.thesis_group_report WHERE group_id = ?", fx.groupId());
    }

    private Object column(Fixture fx, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM thesis.thesis_group_report WHERE group_id = ?", Object.class, fx.groupId());
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }

    private static Throwable capture(String threadName, Callable<?> action) {
        Thread.currentThread().setName(threadName);
        try {
            action.call();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private static void await(CountDownLatch latch, String phase) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).as(phase).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted at " + phase, exception);
        }
    }

    private static void close(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).as("all transaction workers terminate").isTrue();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean postgres() {
        return System.getenv("CAMPUSCORE_REPORT_POSTGRES_URL") != null;
    }

    private void assertBlockedBy(int writerPid, int publisherPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        do {
            Boolean blocked = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM unnest(pg_blocking_pids(?)) blocker WHERE blocker = ?)",
                    Boolean.class, writerPid, publisherPid);
            if (Boolean.TRUE.equals(blocked)) return;
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        throw new AssertionError("PostgreSQL must observe the waiting backend blocked by the expected transaction");
    }

    private static Jwt admin() {
        return Jwt.withTokenValue("test-only").header("alg", "none").subject("report-race-admin")
                .claim("roles", List.of("ADMIN")).build();
    }

    private record Fixture(UUID roundId, UUID groupId, Jwt actor) { }
}
