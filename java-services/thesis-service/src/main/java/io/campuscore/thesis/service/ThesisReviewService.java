package io.campuscore.thesis.service;

import io.campuscore.thesis.domain.ApprovalStatus;
import io.campuscore.thesis.domain.CouncilStatus;
import io.campuscore.thesis.domain.ThesisDefenseCouncil;
import io.campuscore.thesis.domain.ThesisGroup;
import io.campuscore.thesis.domain.ThesisResult;
import io.campuscore.thesis.domain.ThesisReview;
import io.campuscore.thesis.repository.ThesisResultRepository;
import io.campuscore.thesis.repository.ThesisReviewRepository;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.PublishResultRequest;
import io.campuscore.thesis.web.ThesisDtos.ResultResponse;
import io.campuscore.thesis.web.ThesisDtos.ReviewRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThesisReviewService {

    private final ThesisReviewRepository reviews;
    private final ThesisResultRepository results;
    private final ThesisCouncilService councils;
    private final ThesisGroupService groups;

    public ThesisReviewService(
            ThesisReviewRepository reviews,
            ThesisResultRepository results,
            ThesisCouncilService councils,
            ThesisGroupService groups) {
        this.reviews = reviews;
        this.results = results;
        this.councils = councils;
        this.groups = groups;
    }

    @Transactional
    public void submit(ReviewRequest request, UUID lecturerId) {
        if (lecturerId == null) {
            throw new DomainExceptions.Conflict("A lecturer identity is required to submit a review");
        }
        ThesisDefenseCouncil council = councils.get(request.councilId());
        ThesisGroup group = groups.get(request.groupId());
        if (council.getStatus() != CouncilStatus.SCORING_OPEN) {
            throw new DomainExceptions.Conflict("Council scoring is not open");
        }
        if (!council.getRoundId().equals(group.getRoundId())
                || group.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new DomainExceptions.Conflict("Group is not eligible for this council");
        }
        if (!councils.isMember(request.councilId(), lecturerId)) {
            throw new DomainExceptions.Conflict("Only council members can submit reviews");
        }
        if (reviews.existsByCouncilIdAndGroupIdAndReviewerId(
                request.councilId(), request.groupId(), lecturerId)) {
            throw new DomainExceptions.Conflict("A reviewer can submit only one review per group");
        }
        reviews.save(new ThesisReview(
                request.councilId(),
                request.groupId(),
                lecturerId,
                request.score().setScale(2, RoundingMode.HALF_UP),
                request.comment() == null ? null : request.comment().trim()));
    }

    @Transactional
    public ResultResponse publishResult(PublishResultRequest request, UUID actorId) {
        ThesisDefenseCouncil council = councils.get(request.councilId());
        ThesisGroup group = groups.get(request.groupId());
        if (council.getStatus() != CouncilStatus.SCORING_OPEN) {
            throw new DomainExceptions.Conflict("Council scoring is not open");
        }
        if (!council.getRoundId().equals(group.getRoundId())
                || group.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new DomainExceptions.Conflict("Group is not eligible for this council");
        }
        List<ThesisReview> groupReviews = reviews.findAllByCouncilIdAndGroupId(
                request.councilId(), request.groupId());
        int councilSize = councils.getMembers(request.councilId()).size();
        if (groupReviews.size() < councilSize) {
            throw new DomainExceptions.Conflict("Every council member must submit a review before publication");
        }
        if (results.findByGroupId(request.groupId()).isPresent()) {
            throw new DomainExceptions.Conflict("A result already exists for this group");
        }

        BigDecimal total = groupReviews.stream()
                .map(ThesisReview::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(groupReviews.size()), 2, RoundingMode.HALF_UP);
        ThesisResult result = new ThesisResult(request.groupId(), total, gradeFor(total));
        result.publish(actorId);
        ThesisResult saved = results.save(result);
        councils.finalizeCouncil(request.councilId());
        return ResultResponse.from(saved);
    }

    private String gradeFor(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(8.5)) >= 0) {
            return "A";
        }
        if (score.compareTo(BigDecimal.valueOf(7)) >= 0) {
            return "B";
        }
        if (score.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return "C";
        }
        return "F";
    }
}
