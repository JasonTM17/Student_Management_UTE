package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductActivityDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductCriteriaScoreDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.ConductSemesterScoreDto;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.StudentConductSummaryDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * UTE Student Conduct / Training Points ("Điểm rèn luyện" - DRL) reads.
 * Follows the 5-criteria Ministry of Education & Training (MOET) framework.
 * All queries are read-only; scores live in the
 * {@code academic.conduct_semester_score} / {@code academic.conduct_activity}
 * tables while the criteria catalogue itself is fixed regulation, not data.
 */
@Service
@Profile("persistence")
public class AcademicConductService {

    /** The five MOET conduct criteria and their maximum weights (sum = 100). */
    private static final List<ConductCriteriaScoreDto> CRITERIA_TEMPLATE = List.of(
            new ConductCriteriaScoreDto("CRITERIA_1", "Ý thức tham gia học tập",
                    "Learning Attitude & Academic Results", BigDecimal.valueOf(20), null,
                    "Tham gia đầy đủ các buổi học, làm bài tập và thái độ tích cực trong giờ học."),
            new ConductCriteriaScoreDto("CRITERIA_2", "Ý thức chấp hành nội quy, quy chế",
                    "Compliance with Regulations", BigDecimal.valueOf(25), null,
                    "Chấp hành tốt các quy định của nhà trường, pháp luật và quy chế thi cử."),
            new ConductCriteriaScoreDto("CRITERIA_3", "Ý thức tham gia hoạt động chính trị - xã hội, văn thể mỹ",
                    "Extracurricular & Social Activities", BigDecimal.valueOf(20), null,
                    "Tham gia các phong trào Đoàn - Hội, hoạt động tình nguyện, văn nghệ, thể thao."),
            new ConductCriteriaScoreDto("CRITERIA_4", "Phẩm chất công dân và quan hệ cộng đồng",
                    "Civic Quality & Community Relations", BigDecimal.valueOf(25), null,
                    "Ý thức trách nhiệm với xã hội, quan hệ tốt với bạn bè, lối sống lành mạnh."),
            new ConductCriteriaScoreDto("CRITERIA_5", "Ý thức tham gia công tác cán bộ lớp, đoàn thể",
                    "Class / Union Leadership & Special Achievements", BigDecimal.valueOf(10), null,
                    "Đóng góp tích cực cho ban cán sự lớp, các câu lạc bộ hoặc đạt giải thưởng học thuật."));

    private static final String SCORE_COLUMNS =
            "c.criteria1_score, c.criteria2_score, c.criteria3_score, c.criteria4_score, c.criteria5_score, ";

    private final NamedParameterJdbcTemplate jdbc;

