package io.campuscore.restfulapi.mail.repository;

import io.campuscore.restfulapi.mail.model.ScopedRecipient;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Resolves active mail recipients and lecturer-scoped student recipients. */
@Repository
@Profile("persistence")
public class MailRecipientScopeRepository {

    private static final String STUDENT = "\"academic\".\"Student\"";
    private static final String USER = "\"campuscore_auth\".\"User\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";

    private final NamedParameterJdbcTemplate jdbc;

    public MailRecipientScopeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Finds an active account by email for administrator-authored notices.
     * The caller uses the returned address and name instead of request data.
     */
    public ScopedRecipient findActiveRecipientByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        List<ScopedRecipient> rows = jdbc.query(
                "SELECT u.\"email\" AS student_email, u.\"firstName\" AS first_name,"
                        + " u.\"lastName\" AS last_name, CAST(NULL AS VARCHAR(120)) AS student_number"
                        + " FROM " + USER + " u"
                        + " WHERE LOWER(u.\"email\") = LOWER(:email) AND u.\"status\" = 'ACTIVE' LIMIT 1",
                new MapSqlParameterSource("email", email.trim()),
                MailRecipientScopeRepository::map);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Finds the caller's active account when an access token has no email claim. */
    public ScopedRecipient findActiveRecipientByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        List<ScopedRecipient> rows = jdbc.query(
                "SELECT u.\"email\" AS student_email, u.\"firstName\" AS first_name,"
                        + " u.\"lastName\" AS last_name, CAST(NULL AS VARCHAR(120)) AS student_number"
                        + " FROM " + USER + " u"
                        + " WHERE u.\"id\" = :userId AND u.\"status\" = 'ACTIVE' LIMIT 1",
                new MapSqlParameterSource("userId", userId.trim()),
                MailRecipientScopeRepository::map);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Finds an active student by student number or account email for the
     * administrator-only student templates.
     */
    public ScopedRecipient findActiveStudentRecipient(String studentKey) {
        if (studentKey == null || studentKey.isBlank()) {
            return null;
        }
        List<ScopedRecipient> rows = jdbc.query(
                "SELECT student_user.\"email\" AS student_email,"
                        + " student_user.\"firstName\" AS first_name, student_user.\"lastName\" AS last_name,"
                        + " s.\"studentId\" AS student_number"
                        + " FROM " + STUDENT + " s"
                        + " JOIN " + USER + " student_user ON student_user.\"id\" = s.\"userId\""
                        + " WHERE (s.\"studentId\" = :studentKey"
                        + " OR LOWER(student_user.\"email\") = LOWER(:studentKey))"
                        + " AND s.\"status\" = 'ACTIVE' AND student_user.\"status\" = 'ACTIVE' LIMIT 1",
                new MapSqlParameterSource("studentKey", studentKey.trim()),
                MailRecipientScopeRepository::map);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Finds the student whose {@code studentKey} (student number or account
     * email) is or was enrolled in a section taught by {@code lecturerId}.
     * Returns null when no such relationship exists.
     */
    public ScopedRecipient findScopedRecipient(String lecturerId, String studentKey) {
        List<ScopedRecipient> rows = jdbc.query(
                "SELECT student_user.\"email\" AS student_email,"
                        + " student_user.\"firstName\" AS first_name, student_user.\"lastName\" AS last_name,"
                        + " s.\"studentId\" AS student_number"
                        + " FROM " + STUDENT + " s"
                        + " JOIN " + USER + " student_user ON student_user.\"id\" = s.\"userId\""
                        + " WHERE (s.\"studentId\" = :studentKey OR student_user.\"email\" = :studentKey)"
                        + " AND EXISTS ("
                        + "   SELECT 1 FROM " + ENROLLMENT + " e"
                        + "   JOIN " + SECTION + " sec ON sec.\"id\" = e.\"sectionId\""
                        + "   WHERE e.\"studentId\" = s.\"id\""
                        + "     AND sec.\"lecturerId\" = :lecturerId"
                        + "     AND e.\"status\" IN ('ENROLLED','PENDING','CONFIRMED','COMPLETED')"
                        + " ) LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("lecturerId", lecturerId)
                        .addValue("studentKey", studentKey),
                MailRecipientScopeRepository::map);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static ScopedRecipient map(ResultSet rs, int rowNum) throws SQLException {
        String lastName = rs.getString("last_name");
        String firstName = rs.getString("first_name");
        String fullName = ((lastName == null ? "" : lastName.trim()) + " "
                + (firstName == null ? "" : firstName.trim())).trim();
        return new ScopedRecipient(rs.getString("student_email"), fullName, rs.getString("student_number"));
    }
}
