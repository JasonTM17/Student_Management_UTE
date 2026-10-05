package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.FeedbackRequest;
import io.campuscore.restfulapi.web.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.campuscore.restfulapi.security.DatabaseAvailabilityTracker;
import org.springframework.dao.DataAccessException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
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
    private final AssistantRlsState rlsState;
    private final Executor streamExecutor;

    /** Compatibility constructor for focused controller tests. */
    public ThesisAssistantController(ThesisAssistantService assistant) {
        this(assistant, null, null);
    }

    /** Compatibility constructor for focused controller tests. */
    public ThesisAssistantController(ThesisAssistantService assistant, RagAssistantGateway ragGateway) {
        this(assistant, ragGateway, null);
    }

    /** Compatibility constructor: no RLS state means the degraded gate is inactive. */
    public ThesisAssistantController(
            ThesisAssistantService assistant,
            RagAssistantGateway ragGateway,
            AssistantPersonalContextAdvisor personalContext) {
        this(assistant, ragGateway, personalContext, null);
    }

    public ThesisAssistantController(
            ThesisAssistantService assistant,
            RagAssistantGateway ragGateway,
            AssistantPersonalContextAdvisor personalContext,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AssistantRlsState rlsState) {
        this(assistant, ragGateway, personalContext, rlsState, Runnable::run);
    }

    @Autowired
    public ThesisAssistantController(
            ThesisAssistantService assistant,
            RagAssistantGateway ragGateway,
            AssistantPersonalContextAdvisor personalContext,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AssistantRlsState rlsState,
            AssistantStreamExecutor streamExecutor) {
        this(assistant, ragGateway, personalContext, rlsState, streamExecutor::execute);
    }

    /** Controlled executor seam for deterministic transport and ownership tests. */
    ThesisAssistantController(
            ThesisAssistantService assistant,
            RagAssistantGateway ragGateway,
            AssistantPersonalContextAdvisor personalContext,
            AssistantRlsState rlsState,
            Executor streamExecutor) {
        this.assistant = assistant;
        this.ragGateway = ragGateway;
        this.personalContext = personalContext;
        this.rlsState = rlsState;
        this.streamExecutor = streamExecutor;
    }

    /**
     * Wave 1.2: when the RLS runtime verifier could not prove the isolation
     * boundary, assistant chat degrades loudly (503) instead of silently
     * failing against an unprovisioned datasource.
     */
    private void requireAssistantRlsAvailable() {
        if (rlsState != null && !rlsState.verified()) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_RLS_UNAVAILABLE",
                    "Assistant RLS runtime verification failed; assistant features are temporarily unavailable");
        }
    }

    /** Null-safe conversationId → UUID for the idempotency hash (invalid ids hash as new-conversation). */
    private static UUID conversationUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Round-3 chat-7: persists an intercepted PERSONAL_CONTEXT turn into the
     * requested conversation so the history endpoint shows it, and returns the
     * response with the conversation id stamped. An unknown or foreign
     * conversationId throws 404 CONVERSATION_NOT_FOUND — the same contract the
     * KB path has always had — instead of answering 200 into a void. When the
     * caller sent no conversationId (fresh-panel questions) the response stays
     * unpersisted exactly as before.
     */
    private ChatResponse persistPersonalTurn(ChatRequest request, ChatResponse personal, String owner) {
        try {
            // Round-3 cb3-7/cb3-10: a personal turn without a conversationId
            // used to skip persistence entirely — the answer vanished from
            // history AND the idempotency key stayed unconsumed (prod probe
            // caught the 200-reuse). Create the conversation on the fly so
            // every personal turn lands in history and consumes its key.
            String targetConversation = request.conversationId();
            if (targetConversation == null || targetConversation.isBlank()) {
                targetConversation = assistant.createConversation(owner, personal.locale());
            }
            ThesisAssistantService.PersonalTurn recorded = assistant.recordPersonalTurn(owner,
                    targetConversation,
                    request.message(), personal.answer(), personal.locale(), personal.reasonCode());
            assistant.recordPersonalTurnKey(owner, request.clientRequestId(),
                    AssistantInputGuard.canonicalHash(request.message(), personal.locale(),
                            conversationUuid(request.conversationId())),
                    recorded);
            if (recorded == null) {
                return personal;
            }
            return new ChatResponse(personal.answer(), personal.model(), personal.degraded(),
                    personal.reasonCode(), personal.locale(), personal.citations(), personal.requestId(),
                    personal.clientRequestId(), personal.turnId(), personal.replayed(),
                    personal.terminalStatus(), recorded.conversationId(), recorded.messageId(),
                    personal.resetAt());
        } catch (DataAccessException | org.springframework.transaction.TransactionException exception) {
            // The answer itself is valid; a history-write outage must not turn
            // it into a 5xx (same outage contract as the KB path). A
            // commit-time rollback surfaces as TransactionException, not
            // DataAccessException — both mean "nothing persisted".
            LOG.warn("personal turn persistence skipped with {}", exception.getClass().getSimpleName());
            return personal;
        } catch (DomainException exception) {
            // History unconfigured (ASSISTANT_UNAVAILABLE) or the provided
            // conversationId failed validation — validation 404s must surface,
            // only the history-unconfigured case keeps the answer alive.
            if ("ASSISTANT_UNAVAILABLE".equals(exception.code())) {
                LOG.warn("personal turn persistence skipped: history unconfigured");
                return personal;
            }
            throw exception;
        }
    }

    @Operation(summary = "Gửi câu hỏi tới Trợ lý AI (JSON Block Mode)", description = "Hỏi đáp quy chế đào tạo, thời khóa biểu, tiến độ và đề tài khóa luận với mô hình AI RAG")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nhận phản hồi từ trợ lý AI"),
        @ApiResponse(responseCode = "400", description = "Tin nhắn không hợp lệ"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính")
    })
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt actor,
            HttpServletRequest httpRequest) {
        requireAssistantRlsAvailable();
        boolean dbDownAtRequestStart = Boolean.TRUE.equals(
                httpRequest.getAttribute(DatabaseAvailabilityTracker.REQUEST_ATTRIBUTE));
        AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(request.message());
        String locale = AssistantInputGuard.normalizeLocale(request.locale());
        if (!guard.allowed()) {
            return new ChatResponse(ThesisAssistantService.guardMessage(guard.reasonCode(), locale),
                    ThesisAssistantService.MODEL, true, guard.reasonCode(), locale, List.of(),
                    UUID.randomUUID(), request.clientRequestId(), null, false, "REJECTED", null, null);
        }
        // Round-3 chat-8: the specialized scope serves the curated DevOps/REST
        // corpus, so the general-chatbot technical gate must not run ahead of
        // its routing — "Docker compose để chạy dự án" was blocked with zero
        // citations before the scope was ever consulted. Privacy (inspect)
        // and injection gates above still apply in every scope.
        if (!request.isSpecializedScope() && AssistantInputGuard.isTechnicalRequest(guard.normalizedMessage())) {
            return new ChatResponse(ThesisAssistantService.technicalOutputMessage(locale),
                    ThesisAssistantService.MODEL, true, "TECHNICAL_REQUEST_BLOCKED", locale, List.of(),
                    UUID.randomUUID(), request.clientRequestId(), null, false, "REJECTED", null, null);
        }
        // Conversational openers (greeting, thanks, identity) resolve locally
        // before anything else: they must never fall through to a knowledge
        // miss, and they are not charged against the daily RAG quota.
        ChatResponse conversational = ThesisAssistantService.conversationalAnswer(
                request.message(), locale, request.clientRequestId());
        if (conversational != null) {
            return conversational;
        }
        if (personalContext != null && personalContext.handles(request.message())) {
            // Round-3 cb3-10: a reused clientRequestId with a different payload
            // conflicts exactly like the KB path instead of being answered.
            try {
                assistant.enforcePersonalIdempotency(subject(actor), request.clientRequestId(),
                        AssistantInputGuard.canonicalHash(request.message(), locale,
                                conversationUuid(request.conversationId())));
            } catch (DataAccessException | org.springframework.transaction.TransactionException exception) {
                // The idempotency ledger read is down — the SSE path surfaces
                // the same outage as an ASSISTANT_UNAVAILABLE frame instead of
                // a bare 500, so JSON answers with the structured retryable
                // contract too (Kongming F3).
                throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_UNAVAILABLE",
                        "Assistant is temporarily unavailable; please retry");
            }
            ChatResponse personal = personalContext.answer(request, actor);
            if (personal != null) {
                // Round-3 chat-7: an intercepted personal answer used to skip
                // the ledger entirely — the turn vanished from
                // /conversations/{id}/messages and a deleted conversationId was
                // answered 200 instead of the KB path's 404. Persist both
                // sides and validate ownership exactly like the KB path.
                return persistPersonalTurn(request, personal, subject(actor));
            }
        }
        // Off-topic general questions ("Con gà có mấy cái chân") carry no
        // academic signal, so the campus corpus could never ground them; the
        // provider answers them directly instead of the KB miss (owner
        // request 2026-09-30). Scope='specialized' skips this: the
        // professional corpus is the right source there (round-2 chat-1).
        String owner = subject(actor);
        ChatResponse general = assistant.generalAnswerIfOffTopic(request.message(), locale,
                request.clientRequestId(), request.scope(), owner,
                AssistantInputGuard.canonicalHash(request.message(), locale,
                        conversationUuid(request.conversationId())));
        if (general != null) {
            return general;
        }
        // Lexical-first fast path (chatbot latency): a confident local KB
        // match answers before ANY provider/RAG round-trip — remote mode had
        // this inside chatRemoteWithFallback, but the single-service
        // deployment (prod runs the local pipeline) used to pay the full
        // provider call for questions the curated corpus answers directly.
        //
        // Deliberate contract: the fast-path answer is NOT persisted — no
        // turn-ledger reserve, no conversation/message row, messageId=null,
        // and a reused clientRequestId is not conflict-checked. Identical to
        // what remote-mode requests already experienced (the probe ran before
        // the gateway there too) and to the local-grounded fallback contract.
        // Zero database writes is the point — it is what makes the answer
        // cheap even when the datasource is far away. Quota is unaffected:
        // turns.dispatch only charges synthesisRequired && provider-usable
        // answers, so lexical answers were always free.
        ChatResponse fastPath = lexicalFastPathOrNull(request);
        if (fastPath != null) {
            return fastPath;
        }
        if (remoteRag()) {
            return chatRemoteWithFallback(request, owner, dbDownAtRequestStart);
        }
        // The local path touches the turn ledger (reserve/complete) outside the
        // service's own DomainException guard: a ledger outage used to escape
        // as DataAccessException and surface as a 500. The degraded contract is
        // a 200 with no persistence, never a 5xx, so it is honoured here too.
        try {
            return assistant.answer(request.message(), request.locale(), request.conversationId(), owner,
                    request.clientRequestId(), request.scope());
        } catch (DataAccessException exception) {
            // Preserve the outage contract (KNOWLEDGE_UNAVAILABLE, degraded, no
            // citations) rather than the curated fallback, whose NO_MATCH
            // reason code would mask the outage from the runtime probes.
            return ThesisAssistantService.knowledgeUnavailableResponse(locale, request.clientRequestId());
        }
    }

    /**
     * Remote RAG with the local-grounded fallback chain (chatbot-excellence):
     * a transient gateway failure, timeout, or a remote NO_MATCH falls back to
     * the local lexical KB within a small budget; when even that is empty, the
     * deterministic curated fallback answers instead. A RAG outage is therefore
     * a 200 degraded response, never a 5xx and never an empty body.
     *
     * <p>Lexical-first fast path (chatbot latency): a confident local KB match
     * answers before the gateway is ever contacted; only a weak or empty
     * lexical window reaches remote RAG.
     */
    private ChatResponse chatRemoteWithFallback(ChatRequest request, String owner, boolean dbDownAtRequestStart) {
        // The caller already ran lexicalFastPathOrNull — reaching here means
        // the local window was weak or empty, so the remote chain starts at
        // the gateway instead of paying a second retrieval.
        try {
            ChatResponse remote = ragGateway.chat(request, owner);
            if (remote == null) {
                // A remote 2xx with an empty body is the one gateway anomaly
                // that would otherwise return a bare 200. Treat it like a
                // transient failure: the local grounded fallback answers.
                return localGroundedFallback(request, dbDownAtRequestStart, true);
            }
            if (!"NO_MATCH".equals(remote.reasonCode())) {
                return remote;
            }
            // Remote was healthy and honestly had no match: a local miss must
            // surface "not found", not the knowledge-outage copy.
            return localGroundedFallback(request, dbDownAtRequestStart, false);
        } catch (DomainException exception) {
            if (isFallbackEligible(exception)) {
                return localGroundedFallback(request, dbDownAtRequestStart, true);
            }
            throw exception;
        }
    }

    /**
     * Best-effort fast-path probe. Any failure here — retrieval outage, budget
     * timeout, unexpected guard state — returns {@code null} so the request
     * escalates along the unchanged RAG-first chain.
     */
    private ChatResponse lexicalFastPathOrNull(ChatRequest request) {
        try {
            return assistant.lexicalFastPath(request.message(), request.locale(), request.scope());
        } catch (Exception exception) {
            LOG.warn("lexical fast path skipped with {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    /** Gateway failures this deployment can ride through with the local KB. */
    private boolean isFallbackEligible(DomainException exception) {
        return ragGateway.isTransientFailure(exception)
                || (exception.code() != null && exception.code().startsWith("RAG_"));
    }

    /**
     * Best-effort local answer; the curated fallback covers even a failed
     * local read. When the account-state filter recorded a database failure
     * within this request (attribute set at filter time), the outage contract
     * (KNOWLEDGE_UNAVAILABLE) wins over the curated fallback regardless of how
     * long the layered timeouts took.
     */
    private ChatResponse localGroundedFallback(ChatRequest request, boolean dbDownAtRequestStart,
            boolean remoteFailed) {
        try {
            ChatResponse fallback = assistant.groundedFallback(request.message(), request.locale(),
                    dbDownAtRequestStart, request.scope(), !remoteFailed);
            if (fallback != null) {
                return fallback;
            }
        } catch (Exception exception) {
            LOG.warn("local knowledge fallback failed with {}", exception.getClass().getSimpleName());
            if (dbDownAtRequestStart) {
                return ThesisAssistantService.knowledgeUnavailableResponse(
                        AssistantInputGuard.normalizeLocale(request.locale()), request.clientRequestId());
            }
        }
        if (dbDownAtRequestStart) {
            return ThesisAssistantService.knowledgeUnavailableResponse(
                    AssistantInputGuard.normalizeLocale(request.locale()), request.clientRequestId());
        }
        return remoteFailed
                ? ThesisAssistantService.curatedFallback(AssistantInputGuard.normalizeLocale(request.locale()))
                : ThesisAssistantService.noMatchFallback(AssistantInputGuard.normalizeLocale(request.locale()));
    }

    /** Deprecated compatibility alias; clients should use /chat. */
    @Deprecated
    @PostMapping("/chat/complete")
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','ADMIN','SUPER_ADMIN','TRUONG_KHOA')")
    public ChatResponse complete(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt actor,
            HttpServletRequest httpRequest) {
        return chat(request, actor, httpRequest);
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
        requireAssistantRlsAvailable();
        String owner = subject(actor);
        boolean dbDownAtRequestStart = httpRequest != null && Boolean.TRUE.equals(
                httpRequest.getAttribute(DatabaseAvailabilityTracker.REQUEST_ATTRIBUTE));
        SseEmitter emitter = new SseEmitter(120_000L);
        AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(request.message());
        if (!guard.allowed()) {
            sendError(emitter, guard.reasonCode(), false);
            emitter.complete();
            return emitter;
        }
        if (AssistantInputGuard.isTechnicalRequest(guard.normalizedMessage()) && !request.isSpecializedScope()) {
            sendError(emitter, "TECHNICAL_REQUEST_BLOCKED", false);
            emitter.complete();
            return emitter;
        }
        AtomicBoolean transportClosed = new AtomicBoolean(false);
        AtomicBoolean generationFinished = new AtomicBoolean(false);
        // Emit an SSE comment frame while the emitter is open so a proxy or CDN
        // cannot idle-kill the connection during a slow model start. Comment
        // frames (":heartbeat") are transport-level noise: SSE clients ignore
        // them, and the frontend stream parser skips them without disturbing
        // event ordering or the delta sequence.
        java.util.concurrent.ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleWithFixedDelay(
                () -> { if (!transportClosed.get()) sendHeartbeat(emitter); },
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        Runnable stopHeartbeat = () -> heartbeat.cancel(false);
        Consumer<ThesisAssistantService.StreamEvent> sink = event -> {
            if (!transportClosed.get()) send(emitter, event);
        };
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        Runnable generation = () -> {
        try {
            // Same conversational tier as the JSON path: openers answer
            // locally, never fall through to a knowledge miss.
            ChatResponse conversational = ThesisAssistantService.conversationalAnswer(request.message(),
                    AssistantInputGuard.normalizeLocale(request.locale()), request.clientRequestId());
            if (conversational != null) {
                ThesisAssistantService.streamLocalResponse(conversational, request.clientRequestId(), sink);
            } else {
                // Match the JSON ordering: enforce idempotency before the
                // personal DB reads, not after, so a conflicting reused key
                // is rejected without paying for the lookup.
                ChatResponse personal = null;
                if (personalContext != null && personalContext.handles(request.message())) {
                    assistant.enforcePersonalIdempotency(owner, request.clientRequestId(),
                            AssistantInputGuard.canonicalHash(request.message(),
                                    AssistantInputGuard.normalizeLocale(request.locale()),
                                    conversationUuid(request.conversationId())));
                    personal = personalContext.answer(request, actor);
                }
                if (personal != null) {
                    personal = persistPersonalTurn(request, personal, owner);
                    personalContext.stream(personal, request, sink);
                } else {
                    ChatResponse general = assistant.generalAnswerIfOffTopic(request.message(),
                            AssistantInputGuard.normalizeLocale(request.locale()), request.clientRequestId(),
                            request.scope(), owner,
                            AssistantInputGuard.canonicalHash(request.message(),
                                    AssistantInputGuard.normalizeLocale(request.locale()),
                                    conversationUuid(request.conversationId())));
                    if (general != null) {
                        ThesisAssistantService.streamLocalResponse(general, request.clientRequestId(), sink);
                    } else {
                        // Same fast-path ordering as the JSON route: a
                        // confident curated match streams instantly in BOTH
                        // modes instead of waiting on the provider chain.
                        ChatResponse fastPath = lexicalFastPathOrNull(request);
                        if (fastPath != null) {
                            emitLexicalFastPath(fastPath, request,
                                    AssistantInputGuard.normalizeLocale(request.locale()), sink);
                        } else if (remoteRag()) {
                            streamRemoteWithFallback(request, owner, sink, dbDownAtRequestStart);
                        } else {
                            assistant.stream(request.message(), request.locale(), request.conversationId(), owner,
                                    request.clientRequestId(), sink, request.scope());
                        }
                    }
                }
            }
        } catch (DomainException exception) {
            if (!transportClosed.get()) sendError(emitter, exception.code(),
                    exception.status().is5xxServerError() || exception.status() == HttpStatus.TOO_MANY_REQUESTS);
        } catch (Exception exception) {
            LOG.warn("assistant stream failed with {}", exception.getClass().getSimpleName());
            if (!transportClosed.get()) sendError(emitter, "ASSISTANT_UNAVAILABLE", true);
        } finally {
            // Transport cleanup must not interrupt a canonical result that has just finished.
            generationFinished.set(true);
            stopHeartbeat.run();
            emitter.complete();
        }
        };
        FutureTask<Void> task = new FutureTask<>(new DelegatingSecurityContextRunnable(generation, securityContext), null);
        Runnable closeTransport = () -> {
            transportClosed.set(true);
            stopHeartbeat.run();
            if (!generationFinished.get()) task.cancel(true);
        };
        emitter.onCompletion(closeTransport);
        emitter.onTimeout(closeTransport);
        emitter.onError(failure -> closeTransport.run());
        try {
            streamExecutor.execute(task);
        } catch (RejectedExecutionException rejected) {
            // Every student worker is busy waiting on the provider. Before
            // giving up, let a deterministic fast-path question through on the
            // servlet thread: it never touches the provider, so answering it
            // under saturation is strictly better than ASSISTANT_UNAVAILABLE.
            ChatResponse fastPath = lexicalFastPathOrNull(request);
            if (fastPath != null) {
                emitLexicalFastPath(fastPath, request,
                        AssistantInputGuard.normalizeLocale(request.locale()), sink);
                generationFinished.set(true);
                stopHeartbeat.run();
                emitter.complete();
                return emitter;
            }
            generationFinished.set(true);
            stopHeartbeat.run();
            sendError(emitter, "ASSISTANT_UNAVAILABLE", true);
            emitter.complete();
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

    /**
     * SSE flavour of the fallback chain: a transient gateway failure or a
     * remote NO_MATCH emits the local KB answer (or the curated fallback) as a
     * replace/done sequence, so the stream always terminates with usable text
     * instead of a bare error frame. A confident lexical match short-circuits
     * the gateway entirely with a complete meta/delta/citation/done sequence.
     */
    void streamRemoteWithFallback(ChatRequest request, String owner,
            Consumer<ThesisAssistantService.StreamEvent> sink, HttpServletRequest httpRequest) {
        streamRemoteWithFallback(request, owner, sink, httpRequest != null && Boolean.TRUE.equals(
                httpRequest.getAttribute(DatabaseAvailabilityTracker.REQUEST_ATTRIBUTE)));
    }

    private void streamRemoteWithFallback(ChatRequest request, String owner,
            Consumer<ThesisAssistantService.StreamEvent> sink, boolean dbDownAtRequestStart) {
        String locale = AssistantInputGuard.normalizeLocale(request.locale());
        // The caller ran the lexical fast path before choosing this branch —
        // a second retrieval here would only add a DB round-trip.
        boolean[] forwarded = { false };
        Consumer<ThesisAssistantService.StreamEvent> intercept = event -> {
            if (event instanceof ThesisAssistantService.StreamDone done && "NO_MATCH".equals(done.reasonCode())) {
                emitStreamFallback(request, locale, sink, forwarded[0], dbDownAtRequestStart, false);
                return;
            }
            forwarded[0] = true;
            sink.accept(event);
        };
        try {
            ragGateway.stream(request, owner, intercept);
        } catch (DomainException exception) {
            if (isFallbackEligible(exception)) {
                emitStreamFallback(request, locale, sink, forwarded[0], dbDownAtRequestStart, true);
                return;
            }
            throw exception;
        }
    }

    /**
     * A fast-path SSE answer is a complete stream: nothing crossed the wire
     * before it, so meta opens, the cited delta carries the whole answer, and
     * done closes non-degraded. No turn is persisted — identical to the
     * existing local-grounded fallback contract the clients already render.
     */
    void emitLexicalFastPath(ChatResponse fastPath, ChatRequest request, String locale,
            Consumer<ThesisAssistantService.StreamEvent> sink) {
        sink.accept(new ThesisAssistantService.StreamMeta(
                UUID.randomUUID(), request.clientRequestId(), null, null,
                ThesisAssistantService.FAST_PATH_MODEL, locale));
        sink.accept(new ThesisAssistantService.StreamDelta(0, fastPath.answer(),
                fastPath.citations().stream().map(ThesisAssistantDtos.Citation::sourceId)
                        .filter(java.util.Objects::nonNull).toList()));
        fastPath.citations().forEach(citation -> sink.accept(
                new ThesisAssistantService.StreamCitation(citation)));
        sink.accept(new ThesisAssistantService.StreamDone(null, fastPath.reasonCode(),
                fastPath.degraded(), "COMPLETED"));
    }

    private void emitStreamFallback(ChatRequest request, String locale,
            Consumer<ThesisAssistantService.StreamEvent> sink, boolean alreadyForwarded,
            boolean dbDownAtRequestStart, boolean remoteFailed) {
        ChatResponse fallback = localGroundedFallback(request, dbDownAtRequestStart, remoteFailed);
        if (!alreadyForwarded) {
            // Nothing crossed the wire yet, so the fallback is a complete stream.
            sink.accept(new ThesisAssistantService.StreamMeta(
                    UUID.randomUUID(), request.clientRequestId(), null, null,
                    ThesisAssistantService.MODEL, locale));
            sink.accept(new ThesisAssistantService.StreamDelta(0, fallback.answer(),
                    fallback.citations().stream().map(ThesisAssistantDtos.Citation::sourceId)
                            .filter(java.util.Objects::nonNull).toList()));
            fallback.citations().forEach(citation -> sink.accept(
                    new ThesisAssistantService.StreamCitation(citation)));
        } else {
            // Remote frames already streamed: replace them, never append. The
            // replace carries the fallback's own reasonCode — an uncited curated
            // answer must mark the bubble degraded (NO_MATCH), not ANSWERED.
            // Citations still follow: a bare remote meta frame must not strip
            // the fallback's sources the way the JSON path keeps them.
            sink.accept(new ThesisAssistantService.StreamReplace(fallback.answer(),
                    fallback.citations().stream().map(ThesisAssistantDtos.Citation::sourceId)
                            .filter(java.util.Objects::nonNull).toList(),
                    fallback.reasonCode()));
            fallback.citations().forEach(citation -> sink.accept(
                    new ThesisAssistantService.StreamCitation(citation)));
        }
        sink.accept(new ThesisAssistantService.StreamDone(parseMessageId(fallback.messageId()),
                fallback.reasonCode(), fallback.degraded(), "COMPLETED"));
    }

    private static UUID parseMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) return null;
        try { return UUID.fromString(messageId); } catch (IllegalArgumentException ignored) { return null; }
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
