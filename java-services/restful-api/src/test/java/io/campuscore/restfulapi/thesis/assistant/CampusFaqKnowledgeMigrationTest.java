package io.campuscore.restfulapi.thesis.assistant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the V87 FAQ knowledge fills: the five topics the production battery
 * showed either falling through to the fallback or paying the remote
 * round-trip (faculties/majors, exam schedule, tuition, graduation
 * requirements, dormitory). Guards the bilingual slugs, the governed release
 * projection statements, and the truthful-routing wording (docs must route to
 * the responsible office instead of inventing specifics).
 */
class CampusFaqKnowledgeMigrationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V87__campus_faq_knowledge_fills.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V87 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V87 seeds all 10 FAQ document slugs (5 topics x 2 locales)")
    void v87SeedsAllBilingualSlugs() {
        List<String> slugs = List.of(
                "campus-faculties-majors-vi", "campus-faculties-majors-en",
                "campus-exam-schedule-vi", "campus-exam-schedule-en",
                "campus-tuition-vi", "campus-tuition-en",
                "campus-graduation-conditions-vi", "campus-graduation-conditions-en",
                "campus-dormitory-vi", "campus-dormitory-en");
        for (String slug : slugs) {
            assertThat(sql)
                    .as("Migration must seed document slug: %s", slug)
                    .contains("'" + slug + "'");
        }
    }

    @Test
    @DisplayName("V87 configures the governed release projection and runtime pointer")
    void v87FollowsReleaseProjectionConventions() {
        assertThat(MIGRATION_PATH.getFileName().toString()).startsWith("V87__");
        assertThat(sql)
                .contains("INSERT INTO assistant.knowledge_document")
                .contains("INSERT INTO assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_release")
                .contains("INSERT INTO assistant.knowledge_runtime_document")
                .contains("INSERT INTO assistant.knowledge_runtime_state")
                // The release identity must match the migration version family
                // so corpus_version traces back to this seed.
                .contains("'local-demo-v87'")
                .contains("'00000000-0000-0000-0000-000000000087'::uuid");
    }

    @Test
    @DisplayName("Untracked facts route to offices instead of inventing specifics")
    void untrackedFactsRouteToResponsibleOffices() {
        // Tuition: the portal has no fee table — the doc must say so and route.
        assertThat(sql).contains("chưa hiển thị biểu phí");
        assertThat(sql).contains("does not display fee tables");
        // Exam schedule: no per-course timetable in the portal.
        assertThat(sql).contains("chưa hiển thị lịch thi chi tiết");
        assertThat(sql).contains("does not list per-course exam schedules");
        // Dormitory: allocation is out of portal scope.
        assertThat(sql).contains("không thực hiện trực tiếp trên cổng CampusCore");
        // Faculties: the seeded catalog data must be quoted verbatim (6 faculties).
        assertThat(sql).contains("Khoa Công nghệ Thông tin")
                .contains("Khoa Cơ khí Chế tạo máy")
                .contains("Khoa Điện - Điện tử")
                .contains("Khoa Kinh tế")
                .contains("Khoa Ngoại ngữ")
                .contains("Khoa Xây dựng");
    }
}
