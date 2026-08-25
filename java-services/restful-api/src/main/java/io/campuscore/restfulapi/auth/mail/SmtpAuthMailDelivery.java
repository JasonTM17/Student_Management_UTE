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
        String actionPath = event.purpose() == Purpose.EMAIL_VERIFICATION
                ? "/verify-email"
                : "/reset-password";
        String actionUrl = frontendBaseUrl + actionPath + "?token=" + encode(event.rawToken());
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
}
