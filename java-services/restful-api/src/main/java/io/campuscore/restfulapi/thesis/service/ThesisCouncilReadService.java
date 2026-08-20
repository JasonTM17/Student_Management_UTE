package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.repository.ThesisCouncilReadRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.web.ThesisCouncilReadDtos.CouncilResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-read", name = "enabled", havingValue = "true")
public class ThesisCouncilReadService {

    private final ThesisCouncilReadRepository councils;
    private final ThesisRoundReadPort rounds;

    public ThesisCouncilReadService(ThesisCouncilReadRepository councils, ThesisRoundReadPort rounds) {
        this.councils = councils;
        this.rounds = rounds;
    }

    @Transactional(readOnly = true)
    public List<CouncilResponse> list(UUID roundId) {
        rounds.requireExisting(roundId);
        return councils.findByRoundId(roundId);
    }
}
