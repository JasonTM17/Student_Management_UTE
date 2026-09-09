package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.thesis.domain.ApprovalStatus;
import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.thesis.repository.ThesisGroupReadRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRegistrationRoundRepository;
import io.campuscore.restfulapi.thesis.repository.ThesisRoundReadPort;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import io.campuscore.restfulapi.thesis.web.ThesisGroupReadDtos.GroupResponse;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.MemberRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RoundCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.StudentSearchResponse;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicAssignmentRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.thesis.web.ThesisRoundDtos.RoundResponse;
import io.campuscore.restfulapi.thesis.web.ThesisTopicDtos.TopicResponse;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for the thesis core: rounds, topics and student groups. */
@Service
@Profile("persistence")
public class ThesisMutationService {

    private static final int MAX_GROUP_MEMBERS = 3;

    private final NamedParameterJdbcTemplate jdbc;
    private final ThesisRegistrationRoundRepository rounds;
    private final ThesisRoundReadPort roundReadPort;
    private final ThesisTopicRepository topics;
    private final ThesisGroupReadRepository groups;
    private final ThesisRoundReadService roundReads;

    public ThesisMutationService(
            NamedParameterJdbcTemplate jdbc,
            ThesisRegistrationRoundRepository rounds,
            ThesisRoundReadPort roundReadPort,
            ThesisTopicRepository topics,
            ThesisGroupReadRepository groups,
            ThesisRoundReadService roundReads) {
        this.jdbc = jdbc;
        this.rounds = rounds;
        this.roundReadPort = roundReadPort;
        this.topics = topics;
        this.groups = groups;
        this.roundReads = roundReads;
    }

