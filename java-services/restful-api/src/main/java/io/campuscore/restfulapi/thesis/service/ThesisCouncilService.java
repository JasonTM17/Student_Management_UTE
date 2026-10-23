package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.audit.AdminAuditRecorder;
import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.RoundType;
import io.campuscore.restfulapi.thesis.domain.TopicStatus;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Defense councils and component scoring (brief R6–R9): councils of three to
 * five lecturers with exactly one chair and one secretary, per-member
 * component scores, a chair-driven average finalize that can only happen
 * once, supervised topics excluded from grading, and student-visible results
 * after round publication.
 */
@Service
@Profile("persistence")
public class ThesisCouncilService {

    private static final int MIN_COUNCIL_SIZE = 3;
    private static final int MAX_COUNCIL_SIZE = 5;
    private static final String SCORE_COMPONENT = "DEFENSE";
    private static final String GVPB_COMPONENT = "GVPB";
    private static final BigDecimal GVPB_PASS_THRESHOLD = new BigDecimal("5.0");
    // Non-score topic results: REVIEW_FAILED is derived from the latest
    // counter-review score, WITHDRAWN is set explicitly by governance. Both
    // are excluded from the publication completeness check.
    private static final String RESULT_REVIEW_FAILED = "REVIEW_FAILED";
    private static final String RESULT_WITHDRAWN = "WITHDRAWN";

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ThesisCouncilService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAuditRecorder audit;
    private final ThesisNotificationService notifier;

