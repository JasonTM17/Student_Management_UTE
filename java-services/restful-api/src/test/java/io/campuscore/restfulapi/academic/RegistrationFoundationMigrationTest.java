package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:registration_foundation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        })
class RegistrationFoundationMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postgresV14DeclaresUniqueActiveEnrollmentIndexes() throws Exception {
        Path script = Path.of("src/main/resources/db/migration/V14__registration_foundation.sql");
        String sql = Files.readString(script);
        assertThat(script.getFileName().toString()).startsWith("V14");
        assertThat(sql).contains("academic_enrollment_active_student_section_uq");
        assertThat(sql).contains("academic_enrollment_active_student_course_semester_uq");
        assertThat(sql).contains("RegistrationIdempotency");
        assertThat(sql).contains("RegistrationSlip");
        assertThat(sql).contains("creditLimit");
        assertThat(sql).contains("RAISE EXCEPTION");
    }

    @Test
    void h2V7AppliesAfterAssistantV1ToV6() {
        Integer rounds =
                jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"RegistrationRound\"", Integer.class);
        Integer keys =
                jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"RegistrationIdempotency\"", Integer.class);
        assertThat(rounds).isZero();
        assertThat(keys).isZero();
        Path h2V7 = Path.of("src/test/resources/db/migration-h2/V7__registration_foundation.sql");
        Path h2V1 = Path.of("src/test/resources/db/migration-h2/V1__create_thesis_schema.sql");
        Path h2V6 = Path.of("src/test/resources/db/migration-h2/V6__harden_academic_assistant.sql");
        assertThat(h2V7).exists();
        assertThat(h2V1).exists();
        assertThat(h2V6).exists();
    }
}
