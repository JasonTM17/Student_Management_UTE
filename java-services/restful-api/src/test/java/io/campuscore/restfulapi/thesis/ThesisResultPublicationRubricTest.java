package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.service.ThesisNotificationService;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Result-publication rubric contract over the actual mutation, notification,
 * persistence, and transaction services. Only the SMTP-facing EmailService is
 * replaced: all deliveries stay in this test, using reserved fixture addresses.
 * This class exercises service behavior and makes no HTTP authorization claim.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_thesis_result_publication_rubric;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "mail.enabled=true",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=2525",
        "spring.mail.password=test-only-mail-boundary-mock"
})
class ThesisResultPublicationRubricTest {

    private static final String ROUND_NAME = "Rubric result publication";
    private static final List<String> EXPECTED_USERS = List.of("trp-user-1", "trp-user-2", "trp-user-3");
    private static final List<String> EXPECTED_EMAILS = List.of(
            "trp-user-1@fixture.test", "trp-user-2@fixture.test", "trp-user-3@fixture.test");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ThesisMutationService mutations;

    @Autowired
    private ThesisNotificationService notifier;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmailService emailService;

    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    private UUID roundId;
    private TransactionTemplate transaction;

    @BeforeEach
    void prepareCommittedFixture() {
        transaction = new TransactionTemplate(transactionManager);
        deliveries.clear();
        doAnswer(invocation -> {
            Map<String, Object> variables = invocation.getArgument(3);
            deliveries.add(new Delivery(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), new LinkedHashMap<>(variables)));
            return null;
        }).when(emailService).sendHtmlEmail(anyString(), anyString(), anyString(), anyMap());

        // H2's migration tree does not own the inbox table. Match its production
        // row shape in this class's private in-memory database, as the existing
        // ThesisNotificationServiceTest does.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS notifications.notification (
                    id VARCHAR(120) PRIMARY KEY,
                    user_id VARCHAR(120) NOT NULL,
                    title VARCHAR(240) NOT NULL,
                    message VARCHAR(2000) NOT NULL,
                    type VARCHAR(60) NOT NULL,
                    link VARCHAR(500),
                    announcement_id VARCHAR(120),
                    is_read BOOLEAN NOT NULL DEFAULT FALSE,
                    read_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM notifications.notification");
        // thesis_group_report is ON DELETE RESTRICT -> delete reports before groups.
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'trp-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'trp-%'");

        roundId = insertRound(ROUND_NAME);
        UUID topicId = insertFinalizedTopic(roundId);
        insertThreeMemberGroup(roundId, topicId, "APPROVED", "SUBMITTED", 1);
        insertThreeMemberGroup(roundId, topicId, "REJECTED", "SUBMITTED", 4);
        insertThreeMemberGroup(roundId, topicId, "APPROVED", "CANCELLED", 7);
        UUID otherRound = insertRound("Other round excluded from publication");
        insertThreeMemberGroup(otherRound, insertFinalizedTopic(otherRound), "APPROVED", "SUBMITTED", 10);
        insertUser("trp-admin");

