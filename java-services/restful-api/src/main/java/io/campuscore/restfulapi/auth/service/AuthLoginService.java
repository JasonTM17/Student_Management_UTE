package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository.AuthUserRecord;
import io.campuscore.restfulapi.auth.dto.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.dto.AuthDtos.LoginResponse;
import io.campuscore.restfulapi.auth.dto.AuthDtos.UpdateProfileRequest;
import io.campuscore.restfulapi.exception.AppException;
import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.security.AuthPrincipal;
import io.campuscore.restfulapi.security.AuthTokenService;
import io.campuscore.restfulapi.security.AuthTokenService.IssuedAccessToken;
import io.campuscore.restfulapi.security.AuthTokenService.IssuedRefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Authentication and refresh-session service for the Java API. */
@Service
@Profile("persistence")
public class AuthLoginService {

    private static final Logger reuseLogger =
            LoggerFactory.getLogger(AuthLoginService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    /**
     * Same-origin documents share one refresh cookie, so a queued second
     * refresh can still present the token a sibling just rotated out — most
     * often when the rotating response never reached the jar. Replays of that
     * immediately-previous generation are tolerated for this short overlap;
     * older replays keep the stolen-token semantics (family revocation).
     * The overlap covers only the lost-response ordering: the delayed-
     * duplicate ordering (second-processed request rotates and delivers its
     * token, then the delayed duplicate recovers over it) still revokes on
     * the superseded holder's next refresh — see the V138 migration header
     * and docs/security/authorization-model.md.
     */
    private static final Duration REFRESH_ROTATION_OVERLAP = Duration.ofSeconds(30);
    // "rotatedAt" is stamped by this same application clock at rotation time,
    // so a future value should not occur in a single-instance deployment; the
    // tolerance only covers small cross-instance skew and a wildly future
    // timestamp still fails closed. Effective acceptance bound for a replay:
    // REFRESH_ROTATION_OVERLAP + CLOCK_SKEW_TOLERANCE past the rotation.
    private static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofSeconds(2);

    /**
     * Timing-oracle mitigation: a bcrypt comparison against a fixed hash costs
     * the same whether the account exists or not, so unknown-email logins no
     * longer return measurably faster than known-email ones.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5s5dOH.td9LVsNWxfuHP2QsRZMPwq";

    private final AuthUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokens;
    private final TwoFactorService twoFactor;
    private final AdminAuditRecorder audit;
    private final Clock clock;

    @Autowired
    public AuthLoginService(
            AuthUserRepository users,
            PasswordEncoder passwordEncoder,
            AuthTokenService tokens,
            TwoFactorService twoFactor,
            AdminAuditRecorder audit) {
        this(users, passwordEncoder, tokens, twoFactor, audit, Clock.systemUTC());
    }

    AuthLoginService(
            AuthUserRepository users,
            PasswordEncoder passwordEncoder,
            AuthTokenService tokens,
            TwoFactorService twoFactor,
            AdminAuditRecorder audit,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.twoFactor = twoFactor;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResult login(String email, String password, String ipAddress, String userAgent) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || !isValidEmail(normalizedEmail)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        Optional<AuthUserRecord> found = users.findByEmail(normalizedEmail);
        if (found.isEmpty()) {
            // Burn one bcrypt comparison so the unknown-email path costs the
            // same as the known-email one (account-existence timing oracle).
            passwordEncoder.matches(password, DUMMY_PASSWORD_HASH);
            throw new BadCredentialsException("Invalid credentials");
        }
        // Credential verification and session issuance share the same User
        // lock as password replacement; never mint a current revision after
        // verifying a password from a snapshot taken before that replacement.
        users.lockUserRow(found.get().id());
        AuthUserRecord user = users.findById(found.get().id())
                .filter(current -> normalizedEmail.equals(current.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Instant now = clock.instant();

        boolean locked = user.lockedUntil() != null && user.lockedUntil().isAfter(now);
        // The failed-attempt lock is account-keyed, which made it a permanent
        // DoS lever for any known email. A caller connecting from an address
        // that already holds a live session for this account is treated as
        // the legitimate owner and bypasses the lock — TCP source addresses
        // cannot be forged, so attacker IPs (which hold no session) remain
        // fully locked. The password check below still applies either way.
        if (locked && ipAddress != null && users.hasRecentSessionFromIp(user.id(), ipAddress, now)) {
            locked = false;
        }

        if (!"ACTIVE".equals(user.status()) || locked) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            int nextAttemptCount = user.failedLoginAttempts() + 1;
            Instant lockedUntil = nextAttemptCount >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : null;
            users.recordFailedLogin(user.id(), nextAttemptCount, lockedUntil);
            throw new BadCredentialsException("Invalid credentials");
        }

        users.recordSuccessfulLogin(user.id(), now);
        // Opt-in second factor: accounts with the switch on never receive
        // tokens here; they get an emailed LOGIN challenge instead. The
        // single-step response shape is untouched for everyone else.
        if (users.isTwoFactorEnabled(user.id())) {
            String challengeId = twoFactor.startLoginChallenge(user);
            return LoginResult.challenge(challengeId, TwoFactorService.maskEmail(user.email()));
        }
        return issueSession(user, ipAddress, userAgent);
    }

    /**
     * Second step of a two-factor login: validates the emailed code, re-runs
     * the same account-state gate as a password login, then issues the same
     * session result (tokens, cookies handled by the controller). Structured
     * two-factor rejections (wrong code, locked, expired) are committed, not
     * rolled back, so the per-challenge attempt counter survives.
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, AppException.class})
    public LoginResult completeLogin(String challengeId, String code, String ipAddress, String userAgent) {
        String userId = twoFactor.loginChallengeUserId(challengeId);
        // Resolve without a Challenge lock, then keep the global order User
        // before Challenge/Session. Consumption re-reads after this lock so a
        // credential replacement can invalidate a waiting LOGIN challenge.
        users.lockUserRow(userId);
        twoFactor.consumeLoginChallenge(challengeId, code);
        AuthUserRecord user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Instant now = clock.instant();
        boolean locked = user.lockedUntil() != null && user.lockedUntil().isAfter(now);
        if (locked && ipAddress != null && users.hasRecentSessionFromIp(user.id(), ipAddress, now)) {
            locked = false;
        }
        if (!"ACTIVE".equals(user.status()) || locked) {
            throw new BadCredentialsException("Invalid credentials");
        }
        users.recordSuccessfulLogin(user.id(), now);
        return issueSession(user, ipAddress, userAgent);
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResult refresh(String refreshTokenValue, String ipAddress, String userAgent) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is required");
        }

        Jwt decoded = decodeRefresh(refreshTokenValue);
        // Lock the User row before the Session row (User -> Session is the
        // global order): taking the session FOR UPDATE first deadlocks against
        // admin mutations that lock the user and then delete sessions.
        users.lockUserRow(decoded.getSubject());
        Instant now = clock.instant();
        String refreshTokenHash = hash(refreshTokenValue);
        Optional<AuthUserRepository.ActiveRefreshSession> sessionRow =
                users.findByActiveRefreshSession(refreshTokenHash, now);
        if (sessionRow.isEmpty()) {
            return recoverRacedRefresh(decoded, refreshTokenHash, now, ipAddress, userAgent);
        }

        AuthUserRecord user = sessionRow.get().user();
        if (!user.id().equals(decoded.getSubject()) || !"ACTIVE".equals(user.status())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        AuthPrincipal principal = principal(user);
        IssuedAccessToken accessToken = tokens.issueAccessToken(principal);
        IssuedRefreshToken nextRefreshToken = tokens.issueRefreshToken(principal, sessionRow.get().sessionId());
        users.rotateRefreshSession(
                user.id(),
                refreshTokenHash,
                hash(nextRefreshToken.refreshToken()),
                now,
                ipAddress,
                userAgent,
                nextRefreshToken.expiresAt());

        return LoginResult.session(
                new LoginResponse(user.toResponse(), accessToken.accessToken(), nextRefreshToken.refreshToken()),
                accessToken.expiresAt(),
                nextRefreshToken.expiresAt());
    }

    /**
     * No session row holds the presented hash. Two cases remain: the hash is
     * the immediately-previous generation of a live row — the shared-cookie
     * race where a sibling document's rotation committed but its response
     * never reached the jar (navigation mid-flight, transport timeout, a
     * dropped network answer) — which recovers inside the short overlap
     * window; or it is a genuinely stale or foreign token, the stolen-token
     * signal that still fails the whole family closed.
     */
    private LoginResult recoverRacedRefresh(
            Jwt decoded, String refreshTokenHash, Instant now, String ipAddress, String userAgent) {
        String subject = decoded.getSubject();
        Optional<AuthUserRepository.RacedRotation> raced =
                users.findByPreviousRefreshSession(refreshTokenHash, now)
                        .filter(candidate -> withinRotationOverlap(candidate.rotatedAt(), now));
        if (raced.isEmpty()) {
            // Two genuinely different cases land here. When the token names
            // its session ("sid") and that row is already gone, the session
            // was revoked administratively — a password change, single-device
            // logout, a two-factor posture change, or an admin. That is not a
            // theft signal and there is no family left to protect, so the
            // replay fails quietly instead of wiping the caller's other
            // sessions. A superseded token whose row still exists — and any
            // token minted before "sid" existed — keeps the stolen-token
            // semantics and fails the whole family closed.
            String sessionId = decoded.getClaimAsString("sid");
            if (sessionId != null && !users.sessionExistsForUser(subject, sessionId, now)) {
                throw new BadCredentialsException("Invalid refresh token");
            }
            users.findById(subject).ifPresent(owner -> {
                users.deleteAllRefreshSessions(owner.id());
                reuseLogger.warn(
                        "REFRESH_TOKEN_REUSE subject={} ip={} userAgent={}",
                        subject,
                        ipAddress,
                        userAgent);
            });
            throw new BadCredentialsException("Invalid refresh token");
        }

        AuthUserRepository.RacedRotation candidate = raced.get();
        AuthUserRecord user = candidate.user();
        if (!user.id().equals(subject) || !"ACTIVE".equals(user.status())) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        // Same event class as REFRESH_TOKEN_REUSE — a superseded credential
        // was replayed and accepted inside the window. Because detection is
        // deferred there, this line is the only trace of a successful
        // in-window theft, so it stays at warn level. Never log the token
        // or its hash.
        reuseLogger.warn(
                "REFRESH_ROTATION_OVERLAP subject={} session={} ip={} userAgent={}",
                subject,
                candidate.sessionId(),
                ipAddress,
                userAgent);
        AuthPrincipal principal = principal(user);
        IssuedAccessToken accessToken = tokens.issueAccessToken(principal);
        IssuedRefreshToken nextRefreshToken = tokens.issueRefreshToken(principal, candidate.sessionId());
        users.rotateRacedRefreshSession(
                user.id(),
                candidate.sessionId(),
                hash(nextRefreshToken.refreshToken()),
                ipAddress,
                userAgent,
                nextRefreshToken.expiresAt());
        return LoginResult.session(
                new LoginResponse(user.toResponse(), accessToken.accessToken(), nextRefreshToken.refreshToken()),
                accessToken.expiresAt(),
                nextRefreshToken.expiresAt());
    }

    private static boolean withinRotationOverlap(Instant rotatedAt, Instant now) {
        return rotatedAt != null
                && !rotatedAt.isBefore(now.minus(REFRESH_ROTATION_OVERLAP))
                && !rotatedAt.isAfter(now.plus(CLOCK_SKEW_TOLERANCE));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(String userId) {
        return requireActiveUser(userId).toResponse();
    }

    @Transactional
    public AuthUserResponse updateProfile(String userId, UpdateProfileRequest request) {
        AuthUserRecord user = requireActiveUser(userId);
        // Names and date of birth are school-managed: identity fields in the
        // payload are ignored so a user cannot rewrite official records. The
        // fields remain on the request record only so older clients still
        // deserialize successfully.
        users.updateProfile(
                user.id(),
                user.firstName(),
                user.lastName(),
                request != null && request.phone() != null ? request.phone() : user.phone(),
                user.dateOfBirth(),
                request != null && request.address() != null ? request.address() : user.address(),
                request != null && request.avatar() != null ? normalizeAvatar(request.avatar()) : user.avatar());
        return requireActiveUser(user.id()).toResponse();
    }

    /**
     * Inline image data URLs are accepted, and so are https image URLs: the
     * seeded profiles carry unsplash-style avatar URLs, and a GET→PUT
     * round-trip of the profile used to fail its own validator ("avatar must
     * be a png, jpeg or webp data URL") because the accepted-on-write shape
     * was narrower than the served shape (round-2 sweep write-3).
     */
    private static String normalizeAvatar(String avatar) {
        String trimmed = avatar.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String lowered = trimmed.toLowerCase(java.util.Locale.ROOT);
        boolean inlineImage = lowered.startsWith("data:image/png;")
                || lowered.startsWith("data:image/jpeg;")
                || lowered.startsWith("data:image/webp;");
        boolean httpsImage = lowered.startsWith("https://")
                && (lowered.endsWith(".png") || lowered.endsWith(".jpg") || lowered.endsWith(".jpeg")
                        || lowered.endsWith(".webp") || lowered.contains("unsplash")
                        || lowered.contains("/avatar"));
        if (!inlineImage && !httpsImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "avatar must be a png, jpeg or webp data URL or an https image URL");
        }
        return trimmed;
    }

    /**
     * Rotates the account password and revokes the other sessions. The
     * calling device's session — when the presented refresh credential pins
     * a live row — is re-issued inside the same locked transaction: fresh
     * refresh and access tokens at the new credential revision, no overlap
     * window for the superseded token. The response carries the new pair so
     * cookie and body-token clients both land already signed in; an empty
     * result means no session could be preserved and every session was
     * revoked.
     */
    @Transactional
    public Optional<LoginResult> changePassword(
            String userId, String oldPassword, String newPassword, String refreshTokenValue) {
        // Pin the caller's session by stable row id BEFORE the User lock —
        // the same contract as logout(): a refresh holding that lock can
        // rotate the row's token hash while this call waits, and a hash
        // resolved only afterwards would point nowhere.
        String pinnedSessionId = refreshTokenValue == null || refreshTokenValue.isBlank()
                ? null
                : users.findSessionIdByRefreshHash(userId, hash(refreshTokenValue)).orElse(null);
        users.lockUserRow(userId);
        AuthUserRecord user = requireActiveUser(userId);
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password fields are required");
        }
        PasswordPolicy.validate(newPassword, user.email());
        if (!passwordEncoder.matches(oldPassword, user.passwordHash())) {
            // A stable code lets the profile form say "current password is
            // wrong" instead of the generic HTTP_400 validation copy.
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_OLD_PASSWORD",
                    "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSED",
                    "New password must differ from the current password.");
        }

