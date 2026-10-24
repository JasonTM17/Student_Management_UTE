package io.campuscore.restfulapi.audit.repository;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code campuscore_audit."AdminAudit"} insert behind {@code
 * AdminAuditRecorder}. Written with {@link Propagation#MANDATORY}: the row
 * must join the caller's transaction so a mutation and its audit row commit
 * or roll back together.
 */
@Repository
@Profile("persistence")
public class AdminAuditWriteRepository {

    private static final String AUDIT = "campuscore_audit.\"AdminAudit\"";

    private final NamedParameterJdbcTemplate jdbc;

    public AdminAuditWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(String actorId, String actorLabel, String action, String entityType,
            String entityId, String summary, String beforeState, String afterState) {
        jdbc.update(
                "INSERT INTO " + AUDIT
                        + " (\"id\", \"actorId\", \"actorLabel\", \"action\", \"entityType\", \"entityId\","
                        + " \"summary\", \"beforeState\", \"afterState\")"
                        + " VALUES (:id, :actorId, :actorLabel, :action, :entityType, :entityId,"
                        + " :summary, :beforeState, :afterState)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("actorId", actorId)
                        .addValue("actorLabel", actorLabel)
                        .addValue("action", action)
                        .addValue("entityType", entityType)
                        .addValue("entityId", entityId)
                        .addValue("summary", summary)
                        .addValue("beforeState", beforeState)
                        .addValue("afterState", afterState));
    }
}
