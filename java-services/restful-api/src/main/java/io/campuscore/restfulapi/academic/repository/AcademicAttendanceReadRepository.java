package io.campuscore.restfulapi.academic.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC adapter for academic attendance tables. */
@Repository
@Profile("persistence")
public class AcademicAttendanceReadRepository {
    private static final String ATTENDANCE = "\"academic\".\"Attendance\"";
    private static final String STUDENT = "\"academic\".\"Student\"";
    private static final String USER = "\"academic\".\"User\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String COURSE = "\"academic\".\"Course\"";

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicAttendanceReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AttendanceRow> findAll(long offset, int limit, String sectionId, String studentId, Instant date) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        String where = attendanceFilters(parameters, sectionId, studentId, date);
        return jdbc.query(attendanceSelect() + where
                        + " ORDER BY a.\"date\" DESC, a.\"id\" ASC LIMIT :limit OFFSET :offset",
                parameters,
                AcademicAttendanceReadRepository::mapAttendance);
    }

    public long countAll(String sectionId, String studentId, Instant date) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = attendanceFilters(parameters, sectionId, studentId, date);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ATTENDANCE + " a" + where,
                parameters,
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public List<AttendanceRow> findStudentAttendance(String studentId, String sectionId, String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("studentId", studentId);
        StringBuilder where = new StringBuilder(" WHERE a.\"studentId\" = :studentId");
        if (sectionId != null) {
            parameters.addValue("sectionId", sectionId);
            where.append(" AND a.\"sectionId\" = :sectionId");
        } else if (semesterId != null) {
            parameters.addValue("semesterId", semesterId);
            where.append(" AND section.\"semesterId\" = :semesterId");
        }
        return jdbc.query(attendanceSelect() + where + " ORDER BY a.\"date\" DESC, a.\"id\" ASC",
                parameters,
                AcademicAttendanceReadRepository::mapAttendance);
    }

    public List<StudentSummaryRow> studentSummary(String studentId, String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("studentId", studentId);
        String semesterFilter = "";
        if (semesterId != null) {
            parameters.addValue("semesterId", semesterId);
            semesterFilter = " AND section.\"semesterId\" = :semesterId";
        }
        return jdbc.query(
                "SELECT a.\"sectionId\", course.\"code\", course.\"name\", course.\"nameEn\", course.\"nameVi\","
                        + " COUNT(*) AS total,"
                        + " SUM(CASE WHEN a.\"status\" = 'PRESENT' THEN 1 ELSE 0 END) AS present,"
                        + " SUM(CASE WHEN a.\"status\" = 'ABSENT' THEN 1 ELSE 0 END) AS absent,"
                        + " SUM(CASE WHEN a.\"status\" = 'LATE' THEN 1 ELSE 0 END) AS late,"
                        + " SUM(CASE WHEN a.\"status\" = 'EXCUSED' THEN 1 ELSE 0 END) AS excused"
                        + " FROM " + ATTENDANCE + " a"
                        + " JOIN " + SECTION + " section ON section.\"id\" = a.\"sectionId\""
                        + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " WHERE a.\"studentId\" = :studentId" + semesterFilter
                        + " GROUP BY a.\"sectionId\", course.\"code\", course.\"name\", course.\"nameEn\", course.\"nameVi\""
                        + " ORDER BY course.\"code\" ASC, a.\"sectionId\" ASC",
                parameters,
                AcademicAttendanceReadRepository::mapStudentSummary);
    }

    public List<AttendanceRow> findLecturerAttendance(String lecturerId, String sectionId, Instant date) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("lecturerId", lecturerId);
        StringBuilder where = new StringBuilder(" WHERE section.\"lecturerId\" = :lecturerId");
        if (sectionId != null) {
            parameters.addValue("sectionId", sectionId);
            where.append(" AND a.\"sectionId\" = :sectionId");
        }
        if (date != null) {
            parameters.addValue("date", utcTimestamp(date));
            where.append(" AND a.\"date\" = :date");
        }
        return jdbc.query(attendanceSelect() + where
                        + " ORDER BY a.\"date\" DESC, student_user.\"firstName\" ASC, a.\"id\" ASC",
                parameters,
                AcademicAttendanceReadRepository::mapAttendance);
    }

    public List<AttendanceRow> findSectionAttendance(String sectionId, Instant date) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("sectionId", sectionId);
        String dateFilter = "";
        if (date != null) {
            parameters.addValue("date", utcTimestamp(date));
            dateFilter = " AND a.\"date\" = :date";
        }
        return jdbc.query(attendanceSelect()
                        + " WHERE a.\"sectionId\" = :sectionId" + dateFilter
                        + " ORDER BY student_user.\"firstName\" ASC, a.\"id\" ASC",
                parameters,
                AcademicAttendanceReadRepository::mapAttendance);
    }

    public SectionSummaryRow sectionSummary(String sectionId) {
        return jdbc.queryForObject(
                "SELECT COUNT(DISTINCT a.\"date\") AS total_sessions,"
                        + " COUNT(*) AS total_records,"
                        + " SUM(CASE WHEN a.\"status\" = 'PRESENT' THEN 1 ELSE 0 END) AS present,"
                        + " SUM(CASE WHEN a.\"status\" = 'ABSENT' THEN 1 ELSE 0 END) AS absent,"
                        + " SUM(CASE WHEN a.\"status\" = 'LATE' THEN 1 ELSE 0 END) AS late,"
                        + " SUM(CASE WHEN a.\"status\" = 'EXCUSED' THEN 1 ELSE 0 END) AS excused"
                        + " FROM " + ATTENDANCE + " a WHERE a.\"sectionId\" = :sectionId",
                new MapSqlParameterSource("sectionId", sectionId),
                AcademicAttendanceReadRepository::mapSectionSummary);
    }

    public Optional<AttendanceRow> findById(String id) {
        return jdbc.query(attendanceSelect() + " WHERE a.\"id\" = :id",
                        new MapSqlParameterSource("id", id),
                        AcademicAttendanceReadRepository::mapAttendance)
                .stream()
                .findFirst();
    }

    private static String attendanceFilters(
            MapSqlParameterSource parameters,
            String sectionId,
            String studentId,
            Instant date) {
        StringBuilder where = new StringBuilder();
        appendFilter(where, "a.\"sectionId\" = :sectionId", "sectionId", sectionId, parameters);
        appendFilter(where, "a.\"studentId\" = :studentId", "studentId", studentId, parameters);
        if (date != null) {
            appendFilter(where, "a.\"date\" = :date", "date", utcTimestamp(date), parameters);
        }
        return where.toString();
    }

    private static void appendFilter(
            StringBuilder where,
            String sql,
            String name,
            Object value,
            MapSqlParameterSource parameters) {
        if (value == null) {
            return;
        }
        where.append(where.length() == 0 ? " WHERE " : " AND ");
        where.append(sql);
        parameters.addValue(name, value);
    }

    private static String attendanceSelect() {
        return "SELECT a.\"id\", a.\"studentId\", a.\"sectionId\", a.\"date\", a.\"status\", a.\"notes\", a.\"createdAt\","
                + " student.\"studentId\" AS student_number,"
                + " student_user.\"id\" AS student_user_id, student_user.\"email\" AS student_email,"
                + " student_user.\"firstName\" AS student_first_name, student_user.\"lastName\" AS student_last_name,"
                + " section.\"sectionNumber\", section.\"semesterId\","
                + " course.\"id\" AS course_id, course.\"code\", course.\"name\", course.\"nameEn\", course.\"nameVi\""
                + " FROM " + ATTENDANCE + " a"
                + " JOIN " + STUDENT + " student ON student.\"id\" = a.\"studentId\""
                + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                + " JOIN " + SECTION + " section ON section.\"id\" = a.\"sectionId\""
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\"";
    }

    private static AttendanceRow mapAttendance(ResultSet rs, int ignored) throws SQLException {
        return new AttendanceRow(
                rs.getString("id"),
                rs.getString("studentId"),
                rs.getString("sectionId"),
                instant(rs.getTimestamp("date")),
                rs.getString("status"),
                rs.getString("notes"),
                instant(rs.getTimestamp("createdAt")),
                rs.getString("student_number"),
                rs.getString("student_user_id"),
                rs.getString("student_email"),
                rs.getString("student_first_name"),
                rs.getString("student_last_name"),
                rs.getString("sectionNumber"),
                rs.getString("semesterId"),
                rs.getString("course_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("nameEn"),
                rs.getString("nameVi"));
    }

    private static StudentSummaryRow mapStudentSummary(ResultSet rs, int ignored) throws SQLException {
        return new StudentSummaryRow(
                rs.getString("sectionId"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("nameEn"),
                rs.getString("nameVi"),
                rs.getLong("total"),
                rs.getLong("present"),
                rs.getLong("absent"),
                rs.getLong("late"),
                rs.getLong("excused"));
    }

    private static SectionSummaryRow mapSectionSummary(ResultSet rs, int ignored) throws SQLException {
        return new SectionSummaryRow(
                rs.getLong("total_sessions"),
                rs.getLong("total_records"),
                rs.getLong("present"),
                rs.getLong("absent"),
                rs.getLong("late"),
                rs.getLong("excused"));
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    private static Timestamp utcTimestamp(Instant instant) {
        return Timestamp.valueOf(instant.atOffset(ZoneOffset.UTC).toLocalDateTime());
    }

    public record AttendanceRow(
            String id,
            String studentId,
            String sectionId,
            Instant date,
            String status,
            String notes,
            Instant createdAt,
            String studentNumber,
            String studentUserId,
            String studentEmail,
            String studentFirstName,
            String studentLastName,
            String sectionNumber,
            String semesterId,
            String courseId,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi) {
    }

    public record StudentSummaryRow(
            String sectionId,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            long total,
            long present,
            long absent,
            long late,
            long excused) {
    }

    public record SectionSummaryRow(
            long totalSessions,
            long totalRecords,
            long present,
            long absent,
            long late,
            long excused) {
    }
}
