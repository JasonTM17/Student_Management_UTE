package io.campuscore.restfulapi.thesis.assistant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the V90 credit-hour knowledge fill (round-2 sweep write-2): the
 * question "Một tín chỉ lý thuyết tương đương bao nhiêu tiết?" was answered
 * with the withdrawal/credit-load regulation instead of the hour-equivalence
 * fact. Guards the bilingual slugs, the governed release projection
 * statements, and the conservative hour wording (≈15 hours per theory credit;
 * 30–45 hours for practice — routed to the syllabus and Academic Affairs for
 * exact per-course numbers instead of inventing them).
 */
class CreditHourKnowledgeMigrationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V90__credit_hour_knowledge_fill.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V90 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V90 seeds the bilingual credit-hour slugs")
    void v90SeedsBilingualSlugs() {
        for (String slug : List.of(
                "campus-credit-hour-conversion-vi", "campus-credit-hour-conversion-en")) {
            assertThat(sql)
                    .as("Migration must seed document slug: %s", slug)
                    .contains("'" + slug + "'");
        }
    }

    @Test
    @DisplayName("V90 configures the governed release projection and runtime pointer")
    void v90FollowsReleaseProjectionConventions() {
        assertThat(MIGRATION_PATH.getFileName().toString()).startsWith("V90__");
        assertThat(sql)
                .contains("INSERT INTO assistant.knowledge_document")
                .contains("INSERT INTO assistant.knowledge_document_revision")
                .contains("INSERT INTO assistant.knowledge_release")
                .contains("INSERT INTO assistant.knowledge_runtime_document")
                .contains("INSERT INTO assistant.knowledge_runtime_state")
                // The release identity must match the migration version family
                // so corpus_version traces back to this seed.
                .contains("'local-demo-v90'")
                .contains("'00000000-0000-0000-0000-000000000090'::uuid");
    }

    @Test
    @DisplayName("Hour wording stays conservative and routes to the syllabus")
    void hourWordingStaysConservative() {
        // The credit-hour equivalence itself, per the credit-based regulation.
        assertThat(sql).contains("15 giờ học");
        assertThat(sql).contains("30–45 giờ");
        assertThat(sql).contains("about 15 hours of instruction");
        // Per-course numbers route to the syllabus / Academic Affairs, never invented.
        assertThat(sql).contains("đề cương học phần");
        assertThat(sql).contains("course syllabus");
    }
}
