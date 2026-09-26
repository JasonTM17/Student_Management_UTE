package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository.AuthUserRecord;
import io.campuscore.restfulapi.auth.repository.TwoFactorChallengeRepository;
import io.campuscore.restfulapi.auth.repository.TwoFactorChallengeRepository.ChallengeRecord;
import io.campuscore.restfulapi.exception.AppException;
import io.campuscore.restfulapi.mail.config.MailConfig;
import io.campuscore.restfulapi.mail.service.EmailService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    static final String CODE_MAIL_DELIVERY_FAILED = "MAIL_DELIVERY_FAILED";
    static final String CODE_CHALLENGE_INVALID = "TWO_FACTOR_CHALLENGE_INVALID";
    static final String CODE_CODE_EXPIRED = "TWO_FACTOR_CODE_EXPIRED";
    static final String CODE_CODE_INVALID = "TWO_FACTOR_CODE_INVALID";
    static final String CODE_CODE_LOCKED = "TWO_FACTOR_CODE_LOCKED";

    private static final String MAIL_SUBJECT = "[CampusCore] Mã xác thực hai yếu tố";
    private static final String MAIL_TEMPLATE = "two-factor-code";

    private final AuthUserRepository users;
    private final TwoFactorChallengeRepository challenges;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final MailConfig mailConfig;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public TwoFactorService(
            AuthUserRepository users,
            TwoFactorChallengeRepository challenges,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            MailConfig mailConfig) {
        this(users, challenges, passwordEncoder, emailService, mailConfig, Clock.systemUTC());
    }

    TwoFactorService(
            AuthUserRepository users,
            TwoFactorChallengeRepository challenges,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            MailConfig mailConfig,
            Clock clock) {
        this.users = users;
        this.challenges = challenges;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.mailConfig = mailConfig;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }

    public boolean isEnabled(String userId) {
        return users.isTwoFactorEnabled(userId);
    }

    /**
     * Starts the ENABLE opt-in: password re-check, then a code is emailed to
     * the account address. The challenge row and the email dispatch live in
     * one transaction, so a failed send rolls the challenge back — the client
     * gets 502 MAIL_DELIVERY_FAILED instead of an unusable code.
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
     * Confirms the ENABLE challenge and flips the account switch on.
     * Wrong attempts must persist across the transaction boundary (same
     * 5-strikes lock as the LOGIN branch), so AppException is exempted from
     * the rollback.
     */
    @Transactional(noRollbackFor = AppException.class)
    public void confirmEnable(String userId, String challengeId, String code) {
        ChallengeRecord challenge = requireUsableChallenge(
                challengeId, TwoFactorChallengeRepository.PURPOSE_ENABLE, userId);
        verifyCode(challenge, code);
        users.setTwoFactorEnabled(userId, true);
        challenges.consume(challenge.id(), clock.instant());
    }

    /** Turns two-factor auth off after a password re-check. */
    @Transactional
    public void disable(String userId, String password) {
        AuthUserRecord user = requireActiveUser(userId);
        if (!passwordEncoder.matches(password == null ? "" : password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }
        users.setTwoFactorEnabled(userId, false);
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

    /**
     * Validates the LOGIN second factor and returns the account id that owns
     * the challenge. The caller re-checks account state and issues the same
     * session as a normal login.
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
        sendCodeEmail(userId, code);
        return challengeId;
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
