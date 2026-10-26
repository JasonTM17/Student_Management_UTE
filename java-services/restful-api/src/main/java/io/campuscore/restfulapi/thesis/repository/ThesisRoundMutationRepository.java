package io.campuscore.restfulapi.thesis.repository;

import static io.campuscore.restfulapi.thesis.repository.ThesisMutationSql.one;
import static io.campuscore.restfulapi.thesis.repository.ThesisMutationSql.params;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Write-side statements for the thesis registration round lifecycle: schedule
 * authoring, status transitions, cancellation/restore fencing, deletion, the
 * close-registration and deadline escape hatches, and the locked status reads
 * every sibling service's round guard runs on. All row locks take the round
 * row first — the lock order the group and topic mutations rely on.
 */
@Repository
@Profile("persistence")
public class ThesisRoundMutationRepository {

    private static final String ROUND_NOT_FOUND_MESSAGE = "Thesis registration round not found";

    private final NamedParameterJdbcTemplate jdbc;

    public ThesisRoundMutationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertRound(UUID id, String name, String thesisType, Timestamp lecturerSubmitStart,
            Timestamp lecturerSubmitEnd, Timestamp registrationStart, Timestamp registrationEnd,
            Timestamp proposalPublishAt, Timestamp gvpbDeadline, Timestamp reportDate, Timestamp defenseDate) {
        jdbc.update("""
                INSERT INTO thesis.thesis_registration_round
                    (id, name, thesis_type, lecturer_submit_start, lecturer_submit_end,
                     registration_start, registration_end,
                     proposal_publish_at, gvpb_deadline, report_date, defense_date, status)
                VALUES (:id, :name, :thesisType, :lecturerSubmitStart, :lecturerSubmitEnd,
                        :registrationStart, :registrationEnd,
                        :proposalPublishAt, :gvpbDeadline, :reportDate, :defenseDate, 'DRAFT')
                """, params()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("thesisType", thesisType)
                .addValue("lecturerSubmitStart", lecturerSubmitStart)
                .addValue("lecturerSubmitEnd", lecturerSubmitEnd)
                .addValue("registrationStart", registrationStart)
                .addValue("registrationEnd", registrationEnd)
                .addValue("proposalPublishAt", proposalPublishAt)
                .addValue("gvpbDeadline", gvpbDeadline)
                .addValue("reportDate", reportDate)
                .addValue("defenseDate", defenseDate));
    }

