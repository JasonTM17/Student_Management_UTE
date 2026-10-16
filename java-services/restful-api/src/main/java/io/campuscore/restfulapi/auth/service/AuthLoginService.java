package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository.AuthUserRecord;
import io.campuscore.restfulapi.auth.web.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.UpdateProfileRequest;
import io.campuscore.restfulapi.exception.AppException;
import io.campuscore.restfulapi.web.DomainException;
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
import io.campuscore.restfulapi.audit.AdminAuditRecorder;
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
        String refreshTokenHash = hash(refreshTokenValue);
        AuthUserRecord user = users.findByActiveRefreshSession(refreshTokenHash, clock.instant())
                .orElseGet(() -> {
                    // The token carries our signature, yet no active session
                    // holds its hash: it was already rotated or revoked, so a
                    // replay is the classic stolen-token signal. Fail the
                    // family (every live session for the account) closed and
                    // leave an audit trace instead of silently 401-ing.
                    String subject = decoded.getSubject();
                    users.findById(subject).ifPresent(owner -> {
                        users.deleteAllRefreshSessions(owner.id());
                        reuseLogger.warn(
                                "REFRESH_TOKEN_REUSE subject={} ip={} userAgent={}",
                                subject,
                                ipAddress,
                                userAgent);
                    });
                    throw new BadCredentialsException("Invalid refresh token");
                });
        if (!user.id().equals(decoded.getSubject()) || !"ACTIVE".equals(user.status())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        AuthPrincipal principal = principal(user);
        IssuedAccessToken accessToken = tokens.issueAccessToken(principal);
        IssuedRefreshToken nextRefreshToken = tokens.issueRefreshToken(principal);
        users.rotateRefreshSession(
                user.id(),
                refreshTokenHash,
                hash(nextRefreshToken.refreshToken()),
                ipAddress,
                userAgent,
                nextRefreshToken.expiresAt());

        return LoginResult.session(
                new LoginResponse(user.toResponse(), accessToken.accessToken(), nextRefreshToken.refreshToken()),
                accessToken.expiresAt(),
                nextRefreshToken.expiresAt());
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

    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword) {
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
        users.changePassword(user.id(), passwordEncoder.encode(newPassword), clock.instant());
        // Rotation clears the office-issued flag; sessions are revoked so the
        // user signs back in with the new credential.
        users.setMustChangePassword(user.id(), false);
        users.deleteAllRefreshSessions(user.id());
        users.clearUserRefreshToken(user.id());
        // Round-3 ct-4: a self-service credential rotation is exactly the
        // operation whose author an investigation needs to know — the trail
        // previously covered only admin-initiated resets (PASSWORD_RESET).
        // An office-issued temporary credential reaching its rotation is
        // marked separately so a takeover of an admin-issued password is
        // distinguishable from an ordinary change.
        audit.record(userId, null,
                rotatingOfficeIssuedCredential ? "TEMP_CREDENTIAL_ROTATED" : "PASSWORD_CHANGED",
                "USER", userId,
                rotatingOfficeIssuedCredential
                        ? "User rotated an office-issued temporary password; all sessions were revoked"
                        : "User rotated their own password; all sessions were revoked");
    }

    @Transactional
    public void logout(String userId, String refreshTokenValue) {
        users.lockUserRow(userId);
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            users.deleteRefreshSession(userId, hash(refreshTokenValue));
        } else {
            users.deleteAllRefreshSessions(userId);
        }
        users.clearUserRefreshToken(userId);
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
        IssuedRefreshToken refreshToken = tokens.issueRefreshToken(principal);
        users.insertRefreshSession(
                user.id(),
                UUID.randomUUID().toString(),
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
