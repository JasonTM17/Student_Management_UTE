package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Feature-gated thesis assistant contract for web and mobile clients. */
@RestController
@ConditionalOnProperty(prefix = "migration.thesis-assistant", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/thesis/assistant")
public class ThesisAssistantController {

    private final ThesisAssistantService assistant;

    public ThesisAssistantController(ThesisAssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("chat")
    @PreAuthorize("isAuthenticated()")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return assistant.answer(request.message(), request.locale());
    }
}
