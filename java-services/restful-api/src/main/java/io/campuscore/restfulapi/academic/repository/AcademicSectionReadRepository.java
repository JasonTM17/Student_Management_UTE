package io.campuscore.restfulapi.academic.repository;

import io.campuscore.restfulapi.academic.model.GradingSectionRow;
import io.campuscore.restfulapi.academic.model.SectionGradeEnrollmentRow;
import io.campuscore.restfulapi.academic.model.SectionRow;
import io.campuscore.restfulapi.academic.model.SectionScheduleRow;
import io.campuscore.restfulapi.common.util.LikePattern;
import java.math.BigDecimal;
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

/** JDBC adapter for academic section tables. */
@Repository
@Profile("persistence")
public class AcademicSectionReadRepository {
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String COURSE = "\"academic\".\"Course\"";
    private static final String DEPARTMENT = "\"academic\".\"Department\"";
    private static final String SEMESTER = "\"academic\".\"Semester\"";
    private static final String LECTURER = "\"academic\".\"Lecturer\"";
    private static final String USER = "\"campuscore_auth\".\"User\"";
    private static final String CLASSROOM = "\"academic\".\"Classroom\"";
    private static final String SCHEDULE = "\"academic\".\"SectionSchedule\"";
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";
    private static final String STUDENT = "\"academic\".\"Student\"";

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicSectionReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SectionRow> findSections(long offset, int limit, String semesterId, String departmentId, String courseId) {
        return findSections(offset, limit, semesterId, departmentId, courseId, null);
    }

