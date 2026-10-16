package io.campuscore.restfulapi.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Persistence fixtures explicitly own current principals, roles and profile identities. */
public final class AccountAuthorityFixture {
    private AccountAuthorityFixture() { }

    /**
     * Creates only the narrow auth tables needed by persistence fixtures. This
     * variant is safe for shared H2 databases: it does not remove another
     * fixture's users, roles, or profile rows.
     */
    public static void ensureSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS campuscore_auth");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"User\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"email\" VARCHAR(320) NOT NULL, \"password\" VARCHAR(200), \"firstName\" VARCHAR(120) NOT NULL,"
                + " \"lastName\" VARCHAR(120) NOT NULL, \"status\" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',"
                + " \"mustChangePassword\" BOOLEAN NOT NULL DEFAULT FALSE, \"twoFactorEnabled\" BOOLEAN NOT NULL DEFAULT FALSE,"
                + " \"emailVerified\" BOOLEAN NOT NULL DEFAULT TRUE, \"isSuperAdmin\" BOOLEAN NOT NULL DEFAULT FALSE,"
                + " \"failedLoginAttempts\" INTEGER NOT NULL DEFAULT 0)");
        ensureCredentialVersion(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"Role\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"name\" VARCHAR(80) UNIQUE, \"description\" VARCHAR(500), \"isSystem\" BOOLEAN DEFAULT TRUE,"
                + " \"createdAt\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP, \"updatedAt\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"UserRole\" (\"id\" VARCHAR(120) PRIMARY KEY, \"userId\" VARCHAR(120), \"roleId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"Permission\" (\"id\" VARCHAR(120) PRIMARY KEY, \"module\" VARCHAR(80), \"action\" VARCHAR(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"RolePermission\" (\"id\" VARCHAR(120) PRIMARY KEY, \"roleId\" VARCHAR(120), \"permissionId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"Student\" (\"id\" VARCHAR(120) PRIMARY KEY, \"userId\" VARCHAR(120) UNIQUE, \"year\" INTEGER)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"Lecturer\" (\"id\" VARCHAR(120) PRIMARY KEY, \"userId\" VARCHAR(120) UNIQUE)");
    }

    /**
     * Compatibility setup for the attendance fixtures, whose authoritative
     * student table is academic.Student. Unlike the former implementation it
     * never clears global role or lecturer rows.
     */
    public static void prepare(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS campuscore_auth");
        ensureCredentialVersion(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"Role\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"name\" VARCHAR(80) UNIQUE, \"description\" VARCHAR(500), \"isSystem\" BOOLEAN DEFAULT TRUE,"
                + " \"createdAt\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP, \"updatedAt\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"UserRole\" (\"id\" VARCHAR(120) PRIMARY KEY, \"userId\" VARCHAR(120), \"roleId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"Permission\" (\"id\" VARCHAR(120) PRIMARY KEY, \"module\" VARCHAR(80), \"action\" VARCHAR(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"RolePermission\" (\"id\" VARCHAR(120) PRIMARY KEY, \"roleId\" VARCHAR(120), \"permissionId\" VARCHAR(120))");
        jdbc.execute("CREATE VIEW IF NOT EXISTS campuscore_auth.\"Student\" AS SELECT \"id\", \"userId\", \"year\" FROM academic.\"Student\"");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"Lecturer\" (\"id\" VARCHAR(120) PRIMARY KEY, \"userId\" VARCHAR(120) UNIQUE)");
    }

    /** Forward-column fixture for hand-created stores with Flyway disabled. */
    public static void ensureCredentialVersion(JdbcTemplate jdbc) {
        jdbc.execute("ALTER TABLE campuscore_auth.\"User\" ADD COLUMN IF NOT EXISTS"
                + " \"credentialVersion\" BIGINT NOT NULL DEFAULT 0 CHECK (\"credentialVersion\" >= 0)");
    }

    /** Real persisted challenge boundary used by password-replacement fixtures. */
    public static void ensureCredentialReplacementSchema(JdbcTemplate jdbc) {
        ensureCredentialVersion(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_auth.\"TwoFactorChallenge\" ("
                + " \"id\" VARCHAR(120) PRIMARY KEY, \"userId\" VARCHAR(120) NOT NULL,"
                + " \"purpose\" VARCHAR(16) NOT NULL, \"codeHash\" VARCHAR(64) NOT NULL,"
                + " \"expiresAt\" TIMESTAMP WITH TIME ZONE NOT NULL, \"consumedAt\" TIMESTAMP WITH TIME ZONE,"
                + " \"attempts\" INTEGER NOT NULL DEFAULT 0,"
                + " \"createdAt\" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    /** Seeds one pending LOGIN row so replacement tests verify durable invalidation. */
    public static void pendingLoginChallenge(JdbcTemplate jdbc, String id, String userId) {
        jdbc.update("INSERT INTO campuscore_auth.\"TwoFactorChallenge\""
                + " (\"id\", \"userId\", \"purpose\", \"codeHash\", \"expiresAt\")"
                + " VALUES (?, ?, 'LOGIN', ?, CURRENT_TIMESTAMP + INTERVAL '1' HOUR)", id, userId, "0".repeat(64));
    }

    /** Inserts a current active user only when the fixture has not already created one. */
    public static void ensureUser(JdbcTemplate jdbc, String subject) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\"=?", Integer.class, subject) > 0) {
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", subject);
        values.put("email", subject + "@fixture.invalid");
        values.put("password", "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        values.put("firstName", "Fixture");
        values.put("lastName", "Account");
        values.put("status", "ACTIVE");
        values.put("mustChangePassword", false);
        values.put("twoFactorEnabled", false);
        values.put("emailVerified", true);
        values.put("isSuperAdmin", false);
        values.put("failedLoginAttempts", 0);
        values.put("createdAt", LocalDateTime.now(ZoneOffset.UTC));
        values.put("updatedAt", LocalDateTime.now(ZoneOffset.UTC));
        insertAvailable(jdbc, "User", values);
    }

    public static void role(JdbcTemplate jdbc, String subject, String role) {
        var ids = jdbc.queryForList("SELECT \"id\" FROM campuscore_auth.\"Role\" WHERE \"name\"=?", String.class, role);
        String roleId = ids.isEmpty() ? "fixture-role-" + role : ids.get(0);
        if (ids.isEmpty()) jdbc.update("INSERT INTO campuscore_auth.\"Role\""
                + " (\"id\", \"name\", \"isSystem\", \"createdAt\", \"updatedAt\") VALUES (?,?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", roleId, role);
        jdbc.update("DELETE FROM campuscore_auth.\"UserRole\" WHERE \"userId\"=? AND \"roleId\"=?", subject, roleId);
        jdbc.update("INSERT INTO campuscore_auth.\"UserRole\" (\"id\", \"userId\", \"roleId\") VALUES (?,?,?)",
                java.util.UUID.nameUUIDFromBytes((subject + ":" + role).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), subject, roleId);
    }

    /** Replaces only the named user's roles; other shared-fixture principals remain intact. */
    public static void replaceRoles(JdbcTemplate jdbc, String subject, String... roles) {
        jdbc.update("DELETE FROM campuscore_auth.\"UserRole\" WHERE \"userId\"=?", subject);
        for (String role : roles) role(jdbc, subject, role);
    }

    public static void lecturer(JdbcTemplate jdbc, String subject, String profile) {
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"userId\"=? OR \"id\"=?", subject, profile);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", profile);
        values.put("userId", subject);
        values.put("departmentId", "fixture-department");
        values.put("employeeId", "fixture-" + profile);
        values.put("isActive", true);
        insertAvailable(jdbc, "Lecturer", values);
        role(jdbc, subject, "LECTURER");
    }

    /** Records the current student identity for fixtures that use the standalone auth profile table. */
    public static void student(JdbcTemplate jdbc, String subject, String profile, int year) {
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"userId\"=? OR \"id\"=?", subject, profile);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", profile);
        values.put("userId", subject);
        values.put("studentId", "fixture-" + profile);
        values.put("curriculumId", "fixture-curriculum");
        values.put("year", year);
        values.put("status", "ACTIVE");
        values.put("admissionDate", LocalDateTime.now(ZoneOffset.UTC));
        insertAvailable(jdbc, "Student", values);
        role(jdbc, subject, "STUDENT");
    }

    private static void insertAvailable(JdbcTemplate jdbc, String table, Map<String, Object> values) {
        Set<String> columns = Set.copyOf(jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                        + " WHERE LOWER(TABLE_SCHEMA)=LOWER('campuscore_auth') AND LOWER(TABLE_NAME)=LOWER(?)",
                String.class,
                table).stream().map(String::toLowerCase).toList());
        Map<String, Object> matching = new LinkedHashMap<>();
        values.forEach((column, value) -> {
            if (columns.contains(column.toLowerCase())) matching.put(column, value);
        });
        if (matching.isEmpty()) throw new IllegalStateException("Fixture auth table has no compatible columns: " + table);
        String names = matching.keySet().stream().map(column -> "\"" + column + "\"").collect(java.util.stream.Collectors.joining(", "));
        String placeholders = String.join(", ", java.util.Collections.nCopies(matching.size(), "?"));
        jdbc.update("INSERT INTO campuscore_auth.\"" + table + "\" (" + names + ") VALUES (" + placeholders + ")",
                matching.values().toArray());
    }
}
