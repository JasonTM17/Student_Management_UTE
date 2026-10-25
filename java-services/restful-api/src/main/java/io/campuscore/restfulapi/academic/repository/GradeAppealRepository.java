package io.campuscore.restfulapi.academic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL boundary for grade appeals and grade unpublishing. */
@Repository
@Profile("persistence")
public class GradeAppealRepository {

    static final String APPEAL = "academic.\"GradeAppeal\"";
    static final String ENROLLMENT = "academic.\"Enrollment\"";
    static final String STUDENT = "academic.\"Student\"";
    static final String SECTION = "academic.\"Section\"";
    static final String COURSE = "academic.\"Course\"";
    static final String SEMESTER = "academic.\"Semester\"";
    static final String USER = "campuscore_auth.\"User\"";

    private final NamedParameterJdbcTemplate jdbc;

    public GradeAppealRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String select() {
        return "SELECT appeal.\"id\", appeal.\"enrollmentId\" AS enrollment_id,"
                + " enrollment.\"studentId\" AS student_id, student.\"studentId\" AS student_code,"
                + " TRIM(COALESCE(account.\"lastName\", '') || ' ' || COALESCE(account.\"firstName\", '')) AS student_name,"
                + " course.\"code\" AS course_code,"
                + " course.\"name\" AS course_name, section.\"id\" AS section_id,"
                + " section.\"sectionNumber\" AS section_number, semester.\"name\" AS semester_name,"
                + " enrollment.\"finalGrade\" AS final_grade, enrollment.\"letterGrade\" AS letter_grade,"
                + " enrollment.\"gradeStatus\" AS grade_status, enrollment.\"gradePublishedAt\" AS grade_published_at,"
                + " appeal.\"reason\", appeal.\"status\", appeal.\"resolutionReason\" AS resolution_reason,"
                + " appeal.\"reviewedBy\" AS reviewed_by, appeal.\"reviewedAt\" AS reviewed_at,"
                + " appeal.\"beforeFinalGrade\" AS before_final_grade,"
                + " appeal.\"beforeLetterGrade\" AS before_letter_grade,"
                + " appeal.\"afterFinalGrade\" AS after_final_grade,"
                + " appeal.\"afterLetterGrade\" AS after_letter_grade,"
                + " appeal.\"createdAt\" AS created_at, appeal.\"updatedAt\" AS updated_at"
                + " FROM " + APPEAL + " appeal"
                + " JOIN " + ENROLLMENT + " enrollment ON enrollment.\"id\" = appeal.\"enrollmentId\""
                + " JOIN " + STUDENT + " student ON student.\"id\" = enrollment.\"studentId\""
                + " JOIN " + USER + " account ON account.\"id\" = student.\"userId\""
                + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = enrollment.\"semesterId\"";
    }

    public Map<String, Object> findEnrollment(String enrollmentId) {
        return jdbc.queryForMap(
                "SELECT \"id\", \"studentId\" AS student_id, \"sectionId\" AS section_id,"
                        + " \"status\" AS enrollment_status, \"gradeStatus\" AS grade_status,"
                        + " \"finalGrade\" AS final_grade, \"letterGrade\" AS letter_grade,"
                        + " \"gradePublishedAt\" AS grade_published_at FROM " + ENROLLMENT
                        + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", enrollmentId));
    }

    public Map<String, Object> lockEnrollment(String enrollmentId) {
        return jdbc.queryForMap(
                "SELECT \"id\", \"studentId\" AS student_id, \"sectionId\" AS section_id,"
                        + " \"status\" AS enrollment_status, \"gradeStatus\" AS grade_status,"
                        + " \"finalGrade\" AS final_grade, \"letterGrade\" AS letter_grade,"
                        + " \"gradePublishedAt\" AS grade_published_at FROM " + ENROLLMENT
                        + " WHERE \"id\" = :id FOR UPDATE",
                new MapSqlParameterSource("id", enrollmentId));
    }

    public void lockStudent(String studentId) {
        jdbc.queryForList("SELECT \"id\" FROM " + STUDENT + " WHERE \"id\" = :id FOR UPDATE",
                new MapSqlParameterSource("id", studentId));
    }

    public void lockStudents(Collection<String> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) return;
        jdbc.queryForList("SELECT \"id\" FROM " + STUDENT
                        + " WHERE \"id\" IN (:ids) ORDER BY \"id\" FOR UPDATE",
                new MapSqlParameterSource("ids", studentIds));
    }

