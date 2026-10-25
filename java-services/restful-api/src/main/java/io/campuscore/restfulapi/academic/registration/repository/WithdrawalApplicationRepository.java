package io.campuscore.restfulapi.academic.registration.repository;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** All SQL for the approved course-withdrawal (grade W) workflow. */
@Repository
@Profile("persistence")
public class WithdrawalApplicationRepository {

    static final String APPLICATION = "academic.\"WithdrawalApplication\"";
    static final String ENROLLMENT = "academic.\"Enrollment\"";
    static final String SECTION = "academic.\"Section\"";
    static final String COURSE = "academic.\"Course\"";
    static final String SEMESTER = "academic.\"Semester\"";
    static final String STUDENT = "academic.\"Student\"";
    static final String USER = "campuscore_auth.\"User\"";
    static final String EVENT = "academic.\"EnrollmentEvent\"";
    static final String SLIP = "academic.\"RegistrationSlip\"";

    private final NamedParameterJdbcTemplate jdbc;

    public WithdrawalApplicationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String select() {
        return "SELECT application.\"id\", application.\"studentId\" AS student_id, student.\"studentId\" AS student_code,"
                + " TRIM(COALESCE(account.\"lastName\", '') || ' ' || COALESCE(account.\"firstName\", '')) AS student_name,"
                + " account.\"email\" AS student_email, application.\"enrollmentId\" AS enrollment_id,"
                + " application.\"semesterId\" AS semester_id,"
                + " semester.\"name\" AS semester_name, course.\"code\" AS course_code,"
                + " course.\"name\" AS course_name, section.\"sectionNumber\" AS section_number,"
                + " application.\"reason\", application.\"status\", application.\"reviewedBy\" AS reviewed_by,"
                + " application.\"reviewedAt\" AS reviewed_at, application.\"reviewerNote\" AS reviewer_note,"
                + " application.\"createdAt\" AS created_at, application.\"updatedAt\" AS updated_at"
                + " FROM " + APPLICATION + " application"
                + " JOIN " + STUDENT + " student ON student.\"id\" = application.\"studentId\""
                + " JOIN " + USER + " account ON account.\"id\" = student.\"userId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = application.\"semesterId\""
                + " JOIN " + ENROLLMENT + " enrollment ON enrollment.\"id\" = application.\"enrollmentId\""
                + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\"";
    }

    public Map<String, Object> findById(String id) {
        return jdbc.queryForMap(select() + " WHERE application.\"id\" = :id",
                new MapSqlParameterSource("id", id));
    }

    public Map<String, Object> lockById(String id) {
        return jdbc.queryForMap(
                "SELECT \"id\", \"studentId\" AS student_id, \"enrollmentId\" AS enrollment_id,"
                        + " \"semesterId\" AS semester_id, \"status\" FROM " + APPLICATION
                        + " WHERE \"id\" = :id FOR UPDATE",
                new MapSqlParameterSource("id", id));
    }

    public List<Map<String, Object>> listForStudent(String studentId) {
        return jdbc.queryForList(select() + " WHERE application.\"studentId\" = :studentId"
                        + " ORDER BY application.\"createdAt\" DESC LIMIT 200",
                new MapSqlParameterSource("studentId", studentId));
    }

