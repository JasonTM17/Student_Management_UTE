package io.campuscore.restfulapi.academic.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC adapter for academic enrollment and grade tables. */
@Repository
@Profile("persistence")
public class AcademicEnrollmentReadRepository {
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String SCHEDULE = "\"academic\".\"SectionSchedule\"";
    private static final String CLASSROOM = "\"academic\".\"Classroom\"";
    private static final String COURSE = "\"academic\".\"Course\"";
    private static final String SEMESTER = "\"academic\".\"Semester\"";
    private static final String ACADEMIC_YEAR = "\"academic\".\"AcademicYear\"";
    private static final String STUDENT = "\"academic\".\"Student\"";
    private static final String LECTURER = "\"academic\".\"Lecturer\"";
    private static final String USER = "\"academic\".\"User\"";
    private static final String GRADE_ITEM = "\"academic\".\"GradeItem\"";
    private static final String STUDENT_GRADE = "\"academic\".\"StudentGrade\"";

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicEnrollmentReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EnrollmentRow> findStudentEnrollments(String studentId, String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("studentId", studentId);
        StringBuilder sql = new StringBuilder(enrollmentSelect()).append(" WHERE e.\"studentId\" = :studentId");
        if (semesterId != null) {
            sql.append(" AND e.\"semesterId\" = :semesterId");
            parameters.addValue("semesterId", semesterId);
        }
        sql.append(" ORDER BY e.\"enrolledAt\" DESC");
        return jdbc.query(sql.toString(),
                parameters,
                AcademicEnrollmentReadRepository::mapEnrollment);
    }

    public List<EnrollmentRow> findEnrollments(long offset, int limit, String status, String semesterId, String studentId, String courseId, String sectionId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = enrollmentWhere(parameters, status, semesterId, studentId, courseId, sectionId);
        return jdbc.query(enrollmentSelect() + where
                        + " ORDER BY e.\"enrolledAt\" DESC LIMIT :limit OFFSET :offset",
                parameters
                        .addValue("offset", offset)
                        .addValue("limit", limit),
                AcademicEnrollmentReadRepository::mapEnrollment);
    }

    public long countEnrollments(String status, String semesterId, String studentId, String courseId, String sectionId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = enrollmentWhere(parameters, status, semesterId, studentId, courseId, sectionId);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ENROLLMENT + " e JOIN " + SECTION
                        + " section ON section.\"id\" = e.\"sectionId\"" + where,
                parameters,
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<EnrollmentRow> findEnrollmentById(String id) {
        return jdbc.query(enrollmentSelect() + " WHERE e.\"id\" = :id",
                        new MapSqlParameterSource("id", id),
                        AcademicEnrollmentReadRepository::mapEnrollment)
                .stream()
                .findFirst();
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
                AcademicEnrollmentReadRepository::mapSchedule);
    }

    public List<GradeSummaryRow> findStudentGradeSummaries(String studentId, String semesterId) {
        return jdbc.query(
                "SELECT e.\"id\", e.\"semesterId\", e.\"status\", e.\"gradeStatus\", e.\"finalGrade\", e.\"letterGrade\","
                        + " section.\"sectionNumber\", course.\"code\", course.\"name\", course.\"nameEn\", course.\"nameVi\","
                        + " course.\"credits\", semester.\"name\" AS semester_name, semester.\"nameEn\" AS semester_name_en,"
                        + " semester.\"nameVi\" AS semester_name_vi, lecturer_user.\"firstName\" AS lecturer_first_name,"
                        + " lecturer_user.\"lastName\" AS lecturer_last_name"
                        + " FROM " + ENROLLMENT + " e" + commonJoins()
                        + " LEFT JOIN " + ACADEMIC_YEAR + " ay ON ay.\"id\" = semester.\"academicYearId\""
                        + " WHERE e.\"studentId\" = :studentId"
                        + (semesterId == null ? "" : " AND e.\"semesterId\" = :semesterId")
                        + " AND (e.\"status\" = 'COMPLETED' OR e.\"gradeStatus\" IN ('PUBLISHED', 'APPEALED'))"
                        + " ORDER BY ay.\"year\" DESC, semester.\"startDate\" DESC, course.\"code\" ASC",
                optionalParameter(new MapSqlParameterSource("studentId", studentId), "semesterId", semesterId),
                AcademicEnrollmentReadRepository::mapGradeSummary);
    }

