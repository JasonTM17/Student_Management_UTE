package io.campuscore.restfulapi.thesis;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The catalog reads supervisor rosters through the topic list payload, so a
 * round page must never degenerate into one supervisor statement per topic.
 * The list projection carries names only; the email column stays with the
 * per-topic endpoint, which keeps its staff/owner authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_thesis_suplist;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisTopicSupervisorListTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @MockitoSpyBean
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'suplist-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'suplist-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"UserRole\" WHERE \"userId\" LIKE 'suplist-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'suplist-%'");
    }

    @Test
    void publishedTopicsCarryOrderedSupervisorRostersWithoutContactDetails() throws Exception {
        UUID roundId = insertRound();
        UUID topicAlpha = insertTopic(roundId, "Alpha topic");
        UUID topicBeta = insertTopic(roundId, "Beta topic");
        // Shared lecturer (An) on both topics; distinct partners prove the
        // roster is grouped per topic, and supervisor_order 2 stored before
        // order 1 proves the payload ordering is the stored ordering.
        ensureLecturer("suplist-lecturer-shared", "An", "Nguyễn");
        ensureLecturer("suplist-lecturer-alpha2", "Bảo", "Trần");
        ensureLecturer("suplist-lecturer-beta", "Chi", "Lê");
        insertSupervisor(topicAlpha, "suplist-lecturer-alpha2", 2);
        insertSupervisor(topicAlpha, "suplist-lecturer-shared", 1);
        insertSupervisor(topicBeta, "suplist-lecturer-beta", 1);
        insertSupervisor(topicBeta, "suplist-lecturer-shared", 2);

        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.student(jdbc, "suplist-student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(topicAlpha.toString()))
                .andExpect(jsonPath("$[0].supervisors.length()").value(2))
                .andExpect(jsonPath("$[0].supervisors[0].lecturerId").value("suplist-lecturer-shared"))
                .andExpect(jsonPath("$[0].supervisors[0].supervisorOrder").value(1))
                .andExpect(jsonPath("$[0].supervisors[0].firstName").value("An"))
                .andExpect(jsonPath("$[0].supervisors[0].lastName").value("Nguyễn"))
                .andExpect(jsonPath("$[0].supervisors[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].supervisors[1].lecturerId").value("suplist-lecturer-alpha2"))
                .andExpect(jsonPath("$[0].supervisors[1].supervisorOrder").value(2))
                .andExpect(jsonPath("$[1].id").value(topicBeta.toString()))
                .andExpect(jsonPath("$[1].supervisors.length()").value(2))
                .andExpect(jsonPath("$[1].supervisors[0].lecturerId").value("suplist-lecturer-beta"))
                .andExpect(jsonPath("$[1].supervisors[1].lecturerId").value("suplist-lecturer-shared"))
                .andExpect(jsonPath("$[1].supervisors[1].email").doesNotExist());
    }

    @Test
    void draftTopicsAndTheirSupervisorsNeverLeakIntoTheStudentCatalog() throws Exception {
        UUID roundId = insertRound();
        UUID published = insertTopic(roundId, "Visible topic");
        UUID draft = insertTopic(roundId, "Hidden draft");
        jdbc.update("UPDATE thesis.thesis_topic SET status = 'DRAFT' WHERE id = ?", draft);
        ensureLecturer("suplist-lecturer-hidden", "Hidden", "Supervisor");
        ensureLecturer("suplist-lecturer-open", "Open", "Supervisor");
        insertSupervisor(draft, "suplist-lecturer-hidden", 1);
        insertSupervisor(published, "suplist-lecturer-open", 1);

        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.student(jdbc, "suplist-student-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].id", not(hasItem(draft.toString()))))
                .andExpect(jsonPath("$[0].supervisors.length()").value(1))
                .andExpect(jsonPath("$[0].supervisors[0].lecturerId").value("suplist-lecturer-open"));
    }

    @Test
    void aFiftyTopicRoundCostsExactlyOneSupervisorStatement() throws Exception {
        UUID roundId = insertRound();
        ensureLecturer("suplist-lecturer-bulk", "Bulk", "Advisor");
        for (int i = 0; i < 50; i++) {
            UUID topicId = insertTopic(roundId, "Bulk topic " + i);
            insertSupervisor(topicId, "suplist-lecturer-bulk", 1);
        }
        Mockito.clearInvocations(namedJdbc);

        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.student(jdbc, "suplist-student-3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50))
                .andExpect(jsonPath("$[0].supervisors[0].lecturerId").value("suplist-lecturer-bulk"));

        // One IN-clause read resolves the whole page; the defect this guards
        // against is a query (or HTTP request) per topic row.
        Mockito.verify(namedJdbc, Mockito.times(1)).query(
                org.mockito.ArgumentMatchers.contains("FROM thesis.thesis_topic_supervisor supervisor"),
                org.mockito.ArgumentMatchers.any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<?>>any());
    }

    @Test
    void anonymousCallersCannotReadTheCatalog() throws Exception {
        UUID roundId = insertRound();
        mvc.perform(get("/api/v1/thesis/topics").queryParam("roundId", roundId.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void topicWithoutSupervisorsReturnsAnEmptyRoster() throws Exception {
        UUID roundId = insertRound();
        UUID topicId = insertTopic(roundId, "Unstaffed topic");

        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.student(jdbc, "suplist-student-4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(topicId.toString()))
                .andExpect(jsonPath("$[0].supervisors").isArray())
                .andExpect(jsonPath("$[0].supervisors.length()").value(0));
    }

    private UUID insertRound() {
        UUID roundId = UUID.randomUUID();
        Instant start = Instant.now().minusSeconds(3_600);
        Instant end = start.plusSeconds(31L * 24 * 60 * 60);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                roundId,
                "Supervisor list round",
                "KLTN",
                Timestamp.from(start.minusSeconds(7_200)),
                Timestamp.from(start.minusSeconds(3_600)),
                Timestamp.from(start),
                Timestamp.from(end),
                "REGISTRATION_OPEN");
        return roundId;
    }

    private UUID insertTopic(UUID roundId, String title) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                topicId,
                roundId,
                "department-demo",
                title,
                "Topic description",
                2,
                "PUBLISHED",
                "suplist-owner");
        return topicId;
    }

    private void insertSupervisor(UUID topicId, String lecturerId, int supervisorOrder) {
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, ?, ?)",
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
}