    @Transactional
    public RoundResponse createRound(RoundCreateRequest request) {
        requireText(request == null ? null : request.name(), "name");
        requireText(request == null ? null : request.thesisType(), "thesisType");
        requireDates(request == null ? null : request.registrationStart(), request == null ? null : request.registrationEnd());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO thesis.thesis_registration_round
                    (id, name, thesis_type, registration_start, registration_end,
                     proposal_publish_at, report_date, status)
                VALUES (:id, :name, :thesisType, :registrationStart, :registrationEnd,
                        :proposalPublishAt, :reportDate, 'DRAFT')
                """, params()
                .addValue("id", id)
                .addValue("name", request.name().trim())
                .addValue("thesisType", request.thesisType().trim())
                .addValue("registrationStart", request.registrationStart())
                .addValue("registrationEnd", request.registrationEnd())
                .addValue("proposalPublishAt", request.proposalPublishAt())
                .addValue("reportDate", request.reportDate()));
        return roundReads.get(id);
    }

    @Transactional
    public RoundResponse transitionRound(UUID id, RoundStatus expected, RoundStatus next) {
        roundReadPort.requireExisting(id);
        int updated = jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = :next, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id AND status = :expected",
                params().addValue("id", id).addValue("expected", expected.name()).addValue("next", next.name()));
        if (updated != 1) {
            throw conflict("ROUND_STATE_CONFLICT", "Round must be in " + expected.name() + " before it can become " + next.name());
        }
        return roundReads.get(id);
    }

    @Transactional
    public TopicResponse createTopic(TopicCreateRequest request, Jwt actor) {
        requireText(request == null ? null : request.departmentId(), "departmentId");
        requireText(request == null ? null : request.title(), "title");
        requireText(request == null ? null : request.description(), "description");
        int maxGroups = request.maxGroups() == null ? 1 : request.maxGroups();
        if (maxGroups < 1 || maxGroups > 20) {
            throw invalid("maxGroups must be between 1 and 20");
        }
        roundReadPort.requireExisting(request.roundId());
        String actorId = subject(actor);
        ThesisTopic topic = topics.saveAndFlush(new ThesisTopic(
                request.roundId(),
                request.departmentId().trim(),
                request.title().trim(),
                request.description().trim(),
                maxGroups,
                actorId));
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (isLecturer(actor) || !lecturerId.isBlank()) {
            String supervisorId = !lecturerId.isBlank() ? lecturerId : actorId;
            if (!supervisorId.isBlank()) {
                Integer existingSupervisor = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND (lecturer_id = :supervisorId OR supervisor_order = 1)",
                        params().addValue("topicId", topic.getId()).addValue("supervisorId", supervisorId),
                        Integer.class);
                if (existingSupervisor == null || existingSupervisor == 0) {
                    jdbc.update(
                            "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) VALUES (:id, :topicId, :lecturerId, 1)",
                            params().addValue("id", UUID.randomUUID())
                                    .addValue("topicId", topic.getId())
                                    .addValue("lecturerId", supervisorId));
                }
            }
        }
        return TopicResponse.from(topic);
    }

    @Transactional
    public TopicResponse updateTopic(UUID id, TopicUpdateRequest request, Jwt actor) {
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
        authorizeTopicOwner(topic, actor);
        if (topic.getStatus() != TopicStatus.DRAFT) {
            throw conflict("TOPIC_STATE_CONFLICT", "Only draft topics can be edited");
        }
        requireText(request == null ? null : request.departmentId(), "departmentId");
        requireText(request == null ? null : request.title(), "title");
        requireText(request == null ? null : request.description(), "description");
        int maxGroups = request.maxGroups() == null ? topic.getMaxGroups() : request.maxGroups();
        if (maxGroups < 1 || maxGroups > 20) {
            throw invalid("maxGroups must be between 1 and 20");
        }
        topic.update(request.departmentId().trim(), request.title().trim(), request.description().trim(), maxGroups);
        return TopicResponse.from(topics.save(topic));
    }

    @Transactional
    public TopicResponse publishTopic(UUID id, Jwt actor) {
        ThesisTopic topic = topics.findById(id).orElseThrow(() -> notFound("TOPIC_NOT_FOUND", "Thesis topic not found"));
        authorizeTopicOwner(topic, actor);
        try {
            topic.publish();
        } catch (IllegalStateException exception) {
            throw conflict("TOPIC_STATE_CONFLICT", exception.getMessage());
        }
        return TopicResponse.from(topics.save(topic));
    }

    @Transactional
    public GroupResponse createGroup(GroupCreateRequest request, Jwt actor) {
        UUID roundId = request == null ? null : request.roundId();
        if (roundId == null) {
            throw invalid("roundId is required");
        }
        requireRoundStatus(roundId, RoundStatus.REGISTRATION_OPEN);
        String studentId = studentId(actor);
        requireActiveStudent(studentId);
        if (count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE round_id = :roundId AND student_id = :studentId", roundId, studentId) > 0) {
            throw conflict("STUDENT_ALREADY_IN_GROUP", "Student already belongs to a group in this round");
        }
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) VALUES (:id, :roundId, :studentId, 'DRAFT', 'PENDING')", params().addValue("id", groupId).addValue("roundId", roundId).addValue("studentId", studentId));
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, 1, TRUE)", params().addValue("id", UUID.randomUUID()).addValue("groupId", groupId).addValue("roundId", roundId).addValue("studentId", studentId));
        return groups.findById(groupId);
    }

    @Transactional
    public GroupResponse addMember(UUID groupId, MemberRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeLeaderOrAdmin(group, actor);
        requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        requireMutableMembership(group, actor);
        if (count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId", groupId) >= MAX_GROUP_MEMBERS) {
            throw conflict("GROUP_FULL", "A thesis group can have at most three members");
        }
        String studentId = normalize(request == null ? null : request.studentId());
        if (studentId.isBlank()) {
            addExternalMember(group, request);
            return groups.findById(groupId);
        }
        requireActiveStudent(studentId);
        if (count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE round_id = :roundId AND student_id = :studentId", group.roundId(), studentId) > 0) {
            throw conflict("STUDENT_ALREADY_IN_GROUP", "Student already belongs to a group in this round");
        }
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, :memberOrder, FALSE)", params().addValue("id", UUID.randomUUID()).addValue("groupId", groupId).addValue("roundId", group.roundId()).addValue("studentId", studentId).addValue("memberOrder", count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId", groupId) + 1));
        return groups.findById(groupId);
    }

    /**
     * Members from a different department or school have no student profile to
     * reference; their declared identity is stored on the membership row so the
     * reviewer can verify it, and a synthetic student_id keeps the round's
     * one-group-per-member uniqueness intact.
     */
    private void addExternalMember(GroupRow group, MemberRequest request) {
        String displayName = normalize(request == null ? null : request.displayName());
        String contact = normalize(request == null ? null : request.contact());
        if (displayName.isBlank() || displayName.length() > 150) {
            throw invalid("displayName is required for members without a student profile (max 150 characters)");
        }
        if (contact.length() > 150) {
            throw invalid("contact must contain at most 150 characters");
        }
        String syntheticId = "external-" + UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader, display_name, contact, is_external) VALUES (:id, :groupId, :roundId, :studentId, :memberOrder, FALSE, :displayName, :contact, TRUE)",
                params()
                        .addValue("id", UUID.randomUUID())
                        .addValue("groupId", group.id())
                        .addValue("roundId", group.roundId())
                        .addValue("studentId", syntheticId)
                        .addValue("memberOrder", count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId", group.id()) + 1)
                        .addValue("displayName", displayName)
                        .addValue("contact", contact.isBlank() ? null : contact));
    }

    @Transactional
    public GroupResponse removeMember(UUID groupId, String studentId, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeLeaderOrAdmin(group, actor);
        requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        requireMutableMembership(group, actor);
        String normalized = normalize(studentId);
        if (group.leaderStudentId().equals(normalized)) {
            throw conflict("LEADER_CANNOT_BE_REMOVED", "The group leader cannot be removed");
        }
        if (jdbc.update("DELETE FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId", params().addValue("groupId", groupId).addValue("studentId", normalized)) != 1) {
            throw notFound("MEMBER_NOT_FOUND", "Group member not found");
        }
        return groups.findById(groupId);
    }

    @Transactional
    public GroupResponse assignTopic(UUID groupId, TopicAssignmentRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeLeaderOrAdmin(group, actor);
        requireRoundStatus(group.roundId(), RoundStatus.REGISTRATION_OPEN);
        UUID topicId = request == null ? null : request.topicId();
        if (topicId == null) {
            throw invalid("topicId is required");
        }
        if (!isAdmin(actor) && group.approvalStatus() == ApprovalStatus.APPROVED && !topicId.equals(group.topicId())) {
            throw conflict("GROUP_STATE_CONFLICT", "An approved group cannot change its topic");
        }
        Map<String, Object> topic = one("SELECT id, round_id, status, max_groups FROM thesis.thesis_topic WHERE id = :id FOR UPDATE", params().addValue("id", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (!group.roundId().equals(topic.get("round_id"))) {
            throw conflict("TOPIC_ROUND_MISMATCH", "Topic belongs to another registration round");
        }
        if (!TopicStatus.PUBLISHED.name().equals(topic.get("status"))) {
            throw conflict("TOPIC_NOT_PUBLISHED", "Only published topics can be selected");
        }
        int maxGroups = ((Number) topic.get("max_groups")).intValue();
        boolean alreadyOccupiesSlot = topicId.equals(group.topicId()) && group.approvalStatus() != ApprovalStatus.REJECTED;
        if (count("SELECT COUNT(*) FROM thesis.thesis_group WHERE topic_id = :topicId AND status <> 'CANCELLED' AND approval_status <> 'REJECTED'", topicId) >= maxGroups && !alreadyOccupiesSlot) {
            throw conflict("TOPIC_FULL", "This topic has reached its group limit");
        }
        boolean isNewTopic = !topicId.equals(group.topicId());
        jdbc.update("UPDATE thesis.thesis_group SET topic_id = :topicId, status = CASE WHEN status = 'DRAFT' THEN 'SUBMITTED' ELSE status END, approval_status = CASE WHEN approval_status = 'REJECTED' OR :isNewTopic THEN 'PENDING' ELSE approval_status END, approved_by = CASE WHEN :isNewTopic THEN NULL ELSE approved_by END, approved_at = CASE WHEN :isNewTopic THEN NULL ELSE approved_at END, rejection_reason = CASE WHEN approval_status = 'REJECTED' OR :isNewTopic THEN NULL ELSE rejection_reason END, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId",
                params().addValue("topicId", topicId).addValue("groupId", groupId).addValue("isNewTopic", isNewTopic));
        return groups.findById(groupId);
    }

    @Transactional
    public GroupResponse updateProgress(UUID groupId, ProgressRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeLeaderOrAdmin(group, actor);
        GroupStatus status = request == null ? null : request.status();
        if (status == null) {
            throw invalid("status is required");
        }
        // APPROVED/REJECTED are approval semantics owned by the reviewer flow;
        // nobody sets them through progress updates.
        if (!isProgressStatus(status)) {
            throw invalid("Progress accepts only DRAFT, SUBMITTED, COMPLETED or CANCELLED");
        }
        // approveGroup flips approval_status while the status stays SUBMITTED, so an
        // approved group must not be demoted or cancelled behind the reviewer's back.
        if (group.approvalStatus() == ApprovalStatus.APPROVED
                && status != GroupStatus.COMPLETED
                && status != group.status()) {
            throw conflict("GROUP_STATUS_INVALID", "An approved group can only be marked COMPLETED");
        }
        if (!isAdmin(actor) && group.status() == GroupStatus.COMPLETED && status != GroupStatus.COMPLETED) {
            throw conflict("GROUP_STATUS_INVALID", "A completed group cannot reopen; contact your supervisor");
        }
        jdbc.update("UPDATE thesis.thesis_group SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId", params().addValue("status", status.name()).addValue("groupId", groupId));
        return groups.findById(groupId);
    }

    @Transactional
    public GroupResponse approveGroup(UUID groupId, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeReviewer(group, actor);
        if (group.status() != GroupStatus.SUBMITTED || group.topicId() == null) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only a submitted group with a topic can be approved");
        }
        int changed = jdbc.update("UPDATE thesis.thesis_group SET approval_status='APPROVED', approved_by=:actor, approved_at=CURRENT_TIMESTAMP, rejection_reason=NULL, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                params().addValue("id", groupId).addValue("actor", subject(actor)));
        if (changed != 1) throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only pending groups can be approved");
        return groups.findById(groupId);
    }

    @Transactional
    public GroupResponse rejectGroup(UUID groupId, GroupRejectionRequest request, Jwt actor) {
        GroupRow group = lockGroup(groupId);
        authorizeReviewer(group, actor);
        if (group.status() != GroupStatus.SUBMITTED || group.topicId() == null) {
            throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only a submitted group with a topic can be rejected");
        }
        String reason = normalize(request == null ? null : request.reason());
        if (reason.isBlank() || reason.length() > 500) throw invalid("reason is required and must contain at most 500 characters");
        int changed = jdbc.update("UPDATE thesis.thesis_group SET approval_status='REJECTED', approved_by=NULL, approved_at=NULL, rejection_reason=:reason, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                params().addValue("id", groupId).addValue("reason", reason));
        if (changed != 1) throw conflict("GROUP_APPROVAL_STATE_CONFLICT", "Only pending groups can be rejected");
        return groups.findById(groupId);
    }

    /**
     * Directory lookup used by group leaders to invite classmates: matches
     * active students by student number, email or full name (max 8 hits).
     */
    public List<StudentSearchResponse> searchStudents(String query) {
        String normalized = normalize(query).toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() < 2) {
            return List.of();
        }
        String pattern = "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query(
                "SELECT s.\"id\", s.\"studentId\" AS student_number, u.\"email\", u.\"firstName\", u.\"lastName\","
                        + " cur.\"code\" AS curriculum_code, cur.\"name\" AS curriculum_name"
                        + " FROM campuscore_auth.\"Student\" s"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\""
                        + " LEFT JOIN academic.\"Curriculum\" cur ON cur.\"id\" = s.\"curriculumId\""
                        + " WHERE s.\"status\" = 'ACTIVE'"
                        + " AND (LOWER(s.\"studentId\") LIKE :pattern ESCAPE '\\'"
                        + "   OR LOWER(u.\"email\") LIKE :pattern ESCAPE '\\'"
                        + "   OR LOWER(u.\"firstName\" || ' ' || u.\"lastName\") LIKE :pattern ESCAPE '\\')"
                        + " ORDER BY s.\"studentId\" LIMIT 8",
                params().addValue("pattern", pattern),
                (rs, ignored) -> new StudentSearchResponse(
                        rs.getString("id"),
                        rs.getString("student_number"),
                        rs.getString("email"),
                        rs.getString("firstName"),
                        rs.getString("lastName"),
                        rs.getString("curriculum_code"),
                        rs.getString("curriculum_name")));
    }

    private GroupRow lockGroup(UUID id) {
        Map<String, Object> row = one("SELECT id, round_id, leader_student_id, topic_id, status, approval_status FROM thesis.thesis_group WHERE id = :id FOR UPDATE", params().addValue("id", id), "GROUP_NOT_FOUND", "Thesis group not found");
        return new GroupRow((UUID) row.get("id"), (UUID) row.get("round_id"), (String) row.get("leader_student_id"), (UUID) row.get("topic_id"), GroupStatus.valueOf((String) row.get("status")), row.get("approval_status") == null ? ApprovalStatus.PENDING : ApprovalStatus.valueOf((String) row.get("approval_status")));
    }

    private void authorizeLeaderOrAdmin(GroupRow group, Jwt actor) {
        if (isAdmin(actor)) {
            return;
        }
        if (!group.leaderStudentId().equals(studentId(actor))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_OWNER_REQUIRED", "Only the group leader can change this group");
        }
        if (group.status() != GroupStatus.DRAFT && group.status() != GroupStatus.SUBMITTED) {
            throw conflict("GROUP_STATE_CONFLICT", "The group is no longer editable");
        }
    }

    private void authorizeReviewer(GroupRow group, Jwt actor) {
        if (isAdmin(actor)) return;
        if (group.topicId() == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_REVIEWER_REQUIRED", "Only an assigned supervisor or admin can review this group");
        }
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));

        boolean isCreator = (!actorId.isBlank() || !lecturerId.isBlank()) && jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic WHERE id = :topicId AND (created_by = :actorId OR (:lecturerId <> '' AND created_by = :lecturerId))",
                params().addValue("topicId", group.topicId())
                        .addValue("actorId", actorId)
                        .addValue("lecturerId", lecturerId),
                Integer.class) > 0;
        if (isCreator) {
            return;
        }

        Integer supervisorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND (lecturer_id = :lecturerId OR lecturer_id = :actorId)",
                params().addValue("topicId", group.topicId())
                        .addValue("actorId", actorId)
                        .addValue("lecturerId", lecturerId.isBlank() ? actorId : lecturerId),
                Integer.class);
        if (supervisorCount != null && supervisorCount > 0) {
            return;
        }

        throw new DomainException(HttpStatus.FORBIDDEN, "GROUP_REVIEWER_REQUIRED", "Only an assigned supervisor or admin can review this group");
    }

    private void authorizeTopicOwner(ThesisTopic topic, Jwt actor) {
        if (isAdmin(actor)) return;
        String actorId = subject(actor);
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (!topic.getCreatedBy().equals(actorId) && (lecturerId.isBlank() || !topic.getCreatedBy().equals(lecturerId))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "TOPIC_OWNER_REQUIRED", "Only the topic owner can change this topic");
        }
    }

    private void requireRoundStatus(UUID id, RoundStatus required) {
        Map<String, Object> round = one(
                "SELECT status, registration_start, registration_end FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", id), "ROUND_NOT_FOUND", "Thesis registration round not found");
        if (!required.name().equals(round.get("status"))) {
            throw conflict("ROUND_CLOSED", "Registration is not open for this round");
        }
        if (required == RoundStatus.REGISTRATION_OPEN) {
            // The stored dates are normative: an open round whose window has
            // passed must not keep accepting registration mutations.
            Instant now = Instant.now();
            Instant start = instantOf(round.get("registration_start"));
            Instant end = instantOf(round.get("registration_end"));
            if (start == null || end == null || now.isBefore(start) || !now.isBefore(end)) {
                throw conflict("REGISTRATION_WINDOW_CLOSED", "The registration window is closed for this round");
            }
        }
    }

    /** Membership is frozen once a supervisor approves the group; admins coordinate through re-review instead. */
    private void requireMutableMembership(GroupRow group, Jwt actor) {
        if (!isAdmin(actor) && group.approvalStatus() == ApprovalStatus.APPROVED) {
            throw conflict("GROUP_STATE_CONFLICT", "An approved group's membership is frozen");
        }
    }

    private static Instant instantOf(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof java.time.LocalDateTime localDateTime) return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof java.util.Date date) return date.toInstant();
        return null;
    }

    private void requireActiveStudent(String studentId) {
        if (count("SELECT COUNT(*) FROM campuscore_auth.\"Student\" WHERE \"id\" = :studentId AND \"status\" = 'ACTIVE'", null, studentId) == 0) {
            throw new DomainException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "An active student profile is required");
        }
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource parameters, String code, String message) {
        try {
            return jdbc.queryForMap(sql, parameters);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound(code, message);
        }
    }

    private int count(String sql, UUID first, String second) {
        MapSqlParameterSource parameters = params();
        if (first != null) {
            parameters.addValue(sql.contains("group_id") ? "groupId" : sql.contains("round_id") ? "roundId" : "topicId", first);
        }
        if (second != null) {
            parameters.addValue(sql.contains("lecturer_id") ? "lecturerId" : "studentId", second);
        }
        Integer result = jdbc.queryForObject(sql, parameters, Integer.class);
        return result == null ? 0 : result;
    }


    private int count(String sql, UUID first) {
        return count(sql, first, null);
    }

    private static MapSqlParameterSource params() { return new MapSqlParameterSource(); }
    private static String subject(Jwt actor) { return actor == null || actor.getSubject() == null ? "" : actor.getSubject(); }
    private static String studentId(Jwt actor) { return normalize(actor == null ? null : actor.getClaimAsString("studentId")); }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
    private static void requireText(String value, String name) { if (value == null || value.isBlank()) throw invalid(name + " is required"); }
    private static void requireDates(Instant start, Instant end) { if (start == null || end == null || !end.isAfter(start)) throw invalid("registrationEnd must be after registrationStart"); }
    private static boolean isAdmin(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
    }
    private static boolean isLecturer(Jwt actor) {
        if (actor == null) {
            return false;
        }
        List<String> roles = actor.getClaimAsStringList("roles");
        return roles != null && roles.contains("LECTURER");
    }
    private static boolean isProgressStatus(GroupStatus status) { return status == GroupStatus.DRAFT || status == GroupStatus.SUBMITTED || status == GroupStatus.COMPLETED || status == GroupStatus.CANCELLED; }
    private static DomainException invalid(String message) { return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message); }
    private static DomainException conflict(String code, String message) { return new DomainException(HttpStatus.CONFLICT, code, message); }
    private static DomainException notFound(String code, String message) { return new DomainException(HttpStatus.NOT_FOUND, code, message); }

    private record GroupRow(UUID id, UUID roundId, String leaderStudentId, UUID topicId, GroupStatus status, ApprovalStatus approvalStatus) { }
}
