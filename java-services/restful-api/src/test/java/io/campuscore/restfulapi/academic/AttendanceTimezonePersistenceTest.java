package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import io.campuscore.restfulapi.academic.repository.AcademicAttendanceReadRepository;
import io.campuscore.restfulapi.academic.repository.AcademicAttendanceWriteRepository;
import io.campuscore.restfulapi.academic.model.AttendanceInsertRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AttendanceTimezonePersistenceTest {
    @Test
    void timestampWithTimezoneStoresAndFiltersTheSameUtcDayInBangkokJvm() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"));
            String url = System.getProperty("attendance.test.jdbcUrl",
                    "jdbc:h2:mem:attendance_timezone;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, url.startsWith("jdbc:h2:") ? "sa" : "postgres", ""));
            // Keep the in-memory database alive across repository connections.
            if (url.startsWith("jdbc:h2:")) jdbc.execute("SET DB_CLOSE_DELAY -1");
            jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
            jdbc.execute("CREATE TABLE IF NOT EXISTS \"academic\".\"Attendance\" (\"id\" VARCHAR PRIMARY KEY, \"studentId\" VARCHAR, \"sectionId\" VARCHAR, \"date\" TIMESTAMP WITH TIME ZONE, \"status\" VARCHAR, \"notes\" VARCHAR, \"createdAt\" TIMESTAMP WITH TIME ZONE, \"updatedAt\" TIMESTAMP WITH TIME ZONE)");
            jdbc.execute("DELETE FROM \"academic\".\"Attendance\"");
            jdbc.execute("SET TIME ZONE 'Asia/Bangkok'");
            var named = new NamedParameterJdbcTemplate(jdbc);
            var writes = new AcademicAttendanceWriteRepository(named);
            var reads = new AcademicAttendanceReadRepository(named);
            Instant day = Instant.parse("2026-08-20T00:00:00Z");
            writes.insertAttendanceBatch(List.of(new AttendanceInsertRow("current", "student", "section", day, "PRESENT", null, day, day)));
            assertThat(jdbc.queryForObject("SELECT \"date\" FROM \"academic\".\"Attendance\" WHERE \"id\"='current'", OffsetDateTime.class).toInstant()).isEqualTo(day);
            jdbc.update("INSERT INTO \"academic\".\"Attendance\" (\"id\", \"studentId\", \"sectionId\", \"date\", \"status\") VALUES ('legacy', 'student', 'section', ?, 'ABSENT')", OffsetDateTime.parse("2026-08-20T23:30:00Z"));
            assertThat(reads.countAll("section", "student", day)).isEqualTo(2);
            assertThat(reads.sectionSummary("section").totalSessions()).isEqualTo(1);
            writes.deleteAttendanceRecords("section", day, List.of("student"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"Attendance\"", Integer.class)).isZero();
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
