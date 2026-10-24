package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
import io.campuscore.restfulapi.testsupport.CurrentAccountJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
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
        "spring.datasource.url=jdbc:h2:mem:section_cancel;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class SectionCancellationPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
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
    void cancelledSectionPreservesEnrollmentsAndReopenReactivatesThem() throws Exception {
        // section-two has no schedules, so nothing else can hold its slots.
        MvcResult enrolled = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-preserve-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-two\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enrollmentId = enrollmentId(enrolled);

        cancelSection("section-two");

        // Cancelling must not delete or rewrite enrollment data.
        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class, enrollmentId))
                .isEqualTo("ENROLLED");
        assertThat(jdbc.queryForObject(
                        "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = 'section-two'",
                        Integer.class))
                .isEqualTo(1);

        // Reopening through the same admin update reactivates the preserved row.
        mvc.perform(put("/api/v1/sections/section-two")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsUsed").value(2));
    }

    @Test
    void studentCanDropCancelledSectionEnrollmentOutsideRoundWindow() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-drop-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enrollmentId = enrollmentId(enrolled);
        cancelSection("section-open");
        closeAllRounds();

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-drop-student"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class, enrollmentId))
                .isEqualTo("DROPPED");
        assertThat(jdbc.queryForObject(
                        "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = 'section-open'",
                        Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"EnrollmentEvent\" WHERE \"enrollmentId\" = ? AND \"action\" = 'DROP'",
                        Integer.class, enrollmentId))
                .isEqualTo(1);
    }

    @Test
    void adminCanDropCancelledSectionEnrollmentOutsideRoundWindow() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-admin-drop-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enrollmentId = enrollmentId(enrolled);
        cancelSection("section-open");
        closeAllRounds();

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(adminJwt())
                        .header("Idempotency-Key", "cancel-drop-admin"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class, enrollmentId))
                .isEqualTo("DROPPED");
    }

    @Test
    void cancelledSectionEnrollmentCanBeDroppedWhileDependentCourseRemains() throws Exception {
        // course-open requires course-two as a COREQ. When the corequisite
        // section is cancelled its enrollment no longer counts toward that
        // requirement, so keeping it protects nothing — blocking the drop
        // would only trap the student.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-cancel-coreq', 'course-open', 'course-two', 'COREQ', null)");
        seedActiveEnrollment("enrollment-cancel-parent", "section-two", "course-two");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-coreq-dep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());

        cancelSection("section-two");

        mvc.perform(post("/api/v1/enrollments/enrollment-cancel-parent/drop")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-coreq-drop"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enrollment-cancel-parent'",
                        String.class))
                .isEqualTo("DROPPED");
    }

    @Test
    void cancelledSectionDoesNotBlockDuplicateCourseOrSchedule() throws Exception {
        // A second section of course-open lets the student re-register the
        // same course once the first section is cancelled.
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\", \"version\")"
                        + " VALUES ('section-open-b', '02', 'course-open', 'semester-1', NULL, NULL, 35, 0, 'OPEN', 0)");
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES ('sched-open-b', 'section-open-b', 'room-1', 4, '13:00', '15:30')");

        MvcResult firstEnroll = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-dup-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String firstEnrollmentId = objectMapper.readTree(
                firstEnroll.getResponse().getContentAsString()).get("id").asText();
        cancelSection("section-open");

        // Same course, different section: must not trip DUPLICATE_COURSE, and
        // the seat preserved by the cancellation must be retired inside the
        // same transaction — the database's active-enrollment unique index
        // would otherwise reject the insert (Postgres 23505).
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-dup-second")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open-b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        // The preserved row is DROPPED with an event explaining why; a later
        // reopen of the cancelled section must not resurrect the seat.
        assertThat(jdbc.queryForObject(
                "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                String.class, firstEnrollmentId)).isEqualTo("DROPPED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"EnrollmentEvent\""
                        + " WHERE \"enrollmentId\" = ? AND \"action\" = 'REPLACED'",
                Integer.class, firstEnrollmentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = 'section-open'",
                Integer.class)).isZero();

        // The cancelled section's timetable must not conflict-block other
        // sections either: section-overlap shares the same day/slot.
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-conflict-second")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-overlap\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void cancelledSectionCreditsDoNotCountTowardTheCap() throws Exception {
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-credit-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());
        cancelSection("section-open");

        // creditsUsed must exclude cancelled-section enrollments.
        mvc.perform(get("/api/v1/me/registration/summary")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsUsed").value(0));
    }

    @Test
    void cancelledSectionDoesNotSatisfyCorequisite() throws Exception {
        // course-overlap requires course-open as COREQ; an enrollment whose
        // section was cancelled must not satisfy that requirement.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-cancel-satisfy', 'course-overlap', 'course-open', 'COREQ', null)");
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-satisfy-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());
        cancelSection("section-open");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-satisfy-second")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-overlap\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COREQUISITE_UNMET"));
    }

    @Test
    void publishedHistoryInCancelledSectionStillSatisfiesPrerequisite() throws Exception {
        // A completed, published pass stays on the academic record even after
        // its section is cancelled — the transcript keeps it, so the
        // prerequisite gate must keep counting it too.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-cancel-prereq', 'course-overlap', 'course-open', 'PREREQ', 'D')");
        seedCompletedEnrollment("enrollment-cancel-pass", "section-open", "course-open", "B");
        cancelSection("section-open");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-prereq-second")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-overlap\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void enrolledRowInCancelledSectionDoesNotSatisfyPrerequisite() throws Exception {
        // Only completed work survives cancellation: a merely ENROLLED row in
        // a cancelled section is not proof of having taken the course.
        jdbc.update(
                "INSERT INTO \"academic\".\"CourseRequirement\""
                        + " (\"id\", \"courseId\", \"requiredCourseId\", \"kind\", \"minLetterGrade\")"
                        + " VALUES ('req-cancel-prereq-open', 'course-overlap', 'course-open', 'PREREQ', 'D')");
        seedActiveEnrollment("enrollment-cancel-active", "section-open", "course-open");
        cancelSection("section-open");

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-prereq-active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-overlap\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_UNMET"));
    }

    @Test
    void gradeUpdateAndPublishRejectCancelledSection() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Section\" SET \"lecturerId\" = 'lecturer-1' WHERE \"id\" = 'section-open'");
        seedActiveEnrollment("enrollment-cancel-grade", "section-open", "course-open");
        cancelSection("section-open");

        mvc.perform(put("/api/v1/sections/section-open/grades")
                        .with(lecturerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[{\"enrollmentId\":\"enrollment-cancel-grade\",\"processScore\":8,\"finalExamScore\":7}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_CANCELLED"));

        mvc.perform(post("/api/v1/sections/section-open/grades/publish")
                        .with(lecturerJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_CANCELLED"));

        assertThat(jdbc.queryForObject(
                        "SELECT \"gradeStatus\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enrollment-cancel-grade'",
                        String.class))
                .isEqualTo("NOT_GRADED");
    }

    @Test
    void attendanceWriteRejectsCancelledSection() throws Exception {
        jdbc.update("UPDATE \"academic\".\"Section\" SET \"lecturerId\" = 'lecturer-1' WHERE \"id\" = 'section-open'");
        seedActiveEnrollment("enrollment-cancel-attendance", "section-open", "course-open");
        cancelSection("section-open");

        mvc.perform(put("/api/v1/attendance/sections/section-open")
                        .with(lecturerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-25\",\"records\":[{\"studentId\":\"student-1\",\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_CANCELLED"));

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-open'",
                        Integer.class))
                .isZero();
    }

    @Test
    void publishedGradesStayOnTranscriptAfterSectionCancel() throws Exception {
        // History must not be rewritten: an enrollment that was COMPLETED and
        // PUBLISHED before the cancellation remains on the transcript.
        seedCompletedEnrollment("enrollment-cancel-history", "section-open", "course-open", "B+");
        cancelSection("section-open");

        mvc.perform(get("/api/v1/enrollments/my/transcript")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesters[0].records[0].courseCode").value("SE402"))
                .andExpect(jsonPath("$.semesters[0].records[0].letterGrade").value("B+"))
                .andExpect(jsonPath("$.summary.totalCreditsEarned").value(3));
    }

    @Test
    void draftGradesOfCancelledSectionDoNotLeakIntoTranscript() throws Exception {
        // An unpublished draft row must stay invisible whether the section is
        // open or cancelled.
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"finalGrade\", \"letterGrade\", \"courseId\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "enrollment-cancel-draft", "student-1", "section-open", "semester-1", "ENROLLED",
                localDateTime(BASE_TIME), "DRAFT", new java.math.BigDecimal("7.50"), "B",
                "course-open", 3, 0);
        cancelSection("section-open");

        mvc.perform(get("/api/v1/enrollments/my/transcript")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalCreditsEarned").value(0))
                .andExpect(jsonPath("$.semesters[*].records[*].courseCode",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("SE402"))));
    }

    @Test
    void slipListsRealCoursesStudentAndSemester() throws Exception {
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-slip-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());

        MvcResult slip = mvc.perform(get("/api/v1/me/registration/slip")
                        .with(studentJwt()))
                .andExpect(status().isOk())
                .andReturn();

        // The renderer emits literal PDF text operators; the stream is
        // uncompressed ASCII, so the downloaded bytes contain the real values.
        String text = new String(slip.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(text).contains("SE402");
        assertThat(text).contains("Web Application Development");
        assertThat(text).contains("S001");
        assertThat(text).contains("Fall 2026");
        assertThat(text).doesNotContain("Hoc phan dang ky");
    }

    @Test
    void cancellingSectionInvalidatesPersistedSlips() throws Exception {
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cancel-slip-inv-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = 'student-1'",
                        Integer.class))
                .isEqualTo(1);

        cancelSection("section-open");

        // A persisted slip claims the course as a valid registration; once the
        // section is cancelled that claim is stale and must not be served.
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = 'student-1'",
                        Integer.class))
                .isEqualTo(0);
    }

    @Test
    void cancelledSectionDoesNotHoldRoomAndLecturerSlots() throws Exception {
        // section-overlap already schedules room-1 on day 2 08:00-10:00;
        // section-open holds day 2 07:00-09:30, so they clash while both are
        // live. Cancelling section-open must release its slots.
        jdbc.update("UPDATE \"academic\".\"Section\" SET \"lecturerId\" = 'lecturer-1' WHERE \"id\" IN ('section-open', 'section-overlap')");
        cancelSection("section-open");

        mvc.perform(put("/api/v1/sections/section-overlap")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schedules\":[{\"classroomId\":\"room-1\",\"dayOfWeek\":2,\"startTime\":\"07:30\",\"endTime\":\"09:00\"}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void reopeningSectionRevalidatesRoomConflicts() throws Exception {
        // While section-open is cancelled another section may take its slot;
        // reopening must not silently double-book the room.
        jdbc.update("UPDATE \"academic\".\"Section\" SET \"lecturerId\" = 'lecturer-1' WHERE \"id\" = 'section-open'");
        cancelSection("section-open");
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES ('sched-takeover', 'section-two', 'room-1', 2, '07:00', '09:00')");

        mvc.perform(put("/api/v1/sections/section-open")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
    }

    @Test
    void reopeningCancelledSectionReportsStudentConflictsAndHonoursAcknowledgement() throws Exception {
        // section-open's preserved seat belongs to a student who re-registers
        // during the cancel window. Move its room so only student-level
        // conflicts remain, and shrink the round cap so the reopen also
        // breaks the credit limit.
        jdbc.update("INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\", \"capacity\")"
                + " VALUES ('room-2', 'B', '202', 60)");
        jdbc.update("UPDATE \"academic\".\"SectionSchedule\" SET \"classroomId\" = 'room-2'"
                + " WHERE \"sectionId\" = 'section-open'");
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "reopen-conflict-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());
        cancelSection("section-open");
        // The student spends the freed slot on section-overlap — same day,
        // overlapping times — and the round cap no longer fits both courses.
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 4");
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "reopen-conflict-moved")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-overlap\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = 'student-1'",
                Integer.class)).isEqualTo(1);

        // Without an acknowledgement the reopen is refused with the
        // admin-only conflict detail: count, student id, reasons, peer
        // section ids.
        mvc.perform(put("/api/v1/sections/section-open")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REOPEN_CONFLICTS"))
                .andExpect(jsonPath("$.details.conflictCount").value(1))
                .andExpect(jsonPath("$.details.conflicts[0].studentId").value("student-1"))
                .andExpect(jsonPath("$.details.conflicts[0].reasons", org.hamcrest.Matchers
                        .containsInAnyOrder("CREDIT_CAP", "SCHEDULE_OVERLAP")))
                .andExpect(jsonPath("$.details.conflicts[0].sections[0]").value("section-overlap"));
        // An acknowledgement without a reason is still refused.
        mvc.perform(put("/api/v1/sections/section-open")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\",\"acknowledgeConflicts\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REOPEN_CONFLICTS"));

        // An explicit acknowledgement + reason reopens, lands on the audit
        // trail, and invalidates the student's persisted slip.
        mvc.perform(put("/api/v1/sections/section-open")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\",\"acknowledgeConflicts\":true,"
                                + "\"reason\":\"student re-registered during the cancel window\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_audit\".\"AdminAudit\""
                        + " WHERE \"entityId\" = 'section-open'"
                        + " AND \"action\" = 'SECTION_REOPEN_CONFLICTS_ACKNOWLEDGED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = 'student-1'",
                Integer.class)).isZero();
    }

    @Test
    void reopeningEvaluatesTheCapAgainstTheCurrentlyOpenRound() throws Exception {
        // Move the reopened section off the shared slot so the only possible
        // conflict is the credit cap, then let the student spend the freed
        // seat elsewhere while the section is cancelled.
        jdbc.update("UPDATE \"academic\".\"SectionSchedule\" SET \"dayOfWeek\" = 4, \"classroomId\" = 'room-1'"
                + " WHERE \"sectionId\" = 'section-open'");
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cap-round-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());
        cancelSection("section-open");
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "cap-round-moved")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-overlap\"}"))
                .andExpect(status().isOk());

        // The round the preserved enrollment was written under has since
        // closed, and its configured cap could no longer host both courses —
        // but the student won an approved limit under the round that is open
        // now, the same one an enroll attempt would evaluate.
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 4, \"status\" = 'CLOSED'"
                + " WHERE \"id\" = 'round-open'");
        jdbc.update("INSERT INTO \"academic\".\"CreditLimitApplication\""
                + " (\"id\", \"studentId\", \"semesterId\", \"roundId\", \"requestedLimit\", \"reason\", \"status\","
                + " \"reviewedBy\", \"reviewedAt\")"
                + " VALUES ('cap-app-1', 'student-1', 'semester-1', 'round-add-drop', 30,"
                + " 'documented overload', 'APPROVED', 'admin-1', CURRENT_TIMESTAMP)");

        mvc.perform(put("/api/v1/sections/section-open")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void enrollIsRejectedWhileSemesterIsClosed() throws Exception {
        // The semester lifecycle, not only the round row, gates registration:
        // a CLOSED semester must refuse enroll even while a round stays OPEN.
        mvc.perform(put("/api/v1/semesters/semester-1")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "semester-closed-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-two\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEMESTER_CLOSED"));

        // Reopening the semester restores registration without touching data.
        mvc.perform(put("/api/v1/semesters/semester-1")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "semester-reopen-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-two\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void reopeningIsRejectedWhileSemesterIsClosedButCancellingStillWorks() throws Exception {
        // Enroll first so the cancelled section carries a preserved seat.
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt())
                        .header("Idempotency-Key", "closed-semester-enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-two\"}"))
                .andExpect(status().isOk());
        cancelSection("section-two");

        mvc.perform(put("/api/v1/semesters/semester-1")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        // A reopen re-adds enrollment capacity, so it shares the
        // registration path's open-semester gate.
        mvc.perform(put("/api/v1/sections/section-two")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEMESTER_CLOSED"));

        // Cancelling only removes seats; it stays allowed in any semester.
        mvc.perform(put("/api/v1/sections/section-open")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void reopeningIsRejectedWhenAStoredTerminalStatusCarriesNonSpacePadding() throws Exception {
        // The terminal gate compares a normalized value, so a direct write
        // padding CLOSED with a tab, newline, CRLF or a Unicode space —
        // padding a database TRIM would not strip — still refuses the
        // reopen. Cancelling stays legal in every one of them.
        for (String stored : List.of("CLOSED\t", "\nCLOSED", "CLOSED\r\n",
                "CLOSED\u00A0", "CLOSED\u2007", "CLOSED\u202F")) {
            jdbc.update("UPDATE \"academic\".\"Semester\" SET \"status\" = ? WHERE \"id\" = 'semester-1'", stored);
            mvc.perform(put("/api/v1/sections/section-two")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"CANCELLED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            MvcResult refused = mvc.perform(put("/api/v1/sections/section-two")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"OPEN\"}"))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertThat(refused.getResponse().getContentAsString())
                    .as("reopen must refuse a stored terminal value padded with %s",
                            stored.replace("\t", "\\t").replace("\n", "\\n")
                                    .replace("\r", "\\r").replace("\u00A0", "<NBSP>")
                                    .replace("\u2007", "<U+2007>").replace("\u202F", "<U+202F>"))
                    .contains("\"code\":\"SEMESTER_CLOSED\"");

            jdbc.update("UPDATE \"academic\".\"Section\" SET \"status\" = 'OPEN' WHERE \"id\" = 'section-two'");
        }
    }

    @Test
    void schedulingSectionIntoInactiveClassroomIsRejected() throws Exception {
        deactivateRoom("room-1");

        mvc.perform(put("/api/v1/sections/section-two")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schedules\":[{\"classroomId\":\"room-1\",\"dayOfWeek\":3,\"startTime\":\"10:00\",\"endTime\":\"12:00\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASSROOM_INACTIVE"));
    }

    @Test
    void creatingSectionWithInactiveClassroomIsRejected() throws Exception {
        deactivateRoom("room-1");

        mvc.perform(post("/api/v1/sections")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionNumber\":\"09\",\"courseId\":\"course-two\",\"semesterId\":\"semester-1\",\"classroomId\":\"room-1\",\"capacity\":30}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASSROOM_INACTIVE"));
    }

    @Test
    void reopeningSectionWithDeactivatedRoomIsRejected() throws Exception {
        // Reopen revalidates the assignment: a section whose room was
        // deactivated while it was cancelled cannot silently come back online.
        // room-2 is conflict-free so the classroom check is what must fire.
        jdbc.update("INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\", \"capacity\", \"isActive\")"
                + " VALUES ('room-2', 'B', '202', 60, TRUE)");
        insertSection("section-room2", "05", "course-two", null);
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES ('sched-room2', 'section-room2', 'room-2', 5, '13:00', '15:00')");
        cancelSection("section-room2");
        deactivateRoom("room-2");

        mvc.perform(put("/api/v1/sections/section-room2")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASSROOM_INACTIVE"));
    }

    @Test
    void roomDeactivationLeavesExistingSchedulesUntouched() throws Exception {
        // Deactivating a room is non-destructive: stored schedules stay, and
        // the section can still be edited on unrelated fields.
        deactivateRoom("room-1");

        mvc.perform(put("/api/v1/sections/section-open")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":30}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"SectionSchedule\" WHERE \"sectionId\" = 'section-open'",
                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void adminCanReassignSectionToAnotherLecturer() throws Exception {
        // The recovery path for a deactivated lecturer is reassigning the
        // section to an active one; the admin update must accept that.
        insertLecturer("lecturer-2", "lecturer-user-2", "L002");

        mvc.perform(put("/api/v1/sections/section-two")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"lecturer-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturerId").value("lecturer-2"));
    }

    @Test
    void lecturerDeactivationReportsRemainingAssignments() throws Exception {
        // Deactivation is non-destructive (sections keep their lecturer), so
        // the response reports how many sections still point at them.
        jdbc.update("UPDATE \"academic\".\"Section\" SET \"lecturerId\" = 'lecturer-1' WHERE \"id\" = 'section-two'");

        mvc.perform(put("/api/v1/lecturers/lecturer-1")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedSectionCount").value(1));
    }

    private void deactivateRoom(String classroomId) throws Exception {
        mvc.perform(put("/api/v1/classrooms/" + classroomId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk());
    }

    private void insertLecturer(String id, String userId, String employeeId) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                userId, userId + "@campuscore.edu", "New", "Lecturer");
        jdbc.update(
                "INSERT INTO \"academic\".\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                id, userId, "department-1", employeeId, true);
    }

    private void cancelSection(String sectionId) throws Exception {
        mvc.perform(put("/api/v1/sections/" + sectionId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private void closeAllRounds() {
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"windowEnd\" = ?",
                localDateTime(Instant.parse("2021-01-01T00:00:00Z")));
    }

    private String enrollmentId(MvcResult enrolled) throws Exception {
        JsonNode body = objectMapper.readTree(enrolled.getResponse().getContentAsString());
        String id = body.get("id").asText();
        assertThat(id).isNotBlank();
        return id;
    }

    private void seedCompletedEnrollment(String id, String sectionId, String courseId, String letterGrade) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"letterGrade\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "student-1", sectionId, "semester-1", "COMPLETED", localDateTime(BASE_TIME),
                "PUBLISHED", courseId, letterGrade, 3, 0);
    }

    private void seedActiveEnrollment(String id, String sectionId, String courseId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "student-1", sectionId, "semester-1", "ENROLLED", localDateTime(BASE_TIME),
                "NOT_GRADED", courseId, 3, 0);
    }

    private RequestPostProcessor studentJwt() {
        return CurrentAccountJwt.currentJwt(jdbc, "student-user-1");
    }

    private RequestPostProcessor lecturerJwt() {
        return CurrentAccountJwt.currentJwt(jdbc, "lecturer-user-1");
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
                    "isActive" BOOLEAN NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                    "roomNumber" VARCHAR(120) NOT NULL,
                    "capacity" INTEGER NOT NULL DEFAULT 60,
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                    "registrationSource" VARCHAR(40),
                    "enrolledAt" TIMESTAMP NOT NULL,
                    "droppedAt" TIMESTAMP,
                    "gradeStatus" VARCHAR(40) NOT NULL,
                    "finalGrade" DECIMAL(5, 2),
                    "letterGrade" VARCHAR(10),
                    "courseId" VARCHAR(120) NOT NULL,
                    "roundId" VARCHAR(120),
                    "creditsSnapshot" INTEGER NOT NULL,
                    "activeCourseSlot" VARCHAR(120) AS (
                        CASE WHEN "status" IN ('ENROLLED', 'PENDING', 'CONFIRMED')
                             THEN "courseId" ELSE "id" END),
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    -- H2 has no filtered indexes; the generated slot mirrors the
                    -- PostgreSQL partial unique index
                    -- academic_enrollment_active_student_course_semester_uq.
                    UNIQUE ("studentId", "semesterId", "activeCourseSlot")
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
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."GradeItem" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "name" VARCHAR(200),
                    "type" VARCHAR(40) NOT NULL,
                    "maxScore" DECIMAL(5, 2),
                    "weight" DECIMAL(5, 2),
                    "gradedAt" TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."StudentGrade" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "enrollmentId" VARCHAR(120) NOT NULL,
                    "gradeItemId" VARCHAR(120) NOT NULL,
                    "score" DECIMAL(5, 2)
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
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"CreditLimitApplication\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationSlip\"");
        jdbc.update("DELETE FROM \"academic\".\"EnrollmentEvent\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationIdempotency\"");
        jdbc.update("DELETE FROM \"academic\".\"StudentGrade\"");
        jdbc.update("DELETE FROM \"academic\".\"GradeItem\"");
        jdbc.update("DELETE FROM \"academic\".\"CourseRequirement\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationRoundCohort\"");
        jdbc.update("DELETE FROM \"academic\".\"CurriculumCourse\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationRound\"");
        jdbc.update("DELETE FROM \"academic\".\"Attendance\"");
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
                "student-user-1", "student1@campuscore.edu", "An", "Student");
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                "lecturer-user-1", "lecturer1@campuscore.edu", "Lan", "Lecturer");
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "student-1", "student-user-1", "S001", "curriculum-1", 2, "ACTIVE", now, now, now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                "lecturer-1", "lecturer-user-1", "department-1", "L001", true);
        jdbc.update(
                "INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                "ay-2026", 2026, now, localDateTime(BASE_TIME.plusSeconds(31_536_000)), true, now, now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\", \"startDate\","
                        + " \"endDate\", \"status\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "semester-1", "Fall 2026", "Fall 2026", "Hoc ky Thu 2026", "FALL", "ay-2026",
                now, localDateTime(BASE_TIME.plusSeconds(10_368_000)), "ACTIVE", now, now);
        insertCourse("course-open", "SE402", "Web Application Development", 3);
        insertCourse("course-overlap", "SE403", "Systems", 3);
        insertCourse("course-two", "SE404", "Academic Writing", 2);
        insertSection("section-open", "01", "course-open", null);
        insertSection("section-overlap", "01", "course-overlap", null);
        insertSection("section-two", "01", "course-two", null);
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "sched-open", "section-open", "room-1", 2, "07:00", "09:30");
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "sched-overlap", "section-overlap", "room-1", 2, "08:00", "10:00");
        jdbc.update(
                "INSERT INTO \"academic\".\"RegistrationRound\""
                        + " (\"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\","
                        + " \"creditLimit\", \"version\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "round-open", "semester-1", "Registration", "REGISTRATION", "OPEN",
                localDateTime(Instant.parse("2020-01-01T00:00:00Z")),
                localDateTime(Instant.parse("2030-01-01T00:00:00Z")), 28, 0, now, now);
        jdbc.update(
                "INSERT INTO \"academic\".\"RegistrationRound\""
                        + " (\"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\","
                        + " \"creditLimit\", \"version\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "round-add-drop", "semester-1", "Add drop", "ADD_DROP", "OPEN",
                localDateTime(Instant.parse("2020-01-01T00:00:00Z")),
                localDateTime(Instant.parse("2030-01-01T00:00:00Z")), 28, 0, now, now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\", \"capacity\") VALUES (?, ?, ?, ?)",
                "room-1", "A", "101", 60);
        AccountAuthorityFixture.student(jdbc, "student-user-1", "student-1", 2);
        AccountAuthorityFixture.lecturer(jdbc, "lecturer-user-1", "lecturer-1");
        AccountAuthorityFixture.ensureUser(jdbc, "admin-user");
        AccountAuthorityFixture.replaceRoles(jdbc, "admin-user", "ADMIN");
    }

    private void insertCourse(String id, String code, String name, int credits) {
        LocalDateTime now = localDateTime(BASE_TIME);
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"credits\", \"departmentId\","
                        + " \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, name, name, name, credits, "department-1", "semester-1", true, now, now);
    }

    private void insertSection(String id, String sectionNumber, String courseId, String lecturerId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, sectionNumber, courseId, "semester-1", lecturerId, null, 35, 0, "OPEN", 0);
    }

    private static LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
