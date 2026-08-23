package io.campuscore.restfulapi.thesis.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ThesisAssistantDtos {

    private ThesisAssistantDtos() {
    }

    public record ChatRequest(
            @NotBlank(message = "message is required")
            @Size(max = 2000, message = "message must contain at most 2000 characters")
            String message,
            @Pattern(regexp = "^(en|vi)$", message = "locale must be en or vi")
            String locale) {
    }

    public record Citation(
            String id,
            String slug,
            String title,
            String source,
            String locale,
            String excerpt) {
    }

    public record ChatResponse(
            String answer,
            String model,
            boolean degraded,
            String reasonCode,
            String locale,
            List<Citation> citations) {
    }
}
