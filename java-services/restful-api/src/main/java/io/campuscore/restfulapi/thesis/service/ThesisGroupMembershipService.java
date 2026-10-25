package io.campuscore.restfulapi.thesis.service;

import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.MAX_GROUP_MEMBERS;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.MIN_GROUP_MEMBERS;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.afterCommit;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.conflict;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.invalid;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.normalize;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.notFound;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireGovernanceRole;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireReason;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.requireText;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.studentId;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.subject;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.MemberRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RosterOverrideRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.StudentSearchResponse;
import io.campuscore.restfulapi.thesis.model.ApprovalStatus;
import io.campuscore.restfulapi.thesis.model.GroupRow;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupMutationRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicMutationRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Group roster responsibility of the thesis mutation boundary: opening a
 * group, staffing it with campus or external members, removals, the
 * post-close governance roster override, and the leader-facing student
 * directory search. Runs inside the transaction the
 * {@link ThesisMutationService} facade opened.
 */
@Service
@Profile("persistence")
public class ThesisGroupMembershipService {

    private final ThesisGroupAccessGuard guard;
    private final ThesisGroupMutationRepository groupRepo;
    private final ThesisTopicMutationRepository topicRepo;
    private final ThesisRoundLifecycleService roundLifecycle;
    private final AdminAuditRecorder audit;
    private final ThesisNotificationService notifier;

    /**
     * Accumulated-credit/CPA registration condition (thesis.eligibility.*).
     * Optional so hand-built service instances in narrow tests keep working;
     * the full Spring context always wires it.
     */
    @Autowired(required = false)
    private ThesisEligibilityService eligibility;

