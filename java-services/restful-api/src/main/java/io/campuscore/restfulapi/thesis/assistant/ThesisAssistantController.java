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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owner-scoped JSON/SSE assistant contract for web and mobile clients. */
@Tag(name = "AI Academic Assistant (RAG)", description = "Trợ lý AI học vụ HCM-UTE: hỏi đáp quy chế đào tạo, tư vấn đề tài khóa luận qua giao thức JSON / Server-Sent Events (SSE)")
@RestController
@Profile("persistence")
@RequestMapping({"/api/v1/assistant", "/api/v1/thesis/assistant"})
public class ThesisAssistantController {
    private static final Logger LOG = LoggerFactory.getLogger(ThesisAssistantController.class);
    private final ThesisAssistantService assistant;
    private final RagAssistantGateway ragGateway;
    private final AssistantPersonalContextAdvisor personalContext;

    /** Compatibility constructor for focused controller tests. */
    public ThesisAssistantController(ThesisAssistantService assistant) {
        this(assistant, null, null);
    }

    /** Compatibility constructor for focused controller tests. */
    public ThesisAssistantController(ThesisAssistantService assistant, RagAssistantGateway ragGateway) {
        this(assistant, ragGateway, null);
    }

    @Autowired
    public ThesisAssistantController(
            ThesisAssistantService assistant,
            RagAssistantGateway ragGateway,
            AssistantPersonalContextAdvisor personalContext) {
        this.assistant = assistant;
        this.ragGateway = ragGateway;
        this.personalContext = personalContext;
    }

