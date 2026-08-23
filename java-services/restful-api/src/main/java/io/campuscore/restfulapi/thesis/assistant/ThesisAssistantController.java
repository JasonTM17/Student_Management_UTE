package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Curated thesis assistant contract for web and mobile clients. */
@RestController
@Profile("persistence")
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
