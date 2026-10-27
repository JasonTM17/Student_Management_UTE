package io.campuscore.restfulapi.thesis.service;

import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.conflict;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.hasRole;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isAdmin;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isGlobalAdmin;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.isLecturer;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.normalize;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.notFound;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.studentId;
import static io.campuscore.restfulapi.thesis.service.ThesisMutationSupport.subject;

import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.model.ApprovalStatus;
import io.campuscore.restfulapi.thesis.model.GroupRow;
import io.campuscore.restfulapi.thesis.model.GroupStatus;
import io.campuscore.restfulapi.thesis.model.LecturerScope;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupMutationRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicMutationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Shared group guards of the thesis mutation boundary — the single
 * implementation the membership and governance services both decide through:
 * the group row lock, roster and one-seat rules, leader/supervisor/governance
 * authorization, and the enriched group response renderer. Every statement
 * runs in the transaction the {@link ThesisMutationService} facade opened.
 */
@Component
@Profile("persistence")
class ThesisGroupAccessGuard {

    private final ThesisGroupMutationRepository groupRepo;
    private final ThesisTopicMutationRepository topicRepo;
    private final ThesisGroupReadRepository groups;
    private final ThesisGroupWithdrawalPolicy withdrawalPolicy;

    ThesisGroupAccessGuard(
            ThesisGroupMutationRepository groupRepo,
            ThesisTopicMutationRepository topicRepo,
            ThesisGroupReadRepository groups,
            ThesisGroupWithdrawalPolicy withdrawalPolicy) {
        this.groupRepo = groupRepo;
        this.topicRepo = topicRepo;
        this.groups = groups;
        this.withdrawalPolicy = withdrawalPolicy;
    }

    /** Locked group snapshot every group mutation starts from. */
    GroupRow lockGroup(UUID id) {
        return groupRepo.lockGroupRow(id);
    }

    /**
     * The group response enriched with the actor's withdraw-approval
     * affordance; the policy bean stays optional so plain fixture
     * constructors keep working.
     */
    GroupResponse groupView(UUID groupId, Jwt actor) {
        GroupResponse group = groups.findById(groupId);
        return withdrawalPolicy == null ? group : withdrawalPolicy.attach(group, actor);
    }

    void requireActiveRoster(GroupRow group) {
        if (group.status() == GroupStatus.CANCELLED) {
            throw conflict("GROUP_STATE_CONFLICT", "A cancelled group has a read-only historical roster");
        }
    }

    /** Membership is frozen once a supervisor approves the group; admins coordinate through re-review instead. */
    void requireMutableMembership(GroupRow group, Jwt actor) {
        if (!isAdmin(actor) && group.approvalStatus() == ApprovalStatus.APPROVED) {
            throw conflict("GROUP_STATE_CONFLICT", "An approved group's membership is frozen");
        }
    }

    /**
     * Brief R4: a student sits in at most one group while their current round
     * has not finished. Rounds that reached RESULTS_PUBLISHED/CLOSED/CANCELLED
     * release their members for the next round type (e.g. a course topic this
     * term and a graduation thesis later).
     */
    void requireNotInAnotherActiveGroup(UUID roundId, String studentId) {
        // a leader's only way out of a group is
        // PATCH progress → CANCELLED, but the member rows stayed — so the
        // same-round membership check locked the leader out of every new group
        // for the rest of the round. A cancelled group holds no seat.
        int sameRound = groupRepo.countSameRoundMembership(roundId, studentId);
        if (sameRound > 0) {
            throw conflict("STUDENT_ALREADY_IN_GROUP", "Student already belongs to a group in this round");
        }
        int otherActive = groupRepo.countOtherActiveMembership(studentId, roundId);
        if (otherActive > 0) {
            throw conflict("STUDENT_ACTIVE_IN_OTHER_GROUP",
                    "The student already belongs to a group in another round that has not finished");
        }
    }

    void requireActiveStudent(String studentId) {
        Integer present = groupRepo.lockActiveStudent(studentId);
        if (present == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "An active student profile is required");
        }
    }

    /** Reuse a vacant slot; surviving members keep their identity and order. */
    int nextMemberOrder(UUID groupId) {
        List<Integer> usedOrders = groupRepo.listMemberOrders(groupId);
        for (int order = 1; order <= ThesisMutationSupport.MAX_GROUP_MEMBERS; order++) {
            if (!usedOrders.contains(order)) return order;
        }
        throw conflict("GROUP_FULL", "The thesis group has no available member slot");
    }

    void authorizeLeaderOrAdmin(GroupRow group, Jwt actor) {
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
    boolean authorizeMemberManagement(GroupRow group, Jwt actor) {
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

    boolean isSupervisorOf(UUID topicId, String lecturerId) {
        // lecturer_id may hold either a Lecturer.id or, from the legacy
        // created_by fallback, a User.id — resolve either against an ACTIVE
        // lecturer profile so a deactivated supervisor loses review power.
        Integer count = topicRepo.countActiveSupervision(topicId, lecturerId);
        return count != null && count > 0;
    }

    boolean isCurrentSupervisor(UUID topicId, Jwt actor) {
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        Integer supervisorCount = topicRepo.countCurrentSupervision(topicId, actorId,
                lecturerId.isBlank() ? actorId : lecturerId);
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
    boolean canGovernGroupScope(GroupRow group, Jwt actor) {
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
    boolean isGroupAdmin(GroupRow group, Jwt actor) {
        if (isGlobalAdmin(actor)) return true;
        if (!hasRole(actor, "TRUONG_KHOA")) return false;
        LecturerScope deanScope = requireDeanScope(actor);
        if (groups.groupInFaculty(group.id(), deanScope.facultyId()) || isDeanSupervisor(group.topicId(), actor)) {
            return true;
        }
        throw notFound("THESIS_GROUP_NOT_FOUND", "Thesis group not found");
    }

    LecturerScope requireDeanScope(Jwt actor) {
        LecturerScope scope = groups.findLecturerScope(
                normalize(actor.getClaimAsString("lecturerId")), subject(actor));
        if (scope == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "FACULTY_PROFILE_REQUIRED",
                    "An active lecturer profile bound to the faculty head is required");
        }
        return scope;
    }

    /** Whether the group sits inside the dean's own faculty. */
    boolean groupInFaculty(GroupRow group, Jwt actor) {
        return groups.groupInFaculty(group.id(), requireDeanScope(actor).facultyId());
    }

    /** Whether the group sits inside the given department. */
    boolean groupInDepartment(GroupRow group, String department) {
        return groups.groupInDepartment(group.id(), department);
    }

    boolean isDeanSupervisor(UUID topicId, Jwt actor) {
        return topicId != null && isLecturer(actor)
                && isSupervisorOf(topicId, normalize(actor.getClaimAsString("lecturerId")));
    }
}
