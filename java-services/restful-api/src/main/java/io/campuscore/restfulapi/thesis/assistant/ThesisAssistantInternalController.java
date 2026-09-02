package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.FeedbackRequest;
import io.campuscore.restfulapi.web.DomainException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "assistant.rag", name = "service-mode", havingValue = "true")
@RequestMapping({"/internal/rag/assistant", "/internal/rag/thesis/assistant"})
public class ThesisAssistantInternalController {
    private static final Logger LOG = LoggerFactory.getLogger(ThesisAssistantInternalController.class);

    private final ThesisAssistantService assistant;
    private final AssistantRagProperties properties;

    public ThesisAssistantInternalController(ThesisAssistantService assistant, AssistantRagProperties properties) {
        this.assistant = assistant;
        this.properties = properties;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner) {
        verify(token);
        return assistant.answer(request.message(), request.locale(), request.conversationId(), owner(owner), request.clientRequestId());
    }

    @Deprecated
    @PostMapping("/chat/complete")
    public ChatResponse complete(@Valid @RequestBody ChatRequest request,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner) {
        return chat(request, token, owner);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner) {
        verify(token);
        String ownerId = owner(owner);
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean terminal = new AtomicBoolean(false);
        try {
            assistant.stream(request.message(), request.locale(), request.conversationId(), ownerId,
                    request.clientRequestId(), event -> {
                        if (event instanceof ThesisAssistantService.StreamDone
                                || event instanceof ThesisAssistantService.StreamError) {
                            terminal.set(true);
                        }
                        send(emitter, event);
                    });
            if (terminal.compareAndSet(false, true)) {
                sendError(emitter, "ASSISTANT_STREAM_INCOMPLETE", true);
            }
            emitter.complete();
        } catch (DomainException exception) {
            terminal.set(true);
            sendError(emitter, exception.code(), exception.status().is5xxServerError()
                    || exception.status() == HttpStatus.TOO_MANY_REQUESTS);
            emitter.complete();
        } catch (Exception exception) {
            terminal.set(true);
            LOG.warn("internal assistant stream failed with {}", exception.getClass().getSimpleName());
            sendError(emitter, "ASSISTANT_UNAVAILABLE", true);
            emitter.complete();
        }
        return emitter;
    }

    @PostMapping("/requests/{clientRequestId}/cancel")
    public CancelResponse cancel(@PathVariable UUID clientRequestId,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner) {
        verify(token);
        var result = assistant.cancel(clientRequestId, owner(owner));
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
    public FeedbackResponse feedback(@PathVariable UUID messageId, @Valid @RequestBody FeedbackRequest request,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner) {
        verify(token);
        assistant.setFeedback(messageId, owner(owner), request.rating(), request.reason());
        return new FeedbackResponse(messageId, request.rating(), request.reason(), false);
    }

    @DeleteMapping("/messages/{messageId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(@PathVariable UUID messageId,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner) {
        verify(token);
        assistant.deleteFeedback(messageId, owner(owner));
    }

    @GetMapping("/conversations")
    public List<ThesisAssistantRepository.Conversation> conversations(
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            jakarta.servlet.http.HttpServletResponse response) {
        verify(token);
        ThesisAssistantRepository.ConversationPage page = assistant.conversationPage(owner(owner), limit, cursor);
        if (page.nextCursor() != null) response.setHeader("X-Next-Cursor", page.nextCursor());
        return page.data();
    }

    @PostMapping("/conversations")
    public ConversationCreated createConversation(@RequestBody(required = false) CreateConversationRequest request,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner) {
        verify(token);
        String locale = request == null ? "vi" : request.locale();
        if (locale != null && !locale.isBlank() && !locale.equals("vi") && !locale.equals("en")) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_LOCALE", "locale must be en or vi");
        }
        String id = assistant.createConversation(owner(owner), locale);
        return new ConversationCreated(id, locale == null || locale.isBlank() ? "vi" : locale);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ThesisAssistantRepository.Message> messages(@PathVariable UUID id,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            jakarta.servlet.http.HttpServletResponse response) {
        verify(token);
        ThesisAssistantRepository.MessagePage page = assistant.messagePage(id, owner(owner), limit, cursor);
        if (page.nextCursor() != null) response.setHeader("X-Next-Cursor", page.nextCursor());
        return page.data();
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable UUID id,
            @RequestHeader(name = "X-Rag-Service-Token", required = false) String token,
            @RequestHeader(name = "X-Assistant-Owner", required = false) String owner) {
        verify(token);
        assistant.deleteConversation(id, owner(owner));
    }

    private void verify(String token) {
        if (!properties.serviceMode()) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_SERVICE_DISABLED", "RAG service mode is disabled");
        }
        if (!properties.tokenConfigured()) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_SERVICE_TOKEN_MISSING", "RAG service token is not configured");
        }
        if (token == null || token.isBlank()) {
            throw new DomainException(HttpStatus.FORBIDDEN, "RAG_SERVICE_UNAUTHORIZED", "RAG service token is required");
        }
        byte[] expected = properties.serviceToken().getBytes(StandardCharsets.UTF_8);
        byte[] presented = token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, presented)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "RAG_SERVICE_UNAUTHORIZED", "RAG service token is invalid");
        }
    }

    private static String owner(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Assistant owner is required");
        }
        return value;
    }

    private static void send(SseEmitter emitter, ThesisAssistantService.StreamEvent event) {
        try {
            String name = eventName(event);
            emitter.send(SseEmitter.event().name(name).data(event));
        } catch (Exception exception) {
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

    public record StreamError(String code, boolean retryable) {
        @com.fasterxml.jackson.annotation.JsonProperty("type")
        public String type() { return "error"; }
    }
    public record CancelResponse(UUID clientRequestId, String status) { }
    public record FeedbackResponse(UUID messageId, String rating, String reason, boolean removed) { }
    public record CreateConversationRequest(String locale) { }
    public record ConversationCreated(String id, String locale) { }
}
