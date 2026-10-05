package io.campuscore.restfulapi.mail.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Resolves mail recipients for lecturer-scoped sends. A lecturer may only mail
 * students enrolled (current or completed) in sections they teach — the
 * recipient address and identity come from the database, never from the
 * request body.
 */
@Repository
@Profile("persistence")
public class MailRecipientScopeRepository {

    private static final String STUDENT = "\"academic\".\"Student\"";
    private static final String USER = "\"campuscore_auth\".\"User\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";

    public record ScopedRecipient(String email, String fullName, String studentNumber) {}

    private final NamedParameterJdbcTemplate jdbc;

    public MailRecipientScopeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
