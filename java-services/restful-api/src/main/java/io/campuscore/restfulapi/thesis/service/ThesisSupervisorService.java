package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Brief R3: every topic is supervised by one or two lecturers. The creator is
 * seeded as the first supervisor at topic creation; this service lets the
 * owner or a faculty head manage the (optional) second supervisor without
 * ever dropping below one.
 */
@Service
@Profile("persistence")
public class ThesisSupervisorService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ThesisTopicRepository topics;

    public ThesisSupervisorService(NamedParameterJdbcTemplate jdbc, ThesisTopicRepository topics) {
        this.jdbc = jdbc;
        this.topics = topics;
    }

    @Transactional(readOnly = true)
    public List<SupervisorRow> list(UUID topicId) {
        requireTopic(topicId);
        return jdbc.query(
                "SELECT lecturer_id, supervisor_order FROM thesis.thesis_topic_supervisor "
                        + "WHERE topic_id = :topicId ORDER BY supervisor_order",
                new MapSqlParameterSource().addValue("topicId", topicId),
                (rs, ignored) -> new SupervisorRow(rs.getString("lecturer_id"), rs.getInt("supervisor_order")));
    }

    @Transactional
    public List<SupervisorRow> setSupervisors(UUID topicId, List<String> lecturerIds, Jwt actor) {
        ThesisTopic topic = requireTopic(topicId);
        authorize(topic, actor);

        List<String> normalized = lecturerIds == null ? List.of() : lecturerIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty() || normalized.size() > 2) {
            throw invalid("A topic needs exactly one or two supervisors");
        }
        for (String lecturerId : normalized) {
            if (!lecturerExists(lecturerId)) {
                throw invalid("Unknown or inactive lecturer: " + lecturerId);
            }
        }

        // The creator may stay as a supervisor but never becomes the reason a
        // second identical row appears.
        // The delete-then-insert rewrite is safe under concurrency because
        // thesis_topic_supervisor_order_unique UNIQUE (topic_id, supervisor_order)
        // backstops duplicate orders: two racing writers both claim order 1 and
        // one insert aborts.
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId",
                new MapSqlParameterSource().addValue("topicId", topicId), Integer.class);
        if (existing != null && existing > 0
                && jdbc.update("DELETE FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId",
                        new MapSqlParameterSource().addValue("topicId", topicId)) == 0) {
            throw conflict("SUPERVISOR_STATE_CONFLICT", "Supervisors could not be updated");
        }
        int order = 1;
        for (String lecturerId : normalized) {
            jdbc.update(
                    "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                            + "VALUES (:id, :topicId, :lecturerId, :order)",
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("topicId", topicId)
                            .addValue("lecturerId", lecturerId)
                            .addValue("order", order++));
        }
        return list(topicId);
    }

    private ThesisTopic requireTopic(UUID topicId) {
        return topics.findById(topicId)
                .orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
    }

    private void authorize(ThesisTopic topic, Jwt actor) {
        if (hasRole(actor, "ADMIN") || hasRole(actor, "TRUONG_KHOA")) {
            return;
        }
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        boolean owner = StringUtils.hasText(topic.getCreatedBy())
                && (topic.getCreatedBy().equals(actorId) || (StringUtils.hasText(lecturerId) && topic.getCreatedBy().equals(lecturerId)));
        if (!owner) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_OWNER_REQUIRED", "Only the topic owner can change supervisors");
        }
    }

    private boolean lecturerExists(String lecturerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = :lecturerId AND \"isActive\" = TRUE",
                new MapSqlParameterSource().addValue("lecturerId", lecturerId), Integer.class);
        return count != null && count > 0;
    }

    private static boolean hasRole(Jwt actor, String role) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }

    private static String subject(Jwt actor) {
        return actor == null || actor.getSubject() == null ? "" : actor.getSubject();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    private static DomainException notFound(String code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }

    public record SupervisorRow(String lecturerId, int supervisorOrder) { }
}
