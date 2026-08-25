package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.auth.mail.AuthMailService;
import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository;
import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Challenge;
import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository.AuthUserRecord;
import io.campuscore.restfulapi.auth.service.AuthChallengeTokenService.IssuedChallengeToken;
import io.campuscore.restfulapi.auth.web.AuthDtos.ChallengeTokenRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.EmailRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.MessageResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.PasswordResetConfirmRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.PasswordResetRequest;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email verification and password-reset lifecycle owned by the Java API. */
@Service
@Profile("persistence")
public class AuthLifecycleService {

    private static final String VERIFICATION_MESSAGE =
            "If verification is required, an email will be sent";
    private static final String RESET_MESSAGE =
            "If the account exists, password reset instructions will be sent";
    private static final String VERIFIED_MESSAGE = "Email verified. Please sign in.";

    private final AuthUserRepository users;
    private final AuthChallengeRepository challenges;
    private final AuthMailService mail;
    private final PasswordEncoder passwordEncoder;
    private final AuthRequestRateLimiter rateLimiter;
    private final Clock clock;

    @Value("${auth.lifecycle.verification-ttl-seconds:86400}")
    private long verificationTtlSeconds;

    @Value("${auth.lifecycle.password-reset-ttl-seconds:1800}")
    private long passwordResetTtlSeconds;

    @Value("${auth.lifecycle.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${auth.lifecycle.max-challenge-attempts:5}")
    private int maxChallengeAttempts;

    @Autowired
    public AuthLifecycleService(
            AuthUserRepository users,
            AuthChallengeRepository challenges,
            AuthMailService mail,
            PasswordEncoder passwordEncoder,
            AuthRequestRateLimiter rateLimiter) {
        this(users, challenges, mail, passwordEncoder, rateLimiter, Clock.systemUTC());
    }

    AuthLifecycleService(
            AuthUserRepository users,
            AuthChallengeRepository challenges,
            AuthMailService mail,
            PasswordEncoder passwordEncoder,
            AuthRequestRateLimiter rateLimiter,
            Clock clock) {
        this.users = users;
        this.challenges = challenges;
        this.mail = mail;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = DomainException.class)
    public MessageResponse confirmEmail(ChallengeTokenRequest request) {
        String token = required(request == null ? null : request.token(), "token");
        Challenge challenge = consumeValidChallenge(Purpose.EMAIL_VERIFICATION, token);
        AuthUserRecord user = users.findById(challenge.userId()).orElseThrow(this::invalidChallenge);
        users.markEmailVerified(user.id(), clock.instant());
        return new MessageResponse(VERIFIED_MESSAGE);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public MessageResponse resendVerification(EmailRequest request, String ipAddress) {
        String email = normalize(required(request == null ? null : request.email(), "email"));
        try {
            rateLimiter.check("verification-resend", email, ipAddress);
        } catch (DomainException throttled) {
            if (isEnumerationSafeThrottle(throttled)) {
                return new MessageResponse(VERIFICATION_MESSAGE);
            }
            throw throttled;
        }
        Optional<AuthUserRecord> userResult = users.findByEmail(email);
        if (userResult.isEmpty() || userResult.get().emailVerified()) {
            return new MessageResponse(VERIFICATION_MESSAGE);
        }

        AuthUserRecord user = userResult.get();
        lockChallengeStream(user.id());
        Instant now = clock.instant();
        Optional<Challenge> latest = challenges.findLatestForUser(user.id(), Purpose.EMAIL_VERIFICATION, true);
        if (latest.isPresent()
                && latest.get().consumedAt() == null
                && latest.get().lastSentAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            // Keep the public response indistinguishable from an unknown or
            // already-verified address.  The cooldown remains enforced by
            // refusing to issue a new challenge, but account state is never
            // disclosed through a distinct status or error code.
            return new MessageResponse(VERIFICATION_MESSAGE);
        }
        issueAndQueue(user, Purpose.EMAIL_VERIFICATION, verificationTtlSeconds, now);
        return new MessageResponse(VERIFICATION_MESSAGE);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public MessageResponse requestPasswordReset(PasswordResetRequest request, String ipAddress) {
        String email = normalize(required(request == null ? null : request.email(), "email"));
        try {
            rateLimiter.check("password-reset-request", email, ipAddress);
        } catch (DomainException throttled) {
            if (isEnumerationSafeThrottle(throttled)) {
                return new MessageResponse(RESET_MESSAGE);
            }
            throw throttled;
        }
        Optional<AuthUserRecord> userResult = users.findByEmail(email);
        if (userResult.isEmpty() || !"ACTIVE".equals(userResult.get().status())) {
            return new MessageResponse(RESET_MESSAGE);
        }

        AuthUserRecord user = userResult.get();
        lockChallengeStream(user.id());
        Instant now = clock.instant();
        Optional<Challenge> latest = challenges.findLatestForUser(user.id(), Purpose.PASSWORD_RESET, true);
        if (latest.isPresent()
                && latest.get().consumedAt() == null
                && latest.get().lastSentAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            return new MessageResponse(RESET_MESSAGE);
        }
        issueAndQueue(user, Purpose.PASSWORD_RESET, passwordResetTtlSeconds, now);
        return new MessageResponse(RESET_MESSAGE);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public MessageResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        String token = required(request == null ? null : request.token(), "token");
        String newPassword = required(request == null ? null : request.newPassword(), "newPassword");
        if (newPassword.length() < 8) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "Password must contain at least 8 characters");
        }
        Challenge challenge = consumeValidChallenge(Purpose.PASSWORD_RESET, token);
        AuthUserRecord user = users.findById(challenge.userId()).orElseThrow(this::invalidChallenge);
        users.resetPassword(user.id(), passwordEncoder.encode(newPassword), clock.instant());
        users.deleteAllRefreshSessions(user.id());
        users.clearUserRefreshToken(user.id());
        return new MessageResponse("Password reset successfully. Please sign in.");
    }

