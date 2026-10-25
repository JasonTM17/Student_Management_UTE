package io.campuscore.restfulapi.thesis.service;

import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.conflict;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.hasRole;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.instantOf;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.invalid;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isGlobalAdmin;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireGovernanceRole;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireReason;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireRoundType;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireRoundWindow;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireText;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.scheduleOrder;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.subject;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.tsOf;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.CloseRegistrationPreviewResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundDeletionInfo;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.model.RoundType;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundMutationRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import io.campuscore.restfulapi.thesis.scope.ThesisDeanScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Round lifecycle responsibility of the thesis mutation boundary: schedule
 * authoring and amendment, status transitions, cancellation/restore, empty
 * round deletion, and the deadline escape hatches. Owns the round guards
 * ({@link #requireRoundStatus}, {@link #requireMutableRound}, ...) the sibling
 * thesis services reuse; every statement runs in the transaction the
 * {@link ThesisMutationService} facade already opened.
 */
@Service
@Profile("persistence")
public class ThesisRoundLifecycleService {

    /** Refusal instants use the same dd/MM/yyyy HH:mm Asia/Bangkok display the workspace pins. */
    private static final java.time.format.DateTimeFormatter BANGKOK_MINUTES = java.time.format.DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(java.time.ZoneId.of("Asia/Bangkok"));

    /** Restore targets in lifecycle order — a request may never name RESULTS_PUBLISHED, CLOSED or CANCELLED. */
    private static final List<RoundStatus> RESTORE_TARGET_ORDER = List.of(
            RoundStatus.DRAFT, RoundStatus.PROPOSAL_OPEN, RoundStatus.PROPOSALS_PUBLISHED,
            RoundStatus.REGISTRATION_OPEN, RoundStatus.REGISTRATION_CLOSED);

    private final ThesisRoundMutationRepository rounds;
    private final ThesisRoundReadPort roundReadPort;
    private final ThesisRoundReadService roundReads;
    private final ThesisScopeRepository scope;
    private final AdminAuditRecorder audit;

    public ThesisRoundLifecycleService(
            ThesisRoundMutationRepository rounds,
            ThesisRoundReadPort roundReadPort,
            ThesisRoundReadService roundReads,
            ThesisScopeRepository scope,
            AdminAuditRecorder audit) {
        this.rounds = rounds;
        this.roundReadPort = roundReadPort;
        this.roundReads = roundReads;
        this.scope = scope;
        this.audit = audit;
    }

    /**
     * Creates a registration round and validates the window order its type requires.
     *
     * @param request round name, type, and window instants
     * @return the created round as read back through {@link ThesisRoundReadService}
     * @throws DomainException with code VALIDATION_ERROR when the round type's optional windows are
     *         mismatched (TLCN/KLTN require a GVPB deadline and report date, KLTN also a defense
     *         date) or when the supplied windows are out of chronological order
     */
    public RoundResponse createRound(RoundCreateRequest request) {
        return createRound(request, null);
    }

    public RoundResponse createRound(RoundCreateRequest request, Jwt actor) {
        requireText(request == null ? null : request.name(), "name", 180);
        requireText(request == null ? null : request.thesisType(), "thesisType");
        RoundType roundType = requireRoundType(request.thesisType());
        if (roundType.requiresGvpbDeadline() && request.gvpbDeadline() == null) {
            throw invalid(roundType == RoundType.KLTN
                    ? "KLTN rounds require a gvpbDeadline"
                    : "TLCN rounds require a gvpbDeadline");
        }
        if (!roundType.requiresGvpbDeadline() && request.gvpbDeadline() != null) {
            throw invalid(roundType + " rounds must not specify a gvpbDeadline");
        }
        if (roundType.requiresCouncilReportDate() && request.reportDate() == null) {
            throw invalid(roundType + " rounds require a reportDate");
        }
        if (!roundType.requiresCouncilReportDate() && request.reportDate() != null) {
            throw invalid(roundType + " rounds must not specify a reportDate");
        }
        if (roundType.requiresDefenseDate() && request.defenseDate() == null) {
            throw invalid("KLTN rounds require a defenseDate");
        }
        if (!roundType.requiresDefenseDate() && request.defenseDate() != null) {
            throw invalid(roundType + " rounds must not specify a defenseDate");
        }
        Instant regStart = request.registrationStart();
        Instant regEnd = request.registrationEnd();
        requireRoundWindow(regStart, regEnd, "registrationStart", "registrationEnd", "REGISTRATION_WINDOW");
        Instant letStart = request.lecturerSubmitStart();
        Instant letEnd = request.lecturerSubmitEnd();
        requireRoundWindow(letStart, letEnd, "lecturerSubmitStart", "lecturerSubmitEnd", "LECTURER_WINDOW");
        if (regStart.isBefore(letEnd)) {
            throw scheduleOrder("REGISTRATION_START_BEFORE_LECTURER_END",
                    "registrationStart must not be before lecturerSubmitEnd");
        }
        if (request.proposalPublishAt() != null && request.proposalPublishAt().isBefore(letEnd)) {
            throw scheduleOrder("PROPOSAL_PUBLISH_BEFORE_LECTURER_END",
                    "proposalPublishAt must not be before lecturerSubmitEnd");
        }
        if (request.gvpbDeadline() != null && request.gvpbDeadline().isBefore(regEnd)) {
            throw scheduleOrder("GVPB_DEADLINE_BEFORE_REGISTRATION_END",
                    "gvpbDeadline must not be before registrationEnd");
        }
        if (request.reportDate() != null && request.gvpbDeadline() != null
                && request.reportDate().isBefore(request.gvpbDeadline())) {
            throw scheduleOrder("REPORT_DATE_BEFORE_GVPB_DEADLINE",
                    "reportDate must not be before gvpbDeadline");
        }
        if (request.defenseDate() != null && request.reportDate() != null
                && request.defenseDate().isBefore(request.reportDate())) {
            throw scheduleOrder("DEFENSE_DATE_BEFORE_REPORT_DATE",
                    "defenseDate must not be before reportDate");
        }
        UUID id = UUID.randomUUID();
        rounds.insertRound(id, request.name().trim(), roundType.name(), tsOf(letStart), tsOf(letEnd),
                tsOf(regStart), tsOf(regEnd), tsOf(request.proposalPublishAt()), tsOf(request.gvpbDeadline()),
                tsOf(request.reportDate()), tsOf(request.defenseDate()));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("name", request.name().trim());
        afterState.put("thesis_type", roundType.name());
        afterState.put("lecturer_submit_start", letStart);
        afterState.put("lecturer_submit_end", letEnd);
        afterState.put("registration_start", regStart);
        afterState.put("registration_end", regEnd);
        afterState.put("proposal_publish_at", request.proposalPublishAt());
        afterState.put("gvpb_deadline", request.gvpbDeadline());
        afterState.put("report_date", request.reportDate());
        afterState.put("defense_date", request.defenseDate());
        audit.record(subject(actor), null, "THESIS_ROUND_CREATED", "THESIS_ROUND", id.toString(),
                "Round created (" + roundType.name() + ")", null, afterState);
        return roundReads.get(id);
    }

    /**
     * Moves a round to {@code next} only if it is still in the status the caller expects.
     *
     * @param id round to transition
     * @param expected status the round must currently hold
     * @param next status the round moves to
     * @return the round after the transition
     * @throws ResponseStatusException with status 404 when the round does not exist
     * @throws DomainException with code ROUND_STATE_CONFLICT when a concurrent transition already
     *         moved the round out of {@code expected}, or REGISTRATION_WINDOW_CLOSED when opening
     *         registration outside the stored window
     */
    public RoundResponse transitionRound(UUID id, RoundStatus expected, RoundStatus next) {
        return transitionRound(id, expected, next, null);
    }

    public RoundResponse transitionRound(UUID id, RoundStatus expected, RoundStatus next, Jwt actor) {
        roundReadPort.requireExisting(id);
        int updated = rounds.transitionStatus(id, expected.name(), next.name());
        if (updated != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "Round must be in " + expected.name() + " before it can become " + next.name());
        }
        audit.record(subject(actor), null, "THESIS_ROUND_TRANSITION", "THESIS_ROUND", id.toString(),
                "Round transitioned " + expected.name() + " -> " + next.name());
        if (next == RoundStatus.PROPOSAL_OPEN) {
            // Opening proposals early clamps the authored lecturer window to
            // now; that silently rewrites the published schedule, so the
            // adjustment must be traceable in the audit trail.
            int clamped = rounds.clampLecturerSubmitStart(id);
            if (clamped > 0) {
                audit.record(subject(actor), null, "THESIS_ROUND_SCHEDULE_ADJUSTED", "THESIS_ROUND", id.toString(),
                        "lecturer_submit_start was moved to now when the proposal phase was opened early");
            }
        }
        if (next == RoundStatus.REGISTRATION_OPEN) {
            requireLiveRegistrationWindow(id);
        }
        return roundReads.get(id);
    }

    /**
     * Replaces the authored schedule of a round that has not opened
     * registration yet — operators no longer need direct SQL to fix a mistyped
     * window. The same type-conditional rules as {@link #createRound} apply and
     * the change is audited with the previous and new schedule.
     *
     * @param id round to amend
     * @param request full replacement schedule (same shape as createRound)
     * @param actor caller JWT recorded in the audit row
     * @return the round after the amendment
     * @throws DomainException with code ROUND_STATE_CONFLICT once the round has
     *         passed the proposal phase, or VALIDATION_ERROR for an invalid
     *         schedule
     */
    public RoundResponse updateRound(UUID id, RoundCreateRequest request, Jwt actor) {
        Map<String, Object> current = rounds.lockSchedule(id);
        String status = (String) current.get("status");
        // The brief allows amending the schedule until registration opens;
        // once students can act on the round its dates are frozen.
        if (!Set.of(RoundStatus.DRAFT.name(), RoundStatus.PROPOSAL_OPEN.name(),
                RoundStatus.PROPOSALS_PUBLISHED.name()).contains(status)) {
            throw conflict("ROUND_STATE_CONFLICT",
                    "The schedule can only be amended before registration opens");
        }
        requireText(request == null ? null : request.name(), "name", 180);
        requireText(request == null ? null : request.thesisType(), "thesisType");
        RoundType roundType = requireRoundType(request.thesisType());
        if (roundType.requiresGvpbDeadline() && request.gvpbDeadline() == null) {
            throw invalid(roundType + " rounds require a gvpbDeadline");
        }
        if (!roundType.requiresGvpbDeadline() && request.gvpbDeadline() != null) {
            throw invalid(roundType + " rounds must not specify a gvpbDeadline");
        }
        if (roundType.requiresCouncilReportDate() && request.reportDate() == null) {
            throw invalid(roundType + " rounds require a reportDate");
        }
        if (!roundType.requiresCouncilReportDate() && request.reportDate() != null) {
            throw invalid(roundType + " rounds must not specify a reportDate");
        }
        if (roundType.requiresDefenseDate() && request.defenseDate() == null) {
            throw invalid("KLTN rounds require a defenseDate");
        }
        if (!roundType.requiresDefenseDate() && request.defenseDate() != null) {
            throw invalid(roundType + " rounds must not specify a defenseDate");
        }
        requireRoundWindow(request.registrationStart(), request.registrationEnd(),
                "registrationStart", "registrationEnd", "REGISTRATION_WINDOW");
        requireRoundWindow(request.lecturerSubmitStart(), request.lecturerSubmitEnd(),
                "lecturerSubmitStart", "lecturerSubmitEnd", "LECTURER_WINDOW");
        if (request.registrationStart().isBefore(request.lecturerSubmitEnd())) {
            throw scheduleOrder("REGISTRATION_START_BEFORE_LECTURER_END",
                    "registrationStart must not be before lecturerSubmitEnd");
        }
        if (request.proposalPublishAt() != null && request.proposalPublishAt().isBefore(request.lecturerSubmitEnd())) {
            throw scheduleOrder("PROPOSAL_PUBLISH_BEFORE_LECTURER_END",
                    "proposalPublishAt must not be before lecturerSubmitEnd");
        }
        if (request.gvpbDeadline() != null && request.gvpbDeadline().isBefore(request.registrationEnd())) {
            throw scheduleOrder("GVPB_DEADLINE_BEFORE_REGISTRATION_END",
                    "gvpbDeadline must not be before registrationEnd");
        }
        if (request.reportDate() != null && request.gvpbDeadline() != null
                && request.reportDate().isBefore(request.gvpbDeadline())) {
            throw scheduleOrder("REPORT_DATE_BEFORE_GVPB_DEADLINE",
                    "reportDate must not be before gvpbDeadline");
        }
        if (request.defenseDate() != null && request.reportDate() != null
                && request.defenseDate().isBefore(request.reportDate())) {
            throw scheduleOrder("DEFENSE_DATE_BEFORE_REPORT_DATE",
                    "defenseDate must not be before reportDate");
        }
        Map<String, Object> beforeState = new LinkedHashMap<>();
        for (String column : List.of("name", "thesis_type", "lecturer_submit_start", "lecturer_submit_end",
                "registration_start", "registration_end", "proposal_publish_at", "gvpb_deadline",
                "report_date", "defense_date")) {
            beforeState.put(column, current.get(column));
        }
        rounds.updateSchedule(id, request.name().trim(), roundType.name(),
                tsOf(request.lecturerSubmitStart()), tsOf(request.lecturerSubmitEnd()),
                tsOf(request.registrationStart()), tsOf(request.registrationEnd()),
                tsOf(request.proposalPublishAt()), tsOf(request.gvpbDeadline()),
                tsOf(request.reportDate()), tsOf(request.defenseDate()));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("name", request.name().trim());
        afterState.put("thesis_type", roundType.name());
        afterState.put("lecturer_submit_start", request.lecturerSubmitStart());
        afterState.put("lecturer_submit_end", request.lecturerSubmitEnd());
        afterState.put("registration_start", request.registrationStart());
        afterState.put("registration_end", request.registrationEnd());
        afterState.put("proposal_publish_at", request.proposalPublishAt());
        afterState.put("gvpb_deadline", request.gvpbDeadline());
        afterState.put("report_date", request.reportDate());
        afterState.put("defense_date", request.defenseDate());
        audit.record(subject(actor), null, "THESIS_ROUND_UPDATED", "THESIS_ROUND", id.toString(),
                "Round schedule amended", beforeState, afterState);
        return roundReads.get(id);
    }

    /**
     * Cancels a round that has not published results. Cancellation only moves
     * the round row: groups, rosters, topics, reports, councils and scores all
     * stay as history, and the status the round leaves behind is captured in
     * {@code cancelled_from_status} so {@link #restoreRound} can undo an
     * accidental cancel. Students of the cancelled round are free to join
     * another round because the participation checks treat CANCELLED rounds as
     * holding no seat.
     *
     * @param id round to cancel
     * @param actor caller JWT recorded in the audit row
     * @return the round after cancellation
     * @throws DomainException with code ROUND_STATE_CONFLICT when the round is
     *         already terminal (RESULTS_PUBLISHED, CLOSED, or CANCELLED)
     */
    public RoundResponse cancelRound(UUID id, Jwt actor) {
        roundReadPort.requireExisting(id);
        int updated = rounds.cancelIfNotTerminal(id);
        if (updated != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "A round that already published results cannot be cancelled");
        }
        audit.record(subject(actor), null, "THESIS_ROUND_CANCELLED", "THESIS_ROUND", id.toString(),
                "Round cancelled");
        return roundReads.get(id);
    }

    /**
     * Reverses a cancellation (the admin escape hatch). The round returns to
     * the status captured at cancel time; a caller may instead name an
     * explicit {@code targetStatus} — the only override the lifecycle allows
     * is REGISTRATION_CLOSED in place of a recorded REGISTRATION_OPEN whose
     * window has since closed. Rounds cancelled before
     * {@code cancelled_from_status} existed carry NULL: a legacy round that
     * still has live groups requires an explicit target
     * (ROUND_RESTORE_TARGET_REQUIRED), because its data proves registration
     * ran and DRAFT would silently re-hold seats; an empty legacy round falls
     * back to DRAFT, the only status that fabricates no progress.
     *
     * <p>Nothing else was mutated by the cancel, so nothing is resurrected
     * here — the UPDATE is a pure status flip guarded by the row lock and the
     * {@code status = 'CANCELLED'} predicate, which keeps the optimistic
     * version check honest against a concurrent restore or cancel. Three
     * guards run under the locks before the flip:
     *
     * <ul>
     *   <li>a student who joined another still-live round while this one was
     *   cancelled cannot hold two active seats, so the restore is rejected
     *   with ROUND_RESTORE_MEMBER_CONFLICT rather than corrupting rosters;</li>
     *   <li>a round containing frozen historical small groups must not be
     *   reopened — the V113 trigger is the backstop, and the explicit
     *   ROUND_RESTORE_FROZEN_HISTORY check gives the admin a clear reason;</li>
     *   <li>the members' Student rows are locked before the seat count, in the
     *   same round-then-student order createGroup/addMember take them, so a
     *   registration in flight either commits first (and is counted) or waits
     *   for this flip and then sees the round live again — no interleaving can
     *   leave a student seated in two live rounds.</li>
     * </ul>
     *
     * @param id round to restore
     * @param targetStatus explicit restore target, or null to use the recorded
     *        pre-cancel status (DRAFT for a group-less legacy cancellation)
     * @param actor caller JWT recorded in the audit row
     * @return the round after restoration
     */
    public RoundResponse restoreRound(UUID id, String targetStatus, Jwt actor) {
        Map<String, Object> row = rounds.lockCancellation(id);
        if (!RoundStatus.CANCELLED.name().equals(row.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a cancelled round can be restored");
        }
        String recorded = (String) row.get("cancelled_from_status");
        // Same order as registration (requireRoundStatus locks the round, then
        // requireActiveStudent locks the Student row): round first, students in
        // id order — so this transaction and an in-flight registration for one
        // of these students serialize on the Student row either way.
        rounds.lockRoundStudentIds(id);
        boolean hasLiveGroups = rounds.countLiveGroups(id) > 0;
        boolean explicitTarget = targetStatus != null && !targetStatus.isBlank();
        RoundStatus target = resolveRestoreTarget(recorded, targetStatus, hasLiveGroups);
        // Restoring to an open registration replays the same window guard the
        // open-registration transition enforces; otherwise the round would
        // come back "open" while every roster action still fails.
        if (target == RoundStatus.REGISTRATION_OPEN) {
            requireLiveRegistrationWindow(id);
        }
        Integer seatConflicts = rounds.countSeatConflicts(id);
        if (seatConflicts != null && seatConflicts > 0) {
            throw conflict("ROUND_RESTORE_MEMBER_CONFLICT",
                    seatConflicts + " student(s) already hold an active seat in another live round; "
                            + "restoring this round would give them two active groups");
        }
        Integer frozenHistory = rounds.countFrozenHistory(id);
        if (frozenHistory != null && frozenHistory > 0) {
            throw conflict("ROUND_RESTORE_FROZEN_HISTORY",
                    "This round contains frozen historical small groups and cannot be reopened");
        }
        int updated = rounds.restore(id, target.name());
        if (updated != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "The round is no longer cancelled");
        }
        String targetSource = explicitTarget ? "EXPLICIT"
                : recorded == null ? "LEGACY_DEFAULT" : "RECORDED";
        audit.record(subject(actor), null, "THESIS_ROUND_RESTORED", "THESIS_ROUND", id.toString(),
                "Round restored CANCELLED -> " + target.name(),
                Map.of("status", "CANCELLED",
                        "cancelled_from_status", recorded == null ? "" : recorded),
                Map.of("status", target.name(), "target_source", targetSource));
        return roundReads.get(id);
    }

    /**
     * Hard-deletes a round that was created by mistake. Only an EMPTY round in
     * DRAFT or CANCELLED may be removed — cancel + restore stays the escape
     * hatch for anything that already holds thesis data.
     *
     * <p>The round row is locked FOR UPDATE before any check, matching the
     * round-first lock order every other round mutation already follows, so a
     * concurrent topic/group/council create or a restore either commits first
     * (and is counted, producing ROUND_HAS_DATA) or waits for the delete and
     * then fails its own round lookup. The audit row is written in the same
     * transaction before the DELETE so the log records the round snapshot and
     * the checked counts even though the row itself is gone.
     *
     * <p>Inbox rows are left alone deliberately: result notices are only
     * produced for published rounds (never deletable), and any notice pointing
     * at a group's round implies the group row still exists, which is already
     * a ROUND_HAS_DATA blocker.
     *
     * @param id round to delete
     * @param reason governance reason stored on the audit row
     * @param actor caller JWT recorded in the audit row
     */
    public void deleteRound(UUID id, String reason, Jwt actor) {
        requireGovernanceRole(actor);
        // Rounds are university-wide (no faculty column): the existing
        // governance predicate is a dean whose faculty scope resolves, so a
        // faculty head without an active bound lecturer profile is refused.
        if (hasRole(actor, "TRUONG_KHOA") && !isGlobalAdmin(actor)
                && ThesisDeanScope.facultyId(scope, actor) == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "FACULTY_PROFILE_REQUIRED",
                    "An active lecturer profile bound to the faculty head is required");
        }
        String cleanReason = requireReason(reason);
        Map<String, Object> row = rounds.lockDeletionSnapshot(id);
        RoundStatus status = RoundStatus.valueOf((String) row.get("status"));
        if (status != RoundStatus.DRAFT && status != RoundStatus.CANCELLED) {
            throw conflict("ROUND_NOT_DELETABLE",
                    "Only a never-opened or cancelled round can be deleted; cancel it instead");
        }
        RoundDeletionInfo info = roundReads.deletionInfo(id, status);
        if (!info.deletable()) {
            throw new DomainException(HttpStatus.CONFLICT, "ROUND_HAS_DATA",
                    "Round still has thesis data; cancel it instead of deleting",
                    Map.of("topics", info.topics(), "groups", info.groups(),
                            "members", info.members(), "reports", info.reports(),
                            "councils", info.councils()));
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", row.get("name"));
        before.put("status", row.get("status"));
        before.put("thesis_type", row.get("thesis_type"));
        before.put("reason", cleanReason);
        before.put("checkedCounts", Map.of("topics", info.topics(), "groups", info.groups(),
                "members", info.members(), "reports", info.reports(), "councils", info.councils()));
        audit.record(subject(actor), null, "THESIS_ROUND_DELETED", "THESIS_ROUND", id.toString(),
                "Empty round deleted; reason: " + cleanReason, before, null);
        try {
            rounds.deleteRound(id);
        } catch (org.springframework.dao.DataIntegrityViolationException referenced) {
            // A dependency that slipped past the count — e.g. a new referencing
            // table added without a round lock — must still never cascade.
            throw new DomainException(HttpStatus.CONFLICT, "ROUND_HAS_DATA",
                    "Round is still referenced by thesis data; cancel it instead of deleting",
                    Map.of("topics", info.topics(), "groups", info.groups(),
                            "members", info.members(), "reports", info.reports(),
                            "councils", info.councils()));
        }
    }

    /**
     * A restore may never skip the lifecycle. With a recorded marker the only
     * legal targets are that marker itself and — the documented escape hatch —
     * REGISTRATION_CLOSED when the marker is REGISTRATION_OPEN (the window may
     * legitimately have closed while the round sat cancelled). Without a
     * marker the round's own data is the only evidence: live groups prove
     * registration ran, so no target earlier than REGISTRATION_OPEN is honest,
     * while an empty legacy round may return to any live status and defaults
     * to DRAFT.
     */
    private RoundStatus resolveRestoreTarget(String recorded, String requested, boolean hasLiveGroups) {
        RoundStatus marker = recorded == null || recorded.isBlank() ? null : parseRestoreTarget(recorded);
        if (requested == null || requested.isBlank()) {
            if (marker != null) {
                return marker;
            }
            if (!hasLiveGroups) {
                return RoundStatus.DRAFT;
            }
            throw conflict("ROUND_RESTORE_TARGET_REQUIRED",
                    "This round was cancelled before its previous status was recorded and it still has "
                            + "groups; choose an explicit targetStatus (REGISTRATION_OPEN or REGISTRATION_CLOSED)");
        }
        RoundStatus requestedStatus = parseRestoreTarget(requested);
        if (marker != null) {
            boolean allowed = requestedStatus == marker
                    || (marker == RoundStatus.REGISTRATION_OPEN
                            && requestedStatus == RoundStatus.REGISTRATION_CLOSED);
            if (!allowed) {
                throw conflict("ROUND_RESTORE_TARGET_INVALID",
                        "targetStatus must be " + marker + (marker == RoundStatus.REGISTRATION_OPEN
                                ? " or REGISTRATION_CLOSED" : ""));
            }
            return requestedStatus;
        }
        int floor = hasLiveGroups ? RESTORE_TARGET_ORDER.indexOf(RoundStatus.REGISTRATION_OPEN) : 0;
        if (RESTORE_TARGET_ORDER.indexOf(requestedStatus) < floor) {
            throw conflict("ROUND_RESTORE_TARGET_INVALID",
                    "This round still has groups, so its target cannot be earlier than REGISTRATION_OPEN");
        }
        return requestedStatus;
    }

    private static RoundStatus parseRestoreTarget(String name) {
        try {
            RoundStatus status = RoundStatus.valueOf(name.trim());
            if (RESTORE_TARGET_ORDER.contains(status)) {
                return status;
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw invalid("targetStatus must be a live status: DRAFT, PROPOSAL_OPEN, "
                + "PROPOSALS_PUBLISHED, REGISTRATION_OPEN or REGISTRATION_CLOSED");
    }

    /**
     * Consequence preview for close-registration (plan F2): the admin sees how
     * many non-cancelled groups the round carries by approval state, how many
     * report rows are already submitted, and whether the authored GVPB report
     * deadline has already passed. Read-only by design — the close itself
     * still succeeds when the deadline passed (a hard block would strand the
     * round in REGISTRATION_OPEN forever), so the preview carries the warning
     * instead of enforcing it.
     */
    public CloseRegistrationPreviewResponse closeRegistrationPreview(UUID roundId) {
        Map<String, Object> round = rounds.findEffectiveReportDeadline(roundId);
        Map<String, Object> counts = rounds.countApprovalStatesByRound(roundId);
        Long reportRows = rounds.countGroupReportsByRound(roundId);
        Instant reportDeadline = instantOf(round.get("report_deadline"));
        boolean deadlinePassed = reportDeadline != null && !Instant.now().isBefore(reportDeadline);
        return new CloseRegistrationPreviewResponse(
                ((Number) counts.get("group_count")).longValue(),
                ((Number) counts.get("approved_count")).longValue(),
                ((Number) counts.get("pending_count")).longValue(),
                reportRows == null ? 0 : reportRows,
                reportDeadline,
                deadlinePassed);
    }

    /**
     * The report-window escape hatch (plan F1): a REGISTRATION_CLOSED round
     * whose report deadline already passed used to freeze its reports forever —
     * no transition reopened them and {@code updateRound} is locked once
     * registration opens. This audited mutation moves the {@code
     * report_deadline} override forward — the write window reads
     * COALESCE(report_deadline, gvpb_deadline), so report submission reopens
     * while {@code gvpb_deadline} keeps bounding the GVPB scoring window
     * untouched (fixes plan risk R4). Roster and registration stay closed.
     * MON_HOC/NCKH rounds deliberately have no escape hatch: their freeze is
     * the RESULTS_PUBLISHED lifecycle, which is terminal by design.
     */
    public RoundResponse extendReportDeadline(UUID id, Instant newDeadline, Jwt actor) {
        Map<String, Object> current = rounds.lockReportDeadlineRow(id);
        if (!RoundStatus.REGISTRATION_CLOSED.name().equals(current.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT",
                    "The report deadline can only be extended while the round is REGISTRATION_CLOSED");
        }
        RoundType roundType = requireRoundType((String) current.get("thesis_type"));
        if (!roundType.requiresGvpbDeadline()) {
            throw invalid(roundType + " rounds carry no GVPB deadline; their report window ends at results publication");
        }
        if (newDeadline == null || !newDeadline.isAfter(Instant.now())) {
            throw invalid("reportDeadline must be in the future");
        }
        Instant effectiveDeadline = instantOf(current.get("report_deadline"));
        if (effectiveDeadline == null) {
            effectiveDeadline = instantOf(current.get("gvpb_deadline"));
        }
        if (effectiveDeadline != null && newDeadline.isBefore(effectiveDeadline)) {
            throw invalid("reportDeadline must not be earlier than the current report deadline");
        }
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("report_deadline", instantOf(current.get("report_deadline")));
        rounds.setReportDeadline(id, tsOf(newDeadline));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("report_deadline", newDeadline);
        audit.record(subject(actor), null, "THESIS_ROUND_REPORT_DEADLINE_EXTENDED", "THESIS_ROUND", id.toString(),
                "Report submission deadline extended", beforeState, afterState);
        return roundReads.get(id);
    }

    /**
     * The counter-review escape hatch, sibling of {@link #extendReportDeadline}:
     * a missed {@code gvpb_deadline} left the GVPB window closed forever —
     * {@code updateRound} stops accepting changes after PROPOSALS_PUBLISHED,
     * assignReviewer still succeeded but the replacement could never submit.
     * This audited mutation moves {@code gvpb_deadline} forward while the round
     * is live (PROPOSALS_PUBLISHED, REGISTRATION_OPEN or REGISTRATION_CLOSED).
     * The authored schedule ordering still holds: the new deadline may not pass
     * report_date (thesis_round_schedule_order_valid enforces it), so a deeper
     * extension must pair with a report-date change the normal way.
     */
    public RoundResponse extendGvpbDeadline(UUID id, Instant newDeadline, String reason, Jwt actor) {
        String cleanReason = requireReason(reason);
        Map<String, Object> current = rounds.lockGvpbDeadlineRow(id);
        String status = (String) current.get("status");
        if (!RoundStatus.PROPOSALS_PUBLISHED.name().equals(status)
                && !RoundStatus.REGISTRATION_OPEN.name().equals(status)
                && !RoundStatus.REGISTRATION_CLOSED.name().equals(status)) {
            throw conflict("ROUND_STATE_CONFLICT",
                    "The counter-review deadline can only be extended while the round is live");
        }
        RoundType roundType = requireRoundType((String) current.get("thesis_type"));
        if (!roundType.requiresGvpbDeadline()) {
            throw invalid(roundType + " rounds carry no counter-review deadline");
        }
        if (newDeadline == null || !newDeadline.isAfter(Instant.now())) {
            throw invalid("gvpbDeadline must be in the future");
        }
        Instant currentDeadline = instantOf(current.get("gvpb_deadline"));
        if (currentDeadline != null && newDeadline.isBefore(currentDeadline)) {
            throw invalid("gvpbDeadline must not be earlier than the current counter-review deadline");
        }
        Instant reportDate = instantOf(current.get("report_date"));
        if (reportDate != null && newDeadline.isAfter(reportDate)) {
            throw invalid("gvpbDeadline must not be later than the report date; amend the schedule first");
        }
        // The report write window falls back to gvpb_deadline when no
        // report_deadline override exists, so extending GVPB would silently
        // reopen report editing. Pin the override to the OLD deadline first —
        // extending only the counter-review window, in the same transaction.
        Instant currentReportDeadline = instantOf(current.get("report_deadline"));
        if (currentReportDeadline == null && currentDeadline != null) {
            rounds.setReportDeadline(id, tsOf(currentDeadline));
        }
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("gvpb_deadline", currentDeadline);
        beforeState.put("report_deadline", currentReportDeadline);
        rounds.setGvpbDeadline(id, tsOf(newDeadline));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("gvpb_deadline", newDeadline);
        afterState.put("report_deadline",
                currentReportDeadline == null ? currentDeadline : currentReportDeadline);
        audit.record(subject(actor), null, "THESIS_ROUND_GVPB_DEADLINE_EXTENDED", "THESIS_ROUND", id.toString(),
                "Counter-review deadline extended; reason: " + cleanReason, beforeState, afterState);
        return roundReads.get(id);
    }

    /**
     * The stored registration window is normative for every registration
     * mutation ({@link #requireRoundStatus}) and for the student registration
     * CTA, so the transition into REGISTRATION_OPEN must not be able to create a
     * round that renders as "registration open" while every group and topic
     * action answers 409 REGISTRATION_WINDOW_CLOSED.
     *
     * <p>An out-of-window transition is rejected rather than accepted: the
     * PROPOSAL_OPEN clamp above cannot be mirrored here, because
     * {@code registration_start >= lecturer_submit_end} is part of the authored
     * two-phase schedule (enforced by thesis_round_schedule_order_valid), so
     * moving the registration start to "now" would silently rewrite the
     * published schedule and can violate that oracle. REGISTRATION_OPEN is
     * therefore only reachable inside the window the faculty authored.
     */
    private void requireLiveRegistrationWindow(UUID id) {
        Map<String, Object> round = rounds.findRegistrationWindow(id);
        Instant now = Instant.now();
        Instant start = instantOf(round.get("registration_start"));
        Instant end = instantOf(round.get("registration_end"));
        if (start == null || end == null) {
            throw conflict("REGISTRATION_WINDOW_CLOSED",
                    "The round has no usable registration window (start: " + start + ", end: " + end
                            + "); amend the round schedule before opening registration");
        }
        if (!now.isBefore(end)) {
            throw conflict("REGISTRATION_WINDOW_CLOSED",
                    "The registration window is already closed for this round (window: " + start + " to " + end
                            + "); amend the round schedule before opening registration");
        }
        if (now.isBefore(start)) {
            throw conflict("REGISTRATION_WINDOW_NOT_OPEN",
                    "Registration opens on " + start + "; the round cannot be opened before its scheduled window");
        }
    }

    /**
     * Locks the round row and rejects any write once the round is historical.
     * RESULTS_PUBLISHED and CLOSED are terminal, and the only way out of
     * CANCELLED is {@link #restoreRound}, which takes this same row lock
     * first — so the FOR UPDATE read still fences a concurrent
     * cancel/restore/publish transition and freezes every topic/group
     * mutation behind it.
     */
    void requireMutableRound(UUID roundId) {
        Map<String, Object> round = rounds.lockStatus(roundId);
        if (Set.of(RoundStatus.CANCELLED.name(), RoundStatus.RESULTS_PUBLISHED.name(), RoundStatus.CLOSED.name())
                .contains(round.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "The round has finished; its topics and groups are historical");
        }
    }

    void requireRoundStatusAny(UUID id, RoundStatus... accepted) {
        String status = (String) rounds.lockStatus(id).get("status");
        for (RoundStatus candidate : accepted) {
            if (candidate.name().equals(status)) {
                return;
            }
        }
        throw conflict("ROUND_STATE_CONFLICT", "Round must be in " + accepted[0].name()
                + (accepted.length > 1 ? " or " + accepted[1].name() : ""));
    }

    /** Locked status read sibling services use for their own round-window decisions. */
    String lockRoundStatus(UUID id) {
        return (String) rounds.lockStatus(id).get("status");
    }

    String requireRoundStatus(UUID id, RoundStatus required) {
        Map<String, Object> round = rounds.lockRegistrationGate(id);
        if (!required.name().equals(round.get("status"))) {
            throw conflict("ROUND_CLOSED", "Registration is not open for this round");
        }
        if (required == RoundStatus.REGISTRATION_OPEN) {
            // The stored dates are normative: an open round whose window has
            // passed must not keep accepting registration mutations.
            Instant now = Instant.now();
            Instant start = instantOf(round.get("registration_start"));
            Instant end = instantOf(round.get("registration_end"));
            if (start == null || end == null || !now.isBefore(end)) {
                throw conflict("REGISTRATION_WINDOW_CLOSED", "The registration window is closed for this round");
            }
            if (now.isBefore(start)) {
                // A stale or restored REGISTRATION_OPEN row can still precede
                // its stored start: name the opens-at instant instead of
                // answering "closed" for a window that has not begun.
                throw new DomainException(HttpStatus.CONFLICT, "REGISTRATION_WINDOW_NOT_OPEN",
                        "Registration opens on " + BANGKOK_MINUTES.format(start) + " for this round",
                        Map.of("opensAt", BANGKOK_MINUTES.format(start)));
            }
        }
        return (String) round.get("thesis_type");
    }

    /**
     * Brief phase one: topic work stays in the proposal phase. Lecturer
     * submissions additionally obey the configured lecturer submission window;
     * admins can curate department-governed topics without being registered as
     * a lecturer participant.
     */
    void requireProposalPhase(UUID id, Jwt actor) {
        Map<String, Object> round = rounds.lockProposalWindow(id);
        if (!RoundStatus.PROPOSAL_OPEN.name().equals(round.get("status"))) {
            throw conflict("ROUND_NOT_ACCEPTING_PROPOSALS",
                    "Topics can only be submitted while the round is in its lecturer proposal phase");
        }
        if (io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isAdmin(actor)) {
            return;
        }
        Instant now = Instant.now();
        Instant start = instantOf(round.get("lecturer_submit_start"));
        Instant end = instantOf(round.get("lecturer_submit_end"));
        if (start != null && now.isBefore(start)) {
            throw conflict("LECTURER_WINDOW_NOT_OPEN",
                    "The lecturer topic-submission window has not opened yet for this round (opens: " + start + ")");
        }
        if (end == null || !now.isBefore(end) || start == null) {
            throw conflict("LECTURER_WINDOW_CLOSED", "The lecturer topic-submission window is closed for this round");
        }
    }
}
