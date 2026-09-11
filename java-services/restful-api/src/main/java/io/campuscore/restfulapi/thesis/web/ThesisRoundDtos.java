package io.campuscore.restfulapi.thesis.web;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.ThesisRegistrationRound;
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
            Instant proposalPublishAt,
            Instant reportDate,
            RoundStatus status) {

        public static RoundResponse from(ThesisRegistrationRound round) {
            return new RoundResponse(
                    round.getId(), round.getName(), round.getThesisType(),
                    round.getLecturerSubmitStart(), round.getLecturerSubmitEnd(),
                    round.getRegistrationStart(), round.getRegistrationEnd(),
                    round.getGvpbDeadline(), round.getProposalPublishAt(),
                    round.getReportDate(), round.getStatus());
        }
    }
}
