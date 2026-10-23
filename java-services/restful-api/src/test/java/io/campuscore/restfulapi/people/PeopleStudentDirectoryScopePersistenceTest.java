package io.campuscore.restfulapi.people;

import static io.campuscore.restfulapi.testsupport.CurrentAccountJwt.currentJwt;
import static io.campuscore.restfulapi.testsupport.CurrentAccountJwt.fixtureJwt;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:people_scope;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class PeopleStudentDirectoryScopePersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS thesis");
        AccountAuthorityFixture.ensureSchema(jdbc);
        createTables();
        jdbc.update("DELETE FROM thesis.thesis_council_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Curriculum\"");
        jdbc.update("DELETE FROM \"academic\".\"Department\"");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\"");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\"");
        jdbc.update("DELETE FROM campuscore_auth.\"UserRole\"");
        jdbc.update("DELETE FROM campuscore_auth.\"User\"");

        department("dept-eng", "ENG", "Engineering", "faculty-1");
        department("dept-sci", "SCI", "Science", "faculty-1");
        department("dept-ext", "EXT", "External", "faculty-2");
        curriculum("cur-eng", "ENG-C", "dept-eng");
        curriculum("cur-sci", "SCI-C", "dept-sci");
        curriculum("cur-ext", "EXT-C", "dept-ext");

        student("stu-taught-a", "S001", "cur-eng", "ACTIVE", BASE_TIME.plusSeconds(50));
        student("stu-taught-b", "S002", "cur-sci", "ACTIVE", BASE_TIME.plusSeconds(40));
        student("stu-thesis", "S003", "cur-eng", "ACTIVE", BASE_TIME.plusSeconds(30));
        student("stu-ext", "S004", "cur-ext", "ACTIVE", BASE_TIME.plusSeconds(20));
        student("stu-alone", "S005", "cur-eng", "ACTIVE", BASE_TIME.plusSeconds(10));
        student("stu-inactive", "S006", "cur-eng", "INACTIVE", BASE_TIME.plusSeconds(60));

        lecturer("lecturer-a", "dept-eng");
        lecturer("lecturer-b", "dept-sci");
        lecturer("lecturer-head", "dept-eng");
        lecturer("lecturer-dean", "dept-eng");
        lecturer("lecturer-sup", "dept-ext");
        lecturer("lecturer-rev", "dept-ext");
        lecturer("lecturer-coun", "dept-ext");
        lecturer("lecturer-ext", "dept-ext");

        jdbc.update("INSERT INTO \"academic\".\"Course\" (\"id\", \"code\", \"departmentId\") VALUES ('course-eng', 'C-ENG', 'dept-eng')");
        jdbc.update("INSERT INTO \"academic\".\"Course\" (\"id\", \"code\", \"departmentId\") VALUES ('course-sci', 'C-SCI', 'dept-sci')");
        jdbc.update("INSERT INTO \"academic\".\"Course\" (\"id\", \"code\", \"departmentId\") VALUES ('course-ext', 'C-EXT', 'dept-ext')");
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"courseId\", \"lecturerId\") VALUES ('sec-a', 'course-eng', 'lecturer-a')");
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"courseId\", \"lecturerId\") VALUES ('sec-b', 'course-sci', 'lecturer-b')");
        jdbc.update("INSERT INTO \"academic\".\"Section\" (\"id\", \"courseId\", \"lecturerId\") VALUES ('sec-ext', 'course-ext', 'lecturer-ext')");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\") VALUES ('enr-1', 'stu-taught-a', 'sec-a', 'ENROLLED')");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\") VALUES ('enr-2', 'stu-taught-b', 'sec-b', 'ENROLLED')");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\") VALUES ('enr-3', 'stu-ext', 'sec-ext', 'ENROLLED')");
        jdbc.update("INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"status\") VALUES ('enr-4', 'stu-inactive', 'sec-a', 'ENROLLED')");

        jdbc.update("INSERT INTO thesis.thesis_topic (id, department_id, gvpb_lecturer_id, status) VALUES ('topic-1', 'dept-eng', 'lecturer-rev', 'APPROVED')");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) VALUES ('tsup-1', 'topic-1', 'lecturer-sup', 1)");
        jdbc.update("INSERT INTO thesis.thesis_group (id, topic_id, leader_student_id, status) VALUES ('group-1', 'topic-1', 'stu-thesis', 'FORMED')");
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, student_id, member_order, is_leader, is_external) VALUES ('mem-1', 'group-1', 'stu-thesis', 1, TRUE, FALSE)");
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id) VALUES ('council-1', 'round-1')");
        jdbc.update("INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) VALUES ('cm-1', 'council-1', 'lecturer-coun', 'MEMBER')");
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id) VALUES ('ct-1', 'council-1', 'topic-1')");
    }

    @Test
    void lecturerDirectoryListsOnlyStudentsTheyHaveARelationshipWith() throws Exception {
        assertStudentIds(lecturerJwt("user-lecturer-a", "lecturer-a"), 2, "stu-inactive", "stu-taught-a");
        assertStudentIds(lecturerJwt("user-lecturer-b", "lecturer-b"), 1, "stu-taught-b");
        assertStudentIds(lecturerJwt("user-lecturer-head", "lecturer-head", "TRUONG_BO_MON"), 2, "stu-inactive", "stu-taught-a");
        assertStudentIds(lecturerJwt("user-lecturer-dean", "lecturer-dean", "TRUONG_KHOA"), 3,
                "stu-inactive", "stu-taught-a", "stu-taught-b");
        assertStudentIds(lecturerJwt("user-lecturer-sup", "lecturer-sup"), 1, "stu-thesis");
        assertStudentIds(lecturerJwt("user-lecturer-rev", "lecturer-rev"), 1, "stu-thesis");
        assertStudentIds(lecturerJwt("user-lecturer-coun", "lecturer-coun"), 1, "stu-thesis");
        assertStudentIds(lecturerJwt("user-lecturer-ext", "lecturer-ext"), 1, "stu-ext");
        assertStudentIds(fixtureJwt(jdbc, "scope-admin", "ADMIN"), 6,
                "stu-inactive", "stu-taught-a", "stu-taught-b", "stu-thesis", "stu-ext", "stu-alone");
    }

    @Test
    void scopedDirectoryAppliesStatusFilterAndPaginationToTheVisibleSet() throws Exception {
        mvc.perform(get("/api/v1/students")
                        .queryParam("status", "ACTIVE")
                        .with(lecturerJwt("user-lecturer-a", "lecturer-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("stu-taught-a"))
                .andExpect(jsonPath("$.meta.total").value(1));

        mvc.perform(get("/api/v1/students")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .with(lecturerJwt("user-lecturer-dean", "lecturer-dean", "TRUONG_KHOA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        mvc.perform(get("/api/v1/students")
                        .queryParam("page", "3")
                        .queryParam("limit", "1")
                        .with(lecturerJwt("user-lecturer-dean", "lecturer-dean", "TRUONG_KHOA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("stu-taught-b"))
                .andExpect(jsonPath("$.meta.total").value(3));

        mvc.perform(get("/api/v1/students")
                        .with(fixtureJwt(jdbc, "scope-admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(6));
    }

    @Test
    void lecturerDetailReturnsNotFoundForStudentsOutsideTheirScope() throws Exception {
        RequestPostProcessor lecturerA = lecturerJwt("user-lecturer-a", "lecturer-a");

        mvc.perform(get("/api/v1/students/stu-taught-a").with(lecturerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("stu-taught-a"))
                .andExpect(jsonPath("$.user.email").value("stu-taught-a@campuscore.edu"));

        MvcResult outOfScope = mvc.perform(get("/api/v1/students/stu-ext").with(lecturerA))
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult missing = mvc.perform(get("/api/v1/students/stu-missing").with(lecturerA))
                .andExpect(status().isNotFound())
                .andReturn();

        // An out-of-scope id must be indistinguishable from a missing id:
        // same status, same code, same message (no existence oracle, never 403).
        assertThat(JsonPath.<String>read(outOfScope.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("NOT_FOUND");
        assertThat(JsonPath.<String>read(outOfScope.getResponse().getContentAsString(), "$.message"))
                .isEqualTo(JsonPath.<String>read(missing.getResponse().getContentAsString(), "$.message"))
                .isEqualTo("Student not found");

        mvc.perform(get("/api/v1/students/stu-thesis").with(lecturerJwt("user-lecturer-sup", "lecturer-sup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("stu-thesis"));

        mvc.perform(get("/api/v1/students/stu-taught-a").with(lecturerJwt("user-lecturer-sup", "lecturer-sup")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/students/stu-alone").with(fixtureJwt(jdbc, "scope-admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("stu-alone"));
    }

    @Test
    void studentsKeepSelfOnlyAccessAndCannotReachStaffDirectories() throws Exception {
        RequestPostProcessor student = studentJwt("user-stu-taught-a", "stu-taught-a");

        mvc.perform(get("/api/v1/students").with(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/students/stu-taught-a").with(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("stu-taught-a"));

        mvc.perform(get("/api/v1/students/stu-alone").with(student))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/lecturers").with(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/lecturers/lecturer-a").with(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private void assertStudentIds(RequestPostProcessor jwt, int total, String... ids) throws Exception {
        mvc.perform(get("/api/v1/students").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(ids.length))
                .andExpect(jsonPath("$.data[*].id", containsInAnyOrder(ids)))
                .andExpect(jsonPath("$.meta.total").value(total));
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(40) NOT NULL,
                    "name" VARCHAR(200) NOT NULL,
                    "facultyId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Curriculum" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(40) NOT NULL,
                    "name" VARCHAR(200) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL,
                    "studentId" VARCHAR(120) UNIQUE NOT NULL,
                    "curriculumId" VARCHAR(120) NOT NULL,
                    "year" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "admissionDate" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120) UNIQUE NOT NULL,
                    "isActive" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(40) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "courseId" VARCHAR(120) NOT NULL,
                    "lecturerId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS thesis.thesis_topic (
                    id VARCHAR(64) PRIMARY KEY,
                    department_id VARCHAR(120),
                    gvpb_lecturer_id VARCHAR(120),
                    status VARCHAR(40)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS thesis.thesis_topic_supervisor (
                    id VARCHAR(64) PRIMARY KEY,
                    topic_id VARCHAR(64) NOT NULL,
                    lecturer_id VARCHAR(120) NOT NULL,
                    supervisor_order INTEGER NOT NULL DEFAULT 1
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS thesis.thesis_group (
                    id VARCHAR(64) PRIMARY KEY,
                    topic_id VARCHAR(64),
                    leader_student_id VARCHAR(120),
                    status VARCHAR(40) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS thesis.thesis_group_member (
                    id VARCHAR(64) PRIMARY KEY,
                    group_id VARCHAR(64) NOT NULL,
                    student_id VARCHAR(120),
                    member_order INTEGER NOT NULL DEFAULT 0,
                    is_leader BOOLEAN NOT NULL DEFAULT FALSE,
                    is_external BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS thesis.thesis_council (
                    id VARCHAR(64) PRIMARY KEY,
                    round_id VARCHAR(64)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS thesis.thesis_council_member (
                    id VARCHAR(64) PRIMARY KEY,
                    council_id VARCHAR(64) NOT NULL,
                    lecturer_id VARCHAR(120) NOT NULL,
                    member_role VARCHAR(40)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS thesis.thesis_council_topic (
                    id VARCHAR(64) PRIMARY KEY,
                    council_id VARCHAR(64) NOT NULL,
                    topic_id VARCHAR(64) NOT NULL
                )
                """);
    }

    private void department(String id, String code, String name, String facultyId) {
        jdbc.update("INSERT INTO \"academic\".\"Department\" (\"id\", \"code\", \"name\", \"facultyId\") VALUES (?, ?, ?, ?)",
                id, code, name, facultyId);
    }

    private void curriculum(String id, String code, String departmentId) {
        jdbc.update("INSERT INTO \"academic\".\"Curriculum\" (\"id\", \"code\", \"name\", \"departmentId\") VALUES (?, ?, ?, ?)",
                id, code, "Curriculum " + code, departmentId);
    }

    private void student(String id, String studentNumber, String curriculumId, String status, Instant createdAt) {
        String userId = "user-" + id;
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                userId, id + "@campuscore.edu", "Student", id.substring(id.indexOf('-') + 1));
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, studentNumber, curriculumId, 2, status,
                localDateTime(BASE_TIME.minusSeconds(31_536_000)),
                localDateTime(createdAt), localDateTime(createdAt));
    }

    private void lecturer(String id, String departmentId) {
        String userId = "user-" + id;
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                userId, id + "@campuscore.edu", "Lecturer", id.substring(id.indexOf('-') + 1));
        jdbc.update(
                "INSERT INTO \"academic\".\"Lecturer\""
                        + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, userId, departmentId, "E-" + id, true, localDateTime(BASE_TIME), localDateTime(BASE_TIME));
    }

    private RequestPostProcessor lecturerJwt(String userId, String profileId, String... extraRoles) {
        AccountAuthorityFixture.ensureUser(jdbc, userId);
        AccountAuthorityFixture.lecturer(jdbc, userId, profileId);
        if (extraRoles.length > 0) {
            String[] roles = new String[extraRoles.length + 1];
            roles[0] = "LECTURER";
            System.arraycopy(extraRoles, 0, roles, 1, extraRoles.length);
            AccountAuthorityFixture.replaceRoles(jdbc, userId, roles);
        }
        return currentJwt(jdbc, userId);
    }

    private RequestPostProcessor studentJwt(String userId, String profileId) {
        AccountAuthorityFixture.ensureUser(jdbc, userId);
        AccountAuthorityFixture.student(jdbc, userId, profileId, 2);
        return currentJwt(jdbc, userId);
    }

    private static LocalDateTime localDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
