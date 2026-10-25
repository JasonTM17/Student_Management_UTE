package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.dto.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.CloseRegistrationPreviewResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalWithdrawalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.LateTopicRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.MemberRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsPreviewResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RetractPublicationRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RosterOverrideRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundClosureRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.StudentSearchResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicAssignmentRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicCreateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicReassignRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.dto.ThesisTopicDtos.TopicResponse;
import io.campuscore.restfulapi.thesis.model.RoundStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public transaction facade of the thesis write boundary. The controller and
 * every cross-module caller keep addressing this bean; the work itself lives
 * in the responsibility services it delegates to:
 *
 * <ul>
 *   <li>{@link ThesisRoundLifecycleService} — round schedule, transitions,
 *   cancel/restore/delete, deadline escape hatches, and the round guards;</li>
 *   <li>{@link ThesisPublicationService} — publish preview, the guarded
 *   publish transition with its post-commit fan-out, retract/close/reopen;</li>
 *   <li>{@link ThesisTopicMutationService} — topic drafting/editing/approval,
 *   archiving, and the governance outcome verbs;</li>
 *   <li>{@link ThesisGroupMembershipService} — group creation, roster
 *   staffing/removal, the post-close roster override, student search;</li>
 *   <li>{@link ThesisGroupGovernanceService} — topic binding, progress,
 *   supervisor review, approval withdrawal, orphan re-pointing.</li>
 * </ul>
 *
 * <p>Transactions open here — every {@code @Transactional} below starts the
 * unit of work the delegated statements and cross-service guard calls join,
 * preserving the single-transaction semantics the monolith had.
 */
@Service
@Profile("persistence")
public class ThesisMutationService {

    private final ThesisRoundLifecycleService roundLifecycle;
    private final ThesisPublicationService publication;
    private final ThesisTopicMutationService topicMutations;
    private final ThesisGroupMembershipService groupMembership;
    private final ThesisGroupGovernanceService groupGovernance;

    @Autowired
    public ThesisMutationService(
            ThesisRoundLifecycleService roundLifecycle,
            ThesisPublicationService publication,
            ThesisTopicMutationService topicMutations,
            ThesisGroupMembershipService groupMembership,
            ThesisGroupGovernanceService groupGovernance) {
        this.roundLifecycle = roundLifecycle;
        this.publication = publication;
        this.topicMutations = topicMutations;
        this.groupMembership = groupMembership;
        this.groupGovernance = groupGovernance;
    }

    @Transactional
    public RoundResponse createRound(RoundCreateRequest request) {
        return roundLifecycle.createRound(request);
    }

    @Transactional
    public RoundResponse createRound(RoundCreateRequest request, Jwt actor) {
        return roundLifecycle.createRound(request, actor);
    }

    @Transactional
    public RoundResponse transitionRound(UUID id, RoundStatus expected, RoundStatus next) {
        return roundLifecycle.transitionRound(id, expected, next);
    }

    @Transactional
    public RoundResponse transitionRound(UUID id, RoundStatus expected, RoundStatus next, Jwt actor) {
        return roundLifecycle.transitionRound(id, expected, next, actor);
    }

    @Transactional
    public RoundResponse updateRound(UUID id, RoundCreateRequest request, Jwt actor) {
        return roundLifecycle.updateRound(id, request, actor);
    }

    @Transactional
    public RoundResponse cancelRound(UUID id, Jwt actor) {
        return roundLifecycle.cancelRound(id, actor);
    }

    @Transactional
    public RoundResponse restoreRound(UUID id, String targetStatus, Jwt actor) {
        return roundLifecycle.restoreRound(id, targetStatus, actor);
    }

    @Transactional
    public void deleteRound(UUID id, String reason, Jwt actor) {
        roundLifecycle.deleteRound(id, reason, actor);
    }

    @Transactional(readOnly = true)
    public CloseRegistrationPreviewResponse closeRegistrationPreview(UUID roundId) {
        return roundLifecycle.closeRegistrationPreview(roundId);
    }

    @Transactional
    public RoundResponse extendReportDeadline(UUID id, Instant newDeadline, Jwt actor) {
        return roundLifecycle.extendReportDeadline(id, newDeadline, actor);
    }

    @Transactional
    public RoundResponse extendGvpbDeadline(UUID id, Instant newDeadline, String reason, Jwt actor) {
        return roundLifecycle.extendGvpbDeadline(id, newDeadline, reason, actor);
    }

