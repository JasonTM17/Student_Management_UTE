package io.campuscore.restfulapi.auth.service;

import io.campuscore.restfulapi.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Self-service password policy for user-chosen secrets. The checks run in a
 * fixed order and emit stable {@link DomainException} codes so the client can
 * present one precise failure at a time. Admin-issued temporary passwords are
 * intentionally NOT routed through this policy — they are generated secrets,
 * and the user replaces them via the enforced must-change flow.
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;
    // BCrypt silently truncates input at 72 bytes. Rejecting beyond that is
    // mandatory: accepting it would let two different passwords verify
    // against the same prefix.
    static final int MAX_UTF8_BYTES = 72;
    static final int MIN_LOCAL_PART_FOR_SUBSTRING = 4;

    private static final List<String> COMMON_PASSWORDS = List.of(
            "password", "password1", "password123", "qwerty123", "qwerty1234",
            "12345678", "123456789", "1234567890", "11111111", "123123123",
            "iloveyou1", "admin123", "admin1234", "letmein1", "welcome1",
            "abc12345", "changeme1", "secret123", "dragon123", "monkey123",
            "master123", "sunshine1", "football1", "campusute", "campuscore1",
            "student123");

    private PasswordPolicy() {
    }

    public static void validate(String newPassword, String email) {
        if (newPassword == null || newPassword.length() < MIN_LENGTH) {
            throw failure("PASSWORD_TOO_SHORT",
                    "Password must contain at least " + MIN_LENGTH + " characters.");
        }
        if (newPassword.length() > MAX_LENGTH
                || newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw failure("PASSWORD_TOO_LONG",
                    "Password must be at most " + MAX_LENGTH + " characters.");
        }
        String normalized = newPassword.toLowerCase(Locale.ROOT);
        String localPart = localPart(email);
        if (localPart != null
                && (normalized.equals(email.trim().toLowerCase(Locale.ROOT))
                        || normalized.equals(localPart)
                        || (localPart.length() >= MIN_LOCAL_PART_FOR_SUBSTRING
                                && normalized.contains(localPart)))) {
            throw failure("PASSWORD_CONTAINS_IDENTITY",
                    "Password must not contain your email address.");
        }
        boolean hasLetter = newPassword.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = newPassword.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw failure("PASSWORD_TOO_WEAK",
                    "Password must contain at least one letter and one digit.");
        }
        if (COMMON_PASSWORDS.contains(normalized)) {
            throw failure("PASSWORD_TOO_COMMON",
                    "This password is too common; choose a less predictable one.");
        }
    }

    private static String localPart(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        String part = (at > 0 ? email.substring(0, at) : email).trim().toLowerCase(Locale.ROOT);
        return part.isEmpty() ? null : part;
    }

    private static DomainException failure(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }
}
