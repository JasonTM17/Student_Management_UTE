package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional course mutations owned by the single Java API. */
@Service
@Profile("persistence")
public class AcademicMutationService {

    private static final String STUDENT = "\"academic\".\"Student\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String SEMESTER = "\"academic\".\"Semester\"";
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";
    private static final String COURSE = "\"academic\".\"Course\"";
    private static final String USER = "\"campuscore_auth\".\"User\"";

    private final NamedParameterJdbcTemplate jdbc;
    private final AcademicEnrollmentReadService reads;
    private final RegistrationService registration;

    public AcademicMutationService(
            NamedParameterJdbcTemplate jdbc,
            AcademicEnrollmentReadService reads,
            RegistrationService registration) {
        this.jdbc = jdbc;
        this.reads = reads;
        this.registration = registration;
    }

    public EnrollmentResponse enroll(String studentId, String sectionId, List<String> roles, String idempotencyKey) {
        return registration.enroll(studentId, sectionId, roles, idempotencyKey);
    }

    public void drop(String enrollmentId, String studentId, List<String> roles, String idempotencyKey) {
        registration.drop(enrollmentId, studentId, roles, idempotencyKey);
    }

    @Transactional
    public void deleteEnrollment(String enrollmentId) {
        Map<String, Object> enrollment = enrollment(enrollmentId);
        String status = String.valueOf(enrollment.get("status"));
        jdbc.update(
                "DELETE FROM " + ENROLLMENT + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", enrollmentId));
        if (List.of("ENROLLED", "PENDING", "CONFIRMED").contains(status)) {
            jdbc.update(
                    "UPDATE " + SECTION + " SET \"enrolledCount\" = GREATEST(0, \"enrolledCount\" - 1),"
                            + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :sectionId",
                    new MapSqlParameterSource("sectionId", enrollment.get("section_id")));
        }
    }

    @Transactional(readOnly = true)
    public String exportEnrollments(
            String status,
            String semesterId,
            String studentId,
            String courseId) {
        return exportEnrollments(status, semesterId, studentId, courseId, null);
    }

    @Transactional(readOnly = true)
    public String exportEnrollments(
            String status,
            String semesterId,
            String studentId,
            String courseId,
            String sectionId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        List<String> filters = new ArrayList<>();
        addFilter(filters, parameters, "status", status, "e.\"status\" = :status");
        addFilter(filters, parameters, "semesterId", semesterId, "e.\"semesterId\" = :semesterId");
        addFilter(filters, parameters, "studentId", studentId, "e.\"studentId\" = :studentId");
        addFilter(filters, parameters, "courseId", courseId, "section.\"courseId\" = :courseId");
        addFilter(filters, parameters, "sectionId", sectionId, "e.\"sectionId\" = :sectionId");
        String where = filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT e.\"id\", student.\"studentId\" AS student_number,"
                        + " student_user.\"email\", student_user.\"firstName\", student_user.\"lastName\","
                        + " course.\"code\" AS course_code, course.\"name\" AS course_name,"
                        + " section.\"sectionNumber\", semester.\"name\" AS semester_name,"
                        + " e.\"status\", e.\"enrolledAt\", e.\"finalGrade\", e.\"letterGrade\""
                        + " FROM " + ENROLLMENT + " e"
                        + " JOIN " + STUDENT + " student ON student.\"id\" = e.\"studentId\""
                        + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                        + " JOIN " + SECTION + " section ON section.\"id\" = e.\"sectionId\""
                        + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " JOIN " + SEMESTER + " semester ON semester.\"id\" = e.\"semesterId\""
                        + where + " ORDER BY e.\"enrolledAt\" DESC",
                parameters);
        StringBuilder csv = new StringBuilder(
                "id,studentNumber,email,studentName,courseCode,courseName,section,semester,status,enrolledAt,finalGrade,letterGrade\r\n");
        for (Map<String, Object> row : rows) {
            csv.append(csv(row.get("id"))).append(',')
                    .append(csv(row.get("student_number"))).append(',')
                    .append(csv(row.get("email"))).append(',')
                    .append(csv((String.valueOf(row.get("firstName")) + " "
                            + String.valueOf(row.get("lastName"))).trim())).append(',')
                    .append(csv(row.get("course_code"))).append(',')
                    .append(csv(row.get("course_name"))).append(',')
                    .append(csv(row.get("sectionNumber"))).append(',')
                    .append(csv(row.get("semester_name"))).append(',')
                    .append(csv(row.get("status"))).append(',')
                    .append(csv(row.get("enrolledAt"))).append(',')
                    .append(csv(row.get("finalGrade"))).append(',')
                    .append(csv(row.get("letterGrade"))).append("\r\n");
        }
        return csv.toString();
    }