    /**
     * Creates the service over the shared named-parameter JDBC template.
     *
     * @param jdbc template used for the council, scoring, and result queries
     * @param audit admin audit trail; score submissions and finalizations join the
     *              caller's transaction so the grade and its trace commit together
     */
    public ThesisCouncilService(NamedParameterJdbcTemplate jdbc, AdminAuditRecorder audit) {
        this(jdbc, audit, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ThesisCouncilService(NamedParameterJdbcTemplate jdbc, AdminAuditRecorder audit,
            ThesisNotificationService notifier) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.notifier = notifier;
    }

    // ---------- councils ----------

    /**
     * Opens an empty defense council for a round; only a faculty head or an admin may do it.
     *
     * @param roundId round the council grades
     * @param name display name of the council
     * @param actor caller JWT used for the governance-role check and the {@code created_by} audit column
     * @return the newly created council with its empty member and topic rosters
     * @throws DomainException with code VALIDATION_ERROR when the round id or name is missing, or
     *         FORBIDDEN with code COUNCIL_ADMIN_REQUIRED for an unauthorised caller; the round
     *         itself is enforced by the {@code round_id} foreign key
     */
    @Transactional
    public CouncilResponse createCouncil(UUID roundId, String name, Jwt actor) {
        if (roundId == null) {
            throw invalid("roundId is required");
        }
        requireText(name, "name");
        requireGovernanceRole(actor, "Only a faculty head or admin can create a council");
        lockMutableRound(roundId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (:id, :roundId, :name, 'ACTIVE', :createdBy)",
                params().addValue("id", id).addValue("roundId", roundId)
                        .addValue("name", name.trim()).addValue("createdBy", subject(actor)));
        audit.record(subject(actor), null, "COUNCIL_CREATED", "THESIS_COUNCIL", id.toString(),
                "Defense council created: " + name.trim());
        return getCouncil(id);
    }

    /**
     * Adds one lecturer to a council, filling the privileged seats in order.
     *
     * @param councilId council being staffed
     * @param lecturerId id of the active lecturer to add
     * @param memberRole requested seat: {@code CHAIR}, {@code SECRETARY}, or {@code MEMBER}
     * @param actor caller JWT; a faculty head or admin is required
     * @return the council with the new member listed
     * @throws DomainException with code COUNCIL_MEMBER_EXISTS for a duplicate lecturer,
     *         COUNCIL_SIZE_INVALID past five members, COUNCIL_INCOMPLETE when the requested seat
     *         is not the next one to fill, or VALIDATION_ERROR for an unknown lecturer
     */
    @Transactional
    public CouncilResponse addMember(UUID councilId, String lecturerId, String memberRole, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can manage council members");
        requireCouncilGovernanceAccess(councilId, actor);
        lockMutableCouncilRound(councilId);
        lockCouncil(councilId);
        requireRosterMutable(councilId);
        requireText(lecturerId, "lecturerId");
        String role = normalizeRole(memberRole);
        if (!lecturerExists(lecturerId)) {
            throw invalid("Unknown or inactive lecturer: " + lecturerId);
        }
        Integer existing = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId AND lecturer_id = :lecturerId",
                params().addValue("councilId", councilId).addValue("lecturerId", lecturerId));
        if (existing != null && existing > 0) {
            throw conflict("COUNCIL_MEMBER_EXISTS", "The lecturer already sits on this council");
        }
        Integer size = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId",
                params().addValue("councilId", councilId));
        if (size == null || size >= MAX_COUNCIL_SIZE) {
            throw conflict("COUNCIL_SIZE_INVALID", "A defense council has at most five members");
        }
        // Missing privileged seats are refilled first (chair can be lost through
        // removal), then ordinary members fill the remaining seats.
        String expectedRole;
        if (!hasRoleSeat(councilId, "CHAIR")) {
            expectedRole = "CHAIR";
        } else if (!hasRoleSeat(councilId, "SECRETARY")) {
            expectedRole = "SECRETARY";
        } else {
            expectedRole = "MEMBER";
        }
        if (!role.equals(expectedRole)) {
            throw conflict("COUNCIL_INCOMPLETE",
                    "Council seats fill in order: chair, secretary, then members (next seat: " + expectedRole + ")");
        }
        // Third edge of the independence triangle (assignReviewer and
        // assignTopic cover the other two): the reviewer must never join the
        // council grading a topic they counter-review.
        Integer reviewingAssignedTopic = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_topic t ON t.id = ct.topic_id "
                        + "WHERE ct.council_id = :councilId AND t.gvpb_lecturer_id = :lecturerId",
                params().addValue("councilId", councilId).addValue("lecturerId", lecturerId));
        if (reviewingAssignedTopic != null && reviewingAssignedTopic > 0) {
            throw conflict("REVIEWER_MUST_BE_INDEPENDENT",
                    "The assigned counter-reviewer cannot sit on the defense council for this topic");
        }
        Integer supervisingAssignedTopic = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_topic_supervisor sup ON sup.topic_id = ct.topic_id "
                        + "JOIN campuscore_auth.\"Lecturer\" l "
                        + "  ON (l.\"id\" = sup.lecturer_id OR l.\"userId\" = sup.lecturer_id) "
                        + "WHERE ct.council_id = :councilId "
                        + "  AND (sup.lecturer_id = :lecturerId OR l.\"id\" = :lecturerId OR l.\"userId\" = :lecturerId)",
                params().addValue("councilId", councilId).addValue("lecturerId", lecturerId));
        if (supervisingAssignedTopic != null && supervisingAssignedTopic > 0) {
            throw conflict("SUPERVISOR_CANNOT_CHAIR_COUNCIL",
                    "The topic supervisor cannot chair or sit on the defense council for an assigned topic");
        }
        jdbc.update(
                "INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) "
                        + "VALUES (:id, :councilId, :lecturerId, :role)",
                params().addValue("id", UUID.randomUUID()).addValue("councilId", councilId)
                        .addValue("lecturerId", lecturerId).addValue("role", role));
        audit.record(subject(actor), null, "COUNCIL_MEMBER_ADDED", "THESIS_COUNCIL", councilId.toString(),
                "Lecturer " + lecturerId + " seated as " + role);
        return getCouncil(councilId);
    }

    /**
     * Removes a lecturer from a council while protecting the seats a graded council needs.
     *
     * @param councilId council being staffed
     * @param lecturerId lecturer to remove
     * @param actor caller JWT; a faculty head or admin is required
     * @return the council roster after the removal
     * @throws DomainException with code COUNCIL_CHAIR_REQUIRED or COUNCIL_SECRETARY_REQUIRED when
     *         the council already holds topics, COUNCIL_SIZE_INVALID when the roster would drop
     *         below three members, or COUNCIL_MEMBER_NOT_FOUND when the lecturer is not a member
     */
    @Transactional
    public CouncilResponse removeMember(UUID councilId, String lecturerId, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can manage council members");
        requireCouncilGovernanceAccess(councilId, actor);
        lockMutableCouncilRound(councilId);
        lockCouncil(councilId);
        requireText(lecturerId, "lecturerId");
        Integer assignedTopics = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE council_id = :councilId",
                params().addValue("councilId", councilId));
        requireRosterMutable(councilId);
        if (assignedTopics != null && assignedTopics > 0) {
            if (hasCouncilRole(councilId, lecturerId, "CHAIR")) {
                throw conflict("COUNCIL_CHAIR_REQUIRED",
                        "The chair cannot leave a council that has assigned topics; reassign the topics first");
            }
            if (hasCouncilRole(councilId, lecturerId, "SECRETARY")) {
                throw conflict("COUNCIL_SECRETARY_REQUIRED",
                        "The secretary cannot leave a council that has assigned topics; reassign the topics first");
            }
            Integer size = count(
                    "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId",
                    params().addValue("councilId", councilId));
            if (size == null || size - 1 < MIN_COUNCIL_SIZE) {
                throw conflict("COUNCIL_SIZE_INVALID", "A council with assigned topics keeps at least three members");
            }
        }
        if (jdbc.update("DELETE FROM thesis.thesis_council_member WHERE council_id = :councilId AND lecturer_id = :lecturerId",
                params().addValue("councilId", councilId).addValue("lecturerId", lecturerId)) != 1) {
            throw notFound("COUNCIL_MEMBER_NOT_FOUND", "Council member not found");
        }
        audit.record(subject(actor), null, "COUNCIL_MEMBER_REMOVED", "THESIS_COUNCIL", councilId.toString(),
                "Lecturer " + lecturerId + " removed from the council");
        return getCouncil(councilId);
    }

    /**
     * Assigns one thesis topic to a fully staffed council for defense.
     *
     * @param councilId council that will grade the topic
     * @param topicId topic being defended, which must belong to the council's round
     * @param actor caller JWT; a faculty head or admin is required
     * @return the council including the newly assigned topic
     * @throws DomainException with code COUNCIL_INCOMPLETE when the roster is not three to five
     *         members with a chair and a secretary, TOPIC_ROUND_MISMATCH, TOPIC_ALREADY_ASSIGNED,
     *         SUPERVISOR_CANNOT_CHAIR_COUNCIL, or SCORE_ALREADY_FINALIZED for a graded topic
     */
    @Transactional
    public CouncilResponse assignTopic(UUID councilId, UUID topicId, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can assign topics to a council");
        requireCouncilGovernanceAccess(councilId, actor);
        lockMutableCouncilRound(councilId);
        lockCouncil(councilId);
        if (topicId == null) {
            throw invalid("topicId is required");
        }
        // Match ThesisSupervisorService.setSupervisors: assignment and
        // supervisor replacement must serialize on the same topic row.
        lockTopic(topicId);
        Integer size = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId",
                params().addValue("councilId", councilId));
        if (size == null || size < MIN_COUNCIL_SIZE || size > MAX_COUNCIL_SIZE
                || !hasRoleSeat(councilId, "CHAIR") || !hasRoleSeat(councilId, "SECRETARY")) {
            throw conflict("COUNCIL_INCOMPLETE",
                    "A council needs three to five members including one chair and one secretary before it can grade topics");
        }
        Map<String, Object> topic = one(
                "SELECT id, round_id, status, final_score FROM thesis.thesis_topic WHERE id = :topicId",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        ThesisDeanScope.requireTopicAccess(jdbc, actor, topicId);
        if (!"PUBLISHED".equals(topic.get("status")) && !"APPROVED".equals(topic.get("status"))) {
            throw conflict("TOPIC_NOT_PUBLISHED",
                    "Only a published topic can be assigned to a defense council");
        }
        if (!councilRound(councilId).equals(topic.get("round_id"))) {
            throw conflict("TOPIC_ROUND_MISMATCH", "Topic belongs to another registration round");
        }
        if (topic.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "A finalized topic cannot be reassigned to another council");
        }
        Integer taken = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE topic_id = :topicId",
                params().addValue("topicId", topicId));
        if (taken != null && taken > 0) {
            throw conflict("TOPIC_ALREADY_ASSIGNED", "The topic already belongs to a council");
        }
        // Resolve the member's lecturer profile, then match the supervisor
        // row against either of its stored key forms (Lecturer.id or legacy
        // User.id) — a direct id comparison would miss legacy-keyed rows.
        Integer supervisorOfficer = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member m "
                        + "JOIN campuscore_auth.\"Lecturer\" l "
                        + "  ON (l.\"id\" = m.lecturer_id OR l.\"userId\" = m.lecturer_id) "
                        + "JOIN thesis.thesis_topic_supervisor sup "
                        + "  ON sup.topic_id = :topicId "
                        + " AND (sup.lecturer_id = l.\"id\" OR sup.lecturer_id = l.\"userId\") "
                        + "WHERE m.council_id = :councilId AND m.member_role IN ('CHAIR', 'SECRETARY')",
                params().addValue("councilId", councilId).addValue("topicId", topicId));
        if (supervisorOfficer != null && supervisorOfficer > 0) {
            throw conflict("SUPERVISOR_CANNOT_CHAIR_COUNCIL",
                    "The topic supervisor cannot chair or be secretary of the defense council for this topic");
        }
        if (eligibleGraderCount(councilId, topicId) < MIN_COUNCIL_SIZE) {
            throw conflict("COUNCIL_SIZE_INVALID",
                    "A council needs at least three eligible members excluding supervisors to evaluate this topic");
        }
        // Mirror of assignReviewer's REVIEWER_MUST_BE_INDEPENDENT: a lecturer
        // already named counter-reviewer must not grade this topic again from
        // inside the council. The member row may key the reviewer by Lecturer.id
        // or legacy User.id, so resolve the profile and match either form.
        Integer reviewerOnCouncil = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member cm "
                        + "JOIN campuscore_auth.\"Lecturer\" l "
                        + "  ON (l.\"id\" = cm.lecturer_id OR l.\"userId\" = cm.lecturer_id) "
                        + "JOIN thesis.thesis_topic t ON t.gvpb_lecturer_id = l.\"id\" "
                        + "WHERE cm.council_id = :councilId AND t.id = :topicId",
                params().addValue("councilId", councilId).addValue("topicId", topicId));
        if (reviewerOnCouncil != null && reviewerOnCouncil > 0) {
            throw conflict("REVIEWER_MUST_BE_INDEPENDENT",
                    "The assigned counter-reviewer cannot sit on the defense council for this topic");
        }
        jdbc.update(
                "INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) "
                        + "VALUES (:id, :councilId, :topicId, :assignedBy)",
                params().addValue("id", UUID.randomUUID()).addValue("councilId", councilId)
                        .addValue("topicId", topicId).addValue("assignedBy", subject(actor)));
        audit.record(subject(actor), null, "COUNCIL_TOPIC_ASSIGNED", "THESIS_COUNCIL", councilId.toString(),
                "Topic " + topicId + " assigned for defense");
        return getCouncil(councilId);
    }

    /**
     * Lists every council of one registration round with its members and assigned topics.
     *
     * @param roundId round whose councils are listed, oldest first
     * @return the councils of the round, each fully hydrated through {@link #getCouncil(UUID)}
     */
    @Transactional(readOnly = true)
    public List<CouncilResponse> listByRound(UUID roundId) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM thesis.thesis_council WHERE round_id = :roundId ORDER BY created_at, id",
                params().addValue("roundId", roundId), UUID.class);
        return ids.stream().map(this::getCouncil).toList();
    }

    /**
     * Actor-scoped council listing for the API surface: governance staff see
     * every council of the round; a lecturer sees only councils they sit on.
     * Before round 11 the list endpoint answered {@code isAuthenticated()},
     * which handed every authenticated caller — students included — the full
     * roster and pre-publication topic assignments of every council.
     *
     * @param roundId round whose councils are listed
     * @param actor caller JWT; governance roles bypass the membership filter
     * @return the councils of the round visible to the caller
     */
    @Transactional(readOnly = true)
    public List<CouncilResponse> listByRoundForActor(UUID roundId, Jwt actor) {
        List<CouncilResponse> councils = listByRound(roundId);
        if (ThesisDeanScope.isGlobalAdmin(actor)) {
            return councils;
        }
        if (ThesisDeanScope.isFacultyHead(actor)) {
            // Deans stay inside their faculty: councils they created or
            // councils already grading one of their topics. Anything else is a
            // foreign roster — the unconditional governance bypass used to
            // hand every dean the whole round's councils.
            return councils.stream()
                    .filter(council -> deanCanSeeCouncil(council.id(), actor))
                    .toList();
        }
        if (!isCouncilGovernanceStaff(actor)) {
            String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
            return councils.stream()
                    .filter(council -> council.members().stream()
                            .anyMatch(member -> member.lecturerId().equals(lecturerId)))
                    .toList();
        }
        return councils;
    }

    /**
     * Actor-gated single-council read: governance staff always, otherwise only
     * a sitting council member. A non-member lecturer and any caller without a
     * council membership get 403 COUNCIL_MEMBER_REQUIRED, mirroring the
     * scoring endpoints.
     *
     * @param councilId council to read
     * @param actor caller JWT
     * @return the fully hydrated council response
     * @throws DomainException with code COUNCIL_NOT_FOUND when no council has
     *         that id, or FORBIDDEN COUNCIL_MEMBER_REQUIRED for a non-member
     */
    @Transactional(readOnly = true)
    public CouncilResponse readCouncil(UUID councilId, Jwt actor) {
        CouncilResponse council = getCouncil(councilId);
        if (!isCouncilGovernanceStaff(actor)) {
            String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
            boolean member = StringUtils.hasText(lecturerId) && council.members().stream()
                    .anyMatch(entry -> entry.lecturerId().equals(lecturerId));
            if (!member) {
                throw new DomainException(HttpStatus.FORBIDDEN, "COUNCIL_MEMBER_REQUIRED",
                        "Only council members or governance staff can read a council roster");
            }
            return council;
        }
        requireCouncilGovernanceAccess(councilId, actor);
        return council;
    }

    private boolean isCouncilGovernanceStaff(Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        return roles != null && (roles.contains("ADMIN") || roles.contains("TRUONG_KHOA") || roles.contains("SUPER_ADMIN"));
    }

    /**
     * Faculty scope for council surfaces. Global admins pass. A dean may read
     * and staff a council only when they created it or it grades at least one
     * topic inside their faculty — without this check any dean could enumerate
     * every council's roster and restaff foreign councils until scoring began.
     * Out-of-scope councils are answered 404, matching the topic/group
     * concealment convention.
     */
    private void requireCouncilGovernanceAccess(UUID councilId, Jwt actor) {
        if (ThesisDeanScope.isGlobalAdmin(actor) || !ThesisDeanScope.isFacultyHead(actor)) {
            return;
        }
        String facultyId = ThesisDeanScope.facultyId(jdbc, actor);
        if (facultyId == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, "FACULTY_PROFILE_REQUIRED",
                    "An active lecturer profile bound to the faculty head is required");
        }
        Integer createdByDean = count(
                "SELECT COUNT(*) FROM thesis.thesis_council WHERE id = :councilId AND created_by = :subject",
                params().addValue("councilId", councilId).addValue("subject", subject(actor)));
        if (createdByDean != null && createdByDean > 0) {
            return;
        }
        Integer inFacultyTopics = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_topic t ON t.id = ct.topic_id "
                        + "JOIN academic.\"Department\" d ON d.\"id\" = t.department_id "
                        + "WHERE ct.council_id = :councilId AND d.\"facultyId\" = :facultyId",
                params().addValue("councilId", councilId).addValue("facultyId", facultyId));
        if (inFacultyTopics == null || inFacultyTopics == 0) {
            throw new DomainException(HttpStatus.NOT_FOUND, "COUNCIL_NOT_FOUND", "Defense council not found");
        }
    }

    /**
     * Defense scores belong to a defended group, so a topic without at least
     * one approved group cannot be graded or finalized — otherwise a council
     * could pre-bake a final score that the group inherits the moment it is
     * approved.
     */
    private void requireApprovedGroupForTopic(UUID topicId) {
        Integer approvedGroups = count(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE topic_id = :topicId "
                        + "AND approval_status = 'APPROVED' AND status <> 'CANCELLED'",
                params().addValue("topicId", topicId));
        if (approvedGroups == null || approvedGroups == 0) {
            throw conflict("GROUP_NOT_APPROVED",
                    "The topic has no approved group to defend yet");
        }
    }

    /** Non-throwing twin of {@link #requireCouncilGovernanceAccess} for list filtering. */
    private boolean deanCanSeeCouncil(UUID councilId, Jwt actor) {
        try {
            requireCouncilGovernanceAccess(councilId, actor);
            return true;
        } catch (DomainException exception) {
            return false;
        }
    }

    /**
     * Reads one council: header, members ordered chair then secretary then members, and topics.
     *
     * @param councilId council to read
     * @return the fully hydrated council response
     * @throws DomainException with code COUNCIL_NOT_FOUND when no council has that id
     */
    @Transactional(readOnly = true)
    public CouncilResponse getCouncil(UUID councilId) {
        Map<String, Object> council = one(
                "SELECT id, round_id, name, status, created_by FROM thesis.thesis_council WHERE id = :id",
                params().addValue("id", councilId), "COUNCIL_NOT_FOUND", "Defense council not found");
        List<CouncilMember> members = jdbc.query(
                "SELECT lecturer_id, member_role FROM thesis.thesis_council_member "
                        + "WHERE council_id = :councilId ORDER BY CASE member_role WHEN 'CHAIR' THEN 0 "
                        + "WHEN 'SECRETARY' THEN 1 ELSE 2 END, created_at, lecturer_id",
                params().addValue("councilId", councilId),
                (rs, ignored) -> new CouncilMember(rs.getString("lecturer_id"), rs.getString("member_role")));
        List<UUID> topicIds = jdbc.queryForList(
                "SELECT topic_id FROM thesis.thesis_council_topic WHERE council_id = :councilId ORDER BY created_at, topic_id",
                params().addValue("councilId", councilId), UUID.class);
        return new CouncilResponse(
                (UUID) council.get("id"),
                (UUID) council.get("round_id"),
                (String) council.get("name"),
                (String) council.get("status"),
                members, topicIds);
    }

    // ---------- scoring ----------

    /**
     * Records one council member's defense score, overwriting that member's earlier value.
     *
     * @param councilId council grading the topic
     * @param topicId topic being graded
     * @param component requested scoring component; the frozen model stores the single
     *        {@code DEFENSE} component so every member's grade carries equal weight
     * @param score score between 0 and 10 inclusive
     * @param actor caller JWT, which must belong to a non-supervising council member
     * @return the stored score with the grader and resolved component; the write also records
     *         a COUNCIL_SCORE_SUBMITTED audit row with the previous and new score
     * @throws DomainException with code TOPIC_NOT_ASSIGNED when the council does not grade the
     *         topic, FORBIDDEN with code COUNCIL_MEMBER_REQUIRED for a non-member, or
     *         VALIDATION_ERROR for an out-of-range score or a supervisor of the topic
     */
    @Transactional
    public ScoreResponse submitScore(UUID councilId, UUID topicId, String component, BigDecimal score, Jwt actor) {
        return submitScore(councilId, topicId, component, score, null, actor);
    }

    /**
     * Records one council member's defense score together with an optional
     * written evaluation comment (max 1000 characters, visible to students
     * only after the round publishes results).
     */
    @Transactional
    public ScoreResponse submitScore(UUID councilId, UUID topicId, String component, BigDecimal score,
            String comment, Jwt actor) {
        String lecturerId = requireCouncilLecturer(councilId, actor);
        // Membership edits lock Council. Share that lock before the final
        // permission check and first score, so a removal cannot pass its
        // "no scores yet" check while this transaction is about to grade.
        lockMutableCouncilRound(councilId);
        lockCouncil(councilId);
        requireCouncilLecturer(councilId, actor);
        if (!councilHasTopic(councilId, topicId)) {
            throw conflict("TOPIC_NOT_ASSIGNED", "This council has not been assigned the topic");
        }
        requireNotSupervisor(topicId, lecturerId);
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0) {
            throw invalid("score must be between 0 and 10");
        }
        // The frozen scoring model is one row per grader: every council member
        // grades exactly the DEFENSE component, so the finalize average gives
        // each member equal weight (no multi-component vote inflation).
        String resolvedComponent = SCORE_COMPONENT;
        if (StringUtils.hasText(component)
                && !SCORE_COMPONENT.equals(component.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw invalid("component must be " + SCORE_COMPONENT);
        }
        Map<String, Object> topicRow = one(
                "SELECT id, final_score FROM thesis.thesis_topic WHERE id = :topicId FOR UPDATE",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (topicRow.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "The topic score has been finalized by the chair");
        }
        requireApprovedGroupForTopic(topicId);
        // K16: the row is overwritten DELETE-then-INSERT, so the outgoing value
        // only exists here — capture it for the audit trail before the delete.
        Map<String, Object> previousRow = jdbc.query(
                "SELECT score, comment FROM thesis.thesis_topic_score WHERE topic_id = :topicId "
                        + "AND lecturer_id = :lecturerId AND component = :component",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId)
                        .addValue("component", resolvedComponent),
                (rs, ignored) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("score", rs.getBigDecimal("score"));
                    row.put("comment", rs.getString("comment"));
                    return row;
                }).stream().findFirst().orElse(null);
        jdbc.update(
                "DELETE FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND lecturer_id = :lecturerId AND component = :component",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId)
                        .addValue("component", resolvedComponent));
        String trimmedComment = comment == null ? null : comment.trim();
        if (trimmedComment != null && trimmedComment.isEmpty()) {
            trimmedComment = null;
        }
        if (trimmedComment != null && trimmedComment.length() > 1000) {
            throw invalid("comment must contain at most 1000 characters");
        }
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, comment) "
                        + "VALUES (:id, :topicId, :councilId, :lecturerId, :component, :score, :comment)",
                params().addValue("id", UUID.randomUUID()).addValue("topicId", topicId)
                        .addValue("councilId", councilId).addValue("lecturerId", lecturerId)
                        .addValue("component", resolvedComponent).addValue("score", score)
                        .addValue("comment", trimmedComment));
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("component", resolvedComponent);
        beforeState.put("lecturerId", lecturerId);
        beforeState.put("score", previousRow == null ? null : previousRow.get("score"));
        beforeState.put("comment", previousRow == null ? null : previousRow.get("comment"));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("component", resolvedComponent);
        afterState.put("lecturerId", lecturerId);
        afterState.put("score", score);
        afterState.put("comment", trimmedComment);
        audit.record(subject(actor), null, "COUNCIL_SCORE_SUBMITTED", "THESIS_TOPIC", topicId.toString(),
                previousRow == null
                        ? "Defense score " + score + " recorded for topic " + topicId
                        : "Defense score for topic " + topicId + " changed from " + previousRow.get("score") + " to " + score,
                beforeState, afterState);
        return new ScoreResponse(topicId, lecturerId, resolvedComponent, score, trimmedComment);
    }

    /**
     * Assigns the GVPB counter-reviewer of one topic (brief: exactly one
     * reviewer per defended topic, independent of the defense council).
     *
     * @param topicId topic being reviewed
     * @param lecturerId active lecturer who will counter-review; must not be a
     *        supervisor of the topic and the assignment freezes once the
     *        reviewer submitted a score or the chair finalized
     * @param actor caller JWT; a faculty head or admin is required
     * @return the assigned reviewer identity
     * @throws DomainException with code VALIDATION_ERROR for an unknown lecturer,
     *         SUPERVISOR_CANNOT_REVIEW when the lecturer supervises the topic,
     *         GVPB_SCORE_SUBMITTED once a reviewer score exists, or
     *         SCORE_ALREADY_FINALIZED for a graded topic
     */
    @Transactional
    public Map<String, Object> assignReviewer(UUID topicId, String lecturerId, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can assign the counter-reviewer");
        requireText(lecturerId, "lecturerId");
        lockMutableTopicRound(topicId);
        Map<String, Object> topic = one(
                "SELECT t.id, t.status, t.final_score, t.gvpb_lecturer_id, r.gvpb_deadline, r.status AS round_status "
                        + "FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.id = :topicId FOR UPDATE OF t",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        ThesisDeanScope.requireTopicAccess(jdbc, actor, topicId);
        if (RoundStatus.CANCELLED.name().equals(topic.get("round_status"))
                || RoundStatus.RESULTS_PUBLISHED.name().equals(topic.get("round_status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "The round no longer accepts a counter-reviewer assignment");
        }
        if (!TopicStatus.PUBLISHED.name().equals(topic.get("status"))
                && !TopicStatus.APPROVED.name().equals(topic.get("status"))) {
            throw conflict("TOPIC_NOT_PUBLISHED", "Only published topics can be assigned a counter-reviewer");
        }
        if (topic.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "A finalized topic cannot change its counter-reviewer");
        }
        if (topic.get("gvpb_deadline") == null) {
            throw invalid("This round's thesis type has no counter-review phase");
        }
        if (!lecturerExists(lecturerId)) {
            throw invalid("Unknown or inactive lecturer: " + lecturerId);
        }
        // Same dual-key resolution as requireNotSupervisor: a legacy
        // User.id-keyed supervisor row must not slip past the check.
        Integer supervising = count(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor sup "
                        + "JOIN campuscore_auth.\"Lecturer\" l "
                        + "  ON (l.\"id\" = :lecturerId OR l.\"userId\" = :lecturerId) "
                        + " AND (sup.lecturer_id = l.\"id\" OR sup.lecturer_id = l.\"userId\") "
                        + "WHERE sup.topic_id = :topicId",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId));
        if (supervising != null && supervising > 0) {
            throw conflict("SUPERVISOR_CANNOT_REVIEW",
                    "A topic supervisor cannot be its counter-reviewer");
        }
        // The counter-review is the independent grade beside the council's:
        // seating the reviewer on the grading council lets one person cast
        // both the GVPB and a DEFENSE score on the same topic. The council
        // member row may store the reviewer's legacy User.id key, so resolve
        // the profile and match either stored form.
        Integer onCouncil = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_council_member cm ON cm.council_id = ct.council_id "
                        + "JOIN campuscore_auth.\"Lecturer\" l ON l.\"id\" = :lecturerId "
                        + " AND (cm.lecturer_id = l.\"id\" OR cm.lecturer_id = l.\"userId\") "
                        + "WHERE ct.topic_id = :topicId",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId));
        if (onCouncil != null && onCouncil > 0) {
            throw conflict("REVIEWER_MUST_BE_INDEPENDENT",
                    "The counter-reviewer cannot sit on the defense council for this topic");
        }
        String previousReviewer = (String) topic.get("gvpb_lecturer_id");
        if (previousReviewer != null && !previousReviewer.equals(lecturerId)) {
            Integer scored = count(
                    "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND component = :component",
                    params().addValue("topicId", topicId).addValue("component", GVPB_COMPONENT));
            if (scored != null && scored > 0) {
                throw conflict("GVPB_SCORE_SUBMITTED",
                        "The counter-reviewer already submitted a score; the assignment is frozen");
            }
        }
        jdbc.update(
                "UPDATE thesis.thesis_topic SET gvpb_lecturer_id = :lecturerId, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :topicId",
                params().addValue("lecturerId", lecturerId).addValue("topicId", topicId));
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("gvpb_lecturer_id", previousReviewer);
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("gvpb_lecturer_id", lecturerId);
        audit.record(subject(actor), null, "GVPB_ASSIGNED", "THESIS_TOPIC", topicId.toString(),
                "Counter-reviewer assigned to topic " + topicId, beforeState, afterState);
        return afterState;
    }

    /**
     * Records the GVPB counter-review score for a topic, before the round's
     * {@code gvpb_deadline} when the round type declares one.
     *
     * @param topicId topic being counter-reviewed
     * @param score score between 0 and 10 inclusive
     * @param comment optional written evaluation (max 1000 characters)
     * @param actor caller JWT of the assigned counter-reviewer
     * @return the stored score row
     * @throws DomainException with code REVIEWER_REQUIRED when the caller is
     *         not the assigned reviewer, GVPB_DEADLINE_PASSED after the round
     *         deadline, SUPERVISOR_CANNOT_GRADE for a supervisor (supervisors
     *         are re-checked because they can change after assignment), or
     *         SCORE_ALREADY_FINALIZED once the chair closed the topic
     */
    @Transactional
    public ScoreResponse submitReviewerScore(UUID topicId, BigDecimal score, String comment, Jwt actor) {
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (!StringUtils.hasText(lecturerId) || !lecturerExists(lecturerId)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "LECTURER_PROFILE_REQUIRED",
                    "An active lecturer profile is required to submit a counter-review score");
        }
        lockMutableTopicRound(topicId);
        Map<String, Object> topic = one(
                "SELECT t.id, t.round_id, t.status, t.final_score, t.gvpb_lecturer_id, r.gvpb_deadline, r.status AS round_status "
                        + "FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.id = :topicId FOR UPDATE OF t",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        if (RoundStatus.CANCELLED.name().equals(topic.get("round_status"))
                || RoundStatus.RESULTS_PUBLISHED.name().equals(topic.get("round_status"))) {
            throw conflict("ROUND_STATE_CONFLICT", "The round no longer accepts counter-review scores");
        }
        if (!lecturerId.equals(topic.get("gvpb_lecturer_id"))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "REVIEWER_REQUIRED",
                    "Only the assigned counter-reviewer can submit the GVPB score");
        }
        if (!TopicStatus.PUBLISHED.name().equals(topic.get("status"))
                && !TopicStatus.APPROVED.name().equals(topic.get("status"))) {
            throw conflict("TOPIC_NOT_PUBLISHED", "Only published topics can receive a counter-review score");
        }
        if (topic.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "The topic score has been finalized by the chair");
        }
        requireNotSupervisor(topicId, lecturerId);
        Instant deadline = instantOf(topic.get("gvpb_deadline"));
        // Match requireReportWritable's exclusive boundary: the deadline is
        // the first instant the window is closed, so == freezes like a passed
        // deadline does everywhere else in the workflow.
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            throw conflict("GVPB_DEADLINE_PASSED",
                    "The counter-review deadline for this round has passed");
        }
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0) {
            throw invalid("score must be between 0 and 10");
        }
        // A counter-review grades a submitted report — meaningless until an
        // approved group exists to produce one.
        requireApprovedGroupForTopic(topicId);
        String trimmedComment = comment == null ? null : comment.trim();
        if (trimmedComment != null && trimmedComment.isEmpty()) {
            trimmedComment = null;
        }
        if (trimmedComment != null && trimmedComment.length() > 1000) {
            throw invalid("comment must contain at most 1000 characters");
        }
        Map<String, Object> previousRow = jdbc.query(
                "SELECT score, comment FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND component = :component",
                params().addValue("topicId", topicId).addValue("component", GVPB_COMPONENT),
                (rs, ignored) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("score", rs.getBigDecimal("score"));
                    row.put("comment", rs.getString("comment"));
                    return row;
                }).stream().findFirst().orElse(null);
        jdbc.update(
                "DELETE FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND component = :component",
                params().addValue("topicId", topicId).addValue("component", GVPB_COMPONENT));
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, comment) "
                        + "VALUES (:id, :topicId, NULL, :lecturerId, :component, :score, :comment)",
                params().addValue("id", UUID.randomUUID()).addValue("topicId", topicId)
                        .addValue("lecturerId", lecturerId).addValue("component", GVPB_COMPONENT)
                        .addValue("score", score).addValue("comment", trimmedComment));
        audit.record(subject(actor), null, "GVPB_SCORE_SUBMITTED", "THESIS_TOPIC", topicId.toString(),
                previousRow == null
                        ? "Counter-review score " + score + " recorded for topic " + topicId
                        : "Counter-review score for topic " + topicId + " changed from " + previousRow.get("score") + " to " + score);
        syncReviewOutcome(topicId, subject(actor));
        return new ScoreResponse(topicId, lecturerId, GVPB_COMPONENT, score, trimmedComment);
    }

    /**
     * Reads the GVPB counter-review score of a topic. The assigned reviewer
     * reads their own row; governance staff read the row of whichever reviewer
     * is currently assigned. Other callers are rejected so a plain council
     * member cannot probe counter-review state.
     */
    @Transactional(readOnly = true)
    public ScoreResponse reviewerScore(UUID topicId, Jwt actor) {
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        boolean governance = isCouncilGovernanceStaff(actor);
        Map<String, Object> topic = one(
                "SELECT gvpb_lecturer_id FROM thesis.thesis_topic WHERE id = :topicId",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        String assigned = (String) topic.get("gvpb_lecturer_id");
        if (!governance && (lecturerId == null || !lecturerId.equals(assigned))) {
            throw new DomainException(HttpStatus.FORBIDDEN, "REVIEWER_REQUIRED",
                    "Only the assigned counter-reviewer can read the GVPB score");
        }
        // A dean caller is governance but stays faculty-bound: without this the
        // unconditional governance branch leaked foreign-faculty counter-review
        // scores and comments pre-publication.
        ThesisDeanScope.requireTopicAccess(jdbc, actor, topicId);
        if (assigned == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, "REVIEW_SCORE_NOT_FOUND",
                    "No counter-reviewer is assigned to this topic");
        }
        return jdbc.query(
                "SELECT score, comment FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND component = :component",
                params().addValue("topicId", topicId).addValue("component", GVPB_COMPONENT),
                (rs, ignored) -> new ScoreResponse(topicId, assigned, GVPB_COMPONENT,
                        rs.getBigDecimal("score"), rs.getString("comment")))
                .stream().findFirst()
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "REVIEW_SCORE_NOT_FOUND",
                        "The counter-reviewer has not submitted a score yet"));
    }

    /**
     * Lists the submitted defense scores of one topic, newest submission last.
     *
     * @param councilId council that grades the topic
     * @param topicId topic whose scores are read
     * @param actor caller JWT; must be a member of the council or a governance role
     * @return every submitted score for the frozen defense component
     * @throws DomainException with code COUNCIL_SCORE_ACCESS_REQUIRED for an unauthorised reader,
     *         or TOPIC_NOT_ASSIGNED when the council does not grade the topic
     */
    @Transactional(readOnly = true)
    public List<ScoreResponse> listScores(UUID councilId, UUID topicId, Jwt actor) {
        requireScoreReadAccess(councilId, topicId, actor);
        if (!councilHasTopic(councilId, topicId)) {
            throw conflict("TOPIC_NOT_ASSIGNED", "This council has not been assigned the topic");
        }
        return jdbc.query(
                "SELECT lecturer_id, component, score, comment FROM thesis.thesis_topic_score "
                        + "WHERE council_id = :councilId AND topic_id = :topicId AND component = :component "
                        + "ORDER BY graded_at, lecturer_id",
                params().addValue("councilId", councilId).addValue("topicId", topicId)
                        .addValue("component", SCORE_COMPONENT),
                (rs, ignored) -> new ScoreResponse(topicId, rs.getString("lecturer_id"),
                        rs.getString("component"), rs.getBigDecimal("score"), rs.getString("comment")));
    }

    /**
     * Freezes a topic's defense result as the equal-weighted average of the eligible member
     * scores. The update is guarded by {@code final_score IS NULL}, so a concurrent finalize
     * loses instead of double-writing.
     *
     * @param councilId council that graded the topic
     * @param topicId topic being closed out
     * @param actor caller JWT; must be the council chair
     * @return the finalized result with the average, the chair, and the finalization instant;
     *         a COUNCIL_SCORE_FINALIZED audit row records the frozen average
     * @throws DomainException with code COUNCIL_ROLE_REQUIRED for a non-chair, SCORES_INCOMPLETE
     *         while an eligible member has not graded, SCORES_REQUIRED with no usable score, or
     *         SCORE_ALREADY_FINALIZED when another chair froze the result first
     */
    @Transactional
    public TopicResult finalizeScores(UUID councilId, UUID topicId, Jwt actor) {
        requireCouncilRole(councilId, actor, "CHAIR", "Only the council chair can finalize the topic score");
        lockMutableCouncilRound(councilId);
        lockCouncil(councilId);
        requireCouncilRole(councilId, actor, "CHAIR", "Only the council chair can finalize the topic score");
        if (!councilHasTopic(councilId, topicId)) {
            throw conflict("TOPIC_NOT_ASSIGNED", "This council has not been assigned the topic");
        }
        // Lock topic row to prevent concurrent score submission during finalization
        one("SELECT id FROM thesis.thesis_topic WHERE id = :topicId FOR UPDATE",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        requireApprovedGroupForTopic(topicId);
        int expectedScores = eligibleGraderCount(councilId, topicId);
        if (expectedScores < MIN_COUNCIL_SIZE) {
            throw conflict("COUNCIL_SIZE_INVALID",
                    "A council needs at least three eligible members to evaluate and finalize this topic");
        }
        int submittedScores = submittedEligibleScoreCount(councilId, topicId);
        if (submittedScores < expectedScores) {
            throw conflict("SCORES_INCOMPLETE",
                    "Every eligible council member must submit a defense score before finalizing");
        }
        Map<String, Object> roundAndTopic = one(
                "SELECT r.thesis_type, r.gvpb_deadline, t.gvpb_lecturer_id, t.gvpb_waived_reason "
                        + "FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.id = :topicId",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        String roundTypeStr = (String) roundAndTopic.get("thesis_type");
        RoundType roundType = null;
        if (roundTypeStr != null) {
            try {
                roundType = RoundType.valueOf(roundTypeStr);
            } catch (IllegalArgumentException ignored) {
                roundType = null;
            }
        }
        Object gvpbDeadline = roundAndTopic.get("gvpb_deadline");
        String gvpbLecturerId = (String) roundAndTopic.get("gvpb_lecturer_id");
        // A governance waiver lifts the whole counter-review gate for this
        // topic; the stored score (if any) no longer decides finalization.
        boolean waived = roundAndTopic.get("gvpb_waived_reason") != null;
        boolean requiresGvpb = !waived
                && ((roundType != null && roundType.requiresGvpbDeadline() && gvpbDeadline != null)
                || (gvpbLecturerId != null));
        if (requiresGvpb) {
            List<BigDecimal> gvpbScores = jdbc.queryForList(
                    "SELECT score FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND component = :component",
                    params().addValue("topicId", topicId).addValue("component", GVPB_COMPONENT),
                    BigDecimal.class);
            if (gvpbScores.isEmpty() || gvpbScores.get(0) == null) {
                throw conflict("REVIEW_SCORE_REQUIRED",
                        "A counter-review (GVPB) score is required before the defense score can be finalized");
            }
            if (gvpbScores.get(0).compareTo(BigDecimal.valueOf(5.0)) < 0) {
                throw conflict("REVIEW_SCORE_BELOW_THRESHOLD",
                        "Topic counter-review score must be at least 5.0 to qualify for defense");
            }
        }
        List<BigDecimal> scores = jdbc.queryForList(
                "SELECT s.score FROM thesis.thesis_topic_score s "
                        + "JOIN thesis.thesis_council_member m "
                        + "  ON m.council_id = s.council_id AND m.lecturer_id = s.lecturer_id "
                        + "WHERE s.council_id = :councilId AND s.topic_id = :topicId AND s.component = :component "
                        + activeMemberClause("s.lecturer_id")
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM thesis.thesis_topic_supervisor sup "
                        + "  JOIN campuscore_auth.\"Lecturer\" l "
                        + "    ON (l.\"id\" = s.lecturer_id OR l.\"userId\" = s.lecturer_id) "
                        + "   AND (sup.lecturer_id = l.\"id\" OR sup.lecturer_id = l.\"userId\") "
                        + "  WHERE sup.topic_id = :topicId"
                        + ")",
                params().addValue("councilId", councilId).addValue("topicId", topicId)
                        .addValue("component", SCORE_COMPONENT), BigDecimal.class);
        if (scores.isEmpty()) {
            throw conflict("SCORES_REQUIRED", "At least one component score is needed before finalizing");
        }
        BigDecimal average = scores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        int changed = jdbc.update(
                "UPDATE thesis.thesis_topic SET final_score = :final, final_score_finalized_by = :chair, "
                        + "final_score_finalized_at = CURRENT_TIMESTAMP, result_status = 'GRADED', updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :topicId AND final_score IS NULL",
                params().addValue("final", average).addValue("chair", subject(actor)).addValue("topicId", topicId));
        if (changed != 1) {
            throw conflict("SCORE_ALREADY_FINALIZED", "The topic score has already been finalized");
        }
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("final_score", null);
        beforeState.put("result_status", null);
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("final_score", average);
        afterState.put("result_status", "GRADED");
        afterState.put("finalized_by", subject(actor));
        audit.record(subject(actor), null, "COUNCIL_SCORE_FINALIZED", "THESIS_TOPIC", topicId.toString(),
                "Defense score finalized for topic " + topicId + " at " + average,
                beforeState, afterState);
        if (notifier != null) {
            // Same after-commit rule as publishResults: a REQUIRES_NEW fan-out
            // fired inside the transaction would survive a later rollback and
            // announce a score that never persisted.
            afterCommit(() -> {
                try {
                    notifier.notifyScoreFinalized(topicId);
                } catch (RuntimeException exception) {
                    log.warn("Thesis finalize notification fan-out failed for topic {}", topicId, exception);
                }
            });
        }
        return new TopicResult(topicId, average, subject(actor), Instant.now(), "GRADED");
    }

    /**
     * Removes one topic from its council. The wedged path this closes:
     * council officers could not be removed while topics stayed assigned and
     * the unique index on thesis_council_topic.topic_id made direct
     * reassignment impossible, so a council that could not grade held its
     * topics forever. The release is only legal before any DEFENSE score —
     * after that the grade belongs to this council's verdict.
     *
     * @param councilId council releasing the topic
     * @param topicId topic to detach; afterwards it can be re-assigned through
     *        the normal assignTopic flow
     * @param reason governance reason (required, at most 500 characters)
     * @param actor caller JWT; a faculty head or admin is required
     * @return the council after the detachment
     * @throws DomainException with code TOPIC_NOT_ASSIGNED when the council
     *         does not grade the topic or TOPIC_ALREADY_SCORED once a defense
     *         score exists
     */
    @Transactional
    public CouncilResponse unassignTopic(UUID councilId, UUID topicId, String reason, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can unassign topics from a council");
        requireCouncilGovernanceAccess(councilId, actor);
        String cleanReason = requireReason(reason);
        lockMutableCouncilRound(councilId);
        lockCouncil(councilId);
        if (topicId == null) {
            throw invalid("topicId is required");
        }
        lockTopic(topicId);
        if (!councilHasTopic(councilId, topicId)) {
            throw conflict("TOPIC_NOT_ASSIGNED", "This council has not been assigned the topic");
        }
        Integer defenseScores = count(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND component = :component",
                params().addValue("topicId", topicId).addValue("component", SCORE_COMPONENT));
        if (defenseScores != null && defenseScores > 0) {
            throw conflict("TOPIC_ALREADY_SCORED",
                    "A topic with submitted defense scores cannot leave its council");
        }
        jdbc.update(
                "DELETE FROM thesis.thesis_council_topic WHERE council_id = :councilId AND topic_id = :topicId",
                params().addValue("councilId", councilId).addValue("topicId", topicId));
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("topic_id", topicId.toString());
        audit.record(subject(actor), null, "COUNCIL_TOPIC_UNASSIGNED", "THESIS_COUNCIL", councilId.toString(),
                "Topic " + topicId + " unassigned from the council; reason: " + cleanReason,
                beforeState, Map.of());
        return getCouncil(councilId);
    }

    /**
     * Replaces the lecturer sitting in one council seat while keeping the seat
     * and its role. Two traps share this escape: a member whose lecturer
     * profile was deactivated can no longer submit a score (hasMember gates
     * them out) yet still counted toward the expected graders, and removing a
     * member from a three-seat council was refused outright while topics
     * stayed assigned. An in-place swap preserves the roster size, the role
     * seats, and every independence check the original addMember enforced.
     *
     * <p>Once any council score exists the roster is frozen exactly as
     * {@link #requireRosterMutable} rules — except when the outgoing member's
     * lecturer profile is already gone or deactivated: that seat can never
     * score again, so swapping it cannot rewrite a cast vote (the departed
     * member's scores stay stored but stop counting toward the eligible
     * average, the same way the seat check treats them).
     *
     * @param councilId council being staffed
     * @param outgoingLecturerId lecturer leaving the seat
     * @param incomingLecturerId active lecturer taking the seat
     * @param reason governance reason (required, at most 500 characters)
     * @param actor caller JWT; a faculty head or admin is required
     * @return the council roster after the swap
     * @throws DomainException with code COUNCIL_MEMBER_NOT_FOUND when the
     *         outgoing lecturer is not a member, COUNCIL_MEMBER_EXISTS when the
     *         incoming one already sits there, COUNCIL_STATE_CONFLICT when
     *         scoring started and the outgoing member can still submit, or
     *         REVIEWER_MUST_BE_INDEPENDENT / SUPERVISOR_CANNOT_CHAIR_COUNCIL
     *         when the swap would break the independence rules
     */
    @Transactional
    public CouncilResponse replaceMember(UUID councilId, String outgoingLecturerId,
            String incomingLecturerId, String reason, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can manage council members");
        requireCouncilGovernanceAccess(councilId, actor);
        String cleanReason = requireReason(reason);
        requireText(outgoingLecturerId, "outgoingLecturerId");
        requireText(incomingLecturerId, "lecturerId");
        if (outgoingLecturerId.trim().equals(incomingLecturerId.trim())) {
            throw invalid("The replacement must be a different lecturer");
        }
        lockMutableCouncilRound(councilId);
        lockCouncil(councilId);
        Map<String, Object> seat = jdbc.query(
                "SELECT lecturer_id, member_role FROM thesis.thesis_council_member "
                        + "WHERE council_id = :councilId AND lecturer_id = :lecturerId",
                params().addValue("councilId", councilId).addValue("lecturerId", outgoingLecturerId.trim()),
                (rs, ignored) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("lecturer_id", rs.getString("lecturer_id"));
                    row.put("member_role", rs.getString("member_role"));
                    return row;
                }).stream().findFirst()
                .orElseThrow(() -> notFound("COUNCIL_MEMBER_NOT_FOUND", "Council member not found"));
        Integer councilScores = count(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE council_id = :councilId",
                params().addValue("councilId", councilId));
        boolean rosterFrozen = councilScores != null && councilScores > 0;
        if (rosterFrozen && lecturerExists(outgoingLecturerId.trim())) {
            throw conflict("COUNCIL_STATE_CONFLICT",
                    "Council membership cannot change after scoring has started");
        }
        String incoming = incomingLecturerId.trim();
        if (!lecturerExists(incoming)) {
            throw invalid("Unknown or inactive lecturer: " + incoming);
        }
        Integer alreadyMember = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId AND lecturer_id = :lecturerId",
                params().addValue("councilId", councilId).addValue("lecturerId", incoming));
        if (alreadyMember != null && alreadyMember > 0) {
            throw conflict("COUNCIL_MEMBER_EXISTS", "The lecturer already sits on this council");
        }
        // Same independence edges addMember enforces: the incoming member must
        // neither counter-review nor supervise any topic this council grades.
        Integer reviewingAssignedTopic = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_topic t ON t.id = ct.topic_id "
                        + "WHERE ct.council_id = :councilId AND t.gvpb_lecturer_id = :lecturerId",
                params().addValue("councilId", councilId).addValue("lecturerId", incoming));
        if (reviewingAssignedTopic != null && reviewingAssignedTopic > 0) {
            throw conflict("REVIEWER_MUST_BE_INDEPENDENT",
                    "The assigned counter-reviewer cannot sit on the defense council for this topic");
        }
        Integer supervisingAssignedTopic = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic ct "
                        + "JOIN thesis.thesis_topic_supervisor sup ON sup.topic_id = ct.topic_id "
                        + "JOIN campuscore_auth.\"Lecturer\" l "
                        + "  ON (l.\"id\" = sup.lecturer_id OR l.\"userId\" = sup.lecturer_id) "
                        + "WHERE ct.council_id = :councilId "
                        + "  AND (sup.lecturer_id = :lecturerId OR l.\"id\" = :lecturerId OR l.\"userId\" = :lecturerId)",
                params().addValue("councilId", councilId).addValue("lecturerId", incoming));
        if (supervisingAssignedTopic != null && supervisingAssignedTopic > 0) {
            throw conflict("SUPERVISOR_CANNOT_CHAIR_COUNCIL",
                    "The topic supervisor cannot chair or sit on the defense council for an assigned topic");
        }
        jdbc.update(
                "UPDATE thesis.thesis_council_member SET lecturer_id = :incoming "
                        + "WHERE council_id = :councilId AND lecturer_id = :outgoing",
                params().addValue("councilId", councilId)
                        .addValue("incoming", incoming).addValue("outgoing", outgoingLecturerId.trim()));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("lecturer_id", incoming);
        afterState.put("member_role", seat.get("member_role"));
        audit.record(subject(actor), null, "COUNCIL_MEMBER_REPLACED", "THESIS_COUNCIL", councilId.toString(),
                "Council seat " + seat.get("member_role") + " passed from " + outgoingLecturerId.trim()
                        + " to " + incoming + "; reason: " + cleanReason,
                seat, afterState);
        return getCouncil(councilId);
    }

    /**
     * Waives the counter-review requirement for one topic. The wedge this
     * closes: a missed GVPB deadline (or a reviewer who never submits) left
     * finalizeScores permanently rejected while publishResults still counted
     * the topic, so one reviewer absence held the whole round hostage. With
     * the waiver the topic finalizes on defense scores alone; the reason is
     * stored on the row so the lifted requirement stays self-describing.
     * Reversible through {@link #revokeCounterReviewWaiver} while the topic
     * has no final score.
     *
     * @param topicId topic whose counter-review requirement is lifted
     * @param reason governance reason (required, at most 500 characters)
     * @param actor caller JWT; a faculty head (topic in faculty scope) or admin
     * @return the topic with the waiver fields set
     * @throws DomainException with code GVPB_ALREADY_WAIVED when the topic is
     *         already waived, SCORE_ALREADY_FINALIZED for a graded topic, or
     *         VALIDATION_ERROR when the topic has no counter-review to waive
     */
    @Transactional
    public Map<String, Object> waiveCounterReview(UUID topicId, String reason, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can waive a counter-review");
        String cleanReason = requireReason(reason);
        if (topicId == null) {
            throw invalid("topicId is required");
        }
        lockMutableTopicRound(topicId);
        Map<String, Object> topic = one(
                "SELECT t.id, t.status, t.final_score, t.gvpb_lecturer_id, t.gvpb_waived_reason, "
                        + "t.result_status, r.gvpb_deadline, r.thesis_type "
                        + "FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.id = :topicId FOR UPDATE OF t",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        ThesisDeanScope.requireTopicAccess(jdbc, actor, topicId);
        if (!TopicStatus.PUBLISHED.name().equals(topic.get("status"))
                && !TopicStatus.APPROVED.name().equals(topic.get("status"))) {
            throw conflict("TOPIC_NOT_PUBLISHED", "Only published topics can waive a counter-review");
        }
        if (topic.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "A finalized topic cannot change its counter-review requirement");
        }
        if (topic.get("gvpb_waived_reason") != null) {
            throw conflict("GVPB_ALREADY_WAIVED", "The counter-review requirement is already waived");
        }
        RoundType roundType = null;
        try {
            roundType = RoundType.valueOf((String) topic.get("thesis_type"));
        } catch (IllegalArgumentException ignored) {
            roundType = null;
        }
        boolean requiresGvpb = (roundType != null && roundType.requiresGvpbDeadline()
                && topic.get("gvpb_deadline") != null) || topic.get("gvpb_lecturer_id") != null;
        if (!requiresGvpb) {
            throw invalid("This topic has no counter-review requirement to waive");
        }
        jdbc.update(
                "UPDATE thesis.thesis_topic SET gvpb_waived_reason = :reason, gvpb_waived_by = :by, "
                        + "gvpb_waived_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = :topicId",
                params().addValue("topicId", topicId).addValue("reason", cleanReason)
                        .addValue("by", subject(actor)));
        // The waiver overrides a derived REVIEW_FAILED marker; the stored
        // failing score remains for the record.
        syncReviewOutcome(topicId, subject(actor));
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("gvpb_waived_reason", null);
        beforeState.put("result_status", topic.get("result_status"));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("gvpb_waived_reason", cleanReason);
        afterState.put("result_status", topicResultStatus(topicId));
        audit.record(subject(actor), null, "GVPB_WAIVED", "THESIS_TOPIC", topicId.toString(),
                "Counter-review requirement waived; reason: " + cleanReason, beforeState, afterState);
        return afterState;
    }

    /**
     * Restores the counter-review requirement a waiver lifted. Only legal
     * while the topic has no final score; a stored sub-threshold counter-
     * review score marks the topic REVIEW_FAILED again.
     *
     * @param topicId topic whose counter-review requirement is re-armed
     * @param reason governance reason (required, at most 500 characters)
     * @param actor caller JWT; a faculty head (topic in faculty scope) or admin
     * @return the topic's counter-review state after revocation
     * @throws DomainException with code GVPB_NOT_WAIVED when no waiver exists
     *         or SCORE_ALREADY_FINALIZED for a graded topic
     */
    @Transactional
    public Map<String, Object> revokeCounterReviewWaiver(UUID topicId, String reason, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can restore a counter-review");
        String cleanReason = requireReason(reason);
        if (topicId == null) {
            throw invalid("topicId is required");
        }
        lockMutableTopicRound(topicId);
        Map<String, Object> topic = one(
                "SELECT t.id, t.final_score, t.gvpb_waived_reason, t.result_status "
                        + "FROM thesis.thesis_topic t "
                        + "JOIN thesis.thesis_registration_round r ON r.id = t.round_id "
                        + "WHERE t.id = :topicId FOR UPDATE OF t",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        ThesisDeanScope.requireTopicAccess(jdbc, actor, topicId);
        if (topic.get("gvpb_waived_reason") == null) {
            throw conflict("GVPB_NOT_WAIVED", "The counter-review requirement is not waived");
        }
        if (topic.get("final_score") != null) {
            throw conflict("SCORE_ALREADY_FINALIZED", "A finalized topic cannot change its counter-review requirement");
        }
        jdbc.update(
                "UPDATE thesis.thesis_topic SET gvpb_waived_reason = NULL, gvpb_waived_by = NULL, "
                        + "gvpb_waived_at = NULL, updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = :topicId",
                params().addValue("topicId", topicId));
        syncReviewOutcome(topicId, subject(actor));
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("gvpb_waived_reason", topic.get("gvpb_waived_reason"));
        beforeState.put("result_status", topic.get("result_status"));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("gvpb_waived_reason", null);
        afterState.put("result_status", topicResultStatus(topicId));
        audit.record(subject(actor), null, "GVPB_WAIVER_REVOKED", "THESIS_TOPIC", topicId.toString(),
                "Counter-review requirement restored; reason: " + cleanReason, beforeState, afterState);
        return afterState;
    }

    // ---------- student results ----------

    /**
     * Reads the published defense result of one student's approved groups in a round.
     *
     * @param roundId round whose results are read; it must be in the {@code RESULTS_PUBLISHED} status
     * @param studentId id of the student whose memberships are selected
     * @return one row per approved group the student belongs to, with title, final score,
     *         council name, and group leader
     * @throws DomainException with code VALIDATION_ERROR when the round id is missing,
     *         RESULTS_NOT_PUBLISHED before the round publishes, or ROUND_NOT_FOUND for an
     *         unknown round
     */
    @Transactional(readOnly = true)
    public List<StudentResultRow> studentResults(UUID roundId, String studentId) {
        if (roundId == null) {
            throw invalid("roundId is required");
        }
        String status = roundStatus(roundId);
        if (!"RESULTS_PUBLISHED".equals(status)) {
            throw conflict("RESULTS_NOT_PUBLISHED", "Results for this round have not been published yet");
        }
        return jdbc.query(
                "SELECT t.id AS topic_id, t.title, t.final_score, t.final_score_finalized_at, g.id AS group_id, g.leader_student_id, c.name AS council_name "
                        + "FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "LEFT JOIN thesis.thesis_council_topic ct ON ct.topic_id = t.id "
                        + "LEFT JOIN thesis.thesis_council c ON c.id = ct.council_id "
                        + "WHERE m.student_id = :studentId AND g.round_id = :roundId AND g.approval_status = 'APPROVED'",
                params().addValue("studentId", studentId).addValue("roundId", roundId),
                (rs, ignored) -> {
                    UUID topicId = UUID.fromString(rs.getString("topic_id"));
                    return new StudentResultRow(
                            UUID.fromString(rs.getString("group_id")),
                            rs.getString("title"),
                            rs.getBigDecimal("final_score"),
                            rs.getString("council_name"),
                            rs.getString("leader_student_id"),
                            evaluationComments(topicId));
                });
    }

    /**
     * Evaluation comments attached to a topic's score rows, in grading order.
     * Callers only reach this after the round published results, so reviewer
     * remarks never leak to students early.
     */
    private List<ScoreComment> evaluationComments(UUID topicId) {
        return jdbc.query(
                "SELECT component, comment FROM thesis.thesis_topic_score "
                        + "WHERE topic_id = :topicId AND comment IS NOT NULL ORDER BY graded_at, component",
                params().addValue("topicId", topicId),
                (rs, ignored) -> new ScoreComment(rs.getString("component"), rs.getString("comment")));
    }

    // ---------- helpers ----------

    /**
     * Deterministic outcome for the counter-review result: the latest GVPB
     * score decides. Below {@link #GVPB_PASS_THRESHOLD} the topic is marked
     * REVIEW_FAILED — a stored, admin-visible outcome instead of the old
     * silent wedge where finalize kept rejecting the topic while
     * publishResults kept counting it. A resubmission at or above the
     * threshold clears the marker. An explicit waiver or withdrawal always
     * dominates: those are governance decisions, not score derivations.
     */
    private void syncReviewOutcome(UUID topicId, String actorId) {
        Map<String, Object> row = one(
                "SELECT gvpb_waived_reason, result_status FROM thesis.thesis_topic WHERE id = :topicId",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        String current = (String) row.get("result_status");
        if (RESULT_WITHDRAWN.equals(current)) {
            return;
        }
        List<BigDecimal> scores = jdbc.queryForList(
                "SELECT score FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND component = :component "
                        + "ORDER BY graded_at DESC, created_at DESC",
                params().addValue("topicId", topicId).addValue("component", GVPB_COMPONENT),
                BigDecimal.class);
        BigDecimal latest = scores.isEmpty() ? null : scores.get(0);
        boolean waived = row.get("gvpb_waived_reason") != null;
        boolean failed = !waived && latest != null && latest.compareTo(GVPB_PASS_THRESHOLD) < 0;
        if (failed && !RESULT_REVIEW_FAILED.equals(current)) {
            jdbc.update(
                    "UPDATE thesis.thesis_topic SET result_status = 'REVIEW_FAILED', "
                            + "result_reason = :reason, result_by = :by, result_at = CURRENT_TIMESTAMP, "
                            + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                            + "WHERE id = :topicId AND final_score IS NULL "
                            + "AND result_status IS DISTINCT FROM 'WITHDRAWN'",
                    params().addValue("topicId", topicId)
                            .addValue("reason", "Counter-review score "
                                    + latest.stripTrailingZeros().toPlainString()
                                    + " is below the 5.0 pass threshold")
                            .addValue("by", actorId));
        } else if (!failed && RESULT_REVIEW_FAILED.equals(current)) {
            jdbc.update(
                    "UPDATE thesis.thesis_topic SET result_status = NULL, result_reason = NULL, "
                            + "result_by = NULL, result_at = NULL, updated_at = CURRENT_TIMESTAMP, "
                            + "version = version + 1 "
                            + "WHERE id = :topicId AND final_score IS NULL AND result_status = 'REVIEW_FAILED'",
                    params().addValue("topicId", topicId));
        }
    }

    private String topicResultStatus(UUID topicId) {
        List<String> rows = jdbc.queryForList(
                "SELECT result_status FROM thesis.thesis_topic WHERE id = :topicId",
                params().addValue("topicId", topicId), String.class);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String requireReason(String value) {
        String reason = normalize(value);
        if (reason.isBlank() || reason.length() > 500) {
            throw invalid("reason is required and must contain at most 500 characters");
        }
        return reason;
    }

    private boolean councilHasTopic(UUID councilId, UUID topicId) {
        Integer count = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE council_id = :councilId AND topic_id = :topicId",
                params().addValue("councilId", councilId).addValue("topicId", topicId));
        return count != null && count > 0;
    }

    /** Brief R8: nobody grades a topic they supervise. */
    private void requireNotSupervisor(UUID topicId, String lecturerId) {
        // lecturer_id may store either a Lecturer.id or a legacy User.id, so
        // resolve the caller's profile and match either stored key form.
        Integer supervising = count(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor sup "
                        + "JOIN campuscore_auth.\"Lecturer\" l "
                        + "  ON (l.\"id\" = :lecturerId OR l.\"userId\" = :lecturerId) "
                        + " AND (sup.lecturer_id = l.\"id\" OR sup.lecturer_id = l.\"userId\") "
                        + "WHERE sup.topic_id = :topicId",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId));
        if (supervising != null && supervising > 0) {
            throw new DomainException(HttpStatus.FORBIDDEN, "SUPERVISOR_CANNOT_GRADE",
                    "A topic supervisor cannot grade their own topic");
        }
    }

    private String requireCouncilLecturer(UUID councilId, Jwt actor) {
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (!StringUtils.hasText(lecturerId) || !hasMember(councilId, lecturerId)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "COUNCIL_MEMBER_REQUIRED",
                    "Only a council member can grade the assigned topics");
        }
        return lecturerId;
    }

    private void requireCouncilRole(UUID councilId, Jwt actor, String role, String message) {
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (!StringUtils.hasText(lecturerId) || !hasCouncilRole(councilId, lecturerId, role)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "COUNCIL_ROLE_REQUIRED", message);
        }
    }

    private void requireScoreReadAccess(UUID councilId, UUID topicId, Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        // Round-4 council sweep: SUPER_ADMIN is governance staff everywhere
        // ADMIN is (same class of gap as ThesisReportService, fixed in
        // be5ec0c0) — the controller already advertises SUPER_ADMIN on these
        // routes while the service answered 403.
        if (roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"))) {
            return;
        }
        if (roles != null && roles.contains("TRUONG_KHOA")) {
            // A dean reads unpublished scores only inside their faculty (or on
            // topics they supervise); the unconditional bypass let any dean
            // read foreign-faculty defense scores.
            ThesisDeanScope.requireTopicAccess(jdbc, actor, topicId);
            return;
        }
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (StringUtils.hasText(lecturerId) && hasMember(councilId, lecturerId)) {
            return;
        }
        throw new DomainException(HttpStatus.FORBIDDEN, "COUNCIL_SCORE_ACCESS_REQUIRED",
                "Only council members or governance staff can read topic scores");
    }

    /**
     * A member who can still grade: the seat resolves to an active lecturer
     * profile (either stored key form). Deactivated or profile-less members
     * can never submit again — hasMember gates them out at write time — so
     * leaving them in the expected set wedges SCORES_INCOMPLETE forever.
     */
    private static String activeMemberClause(String keyColumn) {
        return "AND EXISTS ("
                + "  SELECT 1 FROM campuscore_auth.\"Lecturer\" lm "
                + "  WHERE (lm.\"id\" = " + keyColumn + " OR lm.\"userId\" = " + keyColumn + ") "
                + "    AND lm.\"isActive\" = TRUE"
                + ") ";
    }

    private int eligibleGraderCount(UUID councilId, UUID topicId) {
        return count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member m "
                        + "WHERE m.council_id = :councilId "
                        + activeMemberClause("m.lecturer_id")
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM thesis.thesis_topic_supervisor sup "
                        + "  JOIN campuscore_auth.\"Lecturer\" l "
                        + "    ON (l.\"id\" = m.lecturer_id OR l.\"userId\" = m.lecturer_id) "
                        + "   AND (sup.lecturer_id = l.\"id\" OR sup.lecturer_id = l.\"userId\") "
                        + "  WHERE sup.topic_id = :topicId"
                        + ")",
                params().addValue("councilId", councilId).addValue("topicId", topicId));
    }

    private int submittedEligibleScoreCount(UUID councilId, UUID topicId) {
        return count(
                "SELECT COUNT(DISTINCT s.lecturer_id) FROM thesis.thesis_topic_score s "
                        + "JOIN thesis.thesis_council_member m "
                        + "  ON m.council_id = s.council_id AND m.lecturer_id = s.lecturer_id "
                        + "WHERE s.council_id = :councilId AND s.topic_id = :topicId AND s.component = :component "
                        + activeMemberClause("s.lecturer_id")
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM thesis.thesis_topic_supervisor sup "
                        + "  JOIN campuscore_auth.\"Lecturer\" l "
                        + "    ON (l.\"id\" = s.lecturer_id OR l.\"userId\" = s.lecturer_id) "
                        + "   AND (sup.lecturer_id = l.\"id\" OR sup.lecturer_id = l.\"userId\") "
                        + "  WHERE sup.topic_id = :topicId"
                        + ")",
                params().addValue("councilId", councilId).addValue("topicId", topicId)
                        .addValue("component", SCORE_COMPONENT));
    }

    private boolean hasMember(UUID councilId, String lecturerId) {
        Integer count = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member m "
                        + "JOIN campuscore_auth.\"Lecturer\" l ON l.\"id\" = m.lecturer_id AND l.\"isActive\" = TRUE "
                        + "WHERE m.council_id = :councilId AND m.lecturer_id = :lecturerId",
                params().addValue("councilId", councilId).addValue("lecturerId", lecturerId));
        return count != null && count > 0;
    }

    private boolean hasCouncilRole(UUID councilId, String lecturerId, String role) {
        Integer count = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member m "
                        + "JOIN campuscore_auth.\"Lecturer\" l ON l.\"id\" = m.lecturer_id AND l.\"isActive\" = TRUE "
                        + "WHERE m.council_id = :councilId AND m.lecturer_id = :lecturerId AND m.member_role = :role",
                params().addValue("councilId", councilId).addValue("lecturerId", lecturerId).addValue("role", role));
        return count != null && count > 0;
    }

    private boolean hasRoleSeat(UUID councilId, String role) {
        Integer count = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId AND member_role = :role",
                params().addValue("councilId", councilId).addValue("role", role));
        return count != null && count > 0;
    }

    private void requireRosterMutable(UUID councilId) {
        Integer submittedScores = count(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE council_id = :councilId",
                params().addValue("councilId", councilId));
        if (submittedScores != null && submittedScores > 0) {
            throw conflict("COUNCIL_STATE_CONFLICT",
                    "Council membership cannot change after scoring has started");
        }
    }

    private void requireGovernanceRole(Jwt actor, String message) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        boolean allowed = roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")
                || roles.contains("TRUONG_KHOA"));
        if (!allowed) {
            throw new DomainException(HttpStatus.FORBIDDEN, "COUNCIL_ADMIN_REQUIRED", message);
        }
    }

    /**
     * Defers a fan-out until the mutation commits; the notifier runs
     * REQUIRES_NEW, so a mid-transaction call would survive a later rollback.
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

    private Map<String, Object> lockCouncil(UUID councilId) {
        return one("SELECT id, round_id, name, status, created_by FROM thesis.thesis_council WHERE id = :id FOR UPDATE",
                params().addValue("id", councilId), "COUNCIL_NOT_FOUND", "Defense council not found");
    }

    private void lockTopic(UUID topicId) {
        one("SELECT id FROM thesis.thesis_topic WHERE id = :topicId FOR UPDATE",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
    }

    private UUID councilRound(UUID councilId) {
        return (UUID) one("SELECT round_id FROM thesis.thesis_council WHERE id = :id",
                params().addValue("id", councilId), "COUNCIL_NOT_FOUND", "Defense council not found").get("round_id");
    }

    private void lockMutableRound(UUID roundId) {
        Map<String, Object> round = one(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", roundId), "ROUND_NOT_FOUND", "Thesis registration round not found");
        String status = (String) round.get("status");
        if (RoundStatus.CANCELLED.name().equals(status)
                || RoundStatus.RESULTS_PUBLISHED.name().equals(status)
                || RoundStatus.CLOSED.name().equals(status)) {
            throw conflict("ROUND_STATE_CONFLICT", "The round no longer accepts changes");
        }
    }

    private void lockMutableCouncilRound(UUID councilId) {
        lockMutableRound(councilRound(councilId));
    }

    private void lockMutableTopicRound(UUID topicId) {
        UUID roundId = (UUID) one("SELECT round_id FROM thesis.thesis_topic WHERE id = :id",
                params().addValue("id", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found").get("round_id");
        lockMutableRound(roundId);
    }

    private String roundStatus(UUID roundId) {
        Map<String, Object> round = one("SELECT status FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", roundId), "ROUND_NOT_FOUND", "Thesis registration round not found");
        return (String) round.get("status");
    }

    private boolean lecturerExists(String lecturerId) {
        Integer count = count(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = :lecturerId AND \"isActive\" = TRUE",
                params().addValue("lecturerId", lecturerId));
        return count != null && count > 0;
    }

    private static String normalizeRole(String role) {
        String normalized = normalize(role).toUpperCase(java.util.Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            throw invalid("memberRole is required");
        }
        if (!"CHAIR".equals(normalized) && !"SECRETARY".equals(normalized) && !"MEMBER".equals(normalized)) {
            throw invalid("memberRole must be CHAIR, SECRETARY or MEMBER");
        }
        return normalized;
    }

    private int count(String sql, MapSqlParameterSource parameters) {
        Integer result = jdbc.queryForObject(sql, parameters, Integer.class);
        return result == null ? 0 : result;
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource parameters, String code, String message) {
        try {
            return jdbc.queryForMap(sql, parameters);
        } catch (EmptyResultDataAccessException exception) {
            throw notFound(code, message);
        }
    }

    private static String subject(Jwt actor) {
        return actor == null || actor.getSubject() == null ? "" : actor.getSubject();
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Instant instantOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw invalid(name + " is required");
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

    public record CouncilMember(String lecturerId, String memberRole) { }

    public record CouncilResponse(
            UUID id,
            UUID roundId,
            String name,
            String status,
            List<CouncilMember> members,
            List<UUID> topicIds) { }

    public record ScoreResponse(UUID topicId, String lecturerId, String component, BigDecimal score, String comment) { }

    public record TopicResult(UUID topicId, BigDecimal finalScore, String finalizedBy, Instant finalizedAt, String status) { }

    public record StudentResultRow(
            UUID groupId,
            String topicTitle,
            BigDecimal finalScore,
            String councilName,
            String leaderStudentId,
            List<ScoreComment> comments) { }

    public record ScoreComment(String component, String comment) { }
}
