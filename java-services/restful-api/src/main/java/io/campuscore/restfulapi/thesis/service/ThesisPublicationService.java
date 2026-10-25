package io.campuscore.restfulapi.thesis.service;

import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.afterCommit;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.conflict;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.instantOf;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.invalid;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireReason;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.subject;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsExcludedTopic;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsPreviewResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RetractPublicationRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundClosureRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisPublicationRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundMutationRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import io.campuscore.restfulapi.thesis.scope.ThesisDeanScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Results publication responsibility of the thesis mutation boundary: the
 * publish preview and its completeness gates, the guarded publish transition
 * and its post-commit fan-out, and the retract/close/reopen escape hatches.
 * Runs inside the transaction the {@link ThesisMutationService} facade opened.
 */
@Service
@Profile("persistence")
public class ThesisPublicationService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ThesisPublicationService.class);

    private final ThesisPublicationRepository publication;
    private final ThesisRoundMutationRepository rounds;
    private final ThesisRoundLifecycleService roundLifecycle;
    private final ThesisRoundReadService roundReads;
    private final ThesisScopeRepository scope;
    private final AdminAuditRecorder audit;
    private final ThesisNotificationService notifier;

    /**
     * Retract-publication grace window in days
     * (thesis.results.retract-grace-days, default 7).
     */
    @Value("${thesis.results.retract-grace-days:7}")
    private int retractGraceDays;

    public ThesisPublicationService(
            ThesisPublicationRepository publication,
            ThesisRoundMutationRepository rounds,
            ThesisRoundLifecycleService roundLifecycle,
            ThesisRoundReadService roundReads,
            ThesisScopeRepository scope,
            AdminAuditRecorder audit,
            ThesisNotificationService notifier) {
        this.publication = publication;
        this.rounds = rounds;
        this.roundLifecycle = roundLifecycle;
        this.roundReads = roundReads;
        this.scope = scope;
        this.audit = audit;
        this.notifier = notifier;
    }

    /**
     * Consequence preview for publish-results: the admin sees exactly which
     * approved groups will publish and which topics are excluded with their
     * outcome (REVIEW_FAILED or WITHDRAWN) before confirming.
     */
    public PublishResultsPreviewResponse publishResultsPreview(UUID roundId, Jwt actor) {
        publication.requireExisting(roundId);
        Map<String, Object> counts = rounds.countApprovalStatesByRound(roundId);
        Map<String, Object> scoring = publication.countScoringStates(roundId);
        List<PublishResultsExcludedTopic> excluded = excludedTopicsQuery(roundId, actor);
        List<PublishResultsExcludedTopic> openReview = publication.listOpenReviewTopics(roundId);
        return new PublishResultsPreviewResponse(
                ((Number) counts.get("group_count")).longValue(),
                ((Number) counts.get("approved_count")).longValue(),
                ((Number) counts.get("pending_count")).longValue(),
                ((Number) scoring.get("included_count")).longValue(),
                ((Number) scoring.get("ungraded_count")).longValue(),
                excluded, openReview);
    }

    /**
     * Excluded topics of a publish preview, scoped like every other
     * topic-level governance read: global admins see the whole round, a
     * faculty head sees only the exclusions inside their own faculty.
     */
    private List<PublishResultsExcludedTopic> excludedTopicsQuery(UUID roundId, Jwt actor) {
        String facultyId = ThesisDeanScope.isFacultyHead(actor) && !ThesisDeanScope.isGlobalAdmin(actor)
                ? ThesisDeanScope.facultyId(scope, actor) : null;
        return publication.listExcludedTopics(roundId, facultyId);
    }

    /**
     * Publishes graded results for the round (brief phase two closure).
     * Requires every approved group in the round to carry a finalized score —
     * publishing with ungraded groups would hand students empty result rows.
     *
     * @param id round whose graded results are published
     * @return the round in the {@code RESULTS_PUBLISHED} status
     * @throws DomainException with code RESULTS_NOT_READY when no topic is graded yet,
     *         SCORES_INCOMPLETE when an approved group still lacks a finalized score, or
     *         ROUND_STATE_CONFLICT when the round is not registration-closed
     */
    public RoundResponse publishResults(UUID id) {
        return publishResults(id, null, null);
    }

    public RoundResponse publishResults(UUID id, Jwt actor) {
        return publishResults(id, null, actor);
    }

    public RoundResponse publishResults(UUID id, Boolean confirmAllExcluded, Jwt actor) {
        publication.requireExisting(id);
        // Lock the round row so a concurrent approveGroup cannot commit between
        // the completeness check below and the guarded status transition.
        publication.lockRoundRow(id);
        Integer approved = publication.countApprovedGroups(id);
        // A sub-threshold counter-review is provisional while its window stays
        // open — the reviewer can still overwrite it — so it neither excludes
        // the topic nor may the round publish past it. Listed by id so the
        // operator can re-review, withdraw, or wait for the deadline.
        List<String> openReview = publication.listOpenReviewIdTitles(id);
        if (!openReview.isEmpty()) {
            throw conflict("REVIEW_WINDOW_OPEN",
                    "The counter-review window is still open for " + openReview.size()
                            + " topic(s): " + String.join(", ", openReview));
        }
        Integer included = publication.countIncludedGroups(id);
        Integer ungraded = publication.countUngradedGroups(id);
        if (approved == null || approved == 0) {
            throw conflict("RESULTS_NOT_READY", "No approved group exists; there is nothing to publish");
        }
        // Publishing a result set where every approved group is excluded must
        // be a deliberate act: the operator confirms in the request and the
        // audit names the confirmation.
        if ((included == null || included == 0) && !Boolean.TRUE.equals(confirmAllExcluded)) {
            throw conflict("RESULTS_ALL_EXCLUDED",
                    "Every approved group's topic is excluded from publication; "
                            + "pass confirmAllExcluded to publish anyway");
        }
        if (included != null && included > 0 && ungraded != null && ungraded.equals(included)) {
            throw conflict("RESULTS_NOT_READY", "No topic has been graded yet; the chair must finalize scores first");
        }
        if (ungraded != null && ungraded > 0) {
            throw conflict("SCORES_INCOMPLETE",
                    "Every approved group must have a finalized score before results are published "
                            + "(" + ungraded + " group(s) still ungraded)");
        }
        // A group left PENDING at publication is permanently wedged — approval
        // requires a non-terminal round — so force the governance team to
        // approve or reject every submission before publishing.
        Integer pending = publication.countPendingGroups(id);
        if (pending != null && pending > 0) {
            throw conflict("GROUPS_PENDING_APPROVAL",
                    "There are still " + pending + " group(s) awaiting approval; approve or reject them before publishing results");
        }
        // The publication audit names its exclusions so a later reader can see
        // which topics were left out of the completeness check and why.
        List<String> excluded = publication.listExcludedIdStatuses(id);
        RoundResponse response = roundLifecycle.transitionRound(id, RoundStatus.REGISTRATION_CLOSED, RoundStatus.RESULTS_PUBLISHED, actor);
        publication.markResultsPublishedAt(id);
        if (!excluded.isEmpty()) {
            String confirmation = (included == null || included == 0)
                    ? " (operator confirmed an all-excluded publication via confirmAllExcluded)" : "";
            audit.record(subject(actor), null, "THESIS_RESULTS_PUBLISHED_WITH_EXCLUSIONS", "THESIS_ROUND",
                    id.toString(),
                    "Results published excluding " + excluded.size() + " topic(s): " + String.join(", ", excluded)
                            + confirmation);
        }
        if (notifier != null) {
            afterCommit(() -> sendPublishedResults(id));
        }
        return response;
    }

    private void sendPublishedResults(UUID roundId) {
        // Both channels read the committed result state. A failed inbox write
        // must not suppress email, and neither failure can undo publication.
        try {
            notifier.notifyResultsPublished(roundId);
        } catch (RuntimeException exception) {
            log.warn("Thesis results committed but inbox fan-out failed for round {}", roundId);
        }
        try {
            notifier.emailResultsPublished(roundId);
        } catch (RuntimeException exception) {
            log.warn("Thesis results committed but email fan-out failed for round {}", roundId);
        }
    }

    /**
     * Retracts a prematurely published result set: allowed only inside the
     * configured grace period after publication, requires a reason plus the
     * typed confirmation "RETRACT", never touches scores, recalls the
     * "results published" inbox notices, notifies the affected students that
     * publication was corrected, and returns the round to REGISTRATION_CLOSED
     * so the normal preview + publish path can republish.
     */
    public RoundResponse retractResults(UUID id, RetractPublicationRequest request, Jwt actor) {
        if (!ThesisDeanScope.isGlobalAdmin(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "RETRACT_ADMIN_REQUIRED",
                    "Only a global admin can retract published results");
        }
        String cleanReason = requireReason(request == null ? null : request.reason());
        if (!"RETRACT".equals(request == null ? null : request.confirmation())) {
            throw invalid("confirmation must be the literal RETRACT");
        }
        Map<String, Object> round = publication.lockRetraction(id);
        if (!RoundStatus.RESULTS_PUBLISHED.name().equals(round.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a published round can be retracted");
        }
        Instant publishedAt = instantOf(round.get("results_published_at"));
        if (publishedAt == null || Instant.now().isAfter(publishedAt.plus(java.time.Duration.ofDays(retractGraceDays)))) {
            throw conflict("RETRACT_WINDOW_CLOSED",
                    "The " + retractGraceDays + "-day retraction grace period has passed; "
                            + "later corrections need the per-topic appeal path");
        }
        int moved = publication.retractToRegistrationClosed(id);
        if (moved != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a published round can be retracted");
        }
        // Inbox rows are recalled; e-mails already delivered cannot be —
        // the audit record states that limitation explicitly.
        audit.record(subject(actor), null, "THESIS_RESULTS_RETRACTED", "THESIS_ROUND", id.toString(),
                "Published results retracted; reason: " + cleanReason
                        + "; typed confirmation accepted; e-mail notifications already sent cannot be recalled",
                Map.of("status", RoundStatus.RESULTS_PUBLISHED.name()),
                Map.of("status", RoundStatus.REGISTRATION_CLOSED.name()));
        afterCommit(() -> {
            if (notifier != null) {
                // The retraction already committed: a recall/notify failure
                // here must not surface as a 500 — log it, mark the audit
                // trail, and let the retract stand.
                try {
                    notifier.recallResultsPublished(id);
                    notifier.notifyResultsRetracted(id);
                } catch (RuntimeException exception) {
                    log.warn("Thesis results retracted for round {} but the inbox recall/notice failed", id, exception);
                    try {
                        audit.record(subject(actor), null, "THESIS_RESULTS_RETRACT_NOTIFY_FAILED", "THESIS_ROUND",
                                id.toString(),
                                "Results were retracted but the inbox recall/notice failed post-commit");
                    } catch (RuntimeException auditFailure) {
                        log.warn("Audit write for the failed retract notice also failed for round {}", id, auditFailure);
                    }
                }
            }
        });
        return roundReads.get(id);
    }

    /**
     * Archives a published round: RESULTS_PUBLISHED -> CLOSED. Closing is the
     * audited end of the lifecycle — the round and everything in it becomes
     * read-only for every mutation path while the published results stay
     * readable. While the retraction grace window is still open the close is
     * refused so a mistaken publication keeps its documented undo path, and
     * {@code results_published_at} is deliberately left untouched so a
     * reopened round is the same publication, not a new one. No notifications
     * are sent: students were already told results are out and an archive is
     * not a result change.
     */
    public RoundResponse closeRound(UUID id, RoundClosureRequest request, Jwt actor) {
        if (!ThesisDeanScope.isGlobalAdmin(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "CLOSE_ADMIN_REQUIRED",
                    "Only a global admin can close a published round");
        }
        String cleanReason = requireReason(request == null ? null : request.reason());
        Map<String, Object> round = publication.lockClosure(id);
        if (!RoundStatus.RESULTS_PUBLISHED.name().equals(round.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a published round can be closed");
        }
        Instant publishedAt = instantOf(round.get("results_published_at"));
        if (publishedAt != null) {
            Instant windowEnds = publishedAt.plus(java.time.Duration.ofDays(retractGraceDays));
            if (Instant.now().isBefore(windowEnds)) {
                throw new DomainException(HttpStatus.CONFLICT, "RETRACT_WINDOW_OPEN",
                        "The retraction grace period is still open for " + windowEnds
                                + "; closing now would silently remove that undo path",
                        Map.of("retractWindowEndsAt", windowEnds.toString(),
                                "remainingHours", java.time.Duration.between(Instant.now(), windowEnds).toHours() + 1));
            }
        }
        int moved = publication.closeToClosed(id);
        if (moved != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a published round can be closed");
        }
        Integer topics = publication.countTopicsInRound(id);
        Integer groups = publication.countGroupsInRound(id);
        audit.record(subject(actor), null, "THESIS_ROUND_CLOSED", "THESIS_ROUND", id.toString(),
                "Round closed (archived); reason: " + cleanReason,
                Map.of("status", RoundStatus.RESULTS_PUBLISHED.name(), "name", round.get("name"),
                        "topics", topics == null ? 0 : topics, "groups", groups == null ? 0 : groups),
                Map.of("status", RoundStatus.CLOSED.name(), "reason", cleanReason));
        return roundReads.get(id);
    }

    /**
     * Reopens an archived round: CLOSED -> RESULTS_PUBLISHED. Only the status
     * moves back — {@code results_published_at} was preserved at close so the
     * round returns to exactly the published state it left, and the retract
     * grace window is already past (close was refused while it was open). No
     * notifications are sent.
     */
    public RoundResponse reopenRound(UUID id, RoundClosureRequest request, Jwt actor) {
        if (!ThesisDeanScope.isGlobalAdmin(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "REOPEN_ADMIN_REQUIRED",
                    "Only a global admin can reopen a closed round");
        }
        String cleanReason = requireReason(request == null ? null : request.reason());
        Map<String, Object> round = publication.lockReopen(id);
        if (!RoundStatus.CLOSED.name().equals(round.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a closed round can be reopened");
        }
        int moved = publication.reopenToResultsPublished(id);
        if (moved != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a closed round can be reopened");
        }
        audit.record(subject(actor), null, "THESIS_ROUND_REOPENED", "THESIS_ROUND", id.toString(),
                "Round reopened to results published; reason: " + cleanReason,
                Map.of("status", RoundStatus.CLOSED.name(), "name", round.get("name")),
                Map.of("status", RoundStatus.RESULTS_PUBLISHED.name(), "reason", cleanReason));
        return roundReads.get(id);
    }
}
