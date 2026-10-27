package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.common.util.LikePattern;
import io.campuscore.restfulapi.exception.DomainException;
import java.security.SecureRandom;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Profile("persistence")
public class AdminUserMutationService {

    private static final String USER = "\"campuscore_auth\".\"User\"";
    private static final String ROLE = "\"campuscore_auth\".\"Role\"";
    private static final String USER_ROLE = "\"campuscore_auth\".\"UserRole\"";
    private static final String STUDENT = "\"campuscore_auth\".\"Student\"";
    private static final String LECTURER = "\"campuscore_auth\".\"Lecturer\"";
    private static final Set<String> SYSTEM_ROLES = Set.of("STUDENT", "LECTURER", "ADMIN", "TRUONG_KHOA", "TRUONG_BO_MON", "SUPER_ADMIN");
    private static final String ACADEMIC_DEPARTMENT = "academic.\"Department\"";
    private static final String ACADEMIC_CURRICULUM = "academic.\"Curriculum\"";
    /**
     * Roles a plain administrator may not hand out. {@code TRUONG_KHOA} is here
     * because a faculty head governs the thesis round lifecycle, which the role
     * ceiling reserves above a plain administrator: without it one administrator
     * could mint a dean and escalate through that account.
     *
     * <p>The same set governs {@link #requireSuperAdminForProtectedAccount}: a role
     * a plain administrator may not grant is also one whose existing holders it may
     * not take over, disable or delete. Blocking only the grant leaves the identical
     * escalation reachable through the account instead of the role.
     *
     * <p>Ordered so the refusal message names the same role every time.
     */
    private static final List<String> SUPER_ADMIN_ONLY_ROLES = List.of("ADMIN", "SUPER_ADMIN", "TRUONG_KHOA", "TRUONG_BO_MON");
    /** Lifecycle values the account-state filter and the UI understand. */
    private static final Set<String> ACCOUNT_STATUSES = Set.of("ACTIVE", "PENDING", "SUSPENDED", "LOCKED", "DISABLED");
    private static final String TEMP_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuthUserRepository authUsers;
    private final AdminAuditRecorder audit;
    private final SecureRandom random = new SecureRandom();

