package io.campuscore.thesis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.thesis.domain.CouncilMemberRole;
import io.campuscore.thesis.domain.ThesisCouncilMember;
import io.campuscore.thesis.domain.ThesisDefenseCouncil;
import io.campuscore.thesis.domain.ThesisGroup;
import io.campuscore.thesis.domain.ThesisReview;
import io.campuscore.thesis.repository.ThesisResultRepository;
import io.campuscore.thesis.repository.ThesisReviewRepository;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.PublishResultRequest;
import io.campuscore.thesis.web.ThesisDtos.ReviewRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThesisReviewServiceTest {

    @Mock
    private ThesisReviewRepository reviews;

    @Mock
    private ThesisResultRepository results;

    @Mock
    private ThesisCouncilService councils;

    @Mock
    private ThesisGroupService groups;

    @Test
    void requiresEveryCouncilMemberToSubmitBeforePublishing() {
        ThesisDefenseCouncil council = scheduledScoringCouncil();
        ThesisGroup group = approvedGroup(council.getRoundId());
        when(councils.get(council.getId())).thenReturn(council);
        when(groups.get(group.getId())).thenReturn(group);
        when(reviews.findAllByCouncilIdAndGroupId(council.getId(), group.getId())).thenReturn(List.of(
                new ThesisReview(council.getId(), group.getId(), UUID.randomUUID(), BigDecimal.valueOf(8), "good"),
                new ThesisReview(council.getId(), group.getId(), UUID.randomUUID(), BigDecimal.valueOf(7), "good")));
        when(councils.getMembers(council.getId())).thenReturn(List.of(
                member(council.getId(), CouncilMemberRole.CHAIR, 1),
                member(council.getId(), CouncilMemberRole.SECRETARY, 2),
                member(council.getId(), CouncilMemberRole.REVIEWER, 3)));

        ThesisReviewService service = new ThesisReviewService(reviews, results, councils, groups);

        assertThatThrownBy(() -> service.publishResult(
                        new PublishResultRequest(council.getId(), group.getId()),
                        UUID.randomUUID()))
                .isInstanceOf(DomainExceptions.Conflict.class)
                .hasMessageContaining("Every council member");
        verify(results, never()).save(any());
    }

    @Test
    void onlyCouncilMembersCanSubmitReviews() {
        ThesisDefenseCouncil council = scheduledScoringCouncil();
        ThesisGroup group = approvedGroup(council.getRoundId());
        UUID lecturerId = UUID.randomUUID();
        when(councils.get(council.getId())).thenReturn(council);
        when(groups.get(group.getId())).thenReturn(group);
        when(councils.isMember(council.getId(), lecturerId)).thenReturn(false);

        ThesisReviewService service = new ThesisReviewService(reviews, results, councils, groups);

        assertThatThrownBy(() -> service.submit(
                        new ReviewRequest(council.getId(), group.getId(), BigDecimal.valueOf(8.5), "comment"),
                        lecturerId))
                .isInstanceOf(DomainExceptions.Conflict.class)
                .hasMessageContaining("council members");
        verify(reviews, never()).save(any());
    }

    private ThesisDefenseCouncil scheduledScoringCouncil() {
        ThesisDefenseCouncil council = new ThesisDefenseCouncil(UUID.randomUUID(), UUID.randomUUID());
        council.schedule(Instant.now().plusSeconds(3600), "A-101");
        council.openScoring();
        return council;
    }

    private ThesisGroup approvedGroup(UUID roundId) {
        ThesisGroup group = new ThesisGroup(roundId, UUID.randomUUID());
        group.assignTopic(UUID.randomUUID());
        group.approve(UUID.randomUUID());
        return group;
    }

    private ThesisCouncilMember member(UUID councilId, CouncilMemberRole role, int order) {
        return new ThesisCouncilMember(councilId, UUID.randomUUID(), role, order);
    }
}
