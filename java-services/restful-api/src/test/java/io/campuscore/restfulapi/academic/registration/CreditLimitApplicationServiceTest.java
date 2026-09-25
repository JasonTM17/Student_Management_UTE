package io.campuscore.restfulapi.academic.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationDtos;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

/**
 * Round-10 finding C2: the credit-limit / eligibility domain is a
 * README-claimed feature whose Java behavior had zero coverage — only a
 * manual e2e run protected it. These tests pin the service contract:
 * window-gated submit, single active application per round, the admin
 * review state transition (reviewedBy + conditional PENDING update), and
 * the 28 → 30 effective-limit raise that eligibility checks consume.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
class CreditLimitApplicationServiceTest {

    private static final String STUDENT = "cl-student-user";

    @Autowired
    private CreditLimitApplicationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void submitReviewAndEffectiveLimitFormTheDocumentedContract() {
        seedStudent();
        seedOpenRound("cl-round", 28);

        // A short reason is rejected; the documented minimum is 20 characters.
        assertThatThrownBy(() -> service.submit(STUDENT, "cl-round", "too short"))
                .isInstanceOf(DomainException.class);

        CreditLimitApplicationDtos.Response submitted =
                service.submit(STUDENT, "cl-round", "Tôi cần đăng ký vượt hạn mức để học môn thay thế học phần bị hoãn.");
        assertThat(submitted.status()).isEqualTo("PENDING");
        assertThat(submitted.requestedLimit()).isEqualTo(30);

        // One active application per student per round.
        assertThatThrownBy(() -> service.submit(STUDENT, "cl-round", "Ứng tuyển thứ hai trong cùng đợt đăng ký này."))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("An active credit-limit application already exists");

        // Admin approves: pure state transition with reviewer attribution.
        CreditLimitApplicationDtos.Response approved =
                service.review(submitted.id(), "approved", "admin-user-1", "Đủ điều kiện môn thay thế.");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.reviewedBy()).isEqualTo("admin-user-1");

        // A second review hits the conditional PENDING guard, not silent success.
        assertThatThrownBy(() -> service.review(submitted.id(), "REJECTED", "admin-user-2", null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Only a pending credit-limit application");

        // The eligibility raise: 28 standard → 30 approved, capped at 30.
        Map<String, Object> round = openRoundRow("cl-round");
        assertThat(service.effectiveLimit(STUDENT, round)).isEqualTo(30);
        assertThat(service.standardLimit(round)).isEqualTo(28);
    }

    @Test
    void reviewRejectsUnknownDecisionsAndClosedRoundsStillAllowDisposition() {
        seedStudent();
        seedOpenRound("cl-round-2", 28);
        CreditLimitApplicationDtos.Response submitted =
                service.submit(STUDENT, "cl-round-2", "Đăng ký vượt hạn mức vì môn bắt buộc trùng lịch học phần đã đăng.");

        assertThatThrownBy(() -> service.review(submitted.id(), "MAYBE", "admin-user-1", null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Decision must be APPROVED or REJECTED");

        // Close the round; review remains possible by design (a pending
        // application must never be stranded by a closed window).
        jdbc.update("UPDATE academic.\"RegistrationRound\" SET \"status\" = 'CLOSED' WHERE \"id\" = 'cl-round-2'");
        CreditLimitApplicationDtos.Response approved =
                service.review(submitted.id(), "APPROVED", "admin-user-1", null);
        assertThat(approved.status()).isEqualTo("APPROVED");
    }

    private void seedStudent() {
        // The service resolves the student in academic."Student" and joins the
        // campuscore_auth."User" account for display fields; the H2 harness
        // ships no academic catalog (V16 documents the omission), so this test
        // provides exactly the columns the SQL reads.
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Student\""
                + " (\"id\" VARCHAR(120) PRIMARY KEY, \"userId\" VARCHAR(120) NOT NULL,"
                + " \"studentId\" VARCHAR(120))");
        jdbc.update("DELETE FROM academic.\"Student\" WHERE \"id\" = ?", STUDENT);
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" = ?", STUDENT);
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + "  \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, 'x', 'Credit', 'Limit', 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                STUDENT, STUDENT + "@example.test");
        jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, 'CL001')",
                STUDENT, STUDENT);
    }

    private void seedOpenRound(String roundId, int creditLimit) {
        // Minimal catalog stand-in: the response mapping joins Semester for
        // its display name, and the H2 harness ships no academic catalog
        // (V16 documents the omission). Provide only the columns the SQL reads.
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Semester\""
                + " (\"id\" VARCHAR(120) PRIMARY KEY, \"name\" VARCHAR(180))");
        jdbc.update("DELETE FROM academic.\"Semester\" WHERE \"id\" = 'cl-semester'");
        jdbc.update("INSERT INTO academic.\"Semester\" (\"id\", \"name\") VALUES ('cl-semester', 'Credit limit semester')");
        jdbc.update("DELETE FROM academic.\"CreditLimitApplication\" WHERE \"roundId\" = ?", roundId);
        jdbc.update("DELETE FROM academic.\"RegistrationRound\" WHERE \"id\" = ?", roundId);
        jdbc.update(
                "INSERT INTO academic.\"RegistrationRound\""
                        + " (\"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\", \"creditLimit\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                roundId,
                "cl-semester",
                "Credit limit round",
                "REGISTRATION",
                "OPEN",
                Timestamp.from(Instant.now().minusSeconds(3600)),
                Timestamp.from(Instant.now().plusSeconds(3600)),
                creditLimit);
        // creditLimit column carries the round's configured cap.
        jdbc.update("UPDATE academic.\"RegistrationRound\" SET \"creditLimit\" = ? WHERE \"id\" = ?", creditLimit, roundId);
    }

    private Map<String, Object> openRoundRow(String roundId) {
        Map<String, Object> row =
                jdbc.queryForMap("SELECT \"id\", \"creditLimit\" AS credit_limit FROM academic.\"RegistrationRound\" WHERE \"id\" = ?", roundId);
        return new HashMap<>(row);
    }
}
