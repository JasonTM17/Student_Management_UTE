package io.campuscore.restfulapi.academic.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/** JDBC repository for academic attendance writes and mutations. */
@Repository
@Profile("persistence")
public class AcademicAttendanceWriteRepository {
    private static final String ATTENDANCE = "\"academic\".\"Attendance\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicAttendanceWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean sectionExists(String sectionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION + " WHERE \"id\" = :sectionId",
                new MapSqlParameterSource("sectionId", sectionId),
                Long.class);
        return count != null && count > 0;
    }

    public boolean isSectionOwnedByLecturer(String sectionId, String lecturerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION + " WHERE \"id\" = :sectionId AND \"lecturerId\" = :lecturerId",
                new MapSqlParameterSource()
                        .addValue("sectionId", sectionId)
                        .addValue("lecturerId", lecturerId),
                Long.class);
        return count != null && count > 0;
    }

    public Set<String> findActiveEnrolledStudentIds(String sectionId) {
        List<String> ids = jdbc.queryForList(
                "SELECT \"studentId\" FROM " + ENROLLMENT
                        + " WHERE \"sectionId\" = :sectionId AND \"status\" NOT IN ('DROPPED', 'CANCELLED')",
                new MapSqlParameterSource("sectionId", sectionId),
                String.class);
        return Set.copyOf(ids);
    }

    public void deleteAttendanceRecords(String sectionId, Instant date, List<String> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }
        jdbc.update(
                "DELETE FROM " + ATTENDANCE
                        + " WHERE \"sectionId\" = :sectionId AND \"date\" = :date AND \"studentId\" IN (:studentIds)",
                new MapSqlParameterSource()
                        .addValue("sectionId", sectionId)
                        .addValue("date", Timestamp.from(date))
                        .addValue("studentIds", studentIds));
    }

    public void insertAttendanceBatch(List<AttendanceInsertRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = rows.stream().map(row -> new MapSqlParameterSource()
                .addValue("id", row.id())
                .addValue("studentId", row.studentId())
                .addValue("sectionId", row.sectionId())
                .addValue("date", Timestamp.from(row.date()))
                .addValue("status", row.status())
                .addValue("notes", row.notes())
                .addValue("createdAt", Timestamp.from(row.createdAt()))
                .addValue("updatedAt", Timestamp.from(row.updatedAt())))
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate(
                "INSERT INTO " + ATTENDANCE + " (\"id\", \"studentId\", \"sectionId\", \"date\", \"status\", \"notes\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (:id, :studentId, :sectionId, :date, :status, :notes, :createdAt, :updatedAt)",
                batch);
    }

    public record AttendanceInsertRow(
            String id,
            String studentId,
            String sectionId,
            Instant date,
            String status,
            String notes,
            Instant createdAt,
            Instant updatedAt) {
    }
}