    @Operation(summary = "Gửi câu hỏi tới Trợ lý AI (JSON Block Mode)", description = "Hỏi đáp quy chế đào tạo, thời khóa biểu, tiến độ và đề tài khóa luận với mô hình AI RAG")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nhận phản hồi từ trợ lý AI"),
        @ApiResponse(responseCode = "400", description = "Tin nhắn không hợp lệ"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính")
    })
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt actor) {
        AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(request.message());
        String locale = AssistantInputGuard.normalizeLocale(request.locale());
        if (!guard.allowed()) {
            return new ChatResponse(ThesisAssistantService.guardMessage(guard.reasonCode(), locale),
                    ThesisAssistantService.MODEL, true, guard.reasonCode(), locale, List.of(),
                    UUID.randomUUID(), request.clientRequestId(), null, false, "REJECTED", null, null);
        }
        if (AssistantInputGuard.isTechnicalRequest(guard.normalizedMessage())) {
            return new ChatResponse(ThesisAssistantService.technicalOutputMessage(locale),
                    ThesisAssistantService.MODEL, true, "TECHNICAL_REQUEST_BLOCKED", locale, List.of(),
                    UUID.randomUUID(), request.clientRequestId(), null, false, "REJECTED", null, null);
        }
        if (personalContext != null && personalContext.handles(request.message())) {
            ChatResponse personal = personalContext.answer(request, actor);
            if (personal != null) {
                return personal;
            }
        }
        String owner = subject(actor);
        if (remoteRag()) {
            return ragGateway.chat(request, owner);
        }
        return assistant.answer(request.message(), request.locale(), request.conversationId(), owner,
                request.clientRequestId(), request.scope());
    }

    /** Deprecated compatibility alias; clients should use /chat. */
    @Deprecated
    @PostMapping("/chat/complete")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public ChatResponse complete(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt actor) {
        return chat(request, actor);
    }

    /** Comment frame cadence: a proxy/CDN idle timeout is typically 30-60 s. */
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT_SCHEDULER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "assistant-sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    @Operation(summary = "Hỏi đáp tương tác thời gian thực (SSE Stream)", description = "Truyền luồng phản hồi từ trợ lý AI qua Server-Sent Events (SSE) kèm heartbeat giữ kết nối và bảo vệ prompt injection")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mở luồng SSE thành công", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE))
    })
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public SseEmitter stream(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt actor,
            HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(120_000L);
        // Emit an SSE comment frame while the emitter is open so a proxy or CDN
        // cannot idle-kill the connection during a slow model start. Comment
        // frames (":heartbeat") are transport-level noise: SSE clients ignore
        // them, and the frontend stream parser skips them without disturbing
        // event ordering or the delta sequence.
        java.util.concurrent.ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleWithFixedDelay(
                () -> sendHeartbeat(emitter), HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        Runnable stopHeartbeat = () -> heartbeat.cancel(false);
        emitter.onCompletion(stopHeartbeat);
        emitter.onTimeout(stopHeartbeat);
        String owner = subject(actor);
        Consumer<ThesisAssistantService.StreamEvent> sink = event -> send(emitter, event);
        try {
            AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(request.message());
            if (!guard.allowed()) {
                sendError(emitter, guard.reasonCode(), false);
                emitter.complete();
                return emitter;
            }
            if (AssistantInputGuard.isTechnicalRequest(guard.normalizedMessage())) {
                sendError(emitter, "TECHNICAL_REQUEST_BLOCKED", false);
                emitter.complete();
                return emitter;
            }
            ChatResponse personal = personalContext != null && personalContext.handles(request.message())
                    ? personalContext.answer(request, actor) : null;
            if (personal != null) {
                personalContext.stream(personal, request, sink);
            } else if (remoteRag()) {
                ragGateway.stream(request, owner, sink);
            } else {
                assistant.stream(request.message(), request.locale(), request.conversationId(), owner,
                        request.clientRequestId(), sink, request.scope());
            }
            emitter.complete();
        } catch (DomainException exception) {
            sendError(emitter, exception.code(), exception.status().is5xxServerError() || exception.status() == HttpStatus.TOO_MANY_REQUESTS);
            emitter.complete();
        } catch (Exception exception) {
            LOG.warn("assistant stream failed with {}", exception.getClass().getSimpleName());
            sendError(emitter, "ASSISTANT_UNAVAILABLE", true);
            emitter.complete();
        } finally {
            stopHeartbeat.run();
        }
        return emitter;
    }

    @Operation(summary = "Hủy yêu cầu trả lời đang xử lý", description = "Hủy lượt phản hồi của model dựa trên clientRequestId của lượt gọi")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Hủy thành công")
    })
    @PostMapping("/requests/{clientRequestId}/cancel")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public CancelResponse cancel(
            @Parameter(description = "Mã định danh yêu cầu của client (UUID)", required = true) @PathVariable UUID clientRequestId,
            @AuthenticationPrincipal Jwt actor) {
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

    @Operation(summary = "Gửi đánh giá câu trả lời của AI", description = "Người dùng xếp hạng hữu ích (like/dislike) và gửi lý do góp ý")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Gửi đánh giá thành công")
    })
    @PutMapping("/messages/{messageId}/feedback")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public FeedbackResponse feedback(
            @Parameter(description = "Mã định danh tin nhắn AI (UUID)", required = true) @PathVariable UUID messageId,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal Jwt actor) {
        String owner = subject(actor);
        if (remoteRag()) {
            return ragGateway.feedback(messageId, request, owner);
        }
        assistant.setFeedback(messageId, owner, request.rating(), request.reason());
        return new FeedbackResponse(messageId, request.rating(), request.reason(), false);
    }

    @Operation(summary = "Xóa đánh giá câu trả lời của AI", description = "Hủy xếp hạng đã gửi cho tin nhắn")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Xóa đánh giá thành công")
    })
    @DeleteMapping("/messages/{messageId}/feedback")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(
            @Parameter(description = "Mã định danh tin nhắn (UUID)", required = true) @PathVariable UUID messageId,
            @AuthenticationPrincipal Jwt actor) {
        String owner = subject(actor);
        if (remoteRag()) {
            ragGateway.deleteFeedback(messageId, owner);
            return;
        }
        assistant.deleteFeedback(messageId, owner);
    }

    @Operation(summary = "Danh sách phiên hội thoại của người dùng", description = "Truy vấn danh sách các cuộc trò chuyện trước đây kèm con trỏ phân trang")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy danh sách phiên hội thoại thành công")
    })
    @GetMapping("/conversations")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public List<ThesisAssistantRepository.Conversation> conversations(
            @AuthenticationPrincipal Jwt actor,
            @Parameter(description = "Giới hạn số bản ghi") @RequestParam(required = false) Integer limit,
            @Parameter(description = "Con trỏ phân trang cursor") @RequestParam(required = false) String cursor,
            HttpServletResponse response) {
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

    @Operation(summary = "Tạo mới phiên hội thoại", description = "Khởi tạo phiên hội thoại mới với ngôn ngữ chỉ định (vi/en)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tạo phiên thành công")
    })
    @PostMapping("/conversations")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
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

    @Operation(summary = "Lấy lịch sử tin nhắn trong phiên hội thoại", description = "Truy xuất danh sách tin nhắn hỏi và đáp theo thứ tự thời gian")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lấy lịch sử tin nhắn thành công")
    })
    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public List<ThesisAssistantRepository.Message> messages(
            @Parameter(description = "Mã định danh phiên hội thoại (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor,
            @Parameter(description = "Giới hạn số tin nhắn") @RequestParam(required = false) Integer limit,
            @Parameter(description = "Con trỏ phân trang cursor") @RequestParam(required = false) String cursor,
            HttpServletResponse response) {
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

    @Operation(summary = "Xóa phiên hội thoại", description = "Xóa vĩnh viễn cuộc trò chuyện và toàn bộ lịch sử tin nhắn tương ứng")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Xóa phiên thành công")
    })
    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(
            @Parameter(description = "Mã định danh phiên hội thoại (UUID)", required = true) @PathVariable UUID id,
            @AuthenticationPrincipal Jwt actor) {
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
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(name).data(event));
            }
        } catch (Exception exception) {
            // A browser disconnect is a transport concern, not a provider or
            // ledger failure. Mark the emitter closed and let the service finish
            // its terminal CAS so the same clientRequestId can replay safely.
            try { emitter.completeWithError(exception); } catch (Exception ignored) { }
        }
    }

    /** Transport-level keepalive; an SSE comment is ignored by every conforming parser. */
    private static void sendHeartbeat(SseEmitter emitter) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            }
        } catch (Exception exception) {
            // The connection died while the model was still working. The
            // worker thread's own send will fail and complete the emitter,
            // which cancels the heartbeat via onCompletion.
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