    public AdminUserMutationService(
            NamedParameterJdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            AuthUserRepository authUsers,
            AdminAuditRecorder audit) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.authUsers = authUsers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int limit, String status, String search) {
        return list(page, limit, status, search, null);
    }

    /**
     * The role filter joins through the assignment tables rather than reading a
     * column: a user's roles live in {@code UserRole}/{@code Role}, and the same
     * predicate is applied to both the page query and the count query so
     * {@code meta.total} describes the filtered set, not the whole table. A tab
     * whose count ignores its own filter is the defect this closes.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int limit, String status, String search, String role) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("page and limit are invalid");
        }
        String roleFilter = role == null || role.isBlank()
                ? null
                : role.trim().toUpperCase(java.util.Locale.ROOT);
        if (roleFilter != null && !SYSTEM_ROLES.contains(roleFilter)) {
            // Same code the mutation guards use for a name that is not a role,
            // so consumers map one code across the whole admin API.
            throw problem(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "Role not found");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", (long) (page - 1) * limit)
                .addValue("status", status, Types.VARCHAR)
                .addValue("role", roleFilter, Types.VARCHAR)
                .addValue("search", LikePattern.contains(search), Types.VARCHAR)
                .addValue("foldFrom", LikePattern.FOLD_FROM)
                .addValue("foldTo", LikePattern.FOLD_TO);
        String searchClause = buildUserSearchClause(params, search);
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT u.\"id\", u.\"email\", u.\"firstName\", u.\"lastName\", u.\"status\", u.\"phone\", u.\"createdAt\","
                        + " (SELECT sp.\"studentId\" FROM " + STUDENT + " sp WHERE sp.\"userId\" = u.\"id\" LIMIT 1) AS \"studentId\","
                        + " (SELECT lp.\"employeeId\" FROM " + LECTURER + " lp WHERE lp.\"userId\" = u.\"id\" LIMIT 1) AS \"employeeId\","
                        + " COALESCE((SELECT STRING_AGG(r.\"name\", ',') FROM " + USER_ROLE + " ur JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\" WHERE ur.\"userId\" = u.\"id\"), '') AS roles,"
                        + " EXISTS(SELECT 1 FROM " + STUDENT + " sp WHERE sp.\"userId\" = u.\"id\") AS \"hasStudentProfile\","
                        + " EXISTS(SELECT 1 FROM " + LECTURER + " lp WHERE lp.\"userId\" = u.\"id\") AS \"hasLecturerProfile\""
                        // PostgreSQL cannot infer the type of an unbound NULL in an IS NULL
                        // predicate. Cast optional filters so the first request from the admin
                        // console (which intentionally sends neither filter) remains valid.
                        // TRANSLATE folds Vietnamese diacritics on the column side so the
                        // operator's unaccented typing still matches the stored spelling.
                        + " FROM " + USER + " u WHERE (CAST(:status AS VARCHAR) IS NULL OR u.\"status\" = CAST(:status AS VARCHAR))"
                        + " AND (CAST(:role AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM " + USER_ROLE + " fr JOIN " + ROLE + " g ON g.\"id\" = fr.\"roleId\""
                        + " WHERE fr.\"userId\" = u.\"id\" AND g.\"name\" = CAST(:role AS VARCHAR)))"
                        + searchClause
                        + " ORDER BY u.\"createdAt\" DESC, u.\"id\" DESC LIMIT :limit OFFSET :offset",
                params);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + USER + " u WHERE (CAST(:status AS VARCHAR) IS NULL OR u.\"status\" = CAST(:status AS VARCHAR))"
                        + " AND (CAST(:role AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM " + USER_ROLE + " fr JOIN " + ROLE + " g ON g.\"id\" = fr.\"roleId\""
                        + " WHERE fr.\"userId\" = u.\"id\" AND g.\"name\" = CAST(:role AS VARCHAR)))"
                        + searchClause,
                params, Long.class);
        long totalItems = total == null ? 0 : total;
        int totalPages = (int) ((totalItems + limit - 1) / limit);
        data.forEach(AdminUserMutationService::withRolesAsArray);
        return Map.of("data", data, "meta", Map.of(
                "total", totalItems,
                "page", page,
                "limit", limit,
                "totalPages", totalPages));
    }

    private static String buildUserSearchClause(MapSqlParameterSource params, String search) {
        List<String> tokens = LikePattern.tokens(search);
        if (tokens.isEmpty()) {
            return " AND (CAST(:search AS VARCHAR) IS NULL"
                    + " OR TRANSLATE(LOWER(u.\"email\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\'"
                    + " OR TRANSLATE(LOWER(u.\"firstName\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\'"
                    + " OR TRANSLATE(LOWER(u.\"lastName\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\')";
        }
        StringBuilder clause = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String paramName = "searchToken" + i;
            params.addValue(paramName, tokens.get(i), Types.VARCHAR);
            clause.append(" AND (TRANSLATE(LOWER(u.\"email\"), :foldFrom, :foldTo) LIKE CAST(:").append(paramName).append(" AS VARCHAR) ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(u.\"firstName\"), :foldFrom, :foldTo) LIKE CAST(:").append(paramName).append(" AS VARCHAR) ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(u.\"lastName\"), :foldFrom, :foldTo) LIKE CAST(:").append(paramName).append(" AS VARCHAR) ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(COALESCE(u.\"phone\", '')), :foldFrom, :foldTo) LIKE CAST(:").append(paramName).append(" AS VARCHAR) ESCAPE '\\'")
                    .append(" OR EXISTS (SELECT 1 FROM ").append(STUDENT).append(" sp WHERE sp.\"userId\" = u.\"id\"")
                    .append(" AND TRANSLATE(LOWER(sp.\"studentId\"), :foldFrom, :foldTo) LIKE CAST(:").append(paramName).append(" AS VARCHAR) ESCAPE '\\')")
                    .append(" OR EXISTS (SELECT 1 FROM ").append(LECTURER).append(" lp WHERE lp.\"userId\" = u.\"id\"")
                    .append(" AND TRANSLATE(LOWER(lp.\"employeeId\"), :foldFrom, :foldTo) LIKE CAST(:").append(paramName).append(" AS VARCHAR) ESCAPE '\\'))");
        }
        return clause.toString();
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> input) {
        return create(input, false);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> input, boolean canManageSuperAdmin) {
        return create(input, canManageSuperAdmin, null);
    }

    @Transactional
    public Map<String, Object> create(
            Map<String, Object> input, boolean canManageSuperAdmin, String actorId) {
        String email = required(input, "email").toLowerCase();
        // The Academic Office never accepts an admin-chosen start credential:
        // a server-generated one-time temporary password is issued and shown
        // exactly once, and the account cannot be used until it is rotated.
        String role = text(input, "role", "STUDENT").toUpperCase(java.util.Locale.ROOT);
        guardRoleMutation(null, role, canManageSuperAdmin);
        requireNoOtherDean(null, role, input);
        preflightConflicts(email, role, input, null);

        String temporaryPassword = generateTemporaryPassword();
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update(
                    "INSERT INTO " + USER
                            + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                            + " \"mustChangePassword\", \"emailVerified\", \"isSuperAdmin\","
                            + " \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                            + " VALUES (:id, :email, :password, :firstName, :lastName, 'ACTIVE',"
                            + " TRUE, FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    new MapSqlParameterSource().addValue("id", id).addValue("email", email)
                            .addValue("password", passwordEncoder.encode(temporaryPassword))
                            .addValue("firstName", required(input, "firstName"))
                            .addValue("lastName", required(input, "lastName")));
        } catch (DataIntegrityViolationException exception) {
            throw conflict(exception);
        }
        for (String systemRole : desiredSystemRoles(role)) {
            assignRole(id, systemRole);
        }
        ensureProfile(id, role, input);
        // Recorded without the request map: it carries a credential by design.
        audit.record(actorId, null, "CREATED", "USER", id,
                "Created " + role + " account " + email);
        return withTemporaryPassword(find(id), temporaryPassword);
    }

    @Transactional
    public Map<String, Object> resetPassword(String id, boolean canManageSuperAdmin, String currentUserId) {
        if (currentUserId != null && currentUserId.equals(id)) {
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "SELF_PASSWORD_RESET_NOT_ALLOWED",
                    "Use change-password to rotate your own password");
        }
        // Rotating a protected account's password hands out a working credential
        // for the role it holds, so this is the same ceiling as granting the role.
        requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "reset the password of");
        String temporaryPassword = generateTemporaryPassword();
        // An office-issued credential must work immediately: a lockout left
        // behind by failed logins would make the just-issued password still
        // answer "Invalid credentials" until it aged out.
        int updated = jdbc.update(
                "UPDATE " + USER + " SET \"password\" = :password, \"mustChangePassword\" = TRUE,"
                        + " \"lockedUntil\" = NULL, \"failedLoginAttempts\" = 0,"
                        + " \"credentialVersion\" = \"credentialVersion\" + 1, \"passwordChangedAt\" = CURRENT_TIMESTAMP,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource().addValue("id", id)
                        .addValue("password", passwordEncoder.encode(temporaryPassword)));
        if (updated == 0) throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        authUsers.invalidateLoginChallenges(id);
        revokeSessions(id);
        // Recorded as an event, never as state: the new credential must not reach
        // the audit table, and a password reset is exactly the operation whose
        // author an investigation needs to know.
        audit.record(currentUserId, null, "PASSWORD_RESET", "USER", id,
                "Issued a one-time password for user " + id);
        return withTemporaryPassword(find(id), temporaryPassword);
    }

    /**
     * five failed logins lock an account for 30 minutes
     * and no API could clear it — the only clearing write (recordSuccessfulLogin)
     * sits behind the very gate the lock trips, and the admin update path never
     * touched lockedUntil, so "the correct password still says Invalid
     * credentials" was unrecoverable for half an hour. An administrator can now
     * lift the lock immediately. No session revocation: the lockout only gates
     * new logins, never an established session.
     */
    @Transactional
    public Map<String, Object> unlock(String id, boolean canManageSuperAdmin, String currentUserId) {
        requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "unlock");
        int updated = jdbc.update(
                "UPDATE " + USER + " SET \"lockedUntil\" = NULL, \"failedLoginAttempts\" = 0,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource().addValue("id", id));
        if (updated == 0) throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        audit.record(currentUserId, null, "USER_UNLOCKED", "USER", id,
                "Cleared the login lockout for user " + id);
        return find(id);
    }

    /**
     * single-user detail for the admin console (the collection
     * existed; one id used to answer 405). A protected account's detail is
     * subject to the same ceiling as managing it.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> get(String id, boolean canManageSuperAdmin) {
        requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "view");
        try {
            return find(id);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }
    }

    @Transactional
    public Map<String, Object> update(String id, Map<String, Object> input) {
        return update(id, input, false, null);
    }

    @Transactional
    public Map<String, Object> update(String id, Map<String, Object> input, boolean canManageSuperAdmin) {
        return update(id, input, canManageSuperAdmin, null);
    }

    @Transactional
    public Map<String, Object> update(String id, Map<String, Object> input, boolean canManageSuperAdmin, String currentUserId) {
        lockUserRow(id);
        // A blank role means "the caller did not send one", not "make this account a
        // student". `text()` would substitute its fallback for the blank, so a client
        // that echoes an empty role back would silently strip a dean's role.
        Object rawRole = input.get("role");
        String requestedRole = rawRole == null || rawRole.toString().isBlank()
                ? null
                : text(input, "role", "STUDENT").toUpperCase(java.util.Locale.ROOT);
        // Canonical status form computed once: the same normalized value feeds
        // every comparison below and the SQL binding, and a blank means "not
        // provided" — it is never stored.
        String newStatus = input.get("status") == null ? null : text(input, "status", "");
        String canonicalStatus = newStatus == null || newStatus.isBlank()
                ? null
                : newStatus.toUpperCase(java.util.Locale.ROOT);
        if (currentUserId != null && currentUserId.equals(id)) {
            if (requestedRole != null
                    && !systemRoleNames(id).equals(desiredSystemRoles(requestedRole))) {
                throw problem(
                        HttpStatus.BAD_REQUEST,
                        "SELF_ROLE_CHANGE_NOT_ALLOWED",
                        "You cannot change your own system role");
            }
            if (canonicalStatus != null && !"ACTIVE".equals(canonicalStatus)) {
                throw problem(
                        HttpStatus.BAD_REQUEST,
                        "SELF_DEACTIVATION_NOT_ALLOWED",
                        "You cannot deactivate or lock your own account");
            }
        }
        if (requestedRole != null) {
            guardRoleMutation(id, requestedRole, canManageSuperAdmin);
            requireNoOtherDean(id, requestedRole, input);
        } else {
            requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "manage");
        }
        if (!canManageSuperAdmin && requestedRole == null && (hasRole(id, "ADMIN") || hasRole(id, "SUPER_ADMIN"))) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can manage administrator accounts");
        }
        // Disabling a protected account is the same refusal as deleting it: the role
        // stops functioning either way, and reactivating it hands the reserved role
        // back into service. Re-sending an unchanged role is not covered by that
        // guard, so this closes the gap the grant-side exemption leaves.
        if (canonicalStatus != null) {
            requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "change status");
        }
        if (canonicalStatus != null && !ACCOUNT_STATUSES.contains(canonicalStatus)) {
            // Free-form status values would silently break the account-state
            // filter and the console badges; only the lifecycle set is valid.
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ACCOUNT_STATUS",
                    "Unknown account status");
        }
        String previousStatus = jdbc.query(
                        "SELECT \"status\" FROM " + USER + " WHERE \"id\" = :id",
                        new MapSqlParameterSource("id", id),
                        (resultSet, ignored) -> resultSet.getString("status"))
                .stream().findFirst()
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        boolean removesActiveSuperAdmin = "ACTIVE".equalsIgnoreCase(previousStatus)
                && hasRole(id, "SUPER_ADMIN")
                && ((requestedRole != null && !desiredSystemRoles(requestedRole).contains("SUPER_ADMIN"))
                        || (canonicalStatus != null && !"ACTIVE".equals(canonicalStatus)));
        if (removesActiveSuperAdmin) {
            requireAnotherActiveSuperAdmin();
        }
        int updated = jdbc.update(
                "UPDATE " + USER + " SET \"firstName\" = COALESCE(:firstName, \"firstName\"),"
                        + " \"lastName\" = COALESCE(:lastName, \"lastName\"), \"phone\" = COALESCE(:phone, \"phone\"),"
                        + " \"status\" = COALESCE(:status, \"status\"), \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("firstName", input.get("firstName"))
                        .addValue("lastName", input.get("lastName")).addValue("phone", input.get("phone"))
                        .addValue("status", canonicalStatus));
        if (updated == 0) throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        if (previousStatus != null && "ACTIVE".equalsIgnoreCase(previousStatus)
                && canonicalStatus != null && !"ACTIVE".equals(canonicalStatus)) {
            // Deactivation must cut live access immediately: drop refresh
            // sessions so the stale token cannot rotate, and the account-state
            // filter rejects remaining access tokens on the next request.
            revokeSessions(id);
        }
        if (requestedRole != null) {
            // roles live in the JWT claims, and the
            // account-state filter never re-derives them from the DB — a
            // demoted admin's old access token kept ROLE_ADMIN until its 15
            // -minute TTL. A genuine role change now cuts live sessions the
            // same way deactivation does (same caveat: refresh rotation dies
            // with the session rows).
            Set<String> desiredRoles = desiredSystemRoles(requestedRole);
            // A conversion that mints a new official profile validates the
            // carried identifiers before any role or profile write, so a
            // duplicate or missing value leaves every column untouched.
            boolean needsStudentProfile =
                    desiredRoles.contains("STUDENT") && !profileExists(id, "STUDENT");
            boolean needsLecturerProfile =
                    desiredRoles.contains("LECTURER") && !profileExists(id, "LECTURER");
            if (needsStudentProfile) {
                preflightProfileConflicts("STUDENT", input);
            }
            if (needsLecturerProfile) {
                preflightProfileConflicts("LECTURER", input);
            }
            // The change test compares the whole system-role set, not just the
            // requested name: demoting a dean to LECTURER is genuine even though
            // the account already holds LECTURER.
            boolean roleChanged = !systemRoleNames(id).equals(desiredRoles);
            replaceSystemRoles(id, desiredRoles);
            removeObsoleteProfiles(id, desiredRoles);
            ensureProfile(id, requestedRole, input);
            if (roleChanged) {
                revokeSessions(id);
            }
        } else {
            completeMissingProfiles(id, input);
        }
        // Records the transition, not the payload: the request map can carry a
        // credential, and a role or status change is what an investigation looks for.
        audit.record(currentUserId, null, requestedRole != null ? "ROLE_OR_DETAILS_CHANGED" : "UPDATED",
                "USER", id,
                "Updated user " + id
                        + (requestedRole != null ? " to role " + requestedRole : "")
                        + (canonicalStatus != null ? " status " + canonicalStatus : ""),
                Map.of("status", String.valueOf(previousStatus), "role", String.valueOf(requestedRole)),
                Map.of("status", String.valueOf(canonicalStatus), "role", String.valueOf(requestedRole)));
        return find(id);
    }

    @Transactional
    public void delete(String id) {
        delete(id, false, null);
    }

    @Transactional
    public void delete(String id, boolean canManageSuperAdmin) {
        delete(id, canManageSuperAdmin, null);
    }

    @Transactional
    public void delete(String id, boolean canManageSuperAdmin, String currentUserId) {
        if (currentUserId != null && currentUserId.equals(id)) {
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "SELF_DELETION_NOT_ALLOWED",
                    "You cannot delete your own account");
        }
        lockUserRow(id);
        // Same ceiling as update()/resetPassword(): a plain administrator can
        // neither edit, reset, nor hard-delete an account holding a reserved role.
        requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "delete");
        if (isActiveSuperAdmin(id)) {
            requireAnotherActiveSuperAdmin();
        }
        requireRemovableProfile(id, "STUDENT");
        requireRemovableProfile(id, "LECTURER");
        // Capture the row first: a hard delete leaves nothing to describe afterwards,
        // and "which account was destroyed and by whom" is the whole point of the
        // record. A read failure is not fatal — the DELETE below reports not-found.
        Map<String, Object> before = null;
        try {
            before = find(id);
        } catch (RuntimeException ignored) {
            // Left null; the audit row then records the id and the action only.
        }
        // Revoke live sessions while the row still exists: after the hard
        // delete there is no account state left for the filter to consult.
        revokeSessions(id);
        try {
            int deleted = jdbc.update("DELETE FROM " + USER + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id));
            if (deleted == 0) throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "USER_IN_USE", "User is still referenced by an academic profile");
        }
        audit.recordDeletion(currentUserId, null, "USER", id, before);
    }

    private Map<String, Object> find(String id) {
        Map<String, Object> user = jdbc.queryForMap(
                "SELECT u.\"id\", u.\"email\", u.\"firstName\", u.\"lastName\", u.\"status\", u.\"phone\", u.\"createdAt\","
                        + " (SELECT sp.\"studentId\" FROM " + STUDENT + " sp WHERE sp.\"userId\" = u.\"id\" LIMIT 1) AS \"studentId\","
                        + " (SELECT lp.\"employeeId\" FROM " + LECTURER + " lp WHERE lp.\"userId\" = u.\"id\" LIMIT 1) AS \"employeeId\","
                        + " COALESCE((SELECT STRING_AGG(r.\"name\", ',') FROM " + USER_ROLE
                        + " ur JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\" WHERE ur.\"userId\" = u.\"id\"), '') AS roles,"
                        + " EXISTS(SELECT 1 FROM " + STUDENT + " sp WHERE sp.\"userId\" = u.\"id\") AS \"hasStudentProfile\","
                        + " EXISTS(SELECT 1 FROM " + LECTURER + " lp WHERE lp.\"userId\" = u.\"id\") AS \"hasLecturerProfile\""
                        + " FROM " + USER + " u WHERE u.\"id\" = :id",
                new MapSqlParameterSource("id", id));
        withRolesAsArray(user);
        return user;
    }

    /**
     * The admin contract matches AuthUserResponse: roles is a JSON array.
     * SQL aggregates the names into a comma-joined string, so it is split
     * back at the service boundary instead of every consumer compensating.
     */
    private static void withRolesAsArray(Map<String, Object> row) {
        Object roles = row.get("roles");
        if (roles == null || String.valueOf(roles).isEmpty()) {
            row.put("roles", List.of());
            return;
        }
        row.put("roles", java.util.Arrays.stream(String.valueOf(roles).split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList());
    }

    // Shared escaping/folding moved to common.util.LikePattern: the private copy is
    // retired so every admin list folds diacritics identically.

    private void assignRole(String userId, String roleName) {
        String roleId = roleId(roleName);
        jdbc.update("INSERT INTO " + USER_ROLE + " (\"id\", \"userId\", \"roleId\") SELECT :id, :userId, :roleId WHERE NOT EXISTS (SELECT 1 FROM " + USER_ROLE + " WHERE \"userId\" = :userId AND \"roleId\" = :roleId)", new MapSqlParameterSource().addValue("id", UUID.randomUUID().toString()).addValue("userId", userId).addValue("roleId", roleId));
    }

    private void replaceSystemRoles(String userId, Set<String> roleNames) {
        List<String> roleIds = roleNames.stream().map(this::roleId).toList();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("roleIds", roleIds);
        jdbc.update(
                "DELETE FROM " + USER_ROLE + " WHERE \"userId\" = :userId AND \"roleId\" IN"
                        + " (SELECT \"id\" FROM " + ROLE + " WHERE \"isSystem\" = TRUE AND \"id\" NOT IN (:roleIds))",
                params);
        for (String roleId : roleIds) {
            jdbc.update(
                    "INSERT INTO " + USER_ROLE + " (\"id\", \"userId\", \"roleId\")"
                            + " SELECT :id, :userId, :roleId WHERE NOT EXISTS"
                            + " (SELECT 1 FROM " + USER_ROLE + " WHERE \"userId\" = :userId AND \"roleId\" = :roleId)",
                    new MapSqlParameterSource().addValue("id", UUID.randomUUID().toString())
                            .addValue("userId", userId).addValue("roleId", roleId));
        }
    }

    /**
     * TRUONG_KHOA is a job title carried by a lecturer account, so the dean pair
     * — never a bare TRUONG_KHOA — is the desired system-role set for that role.
     * Every other request maps to itself.
     */
    private static Set<String> desiredSystemRoles(String roleName) {
        return ("TRUONG_KHOA".equals(roleName) || "TRUONG_BO_MON".equals(roleName))
                ? Set.of("LECTURER", roleName)
                : Set.of(roleName);
    }

    /** Dean and department-head pairs are bound to the lecturer profile. */
    private static boolean usesLecturerProfile(String roleName) {
        return "LECTURER".equals(roleName) || "TRUONG_KHOA".equals(roleName) || "TRUONG_BO_MON".equals(roleName);
    }

    private Set<String> systemRoleNames(String userId) {
        return new java.util.HashSet<>(jdbc.queryForList(
                "SELECT r.\"name\" FROM " + USER_ROLE + " ur JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\""
                        + " WHERE ur.\"userId\" = :userId AND r.\"isSystem\" = TRUE",
                new MapSqlParameterSource("userId", userId), String.class));
    }

    /**
     * The account-level half of the role ceiling.
     *
     * <p>{@link #guardRoleMutation} stops a plain administrator from <em>granting</em>
     * a reserved role. This stops it from reaching the same powers through an
     * account that already holds one: resetting a dean's password returns a working
     * credential for that dean, deleting the account removes the incumbent, and
     * disabling it removes the role from service — none of which requires ever
     * assigning a role, so the grant-side guard alone does not cover them.
     */
    public void requireSuperAdminForProtectedAccount(
            String userId, boolean canManageSuperAdmin, String action) {
        if (userId == null) {
            return;
        }
        // Serialize with role changes on the same account: without the shared
        // row lock a plain administrator's protected-target check can pass
        // against roles a concurrent update() is already replacing, and the
        // mutation then commits onto a freshly protected account.
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            lockUserRow(userId);
        }
        if (canManageSuperAdmin) {
            return;
        }
        for (String role : SUPER_ADMIN_ONLY_ROLES) {
            if (hasRole(userId, role)) {
                throw problem(
                        HttpStatus.FORBIDDEN,
                        "ROLE_ESCALATION",
                        "Only a super administrator can " + action
                                + " an account holding the " + role + " role");
            }
        }
        // A custom role can only be granted by a super administrator, so an
        // account already holding one is protected by the same ceiling:
        // resetting its password would hand out a credential for powers the
        // caller was never allowed to grant.
        if (hasCustomRole(userId)) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can " + action
                            + " an account holding a custom role");
        }
    }

    /**
     * Every mutation path that reads or writes an account's roles takes the
     * same user row lock first, so {@code update()} and the protected-target
     * checks in {@link #requireSuperAdminForProtectedAccount} serialize
     * instead of interleaving a grant between a check and its write.
     */
    private void lockUserRow(String userId) {
        authUsers.lockUserRow(userId);
    }

    /** Serialize removals of the last active SUPER_ADMIN across different target accounts. */
    private void requireAnotherActiveSuperAdmin() {
        authUsers.lockSuperAdminRole();
        if (authUsers.countActiveSuperAdmins() <= 1) {
            throw problem(
                    HttpStatus.CONFLICT,
                    "LAST_SUPER_ADMIN_REQUIRED",
                    "At least one active super administrator must remain");
        }
    }

    private boolean isActiveSuperAdmin(String userId) {
        return authUsers.isActiveSuperAdmin(userId);
    }

    private void guardRoleMutation(String userId, String roleName, boolean canManageSuperAdmin) {
        if (roleName == null || roleName.isBlank()) {
            throw problem(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "Role not found");
        }
        if (!SYSTEM_ROLES.contains(roleName) && !roleExists(roleName)) {
            throw problem(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "Role not found");
        }
        if (!canManageSuperAdmin && !SYSTEM_ROLES.contains(roleName)) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can assign custom roles");
        }
        if (!canManageSuperAdmin && "ADMIN".equals(roleName)) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can manage administrator accounts");
        }
        if (!canManageSuperAdmin
                && ("SUPER_ADMIN".equals(roleName)
                    || (userId != null && (hasRole(userId, "SUPER_ADMIN") || hasRole(userId, "ADMIN"))))) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can manage super administrator accounts");
        }
        // A dean account carries the thesis round lifecycle, so granting it is a
        // super-administrator action. Re-sending the role a user already holds is
        // not a grant, though: the admin edit form echoes the unchanged role back,
        // and refusing that would make every other field on the account unsavable.
        if (!canManageSuperAdmin
                && ("TRUONG_KHOA".equals(roleName) || "TRUONG_BO_MON".equals(roleName))
                && !(userId != null && hasRole(userId, roleName))) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can appoint a faculty or department head");
        }
        // Re-assigning a system role to a custom-role holder is still a take-over
        // of an account whose powers the caller may not grant.
        if (!canManageSuperAdmin && userId != null && hasCustomRole(userId)) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can change the role of an account holding a custom role");
        }
        // Removing a reserved role is the mirror of granting one, and it is worse
        // than it looks: no migration seeds a SUPER_ADMIN, so on a clean deployment
        // nobody can put the role back. A plain administrator sending the held role
        // as something else would strip it permanently, with no actor able to undo
        // it — so this refuses a CHANGE, not merely an assignment.
        if (!canManageSuperAdmin && userId != null) {
            for (String reserved : SUPER_ADMIN_ONLY_ROLES) {
                if (!reserved.equals(roleName) && hasRole(userId, reserved)) {
                    throw problem(
                            HttpStatus.FORBIDDEN,
                            "ROLE_ESCALATION",
                            "Only a super administrator can remove the " + reserved
                                    + " role from an account");
                }
            }
        }
    }

    /**
     * A faculty has one active dean. The faculty is the one the lecturer's
     * department sits in — the same binding the thesis governance scope uses —
     * and a lecturer who has no profile yet takes it from the department the
     * request is about to create the profile in.
     *
     * <p>The faculty row is locked first so two concurrent appointments in the
     * same faculty serialize instead of both passing the check. An inactive
     * incumbent does not block: only an account that can still act as dean does.
     */
    private void requireNoOtherDean(String userId, String requestedRole, Map<String, Object> input) {
        if (!"TRUONG_KHOA".equals(requestedRole) || (userId != null && hasRole(userId, requestedRole))) {
            return;
        }
        String facultyId = deanFacultyId(userId, input);
        if (facultyId == null) {
            return;
        }
        jdbc.queryForList(
                "SELECT \"id\" FROM academic.\"Faculty\" WHERE \"id\" = :facultyId FOR UPDATE",
                new MapSqlParameterSource("facultyId", facultyId), String.class);
        List<String> incumbents = jdbc.queryForList(
                "SELECT u.\"id\" FROM " + USER + " u"
                        + " JOIN " + USER_ROLE + " ur ON ur.\"userId\" = u.\"id\""
                        + " JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\" AND r.\"name\" = 'TRUONG_KHOA'"
                        + " JOIN " + LECTURER + " l ON l.\"userId\" = u.\"id\" AND l.\"isActive\" = TRUE"
                        + " JOIN academic.\"Department\" d ON d.\"id\" = l.\"departmentId\""
                        + " WHERE d.\"facultyId\" = :facultyId AND u.\"status\" = 'ACTIVE'"
                        + " AND u.\"id\" <> :userId",
                new MapSqlParameterSource().addValue("facultyId", facultyId)
                        .addValue("userId", userId == null ? "" : userId),
                String.class);
        if (!incumbents.isEmpty()) {
            throw problem(
                    HttpStatus.CONFLICT,
                    "DEAN_ALREADY_ASSIGNED",
                    "This faculty already has an active dean; remove the dean role from the"
                            + " current holder before appointing another");
        }
    }

    private String deanFacultyId(String userId, Map<String, Object> input) {
        if (userId != null) {
            List<String> held = jdbc.queryForList(
                    "SELECT d.\"facultyId\" FROM " + LECTURER + " l"
                            + " JOIN academic.\"Department\" d ON d.\"id\" = l.\"departmentId\""
                            + " WHERE l.\"userId\" = :userId",
                    new MapSqlParameterSource("userId", userId), String.class);
            if (!held.isEmpty()) {
                return held.get(0);
            }
        }
        Object requested = input.get("departmentId");
        if (requested == null || requested.toString().isBlank()) {
            return null;
        }
        List<String> rows = jdbc.queryForList(
                "SELECT \"facultyId\" FROM academic.\"Department\" WHERE \"id\" = :departmentId",
                new MapSqlParameterSource("departmentId", requested.toString().trim()), String.class);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean roleExists(String roleName) {
        return !jdbc.queryForList(
                "SELECT 1 FROM " + ROLE + " WHERE \"name\" = :roleName",
                new MapSqlParameterSource("roleName", roleName),
                Integer.class).isEmpty();
    }

    private boolean hasRole(String userId, String roleName) {
        return !jdbc.queryForList(
                "SELECT 1 FROM " + USER_ROLE + " ur JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\""
                        + " WHERE ur.\"userId\" = :userId AND r.\"name\" = :roleName",
                new MapSqlParameterSource().addValue("userId", userId).addValue("roleName", roleName),
                Integer.class).isEmpty();
    }

    private boolean hasCustomRole(String userId) {
        return !jdbc.queryForList(
                "SELECT 1 FROM " + USER_ROLE + " ur JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\""
                        + " WHERE ur.\"userId\" = :userId AND r.\"isSystem\" = FALSE",
                new MapSqlParameterSource("userId", userId),
                Integer.class).isEmpty();
    }

    private void removeObsoleteProfiles(String userId, Set<String> desiredRoles) {
        try {
            if (!desiredRoles.contains("STUDENT")) {
                // The auth table is a view over academic."Student": deleting it
                // cascades attendance, conduct and credit-limit history while
                // thesis memberships silently orphan. A profile that already
                // carries academic records is deactivated, never converted
                // destructively.
                requireRemovableProfile(userId, "STUDENT");
                jdbc.update(
                        "DELETE FROM " + STUDENT + " WHERE \"userId\" = :userId",
                        new MapSqlParameterSource("userId", userId));
            }
            // The dean pair contains LECTURER, so promoting or demoting a
            // faculty head never deletes the shared lecturer profile.
            if (!desiredRoles.contains("LECTURER")) {
                requireRemovableProfile(userId, "LECTURER");
                jdbc.update(
                        "DELETE FROM " + LECTURER + " WHERE \"userId\" = :userId",
                        new MapSqlParameterSource("userId", userId));
            }
        } catch (DataIntegrityViolationException exception) {
            throw problem(
                    HttpStatus.CONFLICT,
                    "PROFILE_IN_USE",
                    "The existing academic profile is still referenced");
        }
    }

    /**
     * Deleting a profiled account's row through the campuscore_auth view hits
     * the academic base table, where conduct, attendance and credit-limit
     * rows are ON DELETE CASCADE and thesis memberships carry no key at all.
     * A conversion that would erase or orphan that history is refused; the
     * admin can still deactivate the account instead.
     */
    private void requireRemovableProfile(String userId, String roleName) {
        String table = "STUDENT".equals(roleName) ? STUDENT : LECTURER;
        List<String> profileIds = jdbc.queryForList(
                "SELECT \"id\" FROM " + table + " WHERE \"userId\" = :userId FOR UPDATE",
                new MapSqlParameterSource("userId", userId), String.class);
        if (profileIds.isEmpty()) {
            return;
        }
        String profileId = profileIds.getFirst();
        long references = 0;
        if ("STUDENT".equals(roleName)) {
            references += countReferences("academic.\"Enrollment\"", "\"studentId\"", profileId);
            references += countReferences("academic.\"Attendance\"", "\"studentId\"", profileId);
            references += countReferences("academic.\"RegistrationSlip\"", "\"studentId\"", profileId);
            references += countReferences("academic.\"CreditLimitApplication\"", "\"studentId\"", profileId);
            references += countReferences("academic.conduct_semester_score", "student_id", profileId);
            references += countReferences("academic.conduct_activity", "student_id", profileId);
            references += countReferences("academic.\"EnrollmentEvent\"", "\"studentId\"", profileId);
            references += countReferences("thesis.thesis_group_member", "student_id", profileId);
            references += countReferences("thesis.thesis_group", "leader_student_id", profileId);
            references += countReferences("thesis.thesis_group_report", "submitted_by", profileId);
        } else {
            references += countReferences("academic.\"Section\"", "\"lecturerId\"", profileId);
            references += countReferences("thesis.thesis_council_member", "lecturer_id", profileId);
            references += countReferences("thesis.thesis_topic_supervisor", "lecturer_id", profileId);
            references += countReferences("thesis.thesis_topic_score", "lecturer_id", profileId);
            references += countReferences("thesis.thesis_topic", "gvpb_lecturer_id", profileId);
            references += countReferences("engagement.\"Announcement\"", "\"lecturerId\"", profileId);
        }
        if (references > 0) {
            throw problem(
                    HttpStatus.CONFLICT,
                    "PROFILE_HAS_HISTORY",
                    "The existing profile has academic history; deactivate the account instead of converting its role");
        }
    }

    private long countReferences(String table, String column, String profileId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :profileId",
                new MapSqlParameterSource("profileId", profileId),
                Long.class);
        return count == null ? 0 : count;
    }

    private String roleId(String roleName) {
        return jdbc.queryForList(
                        "SELECT \"id\" FROM " + ROLE + " WHERE \"name\" = :name",
                        new MapSqlParameterSource("name", roleName.toUpperCase(java.util.Locale.ROOT)),
                        String.class)
                .stream()
                .findFirst()
                .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "Role not found"));
    }

    /** Complete an already-held role's missing profile without converting any roles. */
    private void completeMissingProfiles(String userId, Map<String, Object> input) {
        Set<String> heldRoles = systemRoleNames(userId);
        boolean studentFields = input.containsKey("studentId") || input.containsKey("curriculumId")
                || input.containsKey("year");
        boolean lecturerFields = input.containsKey("employeeId") || input.containsKey("departmentId");
        if (studentFields && heldRoles.contains("STUDENT") && !profileExists(userId, "STUDENT")) {
            preflightProfileConflicts("STUDENT", input);
            ensureProfile(userId, "STUDENT", input);
        }
        if (lecturerFields && heldRoles.stream().anyMatch(AdminUserMutationService::usesLecturerProfile)
                && !profileExists(userId, "LECTURER")) {
            preflightProfileConflicts("LECTURER", input);
            ensureProfile(userId, "LECTURER", input);
        }
    }

    private void ensureProfile(String userId, String roleName, Map<String, Object> input) {
        if (profileExists(userId, roleName)) {
            return;
        }
        String profileId = UUID.randomUUID().toString();
        if ("STUDENT".equals(roleName)) {
            // Office-issued accounts carry official identity: no generated
            // placeholder IDs and no demo curriculum fallbacks.
            String studentNumber = required(input, "studentId");
            String curriculumId = required(input, "curriculumId");
            int year;
            try {
                year = Integer.parseInt(required(input, "year"));
            } catch (NumberFormatException exception) {
                throw problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "year must be a number");
            }
            if (year < 1) {
                throw problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "year must be a positive integer");
            }
            requireActiveReference(ACADEMIC_CURRICULUM, curriculumId);
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
                            .addValue("curriculumId", curriculumId)
                            .addValue("year", year));
        } else if (usesLecturerProfile(roleName)) {
            String employeeId = required(input, "employeeId");
            String departmentId = required(input, "departmentId");
            requireActiveReference(ACADEMIC_DEPARTMENT, departmentId);
            jdbc.update(
                    "INSERT INTO " + LECTURER
                            + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                            + " VALUES (:id, :userId, :departmentId, :employeeId, TRUE)",
                    new MapSqlParameterSource()
                            .addValue("id", profileId)
                            .addValue("userId", userId)
                            .addValue("departmentId", departmentId)
                            .addValue("employeeId", employeeId));
        }
    }

    private void requireActiveReference(String table, String id) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE \"id\" = :id AND \"isActive\" = TRUE",
                new MapSqlParameterSource("id", id),
                Long.class);
        if (count == null || count == 0) {
            throw problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Referenced academic record is missing or inactive");
        }
    }

    private boolean profileExists(String userId, String roleName) {
        String table = "STUDENT".equals(roleName) ? STUDENT : usesLecturerProfile(roleName) ? LECTURER : null;
        if (table == null) {
            return true;
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE \"userId\" = :userId",
                new MapSqlParameterSource("userId", userId),
                Long.class);
        return count != null && count > 0;
    }

    /** 16-char one-time credential from an ambiguity-free alphabet; never logged. */
    private String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder(16);
        for (int index = 0; index < 16; index++) {
            builder.append(TEMP_PASSWORD_ALPHABET.charAt(random.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static Map<String, Object> withTemporaryPassword(Map<String, Object> user, String temporaryPassword) {
        Map<String, Object> response = new LinkedHashMap<>(user);
        response.put("temporaryPassword", temporaryPassword);
        return response;
    }

    /** Deterministic duplicate detection; the DB constraint is the backstop. */
    private void preflightConflicts(String email, String role, Map<String, Object> input, String excludeUserId) {
        if (email != null) {
            Long emailCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + USER + " WHERE LOWER(\"email\") = :email"
                            + " AND (:id IS NULL OR \"id\" <> CAST(:id AS VARCHAR))",
                    new MapSqlParameterSource().addValue("email", email)
                            .addValue("id", excludeUserId, Types.VARCHAR),
                    Long.class);
            if (emailCount != null && emailCount > 0) {
                throw problem(HttpStatus.CONFLICT, "EMAIL_EXISTS", "This email is already registered");
            }
        }
        preflightProfileConflicts(role, input);
    }

    /** Official-identifier duplicate check for the profile class a role needs. */
    private void preflightProfileConflicts(String role, Map<String, Object> input) {
        if ("STUDENT".equals(role)) {
            String studentId = text(input, "studentId", null);
            if (studentId != null) {
                Long count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + STUDENT + " WHERE \"studentId\" = :studentId",
                        new MapSqlParameterSource("studentId", studentId),
                        Long.class);
                if (count != null && count > 0) {
                    throw problem(HttpStatus.CONFLICT, "STUDENT_ID_EXISTS", "This student ID already exists");
                }
            }
        }
        if (usesLecturerProfile(role)) {
            String employeeId = text(input, "employeeId", null);
            if (employeeId != null) {
                Long count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + LECTURER + " WHERE \"employeeId\" = :employeeId",
                        new MapSqlParameterSource("employeeId", employeeId),
                        Long.class);
                if (count != null && count > 0) {
                    throw problem(HttpStatus.CONFLICT, "EMPLOYEE_ID_EXISTS", "This employee ID already exists");
                }
            }
        }
    }

    private DomainException conflict(DataIntegrityViolationException exception) {
        String detail = String.valueOf(exception.getMessage()).toLowerCase(java.util.Locale.ROOT);
        if (detail.contains("email")) {
            return problem(HttpStatus.CONFLICT, "EMAIL_EXISTS", "This email is already registered");
        }
        if (detail.contains("studentid") || detail.contains("student_id")) {
            return problem(HttpStatus.CONFLICT, "STUDENT_ID_EXISTS", "This student ID already exists");
        }
        if (detail.contains("employeeid") || detail.contains("employee_id")) {
            return problem(HttpStatus.CONFLICT, "EMPLOYEE_ID_EXISTS", "This employee ID already exists");
        }
        return problem(HttpStatus.CONFLICT, "CONFLICT", "Operation conflicted with existing database state");
    }

    private void revokeSessions(String userId) {
        authUsers.deleteAllRefreshSessions(userId);
        authUsers.clearUserRefreshToken(userId);
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
