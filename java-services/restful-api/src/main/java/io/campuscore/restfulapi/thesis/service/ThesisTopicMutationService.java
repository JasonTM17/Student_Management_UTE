package io.campuscore.restfulapi.thesis.service;

import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.afterCommit;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.conflict;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.hasRole;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.invalid;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isAdmin;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isGlobalAdmin;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isLecturer;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.normalize;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireGovernanceRole;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireReason;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireText;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.subject;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.LateTopicRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos.TopicResponse;
import io.campuscore.restfulapi.thesis.entity.ThesisTopic;
import io.campuscore.restfulapi.thesis.model.LecturerScope;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicMutationRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.scope.ThesisDeanScope;
import io.campuscore.restfulapi.thesis.scope.ThesisDepartmentScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Topic governance responsibility of the thesis mutation boundary: drafting,
 * editing, approval and archiving of lecturer topics, and the governance
 * outcome verbs (withdraw/reinstate/late creation) that release or add a
 * topic's publication obligation. Runs inside the transaction the
 * {@link ThesisMutationService} facade opened.
 */
@Service
@Profile("persistence")
public class ThesisTopicMutationService {

    private final ThesisTopicMutationRepository topicWrites;
    private final ThesisTopicRepository topics;
    private final ThesisScopeRepository scope;
    private final ThesisRoundReadPort roundReadPort;
    private final ThesisRoundLifecycleService roundLifecycle;
    private final ThesisGroupAccessGuard groupAccess;
    private final AdminAuditRecorder audit;
    private final ThesisNotificationService notifier;