    @Transactional(readOnly = true)
    public PublishResultsPreviewResponse publishResultsPreview(UUID roundId, Jwt actor) {
        return publication.publishResultsPreview(roundId, actor);
    }

    @Transactional
    public RoundResponse publishResults(UUID id) {
        return publication.publishResults(id);
    }

    @Transactional
    public RoundResponse publishResults(UUID id, Jwt actor) {
        return publication.publishResults(id, actor);
    }

    @Transactional
    public RoundResponse publishResults(UUID id, Boolean confirmAllExcluded, Jwt actor) {
        return publication.publishResults(id, confirmAllExcluded, actor);
    }

    @Transactional
    public RoundResponse retractResults(UUID id, RetractPublicationRequest request, Jwt actor) {
        return publication.retractResults(id, request, actor);
    }

    @Transactional
    public RoundResponse closeRound(UUID id, RoundClosureRequest request, Jwt actor) {
        return publication.closeRound(id, request, actor);
    }

    @Transactional
    public RoundResponse reopenRound(UUID id, RoundClosureRequest request, Jwt actor) {
        return publication.reopenRound(id, request, actor);
    }

    @Transactional
    public TopicResponse createTopic(TopicCreateRequest request, Jwt actor) {
        return topicMutations.createTopic(request, actor);
    }

    @Transactional
    public TopicResponse updateTopic(UUID id, TopicUpdateRequest request, Jwt actor) {
        return topicMutations.updateTopic(id, request, actor);
    }

    @Transactional
    public TopicResponse publishTopic(UUID id, Jwt actor) {
        return topicMutations.publishTopic(id, actor);
    }

    @Transactional
    public TopicResponse archiveTopic(UUID id, Jwt actor) {
        return topicMutations.archiveTopic(id, actor);
    }

    @Transactional
    public TopicResponse withdrawTopic(UUID id, String reason, Jwt actor) {
        return topicMutations.withdrawTopic(id, reason, actor);
    }

    @Transactional
    public TopicResponse reinstateTopic(UUID id, String reason, Jwt actor) {
        return topicMutations.reinstateTopic(id, reason, actor);
    }

    @Transactional
    public TopicResponse createLateTopic(UUID roundId, LateTopicRequest request, Jwt actor) {
        return topicMutations.createLateTopic(roundId, request, actor);
    }

    @Transactional
    public GroupResponse createGroup(GroupCreateRequest request, Jwt actor) {
        return groupMembership.createGroup(request, actor);
    }

    @Transactional
    public GroupResponse addMember(UUID groupId, MemberRequest request, Jwt actor) {
        return groupMembership.addMember(groupId, request, actor);
    }

    @Transactional
    public GroupResponse removeMember(UUID groupId, String studentId, Jwt actor) {
        return groupMembership.removeMember(groupId, studentId, actor);
    }

    @Transactional
    public GroupResponse overrideGroupRoster(UUID groupId, RosterOverrideRequest request, Jwt actor) {
        return groupMembership.overrideGroupRoster(groupId, request, actor);
    }

    public List<StudentSearchResponse> searchStudents(String query) {
        return groupMembership.searchStudents(query);
    }

    @Transactional
    public GroupResponse assignTopic(UUID groupId, TopicAssignmentRequest request, Jwt actor) {
        return groupGovernance.assignTopic(groupId, request, actor);
    }

    @Transactional
    public GroupResponse updateProgress(UUID groupId, ProgressRequest request, Jwt actor) {
        return groupGovernance.updateProgress(groupId, request, actor);
    }

    @Transactional
    public GroupResponse approveGroup(UUID groupId, Jwt actor) {
        return groupGovernance.approveGroup(groupId, actor);
    }

    @Transactional
    public GroupResponse approveGroup(UUID groupId, GroupApprovalRequest request, Jwt actor) {
        return groupGovernance.approveGroup(groupId, request, actor);
    }

    @Transactional
    public GroupResponse rejectGroup(UUID groupId, GroupRejectionRequest request, Jwt actor) {
        return groupGovernance.rejectGroup(groupId, request, actor);
    }

    @Transactional
    public GroupResponse withdrawGroupApproval(UUID groupId, GroupApprovalWithdrawalRequest request, Jwt actor) {
        return groupGovernance.withdrawGroupApproval(groupId, request, actor);
    }

    @Transactional
    public GroupResponse reassignOrphanedTopic(UUID groupId, TopicReassignRequest request, Jwt actor) {
        return groupGovernance.reassignOrphanedTopic(groupId, request, actor);
    }
}
