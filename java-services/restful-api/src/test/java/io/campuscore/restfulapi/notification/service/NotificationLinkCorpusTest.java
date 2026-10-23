package io.campuscore.restfulapi.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.web.DomainException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract test: every entry of the shared corpus must classify the same way
 * through the Java allowlist that the frontend safe-path module and migration
 * V142 enforce. The corpus file is the single source of truth for all three.
 */
class NotificationLinkCorpusTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void everyCorpusValueClassifiesTheSame() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/notification/link-corpus.json")) {
            assertThat(in).as("link-corpus.json on the test classpath").isNotNull();
            root = MAPPER.readTree(in);
        }

        for (JsonNode safe : root.get("safe")) {
            assertThat(NotificationLinkPolicy.requireSafe(safe.asText()))
                    .as("safe value %s", safe.asText())
                    .isEqualTo(safe.asText().trim());
        }

        for (JsonNode unsafe : root.get("unsafe")) {
            String value = unsafe.get("value").asText();
            assertThatThrownBy(() -> NotificationLinkPolicy.requireSafe(value))
                    .as("unsafe value %s (%s)", value, unsafe.get("why").asText(""))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("single /");
        }
    }
}
