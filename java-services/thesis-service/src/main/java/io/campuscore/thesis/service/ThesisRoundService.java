package io.campuscore.thesis.service;

import io.campuscore.thesis.domain.RoundStatus;
import io.campuscore.thesis.domain.ThesisRegistrationRound;
import io.campuscore.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.thesis.web.DomainExceptions;
import io.campuscore.thesis.web.ThesisDtos.CreateRoundRequest;
import io.campuscore.thesis.web.ThesisDtos.RoundResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThesisRoundService {

    private final ThesisRegistrationRoundRepository rounds;

    public ThesisRoundService(ThesisRegistrationRoundRepository rounds) {
        this.rounds = rounds;
    }

    @Transactional
    public RoundResponse create(CreateRoundRequest request) {
        if (!request.registrationEnd().isAfter(request.registrationStart())) {
            throw new DomainExceptions.Conflict("Registration end must be after registration start");
        }
        return RoundResponse.from(rounds.save(new ThesisRegistrationRound(
                request.name().trim(),
                request.thesisType().trim(),
                request.registrationStart(),
                request.registrationEnd(),
                request.proposalPublishAt(),
                request.reportDate(),
                request.defenseDate())));
    }

    @Transactional(readOnly = true)
    public List<RoundResponse> list(RoundStatus status) {
        List<ThesisRegistrationRound> result = status == null
                ? rounds.findAllByOrderByRegistrationStartDesc()
                : rounds.findAllByStatusOrderByRegistrationStartDesc(status);
        return result.stream().map(RoundResponse::from).toList();
    }

    @Transactional
    public RoundResponse openRegistration(UUID id) {
        ThesisRegistrationRound round = get(id);
        if (Instant.now().isBefore(round.getRegistrationStart())) {
            throw new DomainExceptions.Conflict("Registration cannot open before its configured start");
        }
        round.openRegistration();
        return RoundResponse.from(rounds.save(round));
    }

    @Transactional
    public RoundResponse closeRegistration(UUID id) {
        ThesisRegistrationRound round = get(id);
        round.closeRegistration();
        return RoundResponse.from(rounds.save(round));
    }

    @Transactional
    public RoundResponse publishProposals(UUID id) {
        ThesisRegistrationRound round = get(id);
        round.publishProposals();
        return RoundResponse.from(rounds.save(round));
    }

    @Transactional(readOnly = true)
    public ThesisRegistrationRound get(UUID id) {
        return rounds.findById(id)
                .orElseThrow(() -> new DomainExceptions.NotFound("Thesis registration round not found"));
    }
}
