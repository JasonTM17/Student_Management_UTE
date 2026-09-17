package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.academic.repository.AcademicReadRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * STUD-P1-1: curriculum progress must not treat an F-failed course as
 * COMPLETED. publishGrades stamps status='COMPLETED' on every graded row
 * (failures included), so the progress CASE requires a published passing
 * letter — the same condition its passed_grade/passed_letter siblings use.
 * Both cases below failed before the fix: the F row read back as level 2.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:course_progress_integrity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AcademicCourseProgressIntegrityTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AcademicReadRepository reads;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
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
                    "courseId" VARCHAR(120),
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        insertEnrollment("enrollment-failed", "COMPLETED", "PUBLISHED", "3.00", "F");
        insertEnrollment("enrollment-passed", "COMPLETED", "PUBLISHED", "8.00", "B");
        insertEnrollment("enrollment-active", "ENROLLED", "DRAFT", null, null);
        // Draft grades stay masked: only PUBLISHED letters may mark progress.
        insertEnrollment("enrollment-draft-pass", "COMPLETED", "DRAFT", "9.00", "A+");
    }

    @Test
    void failedCourseIsNotCurriculumProgressWhilePassingCourseIs() {
        Map<String, AcademicReadRepository.CourseProgressRow> byCourse =
                reads.findCourseProgressByStudentId("student-1").stream()
                        .collect(Collectors.toMap(
                                AcademicReadRepository.CourseProgressRow::courseId,
                                Function.identity()));

        AcademicReadRepository.CourseProgressRow failed = byCourse.get("course-failed");
        assertThat(failed.progressLevel()).as("an F row must not read as COMPLETED").isZero();
        assertThat(failed.letterGrade()).as("an F row has no passed letter").isNull();

        AcademicReadRepository.CourseProgressRow passed = byCourse.get("course-passed");
        assertThat(passed.progressLevel()).as("a published passing row stays COMPLETED")
                .isEqualTo(2);
        assertThat(passed.letterGrade()).isEqualTo("B");

        assertThat(byCourse.get("course-active").progressLevel()).isEqualTo(1);
        assertThat(byCourse.get("course-draft-pass").progressLevel())
                .as("draft grades must not complete a course")
                .isZero();
    }

    private void insertEnrollment(
            String id, String status, String gradeStatus, String finalGrade, String letterGrade) {
        String courseCode = id.substring("enrollment-".length());
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"droppedAt\","
                        + " \"gradeStatus\", \"finalGrade\", \"letterGrade\", \"courseId\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, 'student-1', ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?)",
                id,
                "section-" + courseCode,
                "semester-" + courseCode,
                status,
                timestamp(BASE_TIME),
                gradeStatus,
                finalGrade,
                letterGrade,
                "course-" + courseCode,
                timestamp(BASE_TIME),
                timestamp(BASE_TIME));
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(value, ZoneOffset.UTC));
    }
}