    public List<GradeItemRow> findGradeItemsByLecturer(String lecturerId) {
        return jdbc.query(gradeItemSelect()
                        + " WHERE section.\"lecturerId\" = :lecturerId ORDER BY item.\"createdAt\" DESC",
                new MapSqlParameterSource("lecturerId", lecturerId),
                AcademicEnrollmentReadRepository::mapGradeItem);
    }

    public List<GradeItemRow> findGradeItemsBySection(String sectionId) {
        return jdbc.query(gradeItemSelect()
                        + " WHERE item.\"sectionId\" = :sectionId ORDER BY item.\"createdAt\" DESC",
                new MapSqlParameterSource("sectionId", sectionId),
                AcademicEnrollmentReadRepository::mapGradeItem);
    }

    public List<GradeItemRow> findGradeItemsBySectionAndLecturer(String sectionId, String lecturerId) {
        return jdbc.query(gradeItemSelect()
                        + " WHERE item.\"sectionId\" = :sectionId AND section.\"lecturerId\" = :lecturerId"
                        + " ORDER BY item.\"createdAt\" DESC",
                new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("lecturerId", lecturerId),
                AcademicEnrollmentReadRepository::mapGradeItem);
    }

    public List<StudentGradeRow> findStudentGradesByLecturer(String lecturerId, String sectionId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("lecturerId", lecturerId);
        String sectionFilter = "";
        if (sectionId != null) {
            sectionFilter = " AND e.\"sectionId\" = :sectionId";
            parameters.addValue("sectionId", sectionId);
        }
        return jdbc.query(studentGradeSelect()
                        + " WHERE section.\"lecturerId\" = :lecturerId"
                        + sectionFilter
                        + " ORDER BY section.\"sectionNumber\", student_user.\"lastName\", student_user.\"firstName\", item.\"createdAt\"",
                parameters,
                AcademicEnrollmentReadRepository::mapStudentGrade);
    }

    public List<StudentGradeRow> findStudentGradesBySection(String sectionId) {
        return jdbc.query(studentGradeSelect()
                        + " WHERE e.\"sectionId\" = :sectionId"
                        + " ORDER BY student_user.\"lastName\", student_user.\"firstName\", item.\"createdAt\"",
                new MapSqlParameterSource("sectionId", sectionId),
                AcademicEnrollmentReadRepository::mapStudentGrade);
    }

    public List<StudentGradeRow> findStudentGradesBySectionAndLecturer(String sectionId, String lecturerId) {
        return jdbc.query(studentGradeSelect()
                        + " WHERE e.\"sectionId\" = :sectionId AND section.\"lecturerId\" = :lecturerId"
                        + " ORDER BY student_user.\"lastName\", student_user.\"firstName\", item.\"createdAt\"",
                new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("lecturerId", lecturerId),
                AcademicEnrollmentReadRepository::mapStudentGrade);
    }

    public List<StudentGradeRow> findStudentGradesByEnrollment(String enrollmentId) {
        return jdbc.query(studentGradeSelect()
                        + " WHERE e.\"id\" = :enrollmentId ORDER BY item.\"createdAt\"",
                new MapSqlParameterSource("enrollmentId", enrollmentId),
                AcademicEnrollmentReadRepository::mapStudentGrade);
    }

    public List<StudentGradeRow> findStudentGradesByEnrollmentAndLecturer(String enrollmentId, String lecturerId) {
        return jdbc.query(studentGradeSelect()
                        + " WHERE e.\"id\" = :enrollmentId AND section.\"lecturerId\" = :lecturerId"
                        + " ORDER BY item.\"createdAt\"",
                new MapSqlParameterSource().addValue("enrollmentId", enrollmentId).addValue("lecturerId", lecturerId),
                AcademicEnrollmentReadRepository::mapStudentGrade);
    }

