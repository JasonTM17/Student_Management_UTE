package io.campuscore.restfulapi.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Migration validation test for Flyway V43:
 * V43__big_data_campus_ecosystem_enrichment.sql
 *
 * Verifies that the big data campus ecosystem enrichment migration is well-formed,
 * normalizes the 88 placeholder courses into authentic specialized curricula,
 * establishes academic prerequisites in CourseRequirement, adds specialized departments and lecturers,
 * seeds student conduct activities & semester evaluations, expands graduation thesis topics,
 * publishes rich campus announcements, and configures the V43 RAG release projection and runtime state pointer.
 */
class BigDataCampusEcosystemMigrationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V43__big_data_campus_ecosystem_enrichment.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V43 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V43 script follows naming convention and targets academic, thesis, and assistant schemas")
    void v43FollowsNamingAndSchemaConventions() {
        assertThat(MIGRATION_PATH.getFileName().toString())
                .isEqualTo("V43__big_data_campus_ecosystem_enrichment.sql")
                .startsWith("V43__");

        assertThat(sql)
                .contains("INSERT INTO academic.\"Department\"")
                .contains("UPDATE academic.\"Course\" SET")
                .contains("INSERT INTO academic.\"CourseRequirement\"")
                .contains("INSERT INTO academic.\"Lecturer\"")
                .contains("INSERT INTO academic.conduct_activity")
                .contains("INSERT INTO academic.conduct_semester_score")
                .contains("INSERT INTO thesis.thesis_topic")
                .contains("INSERT INTO thesis.thesis_topic_supervisor")
                .contains("INSERT INTO engagement.\"Announcement\"")
                .contains("INSERT INTO assistant.knowledge_document")
                .contains("INSERT INTO assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_release")
                .contains("INSERT INTO assistant.knowledge_runtime_document")
                .contains("INSERT INTO assistant.knowledge_runtime_state");
    }

    @Test
    @DisplayName("V43 contains all 16 required document slugs (8 topics x 2 locales)")
    void v43DeclaresAllRequiredSlugs() {
        List<String> expectedSlugs = List.of(
                "english-exit-benchmark-toeic-ielts-vi",
                "english-exit-benchmark-toeic-ielts-en",
                "it-skills-exit-benchmark-mos-ic3-vi",
                "it-skills-exit-benchmark-mos-ic3-en",
                "graduation-thesis-eligibility-defense-vi",
                "graduation-thesis-eligibility-defense-en",
                "enterprise-internship-ojt-procedure-vi",
                "enterprise-internship-ojt-procedure-en",
                "academic-scholarship-criteria-vi",
                "academic-scholarship-criteria-en",
                "exam-deferral-re-evaluation-rules-vi",
                "exam-deferral-re-evaluation-rules-en",
                "tuition-exemption-reduction-support-vi",
                "tuition-exemption-reduction-support-en",
                "dormitory-campus-amenities-services-vi",
                "dormitory-campus-amenities-services-en");

        for (String slug : expectedSlugs) {
            assertThat(sql)
                    .as("Migration must seed document slug: %s", slug)
                    .contains("'" + slug + "'");
        }
    }

    @Test
    @DisplayName("V43 normalizes placeholder courses into specialized courses")
    void v43NormalizesCourses() {
        assertThat(sql).contains("course-auto-013").contains("SE013").contains("Lập trình Web nâng cao với React & Node.js");
        assertThat(sql).contains("course-auto-026").contains("AI026").contains("Xử lý ngôn ngữ tự nhiên và Mô hình ngôn ngữ lớn LLM");
        assertThat(sql).contains("course-auto-036").contains("NET036").contains("Quản trị mạng doanh nghiệp Cisco CCNA");
        assertThat(sql).contains("course-auto-048").contains("EE048").contains("Kỹ thuật vi điều khiển và Hệ thống nhúng ARM");
        assertThat(sql).contains("course-auto-058").contains("ME058").contains("Robot công nghiệp và Cánh tay máy tự động");
        assertThat(sql).contains("course-auto-068").contains("EC068").contains("Phân tích dữ liệu kinh doanh với PowerBI");
        assertThat(sql).contains("course-auto-081").contains("CE081").contains("Mô hình thông tin công trình BIM với Revit");
        assertThat(sql).contains("course-auto-100").contains("INT100").contains("Đồ án Tốt nghiệp Kỹ sư đa ngành");
    }

    @Test
    @DisplayName("V43 seeds academic prerequisites in CourseRequirement")
    void v43SeedsCourseRequirements() {
        assertThat(sql).contains("req-001").contains("req-009").contains("req-038");
        assertThat(sql).contains("PREREQUISITE");
    }

    @Test
    @DisplayName("V43 activates release 00000000-0000-0000-0000-000000000043 and updates runtime state")
    void v43ActivatesRelease43() {
        assertThat(sql).contains("00000000-0000-0000-0000-000000000043");
        assertThat(sql).contains("'local-demo-v43'");
        assertThat(sql).contains("INSERT INTO assistant.knowledge_runtime_state");
        assertThat(sql).contains("active_release_id = EXCLUDED.active_release_id");
    }
}
