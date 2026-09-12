package io.campuscore.restfulapi.security;

import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single guard for JWT signing secrets, shared by the access-token and
 * refresh-token paths.
 *
 * <p>The repository ships known, guessable secrets in the local and e2e compose
 * files. A secret that is published in the repository cannot protect anything,
 * so a deployed API must refuse to boot with one.
 *
 * <p><strong>Detection is an exact-match denylist, not an entropy heuristic.</strong>
 * Measured per-character Shannon entropy cannot separate the shipped defaults
 * (3.59 – 4.18 bits/char) from a legitimate {@code openssl rand -hex 32} secret
 * (0.46 bits/char), because a random hex string repeats only 16 symbols. Any
 * entropy floor would therefore reject strong secrets while accepting weak ones.
 * Exact matching has no such false-positive mode.
 *
 * <p>The response depends on {@code security.jwt.reject-known-defaults}:
 * {@code true} fails startup; {@code false} logs a warning and continues, which
 * keeps local development, the e2e stack and the CI compose job bootable.
 */
public final class JwtSecretPolicy {

    /**
     * Secrets that ship inside this repository and are therefore public. Any of
     * these values must never sign a real token.
     */
    static final Set<String> KNOWN_DEFAULTS = Set.of(
            "local-course-jwt-secret-change-me",
            "local-course-refresh-secret-change-me",
            "edge-e2e-jwt-secret-0123456789abcdef",
            "edge-e2e-refresh-secret-0123456789abcdef",
            "replace-with-a-long-random-access-secret",
            "replace-with-a-long-random-refresh-secret");

    /** HS256 requires a 256-bit key; UTF-8 is 1 byte/char for these values. */
    static final int MINIMUM_LENGTH = 32;

    private static final Logger LOG = LoggerFactory.getLogger(JwtSecretPolicy.class);

    private JwtSecretPolicy() {
    }

    /**
     * Validates a resolved signing secret.
     *
     * @param variable the environment variable name, used in messages
     * @param secret   the resolved secret value
     * @param strict   {@code true} to abort startup, {@code false} to warn only
     * @throws IllegalStateException when the secret is missing, too short, or a
     *                               known default while {@code strict} is set
     */
    static void enforce(String variable, String secret, boolean strict) {
        if (secret == null || secret.length() < MINIMUM_LENGTH) {
            throw new IllegalStateException(variable + " must contain at least " + MINIMUM_LENGTH + " characters");
        }
        if (!isKnownDefault(secret)) {
            return;
        }
        String message = variable + " is set to a secret that is published in this repository;"
                + " generate one with 'openssl rand -base64 48'";
        if (strict) {
            throw new IllegalStateException(message);
        }
        LOG.warn("{} (startup continues because security.jwt.reject-known-defaults is not enabled)", message);
    }

    /** @return {@code true} when {@code secret} is a value shipped in this repository. */
    static boolean isKnownDefault(String secret) {
        return secret != null && KNOWN_DEFAULTS.contains(secret.strip().toLowerCase(Locale.ROOT));
    }
}
