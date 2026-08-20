package io.campuscore.restfulapi.thesis.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

    public record ChatResponse(String answer, String model, boolean degraded) {
    }
}
