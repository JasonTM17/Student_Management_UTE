package io.campuscore.restfulapi.people;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "migration.people-read.enabled=true",
        "spring.flyway.enabled=false"
})
class PeopleReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparePeopleFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"people\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "people"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL,
                    "email" VARCHAR(320) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL,
                    "studentId" VARCHAR(120) UNIQUE NOT NULL,
                    "curriculumId" VARCHAR(120) NOT NULL,
                    "curriculumCode" VARCHAR(80),
                    "curriculumName" VARCHAR(200),
                    "departmentId" VARCHAR(120),
                    "departmentCode" VARCHAR(80),
                    "departmentName" VARCHAR(200),
                    "year" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "admissionDate" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "people"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL,
                    "email" VARCHAR(320) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "departmentCode" VARCHAR(80),
                    "departmentName" VARCHAR(200),
                    "employeeId" VARCHAR(120) UNIQUE NOT NULL,
                    "title" VARCHAR(120),
                    "specialization" VARCHAR(200),
                    "office" VARCHAR(120),
                    "phone" VARCHAR(80),
                    "isActive" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"people\".\"Student\"");
        jdbc.update("DELETE FROM \"people\".\"Lecturer\"");
    }

    @Test
    void studentsPreserveLegacyListEnvelopeOrderingStatusFilterAndHydration() throws Exception {
        insertStudent("student-old", "S001", "ACTIVE", 2, BASE_TIME.minusSeconds(60), "department-se");
        insertStudent("student-new", "S002", "INACTIVE", 3, BASE_TIME, null);
        insertStudent("student-other", "S003", "ACTIVE", 4, BASE_TIME.plusSeconds(60), "department-cs");

        mvc.perform(get("/api/v1/students")
                        .queryParam("page", "1")
                        .queryParam("limit", "2")
                        .with(jwt().jwt(token -> token.subject("lecturer-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("student-other"))
                .andExpect(jsonPath("$.data[0].user.id").value("user-student-other"))
                .andExpect(jsonPath("$.data[0].curriculum.department.code").value("CS"))
                .andExpect(jsonPath("$.data[1].id").value("student-new"))
                .andExpect(jsonPath("$.data[1].curriculum.department").doesNotExist())
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/students")
                        .queryParam("status", "ACTIVE")
                        .with(jwt().jwt(token -> token.subject("admin-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("student-other"))
                .andExpect(jsonPath("$.data[1].id").value("student-old"))
                .andExpect(jsonPath("$.meta.total").value(2));

        mvc.perform(get("/api/v1/students/student-old")
                        .with(jwt().jwt(token -> token.subject("student-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("student-old"))
                .andExpect(jsonPath("$.studentId").value("S001"))
                .andExpect(jsonPath("$.curriculum.id").value("curriculum-student-old"))
                .andExpect(jsonPath("$.curriculum.department.name").value("Department department-se"))
                .andExpect(jsonPath("$.admissionDate").value("2025-08-20T00:00:00.000Z"));
    }

    @Test
    void lecturersPreserveLegacyListEnvelopeOrderingAndHydration() throws Exception {
        insertLecturer("lecturer-old", "E001", BASE_TIME.minusSeconds(60));
        insertLecturer("lecturer-new", "E002", BASE_TIME);

        mvc.perform(get("/api/v1/lecturers")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .with(jwt().jwt(token -> token.subject("admin-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("lecturer-new"))
                .andExpect(jsonPath("$.data[0].employeeId").value("E002"))
                .andExpect(jsonPath("$.data[0].user.email").value("lecturer-new@campuscore.edu"))
                .andExpect(jsonPath("$.data[0].department.name").value("Software Engineering"))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/lecturers/lecturer-old")
                        .with(jwt().jwt(token -> token.subject("admin-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("lecturer-old"))
                .andExpect(jsonPath("$.title").value("Dr."))
                .andExpect(jsonPath("$.specialization").value("Distributed Systems"))
                .andExpect(jsonPath("$.office").value("A-101"))
                .andExpect(jsonPath("$.phone").value("+84123456789"));
    }

    @Test
    void readBoundaryFailsClosedForAnonymousInvalidPagesUnexpectedQueryAndMissingRows() throws Exception {
        mvc.perform(get("/api/v1/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/students")
                        .queryParam("limit", "101")
                        .with(jwt().jwt(token -> token.subject("admin-user"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/lecturers")
                        .queryParam("status", "ACTIVE")
                        .with(jwt().jwt(token -> token.subject("admin-user"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/students")
                        .queryParam("page", "1", "2")
                        .with(jwt().jwt(token -> token.subject("admin-user"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/lecturers/missing")
                        .with(jwt().jwt(token -> token.subject("admin-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));
    }

    private void insertStudent(
            String id,
            String studentId,
            String status,
            int year,
            Instant createdAt,
            String departmentId) {
        jdbc.update(
                "INSERT INTO \"people\".\"Student\""
                        + " (\"id\", \"userId\", \"email\", \"firstName\", \"lastName\", \"studentId\","
                        + " \"curriculumId\", \"curriculumCode\", \"curriculumName\", \"departmentId\","
                        + " \"departmentCode\", \"departmentName\", \"year\", \"status\", \"admissionDate\","
                        + " \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "user-" + id,
                id + "@campuscore.edu",
                "Student",
                id.substring(id.indexOf('-') + 1),
                studentId,
                "curriculum-" + id,
                "SE",
                "Software Engineering",
                departmentId,
                departmentId == null ? null : departmentCode(departmentId),
                departmentId == null ? null : "Department " + departmentId,
                year,
                status,
                localDateTime(BASE_TIME.minusSeconds(31_536_000)),
                localDateTime(createdAt),
                localDateTime(createdAt));
    }

    private void insertLecturer(String id, String employeeId, Instant createdAt) {
        jdbc.update(
                "INSERT INTO \"people\".\"Lecturer\""
                        + " (\"id\", \"userId\", \"email\", \"firstName\", \"lastName\", \"departmentId\","
                        + " \"departmentCode\", \"departmentName\", \"employeeId\", \"title\","
                        + " \"specialization\", \"office\", \"phone\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "user-" + id,
                id + "@campuscore.edu",
                "Lecturer",
                id.substring(id.indexOf('-') + 1),
                "department-se",
                "SE",
                "Software Engineering",
                employeeId,
                "Dr.",
                "Distributed Systems",
                "A-101",
                "+84123456789",
                true,
                localDateTime(createdAt),
                localDateTime(createdAt));
    }

    private static String departmentCode(String departmentId) {
        return departmentId.endsWith("cs") ? "CS" : "SE";
    }

    private static LocalDateTime localDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
