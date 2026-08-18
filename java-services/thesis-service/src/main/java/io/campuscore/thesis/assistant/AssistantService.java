package io.campuscore.thesis.assistant;

import io.campuscore.thesis.security.AccessContext;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.ChatRequest;
import io.campuscore.thesis.web.ThesisDtos.ChatResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AssistantService {

    private final AssistantProperties properties;
    private final AssistantRateLimiter rateLimiter;
    private final AssistantContextService contextService;
    private final RestClient restClient;

    public AssistantService(
            AssistantProperties properties,
            AssistantRateLimiter rateLimiter,
            AssistantContextService contextService,
            RestClient assistantRestClient) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.contextService = contextService;
        this.restClient = assistantRestClient;
    }

    public ChatResponse chat(ChatRequest request, AccessContext actor) {
        if (!properties.isConfigured()) {
            throw new DomainExceptions.ServiceUnavailable("Assistant provider is not configured");
        }
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isBlank() || message.length() > properties.getMaxMessageLength()) {
            throw new DomainExceptions.Conflict("Message is empty or exceeds the configured limit");
        }
        if (!rateLimiter.allow(actor.userId(), properties.getMaxRequestsPerMinute())) {
            throw new DomainExceptions.RateLimited("Assistant rate limit exceeded or limiter unavailable");
        }

        String systemMessage = AssistantPrompt.systemMessage(request.locale(), actor, contextService.build(actor));
        Map<String, Object> payload = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemMessage),
                        Map.of("role", "user", "content", message)));

        try {
            ProviderResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(ProviderResponse.class);
            String answer = response == null || response.choices() == null || response.choices().isEmpty()
                    ? ""
                    : response.choices().getFirst().message().content();
            if (answer == null || answer.isBlank()) {
                throw new DomainExceptions.ServiceUnavailable("Assistant provider returned no answer");
            }
            return new ChatResponse(answer.trim(), properties.getModel(), false);
        } catch (RestClientException exception) {
            throw new DomainExceptions.ServiceUnavailable("Assistant provider is temporarily unavailable");
        }
    }

    public record ProviderResponse(List<ProviderChoice> choices) {
    }

    public record ProviderChoice(ProviderMessage message) {
    }

    public record ProviderMessage(String content) {
    }
}
