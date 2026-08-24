package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepSeekClientTest {
    @Test
    void disabledProviderFailsBeforeAnyNetworkCall() {
        DeepSeekProperties properties = new DeepSeekProperties(false, "", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800);
        DeepSeekClient client = new DeepSeekClient(properties, new ObjectMapper());
        assertThrows(DeepSeekClient.ProviderUnavailableException.class,
                () -> client.complete("question", "context", "vi"));
    }

    @Test
    void hostileBaseUrlIsRejected() {
        DeepSeekProperties properties = new DeepSeekProperties(true, "fixture", "https://api.deepseek.com.evil", "deepseek-v4-flash", 8000, 800);
        DeepSeekClient client = new DeepSeekClient(properties, new ObjectMapper());
        assertThrows(DeepSeekClient.ProviderUnavailableException.class,
                () -> client.complete("question", "context", "vi"));
    }

    @Test
    void providerStreamRequiresDoneMarkerAndTerminalStopReason() {
        DeepSeekProperties properties = new DeepSeekProperties(false, "", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800);
        DeepSeekClient client = new DeepSeekClient(properties, new ObjectMapper());
        var request = new AssistantCompletionProvider.CompletionRequest("question", "en", "context", List.of("source-1"));
        String missingDone = "data: {\"choices\":[{\"delta\":{\"content\":\"answer\"},\"finish_reason\":\"stop\"}]}\n\n";
        DeepSeekClient.ProviderUnavailableException exception = assertThrows(DeepSeekClient.ProviderUnavailableException.class,
                () -> client.parseBody(new ByteArrayInputStream(missingDone.getBytes(StandardCharsets.UTF_8)), request,
                        ignored -> { }, () -> false, Instant.now().plusSeconds(2)));
        org.junit.jupiter.api.Assertions.assertTrue(exception.malformed());

        String valid = "data: {\"choices\":[{\"delta\":{\"content\":\"answer\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + ": keep-alive\n\n"
                + "data: [DONE]\n\n";
        var result = client.parseBody(new ByteArrayInputStream(valid.getBytes(StandardCharsets.UTF_8)), request,
                ignored -> { }, () -> false, Instant.now().plusSeconds(2));
        assertEquals("answer", result.answer());
        assertEquals("stop", result.finishReason());
    }
}
