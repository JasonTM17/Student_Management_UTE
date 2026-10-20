package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.campuscore.restfulapi.thesis.service.ThesisNotificationService;
import io.campuscore.restfulapi.thesis.service.ThesisSupervisorService;
import io.campuscore.restfulapi.thesis.service.ThesisSupervisorService.SupervisorRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Supervisor-assignment fan-out follows the same after-commit rule as
 * {@code ThesisMutationService} and {@code ThesisCouncilService}:
 * {@code notifySupervisorsAssigned} runs REQUIRES_NEW, so invoking it inside
 * the write transaction publishes a notification for a roster a later
 * rollback erased. The H2 catalog has no {@code notifications} schema, so the
 * real fan-out throws underneath the spy — the service must still swallow it.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_supervisor_notify;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisSupervisorNotificationCommitTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ThesisSupervisorService supervisors;

    @Autowired
    private PlatformTransactionManager txManager;

    @MockitoSpyBean
    private ThesisNotificationService notifier;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'sn-%' OR \"entityId\" LIKE 'sn-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'sn-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'sn-%'");
    }

    @Test
    void rolledBackAssignmentNotifiesNoSupervisor() {
        UUID roundId = insertRound("sn-round-rollback");
        UUID topicId = insertTopicOwnedBy(roundId, "sn-owner-1");
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            supervisors.setSupervisors(topicId, List.of("sn-owner-1", "sn-added-1"), lecturerJwt("sn-owner-1"));
            status.setRollbackOnly();
        });
        verify(notifier, never()).notifySupervisorsAssigned(any(), anyList());
    }

    @Test
    void committedAssignmentNotifiesNewSupervisorsOnce() {
        UUID roundId = insertRound("sn-round-commit");
        UUID topicId = insertTopicOwnedBy(roundId, "sn-owner-1");
        supervisors.setSupervisors(topicId, List.of("sn-owner-1", "sn-added-1"), lecturerJwt("sn-owner-1"));
        // The pre-existing owner is re-seeded, so only the genuinely new
        // supervisor belongs in the fan-out.
        verify(notifier, times(1)).notifySupervisorsAssigned(eq(topicId),
                argThat((List<String> ids) -> ids.equals(List.of("sn-added-1"))));
    }

    @Test
    void unchangedRosterNotifiesNobody() {
        UUID roundId = insertRound("sn-round-unchanged");
        UUID topicId = insertTopicOwnedBy(roundId, "sn-owner-1");
        List<String> roster = List.of("sn-owner-1", "sn-added-1");
        supervisors.setSupervisors(topicId, roster, lecturerJwt("sn-owner-1"));
        supervisors.setSupervisors(topicId, roster, lecturerJwt("sn-owner-1"));
        verify(notifier, times(1)).notifySupervisorsAssigned(eq(topicId), anyList());
    }

    @Test
    void notificationFailurePreservesCommittedRoster() {
        UUID roundId = insertRound("sn-round-notify-failure");
        UUID topicId = insertTopicOwnedBy(roundId, "sn-owner-1");
        doThrow(new RuntimeException("fan-out unavailable"))
                .when(notifier).notifySupervisorsAssigned(any(), anyList());
        List<SupervisorRow> rows =
                supervisors.setSupervisors(topicId, List.of("sn-owner-1", "sn-added-1"), lecturerJwt("sn-owner-1"));
        assertThat(rows).extracting(SupervisorRow::lecturerId)
                .containsExactly("sn-owner-1", "sn-added-1");
        Integer persisted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = ?",
                Integer.class, topicId);
        assertThat(persisted).isEqualTo(2);
        verify(notifier, times(1)).notifySupervisorsAssigned(eq(topicId), anyList());
    }

    private UUID insertRound(String name) {
        ensureLecturer("sn-owner-1");
        ensureLecturer("sn-added-1");
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, 'REGISTRATION_OPEN')",
                roundId, name,
                Timestamp.from(now.minusSeconds(14_400)), Timestamp.from(now.minusSeconds(7_200)),
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.plusSeconds(86_400)),
                Timestamp.from(now.plusSeconds(172_800)));
        return roundId;
    }

    private UUID insertTopicOwnedBy(UUID roundId, String ownerLecturerId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'fixture', 3, 'PUBLISHED', ?)",
                topicId, roundId, "sn-topic-" + topicId.toString().substring(0, 8), ownerLecturerId);
        // Topic creation seeds the creator as the first supervisor; keep the
        // fixture faithful so only later additions count as new assignments.
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), topicId, ownerLecturerId);
        return topicId;
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "sn-user-" + lecturerId;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Spec', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" "
                        + "(\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, TRUE)",
                lecturerId, userId, "GV-" + lecturerId);
    }

    private Jwt lecturerJwt(String lecturerId) {
        String userId = jdbc.queryForObject(
                "SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?",
                String.class, lecturerId);
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId)
                .claim("roles", List.of("LECTURER"))
                .claim("lecturerId", lecturerId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3_600))
                .build();
    }
}
