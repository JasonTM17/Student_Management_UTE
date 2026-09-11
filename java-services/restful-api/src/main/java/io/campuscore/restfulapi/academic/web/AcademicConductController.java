package io.campuscore.restfulapi.academic.web;

import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductActivityDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductCriteriaScoreDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductSemesterScoreDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.StudentConductSummaryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller providing UTE Student Conduct / Training Points ("Điểm rèn luyện" - DRL).
 * Follows the 5-criteria Ministry of Education & Training (MOET) framework.
 */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/conduct")
public class AcademicConductController {

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicConductController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("my")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentConductSummaryDto getMyConductSummary(@AuthenticationPrincipal Jwt jwt) {
        String studentProfileId = resolveStudentProfileId(jwt);
        return buildStudentConductSummary(studentProfileId);
    }

    @GetMapping("student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'LECTURER')")
    public StudentConductSummaryDto getStudentConductSummary(@PathVariable String studentId) {
        return buildStudentConductSummary(studentId);
    }

    @GetMapping("my/semester/{semesterId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ConductSemesterScoreDto getMySemesterScore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String semesterId) {
        String studentProfileId = resolveStudentProfileId(jwt);
        return loadSemesterScore(studentProfileId, semesterId);
    }

    private String resolveStudentProfileId(Jwt jwt) {
        String studentIdClaim = jwt.getClaimAsString("studentId");
        if (studentIdClaim != null && !studentIdClaim.isBlank()) {
            return studentIdClaim;
        }
        String userId = jwt.getSubject();
        List<String> profileIds = jdbc.query(
                "SELECT \"id\" FROM academic.\"Student\" WHERE \"userId\" = :userId LIMIT 1",
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> rs.getString("id"));
        if (!profileIds.isEmpty()) {
            return profileIds.get(0);
        }
        // Fallback to default demo student
        return "student-profile";
    }

    private StudentConductSummaryDto buildStudentConductSummary(String studentProfileId) {
        // 1. Student metadata
        List<Map<String, Object>> studentRows = jdbc.queryForList(
                "SELECT s.\"id\", s.\"studentId\", u.\"firstName\", u.\"lastName\" "
                        + "FROM academic.\"Student\" s "
                        + "JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\" "
                        + "WHERE s.\"id\" = :studentId OR s.\"studentId\" = :studentId LIMIT 1",
                new MapSqlParameterSource("studentId", studentProfileId));

        String studentCode;
        String fullName;
        String resolvedProfileId = studentProfileId;

        if (!studentRows.isEmpty()) {
            Map<String, Object> row = studentRows.get(0);
            resolvedProfileId = String.valueOf(row.get("id"));
            studentCode = String.valueOf(row.get("studentId"));
            String first = String.valueOf(row.get("firstName"));
            String last = String.valueOf(row.get("lastName"));
            fullName = (last + " " + first).trim();
        } else if ("student-profile".equals(studentProfileId) || "student-user".equals(studentProfileId)) {
            studentCode = "24110054";
            fullName = "Nguyễn Tiến Sơn";
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Student not found: " + studentProfileId);
        }

        // 2. Query all conduct scores for this student, ordered by semester start date descending
        String sql = "SELECT c.id, c.student_id, c.semester_id, s.\"name\" AS semester_name, "
                + "c.criteria1_score, c.criteria2_score, c.criteria3_score, c.criteria4_score, c.criteria5_score, "
                + "c.total_score, c.classification, c.classification_vi, c.status, c.evaluator_name "
                + "FROM academic.conduct_semester_score c "
                + "JOIN academic.\"Semester\" s ON s.\"id\" = c.semester_id "
                + "WHERE c.student_id = :studentId "
                + "ORDER BY s.\"startDate\" DESC";

        List<Map<String, Object>> scoreRows = jdbc.queryForList(
                sql, new MapSqlParameterSource("studentId", resolvedProfileId));

        if (scoreRows.isEmpty()) {
            if ("student-profile".equals(resolvedProfileId) || "student-user".equals(resolvedProfileId)) {
                scoreRows = jdbc.queryForList(sql, new MapSqlParameterSource("studentId", "student-profile"));
            }
        }

        if (scoreRows.isEmpty()) {
            return new StudentConductSummaryDto(
                    resolvedProfileId,
                    studentCode,
                    fullName,
                    BigDecimal.ZERO,
                    "Chưa có đánh giá",
                    null,
                    List.of()
            );
        }

        List<ConductSemesterScoreDto> history = new ArrayList<>();
        BigDecimal sumTotal = BigDecimal.ZERO;
        int count = 0;

        for (Map<String, Object> row : scoreRows) {
            String semId = String.valueOf(row.get("semester_id"));
            ConductSemesterScoreDto dto = mapRowToSemesterDto(row, resolvedProfileId, semId);
            history.add(dto);
            sumTotal = sumTotal.add(dto.totalScore());
            count++;
        }

        ConductSemesterScoreDto current = history.isEmpty() ? null : history.get(0);
        BigDecimal cumulativeAverage = count > 0
                ? sumTotal.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(88.0);

        String cumulativeClassification = classifyConductScore(cumulativeAverage);

        return new StudentConductSummaryDto(
                resolvedProfileId,
                studentCode,
                fullName,
                cumulativeAverage,
                cumulativeClassification,
                current,
                history
        );
    }

