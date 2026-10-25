package io.campuscore.restfulapi.thesis.repository;

import static io.campuscore.restfulapi.thesis.repository.ThesisMutationSql.one;
import static io.campuscore.restfulapi.thesis.repository.ThesisMutationSql.params;

import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.StudentSearchResponse;
import io.campuscore.restfulapi.thesis.model.GroupRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Write-side statements for thesis group rosters and the member directory:
 * the group row lock every group mutation starts from, roster inserts and
 * removals, approval/rejection/withdrawal writes, the one-seat membership
 * checks, and the leader-facing student search. Group-first lock order is
 * preserved by every statement that also touches a round or student row.
 */
@Repository
@Profile("persistence")
public class ThesisGroupMutationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisGroupMutationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locked group snapshot every group mutation and guard decides on. */
    public GroupRow lockGroupRow(UUID id) {
        Map<String, Object> row = one(jdbc,
                "SELECT id, round_id, leader_student_id, topic_id, status, approval_status FROM thesis.thesis_group WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "GROUP_NOT_FOUND", "Thesis group not found");
        return new GroupRow((UUID) row.get("id"), (UUID) row.get("round_id"), (String) row.get("leader_student_id"),
                (UUID) row.get("topic_id"),
                io.campuscore.restfulapi.thesis.model.GroupStatus.valueOf((String) row.get("status")),
                row.get("approval_status") == null
                        ? io.campuscore.restfulapi.thesis.model.ApprovalStatus.PENDING
                        : io.campuscore.restfulapi.thesis.model.ApprovalStatus.valueOf((String) row.get("approval_status")));
    }

    public void insertGroup(UUID id, UUID roundId, String studentId) {
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) VALUES (:id, :roundId, :studentId, 'DRAFT', 'PENDING')",
                params().addValue("id", id).addValue("roundId", roundId).addValue("studentId", studentId));
    }

    public void insertLeaderMember(UUID id, UUID groupId, UUID roundId, String studentId) {
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, 1, TRUE)",
                params().addValue("id", id).addValue("groupId", groupId).addValue("roundId", roundId).addValue("studentId", studentId));
    }

    public void insertMember(UUID id, UUID groupId, UUID roundId, String studentId, int memberOrder) {
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) VALUES (:id, :groupId, :roundId, :studentId, :memberOrder, FALSE)",
                params().addValue("id", id).addValue("groupId", groupId).addValue("roundId", roundId).addValue("studentId", studentId).addValue("memberOrder", memberOrder));
    }

    /** External members carry their declared identity on the membership row. */
    public void insertExternalMember(UUID id, UUID groupId, UUID roundId, String syntheticId, int memberOrder,
            String displayName, String contact) {
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader, display_name, contact, is_external) VALUES (:id, :groupId, :roundId, :studentId, :memberOrder, FALSE, :displayName, :contact, TRUE)",
                params()
                        .addValue("id", id)
                        .addValue("groupId", groupId)
                        .addValue("roundId", roundId)
                        .addValue("studentId", syntheticId)
                        .addValue("memberOrder", memberOrder)
                        .addValue("displayName", displayName)
                        .addValue("contact", contact));
    }

    /** Bumps version so concurrent roster edits cannot lose the write. */
    public void touchGroup(UUID groupId) {
        jdbc.update("UPDATE thesis.thesis_group SET updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId",
                params().addValue("groupId", groupId));
    }

    public int deleteMember(UUID groupId, String studentId) {
        return jdbc.update("DELETE FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId",
                params().addValue("groupId", groupId).addValue("studentId", studentId));
    }

    public int countMembers(UUID groupId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId", "groupId", groupId);
    }

    /** Whether a specific student already sits on this roster. */
    public Integer countMemberPresence(UUID groupId, String studentId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId AND student_id = :studentId",
                params().addValue("groupId", groupId).addValue("studentId", studentId), Integer.class);
    }

    /** Submitted report rows of one group (approval-withdrawal downstream check). */
    public int countGroupReports(UUID groupId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group_report WHERE group_id = :groupId", "groupId", groupId);
    }

    public int countLeaders(UUID groupId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = :groupId AND is_leader = TRUE",
                "groupId", groupId);
    }

    /** Exactly one leader row that also matches the group's leader_student_id. */
    public int countMatchingLeaders(UUID groupId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group_member gm "
                + "JOIN thesis.thesis_group g ON g.id = gm.group_id "
                + "WHERE gm.group_id = :groupId AND gm.is_leader = TRUE AND gm.student_id = g.leader_student_id",
                "groupId", groupId);
    }

    /** Locked member row (successor checks and leader removals). */
    public Map<String, Object> lockMemberRow(UUID groupId, String studentId, String missingMessage) {
        return one(jdbc,
                "SELECT id, is_leader FROM thesis.thesis_group_member "
                        + "WHERE group_id = :groupId AND student_id = :studentId FOR UPDATE",
                params().addValue("groupId", groupId).addValue("studentId", studentId),
                "STUDENT_NOT_IN_GROUP", missingMessage);
    }

    public void promoteSuccessor(UUID groupId, String successorId) {
        jdbc.update("UPDATE thesis.thesis_group_member SET is_leader = TRUE "
                        + "WHERE group_id = :groupId AND student_id = :studentId",
                params().addValue("groupId", groupId).addValue("studentId", successorId));
    }

    public void setGroupLeader(UUID groupId, String successor) {
        jdbc.update("UPDATE thesis.thesis_group SET leader_student_id = :successor WHERE id = :groupId",
                params().addValue("groupId", groupId).addValue("successor", successor));
    }

    /** Records the documented below-minimum exception marker on the group. */
    public void setSizeException(UUID groupId, String reason, String by) {
        jdbc.update(
                "UPDATE thesis.thesis_group SET size_exception_reason = :reason, size_exception_by = :by, "
                        + "size_exception_at = CURRENT_TIMESTAMP WHERE id = :groupId",
                params().addValue("groupId", groupId).addValue("reason", reason).addValue("by", by));
    }

    /** Cancellation frees every member's active_participation slot. */
    public void deactivateMembers(UUID groupId) {
        jdbc.update("UPDATE thesis.thesis_group_member SET active_participation = FALSE WHERE group_id = :groupId AND active_participation = TRUE",
                params().addValue("groupId", groupId));
    }

    public void updateStatus(UUID groupId, String status) {
        jdbc.update("UPDATE thesis.thesis_group SET status = :status, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId",
                params().addValue("status", status).addValue("groupId", groupId));
    }

    /** Topic binding write; a topic switch also resets the approval trace. */
    public void bindTopic(UUID groupId, UUID topicId, boolean isNewTopic) {
        jdbc.update("UPDATE thesis.thesis_group SET topic_id = :topicId, status = CASE WHEN status = 'DRAFT' THEN 'SUBMITTED' ELSE status END, approval_status = CASE WHEN approval_status = 'REJECTED' OR :isNewTopic THEN 'PENDING' ELSE approval_status END, approved_by = CASE WHEN :isNewTopic THEN NULL ELSE approved_by END, approved_at = CASE WHEN :isNewTopic THEN NULL ELSE approved_at END, rejection_reason = CASE WHEN approval_status = 'REJECTED' OR :isNewTopic THEN NULL ELSE rejection_reason END, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :groupId",
                params().addValue("topicId", topicId).addValue("groupId", groupId).addValue("isNewTopic", isNewTopic));
    }

    public void reassignTopic(UUID groupId, UUID topicId) {
        jdbc.update("UPDATE thesis.thesis_group SET topic_id = :topicId, updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE id = :groupId",
                params().addValue("topicId", topicId).addValue("groupId", groupId));
    }

    /** Approval flip; a size-exception reason lands in the same statement as the approval. */
    public int approveGroup(UUID groupId, String actor, String sizeReason) {
        if (sizeReason == null) {
            return jdbc.update("UPDATE thesis.thesis_group SET approval_status='APPROVED', approved_by=:actor, approved_at=CURRENT_TIMESTAMP, rejection_reason=NULL, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                    params().addValue("id", groupId).addValue("actor", actor));
        }
        return jdbc.update("UPDATE thesis.thesis_group SET approval_status='APPROVED', approved_by=:actor, approved_at=CURRENT_TIMESTAMP, rejection_reason=NULL, size_exception_reason=:sizeReason, size_exception_by=:actor, size_exception_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                params().addValue("id", groupId).addValue("actor", actor).addValue("sizeReason", sizeReason));
    }

    public int rejectGroup(UUID groupId, String reason) {
        return jdbc.update("UPDATE thesis.thesis_group SET approval_status='REJECTED', approved_by=NULL, approved_at=NULL, rejection_reason=:reason, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=:id AND approval_status='PENDING'",
                params().addValue("id", groupId).addValue("reason", reason));
    }

    public int clearApproval(UUID groupId) {
        return jdbc.update("UPDATE thesis.thesis_group SET approval_status='PENDING',"
                        + " approved_by=NULL, approved_at=NULL, rejection_reason=NULL,"
                        + " size_exception_reason=NULL, size_exception_by=NULL, size_exception_at=NULL,"
                        + " updated_at=CURRENT_TIMESTAMP, version=version+1"
                        + " WHERE id=:id AND approval_status='APPROVED'",
                params().addValue("id", groupId));
    }

    /** Same-round double-seat check; a cancelled group holds no seat. */
    public int countSameRoundMembership(UUID roundId, String studentId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group_member m "
                + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                + "WHERE m.round_id = :roundId AND m.student_id = :studentId AND g.status <> 'CANCELLED'",
                "roundId", roundId, "studentId", studentId);
    }

    /** Cross-round seat check against every round that has not finished. */
    public int countOtherActiveMembership(String studentId, UUID roundId) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group_member m "
                + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                + "JOIN thesis.thesis_registration_round r ON r.id = m.round_id "
                + "WHERE m.student_id = :studentId AND m.round_id <> :roundId "
                + "AND g.status <> 'CANCELLED' "
                + "AND r.status IN ('DRAFT', 'PROPOSAL_OPEN', 'PROPOSALS_PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED')",
                "studentId", studentId, "roundId", roundId);
    }

    /** Locks the Student row (registration lock order: round first, then student). */
    public Integer lockActiveStudent(String studentId) {
        try {
            return jdbc.queryForObject(
                    "SELECT 1 FROM campuscore_auth.\"Student\" WHERE \"id\" = :studentId AND \"status\" = 'ACTIVE' FOR UPDATE",
                    params().addValue("studentId", studentId), Integer.class);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    /** Vacant member slot; surviving members keep their identity and order. */
    public List<Integer> listMemberOrders(UUID groupId) {
        return jdbc.queryForList(
                "SELECT member_order FROM thesis.thesis_group_member WHERE group_id = :groupId",
                params().addValue("groupId", groupId), Integer.class);
    }

    /** Live rounds make the teammate directory answer; the gate lives in the service. */
    public Integer countLiveRegistrationRounds() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_registration_round"
                        + " WHERE status = 'REGISTRATION_OPEN'",
                params(), Integer.class);
    }

    /**
     * Directory search over active students by student number, name or email
     * (max 8 hits, fold-accent LIKE over every token).
     */
    public List<StudentSearchResponse> searchActiveStudents(String normalized) {
        List<String> tokens = io.campuscore.restfulapi.common.util.LikePattern.tokens(normalized);
        if (tokens.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource searchParams = params()
                .addValue("foldFrom", io.campuscore.restfulapi.common.util.LikePattern.FOLD_FROM)
                .addValue("foldTo", io.campuscore.restfulapi.common.util.LikePattern.FOLD_TO);
        StringBuilder tokenPredicates = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String paramName = "token" + i;
            searchParams.addValue(paramName, tokens.get(i));
            tokenPredicates.append(" AND (TRANSLATE(LOWER(s.\"studentId\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(u.\"firstName\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(u.\"lastName\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\'")
                    .append(" OR TRANSLATE(LOWER(u.\"email\"), :foldFrom, :foldTo) LIKE :").append(paramName).append(" ESCAPE '\\')");
        }
        return jdbc.query(
                "SELECT s.\"id\", s.\"studentId\" AS student_number, u.\"firstName\", u.\"lastName\", u.\"email\","
                        + " cur.\"code\" AS curriculum_code, cur.\"name\" AS curriculum_name"
                        + " FROM campuscore_auth.\"Student\" s"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\""
                        + " LEFT JOIN academic.\"Curriculum\" cur ON cur.\"id\" = s.\"curriculumId\""
                        + " WHERE s.\"status\" = 'ACTIVE'"
                        + tokenPredicates
                        + " ORDER BY s.\"studentId\" LIMIT 8",
                searchParams,
                (rs, ignored) -> new StudentSearchResponse(
                        rs.getString("id"),
                        rs.getString("student_number"),
                        rs.getString("firstName"),
                        rs.getString("lastName"),
                        rs.getString("email"),
                        rs.getString("curriculum_code"),
                        rs.getString("curriculum_name")));
    }

    private int count(String sql, String paramName, UUID value) {
        Integer result = jdbc.queryForObject(sql, params().addValue(paramName, value), Integer.class);
        return result == null ? 0 : result;
    }

    private int count(String sql, String roundParam, UUID roundId, String studentParam, String studentId) {
        Integer result = jdbc.queryForObject(sql,
                params().addValue(roundParam, roundId).addValue(studentParam, studentId), Integer.class);
        return result == null ? 0 : result;
    }

    private int count(String sql, String studentParam, String studentId, String roundParam, UUID roundId) {
        Integer result = jdbc.queryForObject(sql,
                params().addValue(studentParam, studentId).addValue(roundParam, roundId), Integer.class);
        return result == null ? 0 : result;
    }
}