        assertThat(roundStatus()).as("fixture round is committed and publishable").isEqualTo("REGISTRATION_CLOSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = ?", Integer.class, roundId))
                .as("fixture has approved, rejected, and cancelled groups in the target round").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_topic WHERE round_id = ? AND final_score IS NOT NULL",
                Integer.class, roundId)).as("fixture has a finalized topic score").isEqualTo(1);
        assertThat(observe()).isEqualTo(new Observation(List.of(), 0));
    }

    @Test
    void committedPublicationSendsHtmlEmailToEveryDistinctEligibleMemberOfOnlyThatRound() {
        var response = mutations.publishResults(roundId, admin());
        Observation actual = observe();
        printObservation("committed-publication", actual);

        assertThat(response.status()).isEqualTo(RoundStatus.RESULTS_PUBLISHED);
        assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
        assertThat(actual.inboxUsers()).as("only approved, non-cancelled members of the published round")
                .containsExactlyElementsOf(EXPECTED_USERS);
        assertThat(deliveries).extracting(Delivery::to)
                .as("one HTML delivery for each distinct eligible member")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_EMAILS);
        assertThat(deliveries).allSatisfy(delivery -> {
            assertThat(delivery.subject()).isNotBlank();
            assertThat(delivery.template()).isNotBlank();
            assertThat(delivery.variables()).isNotEmpty();
        });
    }

    @Test
    void outerRollbackLeavesRoundClosedAndEmitsNoInboxOrEmail() {
        transaction.executeWithoutResult(status -> {
            mutations.publishResults(roundId, admin());
            printObservation("inside-transaction-before-rollback", observe());
            status.setRollbackOnly();
        });
        Observation actual = observe();
        printObservation("after-outer-rollback", actual);

        assertSoftly(softly -> {
            softly.assertThat(roundStatus()).isEqualTo("REGISTRATION_CLOSED");
            softly.assertThat(actual.inboxUsers()).as("rolled-back publication must leave no inbox events").isEmpty();
            softly.assertThat(actual.emailCount()).as("rolled-back publication must leave no HTML deliveries").isZero();
        });
    }

    @Test
    void publicationEmitsNothingBeforeTheOuterCommit() {
        AtomicReference<Observation> beforeCommit = new AtomicReference<>();
        transaction.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            mutations.publishResults(roundId, admin());
            beforeCommit.set(observe());
            printObservation("before-outer-commit", beforeCommit.get());
        });
        printObservation("after-outer-commit", observe());

        assertSoftly(softly -> {
            softly.assertThat(beforeCommit.get().inboxUsers()).as("no inbox events before the outer commit").isEmpty();
            softly.assertThat(beforeCommit.get().emailCount()).as("no HTML deliveries before the outer commit").isZero();
            softly.assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
        });
    }

    @Test
    void repeatedPublicationReturns409WithoutAdditionalEvents() {
        mutations.publishResults(roundId, admin());
        Observation beforeRepeat = observe();
        assertThat(beforeRepeat.inboxUsers()).containsExactlyElementsOf(EXPECTED_USERS);

        assertThatThrownBy(() -> mutations.publishResults(roundId, admin()))
                .isInstanceOfSatisfying(DomainException.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(failure.getCode()).isEqualTo("ROUND_STATE_CONFLICT");
                });

        Observation actual = observe();
        printObservation("after-repeated-publication-409", actual);
        assertThat(actual).as("a repeated publication must add neither inbox rows nor email").isEqualTo(beforeRepeat);
    }

    @Test
    void directNotifierCannotSendUntilResultsArePublished() {
        int written = notifier.notifyResultsPublished(roundId);
        Observation actual = observe();
        printObservation("direct-notifier-on-closed-round-written-" + written, actual);

        assertSoftly(softly -> {
            softly.assertThat(roundStatus()).isEqualTo("REGISTRATION_CLOSED");
            softly.assertThat(written).as("a closed round cannot be announced as published").isZero();
            softly.assertThat(actual.inboxUsers()).as("a closed round cannot emit published-result inbox events").isEmpty();
            softly.assertThat(actual.emailCount()).as("a closed round cannot emit published-result email").isZero();
        });
    }

    private UUID insertRound(String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO thesis.thesis_registration_round
                    (id, name, thesis_type, lecturer_submit_start, lecturer_submit_end,
                     registration_start, registration_end, gvpb_deadline, report_date, defense_date, status)
                VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, ?, 'REGISTRATION_CLOSED')
                """, id, name, Timestamp.from(now.minusSeconds(432_000)), Timestamp.from(now.minusSeconds(345_600)),
                Timestamp.from(now.minusSeconds(259_200)), Timestamp.from(now.minusSeconds(172_800)),
                Timestamp.from(now.minusSeconds(86_400)), Timestamp.from(now.minusSeconds(43_200)), Timestamp.from(now));
        return id;
    }

    private UUID insertFinalizedTopic(UUID round) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO thesis.thesis_topic
                    (id, round_id, department_id, title, description, max_groups, status, created_by,
                     final_score, final_score_finalized_by, final_score_finalized_at, result_status)
                VALUES (?, ?, 'department-demo', 'Rubric publication topic', 'Isolated publication fixture',
                        3, 'PUBLISHED', 'trp-admin', 8.5, 'trp-chair', CURRENT_TIMESTAMP, 'GRADED')
                """, id, round);
        return id;
    }

    private void insertThreeMemberGroup(UUID round, UUID topic, String approval, String status, int firstStudent) {
        UUID group = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, group, round, "trp-student-" + firstStudent, topic, status, approval);
        for (int order = 1; order <= 3; order++) {
            int number = firstStudent + order - 1;
            String userId = "trp-user-" + number;
            String studentId = "trp-student-" + number;
            insertUser(userId);
            jdbc.update("""
                    INSERT INTO campuscore_auth."Student"
                        ("id", "userId", "studentId", "curriculumId", "year", "admissionDate")
                    VALUES (?, ?, ?, 'curriculum-demo', 4, CURRENT_TIMESTAMP)
                    """, studentId, userId, "TRP-" + number);
            jdbc.update("""
                    INSERT INTO thesis.thesis_group_member
                        (id, group_id, round_id, student_id, member_order, is_leader, active_participation)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), group, round, studentId, order, order == 1, !"CANCELLED".equals(status));
        }
    }

    private void insertUser(String id) {
        jdbc.update("""
                INSERT INTO campuscore_auth."User"
                    ("id", "email", "password", "firstName", "lastName", "status", "emailVerified",
                     "isSuperAdmin", "failedLoginAttempts", "createdAt", "updatedAt")
                VALUES (?, ?, 'test-password', 'Rubric', 'Fixture', 'ACTIVE', TRUE, FALSE, 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, id + "@fixture.test");
    }

    private Jwt admin() {
        return Jwt.withTokenValue("publication-service-fixture").header("alg", "none")
                .subject("trp-admin").claim("roles", List.of("ADMIN")).build();
    }

    private String roundStatus() {
        return jdbc.queryForObject("SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId);
    }

    private Observation observe() {
        return new Observation(jdbc.queryForList("SELECT user_id FROM notifications.notification ORDER BY user_id", String.class),
                deliveries.size());
    }

    private void printObservation(String checkpoint, Observation actual) {
        System.out.printf("THESIS_PUBLICATION_OBSERVATION checkpoint=%s round=%s inbox=%d inboxUsers=%s htmlEmail=%d%n",
                checkpoint, roundStatus(), actual.inboxUsers().size(), actual.inboxUsers(), actual.emailCount());
    }

    private record Delivery(String to, String subject, String template, Map<String, Object> variables) { }

    private record Observation(List<String> inboxUsers, int emailCount) { }
}
