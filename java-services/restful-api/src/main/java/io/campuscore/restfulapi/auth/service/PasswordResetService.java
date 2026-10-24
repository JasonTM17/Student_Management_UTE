package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository.AuthUserRecord;
import io.campuscore.restfulapi.auth.repository.PasswordResetTokenRepository;
import io.campuscore.restfulapi.auth.repository.PasswordResetTokenRepository.TokenRecord;
import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.exception.DomainException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service password reset via emailed one-time links.
 *
 * <p>The forgot-password endpoint deliberately takes the same code path for
 * every outcome — unknown mailbox, ineligible account, hourly cap reached,
 * mail disabled — and the mail itself is sent asynchronously, so response
 * timing never reveals whether the account exists. Privileged accounts
 * (ADMIN, SUPER_ADMIN, TRUONG_KHOA) are recovered by a super administrator per
 * docs/PRODUCTION_RUNBOOK.md and get the same generic reply with no token.
 */
@Service
@Profile("persistence")
public class PasswordResetService {

    public static final String GENERIC_RESPONSE_MESSAGE =
            "If an account matches, a reset link has been sent.";
    public static final String SUCCESS_RESPONSE_MESSAGE = "Password has been reset.";
    public static final String CODE_TOKEN_INVALID = "RESET_TOKEN_INVALID";
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    static final int MAX_REQUESTS_PER_WINDOW = 3;

    // Privileged accounts never get self-service resets — see class javadoc.
    private static final List<String> PRIVILEGED_ROLES =
            List.of("ADMIN", "SUPER_ADMIN", "TRUONG_KHOA", "TRUONG_BO_MON");
    private static final String MAIL_TEMPLATE = "password-reset";
    private static final String MAIL_SUBJECT = "Đặt lại mật khẩu CampusCore";

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter MAIL_TIME = DateTimeFormatter
            .ofPattern("HH:mm', ngày' dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final AuthUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AdminAuditRecorder audit;
    private final Clock clock;
    private final String publicBaseUrl;

    /** No Clock bean exists in the context; the service only needs wall time. */
    @Autowired
    public PasswordResetService(
            AuthUserRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AdminAuditRecorder audit,
            @Value("${app.public-base-url}") String publicBaseUrl) {
        this(users, tokens, passwordEncoder, emailService, audit, Clock.systemUTC(), publicBaseUrl);
    }

    PasswordResetService(
            AuthUserRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AdminAuditRecorder audit,
            Clock clock,
            String publicBaseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.audit = audit;
        this.clock = clock;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    /**
     * Issues a reset link when the mailbox belongs to an eligible account;
     * every other outcome returns silently — the caller answers the same
     * generic 200 either way.
     */
    @Transactional
    public void requestReset(String email, String requestIp) {
        AuthUserRecord user = users.findByEmail(normalizeEmail(email)).orElse(null);
        if (user == null || !isEligible(user)) {
            return;
        }
        // Issuance and consumption share User -> ResetToken lock order.
        users.lockUserRow(user.id());
        String normalizedEmail = normalizeEmail(email);
        user = users.findById(user.id())
                .filter(current -> current.email().equals(normalizedEmail))
                .filter(PasswordResetService::isEligible)
                .orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        if (tokens.countRecentByUser(user.id(), now.minus(REQUEST_WINDOW))
                >= MAX_REQUESTS_PER_WINDOW) {
            return;
        }
        // One live link at a time: an earlier unused token can no longer reset.
        tokens.invalidateUnused(user.id(), now);
        String token = generateToken();
        tokens.insert(user.id(), sha256(token), now.plus(TOKEN_TTL), truncate(requestIp, 64));
        audit.record(user.id(), null, "PASSWORD_RESET_REQUESTED", "USER", user.id(),
                "Password reset link requested");
        log.info("password reset issued for user {}", user.id());
        emailService.sendAsyncHtmlEmail(
                user.email(),
                MAIL_SUBJECT,
                MAIL_TEMPLATE,
                Map.of(
                        "subject", MAIL_SUBJECT,
                        "recipientName",
                                user.firstName() != null && !user.firstName().isBlank()
                                        ? user.firstName() : "Bạn",
                        "resetLink", publicBaseUrl + "/reset-password?token=" + token,
                        "expiryMinutes", String.valueOf(TOKEN_TTL.toMinutes()),
                        "requestedAt", MAIL_TIME.format(now)));
    }

    /**
     * Consumes a reset token atomically and installs the new password. The
     * consume is a conditional UPDATE so a token can never be applied twice,
     * even concurrently; policy checks run before it so a rejected candidate
     * keeps the link usable.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = sha256(rawToken);
        Instant now = clock.instant();
        TokenRecord token = tokens.findByTokenHash(tokenHash)
                .filter(found -> found.usedAt() == null && found.expiresAt().isAfter(now))
                .orElseThrow(PasswordResetService::invalidToken);
        // Match login/refresh/admin order: User before reset-token/session.
        users.lockUserRow(token.userId());
        AuthUserRecord user = users.findById(token.userId())
                .filter(PasswordResetService::isEligible)
                .orElseThrow(PasswordResetService::invalidToken);
        // Policy runs before consumption: a weak candidate must not burn the link.
        PasswordPolicy.validate(newPassword, user.email());
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSED",
                    "New password must differ from the current password.");
        }
        boolean consumed;
        try {
            consumed = tokens.consumeIfUnusedAndUnexpired(tokenHash, now);
        } catch (CannotAcquireLockException concurrentLoser) {
            // The competing request holds the row lock — treat it as a lost race.
            consumed = false;
        }
        if (!consumed) {
            throw invalidToken();
        }
        users.resetPassword(user.id(), passwordEncoder.encode(newPassword), now);
        users.deleteAllRefreshSessions(user.id());
        users.clearUserRefreshToken(user.id());
        tokens.invalidateUnused(user.id(), now);
        audit.record(user.id(), null, "PASSWORD_RESET_COMPLETED", "USER", user.id(),
                "Password reset via emailed link; all sessions were revoked");
        log.info("password reset completed for user {}", user.id());
    }

    private static boolean isEligible(AuthUserRecord user) {
        return "ACTIVE".equals(user.status())
                && Collections.disjoint(user.roles(), PRIVILEGED_ROLES);
    }

    private static DomainException invalidToken() {
        return new DomainException(HttpStatus.BAD_REQUEST, CODE_TOKEN_INVALID,
                "This reset link is invalid or has expired.");
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String stripTrailingSlash(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
