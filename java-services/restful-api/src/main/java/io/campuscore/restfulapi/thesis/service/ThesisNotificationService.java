package io.campuscore.restfulapi.thesis.service;

import io.campuscore.restfulapi.mail.config.MailConfig;
import io.campuscore.restfulapi.mail.service.EmailService;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository.CreateNotificationCommand;
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

    /** Notifies every student in an approved group when the round publishes results. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyResultsPublished(UUID roundId) {
        String roundName = publishedRoundName(roundId);
        if (roundName == null) {
            return 0;
        }
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
        return fanOut(userIds, "Kết quả khóa luận đã công bố",
                "Kết quả đợt " + roundName.trim()
                        + " đã công bố. Mở trang khóa luận để xem điểm của bạn.",
                "SUCCESS", STUDENT_LINK);
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
        Instant now = Instant.now(clock);
        String safeTitle = capCodePoints(title, TITLE_CODE_POINTS);
        String safeMessage = capCodePoints(message, MESSAGE_CODE_POINTS);
        List<CreateNotificationCommand> commands = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            commands.add(new CreateNotificationCommand(
                    UUID.randomUUID().toString(), userId, safeTitle, safeMessage, type, link, now));
        }
        return notifications.createBatch(commands);
    }

    /** Truncates by code points so Vietnamese diacritics are never split. */
    private static String capCodePoints(String value, int cap) {
        if (value.codePointCount(0, value.length()) <= cap) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, cap));
    }
}
