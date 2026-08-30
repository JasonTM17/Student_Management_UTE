package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository.AuthUserRecord;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository.RegisterCommand;
import io.campuscore.restfulapi.auth.web.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.LoginResponse;
import io.campuscore.restfulapi.auth.web.AuthDtos.RegisterRequest;
import io.campuscore.restfulapi.auth.web.AuthDtos.UpdateProfileRequest;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Authentication and refresh-session service for the Java API. */
@Service
@Profile("persistence")
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
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || !isValidEmail(normalizedEmail)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        AuthUserRecord user = users.findByEmail(normalizedEmail)
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

        users.recordSuccessfulLogin(user.id(), now);
        return issueSession(user, ipAddress, userAgent);
    }

    @Transactional
    public LoginResult register(RegisterRequest request, String ipAddress, String userAgent) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration request is required");
        }
        String email = normalizeEmail(required(request.email(), "email"));
        if (!isValidEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        String password = required(request.password(), "password");
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters");
        }
        String firstName = required(request.firstName(), "firstName");
        String lastName = required(request.lastName(), "lastName");
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        AuthUserRecord user = users.createUser(new RegisterCommand(
                email,
                passwordEncoder.encode(password),
                firstName,
                lastName,
                request.phone(),
                request.gender(),
                request.dateOfBirth() == null || request.dateOfBirth().isBlank()
                        ? null
                        : parseDate(request.dateOfBirth()),
                request.address()));
        return issueSession(user, ipAddress, userAgent);
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

    @Transactional(readOnly = true)
    public AuthUserResponse me(String userId) {
        return requireActiveUser(userId).toResponse();
    }

    @Transactional
    public AuthUserResponse updateProfile(String userId, UpdateProfileRequest request) {
        AuthUserRecord user = requireActiveUser(userId);
        users.updateProfile(
                user.id(),
                request != null && request.firstName() != null ? request.firstName() : user.firstName(),
                request != null && request.lastName() != null ? request.lastName() : user.lastName(),
                request != null && request.phone() != null ? request.phone() : user.phone(),
                request != null && request.dateOfBirth() != null ? parseDate(request.dateOfBirth()) : user.dateOfBirth(),
                request != null && request.address() != null ? request.address() : user.address());
        return requireActiveUser(user.id()).toResponse();
    }

    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword) {
        AuthUserRecord user = requireActiveUser(userId);
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password fields are required");
        }
        if (newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must contain at least 8 characters");
        }
        if (!passwordEncoder.matches(oldPassword, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid old password");
        }

        users.changePassword(user.id(), passwordEncoder.encode(newPassword), clock.instant());
        users.deleteAllRefreshSessions(user.id());
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

    private AuthUserRecord requireActiveUser(String userId) {
        AuthUserRecord user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid session"));
        if (!"ACTIVE".equals(user.status())) {
            throw new BadCredentialsException("Invalid session");
        }
        return user;
    }

    private static Instant parseDate(String value) {
        try {
            return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateOfBirth must be an ISO date", exception);
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

    private LoginResult issueSession(AuthUserRecord user, String ipAddress, String userAgent) {
        AuthPrincipal principal = principal(user);
        IssuedAccessToken accessToken = tokens.issueAccessToken(principal);
        IssuedRefreshToken refreshToken = tokens.issueRefreshToken(principal);
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

    public record LoginResult(
            LoginResponse response,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt) {
    }
}
