package io.campuscore.restfulapi.thesis.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Round-deletion guard reads: per-kind counts of every row that references a
 * thesis round directly. The read model and the delete mutation share this
 * statement so both agree on what "empty" means.
 */
@Repository
@Profile("persistence")
public class ThesisRoundDeletionRepository {

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

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisRoundDeletionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Counts direct dependents per round, keyed by round id. Rounds with no
     * dependents simply have no entry — the callers read {@code null} as
     * "empty".
     */
    public Map<UUID, Map<String, Number>> dependentCounts(Collection<UUID> roundIds) {
        Map<UUID, Map<String, Number>> byRound = new HashMap<>();
        if (roundIds == null || roundIds.isEmpty()) {
            return byRound;
        }
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