    public AcademicConductService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * LEC-P1-5: a lecturer may read conduct only for students they actually teach.
     * The path parameter accepts either the profile id or the student code, so
     * both are matched against the Student table before looking at enrollments.
     */
    public boolean isStudentTaughtByLecturer(String studentId, String lecturerId) {
        Integer taught = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Enrollment\" e"
                        + " JOIN academic.\"Section\" sec ON sec.\"id\" = e.\"sectionId\""
                        + " WHERE sec.\"lecturerId\" = :lecturerId"
                        + " AND e.\"studentId\" IN ("
                        + "   SELECT s.\"id\" FROM academic.\"Student\" s"
                        + "   WHERE s.\"id\" = :studentId OR s.\"studentId\" = :studentId)",
                new MapSqlParameterSource()
                        .addValue("lecturerId", lecturerId)
                        .addValue("studentId", studentId),
                Integer.class);
        return taught != null && taught > 0;
    }

    /** Resolves the student profile id behind a login when the JWT lacks the claim. */
    public String profileIdForUser(String userId) {
        List<String> profileIds = jdbc.query(
                "SELECT \"id\" FROM academic.\"Student\" WHERE \"userId\" = :userId LIMIT 1",
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> rs.getString("id"));
        return profileIds.isEmpty() ? null : profileIds.get(0);
    }

    public StudentConductSummaryDto studentSummary(String studentProfileId) {
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
        } else {
            // No magic-id demo aliasing: a request for an unknown student (or
            // for the former literal ids) must never return someone else's
            // hardcoded identity.
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Student not found: " + studentProfileId);
        }

        // 2. Query all conduct scores for this student, ordered by semester start date descending
        String sql = "SELECT c.id, c.student_id, c.semester_id, s.\"name\" AS semester_name, "
                + SCORE_COLUMNS
                + "c.total_score, c.classification, c.classification_vi, c.status, c.evaluator_name "
                + "FROM academic.conduct_semester_score c "
                + "JOIN academic.\"Semester\" s ON s.\"id\" = c.semester_id "
                + "WHERE c.student_id = :studentId "
                + "ORDER BY s.\"startDate\" DESC";

        List<Map<String, Object>> scoreRows = jdbc.queryForList(
                sql, new MapSqlParameterSource("studentId", resolvedProfileId));

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

        // Eliminate N+1 query loop: batch-fetch all conduct activities for the student
        Map<String, List<ConductActivityDto>> activitiesBySemester = fetchActivitiesGroupedBySemester(resolvedProfileId);

        for (Map<String, Object> row : scoreRows) {
            String semId = String.valueOf(row.get("semester_id"));
            List<ConductActivityDto> activities = activitiesBySemester.getOrDefault(semId, List.of());
            ConductSemesterScoreDto dto = semesterScoreRow(row, resolvedProfileId, semId, activities);
            history.add(dto);
            sumTotal = sumTotal.add(dto.totalScore());
            count++;
        }

        ConductSemesterScoreDto current = history.isEmpty() ? null : history.get(0);
        // scoreRows is non-empty here (the empty case returned above), so the
        // average is always computed from real rows — no invented 88.0.
        BigDecimal cumulativeAverage = sumTotal.divide(
                BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);

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

    public ConductSemesterScoreDto semesterScore(String studentProfileId, String semesterId) {
        String sql = "SELECT c.id, c.student_id, c.semester_id, s.\"name\" AS semester_name, "
                + SCORE_COLUMNS
                + "c.total_score, c.classification, c.classification_vi, c.status, c.evaluator_name "
                + "FROM academic.conduct_semester_score c "
                + "JOIN academic.\"Semester\" s ON s.\"id\" = c.semester_id "
                + "WHERE c.student_id = :studentId AND c.semester_id = :semesterId "
                + "LIMIT 1";

        List<Map<String, Object>> rows = jdbc.queryForList(
                sql,
                new MapSqlParameterSource("studentId", studentProfileId)
                        .addValue("semesterId", semesterId));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conduct score not found for semester");
        }

        List<ConductActivityDto> activities = fetchActivitiesForSemester(studentProfileId, semesterId);
        return semesterScoreRow(rows.get(0), studentProfileId, semesterId, activities);
    }

    private ConductSemesterScoreDto semesterScoreRow(
            Map<String, Object> row, String studentProfileId, String semesterId, List<ConductActivityDto> activities) {
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
                withScore(CRITERIA_TEMPLATE.get(0), c1),
                withScore(CRITERIA_TEMPLATE.get(1), c2),
                withScore(CRITERIA_TEMPLATE.get(2), c3),
                withScore(CRITERIA_TEMPLATE.get(3), c4),
                withScore(CRITERIA_TEMPLATE.get(4), c5));

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
                activities == null ? List.of() : activities
        );
    }

    private Map<String, List<ConductActivityDto>> fetchActivitiesGroupedBySemester(String studentProfileId) {
        List<Map.Entry<String, ConductActivityDto>> entries = jdbc.query(
                "SELECT id, semester_id, title, category, points, activity_date, organizer, certificate_url "
                        + "FROM academic.conduct_activity "
                        + "WHERE student_id = :studentId "
                        + "ORDER BY activity_date DESC",
                new MapSqlParameterSource("studentId", studentProfileId),
                (rs, rowNum) -> {
                    Date d = rs.getDate("activity_date");
                    ConductActivityDto dto = new ConductActivityDto(
                            rs.getString("id"),
                            rs.getString("title"),
                            rs.getString("category"),
                            rs.getBigDecimal("points"),
                            d != null ? d.toLocalDate() : null,
                            rs.getString("organizer"),
                            rs.getString("certificate_url"));
                    return Map.entry(String.valueOf(rs.getObject("semester_id")), dto);
                });

        Map<String, List<ConductActivityDto>> grouped = new HashMap<>();
        for (Map.Entry<String, ConductActivityDto> entry : entries) {
            grouped.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
        }
        return grouped;
    }

    private List<ConductActivityDto> fetchActivitiesForSemester(String studentProfileId, String semesterId) {
        return jdbc.query(
                "SELECT id, title, category, points, activity_date, organizer, certificate_url "
                        + "FROM academic.conduct_activity "
                        + "WHERE student_id = :studentId AND semester_id = :semesterId "
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
    }

    private static ConductCriteriaScoreDto withScore(ConductCriteriaScoreDto template, BigDecimal score) {
        return new ConductCriteriaScoreDto(
                template.code(),
                template.nameVi(),
                template.nameEn(),
                template.maxScore(),
                score,
                template.description());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        return new BigDecimal(value.toString());
    }

    private static String classifyConductScore(BigDecimal score) {
        double val = score.doubleValue();
        if (val >= 90.0) return "Xuất sắc";
        if (val >= 80.0) return "Tốt";
        if (val >= 65.0) return "Khá";
        if (val >= 50.0) return "Trung bình";
        if (val >= 35.0) return "Yếu";
        return "Kém";
    }
}