    public Map<String, Object> lockSection(String sectionId) {
        return jdbc.queryForMap(
                "SELECT \"id\", \"lecturerId\" AS lecturer_id FROM " + SECTION
                        + " WHERE \"id\" = :id FOR UPDATE",
                new MapSqlParameterSource("id", sectionId));
    }

    public List<Map<String, Object>> findLecturerForSection(String sectionId) {
        return jdbc.queryForList("SELECT lecturer_user.\"id\" AS user_id FROM " + SECTION + " section"
                        + " JOIN academic.\"Lecturer\" lecturer ON lecturer.\"id\" = section.\"lecturerId\""
                        + " JOIN " + USER + " lecturer_user ON lecturer_user.\"id\" = lecturer.\"userId\""
                        + " WHERE section.\"id\" = :sectionId",
                new MapSqlParameterSource("sectionId", sectionId));
    }

    public List<Map<String, Object>> findAdminReviewers() {
        return jdbc.queryForList("SELECT DISTINCT account.\"id\" AS user_id FROM " + USER + " account"
                        + " JOIN campuscore_auth.\"UserRole\" user_role ON user_role.\"userId\" = account.\"id\""
                        + " JOIN campuscore_auth.\"Role\" role ON role.\"id\" = user_role.\"roleId\""
                        + " WHERE role.\"name\" IN ('ADMIN', 'SUPER_ADMIN') AND account.\"status\" = 'ACTIVE'"
                        + " ORDER BY account.\"id\"",
                new MapSqlParameterSource());
    }

    public Map<String, Object> findAppealHead(String appealId) {
        return jdbc.queryForMap(
                "SELECT appeal.\"id\", appeal.\"enrollmentId\" AS enrollment_id,"
                        + " enrollment.\"studentId\" AS student_id, enrollment.\"sectionId\" AS section_id,"
                        + " student.\"userId\" AS student_user_id, section.\"lecturerId\" AS lecturer_id"
                        + " FROM " + APPEAL + " appeal"
                        + " JOIN " + ENROLLMENT + " enrollment ON enrollment.\"id\" = appeal.\"enrollmentId\""
                        + " JOIN " + STUDENT + " student ON student.\"id\" = enrollment.\"studentId\""
                        + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                        + " WHERE appeal.\"id\" = :id",
                new MapSqlParameterSource("id", appealId));
    }

    public Map<String, Object> lockAppeal(String appealId) {
        return jdbc.queryForMap(
                "SELECT \"id\", \"enrollmentId\" AS enrollment_id, \"status\", \"reason\","
                        + " \"resolutionReason\" AS resolution_reason FROM " + APPEAL
                        + " WHERE \"id\" = :id FOR UPDATE",
                new MapSqlParameterSource("id", appealId));
    }

    public List<Map<String, Object>> listForStudent(String studentId) {
        return jdbc.queryForList(select() + " WHERE enrollment.\"studentId\" = :studentId"
                        + " ORDER BY appeal.\"createdAt\" DESC, appeal.\"id\" LIMIT 200",
                new MapSqlParameterSource("studentId", studentId));
    }

