package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.FeedbackRequest;
import io.campuscore.restfulapi.web.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owner-scoped JSON/SSE assistant contract for web and mobile clients. */
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/thesis/assistant")
public class ThesisAssistantController {
    private static final Logger LOG = LoggerFactory.getLogger(ThesisAssistantController.class);
    private final ThesisAssistantService assistant;
    private final RagAssistantGateway ragGateway;

    /** Compatibility constructor for focused controller tests. */
    public ThesisAssistantController(ThesisAssistantService assistant) {
        this(assistant, null);
    }

    @Autowired
    public ThesisAssistantController(ThesisAssistantService assistant, RagAssistantGateway ragGateway) {
        this.assistant = assistant;
        this.ragGateway = ragGateway;
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt actor) {
        String owner = subject(actor);
        if (remoteRag()) {
            return ragGateway.chat(request, owner);
        }
        return assistant.answer(request.message(), request.locale(), request.conversationId(), owner, request.clientRequestId());
    }

    /** Deprecated compatibility alias; clients should use /chat. */
    @Deprecated
    @PostMapping("/chat/complete")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    public ChatResponse complete(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt actor) {
        return chat(request, actor);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    public SseEmitter stream(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt actor,
            HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String owner = subject(actor);
        Consumer<ThesisAssistantService.StreamEvent> sink = event -> send(emitter, event);
        try {
            if (remoteRag()) {
                ragGateway.stream(request, owner, sink);
            } else {
                assistant.stream(request.message(), request.locale(), request.conversationId(), owner,
                        request.clientRequestId(), sink);
            }
            emitter.complete();
        } catch (DomainException exception) {
            sendError(emitter, exception.code(), exception.status().is5xxServerError() || exception.status() == HttpStatus.TOO_MANY_REQUESTS);
            emitter.complete();
        } catch (Exception exception) {
            LOG.warn("assistant stream failed with {}", exception.getClass().getSimpleName());
            sendError(emitter, "ASSISTANT_UNAVAILABLE", true);
            emitter.complete();
        }
        return emitter;
    }

    @PostMapping("/requests/{clientRequestId}/cancel")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    public CancelResponse cancel(@PathVariable UUID clientRequestId, @AuthenticationPrincipal Jwt actor) {
        String owner = subject(actor);
        if (remoteRag()) {
            return ragGateway.cancel(clientRequestId, owner);
        }
        var result = assistant.cancel(clientRequestId, owner);
        if (!result.cancelled() && "COMPLETED".equals(result.status())) {
            throw new DomainException(HttpStatus.CONFLICT, "TURN_COMPLETED", "Completed turns are replayable and cannot be cancelled");
        }
        if (!result.cancelled() && "PURGED".equals(result.status())) {
            throw new DomainException(HttpStatus.GONE, "TURN_PURGED", "Turn has been purged");
        }
        if (!result.cancelled() && ("FAILED_AMBIGUOUS".equals(result.status()) || "TERMINAL_RACE".equals(result.status()))) {
            throw new DomainException(HttpStatus.CONFLICT, "FAILED_AMBIGUOUS", "The provider outcome is ambiguous; automatic redispatch is disabled");
        }
        return new CancelResponse(clientRequestId, result.status());
    }

    @PutMapping("/messages/{messageId}/feedback")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    public FeedbackResponse feedback(@PathVariable UUID messageId, @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal Jwt actor) {
        String owner = subject(actor);
        if (remoteRag()) {
            return ragGateway.feedback(messageId, request, owner);
        }
        assistant.setFeedback(messageId, owner, request.rating(), request.reason());
        return new FeedbackResponse(messageId, request.rating(), request.reason(), false);
    }

    @DeleteMapping("/messages/{messageId}/feedback")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(@PathVariable UUID messageId, @AuthenticationPrincipal Jwt actor) {
        String owner = subject(actor);
        if (remoteRag()) {
            ragGateway.deleteFeedback(messageId, owner);
            return;
        }
        assistant.deleteFeedback(messageId, owner);
    }

    @GetMapping("/conversations")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    public List<ThesisAssistantRepository.Conversation> conversations(
            @AuthenticationPrincipal Jwt actor, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor, HttpServletResponse response) {
        String owner = subject(actor);
        if (remoteRag()) {
            RagAssistantGateway.RemotePage<List<ThesisAssistantRepository.Conversation>> page =
                    ragGateway.conversations(owner, limit, cursor);
            if (page.nextCursor() != null) response.setHeader("X-Next-Cursor", page.nextCursor());
            return page.data();
        }
        ThesisAssistantRepository.ConversationPage page = assistant.conversationPage(owner, limit, cursor);
        if (page.nextCursor() != null) response.setHeader("X-Next-Cursor", page.nextCursor());
        return page.data();
    }

    @PostMapping("/conversations")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    public ConversationCreated createConversation(@RequestBody(required = false) CreateConversationRequest request,
            @AuthenticationPrincipal Jwt actor) {
        String locale = request == null ? "vi" : request.locale();
        if (locale != null && !locale.isBlank() && !locale.equals("vi") && !locale.equals("en")) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_LOCALE", "locale must be en or vi");
        }
        String owner = subject(actor);
        if (remoteRag()) {
            return ragGateway.createConversation(request, owner);
        }
        String id = assistant.createConversation(owner, locale);
        return new ConversationCreated(id, locale == null || locale.isBlank() ? "vi" : locale);
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    public List<ThesisAssistantRepository.Message> messages(@PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor, HttpServletResponse response) {
        String owner = subject(actor);
        if (remoteRag()) {
            RagAssistantGateway.RemotePage<List<ThesisAssistantRepository.Message>> page =
                    ragGateway.messages(id, owner, limit, cursor);
            if (page.nextCursor() != null) response.setHeader("X-Next-Cursor", page.nextCursor());
            return page.data();
        }
        ThesisAssistantRepository.MessagePage page = assistant.messagePage(id, owner, limit, cursor);
        if (page.nextCursor() != null) response.setHeader("X-Next-Cursor", page.nextCursor());
        return page.data();
    }

    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable UUID id, @AuthenticationPrincipal Jwt actor) {
        String owner = subject(actor);
        if (remoteRag()) {
            ragGateway.deleteConversation(id, owner);
            return;
        }
        assistant.deleteConversation(id, owner);
    }

