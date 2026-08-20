package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-read", name = "enabled", havingValue = "true")
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
}