    /** Optimistic status flip; 0 rows means a concurrent transition won. */
    public int transitionStatus(UUID id, String expected, String next) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = :next, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id AND status = :expected",
                params().addValue("id", id).addValue("expected", expected).addValue("next", next));
    }

    /** Early proposal opening clamps the authored lecturer window to now. */
    public int clampLecturerSubmitStart(UUID id) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round "
                        + "SET lecturer_submit_start = LEAST(lecturer_submit_start, CURRENT_TIMESTAMP) "
                        + "WHERE id = :id AND lecturer_submit_start IS NOT NULL AND lecturer_submit_start > CURRENT_TIMESTAMP",
                params().addValue("id", id));
    }

    /** Locks the full schedule row for amendment; every column read under FOR UPDATE. */
    public Map<String, Object> lockSchedule(UUID id) {
        return one(jdbc,
                "SELECT name, thesis_type, lecturer_submit_start, lecturer_submit_end, registration_start, "
                        + "registration_end, proposal_publish_at, gvpb_deadline, report_date, defense_date, status "
                        + "FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    public int updateSchedule(UUID id, String name, String thesisType, Timestamp lecturerSubmitStart,
            Timestamp lecturerSubmitEnd, Timestamp registrationStart, Timestamp registrationEnd,
            Timestamp proposalPublishAt, Timestamp gvpbDeadline, Timestamp reportDate, Timestamp defenseDate) {
        return jdbc.update("""
                UPDATE thesis.thesis_registration_round SET
                    name = :name, thesis_type = :thesisType,
                    lecturer_submit_start = :lecturerSubmitStart, lecturer_submit_end = :lecturerSubmitEnd,
                    registration_start = :registrationStart, registration_end = :registrationEnd,
                    proposal_publish_at = :proposalPublishAt, gvpb_deadline = :gvpbDeadline,
                    report_date = :reportDate, defense_date = :defenseDate,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = :id
                """, params()
                .addValue("id", id)
                .addValue("name", name)
                .addValue("thesisType", thesisType)
                .addValue("lecturerSubmitStart", lecturerSubmitStart)
                .addValue("lecturerSubmitEnd", lecturerSubmitEnd)
                .addValue("registrationStart", registrationStart)
                .addValue("registrationEnd", registrationEnd)
                .addValue("proposalPublishAt", proposalPublishAt)
                .addValue("gvpbDeadline", gvpbDeadline)
                .addValue("reportDate", reportDate)
                .addValue("defenseDate", defenseDate));
    }

    /** Cancel flip; 0 rows means the round already reached a terminal status. */
    public int cancelIfNotTerminal(UUID id) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = 'CANCELLED', cancelled_from_status = status, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')",
                params().addValue("id", id));
    }

    public Map<String, Object> lockCancellation(UUID id) {
        return one(jdbc,
                "SELECT status, cancelled_from_status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    /**
     * Locks the Student rows of every active member of the round's live groups,
     * in id order — the round-then-student lock order registration takes, so a
     * restore and an in-flight registration serialize on the Student row.
     */
    public List<String> lockRoundStudentIds(UUID id) {
        return jdbc.queryForList(
                "SELECT s.\"id\" FROM campuscore_auth.\"Student\" s "
                        + "WHERE s.\"id\" IN ("
                        + "SELECT m.student_id FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "WHERE g.round_id = :id AND g.status <> 'CANCELLED' AND m.active_participation) "
                        + "ORDER BY s.\"id\" FOR UPDATE",
                params().addValue("id", id), String.class);
    }

    public int countLiveGroups(UUID id) {
        return count("SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND status <> 'CANCELLED'",
                "id", id);
    }

    /** Active seats this round's members hold in OTHER live rounds (restore conflict count). */
    public Integer countSeatConflicts(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "JOIN thesis.thesis_group_member other ON other.student_id = m.student_id "
                        + "JOIN thesis.thesis_group og ON og.id = other.group_id AND og.status <> 'CANCELLED' "
                        + "JOIN thesis.thesis_registration_round orr ON orr.id = other.round_id "
                        + "WHERE g.round_id = :id AND g.status <> 'CANCELLED' AND m.active_participation "
                        + "AND other.round_id <> :id AND other.active_participation "
                        + "AND orr.status NOT IN ('RESULTS_PUBLISHED', 'CLOSED', 'CANCELLED')",
                params().addValue("id", id), Integer.class);
    }

    public Integer countFrozenHistory(UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = :id AND legacy_group_size_exception",
                params().addValue("id", id), Integer.class);
    }

    public int restore(UUID id, String target) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET status = :target, cancelled_from_status = NULL, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                        + "WHERE id = :id AND status = 'CANCELLED'",
                params().addValue("id", id).addValue("target", target));
    }

    public Map<String, Object> lockDeletionSnapshot(UUID id) {
        return one(jdbc,
                "SELECT name, status, thesis_type, cancelled_from_status "
                        + "FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    public int deleteRound(UUID id) {
        return jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", id));
    }

    /** The COALESCE report deadline the close-registration preview warns about. */
    public Map<String, Object> findEffectiveReportDeadline(UUID id) {
        return one(jdbc,
                "SELECT COALESCE(report_deadline, gvpb_deadline) AS report_deadline "
                        + "FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    /** Approval-state aggregate of a round's non-cancelled groups (close/publish previews). */
    public Map<String, Object> countApprovalStatesByRound(UUID roundId) {
        return jdbc.queryForMap(
                "SELECT COUNT(*) AS group_count, "
                        + "COUNT(*) FILTER (WHERE approval_status = 'APPROVED') AS approved_count, "
                        + "COUNT(*) FILTER (WHERE approval_status = 'PENDING') AS pending_count "
                        + "FROM thesis.thesis_group WHERE round_id = :roundId AND status <> 'CANCELLED'",
                params().addValue("roundId", roundId));
    }

    public Long countGroupReportsByRound(UUID roundId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_report WHERE round_id = :roundId",
                params().addValue("roundId", roundId), Long.class);
    }

    public Map<String, Object> lockReportDeadlineRow(UUID id) {
        return one(jdbc,
                "SELECT status, thesis_type, report_deadline, gvpb_deadline FROM thesis.thesis_registration_round "
                        + "WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    public int setReportDeadline(UUID id, Timestamp reportDeadline) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET report_deadline = :reportDeadline, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id",
                params().addValue("id", id).addValue("reportDeadline", reportDeadline));
    }

    public Map<String, Object> lockGvpbDeadlineRow(UUID id) {
        return one(jdbc,
                "SELECT status, thesis_type, gvpb_deadline, report_date, defense_date, report_deadline FROM thesis.thesis_registration_round "
                        + "WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    public int setGvpbDeadline(UUID id, Timestamp gvpbDeadline, Timestamp reportDate, Timestamp defenseDate) {
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round SET gvpb_deadline = :gvpbDeadline, "
                        + "report_date = :reportDate, defense_date = :defenseDate, "
                        + "updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = :id",
                params().addValue("id", id)
                        .addValue("gvpbDeadline", gvpbDeadline)
                        .addValue("reportDate", reportDate)
                        .addValue("defenseDate", defenseDate));
    }

    /** Unlocked registration window read for the open-registration transition guard. */
    public Map<String, Object> findRegistrationWindow(UUID id) {
        return one(jdbc,
                "SELECT registration_start, registration_end FROM thesis.thesis_registration_round WHERE id = :id",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    /** Locked bare status read every "is the round still live" guard runs on. */
    public Map<String, Object> lockStatus(UUID id) {
        return one(jdbc,
                "SELECT status FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    /** Locked registration gate: status, type and the stored window in one read. */
    public Map<String, Object> lockRegistrationGate(UUID id) {
        return one(jdbc,
                "SELECT status, thesis_type, registration_start, registration_end FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    /** Locked proposal-phase gate: status plus the lecturer submission window. */
    public Map<String, Object> lockProposalWindow(UUID id) {
        return one(jdbc,
                "SELECT status, lecturer_submit_start, lecturer_submit_end FROM thesis.thesis_registration_round WHERE id = :id FOR UPDATE",
                params().addValue("id", id), "ROUND_NOT_FOUND", ROUND_NOT_FOUND_MESSAGE);
    }

    private int count(String sql, String paramName, UUID value) {
        Integer result = jdbc.queryForObject(sql, params().addValue(paramName, value), Integer.class);
        return result == null ? 0 : result;
    }
}
