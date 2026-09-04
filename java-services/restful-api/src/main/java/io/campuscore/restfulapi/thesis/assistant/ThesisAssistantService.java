package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.AssistantCompletionProvider.CompletionRequest;
import io.campuscore.restfulapi.thesis.assistant.AssistantCompletionProvider.ProviderSegment;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.Citation;
import io.campuscore.restfulapi.web.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Orchestrates lexical retrieval, fenced turns, and provider I/O outside transactions. */
@Service
@Profile("persistence")
public class ThesisAssistantService {
    static final String MODEL = "curated-lexical-rag";
    static final int TOP_K = 5;
    private static final String DEFAULT_LOCALE = "vi";

    private final ThesisAssistantKnowledgeRepository knowledge;
    private final DeepSeekClient provider;
    private final ThesisAssistantRepository legacyHistory;
    private final ThesisAssistantTurnRepository turns;
    private final ThesisAssistantCatalogRepository catalog;
    private final AssistantCancellationRegistry cancellations;
    private final DeepSeekProperties deepSeek;
    private final AssistantProperties properties;

    /** Constructor retained for lexical/unit tests that do not load persistence beans. */
    public ThesisAssistantService(ThesisAssistantKnowledgeRepository knowledge) {
        this.knowledge = knowledge;
        this.provider = null;
        this.legacyHistory = null;
        this.turns = null;
        this.catalog = null;
        this.cancellations = null;
        this.deepSeek = null;
        this.properties = null;
    }

    /** Compatibility constructor retained for the previous candidate tests. */
    public ThesisAssistantService(ThesisAssistantKnowledgeRepository knowledge, DeepSeekClient provider,
            ThesisAssistantRepository history, DeepSeekProperties deepSeek, AssistantProperties properties) {
        this.knowledge = knowledge;
        this.provider = provider;
        this.legacyHistory = history;
        this.turns = null;
        this.catalog = null;
        this.cancellations = null;
        this.deepSeek = deepSeek;
        this.properties = properties;
    }

    @Autowired
    public ThesisAssistantService(ThesisAssistantKnowledgeRepository knowledge, DeepSeekClient provider,
            ThesisAssistantRepository history, ThesisAssistantTurnRepository turns,
            ThesisAssistantCatalogRepository catalog, AssistantCancellationRegistry cancellations,
            DeepSeekProperties deepSeek, AssistantProperties properties) {
        this.knowledge = knowledge;
        this.provider = provider;
        this.legacyHistory = history;
        this.turns = turns;
        this.catalog = catalog;
        this.cancellations = cancellations;
        this.deepSeek = deepSeek;
        this.properties = properties;
    }

    /** Pure lexical path used by tests and by safe fallback when persistence is unavailable. */
    public ChatResponse answer(String message, String locale) {
        return lexicalAnswer(message, locale);
    }

    /** Backward-compatible server entry point; new controllers pass the client key explicitly. */
    public ChatResponse answer(String message, String locale, String conversationId, String ownerId) {
        return answer(message, locale, conversationId, ownerId, UUID.randomUUID(), ignored -> { });
    }

    public ChatResponse answer(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId) {
        return answer(message, locale, conversationId, ownerId, clientRequestId, ignored -> { });
    }

    public ChatResponse answer(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> streamSink) {
        if (turns == null || properties == null) return legacyAnswer(message, locale, conversationId, ownerId);
        return execute(message, locale, conversationId, ownerId, clientRequestId, streamSink == null ? ignored -> { } : streamSink);
    }

    public ChatResponse stream(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> sink) {
        return answer(message, locale, conversationId, ownerId, clientRequestId, sink);
    }

    public ThesisAssistantTurnRepository.CancelResult cancel(UUID clientRequestId, String ownerId) {
        if (turns == null) return new ThesisAssistantTurnRepository.CancelResult(false, "TURN_NOT_FOUND");
        ThesisAssistantTurnRepository.TurnRow row = turns.findByRequest(ownerId, clientRequestId);
        if (row == null) throw problem(404, "TURN_NOT_FOUND", "Request was not found");
        return turns.cancel(row.turnId(), ownerId, clientRequestId,
                handle -> { if (cancellations != null) cancellations.fence(ownerId, handle.clientRequestId(), handle.leaseGeneration()); });
    }

