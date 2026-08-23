package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.academic.service.AdminCatalogMutationService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:admin_catalog_mutation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AdminCatalogMutationPersistenceTest {

    @Autowired
    private AdminCatalogMutationService catalog;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        createTables();
        clearTables();
        insertFixture();
    }

    @Test
    void sectionCreatePersistsSchedules() throws Exception {
        mvc.perform(post("/api/v1/sections")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "section-created",
                                  "sectionNumber": "03",
                                  "courseId": "course-old",
                                  "semesterId": "semester-old",
                                  "classroomId": "room-1",
                                  "capacity": 25,
                                  "schedules": [
                                    {"dayOfWeek": 2, "startTime": "08:00", "endTime": "10:00", "classroomId": "room-1"},
                                    {"dayOfWeek": 4, "startTime": "13:00", "endTime": "15:00", "classroomId": "room-2"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(scheduleRows("section-created"))
                .containsExactly(
                        Map.of("classroomId", "room-1", "dayOfWeek", 2, "startTime", "08:00", "endTime", "10:00"),
                        Map.of("classroomId", "room-2", "dayOfWeek", 4, "startTime", "13:00", "endTime", "15:00"));
    }

    @Test
    void sectionUpdatePersistsMutableCourseSemesterAndReplacementSchedules() throws Exception {
        mvc.perform(put("/api/v1/sections/section-existing")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "course-new",
                                  "semesterId": "semester-new",
                                  "classroomId": "room-2",
                                  "schedules": [
                                    {"dayOfWeek": 6, "startTime": "09:00", "endTime": "11:00", "classroomId": "room-2"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        Map<String, Object> section = jdbc.queryForMap(
                "SELECT \"courseId\", \"semesterId\", \"classroomId\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                "section-existing");
        assertThat(section)
                .containsEntry("courseId", "course-new")
                .containsEntry("semesterId", "semester-new")
                .containsEntry("classroomId", "room-2");
        assertThat(scheduleRows("section-existing"))
                .containsExactly(Map.of(
                        "classroomId", "room-2",
                        "dayOfWeek", 6,
                        "startTime", "09:00",
                        "endTime", "11:00"));
    }

    @Test
    void sectionUpdateRollsBackFieldsAndSchedulesWhenReplacementFails() {
        assertThatThrownBy(() -> catalog.update(
                "\"academic\".\"Section\"",
                "section-existing",
                Map.of(
                        "sectionNumber", "changed",
                        "schedules", List.of(
                                Map.of("dayOfWeek", 3, "startTime", "08:00", "endTime", "10:00", "classroomId", "room-2"),
                                Map.of("dayOfWeek", 5, "startTime", "08:00", "endTime", "10:00", "classroomId", "missing-room")))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT \"sectionNumber\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                String.class,
                "section-existing"))
                .isEqualTo("01");
        assertThat(scheduleRows("section-existing"))
                .containsExactly(Map.of(
                        "classroomId", "room-1",
                        "dayOfWeek", 1,
                        "startTime", "07:00",
                        "endTime", "09:00"));
    }

    @Test
    void semesterUpdatePersistsCoreAcademicFields() throws Exception {
        mvc.perform(put("/api/v1/semesters/semester-old")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "SECOND",
                                  "academicYearId": "year-new",
                                  "startDate": "2027-01-15T00:00:00Z",
                                  "endDate": "2027-06-30T23:59:59Z"
                                }
                                """))
                .andExpect(status().isOk());

        Map<String, Object> semester = jdbc.queryForMap(
                "SELECT \"type\", \"academicYearId\", \"startDate\", \"endDate\""
                        + " FROM \"academic\".\"Semester\" WHERE \"id\" = ?",
                "semester-old");
        assertThat(semester)
                .containsEntry("type", "SECOND")
                .containsEntry("academicYearId", "year-new");
        assertThat(((OffsetDateTime) semester.get("startDate")).toInstant())
                .isEqualTo(Instant.parse("2027-01-15T00:00:00Z"));
        assertThat(((OffsetDateTime) semester.get("endDate")).toInstant())
                .isEqualTo(Instant.parse("2027-06-30T23:59:59Z"));
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."AcademicYear" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "year" INTEGER NOT NULL,
                    "startDate" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "endDate" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "isCurrent" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Semester" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(180) NOT NULL,
                    "nameEn" VARCHAR(180),
                    "nameVi" VARCHAR(180),
                    "type" VARCHAR(40) NOT NULL,
                    "academicYearId" VARCHAR(120) NOT NULL REFERENCES "academic"."AcademicYear" ("id"),
                    "startDate" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "endDate" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "registrationStart" TIMESTAMP WITH TIME ZONE,
                    "registrationEnd" TIMESTAMP WITH TIME ZONE,
                    "addDropStart" TIMESTAMP WITH TIME ZONE,
                    "addDropEnd" TIMESTAMP WITH TIME ZONE,
                    "status" VARCHAR(40) NOT NULL,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(60) NOT NULL,
                    "name" VARCHAR(240) NOT NULL,
                    "credits" INTEGER NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Classroom" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "building" VARCHAR(160) NOT NULL,
                    "roomNumber" VARCHAR(80) NOT NULL,
                    "capacity" INTEGER NOT NULL,
                    "type" VARCHAR(80) NOT NULL,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionNumber" VARCHAR(80) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL REFERENCES "academic"."Course" ("id"),
                    "semesterId" VARCHAR(120) NOT NULL REFERENCES "academic"."Semester" ("id"),
                    "lecturerId" VARCHAR(120),
                    "classroomId" VARCHAR(120) REFERENCES "academic"."Classroom" ("id"),
                    "capacity" INTEGER NOT NULL,
                    "enrolledCount" INTEGER NOT NULL DEFAULT 0,
                    "status" VARCHAR(40) NOT NULL,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."SectionSchedule" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120) NOT NULL REFERENCES "academic"."Section" ("id") ON DELETE CASCADE,
                    "classroomId" VARCHAR(120) NOT NULL REFERENCES "academic"."Classroom" ("id"),
                    "dayOfWeek" INTEGER NOT NULL,
                    "startTime" VARCHAR(10) NOT NULL,
                    "endTime" VARCHAR(10) NOT NULL
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"SectionSchedule\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Classroom\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Semester\"");
        jdbc.update("DELETE FROM \"academic\".\"AcademicYear\"");
    }

    private void insertFixture() {
        jdbc.update("INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "year-old", 2026, OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2026-12-31T23:59:59Z"), true);
        jdbc.update("INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "year-new", 2027, OffsetDateTime.parse("2027-01-01T00:00:00Z"),
                OffsetDateTime.parse("2027-12-31T23:59:59Z"), false);
        insertSemester("semester-old", "year-old", "FIRST");
        insertSemester("semester-new", "year-new", "SECOND");
        insertCourse("course-old", "CS101");
        insertCourse("course-new", "CS202");
        insertClassroom("room-1", "A101");
        insertClassroom("room-2", "B202");
        jdbc.update("INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, 0, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "section-existing", "01", "course-old", "semester-old", "room-1", 30);
        jdbc.update("INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "schedule-old", "section-existing", "room-1", 1, "07:00", "09:00");
    }

    private void insertSemester(String id, String academicYearId, String type) {
        jdbc.update("INSERT INTO \"academic\".\"Semester\""
                        + " (\"id\", \"name\", \"type\", \"academicYearId\", \"startDate\", \"endDate\","
                        + " \"status\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, 'OPEN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, id, type, academicYearId, OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                OffsetDateTime.parse("2026-12-31T23:59:59Z"));
    }

    private void insertCourse(String id, String code) {
        jdbc.update("INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"credits\", \"departmentId\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, 3, 'department-demo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, code, code);
    }

    private void insertClassroom(String id, String roomNumber) {
        jdbc.update("INSERT INTO \"academic\".\"Classroom\""
                        + " (\"id\", \"building\", \"roomNumber\", \"capacity\", \"type\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, 'A', ?, 40, 'LECTURE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, roomNumber);
    }

    private List<Map<String, Object>> scheduleRows(String sectionId) {
        return jdbc.queryForList(
                "SELECT \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\""
                        + " FROM \"academic\".\"SectionSchedule\" WHERE \"sectionId\" = ?"
                        + " ORDER BY \"dayOfWeek\", \"startTime\"",
                sectionId);
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.subject("admin-user").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