    public List<Map<String, Object>> appealEligibility(String studentId) {
        return jdbc.queryForList("SELECT enrollment.\"id\" AS enrollment_id,"
                        + " enrollment.\"status\" AS enrollment_status, enrollment.\"gradeStatus\" AS grade_status,"
                        + " enrollment.\"finalGrade\" AS final_grade, enrollment.\"letterGrade\" AS letter_grade,"
                        + " enrollment.\"gradePublishedAt\" AS grade_published_at,"
                        + " EXISTS (SELECT 1 FROM " + APPEAL + " appeal"
                        + " WHERE appeal.\"enrollmentId\" = enrollment.\"id\") AS appeal_submitted"
                        + " FROM " + ENROLLMENT + " enrollment"
                        + " WHERE enrollment.\"studentId\" = :studentId"
                        + " AND enrollment.\"gradeStatus\" IN ('PUBLISHED', 'APPEALED')"
                        + " ORDER BY enrollment.\"gradePublishedAt\" DESC, enrollment.\"id\"",
                new MapSqlParameterSource("studentId", studentId));
    }

    public List<Map<String, Object>> listForReview(String status, String lecturerId, int maxResults) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(select());
        if (status != null) {
            sql.append(" WHERE appeal.\"status\" = :status");
            parameters.addValue("status", status);
        }
        if (lecturerId != null) {
            sql.append(status == null ? " WHERE" : " AND")
                    .append(" section.\"lecturerId\" = :lecturerId");
            parameters.addValue("lecturerId", lecturerId);
        }
        sql.append(" ORDER BY appeal.\"createdAt\" DESC, appeal.\"id\" LIMIT ").append(maxResults);
        return jdbc.queryForList(sql.toString(), parameters);
    }

    public long countAppealsForEnrollment(String enrollmentId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + APPEAL
                        + " WHERE \"enrollmentId\" = :enrollmentId",
                new MapSqlParameterSource("enrollmentId", enrollmentId), Long.class);
        return count == null ? 0 : count;
    }

    public int insert(String id, String enrollmentId, String reason) {
        return jdbc.update("INSERT INTO " + APPEAL
                        + " (\"id\", \"enrollmentId\", \"reason\") VALUES (:id, :enrollmentId, :reason)",
                new MapSqlParameterSource().addValue("id", id)
                        .addValue("enrollmentId", enrollmentId).addValue("reason", reason));
    }

    public int markAppealed(String enrollmentId) {
        return jdbc.update("UPDATE " + ENROLLMENT
                        + " SET \"gradeStatus\" = 'APPEALED', \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :id AND \"gradeStatus\" = 'PUBLISHED'",
                new MapSqlParameterSource("id", enrollmentId));
    }

    public int transitionToPublished(String enrollmentId) {
        return jdbc.update("UPDATE " + ENROLLMENT
                        + " SET \"gradeStatus\" = 'PUBLISHED', \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :id AND \"gradeStatus\" = 'APPEALED'",
                new MapSqlParameterSource("id", enrollmentId));
    }

    public int correctPublishedGrade(String enrollmentId, java.math.BigDecimal score, String letterGrade) {
        return jdbc.update("UPDATE " + ENROLLMENT
                        + " SET \"gradeStatus\" = 'PUBLISHED', \"finalGrade\" = :score,"
                        + " \"letterGrade\" = :letterGrade, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :id AND \"gradeStatus\" = 'APPEALED'",
                new MapSqlParameterSource().addValue("id", enrollmentId)
                        .addValue("score", score).addValue("letterGrade", letterGrade));
    }

    public int resolveAppeal(
            String appealId,
            String status,
            String reviewerId,
            String reason,
            java.math.BigDecimal beforeScore,
            String beforeLetter,
            java.math.BigDecimal afterScore,
            String afterLetter) {
        return jdbc.update("UPDATE " + APPEAL
                        + " SET \"status\" = :status, \"reviewedBy\" = :reviewerId,"
                        + " \"reviewedAt\" = CURRENT_TIMESTAMP, \"resolutionReason\" = :reason,"
                        + " \"beforeFinalGrade\" = :beforeScore, \"beforeLetterGrade\" = :beforeLetter,"
                        + " \"afterFinalGrade\" = :afterScore, \"afterLetterGrade\" = :afterLetter,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP, \"version\" = \"version\" + 1"
                        + " WHERE \"id\" = :id AND \"status\" = 'PENDING'",
                new MapSqlParameterSource().addValue("id", appealId).addValue("status", status)
                        .addValue("reviewerId", reviewerId).addValue("reason", reason)
                        .addValue("beforeScore", beforeScore).addValue("beforeLetter", beforeLetter)
                        .addValue("afterScore", afterScore).addValue("afterLetter", afterLetter));
    }

    public int cancelAppeal(String appealId) {
        return jdbc.update("UPDATE " + APPEAL
                        + " SET \"status\" = 'CANCELLED', \"updatedAt\" = CURRENT_TIMESTAMP,"
                        + " \"version\" = \"version\" + 1 WHERE \"id\" = :id AND \"status\" = 'PENDING'",
                new MapSqlParameterSource("id", appealId));
    }

    public List<Map<String, Object>> sectionCandidates(String sectionId, Collection<String> enrollmentIds) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("sectionId", sectionId);
        String sql = "SELECT enrollment.\"id\" AS enrollment_id, enrollment.\"studentId\" AS student_id,"
                + " student_user.\"id\" AS user_id, enrollment.\"gradeStatus\" AS grade_status,"
                + " enrollment.\"status\" AS enrollment_status, enrollment.\"finalGrade\" AS final_grade,"
                + " enrollment.\"letterGrade\" AS letter_grade,"
                + " enrollment.\"gradePublishedAt\" AS grade_published_at"
                + " FROM " + ENROLLMENT + " enrollment"
                + " JOIN " + STUDENT + " student ON student.\"id\" = enrollment.\"studentId\""
                + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                + " WHERE enrollment.\"sectionId\" = :sectionId";
        if (enrollmentIds != null && !enrollmentIds.isEmpty()) {
            sql += " AND enrollment.\"id\" IN (:ids)";
            parameters.addValue("ids", enrollmentIds);
        }
        return jdbc.queryForList(sql + " ORDER BY enrollment.\"id\"", parameters);
    }

    public List<Map<String, Object>> lockPendingAppeals(Collection<String> enrollmentIds) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT \"id\", \"enrollmentId\" AS enrollment_id FROM " + APPEAL
                        + " WHERE \"enrollmentId\" IN (:ids) AND \"status\" = 'PENDING'"
                        + " ORDER BY \"id\" FOR UPDATE",
                new MapSqlParameterSource("ids", enrollmentIds));
    }

    public List<Map<String, Object>> lockSectionEnrollments(Collection<String> enrollmentIds) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT \"id\", \"studentId\" AS student_id,"
                        + " \"sectionId\" AS section_id, \"status\" AS enrollment_status,"
                        + " \"gradeStatus\" AS grade_status FROM " + ENROLLMENT
                        + " WHERE \"id\" IN (:ids) ORDER BY \"id\" FOR UPDATE",
                new MapSqlParameterSource("ids", enrollmentIds));
    }

    public List<Map<String, Object>> pendingAppealsForEnrollment(Collection<String> enrollmentIds) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT \"id\", \"enrollmentId\" AS enrollment_id FROM " + APPEAL
                        + " WHERE \"enrollmentId\" IN (:ids) AND \"status\" = 'PENDING'",
                new MapSqlParameterSource("ids", enrollmentIds));
    }

    public int unpublish(Collection<String> enrollmentIds) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) return 0;
        return jdbc.update("UPDATE " + ENROLLMENT
                        + " SET \"gradeStatus\" = 'DRAFT', \"gradePublishedAt\" = NULL,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" IN (:ids) AND \"gradeStatus\" = 'PUBLISHED'",
                new MapSqlParameterSource("ids", enrollmentIds));
    }

    public List<Map<String, Object>> findByStudent(String studentId) {
        return listForStudent(studentId);
    }

    public List<Map<String, Object>> findForReview(String status, String lecturerId, int maxResults) {
        return listForReview(status, lecturerId, maxResults);
    }

    public Map<String, Object> findById(String id) {
        return jdbc.queryForMap(select() + " WHERE appeal.\"id\" = :id",
                new MapSqlParameterSource("id", id));
    }
}
