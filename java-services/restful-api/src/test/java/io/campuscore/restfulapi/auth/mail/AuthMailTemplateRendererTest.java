package io.campuscore.restfulapi.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.auth.repository.AuthChallengeRepository.Purpose;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthMailTemplateRendererTest {

    private final AuthMailTemplateRenderer renderer = new AuthMailTemplateRenderer();

    @Test
    void rendersVietnameseHtmlAndTextWithEscapedAllowlistedValues() {
        AuthMailEvent event = new AuthMailEvent(
                "student@example.test",
                "<script>alert(1)</script>",
                "vi",
                Purpose.EMAIL_VERIFICATION,
                "opaque-token",
                Instant.now().plusSeconds(3600));

        var content = renderer.render(event, "https://campuscore.test/verify-email?token=a&next=b");

        assertThat(content.subject()).contains("Xác minh");
        assertThat(content.text()).contains("<script>alert(1)</script>");
        assertThat(content.html())
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("token=a&amp;next=b")
                .doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void rendersEnglishPasswordResetWithPlainTextFallback() {
        AuthMailEvent event = new AuthMailEvent(
                "student@example.test",
                "Taylor",
                "en",
                Purpose.PASSWORD_RESET,
                "opaque-token",
                Instant.now().plusSeconds(1800));

        var content = renderer.render(event, "https://campuscore.test/reset-password?token=opaque-token");

        assertThat(content.subject()).isEqualTo("Reset your CampusCore password");
        assertThat(content.text()).contains("can be used only once");
        assertThat(content.html()).contains("Reset password");
    }
}