    private static String enrollmentSelect() {
        return "SELECT e.\"id\", e.\"studentId\", e.\"sectionId\", e.\"semesterId\", e.\"status\","
                + " e.\"enrolledAt\", e.\"droppedAt\", e.\"gradeStatus\", e.\"finalGrade\", e.\"letterGrade\","
                + " e.\"createdAt\", e.\"updatedAt\", student.\"studentId\" AS student_number,"
                + " student_user.\"id\" AS student_user_id, student_user.\"email\" AS student_email,"
                + " student_user.\"firstName\" AS student_first_name, student_user.\"lastName\" AS student_last_name,"
                + " section.\"sectionNumber\", section.\"capacity\", section.\"enrolledCount\", section.\"status\" AS section_status,"
                + " course.\"id\" AS course_id, course.\"code\", course.\"name\", course.\"nameEn\", course.\"nameVi\", course.\"credits\","
                + " semester.\"id\" AS sem_id, semester.\"name\" AS sem_name, semester.\"nameEn\" AS sem_name_en,"
                + " semester.\"nameVi\" AS sem_name_vi, semester.\"startDate\" AS sem_start_date,"
                + " lecturer.\"id\" AS lecturer_id, lecturer.\"employeeId\", lecturer_user.\"id\" AS lecturer_user_id,"
                + " lecturer_user.\"email\" AS lecturer_email, lecturer_user.\"firstName\" AS lecturer_first_name,"
                + " lecturer_user.\"lastName\" AS lecturer_last_name FROM " + ENROLLMENT + " e" + commonJoins();
    }

    private static String commonJoins() {
        return " JOIN " + SECTION + " section ON section.\"id\" = e.\"sectionId\""
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = e.\"semesterId\""
                + " JOIN " + STUDENT + " student ON student.\"id\" = e.\"studentId\""
                + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                + " LEFT JOIN " + LECTURER + " lecturer ON lecturer.\"id\" = section.\"lecturerId\""
                + " LEFT JOIN " + USER + " lecturer_user ON lecturer_user.\"id\" = lecturer.\"userId\"";
    }

    private static String enrollmentWhere(MapSqlParameterSource parameters, String status, String semesterId, String studentId, String courseId, String sectionId) {
        List<String> filters = new ArrayList<>();
        addFilter(filters, parameters, "status", status, "e.\"status\" = :status");
        addFilter(filters, parameters, "semesterId", semesterId, "e.\"semesterId\" = :semesterId");
        addFilter(filters, parameters, "studentId", studentId, "e.\"studentId\" = :studentId");
        addFilter(filters, parameters, "courseId", courseId, "section.\"courseId\" = :courseId");
        addFilter(filters, parameters, "sectionId", sectionId, "e.\"sectionId\" = :sectionId");
        return filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
    }

    private static void addFilter(List<String> filters, MapSqlParameterSource parameters, String name, String value, String sql) {
        if (value != null) {
            filters.add(sql);
            parameters.addValue(name, value);
        }
    }

    private static MapSqlParameterSource optionalParameter(MapSqlParameterSource parameters, String name, String value) {
        if (value != null) {
            parameters.addValue(name, value);
        }
        return parameters;
    }

    private static String gradeItemSelect() {
        return "SELECT item.\"id\", item.\"sectionId\", item.\"name\", item.\"type\", item.\"maxScore\", item.\"weight\","
                + " item.\"gradedAt\", item.\"createdAt\", section.\"sectionNumber\", section.\"capacity\","
                + " section.\"enrolledCount\", section.\"status\" AS section_status, course.\"id\" AS course_id,"
                + " course.\"code\", course.\"name\" AS course_name, course.\"nameEn\" AS course_name_en,"
                + " course.\"nameVi\" AS course_name_vi, course.\"credits\", semester.\"id\" AS semester_id,"
                + " semester.\"name\" AS semester_name, semester.\"nameEn\" AS semester_name_en,"
                + " semester.\"nameVi\" AS semester_name_vi, semester.\"startDate\" AS semester_start_date"
                + " FROM " + GRADE_ITEM + " item JOIN " + SECTION + " section ON section.\"id\" = item.\"sectionId\""
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = section.\"semesterId\"";
    }

