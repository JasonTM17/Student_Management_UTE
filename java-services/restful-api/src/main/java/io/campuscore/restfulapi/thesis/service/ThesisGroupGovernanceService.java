package io.campuscore.restfulapi.thesis.service;

import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.MAX_GROUP_MEMBERS;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.MIN_GROUP_MEMBERS;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.afterCommit;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.conflict;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.hasRole;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.invalid;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isAdmin;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isGlobalAdmin;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isProgressStatus;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.normalize;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.notFound;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireGovernanceRole;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireReason;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.studentId;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.subject;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalWithdrawalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicAssignmentRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicReassignRequest;
import io.campuscore.restfulapi.thesis.model.ApprovalStatus;
import io.campuscore.restfulapi.thesis.model.GroupRow;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupMutationRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisScopeRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicMutationRepository;
import io.campuscore.restfulapi.thesis.scope.ThesisDeanScope;
import io.campuscore.restfulapi.thesis.scope.ThesisDepartmentScope;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Group governance and review responsibility of the thesis mutation boundary:
 * topic binding, progress transitions, supervisor review (approve/reject),
 * approval withdrawal, and the post-close re-pointing of a group orphaned by
 * topic governance. Runs inside the transaction the
 * {@link ThesisMutationService} facade opened.
 */