    public ThesisGroupMembershipService(
            ThesisGroupAccessGuard guard,
            ThesisGroupMutationRepository groupRepo,
            ThesisTopicMutationRepository topicRepo,
            ThesisRoundLifecycleService roundLifecycle,
            AdminAuditRecorder audit,
            ThesisNotificationService notifier) {
        this.guard = guard;
        this.groupRepo = groupRepo;
        this.topicRepo = topicRepo;
        this.roundLifecycle = roundLifecycle;
        this.audit = audit;
        this.notifier = notifier;
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
    public GroupResponse createGroup(GroupCreateRequest request, Jwt actor) {
        UUID roundId = request == null ? null : request.roundId();
        if (roundId == null) {
            throw invalid("roundId is required");
        }
        String thesisType = roundLifecycle.requireRoundStatus(roundId, RoundStatus.REGISTRATION_OPEN);
        String studentId = studentId(actor);
        guard.requireActiveStudent(studentId);
        // The caller is the evaluated student: the error may carry figures.
        requireEligibility(studentId, roundId, thesisType, true);
        guard.requireNotInAnotherActiveGroup(roundId, studentId);
        UUID groupId = UUID.randomUUID();
        groupRepo.insertGroup(groupId, roundId, studentId);
        groupRepo.insertLeaderMember(UUID.randomUUID(), groupId, roundId, studentId);
        return guard.groupView(groupId, actor);
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
    public GroupResponse addMember(UUID groupId, MemberRequest request, Jwt actor) {
        GroupRow group = guard.lockGroup(groupId);
        boolean supervisorPath = guard.authorizeMemberManagement(group, actor);
        String thesisType = roundLifecycle.requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        guard.requireActiveRoster(group);
        if (!supervisorPath) {
            guard.requireMutableMembership(group, actor);
        }
        if (groupRepo.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            throw conflict("GROUP_FULL", "A thesis group can have at most " + MAX_GROUP_MEMBERS
                    + " members (allowed range: " + MIN_GROUP_MEMBERS + " to " + MAX_GROUP_MEMBERS + ")");
        }
        String studentId = normalize(request == null ? null : request.studentId());
        if (studentId.isBlank()) {
            addExternalMember(group, request, actor);
            return guard.groupView(groupId, actor);
        }
        guard.requireActiveStudent(studentId);
        // The figures belong to the invitee, not the calling leader/admin —
        // the refusal discloses them only when caller and invitee coincide.
        requireEligibility(studentId, group.roundId(), thesisType,
                studentId.equals(studentId(actor)));
        guard.requireNotInAnotherActiveGroup(group.roundId(), studentId);
        groupRepo.insertMember(UUID.randomUUID(), groupId, group.roundId(), studentId,
                guard.nextMemberOrder(groupId));
        groupRepo.touchGroup(groupId);
        // Rosters are rewritten freely during registration, so without a trail
        // a removed member cannot prove they ever belonged to the group.
        audit.record(subject(actor), null, "THESIS_GROUP_MEMBER_ADDED", "THESIS_GROUP", groupId.toString(),
                "Member " + studentId + " added to thesis group");
        return guard.groupView(groupId, actor);
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
        groupRepo.insertExternalMember(UUID.randomUUID(), group.id(), group.roundId(), syntheticId,
                guard.nextMemberOrder(group.id()), displayName, contact.isBlank() ? null : contact);
        groupRepo.touchGroup(group.id());
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
    public GroupResponse removeMember(UUID groupId, String studentId, Jwt actor) {
        GroupRow group = guard.lockGroup(groupId);
        String normalized = normalize(studentId);
        boolean selfLeave = normalized.equals(studentId(actor))
                && (group.status() == GroupStatus.DRAFT || group.status() == GroupStatus.SUBMITTED)
                && group.approvalStatus() != ApprovalStatus.APPROVED;
        boolean supervisorPath = false;
        if (!selfLeave) {
            supervisorPath = guard.authorizeMemberManagement(group, actor);
        }
        roundLifecycle.requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        guard.requireActiveRoster(group);
        if (!supervisorPath && !selfLeave) {
            guard.requireMutableMembership(group, actor);
        }
        if (group.leaderStudentId().equals(normalized)) {
            throw conflict("LEADER_CANNOT_BE_REMOVED", "The group leader cannot be removed");
        }
        if (group.approvalStatus() == ApprovalStatus.APPROVED) {
            int memberCount = groupRepo.countMembers(groupId);
            if (memberCount <= MIN_GROUP_MEMBERS) {
                int remaining = memberCount - 1;
                throw conflict("GROUP_TOO_SMALL", "An approved thesis group must keep " + MIN_GROUP_MEMBERS + " to "
                        + MAX_GROUP_MEMBERS + " members; this removal would leave " + remaining + " ("
                        + (MIN_GROUP_MEMBERS - remaining) + " short of the minimum of " + MIN_GROUP_MEMBERS
                        + ", add a replacement member first)");
            }
        }
        if (groupRepo.deleteMember(groupId, normalized) != 1) {
            throw notFound("MEMBER_NOT_FOUND", "Group member not found");
        }
        groupRepo.touchGroup(groupId);
        audit.record(subject(actor), null, "THESIS_GROUP_MEMBER_REMOVED", "THESIS_GROUP", groupId.toString(),
                "Member " + normalized + " removed from thesis group");
        return guard.groupView(groupId, actor);
    }

    /**
     * Directory lookup used by group leaders to invite classmates into the
     * current round: matches active students by student number or full name
     * (max 8 hits). Only exists while a round is still live — outside an
     * active round there is nothing to join, and an always-on lookup would let
     * any student enumerate the whole student directory. Email addresses are
     * returned so the leader can reach invitees outside the portal.
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
        Integer liveRounds = groupRepo.countLiveRegistrationRounds();
        if (liveRounds == null || liveRounds == 0) {
            return List.of();
        }
        return groupRepo.searchActiveStudents(normalized);
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
        GroupRow group = guard.lockGroup(groupId);
        String thesisType = roundLifecycle.requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_CLOSED);
        // Post-close roster writes are governance, not review: the dean must
        // govern the faculty that owns the group (the topic's department or,
        // for topicless groups, a member's curriculum). The dean-supervisor
        // escape {@code isGroupAdmin} keeps for ordinary review work would
        // otherwise let a foreign dean rewrite an approved roster — including
        // the size-exception marker the sibling waiver already reserves to
        // the owning faculty.
        if (!guard.canGovernGroupScope(group, actor)) {
            throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
        }
        if (topicRepo.countTopicScores(group.topicId()) > 0) {
            throw conflict("GROUP_SCORES_EXIST",
                    "The roster cannot change once scoring has started for the group's topic");
        }
        if ("REMOVE".equals(action)) {
            return removeOverriddenMember(group, request, actor, studentId, cleanReason);
        }
        // A cancelled group has no roster to override: ADD must refuse it the
        // same way REMOVE already does instead of inserting a dead row.
        guard.requireActiveRoster(group);
        int memberCount = groupRepo.countMembers(groupId);
        if (memberCount >= MAX_GROUP_MEMBERS) {
            throw conflict("GROUP_FULL", "A thesis group can have at most " + MAX_GROUP_MEMBERS + " members");
        }
        Integer already = groupRepo.countMemberPresence(groupId, studentId);
        if (already != null && already > 0) {
            throw conflict("STUDENT_ALREADY_IN_GROUP", "The student is already a member of this group");
        }
        guard.requireActiveStudent(studentId);
        guard.requireNotInAnotherActiveGroup(group.roundId(), studentId);
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
        groupRepo.insertMember(UUID.randomUUID(), groupId, group.roundId(), studentId,
                guard.nextMemberOrder(groupId));
        // The deferred membership validator only permits a size-exception
        // marker on an APPROVED group — and underSize already implies one —
        // so the marker is written exactly when the exception was required.
        if (underSize) {
            groupRepo.setSizeException(groupId, cleanReason, subject(actor));
        }
        groupRepo.touchGroup(groupId);
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
        return guard.groupView(groupId, actor);
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
        guard.requireActiveRoster(group);
        Map<String, Object> member = groupRepo.lockMemberRow(group.id(), studentId,
                "The student is not a member of this group");
        int memberCount = groupRepo.countMembers(group.id());
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
            groupRepo.lockMemberRow(group.id(), successorId, "The successor is not a member of this group");
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
        groupRepo.deleteMember(group.id(), studentId);
        if (successorId != null) {
            groupRepo.promoteSuccessor(group.id(), successorId);
            groupRepo.setGroupLeader(group.id(), successorId);
        }
        // The deferred membership validator accepts a 1-2 member approved
        // roster only while size_exception_reason is set; a non-approved group
        // has no minimum, so the marker is written for approved groups only.
        if (sizeException) {
            groupRepo.setSizeException(group.id(), cleanReason, subject(actor));
        }
        groupRepo.touchGroup(group.id());
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
        return guard.groupView(group.id(), actor);
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
}
