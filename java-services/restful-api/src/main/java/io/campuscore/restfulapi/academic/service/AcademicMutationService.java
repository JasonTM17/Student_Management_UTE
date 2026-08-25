package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.persistence.AcademicSectionEntity;
import io.campuscore.restfulapi.academic.persistence.AcademicSectionRepository;
import io.campuscore.restfulapi.academic.persistence.EnrollmentEntity;
import io.campuscore.restfulapi.academic.persistence.EnrollmentRepository;
import io.campuscore.restfulapi.academic.web.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
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
    private final AcademicSectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final Clock clock;

    public AcademicMutationService(
            NamedParameterJdbcTemplate jdbc,
            AcademicSectionRepository sectionRepository,
            EnrollmentRepository enrollmentRepository,
            Clock clock) {
        this.jdbc = jdbc;
        this.sectionRepository = sectionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.clock = clock;
    }

    @Transactional
    public void deleteEnrollment(String enrollmentId) {
        EnrollmentEntity preview = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        AcademicSectionEntity section = sectionRepository.findLockedById(preview.getSectionId())
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found"));
        EnrollmentEntity enrollment = enrollmentRepository.findLockedById(enrollmentId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        if (isCapacityBearing(enrollment.getStatus())) {
            try {
                section.decrementEnrollment();
            } catch (IllegalStateException underflow) {
                throw problem(HttpStatus.CONFLICT, "SECTION_COUNT_INVARIANT", "Section enrollment count is inconsistent");
            }
            sectionRepository.save(section);
        }
        enrollmentRepository.delete(enrollment);
    }

    @Transactional(readOnly = true)
    public String exportEnrollments(
            String status,
            String semesterId,
            String studentId,
            String courseId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        List<String> filters = new ArrayList<>();
        addFilter(filters, parameters, "status", status, "e.\"status\" = :status");
        addFilter(filters, parameters, "semesterId", semesterId, "e.\"semesterId\" = :semesterId");
        addFilter(filters, parameters, "studentId", studentId, "e.\"studentId\" = :studentId");
        addFilter(filters, parameters, "courseId", courseId, "section.\"courseId\" = :courseId");
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
        sectionRepository.findLockedById(sectionId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found"));
        if (!admin && !ownsSection(sectionId, lecturerId)) {
            throw problem(HttpStatus.FORBIDDEN, "SECTION_FORBIDDEN", "Section is not assigned to the current lecturer");
        }
        Instant now = clock.instant();
        for (GradeUpdate grade : grades.stream().sorted(Comparator.comparing(GradeUpdate::enrollmentId)).toList()) {
            EnrollmentEntity enrollment = enrollmentRepository.findLockedById(grade.enrollmentId())
                    .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
            if (!sectionId.equals(enrollment.getSectionId())) {
                throw problem(HttpStatus.BAD_REQUEST, "GRADE_SECTION_MISMATCH", "Grade enrollment is outside this section");
            }
            enrollment.updateGrade(grade.finalGrade(), grade.letterGrade(), now);
        }
    }

    @Transactional
    public void publishGrades(String sectionId, String lecturerId, boolean admin) {
        sectionRepository.findLockedById(sectionId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found"));
        if (!admin && !ownsSection(sectionId, lecturerId)) {
            throw problem(HttpStatus.FORBIDDEN, "SECTION_FORBIDDEN", "Section is not assigned to the current lecturer");
        }
        Instant now = clock.instant();
        int updated = 0;
        for (EnrollmentEntity enrollment : enrollmentRepository.findLockedBySectionId(sectionId)) {
            if (enrollment.publishGrade(now)) updated++;
        }
        if (updated == 0) {
            throw problem(HttpStatus.CONFLICT, "GRADES_EMPTY", "No complete grades are ready to publish");
        }
    }

    private boolean ownsSection(String sectionId, String lecturerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION + " WHERE \"id\" = :sectionId AND \"lecturerId\" = :lecturerId",
                new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("lecturerId", lecturerId),
                Long.class);
        return count != null && count > 0;
    }


    private static boolean isCapacityBearing(String status) {
        return status != null && List.of("ACTIVE", "ENROLLED", "PENDING", "CONFIRMED")
                .contains(status.toUpperCase(java.util.Locale.ROOT));
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

    private static String csv(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
