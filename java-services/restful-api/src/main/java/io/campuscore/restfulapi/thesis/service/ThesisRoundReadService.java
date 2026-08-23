package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class ThesisRoundReadService {

    private final ThesisRegistrationRoundRepository rounds;

    public ThesisRoundReadService(ThesisRegistrationRoundRepository rounds) {
        this.rounds = rounds;
    }

    @Transactional(readOnly = true)
    public List<RoundResponse> list(RoundStatus status) {
        return (status == null
                ? rounds.findAllByOrderByRegistrationStartDesc()
                : rounds.findAllByStatusOrderByRegistrationStartDesc(status))
                .stream()
                .map(RoundResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoundResponse get(UUID id) {
        return rounds.findById(id)
                .map(RoundResponse::from)
                .orElseThrow(() -> new io.campuscore.restfulapi.web.DomainException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "ROUND_NOT_FOUND",
                        "Thesis registration round not found"));
    }
}