    public int setFeedback(UUID messageId, String ownerId, String rating, String reason) {
        if (turns == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        return turns.setFeedback(messageId, ownerId, rating, reason);
    }

    public int deleteFeedback(UUID messageId, String ownerId) {
        if (turns == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        return turns.deleteFeedback(messageId, ownerId);
    }

    public List<ThesisAssistantRepository.Conversation> conversations(String ownerId) {
        if (legacyHistory == null) return List.of();
        return legacyHistory.conversations(ownerId);
    }

    public ThesisAssistantRepository.ConversationPage conversationPage(String ownerId, Integer limit, String cursor) {
        if (legacyHistory == null) return new ThesisAssistantRepository.ConversationPage(List.of(), null);
        return legacyHistory.conversations(ownerId, limit == null ? 20 : limit, cursor);
    }

    public String createConversation(String ownerId, String locale) {
        if (legacyHistory == null || properties == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        return legacyHistory.ensureConversation(ownerId, null, AssistantInputGuard.normalizeLocale(locale), properties.retentionDays()).toString();
    }

    public List<ThesisAssistantRepository.Message> messages(UUID conversationId, String ownerId) {
        if (legacyHistory == null) return List.of();
        return legacyHistory.messages(conversationId, ownerId);
    }

    public ThesisAssistantRepository.MessagePage messagePage(UUID conversationId, String ownerId, Integer limit, String cursor) {
        if (legacyHistory == null) return new ThesisAssistantRepository.MessagePage(List.of(), null);
        return legacyHistory.messagesPage(conversationId, ownerId, limit == null ? 50 : limit, cursor);
    }

    public void deleteConversation(UUID conversationId, String ownerId) {
        if (legacyHistory == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        if (turns != null && cancellations != null) {
            // Fence, tombstone, and physically delete under one database
            // transaction. This closes the gap where a new reserve could race
            // between the old two-step purge and history delete.
            turns.purgeAndDeleteConversation(conversationId, ownerId,
                    (owner, handle) -> { if (cancellations != null) cancellations.fence(owner, handle.clientRequestId(), handle.leaseGeneration()); });
            return;
        }
        if (legacyHistory.deleteConversation(conversationId, ownerId) == 0) throw problem(404, "CONVERSATION_NOT_FOUND", "Conversation not found");
    }

    private ChatResponse execute(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> sink) {
        if (clientRequestId == null) throw problem(400, "CLIENT_REQUEST_ID_REQUIRED", "clientRequestId is required");
        if (ownerId == null || ownerId.isBlank()) throw problem(401, "UNAUTHENTICATED", "Authentication is required");
        AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(message);
        UUID requestId = UUID.randomUUID();
        if (!guard.allowed()) {
            String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
            String blocked = "PROMPT_INJECTION".equals(guard.reasonCode())
                    ? promptInjectionMessage(normalizedLocale) : sensitiveMessage(normalizedLocale);
            emit(sink, new StreamError(guard.reasonCode(), false));
            return new ChatResponse(blocked, MODEL, true, guard.reasonCode(), normalizedLocale,
                    List.of(), requestId, clientRequestId, null, false, "REJECTED", null, null);
        }
        String normalized = guard.normalizedMessage();
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        UUID requestedConversation = parseConversation(conversationId);
        LexicalResult lexical = retrieve(normalized, normalizedLocale);
        if (lexical.error()) {
            emit(sink, new StreamError("KNOWLEDGE_UNAVAILABLE", true));
            return new ChatResponse(lexical.answer(), MODEL, true, "KNOWLEDGE_UNAVAILABLE", normalizedLocale,
                    List.of(), requestId, clientRequestId, null, false, "FAILED_PRE_DISPATCH", null, null);
        }

        String hash = AssistantInputGuard.canonicalHash(normalized, normalizedLocale, requestedConversation);
        String leaseOwner = "assistant-" + UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = cancellations == null
                ? turns.reserve(ownerId, clientRequestId, hash, requestedConversation, normalizedLocale,
                        leaseOwner, properties.retentionDays())
                : turns.reserve(ownerId, clientRequestId, hash, requestedConversation, normalizedLocale,
                        leaseOwner, properties.retentionDays(), this::fenceExpired);
        if (reservation.status() == ThesisAssistantTurnRepository.ReservationStatus.REPLAY) {
            ThesisAssistantTurnRepository.ReplayResult replay = turns.replay(reservation.turnId(), ownerId);
            emitReplay(sink, replay, clientRequestId, requestId, normalizedLocale, reservation.turnId());
            return response(replay, clientRequestId, requestId, reservation.turnId(), true, normalizedLocale);
        }
        if (reservation.status() == ThesisAssistantTurnRepository.ReservationStatus.ACTIVE) {
            throw problem(409, "TURN_IN_PROGRESS", "A turn with this conversation is already active");
        }
        if (reservation.status() == ThesisAssistantTurnRepository.ReservationStatus.AMBIGUOUS) {
            throw problem(409, "FAILED_AMBIGUOUS", "The provider outcome is ambiguous; automatic redispatch is disabled");
        }
        boolean snapshotReady = cancellations == null
                ? turns.markSnapshotReady(reservation.turnId(), ownerId, reservation.leaseGeneration(), lexical.snapshotHash())
                : turns.markSnapshotReady(reservation.turnId(), ownerId, reservation.leaseGeneration(), lexical.snapshotHash(), this::fenceExpired);
        if (!snapshotReady) {
            throw problem(409, "STALE_LEASE", "Turn lease is no longer current");
        }
        emit(sink, new StreamMeta(requestId, clientRequestId, reservation.turnId(), reservation.conversationId(), MODEL, normalizedLocale));

        boolean providerAttempt = false;
        boolean synthesisRequired = AssistantDifficultyRouter.requiresSynthesis(normalized, lexical.documents());
        String reason = lexical.documents().isEmpty() ? "NO_MATCH"
                : synthesisRequired ? "PROVIDER_DISABLED" : "RAG_GROUNDED";
        boolean degraded = synthesisRequired && !lexical.documents().isEmpty();
        String answer = lexical.answer();
        List<ProviderSegment> emittedSegments = new ArrayList<>();
        StringBuilder providerAnswer = new StringBuilder();
        int[] expectedSequence = { 0 };
        AtomicBoolean cancelToken = cancellations == null ? new AtomicBoolean(false)
                : cancellations.register(ownerId, clientRequestId, reservation.leaseGeneration());
        try {
            if (synthesisRequired && deepSeek != null && deepSeek.usable()) {
                ThesisAssistantTurnRepository.DispatchDecision dispatch = cancellations == null
                        ? turns.dispatch(reservation.turnId(), ownerId, reservation.leaseGeneration(),
                                properties.userDailyQuota(), properties.globalDailyQuota())
                        : turns.dispatch(reservation.turnId(), ownerId, reservation.leaseGeneration(),
                                properties.userDailyQuota(), properties.globalDailyQuota(), this::fenceExpired);
                if (dispatch.dispatched()) {
                    providerAttempt = true;
                    try {
                        CompletionRequest request = new CompletionRequest(normalized, normalizedLocale, lexical.context(), lexical.sourceIds());
                        var result = provider.complete(request, segment -> {
                            if (cancelToken.get()) throw new CancellationException("assistant request cancelled");
                            Runnable acceptedEmission = () -> {
                                validateSegment(segment, lexical.sourceIds(), expectedSequence[0]);
                                // Inspect the complete prefix, not only each
                                // frame. A provider can fragment an email/phone/
                                // student id across otherwise innocuous SSE
                                // segments. Reject it before the unsafe frame
                                // crosses the stream boundary; any already-
                                // rendered safe prefix is replaced below.
                                String candidate = providerAnswer + segment.text();
                                if (!AssistantInputGuard.inspect(candidate).allowed()) {
                                    throw new ProviderOutputRejectedException();
                                }
                                providerAnswer.append(segment.text());
                                expectedSequence[0]++;
                                emittedSegments.add(segment);
                                emit(sink, new StreamDelta(segment.sequence(), segment.text(), segment.sourceIds()));
                            };
                            if (cancellations != null) {
                                if (!cancellations.emitIfActive(ownerId, clientRequestId,
                                        reservation.leaseGeneration(), acceptedEmission)) {
                                    throw new CancellationException("assistant request cancelled");
                                }
                            } else {
                                acceptedEmission.run();
                            }
                        }, cancelToken::get);
                        if (result == null) throw new DeepSeekClient.ProviderUnavailableException("provider returned no result");
                        // Only text that passed the segment/source gate may be
                        // committed. Do not trust a collector's separate answer
                        // field if it diverges from streamed segments.
                        if (emittedSegments.isEmpty()) throw new InvalidSegmentException();
                        answer = emittedSegments.stream().map(ProviderSegment::text).collect(Collectors.joining()).trim();
                        if (answer.isBlank()) throw new InvalidSegmentException();
                        reason = "ANSWERED";
                        degraded = false;
                    } catch (CancellationException | DeepSeekClient.ProviderCancelledException cancelled) {
                        throw problem(409, "TURN_CANCELLED", "Turn was cancelled");
                    } catch (DeepSeekClient.ProviderUnavailableException | InvalidSegmentException
                            | ProviderOutputRejectedException providerFailure) {
                        reason = providerFailure instanceof ProviderOutputRejectedException
                                ? "PROVIDER_UNSAFE_OUTPUT" : "PROVIDER_UNAVAILABLE";
                        degraded = true;
                        answer = lexical.answer();
                        emit(sink, new StreamReplace(answer, lexical.sourceIds(), reason));
                    }
                } else if ("QUOTA_EXCEEDED".equals(dispatch.reasonCode())) {
                    throw problem(429, "QUOTA_EXCEEDED", "The daily assistant quota has been reached");
                } else if (!"DISPATCHED".equals(dispatch.reasonCode())) {
                    throw problem(409, dispatch.reasonCode(), "The assistant turn lease is no longer current");
                }
            }
            if (!providerAttempt && lexical.documents().isEmpty()) {
                degraded = false;
            }
            // A disabled provider still has a deterministic lexical answer. Emit it as a
            // normal delta so clients can render a useful fallback while retaining the
            // terminal degraded reason in the committed turn.
            if (!providerAttempt && ("PROVIDER_DISABLED".equals(reason) || "RAG_GROUNDED".equals(reason)
                    || "NO_MATCH".equals(reason))) {
                emit(sink, new StreamDelta(0, answer, lexical.sourceIds()));
            }
            ThesisAssistantTurnRepository.TerminalResult terminal = cancellations == null
                    ? turns.complete(reservation.turnId(), ownerId, reservation.leaseGeneration(), normalized,
                            reason.equals("ANSWERED") ? deepSeek.model() : MODEL, answer, degraded, reason, lexical.citations())
                    : turns.complete(reservation.turnId(), ownerId, reservation.leaseGeneration(), normalized,
                            reason.equals("ANSWERED") ? deepSeek.model() : MODEL, answer, degraded, reason,
                            lexical.citations(), this::fenceExpired);
            for (Citation citation : terminal.citations()) emit(sink, new StreamCitation(citation));
            emit(sink, new StreamDone(terminal.messageId(), reason, degraded, terminal.terminalStatus()));
            return new ChatResponse(terminal.answer(), terminal.model(), terminal.degraded(), terminal.reasonCode(), normalizedLocale,
                    terminal.citations(), requestId, clientRequestId, reservation.turnId(), false, terminal.terminalStatus(),
                    terminal.conversationId().toString(), terminal.messageId().toString());
        } catch (DomainException exception) {
            if (isTransientTerminalRace(exception.code())) {
                // A cancel, purge, or lease fence can win after one provider
                // delta crossed the transport boundary. Clear that transient
                // text before the stable error frame; the terminal CAS means
                // no USER/ASSISTANT/citation rows are visible for these paths.
                emit(sink, new StreamReplace("", List.of(), exception.code()));
            }
            throw exception;
        } finally {
            if (cancellations != null) cancellations.remove(ownerId, clientRequestId, reservation.leaseGeneration());
        }
    }

    private void fenceExpired(ThesisAssistantTurnRepository.ExpiredLease lease) {
        if (cancellations != null && lease != null) {
            cancellations.fence(lease.ownerId(), lease.clientRequestId(), lease.leaseGeneration());
        }
    }

    private static boolean isTransientTerminalRace(String code) {
        return "TURN_CANCELLED".equals(code)
                || "TURN_TERMINAL_RACE".equals(code)
                || "TURN_NOT_ACTIVE".equals(code)
                || "FAILED_AMBIGUOUS".equals(code)
                || "PURGED".equals(code);
    }

    private ChatResponse legacyAnswer(String message, String locale, String conversationId, String ownerId) {
        ChatResponse lexical = lexicalAnswer(message, locale);
        if (legacyHistory == null || ownerId == null || ownerId.isBlank() || lexical.degraded()) return lexical;
        String requestedLocale = AssistantInputGuard.normalizeLocale(locale);
        try {
            UUID conversation = legacyHistory.ensureConversation(ownerId, conversationId, requestedLocale,
                    properties == null ? 90 : properties.retentionDays());
            legacyHistory.appendMessage(conversation, "USER", AssistantInputGuard.normalizeMessage(message), MODEL, false, "RECEIVED");
            ChatResponse response = lexical;
            if (!lexical.citations().isEmpty() && provider != null && deepSeek != null && deepSeek.usable()
                    && legacyHistory.consumeQuota(ownerId, properties.userDailyQuota(), properties.globalDailyQuota())) {
                try {
                    String generated = provider.complete(message.trim(), lexical.citations().stream()
                            .map(citation -> citation.title() + "\n" + citation.excerpt())
                            .collect(Collectors.joining("\n\n")), requestedLocale);
                    response = new ChatResponse(generated, deepSeek.model(), false, "ANSWERED", requestedLocale, lexical.citations());
                } catch (DeepSeekClient.ProviderUnavailableException exception) {
                    response = new ChatResponse(lexical.answer(), MODEL, true, "PROVIDER_UNAVAILABLE", requestedLocale, lexical.citations());
                }
            } else if (!lexical.citations().isEmpty()) {
                response = new ChatResponse(lexical.answer(), MODEL, true,
                        deepSeek == null || !deepSeek.usable() ? "PROVIDER_DISABLED" : "QUOTA_EXCEEDED", requestedLocale, lexical.citations());
            }
            UUID messageId = legacyHistory.appendMessage(conversation, "ASSISTANT", response.answer(), response.model(), response.degraded(), response.reasonCode());
            legacyHistory.appendCitations(messageId, response.citations());
            return new ChatResponse(response.answer(), response.model(), response.degraded(), response.reasonCode(), response.locale(), response.citations(), conversation.toString(), messageId.toString());
        } catch (DataAccessException exception) {
            return new ChatResponse(lexical.answer(), MODEL, true, "HISTORY_UNAVAILABLE", requestedLocale, lexical.citations());
        }
    }

    private LexicalResult retrieve(String message, String locale) {
        List<String> terms = tokenize(message);
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            addDocuments(documents, seen, knowledge.search(locale, terms, TOP_K));
            String alternateLocale = DEFAULT_LOCALE.equals(locale) ? "en" : DEFAULT_LOCALE;
            if (documents.size() < TOP_K) addDocuments(documents, seen, knowledge.search(alternateLocale, terms, TOP_K - documents.size()));
        } catch (DataAccessException exception) {
            return new LexicalResult(unavailableMessage(locale), List.of(), List.of(), "", true, true);
        }
        if (catalog != null && documents.size() < TOP_K) {
            try {
                for (ThesisAssistantCatalogRepository.CatalogDocument row : catalog.search(locale, terms, TOP_K - documents.size())) {
                    String sourceId = row.entityType() + ":" + row.entityId();
                    ThesisAssistantKnowledgeRepository.KnowledgeDocument candidate = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                            sourceId, sourceId, locale, row.title(), row.text(), "academic-catalog", row.entityType(), row.entityId(), row.updatedAt() == null ? null : row.updatedAt().toInstant());
                    if (isPublicKnowledgeSafe(candidate) && seen.add(sourceId)) documents.add(candidate);
                }
            } catch (DataAccessException ignored) {
                // Public catalog is an additive adapter. A catalog outage must not
                // discard a valid curated answer or leak a database error to clients.
            }
        }
        documents = documents.stream().filter(document -> containsAnyTerm(document, terms)).limit(TOP_K).toList();
        List<Citation> citations = documents.stream().map(ThesisAssistantService::citation).toList();
        String answer = documents.isEmpty() ? noMatchMessage(locale) : documents.get(0).content();
        String context = citations.stream().map(c -> c.title() + "\n" + c.excerpt()).collect(Collectors.joining("\n\n"));
        if (properties != null && context.length() > properties.maxContextChars()) context = context.substring(0, properties.maxContextChars());
        List<String> sourceIds = citations.stream().map(Citation::sourceId).filter(value -> value != null && !value.isBlank()).toList();
        String snapshotMaterial = documents.stream().map(document -> String.join("|",
                safe(document.id()), safe(document.slug()), safe(document.locale()), safe(document.title()),
                safe(document.content()), safe(document.source()),
                safe(document.domain()),
                document.revisionId() == null ? "" : document.revisionId().toString(),
                document.revisionVersion() == null ? "" : document.revisionVersion().toString(),
                safe(document.catalogEntityType()), safe(document.catalogEntityId()),
                document.catalogUpdatedAt() == null ? "" : document.catalogUpdatedAt().toString(),
                safe(document.corpusVersion()), safe(document.corpusHash()),
                document.releaseId() == null ? "" : document.releaseId().toString()))
                .collect(Collectors.joining("\n"));
        return new LexicalResult(answer, documents, citations, context, false, false, sourceIds, sha256(snapshotMaterial));
    }

    private ChatResponse lexicalAnswer(String message, String locale) {
        String normalized = AssistantInputGuard.normalizeMessage(message);
        if (normalized.isBlank()) throw new IllegalArgumentException("message is required");
        if (properties != null && normalized.length() > properties.maxMessageChars()) throw new IllegalArgumentException("message must contain at most " + properties.maxMessageChars() + " characters");
        AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(normalized);
        if (!guard.allowed()) {
            String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
            String blocked = "PROMPT_INJECTION".equals(guard.reasonCode())
                    ? promptInjectionMessage(normalizedLocale) : sensitiveMessage(normalizedLocale);
            return new ChatResponse(blocked, MODEL, true, guard.reasonCode(), normalizedLocale, List.of());
        }
        LexicalResult result = retrieve(normalized, AssistantInputGuard.normalizeLocale(locale));
        if (result.error()) {
            return new ChatResponse(result.answer(), MODEL, true, "KNOWLEDGE_UNAVAILABLE", AssistantInputGuard.normalizeLocale(locale), List.of());
        }
        return new ChatResponse(result.answer(), MODEL, false, result.documents().isEmpty() ? "NO_MATCH" : "ANSWERED", AssistantInputGuard.normalizeLocale(locale), result.citations());
    }

    private static Citation citation(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        String sourceId = "academic-catalog".equals(document.source())
                ? document.catalogEntityType() + ":" + document.catalogEntityId() : document.id();
        String hash = sha256(String.join("|", sourceId, document.title(), document.content(), document.source(),
                document.locale(), document.revisionId() == null ? "" : document.revisionId().toString(),
                document.revisionVersion() == null ? "" : document.revisionVersion().toString(),
                document.catalogEntityType() == null ? "" : document.catalogEntityType(),
                document.catalogEntityId() == null ? "" : document.catalogEntityId(),
                document.catalogUpdatedAt() == null ? "" : document.catalogUpdatedAt().toString()));
        if ("ACADEMIC_CATALOG".equalsIgnoreCase(document.domain()) || "academic-catalog".equals(document.source())) {
            return new Citation(document.id(), document.slug(), document.title(), document.source(), document.locale(), excerpt(document.content()),
                    "ACADEMIC_CATALOG", "CATALOG", sourceId, null, null, hash, document.catalogEntityType(), document.catalogEntityId(),
                    document.catalogUpdatedAt() == null ? null : document.catalogUpdatedAt().toString(),
                    document.corpusVersion(), document.corpusHash(), document.releaseId());
        }
        UUID revision = parseUuid(document.id());
        return new Citation(document.id(), document.slug(), document.title(), document.source(), document.locale(), excerpt(document.content()),
                document.domain() == null ? "THESIS" : document.domain(), "CURATED", sourceId, document.revisionId() == null ? revision : document.revisionId(),
                document.revisionVersion(), hash, null, null, null,
                document.corpusVersion(), document.corpusHash(), document.releaseId());
    }

    private static void validateSegment(ProviderSegment segment, List<String> allowed, int expectedSequence) {
        if (segment == null || segment.text() == null || segment.text().isBlank()) throw new InvalidSegmentException();
        if (segment.sequence() != expectedSequence) throw new InvalidSegmentException();
        if (segment.sourceIds() == null || segment.sourceIds().isEmpty() || !allowed.containsAll(segment.sourceIds())) throw new InvalidSegmentException();
    }

    private static final class ProviderOutputRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static void emitReplay(Consumer<StreamEvent> sink, ThesisAssistantTurnRepository.ReplayResult replay, UUID clientRequestId,
            UUID requestId, String locale, UUID turnId) {
        emit(sink, new StreamMeta(requestId, clientRequestId, turnId, replay.conversationId(), replay.model(), locale));
        emit(sink, new StreamDelta(0, replay.answer(), replay.citations().stream().map(Citation::sourceId).toList()));
        replay.citations().forEach(citation -> emit(sink, new StreamCitation(citation)));
        emit(sink, new StreamDone(replay.messageId(), replay.reasonCode(), replay.degraded(), replay.terminalStatus()));
    }

    private static void emit(Consumer<StreamEvent> sink, StreamEvent event) { if (sink != null) sink.accept(event); }

    private static ChatResponse response(ThesisAssistantTurnRepository.ReplayResult replay, UUID clientRequestId, UUID requestId,
            UUID turnId, boolean replayed, String locale) {
        return new ChatResponse(replay.answer(), replay.model(), replay.degraded(), replay.reasonCode(), locale, replay.citations(),
                requestId, clientRequestId, turnId, replayed, replay.terminalStatus(), replay.conversationId().toString(), replay.messageId().toString());
    }

    private static UUID parseConversation(String value) { if (value == null || value.isBlank()) return null; try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { throw problem(400, "INVALID_CONVERSATION_ID", "conversationId must be a UUID"); } }
    private static UUID parseUuid(String value) { try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }
    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static String excerpt(String content) { String value = content == null ? "" : content.replaceAll("\\s+", " ").trim(); return value.length() <= 280 ? value : value.substring(0, 277) + "..."; }
    private static boolean containsAnyTerm(ThesisAssistantKnowledgeRepository.KnowledgeDocument document, List<String> terms) { String searchable = (safe(document.title()) + " " + safe(document.content())).toLowerCase(Locale.ROOT); return terms.isEmpty() || terms.stream().anyMatch(searchable::contains); }
    private static void addDocuments(List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> target, Set<String> seen, List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> candidates) {
        for (var candidate : candidates) {
            if (isPublicKnowledgeSafe(candidate) && seen.add(candidate.slug())) target.add(candidate);
        }
    }
    private static boolean isPublicKnowledgeSafe(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        return document != null
                && AssistantInputGuard.isPublicKnowledgeSafe(document.slug())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.title())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.content())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.source());
    }
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "do", "does", "for", "how", "i", "is", "it", "of", "on", "or", "the", "to", "what", "when", "where", "why", "with",
            "em", "anh", "chi", "cho", "cua", "de", "la", "lam", "nen", "nhu", "nhung", "gi", "nao", "toi", "va", "ve", "voi");
    private static List<String> tokenize(String message) {
        return java.util.Arrays.stream(message.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= 2 && !STOP_WORDS.contains(term)).distinct().limit(16).toList();
    }
    private static String noMatchMessage(String locale) { return "vi".equals(locale) ? "Chưa tìm thấy hướng dẫn phù hợp trong kho kiến thức CampusCore công khai." : "No matching public CampusCore guidance was found."; }
    private static String unavailableMessage(String locale) { return "vi".equals(locale) ? "Kho kiến thức CampusCore hiện chưa khả dụng. Vui lòng thử lại sau." : "The CampusCore knowledge base is currently unavailable. Please try again later."; }
    private static String sensitiveMessage(String locale) { return "vi".equals(locale) ? "Vui lòng không nhập email, số điện thoại, mã sinh viên hoặc thông tin bí mật vào trợ lý." : "Please do not enter email addresses, phone numbers, student IDs, or secrets into the assistant."; }
    private static String promptInjectionMessage(String locale) { return "vi".equals(locale) ? "Trợ lý chỉ xử lý câu hỏi học vụ công khai và không thể thực hiện yêu cầu thay đổi chỉ dẫn hệ thống." : "The assistant only handles public academic questions and cannot follow requests to change its system instructions."; }
    private static String safe(String value) { return value == null ? "" : value; }
    private static DomainException problem(int status, String code, String message) { return new DomainException(org.springframework.http.HttpStatus.valueOf(status), code, message); }

    public sealed interface StreamEvent permits StreamMeta, StreamDelta, StreamReplace, StreamCitation, StreamDone, StreamError { }
    public record StreamMeta(UUID requestId, UUID clientRequestId, UUID turnId, UUID conversationId, String model, String locale) implements StreamEvent {
        @JsonProperty("type") public String type() { return "meta"; }
    }
    public record StreamDelta(int sequence, String text, List<String> sourceIds) implements StreamEvent {
        @JsonProperty("type") public String type() { return "delta"; }
    }
    public record StreamReplace(String text, List<String> sourceIds, String reasonCode) implements StreamEvent {
        @JsonProperty("type") public String type() { return "replace"; }
    }
    public record StreamCitation(Citation citation) implements StreamEvent {
        @JsonProperty("type") public String type() { return "citation"; }
    }
    public record StreamDone(UUID messageId, String reasonCode, boolean degraded, String terminalStatus) implements StreamEvent {
        @JsonProperty("type") public String type() { return "done"; }
    }
    public record StreamError(String code, boolean retryable) implements StreamEvent {
        @JsonProperty("type") public String type() { return "error"; }
    }
    private record LexicalResult(String answer, List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents, List<Citation> citations, String context, boolean error, boolean ignored, List<String> sourceIds, String snapshotHash) {
        LexicalResult(String answer, List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents, List<Citation> citations, String context, boolean error, boolean ignored) { this(answer, documents, citations, context, error, ignored, List.of(), ""); }
    }
    private static final class InvalidSegmentException extends RuntimeException { }
}
