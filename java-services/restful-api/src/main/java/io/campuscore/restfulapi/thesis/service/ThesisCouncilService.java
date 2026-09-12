package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisCouncilService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- councils ----------

    @Transactional
    public CouncilResponse createCouncil(UUID roundId, String name, Jwt actor) {
        if (roundId == null) {
            throw invalid("roundId is required");
        }
        requireText(name, "name");
        requireGovernanceRole(actor, "Only a faculty head or admin can create a council");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (:id, :roundId, :name, 'ACTIVE', :createdBy)",
                params().addValue("id", id).addValue("roundId", roundId)
                        .addValue("name", name.trim()).addValue("createdBy", subject(actor)));
        return getCouncil(id);
    }

    @Transactional
    public CouncilResponse addMember(UUID councilId, String lecturerId, String memberRole, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can manage council members");
        lockCouncil(councilId);
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
        jdbc.update(
                "INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) "
                        + "VALUES (:id, :councilId, :lecturerId, :role)",
                params().addValue("id", UUID.randomUUID()).addValue("councilId", councilId)
                        .addValue("lecturerId", lecturerId).addValue("role", role));
        return getCouncil(councilId);
    }

    @Transactional
    public CouncilResponse removeMember(UUID councilId, String lecturerId, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can manage council members");
        lockCouncil(councilId);
        requireText(lecturerId, "lecturerId");
        Integer assignedTopics = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE council_id = :councilId",
                params().addValue("councilId", councilId));
        if (assignedTopics != null && assignedTopics > 0) {
            if (hasCouncilRole(councilId, lecturerId, "CHAIR")) {
                throw conflict("COUNCIL_CHAIR_REQUIRED",
                        "The chair cannot leave a council that has assigned topics; reassign the topics first");
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
        return getCouncil(councilId);
    }

    @Transactional
    public CouncilResponse assignTopic(UUID councilId, UUID topicId, Jwt actor) {
        requireGovernanceRole(actor, "Only a faculty head or admin can assign topics to a council");
        lockCouncil(councilId);
        if (topicId == null) {
            throw invalid("topicId is required");
        }
        Integer size = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId",
                params().addValue("councilId", councilId));
        if (size == null || size < MIN_COUNCIL_SIZE || size > MAX_COUNCIL_SIZE
                || !hasRoleSeat(councilId, "CHAIR") || !hasRoleSeat(councilId, "SECRETARY")) {
            throw conflict("COUNCIL_INCOMPLETE",
                    "A council needs three to five members including one chair and one secretary before it can grade topics");
        }
        Map<String, Object> topic = one(
                "SELECT id, round_id, final_score FROM thesis.thesis_topic WHERE id = :topicId",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
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
        Integer supervisorChair = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member m "
                        + "JOIN thesis.thesis_topic_supervisor sup ON sup.lecturer_id = m.lecturer_id "
                        + "WHERE m.council_id = :councilId AND sup.topic_id = :topicId AND m.member_role = 'CHAIR'",
                params().addValue("councilId", councilId).addValue("topicId", topicId));
        if (supervisorChair != null && supervisorChair > 0) {
            throw conflict("SUPERVISOR_CANNOT_CHAIR_COUNCIL",
                    "The topic supervisor cannot chair the defense council for this topic");
        }
        jdbc.update(
                "INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) "
                        + "VALUES (:id, :councilId, :topicId, :assignedBy)",
                params().addValue("id", UUID.randomUUID()).addValue("councilId", councilId)
                        .addValue("topicId", topicId).addValue("assignedBy", subject(actor)));
        return getCouncil(councilId);
    }

    @Transactional(readOnly = true)
    public List<CouncilResponse> listByRound(UUID roundId) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM thesis.thesis_council WHERE round_id = :roundId ORDER BY created_at",
                params().addValue("roundId", roundId), UUID.class);
        return ids.stream().map(this::getCouncil).toList();
    }

    @Transactional(readOnly = true)
    public CouncilResponse getCouncil(UUID councilId) {
        Map<String, Object> council = one(
                "SELECT id, round_id, name, status, created_by FROM thesis.thesis_council WHERE id = :id",
                params().addValue("id", councilId), "COUNCIL_NOT_FOUND", "Defense council not found");
        List<CouncilMember> members = jdbc.query(
                "SELECT lecturer_id, member_role FROM thesis.thesis_council_member "
                        + "WHERE council_id = :councilId ORDER BY CASE member_role WHEN 'CHAIR' THEN 0 "
                        + "WHEN 'SECRETARY' THEN 1 ELSE 2 END, created_at",
                params().addValue("councilId", councilId),
                (rs, ignored) -> new CouncilMember(rs.getString("lecturer_id"), rs.getString("member_role")));
        List<UUID> topicIds = jdbc.queryForList(
                "SELECT topic_id FROM thesis.thesis_council_topic WHERE council_id = :councilId ORDER BY created_at",
                params().addValue("councilId", councilId), UUID.class);
        return new CouncilResponse(
                (UUID) council.get("id"),
                (UUID) council.get("round_id"),
                (String) council.get("name"),
                (String) council.get("status"),
                members, topicIds);
    }

    // ---------- scoring ----------

    @Transactional
    public ScoreResponse submitScore(UUID councilId, UUID topicId, String component, BigDecimal score, Jwt actor) {
        String lecturerId = requireCouncilLecturer(councilId, actor);
        if (!councilHasTopic(councilId, topicId)) {
            throw conflict("TOPIC_NOT_ASSIGNED", "This council has not been assigned the topic");
        }
        requireNotSupervisor(topicId, lecturerId);
        if (score == null || score.doubleValue() < 0 || score.doubleValue() > 10) {
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
        jdbc.update(
                "DELETE FROM thesis.thesis_topic_score WHERE topic_id = :topicId AND lecturer_id = :lecturerId AND component = :component",
                params().addValue("topicId", topicId).addValue("lecturerId", lecturerId)
                        .addValue("component", resolvedComponent));
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score) "
                        + "VALUES (:id, :topicId, :councilId, :lecturerId, :component, :score)",
                params().addValue("id", UUID.randomUUID()).addValue("topicId", topicId)
                        .addValue("councilId", councilId).addValue("lecturerId", lecturerId)
                        .addValue("component", resolvedComponent).addValue("score", score));
        return new ScoreResponse(topicId, lecturerId, resolvedComponent, score);
    }

    @Transactional(readOnly = true)
    public List<ScoreResponse> listScores(UUID councilId, UUID topicId, Jwt actor) {
        requireScoreReadAccess(councilId, actor);
        if (!councilHasTopic(councilId, topicId)) {
            throw conflict("TOPIC_NOT_ASSIGNED", "This council has not been assigned the topic");
        }
        return jdbc.query(
                "SELECT lecturer_id, component, score FROM thesis.thesis_topic_score "
                        + "WHERE council_id = :councilId AND topic_id = :topicId AND component = :component "
                        + "ORDER BY graded_at",
                params().addValue("councilId", councilId).addValue("topicId", topicId)
                        .addValue("component", SCORE_COMPONENT),
                (rs, ignored) -> new ScoreResponse(topicId, rs.getString("lecturer_id"),
                        rs.getString("component"), rs.getBigDecimal("score")));
    }

    @Transactional
    public TopicResult finalizeScores(UUID councilId, UUID topicId, Jwt actor) {
        requireCouncilRole(councilId, actor, "CHAIR", "Only the council chair can finalize the topic score");
        if (!councilHasTopic(councilId, topicId)) {
            throw conflict("TOPIC_NOT_ASSIGNED", "This council has not been assigned the topic");
        }
        // Lock topic row to prevent concurrent score submission during finalization
        one("SELECT id FROM thesis.thesis_topic WHERE id = :topicId FOR UPDATE",
                params().addValue("topicId", topicId), "TOPIC_NOT_FOUND", "Thesis topic not found");
        int expectedScores = eligibleGraderCount(councilId, topicId);
        int submittedScores = submittedEligibleScoreCount(councilId, topicId);
        if (expectedScores == 0 || submittedScores < expectedScores) {
            throw conflict("SCORES_INCOMPLETE",
                    "Every eligible council member must submit a defense score before finalizing");
        }
        List<BigDecimal> scores = jdbc.queryForList(
                "SELECT s.score FROM thesis.thesis_topic_score s "
                        + "JOIN thesis.thesis_council_member m "
                        + "  ON m.council_id = s.council_id AND m.lecturer_id = s.lecturer_id "
                        + "WHERE s.council_id = :councilId AND s.topic_id = :topicId AND s.component = :component "
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM thesis.thesis_topic_supervisor sup "
                        + "  WHERE sup.topic_id = :topicId AND sup.lecturer_id = s.lecturer_id"
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
        return new TopicResult(topicId, average, subject(actor), Instant.now(), "GRADED");
    }

    // ---------- student results ----------

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
                "SELECT t.title, t.final_score, t.final_score_finalized_at, g.id AS group_id, g.leader_student_id, c.name AS council_name "
                        + "FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "LEFT JOIN thesis.thesis_council_topic ct ON ct.topic_id = t.id "
                        + "LEFT JOIN thesis.thesis_council c ON c.id = ct.council_id "
                        + "WHERE m.student_id = :studentId AND g.round_id = :roundId AND g.approval_status = 'APPROVED'",
                params().addValue("studentId", studentId).addValue("roundId", roundId),
                (rs, ignored) -> new StudentResultRow(
                        UUID.fromString(rs.getString("group_id")),
                        rs.getString("title"),
                        rs.getBigDecimal("final_score"),
                        rs.getString("council_name"),
                        rs.getString("leader_student_id")));
    }

    // ---------- helpers ----------

    private boolean councilHasTopic(UUID councilId, UUID topicId) {
        Integer count = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE council_id = :councilId AND topic_id = :topicId",
                params().addValue("councilId", councilId).addValue("topicId", topicId));
        return count != null && count > 0;
    }

    /** Brief R8: nobody grades a topic they supervise. */
    private void requireNotSupervisor(UUID topicId, String lecturerId) {
        Integer supervising = count(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = :topicId AND lecturer_id = :lecturerId",
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

    private void requireScoreReadAccess(UUID councilId, Jwt actor) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        if (roles != null && (roles.contains("ADMIN") || roles.contains("TRUONG_KHOA"))) {
            return;
        }
        String lecturerId = normalize(actor == null ? null : actor.getClaimAsString("lecturerId"));
        if (StringUtils.hasText(lecturerId) && hasMember(councilId, lecturerId)) {
            return;
        }
        throw new DomainException(HttpStatus.FORBIDDEN, "COUNCIL_SCORE_ACCESS_REQUIRED",
                "Only council members or governance staff can read topic scores");
    }

    private int eligibleGraderCount(UUID councilId, UUID topicId) {
        return count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member m "
                        + "WHERE m.council_id = :councilId "
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM thesis.thesis_topic_supervisor sup "
                        + "  WHERE sup.topic_id = :topicId AND sup.lecturer_id = m.lecturer_id"
                        + ")",
                params().addValue("councilId", councilId).addValue("topicId", topicId));
    }

    private int submittedEligibleScoreCount(UUID councilId, UUID topicId) {
        return count(
                "SELECT COUNT(DISTINCT s.lecturer_id) FROM thesis.thesis_topic_score s "
                        + "JOIN thesis.thesis_council_member m "
                        + "  ON m.council_id = s.council_id AND m.lecturer_id = s.lecturer_id "
                        + "WHERE s.council_id = :councilId AND s.topic_id = :topicId AND s.component = :component "
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM thesis.thesis_topic_supervisor sup "
                        + "  WHERE sup.topic_id = :topicId AND sup.lecturer_id = s.lecturer_id"
                        + ")",
                params().addValue("councilId", councilId).addValue("topicId", topicId)
                        .addValue("component", SCORE_COMPONENT));
    }

    private boolean hasMember(UUID councilId, String lecturerId) {
        Integer count = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId AND lecturer_id = :lecturerId",
                params().addValue("councilId", councilId).addValue("lecturerId", lecturerId));
        return count != null && count > 0;
    }

    private boolean hasCouncilRole(UUID councilId, String lecturerId, String role) {
        Integer count = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId AND lecturer_id = :lecturerId AND member_role = :role",
                params().addValue("councilId", councilId).addValue("lecturerId", lecturerId).addValue("role", role));
        return count != null && count > 0;
    }

    private boolean hasRoleSeat(UUID councilId, String role) {
        Integer count = count(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = :councilId AND member_role = :role",
                params().addValue("councilId", councilId).addValue("role", role));
        return count != null && count > 0;
    }

    private void requireGovernanceRole(Jwt actor, String message) {
        List<String> roles = actor == null ? null : actor.getClaimAsStringList("roles");
        boolean allowed = roles != null && (roles.contains("ADMIN") || roles.contains("TRUONG_KHOA"));
        if (!allowed) {
            throw new DomainException(HttpStatus.FORBIDDEN, "COUNCIL_ADMIN_REQUIRED", message);
        }
    }

    private Map<String, Object> lockCouncil(UUID councilId) {
        return one("SELECT id, round_id, name, status, created_by FROM thesis.thesis_council WHERE id = :id FOR UPDATE",
                params().addValue("id", councilId), "COUNCIL_NOT_FOUND", "Defense council not found");
    }

    private UUID councilRound(UUID councilId) {
        return (UUID) one("SELECT round_id FROM thesis.thesis_council WHERE id = :id",
                params().addValue("id", councilId), "COUNCIL_NOT_FOUND", "Defense council not found").get("round_id");
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

    public record ScoreResponse(UUID topicId, String lecturerId, String component, BigDecimal score) { }

    public record TopicResult(UUID topicId, BigDecimal finalScore, String finalizedBy, Instant finalizedAt, String status) { }

    public record StudentResultRow(
            UUID groupId,
            String topicTitle,
            BigDecimal finalScore,
            String councilName,
            String leaderStudentId) { }
}
