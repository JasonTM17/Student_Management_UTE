package io.campuscore.restfulapi.thesis.dto;

import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.entity.ThesisRegistrationRound;
import java.time.Instant;
import java.util.UUID;

public final class ThesisRoundDtos {

    private ThesisRoundDtos() {
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
            RoundStatus cancelledFromStatus) {

        public static RoundResponse from(ThesisRegistrationRound round) {
            return new RoundResponse(
                    round.getId(), round.getName(), round.getThesisType(),
                    round.getLecturerSubmitStart(), round.getLecturerSubmitEnd(),
                    round.getRegistrationStart(), round.getRegistrationEnd(),
                    round.getGvpbDeadline(), round.getReportDeadline(), round.getProposalPublishAt(),
                    round.getReportDate(), round.getDefenseDate(), round.getStatus(),
                    round.getCancelledFromStatus());
        }
    }
}
