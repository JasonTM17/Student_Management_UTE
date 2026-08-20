package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-read", name = "enabled", havingValue = "true")
public class ThesisGroupReadService {
    private final ThesisGroupReadRepository groups; private final ThesisRoundReadPort rounds;
    public ThesisGroupReadService(ThesisGroupReadRepository groups, ThesisRoundReadPort rounds) { this.groups = groups; this.rounds = rounds; }
    @Transactional(readOnly = true) public List<GroupResponse> list(UUID roundId) { rounds.requireExisting(roundId); return groups.findByRoundId(roundId); }
    @Transactional(readOnly = true) public GroupResponse get(UUID id) { GroupResponse group = groups.findById(id); if (group == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thesis group not found"); return group; }
}
