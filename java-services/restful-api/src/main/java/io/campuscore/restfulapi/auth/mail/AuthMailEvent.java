package io.campuscore.restfulapi.auth.mail;

import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose;
import java.time.Instant;

/** In-memory after-commit mail request; rawToken is never persisted or logged. */
public record AuthMailEvent(
        String recipient,
        String firstName,
        String locale,
        Purpose purpose,
        String rawToken,
        Instant expiresAt) {
}
