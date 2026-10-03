package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Source contract of V97 (activate_course_prerequisites): the migration must
 * normalize the seeded requirement kinds to the enforced PREREQ/COREQ spell,
 * extend the prerequisite web, and publish the bilingual map document —
 * read against the file itself, mirroring the V43 contract-test convention.
 */
class V97CoursePrerequisiteMigrationTest {

    private static final Path MIGRATION_PATH =
            Path.of("src/main/resources/db/migration/V97__activate_course_prerequisites.sql");
    private static String sql;

    @BeforeAll
    static void loadMigrationSql() throws Exception {
        assertThat(MIGRATION_PATH)
                .as("Flyway migration V97 file must exist at expected path")
                .exists();
        sql = Files.readString(MIGRATION_PATH);
        assertThat(sql).isNotBlank();
    }

    @Test
    @DisplayName("V97 normalizes the dead PREREQUISITE spelling to the enforced PREREQ contract")
    void v97FixesRequirementKind() {
        assertThat(sql).contains("UPDATE academic.\"CourseRequirement\"");
        assertThat(sql).contains("SET \"kind\" = 'PREREQ'");
        assertThat(sql).contains("WHERE \"kind\" = 'PREREQUISITE'");
    }

    @Test
    @DisplayName("V97 extends the prerequisite web with demo-transcript-safe chains")
    void v97ExtendsThePrerequisiteWeb() {
        assertThat(sql).contains("INSERT INTO academic.\"CourseRequirement\"");
        assertThat(sql).contains("ON CONFLICT (\"courseId\", \"requiredCourseId\", \"kind\") DO NOTHING");
        // New ids live outside the V43 req-001..req-038 namespace.
        assertThat(sql).contains("'req-101'");
        assertThat(sql).doesNotContain("'req-001'");
        // Chain heads reference only courses the demo transcripts completed.
        List<String> allowedRequired = List.of("SE401", "SE402", "SE403", "SE404",
                "SE405", "SE406", "SE407", "SE408", "SE409", "SE410", "SE411", "SE412");
        for (int i = 101; i <= 131; i++) {
            int index = i;
            String line = sql.lines()
                    .filter(candidate -> candidate.contains("'req-" + index + "'"))
                    .findFirst()
                    .orElseThrow();
            String requiredCode = line.replaceAll(".*'(SE\\d+)'.*", "$1");
            assertThat(requiredCode)
                    .as("req-%d must require a demo-completed course", index)
                    .isIn(allowedRequired);
        }
    }

    @Test
    @DisplayName("V97 publishes the bilingual prerequisite-map knowledge release")
    void v97PublishesPrerequisiteMapKnowledge() {
        assertThat(sql).contains("catalog-prerequisite-map-vi");
        assertThat(sql).contains("catalog-prerequisite-map-en");
        assertThat(sql).contains("'campuscore-prerequisite-map'");
        assertThat(sql).contains("ACADEMIC_CATALOG");
        assertThat(sql).contains("00000000-0000-0000-0000-000000000097");
        assertThat(sql).contains("'local-demo-v97'");
        assertThat(sql).contains("INSERT INTO assistant.knowledge_runtime_state");
    }
}
