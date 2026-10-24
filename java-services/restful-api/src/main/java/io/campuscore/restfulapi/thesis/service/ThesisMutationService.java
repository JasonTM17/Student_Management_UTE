package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.thesis.model.ApprovalStatus;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import io.campuscore.restfulapi.thesis.model.LecturerScope;
import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.model.RoundType;
import io.campuscore.restfulapi.thesis.entity.ThesisTopic;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.CloseRegistrationPreviewResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalWithdrawalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.MemberRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsExcludedTopic;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsPreviewResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundClosureRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.StudentSearchResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicAssignmentRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.LateTopicRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RetractPublicationRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RosterOverrideRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicReassignRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundDeletionInfo;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos.TopicResponse;
import io.campuscore.restfulapi.exception.DomainException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.campuscore.restfulapi.thesis.scope.ThesisDeanScope;
import io.campuscore.restfulapi.thesis.scope.ThesisDepartmentScope;

/** Transaction boundary for the thesis core: rounds, topics and student groups. */
@Service
@Profile("persistence")
public class ThesisMutationService {

    static final int MIN_GROUP_MEMBERS = 3;
    static final int MAX_GROUP_MEMBERS = 5;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ThesisMutationService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ThesisScopeRepository scope;
    private final ThesisRegistrationRoundRepository rounds;
    private final ThesisRoundReadPort roundReadPort;
    private final ThesisTopicRepository topics;
    private final ThesisGroupReadRepository groups;
    private final ThesisRoundReadService roundReads;
    private final AdminAuditRecorder audit;
    private final ThesisNotificationService notifier;
    private final ThesisGroupWithdrawalPolicy withdrawalPolicy;

    /**
     * Accumulated-credit/CPA registration condition (thesis.eligibility.*).
     * Optional so hand-built service instances in narrow tests keep working;
     * the full Spring context always wires it.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ThesisEligibilityService eligibility;

    /**
     * Retract-publication grace window in days
     * (thesis.results.retract-grace-days, default 7).
     */
    @org.springframework.beans.factory.annotation.Value("${thesis.results.retract-grace-days:7}")
    private int retractGraceDays;

    /**
     * Wires the write boundary over the shared JDBC template and the thesis repositories.
     *
     * @param jdbc template used for the governance statements that need row locks
     * @param rounds repository backing round existence checks
     * @param roundReadPort read port used for round status assertions
     * @param topics repository for thesis topics
     * @param groups read repository for thesis groups
     * @param roundReads round read service that renders the responses this service returns
     */
    public ThesisMutationService(
            NamedParameterJdbcTemplate jdbc,
            ThesisRegistrationRoundRepository rounds,
            ThesisRoundReadPort roundReadPort,
            ThesisTopicRepository topics,
            ThesisGroupReadRepository groups,
            ThesisRoundReadService roundReads,
            AdminAuditRecorder audit) {
        this(jdbc, rounds, roundReadPort, topics, groups, roundReads, audit, null,
                new ThesisScopeRepository(jdbc), null);
    }

