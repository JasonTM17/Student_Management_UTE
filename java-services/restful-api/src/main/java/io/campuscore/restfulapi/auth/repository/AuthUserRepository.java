package io.campuscore.restfulapi.auth.repository;

import io.campuscore.restfulapi.auth.web.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.StudentContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC adapter for the Prisma-owned auth schema. */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.auth-login", name = "enabled", havingValue = "true")
public class AuthUserRepository {

    private static final String SCHEMA = "\"auth\".";
    private static final String USER_TABLE = SCHEMA + "\"User\"";
    private static final String STUDENT_TABLE = SCHEMA + "\"Student\"";
    private static final String LECTURER_TABLE = SCHEMA + "\"Lecturer\"";
    private static final String SESSION_TABLE = SCHEMA + "\"Session\"";
    private static final String ROLE_TABLE = SCHEMA + "\"Role\"";
    private static final String PERMISSION_TABLE = SCHEMA + "\"Permission\"";
    private static final String USER_ROLE_TABLE = SCHEMA + "\"UserRole\"";
    private static final String ROLE_PERMISSION_TABLE = SCHEMA + "\"RolePermission\"";
    private static final RowMapper<AuthUserRecord> USER_MAPPER = AuthUserRepository::mapUser;

    private final NamedParameterJdbcTemplate jdbc;

    public AuthUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthUserRecord> findByEmail(String email) {
        List<AuthUserRecord> matches = jdbc.query(
                "SELECT u.\"id\", u.\"email\", u.\"password\", u.\"firstName\", u.\"lastName\","
                        + " u.\"phone\", u.\"gender\", u.\"dateOfBirth\", u.\"address\", u.\"avatar\","
                        + " u.\"status\", u.\"failedLoginAttempts\", u.\"lockedUntil\", u.\"createdAt\","
                        + " s.\"id\" AS student_id, s.\"year\" AS student_year,"
                        + " l.\"id\" AS lecturer_id"
                        + " FROM " + USER_TABLE + " u"
                        + " LEFT JOIN " + STUDENT_TABLE + " s ON s.\"userId\" = u.\"id\""
                        + " LEFT JOIN " + LECTURER_TABLE + " l ON l.\"userId\" = u.\"id\""
                        + " WHERE u.\"email\" = :email",
                new MapSqlParameterSource("email", email),
                USER_MAPPER);
        return matches.stream().findFirst().map(user -> user.withAuthorities(
                findRoles(user.id()),
                findPermissions(user.id())));
    }

    public Optional<AuthUserRecord> findById(String userId) {
        List<AuthUserRecord> matches = jdbc.query(
                "SELECT u.\"id\", u.\"email\", u.\"password\", u.\"firstName\", u.\"lastName\","
                        + " u.\"phone\", u.\"gender\", u.\"dateOfBirth\", u.\"address\", u.\"avatar\","
                        + " u.\"status\", u.\"failedLoginAttempts\", u.\"lockedUntil\", u.\"createdAt\","
                        + " s.\"id\" AS student_id, s.\"year\" AS student_year,"
                        + " l.\"id\" AS lecturer_id"
                        + " FROM " + USER_TABLE + " u"
                        + " LEFT JOIN " + STUDENT_TABLE + " s ON s.\"userId\" = u.\"id\""
                        + " LEFT JOIN " + LECTURER_TABLE + " l ON l.\"userId\" = u.\"id\""
                        + " WHERE u.\"id\" = :id",
                new MapSqlParameterSource("id", userId),
                USER_MAPPER);
        return matches.stream().findFirst().map(user -> user.withAuthorities(
                findRoles(user.id()),
                findPermissions(user.id())));
    }

    public Optional<AuthUserRecord> findByActiveRefreshSession(String refreshTokenHash, Instant now) {
        List<AuthUserRecord> matches = jdbc.query(
                "SELECT u.\"id\", u.\"email\", u.\"password\", u.\"firstName\", u.\"lastName\","
                        + " u.\"phone\", u.\"gender\", u.\"dateOfBirth\", u.\"address\", u.\"avatar\","
                        + " u.\"status\", u.\"failedLoginAttempts\", u.\"lockedUntil\", u.\"createdAt\","
                        + " st.\"id\" AS student_id, st.\"year\" AS student_year,"
                        + " l.\"id\" AS lecturer_id"
                        + " FROM " + SESSION_TABLE + " se"
                        + " INNER JOIN " + USER_TABLE + " u ON u.\"id\" = se.\"userId\""
                        + " LEFT JOIN " + STUDENT_TABLE + " st ON st.\"userId\" = u.\"id\""
                        + " LEFT JOIN " + LECTURER_TABLE + " l ON l.\"userId\" = u.\"id\""
                        + " WHERE se.\"refreshToken\" = :refreshToken"
                        + " AND se.\"expiresAt\" > :now",
                new MapSqlParameterSource()
                        .addValue("refreshToken", refreshTokenHash)
                        .addValue("now", localDateTime(now)),
                USER_MAPPER);
        return matches.stream().findFirst().map(user -> user.withAuthorities(
                findRoles(user.id()),
                findPermissions(user.id())));
    }

    public void recordFailedLogin(String userId, int nextAttemptCount, Instant lockedUntil) {
        jdbc.update(
                "UPDATE " + USER_TABLE + " SET \"failedLoginAttempts\" = :attempts,"
                        + " \"lockedUntil\" = :lockedUntil, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :id",
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("attempts", nextAttemptCount)
                        .addValue("lockedUntil", localDateTime(lockedUntil)));
    }

