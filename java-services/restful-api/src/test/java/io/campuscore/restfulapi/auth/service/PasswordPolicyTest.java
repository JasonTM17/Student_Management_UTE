package io.campuscore.restfulapi.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.campuscore.restfulapi.exception.DomainException;
import org.junit.jupiter.api.Test;

/**
 * The self-service password policy is a fixed contract: stable codes in a
 * fixed precedence order, so clients (and the audit trail) can rely on the
 * first failing rule rather than a fuzzy "invalid password".
 */
class PasswordPolicyTest {

    private static final String EMAIL = "nguyen.van@campuscore.edu";

    private static String code(Runnable check) {
        return assertThrows(DomainException.class, check::run).getCode();
    }

    @Test
    void tooShortIsCheckedFirst() {
        assertEquals("PASSWORD_TOO_SHORT",
                code(() -> PasswordPolicy.validate("a1", EMAIL)));
        assertEquals("PASSWORD_TOO_SHORT",
                code(() -> PasswordPolicy.validate("1234567", EMAIL)));
        assertEquals("PASSWORD_TOO_SHORT",
                code(() -> PasswordPolicy.validate(null, EMAIL)));
        // An empty/blank candidate also fails the length rule, and it wins
        // over the weakness rule it would also violate.
        assertEquals("PASSWORD_TOO_SHORT",
                code(() -> PasswordPolicy.validate("", EMAIL)));
    }

    @Test
    void tooLongCoversCharactersAndTheBcryptByteCeiling() {
        // 129 characters is too long even though it fits the byte ceiling.
        assertEquals("PASSWORD_TOO_LONG",
                code(() -> PasswordPolicy.validate("a".repeat(128) + "1", EMAIL)));
        // 60 ASCII chars + 7 "đ" (2 bytes each in UTF-8) = 74 bytes: a
        // password BCrypt would silently truncate to 72 bytes — never accept
        // a credential whose tail is ignored at verification time.
        String seventyFourBytes = "a".repeat(60) + "đ".repeat(7) + "1";
        assertEquals("PASSWORD_TOO_LONG",
                code(() -> PasswordPolicy.validate(seventyFourBytes, EMAIL)));
    }

    @Test
    void seventyTwoByteBoundaryIsAcceptedWhenCompliant() {
        // 63 'a' + 4 'đ' (2 bytes each) + '1' = exactly 72 UTF-8 bytes — the
        // last byte count BCrypt still reads in full.
        assertDoesNotThrow(() -> PasswordPolicy.validate(
                "a".repeat(63) + "đ".repeat(4) + "1", EMAIL));
    }

    @Test
    void identityPartsAreRejectedCaseInsensitively() {
        assertEquals("PASSWORD_CONTAINS_IDENTITY",
                code(() -> PasswordPolicy.validate("NGUYEN.VAN@CAMPUSCORE.EDU", EMAIL)));
        // Exactly the local part, any case.
        assertEquals("PASSWORD_CONTAINS_IDENTITY",
                code(() -> PasswordPolicy.validate("Nguyen.Van", EMAIL)));
        // Local part (>= 4 chars) as a substring.
        assertEquals("PASSWORD_CONTAINS_IDENTITY",
                code(() -> PasswordPolicy.validate("xnguyen.van9", EMAIL)));
        // A short local part (< 4 chars) is not a substring signal — "bo"
        // would over-reject ordinary words otherwise.
        assertDoesNotThrow(() -> PasswordPolicy.validate("aBo12345", "bo@campuscore.edu"));
        // Null/blank emails simply skip the identity rule.
        assertDoesNotThrow(() -> PasswordPolicy.validate("Z9k2m4q8x", null));
        assertDoesNotThrow(() -> PasswordPolicy.validate("Z9k2m4q8x", ""));
    }

    @Test
    void weaknessRequiresALetterAndADigit() {
        assertEquals("PASSWORD_TOO_WEAK",
                code(() -> PasswordPolicy.validate("abcdefgh", EMAIL)));
        assertEquals("PASSWORD_TOO_WEAK",
                code(() -> PasswordPolicy.validate("12345678901", EMAIL)));
        // Unicode letters count — "đ" is a letter for the letter arm.
        assertDoesNotThrow(() -> PasswordPolicy.validate("đđđđđđđ1", "zz@campuscore.edu"));
    }

    @Test
    void denylistIsCaseInsensitive() {
        // Denylist entries lacking a letter or a digit (e.g. "12345678",
        // "campusute") are unreachable here: the weakness rule fires first,
        // and that precedence is asserted separately below.
        for (String candidate : new String[] {
                "password123", "PASSWORD123", "Password123",
                "password1", "qwerty123",
                "iloveyou1", "admin123", "campuscore1"}) {
            assertEquals("PASSWORD_TOO_COMMON",
                    code(() -> PasswordPolicy.validate(candidate, "zz-top@campuscore.edu")),
                    () -> "denylist miss: " + candidate);
        }
        // Near-misses stay legal.
        assertDoesNotThrow(() -> PasswordPolicy.validate("password124", "zz@campuscore.edu"));
    }

    @Test
    void rulePrecedenceIsStable() {
        // Too-short beats every other rule, even for a denylisted candidate.
        assertEquals("PASSWORD_TOO_SHORT",
                code(() -> PasswordPolicy.validate("admin123".substring(0, 7), EMAIL)));
        // Identity beats weakness: the email itself contains both letter and
        // digit arms missing, yet identity fires first.
        assertEquals("PASSWORD_CONTAINS_IDENTITY",
                code(() -> PasswordPolicy.validate("NGUYEN.VAN@CAMPUSCORE.EDU", EMAIL)));
        // Weakness beats the denylist check on non-listed candidates only —
        // a listed candidate that also lacks a letter/digit hits weakness first.
        assertEquals("PASSWORD_TOO_WEAK",
                code(() -> PasswordPolicy.validate("12345678", "zz@campuscore.edu")));
    }

    @Test
    void compliantPasswordsPass() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Tr0ub4dor&3", EMAIL));
        assertDoesNotThrow(() -> PasswordPolicy.validate("correct-horse-9", EMAIL));
        assertDoesNotThrow(() -> PasswordPolicy.validate("MậtKhẩuMới123", EMAIL));
    }
}
