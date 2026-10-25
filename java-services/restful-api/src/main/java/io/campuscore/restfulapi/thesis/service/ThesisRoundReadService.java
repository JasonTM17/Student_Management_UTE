package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.entity.ThesisRegistrationRound;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisDatabaseClockRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.model.EligibilityView;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundDeletionRepository;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundClosureInfo;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundDeletionInfo;
import io.campuscore.restfulapi.thesis.dto.ThesisRoundDtos.RoundResponse;
import java.time.Instant;
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
    private final ThesisGroupReadRepository groups;
    private final ThesisGovernanceCapabilities capabilities;
    private final ThesisDatabaseClockRepository databaseClock;

    /** Optional; round listing still works when the policy bean is absent. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ThesisEligibilityService eligibility;

    public ThesisRoundReadService(
            ThesisRegistrationRoundRepository rounds,
            ThesisRoundDeletionRepository deletions,
            ThesisGroupReadRepository groups,
            ThesisGovernanceCapabilities capabilities,
            ThesisDatabaseClockRepository databaseClock) {
        this.rounds = rounds;
        this.deletions = deletions;
        this.groups = groups;
        this.capabilities = capabilities;
        this.databaseClock = databaseClock;
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
     * registration eligibility — but only on the round types the credit/CPA
     * policy covers (default KLTN). On every other type the field stays null
     * so no card or badge renders for a rule that does not apply there.
     */
    @Transactional(readOnly = true)
    public List<RoundResponse> list(RoundStatus status, Jwt actor) {
        List<ThesisRegistrationRound> list = status == null
                ? rounds.findAllByStatusNotOrderByRegistrationStartDesc(RoundStatus.CLOSED)
                : rounds.findAllByStatusOrderByRegistrationStartDesc(status);
        // A draft is an unannounced schedule — only the roles that create and
        // manage rounds may see it; students and lecturers would otherwise
        // plan around windows the faculty has not committed to.
        if (!canSeeDrafts(actor)) {
            list = list.stream()
                    .filter(round -> round.getStatus() != RoundStatus.DRAFT)
                    .toList();
        }
        Map<UUID, Map<String, Number>> counts = deletions.dependentCounts(
                list.stream().map(ThesisRegistrationRound::getId).toList());
        List<UUID> cancelledRoundIds = list.stream()
                .filter(round -> round.getStatus() == RoundStatus.CANCELLED)
                .map(ThesisRegistrationRound::getId)
                .toList();
        Map<UUID, List<String>> restoreBlockers = hasRestoreRole(actor)
                ? groups.restoreBlockers(cancelledRoundIds)
                : Map.of();
        Instant serverNow = databaseNow();
        Instant lifecycleNow = Instant.now();
        String callerStudentId = callerStudentId(actor);
        // One transcript read per request, shared by every covered row —
        // the standing is per student, only the coverage flag differs.
        EligibilityView callerView = callerStudentId == null
                || list.stream().noneMatch(r -> eligibility.covers(r.getThesisType()))
                ? null : eligibility.evaluate(callerStudentId);
        return list.stream()
                .map(round -> RoundResponse.from(round, deletionInfo(round, counts), closureInfo(round, lifecycleNow),
                        capabilities.counterReviewWaiverAllowed(round.getThesisType()),
                        ThesisMutationSupport.MIN_GROUP_MEMBERS, ThesisMutationSupport.MAX_GROUP_MEMBERS,
                        callerView == null || !eligibility.covers(round.getThesisType())
                                ? null : callerView, serverNow, restoreBlockers.get(round.getId())))
                .toList();
    }

    /**
     * The roles {@link io.campuscore.restfulapi.thesis.controller.ThesisMutationController#createRound}
     * accepts are exactly the governance roles allowed to read drafts.
     */
    private static boolean canSeeDrafts(Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        if (roles == null) {
            return false;
        }
        return roles.contains("ADMIN") || roles.contains("SUPER_ADMIN") || roles.contains("TRUONG_KHOA");
    }

    private boolean hasRestoreRole(Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")
                || roles.contains("TRUONG_KHOA"));
    }

    /** Only a student principal carries a studentId claim worth evaluating. */
    private String callerStudentId(Jwt actor) {
        if (eligibility == null || actor == null) {
            return null;
        }
        String studentId = actor.getClaimAsString("studentId");
        return studentId == null || studentId.isBlank() ? null : studentId;
    }

    @Transactional(readOnly = true)
    public RoundResponse get(UUID id) {
        ThesisRegistrationRound round = rounds.findById(id)
                .orElseThrow(() -> new io.campuscore.restfulapi.exception.DomainException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "ROUND_NOT_FOUND",
                        "Thesis registration round not found"));
        Instant serverNow = databaseNow();
        return RoundResponse.from(round, deletionInfo(round, deletions.dependentCounts(List.of(id))),
                closureInfo(round, Instant.now()),
                capabilities.counterReviewWaiverAllowed(round.getThesisType()),
                ThesisMutationSupport.MIN_GROUP_MEMBERS, ThesisMutationSupport.MAX_GROUP_MEMBERS,
                null, serverNow);
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

    private RoundClosureInfo closureInfo(ThesisRegistrationRound round, Instant now) {
        return RoundClosureInfo.of(round.getStatus(), round.getResultsPublishedAt(),
                retractGraceDays, now);
    }

    private Instant databaseNow() {
        return databaseClock.now();
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
