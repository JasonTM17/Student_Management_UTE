package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos.TopicResponse;
import io.campuscore.restfulapi.thesis.service.ThesisTopicService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Real-Postgres check for the batched supervisor read behind the topic list:
 * the quoted-identifier join against campuscore_auth must resolve on the
 * production dialect, and a full page must cost exactly one statement.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisTopicSupervisorListPostgresIT {

    @Autowired private ThesisTopicService topics;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_SUPLIST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_SUPLIST_POSTGRES_USER", "campuscore"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_SUPLIST_POSTGRES_PASSWORD", "postgres"));
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor WHERE topic_id IN "
                + "(SELECT id FROM thesis.thesis_topic WHERE created_by LIKE 'suplist-it-%')");
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE created_by LIKE 'suplist-it-%'");
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name LIKE 'suplist-it-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'suplist-it-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'suplist-it-%'");
    }

    @Test
    void listResolvesWholePageWithOneBatchedSupervisorRead() {
        UUID roundId = insertRound();
        UUID alpha = insertTopic(roundId, "IT alpha topic");
        UUID beta = insertTopic(roundId, "IT beta topic");
        ensureLecturer("suplist-it-shared", "An", "Nguyen");
        ensureLecturer("suplist-it-second", "Bao", "Tran");
        insertSupervisor(alpha, "suplist-it-second", 2);
        insertSupervisor(alpha, "suplist-it-shared", 1);
        insertSupervisor(beta, "suplist-it-shared", 1);
        clearInvocations(namedJdbc);

        List<TopicResponse> page = topics.list(roundId, null, studentJwt());

        assertThat(page).hasSize(2);
        TopicResponse first = page.get(0);
        assertThat(first.id()).isEqualTo(alpha);
        assertThat(first.supervisors()).extracting("lecturerId")
                .containsExactly("suplist-it-shared", "suplist-it-second");
        assertThat(first.supervisors().get(0).firstName()).isEqualTo("An");
        assertThat(first.supervisors().get(0).lastName()).isEqualTo("Nguyen");
        assertThat(page.get(1).supervisors()).extracting("lecturerId")
                .containsExactly("suplist-it-shared");
        verify(namedJdbc, times(1)).query(
                ArgumentMatchers.contains("FROM thesis.thesis_topic_supervisor supervisor"),
                any(SqlParameterSource.class),
                ArgumentMatchers.<ResultSetExtractor<?>>any());
    }

    private UUID insertRound() {
        UUID roundId = UUID.randomUUID();
        Instant start = Instant.now().minusSeconds(3_600);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                roundId, "suplist-it-round", "KLTN",
                Timestamp.from(start.minusSeconds(7_200)), Timestamp.from(start.minusSeconds(3_600)),
                Timestamp.from(start), Timestamp.from(start.plusSeconds(31L * 24 * 60 * 60)),
                "REGISTRATION_OPEN");
        return roundId;
    }

    private UUID insertTopic(UUID roundId, String title) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                topicId, roundId, "department-demo", title, "Topic description", 2,
                "PUBLISHED", "suplist-it-owner");
        return topicId;
    }

    private void insertSupervisor(UUID topicId, String lecturerId, int supervisorOrder) {
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), topicId, lecturerId, supervisorOrder);
    }

    private void ensureLecturer(String lecturerId, String firstName, String lastName) {
        String userId = lecturerId + "-user";
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', ?, ?, 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@campuscore.edu", firstName, lastName);
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") VALUES (?, ?, 'department-demo', ?, TRUE)",
                lecturerId, userId, "EMP-" + lecturerId);
    }

    private static Jwt studentJwt() {
        return Jwt.withTokenValue("it-token")
                .header("alg", "HS256")
                .subject("suplist-it-student")
                .claim("roles", List.of("STUDENT"))
                .build();
    }

    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_SUPLIST_POSTGRES_URL");
        return url != null && !url.isBlank();
    }
}
