package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import io.campuscore.restfulapi.academic.registration.renderer.RegistrationPdfRenderer;
import io.campuscore.restfulapi.academic.registration.service.RegistrationService;
import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
import io.campuscore.restfulapi.testsupport.CurrentAccountJwt;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:enrollment_mutation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AcademicEnrollmentMutationPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Autowired
    private RegistrationService registrationService;

    @MockitoSpyBean
    private RegistrationPdfRenderer pdfRenderer;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        // Credit-limit review records the admin audit trail (two-write rule):
        // without this DDL the review endpoint 500s in this standalone fixture.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_audit\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS campuscore_audit."AdminAudit" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "actorId" VARCHAR(120),
                    "actorLabel" VARCHAR(240),
                    "action" VARCHAR(48) NOT NULL,
                    "entityType" VARCHAR(48) NOT NULL,
                    "entityId" VARCHAR(120),
                    "summary" VARCHAR(500),
                    "beforeState" TEXT,
                    "afterState" TEXT,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        createTables();
        AccountAuthorityFixture.ensureSchema(jdbc);
        clearTables();
        insertFixture();
    }

    @Test
    void studentCanReadCurriculumPlanForOwnSemester() throws Exception {
        seedLecturerAndCurriculumCourses();
        prepareSecondYearCurriculumTerm();
        mvc.perform(get("/api/v1/me/registration/curriculum-plan")
                        .param("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value("semester-1"))
                .andExpect(jsonPath("$.term.status").value("RESOLVED"))
                .andExpect(jsonPath("$.term.curriculumYear").value(2))
                .andExpect(jsonPath("$.term.curriculumSemester").value(1))
                .andExpect(jsonPath("$.courses[0].courseCode").value("SE402"))
                .andExpect(jsonPath("$.courses[0].state").value("REGISTERABLE"))
                .andExpect(jsonPath("$.courses[0].proposedSection.id").value("section-open"));
    }

    @Test
    void curriculumTermUnknownCanBeManuallySelectedAndSummerMappingIsExplicit() throws Exception {
        seedLecturerAndCurriculumCourses();
        prepareSecondYearCurriculumTerm();
        jdbc.update("UPDATE \"academic\".\"Student\" SET \"admissionDate\"=? WHERE \"id\"=?",
                localDateTime(Instant.parse("2024-09-01T00:00:00Z")), "student-1");

        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.term.reasonCode").value("ADMISSION_YEAR_UNRESOLVED"))
                .andExpect(jsonPath("$.courses").isEmpty());

        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                        .param("curriculumYear", "2").param("curriculumSemester", "1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term.status").value("MANUAL"))
                .andExpect(jsonPath("$.courses[0].courseCode").value("SE402"));

        jdbc.update("UPDATE \"academic\".\"Student\" SET \"admissionDate\"=? WHERE \"id\"=?",
                localDateTime(Instant.parse("2025-09-01T00:00:00Z")), "student-1");
        LocalDateTime overlapStart = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime overlapEnd = LocalDateTime.of(2027, 1, 1, 0, 0);
        jdbc.update("INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\",\"year\",\"startDate\",\"endDate\",\"isCurrent\",\"createdAt\",\"updatedAt\")"
                        + " VALUES (?,?,?,?,?,?,?)",
                "ay-overlap", 2025, overlapStart, overlapEnd, false, overlapStart, overlapStart);
        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.term.reasonCode").value("ADMISSION_YEAR_UNRESOLVED"));
        jdbc.update("DELETE FROM \"academic\".\"AcademicYear\" WHERE \"id\"=?", "ay-overlap");

        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"type\"='SUMMER' WHERE \"id\"=?", "semester-1");
        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term.status").value("RESOLVED"))
                .andExpect(jsonPath("$.term.curriculumYear").value(2))
                .andExpect(jsonPath("$.term.curriculumSemester").value(3))
                .andExpect(jsonPath("$.courses").isEmpty());

        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"type\"='MYSTERY' WHERE \"id\"=?", "semester-1");
        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.term.reasonCode").value("SEMESTER_TYPE_UNKNOWN"));

        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"type\"='SPRING' WHERE \"id\"=?", "semester-1");
        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term.status").value("RESOLVED"))
                .andExpect(jsonPath("$.term.curriculumSemester").value(2));
    }

    @Test
    void curriculumPlanReportsAvailabilityPrerequisitesScheduleConflictsAndPassingGrades() throws Exception {
        seedLecturerAndCurriculumCourses();
        prepareSecondYearCurriculumTerm();

        jdbc.update("UPDATE \"academic\".\"Section\" SET \"capacity\"=1, \"enrolledCount\"=1 WHERE \"id\"=?",
                "section-open");
        planCourse().andExpect(jsonPath("$.courses[0].state").value("FULL"))
                .andExpect(jsonPath("$.courses[0].reasonCode").value("SECTION_FULL"));

        jdbc.update("UPDATE \"academic\".\"Section\" SET \"status\"='CLOSED' WHERE \"id\"=?", "section-open");
        planCourse().andExpect(jsonPath("$.courses[0].state").value("NO_OPEN_SECTION"));

        jdbc.update("UPDATE \"academic\".\"Section\" SET \"status\"='OPEN', \"capacity\"=35, \"enrolledCount\"=0"
                + " WHERE \"id\"=?", "section-open");
        insertPlanEnrollment("active-overlap", "course-overlap", "section-overlap", "ENROLLED", "NOT_GRADED", null);
        planCourse().andExpect(jsonPath("$.courses[0].state").value("CONFLICT"))
                .andExpect(jsonPath("$.courses[0].reasonCode").value("SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.courses[0].sections[0].scheduleConflict").value(true));
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\" WHERE \"id\"=?", "active-overlap");

        jdbc.update("INSERT INTO \"academic\".\"CourseRequirement\""
                + " (\"id\",\"courseId\",\"requiredCourseId\",\"kind\",\"minLetterGrade\") VALUES (?,?,?,?,?)",
                "required-course-open", "course-open", "course-two", "PREREQ", null);
        planCourse().andExpect(jsonPath("$.courses[0].state").value("BLOCKED_PREREQUISITE"))
                .andExpect(jsonPath("$.courses[0].reasonCode").value("PREREQUISITE_UNMET"));
        jdbc.update("DELETE FROM \"academic\".\"CourseRequirement\" WHERE \"id\"=?", "required-course-open");

        insertPlanEnrollment("passed-course", "course-open", "section-open", "COMPLETED", "PUBLISHED", "C");
        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses").isEmpty());
        jdbc.update("UPDATE \"academic\".\"Enrollment\" SET \"letterGrade\"='F' WHERE \"id\"=?", "passed-course");
        planCourse().andExpect(jsonPath("$.courses[0].state").value("REGISTERABLE"));
        jdbc.update("UPDATE \"academic\".\"Enrollment\" SET \"letterGrade\"='W' WHERE \"id\"=?", "passed-course");
        planCourse().andExpect(jsonPath("$.courses[0].state").value("REGISTERABLE"));
    }

    @Test
    void curriculumRegistrationPartiallySucceedsWhenTheExistingCreditCapBlocksAnotherCourse() throws Exception {
        seedLecturerAndCurriculumCourses();
        prepareSecondYearCurriculumTerm();
        insertCurriculumCourse("curriculum-course-second-mandatory", "curriculum-1", "course-overlap", true);
        jdbc.update("UPDATE \"academic\".\"SectionSchedule\" SET \"dayOfWeek\"=4 WHERE \"id\"=?", "sched-overlap");
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\"=3 WHERE \"id\"=?", "round-open");

        mvc.perform(post("/api/v1/me/registration/curriculum-plan/register")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"semesterId\":\"semester-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.courses[0].state").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.courses[1].state").value("CONFLICT"))
                .andExpect(jsonPath("$.courses[1].reasonCode").value("CREDIT_CAP_EXCEEDED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"Enrollment\""
                + " WHERE \"studentId\"=? AND \"status\"='ENROLLED'", Integer.class, "student-1")).isEqualTo(1);
    }

    @Test
    void curriculumEndpointsEnforceStudentAndAcademicAffairsRoles() throws Exception {
        mvc.perform(get("/api/v1/me/registration/curriculum-plan")
                        .param("semesterId", "semester-1").with(adminJwt()))
                .andExpect(status().isForbidden());
        AccountAuthorityFixture.ensureUser(jdbc, "lecturer-user-curriculum");
        AccountAuthorityFixture.replaceRoles(jdbc, "lecturer-user-curriculum", "LECTURER");
        mvc.perform(get("/api/v1/me/registration/curriculum-plan")
                        .param("semesterId", "semester-1")
                        .with(CurrentAccountJwt.currentJwt(jdbc, "lecturer-user-curriculum")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/registration/curriculum-defaults/dry-run")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"round-open\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/registration/curriculum-defaults/runs")
                        .with(CurrentAccountJwt.currentJwt(jdbc, "lecturer-user-curriculum")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/registration/curriculum-defaults/apply")
                        .with(CurrentAccountJwt.currentJwt(jdbc, "lecturer-user-curriculum"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roundId\":\"round-open\"}"))
                .andExpect(status().isForbidden());

        AccountAuthorityFixture.ensureUser(jdbc, "other-user-curriculum");
        AccountAuthorityFixture.replaceRoles(jdbc, "other-user-curriculum", "USER");
        var otherUser = CurrentAccountJwt.currentJwt(jdbc, "other-user-curriculum");
        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1").with(otherUser))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/registration/curriculum-defaults/runs").with(otherUser))
                .andExpect(status().isForbidden());

        AccountAuthorityFixture.ensureUser(jdbc, "superadmin-user-curriculum");
        AccountAuthorityFixture.replaceRoles(jdbc, "superadmin-user-curriculum", "SUPER_ADMIN");
        mvc.perform(get("/api/v1/admin/registration/curriculum-defaults/runs")
                        .with(CurrentAccountJwt.currentJwt(jdbc, "superadmin-user-curriculum")))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions planCourse() throws Exception {
        return mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                .with(studentJwt("student-user-1", "student-1"))).andExpect(status().isOk());
    }

    private void insertPlanEnrollment(String id, String courseId, String sectionId, String status,
            String gradeStatus, String letterGrade) {
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\",\"studentId\",\"sectionId\",\"semesterId\",\"status\",\"enrolledAt\","
                        + " \"gradeStatus\",\"letterGrade\",\"courseId\",\"roundId\",\"creditsSnapshot\","
                        + " \"registrationSource\",\"version\") VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,?,?,?,?,?,?,0)",
                id, "student-1", sectionId, "semester-1", status, gradeStatus, letterGrade, courseId,
                "round-open", 3, null);
    }

    @Test
    void studentCurriculumRegistrationIsIdempotentAndDroppedDefaultStaysDismissed() throws Exception {
        seedLecturerAndCurriculumCourses();
        prepareSecondYearCurriculumTerm();
        String body = "{\"semesterId\":\"semester-1\"}";

        MvcResult first = mvc.perform(post("/api/v1/me/registration/curriculum-plan/register")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.courses[0].courseCode").value("SE402"))
                .andReturn();
        String enrollmentId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("courses").get(0).path("enrollmentId").asText();
        assertThat(jdbc.queryForObject("SELECT \"registrationSource\" FROM \"academic\".\"Enrollment\" WHERE \"id\"=?",
                String.class, enrollmentId)).isEqualTo("CURRICULUM_DEFAULT");

        mvc.perform(post("/api/v1/me/registration/curriculum-plan/register")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.courses[0].state").value("ALREADY_REGISTERED"));

        mvc.perform(post("/api/v1/me/enrollments/" + enrollmentId + "/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-curriculum-default-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/registration/curriculum-plan").param("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].state").value("DISMISSED"));
        mvc.perform(post("/api/v1/me/registration/curriculum-plan/register")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredCount").value(0))
                .andExpect(jsonPath("$.courses[0].state").value("DISMISSED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"Enrollment\" WHERE \"studentId\"=?",
                Integer.class, "student-1")).isEqualTo(1);
    }

    @Test
    void adminCurriculumDryRunDoesNotWriteAndApplyAuditsAnIdempotentRun() throws Exception {
        seedLecturerAndCurriculumCourses();
        prepareSecondYearCurriculumTerm();
        insertUnknownActiveStudentForAdmin();
        String body = "{\"roundId\":\"round-open\",\"semesterId\":\"semester-1\",\"batchSize\":50}";

        mvc.perform(post("/api/v1/admin/registration/curriculum-defaults/dry-run")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.students").value(2))
                .andExpect(jsonPath("$.courses").value(1))
                .andExpect(jsonPath("$.wouldRegister").value(1))
                .andExpect(jsonPath("$.registered").value(0))
                .andExpect(jsonPath("$.failedStudents").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"Enrollment\"", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"action\"='CURRICULUM_DEFAULT_REGISTRATION_APPLY'", Integer.class)).isZero();

        mvc.perform(post("/api/v1/admin/registration/curriculum-defaults/apply")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.registered").value(1))
                .andExpect(jsonPath("$.skipped").value(0));
        mvc.perform(post("/api/v1/admin/registration/curriculum-defaults/apply")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"Enrollment\"", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"action\"='CURRICULUM_DEFAULT_REGISTRATION_APPLY'", Integer.class)).isEqualTo(2);
    }

    private void prepareSecondYearCurriculumTerm() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        jdbc.update("INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\",\"year\",\"startDate\",\"endDate\",\"isCurrent\",\"createdAt\",\"updatedAt\")"
                        + " VALUES (?,?,?,?,?,?,?)",
                "ay-2025", 2025, start, localDateTime(BASE_TIME), false, start, start);
        jdbc.update("UPDATE \"academic\".\"Student\" SET \"admissionDate\"=? WHERE \"id\"=?",
                LocalDateTime.of(2025, 9, 1, 0, 0), "student-1");
    }

    private void insertUnknownActiveStudentForAdmin() {
        LocalDateTime admissionDate = LocalDateTime.of(2024, 9, 1, 0, 0);
        jdbc.update("INSERT INTO \"academic\".\"Student\""
                        + " (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\",\"status\","
                        + " \"admissionDate\",\"createdAt\",\"updatedAt\") VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                "student-0-unknown", "student-user-unknown", "S000", "curriculum-1", 2,
                admissionDate, admissionDate, admissionDate);
    }

    @Test
    void studentEnrollThenDropPersistsRegisteredThenDroppedState() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-open-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-open"))
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andReturn();

        JsonNode body = objectMapper.readTree(enrolled.getResponse().getContentAsString());
        String enrollmentId = body.get("id").asText();
        assertThat(enrollmentId).isNotBlank();

        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class,
                        enrollmentId))
                .isEqualTo("ENROLLED");
        assertThat(jdbc.queryForObject(
                        "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                        Integer.class,
                        "section-open"))
                .isEqualTo(1);
        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsUsed").value(3))
                .andExpect(jsonPath("$.minCredits").value(14))
                .andExpect(jsonPath("$.belowMinimum").value(true));

        mvc.perform(get("/api/v1/enrollments/my").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(enrollmentId))
                .andExpect(jsonPath("$[0].status").value("ENROLLED"));

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-open-1"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class,
                        enrollmentId))
                .isEqualTo("DROPPED");
        assertThat(jdbc.queryForObject(
                        "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                        Integer.class,
                        "section-open"))
                .isEqualTo(0);

        mvc.perform(get("/api/v1/enrollments/my").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(enrollmentId))
                .andExpect(jsonPath("$[0].status").value("DROPPED"));
        assertThat(jdbc.queryForObject(
                "SELECT \"gradeStatus\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                String.class,
                enrollmentId)).isEqualTo("NOT_GRADED");
        assertThat(jdbc.queryForObject(
                "SELECT \"letterGrade\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                String.class,
                enrollmentId)).isNull();
        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsUsed").value(0));
    }

    @Test
    void eligibilityRejectsArchivedSemesterEvenWhenRegistrationRoundIsOpen() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"status\" = 'ARCHIVED' WHERE \"id\" = 'semester-1'");

        mvc.perform(get("/api/v1/me/registration/eligibility")
                        .with(studentJwt("student-user-1", "student-1"))
                        .param("roundId", "round-open"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEMESTER_CLOSED"));
    }

    @Test
    void enrollPersistsRegistrationSlipWithSha256Header() throws Exception {
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "slip-enroll-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        MvcResult slip = mvc.perform(get("/api/v1/me/registration/slip")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Content-SHA256"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        String sha256 = slip.getResponse().getHeader("X-Content-SHA256");
        assertThat(sha256).isNotBlank().matches("[0-9a-f]{64}");
        assertThat(slip.getResponse().getContentAsByteArray().length).isGreaterThan(0);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ? AND TRIM(\"sha256\") = ?",
                        Integer.class,
                        "student-1",
                        sha256))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT TRIM(\"slipSha256\") FROM \"academic\".\"RegistrationIdempotency\" WHERE \"ownerId\" = ? AND \"idempotencyKey\" = ?",
                        String.class,
                        "student-1",
                        "slip-enroll-1"))
                .isEqualTo(sha256);

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ? AND TRIM(\"sha256\") = ?",
                        Integer.class,
                        "student-1",
                        sha256))
                .isEqualTo(1);
    }

    @Test
    void slipGeneratesFallbackWhenNoSlipRowExists() throws Exception {
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "slip-fallback-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());

        jdbc.update("DELETE FROM \"academic\".\"RegistrationSlip\"");

        mvc.perform(get("/api/v1/me/registration/slip")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Content-SHA256"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ?",
                        Integer.class,
                        "student-1"))
                .isEqualTo(1);
    }

    @Test
    void dropInvalidatesPersistedRegistrationSlip() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "slip-drop-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String enrollmentId = objectMapper.readTree(enrolled.getResponse().getContentAsString())
                .get("id").asText();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ?",
                        Integer.class,
                        "student-1"))
                .isEqualTo(1);

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "slip-drop-1"))
                .andExpect(status().isOk());

        // The persisted slip is a snapshot of active registrations; the drop
        // transaction must invalidate it so the next download cannot present
        // the dropped course as a valid enrollment.
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ?",
                        Integer.class,
                        "student-1"))
                .isEqualTo(0);

        mvc.perform(get("/api/v1/me/registration/slip")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminDeleteEnrollmentInvalidatesPersistedRegistrationSlip() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "slip-admin-delete-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String enrollmentId = objectMapper.readTree(enrolled.getResponse().getContentAsString())
                .get("id").asText();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ?",
                        Integer.class,
                        "student-1"))
                .isEqualTo(1);

        mvc.perform(delete("/api/v1/enrollments/" + enrollmentId).with(adminJwt()))
                .andExpect(status().isOk());

        // An admin hard-delete removes the enrollment without a drop event;
        // the persisted slip must be invalidated in the same transaction so
        // the deleted course cannot still appear as a valid registration.
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ?",
                        Integer.class,
                        "student-1"))
                .isEqualTo(0);
    }

    @Test
    void slipResolvesSemesterWithActiveEnrollmentOverLexicallyLaterHistory() throws Exception {
        // "semester-9" sorts after "semester-1" lexically; the default slip
        // lookup must resolve the semester holding active enrollments, not the
        // newest id across all history — otherwise the slip download 404s even
        // though a valid registration exists.
        seedActiveEnrollment("enrollment-slip-current", "section-open", "course-open");
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"letterGrade\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "enrollment-slip-history", "student-1", "section-open", "semester-9", "COMPLETED",
                localDateTime(BASE_TIME.minusSeconds(86400)), "PUBLISHED", "course-open", "B", 3, 0);

        mvc.perform(get("/api/v1/me/registration/slip")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Content-SHA256"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void prerequisiteGateBlocksRegistrationUntilTheRequiredCourseIsCompleted() throws Exception {
        // course-open (SE402) requires course-overlap (SE403) as a PREREQ.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-test-prereq', 'course-open', 'course-overlap', 'PREREQ', 'D')");

        // No completed SE403 pass: registration is refused with the contract code.
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-prereq-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_UNMET"));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"Enrollment\" WHERE \"sectionId\" = 'section-open'",
                        Integer.class))
                .isEqualTo(0);

        // A completed passing SE403 enrollment (any grade above F/W) unlocks it.
        seedCompletedEnrollment("enrollment-prereq-pass", "section-overlap", "course-overlap", "D");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-prereq-met")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void aGradedRowWithoutALetterDoesNotSatisfyThePrerequisiteGate() throws Exception {
        // A producer bug could publish a COMPLETED row without a letter; the
        // gate must require an actual passing letter, matching the knowledge
        // copy ("môn chưa có điểm công bố chưa được tính là đã hoàn thành").
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-test-null-letter', 'course-open', 'course-overlap', 'PREREQ', 'D')");
        seedCompletedEnrollment("enrollment-null-letter", "section-overlap", "course-overlap", null);

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-null-letter-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_UNMET"));

        // Completing with a real letter unlocks it.
        jdbc.update(
                "UPDATE \"academic\".\"Enrollment\" SET \"letterGrade\" = 'C' WHERE \"id\" = 'enrollment-null-letter'");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-null-letter-met")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void prerequisiteFloorRanksPassingMinusLetters() throws Exception {
        // Legacy rows can carry minus letters the 10-point scale never emits;
        // each of them still has a position on the grade scale.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-minus-floor', 'course-open', 'course-overlap', 'PREREQ', 'C')");
        seedCompletedEnrollment("enrollment-minus-letter", "section-overlap", "course-overlap", "C-");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-c-minus-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_UNMET"));

        jdbc.update(
                "UPDATE \"academic\".\"Enrollment\" SET \"letterGrade\" = 'B-' WHERE \"id\" = 'enrollment-minus-letter'");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-b-minus-met")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void corequisiteGateRequiresAConcurrentEnrollmentInThePairedCourse() throws Exception {
        // section-two (SE404) carries no schedules, so the paired enrollment
        // cannot trip the schedule-overlap conflict (409) instead of the gate.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-test-coreq', 'course-open', 'course-two', 'COREQ', null)");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-coreq-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COREQUISITE_UNMET"));

        // An ACTIVE concurrent enrollment in the paired course satisfies the coreq.
        seedActiveEnrollment("enrollment-coreq-active", "section-two", "course-two");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-coreq-met")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void droppingCorequisiteWhileDependentCourseIsEnrolledIsRejected() throws Exception {
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-test-coreq-drop', 'course-open', 'course-two', 'COREQ', null)");

        seedActiveEnrollment("enrollment-coreq-parent", "section-two", "course-two");

        MvcResult enrollResult = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-coreq-dep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String dependentEnrollmentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("id").asText();

        // Attempting to drop course-two (parent corequisite) while course-open is still active fails
        mvc.perform(post("/api/v1/enrollments/enrollment-coreq-parent/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-coreq-parent-blocked"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COREQUISITE_UNMET"));

        // Dropping the dependent course first succeeds
        mvc.perform(post("/api/v1/enrollments/" + dependentEnrollmentId + "/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-coreq-dep-ok"))
                .andExpect(status().isOk());

        // Now dropping the corequisite parent succeeds
        mvc.perform(post("/api/v1/enrollments/enrollment-coreq-parent/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-coreq-parent-ok"))
                .andExpect(status().isOk());
    }

    @Test
    void historicalPassDoesNotExemptAnActiveCorequisiteFromTheDropGuard() throws Exception {
        // course-open declares course-two as a concurrent COREQ.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-test-coreq-history', 'course-open', 'course-two', 'COREQ', null)");
        // A completed pass of course-two from an earlier semester exists, but
        // the COREQ contract requires CONCURRENT enrollment: the historical
        // pass must not exempt the still-active paired course from the drop
        // guard.
        seedCompletedEnrollment("enrollment-coreq-history", "section-two", "course-two", "B");
        seedActiveEnrollment("enrollment-coreq-parent", "section-two", "course-two");
        jdbc.update("UPDATE \"academic\".\"Section\" SET \"enrolledCount\" = 1 WHERE \"id\" = 'section-two'");

        // Enrolling the dependent succeeds because the paired course is active.
        MvcResult enrollResult = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-coreq-history-dep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String dependentEnrollmentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("id").asText();

        // Dropping the still-required paired course is refused despite the
        // historical pass.
        mvc.perform(post("/api/v1/enrollments/enrollment-coreq-parent/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-coreq-parent-history"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COREQUISITE_UNMET"));

        // The refused drop leaves the enrollment row and seat counter intact.
        String status = jdbc.queryForObject(
                "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enrollment-coreq-parent'",
                String.class);
        assertThat(status).isEqualTo("ENROLLED");
        Integer enrolledCount = jdbc.queryForObject(
                "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = 'section-two'",
                Integer.class);
        assertThat(enrolledCount).isEqualTo(1);

        // Order is still honored: drop the dependent first, then the parent.
        mvc.perform(post("/api/v1/enrollments/" + dependentEnrollmentId + "/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-coreq-history-dep"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/enrollments/enrollment-coreq-parent/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-coreq-parent-history-ok"))
                .andExpect(status().isOk());
    }

    @Test
    void legacyPrerequisiteSpellingDoesNotGateRegistrationAfterTheV97Normalization() throws Exception {
        // V97 normalizes seeded data to 'PREREQ'; the enforcement contract is
        // an exact match, so a row still spelled 'PREREQUISITE' (unmigrated
        // data) must NOT gate — this pins the exact-match contract the
        // migration relies on.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-test-legacy', 'course-open', 'course-overlap', 'PREREQUISITE', 'D')");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-legacy-kind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    /** Seeds one completed, passing enrollment used to satisfy a PREREQ check. */
    private void seedCompletedEnrollment(String id, String sectionId, String courseId, String letterGrade) {
        seedCompletedEnrollment(id, sectionId, courseId, "PUBLISHED", letterGrade);
    }

    /** Seeds a completed enrollment with a chosen grade release state for prerequisite checks. */
    private void seedCompletedEnrollment(
            String id, String sectionId, String courseId, String gradeStatus, String letterGrade) {
        LocalDateTime now = localDateTime(BASE_TIME);
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"letterGrade\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "student-1", sectionId, "semester-0", "COMPLETED", now,
                gradeStatus, courseId, letterGrade, 3, 0);
    }

    /** Seeds one active enrollment used to satisfy a COREQ check. */
    private void seedActiveEnrollment(String id, String sectionId, String courseId) {
        LocalDateTime now = localDateTime(BASE_TIME);
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "student-1", sectionId, "semester-1", "ENROLLED", now,
                "NOT_GRADED", courseId, 3, 0);
    }

    @Test
    void catalogReadFallsBackToOpenAddDropWhenRegistrationWindowExpired() throws Exception {
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"windowEnd\" = ? WHERE \"id\" = ?",
                localDateTime(Instant.parse("2020-01-01T00:00:00Z")), "round-open");

        mvc.perform(get("/api/v1/me/registration/sections")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("section-open"));
    }

    @Test
    void enrollFallsBackToOpenAddDropRoundWhenRegistrationClosed() throws Exception {
        // Registration window expired; the fixture's ADD_DROP round stays open
        // until 2030 — enrollment must still succeed inside it.
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"windowEnd\" = ? WHERE \"id\" = ?",
                localDateTime(Instant.parse("2020-01-01T00:00:00Z")), "round-open");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-add-drop-fallback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-open"))
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void inactiveCourseIsHiddenFromRegistrationCatalog() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Course\" SET \"isActive\" = FALSE WHERE \"id\" = 'course-open'");

        MvcResult catalog = mvc.perform(get("/api/v1/me/registration/sections")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sections = objectMapper.readTree(catalog.getResponse().getContentAsString());
        for (JsonNode section : sections) {
            assertThat(section.get("id").asText()).isNotEqualTo("section-open");
        }
    }

    @Test
    void inactiveCourseBlocksNewEnrollmentInAnOpenSection() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Course\" SET \"isActive\" = FALSE WHERE \"id\" = 'course-open'");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "inactive-course-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_INACTIVE"));
    }

    @Test
    void inactiveCourseDoesNotHideOrTrapAnExistingEnrollment() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-before-course-inactive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enrollmentId = objectMapper.readTree(enrolled.getResponse().getContentAsString()).get("id").asText();

        jdbc.update("UPDATE \"academic\".\"Course\" SET \"isActive\" = FALSE WHERE \"id\" = 'course-open'");

        mvc.perform(get("/api/v1/enrollments/my").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(enrollmentId))
                .andExpect(jsonPath("$[0].status").value("ENROLLED"));

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-after-course-inactive"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/enrollments/my").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(enrollmentId))
                .andExpect(jsonPath("$[0].status").value("DROPPED"));
    }

    @Test
    void registrationWindowIncludesBothEndpointsAcrossBangkokAndUtcMidnight() {
        Instant bangkokMidnight = LocalDateTime.of(2026, 1, 1, 0, 0)
                .atZone(ZoneId.of("Asia/Bangkok"))
                .toInstant();
        Instant utcMidnight = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(bangkokMidnight).isEqualTo(Instant.parse("2025-12-31T17:00:00Z"));

        Map<String, Object> round = Map.of(
                "window_start", Timestamp.from(bangkokMidnight),
                "window_end", Timestamp.from(utcMidnight));

        assertThat(Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(registrationService, "inWindow", round, bangkokMidnight))).isTrue();
        assertThat(Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(registrationService, "inWindow", round, utcMidnight))).isTrue();
        assertThat(Boolean.FALSE.equals(ReflectionTestUtils.invokeMethod(
                registrationService, "inWindow", round, bangkokMidnight.minusNanos(1)))).isTrue();
        assertThat(Boolean.FALSE.equals(ReflectionTestUtils.invokeMethod(
                registrationService, "inWindow", round, utcMidnight.plusNanos(1)))).isTrue();
    }

    @Test
    void adjacentRegistrationSchedulePeriodsDoNotConflictWhenEndEqualsStart() throws Exception {
        jdbc.update("UPDATE \"academic\".\"SectionSchedule\" SET \"startTime\" = '07:00', \"endTime\" = '09:00' WHERE \"id\" = 'sched-overlap'");
        jdbc.update("UPDATE \"academic\".\"SectionSchedule\" SET \"startTime\" = '09:00', \"endTime\" = '11:00' WHERE \"id\" = 'sched-open'");
        seedActiveEnrollment("enrollment-abutting", "section-overlap", "course-overlap");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-abutting-period")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-open"));
    }

    @Test
    void summerCreditCapFollowsTheConfiguredRegistrationRound() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"type\" = 'SUMMER' WHERE \"id\" = 'semester-1'");
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 8 WHERE \"id\" = 'round-open'");

        mvc.perform(get("/api/v1/me/registration/eligibility")
                        .with(studentJwt("student-user-1", "student-1"))
                        .param("roundId", "round-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(8));

        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 10 WHERE \"id\" = 'round-open'");
        mvc.perform(get("/api/v1/me/registration/eligibility")
                        .with(studentJwt("student-user-1", "student-1"))
                        .param("roundId", "round-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(10));
    }

    @Test
    void summerConfiguredCapAllowsTenCreditsAndRefusesEleven() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"type\" = 'SUMMER' WHERE \"id\" = 'semester-1'");
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 28 WHERE \"id\" = 'round-open'");
        jdbc.update("UPDATE \"academic\".\"Course\" SET \"credits\" = 10 WHERE \"id\" = 'course-open'");
        jdbc.update("UPDATE \"academic\".\"Course\" SET \"credits\" = 1 WHERE \"id\" = 'course-two'");

        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "summer-ten-credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(10))
                .andExpect(jsonPath("$.creditsUsed").value(10));

        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "summer-eleven-credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-two\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_CAP_EXCEEDED"));
    }

    @Test
    void summerCapUsesRegistrationSummerMaxCreditsProperty() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"type\" = 'SUMMER' WHERE \"id\" = 'semester-1'");
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 28 WHERE \"id\" = 'round-open'");
        int configuredMaximum = environment.getProperty("registration.summer-max-credits", Integer.class, 10);

        mvc.perform(get("/api/v1/me/registration/eligibility")
                        .with(studentJwt("student-user-1", "student-1"))
                        .param("roundId", "round-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(configuredMaximum));
    }

    @Test
    void approvedCreditLimitExceptionRetainsItsExistingSummerBehavior() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"type\" = 'SUMMER' WHERE \"id\" = 'semester-1'");
        jdbc.update(
                "INSERT INTO \"academic\".\"CreditLimitApplication\""
                        + " (\"id\", \"studentId\", \"semesterId\", \"roundId\", \"requestedLimit\","
                        + " \"reason\", \"status\", \"reviewedBy\", \"reviewedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?)",
                "summer-approved-limit",
                "student-1",
                "semester-1",
                "round-open",
                30,
                "Approved exception retained for summer behavior",
                "admin-user",
                localDateTime(BASE_TIME));

        mvc.perform(get("/api/v1/me/registration/eligibility")
                        .with(studentJwt("student-user-1", "student-1"))
                        .param("roundId", "round-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(30));
    }

    @Test
    void registrationSummaryAdvisesBelowMinimumOnlyBelowFourteenCredits() throws Exception {
        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minCredits").value(14))
                .andExpect(jsonPath("$.creditsUsed").value(0))
                .andExpect(jsonPath("$.belowMinimum").value(true));

        jdbc.update("UPDATE \"academic\".\"Course\" SET \"credits\" = 13 WHERE \"id\" = 'course-open'");
        seedActiveEnrollment("enrollment-minimum-below", "section-open", "course-open");
        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minCredits").value(14))
                .andExpect(jsonPath("$.creditsUsed").value(13))
                .andExpect(jsonPath("$.belowMinimum").value(true));

        jdbc.update("UPDATE \"academic\".\"Course\" SET \"credits\" = 14 WHERE \"id\" = 'course-open'");
        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minCredits").value(14))
                .andExpect(jsonPath("$.creditsUsed").value(14))
                .andExpect(jsonPath("$.belowMinimum").value(false));

        jdbc.update("UPDATE \"academic\".\"Course\" SET \"credits\" = 15 WHERE \"id\" = 'course-open'");
        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsUsed").value(15))
                .andExpect(jsonPath("$.belowMinimum").value(false));
    }

    @Test
    void summerSummaryOmitsTheRegularSemesterMinimumAndFirstRegistrationStillSucceeds() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Semester\" SET \"type\" = 'SUMMER' WHERE \"id\" = 'semester-1'");

        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minCredits").value(0))
                .andExpect(jsonPath("$.creditsUsed").value(0))
                .andExpect(jsonPath("$.belowMinimum").value(false));

        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "summer-first-registration-under-minimum")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minCredits").value(0))
                .andExpect(jsonPath("$.creditsUsed").value(3))
                .andExpect(jsonPath("$.belowMinimum").value(false));
    }

    @Test
    void prerequisiteRequiresOfficialPassingGradeAndAcceptsAppealedGradeAtTheFloor() throws Exception {
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-test-official-floor', 'course-open', 'course-overlap', 'PREREQ', 'B')");
        seedCompletedEnrollment(
                "enrollment-prereq-failed", "section-overlap", "course-overlap", "PUBLISHED", "F");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "prereq-failed-grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_UNMET"));

        jdbc.update("UPDATE \"academic\".\"Enrollment\" SET \"gradeStatus\" = 'DRAFT', \"letterGrade\" = 'B'"
                + " WHERE \"id\" = 'enrollment-prereq-failed'");
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "prereq-draft-grade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_UNMET"));

        jdbc.update("UPDATE \"academic\".\"Enrollment\" SET \"gradeStatus\" = 'APPEALED'"
                + " WHERE \"id\" = 'enrollment-prereq-failed'");
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "prereq-appealed-at-floor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void prerequisiteMinLetterGradeFloorBlocksBelowAndPassesAtFloor() throws Exception {
        // Requirement floor 'B': a D+ pass must NOT satisfy it.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-test-floor', 'course-open', 'course-overlap', 'PREREQ', 'B')");
        seedCompletedEnrollment("enrollment-floor-low", "section-overlap", "course-overlap", "D+");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-floor-low")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_UNMET"));

        // Raising the achieved letter to B+ clears the same gate.
        jdbc.update("UPDATE \"academic\".\"Enrollment\" SET \"letterGrade\" = 'B+' WHERE \"id\" = 'enrollment-floor-low'");
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-floor-pass")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    /**
     * Catalog v2: every section carries its schedules (day/time/room/lecturer)
     * and its curriculum relevance label. dayOfWeek is emitted verbatim from
     * the DB in the CampusCore convention (1=Chủ Nhật .. 7=Thứ Bảy); the fixture
     * seeds Monday as 2. Sections without schedules expose an empty list so the
     * FE renders "Chưa có lịch".
     */
    @Test
    void catalogReturnsSchedulesRoomLecturerAndCurriculumRelevance() throws Exception {
        seedLecturerAndCurriculumCourses();
        // A second weekly meeting on section-open proves multi-row fan-out
        // still groups into exactly one catalog entry.
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "sched-open-2", "section-open", "room-1", 4, "13:00", "15:00");

        mvc.perform(get("/api/v1/me/registration/sections")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // SE402 section-open: in curriculum as mandatory, two meetings with room + lecturer.
                .andExpect(jsonPath("$[0].id").value("section-open"))
                .andExpect(jsonPath("$[0].schedules.length()").value(2))
                .andExpect(jsonPath("$[0].schedules[0].dayOfWeek").value(2))
                .andExpect(jsonPath("$[0].schedules[0].startTime").value("07:00"))
                .andExpect(jsonPath("$[0].schedules[0].endTime").value("09:30"))
                .andExpect(jsonPath("$[0].schedules[0].room").value("A 101"))
                .andExpect(jsonPath("$[0].schedules[0].lecturer").value("Nguyen Minh"))
                .andExpect(jsonPath("$[0].schedules[1].dayOfWeek").value(4))
                .andExpect(jsonPath("$[0].schedules[1].startTime").value("13:00"))
                .andExpect(jsonPath("$[0].curriculumRelevance").value("MANDATORY"))
                // SE403 section-overlap: elective in curriculum, schedule without lecturer.
                .andExpect(jsonPath("$[1].id").value("section-overlap"))
                .andExpect(jsonPath("$[1].schedules.length()").value(1))
                .andExpect(jsonPath("$[1].schedules[0].dayOfWeek").value(2))
                .andExpect(jsonPath("$[1].schedules[0].lecturer").value(Matchers.nullValue()))
                .andExpect(jsonPath("$[1].curriculumRelevance").value("ELECTIVE"))
                // SE404 section-two: absent from the curriculum, no schedules at all.
                .andExpect(jsonPath("$[2].id").value("section-two"))
                .andExpect(jsonPath("$[2].schedules.length()").value(0))
                .andExpect(jsonPath("$[2].curriculumRelevance").value("OUTSIDE"));
    }

    /** Seeds one assigned lecturer and one mandatory/elective curriculum pair for the relevance test. */
    private void seedLecturerAndCurriculumCourses() {
        LocalDateTime now = localDateTime(BASE_TIME);
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                "lecturer-user-1",
                "lecturer1@campuscore.edu",
                "Minh",
                "Nguyen");
        jdbc.update(
                "INSERT INTO \"academic\".\"Lecturer\""
                        + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") VALUES (?, ?, ?, ?, ?)",
                "lecturer-1",
                "lecturer-user-1",
                "department-1",
                "GV001",
                true);
        jdbc.update("UPDATE \"academic\".\"Section\" SET \"lecturerId\" = ? WHERE \"id\" = ?",
                "lecturer-1", "section-open");
        insertCurriculumCourse("curriculum-course-mandatory", "curriculum-1", "course-open", true);
        insertCurriculumCourse("curriculum-course-elective", "curriculum-1", "course-overlap", false);
    }

    private void insertCurriculumCourse(String id, String curriculumId, String courseId, boolean mandatory) {
        jdbc.update(
                "INSERT INTO \"academic\".\"CurriculumCourse\""
                        + " (\"id\", \"curriculumId\", \"courseId\", \"year\", \"semester\", \"isMandatory\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                curriculumId,
                courseId,
                2,
                1,
                mandatory);
    }

    /**
     * Verifies that ten independent students racing for one two-seat section
     * are serialized by the section lock: exactly two requests enroll and the
     * remaining requests receive a business conflict without duplicate rows.
     */
    @Test
    void tenStudentsCompetingForSameSectionAreSerializedBySectionLock() throws Exception {
        final int studentCount = 10;
        final int capacity = 2;
        insertConcurrentStudents(studentCount);
        jdbc.update("UPDATE \"academic\".\"Section\" SET \"capacity\" = ? WHERE \"id\" = ?",
                capacity, "section-open");

        ExecutorService executor = Executors.newFixedThreadPool(studentCount);
        CountDownLatch ready = new CountDownLatch(studentCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ConcurrentEnrollmentOutcome>> futures = new ArrayList<>();
        try {
            for (int index = 1; index <= studentCount; index++) {
                final int studentIndex = index;
                Callable<ConcurrentEnrollmentOutcome> task = () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        return new ConcurrentEnrollmentOutcome(studentIndex, -1, "start barrier timeout", null);
                    }
                    try {
                        MvcResult result = mvc.perform(post("/api/v1/me/enrollments")
                                        .with(studentJwt("student-user-" + studentIndex, "student-" + studentIndex))
                                        .header("Idempotency-Key", "concurrent-enroll-" + studentIndex)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"sectionId\":\"section-open\"}"))
                                .andReturn();
                        return new ConcurrentEnrollmentOutcome(
                                studentIndex,
                                result.getResponse().getStatus(),
                                result.getResponse().getContentAsString(),
                                null);
                    } catch (Throwable failure) {
                        return new ConcurrentEnrollmentOutcome(studentIndex, -1, "", failure);
                    }
                };
                futures.add(executor.submit(task));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ConcurrentEnrollmentOutcome> outcomes = new ArrayList<>();
            for (Future<ConcurrentEnrollmentOutcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes).hasSize(studentCount);
            assertThat(outcomes.stream().filter(outcome -> outcome.status() == 200).count())
                    .isEqualTo(capacity);
            assertThat(outcomes.stream().filter(outcome -> outcome.status() == 409).count())
                    .isEqualTo(studentCount - capacity);
            assertThat(outcomes.stream().filter(outcome -> outcome.status() != 200 && outcome.status() != 409)
                    .map(ConcurrentEnrollmentOutcome::diagnostic)
                    .toList()).isEmpty();
            assertThat(outcomes.stream().filter(outcome -> outcome.failure() != null)
                    .map(ConcurrentEnrollmentOutcome::failure)
                    .toList()).isEmpty();

            assertThat(jdbc.queryForObject(
                            "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                            Integer.class,
                            "section-open"))
                    .isEqualTo(capacity);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM \"academic\".\"Enrollment\""
                                    + " WHERE \"sectionId\" = ? AND \"status\" = 'ENROLLED'",
                            Integer.class,
                            "section-open"))
                    .isEqualTo(capacity);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM (SELECT \"studentId\" FROM \"academic\".\"Enrollment\""
                                    + " WHERE \"sectionId\" = ? AND \"status\" = 'ENROLLED'"
                                    + " GROUP BY \"studentId\" HAVING COUNT(*) > 1) duplicates",
                            Integer.class,
                            "section-open"))
                    .isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void enrollmentRollsBackWhenRegistrationSlipCannotBeStored() throws Exception {
        doThrow(new IllegalStateException("renderer unavailable"))
                .when(pdfRenderer)
                .renderEnriched(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), org.mockito.ArgumentMatchers.anyList());

        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "slip-failure-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"Enrollment\" WHERE \"studentId\" = ?",
                        Integer.class,
                        "student-1"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                        Integer.class,
                        "section-open"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationIdempotency\" WHERE \"ownerId\" = ? AND \"idempotencyKey\" = ?",
                        Integer.class,
                        "student-1",
                        "slip-failure-1"))
                .isZero();
    }

    @Test
    void summaryRequiresAnActiveStudentProfile() throws Exception {
        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwtWithoutProfile()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_PROFILE_REQUIRED"));
    }

    @Test
    void missingIdempotencyKeyIsRejectedOnCanonicalAndAlias() throws Exception {
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutSecondActiveRow() throws Exception {
        MvcResult first = mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enrollmentId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        MvcResult second = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText())
                .isEqualTo(enrollmentId);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"Enrollment\" WHERE \"studentId\" = ? AND \"status\" = 'ENROLLED'",
                        Integer.class,
                        "student-1"))
                .isEqualTo(1);
    }

    @Test
    void overlappingScheduleIsConflictOnBothUrls() throws Exception {
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "overlap-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "overlap-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-overlap\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
    }

    @Test
    void enrollPathBillsCurrentCourseCreditsNotTheStaleSnapshot() throws Exception {
        // Regression for the WRITE path of the 15-vs-16 quota drift (Wukong
        // round-4): the student's existing enrollment carries a stale
        // 15-credit snapshot while the course now bills 16 credits. The cap
        // check inside enrollLocked must bill the current 16 — pre-fix it
        // summed the snapshot (15) and let 15+15=30 slip past a 30-credit cap
        // that the current basis (16+15=31) correctly rejects.
        LocalDateTime now = localDateTime(BASE_TIME);
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"credits\","
                        + " \"departmentId\", \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "course-stale-w", "SE416", "Stale Credits W", "Stale Credits W", "Stale Credits W", 16,
                "department-1", "semester-1", true, now, now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"capacity\","
                        + " \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "section-stale-w", "01", "course-stale-w", "semester-1", 40, 0,
                "OPEN", 0);
        // Existing enrollment: snapshot says 15, current course credits say 16.
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"roundId\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "enr-stale-w", "student-1", "section-open", "semester-1", "ENROLLED",
                now, "NONE", "course-open", "round-registration-demo", 15, 1);
        jdbc.update("UPDATE \"academic\".\"Course\" SET \"credits\" = 16 WHERE \"id\" = 'course-open'");
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 30 WHERE \"kind\" = 'REGISTRATION'");

        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "stale-w-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-stale-w\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_CAP_EXCEEDED"));
    }

    @Test
    void creditCapExceededOnCanonicalRoute() throws Exception {
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 2 WHERE \"kind\" = 'REGISTRATION'");
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "credit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_CAP_EXCEEDED"));
    }

    @Test
    void onlyAnApprovedApplicationRaisesOneStudentFromTwentyEightToThirtyCredits() throws Exception {
        LocalDateTime now = localDateTime(BASE_TIME);
        // The 28 used credits must come from the section's CURRENT course
        // credits (the quota basis since the stale-snapshot fix), so this test
        // enrolls the student in a dedicated 28-credit course.
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"credits\","
                        + " \"departmentId\", \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "course-cap", "SE405", "Capstone", "Capstone", "Capstone", 28,
                "department-1", "semester-1", true, now, now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"capacity\","
                        + " \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "section-cap", "01", "course-cap", "semester-1", 35, 0, "OPEN", 0);
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"roundId\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, 'ENROLLED', ?, 'NOT_GRADED', ?, ?, ?, 0)",
                "enrollment-standard-cap",
                "student-1",
                "section-cap",
                "semester-1",
                now,
                "course-cap",
                "round-open",
                28);

        mvc.perform(get("/api/v1/me/registration/eligibility")
                        .with(studentJwt("student-user-1", "student-1"))
                        .param("roundId", "round-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(28))
                .andExpect(jsonPath("$.creditsUsed").value(28))
                .andExpect(jsonPath("$.creditsRemaining").value(0));

        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "standard-cap-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-two\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_CAP_EXCEEDED"));

        MvcResult submitted = mvc.perform(post("/api/v1/me/registration/credit-limit-applications")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"round-open\",\"reason\":\"Tôi cần hoàn thành thêm học phần bắt buộc trong học kỳ cuối.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedLimit").value(30))
                .andReturn();
        String applicationId = objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/me/registration/credit-limit-applications")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"round-open\",\"reason\":\"Tôi cần hoàn thành thêm học phần bắt buộc trong học kỳ cuối.\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_APPLICATION_EXISTS"));

        mvc.perform(get("/api/v1/admin/registration/credit-limit-applications")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentCode").value("S001"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mvc.perform(post("/api/v1/admin/registration/credit-limit-applications/" + applicationId + "/review")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"note\":\"Đã đối chiếu hồ sơ và chấp thuận theo quy định.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value("admin-user"));

        mvc.perform(get("/api/v1/me/registration/eligibility")
                        .with(studentJwt("student-user-1", "student-1"))
                        .param("roundId", "round-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(30))
                .andExpect(jsonPath("$.creditsUsed").value(28))
                .andExpect(jsonPath("$.creditsRemaining").value(2));

        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "approved-cap-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-two\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-two"));
    }

    @Test
    void quotaCountsTheCurrentSectionCreditsNotTheEnrollmentSnapshot() throws Exception {
        // Regression for the 15-vs-16 quota drift: the course was raised from
        // 15 to 16 credits AFTER the student registered, so the enrollment row
        // still carries the stale 15-credit snapshot while the section's
        // current credits are 16. Eligibility (and the summary screen) must
        // bill the current 16 — the pre-fix creditsUsed() summed the snapshot
        // and reported 15, letting a student register past the credit cap.
        LocalDateTime now = localDateTime(BASE_TIME);
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"credits\","
                        + " \"departmentId\", \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "course-stale", "SE406", "Stale Credits", "Stale Credits", "Stale Credits", 16,
                "department-1", "semester-1", true, now, now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"capacity\","
                        + " \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "section-stale", "01", "course-stale", "semester-1", 35, 0, "OPEN", 0);
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"roundId\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, 'ENROLLED', ?, 'NOT_GRADED', ?, ?, ?, 0)",
                "enrollment-stale-credits",
                "student-1",
                "section-stale",
                "semester-1",
                now,
                "course-stale",
                "round-open",
                15);

        mvc.perform(get("/api/v1/me/registration/eligibility")
                        .with(studentJwt("student-user-1", "student-1"))
                        .param("roundId", "round-open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsUsed").value(16))
                .andExpect(jsonPath("$.creditsRemaining").value(12));

        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsUsed").value(16))
                .andExpect(jsonPath("$.creditsRemaining").value(12));
    }

    @Test
    void pendingApplicationCanBeReviewedAfterTheRoundCloses() throws Exception {
        MvcResult submitted = mvc.perform(post("/api/v1/me/registration/credit-limit-applications")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"round-open\",\"reason\":\"Tôi cần hoàn thành thêm học phần bắt buộc trong học kỳ cuối.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String applicationId = objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText();

        // The round closes with the application still PENDING.
        jdbc.update(
                "UPDATE \"academic\".\"RegistrationRound\" SET \"status\" = 'CLOSED', \"windowEnd\" = ? WHERE \"id\" = ?",
                localDateTime(Instant.parse("2026-08-19T00:00:00Z")),
                "round-open");

        // Reviewing is a pure state transition on the submitted item and must
        // stay possible after close (pre-fix this answered 409 WINDOW_CLOSED
        // and the application was stuck forever).
        mvc.perform(post("/api/v1/admin/registration/credit-limit-applications/" + applicationId + "/review")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"note\":\"Đã đối chiếu hồ sơ sau khi vòng đăng ký đóng.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // ... and the round must not be reopened as a side effect.
        assertThat(jdbc.queryForObject(
                "SELECT \"status\" FROM \"academic\".\"RegistrationRound\" WHERE \"id\" = ?",
                String.class,
                "round-open"))
                .isEqualTo("CLOSED");
    }

    @Test
    void adminApplicationListingIsBounded() throws Exception {
        for (int index = 0; index < 505; index++) {
            jdbc.update(
                    "INSERT INTO \"academic\".\"CreditLimitApplication\""
                            + " (\"id\", \"studentId\", \"semesterId\", \"roundId\", \"requestedLimit\", \"reason\", \"status\")"
                            + " VALUES (?, 'student-1', 'semester-1', 'round-open', 30, ?, 'PENDING')",
                    "cl-bulk-" + index,
                    "Cần đăng ký vượt định mức vì thiếu tín chỉ tốt nghiệp, đã được cố vấn học tập xác nhận.");
        }

        mvc.perform(get("/api/v1/admin/registration/credit-limit-applications")
                        .queryParam("status", "ALL")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                // The console feed is newest-first and capped at 500 rows
                // (pre-fix the listing was unbounded).
                .andExpect(jsonPath("$.length()").value(500));
    }

    @Test
    void adminDropCompletesIdempotencySoRetriesReplayInsteadOfConflicting() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "admin-drop-enroll-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andReturn();
        String enrollmentId = objectMapper.readTree(enrolled.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(adminJwt())
                        .header("Idempotency-Key", "admin-drop-1"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT \"state\" FROM \"academic\".\"RegistrationIdempotency\" WHERE \"ownerId\" = ? AND \"idempotencyKey\" = ?",
                        String.class,
                        enrollmentId,
                        "admin-drop-1"))
                .isEqualTo("COMPLETED");

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(adminJwt())
                        .header("Idempotency-Key", "admin-drop-1"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class,
                        enrollmentId))
                .isEqualTo("DROPPED");
        // The admin actor is the JWT subject (no student profile claim), the
        // event still points at the enrollment owner.
        assertThat(jdbc.queryForObject(
                        "SELECT \"actorId\" FROM \"academic\".\"EnrollmentEvent\""
                                + " WHERE \"enrollmentId\" = ? AND \"action\" = 'DROP'",
                        String.class,
                        enrollmentId))
                .isEqualTo("admin-user");
    }

    @Test
    void dropWritesEnrollmentEventForStudentCaller() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "self-drop-enroll-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enrollmentId = objectMapper.readTree(enrolled.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "self-drop-1"))
                .andExpect(status().isOk());

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT \"action\", \"actorId\", \"studentId\", \"sectionId\", \"requestHash\""
                        + " FROM \"academic\".\"EnrollmentEvent\""
                        + " WHERE \"enrollmentId\" = ? AND \"action\" = 'DROP'",
                enrollmentId);
        assertThat(event.get("actorId")).isEqualTo("student-1");
        assertThat(event.get("studentId")).isEqualTo("student-1");
        assertThat(event.get("sectionId")).isEqualTo("section-open");
        assertThat(event.get("requestHash")).isNotNull();
    }

    @Test
    void dropAndEnrollRejectCallerWithoutStudentProfile() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "orphan-drop-enroll-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enrollmentId = objectMapper.readTree(enrolled.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwtWithoutProfile())
                        .header("Idempotency-Key", "orphan-enroll-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_PROFILE_REQUIRED"));

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(studentJwtWithoutProfile())
                        .header("Idempotency-Key", "orphan-drop-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_PROFILE_REQUIRED"));
    }

    /**
     * Adds the additional authenticated student profiles used by the
     * concurrent-registration regression without changing the shared fixture.
     */
    private void insertConcurrentStudents(int studentCount) {
        LocalDateTime now = localDateTime(BASE_TIME);
        for (int index = 2; index <= studentCount; index++) {
            jdbc.update(
                    "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\")"
                            + " VALUES (?, ?, ?, ?)",
                    "student-user-" + index,
                    "student" + index + "@campuscore.edu",
                    "Concurrent",
                    "Student " + index);
            jdbc.update(
                    "INSERT INTO \"academic\".\"Student\""
                            + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                            + " \"admissionDate\", \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "student-" + index,
                    "student-user-" + index,
                    String.format("S%03d", index),
                    "curriculum-1",
                    2,
                    "ACTIVE",
                    now,
                    now,
                    now);
            AccountAuthorityFixture.student(jdbc, "student-user-" + index, "student-" + index, 2);
        }
    }

    private record ConcurrentEnrollmentOutcome(int studentIndex, int status, String diagnostic, Throwable failure) {
    }

    private RequestPostProcessor studentJwt(String subject, String studentId) {
        return CurrentAccountJwt.currentJwt(jdbc, subject);
    }

    private RequestPostProcessor studentJwtWithoutProfile() {
        return CurrentAccountJwt.currentJwt(jdbc, "student-user-without-profile");
    }

    private RequestPostProcessor adminJwt() {
        return CurrentAccountJwt.currentJwt(jdbc, "admin-user");
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(200) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
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
                CREATE TABLE IF NOT EXISTS "academic"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120) NOT NULL,
                    "isActive" BOOLEAN NOT NULL
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
                CREATE TABLE IF NOT EXISTS "academic"."Classroom" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "building" VARCHAR(120) NOT NULL,
                    "roomNumber" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionNumber" VARCHAR(40) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "lecturerId" VARCHAR(120),
                    "classroomId" VARCHAR(120),
                    "capacity" INTEGER NOT NULL,
                    "enrolledCount" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."SectionSchedule" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "classroomId" VARCHAR(120) NOT NULL,
                    "dayOfWeek" INTEGER NOT NULL,
                    "startTime" VARCHAR(20) NOT NULL,
                    "endTime" VARCHAR(20) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."CurriculumCourse" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "curriculumId" VARCHAR(120) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL,
                    "year" INTEGER NOT NULL,
                    "semester" INTEGER NOT NULL,
                    "isMandatory" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "enrolledAt" TIMESTAMP NOT NULL,
                    "droppedAt" TIMESTAMP,
                    "gradeStatus" VARCHAR(40) NOT NULL,
                    "finalGrade" DECIMAL(5, 2),
                    "letterGrade" VARCHAR(10),
                    "courseId" VARCHAR(120) NOT NULL,
                    "roundId" VARCHAR(120),
                    "creditsSnapshot" INTEGER NOT NULL,
                    "registrationSource" VARCHAR(40),
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."RegistrationRound" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "name" VARCHAR(180) NOT NULL,
                    "kind" VARCHAR(40) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "windowStart" TIMESTAMP NOT NULL,
                    "windowEnd" TIMESTAMP NOT NULL,
                    "creditLimit" INTEGER NOT NULL,
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."CreditLimitApplication" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "roundId" VARCHAR(120) NOT NULL,
                    "requestedLimit" INTEGER NOT NULL DEFAULT 30,
                    "reason" VARCHAR(1000) NOT NULL,
                    "status" VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                    "reviewedBy" VARCHAR(120),
                    "reviewedAt" TIMESTAMP,
                    "reviewerNote" VARCHAR(1000),
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "version" INTEGER NOT NULL DEFAULT 0
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."RegistrationRoundCohort" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "roundId" VARCHAR(120) NOT NULL,
                    "curriculumId" VARCHAR(120),
                    "year" INTEGER,
                    "departmentId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."CourseRequirement" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "courseId" VARCHAR(120) NOT NULL,
                    "requiredCourseId" VARCHAR(120) NOT NULL,
                    "kind" VARCHAR(16) NOT NULL,
                    "minLetterGrade" VARCHAR(16)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."RegistrationIdempotency" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "ownerId" VARCHAR(120) NOT NULL,
                    "idempotencyKey" VARCHAR(200) NOT NULL,
                    "requestHash" CHAR(64) NOT NULL,
                    "state" VARCHAR(40) NOT NULL,
                    "enrollmentId" VARCHAR(120),
                    "responseBody" TEXT,
                    "slipSha256" CHAR(64),
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE ("ownerId", "idempotencyKey")
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."EnrollmentEvent" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "enrollmentId" VARCHAR(120) NOT NULL,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "action" VARCHAR(16) NOT NULL,
                    "actorId" VARCHAR(120) NOT NULL,
                    "requestHash" CHAR(64),
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."RegistrationSlip" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "roundId" VARCHAR(120) NOT NULL,
                    "sha256" CHAR(64) NOT NULL,
                    "byteSize" INTEGER NOT NULL,
                    "payload" BYTEA NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\"");
        jdbc.update("DELETE FROM \"academic\".\"CreditLimitApplication\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationSlip\"");
        jdbc.update("DELETE FROM \"academic\".\"EnrollmentEvent\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationIdempotency\"");
        jdbc.update("DELETE FROM \"academic\".\"CourseRequirement\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationRoundCohort\"");
        jdbc.update("DELETE FROM \"academic\".\"CurriculumCourse\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationRound\"");
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"SectionSchedule\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Classroom\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Semester\"");
        jdbc.update("DELETE FROM \"academic\".\"AcademicYear\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
    }

    private void insertFixture() {
        LocalDateTime now = localDateTime(BASE_TIME);
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                "student-user-1",
                "student1@campuscore.edu",
                "An",
                "Student");
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "student-1",
                "student-user-1",
                "S001",
                "curriculum-1",
                2,
                "ACTIVE",
                now,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                "ay-2026",
                2026,
                now,
                localDateTime(BASE_TIME.plusSeconds(31_536_000)),
                true,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\", \"startDate\","
                        + " \"endDate\", \"registrationStart\", \"registrationEnd\", \"addDropStart\", \"addDropEnd\","
                        + " \"status\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "semester-1",
                "Fall 2026",
                "Fall 2026",
                "Hoc ky Thu 2026",
                "FALL",
                "ay-2026",
                now,
                localDateTime(BASE_TIME.plusSeconds(10_368_000)),
                null,
                null,
                null,
                null,
                "ACTIVE",
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\","
                        + " \"descriptionVi\", \"credits\", \"departmentId\", \"semesterId\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "course-open",
                "SE402",
                "Web Application Development",
                "Web Application Development",
                "Phat trien ung dung web",
                null,
                null,
                null,
                3,
                "department-1",
                "semester-1",
                true,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "section-open",
                "01",
                "course-open",
                "semester-1",
                null,
                null,
                35,
                0,
                "OPEN",
                0);
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\","
                        + " \"descriptionVi\", \"credits\", \"departmentId\", \"semesterId\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "course-overlap",
                "SE403",
                "Systems",
                "Systems",
                "He thong",
                null,
                null,
                null,
                3,
                "department-1",
                "semester-1",
                true,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "section-overlap",
                "01",
                "course-overlap",
                "semester-1",
                null,
                null,
                35,
                0,
                "OPEN",
                0);
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\","
                        + " \"descriptionVi\", \"credits\", \"departmentId\", \"semesterId\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "course-two",
                "SE404",
                "Academic Writing",
                "Academic Writing",
                "Viet hoc thuat",
                null,
                null,
                null,
                2,
                "department-1",
                "semester-1",
                true,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "section-two",
                "01",
                "course-two",
                "semester-1",
                null,
                null,
                35,
                0,
                "OPEN",
                0);
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "sched-open",
                "section-open",
                "room-1",
                2,
                "07:00",
                "09:30");
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "sched-overlap",
                "section-overlap",
                "room-1",
                2,
                "08:00",
                "10:00");
        jdbc.update(
                "INSERT INTO \"academic\".\"RegistrationRound\""
                        + " (\"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\","
                        + " \"creditLimit\", \"version\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "round-open",
                "semester-1",
                "Registration",
                "REGISTRATION",
                "OPEN",
                localDateTime(Instant.parse("2020-01-01T00:00:00Z")),
                localDateTime(Instant.parse("2030-01-01T00:00:00Z")),
                28,
                0,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"RegistrationRound\""
                        + " (\"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\","
                        + " \"creditLimit\", \"version\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "round-add-drop",
                "semester-1",
                "Add drop",
                "ADD_DROP",
                "OPEN",
                localDateTime(Instant.parse("2020-01-01T00:00:00Z")),
                localDateTime(Instant.parse("2030-01-01T00:00:00Z")),
                28,
                0,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\") VALUES (?, ?, ?)",
                "room-1",
                "A",
                "101");
        AccountAuthorityFixture.student(jdbc, "student-user-1", "student-1", 2);
        AccountAuthorityFixture.ensureUser(jdbc, "student-user-without-profile");
        AccountAuthorityFixture.replaceRoles(jdbc, "student-user-without-profile", "STUDENT");
        AccountAuthorityFixture.ensureUser(jdbc, "admin-user");
        AccountAuthorityFixture.replaceRoles(jdbc, "admin-user", "ADMIN");
    }

    private static LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
