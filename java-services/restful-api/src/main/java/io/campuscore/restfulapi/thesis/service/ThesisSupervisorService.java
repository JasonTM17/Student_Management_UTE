package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.exception.DomainException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import io.campuscore.restfulapi.thesis.scope.ThesisDeanScope;
import io.campuscore.restfulapi.thesis.scope.ThesisDepartmentScope;

/**
 * Brief R3: every topic is supervised by one or two lecturers. The creator is
 * seeded as the first supervisor at topic creation; this service lets the
 * owner or a faculty head manage the (optional) second supervisor without
 * ever dropping below one.
 */
@Service
@Profile("persistence")
public class ThesisSupervisorService {

    private static final Logger log = LoggerFactory.getLogger(ThesisSupervisorService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ThesisTopicRepository topics;
    private final AdminAuditRecorder audit;
    private final ThesisNotificationService notifier;

    /** Compatibility constructor for tests that only exercise supervisor math. */
    public ThesisSupervisorService(NamedParameterJdbcTemplate jdbc, ThesisTopicRepository topics, AdminAuditRecorder audit) {
        this(jdbc, topics, audit, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ThesisSupervisorService(NamedParameterJdbcTemplate jdbc, ThesisTopicRepository topics,
            AdminAuditRecorder audit, ThesisNotificationService notifier) {
        this.jdbc = jdbc;
        this.topics = topics;
        this.audit = audit;
        this.notifier = notifier;
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

        // Round is locked before the topic so the order stays round -> topic,
        // the same direction the council mutations take. A cancelled, closed
        // or published round freezes the supervisor roster the same way a
        // council assignment does.
        lockMutableRound(topic.getRoundId());

        // Council assignment and supervisor replacement share this topic row
        // as their serialization point.  Without the lock, each transaction
        // could pass its precondition check against the other's uncommitted
        // state and create a supervisor/chair conflict.
        java.util.Map<String, Object> locked = lockTopic(topicId);

        // The early authorize ran on a read that can go stale: re-check
        // against the locked row so a head whose department the topic left in
        // a committed transfer cannot still rewrite its roster.
        authorize(topicId, String.valueOf(locked.get("created_by")),
                String.valueOf(locked.get("department_id")), actor);

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
            // A counter-reviewer's independence would be void if they also
            // supervised the topic; keep the invariant at write time instead of
            // only rejecting their score submission later.
            Integer reviewing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_topic WHERE id = :topicId AND gvpb_lecturer_id = :lecturerId",
                    new MapSqlParameterSource().addValue("topicId", topicId).addValue("lecturerId", lecturerId),
                    Integer.class);
            if (reviewing != null && reviewing > 0) {
                throw conflict("SUPERVISOR_CANNOT_REVIEW",
                        "The assigned counter-reviewer cannot supervise this topic");
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
        // Captured before the rewrite so only genuinely new supervisors are
        // notified — re-saving an unchanged roster must not re-ping anyone.
        List<String> previousSupervisorIds = jdbc.queryForList(
                "SELECT lecturer_id FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId",
                new MapSqlParameterSource("topicId", topicId), String.class);
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
        java.util.Map<String, Object> afterState = new java.util.LinkedHashMap<>();
        afterState.put("supervisorIds", normalized);
        audit.record(subject(actor), null, "TOPIC_SUPERVISORS_UPDATED", "THESIS_TOPIC", topicId.toString(),
                "Supervisors set to " + normalized + " for topic " + topicId, null, afterState);
        if (notifier != null) {
            List<String> added = normalized.stream()
                    .filter(id -> !previousSupervisorIds.contains(id))
                    .toList();
            if (!added.isEmpty()) {
                // Same after-commit rule as the mutation and council services:
                // the fan-out runs REQUIRES_NEW, so an in-transaction call
                // would still publish a notification for a roster a later
                // rollback erased.
                afterCommit(() -> {
                    try {
                        notifier.notifySupervisorsAssigned(topicId, added);
                    } catch (RuntimeException exception) {
                        log.warn("Thesis supervisor notification fan-out failed for topic {}", topicId, exception);
                    }
                });
            }
        }
        return list(topicId, actor);
    }

    /**
     * Runs the fan-out only after the roster write commits. Without an active
     * synchronization (e.g. a non-transactional caller) the action runs inline,
     * matching the convention in {@code ThesisMutationService}.
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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

    private boolean isVisible(ThesisTopic topic, Jwt actor) {
        return topic.getStatus() == TopicStatus.PUBLISHED
                || topic.getStatus() == TopicStatus.APPROVED
                || isStaff(actor)
                || ThesisDeanScope.canAccessTopic(jdbc, actor, topic.getId(), topic.getDepartmentId())
                || ThesisDepartmentScope.owns(jdbc, actor, topic.getDepartmentId())
                || isOwner(topic, actor);
    }

    private static boolean isStaff(Jwt actor) {
        return hasRole(actor, "ADMIN") || hasRole(actor, "SUPER_ADMIN");
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

    private boolean maySeeContactDetails(ThesisTopic topic, Jwt actor) {
        return isStaff(actor) || isOwner(topic, actor)
                || ThesisDeanScope.canAccessTopic(jdbc, actor, topic.getId(), topic.getDepartmentId())
                || ThesisDepartmentScope.owns(jdbc, actor, topic.getDepartmentId());
    }

    private void authorize(ThesisTopic topic, Jwt actor) {
        authorize(topic.getId(), topic.getCreatedBy(), topic.getDepartmentId(), actor);
    }

    /**
     * Same decision as {@link #authorize(ThesisTopic, Jwt)} but on explicit
     * values, so the post-lock re-check can run on the fresh row the FOR
     * UPDATE read returned instead of a possibly stale entity.
     */
    private void authorize(UUID topicId, String createdBy, String departmentId, Jwt actor) {
        if (hasRole(actor, "ADMIN") || hasRole(actor, "SUPER_ADMIN")) {
            return;
        }
        DomainException deanRefusal = null;
        if (ThesisDeanScope.isFacultyHead(actor)) {
            // Rewriting who supervises a topic is governance on the topic's
            // faculty: the supervisor escape requireTopicAccess keeps for
            // ordinary topic work would let a foreign dean who co-supervises
            // replace the home supervisor after registration closed.
            try {
                ThesisDeanScope.requireTopicGovernanceScope(jdbc, actor, topicId);
                return;
            } catch (DomainException refusal) {
                // Ownership is an ordinary right, not a governance grant:
                // the dean who created the topic keeps it after a transfer
                // took the topic out of their faculty, the same way the
                // identical account would pass as a plain lecturer. The
                // concealed refusal is rethrown when ownership is absent,
                // so a dean who merely co-supervises keeps the same answer.
                deanRefusal = refusal;
            }
        }
        if (deanRefusal == null && ThesisDepartmentScope.isDepartmentHead(actor)
                && ThesisDepartmentScope.owns(jdbc, actor, departmentId)) {
            return;
        }
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        boolean owner = StringUtils.hasText(createdBy)
                && (createdBy.equals(actorId) || (StringUtils.hasText(lecturerId) && createdBy.equals(lecturerId)));
        if (!owner) {
            throw deanRefusal != null ? deanRefusal
                    : new DomainException(HttpStatus.FORBIDDEN, "TOPIC_OWNER_REQUIRED", "Only the topic owner can change supervisors");
        }
    }

    private boolean lecturerExists(String lecturerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = :lecturerId AND \"isActive\" = TRUE",
                new MapSqlParameterSource().addValue("lecturerId", lecturerId), Integer.class);
        return count != null && count > 0;
    }

    private void lockMutableRound(UUID roundId) {
        String status;
        try {
            status = jdbc.queryForObject(
                    "SELECT status FROM thesis.thesis_registration_round WHERE id = :roundId FOR UPDATE",
                    new MapSqlParameterSource().addValue("roundId", roundId), String.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw notFound("ROUND_NOT_FOUND", "Thesis registration round not found");
        }
        if (RoundStatus.CANCELLED.name().equals(status)
                || RoundStatus.RESULTS_PUBLISHED.name().equals(status)
                || RoundStatus.CLOSED.name().equals(status)) {
            throw conflict("ROUND_STATE_CONFLICT",
                    "The round is already closed; topic supervisors can no longer change");
        }
    }

    private java.util.Map<String, Object> lockTopic(UUID topicId) {
        try {
            return jdbc.queryForMap(
                    "SELECT created_by, department_id FROM thesis.thesis_topic WHERE id = :topicId FOR UPDATE",
                    new MapSqlParameterSource().addValue("topicId", topicId));
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