    @Transactional
    public void updateGrades(String sectionId, String lecturerId, boolean admin, List<GradeUpdate> grades) {
        requireSection(sectionId);
        if (!admin && !ownsSection(sectionId, lecturerId)) {
            throw problem(HttpStatus.FORBIDDEN, "SECTION_FORBIDDEN", "Section is not assigned to the current lecturer");
        }
        for (GradeUpdate grade : grades) {
            Map<String, Object> enrollment = enrollment(grade.enrollmentId());
            if (!sectionId.equals(enrollment.get("section_id"))) {
                throw problem(HttpStatus.BAD_REQUEST, "GRADE_SECTION_MISMATCH", "Grade enrollment is outside this section");
            }
            jdbc.update(
                    "UPDATE " + ENROLLMENT + " SET \"finalGrade\" = :finalGrade, \"letterGrade\" = :letterGrade,"
                            + " \"gradeStatus\" = 'DRAFT', \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                    new MapSqlParameterSource()
                            .addValue("finalGrade", grade.finalGrade())
                            .addValue("letterGrade", grade.letterGrade().trim().toUpperCase())
                            .addValue("id", grade.enrollmentId()));
        }
    }

    @Transactional
    public void publishGrades(String sectionId, String lecturerId, boolean admin) {
        requireSection(sectionId);
        if (!admin && !ownsSection(sectionId, lecturerId)) {
            throw problem(HttpStatus.FORBIDDEN, "SECTION_FORBIDDEN", "Section is not assigned to the current lecturer");
        }
        int updated = jdbc.update(
                "UPDATE " + ENROLLMENT + " SET \"gradeStatus\" = 'PUBLISHED', \"status\" = 'COMPLETED',"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"sectionId\" = :sectionId"
                        + " AND \"finalGrade\" IS NOT NULL AND \"letterGrade\" IS NOT NULL",
                new MapSqlParameterSource("sectionId", sectionId));
        if (updated == 0) {
            throw problem(HttpStatus.CONFLICT, "GRADES_EMPTY", "No complete grades are ready to publish");
        }
    }

    private void requireStudent(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + STUDENT + " WHERE \"id\" = :id AND \"status\" = 'ACTIVE'",
                new MapSqlParameterSource("id", studentId),
                Long.class);
        if (count == null || count == 0) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
    }

    private Map<String, Object> section(String sectionId) {
        try {
            return jdbc.queryForMap(
                    "SELECT \"id\", \"semesterId\" AS semester_id, \"capacity\", \"enrolledCount\" AS enrolled_count, \"status\""
                            + " FROM " + SECTION + " WHERE \"id\" = :id FOR UPDATE",
                    new MapSqlParameterSource("id", sectionId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found");
        }
    }

    private void requireSection(String sectionId) {
        section(sectionId);
    }

    private boolean semesterOpen(String semesterId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SEMESTER + " WHERE \"id\" = :id AND \"status\" IN ('OPEN', 'REGISTRATION_OPEN', 'ADD_DROP_OPEN', 'ACTIVE')",
                new MapSqlParameterSource("id", semesterId),
                Long.class);
        return count != null && count > 0;
    }

    private boolean ownsSection(String sectionId, String lecturerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION + " WHERE \"id\" = :sectionId AND \"lecturerId\" = :lecturerId",
                new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("lecturerId", lecturerId),
                Long.class);
        return count != null && count > 0;
    }

    private Map<String, Object> enrollment(String enrollmentId) {
        try {
            return jdbc.queryForMap(
                    "SELECT \"id\", \"studentId\" AS student_id, \"sectionId\" AS section_id, \"status\""
                            + " FROM " + ENROLLMENT + " WHERE \"id\" = :id FOR UPDATE",
                    new MapSqlParameterSource("id", enrollmentId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found");
        }
    }

    private static void addFilter(
            List<String> filters,
            MapSqlParameterSource parameters,
            String name,
            String value,
            String expression) {
        if (value != null && !value.isBlank()) {
            filters.add(expression);
            parameters.addValue(name, value.trim());
        }
    }

    static String csv(Object value) {
        String text = value == null ? "" : value.toString();
        if (startsFormulaCharacter(text)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static boolean startsFormulaCharacter(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char first = text.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
