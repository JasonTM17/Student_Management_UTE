package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:lecturer_ceiling;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class LecturerProtectedAccountTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFixture() {
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureSchema(jdbc);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(80) NOT NULL,
                    "title" VARCHAR(120),
                    "specialization" VARCHAR(200),
                    "office" VARCHAR(120),
                    "phone" VARCHAR(40),
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        clearTables();
        for (String subject : List.of("admin-user", "sa-user", "student-user", "lecturer-user", "dean-user",
                "target-admin", "target-sa", "target-dean", "target-custom", "plain-user", "unused-user")) {
            io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureUser(jdbc, subject);
        }
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "admin-user", "ADMIN");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "sa-user", "SUPER_ADMIN");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "student-user", "STUDENT");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "lecturer-user", "LECTURER");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "dean-user", "LECTURER");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "dean-user", "TRUONG_KHOA");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "target-admin", "ADMIN");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "target-sa", "SUPER_ADMIN");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "target-dean", "TRUONG_KHOA");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "plain-user", "LECTURER");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "unused-user", "SUPER_ADMIN");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Role\" (\"id\", \"name\", \"isSystem\")"
                + " VALUES ('role-custom-fixture', 'PAST_EXAM_VIEWER', FALSE)");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\")"
                + " VALUES (?,?,?)", "ur-custom-fixture", "target-custom", "role-custom-fixture");
        jdbc.update("INSERT INTO \"academic\".\"Department\" (\"id\", \"name\") VALUES ('dept-1', 'Fixture Department')");
        insertLecturer("lec-dean", "target-dean", "EMP-DEAN");
        insertLecturer("lec-plain", "plain-user", "EMP-PLAIN");
        insertLecturer("lec-unused", "unused-user", "EMP-UNUSED");
    }

    @Test
    void plainAdminCannotCreateOrUpsertProfilesForProtectedAccounts() throws Exception {
        for (String target : List.of("target-admin", "target-sa", "target-dean", "target-custom")) {
            String employeeId = "EMP-" + target.toUpperCase();
            long before = lecturerCountForUser(target);
            mvc.perform(post("/api/v1/lecturers").with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createPayload(target, employeeId)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
            assertThat(lecturerCountForUser(target)).isEqualTo(before);
        }
    }

    @Test
    void plainAdminCannotUpdateOrDeleteAProtectedProfile() throws Exception {
        mvc.perform(put("/api/v1/lecturers/lec-dean").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Professor\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
        mvc.perform(delete("/api/v1/lecturers/lec-dean").with(adminJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
        assertThat(jdbc.queryForObject(
                "SELECT \"title\" FROM \"academic\".\"Lecturer\" WHERE \"id\"='lec-dean'", String.class))
                .isEqualTo("Lecturer");
        assertThat(lecturerRowCount("lec-dean")).isEqualTo(1);
    }

    @Test
    void nonAdminCallersHitTheClassGuard() throws Exception {
        String payload = createPayload("plain-user", "EMP-NEW-PLAIN");
        for (RequestPostProcessor caller : List.of(
                io.campuscore.restfulapi.testsupport.CurrentAccountJwt.currentJwt(jdbc, "student-user"),
                io.campuscore.restfulapi.testsupport.CurrentAccountJwt.currentJwt(jdbc, "lecturer-user"),
                io.campuscore.restfulapi.testsupport.CurrentAccountJwt.currentJwt(jdbc, "dean-user"))) {
            mvc.perform(post("/api/v1/lecturers").with(caller)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());
        }
        assertThat(lecturerCountForUser("plain-user")).isEqualTo(1);
    }

    @Test
    void superAdminMayManageProtectedProfiles() throws Exception {
        mvc.perform(post("/api/v1/lecturers").with(saJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("target-admin", "EMP-TADMIN")))
                .andExpect(status().isOk());
        assertThat(lecturerCountForUser("target-admin")).isEqualTo(1);

        mvc.perform(put("/api/v1/lecturers/lec-dean").with(saJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Associate Professor\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT \"title\" FROM \"academic\".\"Lecturer\" WHERE \"id\"='lec-dean'", String.class))
                .isEqualTo("Associate Professor");

        mvc.perform(delete("/api/v1/lecturers/lec-unused").with(saJwt()))
                .andExpect(status().isOk());
        assertThat(lecturerRowCount("lec-unused")).isZero();
    }

    @Test
    void plainAdminCrudOnOrdinaryLecturerTargetsStillWorks() throws Exception {
        mvc.perform(post("/api/v1/lecturers").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("plain-user", "EMP-PLAIN")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/lecturers/lec-plain").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"office\":\"B2-101\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/lecturers/lec-plain").with(adminJwt()))
                .andExpect(status().isOk());
        assertThat(lecturerRowCount("lec-plain")).isZero();
    }

    @Test
    void missingProfileMapsTo404ForBothCeilings() throws Exception {
        mvc.perform(put("/api/v1/lecturers/lec-missing").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Professor\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURER_NOT_FOUND"));
        mvc.perform(delete("/api/v1/lecturers/lec-missing").with(saJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LECTURER_NOT_FOUND"));
    }

    private static String createPayload(String userId, String employeeId) {
        return "{"
                + "\"userId\":\"" + userId + "\","
                + "\"departmentId\":\"dept-1\","
                + "\"employeeId\":\"" + employeeId + "\","
                + "\"title\":\"Lecturer\","
                + "\"specialization\":\"Fixture\","
                + "\"office\":\"A1-001\","
                + "\"phone\":\"0900000000\""
                + "}";
    }

    private Integer lecturerCountForUser(String userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Lecturer\" WHERE \"userId\"=?",
                Integer.class, userId);
    }

    private Integer lecturerRowCount(String id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Lecturer\" WHERE \"id\"=?",
                Integer.class, id);
    }

    private void insertLecturer(String id, String userId, String employeeId) {
        jdbc.update("INSERT INTO \"academic\".\"Lecturer\""
                        + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"title\", \"isActive\")"
                        + " VALUES (?,?,?,?,'Lecturer',TRUE)",
                id, userId, "dept-1", employeeId);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Department\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Role\"");
    }

    private RequestPostProcessor adminJwt() {
        return io.campuscore.restfulapi.testsupport.CurrentAccountJwt.currentJwt(jdbc, "admin-user");
    }

    private RequestPostProcessor saJwt() {
        return io.campuscore.restfulapi.testsupport.CurrentAccountJwt.currentJwt(jdbc, "sa-user");
    }
}
