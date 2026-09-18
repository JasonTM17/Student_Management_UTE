package io.campuscore.restfulapi.thesis.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Server-owned, evidence-based student thesis progress contract. */
public final class ThesisProgressDtos {
    private ThesisProgressDtos() { }

    public record ProgressResponse(
            UUID roundId,
            String roundStatus,
            String participationState,
            String currentMilestone,
            List<String> completedMilestones,
            String attentionState,
            UUID groupId,
            String groupStatus,
            String approvalStatus,
            int memberCount,
            UUID topicId,
            String topicTitle,
            UUID reportId,
            Instant reportSubmittedAt,
            UUID councilId,
            BigDecimal finalScore,
            Instant finalScoreFinalizedAt,
            String userReportedGroupStatus) { }
}
