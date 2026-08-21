package io.campuscore.restfulapi.academic.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SELECT-only adapter for legacy Prisma academic waitlist tables. */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-waitlist-read", name = "enabled", havingValue = "true")
public class AcademicWaitlistReadRepository {
    private static final String WAITLIST = "\"academic\".\"Waitlist\"";
    private static final String STUDENT = "\"academic\".\"Student\"";
    private static final String USER = "\"academic\".\"User\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String COURSE = "\"academic\".\"Course\"";
    private static final String DEPARTMENT = "\"academic\".\"Department\"";
    private static final String SEMESTER = "\"academic\".\"Semester\"";
    private static final String SCHEDULE = "\"academic\".\"SectionSchedule\"";
    private static final String CLASSROOM = "\"academic\".\"Classroom\"";

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicWaitlistReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WaitlistRow> findWaitlist(long offset, int limit, String sectionId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        String where = sectionFilter(parameters, sectionId);
        return jdbc.query(waitlistSelect() + where
                        + " ORDER BY w.\"position\" ASC, w.\"id\" ASC LIMIT :limit OFFSET :offset",
                parameters,
                AcademicWaitlistReadRepository::mapWaitlist);
    }

    public long countWaitlist(String sectionId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = sectionFilter(parameters, sectionId);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + WAITLIST + " w" + where,
                parameters,
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<WaitlistRow> findWaitlistById(String id) {
        return jdbc.query(waitlistSelect() + " WHERE w.\"id\" = :id",
                        new MapSqlParameterSource("id", id),
                        AcademicWaitlistReadRepository::mapWaitlist)
                .stream()
                .findFirst();
    }

    public List<WaitlistRow> findActiveBySection(String sectionId) {
        return jdbc.query(waitlistSelect()
                        + " WHERE w.\"sectionId\" = :sectionId AND w.\"status\" = 'ACTIVE'"
                        + " ORDER BY w.\"position\" ASC, w.\"id\" ASC",
                new MapSqlParameterSource("sectionId", sectionId),
                AcademicWaitlistReadRepository::mapWaitlist);
    }

    public List<WaitlistRow> findActiveByStudent(String studentId) {
        return jdbc.query(waitlistSelect()
                        + " WHERE w.\"studentId\" = :studentId AND w.\"status\" = 'ACTIVE'"
                        + " ORDER BY w.\"addedAt\" DESC, w.\"position\" ASC",
                new MapSqlParameterSource("studentId", studentId),
                AcademicWaitlistReadRepository::mapWaitlist);
    }

    public List<ScheduleRow> findSchedulesForSections(List<String> sectionIds) {
        if (sectionIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT schedule.\"id\", schedule.\"sectionId\", schedule.\"dayOfWeek\", schedule.\"startTime\","
                        + " schedule.\"endTime\", classroom.\"id\" AS classroom_id, classroom.\"building\", classroom.\"roomNumber\""
                        + " FROM " + SCHEDULE + " schedule JOIN " + CLASSROOM + " classroom"
                        + " ON classroom.\"id\" = schedule.\"classroomId\""
                        + " WHERE schedule.\"sectionId\" IN (:sectionIds)"
                        + " ORDER BY schedule.\"sectionId\", schedule.\"dayOfWeek\", schedule.\"startTime\"",
                new MapSqlParameterSource("sectionIds", sectionIds),
                AcademicWaitlistReadRepository::mapSchedule);
    }

    private static String sectionFilter(MapSqlParameterSource parameters, String sectionId) {
        if (sectionId == null) {
            return "";
        }
        parameters.addValue("sectionId", sectionId);
        return " WHERE w.\"sectionId\" = :sectionId";
    }

    private static String waitlistSelect() {
        return "SELECT w.\"id\", w.\"studentId\", w.\"sectionId\", w.\"position\", w.\"status\","
                + " w.\"addedAt\", w.\"convertedAt\", student.\"studentId\" AS student_number,"
                + " student_user.\"id\" AS student_user_id, student_user.\"email\" AS student_email,"
                + " student_user.\"firstName\" AS student_first_name, student_user.\"lastName\" AS student_last_name,"
                + " section.\"sectionNumber\", section.\"capacity\", section.\"enrolledCount\", section.\"status\" AS section_status,"
                + " course.\"id\" AS course_id, course.\"code\", course.\"name\", course.\"nameEn\", course.\"nameVi\","
                + " course.\"credits\", department.\"id\" AS department_id, department.\"code\" AS department_code,"
                + " department.\"name\" AS department_name, department.\"nameEn\" AS department_name_en,"
                + " department.\"nameVi\" AS department_name_vi, semester.\"id\" AS semester_id,"
                + " semester.\"name\" AS semester_name, semester.\"nameEn\" AS semester_name_en,"
                + " semester.\"nameVi\" AS semester_name_vi, semester.\"startDate\" AS semester_start_date"
                + " FROM " + WAITLIST + " w"
                + " JOIN " + STUDENT + " student ON student.\"id\" = w.\"studentId\""
                + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                + " JOIN " + SECTION + " section ON section.\"id\" = w.\"sectionId\""
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                + " JOIN " + DEPARTMENT + " department ON department.\"id\" = course.\"departmentId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = section.\"semesterId\"";
    }

    private static WaitlistRow mapWaitlist(ResultSet rs, int ignored) throws SQLException {
        return new WaitlistRow(
                rs.getString("id"),
                rs.getString("studentId"),
                rs.getString("sectionId"),
                rs.getInt("position"),
                rs.getString("status"),
                instant(rs.getTimestamp("addedAt")),
                instant(rs.getTimestamp("convertedAt")),
                rs.getString("student_number"),
                rs.getString("student_user_id"),
                rs.getString("student_email"),
                rs.getString("student_first_name"),
                rs.getString("student_last_name"),
                rs.getString("sectionNumber"),
                rs.getInt("capacity"),
                rs.getInt("enrolledCount"),
                rs.getString("section_status"),
                rs.getString("course_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("nameEn"),
                rs.getString("nameVi"),
                rs.getInt("credits"),
                rs.getString("department_id"),
                rs.getString("department_code"),
                rs.getString("department_name"),
                rs.getString("department_name_en"),
                rs.getString("department_name_vi"),
                rs.getString("semester_id"),
                rs.getString("semester_name"),
                rs.getString("semester_name_en"),
                rs.getString("semester_name_vi"),
                instant(rs.getTimestamp("semester_start_date")));
    }

    private static ScheduleRow mapSchedule(ResultSet rs, int ignored) throws SQLException {
        return new ScheduleRow(
                rs.getString("id"),
                rs.getString("sectionId"),
                rs.getInt("dayOfWeek"),
                rs.getString("startTime"),
                rs.getString("endTime"),
                rs.getString("classroom_id"),
                rs.getString("building"),
                rs.getString("roomNumber"));
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    public record WaitlistRow(
            String id,
            String studentId,
            String sectionId,
            int position,
            String status,
            Instant addedAt,
            Instant convertedAt,
            String studentNumber,
            String studentUserId,
            String studentEmail,
            String studentFirstName,
            String studentLastName,
            String sectionNumber,
            int capacity,
            int enrolledCount,
            String sectionStatus,
            String courseId,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            int credits,
            String departmentId,
            String departmentCode,
            String departmentName,
            String departmentNameEn,
            String departmentNameVi,
            String semesterId,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            Instant semesterStartDate) {
    }

    public record ScheduleRow(
            String id,
            String sectionId,
            int dayOfWeek,
            String startTime,
            String endTime,
            String classroomId,
            String building,
            String roomNumber) {
    }
}