    private static String studentGradeSelect() {
        return "SELECT e.\"id\" AS enrollment_id, e.\"studentId\", e.\"sectionId\", e.\"finalGrade\", e.\"letterGrade\","
                + " e.\"gradeStatus\", student.\"studentId\" AS student_number, student_user.\"firstName\" AS student_first_name,"
                + " student_user.\"lastName\" AS student_last_name, section.\"sectionNumber\", course.\"code\","
                + " course.\"name\" AS course_name, course.\"nameEn\" AS course_name_en, course.\"nameVi\" AS course_name_vi,"
                + " semester.\"name\" AS semester_name, semester.\"nameEn\" AS semester_name_en,"
                + " semester.\"nameVi\" AS semester_name_vi, grade.\"id\" AS grade_id, item.\"id\" AS \"gradeItemId\","
                + " grade.\"score\", item.\"name\" AS grade_item_name, item.\"type\" AS grade_item_type,"
                + " item.\"maxScore\", item.\"weight\" FROM " + ENROLLMENT + " e"
                + " JOIN " + STUDENT + " student ON student.\"id\" = e.\"studentId\""
                + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                + " JOIN " + SECTION + " section ON section.\"id\" = e.\"sectionId\""
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = e.\"semesterId\""
                + " LEFT JOIN " + STUDENT_GRADE + " grade ON grade.\"enrollmentId\" = e.\"id\""
                + " LEFT JOIN " + GRADE_ITEM + " item ON item.\"id\" = grade.\"gradeItemId\""
                + " AND item.\"sectionId\" = e.\"sectionId\"";
    }

    private static EnrollmentRow mapEnrollment(ResultSet rs, int ignored) throws SQLException {
        return new EnrollmentRow(
                rs.getString("id"), rs.getString("studentId"), rs.getString("sectionId"), rs.getString("semesterId"),
                rs.getString("status"), instant(rs.getTimestamp("enrolledAt")), instant(rs.getTimestamp("droppedAt")),
                rs.getString("gradeStatus"), rs.getBigDecimal("finalGrade"), rs.getString("letterGrade"),
                instant(rs.getTimestamp("createdAt")), instant(rs.getTimestamp("updatedAt")), rs.getString("student_number"),
                rs.getString("student_user_id"), rs.getString("student_email"), rs.getString("student_first_name"),
                rs.getString("student_last_name"), rs.getString("sectionNumber"), rs.getInt("capacity"),
                rs.getInt("enrolledCount"), rs.getString("section_status"), rs.getString("course_id"),
                rs.getString("code"), rs.getString("name"), rs.getString("nameEn"), rs.getString("nameVi"),
                rs.getInt("credits"), rs.getString("sem_id"), rs.getString("sem_name"), rs.getString("sem_name_en"),
                rs.getString("sem_name_vi"), instant(rs.getTimestamp("sem_start_date")), rs.getString("lecturer_id"),
                rs.getString("employeeId"), rs.getString("lecturer_user_id"), rs.getString("lecturer_email"),
                rs.getString("lecturer_first_name"), rs.getString("lecturer_last_name"));
    }

    private static ScheduleRow mapSchedule(ResultSet rs, int ignored) throws SQLException {
        return new ScheduleRow(rs.getString("id"), rs.getString("sectionId"), rs.getInt("dayOfWeek"),
                rs.getString("startTime"), rs.getString("endTime"), rs.getString("classroom_id"),
                rs.getString("building"), rs.getString("roomNumber"));
    }

    private static GradeSummaryRow mapGradeSummary(ResultSet rs, int ignored) throws SQLException {
        return new GradeSummaryRow(rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("nameEn"), rs.getString("nameVi"), rs.getInt("credits"), rs.getString("sectionNumber"),
                displayName(rs.getString("lecturer_first_name"), rs.getString("lecturer_last_name")),
                rs.getString("semester_name"), rs.getString("semester_name_en"), rs.getString("semester_name_vi"),
                rs.getString("semesterId"), rs.getBigDecimal("finalGrade"), rs.getString("letterGrade"),
                rs.getString("gradeStatus"), rs.getString("status"));
    }

