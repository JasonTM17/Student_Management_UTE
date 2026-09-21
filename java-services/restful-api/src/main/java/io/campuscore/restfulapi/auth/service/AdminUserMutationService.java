package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.audit.AdminAuditRecorder;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.common.LikePattern;
import io.campuscore.restfulapi.web.DomainException;
import java.security.SecureRandom;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final String USER = "\"campuscore_auth\".\"User\"";
    private static final String ROLE = "\"campuscore_auth\".\"Role\"";
    private static final String USER_ROLE = "\"campuscore_auth\".\"UserRole\"";
    private static final String STUDENT = "\"campuscore_auth\".\"Student\"";
    private static final String LECTURER = "\"campuscore_auth\".\"Lecturer\"";
    private static final Set<String> SYSTEM_ROLES = Set.of("STUDENT", "LECTURER", "ADMIN", "TRUONG_KHOA", "SUPER_ADMIN");
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
    private static final List<String> SUPER_ADMIN_ONLY_ROLES = List.of("ADMIN", "SUPER_ADMIN", "TRUONG_KHOA");
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
        if (page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("page and limit are invalid");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", (long) (page - 1) * limit)
                .addValue("status", status, Types.VARCHAR)
                .addValue("search", LikePattern.contains(search), Types.VARCHAR)
                .addValue("foldFrom", LikePattern.FOLD_FROM)
                .addValue("foldTo", LikePattern.FOLD_TO);
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT u.\"id\", u.\"email\", u.\"firstName\", u.\"lastName\", u.\"status\", u.\"phone\", u.\"createdAt\","
                        + " COALESCE((SELECT STRING_AGG(r.\"name\", ',') FROM " + USER_ROLE + " ur JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\" WHERE ur.\"userId\" = u.\"id\"), '') AS roles"
                        // PostgreSQL cannot infer the type of an unbound NULL in an IS NULL
                        // predicate. Cast optional filters so the first request from the admin
                        // console (which intentionally sends neither filter) remains valid.
                        // TRANSLATE folds Vietnamese diacritics on the column side so the
                        // operator's unaccented typing still matches the stored spelling.
                        + " FROM " + USER + " u WHERE (CAST(:status AS VARCHAR) IS NULL OR u.\"status\" = CAST(:status AS VARCHAR))"
                        + " AND (CAST(:search AS VARCHAR) IS NULL OR TRANSLATE(LOWER(u.\"email\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(u.\"firstName\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(u.\"lastName\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\')"
                        + " ORDER BY u.\"createdAt\" DESC LIMIT :limit OFFSET :offset",
                params);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + USER + " u WHERE (CAST(:status AS VARCHAR) IS NULL OR u.\"status\" = CAST(:status AS VARCHAR))"
                        + " AND (CAST(:search AS VARCHAR) IS NULL OR TRANSLATE(LOWER(u.\"email\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(u.\"firstName\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(u.\"lastName\"), :foldFrom, :foldTo) LIKE CAST(:search AS VARCHAR) ESCAPE '\\')",
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
        assignRole(id, role);
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
        int updated = jdbc.update(
                "UPDATE " + USER + " SET \"password\" = :password, \"mustChangePassword\" = TRUE,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource().addValue("id", id)
                        .addValue("password", passwordEncoder.encode(temporaryPassword)));
        if (updated == 0) throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        revokeSessions(id);
        // Recorded as an event, never as state: the new credential must not reach
        // the audit table, and a password reset is exactly the operation whose
        // author an investigation needs to know.
        audit.record(currentUserId, null, "PASSWORD_RESET", "USER", id,
                "Issued a one-time password for user " + id);
        return withTemporaryPassword(find(id), temporaryPassword);
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
        // A blank role means "the caller did not send one", not "make this account a
        // student". `text()` would substitute its fallback for the blank, so a client
        // that echoes an empty role back would silently strip a dean's role.
        Object rawRole = input.get("role");
        String requestedRole = rawRole == null || rawRole.toString().isBlank()
                ? null
                : text(input, "role", "STUDENT").toUpperCase(java.util.Locale.ROOT);
        if (requestedRole != null) {
            guardRoleMutation(id, requestedRole, canManageSuperAdmin);
        } else {
            requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "manage");
        }
        if (currentUserId != null && currentUserId.equals(id)) {
            if (requestedRole != null && !hasRole(id, requestedRole)) {
                throw problem(
                        HttpStatus.BAD_REQUEST,
                        "SELF_ROLE_CHANGE_NOT_ALLOWED",
                        "You cannot change your own system role");
            }
            String status = input.get("status") == null ? null : text(input, "status", "");
            if (status != null && !status.isBlank() && !"ACTIVE".equalsIgnoreCase(status)) {
                throw problem(
                        HttpStatus.BAD_REQUEST,
                        "SELF_DEACTIVATION_NOT_ALLOWED",
                        "You cannot deactivate or lock your own account");
            }
        }
        if (!canManageSuperAdmin && requestedRole == null && (hasRole(id, "ADMIN") || hasRole(id, "SUPER_ADMIN"))) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can manage administrator accounts");
        }
        String requestedStatus = input.get("status") == null ? null : text(input, "status", "");
        // Disabling a protected account is the same refusal as deleting it: the role
        // stops functioning either way. Re-sending an unchanged role is not covered
        // by that guard, so this closes the gap the grant-side exemption leaves.
        if (requestedStatus != null && !requestedStatus.isBlank()
                && !"ACTIVE".equalsIgnoreCase(requestedStatus)) {
            requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "disable");
        }
        if (requestedStatus != null && !requestedStatus.isBlank()
                && !ACCOUNT_STATUSES.contains(requestedStatus.toUpperCase(java.util.Locale.ROOT))) {
            // Free-form status values would silently break the account-state
            // filter and the console badges; only the lifecycle set is valid.
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ACCOUNT_STATUS",
                    "Unknown account status");
        }
        String previousStatus = jdbc.queryForObject(
                "SELECT \"status\" FROM " + USER + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                String.class);
        int updated = jdbc.update(
                "UPDATE " + USER + " SET \"firstName\" = COALESCE(:firstName, \"firstName\"),"
                        + " \"lastName\" = COALESCE(:lastName, \"lastName\"), \"phone\" = COALESCE(:phone, \"phone\"),"
                        + " \"status\" = COALESCE(:status, \"status\"), \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("firstName", input.get("firstName"))
                        .addValue("lastName", input.get("lastName")).addValue("phone", input.get("phone"))
                        .addValue("status", input.get("status")));
        if (updated == 0) throw problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        String newStatus = input.get("status") == null ? null : text(input, "status", "");
        if (previousStatus != null && "ACTIVE".equalsIgnoreCase(previousStatus)
                && newStatus != null && !newStatus.isBlank() && !"ACTIVE".equalsIgnoreCase(newStatus)) {
            // Deactivation must cut live access immediately: drop refresh
            // sessions so the stale token cannot rotate, and the account-state
            // filter rejects remaining access tokens on the next request.
            revokeSessions(id);
        }
        if (requestedRole != null) {
            replaceSystemRole(id, requestedRole);
            removeObsoleteProfiles(id, requestedRole);
            ensureProfile(id, requestedRole, input);
        }
        // Records the transition, not the payload: the request map can carry a
        // credential, and a role or status change is what an investigation looks for.
        audit.record(currentUserId, null, requestedRole != null ? "ROLE_OR_DETAILS_CHANGED" : "UPDATED",
                "USER", id,
                "Updated user " + id
                        + (requestedRole != null ? " to role " + requestedRole : "")
                        + (newStatus != null && !newStatus.isBlank() ? " status " + newStatus : ""),
                Map.of("status", String.valueOf(previousStatus), "role", String.valueOf(requestedRole)),
                Map.of("status", String.valueOf(newStatus), "role", String.valueOf(requestedRole)));
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
        // Same ceiling as update()/resetPassword(): a plain administrator can
        // neither edit, reset, nor hard-delete an account holding a reserved role.
        requireSuperAdminForProtectedAccount(id, canManageSuperAdmin, "delete");
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
                        + " COALESCE((SELECT STRING_AGG(r.\"name\", ',') FROM " + USER_ROLE
                        + " ur JOIN " + ROLE + " r ON r.\"id\" = ur.\"roleId\" WHERE ur.\"userId\" = u.\"id\"), '') AS roles"
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

    // Shared escaping/folding moved to common.LikePattern: the private copy is
    // retired so every admin list folds diacritics identically.

    private void assignRole(String userId, String roleName) {
        String roleId = roleId(roleName);
        jdbc.update("INSERT INTO " + USER_ROLE + " (\"id\", \"userId\", \"roleId\") SELECT :id, :userId, :roleId WHERE NOT EXISTS (SELECT 1 FROM " + USER_ROLE + " WHERE \"userId\" = :userId AND \"roleId\" = :roleId)", new MapSqlParameterSource().addValue("id", UUID.randomUUID().toString()).addValue("userId", userId).addValue("roleId", roleId));
    }

    private void replaceSystemRole(String userId, String roleName) {
        String roleId = roleId(roleName);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("roleId", roleId);
        jdbc.update(
                "DELETE FROM " + USER_ROLE + " WHERE \"userId\" = :userId AND \"roleId\" IN"
                        + " (SELECT \"id\" FROM " + ROLE + " WHERE \"isSystem\" = TRUE AND \"id\" <> :roleId)",
                params);
        jdbc.update(
                "INSERT INTO " + USER_ROLE + " (\"id\", \"userId\", \"roleId\")"
                        + " SELECT :id, :userId, :roleId WHERE NOT EXISTS"
                        + " (SELECT 1 FROM " + USER_ROLE + " WHERE \"userId\" = :userId AND \"roleId\" = :roleId)",
                params.addValue("id", UUID.randomUUID().toString()));
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
    private void requireSuperAdminForProtectedAccount(
            String userId, boolean canManageSuperAdmin, String action) {
        if (canManageSuperAdmin || userId == null) {
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
                && "TRUONG_KHOA".equals(roleName)
                && !(userId != null && hasRole(userId, "TRUONG_KHOA"))) {
            throw problem(
                    HttpStatus.FORBIDDEN,
                    "ROLE_ESCALATION",
                    "Only a super administrator can assign the faculty head role");
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

    private void removeObsoleteProfiles(String userId, String roleName) {
        try {
            if (!"STUDENT".equals(roleName)) {
                jdbc.update(
                        "DELETE FROM " + STUDENT + " WHERE \"userId\" = :userId",
                        new MapSqlParameterSource("userId", userId));
            }
            if (!"LECTURER".equals(roleName)) {
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

    private String roleId(String roleName) {
        return jdbc.queryForList(
                        "SELECT \"id\" FROM " + ROLE + " WHERE \"name\" = :name",
                        new MapSqlParameterSource("name", roleName.toUpperCase(java.util.Locale.ROOT)),
                        String.class)
                .stream()
                .findFirst()
                .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "Role not found"));
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
        } else if ("LECTURER".equals(roleName)) {
            String employeeId = required(input, "employeeId");
            String departmentId = required(input, "departmentId");
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
        Long emailCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + USER + " WHERE LOWER(\"email\") = :email"
                        + " AND (:id IS NULL OR \"id\" <> CAST(:id AS VARCHAR))",
                new MapSqlParameterSource().addValue("email", email)
                        .addValue("id", excludeUserId, Types.VARCHAR),
                Long.class);
        if (emailCount != null && emailCount > 0) {
            throw problem(HttpStatus.CONFLICT, "EMAIL_EXISTS", "This email is already registered");
        }
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
        if ("LECTURER".equals(role)) {
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
