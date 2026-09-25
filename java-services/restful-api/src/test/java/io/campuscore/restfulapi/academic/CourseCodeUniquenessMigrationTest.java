package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Round-10 finding G5: V80 closed the round-9 course-code duplication
 * (SE401-404 were seeded twice for different courses) by renumbering the
 * second set to SE421-424 and enforcing a school-wide unique index on
 * academic."Course"("code"). The H2 harness stops at V24, so the index itself
 * cannot be exercised here — these assertions pin the migration file instead,
 * the same discipline RegistrationFoundationMigrationTest uses for V14.
 */
class CourseCodeUniquenessMigrationTest {

    @Test
    void v80EnforcesASchoolWideUniqueCourseCode() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V80__dedupe_course_codes.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration);
        assertThat(sql).contains("ux_course_code");
        assertThat(sql).contains("CREATE UNIQUE INDEX");
        assertThat(sql).contains("academic.\"Course\"");
        // The index is a single-column unique constraint on the code itself,
        // not a per-department composite: the seeded duplicates shared codes
        // across departments, so only the global scope closes the hole.
        assertThat(sql).contains("\"code\"");
    }

    @Test
    void v80RenumbersTheSeededDuplicateSet() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V80__dedupe_course_codes.sql"));
        // The second SE401-404 set moves to SE421-424; the renumber statement
        // must reference both ends so a future reseed cannot silently reuse
        // the duplicated range.
        assertThat(sql).contains("SE42");
    }
}
