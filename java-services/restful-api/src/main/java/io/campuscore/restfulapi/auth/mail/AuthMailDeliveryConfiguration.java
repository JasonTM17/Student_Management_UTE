package io.campuscore.restfulapi.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/** Selects no-op local delivery or SMTP/Mailpit without embedding credentials. */
@Configuration
@Profile("persistence")
public class AuthMailDeliveryConfiguration {

    @Bean
    @ConditionalOnProperty(name = "mail.enabled", havingValue = "false", matchIfMissing = true)
    AuthMailDelivery disabledAuthMailDelivery() {
        return event -> {
            // Lifecycle state remains testable when SMTP is intentionally off.
        };
    }

    @Bean
    @ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
    AuthMailDelivery smtpAuthMailDelivery(
            JavaMailSender sender,
            @Value("${mail.from:no-reply@campuscore.local}") String from,
            @Value("${auth.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            AuthMailTemplateRenderer templates) {
        return new SmtpAuthMailDelivery(sender, from, frontendBaseUrl, templates);
    }
}