    private void issueAndQueue(AuthUserRecord user, Purpose purpose, long ttlSeconds, Instant now) {
        IssuedChallengeToken token = AuthChallengeTokenService.issue();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        challenges.create(token.challengeId(), user.id(), purpose, token.tokenHash(), expiresAt, now);
        if (purpose == Purpose.EMAIL_VERIFICATION) {
            mail.queueVerification(user.email(), user.firstName(), token.rawToken(), expiresAt);
        } else {
            mail.queuePasswordReset(user.email(), user.firstName(), token.rawToken(), expiresAt);
        }
    }

    private Challenge consumeValidChallenge(Purpose purpose, String rawToken) {
        String challengeId = AuthChallengeTokenService.challengeId(rawToken)
                .orElseThrow(this::invalidChallenge);
        String userId = challenges.findUserId(challengeId, purpose)
                .orElseThrow(this::invalidChallenge);
        lockChallengeStream(userId);
        Challenge challenge = challenges.findByIdForUpdate(challengeId, purpose)
                .orElseThrow(this::invalidChallenge);
        Instant now = clock.instant();
        if (challenge.consumedAt() != null) {
            throw invalidChallenge();
        }
        if (!challenge.expiresAt().isAfter(now)) {
            challenges.consume(challenge.id(), now);
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "AUTH_CHALLENGE_EXPIRED",
                    "The authentication challenge has expired");
        }
        if (challenge.attemptCount() >= maxChallengeAttempts) {
            throw attemptsExceeded();
        }
        if (!AuthChallengeTokenService.matches(rawToken, challenge.tokenHash())) {
            int nextAttempts = challenge.attemptCount() + 1;
            challenges.recordFailedAttempt(
                    challenge.id(),
                    nextAttempts,
                    nextAttempts >= maxChallengeAttempts ? now : null);
            if (nextAttempts >= maxChallengeAttempts) {
                throw attemptsExceeded();
            }
            throw invalidChallenge();
        }
        if (!challenges.consume(challenge.id(), now)) {
            throw invalidChallenge();
        }
        return challenge;
    }

    private void lockChallengeStream(String userId) {
        if (!challenges.lockUser(userId)) {
            throw invalidChallenge();
        }
    }

    private DomainException invalidChallenge() {
        return new DomainException(
                HttpStatus.BAD_REQUEST,
                "AUTH_CHALLENGE_INVALID",
                "The authentication challenge is invalid");
    }

    private DomainException attemptsExceeded() {
        return new DomainException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AUTH_CHALLENGE_ATTEMPTS_EXCEEDED",
                "Too many authentication challenge attempts");
    }

    private static boolean isEnumerationSafeThrottle(DomainException exception) {
        return "AUTH_RATE_LIMITED".equals(exception.code())
                || "AUTH_RESEND_THROTTLED".equals(exception.code());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", field + " is required");
        }
        return value.trim();
    }
}