@Service
@Profile("persistence")
public class ThesisGroupGovernanceService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ThesisGroupGovernanceService.class);

    private final ThesisGroupAccessGuard guard;
    private final ThesisGroupMutationRepository groupRepo;
    private final ThesisTopicMutationRepository topicRepo;
    private final ThesisScopeRepository scope;
    private final ThesisRoundLifecycleService roundLifecycle;
    private final AdminAuditRecorder audit;
    private final ThesisNotificationService notifier;

    @Autowired(required = false)
    private ThesisEligibilityService eligibility;

    public ThesisGroupGovernanceService(
            ThesisGroupAccessGuard guard,
            ThesisGroupMutationRepository groupRepo,
            ThesisTopicMutationRepository topicRepo,
            ThesisScopeRepository scope,
            ThesisRoundLifecycleService roundLifecycle,
            AdminAuditRecorder audit,
            ThesisNotificationService notifier) {
        this.guard = guard;
        this.groupRepo = groupRepo;
        this.topicRepo = topicRepo;
        this.scope = scope;
        this.roundLifecycle = roundLifecycle;
        this.audit = audit;
        this.notifier = notifier;
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
    public GroupResponse assignTopic(UUID groupId, TopicAssignmentRequest request, Jwt actor) {
        GroupRow group = guard.lockGroup(groupId);
        guard.authorizeLeaderOrAdmin(group, actor);
        String thesisType = roundLifecycle.requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        // the topic binding and approval trace of a
        // CANCELLED group are historical — the roster trigger cannot see this
        // path, so the guard is enforced here too (admin early-return in
        // authorizeLeaderOrAdmin used to let this through).
        guard.requireActiveRoster(group);
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
        Map<String, Object> topic = topicRepo.lockTopicDetail(topicId);
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
        if (topicRepo.countActiveTopicGroups(topicId) >= maxGroups && !alreadyOccupiesSlot) {
            throw conflict("TOPIC_FULL", "This topic has reached its group limit");
        }
        boolean isNewTopic = !topicId.equals(group.topicId());
        groupRepo.bindTopic(groupId, topicId, isNewTopic);
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
        return guard.groupView(groupId, actor);
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
    public GroupResponse updateProgress(UUID groupId, ProgressRequest request, Jwt actor) {
        GroupRow group = guard.lockGroup(groupId);
        guard.authorizeLeaderOrAdmin(group, actor);
        roundLifecycle.requireMutableRound(group.roundId());
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
                && !guard.canGovernGroupScope(group, actor)) {
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
        groupRepo.updateStatus(groupId, status.name());
        if (status == GroupStatus.CANCELLED && group.status() != GroupStatus.CANCELLED) {
            // H2's compatibility migration has no PostgreSQL trigger. PostgreSQL also derives
            // this at the database boundary, so this remains an atomic, harmless no-op there.
            groupRepo.deactivateMembers(groupId);
        }
        if (status != group.status()) {
            // Cancellation frees every member's active_participation slot;
            // without this row the freeing actor is untraceable.
            audit.record(subject(actor), null, "THESIS_GROUP_STATUS_CHANGED", "THESIS_GROUP", groupId.toString(),
                    "Group status changed from " + group.status() + " to " + status);
        }
        return guard.groupView(groupId, actor);
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
    public GroupResponse approveGroup(UUID groupId, Jwt actor) {
        return approveGroup(groupId, null, actor);
    }

    public GroupResponse approveGroup(UUID groupId, GroupApprovalRequest request, Jwt actor) {
        GroupRow group = guard.lockGroup(groupId);
        boolean supervisorActor = authorizeReviewer(group, actor);
        // The round row is locked so an approval cannot slip between
        // publishResults' completeness check and its guarded transition —
        // a published round must never contain an ungraded approved group.
        String roundStatus = roundLifecycle.lockRoundStatus(group.roundId());
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
        Map<String, Object> boundTopic = topicRepo.findTopicScoreState(group.topicId());
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
        int memberCount = groupRepo.countMembers(groupId);
        String sizeExceptionReason = null;
        if (memberCount < MIN_GROUP_MEMBERS) {
            int present = memberCount;
            if (request == null || !Boolean.TRUE.equals(request.allowSizeException())) {
                throw conflict("GROUP_TOO_SMALL", "A thesis group needs " + MIN_GROUP_MEMBERS + " to " + MAX_GROUP_MEMBERS
                        + " members before it can be approved; this group has " + present + " ("
                        + (MIN_GROUP_MEMBERS - present) + " short of the minimum of " + MIN_GROUP_MEMBERS
                        + ", invite more classmates first)");
            }
            // The waiver is governance-only: a topic supervisor reviews normal
            // rosters but cannot approve under the faculty minimum — and a
            // dean supervising from another faculty is still only a reviewer.
            if (!guard.canGovernGroupScope(group, actor)) {
                throw new DomainException(HttpStatus.FORBIDDEN, "GOVERNANCE_ROLE_REQUIRED",
                        "An under-size approval requires an admin or faculty head role");
            }
            sizeExceptionReason = requireReason(request.reason());
        }
        if (memberCount > MAX_GROUP_MEMBERS) {
            throw conflict("GROUP_TOO_LARGE", "A thesis group can have " + MIN_GROUP_MEMBERS + " to "
                    + MAX_GROUP_MEMBERS + " members; this group has " + memberCount);
        }
        int leaderCount = groupRepo.countLeaders(groupId);
        int matchingLeader = groupRepo.countMatchingLeaders(groupId);
        if (leaderCount != 1 || matchingLeader != 1) {
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
        int changed = groupRepo.approveGroup(groupId, subject(actor), sizeExceptionReason);
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
        return guard.groupView(groupId, actor);
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
    public GroupResponse rejectGroup(UUID groupId, GroupRejectionRequest request, Jwt actor) {
        GroupRow group = guard.lockGroup(groupId);
        authorizeReviewer(group, actor);
        roundLifecycle.requireMutableRound(group.roundId());
        if (group.status() != GroupStatus.SUBMITTED || group.topicId() == null) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only a submitted group with a topic can be rejected");
        }
        String reason = normalize(request == null ? null : request.reason());
        if (reason.isBlank() || reason.length() > 500) throw invalid("reason is required and must contain at most 500 characters");
        int changed = groupRepo.rejectGroup(groupId, reason);
        if (changed != 1) throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only pending groups can be rejected");
        // the group row nulls approved_by on rejection and
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
        return guard.groupView(groupId, actor);
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
    public GroupResponse withdrawGroupApproval(UUID groupId, GroupApprovalWithdrawalRequest request, Jwt actor) {
        GroupRow group = guard.lockGroup(groupId);
        authorizeApprovalWithdrawal(group, actor);
        // Same FOR UPDATE read as approve: the approval may only move while
        // the round still reviews registrations, and the lock fences a
        // concurrent close/publish transition.
        String roundStatus = roundLifecycle.lockRoundStatus(group.roundId());
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
        Map<String, Object> boundTopic = topicRepo.findTopicWorkState(group.topicId());
        long reports = groupRepo.countGroupReports(groupId);
        long scores = topicRepo.countTopicScores(group.topicId());
        long councilTopics = topicRepo.countCouncilTopics(group.topicId());
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
        int changed = groupRepo.clearApproval(groupId);
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
        return guard.groupView(groupId, actor);
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
            if (guard.groupInFaculty(group, actor)) {
                return;
            }
            throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
        }
        if (ThesisDepartmentScope.isDepartmentHead(actor)) {
            String department = ThesisDepartmentScope.departmentId(scope, actor);
            if (!department.isEmpty() && guard.groupInDepartment(group, department)) {
                return;
            }
            throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
        }
        if (group.topicId() != null && guard.isCurrentSupervisor(group.topicId(), actor)) {
            return;
        }
        throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_REVIEWER_REQUIRED",
                "Only an assigned supervisor or a governance officer in scope can withdraw this approval");
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
        boolean supervised = group.topicId() != null && guard.isCurrentSupervisor(group.topicId(), actor);
        if (guard.isGroupAdmin(group, actor)) {
            return supervised;
        }
        if (!supervised) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_REVIEWER_REQUIRED", "Only an assigned supervisor or admin can review this group");
        }
        return true;
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
    public GroupResponse reassignOrphanedTopic(UUID groupId, TopicReassignRequest request, Jwt actor) {
        requireGovernanceRole(actor);
        String cleanReason = requireReason(request == null ? null : request.reason());
        if (request == null || request.topicId() == null) {
            throw invalid("topicId is required");
        }
        UUID newTopicId = request.topicId();
        // The group lock precedes the round lock to match the order every
        // sibling group mutation uses; the topic rows it binds come last.
        GroupRow group = guard.lockGroup(groupId);
        roundLifecycle.requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_CLOSED);
        guard.requireActiveRoster(group);
        UUID oldTopicId = group.topicId();
        if (oldTopicId == null) {
            throw conflict("GROUP_TOPIC_NOT_ORPHANED", "The group has no topic to re-point away from");
        }
        if (oldTopicId.equals(newTopicId)) {
            throw invalid("topicId must differ from the group's current topic");
        }
        Map<String, Object> oldTopic = topicRepo.lockTopicOutcome(oldTopicId);
        // The hatch exists only for a governance-orphaned group: a WITHDRAWN
        // outcome removed its topic from the round. A live or review-failed
        // binding is a different question (the group chose that topic).
        if (!ThesisReviewOutcome.WITHDRAWN.equals(oldTopic.get("result_status"))) {
            throw conflict("GROUP_TOPIC_NOT_ORPHANED",
                    "Only a group whose topic was withdrawn can be re-pointed");
        }
        Map<String, Object> newTopic = topicRepo.lockTopicForReassign(newTopicId);
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
        Integer scores = topicRepo.countScoresOnTopics(oldTopicId, newTopicId);
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
        if (topicRepo.countActiveTopicGroups(newTopicId) >= maxGroups) {
            throw conflict("TOPIC_FULL", "This topic has reached its group limit");
        }
        groupRepo.reassignTopic(groupId, newTopicId);
        audit.record(subject(actor), null, "THESIS_GROUP_TOPIC_REASSIGNED", "THESIS_GROUP", groupId.toString(),
                "Orphaned group re-pointed to a live topic; reason: " + cleanReason,
                Map.of("topicId", oldTopicId.toString()),
                Map.of("topicId", newTopicId.toString()));
        afterCommit(() -> {
            if (notifier != null) {
                notifier.notifyTopicAssigned(groupId, newTopicId);
            }
        });
        return guard.groupView(groupId, actor);
    }

    private void requireEligibility(String studentId, UUID roundId, String thesisType,
            boolean discloseFigures) {
        if (eligibility != null) {
            eligibility.requireEligible(studentId, roundId, thesisType, discloseFigures);
        }
    }
}
