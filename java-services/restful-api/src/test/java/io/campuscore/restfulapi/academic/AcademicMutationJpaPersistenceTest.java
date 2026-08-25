package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:academic_mutation_jpa;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AcademicMutationJpaPersistenceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-25T04:05:06Z");

    @Autowired
    private AcademicMutationService mutations;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "lecturerId" VARCHAR(120),
                    "capacity" INTEGER NOT NULL,
                    "enrolledCount" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "version" BIGINT NOT NULL DEFAULT 0
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "roundId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "enrolledAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "droppedAt" TIMESTAMP WITH TIME ZONE,
                    "gradeStatus" VARCHAR(40) NOT NULL,
                    "finalGrade" DECIMAL(5,2),
                    "letterGrade" VARCHAR(16),
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "version" BIGINT NOT NULL DEFAULT 0,
                    CONSTRAINT academic_mutation_enrollment_section_fk
                        FOREIGN KEY ("sectionId") REFERENCES "academic"."Section" ("id")
                )
                """);
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        insertSection("section-a", "lecturer-a", 30, 2);
        insertSection("section-b", "lecturer-b", 30, 1);
        insertEnrollment("enrollment-a", "student-a", "section-a");
        insertEnrollment("enrollment-b", "student-b", "section-a");
        insertEnrollment("enrollment-other", "student-c", "section-b");
    }

    @Test
    void deleteEnrollmentUsesJpaAndKeepsSectionCapacityConsistent() {
        mutations.deleteEnrollment("enrollment-a");

        assertThat(count("SELECT COUNT(*) FROM \"academic\".\"Enrollment\" WHERE \"id\" = 'enrollment-a'"))
                .isZero();
        assertThat(count("SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = 'section-a'"))
                .isEqualTo(1);
    }

    @Test
    void updateAndPublishGradesPersistThroughJpaAggregate() {
        mutations.updateGrades("section-a", "lecturer-a", false, List.of(
                new GradeUpdate("enrollment-b", new BigDecimal("7.50"), "b"),
                new GradeUpdate("enrollment-a", new BigDecimal("9.25"), "a")));

        assertThat(grade("enrollment-a"))
                .containsEntry("gradeStatus", "DRAFT")
                .containsEntry("letterGrade", "A")
                .containsEntry("finalGrade", new BigDecimal("9.25"));
        assertThat(grade("enrollment-b"))
                .containsEntry("gradeStatus", "DRAFT")
                .containsEntry("letterGrade", "B")
                .containsEntry("finalGrade", new BigDecimal("7.50"));

        mutations.publishGrades("section-a", "lecturer-a", false);

        assertThat(grade("enrollment-a"))
                .containsEntry("status", "COMPLETED")
                .containsEntry("gradeStatus", "PUBLISHED");
        assertThat(grade("enrollment-b"))
                .containsEntry("status", "COMPLETED")
                .containsEntry("gradeStatus", "PUBLISHED");
    }

    @Test
    void gradeBatchRollsBackWhenAnyEnrollmentBelongsToAnotherSection() {
        assertThatThrownBy(() -> mutations.updateGrades("section-a", "lecturer-a", false, List.of(
                new GradeUpdate("enrollment-a", new BigDecimal("8.00"), "B+"),
                new GradeUpdate("enrollment-other", new BigDecimal("6.00"), "C"))))
                .isInstanceOf(DomainException.class);

        assertThat(grade("enrollment-a"))
                .containsEntry("gradeStatus", "NOT_GRADED")
                .containsEntry("status", "ENROLLED")
                .containsEntry("finalGrade", null)
                .containsEntry("letterGrade", null);
    }

    private void insertSection(String id, String lecturerId, int capacity, int enrolledCount) {
        jdbc.update("""
                        INSERT INTO "academic"."Section"
                            ("id", "lecturerId", "capacity", "enrolledCount", "status", "version")
                        VALUES (?, ?, ?, ?, 'OPEN', 0)
                        """,
                id, lecturerId, capacity, enrolledCount);
    }

    private void insertEnrollment(String id, String studentId, String sectionId) {
        jdbc.update("""
                            INSERT INTO "academic"."Enrollment"
                            ("id", "studentId", "sectionId", "semesterId", "roundId", "status", "enrolledAt",
                             "gradeStatus", "createdAt", "updatedAt", "version")
                        VALUES (?, ?, ?, 'semester-a', 'round-a', 'ENROLLED', ?, 'NOT_GRADED', ?, ?, 0)
                        """,
                id, studentId, sectionId, FIXED_NOW, FIXED_NOW, FIXED_NOW);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private Map<String, Object> grade(String enrollmentId) {
        return jdbc.queryForMap("""
                        SELECT "status", "gradeStatus", "finalGrade", "letterGrade"
                        FROM "academic"."Enrollment" WHERE "id" = ?
                        """,
                enrollmentId);
    }
}
