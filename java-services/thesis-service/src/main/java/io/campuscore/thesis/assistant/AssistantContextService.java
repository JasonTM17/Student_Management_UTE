package io.campuscore.thesis.assistant;

import io.campuscore.thesis.domain.ThesisGroup;
import io.campuscore.thesis.domain.ThesisGroupMember;
import io.campuscore.thesis.domain.ThesisRegistrationRound;
import io.campuscore.thesis.domain.ThesisTopic;
import io.campuscore.thesis.domain.TopicStatus;
import io.campuscore.thesis.repository.ThesisGroupMemberRepository;
import io.campuscore.thesis.repository.ThesisGroupRepository;
import io.campuscore.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.thesis.repository.ThesisTopicRepository;
import io.campuscore.thesis.security.AccessContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantContextService {

    private final ThesisRegistrationRoundRepository rounds;
    private final ThesisTopicRepository topics;
    private final ThesisGroupRepository groups;
    private final ThesisGroupMemberRepository members;

    public AssistantContextService(
            ThesisRegistrationRoundRepository rounds,
            ThesisTopicRepository topics,
            ThesisGroupRepository groups,
            ThesisGroupMemberRepository members) {
        this.rounds = rounds;
        this.topics = topics;
        this.groups = groups;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public String build(AccessContext actor) {
        StringBuilder context = new StringBuilder();
        List<ThesisRegistrationRound> availableRounds = rounds.findAllByOrderByRegistrationStartDesc();
        context.append("Rounds:\n");
        availableRounds.stream().limit(10).forEach(round -> context.append("- id=")
                .append(round.getId())
                .append(", name=")
                .append(round.getName())
                .append(", type=")
                .append(round.getThesisType())
                .append(", status=")
                .append(round.getStatus())
                .append("\n"));

        context.append("Published topics:\n");
        topics.findAllByStatusOrderByTitle(TopicStatus.PUBLISHED).stream().limit(30)
                .forEach(topic -> appendTopic(context, topic));

        context.append("Current user's group records:\n");
        if (actor.studentId() == null) {
            context.append("- No student identity is attached to this session. Do not infer a group.\n");
        } else {
            availableRounds.stream().limit(10).forEach(round -> appendCurrentGroup(context, round.getId(), actor.studentId()));
        }
        return context.toString();
    }

    private void appendTopic(StringBuilder context, ThesisTopic topic) {
        context.append("- id=")
                .append(topic.getId())
                .append(", roundId=")
                .append(topic.getRoundId())
                .append(", departmentId=")
                .append(topic.getDepartmentId())
                .append(", title=")
                .append(topic.getTitle())
                .append(", maxGroups=")
                .append(topic.getMaxGroups())
                .append("\n");
    }

    private void appendCurrentGroup(StringBuilder context, UUID roundId, UUID studentId) {
        List<ThesisGroupMember> memberships = members.findAllByRoundIdAndStudentId(roundId, studentId);
        for (ThesisGroupMember membership : memberships) {
            groups.findById(membership.getGroupId()).ifPresent(group -> context.append("- groupId=")
                    .append(group.getId())
                    .append(", roundId=")
                    .append(group.getRoundId())
                    .append(", status=")
                    .append(group.getStatus())
                    .append(", approval=")
                    .append(group.getApprovalStatus())
                    .append(", topicId=")
                    .append(group.getTopicId())
                    .append("\n"));
        }
    }
}