    private ConductSemesterScoreDto loadSemesterScore(String studentProfileId, String semesterId) {
        String sql = "SELECT c.id, c.student_id, c.semester_id, s.\"name\" AS semester_name, "
                + "c.criteria1_score, c.criteria2_score, c.criteria3_score, c.criteria4_score, c.criteria5_score, "
                + "c.total_score, c.classification, c.classification_vi, c.status, c.evaluator_name "
                + "FROM academic.conduct_semester_score c "
                + "JOIN academic.\"Semester\" s ON s.\"id\" = c.semester_id "
                + "WHERE c.student_id = :studentId AND c.semester_id = :semesterId "
                + "LIMIT 1";

        List<Map<String, Object>> rows = jdbc.queryForList(
                sql,
                new MapSqlParameterSource("studentId", studentProfileId)
                        .addValue("semesterId", semesterId));

        if (rows.isEmpty() && ("student-profile".equals(studentProfileId) || "student-user".equals(studentProfileId))) {
            rows = jdbc.queryForList(
                    sql,
                    new MapSqlParameterSource("studentId", "student-profile")
                            .addValue("semesterId", semesterId));
        }

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conduct score not found for semester");
        }

        return mapRowToSemesterDto(rows.get(0), studentProfileId, semesterId);
    }

    private ConductSemesterScoreDto mapRowToSemesterDto(
            Map<String, Object> row, String studentProfileId, String semesterId) {
        String id = String.valueOf(row.get("id"));
        String semesterName = String.valueOf(row.get("semester_name"));
        BigDecimal c1 = toBigDecimal(row.get("criteria1_score"));
        BigDecimal c2 = toBigDecimal(row.get("criteria2_score"));
        BigDecimal c3 = toBigDecimal(row.get("criteria3_score"));
        BigDecimal c4 = toBigDecimal(row.get("criteria4_score"));
        BigDecimal c5 = toBigDecimal(row.get("criteria5_score"));
        BigDecimal total = toBigDecimal(row.get("total_score"));
        String classification = String.valueOf(row.get("classification"));
        String classificationVi = String.valueOf(row.get("classification_vi"));
        String status = String.valueOf(row.get("status"));
        String evaluatorName = (String) row.get("evaluator_name");

        List<ConductCriteriaScoreDto> criteria = List.of(
                new ConductCriteriaScoreDto("CRITERIA_1", "Ý thức tham gia học tập", "Learning Attitude & Academic Results", BigDecimal.valueOf(20), c1, "Tham gia đầy đủ các buổi học, làm bài tập và thái độ tích cực trong giờ học."),
                new ConductCriteriaScoreDto("CRITERIA_2", "Ý thức chấp hành nội quy, quy chế", "Compliance with Regulations", BigDecimal.valueOf(25), c2, "Chấp hành tốt các quy định của nhà trường, pháp luật và quy chế thi cử."),
                new ConductCriteriaScoreDto("CRITERIA_3", "Ý thức tham gia hoạt động chính trị - xã hội, văn thể mỹ", "Extracurricular & Social Activities", BigDecimal.valueOf(20), c3, "Tham gia các phong trào Đoàn - Hội, hoạt động tình nguyện, văn nghệ, thể thao."),
                new ConductCriteriaScoreDto("CRITERIA_4", "Phẩm chất công dân và quan hệ cộng đồng", "Civic Quality & Community Relations", BigDecimal.valueOf(25), c4, "Ý thức trách nhiệm với xã hội, quan hệ tốt với bạn bè, lối sống lành mạnh."),
                new ConductCriteriaScoreDto("CRITERIA_5", "Ý thức tham gia công tác cán bộ lớp, đoàn thể", "Class / Union Leadership & Special Achievements", BigDecimal.valueOf(10), c5, "Đóng góp tích cực cho ban cán sự lớp, các câu lạc bộ hoặc đạt giải thưởng học thuật.")
        );

        // Fetch activities for this student and semester
        List<ConductActivityDto> activities = jdbc.query(
                "SELECT id, title, category, points, activity_date, organizer, certificate_url "
                        + "FROM academic.conduct_activity "
                        + "WHERE (student_id = :studentId OR (:studentId IN ('student-profile', 'student-user') AND student_id = 'student-profile')) AND semester_id = :semesterId "
                        + "ORDER BY activity_date DESC",
                new MapSqlParameterSource("studentId", studentProfileId).addValue("semesterId", semesterId),
                (rs, rowNum) -> {
                    Date d = rs.getDate("activity_date");
                    return new ConductActivityDto(
                            rs.getString("id"),
                            rs.getString("title"),
                            rs.getString("category"),
                            rs.getBigDecimal("points"),
                            d != null ? d.toLocalDate() : null,
                            rs.getString("organizer"),
                            rs.getString("certificate_url"));
                });

        return new ConductSemesterScoreDto(
                id,
                semesterId,
                semesterName,
                c1,
                c2,
                c3,
                c4,
                c5,
                total,
                classification,
                classificationVi,
                status,
                evaluatorName,
                criteria,
                activities
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        return new BigDecimal(value.toString());
    }

    private String classifyConductScore(BigDecimal score) {
        double val = score.doubleValue();
        if (val >= 90.0) return "Xuất sắc";
        if (val >= 80.0) return "Tốt";
        if (val >= 65.0) return "Khá";
        if (val >= 50.0) return "Trung bình";
        if (val >= 35.0) return "Yếu";
        return "Kém";
    }
}
