package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.ThesisRegistrationRound;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundDeletionInfo;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class ThesisRoundReadService {

    /**
     * Every table that carries a direct {@code round_id} reference to
     * {@code thesis.thesis_registration_round}. Deeper rows (supervisors,
     * scores, council seats/assignments) hang off these parents, so an empty
     * result here proves the round owns no thesis data.
     */
    private static final String DEPENDENT_COUNT_SQL = """
            SELECT round_id,
                   COUNT(*) FILTER (WHERE kind = 'topic') AS topics,
                   COUNT(*) FILTER (WHERE kind = 'group') AS groups,
                   COUNT(*) FILTER (WHERE kind = 'member') AS members,
                   COUNT(*) FILTER (WHERE kind = 'report') AS reports,
                   COUNT(*) FILTER (WHERE kind = 'council') AS councils
            FROM (
                SELECT round_id, 'topic' AS kind FROM thesis.thesis_topic WHERE round_id IN (:ids)
                UNION ALL
                SELECT round_id, 'group' AS kind FROM thesis.thesis_group WHERE round_id IN (:ids)
                UNION ALL
                SELECT round_id, 'member' AS kind FROM thesis.thesis_group_member WHERE round_id IN (:ids)
                UNION ALL
                SELECT round_id, 'report' AS kind FROM thesis.thesis_group_report WHERE round_id IN (:ids)
                UNION ALL
                SELECT round_id, 'council' AS kind FROM thesis.thesis_council WHERE round_id IN (:ids)
            ) deps
            GROUP BY round_id
            """;

    private final ThesisRegistrationRoundRepository rounds;
    private final NamedParameterJdbcTemplate jdbc;
    private final ThesisGovernanceCapabilities capabilities;

    public ThesisRoundReadService(
            ThesisRegistrationRoundRepository rounds,
            NamedParameterJdbcTemplate jdbc,
            ThesisGovernanceCapabilities capabilities) {
        this.rounds = rounds;
        this.jdbc = jdbc;
        this.capabilities = capabilities;
    }

    @Transactional(readOnly = true)
    public List<RoundResponse> list(RoundStatus status) {
        List<ThesisRegistrationRound> list = status == null
                ? rounds.findAllByOrderByRegistrationStartDesc()
                : rounds.findAllByStatusOrderByRegistrationStartDesc(status);
        Map<UUID, Map<String, Number>> counts = dependentCounts(
                list.stream().map(ThesisRegistrationRound::getId).toList());
        return list.stream()
                .map(round -> RoundResponse.from(round, deletionInfo(round, counts),
                        capabilities.counterReviewWaiverAllowed(round.getThesisType())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoundResponse get(UUID id) {
        ThesisRegistrationRound round = rounds.findById(id)
                .orElseThrow(() -> new io.campuscore.restfulapi.web.DomainException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "ROUND_NOT_FOUND",
                        "Thesis registration round not found"));
        return RoundResponse.from(round, deletionInfo(round, dependentCounts(List.of(id))),
                capabilities.counterReviewWaiverAllowed(round.getThesisType()));
    }

    /**
     * Per-kind dependent counts + status eligibility for one round. Used by the
     * round delete guard so the read model and the mutation agree on what
     * "empty" means.
     */
    @Transactional(readOnly = true)
    public RoundDeletionInfo deletionInfo(UUID roundId, RoundStatus status) {
        Map<String, Number> counts = dependentCounts(List.of(roundId)).get(roundId);
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

    private Map<UUID, Map<String, Number>> dependentCounts(Collection<UUID> roundIds) {
        if (roundIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Number>> byRound = new HashMap<>();
        jdbc.query(DEPENDENT_COUNT_SQL, new MapSqlParameterSource("ids", roundIds), rs -> {
            Map<String, Number> counts = new HashMap<>();
            counts.put("topics", rs.getLong("topics"));
            counts.put("groups", rs.getLong("groups"));
            counts.put("members", rs.getLong("members"));
            counts.put("reports", rs.getLong("reports"));
            counts.put("councils", rs.getLong("councils"));
            byRound.put(rs.getObject("round_id", UUID.class), counts);
        });
        return byRound;
    }
}