    private static GradeItemRow mapGradeItem(ResultSet rs, int ignored) throws SQLException {
        return new GradeItemRow(rs.getString("id"), rs.getString("sectionId"), rs.getString("name"), rs.getString("type"),
                rs.getBigDecimal("maxScore"), rs.getBigDecimal("weight"), instant(rs.getTimestamp("gradedAt")),
                instant(rs.getTimestamp("createdAt")), rs.getString("sectionNumber"), rs.getInt("capacity"),
                rs.getInt("enrolledCount"), rs.getString("section_status"), rs.getString("course_id"), rs.getString("code"),
                rs.getString("course_name"), rs.getString("course_name_en"), rs.getString("course_name_vi"),
                rs.getInt("credits"), rs.getString("semester_id"), rs.getString("semester_name"),
                rs.getString("semester_name_en"), rs.getString("semester_name_vi"), instant(rs.getTimestamp("semester_start_date")));
    }

    private static StudentGradeRow mapStudentGrade(ResultSet rs, int ignored) throws SQLException {
        return new StudentGradeRow(rs.getString("enrollment_id"), rs.getString("studentId"),
                displayName(rs.getString("student_first_name"), rs.getString("student_last_name")), rs.getString("student_number"),
                rs.getString("sectionId"), rs.getString("sectionNumber"), rs.getString("code"), rs.getString("course_name"),
                rs.getString("course_name_en"), rs.getString("course_name_vi"), rs.getString("semester_name"),
                rs.getString("semester_name_en"), rs.getString("semester_name_vi"), rs.getBigDecimal("finalGrade"),
                rs.getString("letterGrade"), rs.getString("gradeStatus"), rs.getString("grade_id"),
                rs.getString("gradeItemId"), rs.getString("grade_item_name"), rs.getString("grade_item_type"),
                rs.getBigDecimal("score"), rs.getBigDecimal("maxScore"), rs.getBigDecimal("weight"));
    }

    private static String displayName(String firstName, String lastName) {
        return ((firstName == null ? "" : firstName.trim()) + " " + (lastName == null ? "" : lastName.trim())).trim();
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    public record EnrollmentRow(String id, String studentId, String sectionId, String semesterId, String status,
            Instant enrolledAt, Instant droppedAt, String gradeStatus, BigDecimal finalGrade, String letterGrade,
            Instant createdAt, Instant updatedAt, String studentNumber, String studentUserId, String studentEmail,
            String studentFirstName, String studentLastName, String sectionNumber, int capacity, int enrolledCount,
            String sectionStatus, String courseId, String courseCode, String courseName, String courseNameEn,
            String courseNameVi, int credits, String semesterIdValue, String semesterName, String semesterNameEn,
            String semesterNameVi, Instant semesterStartDate, String lecturerId, String employeeId, String lecturerUserId,
            String lecturerEmail, String lecturerFirstName, String lecturerLastName) {
    }

    public record ScheduleRow(String id, String sectionId, int dayOfWeek, String startTime, String endTime,
            String classroomId, String building, String roomNumber) {
    }

    public record GradeSummaryRow(String id, String courseCode, String courseName, String courseNameEn, String courseNameVi,
            int credits, String sectionCode, String lecturerName, String semester, String semesterNameEn, String semesterNameVi,
            String semesterId, BigDecimal finalGrade, String letterGrade, String gradeStatus, String enrollmentStatus) {
    }

    public record GradeItemRow(String id, String sectionId, String name, String type, BigDecimal maxScore, BigDecimal weight,
            Instant gradedAt, Instant createdAt, String sectionNumber, int capacity, int enrolledCount, String sectionStatus,
            String courseId, String courseCode, String courseName, String courseNameEn, String courseNameVi, int credits,
            String semesterId, String semesterName, String semesterNameEn, String semesterNameVi, Instant semesterStartDate) {
    }

    public record StudentGradeRow(String enrollmentId, String studentId, String studentName, String studentNumber,
            String sectionId, String sectionNumber, String courseCode, String courseName, String courseNameEn,
            String courseNameVi, String semester, String semesterNameEn, String semesterNameVi, BigDecimal finalGrade,
            String letterGrade, String gradeStatus, String gradeId, String gradeItemId, String gradeItemName,
            String gradeItemType, BigDecimal score, BigDecimal maxScore, BigDecimal weight) {
    }
}
