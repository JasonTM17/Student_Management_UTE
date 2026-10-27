package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.repository.AcademicReadRepository;
import io.campuscore.restfulapi.academic.service.AcademicSectionReadService;
import io.campuscore.restfulapi.thesis.service.ThesisEligibilityService;
import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
import io.campuscore.restfulapi.testsupport.CurrentAccountJwt;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real H2 persistence and authorization coverage for grade appeals/unpublish. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:grade_appeal_workflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "grade.appeal-window-days=7"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@Import(GradeAppealPersistenceTest.FixedClockConfiguration.class)
class GradeAppealPersistenceTest {

    private static final String STUDENT_USER = "appeal-user-student-1";
    private static final String OTHER_STUDENT_USER = "appeal-user-student-2";
    private static final String LECTURER_USER = "appeal-user-lecturer-1";
    private static final String PROFILELESS_LECTURER_USER = "appeal-user-lecturer-no-profile";
    private static final String OTHER_LECTURER_USER = "appeal-user-lecturer-2";
    private static final String ADMIN_USER = "appeal-user-admin";
    private static final String STUDENT = "appeal-student-1";
    private static final String OTHER_STUDENT = "appeal-student-2";
    private static final String LECTURER = "appeal-lecturer-1";
    private static final String OTHER_LECTURER = "appeal-lecturer-2";
    private static final String COURSE = "appeal-course";
    private static final String YEAR = "appeal-year";
    private static final String TERM = "appeal-term";
    private static final String SECTION = "appeal-section";
    private static final String OTHER_SECTION = "appeal-other-section";
    private static final String ENROLLMENT = "appeal-enrollment-1";
    private static final String OTHER_ENROLLMENT = "appeal-enrollment-2";
    private static final String PENDING_REASON = "Please review my final assessment and the marked answers.";
    private static final String REVIEW_REASON = "The corrected score matches the verified marking record.";
    private static final Instant NOW = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0,
            ZoneId.of("Asia/Bangkok")).toInstant();
    private static final Instant PUBLISHED_AT = NOW.minusSeconds(2 * 24 * 60 * 60L);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private javax.sql.DataSource dataSource;
    @Autowired private MockMvc mvc;
    @Autowired private AcademicEnrollmentReadService enrollmentReads;
    @Autowired private AcademicReadRepository academicReads;
    @Autowired private AcademicSectionReadService sectionReads;
    @Autowired private ThesisEligibilityService thesisEligibility;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void fixture() throws Exception {
        createSchemasAndTables();
        AccountAuthorityFixture.ensureSchema(jdbc);
        applyH2Migration();
        clearFixture();

        // Exercise the repository's H2 migration twin against pre-feature rows.
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"finalGrade\", \"letterGrade\")"
                        + " VALUES ('appeal-migration-published', ?, ?, ?, 'COMPLETED', ?, 'PUBLISHED', 8.00, 'B'),"
                        + " ('appeal-migration-pending', ?, ?, ?, 'COMPLETED', ?, 'APPEALED', 8.00, 'B'),"
                        + " ('appeal-migration-draft', ?, ?, ?, 'ENROLLED', ?, 'DRAFT', NULL, NULL)",
                STUDENT, SECTION, TERM, Timestamp.from(NOW), STUDENT, SECTION, TERM, Timestamp.from(NOW),
                STUDENT, SECTION, TERM, Timestamp.from(NOW));
        applyH2Migration();
        List<Timestamp> backfilled = jdbc.queryForList("SELECT \"gradePublishedAt\" FROM academic.\"Enrollment\""
                + " WHERE \"id\" IN ('appeal-migration-published', 'appeal-migration-pending') ORDER BY \"id\"",
                Timestamp.class);
        assertThat(backfilled).hasSize(2).allMatch(value -> value != null);
        Timestamp publishedBackfill = backfilled.get(0);
        applyH2Migration();
        assertThat(jdbc.queryForObject("SELECT \"gradePublishedAt\" FROM academic.\"Enrollment\""
                + " WHERE \"id\" = 'appeal-migration-published'", Timestamp.class)).isEqualTo(publishedBackfill);
        assertThat(jdbc.queryForObject("SELECT \"gradePublishedAt\" FROM academic.\"Enrollment\""
                + " WHERE \"id\" = 'appeal-migration-draft'", Timestamp.class)).isNull();
        jdbc.update("DELETE FROM academic.\"Enrollment\" WHERE \"id\" LIKE 'appeal-migration-%'");

        insertEnrollment(ENROLLMENT, STUDENT, SECTION, "PUBLISHED", PUBLISHED_AT, "B", new java.math.BigDecimal("8.00"));
        insertEnrollment(OTHER_ENROLLMENT, OTHER_STUDENT, SECTION, "PUBLISHED", PUBLISHED_AT, "B", new java.math.BigDecimal("8.00"));
        insertEnrollment("appeal-enrollment-other-section", STUDENT, OTHER_SECTION,
                "PUBLISHED", PUBLISHED_AT, "B", new java.math.BigDecimal("8.00"));
    }

    @Test
    void studentCanAppealOnlyOwnGradeAndLecturerOfTheSectionCanAcceptWithAuditAndTranscriptUpdate() throws Exception {
        String appealId = submit(ENROLLMENT, student());

        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                String.class, ENROLLMENT)).isEqualTo("APPEALED");
        assertThat(enrollmentReads.findStudentTranscript(STUDENT).summary().cumulativeGpa())
                .isEqualByComparingTo("3.00");
        assertThat(enrollmentReads.findStudentTranscript(STUDENT).summary().totalCreditsEarned()).isEqualTo(3);
        assertThat(enrollmentReads.findStudentTranscript(STUDENT).semesters().stream()
                .flatMap(semester -> semester.records().stream())
                .filter(record -> ENROLLMENT.equals(record.id())).findFirst().orElseThrow().gradeStatus())
                .isEqualTo("APPEALED");
        assertThat(thesisEligibility.evaluate(STUDENT).cpa()).isEqualByComparingTo("3.00");
        assertThat(thesisEligibility.evaluate(STUDENT).earnedCredits()).isEqualTo(3);
        assertThat(sectionReads.findLecturerGradingSections(LECTURER, TERM).stream()
                .filter(section -> SECTION.equals(section.id())).findFirst().orElseThrow().publishedCount()).isEqualTo(2);
        assertThat(academicReads.findCourseProgressByStudentId(STUDENT).stream()
                .filter(progress -> COURSE.equals(progress.courseId())).findFirst().orElseThrow().letterGrade())
                .isEqualTo("B");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE user_id=?"
                + " AND link='/dashboard/lecturer/grade-appeals'", Integer.class, LECTURER_USER)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"entityType\"='GradeAppeal' AND \"entityId\"=? AND \"action\"='GRADE_APPEAL_SUBMITTED'",
                Integer.class, appealId)).isEqualTo(1);

        mvc.perform(post("/api/v1/lecturer/grade-appeals/" + appealId + "/review")
                        .with(otherLecturer()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("ACCEPTED", "9.00", REVIEW_REASON)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_FORBIDDEN"));

        mvc.perform(post("/api/v1/lecturer/grade-appeals/" + appealId + "/review")
                        .with(lecturer()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("ACCEPTED", "9.00", REVIEW_REASON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.afterFinalGrade").value(9.0))
                .andExpect(jsonPath("$.afterLetterGrade").value("A+"));

        Map<String, Object> corrected = jdbc.queryForMap("SELECT \"gradeStatus\", \"finalGrade\", \"letterGrade\""
                + " FROM academic.\"Enrollment\" WHERE \"id\"=?", ENROLLMENT);
        assertThat(corrected).containsEntry("gradeStatus", "PUBLISHED").containsEntry("letterGrade", "A+");
        assertThat((java.math.BigDecimal) corrected.get("finalGrade")).isEqualByComparingTo("9.00");
        assertThat(enrollmentReads.findStudentTranscript(STUDENT).summary().cumulativeGpa())
                .isEqualByComparingTo("4.00");
        assertThat(enrollmentReads.findStudentTranscript(STUDENT).summary().totalCreditsEarned()).isEqualTo(3);
        assertThat(enrollmentReads.findTranscriptSummaries(List.of(STUDENT)).get(STUDENT).cumulativeGpa())
                .isEqualByComparingTo("4.00");
        assertThat(thesisEligibility.evaluate(STUDENT).cpa()).isEqualByComparingTo("4.00");
        assertThat(thesisEligibility.evaluate(STUDENT).earnedCredits()).isEqualTo(3);
        // OTHER_SECTION is a second attempt for the same course; the corrected
        // grade must become the best attempt rather than being averaged with it.
        assertThat(academicReads.findCourseProgressByStudentId(STUDENT).stream()
                .filter(progress -> COURSE.equals(progress.courseId())).findFirst().orElseThrow().letterGrade())
                .isEqualTo("A+");

        String auditAfter = jdbc.queryForObject("SELECT \"afterState\" FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"entityType\"='GradeAppeal' AND \"entityId\"=? AND \"action\"='GRADE_APPEAL_ACCEPTED'",
                String.class, appealId);
        assertThat(auditAfter).contains("9.00", "A+", "PUBLISHED");
        String auditBefore = jdbc.queryForObject("SELECT \"beforeState\" FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"entityType\"='GradeAppeal' AND \"entityId\"=? AND \"action\"='GRADE_APPEAL_ACCEPTED'",
                String.class, appealId);
        assertThat(auditBefore).contains("8.00", "B", "APPEALED", "PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE user_id=?"
                + " AND type='SUCCESS'", Integer.class, STUDENT_USER)).isEqualTo(1);

        mvc.perform(post("/api/v1/me/grade-appeals")
                        .with(student()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ENROLLMENT, PENDING_REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_ALREADY_SUBMITTED"));
    }

    @Test
    void lecturerWithoutProfileCannotReviewAppealWhenSectionHasNoLecturer() throws Exception {
        jdbc.update("UPDATE academic.\"Section\" SET \"lecturerId\"=NULL WHERE \"id\"=?", SECTION);
        String appealId = submit(ENROLLMENT, student());
        var request = post("/api/v1/lecturer/grade-appeals/" + appealId + "/review")
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody("ACCEPTED", "9.00", REVIEW_REASON));

        mvc.perform(request.with(profilelessLecturer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GRADE_APPEAL_FORBIDDEN"));
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES ('', ?, 'appeal-department', 'BLANK-ID', TRUE)", PROFILELESS_LECTURER_USER);
        mvc.perform(post("/api/v1/lecturer/grade-appeals/" + appealId + "/review")
                        .with(CurrentAccountJwt.fixtureJwt(jdbc, PROFILELESS_LECTURER_USER,
                                token -> token.claim("lecturerId", ""), "LECTURER"))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewBody("ACCEPTED", "9.00", REVIEW_REASON)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GRADE_APPEAL_FORBIDDEN"));
        assertThat(jdbc.queryForObject("SELECT \"status\" FROM academic.\"GradeAppeal\" WHERE \"id\"=?",
                String.class, appealId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT \"finalGrade\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                java.math.BigDecimal.class, ENROLLMENT)).isEqualByComparingTo("8.00");
    }

    @Test
    void appealWindowIncludesExactDaySevenInBangkokAndClosesImmediatelyAfter() throws Exception {
        Instant exactBoundary = NOW.minusSeconds(7 * 24 * 60 * 60L);
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"gradePublishedAt\"=? WHERE \"id\"=?",
                Timestamp.from(exactBoundary), ENROLLMENT);
        MvcResult eligibility = mvc.perform(get("/api/v1/me/grade-appeals/eligibility").with(student()))
                .andExpect(status().isOk()).andReturn();
        JsonNode eligibilityRows = objectMapper.readTree(eligibility.getResponse().getContentAsString());
        JsonNode target = null;
        for (JsonNode row : eligibilityRows) {
            if (ENROLLMENT.equals(row.path("enrollmentId").asText())) target = row;
        }
        assertThat(target).isNotNull();
        assertThat(target.path("appealAllowed").asBoolean()).isTrue();
        assertThat(target.path("appealUntil").asText())
                .isEqualTo(exactBoundary.plusSeconds(7 * 24 * 60 * 60L).toString());
        submit(ENROLLMENT, student());

        jdbc.update("DELETE FROM academic.\"GradeAppeal\"");
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"gradeStatus\"='PUBLISHED', \"gradePublishedAt\"=?"
                        + " WHERE \"id\"=?",
                Timestamp.from(exactBoundary.minusSeconds(1)), ENROLLMENT);
        mvc.perform(post("/api/v1/me/grade-appeals")
                        .with(student()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ENROLLMENT, PENDING_REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_WINDOW_CLOSED"));
    }

    @Test
    void withdrawnDroppedDraftWAndMissingTimestampGradesCannotBeAppealed() throws Exception {
        assertIneligibleAfter("UPDATE academic.\"Enrollment\" SET \"status\"='WITHDRAWN' WHERE \"id\"=?",
                "GRADE_APPEAL_ENROLLMENT_INELIGIBLE");
        assertIneligibleAfter("UPDATE academic.\"Enrollment\" SET \"status\"='DROPPED' WHERE \"id\"=?",
                "GRADE_APPEAL_ENROLLMENT_INELIGIBLE");
        assertIneligibleAfter("UPDATE academic.\"Enrollment\" SET \"status\"='COMPLETED', \"gradeStatus\"='DRAFT' WHERE \"id\"=?",
                "GRADE_APPEAL_ENROLLMENT_INELIGIBLE");
        assertIneligibleAfter("UPDATE academic.\"Enrollment\" SET \"finalGrade\"=NULL WHERE \"id\"=?",
                "GRADE_APPEAL_ENROLLMENT_INELIGIBLE");
        assertIneligibleAfter("UPDATE academic.\"Enrollment\" SET \"letterGrade\"=NULL WHERE \"id\"=?",
                "GRADE_APPEAL_ENROLLMENT_INELIGIBLE");
        assertIneligibleAfter("UPDATE academic.\"Enrollment\" SET \"gradeStatus\"='PUBLISHED', \"letterGrade\"='W' WHERE \"id\"=?",
                "GRADE_APPEAL_ENROLLMENT_INELIGIBLE");
        assertIneligibleAfter("UPDATE academic.\"Enrollment\" SET \"letterGrade\"='B', \"gradePublishedAt\"=NULL WHERE \"id\"=?",
                "GRADE_APPEAL_TIMESTAMP_UNAVAILABLE");
    }

    @Test
    void studentCannotAppealAnotherStudentsGradeAndPendingAppealBlocksEditAndUnpublish() throws Exception {
        mvc.perform(post("/api/v1/me/grade-appeals")
                        .with(otherStudent()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ENROLLMENT, PENDING_REASON)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_FORBIDDEN"));

        submit(ENROLLMENT, student());
        mvc.perform(put("/api/v1/sections/" + SECTION + "/grades")
                        .with(lecturer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[{\"enrollmentId\":\"" + ENROLLMENT
                                + "\",\"processScore\":8.0,\"finalExamScore\":9.0}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADES_PUBLISHED_LOCKED"));

        mvc.perform(post("/api/v1/admin/sections/" + SECTION + "/grades/unpublish")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody(List.of(ENROLLMENT), REVIEW_REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_UNPUBLISH_APPEAL_PENDING"));
    }

    @Test
    void studentCanCancelPendingAppealButCannotSubmitAnotherAndCancelRestoresPublished() throws Exception {
        String appealId = submit(ENROLLMENT, student());
        mvc.perform(post("/api/v1/me/grade-appeals/" + appealId + "/cancel").with(student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                String.class, ENROLLMENT)).isEqualTo("PUBLISHED");
        mvc.perform(post("/api/v1/me/grade-appeals")
                        .with(student()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ENROLLMENT, PENDING_REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_ALREADY_SUBMITTED"));
    }

    @Test
    void adminMayResolveAnyAppealAndMayUnpublishSelectedPublishedGradeWithReason() throws Exception {
        String appealId = submit(ENROLLMENT, student());
        mvc.perform(post("/api/v1/admin/grade-appeals/" + appealId + "/review")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("REJECTED", null, REVIEW_REASON)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                String.class, ENROLLMENT)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE user_id=?"
                + " AND type='WARNING'", Integer.class, STUDENT_USER)).isEqualTo(1);

        mvc.perform(post("/api/v1/admin/sections/" + SECTION + "/grades/unpublish")
                        .with(lecturer()).contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody(List.of(ENROLLMENT), REVIEW_REASON)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/sections/" + SECTION + "/grades/unpublish")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody(List.of(ENROLLMENT), REVIEW_REASON)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unpublishedCount").value(1));
        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                String.class, ENROLLMENT)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT \"gradePublishedAt\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                Timestamp.class, ENROLLMENT)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE user_id=?"
                + " AND type='GRADE_UNPUBLISH'", Integer.class, STUDENT_USER)).isEqualTo(1);
    }

    @Test
    void unpublishAllLeavesWithdrawnWGradePublishedAndUnpublishesGradeableEnrollments() throws Exception {
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"status\"='WITHDRAWN', \"letterGrade\"='W'"
                + " WHERE \"id\"=?", ENROLLMENT);

        mvc.perform(post("/api/v1/admin/sections/" + SECTION + "/grades/unpublish")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody(List.of(), REVIEW_REASON)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unpublishedCount").value(1));

        Map<String, Object> withdrawn = jdbc.queryForMap("SELECT \"status\", \"gradeStatus\", \"letterGrade\","
                + " \"gradePublishedAt\" FROM academic.\"Enrollment\" WHERE \"id\"=?", ENROLLMENT);
        assertThat(withdrawn).containsEntry("status", "WITHDRAWN")
                .containsEntry("gradeStatus", "PUBLISHED").containsEntry("letterGrade", "W");
        assertThat(((OffsetDateTime) withdrawn.get("gradePublishedAt")).toInstant()).isEqualTo(PUBLISHED_AT);
        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                String.class, OTHER_ENROLLMENT)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"entityType\"='Enrollment' AND \"action\"='GRADE_UNPUBLISHED'"
                + " AND \"entityId\"=?", Integer.class, ENROLLMENT)).isZero();
    }

    @Test
    void explicitlySelectedWithdrawnGradeCannotBeUnpublished() throws Exception {
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"status\"='WITHDRAWN', \"letterGrade\"='W'"
                + " WHERE \"id\"=?", ENROLLMENT);

        mvc.perform(post("/api/v1/admin/sections/" + SECTION + "/grades/unpublish")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody(List.of(ENROLLMENT), REVIEW_REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_UNPUBLISH_CONFLICT"));

        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                String.class, ENROLLMENT)).isEqualTo("PUBLISHED");
    }

    @Test
    void adminCanAcceptWhenSectionHasNoLecturerAndUnpublishAllWithAudit() throws Exception {
        jdbc.update("UPDATE academic.\"Section\" SET \"lecturerId\"=NULL WHERE \"id\"=?", SECTION);
        String appealId = submit(ENROLLMENT, student());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE user_id=?"
                + " AND link='/admin/grade-appeals' AND type='GRADE_APPEAL'", Integer.class, ADMIN_USER)).isEqualTo(1);

        mvc.perform(post("/api/v1/admin/grade-appeals/" + appealId + "/review")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("ACCEPTED", "9.50", REVIEW_REASON)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.afterFinalGrade").value(9.5))
                .andExpect(jsonPath("$.afterLetterGrade").value("A+"));

        mvc.perform(post("/api/v1/admin/sections/" + SECTION + "/grades/unpublish")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody(List.of(), REVIEW_REASON)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_CORRECTION_LOCKED"));
        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                String.class, ENROLLMENT)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT \"finalGrade\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                java.math.BigDecimal.class, ENROLLMENT)).isEqualByComparingTo("9.50");
        assertThat(jdbc.queryForObject("SELECT \"gradePublishedAt\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                Timestamp.class, ENROLLMENT)).isEqualTo(Timestamp.from(PUBLISHED_AT));

        mvc.perform(post("/api/v1/admin/sections/" + SECTION + "/grades/unpublish")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody(List.of(OTHER_ENROLLMENT), REVIEW_REASON)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unpublishedCount").value(1));
        assertThat(jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=?",
                String.class, OTHER_ENROLLMENT)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"entityType\"='Enrollment' AND \"action\"='GRADE_UNPUBLISHED'"
                + " AND \"entityId\"=?", Integer.class, OTHER_ENROLLMENT)).isEqualTo(1);
        String unpublishAudit = jdbc.queryForObject("SELECT \"afterState\" FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"entityType\"='Enrollment' AND \"action\"='GRADE_UNPUBLISHED'"
                + " AND \"entityId\"=?", String.class, OTHER_ENROLLMENT);
        assertThat(unpublishAudit).contains("DRAFT", REVIEW_REASON, "cleared");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE user_id IN (?, ?)"
                + " AND type='GRADE_UNPUBLISH'", Integer.class, STUDENT_USER, OTHER_STUDENT_USER)).isEqualTo(1);
    }

    @Test
    void studentCannotReviewOwnAppealWhenAlsoAssignedAsSectionLecturer() throws Exception {
        AccountAuthorityFixture.lecturer(jdbc, STUDENT_USER, "appeal-self-lecturer");
        jdbc.update("INSERT INTO academic.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\")"
                + " VALUES ('appeal-self-lecturer', ?, 'appeal-department', 'SELF-LEC')", STUDENT_USER);
        jdbc.update("UPDATE academic.\"Section\" SET \"lecturerId\"='appeal-self-lecturer' WHERE \"id\"=?", SECTION);
        String appealId = submit(ENROLLMENT, student());

        mvc.perform(post("/api/v1/lecturer/grade-appeals/" + appealId + "/review")
                        .with(CurrentAccountJwt.fixtureJwt(jdbc, STUDENT_USER, "LECTURER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("REJECTED", null, REVIEW_REASON)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_FORBIDDEN"));
        assertThat(jdbc.queryForObject("SELECT \"status\" FROM academic.\"GradeAppeal\" WHERE \"id\"=?",
                String.class, appealId)).isEqualTo("PENDING");
    }

    @Test
    void validationAndRoleBoundariesReturnStableErrors() throws Exception {
        mvc.perform(post("/api/v1/me/grade-appeals")
                        .with(student()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ENROLLMENT, "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_REASON_INVALID"));
        mvc.perform(get("/api/v1/admin/grade-appeals").with(student()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/lecturer/grade-appeals/missing/review")
                        .with(lecturer()).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("MAYBE", null, REVIEW_REASON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRADE_APPEAL_DECISION_INVALID"));
        mvc.perform(post("/api/v1/admin/sections/" + SECTION + "/grades/unpublish")
                        .with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody(List.of(OTHER_ENROLLMENT), "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRADE_UNPUBLISH_REASON_INVALID"));
    }

    private void createSchemasAndTables() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS academic");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS campuscore_audit");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_audit.\"AdminAudit\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY, \"actorId\" VARCHAR(120), \"actorLabel\" VARCHAR(240),"
                + " \"action\" VARCHAR(48) NOT NULL, \"entityType\" VARCHAR(48) NOT NULL, \"entityId\" VARCHAR(120),"
                + " \"summary\" VARCHAR(500), \"beforeState\" TEXT, \"afterState\" TEXT,"
                + " \"createdAt\" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS notifications.notification (id VARCHAR(64) PRIMARY KEY,"
                + " user_id VARCHAR(64) NOT NULL, title VARCHAR(240) NOT NULL, message VARCHAR(2000) NOT NULL,"
                + " type VARCHAR(16) NOT NULL, link VARCHAR(240), announcement_id VARCHAR(120), source_key VARCHAR(160),"
                + " is_read BOOLEAN DEFAULT FALSE, read_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Student\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"userId\" VARCHAR(120), \"studentId\" VARCHAR(64), \"status\" VARCHAR(40))");
        jdbc.execute("ALTER TABLE academic.\"Student\" ADD COLUMN IF NOT EXISTS \"curriculumId\" VARCHAR(120)");
        jdbc.execute("ALTER TABLE academic.\"Student\" ADD COLUMN IF NOT EXISTS \"year\" INTEGER");
        jdbc.execute("ALTER TABLE academic.\"Student\" ADD COLUMN IF NOT EXISTS \"admissionDate\" TIMESTAMP WITH TIME ZONE");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Lecturer\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"userId\" VARCHAR(120), \"employeeId\" VARCHAR(64))");
        jdbc.execute("ALTER TABLE academic.\"Lecturer\" ADD COLUMN IF NOT EXISTS \"departmentId\" VARCHAR(120)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"User\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"email\" VARCHAR(320), \"firstName\" VARCHAR(120), \"lastName\" VARCHAR(120), \"status\" VARCHAR(40))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Faculty\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"name\" VARCHAR(180), \"code\" VARCHAR(40))");
        jdbc.execute("ALTER TABLE academic.\"Faculty\" ADD COLUMN IF NOT EXISTS \"code\" VARCHAR(40)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Department\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"name\" VARCHAR(180), \"code\" VARCHAR(40), \"facultyId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Curriculum\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"name\" VARCHAR(180), \"code\" VARCHAR(40), \"departmentId\" VARCHAR(120), \"academicYearId\" VARCHAR(120))");
        jdbc.execute("ALTER TABLE academic.\"Curriculum\" ADD COLUMN IF NOT EXISTS \"academicYearId\" VARCHAR(120)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"AcademicYear\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"year\" INTEGER, \"startDate\" TIMESTAMP, \"endDate\" TIMESTAMP, \"isCurrent\" BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Semester\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"name\" VARCHAR(120), \"nameEn\" VARCHAR(120), \"nameVi\" VARCHAR(120),"
                + " \"type\" VARCHAR(40), \"status\" VARCHAR(40), \"startDate\" TIMESTAMP, \"endDate\" TIMESTAMP,"
                + " \"academicYearId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Course\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"code\" VARCHAR(40), \"name\" VARCHAR(240), \"nameEn\" VARCHAR(240),"
                + " \"nameVi\" VARCHAR(240), \"credits\" INTEGER)");
        jdbc.execute("ALTER TABLE academic.\"Course\" ADD COLUMN IF NOT EXISTS \"departmentId\" VARCHAR(120)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Section\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"courseId\" VARCHAR(120), \"semesterId\" VARCHAR(120), \"lecturerId\" VARCHAR(120),"
                + " \"sectionNumber\" VARCHAR(40), \"capacity\" INT, \"enrolledCount\" INT, \"status\" VARCHAR(40))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Enrollment\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"studentId\" VARCHAR(120) NOT NULL, \"sectionId\" VARCHAR(120) NOT NULL,"
                + " \"semesterId\" VARCHAR(120) NOT NULL, \"courseId\" VARCHAR(120), \"status\" VARCHAR(40) NOT NULL,"
                + " \"enrolledAt\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, \"droppedAt\" TIMESTAMP,"
                + " \"gradeStatus\" VARCHAR(40) NOT NULL DEFAULT 'NOT_GRADED', \"finalGrade\" DECIMAL(5,2),"
                + " \"letterGrade\" VARCHAR(10), \"gradePublishedAt\" TIMESTAMP WITH TIME ZONE,"
                + " \"createdAt\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP, \"updatedAt\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("ALTER TABLE academic.\"Enrollment\" ADD COLUMN IF NOT EXISTS \"courseId\" VARCHAR(120)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"GradeItem\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"sectionId\" VARCHAR(120), \"name\" VARCHAR(240), \"type\" VARCHAR(40),"
                + " \"maxScore\" DECIMAL(5,2), \"weight\" DECIMAL(5,2), \"gradedAt\" TIMESTAMP,"
                + " \"createdAt\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"StudentGrade\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"enrollmentId\" VARCHAR(120), \"gradeItemId\" VARCHAR(120), \"score\" DECIMAL(5,2))");
    }

    private void clearFixture() {
        jdbc.update("DELETE FROM academic.\"GradeAppeal\" WHERE \"enrollmentId\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"StudentGrade\" WHERE \"enrollmentId\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"GradeItem\" WHERE \"sectionId\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Enrollment\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Section\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Course\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Semester\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Student\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Lecturer\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Curriculum\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"AcademicYear\" WHERE \"id\" LIKE 'appeal-%'");
        jdbc.update("DELETE FROM academic.\"User\" WHERE \"id\" LIKE 'appeal-user-%'");
        jdbc.update("DELETE FROM notifications.notification WHERE user_id LIKE 'appeal-%'");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'appeal-%'");
        AccountAuthorityFixture.ensureSchema(jdbc);
        for (String user : List.of(STUDENT_USER, OTHER_STUDENT_USER, LECTURER_USER,
                OTHER_LECTURER_USER, ADMIN_USER)) AccountAuthorityFixture.ensureUser(jdbc, user);
        AccountAuthorityFixture.student(jdbc, STUDENT_USER, STUDENT, 3);
        AccountAuthorityFixture.student(jdbc, OTHER_STUDENT_USER, OTHER_STUDENT, 3);
        AccountAuthorityFixture.lecturer(jdbc, LECTURER_USER, LECTURER);
        AccountAuthorityFixture.lecturer(jdbc, OTHER_LECTURER_USER, OTHER_LECTURER);
        AccountAuthorityFixture.ensureUser(jdbc, PROFILELESS_LECTURER_USER);
        AccountAuthorityFixture.role(jdbc, PROFILELESS_LECTURER_USER, "LECTURER");
        AccountAuthorityFixture.role(jdbc, ADMIN_USER, "ADMIN");
        jdbc.update("INSERT INTO academic.\"User\" (\"id\", \"email\", \"firstName\", \"lastName\", \"status\")"
                        + " VALUES (?, ?, 'Student', 'One', 'ACTIVE'), (?, ?, 'Student', 'Two', 'ACTIVE'),"
                + " (?, ?, 'Lecturer', 'One', 'ACTIVE'), (?, ?, 'Lecturer', 'Two', 'ACTIVE'),"
                + " (?, ?, 'Lecturer', 'No Profile', 'ACTIVE'), (?, ?, 'Admin', 'One', 'ACTIVE')",
                STUDENT_USER, STUDENT_USER + "@fixture.invalid", OTHER_STUDENT_USER, OTHER_STUDENT_USER + "@fixture.invalid",
                LECTURER_USER, LECTURER_USER + "@fixture.invalid", OTHER_LECTURER_USER, OTHER_LECTURER_USER + "@fixture.invalid",
                PROFILELESS_LECTURER_USER, PROFILELESS_LECTURER_USER + "@fixture.invalid",
                ADMIN_USER, ADMIN_USER + "@fixture.invalid");
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"code\") VALUES ('appeal-faculty', 'Faculty', 'AP-FAC')");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\")"
                + " VALUES ('appeal-department', 'Department', 'AP-DEP', 'appeal-faculty')");
        jdbc.update("INSERT INTO academic.\"AcademicYear\" (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\")"
                        + " VALUES (?, 2026, ?, ?, TRUE)",
                YEAR, Timestamp.from(NOW.minusSeconds(180 * 24 * 60 * 60L)),
                Timestamp.from(NOW.plusSeconds(180 * 24 * 60 * 60L)));
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"code\", \"departmentId\", \"academicYearId\")"
                + " VALUES ('appeal-curriculum', 'Curriculum', 'AP-CUR', 'appeal-department', ?)", YEAR);
        jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\", \"admissionDate\")"
                        + " VALUES (?, ?, 'AP-1', 'appeal-curriculum', 3, 'ACTIVE', ?),"
                        + " (?, ?, 'AP-2', 'appeal-curriculum', 3, 'ACTIVE', ?)",
                STUDENT, STUDENT_USER, Timestamp.from(NOW), OTHER_STUDENT, OTHER_STUDENT_USER, Timestamp.from(NOW));
        jdbc.update("INSERT INTO academic.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\")"
                        + " VALUES (?, ?, 'appeal-department', 'LEC-1'), (?, ?, 'appeal-department', 'LEC-2')",
                LECTURER, LECTURER_USER, OTHER_LECTURER, OTHER_LECTURER_USER);
        jdbc.update("INSERT INTO academic.\"Semester\" (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\", \"startDate\", \"endDate\", \"status\")"
                        + " VALUES (?, 'Appeal Term', 'Appeal Term', 'Học kỳ phúc khảo', 'FIRST', ?, ?, ?, 'OPEN')",
                TERM, YEAR, Timestamp.from(NOW.minusSeconds(20 * 24 * 60 * 60L)),
                Timestamp.from(NOW.plusSeconds(70 * 24 * 60 * 60L)));
        jdbc.update("INSERT INTO academic.\"Course\" (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"credits\", \"departmentId\")"
                + " VALUES (?, 'AP-101', 'Appeal Course', 'Appeal Course', 'Môn phúc khảo', 3, 'appeal-department')", COURSE);
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\", \"courseId\", \"semesterId\", \"lecturerId\", \"sectionNumber\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, ?, 'S1', 30, 2, 'OPEN'), (?, ?, ?, ?, 'S2', 30, 1, 'OPEN')",
                SECTION, COURSE, TERM, LECTURER, OTHER_SECTION, COURSE, TERM, OTHER_LECTURER);
    }

    private void applyH2Migration() {
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration-h2/V147__grade_appeal_and_unpublish.sql")).execute(dataSource);
    }

    private void insertEnrollment(String id, String studentId, String sectionId, String gradeStatus,
            Instant publishedAt, String letter, java.math.BigDecimal score) {
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"courseId\", \"status\","
                        + " \"enrolledAt\", \"gradeStatus\", \"finalGrade\", \"letterGrade\", \"gradePublishedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, studentFor(id), sectionId, TERM, COURSE, "COMPLETED", Timestamp.from(NOW), gradeStatus, score, letter,
                publishedAt == null ? null : Timestamp.from(publishedAt));
    }

    private void assertIneligibleAfter(String updateSql, String code) throws Exception {
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"status\"='COMPLETED', \"gradeStatus\"='PUBLISHED',"
                + " \"finalGrade\"=8.00, \"letterGrade\"='B', \"gradePublishedAt\"=? WHERE \"id\"=?",
                Timestamp.from(PUBLISHED_AT), ENROLLMENT);
        jdbc.update(updateSql, ENROLLMENT);
        MvcResult eligibility = mvc.perform(get("/api/v1/me/grade-appeals/eligibility").with(student()))
                .andExpect(status().isOk()).andReturn();
        boolean offered = false;
        for (JsonNode row : objectMapper.readTree(eligibility.getResponse().getContentAsString())) {
            if (ENROLLMENT.equals(row.path("enrollmentId").asText())) {
                offered = row.path("appealAllowed").asBoolean();
            }
        }
        assertThat(offered).as("ineligible grade must not expose the appeal action").isFalse();
        mvc.perform(post("/api/v1/me/grade-appeals").with(student()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ENROLLMENT, PENDING_REASON)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));
    }

    private String studentFor(String enrollmentId) {
        return OTHER_ENROLLMENT.equals(enrollmentId) ? OTHER_STUDENT : STUDENT;
    }

    private String submit(String enrollmentId, RequestPostProcessor principal) throws Exception {
        return mvc.perform(post("/api/v1/me/grade-appeals").with(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(enrollmentId, PENDING_REASON)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");
    }

    private static String createBody(String enrollmentId, String reason) {
        return "{\"enrollmentId\":\"" + enrollmentId + "\",\"reason\":\"" + reason + "\"}";
    }

    private static String reviewBody(String decision, String score, String reason) {
        return "{\"decision\":\"" + decision + "\",\"correctedFinalGrade\":"
                + (score == null ? "null" : score) + ",\"reason\":\"" + reason + "\"}";
    }

    private static String unpublishBody(List<String> ids, String reason) {
        String enrollmentIds = ids.isEmpty() ? "[]" : "[\"" + String.join("\",\"", ids) + "\"]";
        return "{\"enrollmentIds\":" + enrollmentIds + ",\"reason\":\"" + reason + "\"}";
    }

    private RequestPostProcessor student() { return CurrentAccountJwt.fixtureJwt(jdbc, STUDENT_USER, "STUDENT"); }
    private RequestPostProcessor otherStudent() { return CurrentAccountJwt.fixtureJwt(jdbc, OTHER_STUDENT_USER, "STUDENT"); }
    private RequestPostProcessor lecturer() { return CurrentAccountJwt.fixtureJwt(jdbc, LECTURER_USER, "LECTURER"); }
    private RequestPostProcessor profilelessLecturer() {
        return CurrentAccountJwt.fixtureJwt(jdbc, PROFILELESS_LECTURER_USER, "LECTURER");
    }
    private RequestPostProcessor otherLecturer() { return CurrentAccountJwt.fixtureJwt(jdbc, OTHER_LECTURER_USER, "LECTURER"); }
    private RequestPostProcessor admin() { return CurrentAccountJwt.fixtureJwt(jdbc, ADMIN_USER, "ADMIN"); }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedAppealClock() {
            return Clock.fixed(NOW, ZoneId.of("UTC"));
        }
    }
}
