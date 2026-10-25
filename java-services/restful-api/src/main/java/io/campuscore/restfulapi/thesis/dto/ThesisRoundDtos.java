package io.campuscore.restfulapi.thesis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.campuscore.restfulapi.thesis.entity.ThesisRegistrationRound;
import io.campuscore.restfulapi.thesis.model.EligibilityView;
import io.campuscore.restfulapi.thesis.model.RoundStatus;
import java.time.Instant;
import java.util.List;
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

    /**
     * End-of-life capability view for the round card: a RESULTS_PUBLISHED
     * round may be closed once the retraction grace window has ended — while
     * it is still open {@code closeBlocker} carries the stable refusal code
     * and {@code retractWindowEndsAt} the instant it lifts — and a CLOSED
     * round may be reopened. Closing only flips the status, so this is a
     * pure derivation from the round row; no extra request is needed.
     */
    public record RoundClosureInfo(
            boolean closeAllowed,
            boolean reopenAllowed,
            String closeBlocker,
            Instant retractWindowEndsAt) {

        public static RoundClosureInfo of(
                RoundStatus status, Instant resultsPublishedAt, int retractGraceDays, Instant now) {
            if (status == RoundStatus.CLOSED) {
                return new RoundClosureInfo(false, true, null, null);
            }
            if (status == RoundStatus.RESULTS_PUBLISHED) {
                Instant endsAt = resultsPublishedAt == null
                        ? null : resultsPublishedAt.plus(java.time.Duration.ofDays(retractGraceDays));
                boolean windowOpen = endsAt != null && now.isBefore(endsAt);
                return new RoundClosureInfo(!windowOpen, false,
                        windowOpen ? "RETRACT_WINDOW_OPEN" : null, windowOpen ? endsAt : null);
            }
            return new RoundClosureInfo(false, false, null, null);
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
            RoundDeletionInfo deletion,
            RoundClosureInfo closure,
            /**
             * Whether governance may waive a topic's counter-review in this
             * round (see ThesisGovernanceCapabilities). False on every
             * shipped undergraduate round type; the UI hides the waive action
             * and must not keep its own round-type list.
             */
            boolean counterReviewWaiverAllowed,
            /**
             * Roster policy bounds for this round (ThesisMutationService's
             * MIN/MAX_GROUP_MEMBERS): advisory while joining, enforced on
             * supervisor approval — surfaced so the group card can show the
             * missing-member state without a client-side constant.
             */
            int minGroupSize,
            int maxGroupSize,
            /**
             * Caller-scoped registration eligibility: populated only for
             * student callers so the thesis page can render the eligibility
             * card without a second request. Null for staff/governance callers.
             */
            EligibilityView eligibility,
            /** Database wall-clock sample used by clients for schedule-gated actions. */
            Instant serverNow,
            /** Admin-only read projection of restore guards; the mutation rechecks under locks. */
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> restoreBlockers) {

        public static RoundResponse from(ThesisRegistrationRound round) {
            return from(round, null, null, false, 0, 0, null);
        }

        public static RoundResponse from(ThesisRegistrationRound round, RoundDeletionInfo deletion,
                RoundClosureInfo closure,
                boolean counterReviewWaiverAllowed, int minGroupSize, int maxGroupSize) {
            return from(round, deletion, closure, counterReviewWaiverAllowed,
                    minGroupSize, maxGroupSize, null);
        }

        public static RoundResponse from(ThesisRegistrationRound round, RoundDeletionInfo deletion,
                RoundClosureInfo closure,
                boolean counterReviewWaiverAllowed, int minGroupSize, int maxGroupSize,
                EligibilityView eligibility) {
            return from(round, deletion, closure, counterReviewWaiverAllowed, minGroupSize,
                    maxGroupSize, eligibility, null);
        }

        public static RoundResponse from(ThesisRegistrationRound round, RoundDeletionInfo deletion,
                RoundClosureInfo closure,
                boolean counterReviewWaiverAllowed, int minGroupSize, int maxGroupSize,
                EligibilityView eligibility, Instant serverNow) {
            return from(round, deletion, closure, counterReviewWaiverAllowed, minGroupSize, maxGroupSize,
                    eligibility, serverNow, null);
        }

        public static RoundResponse from(ThesisRegistrationRound round, RoundDeletionInfo deletion,
                RoundClosureInfo closure,
                boolean counterReviewWaiverAllowed, int minGroupSize, int maxGroupSize,
                EligibilityView eligibility, Instant serverNow, List<String> restoreBlockers) {
            return new RoundResponse(
                    round.getId(), round.getName(), round.getThesisType(),
                    round.getLecturerSubmitStart(), round.getLecturerSubmitEnd(),
                    round.getRegistrationStart(), round.getRegistrationEnd(),
                    round.getGvpbDeadline(), round.getReportDeadline(), round.getProposalPublishAt(),
                    round.getReportDate(), round.getDefenseDate(), round.getStatus(),
                    round.getCancelledFromStatus(), deletion, closure, counterReviewWaiverAllowed,
                    minGroupSize, maxGroupSize, eligibility, serverNow, restoreBlockers);
        }
    }
}