        // Read before rotation: the flag says whether the credential being
        // replaced was office-issued, which decides the audit action below.
        boolean rotatingOfficeIssuedCredential = user.mustChangePassword();
        Instant now = clock.instant();
        users.changePassword(user.id(), passwordEncoder.encode(newPassword), now);
        // The pin ran before the User lock, so under the lock the row can
        // already be gone (concurrent logout or admin revocation) or dead
        // (expired). Re-verify it FOR UPDATE and only then promise the
        // device stays signed in — anything else falls back to revoking
        // every session: the safe direction.
        String keepSessionId = pinnedSessionId != null
                && users.findSessionExpiryForUpdate(userId, pinnedSessionId)
                        .map(expiresAt -> expiresAt.isAfter(now))
                        .orElse(false)
                ? pinnedSessionId
                : null;
        users.setMustChangePassword(user.id(), false);
        if (keepSessionId == null) {
            users.deleteAllRefreshSessions(user.id());
            users.clearUserRefreshToken(user.id());
            auditChange(userId, rotatingOfficeIssuedCredential, false);
            return Optional.empty();
        }
        users.deleteOtherRefreshSessions(user.id(), keepSessionId);
        // Re-issue rather than merely keep: a stolen copy of the
        // password-era refresh token must meet the superseded-token path
        // (its row still exists, so it stays reuse and fails closed), and
        // the response hands the caller a working pair at the new revision
        // — no 401->refresh round trip on the way back into the portal.
        AuthUserRecord rotatedUser = requireActiveUser(userId);
        AuthPrincipal principal = principal(rotatedUser);
        IssuedAccessToken accessToken = tokens.issueAccessToken(principal);
        IssuedRefreshToken nextRefreshToken = tokens.issueRefreshToken(principal, keepSessionId);
        users.reissueRefreshSession(
                user.id(), keepSessionId, hash(nextRefreshToken.refreshToken()), nextRefreshToken.expiresAt());
        auditChange(userId, rotatingOfficeIssuedCredential, true);
        return Optional.of(LoginResult.session(
                new LoginResponse(
                        rotatedUser.toResponse(), accessToken.accessToken(), nextRefreshToken.refreshToken()),
                accessToken.expiresAt(),
                nextRefreshToken.expiresAt()));
    }

    /**
     * Round-3 ct-4: a self-service credential rotation is exactly the
     * operation whose author an investigation needs to know — the trail
     * previously covered only admin-initiated resets (PASSWORD_RESET).
     * An office-issued temporary credential reaching its rotation is
     * marked separately so a takeover of an admin-issued password is
     * distinguishable from an ordinary change. The message must describe
     * the outcome that actually committed.
     */
    private void auditChange(String userId, boolean rotatingOfficeIssuedCredential, boolean sessionKept) {
        audit.record(userId, null,
                rotatingOfficeIssuedCredential ? "TEMP_CREDENTIAL_ROTATED" : "PASSWORD_CHANGED",
                "USER", userId,
                (rotatingOfficeIssuedCredential
                        ? "User rotated an office-issued temporary password; "
                        : "User rotated their own password; ")
                        + (sessionKept
                                ? "other sessions were revoked; the rotating device stays signed in"
                                : "all sessions were revoked"));
    }

    @Transactional
    public void logout(String userId, String refreshTokenValue) {
        // Pin the caller's session by stable row id BEFORE the User lock: a
        // refresh holding that lock can rotate the row's token hash while
        // logout waits, and a hash resolved only afterwards points nowhere —
        // the rotated session would silently survive the logout. When the
        // presented session can no longer be identified at all, revoke every
        // session of the account: termination with an ambiguous device is the
        // safe direction.
        String sessionId = refreshTokenValue == null || refreshTokenValue.isBlank()
                ? null
                : users.findSessionIdByRefreshHash(userId, hash(refreshTokenValue)).orElse(null);
        users.lockUserRow(userId);
        if (sessionId != null) {
            users.deleteRefreshSessionById(userId, sessionId);
        } else {
            users.deleteAllRefreshSessions(userId);
        }
        users.clearUserRefreshToken(userId);
        users.bumpCredentialVersion(userId);
    }

    private Jwt decodeRefresh(String refreshTokenValue) {
        try {
            return tokens.decodeRefreshToken(refreshTokenValue);
        } catch (JwtException exception) {
            throw new BadCredentialsException("Invalid refresh token", exception);
        }
    }

    private AuthUserRecord requireActiveUser(String userId) {
        AuthUserRecord user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid session"));
        if (!"ACTIVE".equals(user.status())) {
            throw new BadCredentialsException("Invalid session");
        }
        return user;
    }

    private static AuthPrincipal principal(AuthUserRecord user) {
        return new AuthPrincipal(
                user.id(),
                user.email(),
                user.firstName(),
                user.lastName(),
                user.status(),
                user.roles(),
                user.permissions(),
                user.studentId(),
                user.studentYear(),
                user.lecturerId(),
                user.credentialVersion());
    }

    private LoginResult issueSession(AuthUserRecord user, String ipAddress, String userAgent) {
        AuthPrincipal principal = principal(user);
        IssuedAccessToken accessToken = tokens.issueAccessToken(principal);
        String sessionId = UUID.randomUUID().toString();
        IssuedRefreshToken refreshToken = tokens.issueRefreshToken(principal, sessionId);
        users.insertRefreshSession(
                user.id(),
                sessionId,
                hash(refreshToken.refreshToken()),
                ipAddress,
                userAgent,
                refreshToken.expiresAt());
        return LoginResult.session(
                new LoginResponse(user.toResponse(), accessToken.accessToken(), refreshToken.refreshToken()),
                accessToken.expiresAt(),
                refreshToken.expiresAt());
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isValidEmail(String email) {
        int at = email.indexOf('@');
        return at > 0
                && at == email.lastIndexOf('@')
                && at < email.length() - 1
                && email.indexOf('.', at + 2) > at + 1
                && !email.contains(" ");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    /**
     * A full session (response + expiry instants, challenge fields null) or a
     * second-factor challenge (response null, challenge fields set). The
     * controller branches on {@code response == null}.
     */
    public record LoginResult(
            LoginResponse response,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt,
            String challengeId,
            String email) {

        public static LoginResult session(
                LoginResponse response,
                Instant accessTokenExpiresAt,
                Instant refreshTokenExpiresAt) {
            return new LoginResult(response, accessTokenExpiresAt, refreshTokenExpiresAt, null, null);
        }

        public static LoginResult challenge(String challengeId, String email) {
            return new LoginResult(null, null, null, challengeId, email);
        }
    }
}
