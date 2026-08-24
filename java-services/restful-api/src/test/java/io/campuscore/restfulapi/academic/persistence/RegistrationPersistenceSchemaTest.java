package io.campuscore.restfulapi.academic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "spring.jpa.hibernate.ddl-auto=none"
})
class RegistrationPersistenceSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegistrationRoundRepository roundRepository;

    @Test
    void forwardMigrationsInstallRegistrationFoundation() {
        Integer rounds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = 'academic' AND LOWER(TABLE_NAME) = 'registrationround'",
                Integer.class);
        Integer operations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = 'academic' AND LOWER(TABLE_NAME) = 'enrollmentoperation'",
                Integer.class);
        assertThat(rounds).isEqualTo(1);
        assertThat(operations).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT MAX(CAST(VERSION AS INT)) FROM thesis.flyway_schema_history WHERE SUCCESS = TRUE",
                Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_SCHEMA) = 'academic' AND LOWER(TABLE_NAME) = 'registrationslip' AND LOWER(COLUMN_NAME) = 'snapshotpayload'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void typedRepositoryIsBoundToNewTableWithoutJdbcMutationPath() {
        assertThat(roundRepository.count()).isZero();
        List<RegistrationRoundEntity> rows = roundRepository.findBySemesterIdOrderByRegistrationStartDesc("missing");
        assertThat(rows).isEmpty();
    }

    @Test
    void idempotencyAndRequirementConstraintsRejectInvalidRows() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        jdbc.update("INSERT INTO academic.\"EnrollmentOperation\" (\"id\",\"studentId\",\"idempotencyKey\",\"canonicalRequestHash\",\"operationType\",\"state\") VALUES (?,?,?,?,?,?)",
                "op-1", "student-1", "key-1", hash, "ENROLL", "RESERVED");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academic.\"EnrollmentOperation\" (\"id\",\"studentId\",\"idempotencyKey\",\"canonicalRequestHash\",\"operationType\",\"state\") VALUES (?,?,?,?,?,?)",
                "op-2", "student-1", "key-1", hash, "ENROLL", "RESERVED"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academic.\"CourseRequirement\" (\"id\",\"courseId\",\"requiredCourseId\",\"requirementType\") VALUES (?,?,?,?)",
                "req-1", "course-1", "course-1", "PREREQUISITE"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void capacityAndIdempotencyRepositoriesDeclarePessimisticLockBoundary() throws Exception {
        assertThat(RegistrationRoundRepository.class.getMethod("findLockedById", String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(AcademicSectionRepository.class.getMethod("findLockedById", String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(EnrollmentOperationRepository.class
                .getMethod("findLockedByStudentIdAndIdempotencyKey", String.class, String.class)
                .getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