    public ThesisTopicMutationService(
            ThesisTopicMutationRepository topicWrites,
            ThesisTopicRepository topics,
            ThesisScopeRepository scope,
            ThesisRoundReadPort roundReadPort,
            ThesisRoundLifecycleService roundLifecycle,
            ThesisGroupAccessGuard groupAccess,
            AdminAuditRecorder audit,
            ThesisNotificationService notifier) {
        this.topicWrites = topicWrites;
        this.topics = topics;
        this.scope = scope;
        this.roundReadPort = roundReadPort;
        this.roundLifecycle = roundLifecycle;
        this.groupAccess = groupAccess;
        this.audit = audit;
        this.notifier = notifier;
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
        roundLifecycle.requireProposalPhase(request.roundId(), actor);
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
                Integer existingSupervisor = topicWrites.countSupervisorOrFirstSeat(topic.getId(), supervisorId);
                if (existingSupervisor == null || existingSupervisor == 0) {
                    topicWrites.insertFirstSupervisor(topic.getId(), supervisorId);
                }
            }
        }
        return topicResponse(topic);
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
    public TopicResponse updateTopic(UUID id, TopicUpdateRequest request, Jwt actor) {
        // created_by and round_id never change, so the gate read can authorize
        // and locate the round before any lock; the DRAFT check must wait for
        // the locked entity load or a concurrent publish slips past it.
        Map<String, Object> gate = topicWrites.findGovernanceGate(id);
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
        roundLifecycle.requireMutableRound(UUID.fromString(String.valueOf(gate.get("round_id"))));
        lockTopicForGovernance(id);
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> ThesisMutationSupport.notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
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
        return topicResponse(topics.save(topic));
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
    public TopicResponse publishTopic(UUID id, Jwt actor) {
        Map<String, Object> gate = topicWrites.findPublishGate(id);
        if (!isAdmin(actor) && !ThesisDepartmentScope.owns(scope, actor, String.valueOf(gate.get("department_id")))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_APPROVER_REQUIRED",
                    "Only department governance can approve a topic");
        }
        String roundStatus = roundLifecycle.lockRoundStatus((UUID) gate.get("round_id"));
        if (!io.campuscore.restfulapi.thesis.model.RoundStatus.PROPOSAL_OPEN.name().equals(roundStatus)) {
            throw conflict("ROUND_NOT_ACCEPTING_PROPOSALS", "Topic approval requires the proposal phase");
        }
        lockTopicForGovernance(id);
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> ThesisMutationSupport.notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
        // The scope decision must use the department read under the topic
        // lock: the earlier gate read can go stale if a concurrent update
        // moves the topic to another department between the check and here.
        if (!isAdmin(actor) && !ThesisDepartmentScope.owns(scope, actor, topic.getDepartmentId())) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_APPROVER_REQUIRED",
                    "Only department governance can approve a topic");
        }
        Integer supervisorCount = topicWrites.countSupervisors(id);
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
        TopicResponse response = topicResponse(topics.saveAndFlush(topic));
        audit.record(subject(actor), null, "THESIS_TOPIC_APPROVED", "THESIS_TOPIC", id.toString(),
                "Department governance approved the topic for student registration");
        return response;
    }

    /** Archives only unused topics; group, council and grading history is never deleted. */
    public TopicResponse archiveTopic(UUID id, Jwt actor) {
        // Gate read for the early authz pass; the authoritative checks run on
        // the entity loaded under the topic lock below.
        Map<String, Object> gate = topicWrites.findArchiveGate(id);
        if (!isAdmin(actor) && !ThesisDepartmentScope.owns(scope, actor, String.valueOf(gate.get("department_id")))) {
            if (!isLecturer(actor)) {
                throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_OWNER_REQUIRED",
                        "Only the topic owner or department governance can archive this topic");
            }
            authorizeTopicOwner(String.valueOf(gate.get("created_by")), actor);
        }
        // Lock round before topic (same order as publishTopic) so an archive
        // cannot slip past a concurrent round termination.
        roundLifecycle.requireMutableRound(UUID.fromString(String.valueOf(gate.get("round_id"))));
        lockTopicForGovernance(id);
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> ThesisMutationSupport.notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
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
                || topicWrites.countGroupsByTopic(id) > 0
                || topicWrites.countCouncilTopics(id) > 0
                || topicWrites.countTopicScores(id) > 0) {
            throw conflict("TOPIC_IN_USE", "A topic with registration or grading history cannot be archived");
        }
        try {
            topic.archive();
        } catch (IllegalStateException exception) {
            throw conflict("TOPIC_STATE_CONFLICT", exception.getMessage());
        }
        TopicResponse response = topicResponse(topics.saveAndFlush(topic));
        audit.record(subject(actor), null, "THESIS_TOPIC_ARCHIVED", "THESIS_TOPIC", id.toString(),
                "Unused topic archived without removing its history");
        return response;
    }

    private void lockTopicForGovernance(UUID id) {
        topicWrites.lockTopicForUpdate(id);
    }

    /**
     * Renders the topic contract with its supervisor roster filled in through
     * the same batched repository read the list endpoint uses, inside the
     * caller's transaction so rows written moments earlier are visible.
     */
    private TopicResponse topicResponse(ThesisTopic topic) {
        return TopicResponse.from(topic,
                scope.findSupervisorsByTopicIds(List.of(topic.getId())).getOrDefault(topic.getId(), List.of()));
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
        Map<String, Object> topic = topicWrites.findOutcomeRow(id);
        if (topic.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "A finalized topic cannot be withdrawn");
        }
        if ("WITHDRAWN".equals(topic.get("result_status"))) {
            throw conflict("TOPIC_ALREADY_WITHDRAWN", "The topic is already withdrawn from the round");
        }
        topicWrites.markWithdrawn(id, cleanReason, subject(actor));
        audit.record(subject(actor), null, "THESIS_TOPIC_WITHDRAWN", "THESIS_TOPIC", id.toString(),
                "Topic withdrawn from the round; reason: " + cleanReason,
                Map.of("result_status", topic.get("result_status") == null ? "" : topic.get("result_status").toString()),
                Map.of("result_status", "WITHDRAWN", "result_reason", cleanReason));
        return topicResponse(topics.findById(id).orElseThrow(
                () -> ThesisMutationSupport.notFound("TOPIC_NOT_FOUND", "Thesis topic not found")));
    }

    /**
     * Reverses a withdrawal while the topic is still ungraded: the outcome
     * reverts to the counter-review's derived state (REVIEW_FAILED when a
     * below-threshold score still stands and no waiver applies, otherwise no
     * outcome) and the topic joins the completeness requirement again.
     */
    public TopicResponse reinstateTopic(UUID id, String reason, Jwt actor) {
        requireGovernanceRole(actor);
        String cleanReason = requireReason(reason);
        requireMutableTopicRound(id);
        lockTopicForGovernance(id);
        ThesisDeanScope.requireTopicGovernanceScope(scope, actor, id);
        Map<String, Object> topic = topicWrites.findReinstateRow(id);
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
        topicWrites.reinstateOutcome(id, failed ? "REVIEW_FAILED" : null, subject(actor));
        audit.record(subject(actor), null, "THESIS_TOPIC_REINSTATED", "THESIS_TOPIC", id.toString(),
                "Topic reinstated into the round; reason: " + cleanReason,
                Map.of("result_status", "WITHDRAWN"),
                Map.of("result_status", failed ? "REVIEW_FAILED" : ""));
        return topicResponse(topics.findById(id).orElseThrow(
                () -> ThesisMutationSupport.notFound("TOPIC_NOT_FOUND", "Thesis topic not found")));
    }

    /**
     * Governance-only escape hatch for a missed lecturer window: a topic whose
     * supervisor could not submit in time is created directly in PUBLISHED
     * state while students can still register, never by extending a window the
     * PROPOSAL_OPEN gate no longer consults. The normal topic validation —
     * text fields, department, group limit, an active supervisor, dean faculty
     * scope — still applies, and the reason is audited.
     */
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
        roundLifecycle.requireLateTopicWindow(roundId);
        String departmentId = request.departmentId().trim();
        requireTopicDepartmentScope(actor, departmentId);
        // The named supervisor must be a real active lecturer — the supervisor
        // row key joins the Lecturer directory, so storing an unresolvable id
        // would break supervision the way a legacy User.id did.
        Integer active = topicWrites.countActiveLecturers(supervisorId);
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
        topicWrites.insertFirstSupervisor(topic.getId(), supervisorId);
        audit.record(subject(actor), null, "THESIS_TOPIC_LATE_CREATED", "THESIS_TOPIC", topic.getId().toString(),
                "Late topic created for supervisor " + supervisorId + "; reason: " + cleanReason,
                Map.of("status", ""), Map.of("status", TopicStatus.PUBLISHED.name(), "supervisorId", supervisorId));
        TopicResponse response = topics.findById(lateTopicId)
                .map(this::topicResponse).orElseGet(() -> topicResponse(topic));
        // Recheck after the write/read path so an insert blocked across the
        // deadline cannot commit a topic students can no longer choose. A
        // refusal here rolls back the topic, supervisor link, and audit row.
        roundLifecycle.requireLateTopicWindow(roundId);
        afterCommit(() -> {
            if (notifier != null) {
                notifier.notifySupervisorsAssigned(lateTopicId, List.of(supervisorId));
            }
        });
        return response;
    }

    private void requireMutableTopicRound(UUID topicId) {
        UUID roundId = (UUID) topicWrites.findTopicRoundId(topicId).get("round_id");
        roundLifecycle.requireMutableRound(roundId);
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

    /** Check the destination too: an own-faculty group cannot confer access to a foreign topic. */
    private void authorizeTopicOwnerOrDepartmentHead(String createdBy, String departmentId, Jwt actor) {
        if (isAdmin(actor) || ThesisDepartmentScope.owns(scope, actor, departmentId)) return;
        authorizeTopicOwner(createdBy, actor);
    }

    private void requireLecturerDepartment(Jwt actor, String requestedDepartment) {
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (lecturerId.isBlank()) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required");
        }
        String department = topicWrites.findActiveLecturerDepartment(lecturerId);
        if (department == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required");
        }
        if (!department.equals(requestedDepartment)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_DEPARTMENT_FORBIDDEN",
                    "A lecturer may submit topics only for their active department");
        }
    }

    private void requireActiveDepartment(String departmentId) {
        Integer count = topicWrites.countActiveDepartment(departmentId);
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
        LecturerScope deanScope = groupAccess.requireDeanScope(actor);
        Integer matches = topicWrites.countDepartmentInFaculty(departmentId, deanScope.facultyId());
        if (matches == null || matches == 0) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_DEPARTMENT_FORBIDDEN",
                    "A faculty head may submit topics only for departments in their own faculty");
        }
    }
}
