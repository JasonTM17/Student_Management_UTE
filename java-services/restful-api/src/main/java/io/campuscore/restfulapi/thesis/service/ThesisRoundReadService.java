package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.entity.ThesisRegistrationRound;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundDeletionRepository;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundDeletionInfo;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class ThesisRoundReadService {

    private final ThesisRegistrationRoundRepository rounds;
    private final ThesisRoundDeletionRepository deletions;
    private final ThesisGovernanceCapabilities capabilities;

    public ThesisRoundReadService(
            ThesisRegistrationRoundRepository rounds,
            ThesisRoundDeletionRepository deletions,
            ThesisGovernanceCapabilities capabilities) {
        this.rounds = rounds;
        this.deletions = deletions;
        this.capabilities = capabilities;
    }

    @Transactional(readOnly = true)
    public List<RoundResponse> list(RoundStatus status) {
        List<ThesisRegistrationRound> list = status == null
                ? rounds.findAllByOrderByRegistrationStartDesc()
                : rounds.findAllByStatusOrderByRegistrationStartDesc(status);
        Map<UUID, Map<String, Number>> counts = deletions.dependentCounts(
                list.stream().map(ThesisRegistrationRound::getId).toList());
        return list.stream()
                .map(round -> RoundResponse.from(round, deletionInfo(round, counts),
                        capabilities.counterReviewWaiverAllowed(round.getThesisType())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoundResponse get(UUID id) {
        ThesisRegistrationRound round = rounds.findById(id)
                .orElseThrow(() -> new io.campuscore.restfulapi.exception.DomainException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "ROUND_NOT_FOUND",
                        "Thesis registration round not found"));
        return RoundResponse.from(round, deletionInfo(round, deletions.dependentCounts(List.of(id))),
                capabilities.counterReviewWaiverAllowed(round.getThesisType()));
    }

    /**
     * Per-kind dependent counts + status eligibility for one round. Used by the
     * round delete guard so the read model and the mutation agree on what
     * "empty" means.
     */
    @Transactional(readOnly = true)
    public RoundDeletionInfo deletionInfo(UUID roundId, RoundStatus status) {
        Map<String, Number> counts = deletions.dependentCounts(List.of(roundId)).get(roundId);
        if (counts == null) {
            return RoundDeletionInfo.of(status, 0, 0, 0, 0, 0);
        }
        return RoundDeletionInfo.of(status,
                counts.get("topics").longValue(),
                counts.get("groups").longValue(),
                counts.get("members").longValue(),
                counts.get("reports").longValue(),
                counts.get("councils").longValue());
    }

    private RoundDeletionInfo deletionInfo(
            ThesisRegistrationRound round, Map<UUID, Map<String, Number>> counts) {
        Map<String, Number> row = counts.get(round.getId());
        if (row == null) {
            return RoundDeletionInfo.of(round.getStatus(), 0, 0, 0, 0, 0);
        }
        return RoundDeletionInfo.of(round.getStatus(),
                row.get("topics").longValue(),
                row.get("groups").longValue(),
                row.get("members").longValue(),
                row.get("reports").longValue(),
                row.get("councils").longValue());
    }
}