    public void recordSuccessfulLogin(String userId, Instant loggedInAt) {
        jdbc.update(
                "UPDATE " + USER_TABLE + " SET \"failedLoginAttempts\" = 0, \"lockedUntil\" = NULL,"
                        + " \"lastLoginAt\" = :loggedInAt, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :id",
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("loggedInAt", localDateTime(loggedInAt)));
    }

    public void replaceRefreshSession(
            String userId,
            String sessionId,
            String refreshTokenHash,
            String ipAddress,
            String userAgent,
            Instant expiresAt) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("sessionId", sessionId)
                .addValue("refreshToken", refreshTokenHash)
                .addValue("ipAddress", ipAddress)
                .addValue("userAgent", userAgent)
                .addValue("expiresAt", localDateTime(expiresAt));
        jdbc.update("DELETE FROM " + SESSION_TABLE + " WHERE \"userId\" = :userId", parameters);
        jdbc.update(
                "INSERT INTO " + SESSION_TABLE
                        + " (\"id\", \"userId\", \"refreshToken\", \"userAgent\", \"ipAddress\", \"expiresAt\", \"createdAt\")"
                        + " VALUES (:sessionId, :userId, :refreshToken, :userAgent, :ipAddress, :expiresAt, CURRENT_TIMESTAMP)",
                parameters);
        jdbc.update(
                "UPDATE " + USER_TABLE + " SET \"refreshToken\" = :refreshToken, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :userId",
                parameters);
    }

    public void deleteRefreshSession(String userId, String refreshTokenHash) {
        jdbc.update(
                "DELETE FROM " + SESSION_TABLE
                        + " WHERE \"userId\" = :userId AND \"refreshToken\" = :refreshToken",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("refreshToken", refreshTokenHash));
    }

    public void deleteAllRefreshSessions(String userId) {
        jdbc.update(
                "DELETE FROM " + SESSION_TABLE + " WHERE \"userId\" = :userId",
                new MapSqlParameterSource("userId", userId));
    }

    public void clearUserRefreshToken(String userId) {
        jdbc.update(
                "UPDATE " + USER_TABLE + " SET \"refreshToken\" = NULL, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :userId",
                new MapSqlParameterSource("userId", userId));
    }

    private List<String> findRoles(String userId) {
        return jdbc.queryForList(
                "SELECT r.\"name\""
                        + " FROM " + USER_ROLE_TABLE + " ur"
                        + " INNER JOIN " + ROLE_TABLE + " r ON r.\"id\" = ur.\"roleId\""
                        + " WHERE ur.\"userId\" = :userId"
                        + " ORDER BY r.\"name\"",
                new MapSqlParameterSource("userId", userId),
                String.class);
    }

    private List<String> findPermissions(String userId) {
        return jdbc.queryForList(
                "SELECT DISTINCT CONCAT(p.\"module\", ':', p.\"action\") AS authority"
                        + " FROM " + USER_ROLE_TABLE + " ur"
                        + " INNER JOIN " + ROLE_PERMISSION_TABLE + " rp ON rp.\"roleId\" = ur.\"roleId\""
                        + " INNER JOIN " + PERMISSION_TABLE + " p ON p.\"id\" = rp.\"permissionId\""
                        + " WHERE ur.\"userId\" = :userId"
                        + " ORDER BY authority",
                new MapSqlParameterSource("userId", userId),
                String.class);
    }

    private static AuthUserRecord mapUser(ResultSet resultSet, int ignored) throws SQLException {
        Integer studentYear = nullableInteger(resultSet, "student_year");
        return new AuthUserRecord(
                resultSet.getString("id"),
                resultSet.getString("email"),
                resultSet.getString("password"),
                resultSet.getString("firstName"),
                resultSet.getString("lastName"),
                resultSet.getString("phone"),
                resultSet.getString("gender"),
                instant(resultSet.getTimestamp("dateOfBirth")),
                resultSet.getString("address"),
                resultSet.getString("avatar"),
                resultSet.getString("status"),
                resultSet.getInt("failedLoginAttempts"),
                instant(resultSet.getTimestamp("lockedUntil")),
                instant(resultSet.getTimestamp("createdAt")),
                resultSet.getString("student_id"),
                studentYear,
                resultSet.getString("lecturer_id"),
                List.of(),
                List.of());
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime localDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record AuthUserRecord(
            String id,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String phone,
            String gender,
            Instant dateOfBirth,
            String address,
            String avatar,
            String status,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant createdAt,
            String studentId,
            Integer studentYear,
            String lecturerId,
            List<String> roles,
            List<String> permissions) {

        AuthUserRecord withAuthorities(List<String> roles, List<String> permissions) {
            return new AuthUserRecord(
                    id,
                    email,
                    passwordHash,
                    firstName,
                    lastName,
                    phone,
                    gender,
                    dateOfBirth,
                    address,
                    avatar,
                    status,
                    failedLoginAttempts,
                    lockedUntil,
                    createdAt,
                    studentId,
                    studentYear,
                    lecturerId,
                    List.copyOf(Objects.requireNonNullElse(roles, List.of())),
                    List.copyOf(Objects.requireNonNullElse(permissions, List.of())));
        }

        public AuthUserResponse toResponse() {
            StudentContext student = studentYear == null ? null : new StudentContext(studentYear);
            return new AuthUserResponse(
                    id,
                    email,
                    firstName,
                    lastName,
                    phone,
                    gender,
                    dateOfBirth,
                    address,
                    avatar,
                    status,
                    createdAt,
                    roles,
                    permissions,
                    studentId,
                    lecturerId,
                    student);
        }
    }
}
