package io.campuscore.restfulapi.auth.mail;

import java.time.Instant;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.context.i18n.LocaleContextHolder;

/** Publishes lifecycle mail requests without coupling auth transactions to SMTP. */
@Component
@Profile("persistence")
public class AuthMailService {

    private final ApplicationEventPublisher events;

    public AuthMailService(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void queueVerification(String recipient, String firstName, String rawToken, Instant expiresAt) {
        events.publishEvent(new AuthMailEvent(
                recipient,
                firstName,
                locale(),
                io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose.EMAIL_VERIFICATION,
                rawToken,
                expiresAt));
    }

    public void queuePasswordReset(String recipient, String firstName, String rawToken, Instant expiresAt) {
        events.publishEvent(new AuthMailEvent(
                recipient,
                firstName,
                locale(),
                io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose.PASSWORD_RESET,
                rawToken,
                expiresAt));
    }

    private static String locale() {
        String language = LocaleContextHolder.getLocale().getLanguage().toLowerCase(Locale.ROOT);
        return "en".equals(language) ? "en" : "vi";
    }
}
