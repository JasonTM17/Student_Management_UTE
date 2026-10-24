package io.campuscore.restfulapi.academic.repository;

import io.campuscore.restfulapi.academic.model.AttendanceRow;
import io.campuscore.restfulapi.academic.model.RosterRow;
import io.campuscore.restfulapi.academic.model.SectionSummaryRow;
import io.campuscore.restfulapi.academic.model.StudentSummaryRow;
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
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";
    private static final String STUDENT = "\"academic\".\"Student\"";
    private static final String USER = "\"campuscore_auth\".\"User\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String COURSE = "\"academic\".\"Course\"";
    private static final String LECTURER = "\"academic\".\"Lecturer\"";
    private static final String DEPARTMENT = "\"academic\".\"Department\"";

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
            bindDay(parameters, date);
            where.append(" AND a.\"date\" >= :dayStart AND a.\"date\" < :dayEnd");
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
            bindDay(parameters, date);
            dateFilter = " AND a.\"date\" >= :dayStart AND a.\"date\" < :dayEnd";
        }
        return jdbc.query(attendanceSelect()
                        + " WHERE a.\"sectionId\" = :sectionId" + dateFilter
                        + " ORDER BY student_user.\"firstName\" ASC, a.\"id\" ASC",
                parameters,
                AcademicAttendanceReadRepository::mapAttendance);
    }

    public SectionSummaryRow sectionSummary(String sectionId) {
        return jdbc.queryForObject(
                "SELECT COUNT(DISTINCT TO_CHAR(a.\"date\" AT TIME ZONE 'UTC', 'YYYY-MM-DD')) AS total_sessions,"
                        + " COUNT(*) AS total_records,"
                        + " SUM(CASE WHEN a.\"status\" = 'PRESENT' THEN 1 ELSE 0 END) AS present,"
                        + " SUM(CASE WHEN a.\"status\" = 'ABSENT' THEN 1 ELSE 0 END) AS absent,"
                        + " SUM(CASE WHEN a.\"status\" = 'LATE' THEN 1 ELSE 0 END) AS late,"
                        + " SUM(CASE WHEN a.\"status\" = 'EXCUSED' THEN 1 ELSE 0 END) AS excused"
                        + " FROM " + ATTENDANCE + " a WHERE a.\"sectionId\" = :sectionId",
                new MapSqlParameterSource("sectionId", sectionId),
                AcademicAttendanceReadRepository::mapSectionSummary);
    }

    /**
     * Attendance roster uses the write path's markable predicate: every
     * enrollment except DROPPED/CANCELLED. The grading read model deliberately
     * excludes PENDING (publishing completeness), but a still-pending
     * enrollment is an actively attending student who must be markable.
     * Historical/repeated enrollments must not duplicate a student in roll
     * call, its counts or exports. Use the latest eligible row for status;
     * enrollment and attendance history remain unchanged.
     */
    public List<RosterRow> findSectionAttendanceRoster(String sectionId) {
        return jdbc.query(
                "SELECT student.\"id\" AS student_id, student.\"studentId\" AS student_number,"
                        + " student_user.\"email\" AS student_email,"
                        + " student_user.\"firstName\" AS student_first_name,"
                        + " student_user.\"lastName\" AS student_last_name,"
                        + " enrollment.\"status\" AS enrollment_status"
                        + " FROM (SELECT \"id\", \"studentId\", \"status\","
                        + " ROW_NUMBER() OVER (PARTITION BY \"studentId\""
                        + " ORDER BY \"enrolledAt\" DESC, \"id\" DESC) AS roster_rank"
                        + " FROM " + ENROLLMENT
                        + " WHERE \"sectionId\" = :sectionId"
                        + " AND \"status\" NOT IN ('DROPPED', 'CANCELLED')) enrollment"
                        + " JOIN " + STUDENT + " student ON student.\"id\" = enrollment.\"studentId\""
                        + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                        + " WHERE enrollment.roster_rank = 1"
                        + " ORDER BY student_user.\"firstName\" ASC, student_user.\"lastName\" ASC, enrollment.\"id\" ASC",
                new MapSqlParameterSource("sectionId", sectionId),
                AcademicAttendanceReadRepository::mapRoster);
    }

    public Optional<AttendanceRow> findById(String id) {
        return jdbc.query(attendanceSelect() + " WHERE a.\"id\" = :id",
                        new MapSqlParameterSource("id", id),
                        AcademicAttendanceReadRepository::mapAttendance)
                .stream()
                .findFirst();
    }

    public boolean isSectionOwnedByLecturer(String sectionId, String lecturerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION
                        + " WHERE \"id\" = :sectionId AND \"lecturerId\" = :lecturerId",
                new MapSqlParameterSource()
                        .addValue("sectionId", sectionId)
                        .addValue("lecturerId", lecturerId),
                Long.class);
        return count != null && count > 0;
    }

    public boolean hasSectionAccess(String sectionId, List<String> roles, String lecturerId) {
        if (roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"))) {
            return true;
        }
        if (lecturerId == null || lecturerId.isBlank()) {
            return false;
        }
        if (isSectionOwnedByLecturer(sectionId, lecturerId)) {
            return true;
        }
        if (roles != null && roles.contains("TRUONG_BO_MON")) {
            try {
                Long count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + SECTION + " section "
                                + "JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\" "
                                + "JOIN " + LECTURER + " lecturer ON lecturer.\"id\" = :lecturerId "
                                + "WHERE section.\"id\" = :sectionId AND course.\"departmentId\" = lecturer.\"departmentId\"",
                        new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("lecturerId", lecturerId),
                        Long.class);
                if (count != null && count > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        if (roles != null && roles.contains("TRUONG_KHOA")) {
            try {
                Long count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + SECTION + " section "
                                + "JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\" "
                                + "JOIN " + DEPARTMENT + " courseDept ON courseDept.\"id\" = course.\"departmentId\" "
                                + "JOIN " + LECTURER + " lecturer ON lecturer.\"id\" = :lecturerId "
                                + "JOIN " + DEPARTMENT + " lecturerDept ON lecturerDept.\"id\" = lecturer.\"departmentId\" "
                                + "WHERE section.\"id\" = :sectionId AND courseDept.\"facultyId\" = lecturerDept.\"facultyId\"",
                        new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("lecturerId", lecturerId),
                        Long.class);
                if (count != null && count > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
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
            where.append(where.length() == 0 ? " WHERE " : " AND ");
            where.append("a.\"date\" >= :dayStart AND a.\"date\" < :dayEnd");
            bindDay(parameters, date);
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
                rs.getObject("date", java.time.OffsetDateTime.class).toInstant(),
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

    private static RosterRow mapRoster(ResultSet rs, int ignored) throws SQLException {
        return new RosterRow(
                rs.getString("student_id"),
                rs.getString("student_number"),
                rs.getString("student_email"),
                rs.getString("student_first_name"),
                rs.getString("student_last_name"),
                rs.getString("enrollment_status"));
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
        return timestamp.toInstant();
    }

    private static java.time.OffsetDateTime utcTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static void bindDay(MapSqlParameterSource parameters, Instant date) {
        Instant start = date.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        parameters.addValue("dayStart", utcTimestamp(start)).addValue("dayEnd", utcTimestamp(start.plusSeconds(86400)));
    }

}