    public List<Map<String, Object>> listForReview(String status, int maxResults) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String sql = select();
        if (status != null) {
            sql += " WHERE application.\"status\" = :status";
            parameters.addValue("status", status);
        }
        sql += " ORDER BY application.\"createdAt\" DESC LIMIT " + maxResults;
        return jdbc.queryForList(sql, parameters);
    }

    public long countPendingForEnrollment(String enrollmentId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + APPLICATION
                        + " WHERE \"enrollmentId\" = :enrollmentId AND \"status\" = 'PENDING'",
                new MapSqlParameterSource("enrollmentId", enrollmentId), Long.class);
        return count == null ? 0 : count;
    }

    public int insert(String id, String studentId, String enrollmentId, String semesterId, String reason) {
        return jdbc.update(
                "INSERT INTO " + APPLICATION
                        + " (\"id\", \"studentId\", \"enrollmentId\", \"semesterId\", \"reason\")"
                        + " VALUES (:id, :studentId, :enrollmentId, :semesterId, :reason)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("studentId", studentId)
                        .addValue("enrollmentId", enrollmentId)
                        .addValue("semesterId", semesterId)
                        .addValue("reason", reason));
    }

    /** Conditional transition: returns 0 when the row left PENDING under us. */
    public int transition(String id, String status, String reviewedBy, String reviewerNote) {
        return jdbc.update(
                "UPDATE " + APPLICATION
                        + " SET \"status\" = :status, \"reviewedBy\" = :reviewedBy,"
                        + " \"reviewedAt\" = :reviewedAt, \"reviewerNote\" = :reviewerNote,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP, \"version\" = \"version\" + 1"
                        + " WHERE \"id\" = :id AND \"status\" = 'PENDING'",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("reviewedBy", reviewedBy)
                        .addValue("reviewedAt", reviewedBy == null ? null : java.sql.Timestamp.from(java.time.Instant.now()))
                        .addValue("reviewerNote", reviewerNote)
                        .addValue("id", id));
    }

    /** Locks the Student row first: every registration mutation serializes on it. */
    public void lockStudent(String studentId) {
        jdbc.queryForList("SELECT \"id\" FROM " + STUDENT + " WHERE \"id\" = :id FOR UPDATE",
                new MapSqlParameterSource("id", studentId));
    }

    public long studentExists(String studentId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + STUDENT + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", studentId), Long.class);
        return count == null ? 0 : count;
    }

    public String studentUserId(String studentId) {
        try {
            return jdbc.queryForObject("SELECT \"userId\" FROM " + STUDENT + " WHERE \"id\" = :id",
                    new MapSqlParameterSource("id", studentId), String.class);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    /** Enrollment row + semester facts, unlocked: submit-time validation read. */
    public Map<String, Object> findEnrollmentWithSemester(String enrollmentId) {
        return jdbc.queryForMap(
                "SELECT e.\"id\", e.\"studentId\" AS student_id, e.\"sectionId\" AS section_id,"
                        + " e.\"semesterId\" AS semester_id, e.\"status\","
                        + " s.\"type\" AS semester_type, s.\"status\" AS semester_status,"
                        + " s.\"name\" AS semester_name, s.\"startDate\" AS semester_start"
                        + " FROM " + ENROLLMENT + " e JOIN " + SEMESTER + " s ON s.\"id\" = e.\"semesterId\""
                        + " WHERE e.\"id\" = :id",
                new MapSqlParameterSource("id", enrollmentId));
    }

    /** Approve-side enrollment lock; the join stays out so only this row is held. */
    public Map<String, Object> lockEnrollment(String enrollmentId) {
        return jdbc.queryForMap(
                "SELECT \"id\", \"studentId\" AS student_id, \"sectionId\" AS section_id,"
                        + " \"semesterId\" AS semester_id, \"status\""
                        + " FROM " + ENROLLMENT + " WHERE \"id\" = :id FOR UPDATE",
                new MapSqlParameterSource("id", enrollmentId));
    }

    public Map<String, Object> lockSection(String sectionId) {
        return jdbc.queryForMap(
                "SELECT \"id\", \"status\", \"courseId\" AS course_id, \"capacity\", \"enrolledCount\""
                        + " FROM " + SECTION + " WHERE \"id\" = :id FOR UPDATE",
                new MapSqlParameterSource("id", sectionId));
    }

    /** Approve-side transition: only an active enrollment may become W. */
    public int markEnrollmentWithdrawn(String enrollmentId, java.util.Set<String> activeStatuses) {
        return jdbc.update(
                "UPDATE " + ENROLLMENT + " SET \"status\" = 'WITHDRAWN', \"letterGrade\" = 'W',"
                        + " \"gradeStatus\" = 'PUBLISHED', \"gradePublishedAt\" = CURRENT_TIMESTAMP,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :id AND \"status\" IN (:active)",
                new MapSqlParameterSource()
                        .addValue("id", enrollmentId)
                        .addValue("active", activeStatuses));
    }

    public void releaseSeat(String sectionId) {
        jdbc.update(
                "UPDATE " + SECTION
                        + " SET \"enrolledCount\" = CASE WHEN \"enrolledCount\" > 0 THEN \"enrolledCount\" - 1 ELSE 0 END,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource("id", sectionId));
    }

    /** Same slip invalidation as a drop: persisted slips mirror the active set. */
    public void deleteSlips(String studentId, String semesterId) {
        jdbc.update("DELETE FROM " + SLIP + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId",
                new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("semesterId", semesterId));
    }

    public void insertEvent(String id, String enrollmentId, String studentId, String sectionId,
            String action, String actorId, String requestHash) {
        jdbc.update(
                "INSERT INTO " + EVENT
                        + " (\"id\", \"enrollmentId\", \"studentId\", \"sectionId\", \"action\", \"actorId\", \"requestHash\")"
                        + " VALUES (:id, :enrollmentId, :studentId, :sectionId, :action, :actorId, :requestHash)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("enrollmentId", enrollmentId)
                        .addValue("studentId", studentId)
                        .addValue("sectionId", sectionId)
                        .addValue("action", action)
                        .addValue("actorId", actorId)
                        .addValue("requestHash", requestHash));
    }

    /** Read-only semester facts for the student-facing window probe. */
    public Map<String, Object> findSemester(String semesterId) {
        return jdbc.queryForMap(
                "SELECT \"id\", \"name\", \"type\", \"status\", \"startDate\" AS semester_start"
                        + " FROM " + SEMESTER + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", semesterId));
    }
}
