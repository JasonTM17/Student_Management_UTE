package io.campuscore.thesis.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.thesis.security.AccessContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantPromptTest {

    @Test
    void makesReadOnlyAndUntrustedContextRulesExplicit() {
        AccessContext actor = new AccessContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                Set.of("STUDENT"),
                Set.of("thesis:group:create"));

        String prompt = AssistantPrompt.systemMessage("vi", actor, "round status=REGISTRATION_OPEN");

        assertThat(prompt).contains("Vietnamese");
        assertThat(prompt).contains("read-only");
        assertThat(prompt).contains("untrusted data");
        assertThat(prompt).contains("REGISTRATION_OPEN");
        assertThat(prompt).doesNotContain("JWT_SECRET");
    }
}