    public ThesisMutationService(
            NamedParameterJdbcTemplate jdbc,
            ThesisRegistrationRoundRepository rounds,
            ThesisRoundReadPort roundReadPort,
            ThesisTopicRepository topics,
            ThesisGroupReadRepository groups,
            ThesisRoundReadService roundReads,
            AdminAuditRecorder audit,
            ThesisNotificationService notifier) {
        this(jdbc, rounds, roundReadPort, topics, groups, roundReads, audit, notifier,
                new ThesisScopeRepository(jdbc), null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ThesisMutationService(
            NamedParameterJdbcTemplate jdbc,
            ThesisRegistrationRoundRepository rounds,
            ThesisRoundReadPort roundReadPort,
            ThesisTopicRepository topics,
            ThesisGroupReadRepository groups,
            ThesisRoundReadService roundReads,
            AdminAuditRecorder audit,
            ThesisNotificationService notifier,
            ThesisScopeRepository scope,
            ThesisGroupWithdrawalPolicy withdrawalPolicy) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.rounds = rounds;
        this.roundReadPort = roundReadPort;
        this.topics = topics;
        this.groups = groups;
        this.roundReads = roundReads;
        this.audit = audit;
        this.notifier = notifier;
        this.withdrawalPolicy = withdrawalPolicy;
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
    @Transactional
    public RoundResponse createRound(RoundCreateRequest request) {
        return createRound(request, null);
    }

    @Transactional
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
        requireDates(regStart, regEnd, "registrationStart", "registrationEnd");
        Instant letStart = request.lecturerSubmitStart();
        Instant letEnd = request.lecturerSubmitEnd();
        requireDates(letStart, letEnd, "lecturerSubmitStart", "lecturerSubmitEnd");
        if (regStart.isBefore(letEnd)) {
            throw invalid("registrationStart must not be before lecturerSubmitEnd");
        }
        if (request.proposalPublishAt() != null && request.proposalPublishAt().isBefore(letEnd)) {
            throw invalid("proposalPublishAt must not be before lecturerSubmitEnd");
        }
        if (request.gvpbDeadline() != null && request.gvpbDeadline().isBefore(regEnd)) {
            throw invalid("gvpbDeadline must not be before registrationEnd");
        }
        if (request.reportDate() != null && request.gvpbDeadline() != null
                && request.reportDate().isBefore(request.gvpbDeadline())) {
            throw invalid("reportDate must not be before gvpbDeadline");
        }
        if (request.defenseDate() != null && request.reportDate() != null
                && request.defenseDate().isBefore(request.reportDate())) {
            throw invalid("defenseDate must not be before reportDate");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO thesis.thesis_registration_round
                    (id, name, thesis_type, lecturer_submit_start, lecturer_submit_end,
                     registration_start, registration_end,
                     proposal_publish_at, gvpb_deadline, report_date, defense_date, status)
                VALUES (:id, :name, :thesisType, :lecturerSubmitStart, :lecturerSubmitEnd,
                        :registrationStart, :registrationEnd,
                        :proposalPublishAt, :gvpbDeadline, :reportDate, :defenseDate, 'DRAFT')
                """, params()
                .addValue("id", id)
                .addValue("name", request.name().trim())
                .addValue("thesisType", roundType.name())
                .addValue("lecturerSubmitStart", tsOf(letStart))
                .addValue("lecturerSubmitEnd", tsOf(letEnd))
                .addValue("registrationStart", tsOf(regStart))
                .addValue("registrationEnd", tsOf(regEnd))
                .addValue("proposalPublishAt", tsOf(request.proposalPublishAt()))
                .addValue("gvpbDeadline", tsOf(request.gvpbDeadline()))
                .addValue("reportDate", tsOf(request.reportDate()))
                .addValue("defenseDate", tsOf(request.defenseDate())));
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
    @Transactional
    public RoundResponse transitionRound(UUID id, RoundStatus expected, RoundStatus next) {
        return transitionRound(id, expected, next, null);
    }

    @Transactional
    public RoundResponse transitionRound(UUID id, RoundStatus expected, RoundStatus next, Jwt actor) {
        roundReadPort.requireExisting(id);
        int updated = jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = :next, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id AND status = :expected",
                params().addValue("id", id).addValue("expected", expected.name()).addValue("next", next.name()));
        if (updated != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "Round must be in " + expected.name() + " before it can become " + next.name());
        }
        audit.record(subject(actor), null, "THESIS_ROUND_TRANSITION", "THESIS_ROUND", id.toString(),
                "Round transitioned " + expected.name() + " -> " + next.name());
        if (next == RoundStatus.PROPOSAL_OPEN) {
            // Opening proposals early clamps the authored lecturer window to
            // now; that silently rewrites the published schedule, so the
            // adjustment must be traceable in the audit trail.
            int clamped = jdbc.update(
                    "UPDATE thesis.thesis_registration_round "
                            + "SET lecturer_submit_start = LEAST(lecturer_submit_start, CURRENT_TIMESTAMP) "
                            + "WHERE id = :id AND lecturer_submit_start IS NOT NULL AND lecturer_submit_start > CURRENT_TIMESTAMP",
                    params().addValue("id", id));
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
    @Transactional
    public RoundResponse updateRound(UUID id, RoundCreateRequest request, Jwt actor) {
        Map<String, Object> current = one(
                "SELECT name, thesis_type, lecturer_submit_start, lecturer_submit_end, registration_start, "
                        + "registration_end, proposal_publish_at, gvpb_deadline, report_date, defense_date, status "
                        + "FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
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
        requireDates(request.registrationStart(), request.registrationEnd(), "registrationStart", "registrationEnd");
        requireDates(request.lecturerSubmitStart(), request.lecturerSubmitEnd(), "lecturerSubmitStart", "lecturerSubmitEnd");
        if (request.registrationStart().isBefore(request.lecturerSubmitEnd())) {
            throw invalid("registrationStart must not be before lecturerSubmitEnd");
        }
        if (request.proposalPublishAt() != null && request.proposalPublishAt().isBefore(request.lecturerSubmitEnd())) {
            throw invalid("proposalPublishAt must not be before lecturerSubmitEnd");
        }
        if (request.gvpbDeadline() != null && request.gvpbDeadline().isBefore(request.registrationEnd())) {
            throw invalid("gvpbDeadline must not be before registrationEnd");
        }
        if (request.reportDate() != null && request.gvpbDeadline() != null
                && request.reportDate().isBefore(request.gvpbDeadline())) {
            throw invalid("reportDate must not be before gvpbDeadline");
        }
        if (request.defenseDate() != null && request.reportDate() != null
                && request.defenseDate().isBefore(request.reportDate())) {
            throw invalid("defenseDate must not be before reportDate");
        }
        Map<String, Object> beforeState = new LinkedHashMap<>();
        for (String column : List.of("name", "thesis_type", "lecturer_submit_start", "lecturer_submit_end",
                "registration_start", "registration_end", "proposal_publish_at", "gvpb_deadline",
                "report_date", "defense_date")) {
            beforeState.put(column, current.get(column));
        }
        jdbc.update("""
                UPDATE thesis.thesis_registration_round SET
                    name = :name, thesis_type = :thesisType,
                    lecturer_submit_start = :lecturerSubmitStart, lecturer_submit_end = :lecturerSubmitEnd,
                    registration_start = :registrationStart, registration_end = :registrationEnd,
                    proposal_publish_at = :proposalPublishAt, gvpb_deadline = :gvpbDeadline,
                    report_date = :reportDate, defense_date = :defenseDate,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = :id
                """, params()
                .addValue("id", id)
                .addValue("name", request.name().trim())
                .addValue("thesisType", roundType.name())
                .addValue("lecturerSubmitStart", tsOf(request.lecturerSubmitStart()))
                .addValue("lecturerSubmitEnd", tsOf(request.lecturerSubmitEnd()))
                .addValue("registrationStart", tsOf(request.registrationStart()))
                .addValue("registrationEnd", tsOf(request.registrationEnd()))
                .addValue("proposalPublishAt", tsOf(request.proposalPublishAt()))
                .addValue("gvpbDeadline", tsOf(request.gvpbDeadline()))
                .addValue("reportDate", tsOf(request.reportDate()))
                .addValue("defenseDate", tsOf(request.defenseDate())));
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
    @Transactional
    public RoundResponse cancelRound(UUID id, Jwt actor) {
        roundReadPort.requireExisting(id);
        int updated = jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = 'CANCELLED', cancelled_from_status = status, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')",
                params().addValue("id", id));
        if (updated != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "A round that already published results cannot be cancelled");
        }
        audit.record(subject(actor), null, "THESIS_ROUND_CANCELLED", "THESIS_ROUND", id.toString(),
                "Round cancelled");
        return roundReads.get(id);
    }

    /** Restore targets in lifecycle order — a request may never name RESULTS_PUBLISHED, CLOSED or CANCELLED. */
    private static final List<RoundStatus> RESTORE_TARGET_ORDER = List.of(
            RoundStatus.DRAFT, RoundStatus.PROPOSAL_OPEN, RoundStatus.PROPOSALS_PUBLISHED,
            RoundStatus.REGISTRATION_OPEN, RoundStatus.REGISTRATION_CLOSED);

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
    @Transactional
    public RoundResponse restoreRound(UUID id, String targetStatus, Jwt actor) {
        Map<String, Object> row = one(
                "SELECT status, cancelled_from_status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
        if (!RoundStatus.CANCELLED.name().equals(row.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a cancelled round can be restored");
        }
        String recorded = (String) row.get("cancelled_from_status");
        // Same order as registration (requireRoundStatus locks the round, then
        // requireActiveStudent locks the Student row): round first, students in
        // id order — so this transaction and an in-flight registration for one
        // of these students serialize on the Student row either way.
        jdbc.queryForList(
                "SELECT s.\"id\" FROM campuscore_auth.\"Student\" s "
                        + "WHERE s.\"id\" IN ("
                        + "SELECT m.student_id FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "WHERE g.round_id = :id AND g.status <> 'CANCELLED' AND m.active_participation) "
                        + "ORDER BY s.\"id\" FOR UPDATE",
                params().addValue("id", id), String.class);
        boolean hasLiveGroups = count(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND status <> 'CANCELLED'",
                "id", id) > 0;
        boolean explicitTarget = targetStatus != null && !targetStatus.isBlank();
        RoundStatus target = resolveRestoreTarget(recorded, targetStatus, hasLiveGroups);
        // Restoring to an open registration replays the same window guard the
        // open-registration transition enforces; otherwise the round would
        // come back "open" while every roster action still fails.
        if (target == RoundStatus.REGISTRATION_OPEN) {
            requireLiveRegistrationWindow(id);
        }
        Integer seatConflicts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "JOIN thesis.thesis_group_member other ON other.student_id = m.student_id "
                        + "JOIN thesis.thesis_group og ON og.id = other.group_id AND og.status <> 'CANCELLED' "
                        + "JOIN thesis.thesis_registration_round orr ON orr.id = other.round_id "
                        + "WHERE g.round_id = :id AND g.status <> 'CANCELLED' AND m.active_participation "
                        + "AND other.round_id <> :id AND other.active_participation "
                        + "AND orr.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')",
                params().addValue("id", id), Integer.class);
        if (seatConflicts != null && seatConflicts > 0) {
            throw conflict("ROUND_RESTORE_MEMBER_CONFLICT",
                    seatConflicts + " student(s) already hold an active seat in another live round; "
                            + "restoring this round would give them two active groups");
        }
        Integer frozenHistory = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND legacy_group_size_exception",
                params().addValue("id", id), Integer.class);
        if (frozenHistory != null && frozenHistory > 0) {
            throw conflict("ROUND_RESTORE_FROZEN_HISTORY",
                    "This round contains frozen historical small groups and cannot be reopened");
        }
        int updated = jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = :target, cancelled_from_status = NULL, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status = 'CANCELLED'",
                params().addValue("id", id).addValue("target", target.name()));
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
    @Transactional
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
        Map<String, Object> row = one(
                "SELECT name, status, thesis_type, cancelled_from_status "
                        + "FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
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
            jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE id = :id",
                    params().addValue("id", id));
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
    @Transactional(readOnly = true)
    public CloseRegistrationPreviewResponse closeRegistrationPreview(UUID roundId) {
        Map<String, Object> round = one(
                "SELECT COALESCE(report_deadline, gvpb_deadline) AS report_deadline "
                        + "FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", roundId), "ROUND_NOT_FOUND", "Thesis registration round not found");
        Map<String, Object> counts = jdbc.queryForMap(
                "SELECT COUNT(*) AS group_count, "
                        + "COUNT(*) FILTER (WHERE approval_status = 'APPROVED') AS approved_count, "
                        + "COUNT(*) FILTER (WHERE approval_status = 'PENDING') AS pending_count "
                        + "FROM thesis.thesis_group WHERE round_id = :roundId AND status <> 'CANCELLED'",
                params().addValue("roundId", roundId));
        Long reportRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_report WHERE round_id = :roundId",
                params().addValue("roundId", roundId), Long.class);
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
    @Transactional
    public RoundResponse extendReportDeadline(UUID id, Instant newDeadline, Jwt actor) {
        Map<String, Object> current = one(
                "SELECT status, thesis_type, report_deadline, gvpb_deadline FROM thesis.thesis_registration_round "
                        + "WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
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
        jdbc.update(
                "UPDATE thesis.thesis_registration_round SET report_deadline = :reportDeadline, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id",
                params().addValue("id", id).addValue("reportDeadline", tsOf(newDeadline)));
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
    @Transactional
    public RoundResponse extendGvpbDeadline(UUID id, Instant newDeadline, String reason, Jwt actor) {
        String cleanReason = requireReason(reason);
        Map<String, Object> current = one(
                "SELECT status, thesis_type, gvpb_deadline, report_date, report_deadline FROM thesis.thesis_registration_round "
                        + "WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
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
            jdbc.update(
                    "UPDATE thesis.thesis_registration_round SET report_deadline = :reportDeadline, "
                            + "updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id",
                    params().addValue("id", id).addValue("reportDeadline", tsOf(currentDeadline)));
        }
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("gvpb_deadline", currentDeadline);
        beforeState.put("report_deadline", currentReportDeadline);
        jdbc.update(
                "UPDATE thesis.thesis_registration_round SET gvpb_deadline = :gvpbDeadline, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id",
                params().addValue("id", id).addValue("gvpbDeadline", tsOf(newDeadline)));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("gvpb_deadline", newDeadline);
        afterState.put("report_deadline",
                currentReportDeadline == null ? currentDeadline : currentReportDeadline);
        audit.record(subject(actor), null, "THESIS_ROUND_GVPB_DEADLINE_EXTENDED", "THESIS_ROUND", id.toString(),
                "Counter-review deadline extended; reason: " + cleanReason, beforeState, afterState);
        return roundReads.get(id);
    }

    /**
     * Consequence preview for publish-results: the admin sees exactly which
     * approved groups will publish and which topics are excluded with their
     * outcome (REVIEW_FAILED or WITHDRAWN) before confirming.
     */
    @Transactional(readOnly = true)
    public PublishResultsPreviewResponse publishResultsPreview(UUID roundId, Jwt actor) {
        one("SELECT id FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", roundId), "ROUND_NOT_FOUND", "Thesis registration round not found");
        Map<String, Object> counts = jdbc.queryForMap(
                "SELECT COUNT(*) AS group_count, "
                        + "COUNT(*) FILTER (WHERE approval_status = 'APPROVED') AS approved_count, "
                        + "COUNT(*) FILTER (WHERE approval_status = 'PENDING') AS pending_count "
                        + "FROM thesis.thesis_group WHERE round_id = :roundId AND status <> 'CANCELLED'",
                params().addValue("roundId", roundId));
        // A sub-threshold counter-review while its window is still open is
        // provisional — the reviewer may overwrite — so it neither excludes
        // the topic nor blocks anything; the preview lists it separately.
        // The inclusion predicate must match publishResults exactly: a NULL
        // result_status makes the exclusion condition NULL, and NOT NULL is
        // still NULL — so "included" is spelled as IS NULL OR NOT <cond>.
        Map<String, Object> scoring = jdbc.queryForMap(
                "SELECT COUNT(*) FILTER (WHERE s.result_status IS NULL OR NOT s.excluded_condition) AS included_count, "
                        + "COUNT(*) FILTER (WHERE s.final_score IS NULL AND (s.result_status IS NULL OR NOT s.excluded_condition)) AS ungraded_count "
                        + "FROM (SELECT t.final_score, t.result_status, "
                        + "  (t.result_status = 'WITHDRAWN' OR (t.result_status = 'REVIEW_FAILED' "
                        + "    AND r.gvpb_deadline IS NOT NULL AND r.gvpb_deadline <= CURRENT_TIMESTAMP)) "
                        + "  AS excluded_condition "
                        + "  FROM thesis.thesis_group g "
                        + "  JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "  JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "  WHERE g.round_id = :roundId AND g.approval_status = 'APPROVED') s",
                params().addValue("roundId", roundId));
        List<PublishResultsExcludedTopic> excluded = excludedTopicsQuery(roundId, actor);
        List<PublishResultsExcludedTopic> openReview = jdbc.query(
                "SELECT t.id, t.title, t.result_status, t.result_reason FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.round_id = :roundId AND t.result_status = 'REVIEW_FAILED' "
                        + "AND (r.gvpb_deadline IS NULL OR r.gvpb_deadline > CURRENT_TIMESTAMP) "
                        + "ORDER BY t.title",
                params().addValue("roundId", roundId),
                (rs, ignored) -> new PublishResultsExcludedTopic(
                        (UUID) rs.getObject("id"), rs.getString("title"),
                        rs.getString("result_status"), rs.getString("result_reason")));
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
        String facultyScope = "";
        String facultyId = ThesisDeanScope.isFacultyHead(actor) && !ThesisDeanScope.isGlobalAdmin(actor)
                ? ThesisDeanScope.facultyId(scope, actor) : null;
        if (facultyId != null) {
            facultyScope = " AND d.\"facultyId\" = :facultyId";
        }
        return jdbc.query(
                "SELECT t.id, t.title, t.result_status, t.result_reason FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = t.department_id "
                        + "WHERE t.round_id = :roundId "
                        + "AND (t.result_status = 'WITHDRAWN' OR (t.result_status = 'REVIEW_FAILED' "
                        + "  AND r.gvpb_deadline IS NOT NULL AND r.gvpb_deadline <= CURRENT_TIMESTAMP)) "
                        + facultyScope
                        + " ORDER BY t.title",
                facultyId == null
                        ? params().addValue("roundId", roundId)
                        : params().addValue("roundId", roundId).addValue("facultyId", facultyId),
                (rs, ignored) -> new PublishResultsExcludedTopic(
                        (UUID) rs.getObject("id"), rs.getString("title"),
                        rs.getString("result_status"), rs.getString("result_reason")));
    }

    /**
     * Governance outcome for a topic that can no longer complete the round —
     * an unresolvable council/roster failure, a counter-review that cannot be
     * satisfied, a topic the faculty pulled. The topic keeps its data; only
     * its publication obligation is released (publishResults excludes it).
     * Reversible through {@link #reinstateTopic} while no final score exists.
     *
     * @param id topic to withdraw
     * @param reason governance reason (required, at most 500 characters)
     * @param actor caller JWT; admin, or faculty head when the topic sits in
     *        their faculty
     * @return the topic carrying the WITHDRAWN outcome
     * @throws DomainException with code TOPIC_ALREADY_WITHDRAWN for a repeat,
     *         SCORE_ALREADY_FINALIZED for a graded topic, or
     *         ROUND_STATE_CONFLICT for a terminal round
     */
    @Transactional
    public TopicResponse withdrawTopic(UUID id, String reason, Jwt actor) {
        // Service-level guard: the controller annotation alone must not be the
        // only role check on a governance outcome.
        requireGovernanceRole(actor);
        String cleanReason = requireReason(reason);
        requireMutableTopicRound(id);
        lockTopicForGovernance(id);
        // An outcome decision is governance-only: the dean must own the
        // topic's faculty, not merely supervise it.
        ThesisDeanScope.requireTopicGovernanceScope(scope, actor, id);
        Map<String, Object> topic = one(
                "SELECT status, final_score, result_status, title FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (topic.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "A finalized topic cannot be withdrawn");
        }
        if ("WITHDRAWN".equals(topic.get("result_status"))) {
            throw conflict("TOPIC_ALREADY_WITHDRAWN", "The topic is already withdrawn from the round");
        }
        jdbc.update(
                "UPDATE thesis.thesis_topic SET result_status = 'WITHDRAWN', result_reason = :reason, "
                        + "result_by = :by, result_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = :id AND final_score IS NULL",
                params().addValue("id", id).addValue("reason", cleanReason).addValue("by", subject(actor)));
        audit.record(subject(actor), null, "THESIS_TOPIC_WITHDRAWN", "THESIS_TOPIC", id.toString(),
                "Topic withdrawn from the round; reason: " + cleanReason,
                Map.of("result_status", topic.get("result_status") == null ? "" : topic.get("result_status").toString()),
                Map.of("result_status", "WITHDRAWN", "result_reason", cleanReason));
        return TopicResponse.from(topics.findById(id).orElseThrow(
                () -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found")));
    }

    /**
     * Reverses a withdrawal while the topic is still ungraded: the outcome
     * reverts to the counter-review's derived state (REVIEW_FAILED when a
     * below-threshold score still stands and no waiver applies, otherwise no
     * outcome) and the topic joins the completeness requirement again.
     */
    @Transactional
    public TopicResponse reinstateTopic(UUID id, String reason, Jwt actor) {
        requireGovernanceRole(actor);
        String cleanReason = requireReason(reason);
        requireMutableTopicRound(id);
        lockTopicForGovernance(id);
        ThesisDeanScope.requireTopicGovernanceScope(scope, actor, id);
        Map<String, Object> topic = one(
                "SELECT status, final_score, result_status, gvpb_waived_reason, title "
                        + "FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (topic.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "A finalized topic cannot change outcome");
        }
        if (!"WITHDRAWN".equals(topic.get("result_status"))) {
            throw conflict("TOPIC_NOT_WITHDRAWN", "Only a withdrawn topic can be reinstated");
        }
        // Derived rule mirrors ThesisCouncilService.syncReviewOutcome through
        // the shared ThesisReviewOutcome helper: a live sub-threshold
        // counter-review without a waiver marks the topic again.
        boolean failed = topic.get("gvpb_waived_reason") == null
                && ThesisReviewOutcome.isFailingScore(ThesisReviewOutcome.latestGvpbScore(scope, id));
        jdbc.update(
                "UPDATE thesis.thesis_topic SET result_status = :outcome, "
                        + "result_reason = CASE WHEN :outcome = 'REVIEW_FAILED' THEN 'Counter-review score below the 5.0 pass threshold' ELSE NULL END, "
                        + "result_by = CASE WHEN :outcome = 'REVIEW_FAILED' THEN :by ELSE NULL END, "
                        + "result_at = CASE WHEN :outcome = 'REVIEW_FAILED' THEN CURRENT_TIMESTAMP ELSE NULL END, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND final_score IS NULL AND result_status = 'WITHDRAWN'",
                params().addValue("id", id)
                        .addValue("outcome", failed ? "REVIEW_FAILED" : null)
                        .addValue("by", subject(actor)));
        audit.record(subject(actor), null, "THESIS_TOPIC_REINSTATED", "THESIS_TOPIC", id.toString(),
                "Topic reinstated into the round; reason: " + cleanReason,
                Map.of("result_status", "WITHDRAWN"),
                Map.of("result_status", failed ? "REVIEW_FAILED" : ""));
        return TopicResponse.from(topics.findById(id).orElseThrow(
                () -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found")));
    }

    /**
     * Governance-only escape hatch for a missed lecturer window: a topic whose
     * supervisor could not submit in time is created directly in PUBLISHED
     * state while students can still register, never by extending a window the
     * PROPOSAL_OPEN gate no longer consults. The normal topic validation —
     * text fields, department, group limit, an active supervisor, dean faculty
     * scope — still applies, and the reason is audited.
     */
    @Transactional
    public TopicResponse createLateTopic(UUID roundId, LateTopicRequest request, Jwt actor) {
        requireGovernanceRole(actor);
        String cleanReason = requireReason(request == null ? null : request.reason());
        requireText(request == null ? null : request.departmentId(), "departmentId");
        requireText(request == null ? null : request.title(), "title", 240);
        requireText(request == null ? null : request.description(), "description");
        requireText(request.supervisorId(), "supervisorId", 120);
        String supervisorId = request.supervisorId().trim();
        int maxGroups = request.maxGroups() == null ? 1 : request.maxGroups();
        if (maxGroups < 1 || maxGroups > 20) {
            throw invalid("maxGroups must be between 1 and 20");
        }
        // Students must still be able to pick the topic up: once registration
        // closed a late topic would strand it with no group.
        requireRoundStatusAny(roundId, RoundStatus.PROPOSALS_PUBLISHED, RoundStatus.REGISTRATION_OPEN);
        String departmentId = request.departmentId().trim();
        requireTopicDepartmentScope(actor, departmentId);
        // The named supervisor must be a real active lecturer — the supervisor
        // row key joins the Lecturer directory, so storing an unresolvable id
        // would break supervision the way a legacy User.id did.
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = :key AND \"isActive\" = TRUE",
                params().addValue("key", supervisorId), Integer.class);
        if (active == null || active == 0) {
            throw invalid("Unknown or inactive supervisor: " + supervisorId);
        }
        // Created via the DRAFT path like every topic, then promoted to
        // PUBLISHED in the same transaction — the escape hatch never exposes
        // a draft the round already closed proposal submission for.
        ThesisTopic topic = new ThesisTopic(
                roundId, departmentId, request.title().trim(), request.description().trim(),
                maxGroups, subject(actor));
        topic.publish();
        topics.saveAndFlush(topic);
        UUID lateTopicId = topic.getId();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) VALUES (:id, :topicId, :lecturerId, 1)",
                params().addValue("id", UUID.randomUUID()).addValue("topicId", topic.getId())
                        .addValue("lecturerId", supervisorId));
        audit.record(subject(actor), null, "THESIS_TOPIC_LATE_CREATED", "THESIS_TOPIC", topic.getId().toString(),
                "Late topic created for supervisor " + supervisorId + "; reason: " + cleanReason,
                Map.of("status", ""), Map.of("status", TopicStatus.PUBLISHED.name(), "supervisorId", supervisorId));
        afterCommit(() -> {
            if (notifier != null) {
                notifier.notifySupervisorsAssigned(lateTopicId, List.of(supervisorId));
            }
        });
        return topics.findById(lateTopicId).map(TopicResponse::from).orElseGet(() -> TopicResponse.from(topic));
    }

    /**
     * Governance override that adds a student to a roster after the
     * registration window closed: only while the round is REGISTRATION_CLOSED,
     * only into a group whose topic has no scores of any kind, and always with
     * the audited reason. A resulting roster below the minimum additionally
     * requires the explicit size-exception flag and records it on the group —
     * the V113 legacy exception cannot serve because its trigger freezes the
     * whole group.
     *
     * <p>Action REMOVE drops a member under the same guards — the departed or
     * deactivated student case. The member row is deleted, which frees the
     * (round_id, student_id) seat: the student can immediately join another
     * group in this round (the unique index only covers live rows) and in any
     * later round. Removing the leader requires {@code successorStudentId}
     * naming a remaining member so the one-leader invariant holds; emptying
     * the group is refused — dissolving it stays on the existing cancel path.
     */
    @Transactional
    public GroupResponse overrideGroupRoster(UUID groupId, RosterOverrideRequest request, Jwt actor) {
        requireGovernanceRole(actor);
        String cleanReason = requireReason(request == null ? null : request.reason());
        requireText(request == null ? null : request.studentId(), "studentId", 120);
        String studentId = request.studentId().trim();
        String action = request.action() == null || request.action().isBlank()
                ? "ADD" : request.action().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"ADD".equals(action) && !"REMOVE".equals(action)) {
            throw invalid("action must be ADD or REMOVE");
        }
        // The group lock precedes the round lock to match the order every
        // sibling group mutation uses (approve, update, assign, roster edits);
        // the opposite order deadlocks under real Postgres row locks.
        GroupRow group = lockGroup(groupId);
        String thesisType = requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_CLOSED);
        // Post-close roster writes are governance, not review: the dean must
        // govern the faculty that owns the group (the topic's department or,
        // for topicless groups, a member's curriculum). The dean-supervisor
        // escape {@link #isGroupAdmin} keeps for ordinary review work would
        // otherwise let a foreign dean rewrite an approved roster — including
        // the size-exception marker the sibling waiver already reserves to
        // the owning faculty.
        if (!canGovernGroupScope(group, actor)) {
            throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
        }
        Integer topicScores = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = :topicId",
                params().addValue("topicId", group.topicId()), Integer.class);
        if (topicScores != null && topicScores > 0) {
            throw conflict("GROUP_SCORES_EXIST",
                    "The roster cannot change once scoring has started for the group's topic");
        }
        if ("REMOVE".equals(action)) {
            return removeOverriddenMember(group, request, actor, studentId, cleanReason);
        }
        // A cancelled group has no roster to override: ADD must refuse it the
        // same way REMOVE already does instead of inserting a dead row.
        requireActiveRoster(group);
        int memberCount = count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId",
                "groupId", groupId);
        if (memberCount >= MAX_GROUP_MEMBERS) {
            throw conflict("GROUP_FULL", "A thesis group can have at most " + MAX_GROUP_MEMBERS + " members");
        }
        Integer already = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId",
                params().addValue("groupId", groupId).addValue("studentId", studentId), Integer.class);
        if (already != null && already > 0) {
            throw conflict("STUDENT_ALREADY_IN_GROUP", "The student is already a member of this group");
        }
        requireActiveStudent(studentId);
        requireNotInAnotherActiveGroup(group.roundId(), studentId);
        // Governance override is never blocked by eligibility, but admitting
        // an ineligible student must stay visible in the audit record.
        boolean coveredRound = eligibility != null && eligibility.covers(thesisType);
        String ineligibilityNote = !coveredRound ? null
                : eligibility.describeIneligibility(studentId, thesisType);
        // The minimum only binds an APPROVED roster — the deferred membership
        // validator enforces it there and accepts a documented marker. A
        // pending roster has no minimum: growth toward it must always be
        // possible without the exception flag.
        boolean underSize = memberCount + 1 < MIN_GROUP_MEMBERS
                && group.approvalStatus() == ApprovalStatus.APPROVED;
        if (underSize && !Boolean.TRUE.equals(request.allowSizeException())) {
            throw conflict("GROUP_SIZE_EXCEPTION_REQUIRED",
                    "The roster stays below the minimum of " + MIN_GROUP_MEMBERS
                            + "; pass allowSizeException to accept a documented exception");
        }
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, :memberOrder, FALSE)",
                params().addValue("id", UUID.randomUUID()).addValue("groupId", groupId)
                        .addValue("roundId", group.roundId()).addValue("studentId", studentId)
                        .addValue("memberOrder", nextMemberOrder(groupId)));
        // The deferred membership validator only permits a size-exception
        // marker on an APPROVED group — and underSize already implies one —
        // so the marker is written exactly when the exception was required.
        if (underSize) {
            jdbc.update(
                    "UPDATE thesis.thesis_group SET size_exception_reason = :reason, size_exception_by = :by, "
                            + "size_exception_at = CURRENT_TIMESTAMP WHERE id = :groupId",
                    params().addValue("groupId", groupId).addValue("reason", cleanReason).addValue("by", subject(actor)));
        }
        jdbc.update("UPDATE thesis.thesis_group SET updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId",
                params().addValue("groupId", groupId));
        Map<String, Object> overrideAfter = new LinkedHashMap<>();
        overrideAfter.put("memberCount", memberCount + 1);
        overrideAfter.put("sizeException", underSize);
        // null records "not evaluated" — the credit rule does not cover this
        // round type, so neither eligible nor ineligible is claimed.
        overrideAfter.put("studentEligible", coveredRound ? ineligibilityNote == null : null);
        audit.record(subject(actor), null, "THESIS_GROUP_ROSTER_OVERRIDE", "THESIS_GROUP", groupId.toString(),
                "Member " + studentId + " added after registration closed; reason: " + cleanReason
                        + (underSize ? "; below-minimum size exception accepted" : "")
                        + (ineligibilityNote == null ? "" : "; " + ineligibilityNote),
                Map.of("memberCount", memberCount),
                overrideAfter);
        afterCommit(() -> {
            if (notifier != null) {
                notifier.notifyGroupRosterChanged(groupId);
            }
        });
        return groupView(groupId, actor);
    }

    /**
     * The REMOVE branch of the roster override. Shares the entry method's
     * governance role, reason, REGISTRATION_CLOSED round state, dean scope and
     * no-scores guards; the member row is already inside the round -> group ->
     * student lock order because the FOR UPDATE read happens after the group
     * lock.
     */
    private GroupResponse removeOverriddenMember(GroupRow group, RosterOverrideRequest request,
            Jwt actor, String studentId, String cleanReason) {
        requireActiveRoster(group);
        Map<String, Object> member = one(
                "SELECT id, is_leader FROM thesis.thesis_group_member "
                        + "WHERE group_id = :groupId AND student_id = :studentId FOR UPDATE",
                params().addValue("groupId", group.id()).addValue("studentId", studentId),
                "STUDENT_NOT_IN_GROUP", "The student is not a member of this group");
        int memberCount = count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId",
                "groupId", group.id());
        int remaining = memberCount - 1;
        if (remaining == 0) {
            throw conflict("GROUP_EMPTY",
                    "The group cannot be left with no members; cancel the group instead");
        }
        String successorId = null;
        if (Boolean.TRUE.equals(member.get("is_leader"))) {
            successorId = request.successorStudentId() == null ? null : request.successorStudentId().trim();
            if (successorId == null || successorId.isEmpty() || successorId.equals(studentId)) {
                throw conflict("LEADER_SUCCESSOR_REQUIRED",
                        "Removing the leader requires successorStudentId naming a remaining member");
            }
            one("SELECT id FROM thesis.thesis_group_member "
                            + "WHERE group_id = :groupId AND student_id = :studentId FOR UPDATE",
                    params().addValue("groupId", group.id()).addValue("studentId", successorId),
                    "STUDENT_NOT_IN_GROUP", "The successor is not a member of this group");
        }
        // The minimum only binds an APPROVED roster — the same rule the ADD
        // branch applies. A non-approved group can drop under it freely, so
        // the exception flag has nothing to waive and must not be demanded.
        boolean belowMinimum = remaining < MIN_GROUP_MEMBERS;
        boolean sizeException = belowMinimum && group.approvalStatus() == ApprovalStatus.APPROVED;
        if (sizeException && !Boolean.TRUE.equals(request.allowSizeException())) {
            throw conflict("GROUP_SIZE_EXCEPTION_REQUIRED",
                    "The roster stays below the minimum of " + MIN_GROUP_MEMBERS
                            + "; pass allowSizeException to accept a documented exception");
        }
        jdbc.update("DELETE FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId",
                params().addValue("groupId", group.id()).addValue("studentId", studentId));
        if (successorId != null) {
            jdbc.update("UPDATE thesis.thesis_group_member SET is_leader = TRUE "
                            + "WHERE group_id = :groupId AND student_id = :studentId",
                    params().addValue("groupId", group.id()).addValue("studentId", successorId));
            jdbc.update("UPDATE thesis.thesis_group SET leader_student_id = :successor WHERE id = :groupId",
                    params().addValue("groupId", group.id()).addValue("successor", successorId));
        }
        // The deferred membership validator accepts a 1-2 member approved
        // roster only while size_exception_reason is set; a non-approved group
        // has no minimum, so the marker is written for approved groups only.
        if (sizeException) {
            jdbc.update(
                    "UPDATE thesis.thesis_group SET size_exception_reason = :reason, size_exception_by = :by, "
                            + "size_exception_at = CURRENT_TIMESTAMP WHERE id = :groupId",
                    params().addValue("groupId", group.id()).addValue("reason", cleanReason)
                            .addValue("by", subject(actor)));
        }
        jdbc.update("UPDATE thesis.thesis_group SET updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId",
                params().addValue("groupId", group.id()));
        audit.record(subject(actor), null, "THESIS_GROUP_ROSTER_OVERRIDE", "THESIS_GROUP", group.id().toString(),
                "Member " + studentId + " removed after registration closed; reason: " + cleanReason
                        + (successorId != null ? "; leadership passed to " + successorId : "")
                        + (belowMinimum ? (sizeException
                                ? "; below-minimum size exception accepted" : "; below-minimum roster") : ""),
                Map.of("memberCount", memberCount),
                Map.of("memberCount", remaining, "sizeException", sizeException));
        afterCommit(() -> {
            if (notifier != null) {
                notifier.notifyGroupMemberRemoved(group.id(), studentId);
            }
        });
        return groupView(group.id(), actor);
    }

    /**
     * Re-points an approved group whose topic governance withdrew after the
     * registration window closed. Every ordinary path refuses that group —
     * assignTopic needs REGISTRATION_OPEN, rejectGroup needs a PENDING row,
     * and member edits are frozen — so without this hatch the roster is
     * excluded from publication with no way back to a live topic. Approval is
     * kept: it covered the roster, the rebind itself is the audited act, and
     * post-close no path could re-approve a demoted group.
     */
    @Transactional
    public GroupResponse reassignOrphanedTopic(UUID groupId, TopicReassignRequest request, Jwt actor) {
        requireGovernanceRole(actor);
        String cleanReason = requireReason(request == null ? null : request.reason());
        if (request == null || request.topicId() == null) {
            throw invalid("topicId is required");
        }
        UUID newTopicId = request.topicId();
        // The group lock precedes the round lock to match the order every
        // sibling group mutation uses; the topic rows it binds come last.
        GroupRow group = lockGroup(groupId);
        requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_CLOSED);
        requireActiveRoster(group);
        UUID oldTopicId = group.topicId();
        if (oldTopicId == null) {
            throw conflict("GROUP_TOPIC_NOT_ORPHANED", "The group has no topic to re-point away from");
        }
        if (oldTopicId.equals(newTopicId)) {
            throw invalid("topicId must differ from the group's current topic");
        }
        Map<String, Object> oldTopic = one(
                "SELECT result_status, final_score FROM thesis.thesis_topic WHERE id = :id FOR UPDATE",
                params().addValue("id", oldTopicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        // The hatch exists only for a governance-orphaned group: a WITHDRAWN
        // outcome removed its topic from the round. A live or review-failed
        // binding is a different question (the group chose that topic).
        if (!ThesisReviewOutcome.WITHDRAWN.equals(oldTopic.get("result_status"))) {
            throw conflict("GROUP_TOPIC_NOT_ORPHANED",
                    "Only a group whose topic was withdrawn can be re-pointed");
        }
        Map<String, Object> newTopic = one(
                "SELECT round_id, status, max_groups, result_status, final_score FROM thesis.thesis_topic "
                        + "WHERE id = :id FOR UPDATE",
                params().addValue("id", newTopicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        // Re-pointing is post-close governance, not supervision: a dean may
        // only re-point between topics inside their own faculty — supervising
        // either end from another faculty confers no write. Foreign topics
        // conceal as not-found on either end.
        if (!ThesisDeanScope.isGlobalAdmin(actor)) {
            ThesisDeanScope.requireTopicGovernanceScope(scope, actor, oldTopicId);
            ThesisDeanScope.requireTopicGovernanceScope(scope, actor, newTopicId);
        }
        // Any score on either side freezes the binding: re-pointing a scored
        // pair would silently move a verdict the group never earned.
        Integer scores = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id IN (:oldId, :newId)",
                params().addValue("oldId", oldTopicId).addValue("newId", newTopicId), Integer.class);
        if (scores != null && scores > 0) {
            throw conflict("GROUP_SCORES_EXIST",
                    "The binding cannot change once scoring has started on either topic");
        }
        if (oldTopic.get("final_score") != null || newTopic.get("final_score") != null) {
            throw conflict("GROUP_SCORES_EXIST", "A finalized topic binding cannot be re-pointed");
        }
        if (!group.roundId().equals(newTopic.get("round_id"))) {
            throw conflict("TOPIC_ROUND_MISMATCH", "Topic belongs to another registration round");
        }
        if (!TopicStatus.PUBLISHED.name().equals(newTopic.get("status"))
                || newTopic.get("result_status") != null) {
            throw conflict("TOPIC_NOT_PUBLISHED",
                    "Only a published, outcome-free topic can take an orphaned group");
        }
        int maxGroups = ((Number) newTopic.get("max_groups")).intValue();
        if (count("SELECT COUNT(*) FROM thesis.thesis_group WHERE topic_id = :topicId "
                + "AND status <> 'CANCELLED' AND approval_status <> 'REJECTED'", "topicId", newTopicId) >= maxGroups) {
            throw conflict("TOPIC_FULL", "This topic has reached its group limit");
        }
        jdbc.update("UPDATE thesis.thesis_group SET topic_id = :topicId, updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = :groupId",
                params().addValue("topicId", newTopicId).addValue("groupId", groupId));
        audit.record(subject(actor), null, "THESIS_GROUP_TOPIC_REASSIGNED", "THESIS_GROUP", groupId.toString(),
                "Orphaned group re-pointed to a live topic; reason: " + cleanReason,
                Map.of("topicId", oldTopicId.toString()),
                Map.of("topicId", newTopicId.toString()));
        afterCommit(() -> {
            if (notifier != null) {
                notifier.notifyTopicAssigned(groupId, newTopicId);
            }
        });
        return groupView(groupId, actor);
    }

    /**
     * Retracts a prematurely published result set: allowed only inside the
     * configured grace period after publication, requires a reason plus the
     * typed confirmation "RETRACT", never touches scores, recalls the
     * "results published" inbox notices, notifies the affected students that
     * publication was corrected, and returns the round to REGISTRATION_CLOSED
     * so the normal preview + publish path can republish.
     */
    @Transactional
    public RoundResponse retractResults(UUID id, RetractPublicationRequest request, Jwt actor) {
        if (!ThesisDeanScope.isGlobalAdmin(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "RETRACT_ADMIN_REQUIRED",
                    "Only a global admin can retract published results");
        }
        String cleanReason = requireReason(request == null ? null : request.reason());
        if (!"RETRACT".equals(request == null ? null : request.confirmation())) {
            throw invalid("confirmation must be the literal RETRACT");
        }
        Map<String, Object> round = one(
                "SELECT status, results_published_at FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
        if (!RoundStatus.RESULTS_PUBLISHED.name().equals(round.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a published round can be retracted");
        }
        Instant publishedAt = instantOf(round.get("results_published_at"));
        if (publishedAt == null || Instant.now().isAfter(publishedAt.plus(java.time.Duration.ofDays(retractGraceDays)))) {
            throw conflict("RETRACT_WINDOW_CLOSED",
                    "The " + retractGraceDays + "-day retraction grace period has passed; "
                            + "later corrections need the per-topic appeal path");
        }
        int moved = jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = 'REGISTRATION_CLOSED', "
                        + "results_published_at = NULL, updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status = 'RESULTS_PUBLISHED'",
                params().addValue("id", id));
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
    @Transactional
    public RoundResponse closeRound(UUID id, RoundClosureRequest request, Jwt actor) {
        if (!ThesisDeanScope.isGlobalAdmin(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "CLOSE_ADMIN_REQUIRED",
                    "Only a global admin can close a published round");
        }
        String cleanReason = requireReason(request == null ? null : request.reason());
        Map<String, Object> round = one(
                "SELECT status, name, results_published_at FROM thesis.thesis_registration_round "
                        + "WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
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
        int moved = jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = 'CLOSED', updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = :id AND status = 'RESULTS_PUBLISHED'",
                params().addValue("id", id));
        if (moved != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a published round can be closed");
        }
        Integer topics = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic WHERE round_id = :id",
                params().addValue("id", id), Integer.class);
        Integer groups = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id",
                params().addValue("id", id), Integer.class);
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
    @Transactional
    public RoundResponse reopenRound(UUID id, RoundClosureRequest request, Jwt actor) {
        if (!ThesisDeanScope.isGlobalAdmin(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "REOPEN_ADMIN_REQUIRED",
                    "Only a global admin can reopen a closed round");
        }
        String cleanReason = requireReason(request == null ? null : request.reason());
        Map<String, Object> round = one(
                "SELECT status, name FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
        if (!RoundStatus.CLOSED.name().equals(round.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a closed round can be reopened");
        }
        int moved = jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = 'RESULTS_PUBLISHED', "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status = 'CLOSED'",
                params().addValue("id", id));
        if (moved != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "Only a closed round can be reopened");
        }
        audit.record(subject(actor), null, "THESIS_ROUND_REOPENED", "THESIS_ROUND", id.toString(),
                "Round reopened to results published; reason: " + cleanReason,
                Map.of("status", RoundStatus.CLOSED.name(), "name", round.get("name")),
                Map.of("status", RoundStatus.RESULTS_PUBLISHED.name(), "reason", cleanReason));
        return roundReads.get(id);
    }

    private void requireGovernanceRole(Jwt actor) {
        if (!isAdmin(actor)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GOVERNANCE_ROLE_REQUIRED",
                    "This action requires an admin or faculty head role");
        }
    }

    private void requireMutableTopicRound(UUID topicId) {
        UUID roundId = (UUID) one("SELECT round_id FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found").get("round_id");
        requireMutableRound(roundId);
    }

    private static String requireReason(String value) {
        String reason = value == null ? "" : value.trim();
        if (reason.isBlank() || reason.length() > 500) {
            throw invalid("reason is required and must contain at most 500 characters");
        }
        return reason;
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
        Map<String, Object> round = one(
                "SELECT registration_start, registration_end FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
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
    @Transactional
    public RoundResponse publishResults(UUID id) {
        return publishResults(id, null, null);
    }

    @Transactional
    public RoundResponse publishResults(UUID id, Jwt actor) {
        return publishResults(id, null, actor);
    }

    @Transactional
    public RoundResponse publishResults(UUID id, Boolean confirmAllExcluded, Jwt actor) {
        roundReadPort.requireExisting(id);
        // Lock the round row so a concurrent approveGroup cannot commit between
        // the completeness check below and the guarded status transition.
        jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), UUID.class);
        Integer approved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND approval_status = 'APPROVED'",
                params().addValue("id", id), Integer.class);
        // A sub-threshold counter-review is provisional while its window stays
        // open — the reviewer can still overwrite it — so it neither excludes
        // the topic nor may the round publish past it. Listed by id so the
        // operator can re-review, withdraw, or wait for the deadline.
        List<String> openReview = jdbc.queryForList(
                "SELECT CAST(t.id AS VARCHAR) || ' ' || t.title FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.round_id = :id AND t.result_status = 'REVIEW_FAILED' "
                        + "AND (r.gvpb_deadline IS NULL OR r.gvpb_deadline > CURRENT_TIMESTAMP) "
                        + "ORDER BY t.id",
                params().addValue("id", id), String.class);
        if (!openReview.isEmpty()) {
            throw conflict("REVIEW_WINDOW_OPEN",
                    "The counter-review window is still open for " + openReview.size()
                            + " topic(s): " + String.join(", ", openReview));
        }
        // Only definitive exclusions release the completeness check: WITHDRAWN
        // always does, REVIEW_FAILED does once its window has closed.
        String excludedCond = "(t.result_status = 'WITHDRAWN' OR (t.result_status = 'REVIEW_FAILED' "
                + "AND r.gvpb_deadline IS NOT NULL AND r.gvpb_deadline <= CURRENT_TIMESTAMP))";
        // NOT <cond> is NULL (never true) for a NULL result_status, so the
        // included predicate must handle the no-outcome row explicitly.
        String includedCond = "(t.result_status IS NULL OR NOT " + excludedCond + ")";
        Integer included = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group g JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE g.round_id = :id AND g.approval_status = 'APPROVED' "
                        + "AND " + includedCond,
                params().addValue("id", id), Integer.class);
        Integer ungraded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group g JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE g.round_id = :id AND g.approval_status = 'APPROVED' "
                        + "AND t.final_score IS NULL AND " + includedCond,
                params().addValue("id", id), Integer.class);
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
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND approval_status = 'PENDING' AND status <> 'CANCELLED'",
                params().addValue("id", id), Integer.class);
        if (pending != null && pending > 0) {
            throw conflict("GROUPS_PENDING_APPROVAL",
                    "There are still " + pending + " group(s) awaiting approval; approve or reject them before publishing results");
        }
        // The publication audit names its exclusions so a later reader can see
        // which topics were left out of the completeness check and why.
        List<String> excluded = jdbc.queryForList(
                "SELECT CAST(t.id AS VARCHAR) || ' ' || t.result_status FROM thesis.thesis_topic t "
                        + "WHERE t.round_id = :id AND t.result_status IN ('REVIEW_FAILED', 'WITHDRAWN') ORDER BY t.id",
                params().addValue("id", id), String.class);
        RoundResponse response = transitionRound(id, RoundStatus.REGISTRATION_CLOSED, RoundStatus.RESULTS_PUBLISHED, actor);
        jdbc.update(
                "UPDATE thesis.thesis_registration_round SET results_published_at = CURRENT_TIMESTAMP WHERE id = :id",
                params().addValue("id", id));
        if (!excluded.isEmpty()) {
            String confirmation = (included == null || included == 0)
                    ? " (operator confirmed an all-excluded publication via confirmAllExcluded)" : "";
            audit.record(subject(actor), null, "THESIS_RESULTS_PUBLISHED_WITH_EXCLUSIONS", "THESIS_ROUND",
                    id.toString(),
                    "Results published excluding " + excluded.size() + " topic(s): " + String.join(", ", excluded)
                            + confirmation);
        }
        if (notifier != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendPublishedResults(id);
                    }
                });
            } else {
                sendPublishedResults(id);
            }
        }
        return response;
    }

    /**
     * Runs the fan-out only after the mutation commits: the notifier methods
     * run REQUIRES_NEW, so an in-transaction call would still publish a
     * notification for a change a later rollback erased.
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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
     * Drafts a lecturer-owned thesis topic inside the round's proposal window.
     *
     * @param request topic title, description, department, and group limit
     * @param actor caller JWT, which must carry a {@code lecturerId} claim
     * @return the saved draft topic
     * @throws DomainException with code VALIDATION_ERROR for a missing lecturer claim or a group
     *         limit outside 1-20, or LECTURER_WINDOW_NOT_OPEN / LECTURER_WINDOW_CLOSED when the
     *         round is not accepting proposals
     */
    @Transactional
    public TopicResponse createTopic(TopicCreateRequest request, Jwt actor) {
        requireText(request == null ? null : request.departmentId(), "departmentId");
        requireText(request == null ? null : request.title(), "title", 240);
        requireText(request == null ? null : request.description(), "description");
        if (request.roundId() == null) {
            throw invalid("roundId is required");
        }
        int maxGroups = request.maxGroups() == null ? 1 : request.maxGroups();
        if (maxGroups < 1 || maxGroups > 20) {
            throw invalid("maxGroups must be between 1 and 20");
        }
        roundReadPort.requireExisting(request.roundId());
        requireProposalPhase(request.roundId(), actor);
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (isLecturer(actor) && lecturerId.isBlank()) {
            // thesis_topic_supervisor.lecturer_id joins the Lecturer directory;
            // falling back to the User id used to seed a supervisor row that
            // never resolved — silently breaking name display, isSupervisorOf,
            // review and council eligibility. Fail loudly instead.
            throw invalid("A lecturerId claim is required to submit a topic as a lecturer");
        }
        String departmentId = request.departmentId().trim();
        requireTopicDepartmentScope(actor, departmentId);
        ThesisTopic topic = topics.saveAndFlush(new ThesisTopic(
                request.roundId(),
                departmentId,
                request.title().trim(),
                request.description().trim(),
                maxGroups,
                actorId));
        if (isLecturer(actor) || !lecturerId.isBlank()) {
            String supervisorId = !lecturerId.isBlank() ? lecturerId : actorId;
            if (!supervisorId.isBlank()) {
                Integer existingSupervisor = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND (lecturer_id = :supervisorId OR supervisor_order = 1)",
                        params().addValue("topicId", topic.getId()).addValue("supervisorId", supervisorId),
                        Integer.class);
                if (existingSupervisor == null || existingSupervisor == 0) {
                    jdbc.update(
                            "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) VALUES (:id, :topicId, :lecturerId, 1)",
                            params().addValue("id", UUID.randomUUID())
                                    .addValue("topicId", topic.getId())
                                    .addValue("lecturerId", supervisorId));
                }
            }
        }
        return TopicResponse.from(topic);
    }

    /**
     * Edits a topic while it is still a draft.
     *
     * @param id topic to edit
     * @param request replacement title, description, department, and group limit
     * @param actor caller JWT; must govern topics in this department
     * @return the updated topic
     * @throws DomainException with code TOPIC_STATE_CONFLICT once the topic has been published, or
     *         VALIDATION_ERROR for a group limit outside 1-20
     */
    @Transactional
    public TopicResponse updateTopic(UUID id, TopicUpdateRequest request, Jwt actor) {
        // created_by and round_id never change, so the gate read can authorize
        // and locate the round before any lock; the DRAFT check must wait for
        // the locked entity load or a concurrent publish slips past it.
        Map<String, Object> gate = one(
                "SELECT round_id, created_by, department_id FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", "Thesis topic not found");
        // A department head curates draft topics in their own department;
        // everyone else still needs to be the owner or a global admin. The
        // DRAFT-only rule below stays authoritative for the actual write.
        authorizeTopicOwnerOrDepartmentHead(
                String.valueOf(gate.get("created_by")), String.valueOf(gate.get("department_id")), actor);
        // A faculty head may only edit topics already inside their faculty;
        // a foreign topic cannot be claimed by rewriting it onto an in-faculty
        // department. The helper is a no-op for global admins and non-deans,
        // and keeps the personally-supervised exception.
        ThesisDeanScope.requireTopicAccess(scope, actor, id);
        requireMutableRound(UUID.fromString(String.valueOf(gate.get("round_id"))));
        lockTopicForGovernance(id);
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
        // Re-verify governance on the locked row: the gate read above can go
        // stale when a concurrent authorized update transfers the topic to a
        // department the caller does not govern between the early check and
        // this lock (the same re-check publishTopic/archiveTopic already do).
        authorizeTopicOwnerOrDepartmentHead(topic.getCreatedBy(), topic.getDepartmentId(), actor);
        ThesisDeanScope.requireTopicAccess(scope, actor, id);
        if (topic.getStatus() != TopicStatus.DRAFT) {
            throw conflict("TOPIC_STATE_CONFLICT", "Only draft topics can be edited");
        }
        requireText(request == null ? null : request.departmentId(), "departmentId");
        requireText(request == null ? null : request.title(), "title", 240);
        requireText(request == null ? null : request.description(), "description");
        int maxGroups = request.maxGroups() == null ? topic.getMaxGroups() : request.maxGroups();
        if (maxGroups < 1 || maxGroups > 20) {
            throw invalid("maxGroups must be between 1 and 20");
        }
        String departmentId = request.departmentId().trim();
        requireTopicDepartmentScope(actor, departmentId);
        topic.update(departmentId, request.title().trim(), request.description().trim(), maxGroups);
        return TopicResponse.from(topics.save(topic));
    }

    /**
     * Publishes a draft topic so student groups can select it.
     *
     * @param id topic to publish
     * @param actor caller JWT; must be the owning lecturer or an admin
     * @return the published topic
     * @throws DomainException with code TOPIC_SUPERVISOR_REQUIRED when the topic has no supervisor
     *         yet, or TOPIC_STATE_CONFLICT when it cannot leave the draft state
     */
    @Transactional
    public TopicResponse publishTopic(UUID id, Jwt actor) {
        Map<String, Object> gate = one(
                "SELECT round_id, department_id FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (!isAdmin(actor) && !ThesisDepartmentScope.owns(scope, actor, String.valueOf(gate.get("department_id")))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_APPROVER_REQUIRED",
                    "Only department governance can approve a topic");
        }
        Map<String, Object> round = one(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", gate.get("round_id")), "ROUND_NOT_FOUND", "Thesis registration round not found");
        if (!RoundStatus.PROPOSAL_OPEN.name().equals(round.get("status"))) {
            throw conflict("ROUND_NOT_ACCEPTING_PROPOSALS", "Topic approval requires the proposal phase");
        }
        lockTopicForGovernance(id);
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
        // The scope decision must use the department read under the topic
        // lock: the earlier gate read can go stale if a concurrent update
        // moves the topic to another department between the check and here.
        if (!isAdmin(actor) && !ThesisDepartmentScope.owns(scope, actor, topic.getDepartmentId())) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_APPROVER_REQUIRED",
                    "Only department governance can approve a topic");
        }
        Integer supervisorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId",
                params().addValue("topicId", id), Integer.class);
        ThesisDeanScope.requireTopicAccess(scope, actor, id);
        if (supervisorCount == null || supervisorCount < 1 || supervisorCount > 2) {
            throw conflict("TOPIC_SUPERVISOR_REQUIRED",
                    "A topic must have one or two active supervisors before it can be published");
        }
        try {
            topic.publish();
        } catch (IllegalStateException exception) {
            throw conflict("TOPIC_STATE_CONFLICT", exception.getMessage());
        }
        TopicResponse response = TopicResponse.from(topics.saveAndFlush(topic));
        audit.record(subject(actor), null, "THESIS_TOPIC_APPROVED", "THESIS_TOPIC", id.toString(),
                "Department governance approved the topic for student registration");
        return response;
    }

    /** Archives only unused topics; group, council and grading history is never deleted. */
    @Transactional
    public TopicResponse archiveTopic(UUID id, Jwt actor) {
        // Gate read for the early authz pass; the authoritative checks run on
        // the entity loaded under the topic lock below.
        Map<String, Object> gate = one(
                "SELECT round_id, department_id, created_by FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", id), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (!isAdmin(actor) && !ThesisDepartmentScope.owns(scope, actor, String.valueOf(gate.get("department_id")))) {
            if (!isLecturer(actor)) {
                throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_OWNER_REQUIRED",
                        "Only the topic owner or department governance can archive this topic");
            }
            authorizeTopicOwner(String.valueOf(gate.get("created_by")), actor);
        }
        // Lock round before topic (same order as publishTopic) so an archive
        // cannot slip past a concurrent round termination.
        requireMutableRound(UUID.fromString(String.valueOf(gate.get("round_id"))));
        lockTopicForGovernance(id);
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
        // Re-verify scope on the locked read: a concurrent update may have
        // moved the topic between departments since the gate read.
        if (!isAdmin(actor) && !ThesisDepartmentScope.owns(scope, actor, topic.getDepartmentId())) {
            if (!isLecturer(actor)) {
                throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_OWNER_REQUIRED",
                        "Only the topic owner or department governance can archive this topic");
            }
            authorizeTopicOwner(topic, actor);
        }
        ThesisDeanScope.requireTopicAccess(scope, actor, id);
        if (topic.getFinalScore() != null || topic.getResultStatus() != null || topic.getGvpbLecturerId() != null
                || count("SELECT COUNT(*) FROM thesis.thesis_group WHERE topic_id = :topicId", "topicId", id) > 0
                || count("SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE topic_id = :topicId", "topicId", id) > 0
                || count("SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = :topicId", "topicId", id) > 0) {
            throw conflict("TOPIC_IN_USE", "A topic with registration or grading history cannot be archived");
        }
        try {
            topic.archive();
        } catch (IllegalStateException exception) {
            throw conflict("TOPIC_STATE_CONFLICT", exception.getMessage());
        }
        TopicResponse response = TopicResponse.from(topics.saveAndFlush(topic));
        audit.record(subject(actor), null, "THESIS_TOPIC_ARCHIVED", "THESIS_TOPIC", id.toString(),
                "Unused topic archived without removing its history");
        return response;
    }

    private void lockTopicForGovernance(UUID id) {
        one("SELECT id FROM thesis.thesis_topic WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "TOPIC_NOT_FOUND", "Thesis topic not found");
    }

    /**
     * Opens a student group in a round that is currently open for registration.
     *
     * @param request round the group is created in; its {@code roundId} is required
     * @param actor caller JWT of the student who becomes the leader and first member
     * @return the created draft group with the leader as its only member
     * @throws DomainException with code VALIDATION_ERROR for a missing round id, ROUND_CLOSED or
     *         REGISTRATION_WINDOW_CLOSED when registration is not open, FORBIDDEN with code
     *         STUDENT_PROFILE_REQUIRED without an active student profile, and
     *         STUDENT_ALREADY_IN_GROUP or STUDENT_ACTIVE_IN_OTHER_GROUP for a double membership
     */
    @Transactional
    public GroupResponse createGroup(GroupCreateRequest request, Jwt actor) {
        UUID roundId = request == null ? null : request.roundId();
        if (roundId == null) {
            throw invalid("roundId is required");
        }
        String thesisType = requireRoundStatus(roundId, RoundStatus.REGISTRATION_OPEN);
        String studentId = studentId(actor);
        requireActiveStudent(studentId);
        // The caller is the evaluated student: the error may carry figures.
        requireEligibility(studentId, roundId, thesisType, true);
        requireNotInAnotherActiveGroup(roundId, studentId);
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) VALUES (:id, :roundId, :studentId, 'DRAFT', 'PENDING')", params().addValue("id", groupId).addValue("roundId", roundId).addValue("studentId", studentId));
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, 1, TRUE)", params().addValue("id", UUID.randomUUID()).addValue("groupId", groupId).addValue("roundId", roundId).addValue("studentId", studentId));
        return groupView(groupId, actor);
    }

    /**
     * Adds one member to a group, either a campus student or an external member.
     *
     * @param groupId group being staffed
     * @param request student id to invite; a blank id takes the external-member path instead
     * @param actor caller JWT; the group leader, a supervisor, or an admin may manage the roster
     * @return the group with the new member listed
     * @throws DomainException with code GROUP_FULL past five members, GROUP_STATE_CONFLICT once the
     *         supervisor approved the roster, ROUND_CLOSED / REGISTRATION_WINDOW_CLOSED outside the
     *         registration window, and STUDENT_PROFILE_REQUIRED, STUDENT_ALREADY_IN_GROUP, or
     *         STUDENT_ACTIVE_IN_OTHER_GROUP for an unsuitable invitee
     */
    @Transactional
    public GroupResponse addMember(UUID groupId, MemberRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        boolean supervisorPath = authorizeMemberManagement(group, actor);
        String thesisType = requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        requireActiveRoster(group);
        if (!supervisorPath) {
            requireMutableMembership(group, actor);
        }
        if (count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId", "groupId", groupId) >= MAX_GROUP_MEMBERS) {
            throw conflict("GROUP_FULL", "A thesis group can have at most " + MAX_GROUP_MEMBERS
                    + " members (allowed range: " + MIN_GROUP_MEMBERS + " to " + MAX_GROUP_MEMBERS + ")");
        }
        String studentId = normalize(request == null ? null : request.studentId());
        if (studentId.isBlank()) {
            addExternalMember(group, request, actor);
            return groupView(groupId, actor);
        }
        requireActiveStudent(studentId);
        // The figures belong to the invitee, not the calling leader/admin —
        // the refusal discloses them only when caller and invitee coincide.
        requireEligibility(studentId, group.roundId(), thesisType,
                studentId.equals(studentId(actor)));
        requireNotInAnotherActiveGroup(group.roundId(), studentId);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, :memberOrder, FALSE)", params().addValue("id", UUID.randomUUID()).addValue("groupId", groupId).addValue("roundId", group.roundId()).addValue("studentId", studentId).addValue("memberOrder", nextMemberOrder(groupId)));
        jdbc.update("UPDATE thesis.thesis_group SET updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId", params().addValue("groupId", groupId));
        // Rosters are rewritten freely during registration, so without a trail
        // a removed member cannot prove they ever belonged to the group.
        audit.record(subject(actor), null, "THESIS_GROUP_MEMBER_ADDED", "THESIS_GROUP", groupId.toString(),
                "Member " + studentId + " added to thesis group");
        return groupView(groupId, actor);
    }

    /**
     * Members from a different department or school have no student profile to
     * reference; their declared identity is stored on the membership row so the
     * reviewer can verify it, and a synthetic student_id keeps the round's
     * one-group-per-member uniqueness intact.
     */
    private void addExternalMember(GroupRow group, MemberRequest request, Jwt actor) {
        String displayName = normalize(request == null ? null : request.displayName());
        String contact = normalize(request == null ? null : request.contact());
        if (displayName.isBlank() || displayName.length() > 150) {
            throw invalid("displayName is required for members without a student profile (max 150 characters)");
        }
        if (contact.length() > 150) {
            throw invalid("contact must contain at most 150 characters");
        }
        String syntheticId = "external-" + UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader, display_name, contact, is_external) VALUES (:id, :groupId, :roundId, :studentId, :memberOrder, FALSE, :displayName, :contact, TRUE)",
                params()
                        .addValue("id", UUID.randomUUID())
                        .addValue("groupId", group.id())
                        .addValue("roundId", group.roundId())
                        .addValue("studentId", syntheticId)
                        .addValue("memberOrder", nextMemberOrder(group.id()))
                        .addValue("displayName", displayName)
                        .addValue("contact", contact.isBlank() ? null : contact));
        jdbc.update("UPDATE thesis.thesis_group SET updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId", params().addValue("groupId", group.id()));
        audit.record(subject(actor), null, "THESIS_GROUP_MEMBER_ADDED", "THESIS_GROUP", group.id().toString(),
                "External member " + displayName + " added to thesis group");
    }

    /**
     * Drops a student from the group roster.
     *
     * @param groupId group to change
     * @param studentId member to remove
     * @param actor caller JWT; the leader, a supervisor, or an admin
     * @return the group without that member
     * @throws DomainException with code LEADER_CANNOT_BE_REMOVED for the leader, GROUP_TOO_SMALL
     *         when an approved group would fall below three members, MEMBER_NOT_FOUND for an
     *         unknown member, or GROUP_STATE_CONFLICT once the roster is frozen
     */
    @Transactional
    public GroupResponse removeMember(UUID groupId, String studentId, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        String normalized = normalize(studentId);
        boolean selfLeave = normalized.equals(studentId(actor))
                && (group.status() == GroupStatus.DRAFT || group.status() == GroupStatus.SUBMITTED)
                && group.approvalStatus() != ApprovalStatus.APPROVED;
        boolean supervisorPath = false;
        if (!selfLeave) {
            supervisorPath = authorizeMemberManagement(group, actor);
        }
        requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        requireActiveRoster(group);
        if (!supervisorPath && !selfLeave) {
            requireMutableMembership(group, actor);
        }
        if (group.leaderStudentId().equals(normalized)) {
            throw conflict("LEADER_CANNOT_BE_REMOVED", "The group leader cannot be removed");
        }
        if (group.approvalStatus() == ApprovalStatus.APPROVED) {
            Integer memberCount = count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId", "groupId", groupId);
            if (memberCount != null && memberCount <= MIN_GROUP_MEMBERS) {
                int remaining = memberCount - 1;
                throw conflict("GROUP_TOO_SMALL", "An approved thesis group must keep " + MIN_GROUP_MEMBERS + " to "
                        + MAX_GROUP_MEMBERS + " members; this removal would leave " + remaining + " ("
                        + (MIN_GROUP_MEMBERS - remaining) + " short of the minimum of " + MIN_GROUP_MEMBERS
                        + ", add a replacement member first)");
            }
        }
        if (jdbc.update("DELETE FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId", params().addValue("groupId", groupId).addValue("studentId", normalized)) != 1) {
            throw notFound("MEMBER_NOT_FOUND", "Group member not found");
        }
        jdbc.update("UPDATE thesis.thesis_group SET updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId", params().addValue("groupId", groupId));
        audit.record(subject(actor), null, "THESIS_GROUP_MEMBER_REMOVED", "THESIS_GROUP", groupId.toString(),
                "Member " + normalized + " removed from thesis group");
        return groupView(groupId, actor);
    }

    /**
     * Binds a published topic to the group inside the registration window.
     *
     * @param groupId group choosing the topic
     * @param request topic selection carrying a required {@code topicId}
     * @param actor caller JWT; the group leader or an admin
     * @return the group with its topic attached
     * @throws DomainException with code GROUP_STATE_CONFLICT once approved, TOPIC_ROUND_MISMATCH,
     *         TOPIC_NOT_PUBLISHED, or TOPIC_FULL when the topic reached its group limit
     */
    @Transactional
    public GroupResponse assignTopic(UUID groupId, TopicAssignmentRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeLeaderOrAdmin(group, actor);
        String thesisType = requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        // Round-5 post-deploy sweep: the topic binding and approval trace of a
        // CANCELLED group are historical — the roster trigger cannot see this
        // path, so the guard is enforced here too (admin early-return in
        // authorizeLeaderOrAdmin used to let this through).
        requireActiveRoster(group);
        // The leader picks the topic, so the leader must satisfy the
        // accumulated-credit registration condition — an admin caller does not
        // bypass it (governance uses the audited roster override instead).
        requireEligibility(group.leaderStudentId(), group.roundId(), thesisType,
                group.leaderStudentId().equals(studentId(actor)));
        UUID topicId = request == null ? null : request.topicId();
        if (topicId == null) {
            throw invalid("topicId is required");
        }
        if (group.approvalStatus() == ApprovalStatus.APPROVED && !topicId.equals(group.topicId())) {
            // Applies to admins as well: re-pointing an approved group silently
            // cleared approved_by/at below. An admin must reject the group
            // first so the downgrade leaves a review trail.
            throw conflict("GROUP_STATE_CONFLICT", "An approved group cannot change its topic");
        }
        Map<String, Object> topic = one("SELECT id, round_id, status, max_groups, department_id, final_score, result_status FROM thesis.thesis_topic WHERE id = :id FOR UPDATE", params().addValue("id", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        ThesisDeanScope.requireTopicAccess(scope, actor, topicId);
        if (!group.roundId().equals(topic.get("round_id"))) {
            throw conflict("TOPIC_ROUND_MISMATCH", "Topic belongs to another registration round");
        }
        if (!TopicStatus.PUBLISHED.name().equals(topic.get("status")) && !TopicStatus.APPROVED.name().equals(topic.get("status"))) {
            throw conflict("TOPIC_NOT_PUBLISHED", "Only published topics can be selected");
        }
        // A withdrawn topic is excluded from the round by governance; binding
        // another group to it would resurrect a dead pick.
        if (ThesisReviewOutcome.WITHDRAWN.equals(topic.get("result_status"))) {
            throw conflict("TOPIC_WITHDRAWN", "This topic has been withdrawn from the round");
        }
        // A finalized topic must not absorb a new group: the publication gate
        // counts every approved group under it as graded, so a late assignee
        // would inherit a defense score it never earned.
        if (topic.get("final_score") != null && !topicId.equals(group.topicId())) {
            throw conflict("TOPIC_ALREADY_GRADED",
                    "This topic's defense score is finalized; it cannot accept another group");
        }
        int maxGroups = ((Number) topic.get("max_groups")).intValue();
        boolean alreadyOccupiesSlot = topicId.equals(group.topicId()) && group.approvalStatus() != ApprovalStatus.REJECTED;
        if (count("SELECT COUNT(*) FROM thesis.thesis_group WHERE topic_id = :topicId AND status <> 'CANCELLED' AND approval_status <> 'REJECTED'", "topicId", topicId) >= maxGroups && !alreadyOccupiesSlot) {
            throw conflict("TOPIC_FULL", "This topic has reached its group limit");
        }
        boolean isNewTopic = !topicId.equals(group.topicId());
        jdbc.update("UPDATE thesis.thesis_group SET topic_id = :topicId, status = CASE WHEN status = 'DRAFT' THEN 'SUBMITTED' ELSE status END, approval_status = CASE WHEN approval_status = 'REJECTED' OR :isNewTopic THEN 'PENDING' ELSE approval_status END, approved_by = CASE WHEN :isNewTopic THEN NULL ELSE approved_by END, approved_at = CASE WHEN :isNewTopic THEN NULL ELSE approved_at END, rejection_reason = CASE WHEN approval_status = 'REJECTED' OR :isNewTopic THEN NULL ELSE rejection_reason END, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId",
                params().addValue("topicId", topicId).addValue("groupId", groupId).addValue("isNewTopic", isNewTopic));
        // A topic switch resets the approval trace (approved_by/at nulled
        // above), so the binding itself must be traceable.
        audit.record(subject(actor), null, "THESIS_GROUP_TOPIC_ASSIGNED", "THESIS_GROUP", groupId.toString(),
                "Thesis topic " + topicId + " assigned to group");
        if (notifier != null && isNewTopic) {
            afterCommit(() -> {
                try {
                    notifier.notifyTopicAssigned(groupId, topicId);
                } catch (RuntimeException exception) {
                    log.warn("Thesis topic-assigned notification fan-out failed for group {}", groupId, exception);
                }
            });
        }
        return groupView(groupId, actor);
    }

    /**
     * Records group progress without touching the supervisor approval state.
     *
     * @param groupId group whose progress changes
     * @param request progress status, restricted to {@code DRAFT}, {@code SUBMITTED},
     *         {@code COMPLETED}, or {@code CANCELLED}
     * @param actor caller JWT; the leader or an admin
     * @return the group with its new progress status
     * @throws DomainException with code VALIDATION_ERROR for a missing or unsupported status, or
     *         GROUP_STATUS_INVALID when an approved group does something other than complete, or a
     *         completed group reopens
     */
    @Transactional
    public GroupResponse updateProgress(UUID groupId, ProgressRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeLeaderOrAdmin(group, actor);
        requireMutableRound(group.roundId());
        GroupStatus status = request == null ? null : request.status();
        if (status == null) {
            throw invalid("status is required");
        }
        // APPROVED/REJECTED are approval semantics owned by the reviewer flow;
        // nobody sets them through progress updates.
        if (!isProgressStatus(status)) {
            throw invalid("Progress accepts only DRAFT, SUBMITTED, COMPLETED or CANCELLED");
        }
        // Every lifecycle write a dean-role actor performs is governance on
        // the group's faculty, not review: DRAFT and SUBMITTED move the
        // roster the leader owns exactly like CANCELLED and COMPLETED do, so
        // the supervisor escape in isGroupAdmin must not reach them. Only
        // the group's own leader, the owning faculty head and a global
        // admin drive these transitions — a dean who merely supervises the
        // topic keeps the review verbs, never the lifecycle.
        if (hasRole(actor, "TRUONG_KHOA")
                && !group.leaderStudentId().equals(studentId(actor))
                && !canGovernGroupScope(group, actor)) {
            throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
        }
        if (group.status() == GroupStatus.CANCELLED && status != GroupStatus.CANCELLED) {
            throw conflict("GROUP_STATE_CONFLICT", "A cancelled group is historical and cannot reopen");
        }
        // approveGroup flips approval_status while the status stays SUBMITTED, so an
        // approved group must not be demoted or cancelled behind the reviewer's back.
        if (group.approvalStatus() == ApprovalStatus.APPROVED
                && status != GroupStatus.COMPLETED
                && status != group.status()) {
            throw conflict("GROUP_STATUS_INVALID", "An approved group can only be marked COMPLETED");
        }
        // The reverse wedge: a PENDING group marked COMPLETED can never be
        // approved (approveGroup requires status SUBMITTED), so only an
        // approved group may complete.
        if (status == GroupStatus.COMPLETED && group.approvalStatus() != ApprovalStatus.APPROVED) {
            throw conflict("GROUP_STATE_CONFLICT", "Only an approved group can be marked completed");
        }
        if (!isAdmin(actor) && group.status() == GroupStatus.COMPLETED && status != GroupStatus.COMPLETED) {
            throw conflict("GROUP_STATUS_INVALID", "A completed group cannot reopen; contact your supervisor");
        }
        jdbc.update("UPDATE thesis.thesis_group SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId", params().addValue("status", status.name()).addValue("groupId", groupId));
        if (status == GroupStatus.CANCELLED && group.status() != GroupStatus.CANCELLED) {
            // H2's compatibility migration has no PostgreSQL trigger. PostgreSQL also derives
            // this at the database boundary, so this remains an atomic, harmless no-op there.
            jdbc.update("UPDATE thesis.thesis_group_member SET active_participation = FALSE WHERE group_id = :groupId AND active_participation = TRUE",
                    params().addValue("groupId", groupId));
        }
        if (status != group.status()) {
            // Cancellation frees every member's active_participation slot;
            // without this row the freeing actor is untraceable.
            audit.record(subject(actor), null, "THESIS_GROUP_STATUS_CHANGED", "THESIS_GROUP", groupId.toString(),
                    "Group status changed from " + group.status() + " to " + status);
        }
        return groupView(groupId, actor);
    }

    /**
     * Approves a submitted group, freezing its membership for the rest of the round.
     *
     * @param groupId group to approve
     * @param actor caller JWT; a supervisor or an admin
     * @return the approved group
     * @throws DomainException with code GROUP_APPROVAL_STATE_CONFLICT unless the group is
     *         submitted with a topic and still pending, GROUP_TOO_SMALL / GROUP_TOO_LARGE for a
     *         roster outside three to five members, or GROUP_LEADER_INVALID without exactly one
     *         matching leader
     */
    @Transactional
    public GroupResponse approveGroup(UUID groupId, Jwt actor) {
        return approveGroup(groupId, null, actor);
    }

    @Transactional
    public GroupResponse approveGroup(UUID groupId, GroupApprovalRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        boolean supervisorActor = authorizeReviewer(group, actor);
        // The round row is locked so an approval cannot slip between
        // publishResults' completeness check and its guarded transition —
        // a published round must never contain an ungraded approved group.
        String roundStatus = (String) one(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", group.roundId()), "ROUND_NOT_FOUND", "Thesis registration round not found")
                .get("status");
        if (!Set.of(RoundStatus.PROPOSALS_PUBLISHED.name(), RoundStatus.REGISTRATION_OPEN.name(),
                RoundStatus.REGISTRATION_CLOSED.name()).contains(roundStatus)) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT",
                    "Groups can only be approved while the round is accepting or reviewing registrations");
        }
        if (group.status() != GroupStatus.SUBMITTED || group.topicId() == null) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only a submitted group with a topic can be approved");
        }
        // A withdrawn topic is excluded from the round by governance; a group
        // still bound to it is orphaned and must be re-pointed, not approved.
        Map<String, Object> boundTopic = one(
                "SELECT final_score, result_status FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", group.topicId()), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (ThesisReviewOutcome.WITHDRAWN.equals(boundTopic.get("result_status"))) {
            throw conflict("TOPIC_WITHDRAWN", "This topic has been withdrawn from the round");
        }
        // Approving a group under a topic whose defense score is already
        // finalized lets that group inherit a grade it never defended for —
        // the same wedge assignTopic blocks upstream.
        if (boundTopic.get("final_score") != null) {
            throw conflict("TOPIC_ALREADY_GRADED",
                    "This topic's defense score is finalized; new groups can no longer be approved for it");
        }
        Integer memberCount = count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId", "groupId", groupId);
        String sizeExceptionReason = null;
        if (memberCount == null || memberCount < MIN_GROUP_MEMBERS) {
            int present = memberCount == null ? 0 : memberCount;
            if (request == null || !Boolean.TRUE.equals(request.allowSizeException())) {
                throw conflict("GROUP_TOO_SMALL", "A thesis group needs " + MIN_GROUP_MEMBERS + " to " + MAX_GROUP_MEMBERS
                        + " members before it can be approved; this group has " + present + " ("
                        + (MIN_GROUP_MEMBERS - present) + " short of the minimum of " + MIN_GROUP_MEMBERS
                        + ", invite more classmates first)");
            }
            // The waiver is governance-only: a topic supervisor reviews normal
            // rosters but cannot approve under the faculty minimum — and a
            // dean supervising from another faculty is still only a reviewer.
            if (!canGovernGroupScope(group, actor)) {
                throw new DomainException(HttpStatus.FORBIDDEN, "GOVERNANCE_ROLE_REQUIRED",
                        "An under-size approval requires an admin or faculty head role");
            }
            sizeExceptionReason = requireReason(request.reason());
        }
        if (memberCount > MAX_GROUP_MEMBERS) {
            throw conflict("GROUP_TOO_LARGE", "A thesis group can have " + MIN_GROUP_MEMBERS + " to "
                    + MAX_GROUP_MEMBERS + " members; this group has " + memberCount);
        }
        Integer leaderCount = count(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId AND is_leader = TRUE",
                "groupId", groupId);
        Integer matchingLeader = count(
                "SELECT COUNT(*) FROM thesis.thesis_group_member gm "
                        + "JOIN thesis.thesis_group g ON g.id = gm.group_id "
                        + "WHERE gm.group_id = :groupId AND gm.is_leader = TRUE AND gm.student_id = g.leader_student_id",
                "groupId", groupId);
        if (leaderCount == null || leaderCount != 1 || matchingLeader == null || matchingLeader != 1) {
            throw conflict("GROUP_LEADER_INVALID", "A thesis group must have exactly one matching leader");
        }
        // An approver who is not the topic's own supervisor acts as a
        // governance override (admin, faculty head in scope): the decision
        // stays allowed, but the reason becomes mandatory so the override is
        // accountable in the audit trail. A supervisor approving their own
        // topic needs no reason — unchanged. The dedicated code lets clients
        // open the documented-reason dialog instead of a dead-end error.
        String overrideReason = null;
        if (!supervisorActor) {
            String raw = request == null ? null : request.reason();
            if (raw == null || raw.isBlank()) {
                throw new DomainException(HttpStatus.BAD_REQUEST,
                        "GROUP_APPROVAL_OVERRIDE_REASON_REQUIRED",
                        "A governance override approval requires a documented reason");
            }
            overrideReason = requireReason(raw);
        }
        int changed;
        if (sizeExceptionReason == null) {
            changed = jdbc.update("UPDATE thesis.thesis_group SET approval_status='APPROVED', approved_by=:actor, approved_at=CURRENT_TIMESTAMP, rejection_reason=NULL, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                    params().addValue("id", groupId).addValue("actor", subject(actor)));
        } else {
            // The marker lands in the same statement as the approval so the
            // deferred membership validator sees both at commit — an
            // under-size APPROVED roster is only legal while it carries one.
            changed = jdbc.update("UPDATE thesis.thesis_group SET approval_status='APPROVED', approved_by=:actor, approved_at=CURRENT_TIMESTAMP, rejection_reason=NULL, size_exception_reason=:sizeReason, size_exception_by=:actor, size_exception_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                    params().addValue("id", groupId).addValue("actor", subject(actor))
                            .addValue("sizeReason", sizeExceptionReason));
        }
        if (changed != 1) throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only pending groups can be approved");
        // Symmetric with THESIS_GROUP_REJECTED below: an approval decides who
        // proceeds to the defense phase, so the approving actor must be
        // traceable even though approved_by already stores them on the row.
        audit.record(subject(actor), null, "THESIS_GROUP_APPROVED", "THESIS_GROUP", groupId.toString(),
                "Thesis group approved"
                        + (overrideReason == null ? "" : "; governance override: " + overrideReason)
                        + (sizeExceptionReason == null
                                ? "" : "; below-minimum size exception: " + sizeExceptionReason));
        if (notifier != null) {
            afterCommit(() -> {
                try {
                    notifier.notifyGroupDecision(groupId, true, null);
                } catch (RuntimeException exception) {
                    log.warn("Thesis group-approval notification fan-out failed for group {}", groupId, exception);
                }
            });
        }
        return groupView(groupId, actor);
    }

    /**
     * Rejects a submitted group and sends it back with a mandatory reason.
     *
     * @param groupId group to reject
     * @param request rejection reason, 1-500 characters
     * @param actor caller JWT; a supervisor or an admin
     * @return the rejected group
     * @throws DomainException with code GROUP_APPROVAL_STATE_CONFLICT unless the group is
     *         submitted with a topic and still pending, or VALIDATION_ERROR for a blank or
     *         over-long reason
     */
    @Transactional
    public GroupResponse rejectGroup(UUID groupId, GroupRejectionRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeReviewer(group, actor);
        requireMutableRound(group.roundId());
        if (group.status() != GroupStatus.SUBMITTED || group.topicId() == null) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only a submitted group with a topic can be rejected");
        }
        String reason = normalize(request == null ? null : request.reason());
        if (reason.isBlank() || reason.length() > 500) throw invalid("reason is required and must contain at most 500 characters");
        int changed = jdbc.update("UPDATE thesis.thesis_group SET approval_status='REJECTED', approved_by=NULL, approved_at=NULL, rejection_reason=:reason, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                params().addValue("id", groupId).addValue("reason", reason));
        if (changed != 1) throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only pending groups can be rejected");
        // Round-3 contract ct-3: the group row nulls approved_by on rejection and
        // has no rejected_by column, so without this trail the rejecting actor is
        // untraceable — the one approval action with no who. Same lockstep
        // transaction as the mutation (recorder runs MANDATORY).
        audit.record(subject(actor), null, "THESIS_GROUP_REJECTED", "THESIS_GROUP", groupId.toString(),
                "Thesis group rejected; reason: " + reason);
        if (notifier != null) {
            String notifyReason = reason;
            afterCommit(() -> {
                try {
                    notifier.notifyGroupDecision(groupId, false, notifyReason);
                } catch (RuntimeException exception) {
                    log.warn("Thesis group-rejection notification fan-out failed for group {}", groupId, exception);
                }
            });
        }
        return groupView(groupId, actor);
    }

    /**
     * Withdraws a mistaken group approval and returns the group to PENDING —
     * the release path approve previously lacked (reject needs PENDING,
     * cancel refuses APPROVED, and the roster override is REGISTRATION_CLOSED
     * only). The group keeps its roster and topic pick; the approval columns
     * (including any size-exception marker) are cleared so the state is
     * exactly as if it had never been approved.
     *
     * <p>Authorization mirrors the narrow post-close discipline, not the
     * wider reviewer rule: the topic's own supervisors, ADMIN/SUPER_ADMIN,
     * and TRUONG_KHOA / TRUONG_BO_MON strictly through their governance
     * scope — a dean or department head supervising a foreign-scope topic
     * gets no supervisor escape. The round must sit in the same window where
     * approve is legal (PROPOSALS_PUBLISHED, REGISTRATION_OPEN,
     * REGISTRATION_CLOSED), and any downstream work — group report, topic
     * scores, council assignment, assigned reviewer, topic result — blocks
     * the withdrawal with per-kind counts so governance knows to use the
     * roster override instead.
     *
     * <p>Lock order is the same as approve/reject: the group row FOR UPDATE
     * first, then the round row FOR UPDATE, so a concurrent approve, reject,
     * cancel, roster override, council assignment or round transition
     * serializes on one of the two rows before the state is re-checked.
     *
     * @param groupId group whose approval is withdrawn
     * @param request mandatory withdrawal reason (1-500 characters)
     * @param actor caller JWT; supervisor or governance officer in scope
     * @return the group, back in PENDING
     * @throws DomainException with code GROUP_APPROVAL_STATE_CONFLICT unless
     *         the group is approved and submitted in a reviewable round,
     *         GROUP_WITHDRAWAL_BLOCKED (with per-kind counts) once downstream
     *         work exists, VALIDATION_ERROR for a missing or over-long
     *         reason, THESIS_GROUP_NOT_FOUND for out-of-scope governance,
     *         or GROUP_REVIEWER_REQUIRED for anyone else
     */
    @Transactional
    public GroupResponse withdrawGroupApproval(UUID groupId, GroupApprovalWithdrawalRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeApprovalWithdrawal(group, actor);
        // Same FOR UPDATE read as approve: the approval may only move while
        // the round still reviews registrations, and the lock fences a
        // concurrent close/publish transition.
        String roundStatus = (String) one(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", group.roundId()), "ROUND_NOT_FOUND", "Thesis registration round not found")
                .get("status");
        if (!Set.of(RoundStatus.PROPOSALS_PUBLISHED.name(), RoundStatus.REGISTRATION_OPEN.name(),
                RoundStatus.REGISTRATION_CLOSED.name()).contains(roundStatus)) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT",
                    "Approvals can only be withdrawn while the round is accepting or reviewing registrations");
        }
        if (group.approvalStatus() != ApprovalStatus.APPROVED
                || group.status() != GroupStatus.SUBMITTED || group.topicId() == null) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT",
                    "Only an approved submitted group can have its approval withdrawn");
        }
        String reason = requireReason(request == null ? null : request.reason());
        // Once downstream work exists the approval is load-bearing: undoing
        // it would orphan the report, score, council slot, reviewer or
        // result. Refuse with the per-kind counts so governance reaches for
        // the roster override instead.
        Map<String, Object> boundTopic = one(
                "SELECT gvpb_lecturer_id, result_status, final_score FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", group.topicId()), "TOPIC_NOT_FOUND", "Thesis topic not found");
        long reports = count(
                "SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = :groupId", "groupId", groupId);
        long scores = count(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = :topicId", "topicId", group.topicId());
        long councilTopics = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE topic_id = :topicId", "topicId", group.topicId());
        long reviewers = boundTopic.get("gvpb_lecturer_id") != null ? 1 : 0;
        long results = boundTopic.get("result_status") != null || boundTopic.get("final_score") != null ? 1 : 0;
        if (reports + scores + councilTopics + reviewers + results > 0) {
            throw new DomainException(HttpStatus.CONFLICT, "GROUP_WITHDRAWAL_BLOCKED",
                    "The group already has downstream work — reports, scores, council or reviewer "
                            + "assignments, results — so its approval cannot be withdrawn; "
                            + "use the governance roster override instead",
                    Map.of("reports", reports, "scores", scores, "councilTopics", councilTopics,
                            "reviewers", reviewers, "results", results));
        }
        int changed = jdbc.update("UPDATE thesis.thesis_group SET approval_status='PENDING',"
                + " approved_by=NULL, approved_at=NULL, rejection_reason=NULL,"
                + " size_exception_reason=NULL, size_exception_by=NULL, size_exception_at=NULL,"
                + " updated_at=CURRENT_TIMESTAMP, version=version+1"
                + " WHERE id=:id AND approval_status='APPROVED'",
                params().addValue("id", groupId));
        if (changed != 1) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT",
                    "Only an approved group can have its approval withdrawn");
        }
        // Same lockstep transaction as the mutation (recorder runs MANDATORY):
        // who, when, why, plus the group/topic/round coordinates.
        audit.record(subject(actor), null, "THESIS_GROUP_APPROVAL_WITHDRAWN", "THESIS_GROUP",
                groupId.toString(),
                "Thesis group approval withdrawn; reason: " + reason,
                Map.of("approvalStatus", "APPROVED", "topicId", group.topicId().toString(),
                        "roundId", group.roundId().toString()),
                Map.of("approvalStatus", "PENDING"));
        if (notifier != null) {
            String notifyReason = reason;
            afterCommit(() -> {
                try {
                    notifier.notifyGroupApprovalWithdrawn(groupId, notifyReason);
                } catch (RuntimeException exception) {
                    log.warn("Thesis approval-withdrawal notification fan-out failed for group {}", groupId, exception);
                }
            });
        }
        return groupView(groupId, actor);
    }

    /**
     * Withdraw-approval authority is narrower than {@link #authorizeReviewer}:
     * governance officers act only inside their scope and get no supervisor
     * escape (the same discipline {@link #updateProgress} and the roster
     * override apply); everyone else must be a current supervisor of the
     * group's topic.
     */
    private void authorizeApprovalWithdrawal(GroupRow group, Jwt actor) {
        if (isGlobalAdmin(actor)) {
            return;
        }
        if (hasRole(actor, "TRUONG_KHOA")) {
            if (groups.groupInFaculty(group.id(), requireDeanScope(actor).facultyId())) {
                return;
            }
            throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
        }
        if (ThesisDepartmentScope.isDepartmentHead(actor)) {
            String department = ThesisDepartmentScope.departmentId(scope, actor);
            if (!department.isEmpty() && groups.groupInDepartment(group.id(), department)) {
                return;
            }
            throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
        }
        if (group.topicId() != null && isCurrentSupervisor(group.topicId(), actor)) {
            return;
        }
        throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_REVIEWER_REQUIRED",
                "Only an assigned supervisor or a governance officer in scope can withdraw this approval");
    }

    /**
     * The group response enriched with the actor's withdraw-approval
     * affordance; the policy bean stays optional so plain fixture
     * constructors keep working.
     */
    private GroupResponse groupView(UUID groupId, Jwt actor) {
        GroupResponse group = groups.findById(groupId);
        return withdrawalPolicy == null ? group : withdrawalPolicy.attach(group, actor);
    }

    /**
     * Directory lookup used by group leaders to invite classmates into the
     * current round: matches active students by student number or full name
     * (max 8 hits). Only exists while a round is still live — outside an
     * active round there is nothing to join, and an always-on lookup would let
     * any student enumerate the whole student directory. Email addresses are
     * never returned: the invite flow only needs the student number.
     *
     * @param query free-text student number or name fragment, at least two characters
     * @return up to eight matching active students ordered by student number; empty when the query
     *         is too short or no round is live enough to join
     */
    public List<StudentSearchResponse> searchStudents(String query) {
        String normalized = normalize(query).toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() < 2) {
            return List.of();
        }
        // Teammate search only matters while a round actually accepts roster
        // changes (addMember requires REGISTRATION_OPEN); outside that window
        // the directory stays off instead of answering lookups it cannot serve.
        Integer liveRounds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_registration_round"
                        + " WHERE status = 'REGISTRATION_OPEN'",
                params(),
                Integer.class);
        if (liveRounds == null || liveRounds == 0) {
            return List.of();
        }
        List<String> tokens = io.campuscore.restfulapi.common.util.LikePattern.tokens(normalized);
        if (tokens.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource searchParams = params()
                .addValue("foldFrom", io.campuscore.restfulapi.common.util.LikePattern.FOLD_FROM)
                .addValue("foldTo", io.campuscore.restfulapi.common.util.LikePattern.FOLD_TO);
        StringBuilder tokenPredicates = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String paramName = "token" + i;
            searchParams.addValue(paramName, tokens.get(i));
            // Search covers student number and names only — an email predicate
            // would double as an existence oracle for student addresses.
            tokenPredicates.append(" AND (TRANSLATE(LOWER(s.\"studentId\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(u.\"firstName\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(u.\"lastName\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\')");
        }
        return jdbc.query(
                "SELECT s.\"id\", s.\"studentId\" AS student_number, u.\"firstName\", u.\"lastName\","
                        + " cur.\"code\" AS curriculum_code, cur.\"name\" AS curriculum_name"
                        + " FROM campuscore_auth.\"Student\" s"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\""
                        + " LEFT JOIN academic.\"Curriculum\" cur ON cur.\"id\" = s.\"curriculumId\""
                        + " WHERE s.\"status\" = 'ACTIVE'"
                        + tokenPredicates
                        + " ORDER BY s.\"studentId\" LIMIT 8",
                searchParams,
                (rs, ignored) -> new StudentSearchResponse(
                        rs.getString("id"),
                        rs.getString("student_number"),
                        rs.getString("firstName"),
                        rs.getString("lastName"),
                        rs.getString("curriculum_code"),
                        rs.getString("curriculum_name")));
    }

    private GroupRow lockGroup(UUID id) {
        Map<String, Object> row = one("SELECT id, round_id, leader_student_id, topic_id, status, approval_status FROM thesis.thesis_group WHERE id = :id FOR UPDATE", params().addValue("id", id), "GROUP_NOT_FOUND", "Thesis group not found");
        return new GroupRow((UUID) row.get("id"), (UUID) row.get("round_id"), (String) row.get("leader_student_id"), (UUID) row.get("topic_id"), GroupStatus.valueOf((String) row.get("status")), row.get("approval_status") == null ? ApprovalStatus.PENDING : ApprovalStatus.valueOf((String) row.get("approval_status")));
    }

    private void authorizeLeaderOrAdmin(GroupRow group, Jwt actor) {
        if (isGroupAdmin(group, actor)) {
            return;
        }
        if (!group.leaderStudentId().equals(studentId(actor))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_OWNER_REQUIRED", "Only the group leader can change this group");
        }
        if (group.status() != GroupStatus.DRAFT && group.status() != GroupStatus.SUBMITTED) {
            throw conflict("GROUP_STATE_CONFLICT", "The group is no longer editable");
        }
    }

    /**
     * Membership may be changed by the group leader, an admin, or — feedback
     * items 5 and 11 — the lecturer who supervises the group's topic, who is
     * expected to complete a group's roster even after the faculty approved it.
     * Returns true on the supervisor path, where the approved-group freeze does
     * not apply; the open-round gate still does and is enforced by the caller.
     */
    private boolean authorizeMemberManagement(GroupRow group, Jwt actor) {
        if (isGroupAdmin(group, actor)) {
            return false;
        }
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (isLecturer(actor) && !lecturerId.isBlank() && group.topicId() != null
                && isSupervisorOf(group.topicId(), lecturerId)) {
            // Supervisors may complete a roster after faculty approval, but a
            // completed group is done — its roster must be final.
            if (group.status() == GroupStatus.COMPLETED) {
                throw conflict("GROUP_STATE_CONFLICT", "A completed group's roster is final");
            }
            return true;
        }
        if (!group.leaderStudentId().equals(studentId(actor))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_OWNER_REQUIRED", "Only the group leader can change this group");
        }
        if (group.status() != GroupStatus.DRAFT && group.status() != GroupStatus.SUBMITTED) {
            throw conflict("GROUP_STATE_CONFLICT", "The group is no longer editable");
        }
        return false;
    }

    private boolean isSupervisorOf(UUID topicId, String lecturerId) {
        // lecturer_id may hold either a Lecturer.id or, from the legacy
        // created_by fallback, a User.id — resolve either against an ACTIVE
        // lecturer profile so a deactivated supervisor loses review power.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor s "
                        + "JOIN campuscore_auth.\"Lecturer\" l ON (l.\"id\" = s.lecturer_id OR l.\"userId\" = s.lecturer_id) "
                        + "AND l.\"isActive\" = TRUE "
                        + "WHERE s.topic_id = :topicId AND s.lecturer_id = :lecturerId",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId),
                Integer.class);
        return count != null && count > 0;
    }

    /**
     * Review authority for approve/reject: a governance admin, or the topic's
     * own current supervisor.
     *
     * @return true when the caller reviews as the topic's supervisor — a dean
     *         who supervises the topic counts as a supervisor, so an approve
     *         from them stays a normal (non-override) approval
     */
    private boolean authorizeReviewer(GroupRow group, Jwt actor) {
        boolean supervised = group.topicId() != null && isCurrentSupervisor(group.topicId(), actor);
        if (isGroupAdmin(group, actor)) {
            return supervised;
        }
        if (!supervised) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_REVIEWER_REQUIRED", "Only an assigned supervisor or admin can review this group");
        }
        return true;
    }

    /**
     * Whether the actor is a CURRENT supervisor of the topic — the topic
     * creator loses this right when setSupervisors replaces them, otherwise a
     * stale creator retains approval power over groups they no longer
     * supervise. The active-lecturer join keeps a deactivated profile from
     * retaining the same approval power after the account is disabled.
     */
    private boolean isCurrentSupervisor(UUID topicId, Jwt actor) {
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        Integer supervisorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor s "
                        + "JOIN campuscore_auth.\"Lecturer\" l ON (l.\"id\" = s.lecturer_id OR l.\"userId\" = s.lecturer_id) "
                        + "AND l.\"isActive\" = TRUE "
                        + "WHERE s.topic_id = :topicId AND (s.lecturer_id = :lecturerId OR s.lecturer_id = :actorId)",
                params().addValue("topicId", topicId)
                        .addValue("actorId", actorId)
                        .addValue("lecturerId", lecturerId.isBlank() ? actorId : lecturerId),
                Integer.class);
        return supervisorCount != null && supervisorCount > 0;
    }

    /**
     * Post-close group governance is narrower than {@link #isGroupAdmin}: the
     * dean-supervisor branch there lets a dean review the groups they
     * personally supervise, but writing post-close roster state — the
     * override or the under-minimum waiver — requires the faculty that owns
     * the group. Supervising its topic from another faculty is still only
     * review.
     */
    private boolean canGovernGroupScope(GroupRow group, Jwt actor) {
        if (isGlobalAdmin(actor)) return true;
        if (!hasRole(actor, "TRUONG_KHOA")) return false;
        return groups.groupInFaculty(group.id(), requireDeanScope(actor).facultyId());
    }

    /**
     * Ordinary group authority is faculty-bound OR supervisor-bound: a dean
     * who supervises the group's topic keeps review/member-management rights
     * across faculties. Post-close writes use {@link #canGovernGroupScope}
     * instead, which drops that supervisor escape.
     */
    private boolean isGroupAdmin(GroupRow group, Jwt actor) {
        if (isGlobalAdmin(actor)) return true;
        if (!hasRole(actor, "TRUONG_KHOA")) return false;
        LecturerScope scope = requireDeanScope(actor);
        if (groups.groupInFaculty(group.id(), scope.facultyId()) || isDeanSupervisor(group.topicId(), actor)) {
            return true;
        }
        throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
    }

    private LecturerScope requireDeanScope(Jwt actor) {
        LecturerScope scope = groups.findLecturerScope(
                normalize(actor.getClaimAsString("lecturerId")), subject(actor));
        if (scope == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "FACULTY_PROFILE_REQUIRED",
                    "An active lecturer profile bound to the faculty head is required");
        }
        return scope;
    }

    /** Check the destination too: an own-faculty group cannot confer access to a foreign topic. */
    private void authorizeTopicOwnerOrDepartmentHead(String createdBy, String departmentId, Jwt actor) {
        if (isAdmin(actor) || ThesisDepartmentScope.owns(scope, actor, departmentId)) return;
        authorizeTopicOwner(createdBy, actor);
    }

    private boolean isDeanSupervisor(UUID topicId, Jwt actor) {
        return topicId != null && isLecturer(actor)
                && isSupervisorOf(topicId, normalize(actor.getClaimAsString("lecturerId")));
    }

    private static boolean isGlobalAdmin(Jwt actor) {
        return hasRole(actor, "ADMIN") || hasRole(actor, "SUPER_ADMIN");
    }

    private static boolean hasRole(Jwt actor, String role) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    private void authorizeTopicOwner(ThesisTopic topic, Jwt actor) {
        authorizeTopicOwner(topic.getCreatedBy(), actor);
    }

    private void authorizeTopicOwner(String createdBy, Jwt actor) {
        if (isAdmin(actor)) return;
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (!createdBy.equals(actorId) && (lecturerId.isBlank() || !createdBy.equals(lecturerId))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_OWNER_REQUIRED", "Only the topic owner can change this topic");
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
    private void requireMutableRound(UUID roundId) {
        Map<String, Object> round = one(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", roundId), "ROUND_NOT_FOUND", "Thesis registration round not found");
        if (Set.of(RoundStatus.CANCELLED.name(), RoundStatus.RESULTS_PUBLISHED.name(), RoundStatus.CLOSED.name())
                .contains(round.get("status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "The round has finished; its topics and groups are historical");
        }
    }

    private void requireRoundStatusAny(UUID id, RoundStatus... accepted) {
        String status = (String) one(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found")
                .get("status");
        for (RoundStatus candidate : accepted) {
            if (candidate.name().equals(status)) {
                return;
            }
        }
        throw conflict("ROUND_STATE_CONFLICT", "Round must be in " + accepted[0].name()
                + (accepted.length > 1 ? " or " + accepted[1].name() : ""));
    }

    private String requireRoundStatus(UUID id, RoundStatus required) {
        Map<String, Object> round = one(
                "SELECT status, thesis_type, registration_start, registration_end FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
        if (!required.name().equals(round.get("status"))) {
            throw conflict("ROUND_CLOSED", "Registration is not open for this round");
        }
        if (required == RoundStatus.REGISTRATION_OPEN) {
            // The stored dates are normative: an open round whose window has
            // passed must not keep accepting registration mutations.
            Instant now = Instant.now();
            Instant start = instantOf(round.get("registration_start"));
            Instant end = instantOf(round.get("registration_end"));
            if (start == null || end == null || now.isBefore(start) || !now.isBefore(end)) {
                throw conflict("REGISTRATION_WINDOW_CLOSED", "The registration window is closed for this round");
            }
        }
        return (String) round.get("thesis_type");
    }

    /** Membership is frozen once a supervisor approves the group; admins coordinate through re-review instead. */
    private void requireMutableMembership(GroupRow group, Jwt actor) {
        if (!isAdmin(actor) && group.approvalStatus() == ApprovalStatus.APPROVED) {
            throw conflict("GROUP_STATE_CONFLICT", "An approved group's membership is frozen");
        }
    }

    /**
     * Brief phase one: topic work stays in the proposal phase. Lecturer
     * submissions additionally obey the configured lecturer submission window;
     * admins can curate department-governed topics without being registered as
     * a lecturer participant.
     */
    private void requireProposalPhase(UUID id, Jwt actor) {
        Map<String, Object> round = one(
                "SELECT status, lecturer_submit_start, lecturer_submit_end FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
        if (!RoundStatus.PROPOSAL_OPEN.name().equals(round.get("status"))) {
            throw conflict("ROUND_NOT_ACCEPTING_PROPOSALS",
                    "Topics can only be submitted while the round is in its lecturer proposal phase");
        }
        if (isAdmin(actor)) {
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

    /**
     * Brief R4: a student sits in at most one group while their current round
     * has not finished. Rounds that reached RESULTS_PUBLISHED/CLOSED/CANCELLED
     * release their members for the next round type (e.g. a course topic this
     * term and a graduation thesis later).
     */
    private void requireNotInAnotherActiveGroup(UUID roundId, String studentId) {
        // Round-3 contract thesis-3: a leader's only way out of a group is
        // PATCH progress → CANCELLED, but the member rows stayed — so the
        // same-round membership check locked the leader out of every new group
        // for the rest of the round. A cancelled group holds no seat.
        Integer sameRound = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "WHERE m.round_id = :roundId AND m.student_id = :studentId AND g.status <> 'CANCELLED'",
                params().addValue("roundId", roundId).addValue("studentId", studentId), Integer.class);
        if (sameRound != null && sameRound > 0) {
            throw conflict("STUDENT_ALREADY_IN_GROUP", "Student already belongs to a group in this round");
        }
        Integer otherActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "JOIN thesis.thesis_registration_round r ON r.id = m.round_id "
                        + "WHERE m.student_id = :studentId AND m.round_id <> :roundId "
                        + "AND g.status <> 'CANCELLED' "
                        + "AND r.status IN ('DRAFT', 'PROPOSAL_OPEN', 'PROPOSALS_PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED')",
                params().addValue("studentId", studentId).addValue("roundId", roundId), Integer.class);
        if (otherActive != null && otherActive > 0) {
            throw conflict("STUDENT_ACTIVE_IN_OTHER_GROUP",
                    "The student already belongs to a group in another round that has not finished");
        }
    }

    private void requireActiveRoster(GroupRow group) {
        if (group.status() == GroupStatus.CANCELLED) {
            throw conflict("GROUP_STATE_CONFLICT", "A cancelled group has a read-only historical roster");
        }
    }

    /** Reuse a vacant slot; surviving members keep their identity and order. */
    private int nextMemberOrder(UUID groupId) {
        List<Integer> usedOrders = jdbc.queryForList(
                "SELECT member_order FROM thesis.thesis_group_member WHERE group_id = :groupId",
                params().addValue("groupId", groupId), Integer.class);
        for (int order = 1; order <= MAX_GROUP_MEMBERS; order++) {
            if (!usedOrders.contains(order)) return order;
        }
        throw conflict("GROUP_FULL", "The thesis group has no available member slot");
    }

    private static RoundType requireRoundType(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("thesisType is required");
        }
        try {
            return RoundType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("thesisType must be one of MON_HOC, NCKH, TLCN, KLTN");
        }
    }

    private static Instant instantOf(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof java.util.Date date) return date.toInstant();
        return null;
    }

    /**
     * Binds an {@link Instant} to JDBC. The PostgreSQL driver cannot infer a SQL type for
     * {@code java.time.Instant} ("Can't infer the SQL type to use for an instance of
     * java.time.Instant"), which previously made {@code POST /thesis/rounds} fail with HTTP 500.
     * The rest of the codebase binds timestamps as {@link java.sql.Timestamp} (see
     * RegistrationService), so we follow the same convention here.
     */
    private static java.sql.Timestamp tsOf(Instant value) {
        return value == null ? null : java.sql.Timestamp.from(value);
    }

    /**
     * Applies the accumulated-credit/CPA registration condition on the round
     * types the policy covers (thesis.eligibility.round-types, default KLTN).
     * Runs inside the transaction the caller already opened — one extra read,
     * no new lock — and fails closed with THESIS_ELIGIBILITY_NOT_MET on
     * missing academic data. Absent in manually-wired tests; never blocks in
     * ADVISORY mode or on uncovered round types. {@code discloseFigures}
     * decides whether the refusal may carry the evaluated student's numbers:
     * only the student themself may see them.
     */
    private void requireEligibility(String studentId, UUID roundId, String thesisType,
            boolean discloseFigures) {
        if (eligibility != null) {
            eligibility.requireEligible(studentId, roundId, thesisType, discloseFigures);
        }
    }

    private void requireActiveStudent(String studentId) {
        Integer present;
        try {
            present = jdbc.queryForObject(
                    "SELECT 1 FROM campuscore_auth.\"Student\" WHERE \"id\" = :studentId AND \"status\" = 'ACTIVE' FOR UPDATE",
                    params().addValue("studentId", studentId), Integer.class);
        } catch (EmptyResultDataAccessException exception) {
            present = null;
        }
        if (present == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "An active student profile is required");
        }
    }

    private void requireLecturerDepartment(Jwt actor, String requestedDepartment) {
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (lecturerId.isBlank()) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required");
        }
        String department;
        try {
            department = jdbc.queryForObject(
                    "SELECT \"departmentId\" FROM campuscore_auth.\"Lecturer\" "
                            + "WHERE \"id\" = :lecturerId AND \"isActive\" = TRUE",
                    params().addValue("lecturerId", lecturerId), String.class);
        } catch (EmptyResultDataAccessException exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required");
        }
        if (department == null || !department.equals(requestedDepartment)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_DEPARTMENT_FORBIDDEN",
                    "A lecturer may submit topics only for their active department");
        }
    }

    private void requireActiveDepartment(String departmentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Department\" WHERE \"id\" = :departmentId AND \"isActive\" = TRUE",
                params().addValue("departmentId", departmentId), Integer.class);
        if (count == null || count == 0) {
            throw invalid("departmentId must reference an active department");
        }
    }

    /**
     * Topic curation scope by caller tier. A global admin may target any
     * active department; a faculty head curates across every department in
     * their own faculty; an ordinary lecturer stays within their personal
     * department. Companion LECTURER roles on a dean/admin account must not
     * narrow the wider governance scope.
     */
    private void requireTopicDepartmentScope(Jwt actor, String departmentId) {
        if (isGlobalAdmin(actor)) {
            requireActiveDepartment(departmentId);
            return;
        }
        if (hasRole(actor, "TRUONG_KHOA")) {
            requireDeanTopicDepartment(actor, departmentId);
            return;
        }
        requireLecturerDepartment(actor, departmentId);
    }

    private void requireDeanTopicDepartment(Jwt actor, String departmentId) {
        LecturerScope scope = requireDeanScope(actor);
        Integer matches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Department\" "
                        + "WHERE \"id\" = :departmentId AND \"facultyId\" = :facultyId AND \"isActive\" = TRUE",
                params().addValue("departmentId", departmentId).addValue("facultyId", scope.facultyId()),
                Integer.class);
        if (matches == null || matches == 0) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_DEPARTMENT_FORBIDDEN",
                    "A faculty head may submit topics only for departments in their own faculty");
        }
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource parameters, String code, String message) {
        try {
            return jdbc.queryForMap(sql, parameters);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound(code, message);
        }
    }

    private int count(String sql, String paramName, UUID value) {
        Integer result = jdbc.queryForObject(sql,
                params().addValue(paramName, value), Integer.class);
        return result == null ? 0 : result;
    }

    private static MapSqlParameterSource params() { return new MapSqlParameterSource(); }
    private static String subject(Jwt actor) { return actor == null || actor.getSubject() == null ? "" : actor.getSubject(); }
    private static String studentId(Jwt actor) { return normalize(actor == null ? null : actor.getClaimAsString("studentId")); }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
    private static void requireText(String value, String name) { if (value == null || value.isBlank()) throw invalid(name + " is required"); }
    private static void requireText(String value, String name, int maxLength) {
        requireText(value, name);
        if (value.trim().length() > maxLength) throw invalid(name + " must contain at most " + maxLength + " characters");
    }
    private static void requireDates(Instant start, Instant end, String startField, String endField) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw invalid(endField + " must be after " + startField);
        }
    }
    private static boolean isAdmin(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN") || roles.contains("TRUONG_KHOA"));
    }
    private static boolean isLecturer(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && roles.contains("LECTURER");
    }
    private static boolean isProgressStatus(GroupStatus status) { return status == GroupStatus.DRAFT || status == GroupStatus.SUBMITTED || status == GroupStatus.COMPLETED || status == GroupStatus.CANCELLED; }
    private static DomainException invalid(String message) { return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message); }
    private static DomainException conflict(String code, String message) { return new DomainException(HttpStatus.CONFLICT, code, message); }
    private static DomainException notFound(String code, String message) { return new DomainException(HttpStatus.NOT_FOUND, code, message); }

    private record GroupRow(UUID id, UUID roundId, String leaderStudentId, UUID topicId, GroupStatus status, ApprovalStatus approvalStatus) { }
}
