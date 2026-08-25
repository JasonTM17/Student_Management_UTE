package io.campuscore.restfulapi.auth.mail;

import io.campuscore.restfulapi.auth.mail.AuthMailTemplateRenderer.MailContent;
import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose;
import jakarta.mail.MessagingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

final class SmtpAuthMailDelivery implements AuthMailDelivery {

    private final JavaMailSender sender;
    private final String from;
    private final String frontendBaseUrl;
    private final AuthMailTemplateRenderer templates;

    SmtpAuthMailDelivery(
            JavaMailSender sender,
            String from,
            String frontendBaseUrl,
            AuthMailTemplateRenderer templates) {
        this.sender = sender;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.templates = templates;
    }

    @Override
    public void send(AuthMailEvent event) {
        String actionUrl = actionUrl(frontendBaseUrl, event.purpose(), event.rawToken());
        MailContent content = templates.render(event, actionUrl);
        var message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(event.recipient());
            helper.setSubject(content.subject());
            helper.setText(content.text(), content.html());
        } catch (MessagingException exception) {
            throw new IllegalStateException("Auth mail message could not be constructed", exception);
        }
        sender.send(message);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String actionUrl(String frontendBaseUrl, Purpose purpose, String rawToken) {
        String actionPath = purpose == Purpose.EMAIL_VERIFICATION
                ? "/verify-email"
                : "/reset-password";
        // URL fragments stay client-side and therefore keep raw challenges out
        // of reverse-proxy, servlet and frontend access logs.
        return frontendBaseUrl.replaceAll("/+$", "") + actionPath + "#token=" + encode(rawToken);
    }
}
