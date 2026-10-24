package io.campuscore.restfulapi.thesis.repository;

import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Personal thesis-progress evidence row for a student inside one round. */
@Repository
@Profile("persistence")
public class ThesisProgressRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisProgressRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> findProgressRow(UUID roundId, String studentId) {
        return jdbc.queryForMap("""
                SELECT r.status AS round_status,
                       g.id AS group_id,
                       g.status AS group_status,
                       g.approval_status,
                       g.legacy_group_size_exception,
                       t.id AS topic_id,
                       t.title AS topic_title,
                       rep.id AS report_id,
                       rep.submitted_at AS report_submitted_at,
                       ct.council_id,
                       t.final_score,
                       t.final_score_finalized_at,
                       t.result_status,
                       (SELECT COUNT(*) FROM thesis.thesis_group_member gm WHERE gm.group_id = g.id) AS member_count
                FROM thesis.thesis_registration_round r
                LEFT JOIN thesis.thesis_group g
                  ON g.round_id = r.id
                 AND EXISTS (
                     SELECT 1 FROM thesis.thesis_group_member mine
                     WHERE mine.group_id = g.id AND mine.student_id = :studentId
                 )
                LEFT JOIN thesis.thesis_topic t ON t.id = g.topic_id
                LEFT JOIN thesis.thesis_group_report rep ON rep.group_id = g.id
                LEFT JOIN thesis.thesis_council_topic ct ON ct.topic_id = t.id
                WHERE r.id = :roundId
                ORDER BY CASE WHEN g.status = 'CANCELLED' THEN 1 ELSE 0 END,
                         g.created_at DESC, g.id
                LIMIT 1
                """, params().addValue("roundId", roundId).addValue("studentId", studentId));
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }
}
