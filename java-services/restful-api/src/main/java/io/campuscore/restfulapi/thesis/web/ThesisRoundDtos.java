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
            Instant registrationStart,
            Instant registrationEnd,
            Instant proposalPublishAt,
            Instant reportDate,
            Instant defenseDate,
            RoundStatus status) {

        public static RoundResponse from(ThesisRegistrationRound round) {
            return new RoundResponse(
                    round.getId(), round.getName(), round.getThesisType(),
                    round.getRegistrationStart(), round.getRegistrationEnd(),
                    round.getProposalPublishAt(), round.getReportDate(),
                    round.getDefenseDate(), round.getStatus());
        }
    }
}
