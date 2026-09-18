package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
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

    /**
     * Reads the supervisor list. Visibility follows the same rule as
     * {@code ThesisTopicService.get}: a hidden (non-published, non-approved)
     * topic is a 404 for everyone except its owner and staff, so the endpoint
     * cannot be used to enumerate drafts. Supervisors' email addresses are
     * staff/owner-only — students receive the display name they need for their
     * group view and nothing more.
     */
    @Transactional(readOnly = true)
    public List<SupervisorRow> list(UUID topicId, Jwt actor) {
        ThesisTopic topic = requireVisible(topicId, actor);
        boolean maySeeContactDetails = maySeeContactDetails(topic, actor);
        // The name travels with the row because the lecturer directory endpoint
        // is closed to students, which left their group view showing the raw
        // lecturer id instead of the supervisor's name.
        return jdbc.query(
                "SELECT supervisor.lecturer_id, supervisor.supervisor_order,"
                        + " user_account.\"firstName\" AS first_name,"
                        + " user_account.\"lastName\" AS last_name,"
                        + " user_account.\"email\" AS email"
                        + " FROM thesis.thesis_topic_supervisor supervisor"
                        + " LEFT JOIN campuscore_auth.\"Lecturer\" lecturer ON lecturer.\"id\" = supervisor.lecturer_id"
                        + " LEFT JOIN campuscore_auth.\"User\" user_account ON user_account.\"id\" = lecturer.\"userId\""
                        + " WHERE supervisor.topic_id = :topicId ORDER BY supervisor.supervisor_order",
                new MapSqlParameterSource().addValue("topicId", topicId),
                (rs, ignored) -> new SupervisorRow(
                        rs.getString("lecturer_id"),
                        rs.getInt("supervisor_order"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        maySeeContactDetails ? rs.getString("email") : null));
    }

    @Transactional
    public List<SupervisorRow> setSupervisors(UUID topicId, List<String> lecturerIds, Jwt actor) {
        ThesisTopic topic = requireTopic(topicId);
        authorize(topic, actor);

        // Council assignment and supervisor replacement share this topic row
        // as their serialization point.  Without the lock, each transaction
        // could pass its precondition check against the other's uncommitted
        // state and create a supervisor/chair conflict.
        lockTopic(topicId);

        Integer assignedTopics = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE topic_id = :topicId",
                new MapSqlParameterSource().addValue("topicId", topicId), Integer.class);
        Integer submittedScores = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = :topicId",
                new MapSqlParameterSource().addValue("topicId", topicId), Integer.class);
        if ((assignedTopics != null && assignedTopics > 0)
                || (submittedScores != null && submittedScores > 0)) {
            throw conflict("SUPERVISOR_STATE_CONFLICT",
                    "Supervisors cannot change after a council is assigned or scoring has started");
        }

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
            Integer chairCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                            + "JOIN thesis.thesis_council_member cm ON cm.council_id = ct.council_id "
                            + "WHERE ct.topic_id = :topicId AND cm.lecturer_id = :lecturerId AND cm.member_role = 'CHAIR'",
                    new MapSqlParameterSource().addValue("topicId", topicId).addValue("lecturerId", lecturerId),
                    Integer.class);
            if (chairCount != null && chairCount > 0) {
                throw conflict("SUPERVISOR_CANNOT_CHAIR_COUNCIL",
                        "The topic supervisor cannot chair the defense council for this topic");
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
        return list(topicId, actor);
    }

    private ThesisTopic requireTopic(UUID topicId) {
        return topics.findById(topicId)
                .orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
    }

    /**
     * Mirrors {@code ThesisTopicService.get}: a topic that is neither PUBLISHED
     * nor APPROVED is invisible (404, not 403 — a 403 would confirm the id
     * exists) to anyone who is not staff or its owner.
     */
    private ThesisTopic requireVisible(UUID topicId, Jwt actor) {
        ThesisTopic topic = requireTopic(topicId);
        if (isVisible(topic, actor)) {
            return topic;
        }
        throw notFound("TOPIC_NOT_FOUND", "Thesis topic not found");
    }

    private static boolean isVisible(ThesisTopic topic, Jwt actor) {
        return topic.getStatus() == TopicStatus.PUBLISHED
                || topic.getStatus() == TopicStatus.APPROVED
                || isStaff(actor)
                || isOwner(topic, actor);
    }

    private static boolean isStaff(Jwt actor) {
        return hasRole(actor, "ADMIN") || hasRole(actor, "TRUONG_KHOA");
    }

    private static boolean isOwner(ThesisTopic topic, Jwt actor) {
        String actorId = subject(actor);
        if (!StringUtils.hasText(actorId) || !StringUtils.hasText(topic.getCreatedBy())) {
            return false;
        }
        if (topic.getCreatedBy().equals(actorId)) {
            return true;
        }
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        return StringUtils.hasText(lecturerId) && topic.getCreatedBy().equals(lecturerId);
    }

    private static boolean maySeeContactDetails(ThesisTopic topic, Jwt actor) {
        return isStaff(actor) || isOwner(topic, actor);
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

    private void lockTopic(UUID topicId) {
        try {
            jdbc.queryForObject(
                    "SELECT id FROM thesis.thesis_topic WHERE id = :topicId FOR UPDATE",
                    new MapSqlParameterSource().addValue("topicId", topicId), UUID.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw notFound("TOPIC_NOT_FOUND", "Thesis topic not found");
        }
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

    /** `firstName`/`lastName`/`email` are null when the lecturer has no directory row. */
    public record SupervisorRow(
            String lecturerId,
            int supervisorOrder,
            String firstName,
            String lastName,
            String email) { }
}
