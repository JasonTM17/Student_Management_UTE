package io.campuscore.restfulapi.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies the JWT secret policy: the secrets shipped inside this repository are
 * refused in strict mode, while legitimate secrets (including the test fixtures
 * used by the rest of the suite) are always accepted.
 */
class JwtSecretPolicyTest {

    /** The exact fixtures used by application-test.yml and AuthTokenServiceTest. */
    private static final String TEST_ACCESS_SECRET =
            "test-only-restful-api-secret-with-at-least-32-characters";
    private static final String TEST_REFRESH_SECRET =
            "test-only-refresh-secret-with-at-least-32-characters";

    @ParameterizedTest
    @ValueSource(strings = {
        "local-course-jwt-secret-change-me",
        "local-course-refresh-secret-change-me",
        "edge-e2e-jwt-secret-0123456789abcdef",
        "edge-e2e-refresh-secret-0123456789abcdef",
        "replace-with-a-long-random-access-secret",
        "replace-with-a-long-random-refresh-secret"
    })
    @DisplayName("every shipped default is recognised as a known secret")
    void shippedDefaultsAreRecognised(String shippedDefault) {
        assertTrue(JwtSecretPolicy.isKnownDefault(shippedDefault), shippedDefault);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "local-course-jwt-secret-change-me",
        "edge-e2e-refresh-secret-0123456789abcdef",
        "replace-with-a-long-random-access-secret"
    })
    @DisplayName("strict mode aborts startup on a shipped default")
    void strictModeRejectsShippedDefaults(String shippedDefault) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> JwtSecretPolicy.enforce("JWT_SECRET", shippedDefault, true));
        assertTrue(failure.getMessage().contains("published in this repository"), failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "local-course-jwt-secret-change-me",
        "edge-e2e-refresh-secret-0123456789abcdef"
    })
    @DisplayName("non-strict mode warns but does not abort startup")
    void nonStrictModeAllowsShippedDefaults(String shippedDefault) {
        assertDoesNotThrow(() -> JwtSecretPolicy.enforce("JWT_SECRET", shippedDefault, false));
    }

    @Test
    @DisplayName("a random 64-char hex secret is accepted in strict mode")
    void strongRandomSecretIsAccepted() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secret = HexFormat.of().formatHex(bytes);
        assertFalse(JwtSecretPolicy.isKnownDefault(secret));
        assertDoesNotThrow(() -> JwtSecretPolicy.enforce("JWT_SECRET", secret, true));
    }

    @Test
    @DisplayName("the test fixtures used elsewhere in the suite are accepted in strict mode")
    void testFixturesAreAccepted() {
        assertDoesNotThrow(() -> JwtSecretPolicy.enforce("JWT_SECRET", TEST_ACCESS_SECRET, true));
        assertDoesNotThrow(() -> JwtSecretPolicy.enforce("JWT_REFRESH_SECRET", TEST_REFRESH_SECRET, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "too-short", "1234567890123456789012345678901"})
    @DisplayName("a secret shorter than 32 characters is rejected in either mode")
    void shortSecretsAreRejected(String shortSecret) {
        assertThrows(IllegalStateException.class,
                () -> JwtSecretPolicy.enforce("JWT_SECRET", shortSecret, true));
        assertThrows(IllegalStateException.class,
                () -> JwtSecretPolicy.enforce("JWT_SECRET", shortSecret, false));
    }

    @Test
    @DisplayName("a null secret is rejected in either mode")
    void nullSecretIsRejected() {
        assertThrows(IllegalStateException.class, () -> JwtSecretPolicy.enforce("JWT_SECRET", null, true));
        assertThrows(IllegalStateException.class, () -> JwtSecretPolicy.enforce("JWT_SECRET", null, false));
    }

    @Test
    @DisplayName("matching ignores surrounding whitespace and letter case")
    void matchingIsNormalised() {
        assertTrue(JwtSecretPolicy.isKnownDefault("  LOCAL-COURSE-JWT-SECRET-CHANGE-ME  "));
        assertThrows(IllegalStateException.class,
                () -> JwtSecretPolicy.enforce("JWT_SECRET", " Edge-E2E-Jwt-Secret-0123456789abcdef ", true));
    }
}
