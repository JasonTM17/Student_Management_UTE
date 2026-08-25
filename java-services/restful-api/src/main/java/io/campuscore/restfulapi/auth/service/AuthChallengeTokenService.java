package io.campuscore.restfulapi.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Generates high-entropy one-time values and their persisted SHA-256 form. */
public final class AuthChallengeTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthChallengeTokenService() {
    }

    public static IssuedChallengeToken issue() {
        String challengeId = UUID.randomUUID().toString();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String rawToken = challengeId + "." + secret;
        return new IssuedChallengeToken(challengeId, rawToken, sha256(rawToken));
    }

    public static Optional<String> challengeId(String rawToken) {
        if (rawToken == null) {
            return Optional.empty();
        }
        int separator = rawToken.indexOf('.');
        if (separator <= 0 || separator == rawToken.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(rawToken.substring(0, separator)).toString());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static String sha256(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public static boolean matches(String rawValue, String expectedHash) {
        if (rawValue == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256(rawValue).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    public record IssuedChallengeToken(String challengeId, String rawToken, String tokenHash) {
    }
}
