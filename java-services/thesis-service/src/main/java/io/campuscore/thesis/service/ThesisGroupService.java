package io.campuscore.thesis.service;

import io.campuscore.thesis.domain.ApprovalStatus;
import io.campuscore.thesis.domain.GroupStatus;
import io.campuscore.thesis.domain.RoundStatus;
import io.campuscore.thesis.domain.ThesisGroup;
import io.campuscore.thesis.domain.ThesisGroupMember;
import io.campuscore.thesis.domain.ThesisRegistrationRound;
import io.campuscore.thesis.domain.ThesisTopic;
import io.campuscore.thesis.domain.TopicStatus;
import io.campuscore.thesis.repository.ThesisGroupMemberRepository;
import io.campuscore.thesis.repository.ThesisGroupRepository;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.AddMemberRequest;
import io.campuscore.thesis.web.ThesisDtos.AssignTopicRequest;
import io.campuscore.thesis.web.ThesisDtos.CreateGroupRequest;
import io.campuscore.thesis.web.ThesisDtos.DecideGroupRequest;
import io.campuscore.thesis.web.ThesisDtos.GroupResponse;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThesisGroupService {

    private final ThesisGroupRepository groups;
    private final ThesisGroupMemberRepository members;
    private final ThesisRoundService rounds;
    private final ThesisTopicService topics;

    public ThesisGroupService(
            ThesisGroupRepository groups,
            ThesisGroupMemberRepository members,
            ThesisRoundService rounds,
            ThesisTopicService topics) {
        this.groups = groups;
        this.members = members;
        this.rounds = rounds;
        this.topics = topics;
    }

    @Transactional
    public GroupResponse create(CreateGroupRequest request, UUID studentId) {
        requireStudent(studentId);
        ThesisRegistrationRound round = rounds.get(request.roundId());
        if (round.getStatus() != RoundStatus.REGISTRATION_OPEN) {
            throw new DomainExceptions.Conflict("Groups can only be created during an open registration round");
        }
        if (members.existsByRoundIdAndStudentId(request.roundId(), studentId)
                || groups.existsByRoundIdAndLeaderStudentId(request.roundId(), studentId)) {
            throw new DomainExceptions.Conflict("Student already belongs to a group in this round");
        }

        ThesisGroup group = groups.save(new ThesisGroup(request.roundId(), studentId));
        members.save(new ThesisGroupMember(group.getId(), round.getId(), studentId, 1, true));
        return response(group);
    }

    @Transactional
    public GroupResponse addMember(UUID groupId, AddMemberRequest request, UUID actorStudentId) {
        ThesisGroup group = lockedGroup(groupId);
        requireLeader(group, actorStudentId);
        if (group.getStatus() == GroupStatus.CANCELLED || group.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new DomainExceptions.Conflict("Approved or cancelled groups cannot change membership");
        }
        List<ThesisGroupMember> currentMembers = members.findAllByGroupIdForUpdate(groupId);
        if (currentMembers.size() >= 3) {
            throw new DomainExceptions.Conflict("A thesis group can contain at most 3 students");
        }
        if (members.existsByRoundIdAndStudentId(group.getRoundId(), request.studentId())) {
            throw new DomainExceptions.Conflict("Student already belongs to a group in this round");
        }
        members.save(new ThesisGroupMember(
                groupId,
                group.getRoundId(),
                request.studentId(),
                currentMembers.size() + 1,
                false));
        return response(group);
    }

    @Transactional
    public GroupResponse assignTopic(UUID groupId, AssignTopicRequest request, UUID actorStudentId) {
        ThesisGroup group = lockedGroup(groupId);
        requireLeader(group, actorStudentId);
        ThesisTopic topic = topics.get(request.topicId());
        if (!group.getRoundId().equals(topic.getRoundId()) || topic.getStatus() != TopicStatus.PUBLISHED) {
            throw new DomainExceptions.Conflict("Topic is not published for this registration round");
        }
        long usedSlots = groups.countByTopicIdAndApprovalStatusIn(
                topic.getId(),
                EnumSet.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED));
        if (group.getTopicId() == null && usedSlots >= topic.getMaxGroups()) {
            throw new DomainExceptions.Conflict("Topic has reached its group capacity");
        }
        group.assignTopic(topic.getId());
        return response(groups.save(group));
    }

    @Transactional
    public GroupResponse decide(UUID groupId, DecideGroupRequest request, UUID actorId) {
        ThesisGroup group = lockedGroup(groupId);
        if (Boolean.TRUE.equals(request.approved())) {
            group.approve(actorId);
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new DomainExceptions.Conflict("A rejection reason is required");
            }
            group.reject(request.reason().trim());
        }
        return response(groups.save(group));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list(UUID roundId) {
        rounds.get(roundId);
        return groups.findAllByRoundIdOrderByCreatedAtDesc(roundId)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getResponse(UUID id) {
        return response(groups.findById(id)
                .orElseThrow(() -> new DomainExceptions.NotFound("Thesis group not found")));
    }

    private ThesisGroup lockedGroup(UUID groupId) {
        ThesisGroup group = groups.findByIdForUpdate(groupId);
        if (group == null) {
            throw new DomainExceptions.NotFound("Thesis group not found");
        }
        return group;
    }

    private GroupResponse response(ThesisGroup group) {
        return GroupResponse.from(group, members.findAllByGroupIdOrderByMemberOrder(group.getId()));
    }

    private void requireLeader(ThesisGroup group, UUID studentId) {
        requireStudent(studentId);
        if (!studentId.equals(group.getLeaderStudentId())) {
            throw new DomainExceptions.Conflict("Only the group leader can change this group");
        }
    }

    private void requireStudent(UUID studentId) {
        if (studentId == null) {
            throw new DomainExceptions.Conflict("A student identity is required for this action");
        }
    }
}
