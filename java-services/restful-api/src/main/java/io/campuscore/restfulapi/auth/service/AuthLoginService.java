package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository.AuthUserRecord;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginResponse;
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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Feature-gated Java login candidate for the monolith cutover path. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.auth-login", name = "enabled", havingValue = "true")
public class AuthLoginService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final AuthUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokens;
    private final Clock clock;

    @Autowired
    public AuthLoginService(
            AuthUserRepository users,
            PasswordEncoder passwordEncoder,
            AuthTokenService tokens) {
        this(users, passwordEncoder, tokens, Clock.systemUTC());
    }

    AuthLoginService(
            AuthUserRepository users,
            PasswordEncoder passwordEncoder,
            AuthTokenService tokens,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResult login(String email, String password, String ipAddress, String userAgent) {
        AuthUserRecord user = users.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Instant now = clock.instant();

        if (!"ACTIVE".equals(user.status()) || (user.lockedUntil() != null && user.lockedUntil().isAfter(now))) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            int nextAttemptCount = user.failedLoginAttempts() + 1;
            Instant lockedUntil = nextAttemptCount >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : null;
            users.recordFailedLogin(user.id(), nextAttemptCount, lockedUntil);
            throw new BadCredentialsException("Invalid credentials");
        }

        AuthPrincipal principal = principal(user);
        IssuedAccessToken accessToken = tokens.issueAccessToken(principal);
        IssuedRefreshToken refreshToken = tokens.issueRefreshToken(principal);

        users.recordSuccessfulLogin(user.id(), now);
        users.replaceRefreshSession(
                user.id(),
                UUID.randomUUID().toString(),
                hash(refreshToken.refreshToken()),
                ipAddress,
                userAgent,
                refreshToken.expiresAt());

        return new LoginResult(
                new LoginResponse(user.toResponse(), accessToken.accessToken(), refreshToken.refreshToken()),
                accessToken.expiresAt(),
                refreshToken.expiresAt());
    }

    @Transactional
    public LoginResult refresh(String refreshTokenValue, String ipAddress, String userAgent) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is required");
        }

        Jwt decoded = decodeRefresh(refreshTokenValue);
        String refreshTokenHash = hash(refreshTokenValue);
        AuthUserRecord user = users.findByActiveRefreshSession(refreshTokenHash, clock.instant())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (!user.id().equals(decoded.getSubject()) || !"ACTIVE".equals(user.status())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        AuthPrincipal principal = principal(user);
        IssuedAccessToken accessToken = tokens.issueAccessToken(principal);
        IssuedRefreshToken nextRefreshToken = tokens.issueRefreshToken(principal);
        users.replaceRefreshSession(
                user.id(),
                UUID.randomUUID().toString(),
                hash(nextRefreshToken.refreshToken()),
                ipAddress,
                userAgent,
                nextRefreshToken.expiresAt());

        return new LoginResult(
                new LoginResponse(user.toResponse(), accessToken.accessToken(), nextRefreshToken.refreshToken()),
                accessToken.expiresAt(),
                nextRefreshToken.expiresAt());
    }

    @Transactional
    public void logout(String userId, String refreshTokenValue) {
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
                user.lecturerId());
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record LoginResult(
            LoginResponse response,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt) {
    }
}
