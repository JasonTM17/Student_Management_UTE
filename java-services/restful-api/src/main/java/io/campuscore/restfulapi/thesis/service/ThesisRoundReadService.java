package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.entity.ThesisRegistrationRound;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.model.EligibilityView;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundDeletionRepository;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundClosureInfo;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundDeletionInfo;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class ThesisRoundReadService {

    /**
     * Retract-publication grace window in days — the same property the
     * mutation service enforces, needed here so {@code closeAllowed} agrees
     * with what the close endpoint would answer.
     */
    @org.springframework.beans.factory.annotation.Value("${thesis.results.retract-grace-days:7}")
    private int retractGraceDays;

    private final ThesisRegistrationRoundRepository rounds;
    private final ThesisRoundDeletionRepository deletions;
    private final ThesisGovernanceCapabilities capabilities;

    /** Optional; round listing still works when the policy bean is absent. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ThesisEligibilityService eligibility;

    public ThesisRoundReadService(
            ThesisRegistrationRoundRepository rounds,
            ThesisRoundDeletionRepository deletions,
            ThesisGovernanceCapabilities capabilities) {
        this.rounds = rounds;
        this.deletions = deletions;
        this.capabilities = capabilities;
    }

    /**
     * The unfiltered call is the "active" list: archived CLOSED rounds stay
     * out of every role's default picker while remaining reachable through
     * the explicit {@code status=CLOSED} query the admin filter uses.
     */
    @Transactional(readOnly = true)
    public List<RoundResponse> list(RoundStatus status) {
        return list(status, null);
    }

    /**
     * Same round list; a student caller additionally receives their own
     * registration eligibility on every row so the thesis page renders the
     * eligibility card without a second request.
     */
    @Transactional(readOnly = true)
    public List<RoundResponse> list(RoundStatus status, Jwt actor) {
        List<ThesisRegistrationRound> list = status == null
                ? rounds.findAllByStatusNotOrderByRegistrationStartDesc(RoundStatus.CLOSED)
                : rounds.findAllByStatusOrderByRegistrationStartDesc(status);
        Map<UUID, Map<String, Number>> counts = deletions.dependentCounts(
                list.stream().map(ThesisRegistrationRound::getId).toList());
        EligibilityView callerEligibility = callerEligibility(actor);
        return list.stream()
                .map(round -> RoundResponse.from(round, deletionInfo(round, counts), closureInfo(round),
                        capabilities.counterReviewWaiverAllowed(round.getThesisType()),
                        ThesisMutationService.MIN_GROUP_MEMBERS, ThesisMutationService.MAX_GROUP_MEMBERS,
                        callerEligibility))
                .toList();
    }

    /** One evaluation per request; only a student principal gets a view. */
    private EligibilityView callerEligibility(Jwt actor) {
        if (eligibility == null || actor == null) {
            return null;
        }
        String studentId = actor.getClaimAsString("studentId");
        if (studentId == null || studentId.isBlank()) {
            return null;
        }
        return eligibility.evaluate(studentId);
    }

    @Transactional(readOnly = true)
    public RoundResponse get(UUID id) {
        ThesisRegistrationRound round = rounds.findById(id)
                .orElseThrow(() -> new io.campuscore.restfulapi.exception.DomainException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "ROUND_NOT_FOUND",
                        "Thesis registration round not found"));
        return RoundResponse.from(round, deletionInfo(round, deletions.dependentCounts(List.of(id))),
                closureInfo(round),
                capabilities.counterReviewWaiverAllowed(round.getThesisType()),
                ThesisMutationService.MIN_GROUP_MEMBERS, ThesisMutationService.MAX_GROUP_MEMBERS);
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

    private RoundClosureInfo closureInfo(ThesisRegistrationRound round) {
        return RoundClosureInfo.of(round.getStatus(), round.getResultsPublishedAt(),
                retractGraceDays, java.time.Instant.now());
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
