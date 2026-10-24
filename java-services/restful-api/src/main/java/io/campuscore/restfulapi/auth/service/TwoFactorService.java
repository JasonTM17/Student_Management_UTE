package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.model.AuthUserRecord;
import io.campuscore.restfulapi.auth.repository.TwoFactorChallengeRepository;
import io.campuscore.restfulapi.auth.model.ChallengeRecord;
import io.campuscore.restfulapi.auth.service.SessionCookieService;
import io.campuscore.restfulapi.exception.AppException;
import io.campuscore.restfulapi.mail.config.MailConfig;
import io.campuscore.restfulapi.mail.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Opt-in email OTP two-factor authentication. A user who has enabled
 * two-factor auth receives a six-digit code at the account's own email
 * address; the code is stored only as a SHA-256 digest and is never logged.
 * Normal logins (flag off) are completely unchanged.
 */
@Service
@Profile("persistence")
public class TwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);

    /** Five wrong codes lock the challenge; the client must start over. */
    static final int MAX_ATTEMPTS = 5;

    /** Codes stay valid for ten minutes. */
    static final Duration CODE_TTL = Duration.ofMinutes(10);

    static final String CODE_TWO_FACTOR_ALREADY_ENABLED = "TWO_FACTOR_ALREADY_ENABLED";
    static final String CODE_TWO_FACTOR_NOT_ENABLED = "TWO_FACTOR_NOT_ENABLED";
    static final String CODE_MAIL_DELIVERY_FAILED = "MAIL_DELIVERY_FAILED";
    static final String CODE_CHALLENGE_INVALID = "TWO_FACTOR_CHALLENGE_INVALID";
    static final String CODE_CODE_EXPIRED = "TWO_FACTOR_CODE_EXPIRED";
    static final String CODE_CODE_INVALID = "TWO_FACTOR_CODE_INVALID";
    static final String CODE_CODE_LOCKED = "TWO_FACTOR_CODE_LOCKED";

    private static final String MAIL_SUBJECT = "[CampusUTE] Mã xác thực hai yếu tố";
    private static final String MAIL_TEMPLATE = "two-factor-code";

    private final AuthUserRepository users;
    private final TwoFactorChallengeRepository challenges;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final MailConfig mailConfig;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final AdminAuditRecorder audit;
    private final SessionCookieService cookies;

    @Autowired
    public TwoFactorService(
            AuthUserRepository users,
            TwoFactorChallengeRepository challenges,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            MailConfig mailConfig,
            AdminAuditRecorder audit,
            SessionCookieService cookies) {
        this(users, challenges, passwordEncoder, emailService, mailConfig, audit, cookies,
                Clock.systemUTC());
    }

    TwoFactorService(
            AuthUserRepository users,
            TwoFactorChallengeRepository challenges,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            MailConfig mailConfig,
            AdminAuditRecorder audit,
            SessionCookieService cookies,
            Clock clock) {
        this.users = users;
        this.challenges = challenges;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.mailConfig = mailConfig;
        this.audit = audit;
        this.cookies = cookies;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }

    public boolean isEnabled(String userId) {
        return users.isTwoFactorEnabled(userId);
    }

    /**
     * Starts the ENABLE opt-in: password re-check, then a code is emailed to
     * the account address. The challenge row commits with this transaction and
     * the email dispatch runs after commit (see {@link #deliver}), so a failed
     * send still gives the client 502 MAIL_DELIVERY_FAILED and leaves no
     * challenge row behind instead of an unusable code.
     */
    @Transactional
    public String startEnableChallenge(String userId, String password) {
        AuthUserRecord user = requireActiveUser(userId);
        if (!passwordEncoder.matches(password == null ? "" : password, user.passwordHash())) {
            // Same convention as change-password: wrong current secret → 400.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }
        if (users.isTwoFactorEnabled(user.id())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    CODE_TWO_FACTOR_ALREADY_ENABLED,
                    "Two-factor authentication is already enabled for this account");
        }
        return createAndDeliverChallenge(user.id(), TwoFactorChallengeRepository.PURPOSE_ENABLE);
    }

    /**
     * Confirms the ENABLE challenge and flips the account switch on. Wrong
     * attempts must persist across the transaction boundary (same 5-strikes
     * lock as the LOGIN branch), so AppException is exempted from the
     * rollback. K17: the credential revision advances, so access tokens
     * issued before the switch flipped stop authenticating immediately,
     * and refresh sessions issued before it are revoked — keeping only the
     * caller's current browser refresh session (see
     * {@link #revokeOtherRefreshSessions(String, String)}).
     */
    @Transactional(noRollbackFor = AppException.class)
    public void confirmEnable(String userId, String challengeId, String code) {
        String keepSessionId = resolveCurrentSessionId(userId);
        ChallengeRecord challenge = requireUsableChallenge(
                challengeId, TwoFactorChallengeRepository.PURPOSE_ENABLE, userId);
        verifyCode(challenge, code);
        users.setTwoFactorEnabled(userId, true);
        challenges.consume(challenge.id(), clock.instant());
        // Round-3 ct-4: turning 2FA on is a security-posture change — the
        // trail records who and when, next to the password events.
        audit.record(userId, null, "TWO_FACTOR_ENABLED", "USER", userId,
                "User enabled two-factor authentication");
        users.bumpCredentialVersion(userId);
        revokeOtherRefreshSessions(userId, keepSessionId);
    }

    /**
     * Round-4 verified finding: turning 2FA OFF used to require only the
     * password — the second factor was never asked when it was being removed,
     * so a stolen session plus a phished password unenrolled the victim
     * without touching their inbox. Disable now mirrors ENABLE: the password
     * starts a challenge, and the emailed code confirms it.
     */
    @Transactional
    public String startDisableChallenge(String userId, String password) {
        AuthUserRecord user = requireActiveUser(userId);
        if (!passwordEncoder.matches(password == null ? "" : password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }
        if (!users.isTwoFactorEnabled(user.id())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    CODE_TWO_FACTOR_NOT_ENABLED,
                    "Two-factor authentication is not enabled for this account");
        }
        return createAndDeliverChallenge(user.id(), TwoFactorChallengeRepository.PURPOSE_DISABLE);
    }

    /**
     * Confirms the DISABLE challenge and flips the account switch off.
     * K17: like the enable path, the credential revision advances so access
     * tokens issued before the change stop authenticating immediately, and
     * refresh sessions issued before it are revoked while the caller's
     * current browser refresh session survives.
     */
    @Transactional(noRollbackFor = AppException.class)
    public void confirmDisable(String userId, String challengeId, String code) {
        String keepSessionId = resolveCurrentSessionId(userId);
        ChallengeRecord challenge = requireUsableChallenge(
                challengeId, TwoFactorChallengeRepository.PURPOSE_DISABLE, userId);
        verifyCode(challenge, code);
        users.setTwoFactorEnabled(userId, false);
        challenges.consume(challenge.id(), clock.instant());
        audit.record(userId, null, "TWO_FACTOR_DISABLED", "USER", userId,
                "User disabled two-factor authentication after password and emailed-code verification");
        users.bumpCredentialVersion(userId);
        revokeOtherRefreshSessions(userId, keepSessionId);
    }

    /**
     * Pins the refresh session serving this request by its stable row id. The
     * pin must be taken before any account write acquires the User row lock:
     * a concurrent refresh holds that same lock while it rotates this row's
     * token hash, so a cookie hash resolved only at delete time can already
     * point nowhere — the just-rotated session would be deleted even though
     * the flow promised to keep it. Session row ids survive rotation, so the
     * pinned id still names the same session whichever interleaving wins.
     * When no session resolves — no cookie, non-browser client, or the row
     * was already rotated away or revoked — the caller is unidentifiable.
     */
    private String resolveCurrentSessionId(String userId) {
        String refreshToken = currentRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        return users.findSessionIdByRefreshHash(userId, sha256(refreshToken)).orElse(null);
    }

    /**
     * K17: enabling or disabling two-factor auth is a security-posture change,
     * so refresh sessions issued before it must stop working. The caller's
     * current session — pinned by row id in {@link #resolveCurrentSessionId}
     * before the credential lock is taken — is kept so the user is not signed
     * out in the middle of the flow; every other session of the account is
     * deleted. This runs after {@code bumpCredentialVersion}, so the account's
     * User row lock is already held and a queued refresh cannot rotate the
     * kept row mid-delete.
     *
     * <p>When no session could be pinned, every session is revoked instead.
     * That is the safe direction: the callers' credential revision bump has
     * already retired every issued access token, and with no refresh session
     * left the next request requires a fresh login.
     */
    private void revokeOtherRefreshSessions(String userId, String keepSessionId) {
        if (keepSessionId == null) {
            users.deleteAllRefreshSessions(userId);
            users.clearUserRefreshToken(userId);
            return;
        }
        users.deleteOtherRefreshSessions(userId, keepSessionId);
    }

    /**
     * Resolves the refresh token of the request being served, if any. Returns
     * null when called outside a servlet request so the fallback (revoke all)
     * stays well-defined.
     */
    private String currentRefreshToken() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return cookies.refreshToken(request, null);
    }

    /**
     * Called by {@link AuthLoginService} after a successful password check
     * for an account with two-factor auth on: emails the LOGIN code and
     * returns the challenge id for the client's second step.
     */
    @Transactional
    public String startLoginChallenge(AuthUserRecord user) {
        return createAndDeliverChallenge(user.id(), TwoFactorChallengeRepository.PURPOSE_LOGIN);
    }

    /** Resolve the owner without taking a Challenge lock; caller locks User first. */
    public String loginChallengeUserId(String challengeId) {
        return requireUsableChallenge(challengeId, TwoFactorChallengeRepository.PURPOSE_LOGIN, null).userId();
    }

    /**
     * Validates the LOGIN second factor under the caller's User lock. The
     * caller re-checks account state before issuing a session.
     */
    @Transactional(noRollbackFor = AppException.class)
    public String consumeLoginChallenge(String challengeId, String code) {
        ChallengeRecord challenge = requireUsableChallenge(
                challengeId, TwoFactorChallengeRepository.PURPOSE_LOGIN, null);
        verifyCode(challenge, code);
        challenges.consume(challenge.id(), clock.instant());
        return challenge.userId();
    }

    private String createAndDeliverChallenge(String userId, String purpose) {
        String code = generateCode();
        Instant expiresAt = clock.instant().plus(CODE_TTL);
        // The digest binds the owner id: a code issued for one account can
        // never verify against another account's challenge (Wukong P2.3).
        String challengeId = challenges.insert(userId, purpose, sha256(userId + ':' + code), expiresAt);
        deliver(challengeId, userId, code);
        return challengeId;
    }

    /**
     * SMTP runs in the after-commit phase. Spring still holds the
     * transaction's JDBC connection at that point (it is released in the
     * later cleanup step), so a degraded mail server pins one pooled
     * connection for the send duration — the send is deliberately synchronous
     * because the 502 MAIL_DELIVERY_FAILED contract needs its outcome. What
     * the after-commit hop buys: DB locks are released and the committed
     * challenge row is already visible to other transactions, so the write
     * window no longer spans the SMTP round-trip. Spring propagates an
     * afterCommit exception to the caller while the transaction stays
     * committed, so a failed send still reads as 502 MAIL_DELIVERY_FAILED;
     * the committed challenge row is deleted so no undelivered code can ever
     * verify (same zero-row contract as rollback).
     */
    private void deliver(String challengeId, String userId, String code) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    sendOrDrop(challengeId, userId, code);
                                }
                            });
            return;
        }
        sendOrDrop(challengeId, userId, code);
    }

    private void sendOrDrop(String challengeId, String userId, String code) {
        try {
            sendCodeEmail(userId, code);
        } catch (RuntimeException exception) {
            try {
                challenges.delete(challengeId);
            } catch (RuntimeException deleteFailure) {
                // The client must still learn the send failed (502), not the
                // cleanup failure (500); an undeleted row expires with the
                // code TTL and can never verify a code that was never sent.
                exception.addSuppressed(deleteFailure);
            }
            throw exception;
        }
    }

    private ChallengeRecord requireUsableChallenge(
            String challengeId, String expectedPurpose, String expectedUserId) {
        String trimmedId = requireText(challengeId, "challengeId is required");
        ChallengeRecord challenge = challenges.findById(trimmedId)
                .orElseThrow(TwoFactorService::invalidChallenge);
        if (!expectedPurpose.equals(challenge.purpose())
                || (expectedUserId != null && !expectedUserId.equals(challenge.userId()))) {
            throw invalidChallenge();
        }
        if (challenge.consumedAt() != null) {
            throw invalidChallenge();
        }
        if (!challenge.expiresAt().isAfter(clock.instant())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    CODE_CODE_EXPIRED,
                    "The verification code has expired; request a new one");
        }
        return challenge;
    }

    private void verifyCode(ChallengeRecord challenge, String code) {
        String submitted = requireText(code, "code is required");
        // Constant-time comparison of the stored hex digest against the
        // submitted code's digest; MessageDigest.isEqual also tolerates the
        // length difference without leaking where the mismatch starts. The
        // digest binds the challenge owner, so a code minted for one account
        // is worthless against another account's challenge.
        if (MessageDigest.isEqual(
                challenge.codeHash().getBytes(StandardCharsets.UTF_8),
                sha256(challenge.userId() + ':' + submitted).getBytes(StandardCharsets.UTF_8))) {
            return;
        }
        int attempts = challenges.incrementAttempts(challenge.id());
        if (attempts < 0) {
            // Consumed between load and increment: treat as invalid.
            throw invalidChallenge();
        }
        if (attempts >= MAX_ATTEMPTS) {
            challenges.consume(challenge.id(), clock.instant());
            log.warn("TWO_FACTOR_CODE_LOCKED challenge={} userId={}", challenge.id(), challenge.userId());
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    CODE_CODE_LOCKED,
                    "Too many wrong codes; start a new verification");
        }
        throw new AppException(
                HttpStatus.BAD_REQUEST,
                CODE_CODE_INVALID,
                "The verification code is incorrect");
    }

    private void sendCodeEmail(String userId, String code) {
        if (!mailConfig.isEnabled()) {
            throw mailUnavailable();
        }
        AuthUserRecord user = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User vanished mid-challenge: " + userId));
        try {
            emailService.sendHtmlEmail(
                    user.email(),
                    MAIL_SUBJECT,
                    MAIL_TEMPLATE,
                    Map.of(
                            "subject", MAIL_SUBJECT,
                            "recipientName", user.firstName() != null ? user.firstName() : "Bạn",
                            "accountEmail", maskEmail(user.email()),
                            "requestedAt", java.time.format.DateTimeFormatter
                                    .ofPattern("HH:mm', ngày' dd/MM/yyyy")
                                    .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                                    .format(clock.instant()),
                            "code", code,
                            "expiryMinutes", (int) CODE_TTL.toMinutes()));
        } catch (RuntimeException exception) {
            // Never log or surface the code itself.
            log.warn("Two-factor code email failed for userId={}: {}", userId, exception.getMessage());
            throw mailUnavailable();
        }
    }

    private static AppException mailUnavailable() {
        return new AppException(
                HttpStatus.BAD_GATEWAY,
                CODE_MAIL_DELIVERY_FAILED,
                "The verification code email could not be delivered; try again later");
    }

    private static AppException invalidChallenge() {
        return new AppException(
                HttpStatus.BAD_REQUEST,
                CODE_CHALLENGE_INVALID,
                "The verification challenge is invalid or already used; start again");
    }

    private AuthUserRecord requireActiveUser(String userId) {
        AuthUserRecord user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        if (!"ACTIVE".equals(user.status())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        return user;
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    /** First character + *** + domain, e.g. s***@gmail.com. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
