package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        "spring.datasource.url=jdbc:h2:mem:admin_user_mutation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AdminUserMutationPersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"auth\"");
        createTables();
        clearTables();
        insertFixture();
    }

    @Test
    void roleDemotionRevokesObsoleteSystemAuthorizationButPreservesCustomRole() throws Exception {
        mvc.perform(put("/api/v1/users/target-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "STUDENT", "studentId": "SV2026001", "curriculumId": "curriculum-demo", "year": 2}
                                """))
                .andExpect(status().isOk());

        assertThat(roleNames("target-user"))
                .containsExactly("AUDITOR", "STUDENT")
                .doesNotContain("ADMIN");
        assertThat(authorities("target-user"))
                .containsExactly("academic:read", "audit:read")
                .doesNotContain("admin:write");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"auth\".\"Student\" WHERE \"userId\" = ?",
                Integer.class,
                "target-user"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"auth\".\"Lecturer\" WHERE \"userId\" = ?",
                Integer.class,
                "target-user"))
                .isZero();
    }

    @Test
    void ordinaryAdminCannotGrantOrEditSuperAdminAccounts() throws Exception {
        mvc.perform(put("/api/v1/users/target-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));

        insertUserRole("user-role-super-target", "target-user", "role-super-admin");
        mvc.perform(put("/api/v1/users/target-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Blocked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
    }

    @Test
    void ordinaryAdminCannotDeleteSuperAdminOrAssignCustomRole() throws Exception {
        insertUserRole("user-role-super-target", "target-user", "role-super-admin");
        mvc.perform(delete("/api/v1/users/target-user").with(adminJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));

        mvc.perform(put("/api/v1/users/second-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"AUDITOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
    }

    @Test
    void userListIncludesCeilingTotalPagesMetadata() throws Exception {
        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "2")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) UNIQUE NOT NULL,
                    "password" VARCHAR(200) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL,
                    "phone" VARCHAR(80),
                    "status" VARCHAR(40) NOT NULL,
                    "emailVerified" BOOLEAN NOT NULL,
                    "isSuperAdmin" BOOLEAN NOT NULL,
                    "failedLoginAttempts" INTEGER NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."Role" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(80) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "isSystem" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."Permission" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(160) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "module" VARCHAR(80) NOT NULL,
                    "action" VARCHAR(80) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."UserRole" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL REFERENCES "auth"."User" ("id") ON DELETE CASCADE,
                    "roleId" VARCHAR(120) NOT NULL REFERENCES "auth"."Role" ("id") ON DELETE CASCADE,
                    CONSTRAINT admin_user_role_unique UNIQUE ("userId", "roleId")
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."RolePermission" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "roleId" VARCHAR(120) NOT NULL REFERENCES "auth"."Role" ("id") ON DELETE CASCADE,
                    "permissionId" VARCHAR(120) NOT NULL REFERENCES "auth"."Permission" ("id") ON DELETE CASCADE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL REFERENCES "auth"."User" ("id") ON DELETE CASCADE,
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
                CREATE TABLE IF NOT EXISTS "auth"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL REFERENCES "auth"."User" ("id") ON DELETE CASCADE,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120) UNIQUE NOT NULL,
                    "isActive" BOOLEAN NOT NULL
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"auth\".\"Student\"");
        jdbc.update("DELETE FROM \"auth\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"auth\".\"RolePermission\"");
        jdbc.update("DELETE FROM \"auth\".\"UserRole\"");
        jdbc.update("DELETE FROM \"auth\".\"Permission\"");
        jdbc.update("DELETE FROM \"auth\".\"Role\"");
        jdbc.update("DELETE FROM \"auth\".\"User\"");
    }

    private void insertFixture() {
        insertRole("role-admin", "ADMIN", true);
        insertRole("role-super-admin", "SUPER_ADMIN", true);
        insertRole("role-student", "STUDENT", true);
        insertRole("role-auditor", "AUDITOR", false);
        insertPermission("permission-admin", "admin", "write");
        insertPermission("permission-academic", "academic", "read");
        insertPermission("permission-audit", "audit", "read");
        insertRolePermission("role-permission-admin", "role-admin", "permission-admin");
        insertRolePermission("role-permission-student", "role-student", "permission-academic");
        insertRolePermission("role-permission-audit", "role-auditor", "permission-audit");
        insertUser("target-user", "target@campuscore.edu", "Target", "User");
        insertUser("second-user", "second@campuscore.edu", "Second", "User");
        insertUser("third-user", "third@campuscore.edu", "Third", "User");
        insertUserRole("user-role-admin", "target-user", "role-admin");
        insertUserRole("user-role-auditor", "target-user", "role-auditor");
        jdbc.update("INSERT INTO \"auth\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('target-student-profile', 'target-user', 'SV-TARGET', 'curriculum-demo',"
                        + " 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO \"auth\".\"Lecturer\""
                        + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                        + " VALUES ('target-lecturer-profile', 'target-user', 'department-demo', 'GV-TARGET', TRUE)");
    }

    private void insertRole(String id, String name, boolean system) {
        jdbc.update("INSERT INTO \"auth\".\"Role\""
                        + " (\"id\", \"name\", \"isSystem\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, name, system);
    }

    private void insertPermission(String id, String module, String action) {
        jdbc.update("INSERT INTO \"auth\".\"Permission\""
                        + " (\"id\", \"name\", \"module\", \"action\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id, module + ":" + action, module, action);
    }

    private void insertRolePermission(String id, String roleId, String permissionId) {
        jdbc.update("INSERT INTO \"auth\".\"RolePermission\" (\"id\", \"roleId\", \"permissionId\") VALUES (?, ?, ?)",
                id, roleId, permissionId);
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update("INSERT INTO \"auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, 'encoded', ?, ?, 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, email, firstName, lastName);
    }

    private void insertUserRole(String id, String userId, String roleId) {
        jdbc.update("INSERT INTO \"auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\") VALUES (?, ?, ?)",
                id, userId, roleId);
    }

    private List<String> roleNames(String userId) {
        return jdbc.queryForList(
                "SELECT role.\"name\" FROM \"auth\".\"UserRole\" user_role"
                        + " JOIN \"auth\".\"Role\" role ON role.\"id\" = user_role.\"roleId\""
                        + " WHERE user_role.\"userId\" = ? ORDER BY role.\"name\"",
                String.class,
                userId);
    }

    private List<String> authorities(String userId) {
        return jdbc.queryForList(
                "SELECT DISTINCT CONCAT(permission.\"module\", ':', permission.\"action\")"
                        + " FROM \"auth\".\"UserRole\" user_role"
                        + " JOIN \"auth\".\"RolePermission\" role_permission ON role_permission.\"roleId\" = user_role.\"roleId\""
                        + " JOIN \"auth\".\"Permission\" permission ON permission.\"id\" = role_permission.\"permissionId\""
                        + " WHERE user_role.\"userId\" = ? ORDER BY 1",
                String.class,
                userId);
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.subject("admin-user").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