    public List<SectionRow> findSections(long offset, int limit, String semesterId, String departmentId, String courseId, String search) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        String where = filters(parameters, semesterId, departmentId, courseId, search);
        return jdbc.query(sectionSelect() + where
                        + " ORDER BY section.\"sectionNumber\" ASC, section.\"id\" ASC LIMIT :limit OFFSET :offset",
                parameters,
                AcademicSectionReadRepository::mapSection);
    }

    public long countSections(String semesterId, String departmentId, String courseId) {
        return countSections(semesterId, departmentId, courseId, null);
    }

    public long countSections(String semesterId, String departmentId, String courseId, String search) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = filters(parameters, semesterId, departmentId, courseId, search);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION + " section"
                        + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " LEFT JOIN " + LECTURER + " lecturer ON lecturer.\"id\" = section.\"lecturerId\""
                        + " LEFT JOIN " + USER + " lecturer_user ON lecturer_user.\"id\" = lecturer.\"userId\"" + where,
                parameters,
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<SectionRow> findSectionById(String id) {
        return jdbc.query(sectionSelect() + " WHERE section.\"id\" = :id",
                        new MapSqlParameterSource("id", id),
                        AcademicSectionReadRepository::mapSection)
                .stream()
                .findFirst();
    }

    public List<SectionRow> findLecturerSections(String lecturerId, String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("lecturerId", lecturerId);
        String semesterFilter = "";
        if (semesterId != null) {
            parameters.addValue("semesterId", semesterId);
            semesterFilter = " AND section.\"semesterId\" = :semesterId";
        }
        return jdbc.query(sectionSelect()
                        + " WHERE section.\"lecturerId\" = :lecturerId" + semesterFilter
                        + " ORDER BY course.\"code\" ASC, section.\"sectionNumber\" ASC",
                parameters,
                AcademicSectionReadRepository::mapSection);
    }

    public boolean hasSectionAccess(String sectionId, List<String> roles, String lecturerId) {
        if (roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"))) {
            return true;
        }
        if (lecturerId == null || lecturerId.isBlank()) {
            return false;
        }
        Long ownCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION + " WHERE \"id\" = :sectionId AND \"lecturerId\" = :lecturerId",
                new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("lecturerId", lecturerId),
                Long.class);
        if (ownCount != null && ownCount > 0) {
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

    public List<GradingSectionRow> findLecturerGradingSections(String lecturerId, String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("lecturerId", lecturerId);
        String semesterFilter = "";
        if (semesterId != null) {
            parameters.addValue("semesterId", semesterId);
            semesterFilter = " AND section.\"semesterId\" = :semesterId";
        }
        return jdbc.query(gradingSelect()
                        // Cancelled sections are not grading workload — the
                        // assistant scope already drops them, and the list
                        // would otherwise show a cancelled class as pending.
                        + " WHERE section.\"lecturerId\" = :lecturerId"
                        + " AND section.\"status\" <> 'CANCELLED'" + semesterFilter
                        + " GROUP BY " + gradingGroupBy()
                        + " ORDER BY semester.\"startDate\" DESC, section.\"sectionNumber\" ASC",
                parameters,
                AcademicSectionReadRepository::mapGradingSection);
    }

    public List<SectionScheduleRow> findSchedulesForSections(List<String> sectionIds) {
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
                AcademicSectionReadRepository::mapSchedule);
    }

    public List<SectionGradeEnrollmentRow> findSectionGradeEnrollments(String sectionId) {
        return jdbc.query(
                "SELECT enrollment.\"id\", enrollment.\"studentId\", student.\"studentId\" AS student_number,"
                        + " student_user.\"email\" AS student_email, student_user.\"firstName\" AS student_first_name,"
                        + " student_user.\"lastName\" AS student_last_name,"
                        + " (SELECT MAX(sg.\"score\") FROM \"academic\".\"StudentGrade\" sg JOIN \"academic\".\"GradeItem\" gi ON gi.\"id\" = sg.\"gradeItemId\" WHERE sg.\"enrollmentId\" = enrollment.\"id\" AND gi.\"type\" = 'PROCESS') AS process_score,"
                        + " (SELECT MAX(sg.\"score\") FROM \"academic\".\"StudentGrade\" sg JOIN \"academic\".\"GradeItem\" gi ON gi.\"id\" = sg.\"gradeItemId\" WHERE sg.\"enrollmentId\" = enrollment.\"id\" AND gi.\"type\" = 'FINAL') AS final_exam_score, enrollment.\"finalGrade\","
                        + " enrollment.\"letterGrade\", enrollment.\"gradeStatus\", enrollment.\"status\" AS enrollment_status"
                        + " FROM " + ENROLLMENT + " enrollment"
                        + " JOIN " + STUDENT + " student ON student.\"id\" = enrollment.\"studentId\""
                        + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                        + " WHERE enrollment.\"sectionId\" = :sectionId"
                        + " AND enrollment.\"semesterId\" = (SELECT section_scope.\"semesterId\""
                        + "     FROM \"academic\".\"Section\" section_scope WHERE section_scope.\"id\" = :sectionId)"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'CONFIRMED', 'COMPLETED')"
                        + " ORDER BY student_user.\"firstName\" ASC, student_user.\"lastName\" ASC, enrollment.\"id\" ASC",
                new MapSqlParameterSource("sectionId", sectionId),
                AcademicSectionReadRepository::mapGradeEnrollment);
    }

    private static String filters(
            MapSqlParameterSource parameters,
            String semesterId,
            String departmentId,
            String courseId,
            String search) {
        StringBuilder where = new StringBuilder();
        appendFilter(where, "section.\"semesterId\" = :semesterId", "semesterId", semesterId, parameters);
        appendFilter(where, "course.\"departmentId\" = :departmentId", "departmentId", departmentId, parameters);
        appendFilter(where, "section.\"courseId\" = :courseId", "courseId", courseId, parameters);
        List<String> tokens = LikePattern.tokens(search);
        if (!tokens.isEmpty()) {
            parameters.addValue("foldFrom", LikePattern.FOLD_FROM);
            parameters.addValue("foldTo", LikePattern.FOLD_TO);
            for (int i = 0; i < tokens.size(); i++) {
                String paramName = "searchToken" + i;
                parameters.addValue(paramName, tokens.get(i));
                where.append(where.length() == 0 ? " WHERE (" : " AND (")
                        .append("TRANSLATE(LOWER(section.\"sectionNumber\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                        .append(" OR TRANSLATE(LOWER(course.\"code\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                        .append(" OR TRANSLATE(LOWER(course.\"name\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                        .append(" OR TRANSLATE(LOWER(COALESCE(course.\"nameEn\", '')), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                        .append(" OR TRANSLATE(LOWER(COALESCE(course.\"nameVi\", '')), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                        .append(" OR TRANSLATE(LOWER(COALESCE(lecturer.\"employeeId\", '')), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                        .append(" OR TRANSLATE(LOWER(COALESCE(lecturer_user.\"firstName\", '')), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                        .append(" OR TRANSLATE(LOWER(COALESCE(lecturer_user.\"lastName\", '')), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\')");
            }
        }
        return where.toString();
    }

    private static void appendFilter(
            StringBuilder where,
            String sql,
            String name,
            String value,
            MapSqlParameterSource parameters) {
        if (value == null) {
            return;
        }
        where.append(where.length() == 0 ? " WHERE " : " AND ");
        where.append(sql);
        parameters.addValue(name, value);
    }

    private static String sectionSelect() {
        return "SELECT section.\"id\", section.\"sectionNumber\", section.\"courseId\", section.\"semesterId\","
                + " section.\"lecturerId\", section.\"classroomId\", section.\"capacity\", section.\"enrolledCount\","
                + " section.\"status\" AS section_status, course.\"code\", course.\"name\" AS course_name,"
                + " course.\"nameEn\" AS course_name_en, course.\"nameVi\" AS course_name_vi, course.\"credits\","
                + " department.\"id\" AS department_id, department.\"code\" AS department_code,"
                + " department.\"name\" AS department_name, department.\"nameEn\" AS department_name_en,"
                + " department.\"nameVi\" AS department_name_vi, semester.\"name\" AS semester_name,"
                + " semester.\"nameEn\" AS semester_name_en, semester.\"nameVi\" AS semester_name_vi,"
                + " semester.\"startDate\" AS semester_start_date, lecturer.\"employeeId\","
                + " lecturer_user.\"id\" AS lecturer_user_id, lecturer_user.\"email\" AS lecturer_email,"
                + " lecturer_user.\"firstName\" AS lecturer_first_name, lecturer_user.\"lastName\" AS lecturer_last_name,"
                + " classroom.\"building\", classroom.\"roomNumber\","
                + " (SELECT COUNT(*) FROM " + ENROLLMENT + " active_enrollment"
                + " WHERE active_enrollment.\"sectionId\" = section.\"id\""
                + " AND active_enrollment.\"semesterId\" = section.\"semesterId\""
                + " AND active_enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED', 'COMPLETED')) AS active_enrollment_count"
                + " FROM " + SECTION + " section"
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                + " JOIN " + DEPARTMENT + " department ON department.\"id\" = course.\"departmentId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = section.\"semesterId\""
                + " LEFT JOIN " + LECTURER + " lecturer ON lecturer.\"id\" = section.\"lecturerId\""
                + " LEFT JOIN " + USER + " lecturer_user ON lecturer_user.\"id\" = lecturer.\"userId\""
                + " LEFT JOIN " + CLASSROOM + " classroom ON classroom.\"id\" = section.\"classroomId\"";
    }

    private static String gradingSelect() {
        return "SELECT section.\"id\", section.\"sectionNumber\", course.\"code\", course.\"name\" AS course_name,"
                + " course.\"nameEn\" AS course_name_en, course.\"nameVi\" AS course_name_vi, course.\"credits\","
                + " department.\"name\" AS department_name, department.\"nameEn\" AS department_name_en,"
                + " department.\"nameVi\" AS department_name_vi, semester.\"name\" AS semester_name,"
                + " semester.\"nameEn\" AS semester_name_en, semester.\"nameVi\" AS semester_name_vi,"
                + " COUNT(enrollment.\"id\") AS enrolled_count,"
                + " SUM(CASE WHEN enrollment.\"finalGrade\" IS NOT NULL THEN 1 ELSE 0 END) AS graded_count,"
                + " SUM(CASE WHEN enrollment.\"gradeStatus\" IN ('PUBLISHED', 'APPEALED') THEN 1 ELSE 0 END) AS published_count"
                + " FROM " + SECTION + " section"
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                + " JOIN " + DEPARTMENT + " department ON department.\"id\" = course.\"departmentId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = section.\"semesterId\""
                + " LEFT JOIN " + ENROLLMENT + " enrollment ON enrollment.\"sectionId\" = section.\"id\""
                + " AND enrollment.\"semesterId\" = section.\"semesterId\""
                + " AND enrollment.\"status\" IN ('ENROLLED', 'CONFIRMED', 'COMPLETED')";
    }

    private static String gradingGroupBy() {
        return "section.\"id\", section.\"sectionNumber\", course.\"code\", course.\"name\", course.\"nameEn\","
                + " course.\"nameVi\", course.\"credits\", department.\"name\", department.\"nameEn\","
                + " department.\"nameVi\", semester.\"name\", semester.\"nameEn\", semester.\"nameVi\", semester.\"startDate\"";
    }

    private static SectionRow mapSection(ResultSet rs, int ignored) throws SQLException {
        return new SectionRow(
                rs.getString("id"),
                rs.getString("sectionNumber"),
                rs.getString("courseId"),
                rs.getString("semesterId"),
                rs.getString("lecturerId"),
                rs.getString("classroomId"),
                rs.getInt("capacity"),
                rs.getInt("enrolledCount"),
                rs.getString("section_status"),
                rs.getString("code"),
                rs.getString("course_name"),
                rs.getString("course_name_en"),
                rs.getString("course_name_vi"),
                rs.getInt("credits"),
                rs.getString("department_id"),
                rs.getString("department_code"),
                rs.getString("department_name"),
                rs.getString("department_name_en"),
                rs.getString("department_name_vi"),
                rs.getString("semester_name"),
                rs.getString("semester_name_en"),
                rs.getString("semester_name_vi"),
                instant(rs.getTimestamp("semester_start_date")),
                rs.getString("employeeId"),
                rs.getString("lecturer_user_id"),
                rs.getString("lecturer_email"),
                rs.getString("lecturer_first_name"),
                rs.getString("lecturer_last_name"),
                rs.getString("building"),
                rs.getString("roomNumber"),
                rs.getLong("active_enrollment_count"));
    }

    private static GradingSectionRow mapGradingSection(ResultSet rs, int ignored) throws SQLException {
        return new GradingSectionRow(
                rs.getString("id"),
                rs.getString("sectionNumber"),
                rs.getString("code"),
                rs.getString("course_name"),
                rs.getString("course_name_en"),
                rs.getString("course_name_vi"),
                rs.getInt("credits"),
                rs.getString("department_name"),
                rs.getString("department_name_en"),
                rs.getString("department_name_vi"),
                rs.getString("semester_name"),
                rs.getString("semester_name_en"),
                rs.getString("semester_name_vi"),
                rs.getLong("enrolled_count"),
                rs.getLong("graded_count"),
                rs.getLong("published_count"));
    }

    private static SectionScheduleRow mapSchedule(ResultSet rs, int ignored) throws SQLException {
        return new SectionScheduleRow(
                rs.getString("id"),
                rs.getString("sectionId"),
                rs.getInt("dayOfWeek"),
                rs.getString("startTime"),
                rs.getString("endTime"),
                rs.getString("classroom_id"),
                rs.getString("building"),
                rs.getString("roomNumber"));
    }

    private static SectionGradeEnrollmentRow mapGradeEnrollment(ResultSet rs, int ignored) throws SQLException {
        return new SectionGradeEnrollmentRow(
                rs.getString("id"),
                rs.getString("studentId"),
                displayName(rs.getString("student_first_name"), rs.getString("student_last_name")),
                rs.getString("student_number"),
                rs.getString("student_email"),
                rs.getBigDecimal("process_score"),
                rs.getBigDecimal("final_exam_score"),
                rs.getBigDecimal("finalGrade"),
                rs.getString("letterGrade"),
                rs.getString("gradeStatus"),
                rs.getString("enrollment_status"));
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    private static String displayName(String firstName, String lastName) {
        // Vietnamese order: lastName carries the surname + title, firstName the given name.
        return ((lastName == null ? "" : lastName.trim()) + " " + (firstName == null ? "" : firstName.trim())).trim();
    }
}
