package io.campuscore.restfulapi.mail;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.mail.repository.MailRecipientScopeRepository;
import io.campuscore.restfulapi.mail.repository.MailRecipientScopeRepository.ScopedRecipient;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Persistence pin for the lecturer mail boundary (audit M1): the scoping SQL
 * is exercised against real rows so a weakened join, swapped operator, or
 * wrong status set fails this test instead of failing open in production.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:mail_scope;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class MailRecipientScopePersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-21T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MailRecipientScopeRepository scope;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(255) NOT NULL,
                    "firstName" VARCHAR(120),
                    "lastName" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "studentId" VARCHAR(40) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "lecturerId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "enrolledAt" TIMESTAMP NOT NULL
                )
                """);

        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");

        // Lecturer A owns section-1 (students 1, 2). Lecturer B owns section-2
        // (student 3). Student 4 is dropped from section-1.
        insertUser("u1", "linh@campuscore.edu", "Linh", "Nguyen");
        insertUser("u2", "minh@campuscore.edu", "Minh", "Tran");
        insertUser("u3", "an@campuscore.edu", "An", "Le");
        insertUser("u4", "nam@campuscore.edu", "Nam", "Pham");
        insertStudent("s1", "u1", "SV001");
        insertStudent("s2", "u2", "SV002");
        insertStudent("s3", "u3", "SV003");
        insertStudent("s4", "u4", "SV004");
        insertSection("section-1", "lecturer-a");
        insertSection("section-2", "lecturer-b");
        insertEnrollment("e1", "s1", "section-1", "ENROLLED");
        insertEnrollment("e2", "s2", "section-1", "PENDING");
        insertEnrollment("e3", "s3", "section-2", "ENROLLED");
        insertEnrollment("e4", "s4", "section-1", "DROPPED");
    }

    @Test
    void resolvesEnrolledStudentByStudentNumber() {
        ScopedRecipient recipient = scope.findScopedRecipient("lecturer-a", "SV001");
        assertThat(recipient).isNotNull();
        assertThat(recipient.email()).isEqualTo("linh@campuscore.edu");
        assertThat(recipient.fullName()).isEqualTo("Nguyen Linh");
        assertThat(recipient.studentNumber()).isEqualTo("SV001");
    }

    @Test
    void resolvesEnrolledStudentByAccountEmail() {
        ScopedRecipient recipient = scope.findScopedRecipient("lecturer-a", "minh@campuscore.edu");
        assertThat(recipient).isNotNull();
        assertThat(recipient.studentNumber()).isEqualTo("SV002");
    }

    @Test
    void studentOfAnotherLecturerIsOutOfScope() {
        assertThat(scope.findScopedRecipient("lecturer-a", "SV003")).isNull();
        assertThat(scope.findScopedRecipient("lecturer-a", "an@campuscore.edu")).isNull();
    }

    @Test
    void droppedEnrollmentDoesNotAuthorize() {
        assertThat(scope.findScopedRecipient("lecturer-a", "SV004")).isNull();
        assertThat(scope.findScopedRecipient("lecturer-a", "nam@campuscore.edu")).isNull();
    }

    @Test
    void unknownKeyAndBlankKeyResolveToNull() {
        assertThat(scope.findScopedRecipient("lecturer-a", "SV999")).isNull();
        assertThat(scope.findScopedRecipient("lecturer-a", "")).isNull();
        assertThat(scope.findScopedRecipient("lecturer-a", "nobody@else.example")).isNull();
    }

    @Test
    void lecturerWithNoSectionsResolvesNobody() {
        assertThat(scope.findScopedRecipient("lecturer-c", "SV001")).isNull();
    }

    @Test
    void completedEnrollmentStillAuthorizes() {
        insertEnrollment("e5", "s3", "section-1", "COMPLETED");
        ScopedRecipient recipient = scope.findScopedRecipient("lecturer-a", "SV003");
        assertThat(recipient).isNotNull();
        assertThat(recipient.email()).isEqualTo("an@campuscore.edu");
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                id, email, firstName, lastName);
    }

    private void insertStudent(String id, String userId, String studentNumber) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                id, userId, studentNumber);
    }

    private void insertSection(String id, String lecturerId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\" (\"id\", \"lecturerId\") VALUES (?, ?)",
                id, lecturerId);
    }

    private void insertEnrollment(String id, String studentId, String sectionId, String status) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\", \"enrolledAt\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                id, studentId, sectionId, status, Timestamp.from(BASE_TIME));
    }
}