    private boolean remoteRag() {
        return ragGateway != null && ragGateway.enabled();
    }

    private static void send(SseEmitter emitter, ThesisAssistantService.StreamEvent event) {
        try {
            String name = eventName(event);
            emitter.send(SseEmitter.event().name(name).data(event));
        } catch (Exception exception) {
            // A browser disconnect is a transport concern, not a provider or
            // ledger failure. Mark the emitter closed and let the service finish
            // its terminal CAS so the same clientRequestId can replay safely.
            try { emitter.completeWithError(exception); } catch (Exception ignored) { }
        }
    }

    private static void sendError(SseEmitter emitter, String code, boolean retryable) {
        try { emitter.send(SseEmitter.event().name("error").data(new StreamError(code, retryable))); }
        catch (Exception ignored) { emitter.completeWithError(ignored); }
    }

    private static String eventName(ThesisAssistantService.StreamEvent event) {
        if (event instanceof ThesisAssistantService.StreamMeta) return "meta";
        if (event instanceof ThesisAssistantService.StreamDelta) return "delta";
        if (event instanceof ThesisAssistantService.StreamReplace) return "replace";
        if (event instanceof ThesisAssistantService.StreamCitation) return "citation";
        if (event instanceof ThesisAssistantService.StreamError) return "error";
        return "done";
    }

    private static String subject(Jwt actor) {
        String subject = actor == null ? null : actor.getSubject();
        if (subject == null || subject.isBlank()) throw new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        return subject;
    }

    public record StreamError(String code, boolean retryable) {
        @com.fasterxml.jackson.annotation.JsonProperty("type")
        public String type() { return "error"; }
    }
    public record CancelResponse(UUID clientRequestId, String status) { }
    public record FeedbackResponse(UUID messageId, String rating, String reason, boolean removed) { }
    public record CreateConversationRequest(String locale) { }
    public record ConversationCreated(String id, String locale) { }
}
