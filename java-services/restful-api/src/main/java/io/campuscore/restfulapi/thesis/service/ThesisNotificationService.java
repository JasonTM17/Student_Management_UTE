package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.mail.config.MailConfig;
import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.notification.model.CreateNotificationCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thesis lifecycle fan-out (spec R7): students hear about group approval,
 * rejection, topic binding, finalized defense scores, and published results;
 * lecturers hear about supervisor assignment. Mirrors
 * {@code GradePublishNotifier}: every fan-out runs {@code REQUIRES_NEW} so a
 * notification failure cannot roll back the workflow mutation, and callers
 * swallow the failure after logging. Results inbox/email dispatch is deferred
 * until the publishing transaction commits and requires published round state.
 */
@Service
@Profile("persistence")
public class ThesisNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ThesisNotificationService.class);

    static final String STUDENT_LINK = "/dashboard/thesis";
    static final String LECTURER_LINK = "/dashboard/lecturer/thesis";
    /** notifications.message is VARCHAR(2000); keep the body compact. */
    static final int MESSAGE_CODE_POINTS = 240;
    static final int TITLE_CODE_POINTS = 240;

    private final NotificationWriteRepository notifications;
    private final NamedParameterJdbcTemplate jdbc;
    private final EmailService email;
    private final MailConfig mailConfig;
    private final Clock clock = Clock.systemUTC();

    public ThesisNotificationService(
            NotificationWriteRepository notifications,
            NamedParameterJdbcTemplate jdbc,
            EmailService email,
            MailConfig mailConfig) {
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.email = email;
        this.mailConfig = mailConfig;
    }

    /** Notifies every group member after approve/reject. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyGroupDecision(UUID groupId, boolean approved, String reason) {
        List<String> userIds = memberUserIds(groupId);
        if (userIds.isEmpty()) {
            return 0;
        }
        String title = approved ? "Nhóm khóa luận được duyệt" : "Nhóm khóa luận bị từ chối";
        String topic = topicTitleByGroup(groupId);
        String message = approved
                ? "Nhóm của bạn" + (topic.isBlank() ? "" : " với đề tài \"" + topic + "\"")
                        + " đã được duyệt. Theo dõi tiến độ tại trang khóa luận."
                : "Nhóm của bạn bị từ chối" + (reason == null || reason.isBlank() ? "" : ". Lý do: " + reason);
        return fanOut(userIds, title, message, approved ? "SUCCESS" : "WARNING", STUDENT_LINK);
    }

    /** Notifies every group member when a mistaken approval is withdrawn. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyGroupApprovalWithdrawn(UUID groupId, String reason) {
        List<String> userIds = memberUserIds(groupId);
        if (userIds.isEmpty()) {
            return 0;
        }
        String message = "Nhóm của bạn đã bị rút lại quyết định duyệt"
                + (reason == null || reason.isBlank() ? "" : ". Lý do: " + reason)
                + ". Nhóm quay lại trạng thái chờ duyệt.";
        return fanOut(userIds, "Nhóm khóa luận bị rút duyệt", message, "WARNING", STUDENT_LINK);
    }

    /** Notifies group members when the leader binds a topic. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyTopicAssigned(UUID groupId, UUID topicId) {
        List<String> userIds = memberUserIds(groupId);
        if (userIds.isEmpty()) {
            return 0;
        }
        String title = topicTitle(topicId);
        return fanOut(userIds, "Đề tài đã được chọn",
                "Nhóm của bạn vừa chọn đề tài \"" + title + "\".", "INFO", STUDENT_LINK);
    }

    /**
     * Notifies group members and supervisors when the chair finalizes a
     * defense score. The score itself is withheld: students only see results
     * after the round publishes, so the message carries no number.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyScoreFinalized(UUID topicId) {
        List<String> userIds = new ArrayList<>(memberUserIdsByTopic(topicId));
        userIds.addAll(supervisorUserIds(topicId));
        userIds = userIds.stream().distinct().toList();
        if (userIds.isEmpty()) {
            return 0;
        }
        String title = topicTitle(topicId);
        return fanOut(userIds, "Hội đồng đã chốt điểm",
                "Hội đồng bảo vệ đã chốt điểm cho đề tài \"" + title
                        + "\". Điểm chính thức hiển thị sau khi đợt công bố kết quả.",
                "INFO", STUDENT_LINK);
    }

    /**
     * Notifies every student in an approved group when the round publishes
     * results. Groups whose topic carries a governance exclusion outcome
     * (REVIEW_FAILED / WITHDRAWN) never see the success "view your score"
     * notice — they get a separate non-success notice that states the outcome
     * without academic-fail wording.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyResultsPublished(UUID roundId) {
        String roundName = publishedRoundName(roundId);
        if (roundName == null) {
            return 0;
        }
        String sourceKey = resultsSourceKey(roundId);
        // A republish after a retraction retires the "retracted" notices too,
        // or the inbox stacks a stale correction on the fresh publication.
        notifications.deleteBySourceKey(resultsRetractedSourceKey(roundId));
        int sent = 0;
        List<String> includedIds = jdbc.queryForList(
                "SELECT DISTINCT s.\"userId\" FROM thesis.thesis_group_member m"
                        + " JOIN thesis.thesis_group g ON g.id = m.group_id"
                        + " JOIN thesis.thesis_topic t ON t.id = g.topic_id"
                        + " JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id"
                        + " WHERE g.round_id = :roundId AND g.approval_status = 'APPROVED'"
                        + " AND g.status <> 'CANCELLED'"
                        + " AND (t.result_status IS NULL OR t.result_status NOT IN ('REVIEW_FAILED', 'WITHDRAWN'))",
                new MapSqlParameterSource("roundId", roundId), String.class);
        if (includedIds != null && !includedIds.isEmpty()) {
            sent += fanOut(includedIds, "Kết quả khóa luận đã công bố",
                    "Kết quả đợt " + roundName.trim()
                            + " đã công bố. Mở trang khóa luận để xem điểm của bạn.",
                    "SUCCESS", STUDENT_LINK, sourceKey);
        }
        List<String> excludedIds = jdbc.queryForList(
                "SELECT DISTINCT s.\"userId\" FROM thesis.thesis_group_member m"
                        + " JOIN thesis.thesis_group g ON g.id = m.group_id"
                        + " JOIN thesis.thesis_topic t ON t.id = g.topic_id"
                        + " JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id"
                        + " WHERE g.round_id = :roundId AND g.approval_status = 'APPROVED'"
                        + " AND g.status <> 'CANCELLED'"
                        + " AND t.result_status IN ('REVIEW_FAILED', 'WITHDRAWN')",
                new MapSqlParameterSource("roundId", roundId), String.class);
        if (excludedIds != null && !excludedIds.isEmpty()) {
            // "Not eligible to defend in this round" — never "fail/trượt": the
            // outcome is a counter-review governance result, not a mark.
            sent += fanOut(excludedIds, "Kết quả khóa luận đã công bố",
                    "Kết quả đợt " + roundName.trim()
                            + " đã công bố. Đề tài của nhóm bạn không đủ điều kiện"
                            + " bảo vệ trong đợt này — xem chi tiết tại trang khóa luận.",
                    "WARNING", STUDENT_LINK, sourceKey);
        }
        return sent;
    }

    /** Inbox recall for retracted publication, keyed by the producer tag. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recallResultsPublished(UUID roundId) {
        // Only producer-tagged rows are recalled: round names are not unique,
        // so matching untagged legacy rows by a name prefix could delete
        // another round's notices. Rounds published before source_key existed
        // predate the retraction window anyway.
        return notifications.deleteBySourceKey(resultsSourceKey(roundId));
    }

    /** Correction notice after a retracted publication. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyResultsRetracted(UUID roundId) {
        String roundName = roundNameOf(roundId);
        List<String> userIds = jdbc.queryForList(
                "SELECT DISTINCT s.\"userId\" FROM thesis.thesis_group_member m"
                        + " JOIN thesis.thesis_group g ON g.id = m.group_id"
                        + " JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id"
                        + " WHERE g.round_id = :roundId AND g.approval_status = 'APPROVED'"
                        + " AND g.status <> 'CANCELLED'",
                new MapSqlParameterSource("roundId", roundId), String.class);
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        return fanOut(userIds, "Kết quả khóa luận đã được thu hồi",
                "Kết quả đợt " + roundName.trim()
                        + " đã được thu hồi để hiệu chỉnh và sẽ được công bố lại sau.",
                "WARNING", STUDENT_LINK, resultsRetractedSourceKey(roundId));
    }

    /** Roster-override notice: every current member hears who governs the change. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyGroupRosterChanged(UUID groupId) {
        List<String> userIds = memberUserIds(groupId);
        if (userIds.isEmpty()) {
            return 0;
        }
        String topic = topicTitleByGroup(groupId);
        return fanOut(userIds, "Danh sách thành viên nhóm đã thay đổi",
                "Danh sách thành viên nhóm của bạn"
                        + (topic.isBlank() ? "" : " (đề tài \"" + topic + "\")")
                        + " đã được điều chỉnh bởi phòng quản lý.",
                "INFO", STUDENT_LINK, null);
    }

    /**
     * Post-close roster removal: the removed student is no longer a member, so
     * the group notice cannot reach them — they get a dedicated message, and
     * the surviving roster still gets the shared roster-changed notice.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyGroupMemberRemoved(UUID groupId, String studentId) {
        int sent = 0;
        List<String> removedUserIds = jdbc.queryForList(
                "SELECT s.\"userId\" FROM campuscore_auth.\"Student\" s WHERE s.\"id\" = :studentId",
                new MapSqlParameterSource("studentId", studentId), String.class);
        if (!removedUserIds.isEmpty()) {
            String topic = topicTitleByGroup(groupId);
            sent += fanOut(removedUserIds, "Bạn đã được gỡ khỏi nhóm",
                    "Phòng quản lý đã gỡ bạn khỏi danh sách thành viên nhóm"
                            + (topic.isBlank() ? "" : " (đề tài \"" + topic + "\")")
                            + ".",
                    "INFO", STUDENT_LINK, null);
        }
        return sent + notifyGroupRosterChanged(groupId);
    }

    /**
     * Lifecycle phase signal for the roles that must act next: lecturers hear
     * when a proposal window opens, students when the catalog publishes or
     * registration opens, and everyone already engaged in the round when it
     * closes, cancels, or ends. Runs {@code REQUIRES_NEW} like every thesis
     * fan-out; callers invoke it only after the transition commits so a
     * reverted phase never leaves a stale notice behind.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyRoundPhase(UUID roundId, RoundStatus phase) {
        if (roundId == null || phase == null) {
            return 0;
        }
        Map<String, Object> round = roundPhaseRow(roundId);
        if (round.isEmpty()) {
            return 0;
        }
        String name = valueOrEmpty(round.get("name")).trim();
        return switch (phase) {
            case PROPOSAL_OPEN -> fanOut(activeLecturerUserIds(),
                    "Đợt khóa luận mở đề xuất đề tài",
                    "Đợt \"" + name + "\" đã mở cổng đề xuất đề tài cho giảng viên"
                            + deadlineClause(round.get("lecturer_submit_end"), "Hạn nộp") + ".",
                    "INFO", LECTURER_LINK);
            case PROPOSALS_PUBLISHED -> fanOut(activeStudentUserIds(),
                    "Danh mục đề tài đã công bố",
                    "Danh mục đề tài của đợt \"" + name + "\" đã được công bố. "
                            + "Xem đề tài tại trang khóa luận.",
                    "INFO", STUDENT_LINK);
            case REGISTRATION_OPEN -> fanOut(activeStudentUserIds(),
                    "Đợt khóa luận mở đăng ký",
                    "Đợt \"" + name + "\" đã mở đăng ký đề tài cho nhóm sinh viên"
                            + deadlineClause(round.get("registration_end"), "Hạn đăng ký") + ".",
                    "INFO", STUDENT_LINK);
            case REGISTRATION_CLOSED -> fanOut(participantUserIds(roundId),
                    "Đợt khóa luận đã đóng đăng ký",
                    "Đợt \"" + name + "\" đã đóng đăng ký đề tài. "
                            + "Theo dõi tiến độ và hạn nộp báo cáo tại trang khóa luận.",
                    "INFO", STUDENT_LINK);
            case CANCELLED -> fanOut(participantUserIds(roundId),
                    "Đợt khóa luận đã bị hủy",
                    "Đợt \"" + name + "\" đã bị hủy. Đăng ký và nhóm của bạn trong đợt này "
                            + "không còn hiệu lực — theo dõi đợt mới tại trang khóa luận.",
                    "WARNING", STUDENT_LINK);
            case CLOSED -> fanOut(participantUserIds(roundId),
                    "Đợt khóa luận đã kết thúc",
                    "Đợt \"" + name + "\" đã kết thúc. "
                            + "Kết quả đã công bố vẫn xem được tại trang khóa luận.",
                    "INFO", STUDENT_LINK);
            default -> 0;
        };
    }

    /** A cancelled round coming back must tell the same participants. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyRoundRestored(UUID roundId) {
        String name = roundNameOf(roundId);
        if (name.isBlank()) {
            return 0;
        }
        return fanOut(participantUserIds(roundId),
                "Đợt khóa luận được khôi phục",
                "Đợt \"" + name.trim() + "\" đã được khôi phục. "
                        + "Kiểm tra lại nhóm và đề tài của bạn tại trang khóa luận.",
                "INFO", STUDENT_LINK);
    }

    /** A closed round reopening into results-published is a lifecycle signal too. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyRoundReopened(UUID roundId) {
        String name = roundNameOf(roundId);
        if (name.isBlank()) {
            return 0;
        }
        return fanOut(participantUserIds(roundId),
                "Đợt khóa luận được mở lại",
                "Đợt \"" + name.trim() + "\" được mở lại; "
                        + "kết quả đã công bố có thể tra cứu tại trang khóa luận.",
                "INFO", STUDENT_LINK);
    }

    private Map<String, Object> roundPhaseRow(UUID roundId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT name, lecturer_submit_end, registration_end FROM thesis.thesis_registration_round WHERE id = :id",
                new MapSqlParameterSource("id", roundId));
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private List<String> activeLecturerUserIds() {
        return jdbc.queryForList(
                "SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"isActive\" = TRUE",
                new MapSqlParameterSource(), String.class);
    }

    private List<String> activeStudentUserIds() {
        return jdbc.queryForList(
                "SELECT \"userId\" FROM campuscore_auth.\"Student\" WHERE \"status\" = 'ACTIVE'",
                new MapSqlParameterSource(), String.class);
    }

    /**
     * Everyone already engaged in the round: group members, topic supervisors,
     * named counter-reviewers, and seated council members. This is the
     * audience for phase events that change the fate of their work — closing,
     * cancelling, or ending the round — rather than the broadcast opens that
     * address every eligible account.
     */
    private List<String> participantUserIds(UUID roundId) {
        List<String> userIds = jdbc.queryForList(
                "SELECT s.\"userId\" FROM thesis.thesis_group_member m"
                        + " JOIN thesis.thesis_group g ON g.id = m.group_id"
                        + " JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id"
                        + " WHERE g.round_id = :roundId"
                        + " UNION"
                        + " SELECT u.\"id\" FROM thesis.thesis_topic_supervisor sup"
                        + " JOIN thesis.thesis_topic t ON t.id = sup.topic_id"
                        + " JOIN campuscore_auth.\"Lecturer\" l"
                        + "   ON (l.\"id\" = sup.lecturer_id OR l.\"userId\" = sup.lecturer_id)"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\""
                        + " WHERE t.round_id = :roundId"
                        + " UNION"
                        + " SELECT u.\"id\" FROM thesis.thesis_topic t"
                        + " JOIN campuscore_auth.\"Lecturer\" l"
                        + "   ON (l.\"id\" = t.gvpb_lecturer_id OR l.\"userId\" = t.gvpb_lecturer_id)"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\""
                        + " WHERE t.round_id = :roundId AND t.gvpb_lecturer_id IS NOT NULL"
                        + " UNION"
                        + " SELECT u.\"id\" FROM thesis.thesis_council_member cm"
                        + " JOIN thesis.thesis_council c ON c.id = cm.council_id"
                        + " JOIN campuscore_auth.\"Lecturer\" l"
                        + "   ON (l.\"id\" = cm.lecturer_id OR l.\"userId\" = cm.lecturer_id)"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\""
                        + " WHERE c.round_id = :roundId",
                new MapSqlParameterSource("roundId", roundId), String.class);
        return userIds == null ? List.of() : userIds;
    }

    /** Deadlines inside notices use the same dd/MM/yyyy HH:mm Asia/Bangkok display the workspace pins. */
    private static String deadlineClause(Object instant, String label) {
        Instant at = instantValue(instant);
        if (at == null) {
            return "";
        }
        return ". " + label + ": " + java.time.format.DateTimeFormatter
                .ofPattern("dd/MM/yyyy HH:mm")
                .withZone(java.time.ZoneId.of("Asia/Bangkok"))
                .format(at);
    }

    private static Instant instantValue(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return null;
    }

    private static String resultsSourceKey(UUID roundId) {
        return "THESIS_RESULTS:" + roundId;
    }

    private static String resultsRetractedSourceKey(UUID roundId) {
        return "THESIS_RESULTS_RETRACTED:" + roundId;
    }

    private String roundNameOf(UUID roundId) {
        List<String> names = jdbc.queryForList(
                "SELECT name FROM thesis.thesis_registration_round WHERE id = :id",
                new MapSqlParameterSource("id", roundId), String.class);
        return names.isEmpty() ? "" : names.getFirst();
    }

    /**
     * Sends a separate notice to each member after publication commits. This
     * is best-effort SMTP submission, without durable retry or a delivery claim
     * when mail is disabled. Numerical grades stay behind authenticated lookup.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public int emailResultsPublished(UUID roundId) {
        String roundName = publishedRoundName(roundId);
        if (roundName == null) {
            return 0;
        }
        if (!mailConfig.isEnabled()) {
            log.warn("Thesis results published for round {} but SMTP is disabled; no email submitted", roundId);
            return 0;
        }
        List<Map<String, Object>> recipients = jdbc.queryForList(
                "SELECT DISTINCT u.\"id\", u.\"email\", u.\"firstName\", u.\"lastName\""
                        + " FROM thesis.thesis_group_member m"
                        + " JOIN thesis.thesis_group g ON g.id = m.group_id"
                        + " JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\""
                        + " WHERE g.round_id = :roundId AND g.approval_status = 'APPROVED'"
                        + " AND g.status <> 'CANCELLED' AND u.\"status\" = 'ACTIVE'",
                new MapSqlParameterSource("roundId", roundId));
        int submitted = 0;
        for (Map<String, Object> recipient : recipients) {
            String address = valueOrEmpty(recipient.get("email")).trim();
            if (address.isBlank()) {
                continue;
            }
            String recipientName = (valueOrEmpty(recipient.get("lastName")) + " "
                    + valueOrEmpty(recipient.get("firstName"))).trim();
            Map<String, Object> variables = Map.of(
                    "category", "KẾT QUẢ KHÓA LUẬN",
                    "title", "Kết quả khóa luận đã công bố",
                    "author", "Bộ môn phụ trách khóa luận",
                    "recipientName", recipientName.isBlank() ? "Sinh viên" : recipientName,
                    "content", "Kết quả đợt " + roundName.trim()
                            + " đã được công bố. Đăng nhập cổng CampusUTE để tra cứu kết quả của bạn.",
                    "actionUrl", "https://www.campusute.io.vn/dashboard/thesis?roundId=" + roundId,
                    "actionText", "Tra cứu kết quả của tôi");
            try {
                email.sendHtmlEmail(address, "[CampusUTE] Kết quả khóa luận đã công bố",
                        "academic-announcement", variables);
                submitted++;
            } catch (RuntimeException exception) {
                // No recipient, provider error or secret is added to this log.
                log.warn("Thesis results email submission failed for a member of round {}", roundId);
            }
        }
        log.info("Thesis results email round {}: {} submitted of {} eligible accounts",
                roundId, submitted, recipients.size());
        return submitted;
    }

    private String publishedRoundName(UUID roundId) {
        List<String> names = jdbc.queryForList(
                "SELECT name FROM thesis.thesis_registration_round"
                        + " WHERE id = :id AND status = 'RESULTS_PUBLISHED'",
                new MapSqlParameterSource("id", roundId), String.class);
        return names.isEmpty() ? null : names.getFirst();
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Notifies lecturers newly assigned as supervisors of a topic. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifySupervisorsAssigned(UUID topicId, List<String> lecturerIds) {
        if (lecturerIds == null || lecturerIds.isEmpty()) {
            return 0;
        }
        List<String> userIds = jdbc.queryForList(
                "SELECT DISTINCT u.\"id\" FROM campuscore_auth.\"Lecturer\" l"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\""
                        + " WHERE l.\"id\" IN (:lecturerIds)",
                new MapSqlParameterSource("lecturerIds", lecturerIds), String.class);
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        String title = topicTitle(topicId);
        return fanOut(userIds, "Bạn được phân công hướng dẫn",
                "Bạn được phân công hướng dẫn đề tài \"" + title + "\".",
                "INFO", LECTURER_LINK);
    }

    private List<String> memberUserIds(UUID groupId) {
        return jdbc.queryForList(
                "SELECT DISTINCT s.\"userId\" FROM thesis.thesis_group_member m"
                        + " JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id"
                        + " WHERE m.group_id = :groupId",
                new MapSqlParameterSource("groupId", groupId), String.class);
    }

    private List<String> memberUserIdsByTopic(UUID topicId) {
        return jdbc.queryForList(
                "SELECT DISTINCT s.\"userId\" FROM thesis.thesis_group_member m"
                        + " JOIN thesis.thesis_group g ON g.id = m.group_id"
                        + " JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id"
                        + " WHERE g.topic_id = :topicId AND g.status <> 'CANCELLED'"
                        + " AND g.approval_status = 'APPROVED'",
                new MapSqlParameterSource("topicId", topicId), String.class);
    }

    private List<String> supervisorUserIds(UUID topicId) {
        return jdbc.queryForList(
                "SELECT DISTINCT u.\"id\" FROM thesis.thesis_topic_supervisor sup"
                        + " JOIN campuscore_auth.\"Lecturer\" l ON l.\"id\" = sup.lecturer_id"
                        + " JOIN campuscore_auth.\"User\" u ON u.\"id\" = l.\"userId\""
                        + " WHERE sup.topic_id = :topicId",
                new MapSqlParameterSource("topicId", topicId), String.class);
    }

    private String topicTitle(UUID topicId) {
        try {
            String title = jdbc.queryForObject(
                    "SELECT title FROM thesis.thesis_topic WHERE id = :id",
                    new MapSqlParameterSource("id", topicId), String.class);
            return title == null ? "" : title;
        } catch (RuntimeException exception) {
            log.warn("Thesis topic title lookup failed for {}", topicId, exception);
            return "";
        }
    }

    private String topicTitleByGroup(UUID groupId) {
        try {
            String title = jdbc.queryForObject(
                    "SELECT t.title FROM thesis.thesis_group g"
                            + " JOIN thesis.thesis_topic t ON t.id = g.topic_id"
                            + " WHERE g.id = :groupId",
                    new MapSqlParameterSource("groupId", groupId), String.class);
            return title == null ? "" : title;
        } catch (RuntimeException exception) {
            log.debug("No topic title for group {} — notification sent without it", groupId, exception);
            return "";
        }
    }

    private int fanOut(List<String> userIds, String title, String message, String type, String link) {
        return fanOut(userIds, title, message, type, link, null);
    }

    private int fanOut(List<String> userIds, String title, String message, String type, String link,
            String sourceKey) {
        Instant now = Instant.now(clock);
        String safeTitle = capCodePoints(title, TITLE_CODE_POINTS);
        String safeMessage = capCodePoints(message, MESSAGE_CODE_POINTS);
        List<CreateNotificationCommand> commands = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            commands.add(new CreateNotificationCommand(
                    UUID.randomUUID().toString(), userId, safeTitle, safeMessage, type, link, now));
        }
        return notifications.createBatch(commands, sourceKey);
    }

    /** Truncates by code points so Vietnamese diacritics are never split. */
    private static String capCodePoints(String value, int cap) {
        if (value.codePointCount(0, value.length()) <= cap) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, cap));
    }
}
