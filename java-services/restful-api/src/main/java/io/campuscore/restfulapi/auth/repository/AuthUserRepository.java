package io.campuscore.restfulapi.auth.repository;

import io.campuscore.restfulapi.auth.web.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.StudentContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC adapter for the Prisma-owned auth schema. */
@Repository
@Profile("persistence")
public class AuthUserRepository {

    private static final String SCHEMA = "\"campuscore_auth\".";
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
    private final boolean postgres;

    public AuthUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.postgres = isPostgres(jdbc.getJdbcTemplate().getDataSource());
    }

    /**
     * Serializes public registration attempts for one normalized address before
     * the read-then-insert sequence. A unique index remains the integrity
     * boundary; this transaction-scoped PostgreSQL lock makes a duplicate
     * request deterministically return the public EMAIL_ALREADY_EXISTS contract
     * instead of surfacing a database constraint race.
     */
    public void lockEmailForRegistration(String normalizedEmail) {
        if (!postgres) {
            return;
        }
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:email AS TEXT), 0))",
                new MapSqlParameterSource("email", normalizedEmail),
                (resultSet, ignored) -> null);
    }

    public Optional<AuthUserRecord> findByEmail(String email) {
        return findByEmail(email, false);
    }

    /**
     * Loads an account while holding its row lock for the surrounding
     * transaction.  Login failure counters are stateful security controls;
     * they must be read from the same serialized stream that updates them.
     */
    public Optional<AuthUserRecord> findByEmailForUpdate(String email) {
        return findByEmail(email, true);
    }

    private Optional<AuthUserRecord> findByEmail(String email, boolean lock) {
        List<AuthUserRecord> matches = jdbc.query(
                "SELECT u.\"id\", u.\"email\", u.\"password\", u.\"firstName\", u.\"lastName\","
                        + " u.\"phone\", u.\"gender\", u.\"dateOfBirth\", u.\"address\", u.\"avatar\","
                        + " u.\"status\", u.\"emailVerified\", u.\"failedLoginAttempts\", u.\"lockedUntil\", u.\"createdAt\","
                        + " s.\"id\" AS student_id, s.\"year\" AS student_year,"
                        + " l.\"id\" AS lecturer_id"
                        + " FROM " + USER_TABLE + " u"
                        + " LEFT JOIN " + STUDENT_TABLE + " s ON s.\"userId\" = u.\"id\""
                        + " LEFT JOIN " + LECTURER_TABLE + " l ON l.\"userId\" = u.\"id\""
                        + " WHERE u.\"email\" = :email"
                        + (lock ? " FOR UPDATE OF u" : ""),
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
                        + " u.\"status\", u.\"emailVerified\", u.\"failedLoginAttempts\", u.\"lockedUntil\", u.\"createdAt\","
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

    public AuthUserRecord createUser(RegisterCommand command) {
        String userId = UUID.randomUUID().toString();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", userId)
                .addValue("email", command.email())
                .addValue("password", command.passwordHash())
                .addValue("firstName", command.firstName())
                .addValue("lastName", command.lastName())
                .addValue("phone", command.phone())
                .addValue("gender", command.gender())
                .addValue("dateOfBirth", localDateTime(command.dateOfBirth()))
                .addValue("address", command.address());
        jdbc.update(
                "INSERT INTO " + USER_TABLE
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\","
                        + " \"phone\", \"gender\", \"dateOfBirth\", \"address\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (:id, :email, :password, :firstName, :lastName, :phone, :gender,"
                        + " :dateOfBirth, :address, 'PENDING_VERIFICATION', FALSE, FALSE, 0,"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                parameters);

        String roleId = jdbc.query(
                        "SELECT \"id\" FROM " + ROLE_TABLE + " WHERE \"name\" = 'STUDENT'",
                        new MapSqlParameterSource(),
                        (resultSet, ignored) -> resultSet.getString("id"))
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    String id = UUID.randomUUID().toString();
                    jdbc.update(
                            "INSERT INTO " + ROLE_TABLE
                                    + " (\"id\", \"name\", \"description\", \"isSystem\", \"createdAt\", \"updatedAt\")"
                                    + " VALUES (:id, 'STUDENT', 'Student access', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                            new MapSqlParameterSource("id", id));
                    return id;
                });
        jdbc.update(
                "INSERT INTO " + USER_ROLE_TABLE + " (\"id\", \"userId\", \"roleId\")"
                        + " SELECT :id, :userId, :roleId WHERE NOT EXISTS"
                        + " (SELECT 1 FROM " + USER_ROLE_TABLE + " WHERE \"userId\" = :userId AND \"roleId\" = :roleId)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("userId", userId)
                        .addValue("roleId", roleId));
        createStudentProfile(userId);
        return findById(userId).orElseThrow(() -> new IllegalStateException("created user was not found"));
    }

    private void createStudentProfile(String userId) {
        String profileId = UUID.randomUUID().toString();
        String studentNumber = "SV" + java.time.Year.now().getValue()
                + profileId.replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        jdbc.update(
                "INSERT INTO " + STUDENT_TABLE
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (:id, :userId, :studentId, 'curriculum-demo', 1, 'ACTIVE',"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource()
                        .addValue("id", profileId)
                        .addValue("userId", userId)
                        .addValue("studentId", studentNumber));
    }

    public Optional<AuthUserRecord> findByActiveRefreshSession(String refreshTokenHash, Instant now) {
        List<AuthUserRecord> matches = jdbc.query(
                "SELECT u.\"id\", u.\"email\", u.\"password\", u.\"firstName\", u.\"lastName\","
                        + " u.\"phone\", u.\"gender\", u.\"dateOfBirth\", u.\"address\", u.\"avatar\","
                        + " u.\"status\", u.\"emailVerified\", u.\"failedLoginAttempts\", u.\"lockedUntil\", u.\"createdAt\","
                        + " st.\"id\" AS student_id, st.\"year\" AS student_year,"
                        + " l.\"id\" AS lecturer_id"
                        + " FROM " + SESSION_TABLE + " se"
                        + " INNER JOIN " + USER_TABLE + " u ON u.\"id\" = se.\"userId\""
                        + " LEFT JOIN " + STUDENT_TABLE + " st ON st.\"userId\" = u.\"id\""
                        + " LEFT JOIN " + LECTURER_TABLE + " l ON l.\"userId\" = u.\"id\""
                        + " WHERE se.\"refreshToken\" = :refreshToken"
                        + " AND se.\"expiresAt\" > :now FOR UPDATE OF se",
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

    public int markEmailVerified(String userId, Instant verifiedAt) {
        return jdbc.update(
                "UPDATE " + USER_TABLE
                        + " SET \"emailVerified\" = TRUE, \"status\" = 'ACTIVE', \"updatedAt\" = :verifiedAt"
                        + " WHERE \"id\" = :userId AND \"emailVerified\" = FALSE"
                        + " AND \"status\" = 'PENDING_VERIFICATION'",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("verifiedAt", localDateTime(verifiedAt)));
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

    public void updateProfile(
            String userId,
            String firstName,
            String lastName,
            String phone,
            Instant dateOfBirth,
            String address) {
        jdbc.update(
                "UPDATE " + USER_TABLE
                        + " SET \"firstName\" = :firstName,"
                        + " \"lastName\" = :lastName,"
                        + " \"phone\" = :phone,"
                        + " \"dateOfBirth\" = :dateOfBirth,"
                        + " \"address\" = :address,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :userId",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("firstName", firstName)
                        .addValue("lastName", lastName)
                        .addValue("phone", phone)
                        .addValue("dateOfBirth", localDateTime(dateOfBirth))
                        .addValue("address", address));
    }

    public void changePassword(String userId, String passwordHash, Instant passwordChangedAt) {
        jdbc.update(
                "UPDATE " + USER_TABLE
                        + " SET \"password\" = :password,"
                        + " \"passwordChangedAt\" = :passwordChangedAt,"
                        + " \"refreshToken\" = NULL,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :userId",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("password", passwordHash)
                        .addValue("passwordChangedAt", localDateTime(passwordChangedAt)));
    }

    public void resetPassword(String userId, String passwordHash, Instant passwordChangedAt) {
        jdbc.update(
                "UPDATE " + USER_TABLE
                        + " SET \"password\" = :password,"
                        + " \"passwordChangedAt\" = :passwordChangedAt,"
                        + " \"refreshToken\" = NULL,"
                        + " \"failedLoginAttempts\" = 0,"
                        + " \"lockedUntil\" = NULL,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"id\" = :userId",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("password", passwordHash)
                        .addValue("passwordChangedAt", localDateTime(passwordChangedAt)));
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
                resultSet.getBoolean("emailVerified"),
                resultSet.getInt("failedLoginAttempts"),
                instant(resultSet.getTimestamp("lockedUntil")),
                instant(resultSet.getTimestamp("createdAt")),
                resultSet.getString("student_id"),
                studentYear,
                resultSet.getString("lecturer_id"),
                List.of(),
                List.of());
    }

    private static boolean isPostgres(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("postgres");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to identify auth user database", exception);
        }
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
            boolean emailVerified,
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
                    emailVerified,
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
                    emailVerified,
                    createdAt,
                    roles,
                    permissions,
                    studentId,
                    lecturerId,
                    student);
        }
    }

    public record RegisterCommand(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String phone,
            String gender,
            Instant dateOfBirth,
            String address) {
    }
}
