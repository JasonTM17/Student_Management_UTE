package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.audit.AdminAuditRecorder;
import io.campuscore.restfulapi.thesis.domain.ApprovalStatus;
import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.RoundType;
import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.MemberRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RoundCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.StudentSearchResponse;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicAssignmentRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import io.campuscore.restfulapi.web.DomainException;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for the thesis core: rounds, topics and student groups. */
@Service
@Profile("persistence")
public class ThesisMutationService {

    private static final int MIN_GROUP_MEMBERS = 1;
    private static final int MAX_GROUP_MEMBERS = 3;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ThesisMutationService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ThesisRegistrationRoundRepository rounds;
    private final ThesisRoundReadPort roundReadPort;
    private final ThesisTopicRepository topics;
    private final ThesisGroupReadRepository groups;
    private final ThesisRoundReadService roundReads;
    private final AdminAuditRecorder audit;
    private final ThesisNotificationService notifier;

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
        this(jdbc, rounds, roundReadPort, topics, groups, roundReads, audit, null);
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
            ThesisNotificationService notifier) {
        this.jdbc = jdbc;
        this.rounds = rounds;
        this.roundReadPort = roundReadPort;
        this.topics = topics;
        this.groups = groups;
        this.roundReads = roundReads;
        this.audit = audit;
        this.notifier = notifier;
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
        requireText(request == null ? null : request.name(), "name");
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
        requireText(request == null ? null : request.name(), "name");
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
     * Cancels a round that has not published results. Cancelled groups keep
     * their historical rosters; the one-active-group index releases the
     * students for a future round.
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
                "UPDATE thesis.thesis_registration_round SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')",
                params().addValue("id", id));
        if (updated != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "A round that already published results cannot be cancelled");
        }
        audit.record(subject(actor), null, "THESIS_ROUND_CANCELLED", "THESIS_ROUND", id.toString(),
                "Round cancelled");
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
        return publishResults(id, null);
    }

    @Transactional
    public RoundResponse publishResults(UUID id, Jwt actor) {
        roundReadPort.requireExisting(id);
        // Lock the round row so a concurrent approveGroup cannot commit between
        // the completeness check below and the guarded status transition.
        jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), UUID.class);
        Integer approved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND approval_status = 'APPROVED'",
                params().addValue("id", id), Integer.class);
        Integer graded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group g JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "WHERE g.round_id = :id AND g.approval_status = 'APPROVED' AND t.final_score IS NOT NULL",
                params().addValue("id", id), Integer.class);
        if (graded == null || graded == 0) {
            throw conflict("RESULTS_NOT_READY", "No topic has been graded yet; the chair must finalize scores first");
        }
        if (approved == null || graded < approved) {
            throw conflict("SCORES_INCOMPLETE",
                    "Every approved group must have a finalized score before results are published");
        }
        RoundResponse response = transitionRound(id, RoundStatus.REGISTRATION_CLOSED, RoundStatus.RESULTS_PUBLISHED, actor);
        if (notifier != null) {
            try {
                notifier.notifyResultsPublished(id);
            } catch (RuntimeException exception) {
                log.warn("Thesis results-published notification fan-out failed for round {}", id, exception);
            }
        }
        return response;
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
        requireText(request == null ? null : request.title(), "title");
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
        if (isLecturer(actor)) {
            requireLecturerDepartment(actor, departmentId);
        } else {
            requireActiveDepartment(departmentId);
        }
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
     * @param actor caller JWT; must be the owning lecturer or an admin
     * @return the updated topic
     * @throws DomainException with code TOPIC_STATE_CONFLICT once the topic has been published, or
     *         VALIDATION_ERROR for a group limit outside 1-20
     */
    @Transactional
    public TopicResponse updateTopic(UUID id, TopicUpdateRequest request, Jwt actor) {
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
        authorizeTopicOwner(topic, actor);
        if (topic.getStatus() != TopicStatus.DRAFT) {
            throw conflict("TOPIC_STATE_CONFLICT", "Only draft topics can be edited");
        }
        requireText(request == null ? null : request.departmentId(), "departmentId");
        requireText(request == null ? null : request.title(), "title");
        requireText(request == null ? null : request.description(), "description");
        int maxGroups = request.maxGroups() == null ? topic.getMaxGroups() : request.maxGroups();
        if (maxGroups < 1 || maxGroups > 20) {
            throw invalid("maxGroups must be between 1 and 20");
        }
        String departmentId = request.departmentId().trim();
        if (isLecturer(actor)) {
            requireLecturerDepartment(actor, departmentId);
        } else {
            requireActiveDepartment(departmentId);
        }
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
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
        authorizeTopicOwner(topic, actor);
        requireProposalPhase(topic.getRoundId(), actor);
        Integer supervisorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId",
                params().addValue("topicId", id), Integer.class);
        if (supervisorCount == null || supervisorCount < 1 || supervisorCount > 2) {
            throw conflict("TOPIC_SUPERVISOR_REQUIRED",
                    "A topic must have one or two active supervisors before it can be published");
        }
        try {
            topic.publish();
        } catch (IllegalStateException exception) {
            throw conflict("TOPIC_STATE_CONFLICT", exception.getMessage());
        }
        return TopicResponse.from(topics.save(topic));
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
        requireRoundStatus(roundId, RoundStatus.REGISTRATION_OPEN);
        String studentId = studentId(actor);
        requireActiveStudent(studentId);
        requireNotInAnotherActiveGroup(roundId, studentId);
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) VALUES (:id, :roundId, :studentId, 'DRAFT', 'PENDING')", params().addValue("id", groupId).addValue("roundId", roundId).addValue("studentId", studentId));
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, 1, TRUE)", params().addValue("id", UUID.randomUUID()).addValue("groupId", groupId).addValue("roundId", roundId).addValue("studentId", studentId));
        return groups.findById(groupId);
    }

    /**
     * Adds one member to a group, either a campus student or an external member.
     *
     * @param groupId group being staffed
     * @param request student id to invite; a blank id takes the external-member path instead
     * @param actor caller JWT; the group leader, a supervisor, or an admin may manage the roster
     * @return the group with the new member listed
     * @throws DomainException with code GROUP_FULL past three members, GROUP_STATE_CONFLICT once the
     *         supervisor approved the roster, ROUND_CLOSED / REGISTRATION_WINDOW_CLOSED outside the
     *         registration window, and STUDENT_PROFILE_REQUIRED, STUDENT_ALREADY_IN_GROUP, or
     *         STUDENT_ACTIVE_IN_OTHER_GROUP for an unsuitable invitee
     */
    @Transactional
    public GroupResponse addMember(UUID groupId, MemberRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        boolean supervisorPath = authorizeMemberManagement(group, actor);
        requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
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
            addExternalMember(group, request);
            return groups.findById(groupId);
        }
        requireActiveStudent(studentId);
        requireNotInAnotherActiveGroup(group.roundId(), studentId);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, :memberOrder, FALSE)", params().addValue("id", UUID.randomUUID()).addValue("groupId", groupId).addValue("roundId", group.roundId()).addValue("studentId", studentId).addValue("memberOrder", nextMemberOrder(groupId)));
        jdbc.update("UPDATE thesis.thesis_group SET updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId", params().addValue("groupId", groupId));
        return groups.findById(groupId);
    }

    /**
     * Members from a different department or school have no student profile to
     * reference; their declared identity is stored on the membership row so the
     * reviewer can verify it, and a synthetic student_id keeps the round's
     * one-group-per-member uniqueness intact.
     */
    private void addExternalMember(GroupRow group, MemberRequest request) {
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
        boolean supervisorPath = authorizeMemberManagement(group, actor);
        requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        requireActiveRoster(group);
        if (!supervisorPath) {
            requireMutableMembership(group, actor);
        }
        String normalized = normalize(studentId);
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
        return groups.findById(groupId);
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
        requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        // Round-5 post-deploy sweep: the topic binding and approval trace of a
        // CANCELLED group are historical — the roster trigger cannot see this
        // path, so the guard is enforced here too (admin early-return in
        // authorizeLeaderOrAdmin used to let this through).
        requireActiveRoster(group);
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
        Map<String, Object> topic = one("SELECT id, round_id, status, max_groups FROM thesis.thesis_topic WHERE id = :id FOR UPDATE", params().addValue("id", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (!group.roundId().equals(topic.get("round_id"))) {
            throw conflict("TOPIC_ROUND_MISMATCH", "Topic belongs to another registration round");
        }
        if (!TopicStatus.PUBLISHED.name().equals(topic.get("status")) && !TopicStatus.APPROVED.name().equals(topic.get("status"))) {
            throw conflict("TOPIC_NOT_PUBLISHED", "Only published topics can be selected");
        }
        int maxGroups = ((Number) topic.get("max_groups")).intValue();
        boolean alreadyOccupiesSlot = topicId.equals(group.topicId()) && group.approvalStatus() != ApprovalStatus.REJECTED;
        if (count("SELECT COUNT(*) FROM thesis.thesis_group WHERE topic_id = :topicId AND status <> 'CANCELLED' AND approval_status <> 'REJECTED'", "topicId", topicId) >= maxGroups && !alreadyOccupiesSlot) {
            throw conflict("TOPIC_FULL", "This topic has reached its group limit");
        }
        boolean isNewTopic = !topicId.equals(group.topicId());
        jdbc.update("UPDATE thesis.thesis_group SET topic_id = :topicId, status = CASE WHEN status = 'DRAFT' THEN 'SUBMITTED' ELSE status END, approval_status = CASE WHEN approval_status = 'REJECTED' OR :isNewTopic THEN 'PENDING' ELSE approval_status END, approved_by = CASE WHEN :isNewTopic THEN NULL ELSE approved_by END, approved_at = CASE WHEN :isNewTopic THEN NULL ELSE approved_at END, rejection_reason = CASE WHEN approval_status = 'REJECTED' OR :isNewTopic THEN NULL ELSE rejection_reason END, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId",
                params().addValue("topicId", topicId).addValue("groupId", groupId).addValue("isNewTopic", isNewTopic));
        if (notifier != null && isNewTopic) {
            try {
                notifier.notifyTopicAssigned(groupId, topicId);
            } catch (RuntimeException exception) {
                log.warn("Thesis topic-assigned notification fan-out failed for group {}", groupId, exception);
            }
        }
        return groups.findById(groupId);
    }

    /**
     * Records group progress without touching the supervisor approval state.
     *
     * @param groupId group whose progress changes
     * @param request progress status, restricted to {@code DRAFT}, {@code SUBMITTED},
     *         {@code COMPLETED}, or {@code CANCELLED}
     * @param actor caller JWT; the leader, a supervisor, or an admin
     * @return the group with its new progress status
     * @throws DomainException with code VALIDATION_ERROR for a missing or unsupported status, or
     *         GROUP_STATUS_INVALID when an approved group does something other than complete, or a
     *         completed group reopens
     */
    @Transactional
    public GroupResponse updateProgress(UUID groupId, ProgressRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeLeaderOrAdmin(group, actor);
        GroupStatus status = request == null ? null : request.status();
        if (status == null) {
            throw invalid("status is required");
        }
        // APPROVED/REJECTED are approval semantics owned by the reviewer flow;
        // nobody sets them through progress updates.
        if (!isProgressStatus(status)) {
            throw invalid("Progress accepts only DRAFT, SUBMITTED, COMPLETED or CANCELLED");
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
        return groups.findById(groupId);
    }

    /**
     * Approves a submitted group, freezing its membership for the rest of the round.
     *
     * @param groupId group to approve
     * @param actor caller JWT; a supervisor or an admin
     * @return the approved group
     * @throws DomainException with code GROUP_APPROVAL_STATE_CONFLICT unless the group is
     *         submitted with a topic and still pending, GROUP_TOO_SMALL / GROUP_TOO_LARGE for a
     *         roster outside one to three members, or GROUP_LEADER_INVALID without exactly one
     *         matching leader
     */
    @Transactional
    public GroupResponse approveGroup(UUID groupId, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeReviewer(group, actor);
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
        Integer memberCount = count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId", "groupId", groupId);
        if (memberCount == null || memberCount < MIN_GROUP_MEMBERS) {
            int present = memberCount == null ? 0 : memberCount;
            throw conflict("GROUP_TOO_SMALL", "A thesis group needs " + MIN_GROUP_MEMBERS + " to " + MAX_GROUP_MEMBERS
                    + " members before it can be approved; this group has " + present + " ("
                    + (MIN_GROUP_MEMBERS - present) + " short of the minimum of " + MIN_GROUP_MEMBERS
                    + ", invite more classmates first)");
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
        int changed = jdbc.update("UPDATE thesis.thesis_group SET approval_status='APPROVED', approved_by=:actor, approved_at=CURRENT_TIMESTAMP, rejection_reason=NULL, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                params().addValue("id", groupId).addValue("actor", subject(actor)));
        if (changed != 1) throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only pending groups can be approved");
        // Symmetric with THESIS_GROUP_REJECTED below: an approval decides who
        // proceeds to the defense phase, so the approving actor must be
        // traceable even though approved_by already stores them on the row.
        audit.record(subject(actor), null, "THESIS_GROUP_APPROVED", "THESIS_GROUP", groupId.toString(),
                "Thesis group approved");
        if (notifier != null) {
            try {
                notifier.notifyGroupDecision(groupId, true, null);
            } catch (RuntimeException exception) {
                log.warn("Thesis group-approval notification fan-out failed for group {}", groupId, exception);
            }
        }
        return groups.findById(groupId);
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
            try {
                notifier.notifyGroupDecision(groupId, false, reason);
            } catch (RuntimeException exception) {
                log.warn("Thesis group-rejection notification fan-out failed for group {}", groupId, exception);
            }
        }
        return groups.findById(groupId);
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
        Integer liveRounds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_registration_round"
                        + " WHERE status IN ('DRAFT', 'PROPOSAL_OPEN', 'PROPOSALS_PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED')",
                params(),
                Integer.class);
        if (liveRounds == null || liveRounds == 0) {
            return List.of();
        }
        String pattern = "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query(
                "SELECT s.\"id\", s.\"studentId\" AS student_number, u.\"firstName\", u.\"lastName\","
                        + " cur.\"code\" AS curriculum_code, cur.\"name\" AS curriculum_name"
                        + " FROM campuscore_auth.\"Student\" s"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\""
                        + " LEFT JOIN academic.\"Curriculum\" cur ON cur.\"id\" = s.\"curriculumId\""
                        + " WHERE s.\"status\" = 'ACTIVE'"
                        + " AND (LOWER(s.\"studentId\") LIKE :pattern ESCAPE '\\'"
                        + "   OR LOWER(u.\"firstName\" || ' ' || u.\"lastName\") LIKE :pattern ESCAPE '\\'"
                        + "   OR LOWER(u.\"lastName\" || ' ' || u.\"firstName\") LIKE :pattern ESCAPE '\\'"
                        + "   OR LOWER(u.\"lastName\") LIKE :pattern ESCAPE '\\'"
                        + "   OR LOWER(u.\"firstName\") LIKE :pattern ESCAPE '\\')"
                        + " ORDER BY s.\"studentId\" LIMIT 8",
                params().addValue("pattern", pattern),
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
        if (isAdmin(actor)) {
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
        if (isAdmin(actor)) {
            return false;
        }
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (isLecturer(actor) && !lecturerId.isBlank() && group.topicId() != null
                && isSupervisorOf(group.topicId(), lecturerId)) {
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
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND lecturer_id = :lecturerId",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId),
                Integer.class);
        return count != null && count > 0;
    }

    private void authorizeReviewer(GroupRow group, Jwt actor) {
        if (isAdmin(actor)) return;
        if (group.topicId() == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_REVIEWER_REQUIRED", "Only an assigned supervisor or admin can review this group");
        }
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));

        // The reviewer must be a CURRENT supervisor — the topic creator loses
        // this right when setSupervisors replaces them, otherwise a stale
        // creator retains approval power over groups they no longer supervise.
        Integer supervisorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND (lecturer_id = :lecturerId OR lecturer_id = :actorId)",
                params().addValue("topicId", group.topicId())
                        .addValue("actorId", actorId)
                        .addValue("lecturerId", lecturerId.isBlank() ? actorId : lecturerId),
                Integer.class);
        if (supervisorCount != null && supervisorCount > 0) {
            return;
        }

        throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_REVIEWER_REQUIRED", "Only an assigned supervisor or admin can review this group");
    }

    private void authorizeTopicOwner(ThesisTopic topic, Jwt actor) {
        if (isAdmin(actor)) return;
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (!topic.getCreatedBy().equals(actorId) && (lecturerId.isBlank() || !topic.getCreatedBy().equals(lecturerId))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_OWNER_REQUIRED", "Only the topic owner can change this topic");
        }
    }

    private void requireRoundStatus(UUID id, RoundStatus required) {
        Map<String, Object> round = one(
                "SELECT status, registration_start, registration_end FROM thesis.thesis_registration_round WHERE id = :id",
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
                "SELECT status, lecturer_submit_start, lecturer_submit_end FROM thesis.thesis_registration_round WHERE id = :id",
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
