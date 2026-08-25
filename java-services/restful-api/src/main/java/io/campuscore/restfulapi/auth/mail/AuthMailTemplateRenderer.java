package io.campuscore.restfulapi.auth.mail;

import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Renders allowlisted bilingual text/HTML templates without persisting tokens. */
@Component
final class AuthMailTemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}");

    MailContent render(AuthMailEvent event, String actionUrl) {
        String locale = "en".equalsIgnoreCase(event.locale()) ? "en" : "vi";
        String templateName = event.purpose() == Purpose.EMAIL_VERIFICATION
                ? "verify-email"
                : "reset-password";
        long minutes = Math.max(1, Duration.between(Instant.now(), event.expiresAt()).toMinutes());
        Map<String, String> values = Map.of(
                "firstName", safeName(event.firstName(), locale),
                "actionUrl", actionUrl,
                "expiresMinutes", Long.toString(minutes));
        String text = substitute(load(locale, templateName, "txt"), values, false);
        String html = substitute(load(locale, templateName, "html"), values, true);
        String subject = event.purpose() == Purpose.EMAIL_VERIFICATION
                ? ("en".equals(locale) ? "Verify your CampusCore email" : "Xác minh email CampusCore")
                : ("en".equals(locale) ? "Reset your CampusCore password" : "Đặt lại mật khẩu CampusCore");
        return new MailContent(subject, text, html);
    }

    private static String load(String locale, String template, String extension) {
        String resource = "templates/mail/" + locale + '/' + template + '.' + extension;
        try (var input = new ClassPathResource(resource).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Auth mail template is unavailable", exception);
        }
    }

    private static String substitute(String template, Map<String, String> values, boolean html) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = values.get(name);
            if (value == null) {
                throw new IllegalStateException("Auth mail template contains a non-allowlisted variable");
            }
            matcher.appendReplacement(
                    rendered,
                    Matcher.quoteReplacement(html ? HtmlUtils.htmlEscape(value) : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String safeName(String firstName, String locale) {
        if (firstName == null || firstName.isBlank()) {
            return "en".equals(locale) ? "CampusCore user" : "bạn";
        }
        return firstName.trim();
    }

    record MailContent(String subject, String text, String html) {
    }
}
