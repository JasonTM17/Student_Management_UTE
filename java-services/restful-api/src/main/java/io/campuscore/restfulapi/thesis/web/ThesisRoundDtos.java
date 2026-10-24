package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.ThesisRegistrationRound;
import java.time.Instant;
import java.util.UUID;

public final class ThesisRoundDtos {

    private ThesisRoundDtos() {
    }

    /**
     * Read-only deletability view for the round card: {@code deletable} is true
     * only for an empty {@code DRAFT}/{@code CANCELLED} round; the per-kind
     * counts explain every blocker so the UI does not need a second request.
     */
    public record RoundDeletionInfo(
            boolean deletable,
            long topics,
            long groups,
            long members,
            long reports,
            long councils) {

        public static RoundDeletionInfo of(
                RoundStatus status,
                long topics,
                long groups,
                long members,
                long reports,
                long councils) {
            boolean statusEligible =
                    status == RoundStatus.DRAFT || status == RoundStatus.CANCELLED;
            boolean empty = topics == 0 && groups == 0 && members == 0
                    && reports == 0 && councils == 0;
            return new RoundDeletionInfo(
                    statusEligible && empty, topics, groups, members, reports, councils);
        }
    }

    public record RoundResponse(
            UUID id,
            String name,
            String thesisType,
            Instant lecturerSubmitStart,
            Instant lecturerSubmitEnd,
            Instant registrationStart,
            Instant registrationEnd,
            Instant gvpbDeadline,
            Instant reportDeadline,
            Instant proposalPublishAt,
            Instant reportDate,
            Instant defenseDate,
            RoundStatus status,
            RoundStatus cancelledFromStatus,
            RoundDeletionInfo deletion) {

        public static RoundResponse from(ThesisRegistrationRound round) {
            return from(round, null);
        }

        public static RoundResponse from(ThesisRegistrationRound round, RoundDeletionInfo deletion) {
            return new RoundResponse(
                    round.getId(), round.getName(), round.getThesisType(),
                    round.getLecturerSubmitStart(), round.getLecturerSubmitEnd(),
                    round.getRegistrationStart(), round.getRegistrationEnd(),
                    round.getGvpbDeadline(), round.getReportDeadline(), round.getProposalPublishAt(),
                    round.getReportDate(), round.getDefenseDate(), round.getStatus(),
                    round.getCancelledFromStatus(), deletion);
        }
    }
}
