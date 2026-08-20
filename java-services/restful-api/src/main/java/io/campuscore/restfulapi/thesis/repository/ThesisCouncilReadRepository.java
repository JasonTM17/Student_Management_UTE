package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.thesis.domain.CouncilMemberRole;
import io.campuscore.restfulapi.thesis.domain.CouncilStatus;
import io.campuscore.restfulapi.thesis.web.ThesisCouncilReadDtos.CouncilMemberResponse;
import io.campuscore.restfulapi.thesis.web.ThesisCouncilReadDtos.CouncilResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC-only read adapter: this candidate never owns or mutates legacy councils. */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.thesis-read", name = "enabled", havingValue = "true")
public class ThesisCouncilReadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisCouncilReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CouncilResponse> findByRoundId(UUID roundId) {
        List<CouncilRow> rows = jdbc.query(
                "SELECT id, round_id, department_id, scheduled_at, room, status "
                        + "FROM thesis.thesis_defense_council "
                        + "WHERE round_id = :roundId ORDER BY scheduled_at ASC NULLS LAST",
                new MapSqlParameterSource("roundId", roundId),
                ThesisCouncilReadRepository::row);
        return hydrate(rows);
    }

    private List<CouncilResponse> hydrate(List<CouncilRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<CouncilMemberResponse>> members = new LinkedHashMap<>();
        for (CouncilRow row : rows) {
            members.put(row.id(), new ArrayList<>());
        }
        jdbc.query(
                "SELECT council_id, lecturer_id, member_role, member_order "
                        + "FROM thesis.thesis_council_member "
                        + "WHERE council_id IN (:ids) ORDER BY council_id, member_order",
                new MapSqlParameterSource("ids", members.keySet()),
                (RowCallbackHandler) rs -> members.get(rs.getObject("council_id", UUID.class)).add(member(rs)));

        return rows.stream()
                .map(row -> new CouncilResponse(
                        row.id(), row.roundId(), row.departmentId(), row.scheduledAt(), row.room(), row.status(),
                        List.copyOf(members.get(row.id()))))
                .toList();
    }

    private static CouncilRow row(ResultSet rs, int ignored) throws SQLException {
        Timestamp scheduledAt = rs.getTimestamp("scheduled_at");
        return new CouncilRow(
                rs.getObject("id", UUID.class),
                rs.getObject("round_id", UUID.class),
                rs.getObject("department_id", UUID.class),
                scheduledAt == null ? null : scheduledAt.toInstant(),
                rs.getString("room"),
                CouncilStatus.valueOf(rs.getString("status")));
    }

    private static CouncilMemberResponse member(ResultSet rs) throws SQLException {
        return new CouncilMemberResponse(
                rs.getObject("lecturer_id", UUID.class),
                CouncilMemberRole.valueOf(rs.getString("member_role")),
                rs.getInt("member_order"));
    }

    private record CouncilRow(
            UUID id,
            UUID roundId,
            UUID departmentId,
            Instant scheduledAt,
            String room,
            CouncilStatus status) { }
}
