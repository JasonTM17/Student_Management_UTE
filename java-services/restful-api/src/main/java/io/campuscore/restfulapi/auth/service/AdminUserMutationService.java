package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class AdminUserMutationService {

    private static final String USER = "\"auth\".\"User\"";
    private static final String ROLE = "\"auth\".\"Role\"";
    private static final String USER_ROLE = "\"auth\".\"UserRole\"";
    private static final String STUDENT = "\"auth\".\"Student\"";
    private static final String LECTURER = "\"auth\".\"Lecturer\"";

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AdminUserMutationService(NamedParameterJdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int limit, String status, String search) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("page and limit are invalid");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", (long) (page - 1) * limit)
                .addValue("status", status)
                .addValue("search", search == null ? null : "%" + search.trim().toLowerCase() + "%");
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT u.\"id\", u.\"email\", u.\"firstName\", u.\"lastName\", u.\"status\", u.\"phone\", u.\"createdAt\","
                        + " COALESCE((SELECT STRING_AGG(r.\"name\", ',') FROM " + USER_ROLE + " ur JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\" WHERE ur.\"userId\" = u.\"id\"), '') AS roles"
                        + " FROM " + USER + " u WHERE (:status IS NULL OR u.\"status\" = :status)"
                        + " AND (:search IS NULL OR LOWER(u.\"email\") LIKE :search OR LOWER(u.\"firstName\") LIKE :search OR LOWER(u.\"lastName\") LIKE :search)"
                        + " ORDER BY u.\"createdAt\" DESC LIMIT :limit OFFSET :offset",
                params);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + USER + " u WHERE (:status IS NULL OR u.\"status\" = :status)"
                        + " AND (:search IS NULL OR LOWER(u.\"email\") LIKE :search OR LOWER(u.\"firstName\") LIKE :search OR LOWER(u.\"lastName\") LIKE :search)",
                params, Long.class);
        return Map.of("data", data, "meta", Map.of("total", total == null ? 0 : total, "page", page, "limit", limit));
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> input) {
        String email = required(input, "email").toLowerCase();
        String password = required(input, "password");
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO " + USER
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (:id, :email, :password, :firstName, :lastName, 'ACTIVE',"
                        + " FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource().addValue("id", id).addValue("email", email)
                        .addValue("password", passwordEncoder.encode(password))
                        .addValue("firstName", required(input, "firstName"))
                        .addValue("lastName", required(input, "lastName")));
        String role = text(input, "role", "STUDENT").toUpperCase(java.util.Locale.ROOT);
        assignRole(id, role);
        ensureProfile(id, role, input);
        return find(id);
    }

    @Transactional
    public Map<String, Object> update(String id, Map<String, Object> input) {
        int updated = jdbc.update(
                "UPDATE " + USER + " SET \"firstName\" = COALESCE(:firstName, \"firstName\"),"
                        + " \"lastName\" = COALESCE(:lastName, \"lastName\"), \"phone\" = COALESCE(:phone, \"phone\"),"
                        + " \"status\" = COALESCE(:status, \"status\"), \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("firstName", input.get("firstName"))
                        .addValue("lastName", input.get("lastName")).addValue("phone", input.get("phone"))
                        .addValue("status", input.get("status")));
        if (updated == 0) throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        if (input.get("role") != null) {
            String role = text(input, "role", "STUDENT").toUpperCase(java.util.Locale.ROOT);
            assignRole(id, role);
            ensureProfile(id, role, input);
        }
        return find(id);
    }

    @Transactional
    public void delete(String id) {
        try {
            int deleted = jdbc.update("DELETE FROM " + USER + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id));
            if (deleted == 0) throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "USER_IN_USE", "User is still referenced by an academic profile");
        }
    }

    private Map<String, Object> find(String id) {
        return jdbc.queryForMap("SELECT \"id\", \"email\", \"firstName\", \"lastName\", \"status\", \"phone\", \"createdAt\" FROM " + USER + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id));
    }

    private void assignRole(String userId, String roleName) {
        String roleId = jdbc.queryForObject("SELECT \"id\" FROM " + ROLE + " WHERE \"name\" = :name", new MapSqlParameterSource("name", roleName.toUpperCase()), String.class);
        if (roleId == null) throw problem(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "Role not found");
        jdbc.update("INSERT INTO " + USER_ROLE + " (\"id\", \"userId\", \"roleId\") SELECT :id, :userId, :roleId WHERE NOT EXISTS (SELECT 1 FROM " + USER_ROLE + " WHERE \"userId\" = :userId AND \"roleId\" = :roleId)", new MapSqlParameterSource().addValue("id", UUID.randomUUID().toString()).addValue("userId", userId).addValue("roleId", roleId));
    }

    private void ensureProfile(String userId, String roleName, Map<String, Object> input) {
        if (profileExists(userId, roleName)) {
            return;
        }
        String profileId = UUID.randomUUID().toString();
        if ("STUDENT".equals(roleName)) {
            String studentNumber = text(input, "studentId", "SV" + java.time.Year.now().getValue()
                    + profileId.replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT));
            jdbc.update(
                    "INSERT INTO " + STUDENT
                            + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                            + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                            + " VALUES (:id, :userId, :studentId, :curriculumId, :year, 'ACTIVE',"
                            + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    new MapSqlParameterSource()
                            .addValue("id", profileId)
                            .addValue("userId", userId)
                            .addValue("studentId", studentNumber)
                            .addValue("curriculumId", text(input, "curriculumId", "curriculum-demo"))
                            .addValue("year", Integer.parseInt(text(input, "year", "1"))));
        } else if ("LECTURER".equals(roleName)) {
            String employeeId = text(input, "employeeId", "GV"
                    + profileId.replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT));
            jdbc.update(
                    "INSERT INTO " + LECTURER
                            + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                            + " VALUES (:id, :userId, :departmentId, :employeeId, TRUE)",
                    new MapSqlParameterSource()
                            .addValue("id", profileId)
                            .addValue("userId", userId)
                            .addValue("departmentId", text(input, "departmentId", "department-demo"))
                            .addValue("employeeId", employeeId));
        }
    }

    private boolean profileExists(String userId, String roleName) {
        String table = "STUDENT".equals(roleName) ? STUDENT : "LECTURER".equals(roleName) ? LECTURER : null;
        if (table == null) {
            return true;
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE \"userId\" = :userId",
                new MapSqlParameterSource("userId", userId),
                Long.class);
        return count != null && count > 0;
    }

    private static String required(Map<String, Object> input, String key) {
        String value = text(input, key, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String text(Map<String, Object> input, String key, String fallback) {
        Object value = input.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
