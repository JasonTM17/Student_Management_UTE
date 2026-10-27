package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import io.campuscore.restfulapi.testsupport.CurrentAccountJwt;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:attendance_read;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AcademicAttendanceReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-21T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepareReadOnlyFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        createTables();
        clearTables();
        insertFixture();
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.prepare(jdbc);
        insertUser("admin-user", "admin@example.test", "Admin", "Fixture");
        insertUser("lecturer-user-1", "lecturer1@example.test", "Lecturer", "One");
        insertUser("lecturer-user-2", "lecturer2@example.test", "Lecturer", "Two");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "admin-user", "ADMIN");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "student-user-1", "STUDENT");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "student-user-2", "STUDENT");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "lecturer-user-1", "lecturer-1");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "lecturer-user-2", "lecturer-2");
    }

    @Test
    void studentSelfAttendanceAndSummaryPreserveSemesterAndSectionFilters() throws Exception {
        mvc.perform(get("/api/v1/attendance/my")
                        .queryParam("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value("attendance-3"))
                .andExpect(jsonPath("$[0].section.course.code").value("CS101"))
                .andExpect(jsonPath("$[0].student.user.email").value("student1@campuscore.edu"))
                .andExpect(jsonPath("$[2].id").value("attendance-1"));

        mvc.perform(get("/api/v1/attendance/my")
                        .queryParam("sectionId", "section-spring")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("attendance-spring"));

        mvc.perform(get("/api/v1/attendance/my/summary")
                        .queryParam("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sectionId").value("section-1"))
                .andExpect(jsonPath("$[0].courseCode").value("CS101"))
                .andExpect(jsonPath("$[0].total").value(3))
                .andExpect(jsonPath("$[0].present").value(1))
                .andExpect(jsonPath("$[0].late").value(1))
                .andExpect(jsonPath("$[0].attendanceRate").value(67));
    }

    @Test
    void studentAndSectionRatesUseTheSameCountableRecordsAndExcludeExcused() throws Exception {
        // section-spring already has one EXCUSED record for student-1. Add a
        // matching PRESENT, LATE and ABSENT record so both summaries see the
        // same four records: 2 attended of 3 countable, rounded half-up to 67%.
        insertAttendance("attendance-spring-present", "student-1", "section-spring",
                BASE_TIME.plusSeconds(2L * 24 * 60 * 60), "PRESENT", null);
        insertAttendance("attendance-spring-late", "student-1", "section-spring",
                BASE_TIME.plusSeconds(3L * 24 * 60 * 60), "LATE", null);
        insertAttendance("attendance-spring-absent", "student-1", "section-spring",
                BASE_TIME.plusSeconds(4L * 24 * 60 * 60), "ABSENT", null);

        MvcResult studentSummary = mvc.perform(get("/api/v1/attendance/my/summary")
                        .queryParam("semesterId", "semester-2")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sectionId").value("section-spring"))
                .andExpect(jsonPath("$[0].total").value(4))
                .andExpect(jsonPath("$[0].present").value(1))
                .andExpect(jsonPath("$[0].late").value(1))
                .andExpect(jsonPath("$[0].absent").value(1))
                .andExpect(jsonPath("$[0].excused").value(1))
                .andReturn();

        MvcResult sectionSummary = mvc.perform(get("/api/v1/attendance/section/section-spring/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(4))
                .andExpect(jsonPath("$.present").value(1))
                .andExpect(jsonPath("$.late").value(1))
                .andExpect(jsonPath("$.absent").value(1))
                .andExpect(jsonPath("$.excused").value(1))
                .andReturn();

        int studentRate = objectMapper.readTree(studentSummary.getResponse().getContentAsString())
                .get(0).get("attendanceRate").asInt();
        int sectionRate = objectMapper.readTree(sectionSummary.getResponse().getContentAsString())
                .get("attendanceRate").asInt();
        assertThat(studentRate)
                .as("student rate %s and section rate %s for identical records", studentRate, sectionRate)
                .isEqualTo(sectionRate)
                .isEqualTo(67);
    }

    @Test
    void attendanceRateIsZeroWhenAllRecordsAreExcusedAndRoundsHalfUp() throws Exception {
        mvc.perform(get("/api/v1/attendance/my/summary")
                        .queryParam("semesterId", "semester-2")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].total").value(1))
                .andExpect(jsonPath("$[0].excused").value(1))
                .andExpect(jsonPath("$[0].attendanceRate").value(0));

        // section-2 has one PRESENT record. With seven ABSENT records, the
        // countable ratio is exactly 12.5%, which must round half-up to 13%.
        for (int day = 1; day <= 7; day++) {
            insertAttendance("attendance-rounding-" + day, "student-2", "section-2",
                    BASE_TIME.plusSeconds((long) day * 24 * 60 * 60), "ABSENT", null);
        }
        mvc.perform(get("/api/v1/attendance/section/section-2/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(1))
                .andExpect(jsonPath("$.absent").value(7))
                .andExpect(jsonPath("$.attendanceRate").value(13));
    }

    @Test
    void examEligibilityFlagsAtRiskOnlyOnceCountableSessionsReachTheMinimum() throws Exception {
        // student-2 already has one PRESENT in section-2; enrolling them and
        // adding four ABSENT records lands on exactly minSessions=5 countable
        // records at a 20% rate — inside the AT_RISK band.
        insertEnrollment("enroll-risk", "student-2", "section-2", "semester-1", "ENROLLED");
        for (int day = 1; day <= 4; day++) {
            insertAttendance("risk-absent-" + day, "student-2", "section-2",
                    BASE_TIME.plusSeconds((long) (day + 10) * 24 * 60 * 60), "ABSENT", null);
        }

        mvc.perform(get("/api/v1/attendance/my/summary")
                        .queryParam("semesterId", "semester-1")
                        .with(studentJwt("student-user-2", "student-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-2')].countableSessions")
                        .value(org.hamcrest.Matchers.contains(5)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-2')].attendanceRate")
                        .value(org.hamcrest.Matchers.contains(20)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-2')].minRate")
                        .value(org.hamcrest.Matchers.contains(0.8)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-2')].minSessions")
                        .value(org.hamcrest.Matchers.contains(5)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-2')].atRisk")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-2')].examEligibility")
                        .value(org.hamcrest.Matchers.contains("AT_RISK")));

        // The lecturer's section view names the at-risk student — and stays
        // advisory: atRiskStudents is a list, not a block.
        mvc.perform(get("/api/v1/attendance/section/section-2/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minRate").value(0.8))
                .andExpect(jsonPath("$.minSessions").value(5))
                .andExpect(jsonPath("$.atRiskStudents.length()").value(1))
                .andExpect(jsonPath("$.atRiskStudents[0].studentId").value("student-2"))
                .andExpect(jsonPath("$.atRiskStudents[0].studentCode").value("S002"))
                .andExpect(jsonPath("$.atRiskStudents[0].countableSessions").value(5))
                .andExpect(jsonPath("$.atRiskStudents[0].attendanceRate").value(20));
    }

    @Test
    void examEligibilityReportsInsufficientDataBelowTheSessionFloorAndOkAtThreshold() throws Exception {
        // section-1 student-1 has 3 countable records (P/A/L) — below
        // minSessions, so the signal is INSUFFICIENT_DATA even though the
        // 67% rate sits under the threshold. EXCUSED never counts.
        mvc.perform(get("/api/v1/attendance/my/summary")
                        .queryParam("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-1')].countableSessions")
                        .value(org.hamcrest.Matchers.contains(3)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-1')].atRisk")
                        .value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-1')].examEligibility")
                        .value(org.hamcrest.Matchers.contains("INSUFFICIENT_DATA")));

        // section-spring holds only an EXCUSED record: countable stays zero.
        mvc.perform(get("/api/v1/attendance/my/summary")
                        .queryParam("semesterId", "semester-2")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-spring')].countableSessions")
                        .value(org.hamcrest.Matchers.contains(0)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-spring')].examEligibility")
                        .value(org.hamcrest.Matchers.contains("INSUFFICIENT_DATA")));

        // Enough countable records at or above the threshold is OK — the
        // advisory never invents risk.
        insertEnrollment("enroll-ok", "student-1", "section-other", "semester-1", "ENROLLED");
        for (int day = 1; day <= 5; day++) {
            insertAttendance("ok-present-" + day, "student-1", "section-other",
                    BASE_TIME.plusSeconds((long) (day + 20) * 24 * 60 * 60), "PRESENT", null);
        }
        mvc.perform(get("/api/v1/attendance/my/summary")
                        .queryParam("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-other')].attendanceRate")
                        .value(org.hamcrest.Matchers.contains(100)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-other')].atRisk")
                        .value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.[?(@.sectionId == 'section-other')].examEligibility")
                        .value(org.hamcrest.Matchers.contains("OK")));
        mvc.perform(get("/api/v1/attendance/section/section-other/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atRiskStudents.length()").value(0));
    }

    @Test
    void withdrawnStudentsNeverAppearInTheAtRiskRoster() throws Exception {
        // student-3 sits in section-1 only as DROPPED per the fixture. A
        // WITHDRAWN enrollment is likewise no longer a markable seat, so even
        // perfect-looking bad attendance must stay out of the risk list.
        insertEnrollment("enroll-withdrawn-risk", "student-3", "section-2", "semester-1", "WITHDRAWN");
        for (int day = 1; day <= 6; day++) {
            insertAttendance("withdrawn-absent-" + day, "student-3", "section-2",
                    BASE_TIME.plusSeconds((long) (day + 30) * 24 * 60 * 60), "ABSENT", null);
        }

        mvc.perform(get("/api/v1/attendance/section/section-2/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atRiskStudents[?(@.studentId == 'student-3')]")
                        .value(org.hamcrest.Matchers.empty()));
    }

    @Test
    void adminListPreservesLegacyEnvelopeFiltersDateAndDescendingDateOrdering() throws Exception {
        mvc.perform(get("/api/v1/attendance")
                        .queryParam("page", "1")
                        .queryParam("limit", "2")
                        .queryParam("sectionId", "section-1")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("attendance-3"))
                .andExpect(jsonPath("$.data[0].student.studentId").value("S001"))
                .andExpect(jsonPath("$.data[0].section.course.nameVi").value("Nhập môn lập trình"))
                .andExpect(jsonPath("$.meta.total").value(5))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        mvc.perform(get("/api/v1/attendance")
                        .queryParam("date", "2026-08-21")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].date").value("2026-08-21T02:00:00.000Z"))
                .andExpect(jsonPath("$.meta.total").value(6));
    }

    @Test
    void lecturerAndSectionReadsPreserveOwnershipOrderingAndSectionSummaryFormula() throws Exception {
        mvc.perform(get("/api/v1/attendance/lecturer/my").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].id").value("attendance-3"))
                .andExpect(jsonPath("$[?(@.sectionId == 'section-other')]").isEmpty());

        mvc.perform(get("/api/v1/attendance/section/section-1")
                        .queryParam("date", "2026-08-21")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].student.user.firstName").value("An"))
                .andExpect(jsonPath("$[1].student.user.firstName").value("Linh"))
                .andExpect(jsonPath("$[2].student.user.firstName").value("Minh"));

        mvc.perform(get("/api/v1/attendance/section/section-1")
                        .queryParam("date", "2026-08-21")
                        .with(lecturerJwt("lecturer-2")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/attendance/section/section-1/summary")
                        .with(lecturerJwt("lecturer-2")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/attendance/section/section-1/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-1"))
                .andExpect(jsonPath("$.totalSessions").value(3))
                .andExpect(jsonPath("$.totalRecords").value(5))
                .andExpect(jsonPath("$.present").value(2))
                .andExpect(jsonPath("$.absent").value(1))
                .andExpect(jsonPath("$.late").value(2))
                .andExpect(jsonPath("$.attendanceRate").value(80));
    }

    @Test
    void attendanceRosterIncludesPendingExcludesDroppedAndStaysOwned() throws Exception {
        // Owning lecturer: ENROLLED + PENDING markable, DROPPED/CANCELLED gone.
        mvc.perform(get("/api/v1/attendance/section/section-1/roster")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].studentId").value("student-1"))
                .andExpect(jsonPath("$[1].studentId").value("student-2"))
                .andExpect(jsonPath("$[?(@.studentId == 'student-3')]").isEmpty())
                .andExpect(jsonPath("$[0].enrollmentStatus").value("ENROLLED"))
                .andExpect(jsonPath("$[1].studentCode").value("S002"))
                .andExpect(jsonPath("$[1].enrollmentStatus").value("PENDING"));

        // Admin sees the same markable population on another lecturer's section.
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Non-owning lecturer and students are denied.
        mvc.perform(get("/api/v1/attendance/section/section-1/roster")
                        .with(lecturerJwt("lecturer-2")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/attendance/section/section-1/roster")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/attendance/section/section-1")
                        .queryParam("date", "2026-08-21")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void withdrawnEnrollmentsAreExcludedFromTheAttendanceRoster() throws Exception {
        // An approved withdrawal leaves the enrollment WITHDRAWN (letter W):
        // transcript history, never a markable seat. A withdrawn-only student
        // must vanish from roll call, while a student who re-enrolled after
        // withdrawing keeps the newest eligible (ENROLLED) row.
        insertUser("student-user-withdrawn", "withdrawn@example.test", "Withdrawn", "Student");
        insertStudent("student-withdrawn", "student-user-withdrawn", "S900");
        insertEnrollment("enrollment-withdrawn", "student-withdrawn", "section-1", "semester-1", "WITHDRAWN");
        insertEnrollment("enrollment-withdrawn-old", "student-1", "section-1", "semester-1", "WITHDRAWN");

        for (var identity : List.of(lecturerJwt("lecturer-1"), adminJwt())) {
            mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(identity))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.studentId == 'student-withdrawn')]").isEmpty())
                    .andExpect(jsonPath("$[?(@.studentId == 'student-1')].enrollmentStatus")
                            .value(org.hamcrest.Matchers.contains("ENROLLED")));
        }
    }

    @Test
    void detailNotFoundAndReadBoundaryFailuresStayExplicit() throws Exception {
        mvc.perform(get("/api/v1/attendance/attendance-1").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("attendance-1"))
                .andExpect(jsonPath("$.notes").value("On time"))
                .andExpect(jsonPath("$.section.course.code").value("CS101"));

        mvc.perform(get("/api/v1/attendance/missing").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/attendance/attendance-1")
                        .with(studentJwt("student-user-2", "student-2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/attendance/section/section-1")
                        .with(lecturerJwt("lecturer-2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/attendance/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/attendance")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/attendance/my")
                        .with(jwt().jwt(token -> token
                                .subject("student-user-1")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        mvc.perform(get("/api/v1/attendance")
                        .queryParam("limit", "101")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/attendance/my")
                        .queryParam("unexpected", "value")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rosterDeduplicatesStudentsWithoutChangingHistory() throws Exception {
        insertEnrollment("history-1", "student-1", "section-1", "semester-1", "COMPLETED");
        insertEnrollment("history-2", "student-1", "section-1", "semester-1", "COMPLETED");
        insertEnrollment("history-pending", "student-2", "section-1", "semester-1", "PENDING");
        var enrollments = jdbc.queryForList("SELECT * FROM academic.\"Enrollment\" ORDER BY \"id\"");
        var attendance = jdbc.queryForList("SELECT * FROM academic.\"Attendance\" ORDER BY \"id\"");
        for (var identity : List.of(lecturerJwt("lecturer-1"), adminJwt())) {
            mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(identity))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.studentId == 'student-1')].studentId").value(org.hamcrest.Matchers.contains("student-1")))
                    .andExpect(jsonPath("$[?(@.studentId == 'student-2')].studentId").value(org.hamcrest.Matchers.contains("student-2")));
        }
        assertThat(jdbc.queryForList("SELECT * FROM academic.\"Enrollment\" ORDER BY \"id\""))
                .isEqualTo(enrollments);
        assertThat(jdbc.queryForList("SELECT * FROM academic.\"Attendance\" ORDER BY \"id\""))
                .isEqualTo(attendance);
    }

    @Test
    void rosterUsesLatestEligibleEnrollmentAndIdTiebreaker() throws Exception {
        insertEnrollment("roster-old", "student-1", "section-1", "semester-1", "COMPLETED");
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"enrolledAt\"=? WHERE \"id\"=?",
                Timestamp.from(BASE_TIME.minusSeconds(60)), "roster-old");
        insertEnrollment("roster-a", "student-1", "section-1", "semester-1", "PENDING");
        insertEnrollment("roster-z", "student-1", "section-1", "semester-1", "CONFIRMED");
        insertEnrollment("roster-dropped", "student-1", "section-1", "semester-1", "DROPPED");
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"enrolledAt\"=? WHERE \"id\"=?",
                Timestamp.from(BASE_TIME.plusSeconds(60)), "roster-dropped");
        insertEnrollment("roster-completed", "student-3", "section-1", "semester-1", "COMPLETED");
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.studentId == 'student-1')].enrollmentStatus").value(org.hamcrest.Matchers.contains("CONFIRMED")))
                .andExpect(jsonPath("$[?(@.studentId == 'student-3')].enrollmentStatus").value(org.hamcrest.Matchers.contains("COMPLETED")));
    }

    @Test
    void rosterKeepsDistinctStudentsWithIdenticalNamesAndRetainsAccessGuards() throws Exception {
        insertUser("student-user-homonym", "homonym@example.invalid", "Linh", "Nguyen");
        insertStudent("student-homonym", "student-user-homonym", "S004");
        insertEnrollment("enrollment-homonym", "student-homonym", "section-1", "semester-1", "ENROLLED");
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.studentCode == 'S001')].studentId").value(org.hamcrest.Matchers.contains("student-1")))
                .andExpect(jsonPath("$[?(@.studentCode == 'S004')].studentId").value(org.hamcrest.Matchers.contains("student-homonym")));
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(lecturerJwt("lecturer-2")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/attendance/section/section-1/roster"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor studentJwt(String subject, String studentId) {
        return jwt().jwt(token -> token
                        .subject(subject)
                        .claim("roles", List.of("STUDENT"))
                        .claim("student", java.util.Map.of("year", 3))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    @Test
    void rosterSixRoleMatrixPreservesOwnershipForAcademicHeads() throws Exception {
        for (String role : List.of("TRUONG_BO_MON", "TRUONG_KHOA")) {
            String subject = "roster-" + role;
            String profile = "profile-" + role;
            io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureUser(jdbc, subject);
            io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, subject, profile);
            io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, subject, role);
            jdbc.update("UPDATE academic.\"Section\" SET \"lecturerId\"=? WHERE \"id\"='section-1'", profile);
            RequestPostProcessor head = jwt().jwt(token -> token.subject(subject)
                            .claim("roles", List.of("LECTURER", role)).claim("lecturerId", profile))
                    .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"), new SimpleGrantedAuthority("ROLE_" + role));
            mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(head))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
            mvc.perform(get("/api/v1/attendance/section/section-2/roster").with(head))
                    .andExpect(status().isForbidden());
        }
        String subject = "roster-super-admin";
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureUser(jdbc, subject);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, subject, "SUPER_ADMIN");
        RequestPostProcessor superAdmin = jwt().jwt(token -> token.subject(subject).claim("roles", List.of("SUPER_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(superAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        jdbc.update("UPDATE academic.\"Section\" SET \"lecturerId\"='lecturer-1' WHERE \"id\"='section-1'");
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void truongBoMonAndTruongKhoaCanReadAttendanceRosterAndSummaryWithoutLecturerRole() throws Exception {
        String tbmSubject = "user-tbm-head";
        String tbmProfile = "lect-tbm-head";
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureUser(jdbc, tbmSubject);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, tbmSubject, tbmProfile);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, tbmSubject, "TRUONG_BO_MON");
        jdbc.update("INSERT INTO \"academic\".\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"isActive\") VALUES (?, ?, ?, ?)",
                tbmProfile, tbmSubject, "department-1", true);

        // Department head has only TRUONG_BO_MON authority (not LECTURER)
        RequestPostProcessor tbmJwt = CurrentAccountJwt.currentJwt(jdbc, tbmSubject);

        // course-1 belongs to department-1, taught by lecturer-1; tbm head can read roster & summary
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(tbmJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/v1/attendance/section/section-1/summary").with(tbmJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-1"));

        String tkSubject = "user-tk-head";
        String tkProfile = "lect-tk-head";
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureUser(jdbc, tkSubject);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, tkSubject, tkProfile);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, tkSubject, "TRUONG_KHOA");
        jdbc.update("INSERT INTO \"academic\".\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"isActive\") VALUES (?, ?, ?, ?)",
                tkProfile, tkSubject, "department-2", true);

        RequestPostProcessor tkJwt = CurrentAccountJwt.currentJwt(jdbc, tkSubject);
        mvc.perform(get("/api/v1/attendance/section/section-1/roster").with(tkJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/v1/attendance/section/section-1/summary").with(tkJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-1"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("admin-user")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor lecturerJwt(String lecturerId) {
        return jwt().jwt(token -> token
                        .subject("lecturer-2".equals(lecturerId) ? "lecturer-user-2" : "lecturer-user-1")
                        .claim("roles", List.of("LECTURER"))
                        .claim("lecturerId", lecturerId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
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
                CREATE TABLE IF NOT EXISTS "academic"."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "facultyId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120),
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
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
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionNumber" VARCHAR(40) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "lecturerId" VARCHAR(120),
                    "classroomId" VARCHAR(120),
                    "capacity" INTEGER NOT NULL,
                    "enrolledCount" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "registrationSource" VARCHAR(40),
                    "enrolledAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Attendance" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "date" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "notes" VARCHAR(1000),
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"Attendance\"");
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Department\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
    }

    private void insertFixture() {
        jdbc.update("INSERT INTO \"academic\".\"Department\" (\"id\", \"facultyId\") VALUES (?, ?)", "department-1", "faculty-1");
        jdbc.update("INSERT INTO \"academic\".\"Department\" (\"id\", \"facultyId\") VALUES (?, ?)", "department-2", "faculty-1");
        jdbc.update("INSERT INTO \"academic\".\"Department\" (\"id\", \"facultyId\") VALUES (?, ?)", "department-3", "faculty-2");
        jdbc.update("INSERT INTO \"academic\".\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"isActive\") VALUES (?, ?, ?, ?)", "lecturer-1", "lecturer-user-1", "department-1", true);
        jdbc.update("INSERT INTO \"academic\".\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"isActive\") VALUES (?, ?, ?, ?)", "lecturer-2", "lecturer-user-2", "department-2", true);
        insertUser("student-user-1", "student1@campuscore.edu", "Linh", "Nguyen");
        insertUser("student-user-2", "student2@campuscore.edu", "Minh", "Tran");
        insertUser("student-user-3", "student3@campuscore.edu", "An", "Le");
        insertStudent("student-1", "student-user-1", "S001");
        insertStudent("student-2", "student-user-2", "S002");
        insertStudent("student-3", "student-user-3", "S003");
        insertCourse("course-1", "CS101", "Intro to Programming", "Intro to Programming", "Nhập môn lập trình");
        insertCourse("course-2", "SE401", "Web Development", "Web Development", "Lập trình web");
        insertSection("section-1", "A", "course-1", "semester-1", "lecturer-1");
        insertSection("section-2", "B", "course-2", "semester-1", "lecturer-1");
        insertSection("section-spring", "C", "course-2", "semester-2", "lecturer-1");
        insertSection("section-other", "D", "course-1", "semester-1", "lecturer-2");
        insertAttendance("attendance-1", "student-1", "section-1", BASE_TIME, "PRESENT", "On time");
        insertAttendance("attendance-2", "student-1", "section-1", BASE_TIME.plusSeconds(24 * 60 * 60), "ABSENT", null);
        insertAttendance("attendance-3", "student-1", "section-1", BASE_TIME.plusSeconds(2L * 24 * 60 * 60), "LATE", "Traffic");
        insertAttendance("attendance-spring", "student-1", "section-spring", BASE_TIME.plusSeconds(3600), "EXCUSED", null);
        insertAttendance("attendance-section-minh", "student-2", "section-1", BASE_TIME, "PRESENT", null);
        insertAttendance("attendance-section-an", "student-3", "section-1", BASE_TIME, "LATE", null);
        insertAttendance("attendance-section2", "student-2", "section-2", BASE_TIME.plusSeconds(7200), "PRESENT", null);
        insertAttendance("attendance-other-lecturer", "student-2", "section-other", BASE_TIME, "PRESENT", null);
        // Roster fixture: the markable population for section-1 is every
        // enrollment except DROPPED/CANCELLED — PENDING must appear.
        insertEnrollment("enroll-1", "student-1", "section-1", "semester-1", "ENROLLED");
        insertEnrollment("enroll-2", "student-2", "section-1", "semester-1", "PENDING");
        insertEnrollment("enroll-3", "student-3", "section-1", "semester-1", "DROPPED");
        insertEnrollment("enroll-other", "student-2", "section-other", "semester-1", "ENROLLED");
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                id, email, firstName, lastName);
    }

    private void insertStudent(String id, String userId, String studentNumber) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\", \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, studentNumber, "curriculum-1", 3, "ACTIVE",
                utcTimestamp(BASE_TIME), utcTimestamp(BASE_TIME), utcTimestamp(BASE_TIME));
    }

    private void insertCourse(String id, String code, String name, String nameEn, String nameVi) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\", \"descriptionVi\","
                        + " \"credits\", \"departmentId\", \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, name, nameEn, nameVi, null, null, null, 4, "department-1", "semester-1", true,
                utcTimestamp(BASE_TIME), utcTimestamp(BASE_TIME));
    }

    private void insertSection(String id, String sectionNumber, String courseId, String semesterId, String lecturerId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, sectionNumber, courseId, semesterId, lecturerId, 30, 12, "OPEN");
    }

    private void insertEnrollment(String id, String studentId, String sectionId, String semesterId, String status) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id, studentId, sectionId, semesterId, status, Timestamp.from(BASE_TIME));
    }

    private void insertAttendance(
            String id,
            String studentId,
            String sectionId,
            Instant date,
            String status,
            String notes) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Attendance\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"date\", \"status\", \"notes\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, studentId, sectionId, utcTimestamp(date), status, notes, utcTimestamp(BASE_TIME));
    }

    private static java.time.OffsetDateTime utcTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
